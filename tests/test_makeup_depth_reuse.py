"""Exact trace-local reuse keys and native fallback/GLSL contract."""
import sys,unittest,tempfile,subprocess
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[1];sys.path[:0]=[str(ROOT/'scripts'),str(ROOT/'tests')]
import build_makeup_depth_reuse as m
import test_makeup_unused_sky as old

class DepthReuseTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.native=m.read_archive(old.BASE);cls.files=m.patch(cls.native)
    def pp(self,files,mode=2,debug=0,extra=None,entry='shaders/world0/gbuffers_water.fsh'):
        return old.preprocess(files,entry,macros=dict(old.MACROS,**(extra or {})),native_options={m.OPTION:mode,m.DEBUG:debug})
    def test_fp32_nearest_and_corner_keys_equal_native_clamped_texel(self):
        rng=np.random.default_rng(50529);accepted=0;corner=0
        for width,height in [(1920,1200),(3839,2159),(2560,1600),(17,33)]:
            size=np.array([width,height],dtype=np.float32)
            # Neighbour coordinates stress same/adjacent texels and clamp edges.
            uv=rng.uniform(-.2,1.2,(100000,2)).astype(np.float32);other=uv+rng.uniform(-.9,.9,uv.shape).astype(np.float32)/size
            def key(x):
                p=np.clip(x,0,1)*size;phase=p-np.floor(p);outside=(x<=0)|(x>=1)
                valid=np.all(outside|((phase>.02)&(phase<.98)),axis=1)
                return np.minimum(np.floor(p),size-1).astype(int),valid
            a,va=key(uv);b,vb=key(other);same=va&vb&np.all(a==b,axis=1);accepted+=int(same.sum())
            # Independent native nearest definition, including exact1 endpoint.
            native=lambda x:np.maximum(0,np.minimum(np.floor(x.astype(np.float64)*size).astype(int),size.astype(int)-1))
            self.assertTrue(np.array_equal(native(uv)[same],native(other)[same]))
            ca=np.all((uv<=0)|(uv>=1),axis=1);cb=np.all((other<=0)|(other>=1),axis=1);cs=ca&cb&np.all((uv>=1)==(other>=1),axis=1);corner+=int(cs.sum());self.assertTrue(np.array_equal(native(uv)[cs],native(other)[cs]))
        self.assertGreater(accepted,100000);self.assertGreater(corner,10000)
    def test_off_excluded_dimensions_and_native_ancestry(self):
        self.assertEqual(old.normalized(self.pp(self.files,0)),old.normalized(self.pp(self.native,0)))
        for entry in ['shaders/world1/gbuffers_water.fsh','shaders/world-1/gbuffers_water.fsh','shaders/gbuffers_water.fsh']:
            self.assertEqual(old.normalized(self.pp(self.files,entry=entry)),old.normalized(self.pp(self.native,entry=entry)))
        for extra in [{'DISTANT_HORIZONS':1},{'VOXY':1},{'VOXY_PATCH':1}]:
            self.assertEqual(old.normalized(self.pp(self.files,extra=extra)),old.normalized(self.pp(self.native,extra=extra)))
        macros={k:v for k,v in old.MACROS.items() if k!='MC_OS_MAC'};macros['MC_OS_LINUX']=1
        options={m.OPTION:2,m.DEBUG:0}
        self.assertEqual(old.normalized(old.preprocess(self.files,'shaders/world0/gbuffers_water.fsh',macros=macros,native_options=options)),old.normalized(old.preprocess(self.native,'shaders/world0/gbuffers_water.fsh',macros=macros,native_options=options)))
        self.assertEqual(self.native['LICENSE'],self.files['LICENSE'])
    def test_native_and_all_new_modes_and_independent_reference_compile(self):
        with tempfile.TemporaryDirectory() as directory:
            for parity in [False,True]:
                files=m.patch(self.native,parity)
                for mode in [0,1,2,3,4]:
                    p=Path(directory)/'reuse.frag';p.write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'+self.pp(files,mode,1));r=subprocess.run(['glslangValidator','-S','frag',str(p)],capture_output=True,text=True);self.assertEqual(r.returncode,0,r.stdout+r.stderr)

if __name__=='__main__':unittest.main()

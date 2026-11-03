"""G05 refinement boxes: independent FP32 sign-path and fallback checks."""
import sys,unittest,tempfile,subprocess
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[1];sys.path[:0]=[str(ROOT/'scripts'),str(ROOT/'tests')]
import build_makeup_refined_miss as m
import test_makeup_unused_sky as old

class RefinedMissTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.native=m.read_archive(old.BASE);cls.files={kind:m.patch(cls.native,kind) for kind in ['glass','water']}
    def test_fp32_sampled_halved_sign_paths_keep_zero_border_and_no_glass_fallback(self):
        rng=np.random.default_rng(505291);f=np.float32;admitted={'water':0,'glass':0};checks=0
        for _ in range(20000):
            px=f(rng.uniform(.1,4));py=f(rng.uniform(.1,4));point=rng.uniform([-300,-300,-500],[300,300,-.01]).astype(np.float32)
            step=rng.uniform(-20,20,3).astype(np.float32);error=f(.0001)*(np.abs(point)+f(2049));radius=np.abs(step)+error
            if not point[2]+radius[2]<0:continue
            edge=np.array([px*point[0],-px*point[0],py*point[1],-py*point[1]],dtype=np.float32)+point[2]
            bound=np.array([px*radius[0],px*radius[0],py*radius[1],py*radius[1]],dtype=np.float32)+radius[2]
            glass=bool((edge>bound).any() and px*point[0]+f(197)*point[2]<-(px*radius[0]+f(197)*radius[2]))
            water=bool(py*point[1]+point[2]>py*radius[1]+radius[2])
            if not (glass or water):continue
            for kind,allowed in [('glass',glass),('water',water)]:
                if not allowed:continue
                admitted[kind]+=1
                # Independent random sign/zero paths include every step count.
                for path in range(4):
                    p=point.copy();increment=step.copy()
                    for j in range(9):
                        increment*=f(.5);p+=increment*f(rng.choice([-1,0,1]));self.assertLess(p[2],0)
                        uv=np.array([px*p[0],py*p[1]],dtype=np.float32)/-p[2]*f(.5)+f(.5)
                        if kind=='water':self.assertGreater(uv[1],1)
                        else:
                            self.assertTrue(((uv<0)|(uv>1)).any());self.assertLess(uv[0],99)
                        checks+=1
        self.assertGreater(admitted['glass'],1000);self.assertGreater(admitted['water'],1000);self.assertGreater(checks,100000)
    def test_native_fallbacks_and_actual_fragment_compile(self):
        with tempfile.TemporaryDirectory() as directory:
            for kind,files in self.files.items():
                option='SHADERLOD_G05_GLASS_MISS' if kind=='glass' else 'SHADERLOD_G05_PROJECTED_MISS';debug='SHADERLOD_G05_GLASS_DEBUG' if kind=='glass' else 'SHADERLOD_G05_DEBUG'
                def pp(f,on=1,extra=None,entry='shaders/world0/gbuffers_water.fsh'):
                    return old.preprocess(f,entry,macros=dict(old.MACROS,**(extra or {})),native_options={option:on,debug:0})
                self.assertEqual(old.normalized(pp(files,0)),old.normalized(pp(self.native,0)))
                for extra in [{'DISTANT_HORIZONS':1},{'VOXY':1},{'VOXY_PATCH':1}]:self.assertEqual(old.normalized(pp(files,extra=extra)),old.normalized(pp(self.native,extra=extra)))
                for entry in ['shaders/world1/gbuffers_water.fsh','shaders/world-1/gbuffers_water.fsh']:self.assertEqual(old.normalized(pp(files,entry=entry)),old.normalized(pp(self.native,entry=entry)))
                p=Path(directory)/'miss.frag';p.write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'+pp(files));r=subprocess.run(['glslangValidator','-S','frag',str(p)],capture_output=True,text=True);self.assertEqual(r.returncode,0,r.stdout+r.stderr)

if __name__=='__main__':unittest.main()

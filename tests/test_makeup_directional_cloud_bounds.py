"""Pinned cloud source and independently sampled directional-atlas bounds."""
import sys, unittest, tempfile, subprocess, math, random
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[1]
sys.path[:0]=[str(ROOT/'scripts'),str(ROOT/'tests')]
import build_makeup_directional_cloud_bounds as m
import test_makeup_unused_sky as old

class DirectionalCloudTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.native=m.read_archive(old.BASE);cls.files=m.patch(cls.native)
        cls.rgba=m.decoded_cloud_rgba(cls.native['shaders/textures/clouds_natural_512_R_8bit.png'])
        cls.atlas=m.bounds(cls.rgba)
    def pp(self,files,mode=2,entry='shaders/world0/deferred.fsh',extra=None):
        return old.preprocess(files,entry,native_options={m.OPTION:mode,m.DEBUG:0,'V_CLOUDS':2,'CLOUD_VOL_STYLE':0,'CLOUD_SPEED':0},macros=dict(old.MACROS,**(extra or {})))
    def test_off_and_excluded_consumers_native(self):
        self.assertEqual(old.normalized(self.pp(self.files,0)),old.normalized(self.pp(self.native,0)))
        for entry in ['shaders/world1/deferred.fsh','shaders/world-1/deferred.fsh','shaders/world0/composite.fsh','shaders/world0/gbuffers_water.fsh']:
            self.assertEqual(old.normalized(self.pp(self.files,entry=entry)),old.normalized(self.pp(self.native,entry=entry)))
        for macro in ['DISTANT_HORIZONS','VOXY','VOXY_PATCH']:
            self.assertEqual(old.normalized(self.pp(self.files,extra={macro:1})),old.normalized(self.pp(self.native,extra={macro:1})))
        self.assertEqual(self.files['LICENSE'],self.native['LICENSE'])
        self.assertNotIn(b'uniform.float.shaderlod',self.files['shaders/shaders.properties'])
    def test_every_sampled_bilinear_contributor_is_bounded_with_wrapping_and_fp32(self):
        rng=random.Random(80829);f=np.float32;queries=0;tighter=0
        for _ in range(12000):
            # Include seams and exact bin boundaries plus arbitrary fractions.
            uv=np.array([rng.uniform(-15,15),rng.uniform(-15,15)],dtype=np.float32)
            if rng.random()<.3:uv[rng.randrange(2)]=f(rng.randrange(-128,128)/32+1e-7*rng.choice([-1,1]))
            travel=np.array([rng.uniform(-.031249,.031249),rng.uniform(-.031249,.031249)],dtype=np.float32)
            samples=rng.choice([4,8,rng.randrange(7,17)]);step=travel/f(samples)
            bins=np.clip(np.ceil(np.abs(travel)*f(128))-1,0,3).astype(int)+(travel<0).astype(int)*4
            tile=np.floor((uv-np.floor(uv))*f(32)).astype(int)
            bound=self.atlas[(bins[1]*32+tile[1])*256+bins[0]*32+tile[0]]
            p=uv.copy()
            for i in range(samples):
                tex=p*f(512)-f(.5);xy=np.floor(tex).astype(int)
                for dy in (0,1):
                    for dx in (0,1):
                        red=self.rgba[4*(((xy[1]+dy)%512)*512+(xy[0]+dx)%512)]
                        self.assertLessEqual(red,bound);queries+=1
                p+=step
            self.assertLessEqual(bound,255)
            # These signed rectangles are strict subsets of old isotropic54².
            oldbound=min(255,1+max(self.rgba[4*(((tile[1]*16+y)%512)*512+(tile[0]*16+x)%512)] for y in range(-19,35) for x in range(-19,35))) if _<100 else 255
            self.assertLessEqual(bound,oldbound)
            if _<100 and bound<oldbound:tighter+=1
        self.assertGreater(queries,350000);self.assertGreater(tighter,0)
    def test_control_and_skip_actual_native_fragment_compile(self):
        with tempfile.TemporaryDirectory() as directory:
            for tail,mode,split,masked in [(tail,mode,False,False) for tail in [0,4,8] for mode in [0,1,2]]+[(4,mode,True,False) for mode in [0,1,2]]+[(tail,mode,False,True) for tail in [4,8] for mode in [0,1,2]]:
                source=self.pp(m.patch(self.native,tail,native_queries=(split or masked),split_tail=split,masked_tail=masked),mode)
                if masked and mode:
                    self.assertEqual(source.count('intersection_pos += increment;'),1)
                    self.assertNotIn('break;',source)
                if mode:self.assertIn('shaderlodG08DirectionalBound',source)
                p=Path(directory)/'cloud.frag';p.write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'+source)
                r=subprocess.run(['glslangValidator','-S','frag',str(p)],capture_output=True,text=True)
                self.assertEqual(r.returncode,0,r.stdout+r.stderr)
    def test_native_nonpositive_density_guard_keeps_queries_and_finite_opacity(self):
        files=m.patch(self.native,native_queries=True,zero_guard=True)
        self.assertNotIn(m.ATLAS,files)
        self.assertNotIn(b'texture.deferred.noisetex',files['shaders/shaders.properties'])
        self.assertEqual(files[m.CLOUD].count(b'texture2D(gaux2,'),self.native[m.CLOUD].count(b'texture2D(gaux2,'))
        self.assertEqual(old.normalized(self.pp(files,0)),old.normalized(self.pp(self.native,0)))
        # Exact native surface/thickness tests for finite nonpositive normalized
        # density, including subnormal magnitudes and rounded coincident planes.
        f=np.float32
        for value in [f(0),f(-1e-40),f(-1e-8),f(-.001),f(-1),f(-100)]:
            for center,lower,upper in [(f(160),f(12),f(40)),(f(300),f(20),f(5))]:
                inf=f(center-f(value*lower));sup=f(center+f(value*upper));thickness=f(sup-inf)
                self.assertLessEqual(thickness,0)
                for y in [f(center-100),center,f(center+100)]:self.assertFalse(y>inf and y<sup)
        with tempfile.TemporaryDirectory() as d:
            for mode in [0,1,2]:
                text=self.pp(files,mode);p=Path(d)/'cloud.frag';p.write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'+text)
                r=subprocess.run(['glslangValidator','-S','frag',str(p)],capture_output=True,text=True);self.assertEqual(r.returncode,0,r.stdout+r.stderr)

if __name__=='__main__':unittest.main()

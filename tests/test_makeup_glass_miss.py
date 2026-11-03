"""G05 glass certificate, fallback-bound counterexample and actual GLSL."""
import sys,math,random,re,subprocess,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'scripts'));sys.path.insert(0,str(ROOT/'tests'))
import build_makeup_glass_miss as m
import test_makeup_unused_sky as old
import numpy as np

class GlassMissTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.native=m.read_archive(old.BASE);cls.files=m.patch(cls.native)
    def pp(self,files,on=1,debug=0,entry='shaders/world0/gbuffers_water.fsh',macros=None):
        return old.preprocess(files,entry,0,macros,{m.OPTION:on,m.DEBUG:debug})
    def test_control_other_dimensions_programs_and_unsupported_tuple_native(self):
        entries=['shaders/world0/gbuffers_water.fsh','shaders/world0/gbuffers_water.vsh','shaders/world0/gbuffers_hand_water.fsh','shaders/world0/deferred.fsh','shaders/world1/gbuffers_water.fsh','shaders/world-1/gbuffers_water.fsh','shaders/gbuffers_water.fsh']
        for entry in entries:
            self.assertEqual(old.normalized(self.pp(self.files,0,entry=entry)),old.normalized(self.pp(self.native,0,entry=entry)))
            if entry!=entries[0]:self.assertEqual(old.normalized(self.pp(self.files,entry=entry)),old.normalized(self.pp(self.native,entry=entry)))
        for key,value in [('DISTANT_HORIZONS',1),('VOXY',1),('VOXY_PATCH',1),('MC_VERSION',12102),('IRIS_VERSION',10811)]:
            macros=dict(old.MACROS,**{key:value});self.assertEqual(old.normalized(self.pp(self.files,macros=macros)),old.normalized(self.pp(self.native,macros=macros)))
    def test_native_water_trace_and_caller_preserved_glass_has_required_bounds(self):
        native=self.native[m.WATER].decode();new=self.files[m.WATER].decode();a=native.index('vec3 fastRaymarch(');b=native.index('\n#if SUN_REFLECTION',a)
        self.assertIn(native[a:b],new)
        a=native.index('vec4 reflection_calc(');b=native.index('//  GLASS',a)
        self.assertIn(native[a:b],new)
        helper=self.files[m.HELPER].decode()
        for anchor in ['i > 0 && i <= 6 && !searchFlag && outOfEyeFlag','direction.z <= 0.0','infinite = 1.0','abs(hitPoint)','dither >= 0.0 && dither <= 1.0','-px * point.x','-py * point.y']:
            self.assertIn(anchor,helper)
        self.assertEqual(helper.count('texture2DLod(depthtex1, screenMarchPos.xy, 0.0)'),1)
        self.assertEqual(self.native['LICENSE'],self.files['LICENSE'])
    def test_why_late_coarse_exit_cannot_ignore_native76_fallback(self):
        # An outward ray is still on-screen at76 but outside at127. This is
        # why the source refuses entry i>=7 even when a tail is monotone.
        px=1.;p=np.array([0.,0.,-100.]);d=np.array([1.,0.,0.])
        uv=lambda t:.5+.5*(p[0]+d[0]*t)/-(p[2]+d[2]*t)
        self.assertLess(uv(76),1);self.assertGreater(uv(127),1)
        self.assertIn('i <= 6',self.files[m.HELPER].decode())
    def test_fp32_accepted_edges_keep_every_refined_tail_and76_fallback_outside(self):
        rng=random.Random(505);f=np.float32;accepted=0;checks=0
        for _ in range(24000):
            px=f(rng.uniform(.1,4));py=f(rng.uniform(.1,4));h=np.array([rng.uniform(-300,300),rng.uniform(-300,300),rng.uniform(-500,-.1)],dtype=np.float32)
            d=np.array([rng.uniform(-1,1),rng.uniform(-1,1),rng.uniform(-1,0)],dtype=np.float32);d/=f(max(1.,float(np.linalg.norm(d))))
            i=rng.randrange(1,7);dither=f(rng.random());t=f(f(2.)**f(i-1+dither)-f(1.));p=h+d*t;err=f(.0001)*(np.abs(h)+np.abs(d)*f(2048.)+f(1.))
            edge=np.array([px*p[0],-px*p[0],py*p[1],-py*p[1]],dtype=np.float32)+p[2]
            slope=np.array([px*d[0],-px*d[0],py*d[1],-py*d[1]],dtype=np.float32)+d[2]
            bound=np.array([px*err[0],px*err[0],py*err[1],py*err[1]],dtype=np.float32)+err[2]
            se=f(1e-5)*(np.array([px*abs(d[0]),px*abs(d[0]),py*abs(d[1]),py*abs(d[1])],dtype=np.float32)+abs(d[2])+f(1.))
            good=(edge>bound)&(slope>se)
            if not p[2]<-err[2] or not good.any():continue
            accepted+=1;oldp=p.copy();step=None;search=False;points=[]
            for j in range(i,10):
                if search:
                    step*=f(.5);p+=step*f(rng.choice([-1,1]))
                else:
                    newp=h+d*f(f(2.)**f(j+dither)-f(1.));step=newp-p;p=newp
                points.append(p.copy())
                if rng.random()<.3:search=True
            points.append(h+d*f(76.))
            for q in points:
                e=np.array([px*q[0],-px*q[0],py*q[1],-py*q[1]],dtype=np.float32)+q[2]
                self.assertTrue(q[2]<0);self.assertTrue((e[good]>0).all());checks+=1
        self.assertGreater(accepted,1000);self.assertGreater(checks,10000)
    def test_actual_control_on_debug_programs_compile(self):
        with tempfile.TemporaryDirectory() as tmp:
            for label,files,on,debug in [('native',self.native,0,0),('control',self.files,0,0),('on',self.files,1,0),('debug',self.files,1,1)]:
                p=Path(tmp)/(label+'.frag');p.write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'+self.pp(files,on,debug))
                run=subprocess.run(['glslangValidator','-S','frag',str(p)],capture_output=True,text=True);self.assertEqual(run.returncode,0,run.stdout+run.stderr)

if __name__=='__main__':unittest.main()

"""Conditional moment model and actual pinned-source limits; no GL/runtime claim."""
import hashlib
import inspect
import math
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile
import test_photon_procedural_lod as photon
import test_normal_error_budget as error_tests

ROOT=Path(__file__).resolve().parents[1]
HELPER=ROOT/'shaderpacks/common/shaders/include/shaderlod/normal_variance.glsl'


def valid_covariance(c):
    return all(map(math.isfinite,c)) and 0<=c[0]<=.01 and 0<=c[2]<=.01 and abs(c[1])<=.01 and c[1]*c[1]<=c[0]*c[2]

def eigenvalues(c):
    d=math.hypot(c[0]-c[2],2*c[1]);return ((c[0]+c[2]-d)/2,(c[0]+c[2]+d)/2)

def removed(c,w):
    if not valid_covariance(c) or not math.isfinite(w) or not 0<=w<=1:return (-1,-1,-1)
    return tuple((1-w*w)*x for x in c)

def scale(mode,allow_ggx,q):
    if mode==1:return 2
    if mode!=2 or not allow_ggx or not math.isfinite(q) or not .5<=q<=.999:return 0
    return 2*q/(-math.log1p(-q)-q)

def compensate(alpha,c,mode=1,enabled=True,near=False,moments=True,mean=True,material=True,unstable=False,allow_ggx=False,q=.94,view=1,light=1,grazing=.15,maxvar=.001,maxanisotropy=.1,maxdelta=.1):
    if not enabled or near or not moments or not mean or not material or unstable or not math.isfinite(alpha) or not .0001<=alpha<=1 or not valid_covariance(c):return alpha,False
    if not all(map(math.isfinite,(view,light,grazing,maxvar,maxanisotropy,maxdelta))) or not 0<=view<=1 or not 0<=light<=1 or not 0<=grazing<=.5 or view<=grazing or light<=grazing or not 0<=maxvar<=.01 or c[0]>maxvar or c[2]>maxvar or not 0<=maxanisotropy<=1 or not 0<=maxdelta<=1:return alpha,False
    trace=c[0]+c[2];d=math.hypot(c[0]-c[2],2*c[1])
    if trace>0 and d>maxanisotropy*trace:return alpha,False
    k=scale(mode,allow_ggx,q)
    if k<=0:return alpha,False
    result=math.sqrt(alpha*alpha+k*trace/2)
    if not math.isfinite(result) or result>1 or result-alpha>maxdelta:return alpha,False
    return result,True

def autocov(delta):return math.cos(delta)/8+math.cos(2*delta)/128

def difference_cov(dx,dy):
    return (2*(autocov(0)-autocov(dx)),autocov(dx-dy)-autocov(dx)-autocov(dy)+autocov(0),2*(autocov(0)-autocov(dy)))

def sample_cov(values):
    mean=tuple(sum(x[i] for x in values)/len(values) for i in (0,1))
    return tuple(sum((x[i]-mean[i])*(x[j]-mean[j]) for x in values)/len(values) for i,j in ((0,0),(0,1),(1,1)))

def ggx_slope_density(radius,alpha):return alpha*alpha/(math.pi*(alpha*alpha+radius*radius)**2)

def simpson(function,b,n=10000):
    h=b/n
    return h/3*(function(0)+function(b)+sum((4 if i%2 else 2)*function(i*h) for i in range(1,n)))


class ConditionalModelTests(unittest.TestCase):
    def test_gaussian_isotropic_width_and_zero_removed_baseline(self):
        for alpha in (.002,.1,.4):
            for variance in (0,.00001,.0001):
                output,accepted=compensate(alpha,(variance,0,variance))
                self.assertTrue(accepted)
                self.assertAlmostEqual(alpha*alpha/2+variance,output*output/2,places=14)
        self.assertEqual((.002,True),compensate(.002,(0,0,0)))

    def test_variance_loss_is_one_minus_weight_squared_not_error_squared(self):
        c=(.0001,0,.0001)
        self.assertEqual((0,0,0),removed(c,1))
        self.assertEqual(c,removed(c,0))
        self.assertAlmostEqual(.75e-4,removed(c,.5)[0])
        self.assertNotEqual((1-.5)**2*c[0],removed(c,.5)[0])
        a=compensate(.002,removed(c,1-1e-6))[0]
        self.assertLess(abs(a-.002),1e-6)

    def test_ggx_full_variance_diverges_and_tail_choice_changes_mapping(self):
        coefficients=[1/scale(2,True,q) for q in (.5,.9,.94,.99,.999)]
        self.assertEqual(coefficients,sorted(coefficients))
        self.assertGreater(coefficients[-1],2.9)
        self.assertEqual(0,scale(2,False,.94))
        self.assertEqual(0,scale(2,True,1))
        for q in (.5,.9,.94,.99):
            alpha=.01;cutoff=alpha*math.sqrt(q/(1-q))
            mass=simpson(lambda r:2*math.pi*r*ggx_slope_density(r,alpha),cutoff)
            moment=simpson(lambda r:2*math.pi*r**3*ggx_slope_density(r,alpha),cutoff)/(2*mass)
            expected=alpha*alpha/scale(2,True,q)
            self.assertAlmostEqual(q,mass,places=8)
            self.assertAlmostEqual(expected,moment,places=10)
        print('radialGGX truncation mass/peraxis_variance_over_alpha_squared:',list(zip((.5,.9,.94,.99,.999),coefficients)))

    def test_known_moments_alone_do_not_preserve_peak_for_non_gaussian_fields(self):
        variance=.0001;alpha=.002
        # Both fields have mean0 and covariance diag(variance,variance).
        # RingA vs halfcenter+ringB differ in their higher moments.
        a_peak=ggx_slope_density(math.sqrt(2*variance),alpha)
        b_peak=.5*ggx_slope_density(0,alpha)+.5*ggx_slope_density(math.sqrt(4*variance),alpha)
        self.assertGreater(b_peak/a_peak,1000)
        prediction=compensate(alpha,(variance,0,variance),mode=2,allow_ggx=True)[0]
        self.assertNotAlmostEqual(a_peak,ggx_slope_density(0,prediction))
        self.assertNotAlmostEqual(b_peak,ggx_slope_density(0,prediction))
        print('samecovariance GGX peak counterexample ring / center-ring / ratio:',a_peak,b_peak,b_peak/a_peak)

    def test_finite_near_full_material_grazing_and_magnitude_fallbacks(self):
        base=.002;c=(.0001,0,.0001)
        for kwargs in ({'enabled':False},{'near':True},{'moments':False},{'mean':False},{'material':False},{'unstable':True},{'view':.15},{'light':.1},{'view':math.nan},{'q':1,'mode':2,'allow_ggx':True},{'mode':2},{'maxdelta':.001},{'maxvar':.00001},{'grazing':.8}):
            self.assertEqual((base,False),compensate(base,c,**kwargs))
        for bad in ((math.nan,0,.0001),(-.001,0,.0001),(.001,.002,.001),(.02,0,.02)):
            self.assertEqual((base,False),compensate(base,bad))
        for a in (0,1.1,math.inf):self.assertFalse(compensate(a,c)[1])
        self.assertFalse(compensate(math.nan,c)[1]) # baseline invalid is retained, not repaired

    def test_anisotropy_and_negative_variance_are_not_silently_clamped(self):
        rank_one=(.0001,.0001,.0001)
        self.assertTrue(valid_covariance(rank_one))
        self.assertEqual((.002,False),compensate(.002,rank_one))
        anti=((-1,-1),(1,1))
        full=[(x[0]-x[0],x[1]-x[1]) for x in anti]
        kept=list(anti)
        difference=tuple(x-y for x,y in zip(sample_cov(full),sample_cov(kept)))
        self.assertLess(eigenvalues(difference)[0],0)
        self.assertEqual((.002,False),compensate(.002,difference))

    def test_finite_difference_phase_moments_and_unknown_increment(self):
        for dx,dy in ((.01,.02),(.2,-.4),(math.pi,math.pi/2),(2*math.pi,0)):
            actual=[]
            for i in range(4096):
                phase=2*math.pi*i/4096
                wave=lambda x:(.5*math.sin(x)+.5)**2
                actual.append((wave(phase+dx)-wave(phase),wave(phase+dy)-wave(phase)))
            for a,b in zip(sample_cov(actual),difference_cov(dx,dy)):self.assertAlmostEqual(a,b,places=12)
        self.assertAlmostEqual(0,difference_cov(0,0)[0],places=14)
        self.assertAlmostEqual(.5,difference_cov(math.pi,0)[0],places=14)
        self.assertAlmostEqual(0,difference_cov(2*math.pi,0)[0],places=14)
        self.assertAlmostEqual(5/32*.01**2,difference_cov(.01,0)[0],places=8)


class ActualPhotonApplicabilityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        base=ROOT/photon.probe.BASE
        if hashlib.sha256(base.read_bytes()).hexdigest()!=photon.probe.BASE_SHA256:raise AssertionError('Pinned Photon changed')
        cls.files=photon.probe.archive_members(base)
        cls.rows=photon.decode_green(cls.files['shaders/image/noise.png'])

    def test_exact_water_alpha_highlight_area_light_and_mirror_ssr_source(self):
        material=self.files['shaders/include/surface/material.glsl'].decode()
        specular=self.files['shaders/include/lighting/specular_lighting.glsl'].decode()
        bsdf=self.files['shaders/include/lighting/bsdf.glsl'].decode()
        self.assertIn('    0.002,',material.split('const Material water_material = Material(',1)[1].split(');',1)[0])
        self.assertIn('float alpha_squared = material.roughness * material.roughness;',specular)
        self.assertIn('alpha_sq / (pi * sqr(1.0 - NoH_sq + NoH_sq * alpha_sq))',bsdf)
        self.assertIn('material.roughness = sqr(1.0 - specular_map.r);',material)
        self.assertIn('float NoH_squared = get_NoH_squared(NoL, NoV, LoV, light_radius);',specular)
        self.assertIn('vec3(specular_max_value)',specular)
        self.assertIn('if (!is_water) { // Rough reflection',specular)
        self.assertIn('vec3 ray_dir = reflect(world_dir, normal);',specular)
        self.assertIn('material.roughness > 0.05',specular)
        translucent=self.files['shaders/program/gbuffers_all_translucent.fsh'].decode()
        self.assertIn('material.roughness += 0.3 * texture_highlight;',translucent)
        # Raising alpha changes NDF/Smith terms but not the mirror water ray.
        ray=lambda n:(2*n[0]*n[2],2*n[1]*n[2],2*n[2]*n[2]-1)
        self.assertNotEqual(ray((0,0,1)),ray((.01,0,math.sqrt(1-.01**2))))

    def test_carrier_only_third_octave_prediction_is_anisotropic_and_large(self):
        direction=(math.cos(math.pi/6),math.sin(math.pi/6))
        angle=math.pi*(3-math.sqrt(5))
        for _ in range(2):direction=(direction[0]*math.cos(angle)+direction[1]*math.sin(angle),-direction[0]*math.sin(angle)+direction[1]*math.cos(angle))
        amplitude=1/7;frequency=.7*1.7**2;wavelength=1.5**2
        delta=tuple(-2*math.pi/wavelength*frequency*d*.1 for d in direction)
        c=tuple(x*(.04*amplitude/.1)**2 for x in difference_cov(*delta))
        self.assertGreater((eigenvalues(c)[1]-eigenvalues(c)[0])/(c[0]+c[2]),.9)
        self.assertEqual((.002,False),compensate(.002,c))
        hypothetical=compensate(.002,c,maxanisotropy=1)[0]
        self.assertGreater(hypothetical,.01)
        print('carrier-only/unvalidated third-octave covariance / anisotropy / isotropicGaussian alpha:',c,(eigenvalues(c)[1]-eigenvalues(c)[0])/(c[0]+c[2]),hypothetical)

    def test_actual_noise_local_covariance_difference_can_be_indefinite(self):
        def slopes(q,w,t):
            h0=photon.height(self.rows,q,3,w,t=t)[0]
            return tuple(.04*(photon.height(self.rows,(q[0]+(.1 if axis==0 else 0),q[1]+(.1 if axis==1 else 0)),3,w,t=t)[0]-h0)/.1 for axis in (0,1))
        negative=0;example=None
        for origin in ((x,y) for x in (7,17.3,513,-13) for y in (13,-4.7,111,-99)):
            for t in (0,17,113):
                coords=[(origin[0]+i*.125,origin[1]+j*.125) for i in range(5) for j in range(5)]
                full=sample_cov([slopes(q,[1,1,1],t) for q in coords])
                kept=sample_cov([slopes(q,[1,0,0],t) for q in coords])
                delta=tuple(a-b for a,b in zip(full,kept))
                if eigenvalues(delta)[0]<0:
                    negative+=1
                    if example is None:example=(origin,t,delta,eigenvalues(delta)[0])
                    self.assertEqual((.002,False),compensate(.002,delta,maxanisotropy=1))
        self.assertEqual(17,negative)
        print('actual pinned-noise scalar5x5 covariance delta indefinite cases:',negative,'of48; first:',example)


class StaticParserTests(unittest.TestCase):
    def test_uniform_sampler_derivative_and_public_option_free(self):
        source=HELPER.read_text()
        self.assertTrue(source.startswith('#if !defined SHADERLOD_NORMAL_VARIANCE_INCLUDED\n'))
        for forbidden in ('uniform ', 'texture(', 'textureLod(', 'textureGrad(', 'texelFetch(', 'dFdx(', 'dFdy('):self.assertNotIn(forbidden,source)
        for required in ('moments_verified', 'mean_slope_preserved', 'material_supported', 'near_or_full', 'allow_ggx_approximation', 'accepted = false;', 'return upstream_alpha;', 'result - upstream_alpha > maximum_alpha_delta'):
            self.assertIn(required,source)

    def test_actual_pinned_iris_syntax_parser_and_options(self):
        # Reuse the generic real-Iris parser harness, not its oracle or shader.
        text=inspect.getsource(error_tests.StaticSourceTests.test_pinned_iris_glsl_syntax_parser_and_option_guard)
        java=text.split("java=r'''",1)[1].split("'''",1)[0]
        java=java.replace('NormalBudgetParser','NormalVarianceParser')
        self.assertEqual(error_tests.IRIS_SHA,hashlib.sha256(error_tests.IRIS.read_bytes()).hexdigest())
        cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
        dependencies=[]
        for pattern in ('org.antlr/antlr4-runtime/4.13.1/*/antlr4-runtime-4.13.1.jar','com.google.guava/guava/32.1.2-jre/*/guava-32.1.2-jre.jar','it.unimi.dsi/fastutil/8.5.12/*/fastutil-8.5.12.jar'):
            paths=list(cache.glob(pattern));self.assertEqual(1,len(paths));dependencies.extend(paths)
        with tempfile.TemporaryDirectory(prefix='shaderlod-normal-variance-') as tmp:
            directory=Path(tmp);harness=directory/'NormalVarianceParser.java';harness.write_text(java)
            embedded=directory/'glsl-transformer.jar'
            with zipfile.ZipFile(error_tests.IRIS) as z:embedded.write_bytes(z.read('META-INF/jarjar/glsl-transformer-2.0.1.jar'))
            cp=os.pathsep.join(map(str,[error_tests.IRIS,embedded,*dependencies]))
            run=subprocess.run(['java','--class-path',cp,str(harness),str(HELPER)],capture_output=True,text=True,timeout=30)
            self.assertEqual(0,run.returncode,run.stdout+run.stderr)
            self.assertIn('syntax parse passed; no option/reference leaks',run.stdout)

if __name__=='__main__':unittest.main()

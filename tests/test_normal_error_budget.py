"""Real-arithmetic normal bounds, binary32 host fixtures, pinned-source parsing.
The new helper is unintegrated; these are not driver/quality/performance evidence.
"""
import hashlib
import math
import os
from pathlib import Path
import random
import struct
import subprocess
import tempfile
import unittest
import zipfile

import test_photon_procedural_lod as photon

ROOT=Path(__file__).resolve().parents[1]
HELPER=ROOT/'shaderpacks/common/shaders/include/shaderlod/normal_error_budget.glsl'
IRIS=ROOT/'dependencies/runtime/iris-neoforge-1.8.12+mc1.21.1.jar'
IRIS_SHA='3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83'
SAFETY=1-1/4096


def finite(x):return math.isfinite(x)

def error_bound(amplitude,h,centered,wave_lipschitz,vmax,vrange,variation_lipschitz):
    inputs=(amplitude,h,*centered,*wave_lipschitz,vmax,vrange,*variation_lipschitz)
    if not all(map(finite,inputs)) or amplitude<0 or h<=0 or centered[0]>0 or centered[1]<0 or min(wave_lipschitz)<0 or vmax<0 or vrange<0 or vrange>vmax or min(variation_lipschitz)<0:
        return (-1,-1)
    span=centered[1]-centered[0]
    center=max(-centered[0],centered[1])
    return tuple(amplitude*min(vmax*span,vmax*min(span,h*w)+center*min(vrange,h*v)) for w,v in zip(wave_lipschitz,variation_lipschitz))

def radius(bounds,influence):
    if not all(map(finite,(*bounds,influence))) or min(bounds)<0:return -1
    result=abs(influence)*math.hypot(*bounds)
    return result if finite(result) else -1

def budget(z,angle,cosine=1,grazing=.15,enabled=True,near=False,salient=False):
    if not enabled or near or salient or not all(map(finite,(z,angle,cosine,grazing))) or z<=0 or angle<=0 or angle>=3 or cosine<0 or cosine>1 or grazing<0 or grazing>.5 or cosine<=grazing:return 0
    confidence=1 if grazing==0 else photon.smooth(grazing,2*grazing,cosine)
    result=2*z*math.tan(angle/2)*confidence*SAFETY
    return result if finite(result) and result>0 else 0

def removal(requested,used,octave,b):
    if not all(map(finite,(requested,used,octave,b))) or requested<0 or requested>1 or used<0 or octave<0 or b<=0 or used>b:return 0
    if octave==0:return requested
    return min(requested,1,max(0,b-used)*SAFETY/octave)

def angle_bound(b,z):
    if not all(map(finite,(b,z))) or b<0 or z<=0:return math.pi
    return 2*math.atan(b/(2*z))

def measured_angle(v,u):
    # atan2(cross,dot) avoids catastrophic small-angle acos cancellation.
    cross=(v[1]*u[2]-v[2]*u[1],v[2]*u[0]-v[0]*u[2],v[0]*u[1]-v[1]*u[0])
    return math.atan2(math.sqrt(sum(x*x for x in cross)),sum(x*y for x,y in zip(v,u)))

def source_influence(flowing,skylight,rain,strength,view_cosine,overworld=True):
    if not all(map(finite,(skylight,rain,strength,view_cosine))) or not 0<=skylight<=1 or not 0<=rain<=1 or not 0<=strength<=2 or not 0<=view_cosine<=1:return None
    dampen=skylight*(2-skylight)
    base=(.1 if flowing else .01*(1-dampen)+(.04+.15*rain)*dampen) if overworld else .04
    return base*strength*photon.smooth(0,.15,view_cosine)

def source_certificate(joint_radius,h,provided_influence,expected_influence):
    # A pack-specific consumer must reject unverified coefficient inputs before
    # relying on the generic theorem; this validator is an oracle, not integration.
    if expected_influence is None or not finite(provided_influence) or abs(provided_influence-expected_influence)>1e-12:return math.pi
    return angle_bound(joint_radius,h)


def octave_bounds(count,persistence=.5,frequency=.7,lacunarity=1.7,variation=True,flowing=False,flow=(1,0)):
    if not 1<=count<=8 or not 0<=persistence<1:raise ValueError('Unproved Photon normalization')
    norm=(1-persistence)/(1-persistence**count)
    direction=flow if flowing else (math.cos(math.pi/6),math.sin(math.pi/6))
    amplitude=norm;wavelength=1.;noise_freq=.007
    result=[]
    for i in range(count):
        wave_lips=tuple((3*math.sqrt(3)/8)*2*math.pi/wavelength*(frequency*abs(x)+2*noise_freq*512) for x in direction)
        result.append(error_bound(amplitude,.1,(-.375,.625),wave_lips,1.5 if variation else 1,1.1 if variation else 0,(1.024,1.024) if variation else (0,0)))
        amplitude*=persistence;frequency*=lacunarity;wavelength*=1.5;noise_freq*=2.5
        if not flowing:
            a=math.pi*(3-math.sqrt(5))
            direction=(direction[0]*math.cos(a)+direction[1]*math.sin(a),-direction[0]*math.sin(a)+direction[1]*math.cos(a))
    return result


def f32(x):
    try:return struct.unpack('f',struct.pack('f',x))[0]
    except OverflowError:return math.copysign(math.inf,x)

def float32_cap(requested,used,octave,b):
    # Simulate helper subtraction/multiplication/division round points on this host.
    if octave==0:return f32(requested)
    remaining=f32(max(0,f32(f32(b)-f32(used)))*f32(SAFETY))
    return min(f32(requested),f32(1),f32(remaining/f32(octave)))


class AngularTheoremTests(unittest.TestCase):
    def test_exact_equality_case_and_scale_invariance(self):
        for h in (1e-6,.1,1,1e6):
            for r in (0,.001,.1,1,10,1000):
                b=r*h
                v=(-b/2,0,h);u=(b/2,0,h)
                self.assertAlmostEqual(angle_bound(b,h),measured_angle(v,u),places=12)
                self.assertAlmostEqual(angle_bound(b,h),angle_bound(b*7,h*7),places=12)

    def test_counterexample_grid_against_exact_bound(self):
        for h in (.01,.1,1):
            for b in (.0001,.01,.1,1,10):
                limit=angle_bound(b,h)
                for x in (-10,-1,-.5,0,.5,1,10):
                    for y in (-1,0,1):
                        for t in (0,.5,1):
                            d=(b*math.cos(t*math.pi),b*math.sin(t*math.pi))
                            v=(x*b,y*b,h);u=(v[0]+d[0],v[1]+d[1],h)
                            self.assertLessEqual(measured_angle(v,u),limit+1e-12)

    def test_finite_singular_near_disabled_full_and_salient_fallbacks(self):
        theta=math.radians(.5)
        for kw in ({'enabled':False},{'near':True},{'salient':True}):self.assertEqual(0,budget(.1,theta,**kw))
        for value in (0,-1,math.inf,math.nan):self.assertEqual(0,budget(value,theta))
        for value in (0,-1,3,math.inf,math.nan):self.assertEqual(0,budget(.1,value))
        for value in (-1,2,math.inf,math.nan):self.assertEqual(0,budget(.1,theta,cosine=value))
        for value in (-1,.6,math.inf,math.nan):self.assertEqual(0,budget(.1,theta,grazing=value))
        self.assertEqual(math.pi,angle_bound(-1,.1));self.assertEqual(math.pi,angle_bound(1,0))
        self.assertEqual(0,removal(1,0,0,0)) # no free exception to full/near fallback
        for args in ((-1,0,1,1),(2,0,1,1),(1,-1,1,1),(1,0,-1,1),(1,2,1,1),(1,0,1,math.nan)):
            self.assertEqual(0,removal(*args))

    def test_grazing_error_budget_continuity(self):
        theta=math.radians(.5)
        self.assertEqual(0,budget(.1,theta,cosine=.15))
        self.assertEqual(0,budget(.1,theta,cosine=.01))
        full=budget(.1,theta,cosine=.3)
        self.assertAlmostEqual(full,budget(.1,theta,cosine=1))
        for edge in (.15,.3):
            self.assertLess(abs(budget(.1,theta,cosine=edge-1e-6)-budget(.1,theta,cosine=edge+1e-6)),1e-11)

    def test_host_binary32_allocations_preserve_chosen_angle_limit(self):
        randomizer=random.Random(73051)
        theta=math.radians(.5)
        raw_limit=2*.1*math.tan(theta/2)
        safe=f32(budget(.1,theta))
        for _ in range(1000):
            used=f32(0)
            for _ in range(8):
                octave=f32(10**randomizer.uniform(-6,-1))
                requested=f32(randomizer.random())
                r=float32_cap(requested,used,octave,safe)
                self.assertGreaterEqual(r,0);self.assertLessEqual(r,requested)
                used=f32(used+f32(octave*r))
                self.assertLessEqual(used,raw_limit)
                self.assertLessEqual(angle_bound(used,.1),theta)


class PinnedPhotonBoundsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        base=ROOT/photon.probe.BASE
        if hashlib.sha256(base.read_bytes()).hexdigest()!=photon.probe.BASE_SHA256:raise AssertionError('Pinned Photon changed')
        cls.files=photon.probe.archive_members(base)
        cls.rows=photon.decode_green(cls.files['shaders/image/noise.png'])

    def test_actual_source_normalization_noise_variation_and_finite_difference_pins(self):
        text=self.files['shaders/include/surface/water_normal.glsl'].decode()
        for fragment in ('const float h = 0.1;', 'const float persistence = 0.5 * WATER_WAVE_PERSISTENCE;',
                         'const float min_height = 0.4;', 'const float height_variation_scale = 2.0;',
                         'const float height_variation_offset = -0.5;', 'const float noise_frequency = 0.007;',
                         'const float noise_strength = 2.0;', 'float(WATER_WAVE_ITERATIONS)'):
            self.assertIn(fragment,text)
        self.assertEqual(512,len(self.rows));self.assertEqual(512,len(self.rows[0]))
        self.assertIn(b'"blur": true',self.files['shaders/image/noise.png.mcmeta'])
        self.assertIn(b'  #define WATER_WAVE_ITERATIONS 3 ',self.files['shaders/settings.glsl'])
        self.assertIn(b'  #define WATER_WAVES_HEIGHT_VARIATION',self.files['shaders/settings.glsl'])

    def test_wave_derivative_extremum_and_independent_product_difference_bound(self):
        maximum=3*math.sqrt(3)/8
        for i in range(10001):
            phase=2*math.pi*i/10000
            value=abs((.5*math.sin(phase)+.5)*math.cos(phase))
            self.assertLessEqual(value,maximum+1e-12)
        for g0 in (0,.1,.375,.9,1):
            for g1 in (0,.1,.375,.9,1):
                for v0 in (.4,.7,1.5):
                    for v1 in (.4,.7,1.5):
                        actual=abs(v1*(g1-.375)-v0*(g0-.375))
                        coarse=1.5
                        local=1.5*abs(g1-g0)+.625*abs(v1-v0)
                        self.assertLessEqual(actual,min(coarse,local)+1e-12)

    def test_actual_noise_removed_height_and_normal_error_are_bounded(self):
        max_ratio=0
        randomizer=random.Random(541)
        for count in (1,3,8):
            for variation in (False,True):
                for flowing,flow in ((False,(1,0)),(True,(0,-1)),(True,(.6,.8))):
                    bounds=octave_bounds(count,variation=variation,flowing=flowing,flow=flow)
                    for _ in range(8):
                        coord=tuple(randomizer.uniform(-600,600) for _ in range(2))
                        w=[1]+[randomizer.random() for _ in range(count-1)]
                        aggregate=tuple(sum((1-w[i])*bounds[i][axis] for i in range(count)) for axis in (0,1))
                        kw=dict(variation=variation,flowing=flowing,flow=flow,t=randomizer.uniform(0,100))
                        full=[];cheap=[]
                        for q in (coord,(coord[0]+.1,coord[1]),(coord[0],coord[1]+.1)):
                            full.append(photon.height(self.rows,q,count,[1]*count,**kw)[0])
                            cheap.append(photon.height(self.rows,q,count,w,**kw)[0])
                        error=tuple(abs((full[j]-full[0])-(cheap[j]-cheap[0])) for j in (1,2))
                        for e,b in zip(error,aggregate):
                            self.assertLessEqual(e,b+1e-12)
                            if b>0:max_ratio=max(max_ratio,e/b)
                        for influence in (.01,.04,.1,.19,.38):
                            v=((full[1]-full[0])*influence,(full[2]-full[0])*influence,.1)
                            u=((cheap[1]-cheap[0])*influence,(cheap[2]-cheap[0])*influence,.1)
                            self.assertLessEqual(measured_angle(v,u),angle_bound(radius(aggregate,influence),.1)+1e-12)
        print('actual noise max component error/bound fixture ratio:',max_ratio)

    def test_previous_22_degree_fixture_joint_octave_certificate(self):
        # Reproduce the complete earlier fixture, not just third-octave omission.
        # h=.1, FULL-N normalization, same actual noise and height variation;
        # source normal influence is already the final rain/skylight/strength term.
        max_observed=0;max_case=None;minimum_margin=math.inf
        for n in (1,3,8):
            for coord in ((7,13),(17.3,-4.7),(513,111),(-13,-99)):
                for flowing,flow in ((False,(0,0)),(True,(1,0)),(True,(0,-1)),(True,(.6,.8))):
                    wave_bounds=octave_bounds(n,flowing=flowing,flow=flow)
                    w=photon.weights(n,(.1,0),(0,.1),48,flowing=flowing,direction=flow if flowing else (math.cos(math.pi/6),math.sin(math.pi/6)))
                    for influence in (0,.04,.1,.19,.38):
                        joint_radius=sum((1-w[i])*radius(wave_bounds[i],influence) for i in range(n))
                        certificate=angle_bound(joint_radius,.1)
                        for t in (0,17,113):
                            kw=dict(flowing=flowing,flow=flow,t=t)
                            original=photon.normal(self.rows,coord,n,[1]*n,influence=influence,**kw)
                            filtered=photon.normal(self.rows,coord,n,w,influence=influence,**kw)
                            observed=measured_angle(original,filtered)
                            self.assertLessEqual(observed,certificate+1e-12)
                            minimum_margin=min(minimum_margin,certificate-observed)
                            if observed>max_observed:
                                max_observed=observed;max_case=(n,coord,flowing,flow,influence,t,math.degrees(certificate),joint_radius)
        self.assertAlmostEqual(22.115413607216066,math.degrees(max_observed),places=10)
        # Verify both original and frozen filtered reconstruction/normalization.
        original=self.files['shaders/include/surface/water_normal.glsl'].decode()
        filtered=(ROOT/'shaderpacks/photon-procedural-lod/shaders/include/shaderlod/procedural_water.glsl').read_text()
        for source in (original,filtered):
            self.assertIn('const float h = 0.1;',source)
            self.assertIn('vec3 normal = vec3(wave1 - wave0, wave2 - wave0, h);',source)
            self.assertIn('normal.xy *= normal_influence;',source)
            self.assertIn('return normalize(normal);',source)
            self.assertIn('(1.0 - persistence)',source)
            self.assertIn('float(WATER_WAVE_ITERATIONS)',source)
        print('prior22degree fixture max observed/certified case:',math.degrees(max_observed),max_case,'minimum slack radians:',minimum_margin)

    def test_actual_source_domain_influence_and_joint_certificate(self):
        original=self.files['shaders/include/surface/water_normal.glsl'].decode()
        filtered=(ROOT/'shaderpacks/photon-procedural-lod/shaders/include/shaderlod/procedural_water.glsl').read_text()
        def region(source):
            begin=source.index('#if defined WORLD_OVERWORLD',source.index('vec3 '+('get_water_normal' if 'vec3 get_water_normal' in source else 'shaderlod_procedural_normal')))
            end=source.index('normal_influence *= WATER_WAVE_STRENGTH;',begin)+len('normal_influence *= WATER_WAVE_STRENGTH;')
            text=source[begin:end]
            import re
            return re.sub(r'\s+','',re.sub(r'//[^\n]*','',text))
        self.assertEqual(region(original),region(filtered),'Normal coefficient differs: no source certificate')
        self.assertIn('return x * (2.0 - x);',self.files['shaders/include/global.glsl'].decode())
        self.assertAlmostEqual(.2,source_influence(True,1,1,2,1))
        self.assertAlmostEqual(.38,source_influence(False,1,1,2,1))
        self.assertEqual(math.pi,source_certificate(0,.1,.38,source_influence(True,1,1,2,1)))
        self.assertEqual(math.pi,source_certificate(0,.1,.1,source_influence(True,1,1,3,1)))
        maximum=0;max_case=None;by_count={1:0,3:0,8:0};default_by_count={1:0,3:0,8:0}
        for n in (1,3,8):
            for flowing,flow in ((False,(0,0)),(True,(1,0)),(True,(0,-1)),(True,(.6,.8))):
                wave_bounds=octave_bounds(n,flowing=flowing,flow=flow)
                w=photon.weights(n,(.1,0),(0,.1),48,flowing=flowing,direction=flow if flowing else (math.cos(math.pi/6),math.sin(math.pi/6)))
                contexts={}
                for rain in (0,.5,1):
                    for sky in (0,.5,1):
                        for strength in (0,1,2):
                            for cosine in (.01,.075,.15,1):
                                for overworld in (False,True):
                                    influence=source_influence(flowing,sky,rain,strength,cosine,overworld)
                                    contexts[influence]=(sky,rain,strength,cosine,overworld)
                for coord in ((7,13),(17.3,-4.7),(513,111),(-13,-99)):
                    for t in (0,17,113):
                        kw=dict(flowing=flowing,flow=flow,t=t)
                        for influence,context in contexts.items():
                            joint_radius=sum((1-w[i])*radius(wave_bounds[i],influence) for i in range(n))
                            certificate=source_certificate(joint_radius,.1,influence,source_influence(flowing,*context))
                            actual=measured_angle(photon.normal(self.rows,coord,n,[1]*n,influence=influence,**kw),photon.normal(self.rows,coord,n,w,influence=influence,**kw))
                            self.assertLessEqual(actual,certificate+1e-12)
                            by_count[n]=max(by_count[n],math.degrees(actual))
                            if influence==source_influence(flowing,1,0,1,1):default_by_count[n]=max(default_by_count[n],math.degrees(actual))
                            if actual>maximum:maximum=actual;max_case=(n,coord,flowing,flow,influence,t,context,math.degrees(certificate))
        print('actual source-domain maximum sampled angle/case:',math.degrees(maximum),max_case,'by count:',by_count,'default strength1/rain0/sky1/cos1 bycount:',default_by_count)

    def test_invalid_singular_bound_inputs_fail_full(self):
        args=(.5,.1,(-.375,.625),(1,1),1.5,1.1,(1,1))
        for index,value in ((0,-1),(1,0),(2,(.1,.625)),(3,(-1,1)),(4,-1),(5,2),(6,(math.nan,1))):
            mutated=list(args);mutated[index]=value
            self.assertEqual((-1,-1),error_bound(*mutated))
        for p in (1,math.nan,-.1):
            with self.assertRaises(ValueError):octave_bounds(3,persistence=p)

    def test_precise_stock_three_and_eight_octave_usefulness_limit(self):
        theta=math.radians(.5)
        bounds=octave_bounds(3)
        b3=radius(bounds[2],.04)
        fraction=removal(1,0,b3,budget(.1,theta))
        self.assertAlmostEqual(6.936811545849245,math.degrees(angle_bound(b3,.1)),places=5)
        self.assertGreater(fraction,.071);self.assertLess(fraction,.072)
        self.assertLess(fraction,1) # attenuation cannot skip the expensive octave
        report=[]
        for count,influence,variation in ((3,.04,True),(8,.04,True),(8,.1,True),(8,.19,True),(8,.38,True),(8,.04,False)):
            used=0;skipped=0
            for b in reversed(octave_bounds(count,variation=variation)[1:]):
                r=removal(1,used,radius(b,influence),budget(.1,theta))
                used+=r*radius(b,influence)
                skipped+=r==1
            report.append((count,influence,variation,skipped,math.degrees(angle_bound(used,.1))))
        self.assertEqual([0,1,1,0,0,2],[row[3] for row in report])
        print('normal .5degree budget count/influence/heightvariation/fullskips/certificate:',report)


class StaticSourceTests(unittest.TestCase):
    def test_helper_is_sampler_derivative_uniform_free_with_safe_guard(self):
        source=HELPER.read_text()
        self.assertTrue(source.startswith('#if !defined SHADERLOD_NORMAL_ERROR_BUDGET_INCLUDED\n'))
        for token in ('texture(', 'textureLod(', 'textureGrad(', 'texelFetch(', 'dFdx(', 'dFdy(', 'uniform '):
            self.assertNotIn(token,source)
        for token in ('near_or_full_quality', 'unstable_or_salient', 'return vec2(-1.0)',
                      'return 0.0', 'maximum_angle_radians', 'used_radius > budget', '1.0 / 4096.0'):
            self.assertIn(token,source)

    def test_pinned_iris_glsl_syntax_parser_and_option_guard(self):
        self.assertEqual(IRIS_SHA,hashlib.sha256(IRIS.read_bytes()).hexdigest())
        cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
        antlr=list(cache.glob('org.antlr/antlr4-runtime/4.13.1/*/antlr4-runtime-4.13.1.jar'))
        self.assertEqual(1,len(antlr))
        dependencies=[]
        for pattern in ('com.google.guava/guava/32.1.2-jre/*/guava-32.1.2-jre.jar','it.unimi.dsi/fastutil/8.5.12/*/fastutil-8.5.12.jar'):
            paths=list(cache.glob(pattern));self.assertEqual(1,len(paths));dependencies.extend(paths)
        java=r'''
import java.nio.file.*;
import org.antlr.v4.runtime.*;
import io.github.douira.glsl_transformer.GLSLLexer;
import io.github.douira.glsl_transformer.GLSLParser;
import net.irisshaders.iris.shaderpack.option.*;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
class NormalBudgetParser {
    public static void main(String[] args) throws Exception {
        String source=Files.readString(Path.of(args[0]));
        var optionSource=new OptionAnnotatedSource(source);
        var refs=optionSource.getBooleanDefineReferences().keySet();
        if(!refs.isEmpty())throw new AssertionError("internal reference leak: "+refs);
        var set=optionSource.getOptionSet(AbsolutePackPath.fromAbsolutePath("/include/shaderlod/normal_error_budget.glsl"),refs);
        if(!set.getBooleanOptions().isEmpty() || !set.getStringOptions().isEmpty())throw new AssertionError("internal option leak");
        String body=source.replaceFirst("#if !defined [^\\n]+\\n#define [^\\n]+\\n","").replaceFirst("#endif\\s*$","");
        var lexer=new GLSLLexer(CharStreams.fromString("#version 410 core\n"+body));
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener(){
            public void syntaxError(Recognizer<?,?> r,Object symbol,int line,int column,String message,RecognitionException e){throw new AssertionError(line+":"+column+" "+message);}
        });
        var parser=new GLSLParser(new CommonTokenStream(lexer));
        parser.setErrorHandler(new BailErrorStrategy());
        parser.translationUnit();
        System.out.println("pinned Iris GLSL syntax parse passed; no option/reference leaks");
    }
}
'''
        with tempfile.TemporaryDirectory(prefix='shaderlod-normal-budget-') as tmp:
            directory=Path(tmp);source=directory/'NormalBudgetParser.java';source.write_text(java)
            embedded=directory/'glsl-transformer-2.0.1.jar'
            with zipfile.ZipFile(IRIS) as z:embedded.write_bytes(z.read('META-INF/jarjar/glsl-transformer-2.0.1.jar'))
            cp=os.pathsep.join(map(str,[IRIS,embedded,*antlr,*dependencies]))
            run=subprocess.run(['java','--class-path',cp,str(source),str(HELPER)],capture_output=True,text=True,timeout=30)
            self.assertEqual(0,run.returncode,run.stdout+run.stderr)
            self.assertIn('pinned Iris GLSL syntax parse passed; no option/reference leaks',run.stdout)

if __name__=='__main__':unittest.main()

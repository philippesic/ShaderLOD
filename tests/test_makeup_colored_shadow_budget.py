"""Pinned colored budget/source and pure sampler checks; no driver or visual evidence."""
import hashlib,json,math,re,shutil,subprocess,sys,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
from build_supported_pack_adapters import apply,read_archive,shader_source_digest

class MakeUpColoredShadowBudgetTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.recipe=(ROOT/'patches/adapters/makeup-ultra-fast-shaders.json').read_bytes()
        cls.manifest=json.loads(cls.recipe)
        cls.stock=read_archive(ROOT/'dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip')
        cls.generated=apply(cls.stock,cls.manifest)
        cls.shader=cls.generated['shaders/lib/shadow_frag.glsl'].decode()
    @staticmethod
    def function(source,name):
        start=source.index('vec3 '+name+'(');brace=source.index('{',start);end=brace+1;depth=1
        while depth:depth+=(source[end]=='{')-(source[end]=='}');end+=1
        return source[start:end]
    def test_literal_native_four_ray_recovery_and_private_permission_scope(self):
        original=self.function(self.stock['shaders/lib/shadow_frag.glsl'].decode(),'get_colored_shadow')
        for before,after in [('shadow2D(shadowtex0,','shaderlodMakeupShadow0('),('shadow2D(shadowtex1,','shaderlodMakeupShadow1('),('texture2D(shadowcolor0,','shaderlodMakeupShadowColor0(')]:original=original.replace(before,after)
        current=self.function(self.shader,'get_colored_shadow')
        start=current.index('            #if SHADERLOD_REFINEMENT_ADAPTER');end=current.index('            #endif\n',start)+len('            #endif\n')
        branch=current[start:end]
        self.assertEqual(current[:start]+current[end:],original)
        for guard in ('MC_VERSION == 12101','IRIS_VERSION == 10812','defined GBUFFER_TERRAIN','!defined THE_END','!defined DISTANT_HORIZONS','!defined VOXY',
                'shaderlodMakeupColoredShadowBudgetPermission == 1','shaderlodPreset == 1','isFoliage == 0.0','gl_FogFragCoord > shaderlodFar'):
            self.assertIn(guard,branch)
        self.assertNotIn('shaderlodMakeupShadowSamplerPermission',branch)
        self.assertNotIn('shaderlodOptTemporalShadowFilter',branch)
        self.assertNotIn('shaderlodOptShadowReceiverFootprint',branch)
        self.assertIn('(shadow_detector_a + shadow_detector_b) * 0.5',branch)
        self.assertIn('(shadow_color_a.rgb + shadow_color_b.rgb) * 0.5',branch)
        self.assertIn('mix(shaderlodColorMean, vec3(1.0), shaderlodDetectorMean)',branch)
        self.assertIn('if (shadow_black_a != shadow_detector_a)',branch)
        self.assertIn('if (shadow_detector_b < 1.0)',branch)
    def test_rounding_dual_means_and_refused_near_controls(self):
        def count(preset=1,permission=1,flag=True,foliage=0,distance=101,enabled=True):
            if not(enabled and permission==1 and flag and preset==1 and foliage==0 and distance>96):return 4
            return 2*(math.ceil(4*.75)//2)
        self.assertEqual(count(),2)
        for change in ({'preset':0},{'preset':2},{'preset':3},{'permission':0},{'flag':False},{'foliage':1},{'distance':96},{'distance':32},{'enabled':False}):self.assertEqual(count(**change),4)
        # Mean of per-ray mixes is a different operation and must not replace native dual means.
        detector=[0.,1.];colors=[0.,1.]
        detector_mean=sum(detector)*.5;color_mean=sum(colors)*.5
        native=color_mean*(1-detector_mean)+detector_mean
        per_ray=sum(c*(1-d)+d for c,d in zip(colors,detector))*.5
        self.assertEqual(native,.75);self.assertEqual(per_ray,.5)
        self.assertEqual(2*(math.ceil(4*.875)//2),4)
    def test_recipe_pin_native_options_and_finally_join(self):
        proof=(ROOT/'src/main/java/com/pp/shaderlod/optimization/makeupao/MakeUpAoSourceProof.java').read_text()
        self.assertIn(hashlib.sha256(self.recipe).hexdigest(),proof)
        self.assertEqual((ROOT/'src/main/resources/assets/shaderlod/adapters/patches/adapters/makeup-ultra-fast-shaders.json').read_bytes(),self.recipe)
        native=json.loads((ROOT/'src/main/resources/assets/shaderlod/makeupquad/native100.json').read_text())
        self.assertEqual(len(native),100);self.assertEqual(native['COLORED_SHADOW'],'true');self.assertEqual(native['AA_TYPE'],'2')
        runtime=(ROOT/'src/main/java/com/pp/shaderlod/optimization/makeupshadow/MakeUpShadowRuntime.java').read_text()
        self.assertIn('m.size()!=111||!"false".equals(m.get("COLORED_SHADOW"))',runtime)
        self.assertIn('m.size()==111&&"true".equals(m.get("COLORED_SHADOW"))',runtime)
        for guard in ('Iris.getShaderPackOptionQueue().isEmpty()','loadedStackMatches()','resourcesMatch(', 'MakeUpColoredShadowContract.depth(', 'MakeUpColoredShadowContract.color(', 'scoped(MakeUpShadowRuntime::draw,nativeDraw,()->revoke(p))'):
            self.assertIn(guard,runtime)
        mixin=(ROOT/'src/main/java/com/pp/shaderlod/optimization/makeupshadow/mixin/ShadowLegacyDrawMixin.java').read_text()
        self.assertIn('MakeUpReflectionRuntime.currentDraw(()->MakeUpShadowRuntime.currentDraw(',mixin)
        changed=dict(self.stock);changed['shaders/lib/shadow_frag.glsl']+=b'\n// tamper\n'
        with self.assertRaises(ValueError):apply(changed,self.manifest)
    def test_actual_pure_sampler_contract_accepts_owned_states_and_rejects_tamper(self):
        if not shutil.which('javac'):self.skipTest('JDK required for actual pure contract')
        source=ROOT/'src/main/java/com/pp/shaderlod/optimization/makeupshadow/MakeUpColoredShadowContract.java'
        java='''import com.pp.shaderlod.optimization.makeupshadow.MakeUpColoredShadowContract;class Checks{
static void check(boolean v){if(!v)throw new AssertionError();}
public static void main(String[]a)throws Exception{Class<?> c=MakeUpColoredShadowContract.Sampler.class;var ctor=c.getDeclaredConstructors()[0];
Object[] d={7,840,840,0x81A6,24,0x8C17,0x884E,0x0203,0x2601,0x2601,0,0x812F,0x812F,0f,-1000f,1000f,1f};
var valid=(MakeUpColoredShadowContract.Sampler)ctor.newInstance(d);check(MakeUpColoredShadowContract.depth(valid,7));check(!MakeUpColoredShadowContract.depth(valid,8));
Object[] wrong={8,839,839,0x8058,0,0,0,0x0201,0x2703,0x2600,1,0x2901,0x2901,.1f,1f,-1f,2f};
for(int i=0;i<d.length;i++){var x=d.clone();x[i]=wrong[i];check(!MakeUpColoredShadowContract.depth((MakeUpColoredShadowContract.Sampler)ctor.newInstance(x),7));}
var color=d.clone();color[3]=0x8058;color[4]=0;color[5]=0;color[6]=0;check(MakeUpColoredShadowContract.color((MakeUpColoredShadowContract.Sampler)ctor.newInstance(color),7));
color[6]=0x884E;check(!MakeUpColoredShadowContract.color((MakeUpColoredShadowContract.Sampler)ctor.newInstance(color),7));System.out.println("PASS owned samplers/17 single-field depth refusers/color compare refusal");}}
'''
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp);(path/'Checks.java').write_text(java)
            subprocess.run(['javac','-d',tmp,str(source),str(path/'Checks.java')],check=True,capture_output=True,text=True)
            subprocess.run(['java','-cp',tmp,'Checks'],check=True,capture_output=True,text=True)

    def test_optional_proof_reads_grant_and_finally_revoke_without_default_extra_queries(self):
        runtime=(ROOT/'src/main/java/com/pp/shaderlod/optimization/makeupshadow/MakeUpShadowRuntime.java').read_text()
        self.assertIn('COLORED_PROOF="1".equals(System.getenv("SHADERLOD_MAKEUP_COLORED_SHADOW_PROOF"))',runtime)
        self.assertIn('if(COLORED_PROOF){proofProgram=p;proofGrantCurrent=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)',runtime)
        self.assertIn('proofGrantColored=GL20.glGetUniformi(p,coloredLocation)',runtime)
        self.assertIn('proofGrantShared=l<0?-1:GL20.glGetUniformi(p,l)',runtime)
        self.assertIn('if(COLORED_PROOF&&p==proofProgram&&proofGrants>proofRevokes)',runtime)
        self.assertIn('proofRevokeColored=integer(p,"shaderlodMakeupColoredShadowBudgetPermission")',runtime)
        self.assertIn('proofRevokeShared=integer(p,"shaderlodMakeupShadowSamplerPermission")',runtime)
        self.assertIn('scoped(MakeUpShadowRuntime::draw,nativeDraw,()->revoke(p))',runtime)
        self.assertIn('if(!COLORED_PROOF&&!MakeUpColoredShadowContract.depth(state(zero),',runtime)
        self.assertIn('if(!COLORED_PROOF&&!MakeUpColoredShadowContract.depth(state(one),',runtime)
        self.assertIn('COLORED_PROOF?"; coloredProof="+controlProof():""',runtime)
        self.assertIn('-1 means inactive/missing, never measured zero',runtime)
        self.assertIn('color0 sampler refused (strict RGBA8 retained)',runtime)

if __name__=='__main__':unittest.main()

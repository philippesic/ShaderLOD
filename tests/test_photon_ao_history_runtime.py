"""Execute the real AO runtime/contract/completion against isolated scripted Iris/GL.
No Gradle, native GL, client, resources or shared build directories are used.
"""
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / 'src/main/java/com/pp/shaderlod/optimization/photonhistory'


class RuntimeOrderingTest(unittest.TestCase):
    def test_real_entry_ordering_and_cold_completion(self):
        with tempfile.TemporaryDirectory(prefix='photon-history-fixture-') as temporary:
            base = Path(temporary)
            def java(name, body):
                path = base / (name.replace('.', '/') + '.java')
                path.parent.mkdir(parents=True, exist_ok=True)
                package, _, cls = name.rpartition('.')
                path.write_text(('package ' + package + ';\n' if package else '') + body)
            for name in ('PhotonAoHistoryRuntime', 'PhotonAoHistoryContract', 'PhotonAoHistoryCompletion'):
                java('com.pp.shaderlod.optimization.photonhistory.' + name, (RUNTIME / (name + '.java')).read_text().split(';', 1)[1])
            java('com.google.gson.JsonParser', '''
public class JsonParser {
 public static JsonParser parseString(String s){return new JsonParser();}
 public JsonParser getAsJsonObject(){return this;}
 public java.util.Set<java.util.Map.Entry<String,JsonParser>> entrySet(){var m=new java.util.TreeMap<String,JsonParser>();for(String k:net.irisshaders.iris.shaderpack.option.ShaderPackOptions.values().keySet())m.put(k,new JsonParser(k));return m.entrySet();}
 private String key;public JsonParser(){}private JsonParser(String k){key=k;}
 public String getAsString(){return net.irisshaders.iris.shaderpack.option.ShaderPackOptions.values().get(key);}
}''')
            # The pinned resource is copied unchanged; the synthetic option parser supplies
            # 395 keys for source-path execution, not evidence of real profile acceptance.
            resource = base / 'assets/shaderlod/photonhistory/options394.json'
            resource.parent.mkdir(parents=True)
            resource.write_bytes((ROOT / 'src/main/resources/assets/shaderlod/photonhistory/options394.json').read_bytes())
            java('com.pp.shaderlod.LodPreset', 'public enum LodPreset { QUALITY, BALANCED }')
            java('com.pp.shaderlod.optimization.OptimizationId', 'public enum OptimizationId { AO_HISTORY_REUSE }')
            java('com.pp.shaderlod.optimization.OptimizationConfig', 'public class OptimizationConfig {public static long generation(){return 1;}}')
            java('com.pp.shaderlod.optimization.CompileOptionControl', 'public class CompileOptionControl {public static boolean overworld(Object d){return d.equals("overworld");}}')
            java('com.pp.shaderlod.optimization.OptimizationRuntimeContext', '''
public class OptimizationRuntimeContext {
 public static boolean enabled=true,request=true,quality=false,absent=false;
 public static Core core(){return absent?null:new Core();}
 public static class Core {public Core settings(){return this;}public boolean enabled(){return enabled;}public com.pp.shaderlod.LodPreset preset(){return quality?com.pp.shaderlod.LodPreset.QUALITY:com.pp.shaderlod.LodPreset.BALANCED;}public Opt optimizations(){return new Opt();}}
 public static class Opt {public boolean enabled(OptimizationId i){return request;}}
}''')
            java('com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle', 'public class MakeUpQuadLifecycle {public static boolean paused;public static boolean suspended(){return paused;}}')
            for name, method in [('photonprime.PhotonDepthPriming', 'profileMatch'), ('photon.PhotonSpatialProfiles', 'eligible'), ('photonearlyalpha.PhotonEarlyAlphaProfile', 'accepts')]:
                java('com.pp.shaderlod.optimization.' + name, 'public class ' + name.split('.')[-1] + ' {public static boolean ' + method + '(java.util.Map<String,String> m){return false;}}')
            java('com.pp.shaderlod.optimization.photonhistory.PhotonAoHistoryPins', 'public class PhotonAoHistoryPins {public static boolean api=true;public static boolean exactApi(){return api;}}')
            java('com.pp.shaderlod.packs.PackSelectionService', 'public class PackSelectionService {public static String verifyGeneratedRendererGraph(java.nio.file.Path p,java.util.Map<String,String> m){return "photon";}}')
            java('com.pp.shaderlod.packs.SupportedPackCatalog', 'public class SupportedPackCatalog {public static String shaderSourceDigest(Object m){return "ee001a1a2824475a13ca05f304b75a163de72160d6d31e47b2a97eb6b76b6e60";}}')
            java('com.pp.shaderlod.integration.PackSourceReader', 'public class PackSourceReader {public static PackSourceReader read(java.nio.file.Path p){return new PackSourceReader();}public Object memberSha256(){return null;}}')
            java('net.irisshaders.iris.shaderpack.option.ShaderPackOptions', '''
public class ShaderPackOptions {
 public static java.util.Map<String,String> values(){var m=new java.util.TreeMap<String,String>();for(int i=0;i<391;i++)m.put("k"+i,"v");m.put("SHADERLOD_PHOTON_DEPTH_PRIMING","0");m.put("SHADERLOD_PHOTON_DEAD_MIP_CHAIN","0");m.put("SHADERLOD_SHARED_SPATIAL","false");m.put("SHADERLOD_LIGHTING_RATE_PROBE","false");return m;}
 public ShaderPackOptions getOptionSet(){return this;}public java.util.Map<String,String> getBooleanOptions(){return java.util.Map.of();}public java.util.Map<String,String> getStringOptions(){return values();}public ShaderPackOptions getOptionValues(){return this;}public boolean getBooleanValueOrDefault(String k){return false;}public String getStringValueOrDefault(String k){return values().get(k);}
}''')
            java('net.irisshaders.iris.shaderpack.ShaderPack','public class ShaderPack {}')
            java('com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle', '''public class IrisSourceLifecycle {
 public interface Validation {boolean run() throws Exception;}
 public static boolean proof(Object key,Validation validation){try{return validation.run();}catch(Exception refused){return false;}}
}''')
            java('net.irisshaders.iris.Iris', '''
public class Iris {
 public static Object pipeline=new Object();public static Pack pack=new Pack();public static String dimension="overworld";public static boolean sourceTrap,fallback;public static int sourceCalls;
 public static java.util.Optional<Pack> getCurrentPack(){return java.util.Optional.of(pack);}public static boolean isFallback(){sourceCalls++;if(sourceTrap)throw new AssertionError("source entered while inactive");return fallback;}
 public static java.util.Map<String,String> getShaderPackOptionQueue(){return java.util.Map.of();}public static Iris getIrisConfig(){return new Iris();}public java.util.Optional<String> getShaderPackName(){return java.util.Optional.of("fixture");}public static java.nio.file.Path getShaderpacksDirectory(){return java.nio.file.Path.of("/fixture");}public static Iris getPipelineManager(){return new Iris();}public Object getPipelineNullable(){return pipeline;}public static Object getCurrentDimension(){return dimension;}
 public static class Pack extends net.irisshaders.iris.shaderpack.ShaderPack {public net.irisshaders.iris.shaderpack.option.ShaderPackOptions getShaderPackOptions(){return new net.irisshaders.iris.shaderpack.option.ShaderPackOptions();}}
}''')
            java('net.irisshaders.iris.platform.IrisPlatformHelpers', 'public class IrisPlatformHelpers {public static IrisPlatformHelpers getInstance(){return new IrisPlatformHelpers();}public boolean isModLoaded(String s){return false;}}')
            java('net.irisshaders.iris.gl.program.Program', 'public class Program {public int getProgramId(){return 7;}}')
            java('net.irisshaders.iris.targets.RenderTarget', 'public class RenderTarget {private int n;public RenderTarget(int n){this.n=n;}public int getMainTexture(){return n*100+1;}public int getAltTexture(){return n*100+2;}public int getWidth(){return 64;}public int getHeight(){return 64;}}')
            java('net.irisshaders.iris.targets.RenderTargets', 'public class RenderTargets {public RenderTarget get(int i){return new RenderTarget(i==6?1:2);}}')
            java('net.irisshaders.iris.shaderpack.programs.ProgramSource', '''
public class ProgramSource {public String getName(){return "deferred3";}public ProgramSource getParent(){return this;}public Object getPack(){return net.irisshaders.iris.Iris.pack;}public ProgramSource getDirectives(){return this;}public int[] getDrawBuffers(){return new int[]{6,14};}public java.util.Optional<String> getFragmentSource(){return java.util.Optional.of("uniform int shaderlodPhotonAOHistoryQualified;");}}
''')
            # Scripted GL has valid ping-pong resources; traps make resource observation
            # visible without changing or extracting the production entry method.
            java('org.lwjgl.opengl.Script', '''
public class Script {public static int frame=0,active=1000,permission=0,observations=0;public static boolean observeTrap=false,broken=false,failZero=false;public static java.util.Map<String,Integer> uniforms=new java.util.HashMap<>();public static java.util.Map<Integer,String> names=new java.util.HashMap<>();
 public static void defaults(){uniforms.clear();uniforms.put("shaderlodEnabled",1);uniforms.put("shaderlodBridgeVersion",2);uniforms.put("shaderlodOptimizationFlagsVersion",1);uniforms.put("shaderlodOptAoHistoryReuse",1);uniforms.put("shaderlodPreset",1);uniforms.put("colortex6",0);uniforms.put("colortex14",1);}
 public static int input(int n){return n*100+1+(frame%2);}public static int output(int n){return n*100+2-(frame%2);}
}''')
            sources = (RUNTIME / 'PhotonAoHistoryRuntime.java').read_text()
            constants = sorted(set(re.findall(r'(GL\d+)\.(GL_\w+)', sources)))
            identifiers = {pair: i+2000 for i, pair in enumerate(constants)}
            identifiers[('GL13','GL_TEXTURE0')] = 1000
            identifiers[('GL30','GL_COLOR_ATTACHMENT0')] = 5000
            identifiers[('GL20','GL_DRAW_BUFFER0')] = 6000
            identifiers[('GL11','GL_TRUE')] = 1
            methods = {
                'GL11': '''
public static int glGetInteger(int k){if(k==GL20.GL_CURRENT_PROGRAM)return 7;if(k==GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)return 16;if(k==GL13.GL_ACTIVE_TEXTURE)return Script.active;if(k==GL_TEXTURE_BINDING_2D)return Script.input(Script.active-1000+1);if(k==GL33.GL_SAMPLER_BINDING)return 0;if(k==GL30.GL_MAX_COLOR_ATTACHMENTS)return 8;if(k>=6000&&k<6002)return 5000+k-6000;return 0;}
public static int glGetTexParameteri(int t,int k){if(k==GL_TEXTURE_MIN_FILTER||k==GL_TEXTURE_MAG_FILTER)return 0x2601;if(k==GL_TEXTURE_WRAP_S||k==GL_TEXTURE_WRAP_T)return 0x812F;if(k>=0x8E42&&k<=0x8E45)return 0x1903+k-0x8E42;return 0;}
public static float glGetTexParameterf(int t,int k){return k==0x84FE?1:0;}
public static int glGetTexLevelParameteri(int t,int level,int k){Script.observations++;if(Script.observeTrap)throw new AssertionError("observe entered while inactive");if(Script.broken)throw new IllegalStateException("broken observation");if(level==1)return 0;if(k==GL_TEXTURE_INTERNAL_FORMAT)return Script.active==1000?0x805B:0x822F;return 64;}
public static void glGetIntegerv(int k,int[] v){v[0]=v[1]=0;v[2]=v[3]=64;}public static boolean glIsEnabled(int k){return false;}
''',
                'GL20': '''
public static int glGetUniformLocation(int p,String n){int l=n.hashCode()&0x7fffffff;Script.names.put(l,n);return l;}
public static int glGetUniformi(int p,int l){String n=Script.names.get(l);return n.equals("frameCounter")?Script.frame:Script.uniforms.getOrDefault(n,0);}
public static void glUniform1i(int l,int v){if(v==0&&Script.failZero)throw new IllegalStateException("zero write unavailable");Script.permission=v;}
''',
                'GL13': 'public static void glActiveTexture(int n){Script.active=n;}',
                'GL30': '''
public static int glGetFramebufferAttachmentParameteri(int t,int a,int k){if(k==GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)return GL11.GL_TEXTURE;if(k==GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL)return 0;return Script.output(a-5000+1);}
public static int glCheckFramebufferStatus(int t){return GL_FRAMEBUFFER_COMPLETE;}public static void glGetBooleani_v(int k,int slot,java.nio.ByteBuffer b){for(int i=0;i<4;i++)b.put(i,(byte)1);}
''',
                'GL33': 'public static int glGetSamplerParameteri(int s,int k){return GL11.glGetTexParameteri(0,k);}public static float glGetSamplerParameterf(int s,int k){return GL11.glGetTexParameterf(0,k);}',
            }
            for cls in ['GL11','GL12','GL13','GL14','GL20','GL30','GL33']:
                declarations = ''.join('public static final int '+key+'='+str(value)+';' for (owner,key),value in identifiers.items() if owner==cls)
                java('org.lwjgl.opengl.'+cls, 'public class '+cls+' {'+declarations+methods.get(cls,'')+'}')
            java('org.lwjgl.opengl.GL', 'public class GL {public boolean OpenGL33=true,GL_EXT_texture_filter_anisotropic=false,GL_ARB_texture_filter_anisotropic=false;public static GL getCapabilities(){return new GL();}}')
            java('org.lwjgl.BufferUtils', 'public class BufferUtils {public static java.nio.ByteBuffer createByteBuffer(int n){return java.nio.ByteBuffer.allocate(n);}}')
            java('Fixture', r'''
import com.pp.shaderlod.optimization.photonhistory.*;
import com.pp.shaderlod.optimization.OptimizationRuntimeContext;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle;
import net.irisshaders.iris.Iris;
import org.lwjgl.opengl.Script;
public class Fixture {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static long count(String k){return PhotonAoHistoryRuntime.costCounters().get(k);}
 static void draw(){PhotonAoHistoryRuntime.currentQuad(()->{});}
 static void cold(){PhotonAoHistoryRuntime.beforeDraw();check(Script.permission==0,"cold permission");PhotonAoHistoryRuntime.afterDraw();}
 public static void main(String[] args){
  Script.defaults();var p=new net.irisshaders.iris.gl.program.Program();var t=new net.irisshaders.iris.targets.RenderTargets();
  PhotonAoHistoryRuntime.built(Iris.pipeline,t,new net.irisshaders.iris.shaderpack.programs.ProgramSource(),p);PhotonAoHistoryRuntime.mark(Iris.pipeline,t,p);
  check(count("source_checks")==0&&count("source_nanos")==0,"initial exact zero");
  Iris.sourceTrap=true;Script.observeTrap=true;
  for(int gate=0;gate<11;gate++){
   OptimizationRuntimeContext.enabled=gate!=0;OptimizationRuntimeContext.quality=gate==1;OptimizationRuntimeContext.request=gate!=2;OptimizationRuntimeContext.absent=gate==3;MakeUpQuadLifecycle.paused=gate==4;Script.defaults();
   if(gate>=5)Script.uniforms.put(new String[]{"shaderlodEnabled","shaderlodBridgeVersion","shaderlodOptimizationFlagsVersion","shaderlodOptAoHistoryReuse","shaderlodPreset","shaderlodPreset"}[gate-5],gate==10?-1:gate==9?3:0);
   Script.permission=1;draw();check(Script.permission==0,"inactive revoke "+gate);
  }
  check(count("source_checks")==0&&count("source_nanos")==0&&Iris.sourceCalls==0&&Script.observations==0,"inactive bypass source/observe");check(count("inactive_draws")==11,"inactive count");
  OptimizationRuntimeContext.enabled=true;OptimizationRuntimeContext.quality=false;OptimizationRuntimeContext.request=true;OptimizationRuntimeContext.absent=false;MakeUpQuadLifecycle.paused=false;Script.defaults();Iris.sourceTrap=false;Script.observeTrap=false;
  Iris.fallback=true;draw();check(count("source_checks")==1&&Iris.sourceCalls==1&&Script.observations==0,"active source refusal before observation");Iris.fallback=false;
  cold();Script.frame++;PhotonAoHistoryRuntime.beforeDraw();check(Script.permission==1,"successful previous completion grants");PhotonAoHistoryRuntime.afterDraw();
  // Off must erase both a pending draw and previously successful history.
  Script.frame++;PhotonAoHistoryRuntime.beforeDraw();check(Script.permission==1,"pre-Off grant");long checks=count("source_checks"),ns=count("source_nanos");OptimizationRuntimeContext.request=false;Iris.sourceTrap=true;Script.observeTrap=true;draw();check(Script.permission==0,"Off clears pending");check(count("source_checks")==checks&&count("source_nanos")==ns,"Off counter deltas exact zero");
  OptimizationRuntimeContext.request=true;Iris.sourceTrap=false;Script.observeTrap=false;Script.frame++;cold();Script.frame++;PhotonAoHistoryRuntime.beforeDraw();check(Script.permission==1,"reactivation earns completion");PhotonAoHistoryRuntime.afterDraw();
  Script.frame++;try{PhotonAoHistoryRuntime.currentQuad(()->{throw new IllegalStateException("draw failed");});throw new AssertionError("failure swallowed");}catch(IllegalStateException expected){}check(Script.permission==0,"failed draw revoked");Script.frame++;cold();
  Script.frame++;PhotonAoHistoryRuntime.beforeDraw();check(Script.permission==1,"grant before completion failure");Script.broken=true;PhotonAoHistoryRuntime.afterDraw();check(Script.permission==0,"failed completion revoked");Script.broken=false;Script.frame++;cold();
  // A failed best-effort revoke must still erase Java history and pending state.
  Script.frame++;long revokes=count("revokes");try{PhotonAoHistoryRuntime.currentQuad(()->{Script.failZero=true;throw new IllegalStateException("draw and revoke failed");});throw new AssertionError("failure swallowed");}catch(IllegalStateException expected){}check(count("revokes")==revokes+1,"failed revoke write not counted");Script.failZero=false;Script.frame++;cold();
  // Active API mismatch retains its guard and must never reach source/observe.
  com.pp.shaderlod.optimization.photonhistory.PhotonAoHistoryPins.api=false;Iris.sourceTrap=true;Script.observeTrap=true;draw();com.pp.shaderlod.optimization.photonhistory.PhotonAoHistoryPins.api=true;Iris.sourceTrap=false;Script.observeTrap=false;Script.frame++;cold();
  // Active source refusal also clears previously completed history.
  Iris.fallback=true;Script.frame++;draw();Iris.fallback=false;Script.frame++;cold();
  check(count("source_checks")==Iris.sourceCalls,"every actual source call counted once");check(count("source_nanos")>=ns&&count("grants")>=4&&count("revokes")>=11,"cumulative costs");check(Script.active==1000,"active texture restored");
  PhotonAoHistoryRuntime.leave();PhotonAoHistoryRuntime.invalidate();System.out.println("PASS real runtime: inactive gates, source refusal, cold reactivation, failed draw/completion, counters");
 }
}''')
            sources = sorted(str(p) for p in base.rglob('*.java'))
            compiled = subprocess.run(['javac', '-d', str(base), *sources], text=True, capture_output=True, timeout=30)
            self.assertEqual(0, compiled.returncode, compiled.stdout + compiled.stderr)
            result = subprocess.run(['java', '-cp', str(base), 'Fixture'], text=True, capture_output=True, timeout=30)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn('PASS real runtime:', result.stdout)
            print(result.stdout.strip())


if __name__ == '__main__':
    unittest.main()

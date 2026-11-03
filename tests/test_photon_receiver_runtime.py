"""Exercise the real receiver and pinned Iris Program lifetime with a counting fake GL.

No game, driver, Gradle or network. The fake services are deliberately local to
this regression; source/sampler admission remains in the production method.
"""
import hashlib
import datetime
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = 'src/main/java/com/pp/shaderlod/optimization/photonreceiver/PhotonReceiverRuntime.java'
IRIS = ROOT / 'dependencies/runtime/iris-neoforge-1.8.12+mc1.21.1.jar'
FIXTURE = ROOT / 'tests/fixtures/java/PhotonReceiverRuntimeFixture.java'

# These services supply controlled caller inputs. Program and GlResource below
# are extracted byte-for-byte from the locally pinned Iris dependency, not faked.
STUBS = {
    # These adapters keep this fixture focused on receiver ordering/lifetime;
    # source lifecycle and captured-options admission are tested independently.
    'com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle': '''public class IrisSourceLifecycle {
        public static boolean loading(){return false;}
        public interface Validation {boolean run() throws Exception;}
        public static boolean proof(Object key,Validation validation)throws Exception{return validation.run();}
    }''',
    'com.pp.shaderlod.optimization.sourcecache.EffectiveOptions': '''public class EffectiveOptions {
        public static java.util.Map<String,String> current(net.irisshaders.iris.shaderpack.ShaderPack.Options o){
            return java.util.Map.of();
        }
    }''',
    'com.pp.shaderlod.optimization.OptimizationId': 'public enum OptimizationId { SHADOW_RECEIVER_FOOTPRINT }',
    'com.pp.shaderlod.optimization.OptimizationConfig': '''public class OptimizationConfig {
        public static boolean requested=true; public static boolean requested(OptimizationId id){return requested;}
    }''',
    'com.pp.shaderlod.optimization.OptimizationRuntimeContext': '''public class OptimizationRuntimeContext {
        public static Core current=new Core(); public static Core core(){return current;}
        public static class Core {
            public boolean enabled=true, requested=true;
            public com.pp.shaderlod.LodPreset preset=com.pp.shaderlod.LodPreset.BALANCED;
            public Core settings(){return this;} public boolean enabled(){return enabled;}
            public Core optimizations(){return this;} public boolean enabled(OptimizationId id){return requested;}
            public com.pp.shaderlod.LodPreset preset(){return preset;}
        }
    }''',
    'com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle': '''public class MakeUpQuadLifecycle {
        public static boolean suspended; public static boolean suspended(){return suspended;}
    }''',
    'com.pp.shaderlod.optimization.photonreceiver.PhotonReceiverApi': '''public class PhotonReceiverApi {
        public static boolean exact=true; public static boolean exact(){return exact;}
    }''',
    'com.pp.shaderlod.optimization.photonprime.PhotonDepthPriming': '''public class PhotonDepthPriming {
        public static boolean profile=true; public static boolean profileMatch(java.util.Map<String,String> m){return profile;}
    }''',
    'com.pp.shaderlod.optimization.photon.PhotonSpatialProfiles': '''public class PhotonSpatialProfiles {
        public static boolean eligible(java.util.Map<String,String> m){return false;}
    }''',
    'com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache': '''public class EarlyAlphaSourceCache {
        public static boolean allowed=true, fail; public static int checks;
        public interface Validation {boolean run() throws Exception;}
        public void invalidate(){}
        public boolean check(Class<?> owner,java.nio.file.Path dir,String name,String family,
                java.util.Map<String,String> values,Object pack,Object options,Object set,Object pipeline,
                Object level,long epoch,Object context,java.util.Collection<String> resources,Validation check)throws Exception {
            checks++;if(fail)throw new java.io.IOException("source failure");return allowed&&check.run();
        }
    }''',
    'com.pp.shaderlod.integration.PackSourceReader': '''public class PackSourceReader {
        public record Source(java.util.Map<String,String> memberSha256){}
        public static Source read(java.nio.file.Path p){return new Source(java.util.Map.of());}
    }''',
    'com.pp.shaderlod.packs.SupportedPackCatalog': '''public class SupportedPackCatalog {
        public static String shaderSourceDigest(java.util.Map<String,String> m){return "ee001a1a2824475a13ca05f304b75a163de72160d6d31e47b2a97eb6b76b6e60";}
    }''',
    'com.pp.shaderlod.packs.PackSelectionService': '''public class PackSelectionService {
        public static String verifyGeneratedRendererGraph(java.nio.file.Path p,java.util.Map<String,String> m){return "photon";}
    }''',
    'com.mojang.blaze3d.systems.RenderSystem': '''public class RenderSystem {
        public static boolean renderThread=true; public static boolean isOnRenderThread(){return renderThread;}
    }''',
    'com.mojang.blaze3d.platform.GlStateManager': '''public class GlStateManager {
        public static void glDeleteProgram(int p){fixture.PhotonReceiverRuntimeFixture.Driver.deleted(p);}
    }''',
    'com.mojang.blaze3d.shaders.ProgramManager': '''public class ProgramManager {
        public static void glUseProgram(int p){fixture.PhotonReceiverRuntimeFixture.Driver.current=p;}
    }''',
    'net.irisshaders.iris.gl.IrisRenderSystem': 'public class IrisRenderSystem {public static void memoryBarrier(int mask){}}',
    'net.irisshaders.iris.gl.program.ProgramUniforms': 'public class ProgramUniforms {public void update(){} public static void clearActiveUniforms(){}}',
    'net.irisshaders.iris.gl.program.ProgramSamplers': 'public class ProgramSamplers {public void update(){} public static void clearActiveSamplers(){}}',
    'net.irisshaders.iris.gl.program.ProgramImages': 'public class ProgramImages {public void update(){} public int getActiveImages(){return 0;}}',
    'net.irisshaders.iris.Iris': '''public class Iris {
        public static net.irisshaders.iris.shaderpack.ShaderPack pack=new net.irisshaders.iris.shaderpack.ShaderPack();
        public static Object pipeline; public static boolean fallback;
        public static java.util.Map<String,String> queue=new java.util.HashMap<>();
        public static java.util.Optional<net.irisshaders.iris.shaderpack.ShaderPack> getCurrentPack(){return java.util.Optional.ofNullable(pack);}
        public static java.util.Map<String,String> getShaderPackOptionQueue(){return queue;}
        public static boolean isFallback(){return fallback;}
        public static Iris getIrisConfig(){return new Iris();}
        public java.util.Optional<String> getShaderPackName(){return java.util.Optional.of("Photon.zip");}
        public static java.nio.file.Path getShaderpacksDirectory(){return java.nio.file.Path.of("fixture-shaderpacks");}
        public static Iris getPipelineManager(){return new Iris();}public Object getPipelineNullable(){return pipeline;}
    }''',
    'net.irisshaders.iris.shaderpack.ShaderPack': '''public class ShaderPack {
        public Options getShaderPackOptions(){return new Options();}
        public static class Options {
            public Options getOptionSet(){return this;}public Options getOptionValues(){return this;}
            public java.util.Map<String,String> getBooleanOptions(){return java.util.Map.of();}
            public java.util.Map<String,String> getStringOptions(){return java.util.Map.of();}
            public boolean getBooleanValueOrDefault(String k){return false;}public String getStringValueOrDefault(String k){return "";}
        }
    }''',
    'net.irisshaders.iris.shaderpack.programs.ProgramSource': '''public class ProgramSource {
        public final String name;public ProgramSource(String name){this.name=name;}
        public String getName(){return name;}public ProgramSource getParent(){return this;}
        public net.irisshaders.iris.shaderpack.ShaderPack getPack(){return net.irisshaders.iris.Iris.pack;}
        public java.util.Optional<String> getFragmentSource(){return java.util.Optional.of("uniform int shaderlodPhotonReceiverQualified;");}
    }''',
    'net.irisshaders.iris.targets.RenderTargets': '''public class RenderTargets {
        public int getCurrentWidth(){return 1280;}public int getCurrentHeight(){return 720;}
    }''',
    'net.irisshaders.iris.shadows.ShadowRenderTargets': '''public class ShadowRenderTargets {
        public ShadowRenderTargets getDepthTextureNoTranslucents(){return this;}
        public int getTextureId(){return 42;}public int getResolution(){return 2048;}
    }''',
    'com.pp.shaderlod.optimization.makeupshadow.mixin.ShadowPipelineView': '''public interface ShadowPipelineView {
        net.irisshaders.iris.targets.RenderTargets shadowlod$targets();
        net.irisshaders.iris.shadows.ShadowRenderTargets shadowlod$shadows();
    }''',
    'net.irisshaders.iris.pipeline.IrisRenderingPipeline': '''public class IrisRenderingPipeline implements com.pp.shaderlod.optimization.makeupshadow.mixin.ShadowPipelineView {
        public final net.irisshaders.iris.targets.RenderTargets targets=new net.irisshaders.iris.targets.RenderTargets();
        public net.irisshaders.iris.targets.RenderTargets shadowlod$targets(){return targets;}
        public net.irisshaders.iris.shadows.ShadowRenderTargets shadowlod$shadows(){return new net.irisshaders.iris.shadows.ShadowRenderTargets();}
    }''',
    'net.irisshaders.iris.shadows.ShadowRenderingState': '''public class ShadowRenderingState {
        public static boolean shadows;public static boolean areShadowsCurrentlyBeingRendered(){return shadows;}
    }''',
    'net.irisshaders.iris.platform.IrisPlatformHelpers': '''public class IrisPlatformHelpers {
        public static String loaded="";public static IrisPlatformHelpers getInstance(){return new IrisPlatformHelpers();}
        public boolean isModLoaded(String m){return loaded.equals(m);}
    }''',
    'net.minecraft.world.level.Level': '''public class Level {
        public static final Object OVERWORLD=new Object();public Object dimension=OVERWORLD;public Object dimension(){return dimension;}
    }''',
    'net.minecraft.server.packs.PackResources': 'public interface PackResources {String packId();}',
    'net.minecraft.client.Minecraft': '''public class Minecraft {
        private static final Minecraft INSTANCE=new Minecraft();public net.minecraft.world.level.Level level=new net.minecraft.world.level.Level();
        public static Minecraft getInstance(){return INSTANCE;}public Minecraft getResourceManager(){return this;}
        public java.util.stream.Stream<net.minecraft.server.packs.PackResources> listPacks(){return java.util.stream.Stream.empty();}
    }''',
    'org.lwjgl.opengl.GLCapabilities': '''public class GLCapabilities {
        public boolean OpenGL41=true,GL_EXT_texture_filter_anisotropic=false,GL_ARB_texture_filter_anisotropic=false;
    }''',
    'org.lwjgl.opengl.GL': '''public class GL {
        public static GLCapabilities context=new GLCapabilities();public static GLCapabilities getCapabilities(){return context;}
    }''',
    'org.lwjgl.opengl.GL11': '''public class GL11 {
        public static final int GL_TRUE=1,GL_TEXTURE_2D=0x0DE1,GL_TEXTURE_BINDING_2D=0x8069,
            GL_TEXTURE_WIDTH=0x1000,GL_TEXTURE_HEIGHT=0x1001,GL_TEXTURE_INTERNAL_FORMAT=0x1003,
            GL_TEXTURE_MIN_FILTER=0x2801,GL_TEXTURE_MAG_FILTER=0x2800,GL_TEXTURE_WRAP_S=0x2802,GL_TEXTURE_WRAP_T=0x2803,GL_VIEWPORT=0x0BA2;
        public static int glGetInteger(int n){return fixture.PhotonReceiverRuntimeFixture.Driver.integer(n);}
        public static void glGetIntegerv(int n,int[] a){fixture.PhotonReceiverRuntimeFixture.Driver.calls++;System.arraycopy(fixture.PhotonReceiverRuntimeFixture.Driver.viewport,0,a,0,4);}
        public static int glGetTexParameteri(int t,int n){return fixture.PhotonReceiverRuntimeFixture.Driver.parameter(n);}
        public static float glGetTexParameterf(int t,int n){return fixture.PhotonReceiverRuntimeFixture.Driver.number(n);}
        public static int glGetTexLevelParameteri(int t,int l,int n){return fixture.PhotonReceiverRuntimeFixture.Driver.level(n);}
    }''',
    'org.lwjgl.opengl.GL12': 'public class GL12 {public static final int GL_TEXTURE_BASE_LEVEL=0x813C,GL_TEXTURE_MIN_LOD=0x813A,GL_TEXTURE_MAX_LOD=0x813B;}',
    'org.lwjgl.opengl.GL13': '''public class GL13 {
        public static final int GL_ACTIVE_TEXTURE=0x84E0,GL_TEXTURE0=0x84C0;
        public static void glActiveTexture(int t){fixture.PhotonReceiverRuntimeFixture.Driver.calls++;fixture.PhotonReceiverRuntimeFixture.Driver.active=t;}
    }''',
    'org.lwjgl.opengl.GL14': 'public class GL14 {public static final int GL_TEXTURE_LOD_BIAS=0x8501;}',
    'org.lwjgl.opengl.GL20': '''public class GL20 {
        public static final int GL_CURRENT_PROGRAM=0x8B8D,GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS=0x8B4D,GL_LINK_STATUS=0x8B82;
        public static int glGetUniformLocation(int p,String n){return fixture.PhotonReceiverRuntimeFixture.Driver.location(p,n);}
        public static int glGetUniformi(int p,int l){return fixture.PhotonReceiverRuntimeFixture.Driver.uniform(p,l);}
        public static void glUniform1i(int l,int v){fixture.PhotonReceiverRuntimeFixture.Driver.write(fixture.PhotonReceiverRuntimeFixture.Driver.current,l,v);}
        public static int glGetProgrami(int p,int n){fixture.PhotonReceiverRuntimeFixture.Driver.calls++;return fixture.PhotonReceiverRuntimeFixture.Driver.linked?1:0;}
    }''',
    'org.lwjgl.opengl.GL30': '''public class GL30 {
        public static final int GL_DRAW_FRAMEBUFFER=0x8CA9,GL_FRAMEBUFFER_COMPLETE=0x8CD5;
        public static int glCheckFramebufferStatus(int f){fixture.PhotonReceiverRuntimeFixture.Driver.calls++;return fixture.PhotonReceiverRuntimeFixture.Driver.framebuffer?GL_FRAMEBUFFER_COMPLETE:0;}
    }''',
    'org.lwjgl.opengl.GL33': '''public class GL33 {
        public static final int GL_SAMPLER_BINDING=0x8919;
        public static int glGetSamplerParameteri(int s,int n){return fixture.PhotonReceiverRuntimeFixture.Driver.parameter(n);}
        public static float glGetSamplerParameterf(int s,int n){return fixture.PhotonReceiverRuntimeFixture.Driver.number(n);}
    }''',
    'org.lwjgl.opengl.GL41': 'public class GL41 {public static void glProgramUniform1i(int p,int l,int v){fixture.PhotonReceiverRuntimeFixture.Driver.write(p,l,v);}}',
}


class PhotonReceiverRuntimeTest(unittest.TestCase):
    def test_actual_runtime_and_iris_program_lifetime(self):
        def run(command, timeout):
            process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            evidence = os.environ.get('PHOTON_RECEIVER_FIXTURE_EVIDENCE')
            identity = subprocess.run(['ps', '-p', str(process.pid), '-o', 'pid=,ppid=,lstart=,command='],
                                      capture_output=True, text=True).stdout if evidence else ''
            try:
                stdout, stderr = process.communicate(timeout=timeout)
            except subprocess.TimeoutExpired:
                process.kill(); process.communicate(); raise
            finally:
                if evidence:
                    with Path(evidence).open('a') as output:
                        output.write(json.dumps({'pid': process.pid, 'identity': identity, 'command': command,
                                                 'exit_code': process.poll(), 'terminal': process.poll() is not None,
                                                 'ended_utc': datetime.datetime.now(datetime.timezone.utc).isoformat()}) + '\n')
            return subprocess.CompletedProcess(command, process.returncode, stdout, stderr)

        if not IRIS.is_file():
            self.skipTest('Local pinned NF 1.21.1 Iris dependency unavailable')
        java_home = os.environ.get('JAVA_HOME', '/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home')
        javac = shutil.which(str(Path(java_home) / 'bin/javac')) or shutil.which('javac')
        java = shutil.which(str(Path(java_home) / 'bin/java')) or shutil.which('java')
        self.assertTrue(javac and java, 'Java 21 required')
        with tempfile.TemporaryDirectory(prefix='photon-receiver-', dir=os.environ.get('PHOTON_RECEIVER_FIXTURE_TMP')) as temporary:
            work = Path(temporary); classes = work / 'classes'; classes.mkdir()
            api = json.loads((ROOT / 'src/main/resources/assets/shaderlod/photonhistory/api.json').read_text())
            pin = next(row for row in api if row['tuple'] == 'neoforge1.21.1')['classes']
            with zipfile.ZipFile(IRIS) as archive:
                for name in ['net/irisshaders/iris/gl/program/Program.class', 'net/irisshaders/iris/gl/GlResource.class']:
                    raw = archive.read(name)
                    if name in pin:
                        self.assertEqual(pin[name], hashlib.sha256(raw).hexdigest())
                    target = classes / name; target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(raw)
            sources = []
            for name, body in STUBS.items():
                package, _, _ = name.rpartition('.')
                path = work / (name.replace('.', '/') + '.java'); path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('package ' + package + ';\n' + body + '\n'); sources.append(path)
            sources.append(FIXTURE)
            runtime = Path(os.environ.get('PHOTON_RECEIVER_RUNTIME_SOURCE', str(ROOT / RUNTIME)))
            sources += [runtime, ROOT / 'src/main/java/com/pp/shaderlod/LodPreset.java',
                        ROOT / 'src/main/java/com/pp/shaderlod/optimization/photonreceiver/PhotonReceiverContract.java',
                        ROOT / 'src/main/java/com/pp/shaderlod/optimization/bsl/BslReceiverContract.java']
            compile_result = run([javac, '-proc:none', '-cp', str(classes), '-d', str(classes), *map(str, sources)], 45)
            self.assertEqual(0, compile_result.returncode, compile_result.stdout + compile_result.stderr)
            mode = os.environ.get('PHOTON_RECEIVER_FIXTURE_MODE', 'new')
            result = run([java, '-ea', '-cp', str(classes), 'fixture.PhotonReceiverRuntimeFixture', mode], 20)
            print(result.stdout, end='')
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == '__main__':
    unittest.main()

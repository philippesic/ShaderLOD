package com.pp.shaderlod.optimization;

import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.sildurpixel.SildurPixelContract;
import com.pp.shaderlod.optimization.sildurpixel.SildurPixelSourceProof;
import com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache;
import com.pp.shaderlod.packs.PackSelectionService;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual shared source producer/proof; loader-specific pixel admission is checked separately, never supplied. */
class SildurPixelSourceCacheTest {
    @TempDir Path directory;
    private final EarlyAlphaSourceCache cache=new EarlyAlphaSourceCache();
    private final Object pack=new Object(),values=new Object(),declared=new Object();
    private Object pipeline=new Object();
    private Object world=new Object();
    private static Map<String,String> pixelOptions(){
        // The full source declaration map is shared. The dedicated runtime pixel profile is NF-only,
        // so using Contract.generated here incorrectly made the Fabric source-proof fixture empty.
        try(var input=SildurPixelSourceCacheTest.class.getResourceAsStream("/assets/shaderlod/rendererquad/generated-main-options.json")) {
            assertNotNull(input);var actual=new TreeMap<String,String>();
            com.google.gson.JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject(SildurPixelContract.ID).entrySet().forEach(e->actual.put(e.getKey(),e.getValue().getAsString()));
            assertEquals(com.pp.shaderlod.integration.sildurquad.SildurSourceCertificate.generatedOptions(),actual,
                "Fixture must be the actual shared source declaration contract");
            assertEquals("0",actual.put(SildurPixelContract.OPTION,"1"),"Select its already-declared binary graph control");
            assertTrue(com.pp.shaderlod.integration.sildurquad.SildurSourceCertificate.generatedOptionsMatch(actual));
            return Map.copyOf(actual);
        }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
    private Map<String,String> options=pixelOptions();
    private String selected;
    private int calls;
    private Class<?> resourceOwner=SildurPixelSourceCacheTest.class;
    private boolean check(long sourceGeneration)throws Exception {return check(cache,sourceGeneration);}
    private boolean check(EarlyAlphaSourceCache ownedCache,long sourceGeneration)throws Exception {
        return LoadSourceValidationFixture.check(ownedCache,resourceOwner,directory,selected,"sildurs-vibrant-shaders",options,
            pack,values,declared,pipeline,world,sourceGeneration,List.of("unchanged-core",Map.of()),List.of("vanilla","mod_resources"),()->{
                calls++;
                return SildurPixelSourceProof.verify(directory.resolve(selected),options,
                    ()->resourceOwner.getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/sildurs-vibrant-shaders.json"));
            });
    }
    private boolean refused() {
        try{return !check(1);}catch(Exception expected){return true;}
    }
    private Object parsedPlan()throws Exception {
        var field=EarlyAlphaSourceCache.class.getDeclaredField("helperPlan");field.setAccessible(true);return field.get(cache);
    }
    private static final class MutableResources extends ClassLoader {
        final Map<String,byte[]> replacements=new HashMap<>();
        MutableResources(){super(SildurPixelSourceCacheTest.class.getClassLoader());}
        Class<?> owner()throws Exception {
            String name=SildurPixelCacheResourceOwner.class.getName();
            try(var in=getParent().getResourceAsStream(name.replace('.','/')+".class")) {
                assertNotNull(in);byte[] bytes=in.readAllBytes();return defineClass(name,bytes,0,bytes.length);
            }
        }
        @Override public java.io.InputStream getResourceAsStream(String name) {
            byte[] bytes=replacements.get(name);
            return bytes==null?super.getResourceAsStream(name):new java.io.ByteArrayInputStream(bytes);
        }
    }
    @Test void mutableRecipeAndPinnedHelperBytesCannotReuseAValidPixelCertificate()throws Exception {
        var prior=OptimizationConfig.state();
        try {
            OptimizationConfig.restoreInMemory(OptimizationSnapshot.noneRequested().with(OptimizationId.BINDING_CERTIFICATE_CACHE,true));
            Files.copy(Path.of("dependencies/upstream/pack-expansion/Sildur's Vibrant Shaders v2.02 Extreme.zip"),directory.resolve("Original.zip"));
            var nativeOptions=new TreeMap<>(com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility.nativeOptions());
            assertEquals(115,nativeOptions.size());assertEquals(131,options.size());
            var result=new PackSelectionService().apply(directory,"Original.zip",true,LodPreset.PERFORMANCE,nativeOptions,false);
            assertTrue(result.accepted(),result.detail());selected=result.packFile();
            var resources=new MutableResources();resourceOwner=resources.owner();
            assertTrue(check(1));Object originalPlan=parsedPlan();assertNotNull(originalPlan);
            assertTrue(check(1));assertEquals(1,calls);assertSame(originalPlan,parsedPlan(),"Unchanged fresh recipe digest reuses its parsed plan");
            var helpers=originalPlan.getClass().getDeclaredMethod("helpers");helpers.setAccessible(true);
            assertThrows(UnsupportedOperationException.class,()->((List<?>)helpers.invoke(originalPlan)).clear(),"No mutable Gson plan may escape");
            String recipe="assets/shaderlod/adapters/patches/adapters/sildurs-vibrant-shaders.json";
            byte[] recipeBytes;
            try(var in=resources.getResourceAsStream(recipe)){assertNotNull(in);recipeBytes=in.readAllBytes();}
            var parsed=com.google.gson.JsonParser.parseString(new String(recipeBytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            String helper="assets/shaderlod/adapters/"+parsed.getAsJsonObject("additions").entrySet().iterator().next()
                .getValue().getAsJsonObject().get("path").getAsString();
            byte[] helperBytes;
            try(var in=resources.getResourceAsStream(helper)){assertNotNull(in);helperBytes=in.readAllBytes();}
            var badHelper=helperBytes.clone();badHelper[badHelper.length/2]^=1;resources.replacements.put(helper,badHelper);
            assertTrue(refused(),"Pinned helper mutation must invalidate before any cached success");
            assertEquals(1,calls,"Helper hash refusal occurs before source callback replay");
            resources.replacements.clear();assertTrue(check(1));assertEquals(2,calls);
            // Whitespace remains valid JSON and leaves all helper/source pins unchanged,
            // but changes the complete recipe byte identity verified by the pixel source callback.
            String text=new String(recipeBytes,java.nio.charset.StandardCharsets.UTF_8);assertTrue(text.contains("\n"));
            byte[] badRecipe=text.replaceFirst("\n"," ").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(recipeBytes.length,badRecipe.length);resources.replacements.put(recipe,badRecipe);
            assertTrue(refused(),"Recipe bytes cannot reuse the previous certificate");
            assertEquals(3,calls,"Changed recipe must reach its unchanged exact SHA proof");
            assertNotSame(originalPlan,parsedPlan(),"Even semantically identical changed bytes require a new parse");
            resources.replacements.clear();assertTrue(check(1));assertEquals(4,calls);
            byte[] malformed=recipeBytes.clone();malformed[0]='!';resources.replacements.put(recipe,malformed);
            assertTrue(refused(),"Malformed fresh recipe must not reuse the prior plan");assertNull(parsedPlan());
            assertEquals(4,calls,"Malformed recipe refusal precedes certificate validation");
            resources.replacements.clear();assertTrue(check(1));Object restoredPlan=parsedPlan();
            int replayBefore=calls;var otherResources=new MutableResources();resourceOwner=otherResources.owner();
            assertTrue(check(1));assertEquals(replayBefore+1,calls);assertNotSame(restoredPlan,parsedPlan(),"Actual resource owner/loader change reparses");
            var changedOwnerPlan=parsedPlan();
            var fresh=EarlyAlphaSourceCache.fingerprint(resourceOwner,directory,selected,"sildurs-vibrant-shaders");
            assertSame(changedOwnerPlan,parsedPlan(),"Public fresh fingerprint does not consume or mutate instance parse memo");
            OptimizationConfig.restoreInMemory(OptimizationSnapshot.noneRequested());
            replayBefore=calls;assertTrue(check(1));assertNull(parsedPlan(),"Cache Off does not retain a plan");
            assertTrue(check(1));assertEquals(replayBefore+2,calls);
            assertEquals(fresh,EarlyAlphaSourceCache.fingerprint(resourceOwner,directory,selected,"sildurs-vibrant-shaders"),"Parse reuse must preserve the complete fingerprint value/order");
        } finally {cache.invalidate();OptimizationConfig.restoreState(prior);}
    }
    @Test void normalPixelSourceMemoSkipsReplayButSameStatMutationsAndOwnersRevalidate()throws Exception {
        var prior=OptimizationConfig.state();
        try {
            OptimizationConfig.restoreInMemory(OptimizationSnapshot.noneRequested().with(OptimizationId.BINDING_CERTIFICATE_CACHE,true));
            Files.copy(Path.of("dependencies/upstream/pack-expansion/Sildur's Vibrant Shaders v2.02 Extreme.zip"),directory.resolve("Original.zip"));
            var nativeOptions=new TreeMap<>(com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility.nativeOptions());
            assertEquals(115,nativeOptions.size());assertEquals(131,options.size());
            var result=new PackSelectionService().apply(directory,"Original.zip",true,LodPreset.PERFORMANCE,nativeOptions,false);
            assertTrue(result.accepted(),result.detail());assertTrue(result.generatedArchive());selected=result.packFile();
            assertTrue(check(1));assertEquals(1,calls);
            assertTrue(check(1));assertEquals(1,calls,"Unchanged subsequent frame skips full source replay");
            var secondConsumer=new EarlyAlphaSourceCache();assertTrue(check(secondConsumer,1));assertEquals(2,calls,"Independent world/post certificate starts with its own full verifier");
            assertTrue(check(secondConsumer,1));assertTrue(check(1));assertEquals(2,calls,"Independent consumer hits do not evict each other or replay on an ordinary next frame");secondConsumer.invalidate();

            world=new Object();assertTrue(check(1));assertEquals(3,calls);
            assertTrue(check(2));assertEquals(4,calls,"Source generation is a cache boundary");
            pipeline=new Object();assertTrue(check(2));assertEquals(5,calls,"Actual pipeline owner is a cache boundary");
            options=new TreeMap<>(options);options.put(SildurPixelContract.OPTION,"0");
            assertTrue(check(2));assertEquals(6,calls,"Actual pixel graph1 to graph0 map is part of the key");
            options=pixelOptions();assertTrue(check(1));
            String state;
            try(var zip=new ZipFile(directory.resolve(selected).toFile());var in=zip.getInputStream(zip.getEntry("shaderlod-selection.json"))) {
                state=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().get("stateFile").getAsString();
            }
            for(String name:List.of("Original.zip",selected,state)) {
                var file=directory.resolve(name);byte[] before=Files.readAllBytes(file);var modified=Files.getLastModifiedTime(file);
                byte[] changed=before.clone();
                if(name.equals(state)) {
                    String text=new String(before,java.nio.charset.StandardCharsets.UTF_8);
                    int key=text.indexOf("\"originalSha256\"");assertTrue(key>=0);
                    int first=text.indexOf('"',text.indexOf(':',key))+1;
                    changed[first]=(byte)(changed[first]=='a'?'b':'a');
                } else changed[changed.length/2]^=1;
                Files.write(file,changed);Files.setLastModifiedTime(file,modified);
                assertEquals(before.length,Files.size(file));assertEquals(modified,Files.getLastModifiedTime(file));
                try {assertTrue(refused(),"Same-stat content mutation must refuse: "+name);}
                finally {Files.write(file,before);Files.setLastModifiedTime(file,modified);}
                assertTrue(check(1),"Restored original bytes can requalify");
            }
            OptimizationConfig.restoreInMemory(OptimizationSnapshot.noneRequested());
            int before=calls;assertTrue(check(1));assertTrue(check(1));assertEquals(before+2,calls,"Cache Off always invokes the pixel proof");
        } finally {cache.invalidate();OptimizationConfig.restoreState(prior);}
    }
    private static Set<String> calls(String className,String method)throws Exception {
        var result=new HashSet<String>();try(var in=SildurPixelSourceCacheTest.class.getClassLoader().getResourceAsStream(className+".class")) {
            assertNotNull(in);new org.objectweb.asm.ClassReader(in.readAllBytes()).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9){
                @Override public org.objectweb.asm.MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                    if(!name.equals(method))return null;
                    return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9){@Override public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean itf){result.add(owner+"."+name);}};
                }
            },org.objectweb.asm.ClassReader.SKIP_DEBUG|org.objectweb.asm.ClassReader.SKIP_FRAMES);
        }return result;
    }
    @Test void sharedSourceFixtureDoesNotAdmitAnUnavailableLoaderPixelProfile()throws Exception {
        var actual=pixelOptions();
        try(var profile=SildurPixelContract.class.getResourceAsStream("/assets/shaderlod/sildurpixel/options131.json")) {
            if(profile!=null) {
                assertEquals(actual,SildurPixelContract.generated("1"),"Shipped runtime profile agrees with shared source input");
                assertTrue(SildurPixelContract.options(actual));
            } else {
                // Concrete unported Fabric runtime boundary: these tests still exercise the real shared
                // source verifier, without inventing the missing native-AA/GL/layout admission profile.
                assertTrue(SildurPixelContract.generated("1").isEmpty());
                assertFalse(SildurPixelContract.options(actual),"Absent loader pixel profile must remain unadmitted");
                try(var metadata=getClass().getResourceAsStream("/fabric.mod.json")) {
                    assertNotNull(metadata,"Missing runtime profile must correspond to the actual Fabric package");
                    var mod=com.google.gson.JsonParser.parseString(new String(metadata.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    assertEquals("shaderlod",mod.get("id").getAsString());
                    for(var mixin:mod.getAsJsonArray("mixins"))assertNotEquals("shaderlod-sildur-pixel.mixins.json",mixin.getAsString(),
                        "Do not register a pixel runtime without its profile; broader port work remains separate");
                }
            }
        }
    }
    @Test void actualCompiledLifecycleSeparatesFrameResetFromSourceInvalidation()throws Exception {
        String world="com/pp/shaderlod/optimization/sildurworld/SildurWorldRuntime",pixel="com/pp/shaderlod/optimization/sildurpixel/SildurPixelRuntime",cacheType="com/pp/shaderlod/optimization/sourcecache/EarlyAlphaSourceCache";
        assertTrue(calls(world,"begin").contains(world+".resetFrame"));assertFalse(calls(world,"begin").contains(world+".invalidate"));
        assertFalse(calls(world,"resetFrame").contains(cacheType+".invalidate"));
        for(String runtime:List.of(world,pixel)) {
            assertTrue(calls(runtime,"source").contains("com/pp/shaderlod/optimization/sourcecache/IrisSourceLifecycle.proof") || calls(runtime,"source").contains(cacheType+".check"));
            assertTrue(calls(runtime,"invalidate").contains(cacheType+".invalidate"));
            assertTrue(calls(runtime,"lose").contains(cacheType+".invalidate"));
            assertFalse(calls(runtime,"begin").contains(cacheType+".invalidate"));
        }
    }

}

/** A real class definition gives the cache its normal Class.getResourceAsStream boundary. */
final class SildurPixelCacheResourceOwner {}

package com.pp.shaderlod.optimization;

import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.solasao.SolasAoContract;
import com.pp.shaderlod.optimization.solasao.SolasAoSourceProof;
import com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache;
import com.pp.shaderlod.packs.PackSelectionService;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual normal producer and unchanged AO proof callback; no GL/runtime-performance claim. */
class AoSourceCertificateCacheTest {
    @TempDir Path directory;
    private final EarlyAlphaSourceCache cache=new EarlyAlphaSourceCache();
    private final Object pack=new Object(),values=new Object(),declared=new Object(),pipeline=new Object();
    private Object world=new Object();
    private Map<String,String> options=SolasAoContract.generated("1");
    private String selected;
    private int calls;
    private Class<?> resourceOwner=AoSourceCertificateCacheTest.class;
    private boolean check(long sourceGeneration)throws Exception {
        return LoadSourceValidationFixture.check(cache,resourceOwner,directory,selected,"solas-shader",options,
            pack,values,declared,pipeline,world,sourceGeneration,List.of("unchanged-core",Map.of()),List.of("vanilla","mod_resources"),()->{
                calls++;
                return SolasAoSourceProof.verify(directory.resolve(selected),()->resourceOwner.getResourceAsStream(
                    "/assets/shaderlod/adapters/patches/adapters/solas-shader.json"));
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
        MutableResources(){super(AoSourceCertificateCacheTest.class.getClassLoader());}
        Class<?> owner()throws Exception {
            String name=AoCacheResourceOwner.class.getName();
            try(var in=getParent().getResourceAsStream(name.replace('.','/')+".class")) {
                assertNotNull(in);byte[] bytes=in.readAllBytes();return defineClass(name,bytes,0,bytes.length);
            }
        }
        @Override public java.io.InputStream getResourceAsStream(String name) {
            byte[] bytes=replacements.get(name);
            return bytes==null?super.getResourceAsStream(name):new java.io.ByteArrayInputStream(bytes);
        }
    }
    @Test void mutableRecipeAndPinnedHelperBytesCannotReuseAValidAoCertificate()throws Exception {
        var prior=OptimizationConfig.state();
        try {
            OptimizationConfig.restoreInMemory(OptimizationSnapshot.noneRequested().with(OptimizationId.BINDING_CERTIFICATE_CACHE,true));
            Files.copy(Path.of("dependencies/upstream/pack-expansion/Solas Shader V3.7b.zip"),directory.resolve("Original.zip"));
            var nativeOptions=new TreeMap<>(options);nativeOptions.keySet().removeIf(k->k.startsWith("SHADERLOD_"));
            var result=new PackSelectionService().apply(directory,"Original.zip",true,LodPreset.PERFORMANCE,nativeOptions,false);
            assertTrue(result.accepted(),result.detail());selected=result.packFile();
            var resources=new MutableResources();resourceOwner=resources.owner();
            assertTrue(check(1));Object originalPlan=parsedPlan();assertNotNull(originalPlan);
            assertTrue(check(1));assertEquals(1,calls);assertSame(originalPlan,parsedPlan(),"Unchanged fresh recipe digest reuses its parsed plan");
            var helpers=originalPlan.getClass().getDeclaredMethod("helpers");helpers.setAccessible(true);
            assertThrows(UnsupportedOperationException.class,()->((List<?>)helpers.invoke(originalPlan)).clear(),"No mutable Gson plan may escape");
            String recipe="assets/shaderlod/adapters/patches/adapters/solas-shader.json";
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
            // but changes the complete recipe byte identity verified by the AO callback.
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
            var fresh=EarlyAlphaSourceCache.fingerprint(resourceOwner,directory,selected,"solas-shader");
            assertSame(changedOwnerPlan,parsedPlan(),"Public fresh fingerprint does not consume or mutate instance parse memo");
            OptimizationConfig.restoreInMemory(OptimizationSnapshot.noneRequested());
            replayBefore=calls;assertTrue(check(1));assertNull(parsedPlan(),"Cache Off does not retain a plan");
            assertTrue(check(1));assertEquals(replayBefore+2,calls);
            assertEquals(fresh,EarlyAlphaSourceCache.fingerprint(resourceOwner,directory,selected,"solas-shader"),"Parse reuse must preserve the complete fingerprint value/order");
        } finally {cache.invalidate();OptimizationConfig.restoreState(prior);}
    }
    @Test void normalAoSourceMemoSkipsReplayButSameStatMutationsAndOwnersRevalidate()throws Exception {
        var prior=OptimizationConfig.state();
        try {
            OptimizationConfig.restoreInMemory(OptimizationSnapshot.noneRequested().with(OptimizationId.BINDING_CERTIFICATE_CACHE,true));
            Files.copy(Path.of("dependencies/upstream/pack-expansion/Solas Shader V3.7b.zip"),directory.resolve("Original.zip"));
            var nativeOptions=new TreeMap<>(options);nativeOptions.keySet().removeIf(k->k.startsWith("SHADERLOD_"));
            var result=new PackSelectionService().apply(directory,"Original.zip",true,LodPreset.PERFORMANCE,nativeOptions,false);
            assertTrue(result.accepted(),result.detail());assertTrue(result.generatedArchive());selected=result.packFile();
            assertTrue(check(1));assertEquals(1,calls);
            assertTrue(check(1));assertEquals(1,calls,"Unchanged subsequent frame skips full source replay");
            world=new Object();assertTrue(check(1));assertEquals(2,calls);
            assertTrue(check(2));assertEquals(3,calls,"Source generation is a cache boundary");
            options=new TreeMap<>(options);options.put(SolasAoContract.OPTION,"0");
            assertTrue(check(2));assertEquals(4,calls,"Actual option map is part of the key");
            options=SolasAoContract.generated("1");assertTrue(check(1));
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
            int before=calls;assertTrue(check(1));assertTrue(check(1));assertEquals(before+2,calls,"Cache Off always invokes the AO proof");
        } finally {cache.invalidate();OptimizationConfig.restoreState(prior);}
    }
}

/** A real class definition gives the cache its normal Class.getResourceAsStream boundary. */
final class AoCacheResourceOwner {}

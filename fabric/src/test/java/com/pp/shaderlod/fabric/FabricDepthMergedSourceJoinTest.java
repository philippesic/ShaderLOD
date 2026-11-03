package com.pp.shaderlod.fabric;

import com.google.gson.JsonParser;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.packs.PackSelectionService;
import com.pp.shaderlod.packs.SupportedPackCatalog;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Fabric public guards and normal archive producer; no Minecraft/GL admission is supplied. */
class FabricDepthMergedSourceJoinTest {
    @TempDir Path directory;
    private record Family(String id,String archive,String nativeResource,int nativeCount,String nativeAa,
            Class<?> depthClass,Predicate<Map<String,String>> depth,List<String> binaryControls,
            Class<?> mergedClass,BiPredicate<Path,Map<String,String>> merged) {}
    private record Prepared(Path directory,Path original,Path generated,Path state,Map<String,String> nativeOptions,
            Map<String,String> generatedOptions) {}

    private static List<Family> families(){return List.of(
        new Family("astralex","dependencies/upstream/pack-expansion/AstraLex-93.0-original-ascii.zip","astraquad/native1015.json",1015,"AA",
            com.pp.shaderlod.optimization.astraprime.AstraDepthPriming.class,com.pp.shaderlod.optimization.astraprime.AstraDepthPriming::profileMatch,
            List.of("SHADERLOD_ASTRALEX_DEPTH_PRIMING","SHADERLOD_ASTRALEX_AO_HALF_RES","SHADERLOD_ASTRALEX_DEAD_MIP_CHAIN"),
            com.pp.shaderlod.optimization.astramerged.AstraMergedLinkageGate.class,com.pp.shaderlod.optimization.astramerged.AstraMergedLinkageGate::source),
        new Family("bliss-shader","dependencies/upstream/pack-expansion/Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip","blissquad/native463.json",463,"TAA",
            com.pp.shaderlod.optimization.blissprime.BlissDepthPriming.class,com.pp.shaderlod.optimization.blissprime.BlissDepthPriming::profileMatch,
            List.of("SHADERLOD_BLISS_DEPTH_PRIMING","SHADERLOD_BLISS_AO_HALF_RES","SHADERLOD_BLISS_SPATIAL_REUSE","SHADERLOD_BLISS_LIGHTING_RATE"),
            com.pp.shaderlod.optimization.blissmerged.BlissMergedLinkageGate.class,com.pp.shaderlod.optimization.blissmerged.BlissMergedLinkageGate::source),
        new Family("bsl-shaders","dependencies/upstream/other-pack-research/BSL_v10.1.8.zip","bslquad/native384.json",384,"TAA",
            com.pp.shaderlod.optimization.bslprime.BslDepthPriming.class,com.pp.shaderlod.optimization.bslprime.BslDepthPriming::profileMatch,
            List.of("SHADERLOD_BSL_DEPTH_PRIMING","SHADERLOD_BSL_AO_HALF_RES","SHADERLOD_BSL_DEAD_MIP_CHAIN","SHADERLOD_BSL_SHADOW_FIELD","SHADERLOD_BSL_RAW_QUERY"),
            com.pp.shaderlod.optimization.bslmerged.BslMergedLinkageGate.class,com.pp.shaderlod.optimization.bslmerged.BslMergedLinkageGate::source),
        new Family("complementary-reimagined","dependencies/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip","reimaginedquad/native398.json",398,"TAA",
            com.pp.shaderlod.optimization.reimaginedprime.ReimaginedDepthPriming.class,com.pp.shaderlod.optimization.reimaginedprime.ReimaginedDepthPriming::profileMatch,
            List.of("SHADERLOD_REIMAGINED_DEPTH_PRIMING","SHADERLOD_REIMAGINED_AO_HALF_RES","SHADERLOD_REIMAGINED_DEAD_MIP_CHAIN","SHADERLOD_REIMAGINED_SPATIAL_REUSE","SHADERLOD_REIMAGINED_SPATIAL_RATE","SHADERLOD_REIMAGINED_PIXEL_GRAPH"),
            com.pp.shaderlod.optimization.reimaginedmerged.ReimaginedMergedLinkageGate.class,com.pp.shaderlod.optimization.reimaginedmerged.ReimaginedMergedLinkageGate::source),
        new Family("complementary-unbound","dependencies/upstream/pack-expansion/ComplementaryUnbound_r5.9.3.zip","unboundquad/native398.json",398,"TAA",
            com.pp.shaderlod.optimization.unboundprime.UnboundDepthPriming.class,com.pp.shaderlod.optimization.unboundprime.UnboundDepthPriming::profileMatch,
            List.of("SHADERLOD_UNBOUND_DEPTH_PRIMING","SHADERLOD_UNBOUND_AO_HALF_RES","SHADERLOD_UNBOUND_DEAD_MIP_CHAIN","SHADERLOD_UNBOUND_SPATIAL_REUSE","SHADERLOD_UNBOUND_SPATIAL_RATE","SHADERLOD_UNBOUND_PIXEL_GRAPH"),
            com.pp.shaderlod.optimization.unboundmerged.UnboundMergedLinkageGate.class,com.pp.shaderlod.optimization.unboundmerged.UnboundMergedLinkageGate::source),
        new Family("makeup-ultra-fast-shaders","dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip","makeupquad/native100.json",100,"AA_TYPE",
            com.pp.shaderlod.optimization.makeupprime.MakeUpDepthPriming.class,com.pp.shaderlod.optimization.makeupprime.MakeUpDepthPriming::profileMatch,
            List.of("SHADERLOD_MAKEUP_DEPTH_PRIMING","SHADERLOD_MAKEUP_AO_HALF_RES","SHADERLOD_MAKEUP_SPATIAL_REUSE","SHADERLOD_MAKEUP_SPATIAL_RATE","SHADERLOD_MAKEUP_WORLD_PIXEL"),null,null),
        new Family("sildurs-vibrant-shaders","dependencies/upstream/pack-expansion/Sildur's Vibrant Shaders v2.02 Extreme.zip","sildurquad/native115.json",115,"TAA",
            com.pp.shaderlod.optimization.sildurprime.SildurDepthPriming.class,com.pp.shaderlod.optimization.sildurprime.SildurDepthPriming::profileMatch,
            List.of("SHADERLOD_SILDUR_DEPTH_PRIMING","SHADERLOD_SILDUR_AO_HALF_RES","SHADERLOD_SILDUR_SPATIAL_REUSE","SHADERLOD_SILDUR_LIGHTING_RATE","SHADERLOD_SILDUR_PIXEL_GRAPH"),
            com.pp.shaderlod.optimization.sildurmerged.SildurMergedLinkageGate.class,com.pp.shaderlod.optimization.sildurmerged.SildurMergedLinkageGate::source),
        new Family("solas-shader","dependencies/upstream/pack-expansion/Solas Shader V3.7b.zip","solasquad/native428.json",428,"TAA",
            com.pp.shaderlod.optimization.solasprime.SolasDepthPriming.class,com.pp.shaderlod.optimization.solasprime.SolasDepthPriming::profileMatch,
            List.of("SHADERLOD_SOLAS_DEPTH_PRIMING","SHADERLOD_SOLAS_AO_HALF_RES","SHADERLOD_SOLAS_DEAD_MIP_CHAIN","SHADERLOD_SOLAS_PIXEL_GRAPH"),
            com.pp.shaderlod.optimization.solasmerged.SolasMergedLinkageGate.class,com.pp.shaderlod.optimization.solasmerged.SolasMergedLinkageGate::source));}

    @TestFactory Stream<DynamicTest> actualFabricDepthProfiles(){return families().stream().map(family->DynamicTest.dynamicTest(
        family.id+" actual depth profile and exact binary normalization",()->{
            assertFabricOverride(family.depthClass);
            var prepared=prepare(family);var actual=prepared.generatedOptions;
            assertTrue(family.depth.test(actual),family.id+" current packaged full map");
            // Only these explicitly normalized transport controls may vary; this is not GL/role admission.
            for(int bits=0;bits<(1<<family.binaryControls.size());bits++) {
                var combination=new TreeMap<>(actual);
                for(int i=0;i<family.binaryControls.size();i++) {
                    String key=family.binaryControls.get(i);assertTrue(actual.containsKey(key),key);
                    combination.put(key,(bits&(1<<i))==0?"0":"1");
                }
                assertTrue(family.depth.test(combination),family.id+" binary vector "+bits);
            }
            assertFalse(family.depth.test(with(actual,family.nativeAa,alternate(actual.get(family.nativeAa)))));
            var missing=new TreeMap<>(actual);missing.remove(family.nativeAa);assertFalse(family.depth.test(missing));
            assertFalse(family.depth.test(with(actual,"SHADERLOD_FOREIGN_TEST_CONTROL","1")));
            assertFalse(family.depth.test(with(actual,"UNDECLARED_NATIVE_OPTION","true")));
            for(String key:family.binaryControls)assertFalse(family.depth.test(with(actual,key,"2")),family.id+" invalid "+key);
            if(family.id.equals("bliss-shader"))assertFalse(family.depth.test(with(actual,"SHADERLOD_BLISS_PIXEL_GRAPH","1")),"Existing Bliss pixel/depth role exclusion");
            if(family.id.equals("sildurs-vibrant-shaders"))assertFalse(family.depth.test(with(actual,"SHADERLOD_SILDUR_SHADOW_QUERY_GRAPH","1")),"Existing Sildur query/depth role exclusion");
        }));}

    @TestFactory Stream<DynamicTest> actualFabricMergedSources(){return families().stream().filter(f->f.merged!=null).map(family->DynamicTest.dynamicTest(
        family.id+" actual native/generated merged source and Off/Quality recovery",()->{
            assertFabricOverride(family.mergedClass);
            var prepared=prepare(family);var actual=prepared.generatedOptions;
            assertTrue(family.merged.test(prepared.original,prepared.nativeOptions),family.id+" original archive");
            assertTrue(family.merged.test(prepared.generated,actual),family.id+" normal public producer result");
            assertFalse(family.merged.test(prepared.generated,with(actual,family.nativeAa,alternate(actual.get(family.nativeAa)))));
            var missing=new TreeMap<>(actual);missing.remove(family.nativeAa);assertFalse(family.merged.test(prepared.generated,missing));
            assertFalse(family.merged.test(prepared.generated,with(actual,"SHADERLOD_FOREIGN_TEST_CONTROL","1")));
            assertFalse(family.merged.test(prepared.generated,with(actual,"UNDECLARED_NATIVE_OPTION","true")));
            for(String key:family.binaryControls)assertFalse(family.merged.test(prepared.generated,with(actual,key,"2")),key);
            byte[] generated=Files.readAllBytes(prepared.generated),state=Files.readAllBytes(prepared.state);
            try {
                var corrupted=generated.clone();corrupted[corrupted.length/2]^=1;Files.write(prepared.generated,corrupted);
                assertFalse(family.merged.test(prepared.generated,actual),family.id+" generated byte tamper");
            } finally {Files.write(prepared.generated,generated);}
            assertTrue(family.merged.test(prepared.generated,actual),"Restored generated bytes requalify");
            try {
                var changed=JsonParser.parseString(new String(state,StandardCharsets.UTF_8)).getAsJsonObject();changed.addProperty("adapterId","foreign-family");
                Files.writeString(prepared.state,changed.toString());
                assertFalse(family.merged.test(prepared.generated,actual),family.id+" state tamper");
            } finally {Files.write(prepared.state,state);}
            assertTrue(family.merged.test(prepared.generated,actual),"Restored state requalifies");
            byte[] original=Files.readAllBytes(prepared.original);var service=new PackSelectionService();
            var off=service.apply(prepared.directory,prepared.generated.getFileName().toString(),false,LodPreset.PERFORMANCE,actual,false);
            assertTrue(off.accepted(),off.detail());assertEquals(prepared.original.getFileName().toString(),off.packFile());
            assertTrue(family.merged.test(prepared.original,prepared.nativeOptions));assertArrayEquals(original,Files.readAllBytes(prepared.original));
            assertNativeSidecarRetained(prepared);
            var on=service.apply(prepared.directory,off.packFile(),true,LodPreset.PERFORMANCE,prepared.nativeOptions,false);
            assertTrue(on.accepted(),on.detail());assertTrue(family.merged.test(prepared.directory.resolve(on.packFile()),actual));
            var quality=service.apply(prepared.directory,on.packFile(),true,LodPreset.QUALITY,actual,false);
            assertTrue(quality.accepted(),quality.detail());assertEquals(off.packFile(),quality.packFile());
            assertTrue(family.merged.test(prepared.original,prepared.nativeOptions));assertArrayEquals(original,Files.readAllBytes(prepared.original));
            assertNativeSidecarRetained(prepared);
        }));}

    private Prepared prepare(Family family)throws Exception {
        Path arm=Files.createTempDirectory(directory,family.id+"-");Path original=arm.resolve("Original.zip");Files.copy(Path.of(family.archive),original);
        var nativeOptions=options("/assets/shaderlod/"+family.nativeResource);assertEquals(family.nativeCount,nativeOptions.size());
        var source=com.pp.shaderlod.integration.PackSourceReader.read(original);
        var recognized=SupportedPackCatalog.recognize(source.memberSha256()).orElseThrow();
        assertEquals(family.id,recognized.pack().id());assertEquals(SupportedPackCatalog.Stage.RECOGNIZED_ORIGINAL,recognized.stage());
        var result=new PackSelectionService().apply(arm,"Original.zip",true,LodPreset.PERFORMANCE,nativeOptions,false);
        assertTrue(result.accepted(),result.detail());assertTrue(result.generatedArchive());Path generated=arm.resolve(result.packFile());
        Map<String,String> actual;
        try(var input=getClass().getResourceAsStream("/assets/shaderlod/rendererquad/generated-main-options.json")) {
            assertNotNull(input);var values=new TreeMap<String,String>();JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject(family.id).entrySet().forEach(e->values.put(e.getKey(),e.getValue().getAsString()));actual=Map.copyOf(values);
        }
        assertEquals(family.id,PackSelectionService.verifyGeneratedRendererGraph(generated,actual));
        Path state;
        try(var zip=new ZipFile(generated.toFile());var input=zip.getInputStream(zip.getEntry("shaderlod-selection.json"))) {
            state=arm.resolve(JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject().get("stateFile").getAsString());
        }
        return new Prepared(arm,original,generated,state,nativeOptions,actual);
    }
    private static Map<String,String> options(String resource)throws Exception {
        try(var input=FabricDepthMergedSourceJoinTest.class.getResourceAsStream(resource)) {
            assertNotNull(input,resource);var values=new TreeMap<String,String>();JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8))
                .getAsJsonObject().entrySet().forEach(e->values.put(e.getKey(),e.getValue().getAsString()));return Map.copyOf(values);
        }
    }
    private static Map<String,String> with(Map<String,String> actual,String key,String value){var changed=new TreeMap<>(actual);changed.put(key,value);return changed;}
    private static String alternate(String value){assertNotNull(value);return value.equals("true")?"false":value.equals("false")?"true":value.equals("0")?"1":"0";}
    private static void assertNativeSidecarRetained(Prepared prepared)throws Exception {
        // The public producer wrote the complete supplied native map before selecting its private graph.
        // Inspect its actual restored sidecar, rather than assuming the input map represents restoration.
        var properties=new Properties();try(var input=Files.newInputStream(prepared.original.resolveSibling(prepared.original.getFileName()+".txt"))){properties.load(input);}
        for(var entry:prepared.nativeOptions.entrySet())assertEquals(entry.getValue(),properties.getProperty(entry.getKey()),"Restored native option "+entry.getKey());
    }
    private static void assertFabricOverride(Class<?> type)throws Exception {
        try(var input=type.getResourceAsStream(type.getSimpleName()+".class")) {
            assertNotNull(input);String constants=new String(input.readAllBytes(),StandardCharsets.ISO_8859_1);
            assertTrue(constants.contains("net/fabricmc/loader/api/FabricLoader"),"Test must load actual Fabric override, not same-FQN root class: "+type.getName());
        }
    }
}

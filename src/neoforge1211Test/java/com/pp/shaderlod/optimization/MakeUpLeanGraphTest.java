package com.pp.shaderlod.optimization;

import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility;
import com.pp.shaderlod.optimization.framecopy.MakeUpLeanGraph;
import com.pp.shaderlod.optimization.mips.BslLeanGraph;
import com.pp.shaderlod.packs.PackSelectionService;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MakeUpLeanGraphTest {
    @TempDir Path dir;
    private static BslLeanGraph.Route route(OptimizationSnapshot flags) {
        return new BslLeanGraph.Route(flags,true,"makeup-ultra-fast-shaders",true,true);
    }
    void checkProducer() throws Exception {
        var fixture=new BslLeanChecksMain();fixture.dir=dir;
        fixture.setup();
        try {
            Files.copy(Path.of("dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip"),dir.resolve("Original.zip"),StandardCopyOption.REPLACE_EXISTING);
            var flags=OptimizationSnapshot.noneRequested().with(OptimizationId.DEAD_FRAME_COPY,true);
            var service=new PackSelectionService();
            var result=service.applyPlanned(dir,"Original.zip",true,LodPreset.PERFORMANCE,
                MakeUpQuadEligibility.nativeOptions(),false,Map.of(),Set.of(),null,route(flags));
            assertTrue(result.accepted(),result.detail());
            var actual=BslLeanChecksMain.values(BslLeanChecksMain.parse(dir.resolve(result.packFile()),fixture.sidecar(result.packFile())));
            assertEquals("0",actual.get(BslLeanGraph.ADAPTER));
            assertTrue(MakeUpLeanGraph.profile(actual),actual.toString());
            assertEquals("makeup-ultra-fast-shaders",PackSelectionService.verifyGeneratedRendererGraph(dir.resolve(result.packFile()),actual));
            for(String key:MakeUpQuadEligibility.nativeOptions().keySet()) {
                var changed=new TreeMap<>(actual);changed.put(key,"changed");
                assertFalse(MakeUpLeanGraph.profile(changed),key);
                assertNull(PackSelectionService.verifyGeneratedRendererGraph(dir.resolve(result.packFile()),changed));
            }
            var changed=new TreeMap<>(actual);changed.put("SHADERLOD_UNKNOWN","0");assertFalse(MakeUpLeanGraph.profile(changed));
            var restored=service.applyPlanned(dir,result.packFile(),false,LodPreset.PERFORMANCE,
                actual,false,Map.of(),Set.of(),null,route(flags));
            assertTrue(restored.accepted(),restored.detail());assertEquals("Original.zip",restored.packFile());
            var broad=service.applyPlanned(dir,"Original.zip",true,LodPreset.PERFORMANCE,
                MakeUpQuadEligibility.nativeOptions(),false,Map.of(),Set.of(),null,route(flags.with(OptimizationId.AO_BUDGET,true)));
            assertTrue(broad.accepted(),broad.detail());
            var broadValues=BslLeanChecksMain.values(BslLeanChecksMain.parse(dir.resolve(broad.packFile()),fixture.sidecar(broad.packFile())));
            assertEquals("1",broadValues.get(BslLeanGraph.ADAPTER));
            assertFalse(MakeUpLeanGraph.profile(broadValues));
            var lean=service.applyPlanned(dir,broad.packFile(),true,LodPreset.PERFORMANCE,
                broadValues,false,Map.of(),Set.of(),null,route(flags));
            assertTrue(lean.accepted(),lean.detail());
            var leanValues=BslLeanChecksMain.values(BslLeanChecksMain.parse(dir.resolve(lean.packFile()),fixture.sidecar(lean.packFile())));
            assertEquals("0",leanValues.get(BslLeanGraph.ADAPTER));
            assertTrue(MakeUpLeanGraph.profile(leanValues));
            var earlyFlags=OptimizationSnapshot.noneRequested().with(OptimizationId.EARLY_ALPHA,true);
            var early=service.applyPlanned(dir,"Original.zip",true,LodPreset.PERFORMANCE,
                MakeUpQuadEligibility.nativeOptions(),false,Map.of(),Set.of(),null,route(earlyFlags));
            assertTrue(early.accepted(),early.detail());
            var earlyValues=BslLeanChecksMain.values(BslLeanChecksMain.parse(dir.resolve(early.packFile()),fixture.sidecar(early.packFile())));
            assertTrue(com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaLeanGraph.profile(earlyValues),earlyValues.toString());
            earlyValues=new TreeMap<>(earlyValues);earlyValues.put(CompileOptionControl.MAKEUP_EARLY_ALPHA,"1");
            assertTrue(com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaRuntime.profile(earlyValues));
            assertEquals("makeup-ultra-fast-shaders",PackSelectionService.verifyGeneratedRendererGraph(dir.resolve(early.packFile()),earlyValues));
            for(String key:MakeUpQuadEligibility.nativeOptions().keySet()) {
                var changedEarly=new TreeMap<>(earlyValues);changedEarly.put(key,"changed");
                assertFalse(com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaLeanGraph.profile(changedEarly),key);
            }
            for(var id:OptimizationId.values())if(id!=OptimizationId.EARLY_ALPHA)
                assertFalse(com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaLeanGraph.eligible(route(earlyFlags.with(id,true)),true,LodPreset.PERFORMANCE));
            var earlyOff=service.applyPlanned(dir,early.packFile(),false,LodPreset.PERFORMANCE,
                earlyValues,false,Map.of(),Set.of(),null,route(earlyFlags));
            assertTrue(earlyOff.accepted(),earlyOff.detail());assertEquals("Original.zip",earlyOff.packFile());
            // Actual pinned producer: exact requests never route to the broad adapter on refusal.
            for(var id:List.of(OptimizationId.TRANSLUCENT_FASTPATH,OptimizationId.AO_ATTENUATION)) {
                var exactFlags=OptimizationSnapshot.noneRequested().with(id,true);
                var unsupportedRoute=new BslLeanGraph.Route(exactFlags,true,"makeup-ultra-fast-shaders",false,true);
                var unsupported=service.applyPlanned(dir,"Original.zip",true,LodPreset.PERFORMANCE,
                    MakeUpQuadEligibility.nativeOptions(),false,Map.of(),Set.of(),null,unsupportedRoute);
                assertTrue(unsupported.accepted(),unsupported.detail());assertEquals("Original.zip",unsupported.packFile());
                var mismatched=new TreeMap<>(MakeUpQuadEligibility.nativeOptions());mismatched.put("AOSTEPS","7");
                var refused=service.applyPlanned(dir,"Original.zip",true,LodPreset.PERFORMANCE,
                    mismatched,false,Map.of(),Set.of("AOSTEPS"),null,route(exactFlags));
                assertTrue(refused.accepted(),refused.detail());assertEquals("Original.zip",refused.packFile());
                var exact=service.applyPlanned(dir,"Original.zip",true,LodPreset.PERFORMANCE,
                    MakeUpQuadEligibility.nativeOptions(),false,Map.of(),Set.of("AOSTEPS"),null,route(exactFlags));
                assertTrue(exact.accepted(),exact.detail());
                var exactValues=BslLeanChecksMain.values(BslLeanChecksMain.parse(dir.resolve(exact.packFile()),fixture.sidecar(exact.packFile())));
                assertEquals("0",exactValues.get(BslLeanGraph.ADAPTER));
                assertTrue(com.pp.shaderlod.optimization.makeupexact.MakeUpExactGraph.profile(exactValues));
                exactValues=new TreeMap<>(exactValues);exactValues.put("AOSTEPS","7");
                var fallback=service.applyPlanned(dir,exact.packFile(),true,LodPreset.PERFORMANCE,
                    exactValues,false,Map.of(),Set.of("AOSTEPS"),null,route(exactFlags));
                assertTrue(fallback.accepted(),fallback.detail());assertEquals("Original.zip",fallback.packFile());
            }
        } finally {fixture.restore();}
    }
    @Test void actualProducerParserOwnershipAndOriginalRecovery() throws Exception {
        var classpath=new java.util.LinkedHashSet<String>();
        for(ClassLoader loader=getClass().getClassLoader();loader!=null;loader=loader.getParent())
            if(loader instanceof java.net.URLClassLoader urls)for(var url:urls.getURLs())
                if(url.getProtocol().equals("file"))classpath.add(Path.of(url.toURI()).toString());
        classpath.addAll(List.of(System.getProperty("java.class.path").split(java.io.File.pathSeparator)));
        Path output=dir.resolve("check.txt"),receiver=dir.resolve("receiver");Files.createDirectory(receiver);
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",
            String.join(java.io.File.pathSeparator,classpath),MakeUpLeanChecksMain.class.getName(),receiver.toString())
            .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(60,java.util.concurrent.TimeUnit.SECONDS),"finite producer/parser fixture exceeded60s");
            assertEquals(0,process.exitValue(),()->{try{return Files.readString(output);}catch(Exception failure){return failure.toString();}});
            assertTrue(Files.readString(output).contains("PASS actual MakeUp lean producer/parser/recovery"));
        } finally {if(process.isAlive()){process.destroyForcibly();process.waitFor();}}
    }
    @Test void earlyAlphaSingletonAndSpoofedCapabilityFailClosed() {
        var flags=OptimizationSnapshot.noneRequested().with(OptimizationId.EARLY_ALPHA,true);
        assertTrue(com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaLeanGraph.eligible(route(flags),true,LodPreset.PERFORMANCE));
        assertFalse(com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaLeanGraph.eligible(route(flags),true,LodPreset.QUALITY));
        assertFalse(com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaLeanGraph.eligible(route(flags),false,LodPreset.PERFORMANCE));
        var key=com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaLeanGraph.DEFINE;
        var incoming=List.of(new net.irisshaders.iris.helpers.StringPair(key,"spoof"),new net.irisshaders.iris.helpers.StringPair("IS_IRIS","1"));
        var refused=com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaLeanGraph.constructorDefines(incoming,false);
        assertEquals(List.of(new net.irisshaders.iris.helpers.StringPair("IS_IRIS","1")),refused);
        var admitted=com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaLeanGraph.constructorDefines(incoming,true);
        assertEquals(1,admitted.stream().filter(x->key.equals(x.key())&&"1".equals(x.value())).count());
    }
    @Test void onlyExplicitSingletonSupportedPerformanceRoute() {
        var flags=OptimizationSnapshot.noneRequested().with(OptimizationId.DEAD_FRAME_COPY,true);
        assertTrue(MakeUpLeanGraph.eligible(route(flags),true,LodPreset.PERFORMANCE));
        assertFalse(MakeUpLeanGraph.eligible(route(flags),true,LodPreset.QUALITY));
        assertFalse(MakeUpLeanGraph.eligible(route(flags),false,LodPreset.PERFORMANCE));
        for(var id:OptimizationId.values())if(id!=OptimizationId.DEAD_FRAME_COPY)
            assertFalse(MakeUpLeanGraph.eligible(route(flags.with(id,true)),true,LodPreset.PERFORMANCE));
        assertFalse(MakeUpLeanGraph.eligible(BslLeanGraph.LEGACY,true,LodPreset.PERFORMANCE));
    }
}

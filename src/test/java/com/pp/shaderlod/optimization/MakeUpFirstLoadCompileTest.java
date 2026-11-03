package com.pp.shaderlod.optimization;

import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.PackSelectionTransaction;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Actual pinned archive + Iris IncludeGraph/OptionValues + selection/writer transaction.
 * No GL driver is impersonated; linked consumer qualification is a separate client check. */
class MakeUpFirstLoadCompileTest {
    @TempDir Path directory;
    private OptimizationConfig.State saved;
    private static final String KEY=CompileOptionControl.MAKEUP_EARLY_ALPHA;
    @BeforeEach void setup()throws Exception {
        assumeTrue("Linux".equals(System.getProperty("os.name"))&&"aarch64".equals(System.getProperty("os.arch")));
        saved=OptimizationConfig.state();
        OptimizationConfig.load(directory.resolve("requests.properties"));
        OptimizationConfig.replace(OptimizationSnapshot.noneRequested().with(OptimizationId.EARLY_ALPHA,true));
    }
    @AfterEach void restore(){if(saved!=null)OptimizationConfig.restoreState(saved);}
    private CompileBootstrapChecksMain.Backend backend()throws Exception {
        Path fixture=Path.of("dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip");
        assumeTrue(Files.isRegularFile(fixture));
        var b=new CompileBootstrapChecksMain.Backend(directory,"makeup-ultra-fast-shaders",KEY,fixture);
        b.g03FirstLoad=true;return b;
    }
    private void run(CompileBootstrapChecksMain.Backend b,PackSelectionTransaction tx)throws Exception {
        CompileOptionControl.reloadTwoPhase(tx,b::load,b);
    }
    @Test void actualFirstLoadIsPositiveAndRepeatOnIsUnchangedThenOffAndQualityReturnOriginal()throws Exception {
        for(boolean quality:List.of(false,true)) {
            var b=backend();byte[] original=Files.readAllBytes(b.dir.resolve("Original.zip.txt"));
            var tx=new PackSelectionTransaction(b);assertTrue(tx.prepare(true,LodPreset.PERFORMANCE));
            assertEquals(Map.of(KEY,"1"),tx.bootstrapCompileOptions());run(b,tx);tx.finishRecovery();
            assertEquals(1,b.loads);assertEquals("1",b.loaded().get(KEY).value());assertEquals("0",b.effectiveOptions().get("SHADERLOD_REFINEMENT_ADAPTER"));
            assertEquals(b.originalValues,b.effectiveOptions().entrySet().stream().filter(e->!e.getKey().startsWith("SHADERLOD_")).collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue)));
            var again=new PackSelectionTransaction(b);assertFalse(again.prepare(true,LodPreset.PERFORMANCE));assertTrue(again.bootstrapCompileOptions().isEmpty());again.finishRecovery();assertEquals(1,b.loads);
            var back=new PackSelectionTransaction(b);assertTrue(back.prepare(quality,quality?LodPreset.QUALITY:LodPreset.PERFORMANCE));assertTrue(back.bootstrapCompileOptions().isEmpty());
            b.preset=quality?LodPreset.QUALITY:LodPreset.PERFORMANCE;run(b,back);back.finishRecovery();
            assertEquals("Original.zip",b.selected);assertEquals(2,b.loads);assertFalse(b.loaded().containsKey(KEY));assertTrue(b.queue().isEmpty());
            assertEquals(b.originalValues,b.effectiveOptions(),"Iris canonical sparse writer may omit native defaults; effective values stay exact");
        }
    }
    @Test void firstLoadFailureMismatchStalenessAndChangedAdmissionRecoverSavedInputs()throws Exception {
        for(int kind=0;kind<5;kind++) {
            var b=backend();byte[] original=Files.readAllBytes(b.dir.resolve("Original.zip.txt"));
            b.failFirst=kind==0;b.ignoreFirstCompile=kind==1;b.staleFirstGeneration=kind==2;b.staleFirstReceipt=kind==3;b.admitted=kind!=4;
            var tx=new PackSelectionTransaction(b);assertTrue(tx.prepare(true,LodPreset.PERFORMANCE));
            assertThrows(Exception.class,()->run(b,tx));assertEquals(1,b.loads,"no correcting second activation after failed first-load proof");
            tx.restoreForRecovery();tx.recoveryReload(b::load);tx.finishRecovery();
            assertEquals("Original.zip",b.selected);assertTrue(b.queue().isEmpty());assertArrayEquals(original,Files.readAllBytes(b.dir.resolve("Original.zip.txt")));
        }
    }
    @Test void newerForeignQueueIntentSurvivesOwnedFirstLoad()throws Exception {
        var b=backend();var tx=new PackSelectionTransaction(b);assertTrue(tx.prepare(true,LodPreset.PERFORMANCE));
        b.queue().put("NATIVE_UNRELATED","future");run(b,tx);tx.finishRecovery();
        assertEquals(1,b.loads);assertEquals(Map.of("NATIVE_UNRELATED","future"),b.queue());
    }
    @Test void unsupportedRouteOrEditedNativeQualityNeverReceivesPositiveCapability()throws Exception {
        for(int kind=0;kind<4;kind++) {
            var b=backend();b.routeExact=kind!=0;b.routeOverworld=kind!=1;
            if(kind==2)OptimizationConfig.replace(OptimizationConfig.snapshot().with(OptimizationId.AO_ATTENUATION,true));
            if(kind==3){Files.writeString(b.dir.resolve("Original.zip.txt"),"AA_TYPE=0\n");}
            // For the native edit use the authentic parser via a normal load before preparation.
            if(kind==3)b.load();
            var tx=new PackSelectionTransaction(b);tx.prepare(true,LodPreset.PERFORMANCE);
            assertTrue(tx.bootstrapCompileOptions().isEmpty());
            if(kind!=2)assertEquals("Original.zip",b.selected);tx.finishRecovery();
            OptimizationConfig.replace(OptimizationSnapshot.noneRequested().with(OptimizationId.EARLY_ALPHA,true));
        }
    }
}

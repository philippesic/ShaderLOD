package com.pp.shaderlod.tuning;

import com.google.gson.JsonParser;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.benchmark.core.*;
import com.pp.shaderlod.measurement.FrameMeasurement;
import com.pp.shaderlod.optimization.OptimizationSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TuningBenchmarkExportTest {
    @TempDir Path directory;
    private TuningJournal journal()throws Exception {
        var original=new TuningJournal.Original("makeup-ultra-fast-shaders",false,LodPreset.QUALITY,
            OptimizationSnapshot.noneRequested(),true,120,90,"Original.zip",new TuningSourceIdentity("Original.zip","Generated.zip","state.json","digest"));
        return new TuningJournal(directory,original,Map.of("resolution","Dimensions[width=1920, height=1200]","render_distance","12"),300,false);
    }
    @Test void postWindowExportRetainsChronologyShortTailAndSeparateUnknownWork()throws Exception {
        var j=journal();j.sessionId="one-JVM";j.confirmation=new TuningConfirmation("pose-8");j.confirmation.searched("pose-0");
        var policy=new TuningPolicy.Configuration(Map.of()).forCase("pose-0","masterOn-request-reload");
        var rows=new ArrayList<FrameMeasurement.Sample>();
        for(int i=0;i<100;i++)rows.add(new FrameMeasurement.Sample(i,4,10_000_000L,2L,null,null,null,"performance: unavailable"));
        j.samples("search","PHASE-0",0,rows);
        j.capture("search",0,0,rows,policy,new BenchmarkAnalysis.Stratum(Map.of("case","pose-0")),BenchmarkResult.Stage.SEARCH,
            false,true,OptimizationSnapshot.noneRequested(),3,"minecraft:overworld",Map.of("AA","native"),12,"");
        j.finish("INCOMPLETE",OptimizationSnapshot.noneRequested());
        var root=JsonParser.parseString(Files.readString(j.directory.resolve("window-0.json"))).getAsJsonObject();
        var window=root.getAsJsonObject("manifest").getAsJsonArray("windows").get(0).getAsJsonObject();
        assertEquals("UNKNOWN",window.get("activation").getAsString());
        assertEquals("PERFORMANCE",window.get("observerMode").getAsString());
        assertEquals(100,window.getAsJsonArray("samples").size());
        assertEquals(0,window.getAsJsonArray("samples").get(0).getAsJsonObject().get("frameId").getAsInt());
        assertEquals(99,window.getAsJsonArray("samples").get(99).getAsJsonObject().get("frameId").getAsInt());
        assertTrue(window.getAsJsonArray("samples").get(0).getAsJsonObject().get("gpuElapsedNs").isJsonNull());
        assertTrue(window.getAsJsonArray("samples").get(0).getAsJsonObject().get("renderThreadCpuNs").isJsonNull());
        assertTrue(window.getAsJsonObject("statistics").get("tailStatus").getAsString().contains("descriptive"));
        assertEquals(1920,window.getAsJsonObject("configuration").get("viewportWidth").getAsInt());
        assertTrue(root.getAsJsonArray("qualificationBlockers").size()>0);
        var index=JsonParser.parseString(Files.readString(j.directory.resolve("benchmark-result.json"))).getAsJsonObject().getAsJsonObject("manifest");
        assertEquals(0,index.getAsJsonArray("windows").get(0).getAsJsonObject().getAsJsonArray("samples").size());
        assertEquals("window-0.json",index.getAsJsonArray("evidenceLinks").get(0).getAsString());
        assertEquals("RESTORED",index.getAsJsonObject("recovery").get("status").getAsString());
        assertTrue(Files.readString(j.directory.resolve("samples.csv")).startsWith("trial,window,pose,frame,epoch"));
    }
    @Test void failedRestorationRetainsJournalOwnerAndNeverExportsReleased()throws Exception {
        var j=journal();j.parkRecovery("baseline restoration failed",3);
        var manifest=JsonParser.parseString(Files.readString(j.directory.resolve("benchmark-result.json"))).getAsJsonObject().getAsJsonObject("manifest");
        assertEquals("FAILED",manifest.getAsJsonObject("recovery").get("status").getAsString());
        assertFalse(manifest.getAsJsonObject("recovery").get("ownershipReleased").getAsBoolean());
        assertEquals("PENDING",TuningJournal.properties(j.activeFile).getProperty("state"));
    }
}

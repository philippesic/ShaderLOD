package com.pp.shaderlod.tuning;

import com.pp.shaderlod.optimization.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TuningContinuationTest {
    @Test void continuationKeepsPriorValidatedWinnerButNoSamplesAndInvalidatesChangedScope() {
        var winner=OptimizationSnapshot.noneRequested().with(OptimizationId.QUAD_COMMAND_COMPACTION,true).with(OptimizationId.SHADOW_ADMISSION_CACHE,true);
        var pass=new TuningContinuation("scope",List.of("pair","cloud"),new Properties());
        pass.completed.add("pair");pass.originalRecovery.setProperty("preset","QUALITY");pass.originalRecovery.setProperty("window_fps","120");var saved=pass.saved(winner,true);
        var resumed=new TuningContinuation("scope",List.of("pair","cloud"),saved);
        assertTrue(resumed.resumed);assertEquals(Set.of("pair"),resumed.completed);assertEquals(winner,resumed.incumbent);
        assertEquals("QUALITY",resumed.originalRecovery.getProperty("preset"));assertEquals("120",resumed.originalRecovery.getProperty("window_fps"));
        // The serialized schema allows canonical flag IDs such as shadow_sample_budget,
        // but no timing/sample/decision payload. Assert the exact field boundary.
        var allowed=new HashSet<>(Set.of("schema","scope","completed","validated_incumbent","incumbent_policy","recovery.preset","recovery.window_fps"));
        for(var id:OptimizationId.values())allowed.add("incumbent."+id.key());
        assertEquals(allowed,saved.stringPropertyNames());
        var poisoned=new Properties();poisoned.putAll(saved);
        poisoned.setProperty("old.gain_ms","-999");poisoned.setProperty("samples.frames","0");poisoned.setProperty("decisions.best_candidate","cloud");
        var ignoresOldTiming=new TuningContinuation("scope",List.of("pair","cloud"),poisoned);
        assertEquals(winner,ignoresOldTiming.incumbent);assertEquals(Set.of("pair"),ignoresOldTiming.completed);
        assertEquals(saved,ignoresOldTiming.saved(OptimizationSnapshot.allRequested(),false));
        // An unsuccessful later segment cannot replace a validated incumbent with an unvalidated candidate.
        var later=new TuningContinuation("scope",List.of("pair","cloud"),resumed.saved(OptimizationSnapshot.allRequested(),false));
        assertEquals(winner,later.incumbent);
        var changed=new TuningContinuation("other-device-or-options",List.of("pair","cloud"),saved);
        assertFalse(changed.resumed);assertTrue(changed.completed.isEmpty());assertEquals(OptimizationSnapshot.noneRequested(),changed.incumbent);
        var changedInventory=new TuningContinuation("scope",List.of("renamed"),saved);
        assertFalse(changedInventory.resumed);assertTrue(changedInventory.completed.isEmpty());
    }
    @Test void stableScopeIgnoresMapInsertionOrderButChangesWithSourceOptionsOrApplicability() {
        assertEquals(TuningContinuation.scope(Map.of("source","a","native_options","x")),TuningContinuation.scope(new TreeMap<>(Map.of("native_options","x","source","a"))));
        assertNotEquals(TuningContinuation.scope(Map.of("source","a")),TuningContinuation.scope(Map.of("source","b")));
        assertNotEquals(TuningContinuation.scope(Map.of("applicability","unknown")),TuningContinuation.scope(Map.of("applicability","declared")));
    }
    @Test void malformedIncumbentCannotSilentlyBecomeAFalseFlag() {
        var saved=new Properties();saved.setProperty("schema","1");saved.setProperty("scope","s");saved.setProperty("completed","pair");saved.setProperty("incumbent.quad_command_compaction","yes");
        var result=new TuningContinuation("s",List.of("pair"),saved);assertFalse(result.resumed);assertTrue(result.completed.isEmpty());
    }
    @Test void independentHumanCoreFlagsAndSceneChangesStartANewCampaign() {
        var original=OptimizationSnapshot.noneRequested();var winner=original.with(OptimizationId.QUAD_COMMAND_COMPACTION,true).with(OptimizationId.SHADOW_ADMISSION_CACHE,true);
        var first=new TuningContinuation("world-and-camera-A",List.of("pair","cloud"),new Properties());first.completed.add("pair");
        first.originalRecovery.setProperty("enabled","false");first.originalRecovery.setProperty("preset","QUALITY");
        for(var id:OptimizationId.values())first.originalRecovery.setProperty(id.key(),"false");
        var saved=first.saved(winner,true);var resumed=new TuningContinuation("world-and-camera-A",List.of("pair","cloud"),saved);
        assertTrue(resumed.acceptsStart(true,"PERFORMANCE",winner));
        assertTrue(resumed.acceptsStart(false,"QUALITY",original));
        assertFalse(resumed.acceptsStart(true,"QUALITY",winner));
        assertFalse(resumed.acceptsStart(true,"PERFORMANCE",winner.with(OptimizationId.WATER_WAVE_BUDGET,true)));
        assertFalse(resumed.acceptsStart(false,"QUALITY",original.with(OptimizationId.AO_BUDGET,true)));
        assertFalse(new TuningContinuation("different-world-or-camera-B",List.of("pair","cloud"),saved).resumed);
    }
    @Test void incompleteNativePerformanceTerminalResumesAnOriginalQualityCampaign() {
        var none=OptimizationSnapshot.noneRequested();var first=new TuningContinuation("scope",List.of("pair","cloud"),new Properties());first.completed.add("pair");
        first.originalRecovery.setProperty("enabled","false");first.originalRecovery.setProperty("preset","QUALITY");first.originalRecovery.setProperty("window_fps","120");
        for(var id:OptimizationId.values())first.originalRecovery.setProperty(id.key(),"false");
        first.terminal(false,"PERFORMANCE",none);var resumed=new TuningContinuation("scope",List.of("pair","cloud"),first.saved(none,false));
        assertTrue(resumed.resumed);assertTrue(resumed.acceptsStart(false,"PERFORMANCE",none));
        assertEquals(Set.of("pair"),resumed.completed);assertEquals("QUALITY",resumed.originalRecovery.getProperty("preset"));assertEquals("120",resumed.originalRecovery.getProperty("window_fps"));
        assertFalse(resumed.validatedIncumbent);assertEquals(none,resumed.incumbent);
        assertFalse(resumed.acceptsStart(false,"PERFORMANCE",none.with(OptimizationId.AO_BUDGET,true)));
    }
    @Test void externalResourceContentsCannotResumeUsingOnlyAnUnchangedPackId() {
        assertTrue(TuningContinuation.resourcesAllowResume(List.of("vanilla","mod_resources")));
        var before=List.of("vanilla","mod_resources","file/PBR.zip");
        var afterContentEditWithSameId=List.of("vanilla","mod_resources","file/PBR.zip");
        assertEquals(before,afterContentEditWithSameId);
        assertFalse(TuningContinuation.resourcesAllowResume(before));
        assertFalse(TuningContinuation.resourcesAllowResume(afterContentEditWithSameId));
        assertFalse(TuningContinuation.resourcesAllowResume(List.of("vanilla","file/PBR.zip")));
    }
}

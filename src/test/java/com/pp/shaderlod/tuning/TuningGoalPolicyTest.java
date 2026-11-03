package com.pp.shaderlod.tuning;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.pp.shaderlod.optimization.OptimizationId.*;
import static org.junit.jupiter.api.Assertions.*;
class TuningGoalPolicyTest {
    @Test void goalsKeepNativeQualityOrdinalAndActualBalancedEngineSeparate() {
        assertEquals(3,LodPreset.QUALITY.ordinal());assertEquals(LodPreset.PERFORMANCE,TuningGoal.QUALITY.enginePreset());
        assertEquals(LodPreset.BALANCED,TuningGoal.BALANCED.enginePreset());assertEquals(LodPreset.PERFORMANCE,TuningGoal.PERFORMANCE.enginePreset());
        assertTrue(TuningGoal.QUALITY.label().contains("exact changes only"));
        assertEquals(300,TuningRequest.quick(TuningGoal.QUALITY).budgetSeconds());assertEquals(3600,TuningRequest.full(TuningGoal.BALANCED).budgetSeconds());
        assertEquals(60,new TuningRequest(TuningGoal.PERFORMANCE,1,false).budgetSeconds());assertEquals(900,new TuningRequest(TuningGoal.PERFORMANCE,9999,false).budgetSeconds());
    }
    @Test void qualityUsesAnExplicitExactSetAndChecksTheWholeCombinedVector() {
        var expected=Set.of(QUAD_COMMAND_COMPACTION,INDEX_SCALAR_CACHE,SHADOW_ADMISSION_CACHE,BINDING_CERTIFICATE_CACHE,BINDING_DIGEST_STREAMING,ZERO_EMISSION_ARITHMETIC);
        for(var id:OptimizationId.values())assertEquals(expected.contains(id),TuningTierPolicy.allowsId(TuningGoal.QUALITY,id),id.key());
        var exact=OptimizationSnapshot.noneRequested().with(QUAD_COMMAND_COMPACTION,true).with(SHADOW_ADMISSION_CACHE,true);
        assertTrue(TuningTierPolicy.rejectedVector(TuningGoal.QUALITY,"complementary-unbound",exact,Map.of()).isEmpty());
        // An individually allowed addition may never carry an earlier approximate incumbent.
        assertFalse(TuningTierPolicy.rejectedVector(TuningGoal.QUALITY,"complementary-unbound",exact.with(SHADOW_SAMPLE_BUDGET,true),Map.of()).isEmpty());
        assertNotNull(TuningTierPolicy.rejection(TuningGoal.PERFORMANCE,"unknown",AO_BUDGET,Map.of()));
    }
    @Test void balancedHasItsOwnPolicyAndAllGoalsRetainTheCompleteBoundedInventory() {
        assertTrue(TuningTierPolicy.allowsId(TuningGoal.BALANCED,AO_BUDGET));assertFalse(TuningTierPolicy.allowsId(TuningGoal.BALANCED,QUAD_COMMAND_COMPACTION));
        assertFalse(TuningTierPolicy.allowsId(TuningGoal.BALANCED,SPATIAL_LIGHTING_RATE));
        for(var goal:TuningGoal.values())assertEquals(TuningCandidates.ordered("complementary-unbound"),TuningCandidates.ordered("complementary-unbound",goal));
        var effects=Map.of("opaque_lighting.mode","FULL");
        assertTrue(TuningTierPolicy.rejection(TuningGoal.PERFORMANCE,"complementary-unbound",SPATIAL_LIGHTING_REUSE,Map.of(),effects).startsWith("INACTIVE_EFFECT_POLICY"));
    }
    @Test void balancedContinuationAcceptsOnlyItsActualSavedEngineAndScopesGoalsIndependently() {
        var flags=OptimizationSnapshot.noneRequested().with(ZERO_EMISSION_ARITHMETIC,true);
        var first=new TuningContinuation("balanced",List.of("exact"),new Properties(),"BALANCED");first.completed.add("exact");
        var resumed=new TuningContinuation("balanced",List.of("exact"),first.saved(flags,true),"BALANCED");
        assertTrue(resumed.acceptsStart(true,"BALANCED",flags));assertFalse(resumed.acceptsStart(true,"PERFORMANCE",flags));
        assertNotEquals(TuningContinuation.scope(Map.of("goal","QUALITY")),TuningContinuation.scope(Map.of("goal","PERFORMANCE")));
    }
}

package com.pp.shaderlod.tuning;

import com.google.gson.JsonParser;
import com.pp.shaderlod.optimization.OptimizationSnapshot;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.pp.shaderlod.optimization.OptimizationId.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exact native choices must not spend a trial on a compiled-out or rounded-away request. */
class TuningInactiveWorkTest {
    private Map<String,String> nativeOptions(String resource) throws Exception {
        try(var in=getClass().getResourceAsStream("/assets/shaderlod/"+resource)) {
            assertNotNull(in,resource);
            var values=new TreeMap<String,String>();
            JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject()
                .entrySet().forEach(e->values.put(e.getKey(),e.getValue().getAsString()));
            return values;
        }
    }
    @Test void photonNativeSmoothReflectionsDoNotQualifyItsAbsentRoughRayBudget() throws Exception {
        var nativeMap=nativeOptions("photonquad/native366.json");
        assertEquals("true",nativeMap.get("ENVIRONMENT_REFLECTIONS"));
        assertEquals("true",nativeMap.get("SSR_ROUGHNESS_SUPPORT"));
        assertEquals("false",nativeMap.get("SPECULAR_MAPPING"));
        assertEquals(TuningEligibility.State.INACTIVE_NATIVE_OPTION,
            TuningEligibility.classify("photon",REFLECTION_RAY_BUDGET,nativeMap).state());
        var flags=OptimizationSnapshot.noneRequested().with(REFLECTION_RAY_BUDGET,true);
        assertFalse(TuningTierPolicy.rejectedVector(TuningGoal.PERFORMANCE,"photon",flags,nativeMap).isEmpty());
        assertNull(TuningTierPolicy.rejection(TuningGoal.PERFORMANCE,"photon",REFLECTION_TRACE_BUDGET,nativeMap),
            "Smooth/water trace work is a separate consumer");
    }
    @Test void knownDisabledRoughPrerequisitesAreNotConfusedWithUnknownOptions() {
        for(String key:List.of("SSR_ROUGHNESS_SUPPORT","SPECULAR_MAPPING")) {
            assertEquals(TuningEligibility.State.INACTIVE_NATIVE_OPTION,
                TuningEligibility.classify("photon",REFLECTION_RAY_BUDGET,Map.of(key,"false")).state());
            for(String value:List.of("true","0","unavailable"))
                assertEquals(TuningEligibility.State.DECLARED_CONSUMER,
                    TuningEligibility.classify("photon",REFLECTION_RAY_BUDGET,Map.of(key,value)).state());
        }
        assertEquals(TuningEligibility.State.DECLARED_CONSUMER,
            TuningEligibility.classify("photon",REFLECTION_RAY_BUDGET,Map.of()).state());
    }
    @Test void skyOnlyRoughReflectionsRemainEligibleWithoutEnvironmentReflections() {
        var skyOnly=Map.of("ENVIRONMENT_REFLECTIONS","false","SKY_REFLECTIONS","true",
            "SSR_ROUGHNESS_SUPPORT","true","SPECULAR_MAPPING","true");
        assertEquals(TuningEligibility.State.DECLARED_CONSUMER,
            TuningEligibility.classify("photon",REFLECTION_RAY_BUDGET,skyOnly).state());
        assertNull(TuningTierPolicy.rejection(TuningGoal.PERFORMANCE,"photon",REFLECTION_RAY_BUDGET,skyOnly));
    }
    @Test void enablingTheRealRoughPrerequisiteDoesNotInventPomOrNormalRequirements() throws Exception {
        var optional=nativeOptions("photonquad/native366.json");
        optional.put("SPECULAR_MAPPING","true");
        assertEquals("false",optional.get("NORMAL_MAPPING"));assertEquals("false",optional.get("POM"));
        assertNull(TuningTierPolicy.rejection(TuningGoal.PERFORMANCE,"photon",REFLECTION_RAY_BUDGET,optional));
    }
    @Test void photonGtaoThreeDoesNotBecomeAnActiveBudgetBenchmarkByRounding() throws Exception {
        var nativeMap=nativeOptions("photonquad/native366.json");
        assertEquals("SHADER_AO_GTAO",nativeMap.get("SHADER_AO"));assertEquals("3",nativeMap.get("GTAO_HORIZON_STEPS"));
        for(var goal:List.of(TuningGoal.PERFORMANCE,TuningGoal.BALANCED))
            assertTrue(TuningTierPolicy.rejection(goal,"photon",AO_BUDGET,nativeMap).startsWith("NO_WORK_REDUCTION:"));
        var larger=new TreeMap<>(nativeMap);larger.put("GTAO_HORIZON_STEPS","4");
        assertNull(TuningTierPolicy.rejection(TuningGoal.PERFORMANCE,"photon",AO_BUDGET,larger));
        var ssao=new TreeMap<>(nativeMap);ssao.put("SHADER_AO","SHADER_AO_SSAO");
        assertNull(TuningTierPolicy.rejection(TuningGoal.PERFORMANCE,"photon",AO_BUDGET,ssao));
        var missing=new TreeMap<>(nativeMap);missing.remove("GTAO_HORIZON_STEPS");
        assertNull(TuningTierPolicy.rejection(TuningGoal.PERFORMANCE,"photon",AO_BUDGET,missing));
        assertNull(TuningTierPolicy.rejection(TuningGoal.PERFORMANCE,"photon",AO_DEPTH_REUSE,nativeMap));
    }
    @Test void unboundNativeFourSampleMinimumDoesNotSuppressOtherAoWork() throws Exception {
        var nativeMap=nativeOptions("unboundquad/native398.json");
        assertEquals("2",nativeMap.get("SSAO_QUALI_DEFINE"));
        for(var goal:List.of(TuningGoal.PERFORMANCE,TuningGoal.BALANCED))
            assertTrue(TuningTierPolicy.rejection(goal,"complementary-unbound",AO_BUDGET,nativeMap).startsWith("NO_WORK_REDUCTION:"));
        var larger=new TreeMap<>(nativeMap);larger.put("SSAO_QUALI_DEFINE","3");
        assertNull(TuningTierPolicy.rejection(TuningGoal.PERFORMANCE,"complementary-unbound",AO_BUDGET,larger));
        assertNull(TuningTierPolicy.rejection(TuningGoal.PERFORMANCE,"complementary-unbound",AO_DEPTH_REUSE,nativeMap));
        var missing=new TreeMap<>(nativeMap);missing.remove("SSAO_QUALI_DEFINE");
        assertNull(TuningTierPolicy.rejection(TuningGoal.PERFORMANCE,"complementary-unbound",AO_BUDGET,missing));
    }
    @Test void blissSevenIsARealBudgetOnlyInTheSearchTierThatCanReduceIt() throws Exception {
        var nativeMap=nativeOptions("blissquad/native463.json");
        assertTrue(TuningTierPolicy.rejection(TuningGoal.BALANCED,"bliss-shader",AO_BUDGET,nativeMap).startsWith("NO_WORK_REDUCTION:"));
        assertNull(TuningTierPolicy.rejection(TuningGoal.PERFORMANCE,"bliss-shader",AO_BUDGET,nativeMap));
        assertNull(TuningTierPolicy.rejection(TuningGoal.PERFORMANCE,"bliss-shader",AO_DEPTH_REUSE,nativeMap));
    }
    @Test void noReductionIsNotAClaimThatTheSourceConsumerIsUnsupported() throws Exception {
        var nativeMap=nativeOptions("photonquad/native366.json");
        assertEquals(TuningEligibility.State.DECLARED_CONSUMER,TuningEligibility.classify("photon",AO_BUDGET,nativeMap).state());
        var flags=OptimizationSnapshot.noneRequested().with(AO_BUDGET,true).with(BINDING_CERTIFICATE_CACHE,true);
        var reasons=TuningTierPolicy.rejectedVector(TuningGoal.PERFORMANCE,"photon",flags,nativeMap);
        assertEquals(1,reasons.size());assertTrue(reasons.getFirst().startsWith("ao_budget: NO_WORK_REDUCTION:"));
        assertEquals("3",nativeMap.get("GTAO_HORIZON_STEPS"));
    }
}

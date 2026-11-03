package com.pp.shaderlod.tuning;
import com.pp.shaderlod.optimization.OptimizationId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TuningEligibilityTest {
    @Test void everyCanonicalRequestHasAnHonestClassificationForEveryFamily() {
        assertEquals(9,TuningEligibility.FAMILIES.size());
        for(String family:TuningEligibility.FAMILIES)for(var id:OptimizationId.values()) {
            var result=TuningEligibility.classify(family,id);assertNotNull(result.state());assertFalse(result.reason().isBlank());
        }
    }
    @Test void unavailableReasonsKeepDuplicateRejectedAndMissingProducerSeparate() {
        assertEquals(TuningEligibility.State.DUPLICATE_LABEL,TuningEligibility.classify("photon",OptimizationId.SHADOW_PREFIX).state());
        assertEquals(TuningEligibility.State.UNQUALIFIED_PROTOTYPE,TuningEligibility.classify("photon",OptimizationId.PHYSICAL_RENDER_SCALE).state());
        assertEquals(TuningEligibility.State.UNSUPPORTED,TuningEligibility.classify("photon",OptimizationId.SPATIAL_SHADOW_REUSE).state());
        assertEquals(TuningEligibility.State.UNKNOWN,TuningEligibility.classify("unrecognized",OptimizationId.AO_BUDGET).state());
    }
}

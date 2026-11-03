package com.pp.shaderlod.optimization.makeuppresence;

import com.pp.shaderlod.optimization.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MakeUpTerrainPresenceTest {
    private static String source() {
        String text = "uniform int shaderlodOptEarlyAlpha;\nbool shaderlodMakeupEarlyAlphaActive(){return false;}\n"
            + "vec4 nativeSample(vec3 p){return shadow2D(shadowtex1,p);}\n";
        for (var id : MakeUpTerrainPresence.CONTROLS.subList(1, 4))
            text += "uniform int " + id.uniform() + ";\nbool query_" + id.key()
                + "(){return shaderlod_optimization_enabled(" + id.uniform() + ");}\n";
        return text;
    }
    @Test void onlyAbsentShadowControlsChangeAndNativeSamplingAndAlphaStayIntact() {
        String result = MakeUpTerrainPresence.specialize(source(), 0, "0");
        assertNotNull(result); assertTrue(MakeUpTerrainPresence.transformed(result));
        assertTrue(result.startsWith(source().substring(0, source().indexOf("uniform int shaderlodOptShadowSampleBudget"))));
        assertFalse(MakeUpTerrainPresence.transformed(result.replace("const int shaderlodOptShadowSampleBudget = 0;",
            "uniform int shaderlodOptShadowSampleBudget;")));
    }
    @Test void requestedControlsNeverBecomeAbsentBecauseTheirRuntimeOwnerIsMissing() {
        var empty = OptimizationSnapshot.noneRequested(); assertEquals(0, MakeUpTerrainPresence.mask(empty));
        for (var id : MakeUpTerrainPresence.CONTROLS) {
            var requested = empty.with(id, true);
            assertTrue(MakeUpTerrainPresence.changesPresence(empty, requested));
            assertTrue(MakeUpTerrainPresence.changesPresence(requested, empty));
            assertNull(MakeUpTerrainPresence.specialize(source(), MakeUpTerrainPresence.mask(requested), "0"));
        }
        assertFalse(MakeUpTerrainPresence.changesPresence(empty, empty.with(OptimizationId.AO_BUDGET, true)));
        assertNull(MakeUpTerrainPresence.specialize(source(), 0, "1"));
    }
    @Test void changedOrDuplicateModOwnedDeclarationsRefuseInsteadOfRewritingArbitraryText() {
        String declaration = "uniform int shaderlodOptShadowSampleBudget;";
        assertNull(MakeUpTerrainPresence.specialize(source().replace(declaration, "uniform float shaderlodOptShadowSampleBudget;"), 0, "0"));
        assertNull(MakeUpTerrainPresence.specialize(source() + declaration, 0, "0"));
        assertNotNull(MakeUpTerrainPresence.specialize(source().replace("shaderlod_optimization_enabled(shaderlodOptTemporalShadowFilter)", "false"), 0, "0"),
            "Native preprocessing can remove this consumer while leaving its declaration");
    }
}

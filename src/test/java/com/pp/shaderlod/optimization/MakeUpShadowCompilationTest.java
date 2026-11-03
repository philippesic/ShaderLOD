package com.pp.shaderlod.optimization;

import com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowCompiledPrograms;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTestFunction;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MakeUpShadowCompilationTest {
    @Test void consistentRegenerationCannotReplaceTheFixedRecipePin() throws Exception {
        try(var in=MakeUpShadowCompiledPrograms.class.getResourceAsStream(
                "/assets/shaderlod/adapters/patches/adapters/makeup-ultra-fast-shaders.json")) {
            assertNotNull(in);var original=in.readAllBytes();
            assertTrue(MakeUpShadowCompiledPrograms.recipeMatches(original));
            original[original.length/2]^=1;
            assertFalse(MakeUpShadowCompiledPrograms.recipeMatches(original));
            assertFalse(MakeUpShadowCompiledPrograms.recipeMatches(null));
        }
    }
    @Test void solidAndCutoutContractsStaySeparate() {
        var greater=new AlphaTest(AlphaTestFunction.GREATER,.1f);
        assertTrue(MakeUpShadowCompiledPrograms.acceptsPass(SodiumPrograms.Pass.TERRAIN,AlphaTest.ALWAYS));
        assertTrue(MakeUpShadowCompiledPrograms.acceptsPass(SodiumPrograms.Pass.TERRAIN_CUTOUT,greater));
        assertFalse(MakeUpShadowCompiledPrograms.acceptsPass(SodiumPrograms.Pass.TERRAIN,greater));
        assertFalse(MakeUpShadowCompiledPrograms.acceptsPass(SodiumPrograms.Pass.TERRAIN_CUTOUT,AlphaTest.ALWAYS));
        for(float value:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-.1f,1.1f})
            assertFalse(MakeUpShadowCompiledPrograms.acceptsPass(SodiumPrograms.Pass.TERRAIN_CUTOUT,
                new AlphaTest(AlphaTestFunction.GREATER,value)));
    }
    @Test void solidAdmissionDoesNotRelaxTheKnownCutoutRewrite() {
        String protocol="uniform int shaderlodMakeupColoredShadowBudgetPermission; uniform int shaderlodOptShadowSampleBudget;";
        assertTrue(MakeUpShadowCompiledPrograms.acceptsTransformed(protocol,AlphaTest.ALWAYS));
        assertFalse(MakeUpShadowCompiledPrograms.acceptsTransformed(protocol,new AlphaTest(AlphaTestFunction.GREATER,.1f)),
            "cutout requires the existing alpha rewrite proof, not just shadow tokens");
        assertFalse(MakeUpShadowCompiledPrograms.acceptsTransformed("native fragment",AlphaTest.ALWAYS));
    }
}

package com.pp.shaderlod.optimization;

import com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpPostSampleAlphaSource;
import java.nio.charset.StandardCharsets;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTestFunction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MakeUpPostSampleAlphaSourceTest {
    private static final AlphaTest CUTOUT = new AlphaTest(AlphaTestFunction.GREATER, .1f);
    private static String nativeStage() throws Exception {
        try (var in = MakeUpPostSampleAlphaSourceTest.class.getResourceAsStream(
                "/com/pp/shaderlod/optimization/g03-native-cutout.glsl")) {
            assertNotNull(in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    @Test void acceptedStageKeepsAllSamplesBeforeRejectionAndRetainsNativeOutputTest() throws Exception {
        String source = nativeStage();
        String result = MakeUpPostSampleAlphaSource.rewrite(source, CUTOUT);
        assertNotNull(result);
        int early = result.indexOf("if (!(clamp(blockColor.a, 0.0f, 1.0f) > iris_currentAlphaTest))");
        assertTrue(early > result.indexOf("shadowValue = get_colored_shadow"));
        assertTrue(early > result.indexOf("fog_texture = texture(gaux4"));
        assertTrue(early < result.indexOf("float block_luma = luma(blockColor.rgb)"));
        assertTrue(early < result.indexOf("blockColor.rgb = endPortal()"));
        assertTrue(early < result.indexOf("shadowValue = mix(shadowValue"));
        assertTrue(early < result.indexOf("float final_gloss_power"));
        assertFalse(java.util.regex.Pattern.compile("\\b(texture|shadow2D|textureLod|dFdx|dFdy|fwidth)\\s*\\(")
            .matcher(result.substring(early)).find(), "No derivative-dependent work may follow new kill");
        assertEquals(source.substring(0, source.indexOf("void main()")),
            result.substring(0, result.indexOf("void main()")), "All shadow/math helpers remain native");
        String nativeSuffix = source.substring(source.indexOf("if (isEyeInWater == 0)"));
        assertTrue(result.endsWith(nativeSuffix), "Native fog arithmetic, clamp, write and final test remain intact");
        assertEquals(2, java.util.regex.Pattern.compile("discard;").matcher(result).results().count());
    }
    @Test void completeSourceMutationAndDuplicateOrChangedLookupAlwaysRefuse() throws Exception {
        String source = nativeStage();
        assertNull(MakeUpPostSampleAlphaSource.rewrite(source.replace("73.334f", "73.333f"), CUTOUT));
        assertNull(MakeUpPostSampleAlphaSource.rewrite(source.replace("texture(gaux4,", "texture(shadowcolor0,"), CUTOUT));
        assertNull(MakeUpPostSampleAlphaSource.rewrite(source + "\n", CUTOUT));
        assertNull(MakeUpPostSampleAlphaSource.rewrite(source.replace("iris_currentAlphaTest", "otherAlphaTest"), CUTOUT));
    }
    @Test void opaqueAndOtherNativePoliciesStayUntouched() throws Exception {
        String source = nativeStage();
        for (AlphaTest alpha : new AlphaTest[]{AlphaTest.ALWAYS, new AlphaTest(AlphaTestFunction.LESS, .1f),
                new AlphaTest(AlphaTestFunction.GREATER, .2f), new AlphaTest(AlphaTestFunction.GREATER, Float.NaN), null})
            assertNull(MakeUpPostSampleAlphaSource.rewrite(source, alpha));
        assertNull(MakeUpPostSampleAlphaSource.rewrite(null, CUTOUT));
    }
    @Test void hostBoundaryNeverEnablesGenericLinuxOrMac() {
        String renderer = "Apple M1 (G13G B1)", driver = "4.6 (Core Profile) Mesa 25.1.0-asahi20250221";
        assertTrue(MakeUpPostSampleAlphaSource.host("Linux", "aarch64", renderer, driver));
        assertFalse(MakeUpPostSampleAlphaSource.host("Mac OS X", "aarch64", renderer, driver));
        assertFalse(MakeUpPostSampleAlphaSource.host("Linux", "x86_64", renderer, driver));
        assertFalse(MakeUpPostSampleAlphaSource.host("Linux", "aarch64", "Apple M2", driver));
        assertFalse(MakeUpPostSampleAlphaSource.host("Linux", "aarch64", renderer, driver + " changed"));
        assertFalse(MakeUpPostSampleAlphaSource.host(null, null, null, null));
    }
}

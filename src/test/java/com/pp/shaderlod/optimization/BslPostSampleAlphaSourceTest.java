package com.pp.shaderlod.optimization;

import com.pp.shaderlod.optimization.bslearlyalpha.BslPostSampleAlphaSource;
import java.nio.charset.StandardCharsets;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTestFunction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BslPostSampleAlphaSourceTest {
    private static String nativeStage() throws Exception {
        try (var in = BslPostSampleAlphaSourceTest.class.getResourceAsStream(
                "/com/pp/shaderlod/optimization/g03-bsl-native-cutout.glsl")) {
            assertNotNull(in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    @Test void allSamplingAndNativeFinalAlphaPolicyRemainBeforeAndAfterTheNewSeam() throws Exception {
        String source = nativeStage();
        String result = BslPostSampleAlphaSource.rewrite(source, new AlphaTest(AlphaTestFunction.GREATER, .1f));
        assertNotNull(result);
        assertTrue(result.startsWith(source.substring(0, source.indexOf("void GetLighting("))),
            "Atlas/shadow helpers and their control flow remain byte identical");
        int early = result.indexOf("if (!(shaderlodNativeAlpha > iris_currentAlphaTest))");
        assertTrue(early > result.indexOf("shadow = GetShadow(worldPos, normal, NoL, basicSubsurface, lightmap.y);"));
        assertTrue(early < result.indexOf("vec3 hsv = RGB2HSV(albedo.rgb)"));
        assertTrue(early < result.indexOf("albedo.rgb = pow(albedo.rgb, vec3(2.2f))"));
        String lightingSuffix = result.substring(early, result.indexOf("float GetNoHSquared("));
        assertFalse(java.util.regex.Pattern.compile("\\b(texture\\w*|shadow2D\\w*|dFdx|dFdy|fwidth)\\s*\\(")
            .matcher(lightingSuffix).find(), "No texture/derivative work after the added test");
        assertTrue(result.endsWith(source.substring(source.indexOf("iris_FragData0 = albedo;"))));
        assertEquals(2, java.util.regex.Pattern.compile("discard;").matcher(result).results().count());
    }
    @Test void survivingRgbOperationsAndOriginalAlphaArgumentArePreservedExactly() throws Exception {
        String source = nativeStage();
        String result = BslPostSampleAlphaSource.rewrite(source, new AlphaTest(AlphaTestFunction.GREATER, .5f));
        assertNotNull(result, "Actual native uniform is retained; no invented fixed threshold");
        String helpers = source.substring(source.indexOf("vec3 RGB2HSV("), source.indexOf("uniform float framemod8;"));
        assertTrue(result.contains(helpers + "void GetLighting("));
        String main = source.substring(source.indexOf("void main()"))
            .replace("vec3 hsv = RGB2HSV(albedo.rgb);\nemission *= GetHardcodedEmission(albedo.rgb, hsv);\n", "")
            .replace("albedo.rgb = pow(albedo.rgb, vec3(2.2f));\n", "")
            .replace("parallaxShadow, emission, subsurface, basicSubsurface);",
                "parallaxShadow, emission, subsurface, basicSubsurface, albedo.a);");
        assertEquals(main, result.substring(result.indexOf("void main()")));
        assertTrue(result.contains(source.substring(source.indexOf("shadow *= parallaxShadow;"),
            source.indexOf("float GetNoHSquared("))), "Surviving native lighting suffix is unchanged");
    }
    @Test void changedSourceOrUnprovedAlphaPolicyAlwaysStaysNative() throws Exception {
        String source = nativeStage();
        AlphaTest greater = new AlphaTest(AlphaTestFunction.GREATER, .1f);
        assertNull(BslPostSampleAlphaSource.rewrite(source + "\n", greater));
        assertNull(BslPostSampleAlphaSource.rewrite(source.replace("shadow *= parallaxShadow;", "shadow *= 0.5f;"), greater));
        assertNull(BslPostSampleAlphaSource.rewrite(source.replace("iris_currentAlphaTest", "otherAlpha"), greater));
        for (AlphaTest alpha : new AlphaTest[]{null, AlphaTest.ALWAYS,
                new AlphaTest(AlphaTestFunction.LESS, .1f), new AlphaTest(AlphaTestFunction.GREATER, Float.NaN),
                new AlphaTest(AlphaTestFunction.GREATER, Float.POSITIVE_INFINITY),
                new AlphaTest(AlphaTestFunction.GREATER, -1f), new AlphaTest(AlphaTestFunction.GREATER, 2f)})
            assertNull(BslPostSampleAlphaSource.rewrite(source, alpha));
        assertNull(BslPostSampleAlphaSource.rewrite(null, greater));
    }
    @Test void basicRelocatesOnlyTheOneTestAndOriginalAlphaArgument() throws Exception {
        String source = nativeStage();
        String result = BslPostSampleAlphaSource.rewrite(source, new AlphaTest(AlphaTestFunction.GREATER, .1f),
            BslPostSampleAlphaSource.Variant.BASIC);
        assertNotNull(result);
        assertEquals(1, java.util.regex.Pattern.compile("discard;").matcher(result).results().count());
        assertFalse(result.contains("if (!(iris_FragData0.a > iris_currentAlphaTest))"));
        assertTrue(result.indexOf("float skylightSqr") < result.indexOf("if (!(shaderlodNativeAlpha"));
        assertTrue(result.indexOf("vec3 hsv = RGB2HSV(albedo.rgb)") > result.indexOf("void main()"));
        String expected = source.substring(source.indexOf("void main()"))
            .replace("parallaxShadow, emission, subsurface, basicSubsurface);",
                "parallaxShadow, emission, subsurface, basicSubsurface, albedo.a);")
            .replace("if (!(iris_FragData0.a > iris_currentAlphaTest)) {\ndiscard;\n}\n", "");
        assertEquals(expected, result.substring(result.indexOf("void main()")));
        String oldLighting = source.substring(source.indexOf("void GetLighting("), source.indexOf("float GetNoHSquared("));
        String lighting = result.substring(result.indexOf("void GetLighting("), result.indexOf("float GetNoHSquared("));
        assertEquals(oldLighting.replace("float basicSubsurface\n", "float basicSubsurface,\nfloat shaderlodNativeAlpha\n")
            .replace("shadow *= parallaxShadow;", new AlphaTest(AlphaTestFunction.GREATER, .1f)
                .toExpression("shaderlodNativeAlpha", "iris_currentAlphaTest", "") + "\nshadow *= parallaxShadow;"), lighting);
    }
    @Test void maximumIsExactlyTheOldRgbShapeWithOnlyTheRedundantFinalTestRemoved() throws Exception {
        String source = nativeStage();
        AlphaTest alpha = new AlphaTest(AlphaTestFunction.GREATER, .1f);
        assertEquals(BslPostSampleAlphaSource.rewrite(source, alpha).replace(
            "if (!(iris_FragData0.a > iris_currentAlphaTest)) {\ndiscard;\n}\n", ""),
            BslPostSampleAlphaSource.rewrite(source, alpha, BslPostSampleAlphaSource.Variant.MAX));
        assertEquals(BslPostSampleAlphaSource.rewrite(source, alpha),
            BslPostSampleAlphaSource.rewrite(source, alpha, BslPostSampleAlphaSource.Variant.TWO_TEST));
    }
    @Test void relocationProofRejectsConditionalCallAlphaMutationOrNativePolicyChange() throws Exception {
        String source = nativeStage();
        // The pinned main has one unconditional call in a plain scope. Its only
        // conditional ends before the call; only RGB is written before final output.
        String main = source.substring(source.indexOf("void main()"));
        assertEquals(1, java.util.regex.Pattern.compile("GetLighting\\(").matcher(main).results().count());
        assertTrue(main.contains("if (foliage > 0.5f) {\nnewNormal = upVec;\n}"));
        assertFalse(main.contains("albedo.a ="));
        for (var variant : new BslPostSampleAlphaSource.Variant[]{BslPostSampleAlphaSource.Variant.BASIC, BslPostSampleAlphaSource.Variant.MAX}) {
            for (String changed : new String[]{source.replace("GetLighting(albedo.rgb", "if (foliage > 0.5f) GetLighting(albedo.rgb"),
                    source.replace("iris_FragData0 = albedo;", "albedo.a *= .5f;\niris_FragData0 = albedo;"),
                    source.replace("iris_FragData0.a >", "iris_FragData0.a >=")})
                assertNull(BslPostSampleAlphaSource.rewrite(changed, new AlphaTest(AlphaTestFunction.GREATER, .1f), variant));
            assertNull(BslPostSampleAlphaSource.rewrite(source, AlphaTest.ALWAYS, variant));
            assertNull(BslPostSampleAlphaSource.rewrite(source, new AlphaTest(AlphaTestFunction.LESS, .1f), variant));
            String result = BslPostSampleAlphaSource.rewrite(source, new AlphaTest(AlphaTestFunction.GREATER, .1f), variant);
            int early = result.indexOf("if (!(shaderlodNativeAlpha > iris_currentAlphaTest))");
            assertTrue(early > result.indexOf("shadow = GetShadow(worldPos, normal, NoL, basicSubsurface, lightmap.y);"));
            assertEquals(1, java.util.regex.Pattern.compile("discard;").matcher(result).results().count());
            assertFalse(java.util.regex.Pattern.compile("\\b(texture\\w*|shadow2D\\w*|dFdx|dFdy|fwidth)\\s*\\(")
                .matcher(result.substring(early, result.indexOf("float GetNoHSquared("))).find());
        }
        assertNull(BslPostSampleAlphaSource.rewrite(source, new AlphaTest(AlphaTestFunction.GREATER, .1f), null));
    }
}

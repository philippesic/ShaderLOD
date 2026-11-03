package com.pp.shaderlod.optimization.bslearlyalpha;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTestFunction;

/** Unhooked source candidate for one complete native BSL 10.1.8 CUTOUT stage.
 * All atlas/shadow reads retain their native order and control flow. Runtime
 * admission, lifecycle and performance qualification are separate obligations. */
public final class BslPostSampleAlphaSource {
    public static final String NATIVE_CUTOUT_SHA =
        "48b7a6e125b76352413d816392157f462845bb841786a94306b916086a91d299";
    private static final String SIGNATURE = "float basicSubsurface\n) {\n";
    private static final String SEAM = """
shadow = GetShadow(worldPos, normal, NoL, basicSubsurface, lightmap.y);
}
shadow *= parallaxShadow;
""";
    private static final String CALL = "parallaxShadow, emission, subsurface, basicSubsurface);";
    private static final String HSV = """
vec3 hsv = RGB2HSV(albedo.rgb);
emission *= GetHardcodedEmission(albedo.rgb, hsv);
""";
    private static final String GAMMA = "albedo.rgb = pow(albedo.rgb, vec3(2.2f));\n";
    private static final String SKY = "float skylightSqr = lightmap.y * lightmap.y;\n";
    private BslPostSampleAlphaSource() {}

    public enum Variant { TWO_TEST, BASIC, MAX }

    /** Explicit development shapes; the existing two-test rewrite stays the default. */
    public static String rewrite(String nativeFragment, AlphaTest alpha, Variant variant) {
        if (variant == null) return null;
        String maximum = rewrite(nativeFragment, alpha);
        if (maximum == null || variant == Variant.TWO_TEST) return maximum;
        // The full-stage pin also proves the sole GetLighting call is unconditional,
        // its albedo argument is RGB-only, and no alpha write or further sampling
        // occurs between that call and the native output/test suffix.
        String tail = "GetLighting(albedo.rgb, shadow, viewPos, worldPos, normal, lightmap, color.a, NoL, vanillaDiffuse, " + CALL
            + "\nalbedo.rgb = sqrt(max(albedo.rgb, vec3(0.0f)));\n}\niris_FragData0 = albedo;\n";
        String finalTest = "if (!(iris_FragData0.a > iris_currentAlphaTest)) {\ndiscard;\n}\n";
        if (count(nativeFragment, "GetLighting(") != 2 || count(nativeFragment, "discard;") != 1
                || !nativeFragment.endsWith(tail + finalTest + "}\n")) return null;
        String candidate = maximum;
        if (variant == Variant.BASIC) {
            String early = alpha.toExpression("shaderlodNativeAlpha", "iris_currentAlphaTest", "");
            candidate = nativeFragment
                .replace(SIGNATURE, "float basicSubsurface,\nfloat shaderlodNativeAlpha\n) {\n")
                .replace(SEAM, "shadow = GetShadow(worldPos, normal, NoL, basicSubsurface, lightmap.y);\n}\n"
                    + early + "\nshadow *= parallaxShadow;\n")
                .replace(CALL, "parallaxShadow, emission, subsurface, basicSubsurface, albedo.a);");
        }
        // This is relocation of the original uniform policy, not a second test.
        if (count(candidate, finalTest) != 1) return null;
        return candidate.replace(finalTest, "");
    }

    /** Null preserves the native source. This method grants no runtime permission. */
    public static String rewrite(String nativeFragment, AlphaTest alpha) {
        if (nativeFragment == null || alpha == null || alpha.function() != AlphaTestFunction.GREATER
                || !Float.isFinite(alpha.reference()) || alpha.reference() < 0 || alpha.reference() > 1
                || !NATIVE_CUTOUT_SHA.equals(sha(nativeFragment))) return null;
        String late = alpha.toExpression("iris_FragData0.a", "iris_currentAlphaTest", "").replaceAll("\\s+", "");
        if (!nativeFragment.replaceAll("\\s+", "").endsWith(late + "}")) return null;
        for (String part : new String[]{SIGNATURE, SEAM, CALL, HSV, GAMMA, SKY})
            if (count(nativeFragment, part) != 1) return null;

        // These two unchanged RGB-only helpers must be declared before their new call site.
        int helpersStart = nativeFragment.indexOf("vec3 RGB2HSV(vec3 c) {");
        int helpersEnd = nativeFragment.indexOf("uniform float framemod8;", helpersStart);
        if (helpersStart < 0 || helpersEnd <= helpersStart) return null;
        String helpers = nativeFragment.substring(helpersStart, helpersEnd);
        String early = alpha.toExpression("shaderlodNativeAlpha", "iris_currentAlphaTest", "");
        // GetShadow observes neither albedo nor emission. Every surviving invocation
        // therefore receives the same lighting inputs, arithmetic and final alpha test.
        return nativeFragment.replace(helpers, "").replace(HSV, "").replace(GAMMA, "").replace(SKY, "")
            .replace("void GetLighting(\n", helpers + "void GetLighting(\n")
            .replace(SIGNATURE, "float basicSubsurface,\nfloat shaderlodNativeAlpha\n) {\n")
            .replace(SEAM, "shadow = GetShadow(worldPos, normal, NoL, basicSubsurface, lightmap.y);\n}\n"
                + early + "\n" + HSV + GAMMA + SKY + "shadow *= parallaxShadow;\n")
            .replace(CALL, "parallaxShadow, emission, subsurface, basicSubsurface, albedo.a);");
    }

    private static int count(String value, String part) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(part, at)) >= 0; at += part.length()) count++;
        return count;
    }
    private static String sha(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}

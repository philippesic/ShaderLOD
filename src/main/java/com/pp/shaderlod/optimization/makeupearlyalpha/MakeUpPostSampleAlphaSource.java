package com.pp.shaderlod.optimization.makeupearlyalpha;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTestFunction;

/** One complete native MakeUp 9.5g/Iris CUTOUT stage, after native alpha injection.
 * Every atlas, shadow and fog lookup remains before the additional discard.
 * No mutable sampler/filter/coverage assumption authorizes this transformation. */
public final class MakeUpPostSampleAlphaSource {
    public static final String NATIVE_CUTOUT_SHA =
        "4907a2d3622185b18fcd69f1d76f3cf4a3f898b99b403fb340524082f7142ed5";
    private static final String FOG = """
vec3 fog_texture;
if (darknessFactor > 0.01f) {
fog_texture = vec3(0.0f);
} else {
fog_texture = texture(gaux4, gl_FragCoord.xy * vec2(pixelSizeX, pixelSizeY)).rgb;
}
""";
    private static final String SEAM = "float final_gloss_power = glossPower;\n";
    private static final String RGB_SETUP = """
float block_luma = luma(blockColor.rgb);
float isEmissiveEntityLocal = 0.0f;
vec3 finalCandleColor = candleColor;
if (isEmissiveEntityLocal > 0.5f) {
finalCandleColor *= block_luma * 1.5f;
}
if (isEndPortal > 0.5f) {
blockColor.rgb = endPortal();
}
""";
    private static final String SHADOW_MIX = "shadowValue = mix(shadowValue, vec3(1.0f), shadowDiffuse);\n";
    private MakeUpPostSampleAlphaSource() {}

    public static boolean host(String os, String arch, String renderer, String driver) {
        return "Linux".equals(os) && "aarch64".equals(arch)
            && "Apple M1 (G13G B1)".equals(renderer)
            && "4.6 (Core Profile) Mesa 25.1.0-asahi20250221".equals(driver);
    }

    /** Null means native fallback. No partial-source match can authorize the rewrite. */
    public static String rewrite(String nativeFragment, AlphaTest alpha) {
        if (nativeFragment == null || alpha == null || alpha.function() != AlphaTestFunction.GREATER
                || Float.compare(alpha.reference(), 0.1f) != 0
                || !NATIVE_CUTOUT_SHA.equals(sha(nativeFragment))) return null;
        String late = alpha.toExpression("iris_FragData0.a", "iris_currentAlphaTest", "").replaceAll("\\s+", "");
        if (!nativeFragment.replaceAll("\\s+", "").endsWith(late + "}")
                || count(nativeFragment, FOG) != 1 || count(nativeFragment, SEAM) != 1
                || count(nativeFragment, RGB_SETUP) != 1 || count(nativeFragment, SHADOW_MIX) != 1) return null;
        String early = alpha.toExpression("clamp(blockColor.a, 0.0f, 1.0f)", "iris_currentAlphaTest", "");
        // Native lighting writes only RGB. Native final clamp/test and output remain intact.
        // Fog is the last derivative-dependent read; it retains its uniform condition/coordinates.
        return nativeFragment.replace(FOG, "").replace(RGB_SETUP, "").replace(SHADOW_MIX, "")
            .replace(SEAM, FOG + early + "\n" + RGB_SETUP + SHADOW_MIX + SEAM);
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

package com.pp.shaderlod.experiment.makeup;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Exact one-source MakeUp 9.5g/Iris/Sodium vertex seam; unavailable returns null. */
public final class MakeupMergedVertexInterface {
    public static final String NATIVE_SHA256 = "b17c8f78738c9eefaa36c51b2614034ddb01b55c31b3449ce1ecd89d126d048a";
    public static final String OFFSET_ANCHOR = "uniform vec3 u_RegionOffset;\n";
    public static final String POSITION_NATIVE = "_vert_position + u_RegionOffset + _get_draw_translation(_draw_id)";
    public static final String POSITION_MERGED = "_vert_position + shaderlod_makeup_current_region_offset + _get_draw_translation(_draw_id)";
    public static final String MAIN_NATIVE = "void main() {\n\t_vert_init();\n";
    public static final String MAIN_MERGED = "void main() {\n\t_vert_init();\n\tshaderlodMakeupRegionOffsetInit();\n";
    private MakeupMergedVertexInterface() {}

    /** Parent passes the exact frozen UTF-8 region-offset.glsl; unknown source stays native. */
    public static String adapt(String nativeVertex, String frozenHelper) {
        if (nativeVertex == null || frozenHelper == null) return null;
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            if (!NATIVE_SHA256.equals(HexFormat.of().formatHex(digest.digest(nativeVertex.getBytes(StandardCharsets.UTF_8))))) return null;
            if (!HELPER_SHA256.equals(HexFormat.of().formatHex(digest.digest(frozenHelper.getBytes(StandardCharsets.UTF_8))))) return null;
            if (occurrences(nativeVertex, OFFSET_ANCHOR) != 1 || occurrences(nativeVertex, POSITION_NATIVE) != 1 || occurrences(nativeVertex, MAIN_NATIVE) != 1) return null;
            return nativeVertex.replace(OFFSET_ANCHOR, OFFSET_ANCHOR + frozenHelper)
                    .replace(POSITION_NATIVE, POSITION_MERGED).replace(MAIN_NATIVE, MAIN_MERGED);
        } catch (Exception unavailable) { return null; }
    }
    // Filled by the reproducible stage generator before handback.
    public static final String HELPER_SHA256 = "7c771c809d2a3183b39080216f93910d758cde7c3fd7d12ea150e5fffd222ac9";
    private static int occurrences(String text, String needle) {
        int count = 0, start = 0;
        while ((start = text.indexOf(needle, start)) >= 0) { count++; start += needle.length(); }
        return count;
    }
}

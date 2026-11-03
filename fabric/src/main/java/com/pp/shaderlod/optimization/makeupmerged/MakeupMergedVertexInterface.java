package com.pp.shaderlod.optimization.makeupmerged;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Dedicated captured MakeUp9.5g/pinned Iris NF1.21.1 vertex seam; unavailable stays native. */
public final class MakeupMergedVertexInterface {
    public static final String OFFSET_ANCHOR = "uniform vec3 u_RegionOffset;\n";
    public static final String POSITION_NATIVE = "_vert_position + u_RegionOffset + _get_draw_translation(_draw_id)";
    public static final String POSITION_MERGED = "_vert_position + shaderlod_makeup_current_region_offset + _get_draw_translation(_draw_id)";
    public static final String MAIN_NATIVE = "void main() {\n\t_vert_init();\n";
    public static final String MAIN_MERGED = "void main() {\n\t_vert_init();\n\tshaderlodMakeupRegionOffsetInit();\n";
    public static final String MAIN_NATIVE_SIMPLE = "void main() {\n_vert_init();\n";
    public static final String MAIN_MERGED_SIMPLE = "void main() {\n_vert_init();\nshaderlodMakeupRegionOffsetInit();\n";
    private MakeupMergedVertexInterface() {}

    /** Parent passes the exact frozen UTF-8 region-offset.glsl; unknown source stays native. */
    public static String adapt(String nativeVertex, String frozenHelper, boolean qualifiedCapturedSource) {
        if (!qualifiedCapturedSource || nativeVertex == null || frozenHelper == null) return null;
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            // Caller proves exact native100/current generated106 archive, active vertex closure,
            // pinned Iris transform callback and equality to the actual native GL shader source.
            // The derivative preserves every byte outside these three dedicated MakeUp seams.

            if (!HELPER_SHA256.equals(HexFormat.of().formatHex(digest.digest(frozenHelper.getBytes(StandardCharsets.UTF_8))))) return null;
            int indented = occurrences(nativeVertex, MAIN_NATIVE), simple = occurrences(nativeVertex, MAIN_NATIVE_SIMPLE);
            if (occurrences(nativeVertex, OFFSET_ANCHOR) != 1 || occurrences(nativeVertex, POSITION_NATIVE) != 1 ||
                    !((indented == 1 && simple == 0) || (indented == 0 && simple == 1))) return null;
            String mainNative = indented == 1 ? MAIN_NATIVE : MAIN_NATIVE_SIMPLE;
            String mainMerged = indented == 1 ? MAIN_MERGED : MAIN_MERGED_SIMPLE;
            return nativeVertex.replace(OFFSET_ANCHOR, OFFSET_ANCHOR + frozenHelper)
                    .replace(POSITION_NATIVE, POSITION_MERGED).replace(mainNative, mainMerged);
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

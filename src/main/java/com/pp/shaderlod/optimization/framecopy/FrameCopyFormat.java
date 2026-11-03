package com.pp.shaderlod.optimization.framecopy;

/** Nominal sized-format payload, not measured VRAM layout or bus traffic. Zero means unknown. */
public final class FrameCopyFormat {
    private FrameCopyFormat() {}
    public static int bytesPerTexel(String format) {
        if (format == null) return 0;
        return switch (format) {
            case "R3_G3_B2", "RGBA2" -> 1;
            case "RGBA4", "RGB5_A1", "RGB565" -> 2;
            case "RGB10_A2", "RGB10_A2UI", "R11F_G11F_B10F", "RGB9_E5" -> 4;
            default -> regular(format);
        };
    }
    private static int regular(String format) {
        int channels, start;
        if (format.startsWith("RGBA")) { channels = 4; start = 4; }
        else if (format.startsWith("RGB")) { channels = 3; start = 3; }
        else if (format.startsWith("RG")) { channels = 2; start = 2; }
        else if (format.startsWith("R")) { channels = 1; start = 1; }
        else return 0;
        String size = format.substring(start);
        for (String suffix : new String[]{"_SNORM", "UI", "I", "F"}) {
            if (size.endsWith(suffix)) { size = size.substring(0, size.length() - suffix.length()); break; }
        }
        return switch (size) { case "8" -> channels; case "16" -> 2 * channels; case "32" -> 4 * channels; default -> 0; };
    }
}

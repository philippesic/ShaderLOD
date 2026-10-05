package com.pp.shaderlod;

/** Boolean-only form of pinned Iris's positive-vertex plane acceptance. */
public final class ShadowCullingMath {
    private ShadowCullingMath() {}

    public static boolean visible(float[][] planes, int count, boolean useFma,
                                  float minX, float minY, float minZ,
                                  float maxX, float maxY, float maxZ) {
        for (int i = 0; i < count; i++) {
            float[] plane = planes[i];
            float x = plane[0] < 0.0f ? minX : maxX;
            float y = plane[1] < 0.0f ? minY : maxY;
            float z = plane[2] < 0.0f ? minZ : maxZ;
            // Keep Iris's nested rounding order and >= acceptance. Its existing
            // boolean helper uses < rejection, which behaves differently for NaN.
            float dot = useFma
                    ? Math.fma(plane[0], x, Math.fma(plane[1], y, plane[2] * z))
                    : plane[0] * x + (plane[1] * y + plane[2] * z);
            if (!(dot >= -plane[3])) return false;
        }
        return true;
    }
}

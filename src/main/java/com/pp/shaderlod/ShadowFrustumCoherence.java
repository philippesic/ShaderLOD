package com.pp.shaderlod;

import java.util.Arrays;

/** Frame-local coarse containment proofs; failed/partial proofs never reject a child box. */
public final class ShadowFrustumCoherence {
    private static final int SLOTS = 64;
    private final int[] cellsX = new int[SLOTS];
    private final int[] cellsY = new int[SLOTS];
    private final int[] cellsZ = new int[SLOTS];
    private final byte[] proofs = new byte[SLOTS];
    private float[][] planes;
    private int planeCount;
    private boolean useFma;
    private double distanceLimit;

    public ShadowFrustumCoherence(float[][] planes, int count, boolean useFma, double distanceLimit) {
        reset(planes, count, useFma, distanceLimit);
    }

    public void reset(float[][] planes, int count, boolean useFma, double distanceLimit) {
        if (ShadowFrustumCoherenceDiagnostics.ENABLED) ShadowFrustumCoherenceDiagnostics.contextReset();
        this.planes = planes;
        this.planeCount = count;
        this.useFma = useFma;
        this.distanceLimit = distanceLimit;
        invalidate();
    }

    public void invalidate() {
        Arrays.fill(proofs, (byte) 0);
        if (ShadowFrustumCoherenceDiagnostics.ENABLED) ShadowFrustumCoherenceDiagnostics.invalidated();
    }

    public boolean matches(float[][] planes, int count, boolean fma, double limit) {
        return this.planes == planes && planeCount == count && useFma == fma && distanceLimit == limit;
    }

    public boolean canAccept(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        if (ShadowFrustumCoherenceDiagnostics.ENABLED) {
            ShadowFrustumCoherenceDiagnostics.request(this, planeCount, useFma, distanceLimit);
        }
        if (!Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(minZ)
                || !Float.isFinite(maxX) || !Float.isFinite(maxY) || !Float.isFinite(maxZ)
                || minX > maxX || minY > maxY || minZ > maxZ) {
            if (ShadowFrustumCoherenceDiagnostics.ENABLED) {
                ShadowFrustumCoherenceDiagnostics.result(ShadowFrustumCoherenceDiagnostics.Outcome.INVALID_AABB);
            }
            return false;
        }
        int x = cell(minX), y = cell(minY), z = cell(minZ);
        float lowX = x * 64.0f, lowY = y * 64.0f, lowZ = z * 64.0f;
        float highX = lowX + 96.0f, highY = lowY + 96.0f, highZ = lowZ + 96.0f;
        // Final containment is authoritative, including float rounding or saturated cell indices.
        if (!(minX >= lowX && minY >= lowY && minZ >= lowZ
                && maxX <= highX && maxY <= highY && maxZ <= highZ)) {
            if (ShadowFrustumCoherenceDiagnostics.ENABLED) {
                ShadowFrustumCoherenceDiagnostics.result(ShadowFrustumCoherenceDiagnostics.Outcome.OUTSIDE_COARSE_BOX);
            }
            return false;
        }
        int slot = slot(x, y, z);
        if (proofs[slot] != 0 && cellsX[slot] == x && cellsY[slot] == y && cellsZ[slot] == z) {
            if (ShadowFrustumCoherenceDiagnostics.ENABLED) {
                ShadowFrustumCoherenceDiagnostics.result(proofs[slot] == 1
                        ? ShadowFrustumCoherenceDiagnostics.Outcome.CACHE_ACCEPT
                        : ShadowFrustumCoherenceDiagnostics.Outcome.CACHE_FALLBACK);
            }
            return proofs[slot] == 1;
        }
        boolean inside = fullyAccepts(lowX, lowY, lowZ, highX, highY, highZ);
        cellsX[slot] = x;
        cellsY[slot] = y;
        cellsZ[slot] = z;
        proofs[slot] = (byte) (inside ? 1 : 2);
        if (ShadowFrustumCoherenceDiagnostics.ENABLED) {
            ShadowFrustumCoherenceDiagnostics.result(inside
                    ? ShadowFrustumCoherenceDiagnostics.Outcome.NEW_PROOF_ACCEPT
                    : ShadowFrustumCoherenceDiagnostics.Outcome.NEW_PROOF_FALLBACK);
        }
        return inside;
    }

    /** Diagnostic boundary only; verifies owner context without consulting/changing proof entries. */
    boolean diagnosticMatchesContext(float[][] expectedPlanes, int expectedCount, boolean expectedFma, double expectedLimit) {
        return ShadowFrustumCoherenceDiagnostics.ENABLED && planes == expectedPlanes && planeCount == expectedCount
                && useFma == expectedFma
                && Double.doubleToRawLongBits(distanceLimit) == Double.doubleToRawLongBits(expectedLimit);
    }

    /** Clone relevant source bits once for an offline trace; never called by canAccept. */
    ShadowFrustumCoherenceDiagnostics.PlaneContext diagnosticPlaneContext() {
        if (!ShadowFrustumCoherenceDiagnostics.ENABLED) return null;
        if (planes == null || planeCount < 0 || planeCount > planes.length || planeCount > 64) {
            return new ShadowFrustumCoherenceDiagnostics.PlaneContext(false, "unsupported plane array/count", planeCount,
                    useFma, Double.doubleToRawLongBits(distanceLimit), null);
        }
        int[][] bits = new int[planeCount][4];
        for (int i = 0; i < planeCount; i++) {
            float[] plane = planes[i];
            if (plane == null || plane.length < 4) {
                return new ShadowFrustumCoherenceDiagnostics.PlaneContext(false, "missing/malformed plane", planeCount,
                        useFma, Double.doubleToRawLongBits(distanceLimit), null);
            }
            for (int coordinate = 0; coordinate < 4; coordinate++) {
                bits[i][coordinate] = Float.floatToRawIntBits(plane[coordinate]);
            }
        }
        return new ShadowFrustumCoherenceDiagnostics.PlaneContext(true, "raw plane context cloned", planeCount,
                useFma, Double.doubleToRawLongBits(distanceLimit), bits);
    }

    private static int cell(float value) {
        float scaled = value * (1.0f / 64.0f);
        int integral = (int) scaled;
        return scaled < integral && integral != Integer.MIN_VALUE ? integral - 1 : integral;
    }

    private static int slot(int x, int y, int z) {
        return (x * 0x1f1f1f1f ^ y * 0x45d9f3b ^ z * 0x119de1f3) & (SLOTS - 1);
    }

    private boolean fullyAccepts(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        // Whole coarse box must lie inside every rejecting Sodium distance constraint.
        if (Double.isNaN(distanceLimit) || distanceLimit < 0.0
                || minX < -distanceLimit || minY < -distanceLimit || minZ < -distanceLimit
                || maxX > distanceLimit || maxY > distanceLimit || maxZ > distanceLimit) return false;
        if (planes == null || planeCount < 0 || planeCount > planes.length) return false;
        for (int i = 0; i < planeCount; i++) {
            float[] p = planes[i];
            if (p == null || p.length < 4 || !Float.isFinite(p[0]) || !Float.isFinite(p[1])
                    || !Float.isFinite(p[2]) || !Float.isFinite(p[3])) return false;
            float low = dot(p, p[0] < 0.0f ? maxX : minX, p[1] < 0.0f ? maxY : minY,
                    p[2] < 0.0f ? maxZ : minZ);
            float high = dot(p, p[0] < 0.0f ? minX : maxX, p[1] < 0.0f ? minY : maxY,
                    p[2] < 0.0f ? minZ : maxZ);
            // Use the ORIGINAL positive expression at both extrema, not Iris's differently rounded negative expression.
            if (!Float.isFinite(low) || !Float.isFinite(high) || !(low >= -p[3])) return false;
        }
        return true;
    }

    private float dot(float[] p, float x, float y, float z) {
        return useFma ? Math.fma(p[0], x, Math.fma(p[1], y, p[2] * z))
                : p[0] * x + (p[1] * y + p[2] * z);
    }
}

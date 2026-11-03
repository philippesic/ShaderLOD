package com.pp.shaderlod.integration.makeupquad;
/** Request-only proof covering every nonnegative native current capacity, including later doubling. */
public final class NativeIndexGrowth {
    public static final int SAFE_BYTE_QUADS = Integer.MAX_VALUE / 24;
    public static final int SAFE_REQUEST_QUADS = Math.min(SAFE_BYTE_QUADS / 2, SAFE_BYTE_QUADS - 16384);
    public static final int SAFE_REQUEST_INDICES = SAFE_REQUEST_QUADS * 6;
    private NativeIndexGrowth() {}
    public static boolean requestSafe(int requestedIndices) {
        return requestedIndices >= 0 && requestedIndices % 6 == 0 && requestedIndices <= SAFE_REQUEST_INDICES;
    }
    /* If r<=p: ensureCapacity does not grow. If p<r<=R: 2p<2R<=Q and r+16384<=Q.
       Thus native int intermediates fit, next=min(max(2p,r+16384),typeMax)<=Q,
       and 24*next<=INT_MAX. Rechecking every batch covers all later growth steps;
       no assumptions about a cached old capacity are needed. Native INTEGER is pinned. */
}

package com.pp.shaderlod.benchmark;

/** Render-thread-only opt-in counters. No GL getters, clocks, hashing, polling or allocations when idle. */
public final class BenchmarkLiveWork {
    public record Frame(long sequence, long ownerEpoch, long completedAtNanos, int nativeFrame,
                        Object pipeline, Object source, long reloads,
                        boolean sodiumHook, boolean layerHook, boolean dhHook,
                        long worldEntries, long worldIndices, long shadowEntries, long shadowIndices,
                        long translucentEntries, long translucentIndices,
                        long dhDraws, long dhIndices, long dhShadowDraws, long dhShadowIndices,
                        long dhFingerprint, boolean dhRendererEntered, boolean valid) {}
    private static boolean enabled, owner, translucent, inLayer, invalid;
    private static boolean sodiumHook, layerHook, dhHook;
    private static long sequence, ownerEpoch, worldEntries, worldIndices, shadowEntries, shadowIndices;
    private static long translucentEntries, translucentIndices, dhDraws, dhIndices, dhShadowDraws, dhShadowIndices;
    private static long dhFingerprint;
    private static boolean dhRendererEntered;
    private static Frame latest;
    private BenchmarkLiveWork() {}

    /** One cheap guard for native submission hooks. Parent must close/disable on every frame exit. */
    public static boolean enabled() { return enabled; }
    public static void hooksInstalled(boolean sodium, boolean layer, boolean dh) {
        sodiumHook |= sodium; layerHook |= layer; dhHook |= dh;
    }
    public static void beginFrame(boolean benchmarkOwner) {
        if (benchmarkOwner && !owner) ownerEpoch++;
        owner = benchmarkOwner;
        enabled = benchmarkOwner;
        if (!benchmarkOwner) { latest = null; return; }
        worldEntries = worldIndices = shadowEntries = shadowIndices = 0;
        translucentEntries = translucentIndices = dhDraws = dhIndices = dhShadowDraws = dhShadowIndices = 0;
        dhFingerprint = 1;
        dhRendererEntered = translucent = inLayer = invalid = false;
    }
    public static void endFrame(long now, int nativeFrame, Object pipeline, Object source, long reloads) {
        if (!enabled) return;
        latest = new Frame(++sequence, ownerEpoch, now, nativeFrame, pipeline, source, reloads,
                sodiumHook, layerHook, dhHook, worldEntries, worldIndices, shadowEntries, shadowIndices,
                translucentEntries, translucentIndices, dhDraws, dhIndices, dhShadowDraws, dhShadowIndices,
                dhFingerprint & Long.MAX_VALUE, dhRendererEntered, !invalid && !inLayer);
        enabled = false;
    }
    public static void disable() { enabled = owner = false; latest = null; }
    public static Frame latest() { return latest; }
    public static void enterLayer(boolean isTranslucent) {
        if (!enabled) return;
        if (inLayer) invalid = true;
        inLayer = true; translucent = isTranslucent;
    }
    public static void leaveLayer() { if (enabled) { inLayer = false; translucent = false; } }

    /** After the native GL delegate returned; entries/indices are positive-index subdraws, not batches. */
    public static void sodiumSubmitted(long entries, long indices, boolean shadow) {
        if (!enabled) return;
        if (!inLayer || entries < 0 || indices < 0) { invalid = true; return; }
        if (shadow) { shadowEntries += entries; shadowIndices += indices; }
        else {
            worldEntries += entries; worldIndices += indices;
            if (translucent) { translucentEntries += entries; translucentIndices += indices; }
        }
    }
    public static void dhRendererEntered() { if (enabled) dhRendererEntered = true; }
    /** fingerprint includes each world draw's index count/order; identity/visibility of pixels stays unproven. */
    public static void dhSubmitted(int indices, boolean opaque, boolean shadow) {
        if (!enabled || indices <= 0) return;
        if (shadow) { dhShadowDraws++; dhShadowIndices += indices; }
        else {
            dhDraws++; dhIndices += indices;
            dhFingerprint = 31 * dhFingerprint + indices;
            dhFingerprint = 31 * dhFingerprint + (opaque ? 1 : 0);
        }
    }
    public static void observerFailed() { if (enabled) invalid = true; }
}

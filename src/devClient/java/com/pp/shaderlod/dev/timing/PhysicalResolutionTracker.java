package com.pp.shaderlod.dev.timing;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.irisshaders.iris.targets.Blaze3dRenderTargetExt;
import net.irisshaders.iris.targets.RenderTargets;

/** Render-thread-only opt-in diagnostic; no GL queries, source hashes or target creation. */
public final class PhysicalResolutionTracker {
    public static final String PROPERTY = "shaderlod.dev.physicalResolution";
    private static final boolean ENABLED = Boolean.getBoolean(PROPERTY);
    private static final PhysicalResolutionEvidence STATE = ENABLED ? new PhysicalResolutionEvidence() : null;
    private static RenderTargets targets;
    private static long[] targetStorageEpochs;
    private PhysicalResolutionTracker() {}
    public static boolean enabled() { return ENABLED; }
    public static void armBoundary(String label) { if (ENABLED) STATE.armBoundary(label); }
    public static void begin(Object pipeline, RenderTargets owner) {
        if (!ENABLED) return;
        STATE.begin(pipeline, owner, owner.getCurrentWidth(), owner.getCurrentHeight());
        observeOwner(owner);
    }
    public static void resized(RenderTargets owner, int width, int height, boolean colorResized,
                               boolean depthStorageResized, boolean depthRebound) {
        if (!ENABLED) return;
        STATE.resized(owner, width, height, colorResized, depthStorageResized, depthRebound);
        observeOwner(owner);
        if (colorResized) {
            for (int index = 0; index < targetStorageEpochs.length; index++)
                if (owner.get(index) != null) targetStorageEpochs[index] = STATE.colorStorageEpoch();
        }
    }
    public static void clearSelected(RenderTargets owner) {
        if (!ENABLED) return;
        STATE.observeTargets(owner, owner.getCurrentWidth(), owner.getCurrentHeight());
        observeOwner(owner); STATE.clearSelected(owner);
    }
    public static void constructorCompleted(Object pipeline, RenderTargets owner) {
        if (ENABLED) STATE.constructorCompleted(pipeline, owner);
    }
    public static void targetCreated(RenderTargets owner, int index) {
        if (!ENABLED) return;
        STATE.targetCreated(owner, owner.getCurrentWidth(), owner.getCurrentHeight());
        observeOwner(owner);
        targetStorageEpochs[index] = STATE.colorStorageEpoch();
    }
    public static void beginCompleted(Object pipeline, RenderTargets owner) {
        if (ENABLED) STATE.beginCompleted(pipeline, owner);
    }
    /** Cheap frame guard: primitive CPU getters; no allocation inventory or event copying. */
    public static PhysicalResolutionEvidence.Snapshot signature() {
        return ENABLED ? STATE.snapshot(main(), List.of(), false) : null;
    }
    /** Boundary-only inventory of already allocated targets, including both ping-pong sides. */
    public static PhysicalResolutionEvidence.Snapshot snapshot() {
        if (!ENABLED) return null;
        var rows = new ArrayList<PhysicalResolutionEvidence.Target>();
        if (targets != null) {
            int count = targetStorageEpochs.length;
            for (int index = 0; index < count; index++) {
                var target = targets.get(index); // getOrCreate is intentionally never used.
                rows.add(target == null ? new PhysicalResolutionEvidence.Target(index, false, -1, -1, -1, -1, 0, 0)
                        : new PhysicalResolutionEvidence.Target(index, true, target.getWidth(), target.getHeight(),
                        target.getMainTexture(), target.getAltTexture(), targetStorageEpochs[index], targetStorageEpochs[index]));
            }
        }
        return STATE.snapshot(main(), rows, true);
    }
    private static void observeOwner(RenderTargets owner) {
        if (targets == owner) return;
        int count = owner.getRenderTargetCount();
        if (count < 0 || count > 256) throw new IllegalStateException("Unbounded Iris target inventory");
        targets = owner; targetStorageEpochs = new long[count];
        for (int index = 0; index < count; index++)
            if (owner.get(index) != null) targetStorageEpochs[index] = STATE.colorStorageEpoch();
    }
    private static PhysicalResolutionEvidence.Main main() {
        var target = Minecraft.getInstance().getMainRenderTarget();
        var ext = (Blaze3dRenderTargetExt) target;
        return new PhysicalResolutionEvidence.Main(target.width, target.height,
                ext.iris$getColorBufferVersion(), ext.iris$getDepthBufferVersion(),
                target.getColorTextureId(), target.getDepthTextureId());
    }
}

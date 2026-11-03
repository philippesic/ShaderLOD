package com.pp.shaderlod;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Render-frame core transport. Supplier reads never advance temporal state. */
public final class CoreUniformFrame {
    public static final int BRIDGE_VERSION = 2;
    public static final int TEMPORAL_VERSION = 1;
    public static final int SCALE_HISTORY_VERSION = 1;
    private static final Map<Object, CoreUniformFrame> FRAMES = new WeakHashMap<>();

    public record Core(LodSettings settings, LodPreset preset, com.pp.shaderlod.optimization.OptimizationSnapshot optimizations) {
        public Core(LodSettings settings, LodPreset preset) {
            this(settings, preset, com.pp.shaderlod.optimization.OptimizationConfig.snapshot());
        }
        public Core {
            Objects.requireNonNull(settings, "settings");
            Objects.requireNonNull(preset, "preset");
            Objects.requireNonNull(optimizations, "optimizations");
        }
    }

    /** Identity fields deliberately compare by reference, never world/entity value equality. */
    public record RenderContext(Object camera, Object cameraMode, Object target, int width, int height,
            int colorTexture, int depthTexture, int depthVersion, int colorVersion) {
        boolean valid() {
            return camera != null && cameraMode != null && target != null && width > 0 && height > 0
                    && colorTexture > 0 && depthTexture > 0 && depthVersion >= 0 && colorVersion >= 0;
        }
        boolean same(RenderContext other) {
            return other != null && camera == other.camera && cameraMode == other.cameraMode
                    && target == other.target && width == other.width && height == other.height
                    && colorTexture == other.colorTexture && depthTexture == other.depthTexture
                    && depthVersion == other.depthVersion && colorVersion == other.colorVersion;
        }
    }

    public record Frame(Core core, int temporalVersion, boolean historyReset, int scaleHistoryVersion,
            int previousEnabled, boolean scaleTransition) {
        public Frame(Core core, int temporalVersion, boolean historyReset) {
            this(core, temporalVersion, historyReset, 0, core.settings().enabled() ? 1 : 0, false);
        }
    }

    private final Supplier<Core> snapshot;
    private final Supplier<Object> worldIdentity;
    private final IntSupplier renderFrame;
    private final Supplier<RenderContext> context;
    private Frame current;
    private Object previousWorld;
    private int previousFrame;
    private boolean notified;
    private boolean previousValid;
    private RenderContext previousContext;
    private boolean completed;
    private boolean duplicateNotification;

    CoreUniformFrame(Supplier<Core> snapshot, Supplier<Object> worldIdentity, IntSupplier renderFrame) {
        this(snapshot, worldIdentity, renderFrame, null);
    }

    CoreUniformFrame(Supplier<Core> snapshot, Supplier<Object> worldIdentity, IntSupplier renderFrame,
            Supplier<RenderContext> context) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.worldIdentity = Objects.requireNonNull(worldIdentity, "worldIdentity");
        this.renderFrame = Objects.requireNonNull(renderFrame, "renderFrame");
        this.context = context;
        // Pipeline construction may precede a world. Do not resolve Minecraft or a frame here.
        current = new Frame(Objects.requireNonNull(snapshot.get(), "initial core"), 0, true);
    }

    /** One listener and snapshot for all programs sharing the pinned Iris notifier. */
    public static synchronized CoreUniformFrame forNotifier(Object notifier, Consumer<Runnable> addListener,
            Supplier<Core> snapshot, Supplier<Object> worldIdentity, IntSupplier renderFrame) {
        return forNotifier(notifier, addListener, snapshot, worldIdentity, renderFrame, null);
    }

    public static synchronized CoreUniformFrame forNotifier(Object notifier, Consumer<Runnable> addListener,
            Supplier<Core> snapshot, Supplier<Object> worldIdentity, IntSupplier renderFrame,
            Supplier<RenderContext> context) {
        Objects.requireNonNull(notifier, "notifier");
        Objects.requireNonNull(addListener, "addListener");
        var existing = FRAMES.get(notifier);
        if (existing != null) return existing;
        var created = new CoreUniformFrame(snapshot, worldIdentity, renderFrame, context);
        addListener.accept(created::beginFrame);
        FRAMES.put(notifier, created);
        return created;
    }

    /** Normal return of this exact notifier's ordinary world pipeline; no uniform snapshot changes. */
    public static synchronized void completeForNotifier(Object notifier, int frame) {
        var state = FRAMES.get(notifier);
        if (state != null) state.completeFrame(frame);
    }

    void completeFrame(int frame) {
        completed = false;
        if (context == null || !notified || frame != previousFrame || !previousValid
                || duplicateNotification || previousContext == null) return;
        try {
            completed = worldIdentity.get() == previousWorld && previousContext.same(context.get());
        } catch (RuntimeException unavailable) {
            // Failed completion cannot certify the next frame. Supplier state stays immutable.
        }
    }

    private RenderContext readContext() {
        if (context == null) return null;
        try {
            var value = context.get();
            return value != null && value.valid() ? value : null;
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private static boolean onlyEnabledChanged(Core previous, Core next) {
        var before = previous.settings();
        var after = next.settings();
        return before.enabled() != after.enabled() && previous.preset() == next.preset()
                && previous.optimizations().equals(next.optimizations())
                && Double.compare(before.near(), after.near()) == 0 && Double.compare(before.far(), after.far()) == 0
                && Double.compare(before.transition(), after.transition()) == 0 && before.debug() == after.debug();
    }

    /** Called only by Iris onNewFrame, before its rendering uniform suppliers. */
    public void beginFrame() {
        final int frame;
        try {
            frame = renderFrame.getAsInt();
        } catch (RuntimeException unavailable) {
            current = new Frame(current.core(), 0, true);
            previousValid = false;
            completed = false;
            return;
        }
        // Multiple level renders in one GameRenderer frame cannot split the transport.
        if (notified && frame == previousFrame) {
            // More than one world transaction in a counter is unsupported; retain current suppliers.
            duplicateNotification = true;
            completed = false;
            return;
        }
        boolean consecutive = notified && frame == previousFrame + 1;
        boolean priorCompleted = completed;
        completed = false;
        duplicateNotification = false;
        notified = true;
        previousFrame = frame;
        try {
            var next = Objects.requireNonNull(snapshot.get(), "core snapshot");
            var world = worldIdentity.get();
            boolean valid = world != null;
            var nextContext = readContext();
            boolean continuous = previousValid && valid && consecutive && world == previousWorld
                    && nextContext != null && nextContext.same(previousContext) && priorCompleted;
            boolean scaleTransition = continuous && onlyEnabledChanged(current.core(), next);
            boolean reset = !previousValid || !valid || !consecutive
                    || world != previousWorld || !next.equals(current.core())
                    || (context != null && !continuous);
            current = new Frame(next, valid ? TEMPORAL_VERSION : 0, reset,
                    valid && nextContext != null ? SCALE_HISTORY_VERSION : 0,
                    (scaleTransition ? current.core() : next).settings().enabled() ? 1 : 0,
                    scaleTransition);
            previousContext = nextContext;
            previousWorld = world;
            previousValid = valid;
        } catch (RuntimeException unavailable) {
            // Keep the last coherent core values; never advertise validated history.
            current = new Frame(current.core(), 0, true);
            previousWorld = null;
            previousContext = null;
            previousValid = false;
        }
    }

    public Frame current() { return current; }
}

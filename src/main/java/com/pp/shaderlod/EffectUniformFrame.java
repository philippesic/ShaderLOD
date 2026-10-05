package com.pp.shaderlod;

import com.pp.shaderlod.lod.Effect;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One extension snapshot shared by all programs using an Iris frame notifier. */
public final class EffectUniformFrame {
    private static final Map<Object, EffectUniformFrame> FRAMES = new WeakHashMap<>();
    private final Supplier<Map<Effect, ShaderLodEffectConfig.Control>> snapshot;
    private Map<Effect, ShaderLodEffectConfig.Control> controls;

    public EffectUniformFrame() { this(ShaderLodEffectConfig::snapshot); }

    EffectUniformFrame(Supplier<Map<Effect, ShaderLodEffectConfig.Control>> snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        beginFrame();
    }

    public static synchronized EffectUniformFrame forNotifier(Object notifier, Consumer<Runnable> addListener) {
        Objects.requireNonNull(notifier, "notifier");
        Objects.requireNonNull(addListener, "addListener");
        var existing = FRAMES.get(notifier);
        if (existing != null) return existing;
        var created = new EffectUniformFrame();
        FRAMES.put(notifier, created);
        addListener.accept(created::beginFrame);
        return created;
    }

    /** Pointer read at frame start; config edits/reloads cannot split this frame's controls. */
    public void beginFrame() { controls = Objects.requireNonNull(snapshot.get(), "snapshot map"); }

    public ShaderLodEffectConfig.Control control(Effect effect) {
        return Objects.requireNonNull(controls.get(Objects.requireNonNull(effect, "effect")), "effect control");
    }
}

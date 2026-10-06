package com.pp.shaderlod;

import com.pp.shaderlod.lod.Effect;
import com.pp.shaderlod.integration.AdapterRuntimeBinding;
import com.pp.shaderlod.integration.IrisAdapterRuntime;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One extension snapshot shared by all programs using an Iris frame notifier. */
public final class EffectUniformFrame {
    private static final Map<Object, EffectUniformFrame> FRAMES = new WeakHashMap<>();
    private final Supplier<Map<Effect, ShaderLodEffectConfig.Control>> snapshot;
    private final Supplier<AdapterRuntimeBinding.Snapshot> bindingSnapshot;
    private Map<Effect, ShaderLodEffectConfig.Control> controls;
    private AdapterRuntimeBinding.Snapshot binding;

    public EffectUniformFrame() { this(ShaderLodEffectConfig::snapshot, IrisAdapterRuntime::snapshot, true); }

    EffectUniformFrame(Supplier<Map<Effect, ShaderLodEffectConfig.Control>> snapshot) {
        this(snapshot, () -> AdapterRuntimeBinding.Snapshot.unavailable(AdapterRuntimeBinding.Reason.UNVALIDATED_PACK));
    }

    EffectUniformFrame(Supplier<Map<Effect, ShaderLodEffectConfig.Control>> snapshot,
                       Supplier<AdapterRuntimeBinding.Snapshot> bindingSnapshot) {
        this(snapshot, bindingSnapshot, false);
    }

    private EffectUniformFrame(Supplier<Map<Effect, ShaderLodEffectConfig.Control>> snapshot,
                               Supplier<AdapterRuntimeBinding.Snapshot> bindingSnapshot, boolean deferRuntime) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.bindingSnapshot = Objects.requireNonNull(bindingSnapshot, "bindingSnapshot");
        if (deferRuntime) {
            // Pipeline construction registers suppliers before it has a live pipeline to query.
            controls = Objects.requireNonNull(snapshot.get(), "snapshot map");
            binding = AdapterRuntimeBinding.Snapshot.unavailable(AdapterRuntimeBinding.Reason.UNVALIDATED_PACK);
        } else beginFrame();
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
    public void beginFrame() {
        controls = Objects.requireNonNull(snapshot.get(), "snapshot map");
        binding = Objects.requireNonNull(bindingSnapshot.get(), "binding snapshot");
    }

    public AdapterRuntimeBinding.Snapshot binding() { return binding; }

    public ShaderLodEffectConfig.Control control(Effect effect) {
        return Objects.requireNonNull(controls.get(Objects.requireNonNull(effect, "effect")), "effect control");
    }
}

package com.pp.shaderlod;

import com.pp.shaderlod.lod.DistanceBands;
import com.pp.shaderlod.lod.Effect;
import com.pp.shaderlod.lod.EffectPolicy;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Requested overrides and their transport values; no inferred shader capability. */
@EventBusSubscriber(modid = ShaderLod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ShaderLodEffectConfig {
    public static final int PROTOCOL_VERSION = 2;

    public enum Mode {
        INHERIT(0), FULL(1), ADAPTIVE(2);
        private final int protocolValue;
        Mode(int protocolValue) { this.protocolValue = protocolValue; }
        public int protocolValue() { return protocolValue; }
    }

    /** Requested ratios remain inspectable when lighting protection transports unity. */
    public record Control(Mode mode, EffectPolicy requested, EffectPolicy policy, boolean repaired) {
        public Control {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(requested, "requested");
            Objects.requireNonNull(policy, "policy");
        }

        public Control(Mode mode, EffectPolicy policy) { this(mode, policy, policy, false); }
    }

    private record Values(ModConfigSpec.EnumValue<Mode> mode,
                          ModConfigSpec.DoubleValue near, ModConfigSpec.DoubleValue far,
                          ModConfigSpec.DoubleValue transition, ModConfigSpec.DoubleValue mediumRatio,
                          ModConfigSpec.DoubleValue farRatio, ModConfigSpec.BooleanValue protectLighting) {}

    public static final ModConfigSpec SPEC;
    private static final Map<Effect, Values> VALUES = new EnumMap<>(Effect.class);
    private static volatile Map<Effect, Control> controls;
    private static volatile boolean irisRegistrationObserved;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.comment("Requested per-effect overrides for explicit ShaderLOD adapters (effects protocol 2).",
                "INHERIT preserves the adapter's existing policy. FULL requests original work.",
                "ADAPTIVE uses these bands and caps; config and registration do not prove consumption.")
                .push("effects");
        for (Effect effect : Effect.values()) {
            var defaults = EffectPolicy.defaultsFor(effect);
            builder.push(effectKey(effect));
            var mode = builder.defineEnum("mode", Mode.INHERIT);
            var near = builder.comment("Center of full-to-medium transition, in camera-relative blocks.")
                    .defineInRange("near", defaults.bands().nearBlocks(), 1.0, 4095.0);
            var far = builder.comment("Center of medium-to-far transition; cross-field reloads repair far above near.")
                    .defineInRange("far", defaults.bands().farBlocks(), 2.0, 4096.0);
            var transition = builder.comment("Full width of each centered band; reloads clamp overlapping bands.")
                    .defineInRange("transition", defaults.bands().transitionBlocks(), 0.0, 512.0);
            var medium = builder.comment("Retained upstream work fraction. Spatial adapters declare evaluation units/rates.")
                    .defineInRange("mediumWorkRatio", 1.0, 0.0, 1.0);
            var low = builder.comment("Retained far fraction, repaired to at most medium; adapter enforces legal minima.")
                    .defineInRange("farWorkRatio", 1.0, 0.0, 1.0);
            var protect = builder.comment("Keep shadow/AO sample budgets full. Does not certify spatial lighting reconstruction.")
                    .define("preserveSalientLighting", true);
            VALUES.put(effect, new Values(mode, near, far, transition, medium, low, protect));
            builder.pop();
        }
        builder.pop();
        SPEC = builder.build();
        controls = defaults();
    }

    private ShaderLodEffectConfig() {}

    public static String effectKey(Effect effect) {
        return Objects.requireNonNull(effect, "effect").name().toLowerCase(Locale.ROOT);
    }

    /** Allocation-free immutable map, published only after load/reload or a complete edit. */
    public static Map<Effect, Control> snapshot() { return controls; }

    @SubscribeEvent
    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        if (event instanceof ModConfigEvent.Unloading) {
            synchronized (ShaderLodEffectConfig.class) { controls = defaults(); }
        } else {
            refresh();
        }
    }

    /** Package-visible for integration tests; no per-frame config reads or allocations. */
    static synchronized void refresh() {
        if (!SPEC.isLoaded()) {
            controls = defaults();
            return;
        }
        var updated = new EnumMap<Effect, Control>(Effect.class);
        for (var entry : VALUES.entrySet()) {
            var values = entry.getValue();
            updated.put(entry.getKey(), normalize(entry.getKey(), values.mode.get(),
                    values.near.get(), values.far.get(), values.transition.get(),
                    values.mediumRatio.get(), values.farRatio.get(), values.protectLighting.get()));
        }
        controls = Map.copyOf(updated);
    }

    static Control normalize(Effect effect, Mode mode, double near, double far, double width,
                             double medium, double low, boolean preserveLighting) {
        var defaults = EffectPolicy.defaultsFor(effect);
        double safeNear = repairRange(near, 1, 4095, defaults.bands().nearBlocks());
        double safeFar = Math.max(repairRange(far, 2, 4096, defaults.bands().farBlocks()), safeNear + 1);
        double safeWidth = Math.min(repairRange(width, 0, 512, defaults.bands().transitionBlocks()),
                Math.min(safeNear * 2, safeFar - safeNear));
        double safeMedium = repairRange(medium, 0, 1, 1);
        double safeLow = Math.min(repairRange(low, 0, 1, 1), safeMedium);
        boolean nonFinite = !Double.isFinite(near) || !Double.isFinite(far) || !Double.isFinite(width)
                || !Double.isFinite(medium) || !Double.isFinite(low);
        Mode safeMode = nonFinite ? Mode.FULL : mode == null ? Mode.INHERIT : mode;
        if (nonFinite || mode == null) { safeMedium = 1; safeLow = 1; }
        var bands = new DistanceBands(safeNear, safeFar, safeWidth);
        var requested = new EffectPolicy(safeMode == Mode.ADAPTIVE, preserveLighting, bands, safeMedium, safeLow);
        boolean protectedLighting = preserveLighting && effect.salientLighting();
        var transport = protectedLighting
                ? new EffectPolicy(requested.enabled(), true, bands, 1, 1) : requested;
        boolean repaired = mode != safeMode || near != safeNear || far != safeFar || width != safeWidth
                || medium != safeMedium || low != safeLow;
        return new Control(safeMode, requested, transport, repaired);
    }

    public static void setMode(Effect effect, Mode mode) {
        Objects.requireNonNull(mode, "mode");
        edit(effect, values -> values.mode.set(mode));
    }

    public static void setBands(Effect effect, double near, double far, double transition) {
        requireRange(near, 1, 4095, "near");
        requireRange(far, 2, 4096, "far");
        requireRange(transition, 0, 512, "transition");
        new DistanceBands(near, far, transition);
        edit(effect, values -> {
            values.near.set(near);
            values.far.set(far);
            values.transition.set(transition);
        });
    }

    public static void setRatios(Effect effect, double medium, double far) {
        requireRange(medium, 0, 1, "medium ratio");
        requireRange(far, 0, 1, "far ratio");
        if (far > medium) throw new IllegalArgumentException("Far ratio must not exceed medium ratio");
        edit(effect, values -> {
            values.mediumRatio.set(medium);
            values.farRatio.set(far);
        });
    }

    public static void setProtection(Effect effect, boolean preserveLighting) {
        edit(effect, values -> values.protectLighting.set(preserveLighting));
    }

    private static void edit(Effect effect, Consumer<Values> edit) {
        Objects.requireNonNull(effect, "effect");
        synchronized (ShaderLodEffectConfig.class) {
            if (!SPEC.isLoaded()) throw new IllegalStateException("Effects config is not loaded");
            edit.accept(VALUES.get(effect));
            refresh();
        }
        // FML save/reload owns its config lock before posting events; never invert that order.
        SPEC.save();
    }

    public static void observeIrisRegistration() { irisRegistrationObserved = true; }
    public static boolean irisRegistrationObserved() { return irisRegistrationObserved; }

    private static Map<Effect, Control> defaults() {
        var defaults = new EnumMap<Effect, Control>(Effect.class);
        for (Effect effect : Effect.values()) defaults.put(effect, new Control(Mode.INHERIT, EffectPolicy.defaultsFor(effect)));
        return Map.copyOf(defaults);
    }

    private static double repairRange(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }

    private static void requireRange(double value, double min, double max, String name) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(name + " must be finite in [" + min + ", " + max + "]");
        }
    }
}

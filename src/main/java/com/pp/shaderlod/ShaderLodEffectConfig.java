package com.pp.shaderlod;

import com.pp.shaderlod.lod.DistanceBands;
import com.pp.shaderlod.lod.Effect;
import com.pp.shaderlod.lod.EffectPolicy;
import java.util.EnumMap;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Optional per-effect overrides. An adapter must explicitly consume extension protocol 1. */
public final class ShaderLodEffectConfig {
    public enum Mode {
        INHERIT(0), FULL(1), ADAPTIVE(2);

        private final int protocolValue;
        Mode(int protocolValue) { this.protocolValue = protocolValue; }
        public int protocolValue() { return protocolValue; }
    }

    public record Control(Mode mode, EffectPolicy policy) {}

    private record Values(ModConfigSpec.EnumValue<Mode> mode,
                          ModConfigSpec.DoubleValue near, ModConfigSpec.DoubleValue far,
                          ModConfigSpec.DoubleValue transition, ModConfigSpec.DoubleValue mediumRatio,
                          ModConfigSpec.DoubleValue farRatio, ModConfigSpec.BooleanValue protectLighting) {}

    public static final ModConfigSpec SPEC;
    private static final Map<Effect, Values> VALUES = new EnumMap<>(Effect.class);

    static {
        var builder = new ModConfigSpec.Builder();
        builder.comment("Per-effect overrides for explicit adapters consuming ShaderLOD effects protocol 1.",
                "INHERIT preserves the adapter's existing policy. FULL requests original work.",
                "ADAPTIVE uses these bands and caps; a config entry alone does not modify a shader.")
                .push("effects");
        for (Effect effect : Effect.values()) {
            var defaults = EffectPolicy.defaultsFor(effect);
            builder.push(effect.name().toLowerCase(java.util.Locale.ROOT));
            var mode = builder.defineEnum("mode", Mode.INHERIT);
            var near = builder.comment("Center of the full-to-medium transition, in camera-relative blocks.")
                    .defineInRange("near", defaults.bands().nearBlocks(), 1.0, 4095.0);
            var far = builder.comment("Center of the medium-to-far transition, in blocks; clamped above near.")
                    .defineInRange("far", defaults.bands().farBlocks(), 2.0, 4096.0);
            var transition = builder.comment("Total width of each centered transition band; overlap is clamped.")
                    .defineInRange("transition", defaults.bands().transitionBlocks(), 0.0, 512.0);
            var medium = builder.comment("Retained fraction of upstream work at medium distance. 1 preserves work.")
                    .defineInRange("mediumWorkRatio", 1.0, 0.0, 1.0);
            var low = builder.comment("Retained far fraction, clamped to medium. The adapter sets legal minima.")
                    .defineInRange("farWorkRatio", 1.0, 0.0, 1.0);
            var protect = builder.comment("Keep shadow/AO quality budgets full. Exact work reuse is still allowed.")
                    .define("preserveSalientLighting", true);
            VALUES.put(effect, new Values(mode, near, far, transition, medium, low, protect));
            builder.pop();
        }
        builder.pop();
        SPEC = builder.build();
    }

    private ShaderLodEffectConfig() {}

    /** Snapshot all fields once per render frame; suppliers read the same immutable controls. */
    public static Map<Effect, Control> snapshot() {
        var controls = new EnumMap<Effect, Control>(Effect.class);
        for (var entry : VALUES.entrySet()) {
            var values = entry.getValue();
            double near = values.near.get();
            double far = Math.max(values.far.get(), near + 1.0);
            double width = Math.min(values.transition.get(), Math.min(near * 2.0, far - near));
            double medium = values.mediumRatio.get();
            double low = Math.min(values.farRatio.get(), medium);
            boolean preserveLighting = values.protectLighting.get();
            boolean protectedLighting = preserveLighting && entry.getKey().salientLighting();
            var mode = values.mode.get();
            var policy = new EffectPolicy(mode == Mode.ADAPTIVE, preserveLighting,
                    new DistanceBands(near, far, width),
                    protectedLighting ? 1.0 : medium, protectedLighting ? 1.0 : low);
            controls.put(entry.getKey(), new Control(mode, policy));
        }
        return Map.copyOf(controls);
    }
}

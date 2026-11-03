package com.pp.shaderlod;

import com.pp.shaderlod.lod.Effect;
import com.pp.shaderlod.lod.EffectPolicy;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** First-wave Fabric transport: inherit explicit adapter policy, no inferred capability. */
public final class ShaderLodEffectConfig {
    public static final int PROTOCOL_VERSION = 2;
    public enum Mode {
        INHERIT(0), FULL(1), ADAPTIVE(2);
        private final int protocolValue;
        Mode(int value) { protocolValue = value; }
        public int protocolValue() { return protocolValue; }
    }
    public record Control(Mode mode, EffectPolicy requested, EffectPolicy policy, boolean repaired) {
        public Control {
            Objects.requireNonNull(mode);
            Objects.requireNonNull(requested);
            Objects.requireNonNull(policy);
        }
        public Control(Mode mode, EffectPolicy policy) { this(mode, policy, policy, false); }
    }
    private static final Map<Effect, Control> CONTROLS;
    private static volatile boolean registrationObserved;
    static {
        var controls = new EnumMap<Effect, Control>(Effect.class);
        for (var effect : Effect.values()) controls.put(effect, new Control(Mode.INHERIT, EffectPolicy.defaultsFor(effect)));
        CONTROLS = Map.copyOf(controls);
    }
    private ShaderLodEffectConfig() {}
    public static Map<Effect, Control> snapshot() { return CONTROLS; }
    public static void observeIrisRegistration() { registrationObserved = true; }
    public static boolean irisRegistrationObserved() { return registrationObserved; }
}

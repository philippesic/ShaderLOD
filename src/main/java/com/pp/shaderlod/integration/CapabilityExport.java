package com.pp.shaderlod.integration;

import com.pp.shaderlod.EffectUniformFrame;
import com.pp.shaderlod.lod.Effect;
import com.pp.shaderlod.lod.adapter.WorkKey;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntSupplier;

/** Explicit schema-1 boolean transport for one exact work operation. */
public record CapabilityExport(WorkKey operation, String uniformName) {
    private static final Set<String> RESERVED = Set.of("shaderlodCapabilitiesVersion", "shaderlodEffectsVersion",
            "shaderlodBridgeVersion", "shaderlodPreset", "shaderlodEnabled", "shaderlodNear", "shaderlodFar",
            "shaderlodTransition", "shaderlodDebug");

    public CapabilityExport {
        Objects.requireNonNull(operation, "operation");
        if (uniformName == null || !uniformName.matches("shaderlod[A-Za-z0-9_]+")
                || uniformName.contains("__") || RESERVED.contains(uniformName)) {
            throw new IllegalArgumentException("Require an unreserved GLSL uniform name in the shaderlod namespace: " + uniformName);
        }
        for (Effect effect : Effect.values()) {
            String prefix = "shaderlod" + effect.uniformKeySuffix();
            for (String suffix : new String[]{"Mode", "Near", "Far", "Transition", "MediumWorkRatio", "FarWorkRatio"}) {
                if (uniformName.equals(prefix + suffix)) {
                    throw new IllegalArgumentException("Uniform is reserved by the effects protocol: " + uniformName);
                }
            }
        }
    }

    /** Allocate once at binding. Every invocation reads the notifier's coherent frame, with no runtime access. */
    public IntSupplier supplier(EffectUniformFrame frame) {
        Objects.requireNonNull(frame, "frame");
        return () -> frame.binding().available(operation) ? 1 : 0;
    }
}

package com.pp.shaderlod;

import com.pp.shaderlod.lod.Effect;
import java.util.Map;

/** Lazily snapshot controls only if the current shader has a linked extension consumer. */
public final class EffectUniformFrame {
    private Map<Effect, ShaderLodEffectConfig.Control> controls;

    public void beginFrame() { controls = null; }

    public ShaderLodEffectConfig.Control control(Effect effect) {
        if (controls == null) controls = ShaderLodEffectConfig.snapshot();
        return controls.get(effect);
    }
}

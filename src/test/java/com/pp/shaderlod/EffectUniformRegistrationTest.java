package com.pp.shaderlod;

import com.pp.shaderlod.lod.Effect;
import com.pp.shaderlod.mixin.EffectUniformsMixin;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.junit.jupiter.api.Assertions.*;

class EffectUniformRegistrationTest {
    private record Registered(String method, Object supplier) {}

    @Test void actualMixinCallbackRegistersTheExactFortyThreeNamesTypesAndDefaultValues() throws Exception {
        var registered = new LinkedHashMap<String, Registered>();
        var holder = (UniformHolder) Proxy.newProxyInstance(UniformHolder.class.getClassLoader(),
                new Class<?>[]{UniformHolder.class}, (proxy, method, args) -> {
                    assertEquals(UniformUpdateFrequency.PER_FRAME, args[0]);
                    assertNull(registered.put((String) args[1], new Registered(method.getName(), args[2])));
                    return proxy;
                });
        var notifier = new FrameUpdateNotifier();
        var callback = EffectUniformsMixin.class.getDeclaredMethod("shaderlod$registerEffects", UniformHolder.class,
                FrameUpdateNotifier.class, PackDirectives.class, CallbackInfo.class);
        callback.setAccessible(true);
        callback.invoke(null, holder, notifier, null, new CallbackInfo("generalCommonUniforms", false));
        assertEquals(43, registered.size());
        assertEquals("uniform1i", registered.get("shaderlodEffectsVersion").method());
        assertEquals(2, ((IntSupplier) registered.get("shaderlodEffectsVersion").supplier()).getAsInt());
        for (Effect effect : Effect.values()) {
            String prefix = "shaderlod" + effect.uniformKeySuffix();
            assertEquals("uniform1i", registered.get(prefix + "Mode").method());
            assertEquals(0, ((IntSupplier) registered.get(prefix + "Mode").supplier()).getAsInt());
            for (String field : new String[]{"Near", "Far", "Transition", "MediumWorkRatio", "FarWorkRatio"}) {
                assertEquals("uniform1f", registered.get(prefix + field).method());
                assertInstanceOf(DoubleSupplier.class, registered.get(prefix + field).supplier());
            }
            assertEquals(1, ((DoubleSupplier) registered.get(prefix + "MediumWorkRatio").supplier()).getAsDouble());
            assertEquals(1, ((DoubleSupplier) registered.get(prefix + "FarWorkRatio").supplier()).getAsDouble());
        }
        assertFalse(registered.containsKey("shaderlodBridgeVersion")); // Existing core registration stays separate.
        notifier.onNewFrame();
        assertTrue(ShaderLodEffectConfig.irisRegistrationObserved());
    }
}

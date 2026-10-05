package com.pp.shaderlod;

import com.pp.shaderlod.lod.Effect;
import com.pp.shaderlod.lod.EffectPolicy;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EffectUniformFrameTest {
    private static ShaderLodEffectConfig.Control control(ShaderLodEffectConfig.Mode mode) {
        return new ShaderLodEffectConfig.Control(mode, EffectPolicy.defaultsFor(Effect.OPAQUE_LIGHTING));
    }

    @Test void reloadOrCommandCannotSplitSuppliersWithinOneFrame() {
        var first = control(ShaderLodEffectConfig.Mode.INHERIT);
        var second = control(ShaderLodEffectConfig.Mode.ADAPTIVE);
        var requests = new AtomicReference<>(Map.of(Effect.OPAQUE_LIGHTING, first));
        var reads = new AtomicInteger();
        var frame = new EffectUniformFrame(() -> { reads.incrementAndGet(); return requests.get(); });
        assertEquals(1, reads.get());
        requests.set(Map.of(Effect.OPAQUE_LIGHTING, second));
        for (int supplier = 0; supplier < 100; supplier++) assertSame(first, frame.control(Effect.OPAQUE_LIGHTING));
        assertEquals(1, reads.get());
        frame.beginFrame();
        assertSame(second, frame.control(Effect.OPAQUE_LIGHTING));
        assertEquals(2, reads.get());
    }

    @Test void multipleProgramRegistrationsShareOneNotifierAndListener() {
        var notifier = new Object();
        var listeners = new ArrayList<Runnable>();
        var first = EffectUniformFrame.forNotifier(notifier, listeners::add);
        var second = EffectUniformFrame.forNotifier(notifier, listeners::add);
        assertSame(first, second);
        assertEquals(1, listeners.size());
        listeners.getFirst().run();
        assertSame(first.control(Effect.OPAQUE_LIGHTING), second.control(Effect.OPAQUE_LIGHTING));
        assertNotSame(first, EffectUniformFrame.forNotifier(new Object(), listeners::add));
        assertEquals(2, listeners.size());
    }
}

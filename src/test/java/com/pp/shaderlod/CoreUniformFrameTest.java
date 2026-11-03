package com.pp.shaderlod;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoreUniformFrameTest {
    private static CoreUniformFrame.Core core(boolean enabled) {
        return new CoreUniformFrame.Core(new LodSettings(enabled, 32, 96, 8, false), LodPreset.PERFORMANCE);
    }

    @Test void constructionDefersWorldAndFrameAccessAndDoesNotValidateHistory() {
        var state = new CoreUniformFrame(() -> core(true),
                () -> { fail("World access before notifier"); return null; },
                () -> { fail("Frame access before notifier"); return 0; });
        assertEquals(core(true), state.current().core());
        assertEquals(0, state.current().temporalVersion());
        assertTrue(state.current().historyReset());
        assertEquals(2, CoreUniformFrame.BRIDGE_VERSION);
    }

    @Test void firstAndUnchangedFramesHaveOneResetThenStableHistory() {
        var counter = new AtomicInteger(1);
        var world = new Object();
        var state = new CoreUniformFrame(() -> core(true), () -> world, counter::get);
        state.beginFrame();
        assertEquals(1, state.current().temporalVersion());
        assertTrue(state.current().historyReset());
        counter.incrementAndGet();
        state.beginFrame();
        assertFalse(state.current().historyReset());
    }

    @Test void bothToggleDirectionsResetExactlyOneFrameAndRepeatedReadsCannotConsumeReset() {
        var requested = new AtomicReference<>(core(false));
        var counter = new AtomicInteger(1);
        var world = new Object();
        var state = new CoreUniformFrame(requested::get, () -> world, counter::get);
        state.beginFrame();
        for (boolean enabled : new boolean[]{true, false}) {
            requested.set(core(enabled));
            counter.incrementAndGet();
            state.beginFrame();
            var toggled = state.current();
            assertTrue(toggled.historyReset());
            for (int program = 0; program < 100; program++) assertSame(toggled, state.current());
            counter.incrementAndGet();
            state.beginFrame();
            assertFalse(state.current().historyReset());
        }
    }

    @Test void allCoreValuesRemainCoherentUntilNextNotifierFrame() {
        var reads = new AtomicInteger();
        var requested = new AtomicReference<>(core(true));
        var counter = new AtomicInteger(1);
        var world = new Object();
        var state = new CoreUniformFrame(() -> { reads.incrementAndGet(); return requested.get(); },
                () -> world, counter::get);
        state.beginFrame();
        var stable = state.current();
        var changed = new CoreUniformFrame.Core(new LodSettings(false, 48, 120, 12, true), LodPreset.AGGRESSIVE);
        requested.set(changed);
        for (int program = 0; program < 100; program++) {
            assertSame(stable, state.current());
            assertEquals(core(true), state.current().core());
        }
        state.beginFrame(); // Duplicate same-render-frame notifier cannot capture the edit.
        assertEquals(2, reads.get());
        assertSame(stable, state.current());
        counter.incrementAndGet();
        state.beginFrame();
        assertEquals(changed, state.current().core());
        assertTrue(state.current().historyReset());
        assertEquals(3, reads.get());
    }

    @Test void actualIrisNotifierSharesOneListenerAndUpdatesBeforeDownstreamSuppliers() {
        var notifier = new FrameUpdateNotifier();
        var count = new AtomicInteger(1);
        var requested = new AtomicReference<>(core(false));
        var world = new Object();
        var listeners = new AtomicInteger();
        java.util.function.Consumer<Runnable> register = listener -> {
            listeners.incrementAndGet(); notifier.addListener(listener);
        };
        var first = CoreUniformFrame.forNotifier(notifier, register, requested::get, () -> world, count::get);
        var second = CoreUniformFrame.forNotifier(notifier, register, requested::get, () -> world, count::get);
        assertSame(first, second);
        assertEquals(1, listeners.get());
        // Actual Iris ArrayList listener order, followed by rendering supplier reads.
        var seen = new ArrayList<CoreUniformFrame.Frame>();
        notifier.addListener(() -> seen.add(first.current()));
        notifier.onNewFrame();
        assertSame(first.current(), seen.getFirst());
        assertEquals(1, seen.getFirst().temporalVersion());
        requested.set(core(true));
        count.incrementAndGet();
        notifier.onNewFrame();
        assertTrue(seen.getLast().core().settings().enabled());
        assertTrue(second.current().historyReset());
    }

    @Test void newNotifierAndReloadCannotInheritValidatedHistory() {
        var world = new Object();
        var counter = new AtomicInteger(1);
        var listeners = new ArrayList<Runnable>();
        var first = CoreUniformFrame.forNotifier(new Object(), listeners::add, () -> core(true), () -> world, counter::get);
        listeners.getFirst().run();
        counter.incrementAndGet();
        listeners.getFirst().run();
        assertFalse(first.current().historyReset());
        var reloaded = CoreUniformFrame.forNotifier(new Object(), listeners::add, () -> core(true), () -> world, counter::get);
        assertNotSame(first, reloaded);
        assertEquals(2, listeners.size());
        assertEquals(0, reloaded.current().temporalVersion());
        listeners.getLast().run();
        assertTrue(reloaded.current().historyReset());
        assertEquals(1, reloaded.current().temporalVersion());
    }

    @Test void worldObjectIdentityChangeResetsEvenWhenObjectsCompareEqual() {
        var world = new AtomicReference<Object>(new String("same dimension"));
        var counter = new AtomicInteger(1);
        var state = new CoreUniformFrame(() -> core(true), world::get, counter::get);
        state.beginFrame();
        counter.incrementAndGet(); state.beginFrame();
        assertFalse(state.current().historyReset());
        world.set(new String("same dimension"));
        counter.incrementAndGet(); state.beginFrame();
        assertTrue(state.current().historyReset());
    }

    @Test void unknownWorldAndRecoveryCannotValidatePriorHistory() {
        var world = new AtomicReference<Object>();
        var counter = new AtomicInteger(1);
        var state = new CoreUniformFrame(() -> core(true), world::get, counter::get);
        state.beginFrame();
        assertEquals(0, state.current().temporalVersion());
        assertTrue(state.current().historyReset());
        world.set(new Object());
        counter.incrementAndGet(); state.beginFrame();
        assertEquals(1, state.current().temporalVersion());
        assertTrue(state.current().historyReset());
        counter.incrementAndGet(); state.beginFrame();
        assertFalse(state.current().historyReset());
        world.set(null);
        counter.incrementAndGet(); state.beginFrame();
        assertEquals(0, state.current().temporalVersion());
        assertTrue(state.current().historyReset());
    }

    @Test void invalidConfigRetainsCoherentCoreAndRecoveryResets() {
        var requested = new AtomicReference<>(core(true));
        var world = new Object();
        var counter = new AtomicInteger(1);
        var state = new CoreUniformFrame(requested::get, () -> world, counter::get);
        state.beginFrame();
        requested.set(null);
        counter.incrementAndGet(); state.beginFrame();
        assertEquals(core(true), state.current().core());
        assertEquals(0, state.current().temporalVersion());
        assertTrue(state.current().historyReset());
        requested.set(core(false));
        counter.incrementAndGet(); state.beginFrame();
        assertEquals(core(false), state.current().core());
        assertEquals(1, state.current().temporalVersion());
        assertTrue(state.current().historyReset());
    }

    @Test void skippedAndResetRenderCountersConservativelyResetHistory() {
        var world = new Object();
        var counter = new AtomicInteger(1);
        var state = new CoreUniformFrame(() -> core(true), () -> world, counter::get);
        state.beginFrame();
        counter.set(7); state.beginFrame();
        assertTrue(state.current().historyReset());
        counter.set(8); state.beginFrame();
        assertFalse(state.current().historyReset());
        counter.set(0); state.beginFrame();
        assertTrue(state.current().historyReset());
    }

    @Test void unavailableCounterCannotValidateHistoryOrChangeCoreValues() {
        var counter = new AtomicInteger(1);
        var world = new Object();
        var state = new CoreUniformFrame(() -> core(true), () -> world,
                () -> { if (counter.get() == -1) throw new IllegalStateException("unknown frame"); return counter.get(); });
        state.beginFrame();
        counter.set(-1); state.beginFrame();
        assertEquals(core(true), state.current().core());
        assertEquals(0, state.current().temporalVersion());
        assertTrue(state.current().historyReset());
        counter.set(2); state.beginFrame();
        assertTrue(state.current().historyReset());
        assertEquals(1, state.current().temporalVersion());
    }

    private static CoreUniformFrame.RenderContext context(Object camera, Object mode, Object target) {
        return new CoreUniformFrame.RenderContext(camera, mode, target, 2976, 1672, 10, 11, 2, 2);
    }

    private static final class ScaleFixture {
        final AtomicReference<CoreUniformFrame.Core> requested = new AtomicReference<>(core(false));
        final AtomicInteger counter = new AtomicInteger(1);
        final AtomicReference<Object> world = new AtomicReference<>(new Object());
        final AtomicReference<CoreUniformFrame.RenderContext> context = new AtomicReference<>(
                CoreUniformFrameTest.context(new Object(), new Object(), new Object()));
        final CoreUniformFrame state = new CoreUniformFrame(requested::get, world::get, counter::get, context::get);
        void begin() { state.beginFrame(); }
        void complete() { state.completeFrame(counter.get()); }
        void next() { counter.incrementAndGet(); begin(); }
        void toggle() { requested.set(core(!requested.get().settings().enabled())); next(); }
    }

    @Test void legacyApiNeverCertifiesScaleTransitionEvenWithCompletion() {
        var requested = new AtomicReference<>(core(false));
        var count = new AtomicInteger(1);
        var world = new Object();
        var state = new CoreUniformFrame(requested::get, () -> world, count::get);
        state.beginFrame(); state.completeFrame(1);
        requested.set(core(true)); count.incrementAndGet(); state.beginFrame();
        assertEquals(0, state.current().scaleHistoryVersion());
        assertFalse(state.current().scaleTransition());
        assertTrue(state.current().historyReset());
    }

    @Test void bothCompletedAdjacentToggleDirectionsCertifyButKeepTemporalReset() {
        var f = new ScaleFixture();
        assertEquals(0, f.state.current().scaleHistoryVersion());
        f.begin();
        assertEquals(1, f.state.current().scaleHistoryVersion());
        assertFalse(f.state.current().scaleTransition());
        for (boolean enabled : new boolean[]{true, false}) {
            var prior = f.state.current();
            f.complete();
            assertSame(prior, f.state.current(), "Completion must not mutate suppliers");
            f.toggle();
            var transition = f.state.current();
            assertEquals(enabled, transition.core().settings().enabled());
            assertEquals(enabled ? 0 : 1, transition.previousEnabled());
            assertEquals(1, transition.temporalVersion());
            assertEquals(1, transition.scaleHistoryVersion());
            assertTrue(transition.scaleTransition());
            assertTrue(transition.historyReset());
            for (int i = 0; i < 100; i++) assertSame(transition, f.state.current());
            f.complete(); f.next();
            assertFalse(f.state.current().historyReset());
            assertFalse(f.state.current().scaleTransition());
            assertEquals(enabled ? 1 : 0, f.state.current().previousEnabled());
        }
    }

    @Test void missingOrWrongFrameCompletionHardResetsAndCannotCertify() {
        var f = new ScaleFixture(); f.begin(); f.toggle();
        assertTrue(f.state.current().historyReset());
        assertFalse(f.state.current().scaleTransition());
        f.state.completeFrame(f.counter.get() - 1); f.toggle();
        assertTrue(f.state.current().historyReset());
        assertFalse(f.state.current().scaleTransition());
        f.complete(); f.next();
        assertFalse(f.state.current().historyReset());
    }

    @Test void allNonEnabledCoreChangesDisqualifyEvenWhenEnabledAlsoChanges() {
        var alternatives = new CoreUniformFrame.Core[]{
            new CoreUniformFrame.Core(new LodSettings(true, 33, 96, 8, false), LodPreset.PERFORMANCE),
            new CoreUniformFrame.Core(new LodSettings(true, 32, 97, 8, false), LodPreset.PERFORMANCE),
            new CoreUniformFrame.Core(new LodSettings(true, 32, 96, 9, false), LodPreset.PERFORMANCE),
            new CoreUniformFrame.Core(new LodSettings(true, 32, 96, 8, true), LodPreset.PERFORMANCE),
            new CoreUniformFrame.Core(new LodSettings(true, 32, 96, 8, false), LodPreset.AGGRESSIVE)
        };
        for (var change : alternatives) {
            var f = new ScaleFixture(); f.begin(); f.complete(); f.requested.set(change); f.next();
            assertTrue(f.state.current().historyReset());
            assertFalse(f.state.current().scaleTransition());
            assertEquals(1, f.state.current().previousEnabled());
        }
        var f = new ScaleFixture();
        f.requested.set(new CoreUniformFrame.Core(new LodSettings(false, 32, 96, -0.0, false), LodPreset.PERFORMANCE));
        f.begin(); f.complete();
        f.requested.set(new CoreUniformFrame.Core(new LodSettings(true, 32, 96, 0.0, false), LodPreset.PERFORMANCE));
        f.next(); assertFalse(f.state.current().scaleTransition());
    }

    @Test void cameraModeTargetSizeAndAllocationChangesAllDisqualify() {
        for (int change = 0; change < 9; change++) {
            var f = new ScaleFixture(); f.begin(); f.complete(); var c = f.context.get();
            f.context.set(new CoreUniformFrame.RenderContext(change == 0 ? new Object() : c.camera(),
                    change == 1 ? new Object() : c.cameraMode(), change == 2 ? new Object() : c.target(),
                    change == 3 ? 1920 : c.width(), change == 4 ? 1080 : c.height(),
                    change == 5 ? 12 : c.colorTexture(), change == 6 ? 13 : c.depthTexture(),
                    change == 7 ? 3 : c.depthVersion(), change == 8 ? 3 : c.colorVersion()));
            f.toggle(); assertFalse(f.state.current().scaleTransition());
            assertTrue(f.state.current().historyReset());
            f.complete(); f.next(); assertFalse(f.state.current().historyReset());
        }
    }

    @Test void changedCompletionWorldOrContextAndCompletionExceptionDisqualify() {
        var f = new ScaleFixture(); f.begin(); var stable = f.state.current();
        var saved = f.context.get(); f.context.set(null); f.complete(); f.context.set(saved);
        assertSame(stable, f.state.current()); f.toggle();
        assertFalse(f.state.current().scaleTransition()); assertTrue(f.state.current().historyReset());
        f = new ScaleFixture(); f.begin(); var world = f.world.get();
        f.world.set(new Object()); f.complete(); f.world.set(world); f.toggle();
        assertFalse(f.state.current().scaleTransition());
        var count = new AtomicInteger(1); var unavailable = new AtomicInteger(0);
        var context = context(new Object(), new Object(), new Object()); var requested = new AtomicReference<>(core(false));
        var state = new CoreUniformFrame(requested::get, () -> world, count::get,
                () -> { if (unavailable.get() != 0) throw new IllegalStateException(); return context; });
        state.beginFrame(); unavailable.set(1); state.completeFrame(1); unavailable.set(0);
        requested.set(core(true)); count.incrementAndGet(); state.beginFrame();
        assertFalse(state.current().scaleTransition()); assertTrue(state.current().historyReset());
    }

    @Test void unknownContextAndRecoveryRemainHardResetsWithVersionZeroWhenUnknown() {
        var f = new ScaleFixture(); f.begin(); f.complete(); var saved = f.context.get();
        f.context.set(null); f.toggle();
        assertEquals(0, f.state.current().scaleHistoryVersion());
        assertTrue(f.state.current().historyReset()); assertFalse(f.state.current().scaleTransition());
        f.complete(); f.context.set(saved); f.toggle();
        assertEquals(1, f.state.current().scaleHistoryVersion());
        assertTrue(f.state.current().historyReset()); assertFalse(f.state.current().scaleTransition());
        f.complete(); f.next(); assertFalse(f.state.current().historyReset());
        var bad = new CoreUniformFrame.RenderContext(saved.camera(), saved.cameraMode(), saved.target(),
                0, 1672, 10, 11, 2, 2);
        f.context.set(bad); f.toggle(); assertEquals(0, f.state.current().scaleHistoryVersion());
    }

    @Test void worldGapCounterWrapInvalidCoreAndReloadNeverCertify() {
        for (int change = 0; change < 5; change++) {
            var f = new ScaleFixture(); f.begin(); f.complete();
            switch (change) {
                case 0 -> f.world.set(new Object());
                case 1 -> f.counter.addAndGet(3);
                case 2 -> f.counter.set(-1);
                case 3 -> f.world.set(null);
                case 4 -> { f.requested.set(null); f.next(); f.requested.set(core(false)); }
            }
            f.toggle(); assertFalse(f.state.current().scaleTransition());
            assertTrue(f.state.current().historyReset());
        }
        var f = new ScaleFixture(); f.begin(); f.complete();
        var reloaded = new CoreUniformFrame(f.requested::get, f.world::get, f.counter::get, f.context::get);
        reloaded.beginFrame(); assertFalse(reloaded.current().scaleTransition());
        assertTrue(reloaded.current().historyReset());
    }

    @Test void duplicateSameCounterWorldTransactionsCannotCertifyNextFrame() {
        var f = new ScaleFixture(); f.begin(); f.complete(); var stable = f.state.current();
        f.begin(); f.complete(); assertSame(stable, f.state.current()); f.toggle();
        assertFalse(f.state.current().scaleTransition()); assertTrue(f.state.current().historyReset());
    }

    @Test void pinnedNotifierCompletionIsIsolatedToExactRegisteredNotifier() {
        var notifier = new FrameUpdateNotifier(); var other = new FrameUpdateNotifier();
        var f = new ScaleFixture();
        var state = CoreUniformFrame.forNotifier(notifier, notifier::addListener,
                f.requested::get, f.world::get, f.counter::get, f.context::get);
        notifier.onNewFrame(); CoreUniformFrame.completeForNotifier(other, 1);
        f.requested.set(core(true)); f.counter.incrementAndGet(); notifier.onNewFrame();
        assertFalse(state.current().scaleTransition());
        CoreUniformFrame.completeForNotifier(notifier, 2);
        f.requested.set(core(false)); f.counter.incrementAndGet(); notifier.onNewFrame();
        assertTrue(state.current().scaleTransition()); assertEquals(1, state.current().previousEnabled());
    }

    @Test void extendedConstructionDefersContextAndMidFrameEditsDoNotSplitSuppliers() {
        var count = new AtomicInteger(1); var world = new Object();
        var requested = new AtomicReference<>(core(false)); var callbacks = new AtomicInteger();
        var context = context(new Object(), new Object(), new Object());
        var state = new CoreUniformFrame(requested::get, () -> world, count::get,
                () -> { callbacks.incrementAndGet(); return context; });
        assertEquals(0, callbacks.get());
        state.beginFrame(); var snapshot = state.current(); requested.set(core(true));
        state.completeFrame(1); assertSame(snapshot, state.current());
        count.incrementAndGet(); state.beginFrame(); assertTrue(state.current().scaleTransition());
        assertTrue(state.current().historyReset()); assertEquals(0, state.current().previousEnabled());
    }
}

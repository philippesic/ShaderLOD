package com.pp.shaderlod.optimization.framecopy;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure accounting/history-contract tests; no client or GL. main supports an isolated javac run. */
public class FrameCopyCountersTest {
    private static FrameCopyCounters.Target target(int width, int height, String format) {
        return new FrameCopyCounters.Target(3, 10, 21, 20, width, height, format, FrameCopyFormat.bytesPerTexel(format));
    }
    @Test void executedCopiesAreSeparateFromPlansAndResetPerFrame() {
        var c = new FrameCopyCounters(); Object owner = new Object(); var plan = List.of(target(3840, 2160, "RGBA16F"));
        c.begin(owner, plan, "OK");
        assertEquals(0, c.snapshot(true, true).executedSwapCopies());
        c.swapExecuted(3840, 2160); c.end();
        c.begin(owner, plan, "OK"); c.swapExecuted(3840, 2160); c.end();
        var s = c.snapshot(true, true);
        assertEquals(2, s.finalPassesCompleted()); assertEquals(2, s.executedSwapCopies());
        assertEquals(2L * 3840 * 2160, s.swapTexels()); assertEquals(2L * 3840 * 2160 * 8, s.swapPayloadBytes());
        assertEquals(0, s.streamMismatches()); assertEquals(1, s.graphChanges());
        assertEquals(2, s.targets().getFirst().executedCopies());
        assertThrows(UnsupportedOperationException.class, () -> s.targets().clear());
    }
    @Test void emptySwapListProvesNoExecutedSwapCallsOnlyAfterCompletion() {
        var c = new FrameCopyCounters(); c.begin(new Object(), List.of(), "OK"); c.end();
        var s = c.snapshot(true, true); assertEquals(1, s.finalPassesCompleted());
        assertEquals(0, s.executedSwapCopies()); assertEquals(0, s.plannedSwapCopies());
        assertEquals("OBSERVATION_ONLY_ALIAS_HISTORY_UNPROVEN", s.eligibility());
    }
    @Test void unknownFormatAndFallbackNeverInventPayloadBytes() {
        var c = new FrameCopyCounters(); c.begin(new Object(), List.of(target(100, 50, "RGBA")), "OK");
        c.fallbackExecuted(200, 100); c.swapExecuted(100, 50); c.end();
        var s = c.snapshot(true, true); assertEquals(2, s.unknownByteCopies());
        assertEquals(20000, s.fallbackTexels()); assertEquals(0, s.fallbackPayloadBytes()); assertEquals(0, s.swapPayloadBytes());
    }
    @Test void incompleteExtraAndChangedExtentStreamsAreVisible() {
        var c = new FrameCopyCounters(); var plan = List.of(target(10, 20, "R8")); Object graph = new Object();
        c.begin(graph, plan, "OK"); c.end();
        c.begin(graph, plan, "OK"); c.swapExecuted(5, 20); c.swapExecuted(10, 20); c.end();
        var s = c.snapshot(true, true); assertEquals(4, s.streamMismatches());
        assertEquals(2, s.unknownByteCopies()); assertEquals(2, s.executedSwapCopies()); assertEquals(0, s.swapPayloadBytes());
    }
    @Test void resizeAndNewGraphPublishFreshTargetMetadata() {
        var c = new FrameCopyCounters(); Object graph = new Object();
        c.begin(graph, List.of(target(10, 20, "R16F")), "OK"); c.swapExecuted(10, 20); c.end();
        c.begin(graph, List.of(target(20, 40, "R16F")), "OK"); c.swapExecuted(20, 40); c.end();
        var s = c.snapshot(true, true); assertEquals(2, s.graphChanges()); assertEquals(2000, s.swapPayloadBytes());
        assertEquals(1, s.targets().getFirst().executedCopies()); assertEquals(20, s.targets().getFirst().target().width());
    }
    @Test void failedMetadataAndOverflowRemainExplicit() {
        var c = new FrameCopyCounters(); c.begin(new Object(), List.of(), "SWAP_METADATA_UNAVAILABLE");
        c.swapExecuted(-1, 20); c.end();
        var s = c.snapshot(true, true); assertEquals(1, s.invalidExtentCopies()); assertEquals(1, s.metadataFailures());
        c.begin(new Object(), List.of(target(Integer.MAX_VALUE, Integer.MAX_VALUE, "RGBA32F")), "OK");
        c.swapExecuted(Integer.MAX_VALUE, Integer.MAX_VALUE); c.end();
        assertTrue(c.snapshot(true, true).saturated()); assertEquals(Long.MAX_VALUE, c.snapshot(true, true).swapPayloadBytes());
    }
    @Test void sizedFormatsAreNominalAndUnsizedFormatsStayUnknown() {
        assertEquals(4, FrameCopyFormat.bytesPerTexel("R11F_G11F_B10F"));
        assertEquals(8, FrameCopyFormat.bytesPerTexel("RGBA16F")); assertEquals(1, FrameCopyFormat.bytesPerTexel("R8"));
        assertEquals(6, FrameCopyFormat.bytesPerTexel("RGB16_SNORM")); assertEquals(16, FrameCopyFormat.bytesPerTexel("RGBA32UI"));
        assertEquals(0, FrameCopyFormat.bytesPerTexel("RGBA")); assertEquals(0, FrameCopyFormat.bytesPerTexel("UNKNOWN"));
    }
    @Test void runtimeOffAndResetDoNotPublishStaleCounters() {
        FrameCopyRuntime.setObserving(false); FrameCopyRuntime.resetCounters();
        FrameCopyRuntime.swapExecuted(100, 100); assertEquals(0, FrameCopyRuntime.snapshot().executedSwapCopies());
        FrameCopyRuntime.setObserving(true); assertTrue(FrameCopyRuntime.observing());
        // This assertion intentionally tests the actual pinned jar when present in the focused test classpath.
        if (FrameCopyPins.exactApi()) {
            FrameCopyRuntime.beginFinal(this, List.of(target(10, 20, "R8")), "OK");
            FrameCopyRuntime.swapExecuted(10, 20); FrameCopyRuntime.endFinal();
            assertEquals(1, FrameCopyRuntime.snapshot().executedSwapCopies());
        }
        FrameCopyRuntime.resetCounters(); assertEquals(0, FrameCopyRuntime.snapshot().executedSwapCopies());
        FrameCopyRuntime.setObserving(false); assertFalse(FrameCopyRuntime.snapshot().observing());
    }
    public static void main(String[] args) {
        var t = new FrameCopyCountersTest();
        t.executedCopiesAreSeparateFromPlansAndResetPerFrame(); t.emptySwapListProvesNoExecutedSwapCallsOnlyAfterCompletion();
        t.unknownFormatAndFallbackNeverInventPayloadBytes(); t.incompleteExtraAndChangedExtentStreamsAreVisible();
        t.resizeAndNewGraphPublishFreshTargetMetadata(); t.failedMetadataAndOverflowRemainExplicit();
        t.sizedFormatsAreNominalAndUnsizedFormatsStayUnknown(); t.runtimeOffAndResetDoNotPublishStaleCounters();
        System.out.println("PASS: 8 pure framecopy checks; apiPinned=" + FrameCopyPins.exactApi());
    }
}

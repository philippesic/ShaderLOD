package com.pp.shaderlod.dev;

/** Standalone pure lifecycle fixture. Root runs this after releasing client/GPU work. */
public final class CaptureWriteTrackerFixture {
    private static int checks;
    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }
    private static void rejects(Runnable operation) {
        try { operation.run(); }
        catch (IllegalArgumentException | IllegalStateException expected) { checks++; return; }
        throw new AssertionError("Invalid ownership operation was accepted");
    }

    public static void main(String[] args) throws Exception {
        rejects(() -> new CaptureWriteTracker(0));
        rejects(() -> new CaptureWriteTracker(3));
        var one = new CaptureWriteTracker(1);
        check(one.drained() && one.hasCapacity(), "initially drained");
        one.reserve(0);
        rejects(() -> one.reserve(1));
        rejects(() -> one.callback(0));
        one.queued(0);
        one.beginWrite(0);
        one.imageReleased(0);
        check(one.snapshot().imageSlots() == 0, "image released before callback");
        check(!one.hasCapacity() && !one.drained(), "default one waits for callback");
        one.callback(0);
        check(one.drained() && one.hasCapacity(), "own callback drains");
        rejects(() -> one.callback(0));
        rejects(() -> one.reserve(0));
        one.reserve(2);
        one.rejectedBeforeWrite(2);
        check(one.drained() && one.snapshot().rejectedBeforeWrite() == 1, "readback rejection releases lease");
        one.reserve(3);
        one.queued(3);
        one.rejectedBeforeWrite(3);
        check(one.drained(), "submission rejection releases lease");
        rejects(() -> one.rejectedBeforeWrite(3));

        var two = new CaptureWriteTracker(2);
        two.reserve(10);
        two.queued(10);
        two.beginWrite(10);
        two.reserve(11);
        two.queued(11);
        two.observeExecutorQueue(1);
        check(two.snapshot().pendingCount() == 2 && two.snapshot().imageSlots() == 2, "two owned slots bound");
        check(two.snapshot().writingImages() == 1 && two.snapshot().waitingWriterImages() == 1, "one encoder plus one waiting");
        rejects(() -> two.reserve(12));
        rejects(() -> two.beginWrite(11));
        rejects(() -> two.rejectedBeforeWrite(10));
        rejects(() -> two.observeExecutorQueue(2));
        two.imageReleased(10);
        two.beginWrite(11);
        two.imageReleased(11);
        check(!two.drained() && two.snapshot().awaitingCallbacks() == 2, "all closed images still await their callbacks");
        // Callback identity does not assume callback arrival order.
        two.callback(11);
        check(two.snapshot().pendingSlots().equals(java.util.List.of(10)), "out-of-order callback settles only own slot");
        rejects(() -> two.callback(11));
        two.callback(10);
        check(two.drained() && two.snapshot().settledCallbacks() == 2, "all callbacks required for drain");
        check(two.snapshot().peakImageSlots() == 2 && two.snapshot().peakWritingImages() == 1, "peak memory and encoder bounds");
        check(two.snapshot().peakExecutorQueueSize() == 1, "bounded queue peak");

        var failedStart = new CaptureWriteTracker(1);
        failedStart.reserve(1);
        failedStart.queued(1);
        failedStart.imageReleased(1); // Failed writer startup still closed its image.
        check(!failedStart.drained(), "failed startup still requires own error callback");
        failedStart.callback(1);
        check(failedStart.drained(), "error callback drains failed startup");

        var crossed = new CaptureWriteTracker(2);
        crossed.reserve(20);
        crossed.queued(20);
        Thread worker = new Thread(() -> { crossed.beginWrite(20); crossed.imageReleased(20); });
        worker.start();
        worker.join(1000);
        check(!worker.isAlive(), "worker bookkeeping completed");
        check(crossed.snapshot().awaitingCallbacks() == 1 && !crossed.drained(), "worker does not settle client callback");
        crossed.callback(20);
        check(crossed.drained(), "client callback settles cross-thread write");
        rejects(() -> crossed.imageReleased(20));
        System.out.println("PASS: bounded image leases, single encoder, rejection, callback identity/order, delayed drain and cross-thread bookkeeping; checks=" + checks);
    }
}

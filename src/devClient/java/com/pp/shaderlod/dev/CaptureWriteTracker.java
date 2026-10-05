package com.pp.shaderlod.dev;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure, bounded image-lease bookkeeping. Reservations settle only at their own callbacks. */
final class CaptureWriteTracker {
    private enum Stage { READBACK, WAITING_WRITER, WRITING, AWAITING_CALLBACK }
    private final int maximum;
    private final Map<Integer, Stage> pending = new LinkedHashMap<>();
    private int lastAcceptedSlot = -1, accepted, callbacks, rejected;
    private int peakPending, peakImageSlots, peakWaitingWriter, peakWriting, executorQueuePeak;

    CaptureWriteTracker(int maximum) {
        if (maximum < 1 || maximum > 2) throw new IllegalArgumentException("Image limit must be 1 or 2");
        this.maximum = maximum;
    }

    synchronized boolean hasCapacity() { return pending.size() < maximum; }
    synchronized boolean drained() { return pending.isEmpty(); }
    synchronized int pendingCount() { return pending.size(); }

    synchronized void reserve(int slot) {
        if (slot < 0 || slot <= lastAcceptedSlot) throw new IllegalStateException("Capture slots must be unique and increasing");
        if (!hasCapacity()) throw new IllegalStateException("Capture image/callback bound exhausted");
        pending.put(slot, Stage.READBACK);
        lastAcceptedSlot = slot;
        accepted++;
        updatePeaks();
    }

    synchronized void queued(int slot) { transition(slot, Stage.READBACK, Stage.WAITING_WRITER); }
    synchronized void beginWrite(int slot) {
        if (pending.containsValue(Stage.WRITING)) throw new IllegalStateException("Only one encoder may own a write at a time");
        transition(slot, Stage.WAITING_WRITER, Stage.WRITING);
    }
    /** Caller has closed its NativeImage before entering this state. */
    synchronized void imageReleased(int slot) {
        Stage stage = pending.get(slot);
        // A failed beginWrite also closes its source in the resource block.
        if (stage != Stage.WRITING && stage != Stage.WAITING_WRITER) throw new IllegalStateException("Unexpected released image stage for slot " + slot);
        pending.put(slot, Stage.AWAITING_CALLBACK);
        updatePeaks();
    }
    synchronized void callback(int slot) {
        require(slot, Stage.AWAITING_CALLBACK);
        pending.remove(slot);
        callbacks++;
    }
    /** Readback or submission failed; caller closes any allocated image before releasing the lease. */
    synchronized void rejectedBeforeWrite(int slot) {
        Stage stage = pending.get(slot);
        if (stage != Stage.READBACK && stage != Stage.WAITING_WRITER) throw new IllegalStateException("Cannot reject an active/released writer image");
        pending.remove(slot);
        rejected++;
    }
    synchronized void observeExecutorQueue(int size) {
        if (size < 0 || size > 1) throw new IllegalStateException("Single-writer executor queue exceeded its capacity-one bound");
        executorQueuePeak = Math.max(executorQueuePeak, size);
    }

    synchronized Snapshot snapshot() {
        int readback = count(Stage.READBACK), waiting = count(Stage.WAITING_WRITER), writing = count(Stage.WRITING);
        Map<Integer, String> stages = new LinkedHashMap<>();
        pending.forEach((slot, stage) -> stages.put(slot, stage.name()));
        return new Snapshot(maximum, pending.size(), readback + waiting + writing, readback,
                waiting, writing, count(Stage.AWAITING_CALLBACK), peakPending, peakImageSlots,
                peakWaitingWriter, peakWriting, executorQueuePeak, accepted, callbacks,
                rejected, List.copyOf(pending.keySet()), Map.copyOf(stages));
    }

    private void transition(int slot, Stage from, Stage to) {
        require(slot, from);
        pending.put(slot, to);
        updatePeaks();
    }
    private void require(int slot, Stage expected) {
        if (pending.get(slot) != expected) throw new IllegalStateException("Unexpected capture write stage for slot " + slot + ": " + pending.get(slot) + " (expected " + expected + ")");
    }
    private int count(Stage stage) { return (int)pending.values().stream().filter(value -> value == stage).count(); }
    private void updatePeaks() {
        peakPending = Math.max(peakPending, pending.size());
        peakImageSlots = Math.max(peakImageSlots, pending.size() - count(Stage.AWAITING_CALLBACK));
        peakWaitingWriter = Math.max(peakWaitingWriter, count(Stage.WAITING_WRITER));
        peakWriting = Math.max(peakWriting, count(Stage.WRITING));
    }

    /** Reserved image slots conservatively include the readback call; released images await callbacks separately. */
    record Snapshot(int maximum, int pendingCount, int imageSlots, int readbackSlots,
                    int waitingWriterImages, int writingImages, int awaitingCallbacks,
                    int peakPendingCount, int peakImageSlots, int peakWaitingWriterImages,
                    int peakWritingImages, int peakExecutorQueueSize, int acceptedSlots,
                    int settledCallbacks, int rejectedBeforeWrite, List<Integer> pendingSlots,
                    Map<Integer, String> pendingStages) {}
}

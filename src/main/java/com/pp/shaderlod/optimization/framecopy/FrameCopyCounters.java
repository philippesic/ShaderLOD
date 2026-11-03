package com.pp.shaderlod.optimization.framecopy;

import java.util.ArrayList;
import java.util.List;

/** Render-thread-owned counters. No GL queries, reflection, allocation or clocks per executed copy. */
public final class FrameCopyCounters {
    public record Target(int target, int sourceFramebuffer, int sourceTexture, int destinationTexture,
                         int width, int height, String internalFormat, int bytesPerTexel) {}
    public record TargetSnapshot(Target target, long executedCopies, long texels, long payloadBytes) {}
    public record Snapshot(boolean observing, boolean apiPinned, String eligibility,
                           long finalPassesEntered, long finalPassesCompleted, long plannedSwapCopies,
                           long executedSwapCopies, long swapTexels, long swapPayloadBytes,
                           long executedFallbackCopies, long fallbackTexels, long fallbackPayloadBytes,
                           long unknownByteCopies, long invalidExtentCopies, long metadataFailures,
                           long streamMismatches, long graphChanges, boolean saturated,
                           String metadataStatus, List<TargetSnapshot> targets) {}
    private long entered, completed, planned, swaps, swapTexels, swapBytes, fallbacks, fallbackTexels;
    private long unknown, invalid, metadataFailures, mismatches, graphChanges;
    private boolean saturated, inFinal;
    private int cursor;
    private Object graph;
    private List<Target> targets = List.of();
    private long[] counts = new long[0], texels = new long[0], bytes = new long[0];
    private String metadataStatus = "NOT_OBSERVED";

    public void begin(Object graphKey, List<Target> plan, String status) {
        if (inFinal) mismatches = add(mismatches, 1);
        if (graph != graphKey || targets != plan) {
            graph = graphKey; targets = plan;
            counts = new long[plan.size()]; texels = new long[plan.size()]; bytes = new long[plan.size()];
            graphChanges = add(graphChanges, 1);
        }
        metadataStatus = status;
        if (!"OK".equals(status)) metadataFailures = add(metadataFailures, 1);
        entered = add(entered, 1); planned = add(planned, plan.size());
        cursor = 0; inFinal = true;
    }
    /** Called AFTER the native call returned; proves command submission, not GPU completion/success. */
    public void swapExecuted(int width, int height) {
        swaps = add(swaps, 1);
        long area = area(width, height); swapTexels = add(swapTexels, area);
        int index = cursor++;
        if (!inFinal || index >= targets.size()) { unknown = add(unknown, 1); mismatches = add(mismatches, 1); return; }
        Target target = targets.get(index);
        counts[index] = add(counts[index], 1); texels[index] = add(texels[index], area);
        if (width != target.width() || height != target.height()) {
            mismatches = add(mismatches, 1); unknown = add(unknown, 1); return;
        }
        if (target.bytesPerTexel() <= 0) { unknown = add(unknown, 1); return; }
        long payload = multiply(area, target.bytesPerTexel());
        swapBytes = add(swapBytes, payload); bytes[index] = add(bytes[index], payload);
    }
    /** The host target format is deliberately unknown; do not invent a four-byte estimate. */
    public void fallbackExecuted(int width, int height) {
        fallbacks = add(fallbacks, 1); fallbackTexels = add(fallbackTexels, area(width, height));
        unknown = add(unknown, 1);
        if (!inFinal) mismatches = add(mismatches, 1);
    }
    public void end() {
        if (!inFinal || cursor != targets.size()) mismatches = add(mismatches, 1);
        completed = add(completed, 1); inFinal = false;
    }
    public Snapshot snapshot(boolean observing, boolean apiPinned) {
        var rows = new ArrayList<TargetSnapshot>(targets.size());
        for (int i = 0; i < targets.size(); i++) rows.add(new TargetSnapshot(targets.get(i), counts[i], texels[i], bytes[i]));
        return new Snapshot(observing, apiPinned,
                apiPinned ? "OBSERVATION_ONLY_ALIAS_HISTORY_UNPROVEN" : "UNSUPPORTED_IRIS_API",
                entered, completed, planned, swaps, swapTexels, swapBytes, fallbacks, fallbackTexels, 0,
                unknown, invalid, metadataFailures, mismatches, graphChanges, saturated, metadataStatus, List.copyOf(rows));
    }
    private long area(int width, int height) {
        if (width < 0 || height < 0) { invalid = add(invalid, 1); return 0; }
        return (long) width * height;
    }
    private long add(long a, long b) {
        if (Long.MAX_VALUE - a < b) { saturated = true; return Long.MAX_VALUE; }
        return a + b;
    }
    private long multiply(long a, int b) {
        if (a > Long.MAX_VALUE / b) { saturated = true; return Long.MAX_VALUE; }
        return a * b;
    }
}

package com.pp.shaderlod.dev.timing;

import java.util.ArrayList;
import java.util.List;
import net.irisshaders.iris.gl.uniform.FloatSupplier;

/** Dev-only observation at existing Iris suppliers; no additional smoother reads. */
public final class ShaderInputTracker {
    private static final Float FIXED = parseFixedWetness(System.getProperty("shaderlod.dev.fixedWetness"));
    private static final List<Probe> PROBES = new ArrayList<>();
    private static final int MAX_REGISTRATIONS = 8192;
    private static long reads, windowReads;
    private static Probe latest;
    private static float rawMin = Float.POSITIVE_INFINITY, rawMax = Float.NEGATIVE_INFINITY;
    private static float appliedMin = Float.POSITIVE_INFINITY, appliedMax = Float.NEGATIVE_INFINITY;

    private ShaderInputTracker() {}

    public static Float parseFixedWetness(String value) {
        if (value == null || value.isBlank()) return null;
        float parsed = Float.parseFloat(value);
        if (!Float.isFinite(parsed) || parsed < 0 || parsed > 1) throw new IllegalArgumentException("Fixed shader wetness must be finite within [0,1]");
        return parsed;
    }

    public static float applyWetness(float value, Float fixed) {
        if (fixed == null) return value;
        if (!Float.isFinite(fixed)) throw new IllegalArgumentException("Nonfinite fixed shader wetness");
        return Math.clamp(fixed, 0.0f, 1.0f);
    }

    public static FloatSupplier wrapWetness(FloatSupplier original) { return wrapWetness(original, "unknown holder/program"); }

    public static FloatSupplier wrapWetness(FloatSupplier original, String label) {
        if (PROBES.size() >= MAX_REGISTRATIONS) throw new IllegalStateException("Development wetness registration capacity exceeded");
        var probe = new Probe(PROBES.size() + 1, label);
        PROBES.add(probe); // registration-time allocation, never at uniform evaluation
        return () -> {
            float observed = original.getAsFloat(); // exactly one normal Iris evaluation, even when fixed
            float published = applyWetness(observed, FIXED);
            reads++; windowReads++;
            probe.observe(observed, published);
            latest = probe;
            if (Float.isFinite(observed)) { rawMin = Math.min(rawMin, observed); rawMax = Math.max(rawMax, observed); }
            if (Float.isFinite(published)) { appliedMin = Math.min(appliedMin, published); appliedMax = Math.max(appliedMax, published); }
            return published;
        };
    }

    public static void beginMeasurement() {
        windowReads = 0;
        rawMin = appliedMin = Float.POSITIVE_INFINITY;
        rawMax = appliedMax = Float.NEGATIVE_INFINITY;
        for (Probe probe : PROBES) probe.resetWindow();
    }

    public static Snapshot snapshot() {
        return new Snapshot(FIXED == null ? "dynamic" : "fixed", FIXED, PROBES.size(), reads, windowReads,
                latest == null ? null : finite(latest.raw), latest == null ? null : finite(latest.applied),
                finite(rawMin), finite(rawMax), finite(appliedMin), finite(appliedMax),
                PROBES.stream().map(Probe::snapshot).toList());
    }

    private static Float finite(float value) { return Float.isFinite(value) ? value : null; }
    private static final class Probe {
        final int id;
        final String label;
        long reads, windowReads;
        float raw = Float.NaN, applied = Float.NaN;
        float rawMin = Float.POSITIVE_INFINITY, rawMax = Float.NEGATIVE_INFINITY;
        float appliedMin = Float.POSITIVE_INFINITY, appliedMax = Float.NEGATIVE_INFINITY;
        Probe(int id, String label) { this.id = id; this.label = label; }
        void observe(float observed, float published) {
            reads++; windowReads++;
            raw = observed; applied = published;
            if (Float.isFinite(raw)) { rawMin = Math.min(rawMin, raw); rawMax = Math.max(rawMax, raw); }
            if (Float.isFinite(applied)) { appliedMin = Math.min(appliedMin, applied); appliedMax = Math.max(appliedMax, applied); }
        }
        void resetWindow() {
            windowReads = 0;
            rawMin = appliedMin = Float.POSITIVE_INFINITY;
            rawMax = appliedMax = Float.NEGATIVE_INFINITY;
        }
        ProbeSnapshot snapshot() {
            return new ProbeSnapshot(id, label, "unknown; registration ID is not a shader program ID", reads, windowReads,
                    finite(raw), finite(applied), finite(rawMin), finite(rawMax), finite(appliedMin), finite(appliedMax));
        }
    }
    public record ProbeSnapshot(int registrationId, String holderLabel, String programIdentity, long supplierReads, long measurementReads,
                                Float rawWetness, Float appliedWetness, Float rawMin, Float rawMax, Float appliedMin, Float appliedMax) {}
    public record Snapshot(String mode, Float fixedWetness, long registrations, long supplierReads, long measurementReads,
                           Float rawWetness, Float appliedWetness, Float rawMin, Float rawMax, Float appliedMin, Float appliedMax,
                           List<ProbeSnapshot> suppliers) {}
}

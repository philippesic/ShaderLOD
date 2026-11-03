package com.pp.shaderlod;

import java.util.Arrays;
import java.util.Objects;

/**
 * Exact immutable input values for the pinned Sodium 0.6.13/Iris 1.8.12
 * shadow traversal. INPUTS_CAPTURED certifies only value capture, never producer
 * coverage, list ownership, replay, eligibility, a cache hit or permission to skip.
 * Runtime adapters must copy actual stored values at the native consuming call.
 */
public final class ShadowTraversalInputs {
    public static final String PROPERTY = "shaderlod.dev.shadowTraversalCertificate";
    private ShadowTraversalInputs() {}

    public enum Status { INPUTS_CAPTURED, UNCERTIFIED }
    public enum FrustumKind { ADVANCED, REVERSED_ADVANCED, UNKNOWN }
    public enum ProjectionSelection { VANILLA, DISTANT_HORIZONS, UNKNOWN }
    public enum Reason {
        NONE, MISSING_INPUT, NONFINITE_CAMERA, NONFINITE_TRANSFORM, INVALID_SEARCH_DISTANCE,
        INVALID_WORLD_RANGE, SORT_ORIGIN_MISMATCH, NOT_SHADOW_PASS, UNKNOWN_LIFECYCLE,
        UNKNOWN_PROJECTION, UNSUPPORTED_FRUSTUM, INCOMPLETE_FRUSTUM, NONFINITE_FRUSTUM,
        INVALID_BOX, UNKNOWN_FRUSTUM_IMPLEMENTATION
    }

    public record Capture(Status status, Reason reason, Key key) {
        private static Capture refused(Reason reason) {
            return new Capture(Status.UNCERTIFIED, reason, null);
        }
    }

    /** Identity tokens deliberately use reference equality, even for equal-valued objects. */
    public static final class Identity {
        private final Object value;
        private Identity(Object value) { this.value = value; }
        public static Identity of(Object value) { return new Identity(value); }
        public boolean isPresent() { return value != null; }
        @Override public boolean equals(Object other) {
            return other instanceof Identity identity && value == identity.value;
        }
        @Override public int hashCode() { return System.identityHashCode(value); }
    }

    /** Camera block position is consumed by shouldUseOcclusionCulling, independently of viewport. */
    public record CameraState(long xBits, long yBits, long zBits, int blockX, int blockY, int blockZ) {
        public static CameraState stored(double x, double y, double z, int blockX, int blockY, int blockZ) {
            return new CameraState(bits(x), bits(y), bits(z), blockX, blockY, blockZ);
        }
    }

    /** Never derive int/fraction fields from xyz: Sodium stores truncation and signed-128 rounding. */
    public record StoredTransform(int intX, int intY, int intZ, int fracXBits, int fracYBits, int fracZBits,
                                  long xBits, long yBits, long zBits) {
        public static StoredTransform stored(int intX, int intY, int intZ,
                                             float fracX, float fracY, float fracZ,
                                             double x, double y, double z) {
            return new StoredTransform(intX, intY, intZ, bits(fracX), bits(fracY), bits(fracZ),
                    bits(x), bits(y), bits(z));
        }
    }

    public record ViewportState(StoredTransform transform, int sectionX, int sectionY, int sectionZ,
                                int blockX, int blockY, int blockZ) {}

    /** Search and occlusion values are the actual native return locals, not re-evaluated options. */
    public record TraversalState(int searchDistanceBits, boolean useOcclusionCulling, boolean spectator,
                                 int minSection, int maxSection, int sortSectionX, int sortSectionY,
                                 int sortSectionZ, boolean shadowPass, boolean booleanSpecialization,
                                 boolean frustumCoherence) {
        public static TraversalState effective(float distance, boolean occlusion, boolean spectator,
                                                int minSection, int maxSection, int sortX, int sortY,
                                                int sortZ, boolean shadow, boolean specialization,
                                                boolean coherence) {
            return new TraversalState(bits(distance), occlusion, spectator, minSection, maxSection,
                    sortX, sortY, sortZ, shadow, specialization, coherence);
        }
    }

    /** Known is supplied only by the lifecycle adapter; serial must change for same-owner reloads. */
    public record Lifecycle(Identity manager, Identity world, Identity renderer, Identity pipeline,
                            Identity pack, Identity resources, Identity selectedProjection,
                            long serial, boolean known, ProjectionSelection projectionSelection) {
        private boolean complete() {
            return known && serial >= 0 && present(manager) && present(world) && present(renderer)
                    && present(pipeline) && present(pack) && present(resources)
                    && present(selectedProjection);
        }
    }

    /** Capture all actual BoxCuller stored fields; null boxes use absent(), never infinity sentinels. */
    public record BoxState(boolean present, long maxDistanceBits, long minXBits, long maxXBits,
                            long minYBits, long maxYBits, long minZBits, long maxZBits) {
        public static BoxState absent() { return new BoxState(false, 0, 0, 0, 0, 0, 0, 0); }
        public static BoxState stored(double distance, double minX, double maxX, double minY,
                                       double maxY, double minZ, double maxZ) {
            return new BoxState(true, bits(distance), bits(minX), bits(maxX), bits(minY), bits(maxY),
                    bits(minZ), bits(maxZ));
        }
        private boolean valid() {
            return !present || (finite(maxDistanceBits) && value(maxDistanceBits) >= 0
                    && finite(minXBits, maxXBits, minYBits, maxYBits, minZBits, maxZBits)
                    && value(minXBits) <= value(maxXBits) && value(minYBits) <= value(maxYBits)
                    && value(minZBits) <= value(maxZBits));
        }
    }

    /**
     * Only the typed pinned runtime adapter may select a known kind after EXACT
     * class equality. Planes are active first-four coefficient bits in native
     * order; backing arrays and returned diagnostic arrays are independently cloned.
     */
    public static final class FrustumState {
        private final FrustumKind kind;
        private final boolean complete;
        private final String implementation;
        private final long xBits, yBits, zBits;
        private final boolean useFma;
        private final int[][] planeBits;
        private final BoxState primaryBox, distanceBox;

        private FrustumState(FrustumKind kind, boolean complete, String implementation,
                             double x, double y, double z, boolean fma, int[][] planes,
                             BoxState primaryBox, BoxState distanceBox) {
            this.kind = kind;
            this.complete = complete;
            this.implementation = implementation;
            this.xBits = bits(x); this.yBits = bits(y); this.zBits = bits(z);
            this.useFma = fma;
            this.planeBits = clonePlanes(planes);
            this.primaryBox = primaryBox; this.distanceBox = distanceBox;
        }

        public static FrustumState unknown(String implementation) {
            return new FrustumState(FrustumKind.UNKNOWN, false, implementation, 0, 0, 0,
                    false, null, null, null);
        }

        public static FrustumState stored(FrustumKind kind, double x, double y, double z,
                                           boolean fma, float[][] planes, int planeCount,
                                           BoxState primaryBox, BoxState distanceBox) {
            String implementation = kind == FrustumKind.ADVANCED
                    ? "net.irisshaders.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum"
                    : kind == FrustumKind.REVERSED_ADVANCED
                    ? "net.irisshaders.iris.shadows.frustum.advanced.ReversedAdvancedShadowCullingFrustum"
                    : "unknown";
            boolean complete = planes != null && planeCount >= 0 && planeCount <= 13
                    && planeCount <= planes.length && primaryBox != null && distanceBox != null
                    && (kind != FrustumKind.ADVANCED || !distanceBox.present());
            int[][] snapshot = complete ? new int[planeCount][] : null;
            if (complete) for (int i = 0; i < planeCount; i++) {
                if (planes[i] == null || planes[i].length < 4) { complete = false; break; }
                snapshot[i] = new int[4];
                for (int j = 0; j < 4; j++) snapshot[i][j] = bits(planes[i][j]);
            }
            return new FrustumState(kind, complete, implementation, x, y, z, fma,
                    snapshot, primaryBox, distanceBox);
        }

        public FrustumKind kind() { return kind; }
        public int planeCount() { return planeBits == null ? 0 : planeBits.length; }
        public int[][] planeBits() { return clonePlanes(planeBits); }
        public boolean useFma() { return useFma; }
        public BoxState primaryBox() { return primaryBox; }
        public BoxState distanceBox() { return distanceBox; }
        public String implementation() { return implementation; }
        private Reason problem() {
            if (kind == null || kind == FrustumKind.UNKNOWN) return Reason.UNSUPPORTED_FRUSTUM;
            if (!complete) return Reason.INCOMPLETE_FRUSTUM;
            if (!finite(xBits, yBits, zBits)) return Reason.NONFINITE_FRUSTUM;
            for (int[] plane : planeBits) for (int coefficient : plane)
                if (!Float.isFinite(Float.intBitsToFloat(coefficient))) return Reason.NONFINITE_FRUSTUM;
            if (!primaryBox.valid() || !distanceBox.valid()) return Reason.INVALID_BOX;
            return Reason.NONE;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof FrustumState state)) return false;
            return kind == state.kind && complete == state.complete && xBits == state.xBits
                    && yBits == state.yBits && zBits == state.zBits && useFma == state.useFma
                    && Objects.equals(implementation, state.implementation)
                    && Arrays.deepEquals(planeBits, state.planeBits)
                    && Objects.equals(primaryBox, state.primaryBox) && Objects.equals(distanceBox, state.distanceBox);
        }
        @Override public int hashCode() {
            return 31 * Objects.hash(kind, complete, implementation, xBits, yBits, zBits, useFma,
                    primaryBox, distanceBox) + Arrays.deepHashCode(planeBits);
        }
    }

    /** Frame/generation is intentionally absent; producer and touched-write certificates own it. */
    public record Key(CameraState camera, ViewportState viewport, TraversalState traversal,
                       Lifecycle lifecycle, FrustumState frustum) {}

    public static Capture capture(CameraState camera, ViewportState viewport, TraversalState traversal,
                                    Lifecycle lifecycle, FrustumState frustum) {
        if (camera == null || viewport == null || viewport.transform() == null || traversal == null
                || lifecycle == null || frustum == null) return Capture.refused(Reason.MISSING_INPUT);
        if (!finite(camera.xBits(), camera.yBits(), camera.zBits()))
            return Capture.refused(Reason.NONFINITE_CAMERA);
        StoredTransform transform = viewport.transform();
        if (!finite(transform.xBits(), transform.yBits(), transform.zBits())
                || !Float.isFinite(Float.intBitsToFloat(transform.fracXBits()))
                || !Float.isFinite(Float.intBitsToFloat(transform.fracYBits()))
                || !Float.isFinite(Float.intBitsToFloat(transform.fracZBits())))
            return Capture.refused(Reason.NONFINITE_TRANSFORM);
        float distance = Float.intBitsToFloat(traversal.searchDistanceBits());
        if (!Float.isFinite(distance) || distance < 0) return Capture.refused(Reason.INVALID_SEARCH_DISTANCE);
        if (traversal.minSection() >= traversal.maxSection()) return Capture.refused(Reason.INVALID_WORLD_RANGE);
        if (traversal.sortSectionX() != viewport.sectionX() || traversal.sortSectionY() != viewport.sectionY()
                || traversal.sortSectionZ() != viewport.sectionZ()) return Capture.refused(Reason.SORT_ORIGIN_MISMATCH);
        if (!traversal.shadowPass()) return Capture.refused(Reason.NOT_SHADOW_PASS);
        if (!lifecycle.complete()) return Capture.refused(Reason.UNKNOWN_LIFECYCLE);
        if (lifecycle.projectionSelection() == null || lifecycle.projectionSelection() == ProjectionSelection.UNKNOWN)
            return Capture.refused(Reason.UNKNOWN_PROJECTION);
        Reason problem = frustum.problem();
        if (problem != Reason.NONE) return Capture.refused(problem);
        return new Capture(Status.INPUTS_CAPTURED, Reason.NONE, new Key(camera, viewport, traversal, lifecycle, frustum));
    }

    private static boolean present(Identity identity) { return identity != null && identity.isPresent(); }
    private static int bits(float value) { return Float.floatToRawIntBits(value); }
    private static long bits(double value) { return Double.doubleToRawLongBits(value); }
    private static double value(long bits) { return Double.longBitsToDouble(bits); }
    private static boolean finite(long... bits) {
        for (long value : bits) if (!Double.isFinite(value(value))) return false;
        return true;
    }
    private static int[][] clonePlanes(int[][] planes) {
        if (planes == null) return null;
        int[][] copy = new int[planes.length][];
        for (int i = 0; i < planes.length; i++) copy[i] = planes[i] == null ? null : planes[i].clone();
        return copy;
    }
}

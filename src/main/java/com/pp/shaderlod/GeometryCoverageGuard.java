package com.pp.shaderlod;

/** A manual scene acknowledgement is never evidence for a later view or renderer. */
public final class GeometryCoverageGuard {
    /** Grid cells only bound travel; remaining inside one cell is not a coverage proof. */
    public record Scene(Object world, Object pipeline, Object camera, int chunkX, int sectionY,
                        int chunkZ, int requestedChunks, int cap, int dhChunks) {
        public boolean matches(Scene other) {
            return other != null && world == other.world && pipeline == other.pipeline
                    && camera == other.camera && chunkX == other.chunkX && sectionY == other.sectionY
                    && chunkZ == other.chunkZ && requestedChunks == other.requestedChunks
                    && cap == other.cap && dhChunks == other.dhChunks;
        }

        /** Allocation-free travel check for the Options getter. */
        public boolean contains(Object currentWorld, Object currentCamera, double x, double y, double z) {
            return world == currentWorld && camera == currentCamera
                    && chunkX == cell(x) && sectionY == cell(y) && chunkZ == cell(z);
        }
    }

    private volatile Scene confirmedScene;
    private volatile boolean armed = true;

    /** Null scenes before first eligibility wait; after confirmation they revoke acknowledgement. */
    public boolean confirmed(Scene scene, boolean acknowledgement) {
        Scene previous = confirmedScene;
        if (!acknowledgement) {
            confirmedScene = null;
            armed = true;
        } else if (armed && scene != null) {
            confirmedScene = scene;
            armed = false;
        } else if (previous != null && !previous.matches(scene)) {
            // Stay disarmed even if the camera returns to its old position.
            confirmedScene = null;
        }
        return acknowledgement && isConfirmed(scene);
    }

    /** Latch a getter-observed excursion even before a client tick. */
    public void invalidate() {
        confirmedScene = null;
        armed = false;
    }

    /** Explicit inspected-scene action; binding still waits for eligibility/warmup. */
    public void rearm() {
        confirmedScene = null;
        armed = true;
    }

    public boolean isConfirmed(Scene scene) {
        Scene current = confirmedScene;
        return current != null && current.matches(scene);
    }

    public static int cell(double coordinate) {
        return (int) Math.floor(coordinate / 16.0);
    }
}

package com.pp.shaderlod.integration.makeupquad;
/** Capacity is captured from the actual native allocation, never inferred from batch size. */
public interface PortBatchState {
    long shaderlod$rendererGeneration();
    void shaderlod$rendererGeneration(long value);
    void shaderlod$invalidateIndexScalar();
    int shaderlod$capacity();
    boolean shaderlod$rendererEnabled();
    void shaderlod$rendererEnabled(boolean value);
}

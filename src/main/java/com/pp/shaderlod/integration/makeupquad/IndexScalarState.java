package com.pp.shaderlod.integration.makeupquad;
/** Pinned batch-owned scalar; no command or index storage is retained. */
public final class IndexScalarState {
    private boolean valid; private int size,maximum; private long generation;
    public void invalidate() {valid=false;}
    public boolean reusable(boolean filled,int actualSize,long actualGeneration) {
        return valid && filled && size==actualSize && generation==actualGeneration;
    }
    public int maximum() {if(!valid)throw new IllegalStateException("Invalid index scalar");return maximum;}
    public void remember(boolean filled,int actualSize,int nativeMaximum,long actualGeneration) {
        valid=filled && actualSize>=0 && nativeMaximum>=0;
        size=actualSize;maximum=nativeMaximum;generation=actualGeneration;
    }
}

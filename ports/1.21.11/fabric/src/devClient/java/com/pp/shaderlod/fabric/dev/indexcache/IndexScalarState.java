package com.pp.shaderlod.fabric.dev.indexcache;
/** Batch-local scalar memo; writes invalidate before partial mutation can begin. */
public final class IndexScalarState {
    private boolean valid;
    private int size, maximum;
    public void invalidate() { valid=false; }
    public boolean reusable(boolean filled,int actualSize) { return valid && filled && actualSize==size; }
    public int maximum() { if(!valid)throw new IllegalStateException("Invalid index scalar");return maximum; }
    public void remember(boolean filled,int actualSize,int nativeMaximum) {
        valid=filled && actualSize>=0 && nativeMaximum>=0;
        size=actualSize;maximum=nativeMaximum;
    }
}

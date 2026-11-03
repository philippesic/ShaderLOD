package com.pp.shaderlod.optimization.framecopy.exchange;

/** Ordered native RT8/RT1 obligations; a return is not a completion witness. */
public final class RetainedCopyPair {
    private int started,returned;
    private boolean completed;
    public int started() { return started; }
    public int returned() { return returned; }
    public boolean completed() { return completed; }
    public boolean quiescent() { return started == 0 || completed; }
    public void nextFrame() {
        require(quiescent(),"PENDING_PAIR_AT_NEXT_FRAME");started=returned=0;completed=false;
    }
    public void start(int index) {
        require(!completed&&(index==0&&started==0&&returned==0||index==1&&started==1&&returned==1),"COPY_START_ORDER_OR_MISSING_RETURN");
        started|=1<<index;
    }
    public void returned(int index) {
        require(index>=0&&index<2&&started==(index==0?1:3)&&returned==(index==0?0:1),"COPY_RETURN_ORDER");
        returned|=1<<index;
    }
    public void completionSucceeded() {
        require(started==3&&returned==3&&!completed,"EXACT_RETURNED_PAIR_REQUIRED");completed=true;
    }
    public void requireCompletedBeforeHistory() { require(completed,"PAIR_COMPLETION_REQUIRED_BEFORE_RT3"); }
    private static void require(boolean valid,String reason){if(!valid)throw new IllegalStateException(reason);}
}

package com.pp.shaderlod.tuning;

/** Bounded automatic retries; parking never acknowledges or releases the caller's scene owner. */
final class TuningRecovery {
    @FunctionalInterface interface Operation {void restore() throws Throwable;}
    enum Result { COMPLETE, RETRY, PARKED }
    private int attempts;
    private boolean parked;
    private Throwable failure;
    Result attempt(Operation operation) {
        if(parked)return Result.PARKED; // No operation or I/O until explicit retry.
        attempts++;
        try {operation.restore();failure=null;return Result.COMPLETE;}
        catch(Throwable error){failure=error;parked=attempts>=3;return parked?Result.PARKED:Result.RETRY;}
    }
    void explicitRetry(){attempts=0;parked=false;}
    boolean parked(){return parked;}
    int attempts(){return attempts;}
    Throwable failure(){return failure;}
}

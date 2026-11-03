package com.pp.shaderlod.tuning;

/** Time and frame requirements apply together; a fast renderer cannot shorten a trial. */
record TuningWindow(long warmupNs,int warmupFrames,long sampleNs,int sampleFrames,long cutoffNs) {
    /** Focus/chat share one pause interval; their overlap must not be subtracted twice. */
    static final class InputPause {
        private boolean paused;
        private long at;
        void pause(long now){if(!paused){paused=true;at=now;}}
        long resume(long now){if(!paused)return 0;paused=false;return now-at;}
    }
    /** Only the first interval after an explicit clock reset lacks a preceding render start. */
    static final class InitialInterval {
        private boolean seen;
        void reset() { seen = false; }
        boolean exclude(com.pp.shaderlod.measurement.FrameMeasurement.Sample sample) {
            if (seen) return false;
            seen = true;
            return sample.frameWallNs() == null;
        }
    }
    static final TuningWindow SCREEN=new TuningWindow(2_000_000_000L,60,3_000_000_000L,180,30_000_000_000L);
    static final TuningWindow CONFIRM=new TuningWindow(3_000_000_000L,90,5_000_000_000L,240,40_000_000_000L);
    static final TuningWindow FINAL=new TuningWindow(4_000_000_000L,120,8_000_000_000L,360,60_000_000_000L);
    /** Admission reserves the entire planned screen AND confirmation, not just a first trial. */
    static long candidateReserveSeconds(int screens,int confirmations){
        return 3L*screens*plannedSeconds(SCREEN)+3L*confirmations*plannedSeconds(CONFIRM);
    }
    static long finalReserveSeconds(int poses,int brackets,int motionBrackets){
        return poses*3L*brackets*plannedSeconds(FINAL)+motionBrackets*3L*7+20;
    }
    private static long plannedSeconds(TuningWindow window){return (window.warmupNs+window.sampleNs)/1_000_000_000L+2;}
    boolean warming(long elapsed,int frames){return elapsed<warmupNs||frames<warmupFrames;}
    boolean complete(long measuredNs,int frames){return measuredNs>=sampleNs&&frames>=sampleFrames;}
}

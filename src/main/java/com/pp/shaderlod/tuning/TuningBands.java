package com.pp.shaderlod.tuning;
import com.pp.shaderlod.LodSettings;
/** Raw user fields, without ShaderLodConfig.snapshot's repair/clamping of cross-field edits. */
record TuningBands(double near,double far,double transition,boolean debug) {
    TuningBands {new LodSettings(true,near,far,transition,debug);}
    void requireSame(TuningBands actual) {
        if(!equals(actual))throw new IllegalStateException("LOD near/far/transition/debug changed externally; tuning aborted and user edits retained");
    }
}

package com.pp.shaderlod.tuning;
import com.pp.shaderlod.LodPreset;
/** Search goal is independent of the engine's native QUALITY mode and stable preset ordinals. */
public enum TuningGoal {
    PERFORMANCE, BALANCED, QUALITY;
    public LodPreset enginePreset(){return this==BALANCED?LodPreset.BALANCED:LodPreset.PERFORMANCE;}
    public String label(){return this==QUALITY?"Quality — exact changes only":this==BALANCED?"Balanced":"Performance";}
}

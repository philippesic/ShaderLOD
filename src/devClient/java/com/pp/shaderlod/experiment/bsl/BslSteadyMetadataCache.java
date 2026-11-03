package com.pp.shaderlod.experiment.bsl;
import java.util.function.BooleanSupplier;
/** Bounds metadata validation, never resource/GL/epoch guards. Native frame
 * epochs are distinct from counter values. Every identity compares by reference.
 * Direct within-frame external mutation of Iris's exposed option maps is outside
 * this pinned native-frame protocol; identity is not a mutable-map certificate. */
public final class BslSteadyMetadataCache {
    public record Options(long renderEpoch,Object pipeline,Object pack,Object options,Object values,
                          Object set,Object context,Object resources,Object target,Object depth,Object source) {
        boolean same(Options b){return b!=null&&renderEpoch==b.renderEpoch&&pipeline==b.pipeline&&pack==b.pack
            &&options==b.options&&values==b.values&&set==b.set&&context==b.context&&resources==b.resources
            &&target==b.target&&depth==b.depth&&source==b.source;}
    }
    private Options options;
    private boolean optionsValue,coreValue;
    private Object coreState,coreFrame;
    private long coreEpoch;
    public boolean options(Options key,BooleanSupplier validate) {
        if(key==null){invalidate();return false;}
        if(options==null||!options.same(key)){options=key;optionsValue=false;optionsValue=validate.getAsBoolean();}
        return optionsValue;
    }
    public boolean core(Object state,Object immutableFrame,long epoch,BooleanSupplier validate) {
        if(state==null||immutableFrame==null){coreState=coreFrame=null;coreValue=false;return false;}
        if(coreState!=state||coreFrame!=immutableFrame||coreEpoch!=epoch){
            coreState=state;coreFrame=immutableFrame;coreEpoch=epoch;coreValue=false;coreValue=validate.getAsBoolean();
        }
        return coreValue;
    }
    public void invalidate(){options=null;optionsValue=false;coreState=coreFrame=null;coreValue=false;}
}

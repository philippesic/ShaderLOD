package com.pp.shaderlod.optimization.sourcecache;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** A synchronous build transaction. Rendering can only consume a published immutable result. */
public final class SourceProofEpoch {
    private long generation;
    private Attempt active;
    private Receipt published;
    private record Receipt(Object owner, long generation, Map<Object,Boolean> proofs) {}
    public final class Attempt implements AutoCloseable {
        private final long epoch;
        private final Map<Object,Boolean> proofs=new HashMap<>();
        private boolean closed;
        private Attempt(long epoch){this.epoch=epoch;}
        public boolean validate(Object key, BooleanSupplier validation){
            if(closed||active!=this||epoch!=generation)return false;
            Boolean prior=proofs.get(key);if(prior!=null)return prior;
            boolean result=validation.getAsBoolean();
            if(closed||active!=this||epoch!=generation)return false;
            proofs.put(key,result);return result;
        }
        public boolean publish(Object owner, BooleanSupplier current){
            if(owner==null||closed||active!=this||epoch!=generation||!current.getAsBoolean())return false;
            if(closed||active!=this||epoch!=generation)return false;
            published=new Receipt(owner,epoch,Map.copyOf(proofs));return true;
        }
        public void close(){closed=true;if(active==this)active=null;}
    }
    public Attempt begin(){invalidate();active=new Attempt(generation);return active;}
    public void invalidate(){generation++;published=null;active=null;}
    public boolean current(Object owner,Object key){
        var receipt=published;return receipt!=null&&receipt.owner()==owner&&receipt.generation()==generation
            &&Boolean.TRUE.equals(receipt.proofs().get(key));
    }
}

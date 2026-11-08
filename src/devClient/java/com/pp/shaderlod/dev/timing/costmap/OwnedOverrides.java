package com.pp.shaderlod.dev.timing.costmap;

import java.util.*;

/** CAS restoration, including absent overrides. Receipt is recorded before a potentially failing setter. */
public final class OwnedOverrides<K,V> implements AutoCloseable {
    public interface Access<K,V> { V get(K key); void set(K key,V value); }
    private final Access<K,V> access;
    private final Map<K,V> before=new LinkedHashMap<>(),written=new LinkedHashMap<>();
    public OwnedOverrides(Access<K,V> access){this.access=access;}
    public void set(K key,V value){
        if(!before.containsKey(key)){before.put(key,access.get(key));written.put(key,access.get(key));}
        if(!Objects.equals(access.get(key),written.get(key)))throw new IllegalStateException("concurrent override edit");
        written.put(key,value);access.set(key,value);
        if(!Objects.equals(access.get(key),value))throw new IllegalStateException("override refused");
    }
    @Override public void close(){
        List<K> keys=new ArrayList<>(before.keySet());Collections.reverse(keys);
        IllegalStateException failure=null;
        for(K key:keys) {
            if(!Objects.equals(access.get(key),written.get(key))){failure=new IllegalStateException("concurrent edit preserved");continue;}
            try {access.set(key,before.get(key));before.remove(key);written.remove(key);}
            catch(RuntimeException e){failure=new IllegalStateException("restoration failed",e);}
        }
        if(failure!=null)throw failure;
    }
}

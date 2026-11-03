package com.pp.shaderlod.optimization.sourcecache;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.function.ToIntFunction;

/** Caches locations (including absence), never mutable uniform values or permissions. */
public final class LinkedUniformLocationCache<P> {
    @FunctionalInterface
    public interface LocationQuery { int query(int program,String name); }
    private record Owner<P>(Object pipeline,Object context,P program,Object2IntOpenHashMap<String> locations) {}
    private final Int2ObjectOpenHashMap<Owner<P>> owners=new Int2ObjectOpenHashMap<>();
    private final ToIntFunction<P> handle;
    private final LocationQuery query;
    public LinkedUniformLocationCache(ToIntFunction<P> handle,LocationQuery query) {
        this.handle=handle;this.query=query;
    }
    private boolean live(P program,int id) {
        try{return handle.applyAsInt(program)==id;}catch(RuntimeException deleted){return false;}
    }
    public void register(int id,Object pipeline,Object context,P program) {
        owners.remove(id);
        if(id>0 && context!=null && program!=null && live(program,id))
            owners.put(id,new Owner<>(pipeline,context,program,new Object2IntOpenHashMap<>()));
    }
    public int location(int id,Object context,String name) {
        var owner=owners.get(id);
        if(owner!=null && !live(owner.program(),id)) {owners.remove(id);owner=null;}
        if(owner==null || owner.context()!=context)return query.query(id,name);
        var locations=owner.locations();
        // GL locations are -1 or nonnegative. The second check also keeps
        // this generic cache correct for a query returning the sentinel.
        int known=locations.getOrDefault(name,-2);
        if(known!=-2 || locations.containsKey(name))return known;
        int actual=query.query(id,name);locations.put(name,actual);return actual;
    }
    public void destroy(Object pipeline) {owners.values().removeIf(owner->owner.pipeline()==pipeline);}
    public void clear() {owners.clear();}
}

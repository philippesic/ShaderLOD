package com.pp.shaderlod.optimization;
import java.util.*;
/** Complete immutable requested flags; availability remains the consumer's responsibility. */
public record OptimizationSnapshot(Map<OptimizationId,Boolean> flags) {
    public OptimizationSnapshot {
        var copy=new EnumMap<OptimizationId,Boolean>(OptimizationId.class);
        for(var id:OptimizationId.values()) copy.put(id, Objects.requireNonNull(flags.get(id), id.key()));
        flags=Collections.unmodifiableMap(copy);
    }
    public static OptimizationSnapshot defaults() {
        var flags=new EnumMap<OptimizationId,Boolean>(OptimizationId.class);
        for(var id:OptimizationId.values()) flags.put(id,id.defaultEnabled());
        return new OptimizationSnapshot(flags);
    }
    public static OptimizationSnapshot noneRequested() {
        var flags=new EnumMap<OptimizationId,Boolean>(OptimizationId.class);
        for(var id:OptimizationId.values())flags.put(id,false);
        return new OptimizationSnapshot(flags);
    }
    public static OptimizationSnapshot allRequested() {
        var flags=new EnumMap<OptimizationId,Boolean>(OptimizationId.class);
        for(var id:OptimizationId.values()) flags.put(id,true);
        return new OptimizationSnapshot(flags);
    }
    public boolean enabled(OptimizationId id) { return flags.get(id); }
    public int uniform(OptimizationId id) { return enabled(id)?1:0; }
    public OptimizationSnapshot with(OptimizationId id,boolean value) {
        var copy=new EnumMap<OptimizationId,Boolean>(flags); copy.put(id,value); return new OptimizationSnapshot(copy);
    }
}

package com.pp.shaderlod.optimization.sourcecache;

import java.util.*;
import java.util.function.*;

/** Explicit native failure is terminal; only explicitly registered derivatives use legacy proof. */
public final class TemporalProgramReceipts<P,C> {
    private record Registration<P>(P program, boolean nativeProgram) {}
    private final Map<Integer,Registration<P>> registrations = new HashMap<>();
    private final LinkedProgramLocations<P,C> linked;
    private long compiledDraws, legacyDraws, refusedDraws;
    public TemporalProgramReceipts(ToIntFunction<P> handle) { linked=new LinkedProgramLocations<>(handle); }
    public void nativeProgram(int id, P program, C certificate, Map<String,Integer> locations) {
        forget(id);
        if(id<=0)return;
        registrations.put(id,new Registration<>(program,true));
        linked.put(id,program,certificate,locations);
    }
    public void legacyProgram(int id) {
        forget(id);
        if(id>0)registrations.put(id,new Registration<>(null,false));
    }
    public boolean allows(int id, Predicate<C> current, BooleanSupplier legacy) {
        var registration=registrations.get(id);
        if(registration==null){refusedDraws++;return false;}
        if(!registration.nativeProgram()){legacyDraws++;return legacy.getAsBoolean();}
        var entry=linked.current(id);
        if(entry==null || entry.program()!=registration.program() || !current.test(entry.certificate())) {
            refusedDraws++;return false;
        }
        compiledDraws++;return true;
    }
    public Integer location(int id,String name) {
        var entry=linked.current(id);
        return entry==null?null:entry.locations().get(name);
    }
    public boolean nativeProgram(int id) {
        var registration=registrations.get(id);
        return registration!=null && registration.nativeProgram();
    }
    public void forget(int id){registrations.remove(id);linked.forget(id);}
    public void clear(){registrations.clear();linked.clear();}
    public Map<String,Long> counters(){return Map.of("compiled",compiledDraws,"legacy",legacyDraws,"refused",refusedDraws);}
}

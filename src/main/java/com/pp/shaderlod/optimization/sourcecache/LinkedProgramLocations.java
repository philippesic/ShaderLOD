package com.pp.shaderlod.optimization.sourcecache;

import java.util.Map;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Locations belong to a Java program's link lifetime, never just a reusable GL name. */
public final class LinkedProgramLocations<P, C> {
    public record Entry<P, C>(P program, C certificate, Map<String, Integer> locations) {
        public Entry { locations = Map.copyOf(locations); }
    }
    private final ToIntFunction<P> handle;
    private final Int2ObjectOpenHashMap<Entry<P, C>> entries = new Int2ObjectOpenHashMap<>();
    public LinkedProgramLocations(ToIntFunction<P> handle) { this.handle = handle; }
    public boolean contains(int id) { return entries.containsKey(id); }
    public void put(int id, P program, C certificate, Map<String, Integer> locations) {
        entries.remove(id);
        if (id > 0 && program != null && certificate != null && live(program, id))
            entries.put(id, new Entry<>(program, certificate, locations));
    }
    private boolean live(P program, int id) {
        try { return handle.applyAsInt(program) == id; }
        catch (RuntimeException invalidated) { return false; }
    }
    public Entry<P, C> current(int id) {
        var entry = entries.get(id);
        if (entry != null && !live(entry.program(), id)) {
            entries.remove(id);
            return null;
        }
        return entry;
    }
    /** A second native registration may preserve evidence only for this exact object. */
    public void observe(int id, P program) {
        var entry = current(id);
        if (entry != null && entry.program() != program) entries.remove(id);
    }
    /** Replace both stores even when a reused name's new role is rejected. */
    public <O> void registerNative(Map<Integer,O> owners, int id, P program, O owner) {
        owners.remove(id);
        if (owner == null || program == null || !live(program,id)) {
            entries.remove(id);
            return;
        }
        observe(id,program);
        owners.put(id,owner);
    }
    public <O> void registerUncertified(Map<Integer,O> owners, int id, O owner) {
        entries.remove(id);
        owners.remove(id);
        if (id > 0 && owner != null) owners.put(id,owner);
    }
    public void forget(int id) { entries.remove(id); }
    public void clear() { entries.clear(); }
    public void removeIf(Predicate<C> predicate) {
        entries.values().removeIf(entry -> predicate.test(entry.certificate()));
    }
}

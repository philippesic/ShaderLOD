package com.pp.shaderlod.optimization;
import java.io.*;
import java.nio.file.*;
import java.util.*;
/** Loader-neutral persisted requests. Edits publish only after a successful atomic write. */
public final class OptimizationConfig {
    public static final int PROTOCOL_VERSION=1;
    private static volatile OptimizationSnapshot current=OptimizationSnapshot.defaults();
    private static volatile long generation;
    private static Path path; private static boolean backedUp;
    private static volatile boolean loaded;
    private static boolean scoped;
    private static String family="global";
    private static OptimizationSnapshot legacy=OptimizationSnapshot.defaults();
    private static final Map<String,OptimizationSnapshot> profiles=new HashMap<>();
    private static Properties preserved=new Properties();
    /** Enabled by the 1.21.1 NeoForge entry point only; unchanged ports retain flat requests. */
    public static synchronized void enableShaderProfiles() {scoped=true;}
    public static synchronized String activeFamily() {return scoped?family:"global";}
    public static synchronized boolean selectFamily(String selected) {
        if(!scoped||family.equals(selected))return false;
        if(!selected.matches("[a-z0-9-]+"))throw new IllegalArgumentException("Invalid shader family");
        family=selected;current=loaded?profiles.getOrDefault(family,legacy):OptimizationSnapshot.noneRequested();generation++;return true;
    }
    public static synchronized OptimizationSnapshot snapshot(String selected) {return profiles.getOrDefault(selected,legacy);}
    /** Restore an old family after an external pack change without overwriting the new family's flags. */
    public static synchronized void restoreFamily(String selected,OptimizationSnapshot flags) throws IOException {
        if(!scoped||family.equals(selected)){replace(flags);return;}
        var old=profiles.put(selected,flags);
        try{write(current);}catch(IOException failure){if(old==null)profiles.remove(selected);else profiles.put(selected,old);throw failure;}
    }
    private OptimizationConfig() {}
    public static OptimizationSnapshot snapshot() { return current; }
    public static long generation() { return generation; }
    public static boolean loaded() { return loaded; }
    public static synchronized void load(Path file) throws IOException {
        loaded=false;current=OptimizationSnapshot.noneRequested();legacy=current;generation++;
        path=file; backedUp=false;family="global";profiles.clear();preserved=new Properties();
        var next=OptimizationSnapshot.defaults();
        if(Files.exists(file)) {
            var properties=new Properties(); try(var reader=Files.newBufferedReader(file)) { properties.load(reader); }
            preserved.putAll(properties);
            for(var id:OptimizationId.values()) {
                String value=properties.getProperty(id.key());
                if(value==null) {
                    // Canonical key always wins; deterministic legacy priority otherwise.
                    for(String legacy:java.util.List.of("quad_index_fill","quad_submission_copy","command_metadata_cache"))
                        if(OptimizationId.keyAliases().get(legacy)==id&&properties.containsKey(legacy)){value=properties.getProperty(legacy);break;}
                }
                if(value!=null) {
                    if(!value.equals("true")&&!value.equals("false")) throw new IOException("Invalid optimization flag "+id.key());
                    next=next.with(id,Boolean.parseBoolean(value));
                }
            }
        }
        legacy=next;
        for(String key:preserved.stringPropertyNames())if(key.startsWith("shader.")) {
            int split=key.indexOf('.',7);if(split<0)continue;
            String selected=key.substring(7,split),feature=key.substring(split+1);
            if(!selected.matches("[a-z0-9-]+"))continue;
            OptimizationId id;try{id=OptimizationId.fromKey(feature);}catch(IllegalArgumentException unknown){continue;}
            if(!feature.equals(id.key())&&preserved.containsKey("shader."+selected+"."+id.key()))continue;
            String value=preserved.getProperty(key);
            if(!value.equals("true")&&!value.equals("false"))throw new IOException("Invalid optimization flag "+key);
            profiles.put(selected,profiles.getOrDefault(selected,legacy).with(id,Boolean.parseBoolean(value)));
        }
        current=next; generation++; loaded=true;
    }
    public static synchronized void replace(OptimizationSnapshot next) throws IOException {
        Objects.requireNonNull(next);
        write(next);
        if(scoped&&!family.equals("global"))profiles.put(family,next);else legacy=next;
        current=next; generation++;if(path!=null)loaded=true;
    }
    private static void write(OptimizationSnapshot next) throws IOException {
        if(path!=null) {
            Files.createDirectories(path.toAbsolutePath().getParent());
            if(!backedUp&&Files.exists(path)) {
                Path backup=path.resolveSibling(path.getFileName()+".bak"); int suffix=1;
                while(Files.exists(backup)) backup=path.resolveSibling(path.getFileName()+".bak."+suffix++);
                Files.copy(path,backup); backedUp=true;
            }
            var properties=new Properties();properties.putAll(preserved);
            var flat=scoped&&!family.equals("global")?legacy:next;
            for(var id:OptimizationId.values())properties.setProperty(id.key(),Boolean.toString(flat.enabled(id)));
            for(var entry:profiles.entrySet())for(var id:OptimizationId.values())properties.setProperty("shader."+entry.getKey()+"."+id.key(),Boolean.toString(entry.getValue().enabled(id)));
            if(scoped&&!family.equals("global"))for(var id:OptimizationId.values())properties.setProperty("shader."+family+"."+id.key(),Boolean.toString(next.enabled(id)));
            Path temp=Files.createTempFile(path.toAbsolutePath().getParent(),"shaderlod-optimization-",".tmp");
            try {
                try(var writer=Files.newBufferedWriter(temp)) { properties.store(writer,"ShaderLOD independent requested optimizations; consumer guards still apply"); }
                try { Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
                catch(AtomicMoveNotSupportedException ex) { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temp); }
        }
    }
    static synchronized void restoreInMemory(OptimizationSnapshot previous) {current=Objects.requireNonNull(previous);if(scoped&&!family.equals("global"))profiles.put(family,previous);else legacy=previous;generation++;}
    record State(OptimizationSnapshot snapshot,Path path,boolean backedUp,boolean loaded,boolean scoped,String family,OptimizationSnapshot legacy,Map<String,OptimizationSnapshot> profiles,Properties preserved) {
        State(OptimizationSnapshot snapshot,Path path,boolean backedUp,boolean loaded) {
            this(snapshot,path,backedUp,loaded,false,"global",snapshot,Map.of(),new Properties());
        }
    }
    static synchronized State state() {var saved=new Properties();saved.putAll(preserved);return new State(current,path,backedUp,loaded,scoped,family,legacy,Map.copyOf(profiles),saved);}
    static synchronized void restoreState(State previous) {current=previous.snapshot();path=previous.path();backedUp=previous.backedUp();loaded=previous.loaded();scoped=previous.scoped();family=previous.family();legacy=previous.legacy();profiles.clear();profiles.putAll(previous.profiles());preserved=new Properties();preserved.putAll(previous.preserved());generation++;}
    public static boolean requested(OptimizationId id) { return current.enabled(id); }
}

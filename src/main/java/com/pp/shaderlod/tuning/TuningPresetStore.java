package com.pp.shaderlod.tuning;

import com.google.gson.*;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Versioned, complete named tunings. Filenames are UUIDs; display names never become paths. */
public final class TuningPresetStore {
    private static final int MAX_BYTES=1024*1024;
    private static final Gson JSON=new GsonBuilder().serializeNulls().setPrettyPrinting().create();
    private final Path directory;
    public TuningPresetStore(Path directory){this.directory=directory.toAbsolutePath().normalize();}
    public List<TuningPreset> list()throws IOException {
        if(!Files.exists(directory))return List.of();requireDirectory();var result=new ArrayList<TuningPreset>();
        try(var files=Files.list(directory)) {
            for(var p:files.filter(x->x.getFileName().toString().endsWith(".json")).sorted().toList())result.add(read(p));
        }
        return result.stream().sorted(Comparator.comparing(TuningPreset::name).thenComparing(TuningPreset::createdAt)).toList();
    }
    public TuningPreset load(String id)throws IOException {return read(file(id));}
    public void save(TuningPreset preset)throws IOException {
        if(!Files.exists(directory))Files.createDirectories(directory);requireDirectory();
        var target=file(preset.id());if(Files.exists(target)&&!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS))throw new IOException("Unsafe tuning file");
        var bytes=encode(preset).getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BYTES)throw new IOException("Tuning file exceeds limit");
        var temp=Files.createTempFile(directory,"tuning-",".tmp");
        try {Files.write(temp,bytes);try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ignored){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}}
        finally{Files.deleteIfExists(temp);}
    }
    public TuningPreset importFile(Path path)throws IOException {var preset=read(path).importedCopy();save(preset);return preset;}
    public static String encode(TuningPreset p) {
        var o=new JsonObject();o.addProperty("schema",4);o.addProperty("id",p.id());o.addProperty("name",p.name());
        o.addProperty("family",p.family());o.addProperty("original_source_sha256",p.originalSourceSha256());
        o.addProperty("enabled",p.enabled());o.addProperty("engine_preset",p.enginePreset().name());
        o.add("bands",JSON.toJsonTree(p.bands()));o.add("terrain_policy",JSON.toJsonTree(p.terrainPolicy()));var flags=new TreeMap<String,Boolean>();
        for(var id:OptimizationId.values())flags.put(id.key(),p.requests().enabled(id));o.add("requests",JSON.toJsonTree(flags));
        o.add("native_options",JSON.toJsonTree(p.nativeOptions()));o.add("effect_options",JSON.toJsonTree(p.effectOptions()));o.add("context",JSON.toJsonTree(p.context()));
        o.add("evidence",JSON.toJsonTree(p.evidence()));o.addProperty("created_at",p.createdAt());
        o.addProperty("configuration_sha256",p.configurationSha256());return JSON.toJson(o)+"\n";
    }
    public static TuningPreset read(Path path)throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>MAX_BYTES)throw new IOException("Invalid tuning file or size");
        try{return decode(Files.readString(path));}catch(RuntimeException failure){throw new IOException("Invalid tuning: "+failure.getMessage(),failure);}
    }
    public static TuningPreset decode(String text) {
        if(text.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("Tuning exceeds limit");
        var o=JsonParser.parseString(text).getAsJsonObject();
        if(!o.has("schema")||!o.get("schema").isJsonPrimitive()||!o.get("schema").getAsJsonPrimitive().isNumber()||!Set.of("1","2","3","4").contains(o.get("schema").getAsString()))throw new IllegalArgumentException("Unknown tuning schema");
        String schema=o.get("schema").getAsString();boolean hasTerrain=!"1".equals(schema);
        var fields=new HashSet<>(Set.of("schema","id","name","family","original_source_sha256","enabled","engine_preset","bands","requests","native_options","effect_options","context","evidence","created_at","configuration_sha256"));
        if(hasTerrain)fields.add("terrain_policy");keys(o,fields);
        var flags=o.getAsJsonObject("requests");var expected=new HashSet<String>();for(var id:OptimizationId.values())expected.add(id.key());
        // Only the complete historical vector may gain the one newly introduced, default-off flag.
        // Also retain complete 62-flag schema 2 files written before the schema bump.
        boolean legacyRequests=!"3".equals(schema)&&!flags.has(OptimizationId.DEAD_FRAME_COPY.key());
        boolean legacyExact=!"4".equals(schema);
        if(legacyExact){expected.remove(OptimizationId.TRANSLUCENT_FASTPATH.key());expected.remove(OptimizationId.AO_ATTENUATION.key());}
        if(legacyRequests||"1".equals(schema))expected.remove(OptimizationId.DEAD_FRAME_COPY.key());keys(flags,expected);
        var snapshot=OptimizationSnapshot.noneRequested();for(var id:OptimizationId.values()) {
            if((!legacyRequests||id!=OptimizationId.DEAD_FRAME_COPY)&&(!legacyExact||!Set.of(OptimizationId.TRANSLUCENT_FASTPATH,OptimizationId.AO_ATTENUATION).contains(id)))snapshot=snapshot.with(id,bool(flags,id.key()));
        }
        var b=o.getAsJsonObject("bands");keys(b,Set.of("near","far","transition","debug"));
        var e=o.getAsJsonObject("evidence");keys(e,Set.of("provenance","goal","runId","fileSha256","visualReview"));
        TuningPreset.TerrainPolicy terrain=null;
        if(hasTerrain&&!o.get("terrain_policy").isJsonNull()) {
            var t=o.getAsJsonObject("terrain_policy");keys(t,Set.of("chunkCap","warmupSeconds"));
            terrain=new TuningPreset.TerrainPolicy(Integer.parseInt(t.get("chunkCap").getAsString()),Integer.parseInt(t.get("warmupSeconds").getAsString()));
        }
        var p=new TuningPreset(string(o,"id"),string(o,"name"),string(o,"family"),string(o,"original_source_sha256"),bool(o,"enabled"),
                LodPreset.valueOf(string(o,"engine_preset")),new TuningPreset.Bands(b.get("near").getAsDouble(),b.get("far").getAsDouble(),b.get("transition").getAsDouble(),bool(b,"debug")),terrain,snapshot,
                strings(o.getAsJsonObject("native_options")),strings(o.getAsJsonObject("effect_options")),strings(o.getAsJsonObject("context")),
                new TuningPreset.Evidence(TuningPreset.Provenance.valueOf(string(e,"provenance")),string(e,"goal"),string(e,"runId"),strings(e.getAsJsonObject("fileSha256")),string(e,"visualReview")),string(o,"created_at"));
        // Preserve the stored evidence; its old measured identity does not become the current hash.
        String checksum=legacyRequests?p.legacyConfigurationSha256():legacyExact?p.previousConfigurationSha256():p.configurationSha256();
        if(!checksum.equals(string(o,"configuration_sha256")))throw new IllegalArgumentException("Tuning configuration checksum mismatch");
        return p;
    }
    private Path file(String id){UUID.fromString(id);return directory.resolve(id+".json");}
    private void requireDirectory()throws IOException {if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))throw new IOException("Unsafe tuning directory");}
    private static void keys(JsonObject o,Set<String> keys){if(!o.keySet().equals(keys))throw new IllegalArgumentException("Incomplete or unknown tuning fields");}
    private static String string(JsonObject o,String key){var x=o.get(key);if(x==null||!x.isJsonPrimitive()||!x.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected text: "+key);return x.getAsString();}
    private static boolean bool(JsonObject o,String key){var x=o.get(key);if(x==null||!x.isJsonPrimitive()||!x.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Expected boolean: "+key);return x.getAsBoolean();}
    private static Map<String,String> strings(JsonObject o){var result=new TreeMap<String,String>();o.keySet().forEach(k->result.put(k,string(o,k)));return result;}
}

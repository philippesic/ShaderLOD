package com.pp.shaderlod.tuning;

import com.pp.shaderlod.*;
import com.pp.shaderlod.measurement.MeasurementPins;
import com.pp.shaderlod.measurement.RuntimeMeasurement;
import com.pp.shaderlod.optimization.*;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;

/** Normal saved-tuning consumer. Native settings are matched, never silently overwritten. */
public final class SavedTunings {
    private SavedTunings() {}
    public static Path directory(){return Iris.getShaderpacksDirectory().resolveSibling("shaderlod-tunings");}
    private static Path runParent(){return Iris.getShaderpacksDirectory().resolveSibling("shaderlod-tuning");}
    private static TuningPresetStore store(){return new TuningPresetStore(directory().resolve("presets"));}
    public static List<TuningPreset> list(){try{return store().list();}catch(IOException e){throw failure(e);}}
    private static String selected(){return Iris.getIrisConfig().getShaderPackName().orElseThrow(()->new IllegalStateException("Select an original supported shader"));}
    public static Map<String,String> nativeOptions() {
        var options=Iris.getCurrentPack().orElseThrow(()->new IllegalStateException("Load the selected shader in a world first")).getShaderPackOptions();
        var values=options.getOptionValues();var all=new TreeMap<String,String>();
        options.getOptionSet().getBooleanOptions().forEach((k,v)->all.put(k,Boolean.toString(values.getBooleanValueOrDefault(k))));
        options.getOptionSet().getStringOptions().forEach((k,v)->all.put(k,values.getStringValueOrDefault(k)));
        try{return TuningNativeOptions.project(Iris.getShaderpacksDirectory(),selected(),OptimizationConfig.activeFamily(),all);}
        catch(IOException e){throw failure(e);}
    }
    private static TuningPreset capture(String name)throws IOException {
        if(Minecraft.getInstance().level==null||Iris.isFallback())throw new IllegalStateException("Load the selected shader in a ready world first");
        var source=TuningSourceIdentity.capture(Iris.getShaderpacksDirectory(),selected());
        return new TuningPreset(UUID.randomUUID().toString(),name,OptimizationConfig.activeFamily(),source.originalSourceSha256(),
                ShaderLodConfig.ENABLED.get(),ShaderLodConfig.PRESET.get(),bands(),new TuningPreset.TerrainPolicy(ShaderLodConfig.TERRAIN_VANILLA_CHUNK_CAP.get(),ShaderLodConfig.TERRAIN_WARMUP_SECONDS.get()),OptimizationConfig.snapshot(),nativeOptions(),ShaderLodEffectConfig.tuningValues(),
                compactContext(MeasurementPins.capture("named-tuning")),TuningPreset.Evidence.manual(),Instant.now().toString());
    }
    private static TuningPreset.Bands bands(){return new TuningPreset.Bands(ShaderLodConfig.NEAR.get(),ShaderLodConfig.FAR.get(),ShaderLodConfig.TRANSITION.get(),ShaderLodConfig.DEBUG.get());}
    public static TuningPreset saveCurrent(String name) {
        idle();try {
            var p=capture(name);
            // A name-only copy of the exact current local measured result can retain its evidence.
            for(var previous:store().list())if(previous.configurationSha256().equals(p.configurationSha256())&&locallyMeasured(previous)
                    &&sameMachine(previous.context(),p.context())) {
                p=new TuningPreset(p.id(),p.name(),p.family(),p.originalSourceSha256(),p.enabled(),p.enginePreset(),p.bands(),p.terrainPolicy(),p.requests(),
                        p.nativeOptions(),p.effectOptions(),previous.context(),previous.evidence(),p.createdAt());break;
            }
            store().save(p);return p;
        }catch(IOException e){throw failure(e);}
    }
    public static void rename(String id,String name){idle();try{store().save(store().load(id).renamed(name));}catch(IOException e){throw failure(e);}}
    public static boolean pending() {
        try {var p=directory().resolve("apply-undo.properties");return Files.exists(p)&&"PENDING".equals(TuningJournal.properties(p).getProperty("state"));}
        catch(IOException e){throw failure(e);}
    }
    private static void idle() {
        if(AutoTuner.active()||AutoTuner.restorationPending()||pending())throw new IllegalStateException("Restore the pending tune or preset with Undo before editing presets");
        if(RuntimeMeasurement.requested()||RuntimeMeasurement.pair()!=null&&RuntimeMeasurement.pair().restorationPending())throw new IllegalStateException("Stop timing and restore its comparison before editing presets");
    }
    public static String compatibility(TuningPreset p) {
        try {var current=capture("Compatibility check");p.requireCompatible(current.family(),current.originalSourceSha256(),current.nativeOptions());
            if(p.terrainPolicy()==null)return "Compatible older settings; terrain preferences will stay as they are";
            return locallyMeasured(p)&&sameMachine(p.context(),current.context())?"Locally measured configuration; visual quality unreviewed":"Compatible settings; run a fresh tune to measure this setup";
        }catch(Exception e){return "Unavailable: "+e.getMessage();}
    }
    public static void apply(String id) {
        idle();try {
            var target=store().load(id);var original=capture("Before custom tuning");
            target.requireCompatible(original.family(),original.originalSourceSha256(),original.nativeOptions());
            var pair=TuningSourceIdentity.capture(Iris.getShaderpacksDirectory(),selected());
            var undo=new Properties();undo.setProperty("schema","1");undo.setProperty("state","PENDING");undo.setProperty("original",TuningPresetStore.encode(original));
            Files.createDirectories(directory());TuningJournal.atomic(directory().resolve("apply-undo.properties"),undo);
            try {applyConfiguration(target,pair);undo.setProperty("state","APPLIED");TuningJournal.atomic(directory().resolve("apply-undo.properties"),undo);}
            catch(Exception failure) {
                try {applyConfiguration(original,pair);undo.setProperty("state","RESTORED");TuningJournal.atomic(directory().resolve("apply-undo.properties"),undo);}
                catch(Exception restore){failure.addSuppressed(restore);}
                throw new IllegalStateException("Preset Apply failed; "+(pending()?"restoration is pending; use preset Undo":"original configuration restored"),failure);
            }
        }catch(IOException e){throw failure(e);}
    }
    public static void undo() {
        if(AutoTuner.active()||AutoTuner.restorationPending())throw new IllegalStateException("Restore the tuner first");
        if(RuntimeMeasurement.requested()||RuntimeMeasurement.pair()!=null&&RuntimeMeasurement.pair().restorationPending())throw new IllegalStateException("Stop timing and restore its comparison first");
        try {
            var path=directory().resolve("apply-undo.properties");if(!Files.exists(path))throw new IllegalStateException("No custom tuning Undo is available");
            var undo=TuningJournal.properties(path);var original=TuningPresetStore.decode(undo.getProperty("original"));
            original.requireCompatible(OptimizationConfig.activeFamily(),TuningSourceIdentity.capture(Iris.getShaderpacksDirectory(),selected()).originalSourceSha256(),nativeOptions());
            undo.setProperty("state","PENDING");TuningJournal.atomic(path,undo);
            applyConfiguration(original,TuningSourceIdentity.capture(Iris.getShaderpacksDirectory(),selected()));
            undo.setProperty("state","UNDONE");TuningJournal.atomic(path,undo);
        }catch(IOException e){throw failure(e);}
    }
    private static void applyConfiguration(TuningPreset p,TuningSourceIdentity pair)throws IOException {
        pair.requireCurrent(Iris.getShaderpacksDirectory(),selected());
        p.requireCompatible(OptimizationConfig.activeFamily(),pair.originalSourceSha256(),nativeOptions());
        // Coverage is scene authority, never a saved preference. Recovery also requires fresh inspection.
        GeometryLodController.revokeCurrentScene();ShaderLodConfig.TERRAIN_WARM_SOURCE_CONFIRMED.set(false);
        if(p.terrainPolicy()!=null) {
            ShaderLodConfig.TERRAIN_VANILLA_CHUNK_CAP.set(p.terrainPolicy().chunkCap());
            ShaderLodConfig.TERRAIN_WARMUP_SECONDS.set(p.terrainPolicy().warmupSeconds());
        }
        ShaderLodConfig.SPEC.save();
        var clear=OptimizationEditTransaction.apply(OptimizationSnapshot.noneRequested(),Iris::reload,Iris::isFallback,CompileOptionControl::open);
        if(!clear.success())throw new IOException(clear.message());
        ShaderLodConfig.NEAR.set(p.bands().near());ShaderLodConfig.FAR.set(p.bands().far());ShaderLodConfig.TRANSITION.set(p.bands().transition());ShaderLodConfig.DEBUG.set(p.bands().debug());ShaderLodConfig.SPEC.save();
        ShaderLodEffectConfig.applyTuningValues(p.effectOptions());
        if(!ShaderLodSettings.apply(p.enabled(),p.enginePreset()))throw new IOException(ShaderLodSettings.failureMessage());
        var result=OptimizationEditTransaction.apply(p.requests(),Iris::reload,Iris::isFallback,CompileOptionControl::open);
        if(!result.success())throw new IOException(result.message());
        pair.requireCurrent(Iris.getShaderpacksDirectory(),selected());
        p.requireCompatible(OptimizationConfig.activeFamily(),pair.originalSourceSha256(),nativeOptions());
        if(!p.requests().equals(OptimizationConfig.snapshot())||p.enabled()!=ShaderLodConfig.ENABLED.get()||p.enginePreset()!=ShaderLodConfig.PRESET.get()
                ||!p.bands().equals(bands())||!p.effectOptions().equals(ShaderLodEffectConfig.tuningValues())||Iris.isFallback()
                ||p.terrainPolicy()!=null&&(p.terrainPolicy().chunkCap()!=ShaderLodConfig.TERRAIN_VANILLA_CHUNK_CAP.get()||p.terrainPolicy().warmupSeconds()!=ShaderLodConfig.TERRAIN_WARMUP_SECONDS.get())
                ||ShaderLodConfig.TERRAIN_WARM_SOURCE_CONFIRMED.get())throw new IOException("Preset state was not acknowledged");
    }
    /** Called only after the actual normal tuner commits SAVED and its restoration barrier completes. */
    public static void recordValidated(Path runDirectory,OptimizationSnapshot flags,String goal)throws IOException {
        var local=runParent().toAbsolutePath().normalize();var run=runDirectory.toAbsolutePath().normalize();
        if(!run.getParent().equals(local)||!Files.isDirectory(run,LinkOption.NOFOLLOW_LINKS))throw new IOException("Unowned tuning evidence directory");
        var metadata=TuningJournal.properties(run.resolve("metadata.properties"));
        var p=capture(OptimizationConfig.activeFamily()+" — "+goal+" — "+Instant.now().toString().substring(0,16));
        var original=TuningJournal.read(run.resolve("undo-original.properties"));
        var hashes=new TreeMap<String,String>();for(String file:List.of("metadata.properties","decisions.csv","samples.csv","undo-original.properties"))hashes.put(file,hash(run.resolve(file)));
        var measured=TuningMeasuredPreset.assemble(p,flags,goal,run.getFileName().toString(),metadata,original,hashes);
        store().save(measured);
    }
    private static Map<String,String> prefix(Properties p,String prefix){var result=new TreeMap<String,String>();for(var k:p.stringPropertyNames())if(k.startsWith(prefix))result.put(k.substring(prefix.length()),p.getProperty(k));return result;}
    /** The complete module manifest stays in the measured journal; its digest identifies it here. */
    private static Map<String,String> compactContext(Map<String,String> pins) {
        var compact=new TreeMap<>(pins);compact.remove("mod_artifact_manifest");
        return Map.copyOf(compact);
    }
    public static List<Path> imports() {
        try {var dir=directory().resolve("imports");Files.createDirectories(dir);try(var files=Files.list(dir)){return files.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&p.getFileName().toString().endsWith(".json")).sorted().toList();}}
        catch(IOException e){throw failure(e);}
    }
    public static TuningPreset importFile(String filename) {
        idle();try {var file=imports().stream().filter(p->p.getFileName().toString().equals(filename)).findFirst().orElseThrow(()->new IOException("Put a preset JSON in the imports folder first"));return store().importFile(file);}
        catch(IOException e){throw failure(e);}
    }
    public static Path export(String id) {
        idle();try {
            var p=store().load(id);var exports=directory().resolve("exports");Files.createDirectories(exports);
            var zip=exports.resolve("tuning-"+p.id()+"-"+UUID.randomUUID()+".zip");boolean measured=locallyMeasured(p);
            var portable=exports.resolve("tuning-"+p.id()+".json");Files.writeString(portable,TuningPresetStore.encode(p));
            try(var out=new ZipOutputStream(Files.newOutputStream(zip,StandardOpenOption.CREATE_NEW))) {
                entry(out,"preset.json",TuningPresetStore.encode(p).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                entry(out,"README.txt",("Import preset.json through Saved tunings. Imported settings are unverified on the receiving setup.\nVisual/motion/DH/PBR quality is UNREVIEWED. Review measurements before proposing mod defaults.\nLocal evidence verified: "+measured+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if(measured)for(var file:p.evidence().fileSha256().keySet()){out.putNextEntry(new ZipEntry("evidence/"+file));Files.copy(runParent().resolve(p.evidence().runId()).resolve(file),out);out.closeEntry();}
            }
            return zip;
        }catch(IOException e){throw failure(e);}
    }
    private static void entry(ZipOutputStream zip,String name,byte[] bytes)throws IOException{zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();}
    private static boolean locallyMeasured(TuningPreset p) {
        if(p.evidence().provenance()!=TuningPreset.Provenance.MEASURED_LOCAL||!p.configurationSha256().equals(p.context().get("evidence_configuration_sha256")))return false;
        if(!"true".equals(p.context().get("mod_artifact_valid"))||!p.context().containsKey("mod_artifact_sha256"))return false;
        try {for(var e:p.evidence().fileSha256().entrySet())if(!hash(runParent().resolve(p.evidence().runId()).resolve(e.getKey())).equals(e.getValue()))return false;return true;}
        catch(IOException e){return false;}
    }
    private static boolean sameMachine(Map<String,String> a,Map<String,String> b) {
        for(String key:List.of("gpu_vendor","gpu_renderer","gl_version","resolution","render_distance","java","os","mod_artifact_sha256","minecraft_options"))
            if(!Objects.equals(a.get(key),b.get(key)))return false;
        return true;
    }
    private static String hash(Path file)throws IOException {
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Missing regular evidence file");
        try {var digest=MessageDigest.getInstance("SHA-256");try(var input=Files.newInputStream(file)){var buffer=new byte[65536];int n;while((n=input.read(buffer))!=-1)digest.update(buffer,0,n);}return HexFormat.of().formatHex(digest.digest());}
        catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    private static IllegalStateException failure(Exception e){return new IllegalStateException("Saved tuning: "+e.getMessage(),e);}
}

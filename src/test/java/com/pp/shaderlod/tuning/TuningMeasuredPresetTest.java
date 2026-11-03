package com.pp.shaderlod.tuning;

import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.measurement.*;
import com.pp.shaderlod.optimization.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

/** Factory contract only: synthetic terminal SAVED metadata is UNIT_ONLY, never a timing win or normal profile. */
class TuningMeasuredPresetTest {
    private static TuningPreset current;
    private static TuningJournal.Original original;
    @TempDir Path directory;
    @BeforeAll static void authenticInputs() throws Exception {
        // Actual loaded code and pinned original bytes; only metadata assembly is
        // synthetic UNIT_ONLY. No game, GPU measurement, or timing result is claimed.
        var origin=Path.of(MeasurementPins.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var context=new HashMap<>(ModArtifactIdentity.capture(new ModArtifactIdentity.Origins("actual test class-directory origin",
            "complete effective loaded measurement class view; physical secondary inputs not claimed",List.of(origin),List.of(origin),false),
            MeasurementPins.class,RuntimeMeasurement.class,OpenGlElapsedBackend.class));
        assertEquals("true",context.get("mod_artifact_valid"),context.toString());
        context.remove("mod_artifact_manifest");
        Path source=Path.of("dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip").toAbsolutePath();
        var input=com.pp.shaderlod.integration.PackSourceReader.read(source);
        var identity=TuningSourceIdentity.capture(source.getParent(),source.getFileName().toString());
        identity.requireCurrent(source.getParent(),source.getFileName().toString());
        context.put("source_path",source.toString());context.put("source_sha256",input.sourceSha256());
        context.put("source_family",MeasurementPins.sourceFamily(input));
        context.put("gpu_vendor","UNIT_ONLY fixture vendor");context.put("gpu_renderer","UNIT_ONLY fixture renderer");
        context.put("gl_version","UNIT_ONLY fixture GL");context.put("resolution","Dimensions[width=1920, height=1200]");
        context.put("java",System.getProperty("java.version"));context.put("os",System.getProperty("os.name"));context.put("render_distance","24");
        context.put("UNIT_ONLY_SYNTHETIC_CONTEXT","not device or performance evidence");
        var nativeOptions=new TreeMap<String,String>();
        try(var in=TuningMeasuredPresetTest.class.getResourceAsStream("/assets/shaderlod/makeupquad/native100.json")) {
            com.google.gson.JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject()
                .entrySet().forEach(e->nativeOptions.put(e.getKey(),e.getValue().getAsString()));
        }
        var requests=OptimizationSnapshot.noneRequested().with(OptimizationId.AO_DEPTH_REUSE,true);
        current=new TuningPreset("71c71fd1-a7cc-4a30-9114-c2b03f157966","UNIT_ONLY assembly fixture","makeup-ultra-fast-shaders",identity.originalSourceSha256(),
            true,com.pp.shaderlod.LodPreset.PERFORMANCE,new TuningPreset.Bands(32,96,8,false),new TuningPreset.TerrainPolicy(16,30),requests,
            nativeOptions,com.pp.shaderlod.lod.EffectTuningValues.defaults(),context,TuningPreset.Evidence.manual(),"2026-10-17T00:00:00Z");
        original=new TuningJournal.Original(current.family(),true,current.enginePreset(),requests,false,260,260,source.getFileName().toString(),identity);
    }
    private Properties terminal() {
        var m=new Properties();m.putAll(current.context());m.setProperty("UNIT_ONLY_SYNTHETIC_TERMINAL","not a performance result");
        m.setProperty("result","SAVED");m.setProperty("tuning_goal","PERFORMANCE");m.setProperty("tuning_engine_preset","PERFORMANCE");m.setProperty("tuning_policy",TuningTierPolicy.VERSION);
        m.setProperty("final.terrain_cap",Integer.toString(current.terrainPolicy().chunkCap()));m.setProperty("final.terrain_warmup_seconds",Integer.toString(current.terrainPolicy().warmupSeconds()));
        m.setProperty("final_enabled",Boolean.toString(current.enabled()));m.setProperty("final_preset",current.enginePreset().name());
        m.setProperty("final.near",Double.toString(current.bands().near()));m.setProperty("final.far",Double.toString(current.bands().far()));m.setProperty("final.transition",Double.toString(current.bands().transition()));m.setProperty("final.debug",Boolean.toString(current.bands().debug()));
        for(var id:OptimizationId.values())m.setProperty("final_profile."+id.key(),Boolean.toString(current.requests().enabled(id)));
        current.nativeOptions().forEach((k,v)->m.setProperty("final.native_option."+k,v));current.effectOptions().forEach((k,v)->m.setProperty("final.effect_option."+k,v));return m;
    }
    private record Journal(Path run,Properties metadata,Map<String,String> hashes) {}
    private Journal journal() throws Exception {
        var run=Files.createTempDirectory(directory,"tune-UNIT_ONLY-");var m=terminal();TuningJournal.atomic(run.resolve("metadata.properties"),m);
        Files.writeString(run.resolve("samples.csv"),"trial,window,pose,frame,epoch,frame_wall_ns,render_wall_ns,render_cpu_ns,process_cpu_ns,gpu_ns,gpu_status\n");
        Files.writeString(run.resolve("decisions.csv"),"candidate,verdict,gain_ms,drift_ms,threshold_ms,reason\nUNIT_ONLY,UNTESTED,NaN,NaN,NaN,synthetic assembly fixture; no timing win\n");
        var undo=new Properties();undo.setProperty("family",original.family());undo.setProperty("enabled",Boolean.toString(original.enabled()));undo.setProperty("preset",original.preset().name());undo.setProperty("vsync",Boolean.toString(original.vsync()));undo.setProperty("fps",Integer.toString(original.fps()));undo.setProperty("window_fps",Integer.toString(original.windowFps()));undo.setProperty("pack",original.pack());original.source().store(undo);
        for(var id:OptimizationId.values())undo.setProperty(id.key(),Boolean.toString(original.flags().enabled(id)));TuningJournal.atomic(run.resolve("undo-original.properties"),undo);
        return new Journal(run,m,hashes(run));
    }
    private Map<String,String> hashes(Path run)throws Exception {
        var hashes=new TreeMap<String,String>();for(String name:List.of("metadata.properties","samples.csv","decisions.csv","undo-original.properties"))
            hashes.put(name,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(run.resolve(name)))));return hashes;
    }
    private TuningPreset assemble(Journal j,Properties metadata,TuningJournal.Original source,String goal)throws Exception {
        return TuningMeasuredPreset.assemble(current,current.requests(),goal,j.run().getFileName().toString(),metadata,source,j.hashes());
    }
    @Test void sameFactoryBuildsBoundRecordAndStoreRenameImportDoNotRewriteEvidence()throws Exception {
        var j=journal();var measured=assemble(j,j.metadata(),original,"PERFORMANCE");
        assertEquals(current.configurationSha256(),measured.configurationSha256());assertEquals(current.context().get("mod_artifact_sha256"),measured.context().get("mod_artifact_sha256"));
        assertEquals(TuningPreset.Provenance.MEASURED_LOCAL,measured.evidence().provenance());assertEquals("UNREVIEWED",measured.evidence().visualReview());assertEquals(j.hashes(),measured.evidence().fileSha256());
        var store=new TuningPresetStore(directory.resolve("UNIT_ONLY-presets"));store.save(measured);var renamed=measured.renamed("UNIT_ONLY renamed factory fixture");store.save(renamed);
        assertEquals(measured.evidence(),store.load(measured.id()).evidence());assertEquals(measured.configurationSha256(),renamed.configurationSha256());
        var imported=store.importFile(directory.resolve("UNIT_ONLY-presets").resolve(measured.id()+".json"));assertEquals(TuningPreset.Provenance.IMPORTED_UNVERIFIED,imported.evidence().provenance());
        assertEquals(j.hashes(),hashes(j.run()));assertEquals("not a performance result",TuningJournal.properties(j.run().resolve("metadata.properties")).getProperty("UNIT_ONLY_SYNTHETIC_TERMINAL"));
    }
    @Test void everyTerminalConfigurationMismatchAndIncompleteCanonicalVectorRefuses()throws Exception {
        var edits=new LinkedHashMap<String,Consumer<Properties>>();
        edits.put("nonSaved",p->p.setProperty("result","INCONCLUSIVE"));edits.put("goal",p->p.setProperty("tuning_goal","QUALITY"));edits.put("policy",p->p.setProperty("tuning_policy","unknown-policy"));
        edits.put("engine",p->p.setProperty("tuning_engine_preset","BALANCED"));edits.put("core",p->p.setProperty("final_enabled","false"));edits.put("finalPreset",p->p.setProperty("final_preset","QUALITY"));
        edits.put("near",p->p.setProperty("final.near","33.0"));edits.put("far",p->p.setProperty("final.far","97.0"));edits.put("transition",p->p.setProperty("final.transition","9.0"));edits.put("debug",p->p.setProperty("final.debug","true"));
        edits.put("terrainCap",p->p.setProperty("final.terrain_cap","17"));edits.put("terrainWarmup",p->p.setProperty("final.terrain_warmup_seconds","31"));
        edits.put("native",p->p.setProperty("final.native_option.TAA","false"));edits.put("effect",p->p.setProperty("final.effect_option.opaque_lighting.mode","ADAPTIVE"));
        edits.put("flag",p->p.setProperty("final_profile.ao_depth_reuse","false"));edits.put("missingFlag",p->p.remove("final_profile.water_wave_budget"));edits.put("unknownFlag",p->p.setProperty("final_profile.invented","true"));
        edits.put("missingEffect",p->p.remove("final.effect_option.opaque_lighting.protect"));edits.put("invalidArtifact",p->p.setProperty("mod_artifact_valid","false"));
        edits.put("changedArtifact",p->p.setProperty("mod_artifact_sha256",current.originalSourceSha256()));edits.put("unknownDevice",p->p.setProperty("gpu_renderer","unknown"));
        for(var edit:edits.entrySet()) {var j=journal();edit.getValue().accept(j.metadata());assertThrows(IOException.class,()->assemble(j,j.metadata(),original,"PERFORMANCE"),edit.getKey());}
    }
    @Test void familySourceGoalAndEvidenceBindingsCannotBeRelabelled()throws Exception {
        var j=journal();var otherFamily=new TuningJournal.Original("photon",original.enabled(),original.preset(),original.flags(),original.vsync(),original.fps(),original.windowFps(),original.pack(),original.source());
        assertThrows(IOException.class,()->assemble(j,j.metadata(),otherFamily,"PERFORMANCE"));
        var wrongSource=new TuningSourceIdentity(original.source().originalFile(),original.source().generatedFile(),original.source().stateFile(),"0".repeat(64));
        var otherSource=new TuningJournal.Original(original.family(),original.enabled(),original.preset(),original.flags(),original.vsync(),original.fps(),original.windowFps(),original.pack(),wrongSource);
        assertThrows(IOException.class,()->assemble(j,j.metadata(),otherSource,"PERFORMANCE"));
        j.metadata().setProperty("tuning_goal","QUALITY");assertThrows(IOException.class,()->assemble(j,j.metadata(),original,"QUALITY")); // Performance fixture cannot be relabelled Quality.
        var missing=new HashMap<>(j.hashes());missing.remove("undo-original.properties");
        assertThrows(IOException.class,()->TuningMeasuredPreset.assemble(current,current.requests(),"PERFORMANCE",j.run().getFileName().toString(),terminal(),original,missing));
        assertThrows(IOException.class,()->TuningMeasuredPreset.assemble(current,OptimizationSnapshot.noneRequested(),"PERFORMANCE",j.run().getFileName().toString(),terminal(),original,j.hashes()));
    }
    @Test void unknownLegacyTerrainInvalidCurrentArtifactAndMalformedHashesCannotBecomeMeasured()throws Exception {
        var j=journal();
        var legacy=new TuningPreset(current.id(),current.name(),current.family(),current.originalSourceSha256(),current.enabled(),current.enginePreset(),current.bands(),
            current.requests(),current.nativeOptions(),current.effectOptions(),current.context(),current.evidence(),current.createdAt());
        assertNull(legacy.terrainPolicy());
        assertThrows(IOException.class,()->TuningMeasuredPreset.assemble(legacy,legacy.requests(),"PERFORMANCE",j.run().getFileName().toString(),j.metadata(),original,j.hashes()));
        var invalidContext=new HashMap<>(current.context());invalidContext.put("mod_artifact_valid","false");
        var invalid=new TuningPreset(current.id(),current.name(),current.family(),current.originalSourceSha256(),current.enabled(),current.enginePreset(),current.bands(),current.terrainPolicy(),
            current.requests(),current.nativeOptions(),current.effectOptions(),invalidContext,current.evidence(),current.createdAt());
        assertThrows(IOException.class,()->TuningMeasuredPreset.assemble(invalid,invalid.requests(),"PERFORMANCE",j.run().getFileName().toString(),j.metadata(),original,j.hashes()));
        var malformed=new HashMap<>(j.hashes());malformed.put("samples.csv","unavailable");
        assertThrows(IOException.class,()->TuningMeasuredPreset.assemble(current,current.requests(),"PERFORMANCE",j.run().getFileName().toString(),j.metadata(),original,malformed));
    }
}

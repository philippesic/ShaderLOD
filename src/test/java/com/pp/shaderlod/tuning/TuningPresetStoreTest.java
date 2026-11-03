package com.pp.shaderlod.tuning;

import com.google.gson.JsonParser;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TuningPresetStoreTest {
    @TempDir Path directory;
    private TuningPreset preset() {
        return new TuningPreset(UUID.randomUUID().toString(),"My 5080 / rainy coast", "bsl-shaders","a".repeat(64),true,LodPreset.BALANCED,
            new TuningPreset.Bands(32,96,8,false),new TuningPreset.TerrainPolicy(16,30),OptimizationSnapshot.noneRequested().with(OptimizationId.AO_BUDGET,true),
            Map.of("AO","true","TAA","true"),com.pp.shaderlod.lod.EffectTuningValues.defaults(),Map.of("gpu_renderer","test GPU"),TuningPreset.Evidence.manual(),Instant.now().toString());
    }
    @Test void completeConfigurationSurvivesRestartAndNamesNeverBecomePaths()throws Exception {
        var p=preset();new TuningPresetStore(directory).save(p);var restored=new TuningPresetStore(directory).load(p.id());
        assertEquals(p,restored);assertEquals(p.configurationSha256(),restored.configurationSha256());
        try(var files=Files.list(directory)){assertEquals(List.of(p.id()+".json"),files.map(x->x.getFileName().toString()).toList());}
        assertEquals(p.configurationSha256(),p.renamed("Different name").configurationSha256());
    }
    @Test void importedMeasuredProfileBecomesUnverifiedAndCannotOverwriteOriginal()throws Exception {
        var p=preset();p=new TuningPreset(p.id(),p.name(),p.family(),p.originalSourceSha256(),p.enabled(),p.enginePreset(),p.bands(),p.terrainPolicy(),p.requests(),p.nativeOptions(),p.effectOptions(),p.context(),
            new TuningPreset.Evidence(TuningPreset.Provenance.MEASURED_LOCAL,"QUALITY","tune-2026-10-17",Map.of("metadata.properties","a".repeat(64),"samples.csv","b".repeat(64),"decisions.csv","c".repeat(64)),"UNREVIEWED"),p.createdAt());
        var store=new TuningPresetStore(directory);store.save(p);var imported=store.importFile(directory.resolve(p.id()+".json"));
        assertNotEquals(p.id(),imported.id());assertEquals(p.configurationSha256(),imported.configurationSha256());
        assertEquals(TuningPreset.Provenance.IMPORTED_UNVERIFIED,imported.evidence().provenance());
        assertEquals(TuningPreset.Provenance.MEASURED_LOCAL,store.load(p.id()).evidence().provenance());
    }
    @Test void missingUnknownOrNonBooleanRequestsRefuseInsteadOfBecomingDefaults() {
        var o=JsonParser.parseString(TuningPresetStore.encode(preset())).getAsJsonObject();
        o.getAsJsonObject("requests").remove("ao_budget");assertThrows(IllegalArgumentException.class,()->TuningPresetStore.decode(o.toString()));
        o.getAsJsonObject("requests").addProperty("ao_budget","true");assertThrows(IllegalArgumentException.class,()->TuningPresetStore.decode(o.toString()));
        o.getAsJsonObject("requests").addProperty("ao_budget",true);o.getAsJsonObject("requests").addProperty("invented",true);
        assertThrows(IllegalArgumentException.class,()->TuningPresetStore.decode(o.toString()));
    }
    @Test void incompleteEffectsAndInvalidRatiosRefuseBeforeAnyApplyConsumer() {
        var o=JsonParser.parseString(TuningPresetStore.encode(preset())).getAsJsonObject();
        o.getAsJsonObject("effect_options").remove("opaque_lighting.mode");assertThrows(RuntimeException.class,()->TuningPresetStore.decode(o.toString()));
        o.getAsJsonObject("effect_options").addProperty("opaque_lighting.mode","ADAPTIVE");
        o.getAsJsonObject("effect_options").addProperty("opaque_lighting.low","1.5");assertThrows(IllegalArgumentException.class,()->TuningPresetStore.decode(o.toString()));
    }
    @Test void checksumAndCompatibilityCatchSemanticChanges() {
        var p=preset();var o=JsonParser.parseString(TuningPresetStore.encode(p)).getAsJsonObject();
        o.getAsJsonObject("bands").addProperty("near",48);assertThrows(IllegalArgumentException.class,()->TuningPresetStore.decode(o.toString()));
        assertDoesNotThrow(()->p.requireCompatible(p.family(),p.originalSourceSha256(),p.nativeOptions()));
        assertThrows(IllegalArgumentException.class,()->p.requireCompatible("photon",p.originalSourceSha256(),p.nativeOptions()));
        assertThrows(IllegalArgumentException.class,()->p.requireCompatible(p.family(),"b".repeat(64),p.nativeOptions()));
        assertThrows(IllegalArgumentException.class,()->p.requireCompatible(p.family(),p.originalSourceSha256(),Map.of("AO","true","TAA","false")));
        assertThrows(IllegalArgumentException.class,()->new TuningPreset.Bands(32,16,8,false));
    }
    @Test void activeEffectPolicyIsPartOfTheConfigurationIdentity() {
        var p=preset();var effects=new HashMap<>(p.effectOptions());effects.put("opaque_lighting.mode","FULL");
        var changed=new TuningPreset(p.id(),p.name(),p.family(),p.originalSourceSha256(),p.enabled(),p.enginePreset(),p.bands(),p.terrainPolicy(),p.requests(),p.nativeOptions(),effects,p.context(),p.evidence(),p.createdAt());
        assertNotEquals(p.configurationSha256(),changed.configurationSha256());
        assertEquals(changed,TuningPresetStore.decode(TuningPresetStore.encode(changed)));
    }
    @Test void equivalentPortableNumbersIncludingSignedZeroHaveOneIdentity() {
        var p=preset();var effects=new HashMap<>(p.effectOptions());effects.put("opaque_lighting.near","32");effects.put("opaque_lighting.transition","-0.0");
        var a=new TuningPreset(p.id(),p.name(),p.family(),p.originalSourceSha256(),p.enabled(),p.enginePreset(),p.bands(),p.terrainPolicy(),p.requests(),p.nativeOptions(),effects,p.context(),p.evidence(),p.createdAt());
        assertEquals("32.0",a.effectOptions().get("opaque_lighting.near"));assertEquals("0.0",a.effectOptions().get("opaque_lighting.transition"));
        effects.put("opaque_lighting.transition","0");var b=new TuningPreset(p.id(),p.name(),p.family(),p.originalSourceSha256(),p.enabled(),p.enginePreset(),p.bands(),p.terrainPolicy(),p.requests(),p.nativeOptions(),effects,p.context(),p.evidence(),p.createdAt());
        assertEquals(a.configurationSha256(),b.configurationSha256());
    }
    @Test void terrainPreferencesChangeIdentityButNeverContainCoverageAuthority() {
        var p=preset();var changed=new TuningPreset(p.id(),p.name(),p.family(),p.originalSourceSha256(),p.enabled(),p.enginePreset(),p.bands(),new TuningPreset.TerrainPolicy(24,60),p.requests(),p.nativeOptions(),p.effectOptions(),p.context(),p.evidence(),p.createdAt());
        assertNotEquals(p.configurationSha256(),changed.configurationSha256());assertEquals(changed,TuningPresetStore.decode(TuningPresetStore.encode(changed)));
        assertThrows(IllegalArgumentException.class,()->new TuningPreset.TerrainPolicy(33,30));
        var json=JsonParser.parseString(TuningPresetStore.encode(changed)).getAsJsonObject();json.getAsJsonObject("terrain_policy").addProperty("confirmed",true);
        assertThrows(IllegalArgumentException.class,()->TuningPresetStore.decode(json.toString()));
    }
    @Test void olderManualPresetRemainsReadableWithoutInventingTerrainPreferences() {
        var p=preset();var old=new TuningPreset(p.id(),p.name(),p.family(),p.originalSourceSha256(),p.enabled(),p.enginePreset(),p.bands(),p.requests(),p.nativeOptions(),p.effectOptions(),p.context(),p.evidence(),p.createdAt());
        var json=JsonParser.parseString(TuningPresetStore.encode(old)).getAsJsonObject();json.addProperty("schema",1);json.remove("terrain_policy");json.getAsJsonObject("requests").remove("dead_frame_copy");json.getAsJsonObject("requests").remove("translucent_fastpath");json.getAsJsonObject("requests").remove("ao_attenuation");json.addProperty("configuration_sha256",old.legacyConfigurationSha256());
        var decoded=TuningPresetStore.decode(json.toString());assertNull(decoded.terrainPolicy());assertEquals(old.configurationSha256(),decoded.configurationSha256());
        assertEquals(TuningPreset.Provenance.MANUAL,decoded.evidence().provenance());
        assertThrows(IllegalArgumentException.class,()->new TuningPreset(old.id(),old.name(),old.family(),old.originalSourceSha256(),old.enabled(),old.enginePreset(),old.bands(),old.requests().with(OptimizationId.TERRAIN_RADIUS,true),old.nativeOptions(),old.effectOptions(),old.context(),old.evidence(),old.createdAt()));
    }
    @Test void oversizedAndSymlinkInputsRefuse()throws Exception {
        var bad=directory.resolve("bad.json");Files.writeString(bad," ".repeat(1024*1024+1));assertThrows(java.io.IOException.class,()->TuningPresetStore.read(bad));
        var valid=directory.resolve("valid.json");Files.writeString(valid,TuningPresetStore.encode(preset()));var link=directory.resolve("link.json");Files.createSymbolicLink(link,valid);
        assertThrows(java.io.IOException.class,()->TuningPresetStore.read(link));
        assertThrows(IllegalArgumentException.class,()->new TuningPresetStore(directory).load("../escape"));
    }
}

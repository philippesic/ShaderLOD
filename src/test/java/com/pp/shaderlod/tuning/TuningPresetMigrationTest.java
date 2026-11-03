package com.pp.shaderlod.tuning;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.lod.EffectTuningValues;
import com.pp.shaderlod.optimization.OptimizationId;
import com.pp.shaderlod.optimization.OptimizationSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TuningPresetMigrationTest {
    private static final String DEAD_COPY=OptimizationId.DEAD_FRAME_COPY.key();

    private TuningPreset preset(int schema,TuningPreset.Evidence evidence) {
        return new TuningPreset("71c71fd1-a7cc-4a30-9114-c2b03f157966","Migration unit fixture","bsl-shaders","a".repeat(64),
                true,LodPreset.BALANCED,new TuningPreset.Bands(32,96,8,false),
                schema==1?null:new TuningPreset.TerrainPolicy(16,30),
                OptimizationSnapshot.noneRequested().with(OptimizationId.AO_BUDGET,true),
                Map.of("AO","true","TAA","true"),EffectTuningValues.defaults(),Map.of("unit_only","true"),evidence,"2026-10-17T00:00:00Z");
    }
    private JsonObject legacy(int schema,TuningPreset p) {
        var json=JsonParser.parseString(TuningPresetStore.encode(p)).getAsJsonObject();
        json.addProperty("schema",schema);
        if(schema==1)json.remove("terrain_policy");
        json.getAsJsonObject("requests").remove(DEAD_COPY);
        json.getAsJsonObject("requests").remove("translucent_fastpath");json.getAsJsonObject("requests").remove("ao_attenuation");
        json.addProperty("configuration_sha256",p.legacyConfigurationSha256());
        return json;
    }
    private void refuses(JsonObject json,Consumer<JsonObject> corruption) {
        var changed=json.deepCopy();corruption.accept(changed);
        assertThrows(RuntimeException.class,()->TuningPresetStore.decode(changed.toString()));
    }
    private void checksumRefuses(JsonObject json,Consumer<JsonObject> corruption) {
        var changed=json.deepCopy();corruption.accept(changed);
        var error=assertThrows(IllegalArgumentException.class,()->TuningPresetStore.decode(changed.toString()));
        assertEquals("Tuning configuration checksum mismatch",error.getMessage());
    }

    @Test void completeOldVectorsMigrateAndOnlyCurrentIdentityIsWritten() {
        for(int schema:new int[]{1,2}) {
            var p=preset(schema,TuningPreset.Evidence.manual());var json=legacy(schema,p);
            assertEquals(61,json.getAsJsonObject("requests").size());
            var migrated=TuningPresetStore.decode(json.toString());assertEquals(p,migrated);
            assertFalse(migrated.requests().enabled(OptimizationId.DEAD_FRAME_COPY));
            assertNotEquals(json.get("configuration_sha256").getAsString(),migrated.configurationSha256());
            var current=JsonParser.parseString(TuningPresetStore.encode(migrated)).getAsJsonObject();
            assertEquals(4,current.get("schema").getAsInt());assertEquals(64,current.getAsJsonObject("requests").size());
            assertEquals(migrated.configurationSha256(),current.get("configuration_sha256").getAsString());
            assertEquals(migrated,TuningPresetStore.decode(current.toString()));
        }
    }

    @Test void migrationRetainsMeasuredEvidenceWithoutRebindingItsOldConfigurationIdentity() {
        var evidence=new TuningPreset.Evidence(TuningPreset.Provenance.MEASURED_LOCAL,"QUALITY","tune-UNIT_ONLY-legacy",
                Map.of("metadata.properties","b".repeat(64),"samples.csv","c".repeat(64),"decisions.csv","d".repeat(64)),"UNREVIEWED");
        var p=preset(2,evidence);var json=legacy(2,p);var migrated=TuningPresetStore.decode(json.toString());
        assertEquals(p,migrated);assertEquals(evidence,migrated.evidence());
        assertEquals(json.get("configuration_sha256").getAsString(),migrated.legacyConfigurationSha256());
        assertNotEquals(json.get("configuration_sha256").getAsString(),migrated.configurationSha256());
        assertEquals(evidence,TuningPresetStore.decode(TuningPresetStore.encode(migrated)).evidence());
    }

    @Test void everyMissingHistoricalFlagUnknownKeysAndNonBooleansRefuse() {
        for(int schema:new int[]{1,2}) {
            var json=legacy(schema,preset(schema,TuningPreset.Evidence.manual()));
            for(var id:OptimizationId.values())if(!java.util.Set.of(OptimizationId.DEAD_FRAME_COPY,OptimizationId.TRANSLUCENT_FASTPATH,OptimizationId.AO_ATTENUATION).contains(id)) {
                var missing=json.deepCopy();missing.getAsJsonObject("requests").remove(id.key());
                var failure=assertThrows(IllegalArgumentException.class,()->TuningPresetStore.decode(missing.toString()),id.key());
                assertEquals("Incomplete or unknown tuning fields",failure.getMessage());
                refuses(json,x->x.getAsJsonObject("requests").addProperty(id.key(),"false"));
            }
            refuses(json,x->x.getAsJsonObject("requests").addProperty("unknown_request",false));
            refuses(json,x->x.getAsJsonObject("requests").add("ao_budget",JsonNull.INSTANCE));
            refuses(json,x->x.getAsJsonObject("requests").addProperty("ao_budget",0));
        }
    }

    @Test void legacyChecksumMustAuthenticateTheHistoricalVectorAndAllExecutableSettings() {
        for(int schema:new int[]{1,2}) {
            var p=preset(schema,TuningPreset.Evidence.manual());var json=legacy(schema,p);
            checksumRefuses(json,x->x.addProperty("configuration_sha256","0".repeat(64)));
            // A current 62-flag hash cannot stand in for a stored historical checksum.
            checksumRefuses(json,x->x.addProperty("configuration_sha256",p.configurationSha256()));
            checksumRefuses(json,x->x.getAsJsonObject("requests").addProperty("ao_budget",false));
            checksumRefuses(json,x->x.getAsJsonObject("bands").addProperty("near",33));
            checksumRefuses(json,x->x.getAsJsonObject("native_options").addProperty("TAA","false"));
            checksumRefuses(json,x->x.getAsJsonObject("effect_options").addProperty("opaque_lighting.mode","FULL"));
            if(schema==2)checksumRefuses(json,x->x.getAsJsonObject("terrain_policy").addProperty("chunkCap",17));
        }
    }

    @Test void currentSchemaRoundTripsBothNewFlagValuesAndRequiresTheWholeVector() {
        for(boolean enabled:new boolean[]{false,true}) {
            var base=preset(3,TuningPreset.Evidence.manual());
            var p=new TuningPreset(base.id(),base.name(),base.family(),base.originalSourceSha256(),base.enabled(),base.enginePreset(),base.bands(),base.terrainPolicy(),
                    base.requests().with(OptimizationId.DEAD_FRAME_COPY,enabled),base.nativeOptions(),base.effectOptions(),base.context(),base.evidence(),base.createdAt());
            var json=JsonParser.parseString(TuningPresetStore.encode(p)).getAsJsonObject();assertEquals(4,json.get("schema").getAsInt());
            assertEquals(p,TuningPresetStore.decode(json.toString()));
            for(var id:OptimizationId.values())refuses(json,x->x.getAsJsonObject("requests").remove(id.key()));
            refuses(json,x->x.getAsJsonObject("requests").addProperty(DEAD_COPY,"false"));
            refuses(json,x->x.getAsJsonObject("requests").addProperty("unknown_request",false));
            checksumRefuses(json,x->x.getAsJsonObject("requests").addProperty(DEAD_COPY,!enabled));
        }
    }

    @Test void interimCompleteSchemaTwoUsesItsOwnCurrentChecksumAndSchemaOneRemainsHistorical() {
        var p=preset(2,TuningPreset.Evidence.manual());var json=JsonParser.parseString(TuningPresetStore.encode(p)).getAsJsonObject();
        json.addProperty("schema",2);json.getAsJsonObject("requests").remove("translucent_fastpath");json.getAsJsonObject("requests").remove("ao_attenuation");json.addProperty("configuration_sha256",p.previousConfigurationSha256());assertEquals(p,TuningPresetStore.decode(json.toString()));
        var enabled=json.deepCopy();enabled.getAsJsonObject("requests").addProperty(DEAD_COPY,true);
        var withCopy=new TuningPreset(p.id(),p.name(),p.family(),p.originalSourceSha256(),p.enabled(),p.enginePreset(),p.bands(),p.terrainPolicy(),
                p.requests().with(OptimizationId.DEAD_FRAME_COPY,true),p.nativeOptions(),p.effectOptions(),p.context(),p.evidence(),p.createdAt());
        enabled.addProperty("configuration_sha256",withCopy.previousConfigurationSha256());
        assertEquals(withCopy,TuningPresetStore.decode(enabled.toString()));
        checksumRefuses(json,x->x.addProperty("configuration_sha256",p.legacyConfigurationSha256()));
        refuses(json,x->x.getAsJsonObject("requests").remove("ao_budget"));
        var schemaOne=JsonParser.parseString(TuningPresetStore.encode(preset(1,TuningPreset.Evidence.manual()))).getAsJsonObject();
        schemaOne.addProperty("schema",1);schemaOne.remove("terrain_policy");
        assertThrows(IllegalArgumentException.class,()->TuningPresetStore.decode(schemaOne.toString()));
    }

    @Test void schemaThreeCompleteVectorKeepsItsStoredIdentityAndNewRequestsOff() {
        var p=preset(3,TuningPreset.Evidence.manual());
        var json=JsonParser.parseString(TuningPresetStore.encode(p)).getAsJsonObject();json.addProperty("schema",3);
        json.getAsJsonObject("requests").remove("translucent_fastpath");json.getAsJsonObject("requests").remove("ao_attenuation");
        json.addProperty("configuration_sha256",p.previousConfigurationSha256());
        var next=TuningPresetStore.decode(json.toString());assertEquals(p,next);
        assertFalse(next.requests().enabled(OptimizationId.TRANSLUCENT_FASTPATH));assertFalse(next.requests().enabled(OptimizationId.AO_ATTENUATION));
        refuses(json,x->x.getAsJsonObject("requests").remove("ao_budget"));
        checksumRefuses(json,x->x.getAsJsonObject("requests").addProperty("ao_budget",false));
    }

    @Test void migrationPreservesStrictTopLevelNestedAndValueValidation() {
        for(int schema:new int[]{1,2,4}) {
            var p=preset(schema,TuningPreset.Evidence.manual());
            var json=schema==4?JsonParser.parseString(TuningPresetStore.encode(p)).getAsJsonObject():legacy(schema,p);
            refuses(json,x->x.addProperty("unexpected",true));refuses(json,x->x.remove("enabled"));
            refuses(json,x->x.addProperty("schema","2"));refuses(json,x->x.addProperty("schema",5));
            refuses(json,x->x.addProperty("enabled","true"));refuses(json,x->x.addProperty("family",1));
            refuses(json,x->x.add("requests",JsonNull.INSTANCE));refuses(json,x->x.addProperty("requests",true));
            refuses(json,x->x.add("native_options",JsonNull.INSTANCE));
            refuses(json,x->x.getAsJsonObject("bands").addProperty("unknown",1));
            refuses(json,x->x.getAsJsonObject("bands").remove("debug"));
            refuses(json,x->x.getAsJsonObject("evidence").addProperty("measured",true));
            refuses(json,x->x.getAsJsonObject("evidence").remove("visualReview"));
            refuses(json,x->x.getAsJsonObject("native_options").addProperty("AO",true));
            refuses(json,x->x.getAsJsonObject("context").addProperty("unit_only",true));
            refuses(json,x->x.getAsJsonObject("effect_options").remove("opaque_lighting.mode"));
            if(schema!=1) {
                refuses(json,x->x.remove("terrain_policy"));
                refuses(json,x->x.getAsJsonObject("terrain_policy").remove("chunkCap"));
                refuses(json,x->x.getAsJsonObject("terrain_policy").addProperty("confirmed",true));
            }
        }
    }
}

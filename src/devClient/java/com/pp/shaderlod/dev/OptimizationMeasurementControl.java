package com.pp.shaderlod.dev;
import com.google.gson.*;
import com.pp.shaderlod.ShaderLodConfig;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.measurement.RuntimeMeasurement;
import java.nio.file.*;
import java.time.Instant;
import java.util.Set;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
/** One-shot dev entry to the same normal UI APIs; no timer, scene actor or benchmark implementation. */
final class OptimizationMeasurementControl {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    record Request(String requestId,String action,OptimizationId feature,Boolean enabled) {}
    @FunctionalInterface interface Inspection {void require();}
    @FunctionalInterface interface Action {JsonObject run(Request request) throws Exception;}
    private OptimizationMeasurementControl() {}
    static Request parse(JsonObject control) {
        var nested=control.get("optimizationMeasurement");
        if(nested==null||!nested.isJsonObject())throw new IllegalArgumentException("optimizationMeasurement must be an object");
        var object=nested.getAsJsonObject();String id=string(object,"request_id"),action=string(object,"action");
        if(!id.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Invalid request_id");
        if(!Set.of("set","timing_on","timing_off","compare","cancel","export","status","spatial_coverage","spatial_fullrate_on","spatial_fullrate_off").contains(action))throw new IllegalArgumentException("Unknown measurement action: "+action);
        boolean featureAction=action.equals("set")||action.equals("compare");
        Set<String> keys=action.equals("set")?Set.of("request_id","action","feature","enabled"):featureAction?Set.of("request_id","action","feature"):Set.of("request_id","action");
        if(!keys.equals(object.keySet()))throw new IllegalArgumentException("Missing/unknown fields for "+action);
        OptimizationId feature=featureAction?OptimizationId.fromKey(string(object,"feature")):null;Boolean enabled=null;
        if(action.equals("set")) {
            var value=object.get("enabled");if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("enabled must be Boolean");enabled=value.getAsBoolean();
        }
        return new Request(id,action,feature,enabled);
    }
    private static String string(JsonObject object,String key) {
        var value=object.get(key);
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException(key+" must be a string");
        return value.getAsString();
    }
    static Path consume(JsonObject control,Path controlFile,Inspection inspection) throws Exception {
        return consume(control,controlFile,inspection,OptimizationMeasurementControl::normalAction);
    }
    /** Focused validation seam, still using real request parsing and exclusive on-disk receipts. */
    static Path consume(JsonObject control,Path controlFile,Inspection inspection,Action action) throws Exception {
        // Reserve a valid identity even when a later action/schema validation is rejected.
        var nested=control.get("optimizationMeasurement");
        if(nested==null||!nested.isJsonObject())throw new IllegalArgumentException("optimizationMeasurement must be an object");
        String id=string(nested.getAsJsonObject(),"request_id");
        if(!id.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Invalid request_id");
        Path directory=controlFile.toAbsolutePath().normalize().getParent().resolve("optimization-measurement-receipts");Files.createDirectories(directory);
        Path receipt=directory.resolve(id+".json");var result=new JsonObject();result.addProperty("request_id",id);result.addProperty("observed_at",Instant.now().toString());result.addProperty("status","PENDING");
        Files.writeString(receipt,JSON.toJson(result)+"\n",StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        try {
            Request request=parse(control);result.addProperty("action",request.action());if(request.feature()!=null)result.addProperty("feature",request.feature().key());
            inspection.require(); // Existing guard requires dev CPU/GPU timings and old benchmarks disabled.
            JsonObject actual=action.run(request);result.add("normal_api_result",actual);
            boolean accepted=!actual.has("success")||actual.get("success").getAsBoolean();result.addProperty("status",accepted?"ACCEPTED":"REJECTED");
        }catch(Exception failure) {result.addProperty("status","REJECTED");result.addProperty("error",failure.toString());}
        result.addProperty("completed_at",Instant.now().toString());
        Path temporary=Files.createTempFile(directory,id+"-",".tmp");
        try {Files.writeString(temporary,JSON.toJson(result)+"\n");try {Files.move(temporary,receipt,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,receipt,StandardCopyOption.REPLACE_EXISTING);}}
        finally {Files.deleteIfExists(temporary);}
        com.mojang.logging.LogUtils.getLogger().info("ShaderLOD normal optimization measurement receipt: {} result={}",receipt,result);
        return receipt;
    }
    /** Two exact development proof properties; no user-supplied property name or value. */
    static String spatialFullrateProof(String family,boolean enabled) {
        String property=switch(family) {
            case "complementary-unbound" -> "shaderlod.unbound.spatial.fullRateProof";
            case "complementary-reimagined" -> "shaderlod.reimagined.spatial.fullRateProof";
            default -> throw new IllegalStateException("Spatial full-rate proof requires selected Unbound or Reimagined");
        };
        System.setProperty(property,Boolean.toString(enabled));return property;
    }
    static JsonObject normalAction(Request request) throws Exception {
        var result=new JsonObject();result.addProperty("success",true);
        switch(request.action()) {
            case "set" -> {
                RuntimeMeasurement.cancelActivePair();
                var next=OptimizationConfig.snapshot().with(request.feature(),request.enabled());
                var applied=OptimizationEditTransaction.apply(next,Iris::reload,Iris::isFallback,CompileOptionControl::open);
                result.addProperty("success",applied.success());result.addProperty("message",applied.message());
            }
            case "timing_on" -> RuntimeMeasurement.request(true);
            case "timing_off" -> RuntimeMeasurement.request(false);
            case "compare" -> {
                Minecraft.getInstance().setScreen(null);
                RuntimeMeasurement.startPair(request.feature(),120,240,()->ShaderLodConfig.snapshot().toString()+ShaderLodConfig.PRESET.get());
                var started=RuntimeMeasurement.pair();result.addProperty("success",started!=null&&started.active());
                if(started!=null)result.addProperty("message",started.status());
            }
            case "cancel" -> RuntimeMeasurement.cancelActivePair();
            case "export" -> result.addProperty("export_path",RuntimeMeasurement.export(Iris.getShaderpacksDirectory().resolveSibling("shaderlod-measurements")).toString());
            case "spatial_fullrate_on","spatial_fullrate_off" -> {
                var activePair=RuntimeMeasurement.pair();
                if(RuntimeMeasurement.requested()||(activePair!=null&&activePair.active()))throw new IllegalStateException("Spatial full-rate proof switching requires timing disabled");
                String family=com.pp.shaderlod.measurement.MeasurementPins.selectedFamily();
                boolean enabled=request.action().equals("spatial_fullrate_on");
                String property=spatialFullrateProof(family,enabled);
                result.addProperty("source_family",family);result.addProperty("spatial_fullrate_property",property);result.addProperty("spatial_fullrate_enabled",enabled);
            }
            case "spatial_coverage" -> {
                var activePair=RuntimeMeasurement.pair();
                if(RuntimeMeasurement.requested()||(activePair!=null&&activePair.active()))throw new IllegalStateException("Spatial coverage readback requires timing disabled");
                String family=com.pp.shaderlod.measurement.MeasurementPins.selectedFamily();
                var coverage=switch(family) {
                    case "complementary-unbound" -> com.pp.shaderlod.optimization.unboundspatial.UnboundSpatialRuntime.coverageProbe();
                    case "complementary-reimagined" -> com.pp.shaderlod.optimization.reimaginedspatial.ReimaginedSpatialRuntime.coverageProbe();
                    default -> throw new IllegalStateException("Spatial coverage requires selected Unbound or Reimagined");
                };
                result.add("spatial_coverage",JSON.toJsonTree(coverage));
            }
            case "status" -> {String family=com.pp.shaderlod.measurement.MeasurementPins.selectedFamily();result.addProperty("source_family",family);result.addProperty("spatial_backend_implementation",OptimizationRuntimeContext.implementation(family,OptimizationId.SPATIAL_LIGHTING_REUSE));result.addProperty("temporal_backend_implementation",OptimizationRuntimeContext.implementation(family,OptimizationId.TEMPORAL_SHADOW_FILTER));result.addProperty("receiver_backend_implementation",OptimizationRuntimeContext.implementation(family,OptimizationId.SHADOW_RECEIVER_FOOTPRINT));result.addProperty("reflected_support_backend_implementation",OptimizationRuntimeContext.implementation(family,OptimizationId.REFLECTED_CLOUD_DISTANCE_EXIT));result.addProperty("projected_refinement_backend_implementation",OptimizationRuntimeContext.implementation(family,OptimizationId.REFLECTION_PROJECTED_REFINEMENT));result.addProperty("next_event_backend_implementation",OptimizationRuntimeContext.implementation(family,OptimizationId.REFLECTION_NEXT_EVENT_REFINEMENT));result.addProperty("pixel_backend_implementation",OptimizationRuntimeContext.implementation(family,OptimizationId.PIXEL_SCALE));result.addProperty("ao_backend_implementation",OptimizationRuntimeContext.implementation(family,OptimizationId.AO_RENDER_SCALE));result.addProperty("early_alpha_backend_implementation",OptimizationRuntimeContext.implementation(family,OptimizationId.EARLY_ALPHA));result.add("selected_resource_pack_ids",new com.google.gson.Gson().toJsonTree(net.minecraft.client.Minecraft.getInstance().getResourcePackRepository().getSelectedIds()));}
            default -> throw new IllegalArgumentException("Unknown measurement action");
        }
        result.addProperty("runtime_status",RuntimeMeasurement.status());result.addProperty("normal_timing_requested",RuntimeMeasurement.requested());
        var pair=RuntimeMeasurement.pair();if(pair!=null){result.addProperty("paired_status",pair.status());result.addProperty("paired_phase",pair.phase().name());result.addProperty("paired_active",pair.active());}
        if(RuntimeMeasurement.lastExport()!=null)result.addProperty("last_export_path",RuntimeMeasurement.lastExport().toString());
        var flags=new JsonObject();for(var id:OptimizationId.values())flags.addProperty(id.key(),OptimizationConfig.snapshot().enabled(id));result.add("requested_flags",flags);
        return result;
    }
}

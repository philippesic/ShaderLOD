package com.pp.shaderlod.fabric.dev;
import com.google.gson.*;
import com.pp.shaderlod.fabric.FabricLodConfig;
import com.pp.shaderlod.fabric.FabricLodSettings;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.measurement.RuntimeMeasurement;
import java.nio.file.*;
import java.time.Instant;
import java.util.Set;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
/** One-shot dev entry to the same normal UI APIs; no timer, scene actor or benchmark implementation. */
final class FabricOptimizationMeasurementControl {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    record Request(String requestId,String action,OptimizationId feature,Boolean enabled,LodPreset preset) {}
    @FunctionalInterface interface Inspection {void require();}
    @FunctionalInterface interface Action {JsonObject run(Request request) throws Exception;}
    private FabricOptimizationMeasurementControl() {}
    static Request parse(JsonObject control) {
        var nested=control.get("optimizationMeasurement");
        if(nested==null||!nested.isJsonObject())throw new IllegalArgumentException("optimizationMeasurement must be an object");
        var object=nested.getAsJsonObject();String id=string(object,"request_id"),action=string(object,"action");
        if(!id.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Invalid request_id");
        if(!Set.of("set","timing_on","timing_off","compare","cancel","export","status","core").contains(action))throw new IllegalArgumentException("Unknown measurement action: "+action);
        boolean featureAction=action.equals("set")||action.equals("compare");
        Set<String> keys=action.equals("core")?Set.of("request_id","action","enabled","preset"):action.equals("set")?Set.of("request_id","action","feature","enabled"):featureAction?Set.of("request_id","action","feature"):Set.of("request_id","action");
        if(!keys.equals(object.keySet()))throw new IllegalArgumentException("Missing/unknown fields for "+action);
        OptimizationId feature=featureAction?OptimizationId.fromKey(string(object,"feature")):null;Boolean enabled=null;
        if(action.equals("set")||action.equals("core")) {
            var value=object.get("enabled");if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("enabled must be Boolean");enabled=value.getAsBoolean();
        }
        LodPreset preset=action.equals("core")?LodPreset.valueOf(string(object,"preset")):null;
        return new Request(id,action,feature,enabled,preset);
    }
    private static String string(JsonObject object,String key) {
        var value=object.get(key);
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException(key+" must be a string");
        return value.getAsString();
    }
    static Path consume(JsonObject control,Path controlFile,Inspection inspection) throws Exception {
        return consume(control,controlFile,inspection,FabricOptimizationMeasurementControl::normalAction);
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
            Request request=parse(control);result.addProperty("action",request.action());if(request.feature()!=null)result.addProperty("feature",request.feature().key());if(request.enabled()!=null)result.addProperty("enabled",request.enabled());if(request.preset()!=null)result.addProperty("preset",request.preset().name());
            inspection.require(); // Existing guard requires dev CPU/GPU timings and old benchmarks disabled.
            JsonObject actual=action.run(request);result.add("normal_api_result",actual);
            boolean accepted=!actual.has("success")||actual.get("success").getAsBoolean();result.addProperty("status",accepted?"ACCEPTED":"REJECTED");
        }catch(Exception|Error failure) {result.addProperty("status","REJECTED");result.addProperty("error",failure.toString());var suppressed=new JsonArray();for(Throwable secondary:failure.getSuppressed())suppressed.add(secondary.toString());result.add("suppressed",suppressed);}
        result.addProperty("completed_at",Instant.now().toString());
        Path temporary=Files.createTempFile(directory,id+"-",".tmp");
        try {Files.writeString(temporary,JSON.toJson(result)+"\n");try {Files.move(temporary,receipt,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,receipt,StandardCopyOption.REPLACE_EXISTING);}}
        finally {Files.deleteIfExists(temporary);}
        com.mojang.logging.LogUtils.getLogger().info("ShaderLOD normal optimization measurement receipt: {} result={}",receipt,result);
        return receipt;
    }
    private static JsonObject normalAction(Request request) throws Exception {
        var result=new JsonObject();result.addProperty("success",true);
        switch(request.action()) {
            case "set" -> {
                RuntimeMeasurement.cancelPair();
                var next=OptimizationConfig.snapshot().with(request.feature(),request.enabled());
                var applied=OptimizationEditTransaction.apply(next,Iris::reload,Iris::isFallback,CompileOptionControl::open);
                result.addProperty("success",applied.success());result.addProperty("message",applied.message());
            }
            case "core" -> {
                RuntimeMeasurement.cancelPair();
                boolean accepted=FabricLodSettings.apply(request.enabled(),request.preset());
                result.addProperty("success",accepted);
                result.addProperty("message",accepted?"Applied Fabric core through normal settings transaction":FabricLodSettings.failureMessage());
            }
            case "timing_on" -> RuntimeMeasurement.request(true);
            case "timing_off" -> RuntimeMeasurement.request(false);
            case "compare" -> {
                Minecraft.getInstance().setScreen(null);
                RuntimeMeasurement.startPair(request.feature(),120,240,()->FabricLodConfig.snapshot().toString());
                var started=RuntimeMeasurement.pair();result.addProperty("success",started!=null&&started.active());
                if(started!=null)result.addProperty("message",started.status());
            }
            case "cancel" -> RuntimeMeasurement.cancelPair();
            case "export" -> result.addProperty("export_path",RuntimeMeasurement.export(Iris.getShaderpacksDirectory().resolveSibling("shaderlod-measurements")).toString());
            case "status" -> {String family=com.pp.shaderlod.measurement.MeasurementPins.selectedFamily();result.addProperty("source_family",family);result.addProperty("ao_backend_implementation",OptimizationRuntimeContext.implementation(family,OptimizationId.AO_RENDER_SCALE));}
            default -> throw new IllegalArgumentException("Unknown measurement action");
        }
        var context=new JsonObject();var client=Minecraft.getInstance();
        context.addProperty("world_available",client!=null&&client.level!=null&&client.player!=null);
        var server=client==null?null:client.getSingleplayerServer();
        context.addProperty("integrated_server_available",server!=null);
        if(server!=null){context.addProperty("integrated_server_identity",System.identityHashCode(server));context.addProperty("integrated_server_frozen",server.tickRateManager().isFrozen());context.addProperty("integrated_server_published",server.isPublished());context.addProperty("integrated_server_player_count",server.getPlayerList().getPlayerCount());}
        if(client!=null&&client.level!=null&&client.player!=null){
            context.addProperty("dimension",client.level.dimension().location().toString());
            context.addProperty("shader_pack",Iris.getCurrentPackName());context.addProperty("iris_fallback",Iris.isFallback());
            context.addProperty("framebuffer_width",client.getMainRenderTarget().width);context.addProperty("framebuffer_height",client.getMainRenderTarget().height);
            var window=client.getWindow();context.addProperty("window_framebuffer_width",window.getWidth());context.addProperty("window_framebuffer_height",window.getHeight());context.addProperty("window_fullscreen",window.isFullscreen());context.addProperty("window_native_monitor",window.findBestMonitor()==null?0L:window.findBestMonitor().getMonitor());context.addProperty("shader_pack_in_use",net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse());
            var loaded=new JsonObject();var pack=Iris.getCurrentPack().orElse(null);
            if(pack!=null){var opts=pack.getShaderPackOptions();var values=opts.getOptionValues();
                for(String key:opts.getOptionSet().getBooleanOptions().keySet())loaded.addProperty(key,Boolean.toString(values.getBooleanValueOrDefault(key)));
                for(String key:opts.getOptionSet().getStringOptions().keySet())loaded.addProperty(key,values.getStringValueOrDefault(key));}
            context.add("loaded_options",loaded);
        }
        result.add("client_context",context);
        result.add("requested_core",JSON.toJsonTree(FabricLodConfig.snapshot()));
        result.addProperty("runtime_status",RuntimeMeasurement.status());result.addProperty("normal_timing_requested",RuntimeMeasurement.requested());
        result.addProperty("scene_provenance",RuntimeMeasurement.sceneProvenance());
        var pair=RuntimeMeasurement.pair();result.addProperty("paired_restoration_pending",pair!=null&&pair.restorationPending());if(pair!=null){result.addProperty("paired_status",pair.status());result.addProperty("paired_phase",pair.phase().name());result.addProperty("paired_active",pair.active());}
        if(RuntimeMeasurement.lastExport()!=null)result.addProperty("last_export_path",RuntimeMeasurement.lastExport().toString());
        var flags=new JsonObject();for(var id:OptimizationId.values())flags.addProperty(id.key(),OptimizationConfig.snapshot().enabled(id));result.add("requested_flags",flags);
        return result;
    }
}

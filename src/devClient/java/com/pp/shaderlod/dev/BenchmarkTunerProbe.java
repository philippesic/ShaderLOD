package com.pp.shaderlod.dev;

import com.google.gson.*;
import com.pp.shaderlod.BenchmarkUi;
import com.pp.shaderlod.benchmark.BenchmarkWorld;
import com.pp.shaderlod.tuning.AutoTuner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.nio.file.*;
import java.time.Instant;

/** Untimed, one-shot caller of shipped benchmark/tuner entry points; no substitute measurement code. */
final class BenchmarkTunerProbe {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private final Path control;
    private long modified=-1;
    private boolean readinessReported;
    private boolean windowConfigured;
    private long coastPoseAt;
    private int closeClientTicks;
    private boolean coastReady(){
        var client=Minecraft.getInstance();
        return coastPoseAt!=0&&System.nanoTime()-coastPoseAt>=40_000_000_000L&&client.level!=null&&client.player!=null
            &&client.getSingleplayerServer()!=null&&!client.getSingleplayerServer().isPublished()&&client.levelRenderer.hasRenderedAllSections();
    }
    private BenchmarkTunerProbe(Path control){this.control=control;}
    static boolean register(){
        String path=System.getProperty("shaderlod.dev.benchmarkTunerProbe","");
        if(path.isBlank())return false;
        NeoForge.EVENT_BUS.addListener(CoastMotionObservation::beginRender);
        NeoForge.EVENT_BUS.addListener(CoastMotionObservation::endRender);
        NeoForge.EVENT_BUS.addListener(new BenchmarkTunerProbe(Path.of(path))::tick);return true;
    }
    private void tick(ClientTickEvent.Post event){
        if(closeClientTicks>0&&--closeClientTicks==0){Minecraft.getInstance().stop();return;}
        CoastMotionObservation.tick();
        BottleneckProbe.tick();
        BenchmarkProfileComparison.tick();
        SettledConsumerUniforms.pollBenchmarkSky();
        var game=Minecraft.getInstance();
        if(!windowConfigured&&game.getWindow()!=null){org.lwjgl.glfw.GLFW.glfwSetWindowAttrib(game.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_AUTO_ICONIFY,org.lwjgl.glfw.GLFW.GLFW_FALSE);windowConfigured=true;}
        if((BenchmarkWorld.ready()||coastReady())&&!readinessReported){
            readinessReported=true;
            var window=game.getWindow();var target=game.getMainRenderTarget();
            com.mojang.logging.LogUtils.getLogger().info("ShaderLOD benchmark probe ready: shaders={}, fallback={}",net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse(),net.irisshaders.iris.Iris.isFallback());
            com.mojang.logging.LogUtils.getLogger().info("ShaderLOD inspection native fullscreen: {}, monitor={}, framebuffer={}x{}, target={}x{}",window.isFullscreen(),org.lwjgl.glfw.GLFW.glfwGetWindowMonitor(window.getWindow()),window.getWidth(),window.getHeight(),target.width,target.height);
        }
        if(!(Minecraft.getInstance().screen instanceof TitleScreen)&&!(Minecraft.getInstance().screen instanceof com.pp.shaderlod.BenchmarkScreen)&&Minecraft.getInstance().level==null&&!BenchmarkWorld.active())return;
        if(!Files.isRegularFile(control))return;
        try{
            long time=Files.getLastModifiedTime(control).toMillis();if(time==modified)return;modified=time;
            var request=JsonParser.parseString(Files.readString(control)).getAsJsonObject();
            String id=request.get("request_id").getAsString();if(!id.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Invalid request identity");
            Path dir=control.toAbsolutePath().getParent().resolve("receipts");Files.createDirectories(dir);Path receipt=dir.resolve(id+".json");
            Files.writeString(receipt,"{\"status\":\"PENDING\"}\n",StandardOpenOption.CREATE_NEW);
            var result=new JsonObject();result.addProperty("request_id",id);result.addProperty("at",Instant.now().toString());
            try{
                var client=Minecraft.getInstance();String action=request.get("action").getAsString();
                if(CoastMotionObservation.active()&&!action.equals("status")&&!action.equals("coast_motion_cancel")){
                    CoastMotionObservation.cancel();
                    throw new IllegalStateException("Motion cancelled before conflicting control action; retry after restoration");
                }
                switch(action){
                    case "c01_bounds_sources"->result.add("c01_bounds_sources",JSON.toJsonTree(com.pp.shaderlod.optimization.makeupprime.C01Bounds.sources()));
                    case "c01_bounds"->{
                        if(request.has("mode")) {
                            if(AutoTuner.active()||BenchmarkProfileComparison.active()||com.pp.shaderlod.measurement.RuntimeMeasurement.requested()
                                    ||client.level==null||client.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD)
                                throw new IllegalStateException("Untimed owned Overworld C01 configuration required");
                            com.pp.shaderlod.optimization.makeupprime.C01Prefix.stop();
                            com.pp.shaderlod.optimization.makeupprime.C01Bounds.configure(id,request.get("mode").getAsString(),request.has("prefix")?request.get("prefix").getAsInt():4,request.has("group")?request.get("group").getAsInt():16,request.has("frames")?request.get("frames").getAsInt():0,request.has("delay")?request.get("delay").getAsInt():256);
                        }
                        result.add("c01_bounds",JSON.toJsonTree(com.pp.shaderlod.optimization.makeupprime.C01Bounds.snapshot()));
                    }
                    case "c01_prefix"->{
                        if(request.has("mode")) {
                            if(AutoTuner.active()||BenchmarkProfileComparison.active()||com.pp.shaderlod.measurement.RuntimeMeasurement.requested()
                                    ||client.level==null||client.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD)
                                throw new IllegalStateException("Untimed owned Overworld C01 configuration required");
                            com.pp.shaderlod.optimization.makeupprime.C01Bounds.stop();
                            com.pp.shaderlod.optimization.makeupprime.C01Prefix.configure(id,request.get("mode").getAsString(),request.has("prefix")?request.get("prefix").getAsInt():4,request.has("frames")?request.get("frames").getAsInt():0);
                        }
                        result.add("c01_prefix",JSON.toJsonTree(com.pp.shaderlod.optimization.makeupprime.C01Prefix.snapshot()));
                    }
                    case "c01_observation"->{
                        if(AutoTuner.active()||BenchmarkProfileComparison.active()||com.pp.shaderlod.measurement.RuntimeMeasurement.requested()
                                ||client.level==null||client.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD)
                            throw new IllegalStateException("Untimed owned Overworld C01 diagnostic required");
                        if(request.has("frames"))com.pp.shaderlod.optimization.makeupprime.C01Observation.configure(id,request.get("frames").getAsInt(),request.has("visibility_frames")?request.get("visibility_frames").getAsInt():0);
                        result.add("c01_observation",JSON.toJsonTree(com.pp.shaderlod.optimization.makeupprime.C01Observation.snapshot()));
                    }
                    case "bottleneck" -> result.add("bottleneck",BottleneckProbe.consume(request,dir));
                    case "coast_motion"->{if(!coastReady())throw new IllegalStateException("Settled coast required");CoastMotionObservation.arm(request);}
                    case "coast_motion_cancel"->CoastMotionObservation.cancel();
                    case "open"->BenchmarkUi.openDemo();
                    case "open_coast"->{
                        if(client.level!=null||client.getSingleplayerServer()!=null||!(client.screen instanceof TitleScreen))
                            throw new IllegalStateException("Coast opening requires the title screen");
                        if(!client.getLevelSource().levelExists("ShaderLOD-Landscape"))throw new IllegalStateException("Prepared coast copy missing");
                        client.createWorldOpenFlows().openWorld("ShaderLOD-Landscape",()->{});
                    }
                    case "coast_pose"->{
                        var server=client.getSingleplayerServer();
                        if(BenchmarkWorld.active()||server==null||server.isPublished()||client.player==null
                            ||!server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toRealPath().equals(
                                client.gameDirectory.toPath().resolve("saves/ShaderLOD-Landscape").toRealPath()))
                            throw new IllegalStateException("The prepared private coast must own this client");
                        boolean pbr=request.has("pbr")&&request.get("pbr").getAsBoolean();
                        if(pbr&&!"bsl-shaders".equals(com.pp.shaderlod.optimization.OptimizationConfig.activeFamily()))throw new IllegalStateException("PBR wall view requires the pinned BSL source");
                        double x=pbr?492.5:256.5,y=pbr?68:95,z=pbr?660.5:768.5;float yaw=pbr?90:135,pitch=pbr?3.7f:9;
                        server.submit(()->{
                            var level=server.overworld();level.setDayTime(pbr?6000:7000);level.setWeatherParameters(1_000_000,0,false,false);
                            var player=server.getPlayerList().getPlayer(client.player.getUUID());
                            player.teleportTo(level,x,y,z,java.util.Set.of(),yaw,pitch);
                        }).get(2,java.util.concurrent.TimeUnit.SECONDS);
                        client.player.setPos(x,y,z);client.player.setYRot(yaw);client.player.setXRot(pitch);
                        client.level.setRainLevel(0);client.level.setThunderLevel(0);client.options.hideGui=true;client.setScreen(null);
                        org.lwjgl.glfw.GLFW.glfwFocusWindow(client.getWindow().getWindow());coastPoseAt=System.nanoTime();
                    }
                    case "private_dimension"->{
                        var server=client.getSingleplayerServer();
                        if(AutoTuner.active()||BenchmarkProfileComparison.active()||
                            com.pp.shaderlod.measurement.RuntimeMeasurement.requested()||BenchmarkWorld.active()||
                            server==null||server.isPublished()||client.player==null||
                            !server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toRealPath().equals(
                                client.gameDirectory.toPath().resolve("saves/ShaderLOD-Landscape").toRealPath()))
                            throw new IllegalStateException("Untimed private coast dimension transition required");
                        String target=request.get("dimension").getAsString();
                        var key=switch(target){
                            case "minecraft:overworld"->net.minecraft.world.level.Level.OVERWORLD;
                            case "minecraft:the_nether"->net.minecraft.world.level.Level.NETHER;
                            default->throw new IllegalArgumentException("Only Overworld and Nether are accepted");
                        };
                        double x=request.get("x").getAsDouble(),y=request.get("y").getAsDouble(),z=request.get("z").getAsDouble();
                        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||y<64||y>250)
                            throw new IllegalArgumentException("Private transition position invalid");
                        var playerId=client.player.getUUID();
                        server.submit(()->{
                            var level=server.getLevel(key);var player=server.getPlayerList().getPlayer(playerId);
                            if(level==null||player==null)throw new IllegalStateException("Private dimension unavailable");
                            player.teleportTo(level,x,y,z,java.util.Set.of(),player.getYRot(),player.getXRot());
                        }).get(2,java.util.concurrent.TimeUnit.SECONDS);
                        result.addProperty("requested_dimension",target);
                    }
                    case "coast_camera"->{
                        var server=client.getSingleplayerServer();
                        if(AutoTuner.active()||com.pp.shaderlod.measurement.RuntimeMeasurement.requested()||BenchmarkWorld.active()||server==null||server.isPublished()||client.player==null
                                ||!server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toRealPath().equals(client.gameDirectory.toPath().resolve("saves/ShaderLOD-Landscape").toRealPath()))
                            throw new IllegalStateException("Untimed owned private coast camera required");
                        float yaw=request.get("yaw").getAsFloat(),pitch=request.get("pitch").getAsFloat();
                        if(!Float.isFinite(yaw)||!Float.isFinite(pitch)||Math.abs(yaw)>360||Math.abs(pitch)>85)throw new IllegalArgumentException("Bounded camera angles required");
                        client.player.setYRot(yaw);client.player.setXRot(pitch);
                    }
                    case "start"->{org.lwjgl.glfw.GLFW.glfwFocusWindow(client.getWindow().getWindow());BenchmarkUi.start(new com.pp.shaderlod.tuning.TuningRequest(
                        request.has("goal")?com.pp.shaderlod.tuning.TuningGoal.valueOf(request.get("goal").getAsString()):com.pp.shaderlod.tuning.TuningGoal.PERFORMANCE,
                        request.get("seconds").getAsInt(),request.has("full")&&request.get("full").getAsBoolean()));}
                    case "saved_tunings"->{result.add("saved_tunings",JSON.toJsonTree(com.pp.shaderlod.tuning.SavedTunings.list()));result.addProperty("preset_recovery_pending",com.pp.shaderlod.tuning.SavedTunings.pending());}
                    case "save_tuning"->{var p=com.pp.shaderlod.tuning.SavedTunings.saveCurrent(request.get("name").getAsString());result.add("saved_tuning",JsonParser.parseString(com.pp.shaderlod.tuning.TuningPresetStore.encode(p)));}
                    case "apply_tuning"->com.pp.shaderlod.tuning.SavedTunings.apply(request.get("id").getAsString());
                    case "undo_tuning"->com.pp.shaderlod.tuning.SavedTunings.undo();
                    case "rename_tuning"->com.pp.shaderlod.tuning.SavedTunings.rename(request.get("id").getAsString(),request.get("name").getAsString());
                    case "export_tuning"->result.addProperty("preset_export",com.pp.shaderlod.tuning.SavedTunings.export(request.get("id").getAsString()).toString());
                    case "import_tuning"->{var p=com.pp.shaderlod.tuning.SavedTunings.importFile(request.get("file").getAsString());result.add("imported_tuning",JsonParser.parseString(com.pp.shaderlod.tuning.TuningPresetStore.encode(p)));}
                    case "native_macro_inputs"->{
                        var pair=com.pp.shaderlod.measurement.RuntimeMeasurement.pair();
                        if(AutoTuner.active()||BenchmarkProfileComparison.active()||com.pp.shaderlod.measurement.RuntimeMeasurement.requested()
                                ||pair!=null&&(pair.active()||pair.restorationPending())||client.level==null
                                ||!"minecraft:overworld".equals(client.level.dimension().location().toString())
                                ||com.pp.shaderlod.ShaderLodConfig.ENABLED.get())throw new IllegalStateException("Untimed native overworld with core Off required");
                        for(var flag:com.pp.shaderlod.optimization.OptimizationId.values())
                            if(com.pp.shaderlod.optimization.OptimizationConfig.requested(flag))throw new IllegalStateException("All optimization requests must be Off");
                        result.add("native_macro_inputs",NativeMacroInspection.export(dir.resolve(id+"-native-inputs.json")));
                    }
                    case "native_projection"->{if(AutoTuner.active())throw new IllegalStateException("Projection is untimed only");result.add("native_projection",JSON.toJsonTree(com.pp.shaderlod.tuning.SavedTunings.nativeOptions()));}
                    case "screen_tunings"->client.setScreen(new com.pp.shaderlod.SavedTuningsScreen(client.screen));
                    case "close_screen"->client.setScreen(null);
                    case "focus_window"->{
                        var pair=com.pp.shaderlod.measurement.RuntimeMeasurement.pair();
                        if(AutoTuner.active()||BenchmarkProfileComparison.active()
                                ||com.pp.shaderlod.measurement.RuntimeMeasurement.requested()
                                ||pair!=null&&(pair.active()||pair.restorationPending()))
                            throw new IllegalStateException("Window focus requests require an untimed client");
                        org.lwjgl.glfw.GLFW.glfwFocusWindow(client.getWindow().getWindow());
                        result.addProperty("focus_request_sent",true);
                    }
                    case "bands"->{
                        // Intentional external edit: the normal tuner must abort rather than certify changed inputs.
                        com.pp.shaderlod.ShaderLodConfig.NEAR.set(request.get("near").getAsDouble());com.pp.shaderlod.ShaderLodConfig.FAR.set(request.get("far").getAsDouble());
                        com.pp.shaderlod.ShaderLodConfig.TRANSITION.set(request.get("transition").getAsDouble());com.pp.shaderlod.ShaderLodConfig.SPEC.save();
                    }
                    case "effect_mode"->com.pp.shaderlod.ShaderLodEffectConfig.setMode(com.pp.shaderlod.lod.Effect.valueOf(request.get("effect").getAsString()),com.pp.shaderlod.ShaderLodEffectConfig.Mode.valueOf(request.get("mode").getAsString()));
                    case "terrain_preferences"->{
                        if(AutoTuner.active()||com.pp.shaderlod.measurement.RuntimeMeasurement.requested())throw new IllegalStateException("Terrain preference edits are untimed only");
                        var p=new com.pp.shaderlod.tuning.TuningPreset.TerrainPolicy(request.get("cap").getAsInt(),request.get("warmup").getAsInt());
                        com.pp.shaderlod.GeometryLodController.revokeCurrentScene();com.pp.shaderlod.ShaderLodConfig.TERRAIN_WARM_SOURCE_CONFIRMED.set(false);
                        com.pp.shaderlod.ShaderLodConfig.TERRAIN_VANILLA_CHUNK_CAP.set(p.chunkCap());com.pp.shaderlod.ShaderLodConfig.TERRAIN_WARMUP_SECONDS.set(p.warmupSeconds());com.pp.shaderlod.ShaderLodConfig.SPEC.save();
                    }
                    case "resources"->{
                        if(AutoTuner.active()||com.pp.shaderlod.measurement.RuntimeMeasurement.requested())throw new IllegalStateException("Resources are outside timing only");
                        var ids=new java.util.ArrayList<String>();for(var entry:request.getAsJsonArray("ids"))ids.add(entry.getAsString());
                        client.getResourcePackRepository().reload();
                        if(!client.getResourcePackRepository().getAvailableIds().containsAll(ids))throw new IllegalArgumentException("Resource pack not installed");
                        client.getResourcePackRepository().setSelected(ids);client.options.updateResourcePacks(client.getResourcePackRepository());client.reloadResourcePacks();
                    }
                    case "bsl_prefix_status"->{
                        if(AutoTuner.active()||com.pp.shaderlod.measurement.RuntimeMeasurement.requested())throw new IllegalStateException("Untimed status only");
                        result.addProperty("bsl_prefix_status",com.pp.shaderlod.bslprefix.BslPrefixRuntime.status());
                    }
                    case "bsl_driver"->{
                        if(AutoTuner.active()||com.pp.shaderlod.measurement.RuntimeMeasurement.requested()||!"bsl-shaders".equals(com.pp.shaderlod.optimization.OptimizationConfig.activeFamily()))throw new IllegalStateException("Untimed current BSL only");
                        var field=com.pp.shaderlod.bslprefix.BslPrefixRuntime.class.getDeclaredField("OWNERS");field.setAccessible(true);
                        var owners=(java.util.Map<?,?>)field.get(null);var stages=new JsonArray();
                        for(var owner:owners.values()) {
                            var type=owner.getClass();var pf=type.getDeclaredField("pipeline");pf.setAccessible(true);
                            if(pf.get(owner)!=net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable())continue;
                            var sf=type.getDeclaredField("source");sf.setAccessible(true);var source=(net.irisshaders.iris.shaderpack.programs.ProgramSource)sf.get(owner);
                            if(source==null||source.getParent().getPack()!=net.irisshaders.iris.Iris.getCurrentPack().orElseThrow()||!source.getName().equals("gbuffers_terrain"))continue;
                            var programField=type.getDeclaredField("program");programField.setAccessible(true);int program=programField.getInt(owner);
                            if(!org.lwjgl.opengl.GL20.glIsProgram(program)||org.lwjgl.opengl.GL20.glGetProgrami(program,org.lwjgl.opengl.GL20.GL_LINK_STATUS)!=org.lwjgl.opengl.GL11.GL_TRUE)throw new IllegalStateException("Owner program not linked");
                            int[] count={0},attached=new int[2];org.lwjgl.opengl.GL20.glGetAttachedShaders(program,count,attached);if(count[0]!=2)throw new IllegalStateException("Two actual attached stages required");
                            for(int shader:attached)if(org.lwjgl.opengl.GL20.glGetShaderi(shader,org.lwjgl.opengl.GL20.GL_SHADER_TYPE)==org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER) {
                                String text=org.lwjgl.opengl.GL20.glGetShaderSource(shader);if(text==null||text.length()<100)throw new IllegalStateException("Driver fragment unavailable");
                                Path output=dir.resolve(id+"-bsl-program-"+program+".fsh");Files.writeString(output,text,StandardOpenOption.CREATE_NEW);
                                var row=new JsonObject();row.addProperty("program",program);row.addProperty("path",output.toString());row.addProperty("pack",net.irisshaders.iris.Iris.getCurrentPackName());
                                row.addProperty("pipeline_identity",System.identityHashCode(pf.get(owner)));row.addProperty("pack_identity",System.identityHashCode(source.getParent().getPack()));
                                var functions=new JsonObject();for(String function:com.pp.shaderlod.bslprefix.BslPrefixSourceR8.FUNCTIONS)
                                    functions.addProperty(function,com.pp.shaderlod.bslprefix.BslPrefixSourceR8.hash(com.pp.shaderlod.bslprefix.BslPrefixSourceR8.function(text,function)));
                                row.add("function_sha256",functions);stages.add(row);
                            }
                        }
                        if(stages.isEmpty())throw new IllegalStateException("No actual current linked terrain owner");result.add("bsl_driver",stages);
                    }
                    case "view_y"->{
                        if(AutoTuner.active()||com.pp.shaderlod.measurement.RuntimeMeasurement.requested()||!BenchmarkWorld.ready())throw new IllegalStateException("Private ready demo and untimed lane required");
                        double y=request.get("y").getAsDouble();if(!Double.isFinite(y)||y<64||y>600)throw new IllegalArgumentException("Cloud view height outside64..600");
                        var server=client.getSingleplayerServer();double x=client.player.getX(),z=client.player.getZ();float yaw=client.player.getYRot(),pitch=client.player.getXRot();
                        server.submit(()->{var p=server.getPlayerList().getPlayer(client.player.getUUID());p.teleportTo(server.overworld(),x,y,z,java.util.Set.of(),yaw,pitch);}).get(2,java.util.concurrent.TimeUnit.SECONDS);
                        client.player.setPos(x,y,z);
                    }
                    case "here"->{client.setScreen(null);AutoTuner.start(request.get("seconds").getAsInt(),false);}
                    case "cancel"->{BenchmarkProfileComparison.cancel();AutoTuner.cancel();}
                    case "compare_profile"->{client.setScreen(null);org.lwjgl.glfw.GLFW.glfwFocusWindow(client.getWindow().getWindow());BenchmarkProfileComparison.start();}
                    case "pair"->{
                        if(AutoTuner.active())throw new IllegalStateException("Tuner already owns measurement");
                        client.setScreen(null);
                        org.lwjgl.glfw.GLFW.glfwFocusWindow(client.getWindow().getWindow());
                        var featureId=com.pp.shaderlod.optimization.OptimizationId.fromKey(request.get("feature").getAsString());
                        com.pp.shaderlod.measurement.RuntimeMeasurement.startPair(featureId,120,240,()->com.pp.shaderlod.ShaderLodConfig.snapshot().toString()+com.pp.shaderlod.ShaderLodConfig.PRESET.get());
                    }
                    case "diagnostic_gpu_passes"->{
                        var pair=com.pp.shaderlod.measurement.RuntimeMeasurement.pair();
                        if(!Boolean.getBoolean("shaderlod.dev.gpuTiming")||!Boolean.getBoolean("shaderlod.dev.gpuPassTiming")
                                ||AutoTuner.active()||BenchmarkProfileComparison.active()
                                ||com.pp.shaderlod.measurement.RuntimeMeasurement.requested()
                                ||pair!=null&&(pair.active()||pair.restorationPending()))
                            throw new IllegalStateException("Exclusive development pass-timing lane required");
                        if(request.has("reset")&&request.get("reset").getAsBoolean())
                            com.pp.shaderlod.dev.timing.GpuTimers.resetSamples();
                        com.google.gson.JsonSerializer<Double> finite=(value,type,context)->Double.isFinite(value)
                            ?new com.google.gson.JsonPrimitive(value):com.google.gson.JsonNull.INSTANCE;
                        var diagnosticJson=new GsonBuilder().registerTypeAdapter(Double.class,finite)
                            .registerTypeAdapter(double.class,finite).serializeNulls().create();
                        result.add("diagnostic_gpu_passes",diagnosticJson.toJsonTree(com.pp.shaderlod.dev.timing.GpuTimers.snapshot()));
                        result.addProperty("diagnostic_limits","Existing sampled elapsed/timestamp pass queries; instrumented diagnostic only, not matched normal frame-speed evidence");
                    }
                    case "spatial_tile_diagnostic"->{
                        var pair=com.pp.shaderlod.measurement.RuntimeMeasurement.pair();
                        if(AutoTuner.active()||BenchmarkProfileComparison.active()||BenchmarkWorld.active()
                                ||Boolean.getBoolean("shaderlod.dev.gpuTiming")||Boolean.getBoolean("shaderlod.dev.cpuTiming")
                                ||com.pp.shaderlod.measurement.RuntimeMeasurement.requested()
                                ||pair!=null&&(pair.active()||pair.restorationPending())
                                ||client.level==null||client.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD
                                ||!net.irisshaders.iris.Iris.getShaderPackOptionQueue().isEmpty())
                            throw new IllegalStateException("Untimed settled Overworld diagnostic required");
                        String family=com.pp.shaderlod.measurement.MeasurementPins.selectedFamily();
                        result.add("spatial_tile_diagnostic",JSON.toJsonTree(SpatialTileDiagnostic.capture(family)));
                    }

                    case "measurement"->{
                        var measurementReceipt=OptimizationMeasurementControl.consume(request,control,()->{
                            if(AutoTuner.active()||BenchmarkProfileComparison.active()
                                    ||com.pp.shaderlod.dev.timing.GpuTimers.recording()||com.pp.shaderlod.dev.timing.CpuTimers.recording())
                                throw new IllegalStateException("Another measurement owns the client");
                        },BenchmarkTunerProbe::guardedMeasurement);
                        result.addProperty("measurement_receipt",measurementReceipt.toString());
                        result.add("measurement_guard",JSON.toJsonTree(BenchmarkMeasurementGuard.status()));
                    }
                    case "measurement_guard_selftest"->{
                        if(AutoTuner.active()||BenchmarkProfileComparison.active()
                                ||com.pp.shaderlod.measurement.RuntimeMeasurement.requested())
                            throw new IllegalStateException("Another measurement owns the client");
                        BenchmarkMeasurementGuard.arm();BenchmarkMeasurementGuard.disarm();
                        var proof=BenchmarkMeasurementGuard.status();
                        if(Boolean.TRUE.equals(proof.get("valid"))||((Number)proof.get("completed_render_frames")).longValue()!=0
                                ||((Number)proof.get("render_boundary_observations")).longValue()!=0)
                            throw new IllegalStateException("An empty manual window must not qualify");
                        result.add("measurement_guard",JSON.toJsonTree(proof));
                    }
                    case "enabled"->{
                        if(AutoTuner.active()||BenchmarkProfileComparison.active())throw new IllegalStateException("Measurement owns the profile");
                        var preset=request.has("preset")?com.pp.shaderlod.LodPreset.valueOf(request.get("preset").getAsString()):com.pp.shaderlod.ShaderLodConfig.PRESET.get();
                        if(!com.pp.shaderlod.ShaderLodSettings.apply(request.get("enabled").getAsBoolean(),preset))
                            throw new IllegalStateException(com.pp.shaderlod.ShaderLodSettings.failureMessage());
                    }
                    case "profile"->{
                        if(AutoTuner.active()||BenchmarkProfileComparison.active()||com.pp.shaderlod.measurement.RuntimeMeasurement.requested())
                            throw new IllegalStateException("Measurement owns the profile");
                        var flags=com.pp.shaderlod.optimization.OptimizationSnapshot.noneRequested();
                        for(var entry:request.getAsJsonArray("features"))
                            flags=flags.with(com.pp.shaderlod.optimization.OptimizationId.fromKey(entry.getAsString()),true);
                        boolean observeCpu2=request.has("native_cpu2_identity")&&request.get("native_cpu2_identity").getAsBoolean();
                        NativeCpuIdentity.State cpu2Before=null;
                        if(observeCpu2){
                            if(!com.pp.shaderlod.optimization.OptimizationEditTransaction.cpuOnlyRequests(flags)
                                ||!com.pp.shaderlod.optimization.OptimizationEditTransaction.cpuOnlyRequests(com.pp.shaderlod.optimization.OptimizationConfig.snapshot()))
                                throw new IllegalStateException("CPU2-only requests required");
                            cpu2Before=NativeCpuIdentity.read();
                        }
                        long normalApplyReloads=com.pp.shaderlod.measurement.RuntimeMeasurement.reloadCount();
                        var applied=com.pp.shaderlod.optimization.OptimizationEditTransaction.apply(flags,
                            net.irisshaders.iris.Iris::reload,net.irisshaders.iris.Iris::isFallback,
                            com.pp.shaderlod.optimization.CompileOptionControl::open);
                        result.addProperty("normal_apply_message",applied.message());
                        result.addProperty("normal_apply_reload_delta",com.pp.shaderlod.measurement.RuntimeMeasurement.reloadCount()-normalApplyReloads);
                        if(!applied.success())throw new IllegalStateException(applied.message());
                        if(observeCpu2){
                            var cpu2After=NativeCpuIdentity.read();
                            result.addProperty("normal_apply_reload_delta",cpu2After.reloads()-cpu2Before.reloads());
                            result.addProperty("normal_apply_message",applied.message());
                            result.add("native_cpu2_before",cpu2Before.report());
                            result.add("native_cpu2_after",cpu2After.report());
                            result.addProperty("same_native_cpu2_identity",cpu2Before.same(cpu2After));
                            if(!cpu2Before.same(cpu2After))throw new IllegalStateException("CPU2 reload/identity/options/resources/core/scene changed; stop experiment");
                        }
                    }
                    case "native_cpu2_identity"->{result.add("native_cpu2_identity",NativeCpuIdentity.read().report());}
                    case "generated_control"->{
                        if(AutoTuner.active()||BenchmarkProfileComparison.active()||com.pp.shaderlod.measurement.RuntimeMeasurement.requested())
                            throw new IllegalStateException("Measurement owns the profile");
                        var applied=com.pp.shaderlod.optimization.OptimizationEditTransaction.apply(
                            com.pp.shaderlod.optimization.OptimizationSnapshot.noneRequested(),
                            net.irisshaders.iris.Iris::reload,net.irisshaders.iris.Iris::isFallback,
                            com.pp.shaderlod.optimization.GeneratedControl::open);
                        if(!applied.success())throw new IllegalStateException(applied.message());
                        com.pp.shaderlod.optimization.GeneratedControl.holdCurrentSelection();
                    }
                    case "sildur_gpu_consumer"->{
                        Path observation=dir.resolve(id+"-sildur-gpu-consumer.json");
                        SettledConsumerUniforms.armBenchmarkSildur(observation,request.get("expected_pack").getAsString(),request.get("flag").getAsString());
                        result.addProperty("consumer_receipt",observation.toString());
                    }
case "bliss_ao_consumer" -> {
    Path observation=dir.resolve(id+"-bliss-ao-consumer.json");
    SettledConsumerUniforms.armBenchmarkBlissNativeAo(observation,request.get("expected_pack").getAsString());
    result.addProperty("consumer_receipt",observation.toString());
}
case "photon_trace_consumer" -> {
    Path observation=dir.resolve(id+"-photon-trace-consumer.json");
    SettledConsumerUniforms.armBenchmarkPhotonOrdinaryTrace(observation,request.get("expected_pack").getAsString());
    result.addProperty("consumer_receipt",observation.toString());
}
                    case "makeup_ao_consumer"->{
                        Path observation=dir.resolve(id+"-makeup-ao-consumer.json");
                        SettledConsumerUniforms.armBenchmarkMakeUpAo(observation,request.get("expected_pack").getAsString());
                        result.addProperty("consumer_receipt",observation.toString());
                    }
                    case "makeup_volumetric_consumer"->{
                        Path observation=dir.resolve(id+"-makeup-volumetric-consumer.json");
                        SettledConsumerUniforms.armBenchmarkMakeUpVolumetric(observation,request.get("expected_pack").getAsString());
                        result.addProperty("consumer_receipt",observation.toString());
                    }
                    case "makeup_cloud_consumer"->{
                        Path observation=dir.resolve(id+"-makeup-cloud-consumer.json");
                        SettledConsumerUniforms.armBenchmarkMakeUpCloud(observation,request.get("expected_pack").getAsString());
                        result.addProperty("consumer_receipt",observation.toString());
                    }
                    case "makeup_cloud_live"->{
                        String expected=request.get("expected_pack").getAsString();
                        SettledConsumerUniforms.requireMakeUpCloudWitness(expected);
                        long reloads=com.pp.shaderlod.measurement.RuntimeMeasurement.reloadCount();
                        Object pack=net.irisshaders.iris.Iris.getCurrentPack().orElse(null);
                        Object pipeline=net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable();
                        result.addProperty("reloads_before",reloads);
                        result.addProperty("pack_identity_before",System.identityHashCode(pack));
                        result.addProperty("pipeline_identity_before",System.identityHashCode(pipeline));
                        var flags=com.pp.shaderlod.optimization.OptimizationSnapshot.noneRequested().with(
                            com.pp.shaderlod.optimization.OptimizationId.CLOUD_PRIMARY_BUDGET,request.get("enabled").getAsBoolean());
                        var applied=com.pp.shaderlod.optimization.OptimizationEditTransaction.apply(flags,
                            net.irisshaders.iris.Iris::reload,net.irisshaders.iris.Iris::isFallback,
                            com.pp.shaderlod.optimization.GeneratedControl::openMakeUpCloud);
                        result.addProperty("reloads_after",com.pp.shaderlod.measurement.RuntimeMeasurement.reloadCount());
                        result.addProperty("pack_identity_after",System.identityHashCode(net.irisshaders.iris.Iris.getCurrentPack().orElse(null)));
                        result.addProperty("pipeline_identity_after",System.identityHashCode(net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable()));
                        if(!applied.success())throw new IllegalStateException(applied.message());
                        if(reloads!=com.pp.shaderlod.measurement.RuntimeMeasurement.reloadCount()
                                ||pack!=net.irisshaders.iris.Iris.getCurrentPack().orElse(null)
                                ||pipeline!=net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable())
                            throw new IllegalStateException("Same-program cloud control reloaded or changed identity");
                        Path observation=dir.resolve(id+"-makeup-cloud-live-consumer.json");
                        SettledConsumerUniforms.reobserveMakeUpCloud(observation,expected);
                        result.addProperty("same_program_apply_verified",true);
                        result.addProperty("consumer_receipt",observation.toString());
                    }
                    case "sky_consumer"->{
                        Path observation=dir.resolve(id+"-sky-consumer.json");
                        SettledConsumerUniforms.armBenchmarkSky(observation,request.get("expected_pack").getAsString());
                        result.addProperty("consumer_receipt",observation.toString());
                    }
                    case "bliss_shadow_consumer"->{
                        Path observation=dir.resolve(id+"-bliss-shadow-consumer.json");
                        SettledConsumerUniforms.armBenchmarkBlissShadow(observation,request.get("expected_pack").getAsString());
                        result.addProperty("consumer_receipt",observation.toString());
                    }
                    case "unbound_reflection_metric_consumer"->{
                        Path observation=dir.resolve(id+"-unbound-reflection-metric-consumer.json");
                        SettledConsumerUniforms.armBenchmarkUnboundReflectionMetric(observation,request.get("expected_pack").getAsString());
                        result.addProperty("consumer_receipt",observation.toString());
                    }
                    case "bliss_cloud_extent_consumer"->{
                        Path observation=dir.resolve(id+"-bliss-cloud-extent-consumer.json");
                        SettledConsumerUniforms.armBenchmarkBlissCloudExtent(observation,request.get("expected_pack").getAsString());
                        result.addProperty("consumer_receipt",observation.toString());
                    }
                    case "undo"->AutoTuner.undo();
                    case "video"->{
                        if(AutoTuner.active())throw new IllegalStateException("Video edits are outside tuning only");
                        com.pp.shaderlod.measurement.TimingVideoSettings.apply(request.get("fps").getAsInt(),request.get("vsync").getAsBoolean());client.options.save();
                    }
                    case "fullscreen"->{
                        if(AutoTuner.active()||BenchmarkProfileComparison.active()||com.pp.shaderlod.measurement.RuntimeMeasurement.requested())
                            throw new IllegalStateException("Measurement owns presentation");
                        if(client.getWindow().isFullscreen()!=request.get("fullscreen").getAsBoolean())client.getWindow().toggleFullScreen();
                    }
                    case "video_mode"->{
                        var pair=com.pp.shaderlod.measurement.RuntimeMeasurement.pair();
                        if(AutoTuner.active()||BenchmarkProfileComparison.active()
                                ||com.pp.shaderlod.measurement.RuntimeMeasurement.requested()
                                ||pair!=null&&(pair.active()||pair.restorationPending()))
                            throw new IllegalStateException("Measurement owns presentation");
                        var window=client.getWindow();var monitor=window.findBestMonitor();
                        if(monitor==null)throw new IllegalStateException("No actual fullscreen monitor");
                        monitor.refreshVideoModes();
                        int width=request.get("width").getAsInt(),height=request.get("height").getAsInt(),refresh=request.get("refresh").getAsInt();
                        com.mojang.blaze3d.platform.VideoMode selected=null;
                        for(int i=0;i<monitor.getModeCount();i++){
                            var mode=monitor.getMode(i);
                            if(mode.getWidth()==width&&mode.getHeight()==height&&mode.getRefreshRate()==refresh){
                                selected=mode;break;
                            }
                        }
                        if(selected==null)throw new IllegalArgumentException("Requested mode is not advertised by the actual monitor");
                        Path backup=dir.resolve(id+"-options-backup.txt");
                        Files.copy(client.gameDirectory.toPath().resolve("options.txt"),backup);
                        result.addProperty("options_backup",backup.toString());
                        // Force reapplication after a monitor wake even if the saved preference is equal.
                        window.setPreferredFullscreenVideoMode(java.util.Optional.empty());
                        window.setPreferredFullscreenVideoMode(java.util.Optional.of(selected));
                        client.options.fullscreenVideoModeString=selected.write();
                        if(!window.isFullscreen())window.toggleFullScreen();else window.changeFullscreenVideoMode();
                        client.options.save();result.addProperty("requested_video_mode",selected.write());
                    }
                    case "viewport"->{
                        if(AutoTuner.active()||BenchmarkProfileComparison.active()||com.pp.shaderlod.measurement.RuntimeMeasurement.requested())
                            throw new IllegalStateException("Measurement owns presentation");
                        var window=client.getWindow();
                        if(window.isFullscreen())throw new IllegalStateException("Viewport sizing requires windowed presentation");
                        int width=request.get("width").getAsInt(),height=request.get("height").getAsInt();
                        if(width<640||width>3840||height<480||height>2400)throw new IllegalArgumentException("Diagnostic viewport outside bounds");
                        int logicalWidth=(int)Math.round(width*(double)window.getScreenWidth()/window.getWidth());
                        int logicalHeight=(int)Math.round(height*(double)window.getScreenHeight()/window.getHeight());
                        org.lwjgl.glfw.GLFW.glfwSetWindowSize(window.getWindow(),logicalWidth,logicalHeight);
                    }
                    case "generated_zero_pack"->{
                        // Render-only A/B control: no optimization request or permission may be enabled.
                        var pair=com.pp.shaderlod.measurement.RuntimeMeasurement.pair();
                        var core=com.pp.shaderlod.optimization.OptimizationRuntimeContext.core();
                        if(AutoTuner.active()||BenchmarkProfileComparison.active()
                                ||com.pp.shaderlod.measurement.RuntimeMeasurement.requested()
                                ||pair!=null&&(pair.active()||pair.restorationPending())
                                ||core==null||!core.settings().enabled()||core.preset()!=com.pp.shaderlod.LodPreset.PERFORMANCE
                                ||!com.pp.shaderlod.optimization.OptimizationConfig.snapshot().equals(com.pp.shaderlod.optimization.OptimizationSnapshot.noneRequested())
                                ||!net.irisshaders.iris.Iris.getShaderPackOptionQueue().isEmpty()
                                ||!"sildurs-vibrant-shaders".equals(com.pp.shaderlod.optimization.OptimizationConfig.activeFamily())
                                ||client.level==null||client.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD)
                            throw new IllegalStateException("Unowned Sildur Overworld generated-zero control required");
                        String name=request.get("pack").getAsString(),expected=request.get("archive_sha256").getAsString();
                        Path selected=net.irisshaders.iris.Iris.getShaderpacksDirectory().resolve(name);
                        if(!name.startsWith("ShaderLOD-sildurs-vibrant-shaders-")||!Path.of(name).getFileName().toString().equals(name)
                                ||Files.isSymbolicLink(selected)||!Files.isRegularFile(selected)
                                ||!expected.matches("[0-9a-f]{64}")||!expected.equals(com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(selected)))
                            throw new IllegalStateException("Exact retained generated archive required");
                        try(var zip=new java.util.zip.ZipFile(selected.toFile())){
                            var marker=zip.getEntry("shaderlod-selection.json");if(marker==null)throw new IllegalStateException("Generated marker absent");
                            try(var input=zip.getInputStream(marker)){
                                var value=JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                                if(!"sildurs-vibrant-shaders".equals(value.get("adapterId").getAsString())
                                        ||!request.get("recipe_sha256").getAsString().equals(value.get("recipeSha256").getAsString()))
                                    throw new IllegalStateException("Retained recipe identity differs");
                            }
                        }
                        net.irisshaders.iris.Iris.getIrisConfig().setShaderPackName(name);net.irisshaders.iris.Iris.getIrisConfig().save();net.irisshaders.iris.Iris.reload();
                        if(net.irisshaders.iris.Iris.isFallback()||!name.equals(net.irisshaders.iris.Iris.getCurrentPackName())
                                ||!expected.equals(com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(selected)))
                            throw new IllegalStateException("Retained generated control did not load unchanged");
                        com.pp.shaderlod.optimization.GeneratedControl.holdCurrentSelection();
                        result.addProperty("render_control_archive_sha256",expected);
                        result.addProperty("render_control_limits","All requests Off; archive-bound render-only control; no source-certificate or optimization permission granted");
                    }
                    case "pack"->{
                        if(AutoTuner.active())throw new IllegalStateException("Pack edits are outside tuning only");
                        String name=request.get("pack").getAsString();
                        if(!Path.of(name).getFileName().toString().equals(name)||!Files.isRegularFile(net.irisshaders.iris.Iris.getShaderpacksDirectory().resolve(name)))throw new IllegalArgumentException("Installed pack basename required");
                        net.irisshaders.iris.Iris.getIrisConfig().setShaderPackName(name);net.irisshaders.iris.Iris.getIrisConfig().save();net.irisshaders.iris.Iris.reload();
                    }
                    case "exit"->BenchmarkUi.exitDemo();
                    case "close_client"->{
                        if(com.pp.shaderlod.measurement.RuntimeMeasurement.requested()
                            ||AutoTuner.active()||BenchmarkProfileComparison.active()
                            ||com.pp.shaderlod.optimization.OptimizationRuntimeContext.core()==null
                            ||com.pp.shaderlod.optimization.OptimizationRuntimeContext.core().settings().enabled()
                            ||java.util.Arrays.stream(com.pp.shaderlod.optimization.OptimizationId.values()).anyMatch(com.pp.shaderlod.optimization.OptimizationConfig::requested))
                            throw new IllegalStateException("Restore timing, core and requests Off before closing the owned dev client");
                        closeClientTicks=20;
                        result.addProperty("client_close_scheduled",true);
                    }
                    case "pose"->BenchmarkWorld.pose(request.get("index").getAsInt());
                    case "flythrough"->BenchmarkWorld.beginFlythrough();
                    case "click"->{
                        String label=request.get("label").getAsString();
                        var button=client.screen.children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().equals(label)).findFirst().orElseThrow();
                        ((Button)button).onPress();
                    }
                    case "named_capture"->{
                        if(AutoTuner.active()||BenchmarkProfileComparison.active()
                                ||com.pp.shaderlod.measurement.RuntimeMeasurement.requested()
                                ||System.getProperty("shaderlod.dev.controlCaptureReceipt","").isBlank())
                            throw new IllegalStateException("Explicit untimed named capture required");
                        ControlCaptureReceipt.capture(client,request.get("name").getAsString());
                    }
                    case "capture"->{if(AutoTuner.active())throw new IllegalStateException("Captures are outside timing windows only");Screenshot.grab(client.gameDirectory,"benchmark-"+id+".png",client.getMainRenderTarget(),message->{});}
                    case "shadow_probe_inputs"->{
                        if(AutoTuner.active()||BenchmarkProfileComparison.active()
                                ||com.pp.shaderlod.measurement.RuntimeMeasurement.requested())
                            throw new IllegalStateException("Untimed shadow evidence only");
                        var directory=client.gameDirectory.toPath().toAbsolutePath().normalize();
                        var inputs=new WorkloadInputs(directory).benchmarkInputState();
                        var requests=new JsonObject();
                        for(var opt:com.pp.shaderlod.optimization.OptimizationId.values())
                            requests.addProperty(opt.key(),com.pp.shaderlod.optimization.OptimizationConfig.requested(opt));
                        inputs.add("optimization_requests",requests);
                        inputs.add("configuration_files",ConfigurationFilesEvidence.capture(directory));
                        result.add("shadow_probe_inputs",inputs);
                        result.add("loaded_artifact_evidence",LoadedArtifactEvidence.capture());
                        result.add("shadow_original_class_resources",LoadedArtifactEvidence.shadowOriginalResources());
                        result.add("native_source_pins",JSON.toJsonTree(com.pp.shaderlod.measurement.MeasurementPins.capture("shadow-positive")));
                        var properties=new JsonObject();
                        for(String key:java.util.List.of("shadowCoherenceOracle","shadowObserve","visibilityLists","workTrace","gpuTiming","cpuTiming","gpuPassTiming","gpuAttribution","cpuLayerTiming"))
                            properties.addProperty(key,System.getProperty("shaderlod.dev."+key,"unspecified"));
                        result.add("shadow_probe_properties",properties);
                    }
                    case "receiver_clear_diagnostic" -> {
                        try {
                            result.add("receiver_clear_diagnostic",
                                    JSON.toJsonTree(ReceiverClearDiagnostic.capture()));
                        } catch (RuntimeException | Error failure) {
                            var trace = new java.io.StringWriter();
                            failure.printStackTrace(new java.io.PrintWriter(trace));
                            result.addProperty("receiver_clear_diagnostic_failure", trace.toString());
                            throw new IllegalStateException("Receiver clear diagnostic failed; see receiver_clear_diagnostic_failure", failure);
                        }
                    }
                    case "native_submit_arm" -> {NativeSubmitObserver.arm(request.has("pass")?request.get("pass").getAsString():"SOLID"); result.addProperty("native_submit_armed", true);}
                    case "native_submit_export" -> result.add("native_submit", JSON.toJsonTree(NativeSubmitObserver.export()));
                    case "half_ao_uv_arm"->{HalfAoUvObservation.arm();}
                    case "half_ao_uv_status"->{result.add("half_ao_uv",HalfAoUvObservation.status());}
                    case "status"->{result.addProperty("native_draw_chain",com.pp.shaderlod.optimization.nativechain.NativeChainAdmission.status());}
                    default->throw new IllegalArgumentException("Unknown benchmark action");
                }
                result.addProperty("success",true);
            }catch(Exception failure){result.addProperty("success",false);result.addProperty("error",failure.toString());}
            var client=Minecraft.getInstance();result.addProperty("world_active",BenchmarkWorld.active());result.addProperty("world_ready",BenchmarkWorld.ready());
            result.add("coast_motion",CoastMotionObservation.status());
            if(client.player!=null){result.addProperty("camera_yaw",client.player.getYRot());result.addProperty("camera_pitch",client.player.getXRot());}
            result.addProperty("world_status",BenchmarkWorld.status());result.addProperty("tuner_active",AutoTuner.active());result.addProperty("tuner_status",AutoTuner.status());
            result.addProperty("bsl_early_alpha_status",com.pp.shaderlod.optimization.bslearlyalpha.BslEarlyAlphaRuntime.status());
            result.addProperty("astra_early_alpha_status",com.pp.shaderlod.optimization.astraearlyalpha.AstraEarlyAlphaRuntime.status());
            result.addProperty("bliss_early_alpha_status",com.pp.shaderlod.optimization.blissearlyalpha.BlissEarlyAlphaRuntime.status());
            result.addProperty("photon_early_alpha_status",com.pp.shaderlod.optimization.photonearlyalpha.PhotonEarlyAlphaRuntime.status());
            result.addProperty("reimagined_early_alpha_status",com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedEarlyAlphaRuntime.status());
            result.addProperty("sildur_early_alpha_status",com.pp.shaderlod.optimization.sildurearlyalpha.SildurEarlyAlphaRuntime.status());
            result.addProperty("solas_early_alpha_status",com.pp.shaderlod.optimization.solasearlyalpha.SolasEarlyAlphaRuntime.status());
            result.addProperty("unbound_early_alpha_status",com.pp.shaderlod.optimization.unboundearlyalpha.UnboundEarlyAlphaRuntime.status());
            result.addProperty("displayed_fps",client.getFps());
            var animationClock=new JsonObject();
            animationClock.addProperty("fixed",ShaderAnimationClock.fixed());
            animationClock.addProperty("source",ShaderAnimationClock.source());
            animationClock.addProperty("fixed_value",ShaderAnimationClock.fixedValue());
            animationClock.addProperty("current_frame_time_counter",net.irisshaders.iris.uniforms.SystemTimeUniforms.TIMER.getFrameTimeCounter());
            animationClock.addProperty("native_last_frame_time",net.irisshaders.iris.uniforms.SystemTimeUniforms.TIMER.getLastFrameTime());
            animationClock.addProperty("native_iris_frame_counter",net.irisshaders.iris.uniforms.SystemTimeUniforms.COUNTER.getAsInt());
            animationClock.addProperty("scope",ShaderAnimationClock.SCOPE);
            result.add("shader_animation_clock",animationClock);
            result.addProperty("world_loaded",client.level!=null);result.addProperty("coast_ready",coastReady());
            result.addProperty("dimension",client.level==null?"unavailable":client.level.dimension().location().toString());
            var currentPipeline=net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable();
            var currentPack=net.irisshaders.iris.Iris.getCurrentPack().orElse(null);
            result.addProperty("pipeline_identity",currentPipeline==null?0:System.identityHashCode(currentPipeline));
            result.addProperty("current_pack_identity",currentPack==null?0:System.identityHashCode(currentPack));
            result.addProperty("meshes_ready",client.level!=null&&client.levelRenderer.hasRenderedAllSections());
            if(client.getSingleplayerServer()!=null)result.addProperty("server_simulation_frozen",client.getSingleplayerServer().tickRateManager().isFrozen());
            result.addProperty("screen",client.screen==null?"none":client.screen.getClass().getName());
            result.addProperty("paused",client.isPaused());
            result.addProperty("overlay",client.getOverlay()==null?"none":client.getOverlay().getClass().getName());
            if(client.screen!=null){var labels=new JsonArray();for(var child:client.screen.children())if(child instanceof Button b)labels.add(b.getMessage().getString());result.add("screen_buttons",labels);}
            result.addProperty("shaders",net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse());result.addProperty("fallback",net.irisshaders.iris.Iris.isFallback());
            result.addProperty("native_macro_status",NativeMacroInspection.status());
            result.addProperty("pack",net.irisshaders.iris.Iris.getCurrentPackName());result.addProperty("reloads",com.pp.shaderlod.measurement.RuntimeMeasurement.reloadCount());
            result.add("gpu_query_allocations",JSON.toJsonTree(com.pp.shaderlod.measurement.RuntimeMeasurement.queryAllocationCounts()));
            result.addProperty("core_enabled",com.pp.shaderlod.ShaderLodConfig.ENABLED.get());result.addProperty("preset",com.pp.shaderlod.ShaderLodConfig.PRESET.get().name());
            result.add("core_bands",JSON.toJsonTree(com.pp.shaderlod.ShaderLodConfig.snapshot()));
            result.add("effect_settings",JSON.toJsonTree(com.pp.shaderlod.ShaderLodEffectConfig.tuningValues()));
            result.addProperty("terrain_chunk_cap",com.pp.shaderlod.ShaderLodConfig.TERRAIN_VANILLA_CHUNK_CAP.get());result.addProperty("terrain_warmup_seconds",com.pp.shaderlod.ShaderLodConfig.TERRAIN_WARMUP_SECONDS.get());
            result.addProperty("terrain_confirmation",com.pp.shaderlod.ShaderLodConfig.TERRAIN_WARM_SOURCE_CONFIRMED.get());
            result.addProperty("fps_limit",client.options.framerateLimit().get());result.addProperty("vsync",client.options.enableVsync().get());
            result.addProperty("window_fps_limit",client.getWindow().getFramerateLimit());
            result.addProperty("mac_swap_interval",com.pp.shaderlod.measurement.TimingVideoSettings.macSwapInterval());
            result.addProperty("fullscreen",client.getWindow().isFullscreen());
            result.addProperty("framebuffer_width",client.getWindow().getWidth());result.addProperty("framebuffer_height",client.getWindow().getHeight());
            result.addProperty("target_width",client.getMainRenderTarget().width);result.addProperty("target_height",client.getMainRenderTarget().height);
            result.addProperty("focused",client.isWindowActive());
            result.addProperty("comparison_active",BenchmarkProfileComparison.active());result.addProperty("comparison_status",BenchmarkProfileComparison.status());
            if(BenchmarkProfileComparison.output()!=null)result.addProperty("comparison_directory",BenchmarkProfileComparison.output().toString());
            var pair=com.pp.shaderlod.measurement.RuntimeMeasurement.pair();if(pair!=null){result.addProperty("pair_active",pair.active());result.addProperty("pair_status",pair.status());}
            // Read the actual post-Apply consumer only outside owned timing windows.
            // This lets untimed family controls verify native choices and private compile values.
            if(!AutoTuner.active()&&!BenchmarkProfileComparison.active()
                    &&!com.pp.shaderlod.measurement.RuntimeMeasurement.requested())try{
                var pack=net.irisshaders.iris.Iris.getCurrentPack().orElse(null);
                if(pack!=null){
                    var options=pack.getShaderPackOptions();var values=options.getOptionValues();
                    var effective=new JsonObject();
                    for(String key:new java.util.TreeSet<>(options.getOptionSet().getBooleanOptions().keySet()))
                        effective.addProperty(key,Boolean.toString(values.getBooleanValueOrDefault(key)));
                    for(String key:new java.util.TreeSet<>(options.getOptionSet().getStringOptions().keySet()))
                        effective.addProperty(key,values.getStringValueOrDefault(key));
                    result.add("effective_shader_options",effective);
                    if("complementary-reimagined".equals(com.pp.shaderlod.optimization.OptimizationConfig.activeFamily()))
                        result.add("ao_depth_control_proof",JSON.toJsonTree(com.pp.shaderlod.optimization.reimagineddepth.ReimaginedAoDepthRuntime.controlProof()));

                    result.add("ao_depth_source_calls",JSON.toJsonTree(java.util.Map.of(
                        "reimagined",com.pp.shaderlod.optimization.reimagineddepth.ReimaginedAoDepthRuntime.sourceCalls(),
                        "unbound",com.pp.shaderlod.optimization.unbounddepth.UnboundAoDepthRuntime.sourceCalls(),
                        "solas",com.pp.shaderlod.optimization.solasdepth.SolasAoDepthRuntime.sourceCalls(),
                        "bsl",com.pp.shaderlod.optimization.bsl.BslAoDepthRuntime.sourceCalls())));

                    if("photon".equals(com.pp.shaderlod.optimization.OptimizationConfig.activeFamily()))
                        result.add("photon_ao_history_costs",JSON.toJsonTree(com.pp.shaderlod.optimization.photonhistory.PhotonAoHistoryRuntime.costCounters()));
                    if("photon".equals(com.pp.shaderlod.optimization.OptimizationConfig.activeFamily())){
                        result.add("photon_receiver_costs",JSON.toJsonTree(com.pp.shaderlod.optimization.photonreceiver.PhotonReceiverRuntime.costCounters()));
                        result.addProperty("photon_receiver_status",com.pp.shaderlod.optimization.photonreceiver.PhotonReceiverRuntime.status());
                    }
                    result.addProperty("option_values_type",values.getClass().getName());
                    result.addProperty("native_graph_available",com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.nativeGraphAvailable());
                    result.addProperty("quad_compaction_admitted",com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled(com.pp.shaderlod.optimization.OptimizationId.QUAD_COMMAND_COMPACTION));
                    result.addProperty("quad_compaction_status",com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.compactionStatus());
                    result.addProperty("native_admission_cache",com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.admissionCacheStatus());
                    result.addProperty("depth_priming_status",com.pp.shaderlod.optimization.OptimizationRuntimeContext.implementation(
                        com.pp.shaderlod.optimization.OptimizationConfig.activeFamily(),com.pp.shaderlod.optimization.OptimizationId.DEPTH_PRIMING));
                    if("makeup-ultra-fast-shaders".equals(com.pp.shaderlod.optimization.OptimizationConfig.activeFamily())){
                        result.addProperty("makeup_ao_status",com.pp.shaderlod.optimization.makeupao.MakeUpAoRuntime.status());
                        result.add("makeup_ao_costs",JSON.toJsonTree(com.pp.shaderlod.optimization.makeupao.MakeUpAoRuntime.costCounters()));
                        result.add("makeup_ao_compiled",JSON.toJsonTree(com.pp.shaderlod.optimization.makeupao.MakeUpAoCompiledInputs.status()));
                        result.addProperty("makeup_shadow_status",com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowRuntime.status());
                        result.addProperty("makeup_early_alpha_status",com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaRuntime.status());
                        result.add("makeup_early_alpha_costs",JSON.toJsonTree(com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaRuntime.costCounters()));
                        result.add("makeup_colored_shadow_proof",new com.google.gson.Gson().toJsonTree(com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowRuntime.controlProof()));
                    }
                    if("sildurs-vibrant-shaders".equals(com.pp.shaderlod.optimization.OptimizationConfig.activeFamily())){
                        result.add("sildur_ao_compiled",JSON.toJsonTree(com.pp.shaderlod.optimization.sourcecache.IrisCompositeCompiledInputs.status(com.pp.shaderlod.optimization.sourcecache.IrisCompositeCompiledInputs.Family.SILDUR)));
                        result.addProperty("sildur_ao_status",com.pp.shaderlod.optimization.sildurao.SildurAoRuntime.status());
                        result.add("sildur_ao_costs",JSON.toJsonTree(com.pp.shaderlod.optimization.sildurao.SildurAoRuntime.costCounters()));
                    }
                    if("complementary-unbound".equals(com.pp.shaderlod.optimization.OptimizationConfig.activeFamily())){
                        result.add("unbound_ao_compiled",JSON.toJsonTree(com.pp.shaderlod.optimization.sourcecache.IrisCompositeCompiledInputs.status(com.pp.shaderlod.optimization.sourcecache.IrisCompositeCompiledInputs.Family.UNBOUND)));
                        result.addProperty("unbound_ao_status",com.pp.shaderlod.optimization.unboundao.UnboundAoRuntime.status());
                        result.add("unbound_ao_costs",JSON.toJsonTree(com.pp.shaderlod.optimization.unboundao.UnboundAoRuntime.costCounters()));
                        result.add("unbound_receiver_proof",JSON.toJsonTree(com.pp.shaderlod.optimization.unboundearlyalpha.UnboundEarlyAlphaRuntime.receiverCostCounters()));
                        result.add("unbound_temporal_proof",JSON.toJsonTree(com.pp.shaderlod.optimization.unboundearlyalpha.UnboundEarlyAlphaRuntime.temporalProofCounters()));
                        result.addProperty("receiver_status",com.pp.shaderlod.optimization.unboundearlyalpha.UnboundEarlyAlphaRuntime.receiverStatus());
                        result.addProperty("temporal_status",com.pp.shaderlod.optimization.unboundearlyalpha.UnboundEarlyAlphaRuntime.temporalStatus());
                    }
                    if("complementary-reimagined".equals(com.pp.shaderlod.optimization.OptimizationConfig.activeFamily())){
                        result.add("reimagined_ao_compiled",JSON.toJsonTree(com.pp.shaderlod.optimization.sourcecache.IrisCompositeCompiledInputs.status(com.pp.shaderlod.optimization.sourcecache.IrisCompositeCompiledInputs.Family.REIMAGINED)));
                        result.addProperty("reimagined_ao_status",com.pp.shaderlod.optimization.reimaginedao.ReimaginedAoRuntime.status());
                        result.add("reimagined_ao_costs",JSON.toJsonTree(com.pp.shaderlod.optimization.reimaginedao.ReimaginedAoRuntime.costCounters()));
                    }
                    if("complementary-reimagined".equals(com.pp.shaderlod.optimization.OptimizationConfig.activeFamily())){
                        result.addProperty("early_alpha_status",com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedEarlyAlphaRuntime.status());
                        if(request.has("observe_sources")&&request.get("observe_sources").getAsBoolean()){
                            result.addProperty("early_alpha_source_admitted",com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedEarlyAlphaRuntime.source(pack));
                            result.addProperty("temporal_source_admitted",com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedEarlyAlphaRuntime.temporalSource(pack));
                            result.addProperty("receiver_source_admitted",com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedEarlyAlphaRuntime.receiverSource(pack));
                        }
                        result.add("reimagined_shadow_costs",JSON.toJsonTree(com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedEarlyAlphaRuntime.costCounters()));
                        result.add("reimagined_temporal_proof",JSON.toJsonTree(com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedEarlyAlphaRuntime.temporalProofCounters()));
                        result.addProperty("temporal_status",com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedEarlyAlphaRuntime.temporalStatus());
                        result.addProperty("receiver_status",com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedEarlyAlphaRuntime.receiverStatus());
                        result.add("early_alpha_control_proof",new com.google.gson.Gson().toJsonTree(com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedEarlyAlphaRuntime.controlProof()));
                    }
                }
            }catch(Exception|LinkageError unavailable){result.addProperty("consumer_observation_error",unavailable.toString());}
            var flags=new JsonObject();for(var opt:com.pp.shaderlod.optimization.OptimizationId.values())flags.addProperty(opt.key(),com.pp.shaderlod.optimization.OptimizationConfig.requested(opt));result.add("flags",flags);
            Path temp=Files.createTempFile(dir,id,".tmp");Files.writeString(temp,JSON.toJson(result)+"\n");Files.move(temp,receipt,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        }catch(Exception failure){com.mojang.logging.LogUtils.getLogger().warn("Benchmark probe rejected: {}",failure.toString());}
    }
    /** Guard admission, timing action and receipt share consume()'s failure transaction. */
    private static JsonObject guardedMeasurement(OptimizationMeasurementControl.Request request) throws Exception {
        JsonObject result;
        if(request.action().equals("timing_on")) {
            var pair=com.pp.shaderlod.measurement.RuntimeMeasurement.pair();
            if(com.pp.shaderlod.measurement.RuntimeMeasurement.requested()
                    ||pair!=null&&(pair.active()||pair.restorationPending()))
                throw new IllegalStateException("Another timing owner is active or needs restoration");
            BenchmarkMeasurementGuard.arm();
            try {
                com.pp.shaderlod.optimization.RendererWorkDiagnostics.beginWindow();
                result=OptimizationMeasurementControl.normalAction(request);
            }
            catch(Exception|Error failure) {
                try{com.pp.shaderlod.measurement.RuntimeMeasurement.request(false);}
                catch(Exception|Error recovery){failure.addSuppressed(recovery);}
                finally{BenchmarkMeasurementGuard.disarm();com.pp.shaderlod.optimization.RendererWorkDiagnostics.endWindow();}
                throw failure;
            }
        } else {
            result=OptimizationMeasurementControl.normalAction(request);
            if(request.action().equals("timing_off")&&(!result.has("success")||result.get("success").getAsBoolean())) {
                BenchmarkMeasurementGuard.disarm();
                com.pp.shaderlod.optimization.RendererWorkDiagnostics.endWindow();
            }
        }
        result.add("manual_window_guard",JSON.toJsonTree(BenchmarkMeasurementGuard.status()));
        result.add("renderer_work",JSON.toJsonTree(com.pp.shaderlod.optimization.RendererWorkDiagnostics.snapshot()));
        return result;
    }
}

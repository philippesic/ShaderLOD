package com.pp.shaderlod.pccontrol;

import com.google.gson.*;
import com.pp.shaderlod.BenchmarkUi;
import com.pp.shaderlod.ShaderLodConfig;
import com.pp.shaderlod.benchmark.BenchmarkWorld;
import com.pp.shaderlod.measurement.RuntimeMeasurement;
import com.pp.shaderlod.optimization.OptimizationConfig;
import com.pp.shaderlod.tuning.*;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.TreeMap;

/** Optional test-harness variant: no mixins, network, or startup mutations; explicit timing only. */
@Mod(value="shaderlod_dev",dist=Dist.CLIENT)
public final class PcControl {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_REQUEST_BYTES=65536;
    private static final long RECEIPT_RETRY_NS=250_000_000L, RECEIPT_DEADLINE_NS=15_000_000_000L;
    private Path base;
    private FileTime observed;
    private long observedSize=-1,nextPoll;
    private PendingQuit quitting;
    private PendingPublication publishing;
    private JsonObject lastPublicationFailure;
    private final PcBenchmarkControl benchmark = new PcBenchmarkControl();
    private record PendingQuit(Path receipt,JsonObject result,long deadline) {}
    /** Completed action only: retries never retain a command or invoke dispatch. */
    private static final class PendingPublication {
        final Path receipt;
        final JsonObject result;
        final long deadline;
        String payload;
        long nextAttempt;
        int attempts;
        boolean stopAfterPublish;
        Exception lastFailure;
        PendingPublication(Path receipt,JsonObject result,boolean stopAfterPublish,long now) {
            this.receipt=receipt;this.result=result.deepCopy();this.stopAfterPublish=stopAfterPublish;
            deadline=now+RECEIPT_DEADLINE_NS;payload=JSON.toJson(this.result)+"\n";
        }
    }

    public PcControl() {
        if(Boolean.getBoolean("shaderlod.pcControl")) {
            NeoForge.EVENT_BUS.addListener(this::tick);
            NeoForge.EVENT_BUS.addListener(this::frame);
        }
    }

    private void frame(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event) {
        benchmark.monitor(Minecraft.getInstance());
    }

    private void tick(ClientTickEvent.Post event) {
        var client=Minecraft.getInstance();
        benchmark.monitor(client);
        if(publishing!=null){publishReceipt(client);return;}
        if(quitting!=null){finishQuit(client);return;}
        long now=System.nanoTime();if(now<nextPoll)return;nextPoll=now+250_000_000L;
        if(base==null)base=client.gameDirectory.toPath().toAbsolutePath().normalize().resolve("shaderlod-remote");
        Path request=base.resolve("request.json");
        try {
            if(Files.isSymbolicLink(base)||!Files.isRegularFile(request,LinkOption.NOFOLLOW_LINKS))return;
            var time=Files.getLastModifiedTime(request,LinkOption.NOFOLLOW_LINKS);long size=Files.size(request);
            if(time.equals(observed)&&size==observedSize)return;observed=time;observedSize=size;
            if(size>MAX_REQUEST_BYTES)throw new IOException("Request exceeds 64 KiB");
            byte[] bytes;try(var input=Files.newInputStream(request)){bytes=input.readNBytes(MAX_REQUEST_BYTES+1);}
            if(bytes.length>MAX_REQUEST_BYTES)throw new IOException("Request exceeds 64 KiB");
            var command=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            String id=string(command,"request_id");if(!id.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Invalid request_id");
            Path receipts=base.resolve("receipts");if(Files.isSymbolicLink(receipts))throw new IOException("Receipt directory must not be a symlink");
            Files.createDirectories(receipts);Path receipt=receipts.resolve(id+".json");
            // Existing PENDING and terminal receipts both prohibit replay, including after restart.
            try{Files.writeString(receipt,"{\"status\":\"PENDING\",\"request_id\":\""+id+"\"}\n",StandardOpenOption.CREATE_NEW);}
            catch(FileAlreadyExistsException replay){return;}
            var result=new JsonObject();result.addProperty("request_id",id);result.addProperty("at",Instant.now().toString());
            try {
                dispatch(client,command,result);
                if(quitting!=null)return;
                result.addProperty("success",true);
            }catch(Exception failure){result.addProperty("success",false);result.addProperty("error",failure.toString());}
            completeReceipt(client,receipt,result,false);
        }catch(Exception failure){com.mojang.logging.LogUtils.getLogger().error("ShaderLOD PC control request refused",failure);}
    }

    private void dispatch(Minecraft client,JsonObject command,JsonObject result)throws Exception {
        switch(string(command,"action")) {
            case "status" -> {}
            case "bsl_c6_mode" -> {
                idle();
                if(!Boolean.getBoolean("shaderlod.pcControl.bslC6Experiment")
                        ||com.pp.shaderlod.optimization.framecopy.FrameCopyRuntime.observing()
                        ||com.pp.shaderlod.optimization.mips.BslMipWitness.observing())
                    throw new IllegalStateException("Explicit experiment launch and stopped diagnostics required");
                String key="shaderlod.experimentalBslC6Mip",prior=System.getProperty(key);
                boolean enabled=command.get("enabled").getAsBoolean();
                try {System.setProperty(key,Boolean.toString(enabled));Iris.reload();
                    if(Iris.isFallback())throw new IllegalStateException("Experimental graph reload failed");
                }catch(Exception failure){try{if(prior==null)System.clearProperty(key);else System.setProperty(key,prior);Iris.reload();}
                    catch(Exception recovery){failure.addSuppressed(recovery);}throw failure;}
                result.addProperty("bsl_c6_experiment_requested",enabled);
            }
            case "frame_copy_observe" -> {idle();com.pp.shaderlod.optimization.framecopy.FrameCopyRuntime.setObserving(false);if(!command.has("reset")||command.get("reset").getAsBoolean())com.pp.shaderlod.optimization.framecopy.FrameCopyRuntime.resetCounters();com.pp.shaderlod.optimization.framecopy.FrameCopyRuntime.setObserving(command.get("enabled").getAsBoolean());}
            case "bsl_mip_observe" -> {
                idle();if(com.pp.shaderlod.optimization.framecopy.FrameCopyRuntime.observing())throw new IllegalStateException("Exclusive untimed observer required");
                boolean enabled=command.get("enabled").getAsBoolean(),reload=command.has("reload")&&command.get("reload").getAsBoolean();
                if(reload&&!enabled)throw new IllegalArgumentException("Reload requires armed observer");
                boolean was=com.pp.shaderlod.optimization.mips.BslMipWitness.observing();
                com.pp.shaderlod.optimization.mips.BslMipWitness.setObserving(enabled);
                if((!command.has("reset")||command.get("reset").getAsBoolean())&&was==enabled)com.pp.shaderlod.optimization.mips.BslMipWitness.resetCounters();
                if(reload)Iris.reload();
                result.add("bsl_mip_witness",JSON.toJsonTree(com.pp.shaderlod.optimization.mips.BslMipWitness.snapshot()));
            }
            case "measure_start" -> {idle();if(com.pp.shaderlod.optimization.framecopy.FrameCopyRuntime.observing()||com.pp.shaderlod.optimization.mips.BslMipWitness.observing())throw new IllegalStateException("Stop diagnostics before performance capture");benchmark.start(client,command,result);}
            case "measure_stop" -> benchmark.stop(client,result);
            case "pose" -> {idle();BenchmarkWorld.pose(command.get("index").getAsInt());}
            case "focus" -> {idle();org.lwjgl.glfw.GLFW.glfwFocusWindow(client.getWindow().getWindow());result.addProperty("focus_requested",true);}
            case "dh_mode" -> {idle();benchmark.dhMode(string(command,"mode"),result);}
            case "capture" -> {
                idle();if(!BenchmarkWorld.ready()||!client.isWindowActive()||client.screen!=null||client.getOverlay()!=null||Iris.isFallback())
                    throw new IllegalStateException("Settled focused owned world required for capture");
                String name="pc-"+string(command,"request_id")+".png";
                net.minecraft.client.Screenshot.grab(client.gameDirectory,name,client.getMainRenderTarget(),message->{});
                result.addProperty("capture_requested",client.gameDirectory.toPath().resolve("screenshots").resolve(name).toString());
                result.addProperty("capture_completed",false);
            }
            case "open" -> {idle();BenchmarkUi.openDemo();}
            case "start" -> {
                idle();var goal=TuningGoal.valueOf(string(command,"goal"));
                int seconds=command.get("seconds").getAsInt();boolean full=command.get("full").getAsBoolean();
                boolean readyBefore=BenchmarkWorld.ready();
                org.lwjgl.glfw.GLFW.glfwFocusWindow(client.getWindow().getWindow());
                BenchmarkUi.start(new TuningRequest(goal,seconds,full));
                // The synchronous ready-world start has just applied native-Off and
                // armed warmup; no subsequent render/sample has occurred yet.
                if(readyBefore&&AutoTuner.active()&&System.getProperty("os.name","").startsWith("Windows"))try{
                    result.addProperty("windows_swap_interval_at_start",org.lwjgl.opengl.WGLEXTSwapControl.wglGetSwapIntervalEXT());
                }catch(Exception|LinkageError unavailable){result.addProperty("windows_swap_interval_at_start_error",unavailable.toString());}
            }
            case "cancel" -> AutoTuner.cancel();
            case "undo" -> {requirePackRestored();if(RuntimeMeasurement.needed())throw new IllegalStateException("Manual measurement owns recovery");BenchmarkUi.undo();}
            case "enabled" -> {
                idle();var preset=com.pp.shaderlod.LodPreset.valueOf(string(command,"preset"));
                if(!com.pp.shaderlod.ShaderLodSettings.apply(command.get("enabled").getAsBoolean(),preset))
                    throw new IllegalStateException(com.pp.shaderlod.ShaderLodSettings.failureMessage());
            }
            case "fullscreen" -> {
                idle();if(client.getWindow().isFullscreen()!=command.get("fullscreen").getAsBoolean())client.getWindow().toggleFullScreen();
            }
            case "video_mode" -> {
                idle();var window=client.getWindow();long monitor=org.lwjgl.glfw.GLFW.glfwGetWindowMonitor(window.getWindow());
                if(monitor==0)monitor=org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor();
                int width=command.get("width").getAsInt(),height=command.get("height").getAsInt(),refresh=command.get("refresh").getAsInt();
                var modes=org.lwjgl.glfw.GLFW.glfwGetVideoModes(monitor);com.mojang.blaze3d.platform.VideoMode selected=null;
                if(modes!=null)for(int i=0;i<modes.limit();i++){var m=modes.get(i);if(m.width()==width&&m.height()==height&&m.refreshRate()==refresh){selected=new com.mojang.blaze3d.platform.VideoMode(m);break;}}
                if(selected==null)throw new IllegalArgumentException("Requested mode is not advertised by the actual monitor");
                window.setPreferredFullscreenVideoMode(java.util.Optional.of(selected));client.options.fullscreenVideoModeString=selected.write();
                if(!window.isFullscreen())window.toggleFullScreen();else window.changeFullscreenVideoMode();
                client.options.save();result.addProperty("requested_video_mode",selected.write());
            }
            case "viewport" -> {
                idle();var window=client.getWindow();
                if(window.isFullscreen())throw new IllegalStateException("Viewport diagnosis requires windowed presentation");
                int width=command.get("width").getAsInt(),height=command.get("height").getAsInt();
                if(width<640||width>3840||height<480||height>2160)throw new IllegalArgumentException("Diagnostic viewport outside640x480..3840x2160");
                int logicalWidth=(int)Math.round(width*(double)window.getScreenWidth()/window.getWidth());
                int logicalHeight=(int)Math.round(height*(double)window.getScreenHeight()/window.getHeight());
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(window.getWindow(),logicalWidth,logicalHeight);
            }
            case "profile" -> {
                idle();var flags=com.pp.shaderlod.optimization.OptimizationSnapshot.noneRequested();
                for(var entry:command.getAsJsonArray("features"))
                    flags=flags.with(com.pp.shaderlod.optimization.OptimizationId.fromKey(entry.getAsString()),true);
                var applied=com.pp.shaderlod.optimization.OptimizationEditTransaction.apply(flags,Iris::reload,Iris::isFallback,
                    com.pp.shaderlod.optimization.CompileOptionControl::open);
                if(!applied.success())throw new IllegalStateException(applied.message());
            }
            case "source_fingerprint" -> {
                idle();String family=OptimizationConfig.activeFamily();
                String stem=switch(family){case "photon"->"Photon";case "solas-shader"->"Solas";case "astralex"->"Astra";
                    case "bsl-shaders"->"Bsl";case "makeup-ultra-fast-shaders"->"MakeUp";case "sildurs-vibrant-shaders"->"Sildur";
                    case "bliss-shader"->"Bliss";case "complementary-reimagined"->"Reimagined";case "complementary-unbound"->"Unbound";
                    default->throw new IllegalStateException("Pinned early-alpha family required");};
                var owner=Class.forName("com.pp.shaderlod.optimization."+stem.toLowerCase(java.util.Locale.ROOT)+"earlyalpha."+stem+"EarlyAlphaRuntime");
                Object stamp=com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache.fingerprint(owner,Iris.getShaderpacksDirectory(),Iris.getCurrentPackName(),family);
                result.addProperty("source_fingerprint_sha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(JSON.toJson(stamp).getBytes(java.nio.charset.StandardCharsets.UTF_8))));
                result.addProperty("source_fingerprint_scope","Actual shipped validator and current selected source/resources; not a draw grant or avoided-pixel count");
            }
            case "optimization_status" -> {
                idle();var id=com.pp.shaderlod.optimization.OptimizationId.fromKey(string(command,"feature"));
                result.addProperty("implementation_status",com.pp.shaderlod.optimization.OptimizationRuntimeContext.implementation(
                    OptimizationConfig.activeFamily(),id));
            }
            case "pack" -> {
                idle();String pack=basename(string(command,"pack"));Path installed=Iris.getShaderpacksDirectory().resolve(pack);
                if(!Files.isRegularFile(installed,LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException("Installed original ZIP basename required");
                var recognized=com.pp.shaderlod.packs.SupportedPackCatalog.recognize(
                    com.pp.shaderlod.integration.PackSourceReader.read(installed).memberSha256());
                if(recognized.isEmpty()||recognized.get().stage()!=com.pp.shaderlod.packs.SupportedPackCatalog.Stage.RECOGNIZED_ORIGINAL)
                    throw new IllegalArgumentException("Pinned supported original shader required");
                var before=new JsonObject();before.addProperty("previous_pack",Iris.getIrisConfig().getShaderPackName().orElse(""));
                before.addProperty("previous_enabled",Iris.getIrisConfig().areShadersEnabled());
                Files.writeString(base.resolve("pack-recovery.json"),JSON.toJson(before),StandardOpenOption.CREATE_NEW);
                try {
                    Iris.getIrisConfig().setShaderPackName(pack);Iris.getIrisConfig().setShadersEnabled(true);Iris.getIrisConfig().save();Iris.reload();
                    if(Iris.isFallback())throw new IllegalStateException("Shader reload entered fallback");
                    Files.delete(base.resolve("pack-recovery.json"));
                }catch(Exception failure){
                    try{restorePack();}catch(Exception recovery){failure.addSuppressed(recovery);throw new IllegalStateException("Pack recovery remains pending; use recover_pack",failure);}
                    throw failure;
                }
            }
            case "recover_pack" -> {noOtherOwner();restorePack();}
            case "exit" -> {idle();BenchmarkUi.exitDemo();}
            case "save" -> {idle();result.add("preset",JsonParser.parseString(TuningPresetStore.encode(SavedTunings.saveCurrent(string(command,"name")))));}
            case "list" -> result.add("presets",JSON.toJsonTree(SavedTunings.list()));
            case "export" -> {idle();result.addProperty("export",SavedTunings.export(string(command,"id")).toString());}
            case "import" -> {idle();result.add("preset",JsonParser.parseString(TuningPresetStore.encode(SavedTunings.importFile(basename(string(command,"file"))))));}
            case "apply" -> {idle();SavedTunings.apply(string(command,"id"));}
            case "undo_preset" -> {
                requirePackRestored();
                if(AutoTuner.active()||AutoTuner.restorationPending()||RuntimeMeasurement.needed())throw new IllegalStateException("Other recovery owner is active");
                SavedTunings.undo();
            }
            case "quit" -> {
                idle();
                if(client.level!=null&&!BenchmarkWorld.active())throw new IllegalStateException("Unowned world: save and quit normally first");
                benchmark.restoreDh();
                BenchmarkUi.exitDemo();
                Path receipt=base.resolve("receipts").resolve(string(command,"request_id")+".json");
                quitting=new PendingQuit(receipt,result,System.nanoTime()+60_000_000_000L);
            }
            default -> throw new IllegalArgumentException("Unsupported action");
        }
    }

    private void idle() {
        if(benchmark.owned())throw new IllegalStateException("Controller window owns its samples; use measure_stop first");
        noOtherOwner();
        requirePackRestored();
    }

    private void requirePackRestored() {
        if(Files.exists(base.resolve("pack-recovery.json")))throw new IllegalStateException("Pack recovery pending; use recover_pack");
    }

    private static void noOtherOwner() {
        if(AutoTuner.active()||AutoTuner.restorationPending()||SavedTunings.pending()||RuntimeMeasurement.needed())
            throw new IllegalStateException("Timing/restoration owns configuration; use status and the owning Cancel/Undo first");
    }

    private void restorePack()throws Exception {
        Path marker=base.resolve("pack-recovery.json");
        if(Files.isSymbolicLink(marker)||!Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS)||Files.size(marker)>MAX_REQUEST_BYTES)
            throw new IOException("Valid owned pack recovery marker required");
        var before=JsonParser.parseString(Files.readString(marker)).getAsJsonObject();String pack=string(before,"previous_pack");
        if(!pack.isEmpty())basename(pack);
        Iris.getIrisConfig().setShaderPackName(pack.isEmpty()?null:pack);Iris.getIrisConfig().setShadersEnabled(before.get("previous_enabled").getAsBoolean());
        Iris.getIrisConfig().save();Iris.reload();
        if(Iris.isFallback()||!Iris.getIrisConfig().getShaderPackName().orElse("").equals(pack)
                ||Iris.getIrisConfig().areShadersEnabled()!=before.get("previous_enabled").getAsBoolean())
            throw new IllegalStateException("Previous shader selection not restored; recovery retained");
        Files.delete(marker);
    }

    private void finishQuit(Minecraft client) {
        var pending=quitting;
        try {
            requireQuitReady(client,false);
            if(!BenchmarkWorld.active()&&client.level==null&&client.getSingleplayerServer()==null) {
                pending.result.addProperty("success",true);pending.result.addProperty("quit","saved-world-closed; stopping client after receipt commit");
                completeReceipt(client,pending.receipt,pending.result,true);quitting=null;return;
            }
            if(System.nanoTime()>=pending.deadline)throw new IllegalStateException("Quit save/close deadline exceeded; client retained, inspect status/recovery");
        }catch(Exception failure) {
            pending.result.addProperty("success",false);pending.result.addProperty("error",failure.toString());
            pending.result.addProperty("quit","client retained");
            completeReceipt(client,pending.receipt,pending.result,false);quitting=null;
        }
    }

    private void requireQuitReady(Minecraft client,boolean closed) {
        idle();
        if(closed&&(BenchmarkWorld.active()||client.level!=null||client.getSingleplayerServer()!=null))
            throw new IllegalStateException("World ownership changed while quit receipt was pending; client retained");
    }

    private void completeReceipt(Minecraft client,Path receipt,JsonObject result,boolean stopAfterPublish) {
        captureStatus(client,result);
        queueReceipt(receipt,result,stopAfterPublish);
    }

    private void captureStatus(Minecraft client,JsonObject result) {
        try{appendStatus(client,result);}
        catch(Exception failure){result.addProperty("status_capture_error",failure.toString());}
        result.addProperty("status","COMPLETE");
    }

    private void queueReceipt(Path receipt,JsonObject result,boolean stopAfterPublish) {
        if(publishing!=null)throw new IllegalStateException("A completed receipt already owns publication");
        publishing=new PendingPublication(receipt,result,stopAfterPublish,System.nanoTime());
    }

    /** One atomic publication attempt per interval; no sleeping, resampling or action replay. */
    private void publishReceipt(Minecraft client) {
        var pending=publishing;
        long now=System.nanoTime();
        if(now>=pending.deadline){publicationFailed(pending);return;}
        if(now<pending.nextAttempt)return;
        if(pending.stopAfterPublish)try{requireQuitReady(client,true);}
        catch(Exception failure){
            pending.stopAfterPublish=false;
            pending.result.addProperty("success",false);pending.result.addProperty("error",failure.toString());
            pending.result.addProperty("quit","client retained");
            captureStatus(client,pending.result);pending.payload=JSON.toJson(pending.result)+"\n";
        }
        pending.attempts++;
        try{writeReceipt(pending.receipt,pending.payload);}
        catch(Exception failure){
            pending.lastFailure=failure;
            pending.nextAttempt=System.nanoTime()+RECEIPT_RETRY_NS;
            if(System.nanoTime()>=pending.deadline)publicationFailed(pending);
            return;
        }
        publishing=null;
        if(pending.attempts>1)com.mojang.logging.LogUtils.getLogger().info(
            "ShaderLOD receipt committed after {} publication attempts; action was not replayed: {}",pending.attempts,pending.receipt);
        if(pending.stopAfterPublish)client.stop();
    }

    private void publicationFailed(PendingPublication pending) {
        var failure=new JsonObject();failure.addProperty("request_id",pending.result.get("request_id").getAsString());
        failure.addProperty("at",Instant.now().toString());failure.addProperty("receipt",pending.receipt.toString());
        failure.addProperty("attempts",pending.attempts);failure.addProperty("dispatch_ran_once",true);
        failure.addProperty("dispatch_success",pending.result.get("success").getAsBoolean());
        failure.addProperty("error",pending.lastFailure==null?"Receipt publication deadline exceeded before an attempt":pending.lastFailure.toString());
        failure.addProperty("resolution","PENDING retained; action must not be replayed. Inspect outcome with a fresh status ID; client retained.");
        lastPublicationFailure=failure;
        publishing=null; // Finite failure permits fresh IDs; CREATE_NEW still forbids this ID's replay.
        com.mojang.logging.LogUtils.getLogger().error("ShaderLOD completed action receipt could not be published; PENDING retained, no replay, client retained: {}",failure);
    }

    private void appendStatus(Minecraft client,JsonObject result) {
        benchmark.appendStatus(client,result);
        if(lastPublicationFailure!=null)result.add("last_receipt_publication_failure",lastPublicationFailure.deepCopy());
        result.addProperty("client_pid",ProcessHandle.current().pid());
        result.addProperty("client_started_at",ProcessHandle.current().info().startInstant().map(Instant::toString).orElse("unavailable"));
        result.addProperty("game_directory",client.gameDirectory.toPath().toAbsolutePath().normalize().toString());
        result.addProperty("java_runtime_version",System.getProperty("java.runtime.version"));
        result.addProperty("pack_recovery_pending",Files.exists(base.resolve("pack-recovery.json")));
        result.add("frame_copy",JSON.toJsonTree(com.pp.shaderlod.optimization.framecopy.FrameCopyRuntime.snapshot()));
        result.addProperty("bsl_mip_observing",com.pp.shaderlod.optimization.mips.BslMipWitness.observing());
        if("bsl-shaders".equals(OptimizationConfig.activeFamily()))result.add("bsl_mip_graph",JSON.toJsonTree(com.pp.shaderlod.optimization.mips.DeadMipAdmission.bslGraphObservation()));
        result.addProperty("status","COMPLETE");result.addProperty("world_active",BenchmarkWorld.active());result.addProperty("world_ready",BenchmarkWorld.ready());result.addProperty("world_status",BenchmarkWorld.status());
        result.addProperty("world_loaded",client.level!=null);result.addProperty("server_present",client.getSingleplayerServer()!=null);
        result.addProperty("tuner_active",AutoTuner.active());result.addProperty("tuner_status",AutoTuner.status());
        try{result.addProperty("tuner_recovery_pending",AutoTuner.restorationPending());result.addProperty("preset_recovery_pending",SavedTunings.pending());}
        catch(Exception failure){result.addProperty("recovery_status_error",failure.toString());}
        result.addProperty("manual_measurement_owned",RuntimeMeasurement.needed());result.addProperty("pack",Iris.getCurrentPackName());
        result.addProperty("selected_pack",Iris.getIrisConfig().getShaderPackName().orElse(""));result.addProperty("shaders_enabled",Iris.getIrisConfig().areShadersEnabled());
        result.addProperty("fallback",Iris.isFallback());result.addProperty("family",OptimizationConfig.activeFamily());
        result.addProperty("focused",client.isWindowActive());result.addProperty("width",client.getWindow().getWidth());result.addProperty("height",client.getWindow().getHeight());
        result.addProperty("frame_limit",client.options.framerateLimit().get());result.addProperty("window_frame_limit",client.getWindow().getFramerateLimit());
        result.addProperty("vsync",client.options.enableVsync().get());result.addProperty("fullscreen",client.getWindow().isFullscreen());
        result.addProperty("core_enabled",ShaderLodConfig.ENABLED.get());result.addProperty("core_preset",ShaderLodConfig.PRESET.get().name());
        var flags=new TreeMap<String,Boolean>();OptimizationConfig.snapshot().flags().forEach((id,value)->flags.put(id.key(),value));result.add("flags",JSON.toJsonTree(flags));
        // Actual GL strings and native parser projection are never queried while timing owns the client.
        if(!AutoTuner.active()&&!AutoTuner.restorationPending()&&!RuntimeMeasurement.needed()) {
            long monitor=org.lwjgl.glfw.GLFW.glfwGetWindowMonitor(client.getWindow().getWindow());
            if(monitor!=0){var mode=org.lwjgl.glfw.GLFW.glfwGetVideoMode(monitor);if(mode!=null)result.addProperty("monitor_refresh_hz",mode.refreshRate());
                var modes=org.lwjgl.glfw.GLFW.glfwGetVideoModes(monitor);var names=new JsonArray();if(modes!=null)for(int i=0;i<modes.limit();i++)names.add(new com.mojang.blaze3d.platform.VideoMode(modes.get(i)).write());result.add("monitor_modes",names);}
            if(System.getProperty("os.name","").startsWith("Windows"))try{
                result.addProperty("windows_swap_interval",org.lwjgl.opengl.WGLEXTSwapControl.wglGetSwapIntervalEXT());
            }catch(Exception|LinkageError unavailable){result.addProperty("windows_swap_interval_error",unavailable.toString());}
            result.addProperty("gpu_renderer",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
            result.addProperty("gpu_vendor",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VENDOR));
            result.addProperty("gl_version",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION));
            if(client.level!=null)try{result.add("native_options",JSON.toJsonTree(SavedTunings.nativeOptions()));}
            catch(Exception failure){result.addProperty("native_options_error",failure.toString());}
        }
    }

    private static String string(JsonObject object,String key) {
        var value=object.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("String required: "+key);
        return value.getAsString();
    }
    private static String basename(String name) {
        if(name.isBlank()||name.equals(".")||name.equals("..")||name.indexOf('/')>=0||name.indexOf('\\')>=0||name.indexOf(':')>=0||!Path.of(name).getFileName().toString().equals(name))
            throw new IllegalArgumentException("Basename required");return name;
    }
    private static void writeReceipt(Path target,JsonObject result)throws IOException {
        writeReceipt(target,JSON.toJson(result)+"\n");
    }
    private static void writeReceipt(Path target,String payload)throws IOException {
        Path temporary=Files.createTempFile(target.getParent(),"receipt-",".tmp");
        try{Files.writeString(temporary,payload);Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        finally{
            // Cleanup failure must neither mask the move's error nor turn a committed receipt into failure.
            try{Files.deleteIfExists(temporary);}
            catch(IOException cleanup){com.mojang.logging.LogUtils.getLogger().warn("ShaderLOD receipt temporary cleanup refused: {}",temporary,cleanup);}
        }
    }
}

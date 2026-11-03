package com.pp.shaderlod.measurement;
import com.pp.shaderlod.integration.*;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import java.util.*;
/** Actual live source/options/scene/hardware. Full source reads occur at phase boundaries only. */
public final class MeasurementPins {
    private MeasurementPins() {}
    /** Identity comes from executable member hashes, never selected filename/prefix or options. */
    public static String sourceFamily(SourceFingerprint source) {
        return com.pp.shaderlod.packs.SupportedPackCatalog.recognize(source.memberSha256()).map(found->found.pack().id()).orElse("unrecognized");
    }
    /** Explicit untimed development status observation only; measurement capture reuses its existing phase fingerprint. */
    public static String selectedFamily() {
        try {var pack=Iris.getCurrentPack().orElseThrow();var options=pack.getShaderPackOptions();var keys=new HashSet<String>(options.getOptionSet().getBooleanOptions().keySet());keys.addAll(options.getOptionSet().getStringOptions().keySet());var access=new IrisRuntimeAccess(keys);if(access.fallback()||!access.pipelineAvailable())return "unavailable";return sourceFamily(PackSourceReader.read(access.currentPack().orElseThrow().source()));}
        catch(Exception unavailable){return "unavailable";}
    }
    public static String scene() {
        var client=Minecraft.getInstance();var camera=client.getCameraEntity();
        if(client.level==null||camera==null)return "unavailable";
        return System.identityHashCode(client.level)+":"+client.level.dimension()+":"+camera.getX()+":"+camera.getY()+":"+camera.getZ()+":"+camera.getXRot()+":"+camera.getYRot();
    }
    public static Map<String,String> capture(String settings) {return capture(settings,null);}
    public static Map<String,String> capture(String settings,com.pp.shaderlod.optimization.OptimizationId selected) {
        try {
            var pack=Iris.getCurrentPack().orElseThrow();var options=pack.getShaderPackOptions();
            var keys=new HashSet<String>(options.getOptionSet().getBooleanOptions().keySet());keys.addAll(options.getOptionSet().getStringOptions().keySet());
            var access=new IrisRuntimeAccess(keys);var observed=access.currentPack().orElseThrow();
            if(access.fallback()||!access.pipelineAvailable())throw new IllegalStateException("No usable shader pipeline");
            var client=Minecraft.getInstance();var camera=client.getCameraEntity();
            if(client.level==null||camera==null)throw new IllegalStateException("World/camera unavailable");
            var pins=new TreeMap<String,String>();
            pins.put("optimization_profile",com.pp.shaderlod.optimization.OptimizationConfig.activeFamily());
            pins.put("settings",settings);pins.put("source_path",observed.source().toString());
            var fingerprint=PackSourceReader.read(observed.source());String family=sourceFamily(fingerprint);
            pins.put("source_sha256",fingerprint.sourceSha256());pins.put("source_family",family);
            var effective=new TreeMap<>(observed.effectiveOptions());var controlled=new TreeMap<String,String>();
            if(selected!=null)for(var key:com.pp.shaderlod.optimization.CompileOptionControl.controlledOptions(selected)) {
                var value=effective.remove(key);if(value!=null)controlled.put(key,value);
            }
            pins.put("effective_options",effective.toString());
            pins.put("controlled_option_keys",controlled.keySet().toString());
            pins.put("controlled_option_values",controlled.toString());
            pins.put("resolution",access.renderedSurface().orElseThrow().toString());
            pins.put("gpu_vendor",GL11.glGetString(GL11.GL_VENDOR));pins.put("gpu_renderer",GL11.glGetString(GL11.GL_RENDERER));pins.put("gl_version",GL11.glGetString(GL11.GL_VERSION));
            pins.put("minecraft_options",client.options.dumpOptionsForReport());
            pins.put("frame_limit",client.options.framerateLimit().get().toString());
            pins.put("vsync",client.options.enableVsync().get().toString());
            pins.put("window_frame_limit","unavailable: modern Window has no limit API");
            pins.put("effective_frame_limit",Integer.toString(client.getFramerateLimitTracker().getFramerateLimit()));
            pins.put("client_running",Boolean.toString(client.isRunning()));
            pins.put("mac_swap_interval",TimingVideoSettings.macSwapInterval());
            pins.put("render_distance",client.options.renderDistance().get().toString());
            pins.put("java",System.getProperty("java.runtime.version"));pins.put("os",System.getProperty("os.name")+" "+System.getProperty("os.arch"));
            pins.put("scene",scene());
            pins.put("simulation",RuntimeMeasurement.sceneProvenance());
            pins.put("shader_clock","native shader animation/frame clocks continue; temporal AA stays native");
            if(selected!=null)pins.put("backend_implementation",com.pp.shaderlod.optimization.OptimizationRuntimeContext.implementation(family,selected));
            pins.put("scope","GameRenderer.render HEAD/RETURN; frame wall between render starts; process CPU includes all JVM threads during render");
            pins.put("gpu_query_mode",RuntimeMeasurement.gpuQueryMode());
            pins.put("clock_resolution","storage ns; nanoTime/thread/process/GPU effective resolution is platform/driver-dependent and not inferred from units; GPU counter width appears in raw status");
            pins.putAll(ModArtifactIdentity.capture(MeasurementPins.class,RuntimeMeasurement.class,OpenGlElapsedBackend.class));
            pins.put("clock","System.nanoTime; ThreadMXBean; OperatingSystemMXBean; GL_TIME_ELAPSED result ns with reported counter bits; storage unit ns is not hardware resolution");
            return Collections.unmodifiableMap(pins);
        }catch(java.io.IOException failure) {throw new IllegalStateException("Could not fingerprint selected source",failure);}
    }
}

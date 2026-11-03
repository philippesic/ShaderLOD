package com.pp.shaderlod.dev;

import com.pp.shaderlod.measurement.RuntimeMeasurement;
import com.pp.shaderlod.optimization.OptimizationConfig;
import java.util.Map;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;

/** Development-only observations around manual normal-clock windows; no extra GL queries. */
public final class BenchmarkMeasurementGuard {
    private static boolean armed;
    private static Object world,pack,cameraOwner;
    private static long reloads,generation,frames,boundaries,worldFrames,worldFramesAtBegin;
    private static boolean rendering,worldRendering;
    private static int width,height,cap,windowCap,targetWidth,targetHeight,renderDistance,fov;
    private static boolean fullscreen,vsync;
    private static double x,y,z;
    private static float yaw,pitch;
    private static String failure="not armed";
    private BenchmarkMeasurementGuard() {}

    static void arm() {
        if(armed)throw new IllegalStateException("A manual benchmark window is already guarded");
        var c=Minecraft.getInstance();var camera=c.getCameraEntity();
        if(c.level==null||camera==null||c.screen!=null||!c.isWindowActive())
            throw new IllegalStateException("A focused world without a GUI is required for manual benchmark timing");
        world=c.level;pack=Iris.getCurrentPack().orElse(null);cameraOwner=camera;
        reloads=RuntimeMeasurement.reloadCount();generation=OptimizationConfig.generation();
        width=c.getWindow().getWidth();height=c.getWindow().getHeight();
        targetWidth=c.getMainRenderTarget().width;targetHeight=c.getMainRenderTarget().height;
        renderDistance=c.options.renderDistance().get();fov=c.options.fov().get();
        fullscreen=c.getWindow().isFullscreen();vsync=c.options.enableVsync().get();
        cap=c.options.framerateLimit().get();windowCap=c.getWindow().getFramerateLimit();
        x=camera.getX();y=camera.getY();z=camera.getZ();yaw=camera.getYRot();pitch=camera.getXRot();
        frames=boundaries=worldFrames=worldFramesAtBegin=0;failure="";rendering=worldRendering=false;armed=true;
    }

    /** Observe both render boundaries so focus loss is retained even if a later command refocuses. */
    private static void observe() {
        if(!armed)return;
        if(!failure.isEmpty())return;
        var c=Minecraft.getInstance();var camera=c.getCameraEntity();
        if(!c.isWindowActive())failure="window focus lost";
        else if(c.screen!=null||c.level!=world||camera==null||camera!=cameraOwner)failure="world/GUI/camera ownership changed";
        else if(Iris.isFallback()||Iris.getCurrentPack().orElse(null)!=pack
                ||RuntimeMeasurement.reloadCount()!=reloads||OptimizationConfig.generation()!=generation)
            failure="pack/reload/optimization ownership changed";
        else if(c.getWindow().getWidth()!=width||c.getWindow().getHeight()!=height
                ||c.getMainRenderTarget().width!=targetWidth||c.getMainRenderTarget().height!=targetHeight
                ||c.options.renderDistance().get()!=renderDistance||c.options.fov().get()!=fov
                ||c.getWindow().isFullscreen()!=fullscreen||c.options.enableVsync().get()!=vsync
                ||c.options.framerateLimit().get()!=cap||c.getWindow().getFramerateLimit()!=windowCap)
            failure="presentation/pacing changed";
        else if(camera.getX()!=x||camera.getY()!=y||camera.getZ()!=z
                ||camera.getYRot()!=yaw||camera.getXRot()!=pitch)failure="fixed camera changed";
    }

    public static void beginRender() {
        if(!armed)return;
        if(rendering&&failure.isEmpty())failure="nested render boundary";
        observe();boundaries++;rendering=true;worldFramesAtBegin=worldFrames;
    }
    public static void endRender() {
        if(!armed)return;
        observe();boundaries++;
        if(!rendering&&failure.isEmpty())failure="unmatched render return";
        if((worldRendering||worldFrames!=worldFramesAtBegin+1)&&failure.isEmpty())
            failure="render did not contain exactly one completed world render";
        if(rendering)frames++;
        rendering=false;
    }
    public static void beginWorldRender() {
        if(!armed)return;
        if((!rendering||worldRendering)&&failure.isEmpty())failure="unmatched or nested world render";
        worldRendering=true;
    }
    public static void endWorldRender() {
        if(!armed)return;
        if(!worldRendering&&failure.isEmpty())failure="unmatched world render return";
        if(worldRendering)worldFrames++;
        worldRendering=false;
    }
    static void disarm() {
        if(armed){observe();if((rendering||worldRendering)&&failure.isEmpty())failure="unfinished render at timing stop";}
        armed=false;
    }
    static Map<String,Object> status() {
        return Map.of("armed",armed,"render_boundary_observations",boundaries,"completed_render_frames",frames,
                "completed_world_renders",worldFrames,"valid",frames>0&&worldFrames==frames&&failure.isEmpty(),"failure",failure,
                "scope","render-boundary focus/world/GUI/ownership, pack/reload/flag generation, viewport/target/pacing/distance/FOV/camera; one world render per frame; no per-frame GL queries or full shader-option comparison");
    }
}

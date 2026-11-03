package com.pp.shaderlod.optimization.vrs;

import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.ShaderLodConfig;
import java.util.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.irisshaders.iris.targets.RenderTargets;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

/** Experimental 2x2 BSL bloom prefilter only. Native shader options/AA are unchanged. */
public final class BloomVrsRuntime {
    private static final LwjglVrsDriver DRIVER = new LwjglVrsDriver();
    private static final NvBloomVrsBackend BACKEND = new NvBloomVrsBackend(DRIVER);
    private static int lastWidth,lastHeight;
    private static String last = "default OFF";
    private static long drawAttempts,eligibleDraws,qualifiedDraws;
    private BloomVrsRuntime() {}
    public static boolean requested() {
        return ShaderLodConfig.SPEC.isLoaded() && ShaderLodConfig.EXPERIMENTAL_BLOOM_VRS.get();
    }
    private static boolean eligible() {
        var client=Minecraft.getInstance();
        return requested() && ShaderLodConfig.ENABLED.get() && ShaderLodConfig.PRESET.get()==LodPreset.PERFORMANCE
                && client.level!=null && client.level.dimension()==Level.OVERWORLD && !Iris.isFallback()
                && !ShadowRenderingState.areShadowsCurrentlyBeingRendered()
                && !com.pp.shaderlod.tuning.AutoTuner.active()
                && com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.stackMatches(
                        com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.loadedStack(),false)
                && !net.neoforged.fml.ModList.get().isLoaded("distanthorizons")
                && !net.neoforged.fml.ModList.get().isLoaded("voxy");
    }
    public static void frame() {
        if (!requested()) { BACKEND.release(); return; }
        var window=Minecraft.getInstance().getWindow();
        int w=window.getWidth(),h=window.getHeight();
        if (lastWidth!=w || lastHeight!=h) { BACKEND.release();lastWidth=w;lastHeight=h; }
        if (!eligible()) BACKEND.release();
    }
    public static NvBloomVrsBackend.Scope beforeDraw(Object renderer,RenderTargets targets) {
        drawAttempts++;
        if (!eligible() || !DRIVER.capabilities().supported()) { last="native: hardware/context/profile unavailable";return null; }
        eligibleDraws++;
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (!BloomVrsPrograms.qualified(renderer,targets,program)) { last="native: bound bloom program qualification refused";return null; }
        qualifiedDraws++;
        try (var stack=MemoryStack.stackPush()) {
            int fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            if (fbo==0 || GL11.glIsEnabled(GL11.GL_DEPTH_TEST) || GL11.glIsEnabled(GL11.GL_STENCIL_TEST)
                    || GL11.glIsEnabled(GL40.GL_SAMPLE_SHADING) || GL11.glIsEnabled(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE)
                    || GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD) || GL11.glGetInteger(GL13.GL_SAMPLES)!=0
                    || GL30.glIsEnabledi(GL11.GL_BLEND,0)) { last="native: incompatible bloom raster state";return null; }
            var viewport=stack.mallocInt(4);GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
            var target=targets.get(1);
            if (target==null || viewport.get(0)!=0 || viewport.get(1)!=0
                    || viewport.get(2)!=targets.getCurrentWidth() || viewport.get(3)!=targets.getCurrentHeight()) return null;
            int draw=GL11.glGetInteger(GL20.GL_DRAW_BUFFER0);
            if (draw<GL30.GL_COLOR_ATTACHMENT0 || draw>=GL30.GL_COLOR_ATTACHMENT0+GL11.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS)) return null;
            // Query once per admission; no mutable FBO state is cached across draws.
            int maxDrawBuffers=GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);
            for(int i=1;i<maxDrawBuffers;i++)
                if(GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+i)!=GL11.GL_NONE)return null;
            if (GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,draw,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)!=GL11.GL_TEXTURE) return null;
            int texture=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,draw,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            if (texture!=target.getMainTexture() && texture!=target.getAltTexture()) return null;
            var result=BACKEND.open(true,fbo,viewport.get(2),viewport.get(3));
            if(result!=null)last="active experimental BSL composite4 bloom-only 2x2; image-quality tradeoff";
            return result;
        }
    }
    public static void invalidate() { BloomVrsPrograms.invalidate();BACKEND.release();last="invalidated/native"; }
    public static void releaseImage() { BACKEND.release(); }
    public static Map<String,Object> status() {
        var caps=DRIVER.capabilities();
        var out=new LinkedHashMap<String,Object>();
        out.put("experimental",true);out.put("requested",requested());out.put("hardware_available",caps.supported());
        out.put("missing",caps.missing());out.put("callable_functions",caps.functions());
        out.put("OpenGL45",caps.openGl45());out.put("GL_NV_shading_rate_image",caps.shadingRateImage());
        out.put("GL_NV_framebuffer_mixed_samples",caps.framebufferMixedSamples());
        out.put("renderer",GL11.glGetString(GL11.GL_RENDERER));out.put("gl_version",GL11.glGetString(GL11.GL_VERSION));
        out.put("scope","BSL10.1.8 native384, Overworld, pinned minimal stack, composite4 bloom prefilter only, 2x2");
        out.put("quality","Experimental lower bloom shading density; native AA/options preserved; not image-equivalent.");
        out.put("draw_attempts",drawAttempts);out.put("eligible_draws",eligibleDraws);out.put("qualified_draws",qualifiedDraws);
        out.put("programs",BloomVrsPrograms.status());out.put("backend",BACKEND.status());out.put("driver",DRIVER.counters());out.put("last",last);
        return Collections.unmodifiableMap(out);
    }
    public static String setRequested(boolean enabled) {
        if (!ShaderLodConfig.SPEC.isLoaded()) throw new IllegalStateException("Configuration not loaded");
        if (enabled && !DRIVER.capabilities().supported())
            throw new IllegalStateException("Experimental bloom VRS unavailable: "+DRIVER.capabilities().missing());
        boolean prior=ShaderLodConfig.EXPERIMENTAL_BLOOM_VRS.get();
        try {
            ShaderLodConfig.EXPERIMENTAL_BLOOM_VRS.set(enabled);invalidate();
            if(enabled){Iris.reload();if(Iris.isFallback())throw new IllegalStateException("Iris rejected experimental Apply");}
            ShaderLodConfig.SPEC.save();
            return "Experimental bloom VRS requested="+enabled+"; hardware="+DRIVER.capabilities().supported()
                    +"; "+BloomVrsPrograms.status()+"; 2x2 bloom quality tradeoff; /shaderlod experimental bloom_vrs status";
        } catch(Exception | LinkageError failure) {
            ShaderLodConfig.EXPERIMENTAL_BLOOM_VRS.set(prior);invalidate();
            try { Iris.reload();ShaderLodConfig.SPEC.save(); } catch(Exception | LinkageError rollback) { failure.addSuppressed(rollback); }
            throw new IllegalStateException("Bloom VRS change failed; previous request restored",failure);
        }
    }
}

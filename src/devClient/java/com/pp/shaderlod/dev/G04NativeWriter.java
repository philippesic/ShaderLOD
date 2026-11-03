package com.pp.shaderlod.dev;

import com.google.gson.JsonObject;
import com.pp.shaderlod.dev.timing.CloudSamplerStateProbe;
import java.nio.file.Files;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;

/** Private G04 diagnostic only. Two bounded receipts; getters only at the native seam. */
public final class G04NativeWriter {
    private G04NativeWriter() {}
    private static final boolean ENABLED = Boolean.getBoolean("shaderlod.dev.fragmentWorkProbe")
            && Boolean.getBoolean("shaderlod.dev.rasterFragmentSource");
    private static final String ARCHIVE = "300ecdbed317feac1109585962fe8f9943e97481e84e0387d10aca3a595d338c";
    private static State pending;
    private static final State[] completed = new State[2];
    private static String refusal = "no current-frame native return receipt";
    private static int failedFrame = Integer.MIN_VALUE;

    // Object references, not identityHashCode or GL names, are the lifetime binding.
    record State(Object pipeline, Object pack, Object program, Object target, Object world,
                 Object capabilities, Thread thread, long context, String archive, String source,
                 int shader, int handle, int frame, float time, int main, int alt,
                 int framebuffer, int route, int texture, int bank) {
        boolean owner(State s) {
            return pipeline == s.pipeline && pack == s.pack && program == s.program && target == s.target
                && world == s.world && capabilities == s.capabilities && thread == s.thread && context == s.context
                && archive.equals(s.archive) && source.equals(s.source) && shader == s.shader && handle == s.handle
                && frame == s.frame && Float.compare(time, s.time) == 0 && main == s.main && alt == s.alt;
        }
    }

    public static void before() {
        if (!ENABLED) return;
        pending = null;
        var p = Iris.getPipelineManager().getPipelineNullable();
        if (!(p instanceof IrisRenderingPipeline iris) || iris.getSodiumPrograms() == null) return;
        var program = iris.getSodiumPrograms().getProgram(DefaultTerrainRenderPasses.TRANSLUCENT);
        if (program == null || GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != program.handle()) return;
        try {
            pending = snapshot(true);
        } catch (Exception failure) { fail(failure); }
    }

    public static void returned() {
        if (!ENABLED || pending == null) return;
        State before = pending; pending = null;
        try {
            State after = snapshot(true);
            if (!before.owner(after) || before.framebuffer != after.framebuffer || before.route != after.route || before.texture != after.texture || before.bank != after.bank) throw new IllegalStateException("native call changed owner/source/frame/routing");
            State old = completed[after.bank];
            if (old != null && old.frame == after.frame && !old.owner(after))
                throw new IllegalStateException("ambiguous same-frame native writer");
            completed[after.bank] = after;
        } catch (Exception failure) { fail(failure); }
    }

    private static void fail(Exception failure) {
        refusal = failure.toString(); failedFrame = SystemTimeUniforms.COUNTER.getAsInt();
        completed[0] = completed[1] = null;
    }

    private static State snapshot(boolean draw) throws Exception {
        var pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (!(pipeline instanceof IrisRenderingPipeline iris) || Iris.isFallback()
                || net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()
                || !Iris.getShaderPackOptionQueue().isEmpty()) throw new IllegalStateException("unsettled native owner");
        if (Minecraft.getInstance().level == null || org.lwjgl.glfw.GLFW.glfwGetCurrentContext() == 0)
            throw new IllegalStateException("no live world/context");
        var pack = Iris.getCurrentPack().orElseThrow();
        String name = Iris.getCurrentPackName();
        if (!name.matches("[A-Za-z0-9_.-]+\\.zip")) throw new IllegalStateException("unsafe observer archive");
        var file = Iris.getShaderpacksDirectory().resolve(name);
        if (Files.isSymbolicLink(file) || !ARCHIVE.equals(LoadedArtifactEvidence.fileSha256(file)))
            throw new IllegalStateException("selected archive is not the validated G04 observer");
        var program = iris.getSodiumPrograms().getProgram(DefaultTerrainRenderPasses.TRANSLUCENT);
        if (program == null || (draw && GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != program.handle()))
            throw new IllegalStateException("not the actual named translucent Program");
        var source = CloudSamplerStateProbe.g04FragmentSource(program.handle());
        if (!source.has("g04_class_encoding") || !source.get("g04_class_encoding").getAsBoolean())
            throw new IllegalStateException("attached output1 G04 class source unavailable or mismatched: " + source);
        var field = IrisRenderingPipeline.class.getDeclaredField("renderTargets");field.setAccessible(true);
        var target = ((RenderTargets) field.get(iris)).get(8);
        if (target == null) throw new IllegalStateException("RT8 absent");
        int main = target.getMainTexture(), alt = target.getAltTexture();
        if (main <= 0 || alt <= 0 || main == alt) throw new IllegalStateException("ambiguous RT8 banks");
        int fbo = 0, route = 0, texture = 0, bank = -1;
        if (draw) {
            fbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            route = GL11.glGetInteger(GL20.GL_DRAW_BUFFER1);
            if (fbo == 0 || route < GL30.GL_COLOR_ATTACHMENT0 || route >= GL30.GL_COLOR_ATTACHMENT0 + GL11.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS))
                throw new IllegalStateException("output1 does not route to a color FBO attachment");
            int type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, route, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            if (type != GL11.GL_TEXTURE || GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, route, GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL) != 0
                    || GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, route, GL32.GL_FRAMEBUFFER_ATTACHMENT_LAYERED) != 0
                    || GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, route, GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE) != 0)
                throw new IllegalStateException("output1 requires a nonlayered level0 texture");
            texture = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, route, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            bank = routeBank(main, alt, texture);
            if (bank < 0) throw new IllegalStateException("output1 attachment is not a unique RT8 bank");
            int count = GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);
            for (int i = 0; i < count; i++) if (i != 1 && GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + i) == route)
                throw new IllegalStateException("ambiguous duplicate draw-buffer route");
        }
        return new State(pipeline, pack, program, target, Minecraft.getInstance().level, GL.getCapabilities(), Thread.currentThread(),
            org.lwjgl.glfw.GLFW.glfwGetCurrentContext(), name + ":" + ARCHIVE, source.get("sha256").getAsString(),
            source.get("fragment_shader").getAsInt(), program.handle(), SystemTimeUniforms.COUNTER.getAsInt(),
            SystemTimeUniforms.TIMER.getFrameTimeCounter(), main, alt, fbo, route, texture, bank);
    }

    static int routeBank(int main, int alt, int texture) {
        return main > 0 && alt > 0 && main != alt ? (texture == main ? 0 : texture == alt ? 1 : -1) : -1;
    }

    static JsonObject join(String digest, int main, int alt, int frame) throws Exception {
        if (!ENABLED || !ARCHIVE.equals(digest)) throw new IllegalStateException("G04 writer opt-in/archive mismatch");
        State current = snapshot(false);
        if (current.frame != frame || current.main != main || current.alt != alt || pending != null || failedFrame == frame)
            throw new IllegalStateException("G04 current read epoch refused: " + refusal);
        var result = new JsonObject();var receipts = new com.google.gson.JsonArray();
        for (State receipt : completed) {
            if (receipt == null || !receipt.owner(current)) continue;
            var row = new JsonObject();row.addProperty("bank", receipt.bank == 0 ? "main" : "alt");
            row.addProperty("texture", receipt.texture);row.addProperty("draw_framebuffer", receipt.framebuffer);
            row.addProperty("output_index", 1);row.addProperty("gl_draw_buffer1", receipt.route);row.addProperty("texture_level", 0);
            row.addProperty("native_call_returned", true);row.addProperty("frame_counter", receipt.frame);
            row.addProperty("frame_time", receipt.time);row.addProperty("named_translucent_program", receipt.handle);
            row.addProperty("attached_fragment_sha256", receipt.source);row.addProperty("archive", receipt.archive);
            row.addProperty("attached_fragment_shader", receipt.shader);
            row.addProperty("pipeline_identity", System.identityHashCode(receipt.pipeline));
            row.addProperty("pack_identity", System.identityHashCode(receipt.pack));
            row.addProperty("program_object_identity", System.identityHashCode(receipt.program));
            row.addProperty("rt8_target_identity", System.identityHashCode(receipt.target));
            row.addProperty("gl_context", receipt.context);
            row.addProperty("render_thread", receipt.thread.getName());
            row.addProperty("owner_binding", "Retained object reference equality plus current source/archive/frame/context; numeric identities alone are not generation proof");
            receipts.add(row);
        }
        if (receipts.isEmpty()) throw new IllegalStateException("G04 no same-owner/source/frame/RT8 native return receipt: " + refusal);
        result.add("receipts", receipts);result.addProperty("scope", "Private diagnostic native call return and RT8 identity join; no GPU completion, pixel provenance, physical work or performance proof. Banks without a receipt remain unjoined.");
        return result;
    }
}

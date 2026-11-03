package com.pp.shaderlod.optimization.reimaginedspatial;

import com.mojang.blaze3d.platform.GlStateManager;
import net.irisshaders.iris.gl.IrisRenderSystem;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import java.util.Arrays;

/** Reimagined-only current-SOLID inputs and coefficients. No Iris target aliases. */
public final class ReimaginedSpatialBank implements AutoCloseable {
    public final int width, height;
    private final int[] input = new int[5], coefficient = new int[2];
    private int inputFbo, coefficientFbo, resolveFbo, copyFbo, color, colorFormat, scene;
    private boolean closed;

    public static boolean supported(int w, int h) {
        return GL.getCapabilities().OpenGL33 && w >= 2 && h >= 2 && (long) w * h <= 16777216 &&
            GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS) >= 8 &&
            GL11.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS) >= 8 &&
            GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS) >= 16 &&
            w <= GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE) && h <= GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
    }
    public ReimaginedSpatialBank(int w, int h) {
        if (!supported(w, h)) throw new IllegalStateException("Reimagined private MRT8/GL33/size unavailable");
        width = w; height = h;
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            inputFbo = GL30.glGenFramebuffers(); coefficientFbo = GL30.glGenFramebuffers();
            resolveFbo = GL30.glGenFramebuffers(); copyFbo = GL30.glGenFramebuffers();
            for (int i = 0; i < 5; i++) input[i] = texture(GL30.GL_RGBA32F);
            for (int i = 0; i < 2; i++) coefficient[i] = texture(GL30.GL_RGBA32F);
            attach(inputFbo, input); attach(coefficientFbo, coefficient);
        } catch (RuntimeException | Error failure) { close(); throw failure; }
        finally { GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw); GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound); GlStateManager._activeTexture(active); }
    }
    private int texture(int format) {
        int t = GL11.glGenTextures();
        int unpack = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, t);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, width, height, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
            return t;
        } catch (RuntimeException | Error failure) { GL11.glDeleteTextures(t); throw failure; }
        finally { GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpack); }
    }
    private static void attach(int fbo, int[] textures) {
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);
        int[] outputs = new int[textures.length];
        for (int i = 0; i < textures.length; i++) {
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + i, GL11.GL_TEXTURE_2D, textures[i], 0);
            outputs[i] = GL30.GL_COLOR_ATTACHMENT0 + i;
        }
        GL20.glDrawBuffers(outputs);
        if (GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Reimagined private framebuffer incomplete");
    }
    public int input(int i) { return closed ? 0 : input[i]; }
    public int diffuse() { return closed ? 0 : coefficient[0]; }
    public int highlight() { return closed ? 0 : coefficient[1]; }
    public int scene() { return closed ? 0 : scene; }
    public int color() { return closed ? 0 : color; }
    public int producerFbo() { return coefficientFbo; }
    public int resolverFbo() { return resolveFbo; }
    public void clearFrame() {
        if (closed) throw new IllegalStateException("Reimagined private bank closed");
        try (var state = new ReimaginedSpatialState()) {
            state.fullscreen();
            try (var stack = MemoryStack.stackPush()) {
                var value = stack.floats(0, 0, 0, -1);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, inputFbo);
                // Input3 owns the frame/depth/material validity tag. The pinned
                // SpatialInput predicate requires its current-frame tag before any
                // input or payload can contribute. A qualified native export writes
                // all five MRT fields together; an excluded fragment writes tag -1.
                // Thus untouched payload fields may retain arbitrary old contents,
                // including after allocation, resize or frame-counter wrap. Keep the
                // full tag clear: geometry does not cover every screen pixel.
                GL30.glClearBufferfv(GL11.GL_COLOR, 3, value);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, coefficientFbo);
                for (int i = 0; i < 2; i++) GL30.glClearBufferfv(GL11.GL_COLOR, i, value);
            }
        }
        scene = 0;
    }
    /** Scene source is latched from the native SOLID color attachment, not guessed bank side. */
    public boolean captureScene(int texture) {
        if (closed || texture <= 0 || Arrays.stream(input).anyMatch(x -> x == texture) || Arrays.stream(coefficient).anyMatch(x -> x == texture)) return false;
        if (scene > 0 && scene != texture) return false;
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            int f = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
            // Exact native Reimagined packed HDR format; original albedo is a separate FP32 export. No float narrowing or remap.
            if (w != width || h != height || (f != GL30.GL_R11F_G11F_B10F)) return false;
            if (color == 0 || colorFormat != f) {
                if (color != 0) GL11.glDeleteTextures(color);
                color = texture(f); colorFormat = f; attach(resolveFbo, new int[]{color});
            }
            attach(copyFbo, new int[]{texture}); scene = texture; return true;
        } finally { GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw); GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound); GlStateManager._activeTexture(active); }
    }
    public void copyResolved() {
        if (scene <= 0 || color <= 0) throw new IllegalStateException("Reimagined private scene not owned");
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, resolveFbo);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copyFbo);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }
    public Lease exportLease(int program) { return new Lease(program); }
    public final class Lease implements AutoCloseable {
        private final int fbo, program;
        private final int[] buffers;
        private boolean attached;
        private Lease(int p) {
            program = p; fbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            buffers = new int[GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS)];
            for (int i = 0; i < buffers.length; i++) buffers[i] = GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + i);
        }
        public boolean attach() {
            if (closed || fbo <= 0 || buffers.length < 8 || GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != program ||
                !GL11.glIsEnabled(GL11.GL_DEPTH_TEST) || !GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) ||
                GL11.glGetInteger(GL11.GL_DEPTH_FUNC) != GL11.GL_LEQUAL || GL11.glIsEnabled(GL11.GL_SCISSOR_TEST) ||
                GL11.glIsEnabled(GL11.GL_STENCIL_TEST) || GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD)) return false;
            int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            if (!Arrays.equals(viewport, new int[]{0, 0, width, height})) return false;
            for (int i = 0; i < buffers.length; i++) {
                if (i < 3 && buffers[i] != GL30.GL_COLOR_ATTACHMENT0 + i || i >= 3 && buffers[i] != GL11.GL_NONE || GL30.glIsEnabledi(GL11.GL_BLEND, i)) return false;
                try (var stack = MemoryStack.stackPush()) {
                    var mask = stack.malloc(4); GL30.glGetBooleani_v(GL11.GL_COLOR_WRITEMASK, i, mask);
                    for (int j = 0; j < 4; j++) if (mask.get(j) == 0) return false;
                }
            }
            for (int i = 3; i < 8; i++) if (GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + i, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) != GL11.GL_NONE) return false;
            int sceneTexture = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            if (!captureScene(sceneTexture)) return false;
            attached = true;
            for (int i = 0; i < 5; i++) GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT3 + i, GL11.GL_TEXTURE_2D, input[i], 0);
            GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1, GL30.GL_COLOR_ATTACHMENT2, GL30.GL_COLOR_ATTACHMENT3, GL30.GL_COLOR_ATTACHMENT4, GL30.GL_COLOR_ATTACHMENT5, GL30.GL_COLOR_ATTACHMENT6, GL30.GL_COLOR_ATTACHMENT7});
            return GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
        }
        @Override public void close() {
            if (!attached) return;
            int current = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            try {
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);
                for (int i = 3; i < 8; i++) GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + i, GL11.GL_TEXTURE_2D, 0, 0);
                GL20.glDrawBuffers(buffers);
            } finally { GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, current); attached = false; }
        }
    }
    @Override public void close() {
        if (closed) return; closed = true;
        for (int t : input) if (t > 0) GL11.glDeleteTextures(t);
        for (int t : coefficient) if (t > 0) GL11.glDeleteTextures(t);
        if (color > 0) GL11.glDeleteTextures(color);
        for (int f : new int[]{inputFbo, coefficientFbo, resolveFbo, copyFbo}) if (f > 0) GL30.glDeleteFramebuffers(f);
        scene = 0;
    }
}

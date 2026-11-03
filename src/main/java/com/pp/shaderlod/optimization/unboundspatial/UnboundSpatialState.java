package com.pp.shaderlod.optimization.unboundspatial;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.ProgramManager;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

/** Scope around two private fullscreen draws at the completed native SOLID boundary. */
public final class UnboundSpatialState implements AutoCloseable {
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING), array = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    private final int[] viewport = new int[4], scissor = new int[4], textures, samplers;
    private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), scissors = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
    private static final int[] PRIVATE_DISABLED_CAPS={GL13.GL_SAMPLE_ALPHA_TO_COVERAGE,GL13.GL_SAMPLE_ALPHA_TO_ONE,GL13.GL_SAMPLE_COVERAGE,GL32.GL_SAMPLE_MASK,GL30.GL_RASTERIZER_DISCARD};
    private final boolean[] privateCaps=new boolean[PRIVATE_DISABLED_CAPS.length];
    private final boolean[] blends;
    private final byte[][] masks;
    public UnboundSpatialState() {
        for(int i=0;i<privateCaps.length;i++)privateCaps[i]=GL11.glIsEnabled(PRIVATE_DISABLED_CAPS[i]);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport); GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
        int units = GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS), outputs = GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);
        textures = new int[units]; samplers = new int[units]; blends = new boolean[outputs]; masks = new byte[outputs][4];
        try {
            for (int i = 0; i < units; i++) { GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i); textures[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D); samplers[i] = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING); }
            try (var stack = MemoryStack.stackPush()) { var mask = stack.malloc(4);
                for (int i = 0; i < outputs; i++) { blends[i] = GL30.glIsEnabledi(GL11.GL_BLEND, i); GL30.glGetBooleani_v(GL11.GL_COLOR_WRITEMASK, i, mask); for (int j = 0; j < 4; j++) masks[i][j] = mask.get(j); }
            }
        } finally { GlStateManager._activeTexture(active); }
    }
    public void fullscreen() {
        for(int cap:PRIVATE_DISABLED_CAPS)GL11.glDisable(cap);
        GlStateManager._disableDepthTest(); GlStateManager._disableCull(); GlStateManager._disableScissorTest(); GL11.glDisable(GL11.GL_STENCIL_TEST);
        for (int i = 0; i < masks.length; i++) { GL30.glDisablei(GL11.GL_BLEND, i); GL30.glColorMaski(i, true, true, true, true); }
    }
    @Override public void close() {
        // Program.use owns the Iris active caches; invalidate them before restoring the native state.
        ProgramSamplers.clearActiveSamplers(); ProgramUniforms.clearActiveUniforms();
        for(int i=0;i<privateCaps.length;i++){if(privateCaps[i])GL11.glEnable(PRIVATE_DISABLED_CAPS[i]);else GL11.glDisable(PRIVATE_DISABLED_CAPS[i]);}
        for (int i = 0; i < textures.length; i++) { IrisRenderSystem.bindTextureToUnit(GL11.GL_TEXTURE_2D, i, textures[i]); IrisRenderSystem.bindSamplerToUnit(i, samplers[i]); }
        GlStateManager._activeTexture(active);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw); GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        GlStateManager._scissorBox(scissor[0], scissor[1], scissor[2], scissor[3]);
        if (depth) GlStateManager._enableDepthTest(); else GlStateManager._disableDepthTest();
        if (cull) GlStateManager._enableCull(); else GlStateManager._disableCull();
        if (scissors) GlStateManager._enableScissorTest(); else GlStateManager._disableScissorTest();
        if (stencil) GL11.glEnable(GL11.GL_STENCIL_TEST); else GL11.glDisable(GL11.GL_STENCIL_TEST);
        for (int i = 0; i < masks.length; i++) { if (blends[i]) GL30.glEnablei(GL11.GL_BLEND, i); else GL30.glDisablei(GL11.GL_BLEND, i); GL30.glColorMaski(i, masks[i][0] != 0, masks[i][1] != 0, masks[i][2] != 0, masks[i][3] != 0); }
        GL30.glBindVertexArray(vao); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, array); ProgramManager.glUseProgram(program);
    }
}

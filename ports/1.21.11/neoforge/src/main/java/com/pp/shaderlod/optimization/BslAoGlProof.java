package com.pp.shaderlod.optimization;

import net.irisshaders.iris.targets.RenderTargets;
import org.lwjgl.opengl.*;
import java.util.Arrays;

/** Real currently-bound producer/consumer inspection. No readback, allocation or fake qualification. */
public final class BslAoGlProof {
    public record Binding(int texture,int sampler,int width,int height,int format,int base,int max,
                          int min,int mag,int wrapS,int wrapT,int compare,float lodBias,float anisotropy) {}
    public record Producer(BslAoEpoch.Output output,Binding depth,Binding noise,int[] viewport,boolean qualified) {}
    private BslAoGlProof() {}
    public static Binding binding(int program,String uniform) {
        int location=GL20.glGetUniformLocation(program,uniform);if(location<0)return null;
        int unit=GL20.glGetUniformi(program,location);
        if(unit<0||unit>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))return null;
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
            int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);if(texture<=0)return null;
            int sampler=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            return new Binding(texture,sampler,GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),
                    GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT),
                    GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT),
                    GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL),
                    GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MAX_LEVEL),
                    parameter(sampler,GL11.GL_TEXTURE_MIN_FILTER),parameter(sampler,GL11.GL_TEXTURE_MAG_FILTER),
                    parameter(sampler,GL11.GL_TEXTURE_WRAP_S),parameter(sampler,GL11.GL_TEXTURE_WRAP_T),
                    parameter(sampler,GL14.GL_TEXTURE_COMPARE_MODE),parameterFloat(sampler,GL14.GL_TEXTURE_LOD_BIAS),anisotropy(sampler));
        } finally {GL13.glActiveTexture(active);}
    }
    private static int parameter(int sampler,int parameter) {
        return sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,parameter):GL33.glGetSamplerParameteri(sampler,parameter);
    }
    private static float parameterFloat(int sampler,int parameter) {
        return sampler==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,parameter):GL33.glGetSamplerParameterf(sampler,parameter);
    }
    private static float anisotropy(int sampler) {
        var caps=GL.getCapabilities();return caps.GL_EXT_texture_filter_anisotropic||caps.GL_ARB_texture_filter_anisotropic?
            parameterFloat(sampler,EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT):1.0f;
    }
    private static boolean baseSampler(Binding b) {
        return b!=null&&b.compare()==GL11.GL_NONE&&b.base()==0&&
                (b.min()==GL11.GL_NEAREST||b.min()==GL11.GL_LINEAR)&&
                (b.mag()==GL11.GL_NEAREST||b.mag()==GL11.GL_LINEAR);
    }
    private static boolean writesAll() {int[] mask=new int[4];GL11.glGetIntegerv(GL11.GL_COLOR_WRITEMASK,mask);return Arrays.equals(mask,new int[]{1,1,1,1});}
    private static int imageFormat(int texture) {int previous=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);try {GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);return GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT);}finally{GL11.glBindTexture(GL11.GL_TEXTURE_2D,previous);}}
    public static Producer producer(RenderTargets targets,int program) {
        int w=targets.getCurrentWidth(),h=targets.getCurrentHeight();
        var target=targets.get(10);int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        int fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int texture=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        var depth=binding(program,"depthtex0");var noise=binding(program,"noisetex");
        boolean valid=target!=null&&w>=2&&h>=2&&w<=16384&&h<=16384&&
                target.getWidth()==w&&target.getHeight()==h&&target.getInternalFormat().name().equals("RG32F")&&
                (texture==target.getMainTexture()||texture==target.getAltTexture())&&texture>0&&fbo>0&&
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==program&&GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==GL11.GL_TRUE&&
                GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE&&
                GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)==GL11.GL_TEXTURE&&
                GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL)==0&&
                writesAll()&&GL11.glGetInteger(GL20.GL_DRAW_BUFFER0)==GL30.GL_COLOR_ATTACHMENT0&&Arrays.equals(viewport,new int[]{0,0,w/2,h/2})&&
                !GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)&&!GL11.glIsEnabled(GL11.GL_DEPTH_TEST)&&!GL11.glIsEnabled(GL11.GL_BLEND)&&
                !GL11.glIsEnabled(GL11.GL_STENCIL_TEST)&&!GL11.glIsEnabled(GL11.GL_CULL_FACE)&&!GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD)&&
                baseSampler(depth)&&depth.texture()==((net.irisshaders.iris.mixinterface.GpuTextureInterface)targets.getDepthTexture()).iris$getGlId()&&depth.width()==w&&depth.height()==h&&
                baseSampler(noise)&&noise.width()==512&&noise.height()==512;
        return new Producer(new BslAoEpoch.Output(texture,fbo,w,h),depth,noise,viewport,valid);
    }
    public static boolean nativeProducer(RenderTargets targets,int program,int expectedTexture) {
        var target=targets.get(4);int w=targets.getCurrentWidth(),h=targets.getCurrentHeight();
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        var depth=binding(program,"depthtex0");var noise=binding(program,"noisetex");
        int texture=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        return target!=null&&(target.getInternalFormat().name().equals("RGBA")||target.getInternalFormat().name().equals("RGBA8"))&&texture==expectedTexture&&imageFormat(texture)==GL11.GL_RGBA8&&
            (texture==target.getMainTexture()||texture==target.getAltTexture())&&target.getWidth()==w&&target.getHeight()==h&&
            GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==program&&GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==GL11.GL_TRUE&&
            GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE&&
            GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)==GL11.GL_TEXTURE&&
            GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL)==0&&
            writesAll()&&GL11.glGetInteger(GL20.GL_DRAW_BUFFER0)==GL30.GL_COLOR_ATTACHMENT0&&Arrays.equals(viewport,new int[]{0,0,w,h})&&
            !GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)&&!GL11.glIsEnabled(GL11.GL_DEPTH_TEST)&&!GL11.glIsEnabled(GL11.GL_BLEND)&&
            !GL11.glIsEnabled(GL11.GL_STENCIL_TEST)&&!GL11.glIsEnabled(GL11.GL_CULL_FACE)&&!GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD)&&
            baseSampler(depth)&&depth.texture()==((net.irisshaders.iris.mixinterface.GpuTextureInterface)targets.getDepthTexture()).iris$getGlId()&&depth.width()==w&&depth.height()==h&&
            baseSampler(noise)&&noise.width()==512&&noise.height()==512;
    }
    /** Pinned native RGBA noninteger RenderTarget has texture LINEAR/LINEAR,
     * CLAMP_TO_EDGE; IrisSamplers supplies no sampler object for colortex4.
     * Base/max and comparison are newly-created native GL texture defaults. */
    public static boolean nativeConsumer(RenderTargets targets,int program,int writtenTexture) {
        var b=binding(program,"colortex4");var target=targets.get(4);
        return nativeFilter(b)&&target!=null&&b.texture()==writtenTexture&&b.format()==GL11.GL_RGBA8&&
            b.width()==target.getWidth()&&b.height()==target.getHeight()&&
            b.width()==targets.getCurrentWidth()&&b.height()==targets.getCurrentHeight();
    }
    /** Predicate is exposed for boundary-unit checks; it never synthesizes a GL binding. */
    public static boolean nativeFilter(Binding b) {
        return b!=null&&b.sampler()==0&&b.base()==0&&b.max()==1000&&b.compare()==GL11.GL_NONE&&
            b.min()==GL11.GL_LINEAR&&b.mag()==GL11.GL_LINEAR&&
            b.wrapS()==GL12.GL_CLAMP_TO_EDGE&&b.wrapT()==GL12.GL_CLAMP_TO_EDGE&&b.lodBias()==0.0f&&b.anisotropy()==1.0f;
    }
    public static boolean consumer(RenderTargets targets,int program,int completedTexture) {
        var b=binding(program,"colortex10");var target=targets.get(10);
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        return target!=null&&GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==program&&baseSampler(b)&&
                b.texture()==completedTexture&&b.format()==GL30.GL_RG32F&&
                b.width()==targets.getCurrentWidth()&&b.height()==targets.getCurrentHeight()&&
                Arrays.equals(viewport,new int[]{0,0,targets.getCurrentWidth(),targets.getCurrentHeight()});
    }
}

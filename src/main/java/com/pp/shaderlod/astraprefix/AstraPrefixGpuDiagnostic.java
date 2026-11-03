package com.pp.shaderlod.astraprefix;

import java.nio.ByteBuffer;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** One explicitly armed DEV call, never a supplier or per-frame observer.
 * Caller owns the render thread and passes exact current PBR sprite rectangles.
 * Reads the already allocated normal image through a private READ FBO; no draw,
 * sampler mutation, upload, mip generation, program change, or extra texture. */
public final class AstraPrefixGpuDiagnostic {
    public record Image(int program,int unit,int texture,int sampler,int width,int height,
        int internalFormat,int alphaBits,int swizzleA,int baseLevel,int maxLevel,
        int minFilter,int magFilter,int compareMode,int wrapS,int wrapT,
        float anisotropy,float lodBias,float minLod,float maxLod) {}
    public record Pixels(AstraPrefixCertificate.Rect rect,int maximumAlpha,String rgbaSha256) {}
    public record Result(Image image,List<Pixels> pixels) {}
    private boolean attempted;
    public Result captureOnce(int expectedProgram,int expectedNormalTexture,List<AstraPrefixCertificate.Rect> rects, JsonObject receipt) {
        if(attempted)throw new IllegalStateException("finite diagnostic already attempted");
        attempted=true;
        if(rects.size()!=3)throw new IllegalArgumentException("exactly three rectangles");
        AstraPrefixGlErrorReceipt.checkpoint(receipt,"pre-query",GL11::glGetError);
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int readFbo=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int readBuffer=GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int pbo=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int[] names={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,
                     GL11.GL_PACK_SKIP_PIXELS,GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST,
                     GL12.GL_PACK_IMAGE_HEIGHT,GL12.GL_PACK_SKIP_IMAGES};
        int[] pack=new int[names.length];for(int i=0;i<names.length;i++)pack[i]=GL11.glGetInteger(names[i]);
        int fbo=0;Throwable primary=null;
        try {
            if(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=expectedProgram||expectedProgram<=0)
                throw new IllegalStateException("not the consuming terrain program");
            int loc=GL20.glGetUniformLocation(expectedProgram,"normals");
            if(loc<0)throw new IllegalStateException("actual normals sampler absent");
            int unit=GL20.glGetUniformi(expectedProgram,loc);
            int units=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
            if(unit<0||unit>=units)throw new IllegalStateException("invalid sampler unit");
            GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
            int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int sampler=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            if(texture<=0||texture!=expectedNormalTexture)throw new IllegalStateException("normal image join mismatch");
            int w=level(GL11.GL_TEXTURE_WIDTH),h=level(GL11.GL_TEXTURE_HEIGHT);
            int format=level(GL11.GL_TEXTURE_INTERNAL_FORMAT),alpha=level(GL11.GL_TEXTURE_ALPHA_SIZE);
            int swizzle=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL33.GL_TEXTURE_SWIZZLE_A);
            int base=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL);
            int maximum=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MAX_LEVEL);
            var caps=GL.getCapabilities();
            boolean aniso=caps.GL_EXT_texture_filter_anisotropic||caps.GL_ARB_texture_filter_anisotropic;
            float anisotropy=aniso?paramf(sampler,0x84FE):1f;
            Image image=new Image(expectedProgram,unit,texture,sampler,w,h,format,alpha,swizzle,base,maximum,
                param(sampler,GL11.GL_TEXTURE_MIN_FILTER),param(sampler,GL11.GL_TEXTURE_MAG_FILTER),
                param(sampler,GL14.GL_TEXTURE_COMPARE_MODE),param(sampler,GL11.GL_TEXTURE_WRAP_S),
                param(sampler,GL11.GL_TEXTURE_WRAP_T),anisotropy,paramf(sampler,GL14.GL_TEXTURE_LOD_BIAS),
                paramf(sampler,GL12.GL_TEXTURE_MIN_LOD),paramf(sampler,GL12.GL_TEXTURE_MAX_LOD));
            receipt.add("actual_gpu_image_query",new Gson().toJsonTree(image));
            receipt.add("actual_gpu_rectangle_inputs",new Gson().toJsonTree(rects));
            receipt.addProperty("GPU_image_query_status","UNQUALIFIED_OBSERVATION");
            AstraPrefixGlErrorReceipt.checkpoint(receipt,"post-query",GL11::glGetError);
            receipt.addProperty("GPU_image_query_status","NO_RECORDED_QUERY_GL_ERROR_NO_SUPPORT_ADMISSION");
            // Reading RGBA8 is exact 8-bit storage observation. It is not filtered-value proof.
            if(format!=GL11.GL_RGBA8||alpha!=8||swizzle!=GL11.GL_ALPHA||base!=0)
                throw new IllegalStateException("unsupported actual normal image format/swizzle/base");
            for(var r:rects)if(!r.valid(w,h)||r.x1()-r.x0()+1>64||r.y1()-r.y0()+1>64)
                throw new IllegalArgumentException("small current-image rectangle required");
            fbo=GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,fbo);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,
                                        GL11.GL_TEXTURE_2D,texture,0);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            int framebufferStatus=GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER);
            receipt.addProperty("GPU_read_framebuffer_status",framebufferStatus);
            AstraPrefixGlErrorReceipt.checkpoint(receipt,"pre-read-fbo",GL11::glGetError);
            if(framebufferStatus!=GL30.GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("normal base image not framebuffer readable");
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);
            for(int name:names)GL11.glPixelStorei(name,name==GL11.GL_PACK_ALIGNMENT?1:0);
            AstraPrefixGlErrorReceipt.checkpoint(receipt,"pre-read-pack",GL11::glGetError);
            List<Pixels> pixels=new ArrayList<>(3);int ordinal=0;
            for(var r:rects) {
                int rw=r.x1()-r.x0()+1,rh=r.y1()-r.y0()+1;
                ByteBuffer bytes=BufferUtils.createByteBuffer(Math.multiplyExact(Math.multiplyExact(rw,rh),4));
                AstraPrefixGlErrorReceipt.checkpoint(receipt,"pre-read-"+ordinal,GL11::glGetError);
                GL11.glReadPixels(r.x0(),r.y0(),rw,rh,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);
                AstraPrefixGlErrorReceipt.checkpoint(receipt,"read-"+ordinal,GL11::glGetError);ordinal++;
                byte[] raw=new byte[bytes.capacity()];bytes.get(raw);
                int max=0;for(int i=3;i<raw.length;i+=4)max=Math.max(max,Byte.toUnsignedInt(raw[i]));
                pixels.add(new Pixels(r,max,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw))));
            }
            return new Result(image,List.copyOf(pixels));
        } catch (RuntimeException|Error failure) {primary=failure;throw failure;}
        catch (java.security.NoSuchAlgorithmException impossible) {var failure=new AssertionError(impossible);primary=failure;throw failure;}
        finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,readFbo);
            GL11.glReadBuffer(readBuffer);
            if(fbo!=0)GL30.glDeleteFramebuffers(fbo);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pbo);
            for(int i=0;i<names.length;i++)GL11.glPixelStorei(names[i],pack[i]);
            GL13.glActiveTexture(active);
            try{AstraPrefixGlErrorReceipt.checkpoint(receipt,"restore",GL11::glGetError);}
            catch(RuntimeException|Error restoreError){if(primary!=null)primary.addSuppressed(restoreError);else throw restoreError;}
        }
    }
    private static int level(int name) {return GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,name);}
    private static int param(int sampler,int name) {return sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,name):GL33.glGetSamplerParameteri(sampler,name);}
    private static float paramf(int sampler,int name) {return sampler==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,name):GL33.glGetSamplerParameterf(sampler,name);}
}

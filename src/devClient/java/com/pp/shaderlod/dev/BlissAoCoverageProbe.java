package com.pp.shaderlod.dev;

import java.util.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

/** Read-only depth opportunity census, called only by the two-frame untimed observer. */
final class BlissAoCoverageProbe {
    private BlissAoCoverageProbe() {}
    static Map<String,Object> capture(int program,String source,int ownedDepth) {
        // These source conditions join the census to the exact guarded native input.
        String compact=source.replaceAll("(?s)/\\*.*?\\*/|//[^\\n]*","").replaceAll("\\s+","");
        if(!compact.contains("uniformsampler2Ddepthtex1;")||!compact.contains("floatz=texture(depthtex1,texcoord).x;")
            ||!compact.contains("floatswappedDepth=z;")||compact.split("swappedDepth=",-1).length!=2
            ||!compact.contains("vec2texcoord=gl_FragCoord.xy*texelSize;"))
            throw new IllegalStateException("Exact native AO depth input required");
        int location=GL20.glGetUniformLocation(program,"depthtex1"),sizeLocation=GL20.glGetUniformLocation(program,"texelSize");
        if(location<0||sizeLocation<0)throw new IllegalStateException("Live AO depth/size uniform absent");
        int unit=GL20.glGetUniformi(program,location),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        if(unit<0||unit>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))throw new IllegalStateException("AO depth unit outside bound");
        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
        try {
            int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),sampler=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            if(texture<=0||texture!=ownedDepth||!GL11.glIsTexture(texture))throw new IllegalStateException("AO depth image differs from actual Iris no-translucents owner");
            int width=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),height=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT);
            int min=sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER):GL33.glGetSamplerParameteri(sampler,GL11.GL_TEXTURE_MIN_FILTER);
            int mag=sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER):GL33.glGetSamplerParameteri(sampler,GL11.GL_TEXTURE_MAG_FILTER);
            int compare=sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL14.GL_TEXTURE_COMPARE_MODE):GL33.glGetSamplerParameteri(sampler,GL14.GL_TEXTURE_COMPARE_MODE);
            int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
            var texel=org.lwjgl.BufferUtils.createFloatBuffer(2);GL20.glGetUniformfv(program,sizeLocation,texel);
            if(width!=2560||height!=1600||!Arrays.equals(viewport,new int[]{0,0,width,height})
                ||min!=GL11.GL_NEAREST||mag!=GL11.GL_NEAREST||compare!=GL11.GL_NONE
                ||texel.get(0)!=1.0f/width||texel.get(1)!=1.0f/height)
                throw new IllegalStateException("Pinned full-resolution nearest AO input required");
            int base=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL);
            int format=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT);
            int swizzle=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL33.GL_TEXTURE_SWIZZLE_R);
            boolean depthStencil=format==GL30.GL_DEPTH24_STENCIL8||format==GL30.GL_DEPTH32F_STENCIL8;
            int depthMode=depthStencil?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL43.GL_DEPTH_STENCIL_TEXTURE_MODE):GL11.GL_DEPTH_COMPONENT;
            if(base!=0||swizzle!=GL11.GL_RED||depthMode!=GL11.GL_DEPTH_COMPONENT
                ||!(depthStencil||Set.of(GL11.GL_DEPTH_COMPONENT,GL14.GL_DEPTH_COMPONENT16,GL14.GL_DEPTH_COMPONENT24,GL14.GL_DEPTH_COMPONENT32,GL30.GL_DEPTH_COMPONENT32F).contains(format)))
                throw new IllegalStateException("AO depth format/base/swizzle differs: format="+format+" base="+base+" swizzle="+swizzle+" depthMode="+depthMode);
            int[] keys={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS,GL12.GL_PACK_IMAGE_HEIGHT,GL12.GL_PACK_SKIP_IMAGES,GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST};
            int[] prior=new int[keys.length];for(int i=0;i<keys.length;i++)prior[i]=GL11.glGetInteger(keys[i]);
            int packBuffer=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
            var pixels=MemoryUtil.memAllocFloat(width*height);
            long sky=0,nonSky=0,nonfinite=0;
            try {
                if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new IllegalStateException("Prior AO census GL error");
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);
                for(int i=0;i<keys.length;i++)GL11.glPixelStorei(keys[i],i==0?4:0);
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,pixels);
                if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new IllegalStateException("AO census readback failed");
                for(int i=0;i<pixels.limit();i++){
                    float value=pixels.get(i);if(!Float.isFinite(value))nonfinite++;
                    if(value>=1.0f)sky++;else nonSky++;
                }
            } finally {
                for(int i=0;i<keys.length;i++)GL11.glPixelStorei(keys[i],prior[i]);
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,packBuffer);MemoryUtil.memFree(pixels);
            }
            var result=new LinkedHashMap<String,Object>();
            result.put("texture",texture);result.put("owned_depth_texture",ownedDepth);result.put("sampler",sampler);result.put("width",width);result.put("height",height);
            result.put("internal_format",format);result.put("depth_stencil_texture_mode",depthMode);result.put("red_swizzle",swizzle);
            result.put("sky_input_texels",sky);result.put("non_sky_input_texels",nonSky);result.put("nonfinite_input_texels",nonfinite);
            result.put("texel_size",List.of(texel.get(0),texel.get(1)));result.put("viewport",List.of(0,0,width,height));
            result.put("scope","Untimed post-consumer bound nearest depthtex1 texel census. Potential sky eligibility under fixed full-resolution pixel-center mapping; raster reachability and executed branches not counted. No GPU instruction/fetch or image parity claim.");
            return Map.copyOf(result);
        } finally {GL13.glActiveTexture(active);}
    }
}

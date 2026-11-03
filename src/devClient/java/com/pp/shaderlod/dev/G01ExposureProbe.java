package com.pp.shaderlod.dev;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

/** Untimed private G01 diagnostic: four-byte samples only, no full-image readback. */
final class G01ExposureProbe {
    private G01ExposureProbe() {}
    static JsonObject read(String digest)throws Exception {
        if(!Boolean.getBoolean("shaderlod.dev.g01ExposureProbe"))throw new IllegalStateException("Explicit G01 diagnostic launch required");
        var c=Minecraft.getInstance();var pipeline=Iris.getPipelineManager().getPipelineNullable();
        if(!(pipeline instanceof IrisRenderingPipeline iris)||c.level==null||Iris.isFallback()
                ||c.screen!=null||!BackgroundLaunch.qualityWindowReady(c)||!Iris.getShaderPackOptionQueue().isEmpty())
            throw new IllegalStateException("Settled focused native pipeline required");
        String name=Iris.getCurrentPackName();if(!name.matches("[A-Za-z0-9_.-]+\\.zip")||!digest.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Exact safe archive identity required");
        Path file=Iris.getShaderpacksDirectory().resolve(name);
        if(Files.isSymbolicLink(file)||!LoadedArtifactEvidence.fileSha256(file).equals(digest))throw new IllegalStateException("Archive changed");
        try(var z=new ZipFile(file.toFile())) {
            var marker=z.getEntry("SHADERLOD_G01_EXPOSURE_DIAGNOSTIC.json");
            if(marker==null||marker.getSize()>4096)throw new IllegalStateException("Untimed diagnostic archive marker absent");
            var m=JsonParser.parseString(new String(z.getInputStream(marker).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if(!m.get("diagnostic_only").getAsBoolean()||!"0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8".equals(m.get("native").getAsString()))throw new IllegalStateException("Unpinned diagnostic marker");
        }
        var pack=Iris.getCurrentPack().orElseThrow();
        String scalar=pack.getShaderPackOptions().getOptionValues().getStringValueOrDefault("SHADERLOD_G01_SCALAR_EXPOSURE");
        if(!Set.of("1","2").contains(scalar))throw new IllegalStateException("Scalar history On required");
        var f=IrisRenderingPipeline.class.getDeclaredField("renderTargets");f.setAccessible(true);var targets=(RenderTargets)f.get(iris);
        int frame=SystemTimeUniforms.COUNTER.getAsInt();var out=new JsonObject();out.addProperty("frame_counter",frame);out.addProperty("pack",name);out.addProperty("archive_sha256",digest);out.addProperty("scalar_mode",scalar);
        out.addProperty("scope","Untimed level-zero center samples of both banks: RT9 1x1 unrounded exposure transfer, RT10 independent native unrounded exposure, RT6 native and RT8 scalar R16F histories. No timing or whole-image equivalence proof.");
        var samples=new JsonArray();
        int readFbo=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),readBuffer=GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),packBuffer=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int[] keys={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS,GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST};
        int[] prior=Arrays.stream(keys).map(GL11::glGetInteger).toArray();int scratch=GL30.glGenFramebuffers();
        try(var stack=MemoryStack.stackPush()) {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);
            for(int key:keys)GL11.glPixelStorei(key,key==GL11.GL_PACK_ALIGNMENT?4:0);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,scratch);
            for(int index:new int[]{6,8,9,10}) {
                var target=targets.get(index);if(target==null)throw new IllegalStateException("Diagnostic target missing: "+index);
                for(int bank=0;bank<2;bank++) {
                    int texture=bank==0?target.getMainTexture():target.getAltTexture();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
                    int width=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),height=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT);
                    int format=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT);
                    if(width!=(index==6||index==10?c.getWindow().getWidth():1)||height!=(index==6||index==10?c.getWindow().getHeight():1)||format!=(index==6||index==8?GL30.GL_R16F:GL30.GL_R32F))throw new IllegalStateException("Wrong diagnostic allocation: target="+index+" bank="+bank+" width="+width+" height="+height+" format="+format);
                    GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                    if(GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete diagnostic read FBO");
                    var pixel=stack.mallocFloat(1);GL11.glReadPixels(width/2,height/2,1,1,GL11.GL_RED,GL11.GL_FLOAT,pixel);float value=pixel.get(0);
                    var row=new JsonObject();row.addProperty("target",index);row.addProperty("bank",bank==0?"main":"alt");row.addProperty("texture",texture);row.addProperty("width",width);row.addProperty("height",height);row.addProperty("format",format);
                    row.addProperty("value",value);row.addProperty("float_bits",Float.floatToRawIntBits(value));row.addProperty("r16f_roundtrip",Float.float16ToFloat(Float.floatToFloat16(value)));samples.add(row);
                }
            }
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,readFbo);GL11.glReadBuffer(readBuffer);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,binding);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,packBuffer);
            for(int i=0;i<keys.length;i++)GL11.glPixelStorei(keys[i],prior[i]);GL30.glDeleteFramebuffers(scratch);
        }
        if(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)!=readFbo||GL11.glGetInteger(GL11.GL_READ_BUFFER)!=readBuffer||GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)!=binding||GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING)!=packBuffer||SystemTimeUniforms.COUNTER.getAsInt()!=frame||Iris.getPipelineManager().getPipelineNullable()!=pipeline)throw new IllegalStateException("Readback context/state changed");
        for(int i=0;i<keys.length;i++)if(GL11.glGetInteger(keys[i])!=prior[i])throw new IllegalStateException("Pixel-store restoration failed");
        out.add("samples",samples);out.addProperty("state_restored",true);return out;
    }
}

package com.pp.shaderlod.dev;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

/** Bounded untimed read of the actual completed diagnostic cloud writer. */
final class G08CloudReadback {
    private G08CloudReadback() {}
    static JsonObject read(String digest)throws Exception {
        var c=Minecraft.getInstance();
        if(!G08CloudProbe.observing()||c.level==null||c.screen!=null||Iris.isFallback()
            ||!BackgroundLaunch.qualityWindowReady(c)||!Iris.getShaderPackOptionQueue().isEmpty())
            throw new IllegalStateException("Settled owned untimed cloud capture required");
        String name=Iris.getCurrentPackName();
        if(!name.matches("[A-Za-z0-9_.-]+\\.zip")||!digest.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Safe exact archive required");
        Path file=Iris.getShaderpacksDirectory().resolve(name);
        if(Files.isSymbolicLink(file)||!LoadedArtifactEvidence.fileSha256(file).equals(digest))throw new IllegalStateException("Archive differs");
        JsonObject marker;
        try(var z=new ZipFile(file.toFile())){
            var e=z.getEntry("SHADERLOD_G08_CLOUD_DIAGNOSTIC.json");
            if(e==null||e.getSize()<1||e.getSize()>8192)throw new IllegalStateException("Bounded cloud marker absent");
            marker=JsonParser.parseString(new String(z.getInputStream(e).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        if(!marker.get("diagnostic_only").getAsBoolean()||marker.get("schema").getAsInt()!=1023
            ||!marker.get("original_sha256").getAsString().equals("120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead")
            ||!marker.get("base_sha256").getAsString().equals("c4e508a6df6ebfd819ef9c5638c68c8e902ff9e10a48e36a9f7b586afd1f5159"))throw new IllegalStateException("Unpinned cloud probe");
        var before=G08CloudProbe.status();
        if(!Boolean.TRUE.equals(before.get("qualified"))||!Boolean.TRUE.equals(before.get("full_draw"))
            ||((Number)before.get("draws")).longValue()<1)throw new IllegalStateException("No completed full diagnostic writer");
        int texture=((Number)before.get("work_texture")).intValue();int[] v=(int[])before.get("viewport");
        if(texture<=0||!GL11.glIsTexture(texture)||v.length!=4||v[0]<0||v[1]<0||v[2]<1||v[3]<1||v[2]>4096||v[3]>4096)throw new IllegalStateException("Bounded writer texture/viewport required");
        int readFbo=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),readBuffer=GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),pbo=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int[] keys={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS,GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST,GL12.GL_PACK_IMAGE_HEIGHT,GL12.GL_PACK_SKIP_IMAGES};
        int[] prior=Arrays.stream(keys).map(GL11::glGetInteger).toArray();int fbo=GL30.glGenFramebuffers();
        long hitPixels=0,hits=0,depthDiff=0,outputDiff=0,nonfiniteCandidate=0,nonfiniteReference=0,diffWithoutReuse=0;long[] codes=new long[8];
        try{
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            int w=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),h=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT);
            if(w<v[0]+v[2]||h<v[1]+v[3]||GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT)!=GL30.GL_RGBA16F)throw new IllegalStateException("Cloud writer allocation differs");
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);for(int key:keys)GL11.glPixelStorei(key,key==GL11.GL_PACK_ALIGNMENT?4:0);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,fbo);GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            if(GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete cloud read framebuffer");
            var pixels=MemoryUtil.memAllocFloat(Math.multiplyExact(Math.multiplyExact(v[2],v[3]),4));
            try{
                GL11.glReadPixels(v[0],v[1],v[2],v[3],GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
                for(int i=0;i<pixels.capacity();i+=4){
                    float a=pixels.get(i),b=pixels.get(i+1),d=pixels.get(i+2),schema=pixels.get(i+3);
                    if(schema!=1023||!Float.isFinite(a)||a<0||a>1024||a!=Math.round(a)||(b!=0&&b!=1)||!Float.isFinite(d)||d<0||d>7||d!=Math.round(d))throw new IllegalStateException("Cloud encoding/header not produced across full viewport");
                    int n=(int)a,code=(int)d;codes[code]++;hits+=n;if(n>0)hitPixels++;if(b==1)depthDiff++;
                    if((code&1)!=0){outputDiff++;if(n==0)diffWithoutReuse++;}if((code&2)!=0)nonfiniteCandidate++;if((code&4)!=0)nonfiniteReference++;
                }
            }finally{MemoryUtil.memFree(pixels);}
        }finally{
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,readFbo);GL11.glReadBuffer(readBuffer);GL11.glBindTexture(GL11.GL_TEXTURE_2D,binding);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pbo);
            for(int i=0;i<keys.length;i++)GL11.glPixelStorei(keys[i],prior[i]);GL30.glDeleteFramebuffers(fbo);
        }
        var after=G08CloudProbe.status();
        for(String key:List.of("program","draws","draw_counter","frame_counter","work_texture","full_draw","qualified"))if(!Objects.equals(before.get(key),after.get(key)))throw new IllegalStateException("Cloud writer changed during readback");
        if(!LoadedArtifactEvidence.fileSha256(file).equals(digest)||GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)!=active||GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)!=binding||GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)!=readFbo||GL11.glGetInteger(GL11.GL_READ_BUFFER)!=readBuffer||GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING)!=pbo)throw new IllegalStateException("Cloud readback ownership/restoration failed");
        for(int i=0;i<keys.length;i++)if(GL11.glGetInteger(keys[i])!=prior[i])throw new IllegalStateException("Cloud pack-state restoration failed");
        var out=new JsonObject();out.addProperty("pack",name);out.addProperty("archive_sha256",digest);out.add("diagnostic",marker);out.add("writer",new Gson().toJsonTree(before));
        out.addProperty("pixels",(long)v[2]*v[3]);out.addProperty("reuse_pixels",hitPixels);out.addProperty("reuse_hits",hits);out.addProperty("depth_difference_pixels",depthDiff);out.addProperty("output_difference_pixels",outputDiff);out.addProperty("difference_without_reuse_pixels",diffWithoutReuse);out.addProperty("candidate_nonfinite_pixels",nonfiniteCandidate);out.addProperty("reference_nonfinite_pixels",nonfiniteReference);out.add("output_codes",new Gson().toJsonTree(codes));out.addProperty("state_restored",true);
        out.addProperty("scope","Same-fragment raw float comparison before storage; actual completed cloud writer at captured frameCounter. No displayed-image equivalence, hardware fetch or timing claim.");return out;
    }
}

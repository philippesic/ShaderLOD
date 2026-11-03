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
import org.lwjgl.system.MemoryUtil;

/** Explicitly untimed, bounded integer work images in private pinned diagnostics. */
final class FragmentWorkProbe {
    private FragmentWorkProbe() {}
    static JsonObject read(String digest)throws Exception {
        if(!Boolean.getBoolean("shaderlod.dev.fragmentWorkProbe"))throw new IllegalStateException("Explicit untimed work launch required");
        var c=Minecraft.getInstance();var pipeline=Iris.getPipelineManager().getPipelineNullable();
        if(!(pipeline instanceof IrisRenderingPipeline iris)||c.level==null||Iris.isFallback()||c.screen!=null
                ||!BackgroundLaunch.qualityWindowReady(c)||!Iris.getShaderPackOptionQueue().isEmpty())
            throw new IllegalStateException("Settled focused native pipeline required");
        String name=Iris.getCurrentPackName();
        if(!name.matches("[A-Za-z0-9_.-]+\\.zip")||!digest.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Safe exact archive required");
        Path file=Iris.getShaderpacksDirectory().resolve(name);
        if(Files.isSymbolicLink(file)||!LoadedArtifactEvidence.fileSha256(file).equals(digest))throw new IllegalStateException("Archive changed");
        JsonObject marker;
        try(var z=new ZipFile(file.toFile())) {
            var entry=z.getEntry("SHADERLOD_FRAGMENT_WORK_DIAGNOSTIC.json");
            if(entry==null||entry.getSize()<0||entry.getSize()>4096)throw new IllegalStateException("Bounded work marker absent");
            marker=JsonParser.parseString(new String(z.getInputStream(entry).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        String kind=marker.get("kind").getAsString(),nativeHash=marker.get("native").getAsString();
        boolean pinned=("G09-reuse".equals(kind)&&"36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5".equals(nativeHash))
            || ("0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8".equals(nativeHash)
                && Set.of("G04-class","G05-glass","G05-water","G05-reuse","G08-cloud","G10-bloom","G06-fragment","G06-vertex","G06-prepow","G07-positive","G07-negative").contains(kind));
        if(!marker.get("diagnostic_only").getAsBoolean()||!pinned)throw new IllegalStateException("Unpinned work diagnostic");
        var pack=Iris.getCurrentPack().orElseThrow();var f=IrisRenderingPipeline.class.getDeclaredField("renderTargets");f.setAccessible(true);var targets=(RenderTargets)f.get(iris);
        var target=targets.get(8);if(target==null)throw new IllegalStateException("RT8 work image absent");
        int width=c.getWindow().getWidth(),height=c.getWindow().getHeight();
        if(width<1||height<1||width>4096||height>4096)throw new IllegalStateException("Work image extent exceeds bound");
        int frame=SystemTimeUniforms.COUNTER.getAsInt();var out=new JsonObject();out.addProperty("frame_counter",frame);out.addProperty("pack",name);out.addProperty("archive_sha256",digest);out.add("diagnostic",marker);
        boolean predicates=marker.has("encoding_mode")&&"predicate-bits".equals(marker.get("encoding_mode").getAsString());
        out.addProperty("scope","Untimed actual level0 RT8 R16F integer histogram, both banks kept separate. 0 background,1 eligible with no omission,N+1 source work units omitted. Visible last raster fragment only, not overdraw totals, hardware fetch counters, equivalence or speed proof.");
        if(predicates)out.addProperty("scope","Untimed RT8 R16F predicate-bit histogram. Consult diagnostic marker for bit meanings. No saved-work inference from encoded numbers.");
        if(marker.get("kind").getAsString().startsWith("G05-")) {
            var program=iris.getSodiumPrograms().getProgram(net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses.TRANSLUCENT);
            if(program!=null) {
                int id=program.handle(),location=GL20.glGetUniformLocation(id,"gbufferProjection");var projection=new JsonObject();projection.addProperty("named_translucent_program",id);projection.addProperty("uniform_location",location);
                if(location>=0)try(var stack=org.lwjgl.system.MemoryStack.stackPush()) {
                    // The pinned attached fragment declares this exact mat4;
                    // querying a named linked program does not bind or edit it.
                    var values=stack.mallocFloat(16);GL20.glGetUniformfv(id,location,values);var array=new JsonArray();
                    for(int i=0;i<16;i++){float value=values.get(i);if(!Float.isFinite(value))throw new IllegalStateException("Nonfinite projection");array.add(value);}
                    projection.add("column_major",array);
                }
                out.add("projection_readback",projection);
            }
        }
        JsonObject g04Writer = "G04-class".equals(kind)
            ? G04NativeWriter.join(digest,target.getMainTexture(),target.getAltTexture(),frame) : null;
        int readFbo=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),readBuffer=GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),packBuffer=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int[] keys={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS,GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST};
        int[] prior=Arrays.stream(keys).map(GL11::glGetInteger).toArray();int scratch=GL30.glGenFramebuffers();var rows=new JsonArray();
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);for(int key:keys)GL11.glPixelStorei(key,key==GL11.GL_PACK_ALIGNMENT?4:0);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,scratch);
            for(int bank=0;bank<2;bank++) {
                int texture=bank==0?target.getMainTexture():target.getAltTexture();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
                if(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH)!=width||GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT)!=height
                        ||GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT)!=GL30.GL_R16F)throw new IllegalStateException("Wrong work image allocation");
                GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                if(GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete work read FBO");
                int maxCode=marker.has("max_code")?marker.get("max_code").getAsInt():257;
                if(maxCode<1||maxCode>1024)throw new IllegalStateException("Bounded integer diagnostic code required");
                long[] histogram=new long[maxCode+1];long eligible=0,omitting=0,units=0;
                var pixels=MemoryUtil.memAllocFloat(Math.multiplyExact(width,height));
                try {
                    GL11.glReadPixels(0,0,width,height,GL11.GL_RED,GL11.GL_FLOAT,pixels);
                    for(int i=0;i<pixels.capacity();i++) {
                        float value=pixels.get(i);int integer=Math.round(value);
                        if(!Float.isFinite(value)||value<0||integer>maxCode||value!=integer)throw new IllegalStateException("Noninteger/out-of-range work image; blending or encoding unqualified");
                        histogram[integer]++;if(integer>0)eligible++;if(integer>1){omitting++;units+=integer-1;}
                    }
                } finally {MemoryUtil.memFree(pixels);}
                var row=new JsonObject();row.addProperty("bank",bank==0?"main":"alt");row.addProperty("texture",texture);row.addProperty("width",width);row.addProperty("height",height);row.addProperty("eligible_pixels",eligible);row.addProperty(predicates?"nonzero_code_pixels":"omitting_pixels",omitting);row.addProperty(predicates?"encoded_excess_sum":"omitted_source_units",units);row.add("histogram",new Gson().toJsonTree(histogram));rows.add(row);
            }
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,readFbo);GL11.glReadBuffer(readBuffer);GL11.glBindTexture(GL11.GL_TEXTURE_2D,binding);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,packBuffer);
            for(int i=0;i<keys.length;i++)GL11.glPixelStorei(keys[i],prior[i]);GL30.glDeleteFramebuffers(scratch);
        }
        if(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)!=readFbo||GL11.glGetInteger(GL11.GL_READ_BUFFER)!=readBuffer||GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)!=binding
                ||GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING)!=packBuffer||SystemTimeUniforms.COUNTER.getAsInt()!=frame||Iris.getCurrentPack().orElse(null)!=pack||Iris.getPipelineManager().getPipelineNullable()!=pipeline)throw new IllegalStateException("Work readback context changed");
        for(int i=0;i<keys.length;i++)if(GL11.glGetInteger(keys[i])!=prior[i])throw new IllegalStateException("Pixel-store restoration failed");
        if (g04Writer != null) {
            var afterWriter = G04NativeWriter.join(digest,target.getMainTexture(),target.getAltTexture(),frame);
            if (!g04Writer.equals(afterWriter)) throw new IllegalStateException("G04 writer receipt changed during readback");
            out.add("g04_native_writer", g04Writer);
            for (var element : rows) {
                var row = element.getAsJsonObject();boolean joined = false;
                for (var receipt : g04Writer.getAsJsonArray("receipts"))
                    if (receipt.getAsJsonObject().get("texture").equals(row.get("texture"))) joined = true;
                row.addProperty("same_frame_native_writer_joined", joined);
            }
        }
        out.add("banks",rows);out.addProperty("state_restored",true);return out;
    }
}

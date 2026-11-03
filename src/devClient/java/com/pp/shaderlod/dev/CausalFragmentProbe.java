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

/** Untimed private witnesses. Read actual RGBA32F bits, never infer a lean gain. */
final class CausalFragmentProbe {
    private CausalFragmentProbe() {}
    private static JsonObject vector(java.nio.FloatBuffer pixels) {
        var out=new JsonObject();var values=new JsonArray();var bits=new JsonArray();
        for(int i=0;i<4;i++){float v=pixels.get(i);values.add(Float.toString(v));bits.add(String.format("%08x",Float.floatToRawIntBits(v)));}
        out.add("values",values);out.add("ieee754_bits",bits);return out;
    }
    private static void attach(int texture,int width,int height) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        if(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH)!=width
            ||GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT)!=height
            ||GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT)!=GL30.GL_RGBA32F)
            throw new IllegalStateException("Wrong causal witness allocation");
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        if(GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete causal read FBO");
    }
    static JsonObject read(String digest)throws Exception {
        if(!Boolean.getBoolean("shaderlod.dev.fragmentWorkProbe"))throw new IllegalStateException("Explicit untimed witness launch required");
        var c=Minecraft.getInstance();var pipeline=Iris.getPipelineManager().getPipelineNullable();
        if(!(pipeline instanceof IrisRenderingPipeline iris)||c.level==null||Iris.isFallback()||c.screen!=null
            ||!BackgroundLaunch.qualityWindowReady(c)||!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("Settled native pipeline required");
        String name=Iris.getCurrentPackName();
        if(!name.matches("[A-Za-z0-9_.-]+\\.zip")||!digest.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Safe exact archive required");
        Path file=Iris.getShaderpacksDirectory().resolve(name);
        if(Files.isSymbolicLink(file)||!LoadedArtifactEvidence.fileSha256(file).equals(digest))throw new IllegalStateException("Archive changed");
        JsonObject marker;
        try(var z=new ZipFile(file.toFile())) {
            var entry=z.getEntry("SHADERLOD_CAUSAL_DIAGNOSTIC.json");
            if(entry==null||entry.getSize()<0||entry.getSize()>16384)throw new IllegalStateException("Bounded causal marker absent");
            marker=JsonParser.parseString(new String(z.getInputStream(entry).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        String kind=marker.get("kind").getAsString(),nativeHash=marker.get("native").getAsString();
        boolean pinned=("G09".equals(kind)&&"36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5".equals(nativeHash))
            ||(("G07".equals(kind)||"G05".equals(kind)||"G08".equals(kind))&&"0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8".equals(nativeHash));
        if(!pinned||!marker.get("diagnostic_only").getAsBoolean())throw new IllegalStateException("Unpinned causal diagnostic");
        var pack=Iris.getCurrentPack().orElseThrow();var f=IrisRenderingPipeline.class.getDeclaredField("renderTargets");f.setAccessible(true);var targets=(RenderTargets)f.get(iris);
        int width=c.getWindow().getWidth(),height=c.getWindow().getHeight();
        if(width<1||height<1||width>4096||height>4096)throw new IllegalStateException("Witness extent exceeds bound");
        int lastTarget=marker.has("last_target")?marker.get("last_target").getAsInt():13;
        if(lastTarget<11||lastTarget>13)throw new IllegalStateException("Unbounded causal target layout");
        for(int t=8;t<=lastTarget;t++)if(targets.get(t)==null||!targets.get(t).getInternalFormat().name().equals("RGBA32F"))throw new IllegalStateException("Missing RGBA32F witness target");
        int frame=SystemTimeUniforms.COUNTER.getAsInt();var out=new JsonObject();out.addProperty("frame_counter",frame);out.addProperty("pack",name);out.addProperty("archive_sha256",digest);out.add("diagnostic",marker);
        out.addProperty("scope","Untimed last visible fragment witnesses, banks separate; first failing pixels in framebuffer scan order and first common scalar mismatch in source order. Values+IEEE754bits are actual RGBA32F readbacks. Instrumentation can change compiler decisions; compare lean and forcedfalse controls. No speed, hardware fetch, lane/subgroup or harmless-precision inference.");
        int readFbo=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),readBuffer=GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),packBuffer=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int[] keys={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS,GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST};
        int[] prior=Arrays.stream(keys).map(GL11::glGetInteger).toArray();int scratch=GL30.glGenFramebuffers();var rows=new JsonArray();
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);for(int key:keys)GL11.glPixelStorei(key,key==GL11.GL_PACK_ALIGNMENT?4:0);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,scratch);
            for(int bank=0;bank<2;bank++) {
                var selected=new ArrayList<Integer>();var shortcutExamples=new ArrayList<Integer>();long eligible=0,mismatches=0,shortcuts=0,traceMismatches=0;long hierarchySaved=0,hierarchyBounds=0;var fields=new TreeMap<Integer,Long>();var traceFields=new TreeMap<Integer,Long>();var traceExamples=new ArrayList<Integer>();
                var selectedSteps=new TreeMap<Integer,Long>();
                int stepCount="G09".equals(kind)&&marker.has("selected_step_in_flag_w")&&marker.get("selected_step_in_flag_w").getAsBoolean()?8:
                    ("G07".equals(kind)||"G08".equals(kind))&&marker.has("selected_step_count")?marker.get("selected_step_count").getAsInt():0;
                if(stepCount<0||stepCount>16)throw new IllegalStateException("Selected iteration count outside bounded diagnostic");
                boolean stepWitness=stepCount>0;
                var image=MemoryUtil.memAllocFloat(Math.multiplyExact(Math.multiplyExact(width,height),4));
                try {
                    var target=targets.get(8);attach(bank==0?target.getMainTexture():target.getAltTexture(),width,height);
                    GL11.glReadPixels(0,0,width,height,GL11.GL_RGBA,GL11.GL_FLOAT,image);
                    for(int p=0;p<width*height;p++) {
                        float flag=image.get(p*4);if(!Float.isFinite(flag)||flag!=Math.round(flag)||flag<0||flag>2)throw new IllegalStateException("Invalid causal flag; blending/encoding unqualified");
                        if(flag>0){eligible++;int field=Math.round(image.get(p*4+1));if(field>=0){traceMismatches++;traceFields.merge(field,1L,Long::sum);if(traceExamples.size()<8)traceExamples.add(p);}}
                        if(flag==2){mismatches++;int field=Math.round(image.get(p*4+1));fields.merge(field,1L,Long::sum);if(selected.size()<32)selected.add(p);}
                        if(flag>0&&image.get(p*4+2)>0){shortcuts++;if(shortcutExamples.size()<8)shortcutExamples.add(p);}
                        if(stepWitness&&flag>0){float step=image.get(p*4+3);if(!Float.isFinite(step)||step!=Math.round(step)||step<0||step>=stepCount)throw new IllegalStateException("Invalid selected iteration");selectedSteps.merge((int)step,1L,Long::sum);}
                        if("G05".equals(kind)&&flag>0){float saved=image.get(p*4+2),bounds=image.get(p*4+3);if(!Float.isFinite(saved)||!Float.isFinite(bounds)||saved<0||saved>10||bounds<0||bounds>10||saved!=Math.round(saved)||bounds!=Math.round(bounds))throw new IllegalStateException("Hierarchy count encoding invalid");hierarchySaved+=(long)saved;hierarchyBounds+=(long)bounds;}
                    }
                    var row=new JsonObject();row.addProperty("bank",bank==0?"main":"alt");row.addProperty("width",width);row.addProperty("height",height);row.addProperty("eligible_pixels",eligible);row.addProperty("mismatch_pixels",mismatches);row.addProperty("shortcut_pixels",shortcuts);row.addProperty("trace_mismatch_pixels",traceMismatches);row.add("trace_field_histogram",new Gson().toJsonTree(traceFields));row.add("first_field_histogram",new Gson().toJsonTree(fields));
                    if(stepWitness)row.add("selected_step_histogram",new Gson().toJsonTree(selectedSteps));
                    var examples=new JsonArray();if(selected.isEmpty())selected.addAll(traceExamples.isEmpty()?shortcutExamples:traceExamples);
                    var pixel=MemoryUtil.memAllocFloat(4);
                    try {
                        for(int p:selected) {
                            var sample=new JsonObject();sample.addProperty("x",p%width);sample.addProperty("y_gl",p/width);sample.addProperty("y_top",height-1-p/width);var vectors=new JsonObject();
                            for(int i=0;i<4;i++)pixel.put(i,image.get(p*4+i));vectors.add("rt8",vector(pixel));
                            for(int t=9;t<=lastTarget;t++){var target2=targets.get(t);attach(bank==0?target2.getMainTexture():target2.getAltTexture(),width,height);GL11.glReadPixels(p%width,p/width,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,pixel);vectors.add("rt"+t,vector(pixel));}
                            sample.add("vectors",vectors);examples.add(sample);
                        }
                    }finally{MemoryUtil.memFree(pixel);}
                    row.add("first_pixels",examples);rows.add(row);
                    if("G05".equals(kind)){
                        var repairTarget=targets.get(11);attach(bank==0?repairTarget.getMainTexture():repairTarget.getAltTexture(),width,height);GL11.glReadPixels(0,0,width,height,GL11.GL_RGBA,GL11.GL_FLOAT,image);long repairs=0;
                        for(int p=0;p<width*height;p++){float value=image.get(p*4);if(!Float.isFinite(value)||value<0||value>10||value!=Math.round(value))throw new IllegalStateException("Hierarchy repair count invalid");repairs+=(long)value;}
                        row.addProperty("saved_depth_query_expressions",hierarchySaved);row.addProperty("bound_query_expressions",hierarchyBounds);row.addProperty("exact_predecessor_repair_query_expressions",repairs);row.addProperty("net_query_expressions_removed",hierarchySaved-hierarchyBounds-repairs);
                    }
                }finally{MemoryUtil.memFree(image);}
            }
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,readFbo);GL11.glReadBuffer(readBuffer);GL11.glBindTexture(GL11.GL_TEXTURE_2D,binding);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,packBuffer);
            for(int i=0;i<keys.length;i++)GL11.glPixelStorei(keys[i],prior[i]);GL30.glDeleteFramebuffers(scratch);
        }
        if(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)!=readFbo||GL11.glGetInteger(GL11.GL_READ_BUFFER)!=readBuffer||GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)!=binding
            ||GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING)!=packBuffer||SystemTimeUniforms.COUNTER.getAsInt()!=frame||Iris.getCurrentPack().orElse(null)!=pack||Iris.getPipelineManager().getPipelineNullable()!=pipeline)throw new IllegalStateException("Causal readback context changed");
        for(int i=0;i<keys.length;i++)if(GL11.glGetInteger(keys[i])!=prior[i])throw new IllegalStateException("Pixel-store restoration failed");
        out.add("banks",rows);out.addProperty("state_restored",true);return out;
    }
}

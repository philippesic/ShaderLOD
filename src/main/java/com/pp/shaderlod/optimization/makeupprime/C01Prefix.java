package com.pp.shaderlod.optimization.makeupprime;

import java.util.*;
import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import org.lwjgl.opengl.*;

/** Finite development-only prefix experiment. No persisted/public enable switch. */
public final class C01Prefix {
    private C01Prefix() {}
    private static String mode="off", refusal="",label="off";
    private static int prefix=4,remaining,drawIndex,program;
    private static long deadline,sequence,epoch;
    private static boolean frame,admitted;
    private static MakeUpDepthPriming.State state;
    private static final List<Map<String,Object>> records=new ArrayList<>();
    private record Pending(int query,Map<String,Object> record,String field) {}
    private static final List<Pending> pending=new ArrayList<>();
    private static final Map<String,Object> source=new LinkedHashMap<>();
    private static long nativeDraws,queryDraws,conditionalDraws,nativeCommands,nativeIndices;
    private static long knownRejectedCommands,knownRejectedIndices,unavailableCommands,unavailableIndices;

    public static void validate(String value,int first,int frames) {
        if(!Set.of("off","observe","conditional").contains(value)||first<0||first>256||frames<0||frames>20000)
            throw new IllegalArgumentException("Finite C01 prefix: off/observe/conditional, prefix0..256, frames0..20000");
    }
    public static void configure(String name,String value,int first,int frames) {
        validate(value,first,frames);stop();label=name;mode=value;prefix=first;remaining=frames;
        deadline=System.nanoTime()+180_000_000_000L;refusal="";records.clear();source.clear();sequence=0;
        nativeDraws=queryDraws=conditionalDraws=nativeCommands=nativeIndices=0;
        knownRejectedCommands=knownRejectedIndices=unavailableCommands=unavailableIndices=0;
    }
    public static void stop() {
        frame=false;state=null;remaining=0;mode="off";admitted=false;program=0;
        for(var p:pending)GL15C.glDeleteQueries(p.query());pending.clear();
    }
    /** Reject all mutable shader side effects even with framebuffer writes masked. */
    public static boolean sideEffectFree(String text,boolean vertex) {
        if(C01NativeAdmission.qualified())return C01NativeAdmission.stageAllowed(text,vertex);
        if(text==null||text.matches("(?s).*\\b(buffer|image\\w*|[ui]image\\w*|[ui]?atomic\\w*|memoryBarrier\\w*|barrier|gl_FragDepth|gl_SampleMask|gl_ClipDistance)\\b.*"))return false;
        return vertex||MakeUpDepthPriming.invariantStage(text,false);
    }
    static boolean eligible() {
        if(C01NativeAdmission.qualified())return true;
        var client=net.minecraft.client.Minecraft.getInstance();
        if(client.level==null||client.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD
            ||!com.pp.shaderlod.ShaderLodConfig.SPEC.isLoaded()
            ||!com.pp.shaderlod.ShaderLodConfig.snapshot().enabled()
            ||com.pp.shaderlod.ShaderLodConfig.PRESET.get()!=com.pp.shaderlod.LodPreset.PERFORMANCE
            ||com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended())return false;
        for(var id:com.pp.shaderlod.optimization.OptimizationId.values())
            if(com.pp.shaderlod.optimization.OptimizationConfig.snapshot().enabled(id))return false;
        return MakeUpDepthPriming.setup();
    }
    private static void drain() {
        for(var it=pending.iterator();it.hasNext();) {
            var p=it.next();if(GL15C.glGetQueryObjecti(p.query(),GL15C.GL_QUERY_RESULT_AVAILABLE)==0)continue;
            p.record().put(p.field(),GL15C.glGetQueryObjecti(p.query(),GL15C.GL_QUERY_RESULT)!=0);
            GL15C.glDeleteQueries(p.query());it.remove();
        }
    }
    public static void beginFrame(boolean solid) {
        frame=false;state=null;drawIndex=0;
        if(!solid)return;
        drain();
        if(mode.equals("off")||remaining<=0||System.nanoTime()>=deadline)return;
        if(!eligible()){refusal="source/profile/config/world/lifecycle guard";return;}
        if(pending.size()>4096){refusal="query backlog; native fallback";return;}
        remaining--;sequence++;frame=true;
    }
    static boolean admit(int nativeProgram) {
        if(admitted&&program==nativeProgram&&epoch==MakeUpDepthPriming.sourceEpoch())return true;
        int[] attached=new int[8],count=new int[1];GL20C.glGetAttachedShaders(nativeProgram,count,attached);
        boolean vertex=false,fragment=false;source.clear();
        for(int i=0;i<count[0];i++) {
            int type=GL20C.glGetShaderi(attached[i],GL20C.GL_SHADER_TYPE);
            String text=GL20C.glGetShaderSource(attached[i]);
            if(type==GL20C.GL_VERTEX_SHADER)vertex=sideEffectFree(text,true);
            else if(type==GL20C.GL_FRAGMENT_SHADER)fragment=sideEffectFree(text,false);
            else return false;
            try{source.put(type==GL20C.GL_VERTEX_SHADER?"vertex_sha256":"fragment_sha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))));}
            catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
        }
        source.put("native_program",nativeProgram);source.put("vertex_side_effect_free",vertex);source.put("fragment_side_effect_free",fragment);
        source.put("selected_pack",net.irisshaders.iris.Iris.getCurrentPackName());
        admitted=count[0]==2&&vertex&&fragment;program=nativeProgram;epoch=MakeUpDepthPriming.sourceEpoch();source.put("source_epoch",epoch);return admitted;
    }
    static Map<String,Object> sourceSnapshot(){return Map.copyOf(source);}
    public static void draw(int nativeProgram,MultiDrawBatch batch,Runnable nativeDraw) {
        if(!frame){nativeDraw.run();return;}
        if(state==null) {
            if(!admit(nativeProgram)||GL15C.glGetQueryi(GL33C.GL_ANY_SAMPLES_PASSED,GL15C.GL_CURRENT_QUERY)!=0
                ||GL11C.glGetBoolean(GL40C.GL_TRANSFORM_FEEDBACK_BUFFER_ACTIVE)) {
                frame=false;refusal="native program/query/transform-feedback guard";nativeDraw.run();return;
            }
            state=MakeUpDepthPriming.State.capture(nativeProgram,false);
            if(state==null){frame=false;refusal="native raster/depth/FBO guard";nativeDraw.run();return;}
        }
        nativeDraws++;nativeCommands+=batch.size;long indices=C01Observation.indices(batch);nativeIndices+=indices;
        if(drawIndex++<prefix){nativeDraw.run();return;}
        int query=GL15C.glGenQueries();boolean owned=true;
        long signature=C01Observation.appendSignature(0xcbf29ce484222325L,batch);
        try {
            GL11C.glColorMask(false,false,false,false);GL11C.glDepthMask(false);
            try {
                GL15C.glBeginQuery(GL33C.GL_ANY_SAMPLES_PASSED,query);
                try{nativeDraw.run();queryDraws++;}finally{GL15C.glEndQuery(GL33C.GL_ANY_SAMPLES_PASSED);}
            } finally {GL11C.glDepthMask(true);GL11C.glColorMask(true,true,true,true);}
            if(signature!=C01Observation.appendSignature(0xcbf29ce484222325L,batch))
                throw new IllegalStateException("C01 prefix native command changed during query");
            boolean available=GL15C.glGetQueryObjecti(query,GL15C.GL_QUERY_RESULT_AVAILABLE)!=0;
            boolean passed=!available||GL15C.glGetQueryObjecti(query,GL15C.GL_QUERY_RESULT)!=0;
            var r=new LinkedHashMap<String,Object>();r.put("frame",sequence);r.put("draw",drawIndex);r.put("commands",batch.size);r.put("indices",indices);
            r.put("signature",Long.toUnsignedString(signature));r.put("result_available_before_native",available);
            if(available)r.put("passed",passed);
            if(records.size()<20000)records.add(r);
            if(mode.equals("conditional")) {
                // Query is from THIS immediate native command, under identical uniforms/geometry/depth.
                // NO_WAIT renders conservatively if the result is unavailable; no previous-frame reuse.
                GL30C.glBeginConditionalRender(query,GL30C.GL_QUERY_NO_WAIT);
                try{nativeDraw.run();conditionalDraws++;}finally{GL30C.glEndConditionalRender();}
                if(available&&!passed){knownRejectedCommands+=batch.size;knownRejectedIndices+=indices;}
                if(!available){unavailableCommands+=batch.size;unavailableIndices+=indices;}
            } else {
                // Independent witness wraps the ACTUAL native color draw, not a second diagnostic replay.
                int reference=GL15C.glGenQueries();boolean retained=false;
                try {
                    GL15C.glBeginQuery(GL15C.GL_SAMPLES_PASSED,reference);
                    try{nativeDraw.run();}finally{GL15C.glEndQuery(GL15C.GL_SAMPLES_PASSED);}
                    pending.add(new Pending(reference,r,"native_passed"));retained=true;
                } finally {if(!retained)GL15C.glDeleteQueries(reference);}
            }
            pending.add(new Pending(query,r,"passed"));owned=false;
        } finally {if(owned)GL15C.glDeleteQueries(query);}
    }
    public static void endFrame() {
        if(state!=null){state.assertRestored(program);MakeUpDepthPriming.assertNoGlError("C01 prefix native restore");}
        frame=false;state=null;
    }
    public static Map<String,Object> snapshot() {
        drain();var out=new LinkedHashMap<String,Object>();out.put("label",label);out.put("mode",mode);out.put("prefix_native_region_draws",prefix);
        out.put("remaining_frames",remaining);out.put("frames",sequence);out.put("refusal",refusal);out.put("source",Map.copyOf(source));
        out.put("native_region_draws",nativeDraws);out.put("native_commands",nativeCommands);out.put("native_indices",nativeIndices);
        out.put("query_replay_draws",queryDraws);out.put("conditional_native_draws",conditionalDraws);out.put("known_rejected_commands_lower_bound",knownRejectedCommands);out.put("known_rejected_indices_lower_bound",knownRejectedIndices);
        out.put("unavailable_commands",unavailableCommands);out.put("unavailable_indices",unavailableIndices);out.put("pending_queries",pending.size());out.put("records",List.copyOf(records));
        out.put("false_hidden_observations",records.stream().filter(r->Boolean.FALSE.equals(r.get("passed"))&&Boolean.TRUE.equals(r.get("native_passed"))).count());
        out.put("paired_native_observations",records.stream().filter(r->r.containsKey("passed")&&r.containsKey("native_passed")).count());
        out.put("bounds","exact native geometry including displacement/clipping; no proxy boxes");
        out.put("depth","current native prefix plus earlier retained native draws; conventional LEQUAL, unchanged culling/order");
        out.put("conditional_count_limit","Unavailable-at-CPU results may execute or reject on GPU; actual avoided commands bounded, not inferred from hidden count");return out;
    }
}

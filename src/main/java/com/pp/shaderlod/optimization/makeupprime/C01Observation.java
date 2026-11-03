package com.pp.shaderlod.optimization.makeupprime;

import java.util.*;
import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

/** Finite development observer. Never changes or suppresses a native colour submission. */
public final class C01Observation {
    private C01Observation() {}
    private static int remaining, visibilityRemaining, currentTimer;
    private static boolean observing;
    private static long cpuStart, sequence;
    private static final List<Map<String,Object>> frames = new ArrayList<>();
    private static final List<Map<String,Object>> sections = new ArrayList<>();
    private static final List<int[]> pending = new ArrayList<>();
    private static String label = "off";
    private static final Map<String,Object> source = new LinkedHashMap<>();
    public static boolean nativeProgram(int program) {
        if(GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM)!=program)return false;
        int[] attached=new int[8],count=new int[1];GL20C.glGetAttachedShaders(program,count,attached);
        boolean fragment=false,vertex=false;
        source.clear();source.put("native_program",program);source.put("selected_pack",net.irisshaders.iris.Iris.getCurrentPackName());
        source.put("depth_producer","untouched native SOLID colour pass; observation occurs after submission");
        for(int i=0;i<count[0];i++) {
            int type=GL20C.glGetShaderi(attached[i],GL20C.GL_SHADER_TYPE);String text=GL20C.glGetShaderSource(attached[i]);
            if(type==GL20C.GL_FRAGMENT_SHADER)fragment=MakeUpDepthPriming.invariantStage(text,false);
            if(type==GL20C.GL_VERTEX_SHADER)vertex=true;
            try{source.put(type==GL20C.GL_VERTEX_SHADER?"vertex_sha256":"fragment_sha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))));}
            catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
        }
        source.put("fragment_no_discard_depth_or_image_side_effects",fragment);source.put("same_native_vertex_program",vertex);
        return fragment&&vertex&&count[0]==2;
    }
    /** Admission for the private neutral-controller cost bridge; no public enable path. */
    public static boolean privateCostQualified() {
        return "true".equals(System.getProperty("shaderlod.dev.c01Cost"))
            && "true".equals(System.getProperty("shaderlod.dev.c01Bounds"))
            && "true".equals(System.getProperty("shaderlod.dev.c01NativeR66"))
            && C01NativeAdmission.qualified();
    }
    public static void configure(String name, int count, int visibilityFrames) {
        if (count < 0 || count > 600 || visibilityFrames < 0 || visibilityFrames > 4 || visibilityFrames > count)
            throw new IllegalArgumentException("Finite observer: 0..600 timing frames, at most 4 visibility frames");
        stop(); frames.clear(); sections.clear(); label=name; remaining=count; visibilityRemaining=visibilityFrames;
    }
    public static void stop() {
        remaining=0; visibilityRemaining=0; observing=false;
        for (var q:pending) GL15C.glDeleteQueries(q[0]); pending.clear();
        if(currentTimer!=0){GL15C.glEndQuery(GL33C.GL_TIME_ELAPSED);GL15C.glDeleteQueries(currentTimer);currentTimer=0;}
    }
    private static int capturedIrisFrame;
    public static void beginFrame() {
        for (var it=pending.iterator();it.hasNext();) {
            var q=it.next();
            if(GL15C.glGetQueryObjecti(q[0],GL15C.GL_QUERY_RESULT_AVAILABLE)!=0) {
                frames.get(q[1]).put("solid_gpu_ns",GL33C.glGetQueryObjectui64(q[0],GL15C.GL_QUERY_RESULT));
                GL15C.glDeleteQueries(q[0]);it.remove();
            }
        }
        observing=false;
        if(remaining<=0)return;
        if(GL15C.glGetQueryi(GL33C.GL_TIME_ELAPSED,GL15C.GL_CURRENT_QUERY)!=0)return;
        remaining--; sequence++; capturedIrisFrame=net.irisshaders.iris.uniforms.SystemTimeUniforms.COUNTER.getAsInt(); observing=visibilityRemaining>0;
        if(observing)visibilityRemaining--;
        currentTimer=GL15C.glGenQueries();GL15C.glBeginQuery(GL33C.GL_TIME_ELAPSED,currentTimer);cpuStart=System.nanoTime();
    }
    public static void endFrame() {
        if(currentTimer==0)return;
        GL15C.glEndQuery(GL33C.GL_TIME_ELAPSED);
        var f=new LinkedHashMap<String,Object>();f.put("sequence",sequence);f.put("iris_frame",capturedIrisFrame);f.put("visibility_observation",observing);
        f.put("solid_cpu_ns",System.nanoTime()-cpuStart);frames.add(f);
        pending.add(new int[]{currentTimer,frames.size()-1});currentTimer=0;observing=false;
    }
    public static boolean observing(){return observing;}
    /** Ordered native command identity; pointers are consumed only synchronously, never retained. */
    public static long appendSignature(long value, MultiDrawBatch b) {
        for(int i=0;i<b.size;i++) {
            value=(value^MemoryUtil.memGetInt(b.pElementCount+4L*i))*0x100000001b3L;
            value=(value^MemoryUtil.memGetInt(b.pBaseVertex+4L*i))*0x100000001b3L;
            value=(value^MemoryUtil.memGetAddress(b.pElementPointer+(long)org.lwjgl.system.Pointer.POINTER_SIZE*i))*0x100000001b3L;
        }
        return value;
    }
    public static long indices(MultiDrawBatch b) {
        long n=0;for(int i=0;i<b.size;i++)n+=Integer.toUnsignedLong(MemoryUtil.memGetInt(b.pElementCount+4L*i));return n;
    }
    /** Both tests use the exact invariant native vertex/opaque coverage program and native GL culling. */
    public static boolean hidden(long covered,long passed,boolean nearOrUnknown) {return !nearOrUnknown&&covered>0&&passed==0;}
    public static void section(int x,int y,int z,long geometryPointer,boolean nearOrUnknown,MultiDrawBatch b,Runnable draw) {
        int visible=GL15C.glGenQueries(),raster=GL15C.glGenQueries();
        try {
            GL15C.glBeginQuery(GL15C.GL_SAMPLES_PASSED,visible);
            try{draw.run();}finally{GL15C.glEndQuery(GL15C.GL_SAMPLES_PASSED);}
            GL11C.glDepthFunc(GL11C.GL_ALWAYS);
            try {
                GL15C.glBeginQuery(GL15C.GL_SAMPLES_PASSED,raster);
                try{draw.run();}finally{GL15C.glEndQuery(GL15C.GL_SAMPLES_PASSED);}
            } finally {GL11C.glDepthFunc(GL11C.GL_LEQUAL);}
            long passed=GL33C.glGetQueryObjectui64(visible,GL15C.GL_QUERY_RESULT);
            long covered=GL33C.glGetQueryObjectui64(raster,GL15C.GL_QUERY_RESULT);
            var s=new LinkedHashMap<String,Object>();s.put("sequence",sequence);s.put("section",List.of(x,y,z));
            s.put("geometry_pointer",Long.toUnsignedString(geometryPointer));s.put("commands",b.size);s.put("indices",indices(b));
            s.put("command_signature",Long.toUnsignedString(appendSignature(0xcbf29ce484222325L,b)));
            s.put("passed_samples",passed);s.put("raster_samples",covered);
            s.put("near_or_unknown_fallback",nearOrUnknown);
            s.put("source_epoch",MakeUpDepthPriming.sourceEpoch());
            s.put("conservatively_hidden",hidden(covered,passed,nearOrUnknown));sections.add(s);
        } finally {GL15C.glDeleteQueries(visible);GL15C.glDeleteQueries(raster);}
    }
    public static void verifyRegion(long expected,long observed,int expectedCommands,int observedCommands) {
        if(expected!=observed||expectedCommands!=observedCommands)throw new IllegalStateException("C01 section/native command identity mismatch");
    }
    public static Map<String,Object> snapshot() {
        return Map.of("label",label,"remaining_frames",remaining,"pending_gpu_queries",pending.size(),"frames",List.copyOf(frames),"source",Map.copyOf(source),
            "sections",List.copyOf(sections),"suppressed_native_commands",0,"visibility_method","same-frame exact native geometry; raster>0 and LEQUAL samples=0; no writes; diagnostic only");
    }
}

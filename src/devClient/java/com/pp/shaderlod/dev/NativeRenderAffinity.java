package com.pp.shaderlod.dev;

import java.util.*;
import org.lwjgl.system.*;

/** Optional Linux diagnostic: pin only the caller during an already settled window.
 * Restore before reloads. New surviving threads inheriting the temporary mask invalidate the run. */
final class NativeRenderAffinity implements AutoCloseable {
    static final int BYTES=128;
    private static final ThreadLocal<NativeRenderAffinity> OWNER=new ThreadLocal<>();
    interface Backend { byte[] read(); void write(byte[] mask); default Set<String> tasks(){return Set.of();} default String mask(String task){return "";} }
    private final int cpu;
    private final Backend backend;
    private final byte[] original;
    private final Set<String> tasks;
    private final long thread=Thread.currentThread().threadId();
    private boolean applied,restored;
    private String error="";
    private final List<String> inherited=new ArrayList<>();

    static NativeRenderAffinity open(){
        int cpu=Integer.getInteger("shaderlod.dev.renderCpu",-1);
        return open(cpu,cpu<0?null:Linux.INSTANCE);
    }
    static NativeRenderAffinity open(int cpu,Backend backend){
        if(cpu< -1)throw new IllegalArgumentException("Diagnostic CPU must be -1 or nonnegative");
        if(cpu>=0&&OWNER.get()!=null)throw new IllegalStateException("Nested affinity diagnostic");
        var result=new NativeRenderAffinity(cpu,backend);if(cpu>=0)OWNER.set(result);return result;
    }
    private NativeRenderAffinity(int cpu,Backend backend){
        this.cpu=cpu;this.backend=backend;
        if(cpu<0){original=new byte[0];tasks=Set.of();restored=true;return;}
        if(cpu>=BYTES*8)throw new IllegalArgumentException("Diagnostic CPU outside bounded mask");
        original=backend.read().clone();
        if(original.length!=BYTES||(original[cpu/8]&(1<<(cpu%8)))==0)
            throw new IllegalStateException("Diagnostic CPU outside actual caller affinity");
        tasks=Set.copyOf(backend.tasks());
        byte[] target=new byte[BYTES];target[cpu/8]=(byte)(1<<(cpu%8));
        try{
            backend.write(target);applied=true;
            if(!Arrays.equals(target,backend.read()))throw new IllegalStateException("Diagnostic CPU mask not applied exactly");
        }catch(RuntimeException|LinkageError failure){
            try{backend.write(original);restored=Arrays.equals(original,backend.read());}
            catch(RuntimeException|LinkageError restoreFailure){failure.addSuppressed(restoreFailure);}
            if(!restored)failure.addSuppressed(new IllegalStateException("Original caller affinity not verified after refused pin"));
            throw failure;
        }
    }
    @Override public void close(){
        if(cpu<0)return;
        if(Thread.currentThread().threadId()!=thread)throw new IllegalStateException("Affinity must restore on its owning thread");
        if(!restored){backend.write(original);restored=Arrays.equals(original,backend.read());}
        if(!restored)throw new IllegalStateException("Original render-thread affinity restoration differs");
        if(OWNER.get()==this)OWNER.remove();
        if(inherited.isEmpty()&&cpus(original).size()>1){
            for(String task:backend.tasks())if(!tasks.contains(task)&&Integer.toString(cpu).equals(backend.mask(task)))inherited.add(task);
            if(!inherited.isEmpty())error="New thread inherited diagnostic mask; comparison invalid, owned client must stop";
        }
        if(!error.isEmpty())throw new IllegalStateException(error);
    }
    private static List<Integer> cpus(byte[] mask){
        var values=new ArrayList<Integer>();for(int i=0;i<mask.length*8;i++)if((mask[i/8]&(1<<(i%8)))!=0)values.add(i);return List.copyOf(values);
    }
    Map<String,Object> snapshot(){return Map.of("requested_cpu",cpu,"applied",applied,"restored",restored,
        "original_cpus",cpus(original),"inherited_restricted_tasks",List.copyOf(inherited),"error",error,
        "scope","Diagnostic caller-thread affinity only during settled capture; original mask verified before reloads. No governor/global settings change. Endpoint task census detects surviving newly restricted threads; exited transient children are not observed. Detected survivors invalidate the comparison.");}

    private static final class Linux implements Backend {
        static final Linux INSTANCE=new Linux();
        final SharedLibrary library;
        final long get,set;
        Linux(){
            if(!"Linux".equals(System.getProperty("os.name")))throw new IllegalStateException("Linux affinity diagnostic required");
            if(java.nio.ByteOrder.nativeOrder()!=java.nio.ByteOrder.LITTLE_ENDIAN)throw new IllegalStateException("Pinned little-endian Linux CPU mask required");
            library=Library.loadNative(NativeRenderAffinity.class,"org.lwjgl","libc.so.6",false);
            get=library.getFunctionAddress("sched_getaffinity");set=library.getFunctionAddress("sched_setaffinity");
            if(get==0||set==0)throw new IllegalStateException("glibc affinity functions unavailable");
        }
        public byte[] read(){
            var buffer=MemoryUtil.memAlloc(BYTES);
            try{if(JNI.callPPI(0,(long)BYTES,MemoryUtil.memAddress(buffer),get)!=0)throw new IllegalStateException("sched_getaffinity refused");
                byte[] result=new byte[BYTES];buffer.get(result);return result;}
            finally{MemoryUtil.memFree(buffer);}
        }
        public void write(byte[] mask){
            if(mask.length!=BYTES)throw new IllegalArgumentException("Exact affinity mask required");
            var buffer=MemoryUtil.memAlloc(BYTES);
            try{buffer.put(mask).flip();if(JNI.callPPI(0,(long)BYTES,MemoryUtil.memAddress(buffer),set)!=0)throw new IllegalStateException("sched_setaffinity refused");}
            finally{MemoryUtil.memFree(buffer);}
        }
        public Set<String> tasks(){
            try(var paths=java.nio.file.Files.list(java.nio.file.Path.of("/proc/self/task"))){return paths.map(p->p.getFileName().toString()).collect(java.util.stream.Collectors.toUnmodifiableSet());}
            catch(java.io.IOException e){throw new IllegalStateException("Affinity thread census unavailable",e);}
        }
        public String mask(String task){
            try{return java.nio.file.Files.readAllLines(java.nio.file.Path.of("/proc/self/task",task,"status")).stream()
                .filter(s->s.startsWith("Cpus_allowed_list:")).map(s->s.substring(s.indexOf(':')+1).trim()).findFirst().orElse("");}
            catch(java.nio.file.NoSuchFileException exited){return "";}
            catch(java.io.IOException e){throw new IllegalStateException("New thread affinity unreadable",e);}
        }
    }
}

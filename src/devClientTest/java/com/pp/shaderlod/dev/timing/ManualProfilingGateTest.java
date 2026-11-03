package com.pp.shaderlod.dev.timing;

/** Manual control latches between frames; disabled GPU entrypoints must not touch GL. */
public final class ManualProfilingGateTest {
    public static void main(String[] args) {
        System.setProperty("shaderlod.dev.gpuTiming","true");
        System.setProperty("shaderlod.dev.cpuTiming","true");
        System.setProperty("shaderlod.dev.manualProfiling","true");
        System.setProperty("shaderlod.dev.gpuSampleInterval","16");
        System.setProperty("shaderlod.dev.cpuLayerSampleInterval","16");
        GpuTimers.beginTick(true);CpuTimers.beginFrame(1,true);
        exerciseGpu();CpuTimers.begin("off");CpuTimers.end("off");GpuTimers.endTick();
        if(GpuTimers.recording()||CpuTimers.recording()||!CpuTimers.drainSamples().isEmpty())throw new AssertionError("Manual profiling did not start disabled");
        GpuTimers.setRecording(true);CpuTimers.setRecording(true);
        if(GpuTimers.recording()||CpuTimers.recording())throw new AssertionError("Control latched inside a frame");
        GpuTimers.beginTick(true);CpuTimers.beginFrame(2,true);
        exerciseGpu();CpuTimers.begin("coarse");CpuTimers.beginLayer("sparse");CpuTimers.endLayer("sparse");CpuTimers.end("coarse");GpuTimers.endTick();
        var samples=CpuTimers.drainSamples();
        if(samples.size()!=1||!samples.getFirst().scope().equals("coarse"))throw new AssertionError("Sparse CPU layers suppressed coarse timing or ran unsampled");
        for(int frame=3;frame<=17;frame++){GpuTimers.beginTick(true);CpuTimers.beginFrame(frame,true);GpuTimers.endTick();}
        CpuTimers.begin("outer");CpuTimers.beginLayer("sparse");
        CpuTimers.setRecording(false);GpuTimers.setRecording(false);
        CpuTimers.endLayer("sparse");CpuTimers.end("outer");
        if(CpuTimers.drainSamples().size()!=2)throw new AssertionError("Disabling unbalanced open CPU scopes");
        GpuTimers.beginTick(true);CpuTimers.beginFrame(18,true);exerciseGpu();GpuTimers.endTick();
        if(GpuTimers.recording()||CpuTimers.recording()||!GpuTimers.snapshot().backend().equals("UNINITIALIZED"))throw new AssertionError("Disabled control reached GL");
        System.out.println("Manual frame-boundary CPU/GPU control and sparse layers passed");
    }
    private static void exerciseGpu(){
        GpuTimers.beginFrame(true);GpuTimers.beginIrisGroup("deferred");GpuTimers.endIrisGroup();
        GpuTimers.beginStandalonePass("final");GpuTimers.endStandalonePass();
        GpuTimers.beginCategory("terrain",GpuSamplingPolicy.Category.TERRAIN);GpuTimers.endCategory();GpuTimers.endFrame();
    }
}

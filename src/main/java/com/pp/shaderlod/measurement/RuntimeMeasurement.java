package com.pp.shaderlod.measurement;
import java.lang.management.*;
import java.util.*;
/** Normal mod instrumentation; render-thread control, opt-in, independent of dev timing properties. */
public final class RuntimeMeasurement {
    private static final OpenGlElapsedBackend GPU_BACKEND=new OpenGlElapsedBackend();
    public enum ObserverMode { PERFORMANCE, GPU_ELAPSED }
    private static ObserverMode observerMode=ObserverMode.GPU_ELAPSED;
    private static final FrameMeasurement.GpuBackend NO_GPU=new FrameMeasurement.GpuBackend() {
        public String status(){return "performance: GPU queries disabled; elapsed is unavailable";}
        public int counterBits(){return 0;}
        public int create(){throw new IllegalStateException("Performance observer cannot allocate GPU queries");}
        public boolean busy(){return false;}
        public void begin(int query){throw new IllegalStateException("Performance observer cannot start GPU queries");}
        public void end(){throw new IllegalStateException("Performance observer cannot end GPU queries");}
        public boolean available(int query){return false;}
        public long nanoseconds(int query){throw new IllegalStateException("Performance observer has no GPU results");}
        public void delete(int query){}
    };
    private static FrameMeasurement timer;
    public static ObserverMode observerMode(){return observerMode;}
    /** Change observer regimes only between owners; pending queries and epochs never cross modes. */
    public static void observerMode(ObserverMode mode){
        Objects.requireNonNull(mode);
        if(requested||windowPaused||sceneLease!=null||paired!=null&&(paired.active()||paired.restorationPending()))
            throw new IllegalStateException("Stop and restore the current measurement owner before changing observer mode");
        if(mode==observerMode)return;
        if(timer!=null)timer.enabled(false);
        timer=null;samples.clear();manualPins=Map.of();observerMode=mode;
    }
    public static String gpuQueryMode(){return observerMode==ObserverMode.PERFORMANCE?"performance_off":GPU_BACKEND.diagnosticQueryOff()?"diagnostic_off":GPU_BACKEND.reuseCompletedQueries()?"elapsed_on_completed_reuse":"elapsed_on";}
    private static boolean requested;
    private static boolean windowPaused;
    private static long reloadCount;
    private static PairedMeasurement paired;
    private static SceneLease sceneLease;
    private static java.util.Map<String,String> manualPins=java.util.Map.of();
    private static boolean requestedBeforePair;
    private static long pairStarted;
    private static boolean ownedReload,externalReload;
    private static java.util.function.Supplier<String> pairSettings;
    private static Object previousWorld,previousPack;
    public static boolean needed() {return requested||(timer!=null&&timer.enabled())||sceneLease!=null||(paired!=null&&paired.restorationPending());}
    public static void onReload() {
        reloadCount++;
        invalidate();
        if(paired!=null&&paired.active()&&!ownedReload)externalReload=true;
    }
    public static void context(Object world,Object pack) {
        if(previousWorld!=world||previousPack!=pack) {
            invalidate();
            if(previousWorld!=null&&paired!=null&&paired.active()&&!ownedReload)externalReload=true;
            previousWorld=world;previousPack=pack;
        }
    }
    private static String error="disabled";
    private static java.nio.file.Path lastExport;
    private static final ThreadMXBean THREAD=ManagementFactory.getThreadMXBean();
    private static final OperatingSystemMXBean OS=ManagementFactory.getOperatingSystemMXBean();
    private static final ArrayDeque<FrameMeasurement.Sample> samples=new ArrayDeque<>();
    private RuntimeMeasurement() {}
    public static void request(boolean enabled) {
        if(!enabled)pauseCpuWindow();
        if(enabled&&paired!=null&&!paired.active()&&paired.restorationPending())throw new IllegalStateException("Restore the failed pair before requesting timing");
        if(!enabled&&paired!=null&&(paired.active()||paired.restorationPending()))cancelPair();
        if(enabled&&!requested) {samples.clear();manualPins=java.util.Map.of();if(paired==null||!paired.active())paired=null;if(timer!=null){timer.invalidate();timer.resetCpuWindow();}}
        requested=enabled;
        windowPaused=false;
    }
    public static boolean requested() {return requested;}
    public static long reloadCount() {return reloadCount;}
    public static Map<String,Long> queryAllocationCounts() {return timer==null?Map.of():timer.queryAllocationCounts();}
    public static String status() {
        if(error.startsWith("timing error")||error.startsWith("pair startup failed")||error.startsWith("pair cancellation")||error.startsWith("cleanup restoration"))return error+pendingStatus();
        if(paired!=null&&(paired.active()||paired.phase()==PairedMeasurement.Phase.FAILED))return paired.status()+pendingStatus();
        if(!samples.isEmpty()) {
            var s=samples.getLast();return (requested?"live ":"last ")+"FPS="+(s.fps()==null?"n/a":String.format(java.util.Locale.ROOT,"%.1f",s.fps()))+
                " render="+String.format(java.util.Locale.ROOT,"%.2fms",s.renderWallNs()/1e6)+
                " threadCPU="+(s.renderCpuNs()==null?"n/a":String.format(java.util.Locale.ROOT,"%.2fms",s.renderCpuNs()/1e6))+
                " processCPU="+(s.processCpuNs()==null?"n/a":String.format(java.util.Locale.ROOT,"%.2fms",s.processCpuNs()/1e6))+
                " GPU="+(s.gpuNs()==null?s.gpuStatus():String.format(java.util.Locale.ROOT,"%.2fms",s.gpuNs()/1e6));
        }
        return error+pendingStatus();
    }
    private static String pendingStatus(){return (paired!=null&&paired.restorationPending()&&!paired.active()?"; flags/source restoration pending":"")+(sceneLease!=null&&(paired==null||!paired.active())?"; simulation restoration pending":"");}
    public static PairedMeasurement pair() {return paired;}
    public static List<FrameMeasurement.Sample> drain() {var result=List.copyOf(samples);samples.clear();return result;}
    /** Stop issuing queries while an owned manual window drains its asynchronous tail. */
    public static void pauseWindow() {
        if(!requested||(paired!=null&&(paired.active()||paired.restorationPending())))
            throw new IllegalStateException("A manual timing window must own the clock before pausing");
        pauseCpuWindow();windowPaused=true;
    }
    public static CpuWindowMeasurement.Result pauseCpuWindow() {return timer==null?null:timer.pauseCpuWindow();}
    public static int pendingQueries() {return timer==null?0:timer.pendingQueries();}
    /** Nonblocking; the caller must run on the render thread with the current GL context. */
    public static void pollSamples() {if(timer!=null){timer.poll();consume();}}
    public static void resumeWindow() {
        if(paired!=null&&(paired.active()||paired.restorationPending()))
            throw new IllegalStateException("A paired run owns the timing clock");
        invalidate();if(timer!=null)timer.resetCpuWindow();windowPaused=false;
    }
    private static long cpu() {
        return THREAD.isCurrentThreadCpuTimeSupported()&&THREAD.isThreadCpuTimeEnabled()?THREAD.getCurrentThreadCpuTime():-1;
    }
    private static long processCpu() {
        return OS instanceof com.sun.management.OperatingSystemMXBean os?os.getProcessCpuTime():-1;
    }
    public static CpuWindowMeasurement.Snapshot cpuWindowSnapshot() {
        return new CpuWindowMeasurement.Snapshot(Thread.currentThread().threadId(),System.nanoTime(),cpu(),processCpu());
    }
    public static void startPair(com.pp.shaderlod.optimization.OptimizationId feature,int warmup,int samples,
            java.util.function.Supplier<String> settings) {
        PairedMeasurement.requireIndependentSelection(com.pp.shaderlod.optimization.OptimizationConfig.activeFamily(),feature,com.pp.shaderlod.optimization.OptimizationConfig.snapshot());
        if(paired!=null&&(paired.active()||paired.restorationPending()))throw new IllegalStateException("A paired run or its restoration is already owned");
        requestedBeforePair=requested;
        try {
            releaseScene();sceneLease=SceneLease.prepare();sceneLease.freeze();
            var prepared=com.pp.shaderlod.optimization.OptimizationEditTransaction.apply(com.pp.shaderlod.optimization.OptimizationConfig.snapshot(),
                    net.irisshaders.iris.Iris::reload,net.irisshaders.iris.Iris::isFallback,com.pp.shaderlod.optimization.CompileOptionControl::open);
            if(!prepared.success())throw new IllegalStateException(prepared.message());
            invalidate();requested=true;pairStarted=System.nanoTime();pairSettings=settings;
            error="paired measurement prepared";
            String pairFamily=com.pp.shaderlod.optimization.OptimizationConfig.activeFamily();
            String pairPack=net.irisshaders.iris.Iris.getCurrentPackName();
            paired=PairedMeasurement.prepare(feature,warmup,samples,()->MeasurementPins.capture(settings.get(),feature),flags->{
                if(!java.util.Objects.equals(pairPack,net.irisshaders.iris.Iris.getCurrentPackName())||
                        !pairFamily.equals(com.pp.shaderlod.optimization.OptimizationConfig.activeFamily())) {
                    try{com.pp.shaderlod.optimization.OptimizationConfig.restoreFamily(pairFamily,flags);return true;}
                    catch(java.io.IOException failure){throw new IllegalStateException("Could not restore original shader requests",failure);}
                }
                invalidate();ownedReload=true;Throwable primary=null;
                try {return com.pp.shaderlod.optimization.OptimizationEditTransaction.apply(flags,net.irisshaders.iris.Iris::reload,net.irisshaders.iris.Iris::isFallback,com.pp.shaderlod.optimization.CompileOptionControl::open).success();}
                catch(Throwable failure){primary=failure;PairedMeasurement.throwUnchecked(failure);return false;}
                finally {
                    ownedReload=false;
                    try{previousPack=net.irisshaders.iris.Iris.getCurrentPack().orElse(null);}
                    catch(Throwable cleanup){if(primary!=null)PairedMeasurement.append(primary,cleanup);else PairedMeasurement.throwUnchecked(cleanup);}
                }
            });
            paired.start();
            if(!paired.active()){requested=requestedBeforePair;releaseScene();autoExport();}
        }catch(Throwable failure) {
            requested=requestedBeforePair;
            // Retain failed pair/lease owners when their recovery is incomplete.
            try{if(paired!=null&&paired.active())paired.abort("pair startup failed");}catch(Throwable recovery){PairedMeasurement.append(failure,recovery);}
            try{releaseScene();}catch(Throwable recovery){PairedMeasurement.append(failure,recovery);}
            error="pair startup failed: "+PairedMeasurement.describe(failure);PairedMeasurement.throwUnchecked(failure);
        }
    }
    /** No-op outside an owned pair; ordinary timing state must remain unchanged. */
    public static void cancelActivePair() {
        if(paired!=null&&(paired.active()||paired.restorationPending())||sceneLease!=null)cancelPair();
    }
    public static void cancelPair() {
        Throwable failure=null;
        try{if(paired!=null&&(paired.active()||paired.restorationPending()))paired.cancel();}catch(Throwable recovery){failure=recovery;}
        finally{requested=requestedBeforePair;try{releaseScene();}catch(Throwable recovery){failure=PairedMeasurement.append(failure,recovery);}}
        if(paired!=null&&paired.restorationPending())failure=PairedMeasurement.append(failure,new IllegalStateException("Original flags/source restoration remains pending"));
        try{if(paired!=null)autoExport();}catch(Throwable export){failure=PairedMeasurement.append(failure,export);}
        if(failure!=null){error="pair cancellation restoration pending/failed: "+PairedMeasurement.describe(failure);PairedMeasurement.throwUnchecked(failure);}
    }
    public static String sceneProvenance() {return sceneLease==null?"simulation not owned":sceneLease.provenance();}
    private static void releaseScene() {if(sceneLease!=null){sceneLease.close();sceneLease=null;}}
    public static java.nio.file.Path lastExport() {return lastExport;}
    private static void autoExport() {
        try {lastExport=export(net.irisshaders.iris.Iris.getShaderpacksDirectory().resolveSibling("shaderlod-measurements"));}
        catch(java.io.IOException failure){error+="; export failed: "+failure.getMessage();}
    }
    public static java.nio.file.Path export(java.nio.file.Path directory) throws java.io.IOException {
        var cpuWindow=timer==null?null:timer.snapshotCpuWindow();
        var pins=new TreeMap<>(manualPins);
        pins.putAll(CpuMeasurementReport.observations(cpuWindow,List.copyOf(samples)));
        return MeasurementExport.write(directory,paired,List.copyOf(samples),status(),pins);
    }
    public static void begin(boolean world) {begin(world,false);}
    public static void begin(boolean world,boolean screenOpen) {
        if(timer==null&&!requested&&sceneLease==null&&(paired==null||!paired.restorationPending()))return;
        try {
            if(sceneLease!=null&&(paired==null||!paired.active())) {
                if(sceneLease.restoring()&&!sceneLease.restoreReady()) {error="local simulation restoration pending";return;}
                releaseScene();
            }
            if(paired!=null&&!paired.active()&&paired.restorationPending()) {error="original flags/source restoration pending; cancel retries";return;}
            if(paired!=null&&paired.active()&&(sceneLease==null||!sceneLease.valid())) {cancelPair();error="frozen simulation ownership changed";}
            if(screenOpen&&paired!=null&&paired.active()) {cancelPair();error="GUI opened during paired run; original flags restored";}
            if(externalReload) {externalReload=false;cancelPair();error="external reload/world change invalidated paired run";}
            if(paired!=null&&paired.active()&&(!paired.fixedPins().get("scene").equals(MeasurementPins.scene())||
                    !paired.fixedPins().get("settings").equals(pairSettings.get()))) {
                cancelPair();error="live camera/settings drift invalidated paired run";
            }
            if(paired!=null&&paired.active()&&System.nanoTime()-pairStarted>600_000_000_000L) {
                cancelPair();error="paired run exceeded finite 10-minute deadline; original flags restored";
            }
            if(timer==null) {
                if(THREAD.isCurrentThreadCpuTimeSupported()&&!THREAD.isThreadCpuTimeEnabled()) {
                    try {THREAD.setThreadCpuTimeEnabled(true);}catch(SecurityException|UnsupportedOperationException ignored) {}
                }
                timer=new FrameMeasurement(observerMode==ObserverMode.PERFORMANCE?NO_GPU:GPU_BACKEND,System::nanoTime,RuntimeMeasurement::cpu,RuntimeMeasurement::processCpu);
            }
            timer.enabled(requested&&world);
            if(!timer.enabled()) {error=requested?"awaiting world":"disabled";return;}
            if(windowPaused){timer.poll();consume();error="manual window draining GPU queries";return;}
            if(paired!=null&&paired.active()&&paired.readyToAdvance()) {
                timer.poll();consume();
                if(timer.pendingQueries()==0) {
                    paired.advance();timer.invalidate();
                    if(!paired.active()){requested=requestedBeforePair;releaseScene();autoExport();}
                }
                return;
            }
            if((paired==null||!paired.active())&&manualPins.isEmpty()) {
                try {var core=com.pp.shaderlod.optimization.OptimizationRuntimeContext.core();manualPins=MeasurementPins.capture(core==null?"core unavailable":core.toString());}
                catch(RuntimeException unavailable){manualPins=java.util.Map.of("provenance_status","unavailable: "+unavailable.getMessage(),"scope","GameRenderer HEAD/RETURN; actual clocks only");}
            }
            timer.begin();error="recording: frame-start interval, GameRenderer wall/render-thread CPU/process CPU, GameRenderer GPU";
        }catch(Throwable failure) {fail(failure);}
    }
    public static void end() {
        if(timer==null||!timer.enabled()||!requested||windowPaused)return;
        try {
            timer.end();
            consume();
        }catch(Throwable failure) {fail(failure);}
    }
    private static void consume() {
        for(var sample:timer.drain()) {
            if(samples.size()==8192)samples.removeFirst();samples.addLast(sample);
            if(paired!=null&&paired.active())paired.accept(sample);
        }
    }
    public static void invalidate() {if(timer!=null)timer.invalidate();samples.clear();manualPins=java.util.Map.of();}
    public static void close() {
        pauseCpuWindow();requested=false;windowPaused=false;Throwable failure=null;
        try{if(paired!=null&&(paired.active()||paired.restorationPending()))paired.cancel();}catch(Throwable recovery){failure=recovery;}
        try{releaseScene();}catch(Throwable recovery){failure=PairedMeasurement.append(failure,recovery);}
        if(paired!=null&&paired.restorationPending())failure=PairedMeasurement.append(failure,new IllegalStateException("Original flags/source restoration remains pending"));
        try{if(timer!=null)timer.enabled(false);timer=null;}catch(Throwable cleanup){failure=PairedMeasurement.append(failure,cleanup);}
        samples.clear();
        if(failure!=null){error="cleanup restoration pending/failed: "+PairedMeasurement.describe(failure);if(failure instanceof Error)PairedMeasurement.throwUnchecked(failure);}
    }
    private static void fail(Throwable failure) {
        String detail="timing error: "+PairedMeasurement.describe(failure);requested=false;windowPaused=false;
        try{if(timer!=null)timer.enabled(false);timer=null;}catch(Throwable cleanup){PairedMeasurement.append(failure,cleanup);}
        try{if(paired!=null&&paired.active())paired.abort(detail);}catch(Throwable recovery){PairedMeasurement.append(failure,recovery);}
        try{releaseScene();}catch(Throwable recovery){PairedMeasurement.append(failure,recovery);}
        requested=false;error=detail;
        try{if(paired!=null)autoExport();}catch(Throwable export){PairedMeasurement.append(failure,export);}
        if(failure instanceof Error)PairedMeasurement.throwUnchecked(failure);
    }
}

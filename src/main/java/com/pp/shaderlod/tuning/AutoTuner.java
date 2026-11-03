package com.pp.shaderlod.tuning;

import com.pp.shaderlod.*;
import com.pp.shaderlod.benchmark.BenchmarkWorld;
import com.pp.shaderlod.benchmark.BenchmarkScenario;
import com.pp.shaderlod.benchmark.core.*;
import java.util.function.BooleanSupplier;
import com.pp.shaderlod.measurement.*;
import com.pp.shaderlod.optimization.*;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;

/** Client-thread, finite automatic search using the normal mod clocks and guarded Apply path. */
public final class AutoTuner {
    private static Job job;
    private static BooleanSupplier benchmarkOwnerCheck=()->false;
    private static java.util.function.Function<TuningQualification.Scope,TuningQualification.Evidence> qualificationProvider=scope->TuningQualification.Evidence.untested();
    /** Evidence provider must bind references to the supplied exact run/source/profile scope. */
    public static void setQualificationProvider(java.util.function.Function<TuningQualification.Scope,TuningQualification.Evidence> provider){
        requireIdleForBenchmark();qualificationProvider=Objects.requireNonNull(provider);
    }
    /** Register the benchmark session's active OR pending-restoration state on the client thread. */
    public static void setBenchmarkOwnerCheck(BooleanSupplier check){benchmarkOwnerCheck=Objects.requireNonNull(check);}
    /** Benchmark callers must invoke before acquiring clocks, scene or settings. */
    public static void requireIdleForBenchmark(){
        if(active()||restorationPending())throw new IllegalStateException("Tuner owns the scene or restoration; use Cancel/Undo first");
    }
    private static boolean recoveryPending;
    private static String last="Idle; Quick is partial; Full inventory continues across finite segments";
    private AutoTuner() {}
    public static boolean active(){return job!=null;}
    public static boolean restorationPending(){
        if(recoveryPending||job!=null&&job.restoring)return true;
        try {var file=parent().resolve("undo.properties");return job==null&&Files.exists(file)&&"PENDING".equals(TuningJournal.properties(file).getProperty("state"));}
        catch(java.io.IOException failure){throw new IllegalStateException("Cannot establish tuner recovery state",failure);}
    }
    public static String status(){return job==null?last:job.status();}
    static List<String> captureSelectedResources(Collection<String> actual){return List.copyOf(actual);}
    static void requireResourceSelection(List<String> expected,Collection<String> actual){
        if(expected==null||!expected.equals(List.copyOf(actual)))throw new IllegalStateException("Selected resource packs changed externally; tuning aborted and user edits retained");
    }
    public static void start(int budgetSeconds,boolean full) {start(new TuningRequest(TuningGoal.PERFORMANCE,budgetSeconds,full));}
    public static void start(TuningRequest request) {
        Objects.requireNonNull(request);
        if(benchmarkOwnerCheck.getAsBoolean())throw new IllegalStateException("Benchmark owns the scene or restoration");
        if(SavedTunings.pending())throw new IllegalStateException("Saved-tuning recovery is pending; use its Undo control before tuning");
        if(job!=null||recoveryPending)throw new IllegalStateException("Tuning or pending restoration already owned; use Undo");
        if(RuntimeMeasurement.requested()||RuntimeMeasurement.pair()!=null&&RuntimeMeasurement.pair().restorationPending())
            throw new IllegalStateException("Stop manual/paired timing and restore it before tuning");
        var recovery=parent().resolve("undo.properties");
        try {if(Files.exists(recovery)&&TuningJournal.properties(recovery).getProperty("state","").equals("PENDING"))
            throw new IllegalStateException("Previous tuning was interrupted; use Undo before starting another run");}
        catch(java.io.IOException e){throw new IllegalStateException("Cannot inspect tuner recovery journal",e);}
        job=new Job(request);try{job.start();}catch(Throwable failure){
            if(job.original==null){last="Tuning refused before changing settings: "+failure;job=null;}
            else job.fail("Startup: "+failure);
        }
    }
    public static void tick(ClientTickEvent.Post event) {
        if(job==null)return;
        try{job.tick();}catch(Throwable failure){
            if(failure instanceof CandidateApplyFailure apply&&apply.restored&&job.stage==Stage.TRIAL&&job.phase==1) {
                try{job.rejectUnavailable(apply.getMessage());}catch(Throwable recovery){job.fail(recovery.toString());}
            }else job.fail(failure.toString());
        }
    }
    public static void cancel(){if(job!=null)job.fail("Cancelled; restoring original profile");}
    public static void shutdown(){if(job!=null)job.fail("Client shutdown; restoring original state");}
    public static void undo() {
        if(SavedTunings.pending())throw new IllegalStateException("Saved-tuning recovery is pending; use the saved-tuning Undo control first");
        if(job!=null){job.explicitUndo();return;}
        var file=parent().resolve("undo.properties");
        try {
            if(!Files.exists(file))throw new IllegalStateException("No tuner Undo journal");
            var original=TuningJournal.read(file);
            var client=Minecraft.getInstance();
            var recoveryState=TuningJournal.properties(file);recoveryState.setProperty("state","PENDING");TuningJournal.atomic(file,recoveryState);
            try{original.source().requireCurrent(Iris.getShaderpacksDirectory(),selectedPack());}
            catch(java.io.IOException mismatch){OptimizationConfig.restoreFamily(original.family(),original.flags());throw new IllegalStateException("Original family requests restored; graph recovery remains pending: "+mismatch.getMessage(),mismatch);}
            if(!OptimizationConfig.activeFamily().equals(original.family())) {
                OptimizationConfig.restoreFamily(original.family(),original.flags());
                throw new IllegalStateException("Original family requests restored; select "+original.family()+" and retry Undo to restore its graph/core settings");
            }
            if(!ShaderLodSettings.apply(original.enabled(),original.preset()))throw new IllegalStateException(ShaderLodSettings.failureMessage());
            var result=OptimizationEditTransaction.apply(original.flags(),Iris::reload,Iris::isFallback,CompileOptionControl::open);
            if(!result.success())throw new IllegalStateException(result.message());
            TimingVideoSettings.apply(original.fps(),original.vsync(),original.windowFps());client.options.save();
            var p=TuningJournal.properties(file);p.setProperty("state","UNDONE");TuningJournal.atomic(file,p);
            recoveryPending=false;last="Original profile/video settings restored from Undo";
        }catch(java.io.IOException failure){throw new IllegalStateException("Undo recovery failed; journal retained",failure);}
    }
    private static String selectedPack(){return Iris.getIrisConfig().getShaderPackName().orElseThrow(()->new IllegalStateException("No selected shader"));}
    private static Path parent(){return Iris.getShaderpacksDirectory().resolveSibling("shaderlod-tuning");}
    private static final class CandidateApplyFailure extends Exception {
        final boolean restored;
        CandidateApplyFailure(String message,boolean restored){super(message);this.restored=restored;}
    }
    private enum Stage { NATIVE, SAFE, REPEATABILITY, TRIAL, FINAL, MOTION, RESTORING }
    private static final class Job {
        final long started=System.nanoTime();final int budget;final boolean full;final TuningGoal goal;final Minecraft client=Minecraft.getInstance();
        final Object world=client.level;final List<TuningCandidates.Candidate> candidates=new ArrayList<>();
        final Set<OptimizationId> covered=EnumSet.noneOf(OptimizationId.class),measured=EnumSet.noneOf(OptimizationId.class);
        final TuningApplyPins sourcePins=new TuningApplyPins();
        final TuningWindow.InitialInterval motionInitialInterval=new TuningWindow.InitialInterval();
        final TuningWindow.InputPause readinessPause=new TuningWindow.InputPause();
        final TuningRecovery recovery=new TuningRecovery();
        final TuningPolicy.Configuration analysisConfiguration=TuningPolicy.configuration();
        final java.util.function.Function<TuningQualification.Scope,TuningQualification.Evidence> qualification=qualificationProvider;
        final String sessionId="one-live-JVM-session-"+UUID.randomUUID();
        final List<BenchmarkAnalysis.Bracket> repeatedBrackets=new ArrayList<>();
        final Map<List<FrameMeasurement.Sample>,String> captureIds=new IdentityHashMap<>();
        TuningConfirmation confirmation;
        RuntimeMeasurement.ObserverMode previousObserverMode;
        Map<String,String> identityPins=Map.of();
        int attemptCount,retryCount,round,finalRound,motionRound,captureSequence,validSampleFrames;
        boolean nullControlPassed;
        final List<List<BenchmarkAnalysis.Bracket>> finalEvidence=new ArrayList<>();
        TuningJournal.Original original;TuningJournal journal;SceneLease lease;TuningBands bands;TuningPreset.TerrainPolicy terrainPolicy;Map<String,String> effectOptions=Map.of();
        TuningContinuation continuation;Path continuationFile;int scheduledCount;Map<String,String> nativeEffectOptions=Map.of();
        Stage stage;OptimizationSnapshot best=OptimizationSnapshot.noneRequested(),baseline,candidate,expectedFlags;
        boolean expectedEnabled,confirm,cleanup,draining,restoring,cancelled,chatPaused,motionStarted,posePending,windowFlushed,motionQueued,meshPending,focusPaused;Object expectedPack;
        LodPreset expectedPreset;String expectedScene="",failure="",trial="",continuationUnavailable="",windowInvalidity="";int index,phase,pose,warm,applyCalls,trialRetry,windowRetry,repeatabilityRetry;
        List<String> expectedSelectedResources;Map<String,String> expectedEffectiveOptions=Map.of();long expectedFlagGeneration;String expectedSelectedName;Object expectedPipeline;
        long windowAt,sampleNs,sampleEndAt,drainAt,nextRestoreAt,applyNs,reloadNs,initialReloadCount,poseAt,firstSampleAt,meshAt,motionAt,readinessPauseNs,meshPauseNs;int reloadCalls;
        TuningCandidates.Candidate selected;
        final List<FrameMeasurement.Sample> window=new ArrayList<>(),excluded=new ArrayList<>();
        final List<List<FrameMeasurement.Sample>> bracket=new ArrayList<>();
        final List<List<FrameMeasurement.Sample>> finalWindows=new ArrayList<>();
        int finalPoses,finalPhase,finalPose;
        Job(TuningRequest request){this.budget=request.budgetSeconds();this.full=request.full();this.goal=request.goal();}
        String status(){return (recovery.parked()?"RESTORATION PENDING; scene/profile ownership retained; use Undo. ":"Tuning ")+goal.label()+" "+(stage==null?"preparing":stage)+" "+trial+" pose="+pose+" warm="+warm+" samples="+window.size()+" elapsed="+(System.nanoTime()-started)/1_000_000_000L+"/"+budget+"s measured="+measured.size()+"/"+OptimizationId.values().length+(continuationUnavailable.isEmpty()?"":"; Full single segment: "+continuationUnavailable)+(focusPaused?"; paused until game regains focus":"")+(failure.isEmpty()?"":"; "+failure)+(journal==null?"":"; "+journal.directory);}
        void start()throws Exception {
            expectedSelectedResources=captureSelectedResources(client.getResourcePackRepository().getSelectedIds());
            if(client.level==null||client.getCameraEntity()==null)throw new IllegalStateException("A ready private singleplayer scene is required");
            bands=rawBands();terrainPolicy=rawTerrainPolicy();effectOptions=Map.copyOf(ShaderLodEffectConfig.tuningValues());
            var capturedOriginal=new TuningJournal.Original(OptimizationConfig.activeFamily(),ShaderLodConfig.ENABLED.get(),ShaderLodConfig.PRESET.get(),OptimizationConfig.snapshot(),client.options.enableVsync().get(),client.options.framerateLimit().get(),client.getWindow().getFramerateLimit(),selectedPack(),TuningSourceIdentity.capture(Iris.getShaderpacksDirectory(),selectedPack()));
            var pins=MeasurementPins.capture(settings());TuningApplyPins.requireValid(pins);
            // Invalid initial provenance refuses before this job owns mutable state or recovery.
            original=capturedOriginal;
            candidates.addAll(TuningCandidates.ordered(original.family(),goal));scheduledCount=candidates.size();
            identityPins=Map.copyOf(pins);journal=new TuningJournal(parent(),original,pins,budget,full);initialReloadCount=RuntimeMeasurement.reloadCount();
            journal.metadata.setProperty("tuning_goal",goal.name());journal.metadata.setProperty("tuning_engine_preset",goal.enginePreset().name());
            journal.metadata.setProperty("tuning_policy",TuningTierPolicy.VERSION);recordBands("initial");
            effectOptions.forEach((key,value)->journal.metadata.setProperty("initial.effect_option."+key,value));
            journal.metadata.setProperty("top_level_pins_scope","Pre-run original state; effective candidate settings are recorded under each apply prefix");
            journal.metadata.setProperty("timing_vsync","false");journal.metadata.setProperty("timing_frame_limit","260");
            journal.metadata.setProperty("loaded_mods",net.neoforged.fml.ModList.get().getMods().stream().map(m->m.getModId()+"="+m.getVersion()).sorted().collect(java.util.stream.Collectors.joining(",")));
            journal.metadata.setProperty("scene_version",BenchmarkWorld.active()?BenchmarkWorld.version():"private-user-scene");
            journal.metadata.setProperty("scenario_protocol",BenchmarkScenario.VERSION+": authored pose/environment definitions; tuner window budgets are separate; DH/work/readiness unqualified until observed");
            if(BenchmarkWorld.active())for(int i=0;i<BenchmarkWorld.poseCount();i++)journal.metadata.setProperty("pose."+i,BenchmarkWorld.poseName(i));
            journal.metadata.setProperty("initial.selected_resource_ids",expectedSelectedResources.toString());
            journal.metadata.setProperty("window_policy","screen warm2s/60frames sample3s/180frames; confirm warm3s/90 sample5s/240; final warm4s/120 sample8s/360; both duration and valid frames required; one pre-final window cutoff retry; no final/holdout retry");
            journal.metadata.setProperty("motion_validation","planned repeated 80-tick native-Off/candidate/native-return; wall-time route coverage requires evidence; timing does not certify visual quality");journal.save();
            lease=SceneLease.prepare();lease.freeze();
            TimingVideoSettings.apply(260,false);
            previousObserverMode=RuntimeMeasurement.observerMode();
            RuntimeMeasurement.observerMode(RuntimeMeasurement.ObserverMode.PERFORMANCE);
            journal.metadata.setProperty("observer_mode","PERFORMANCE: coarse clocks; GPU queries disabled");
            journal.metadata.setProperty("repeat_unit","One live JVM session; repeated windows/blocks are NOT independent JVM repeats");
            journal.metadata.setProperty("analysis_version",BenchmarkAnalysis.ANALYSIS_VERSION);
            journal.metadata.setProperty("analysis_configuration",analysisConfiguration.canonicalDescription());
            journal.metadata.setProperty("known_effect_control","UNTESTED: no injected, scoped known-effect calibration");
            journal.metadata.setProperty("budget_policy","Admission reserves planned screen + confirmation + all final brackets + motion + 20s recovery; progress cutoffs still use global hard deadline; no final retry");
            journal.metadata.setProperty("confirmation_order","Repeated baseline/candidate/return brackets; session-local; no counterbalance or JVM independence claim");
            journal.metadata.setProperty("quality_verdict","UNTESTED: timing does not certify visual quality");
            journal.metadata.setProperty("compatibility_verdict","UNTESTED: no tuple-specific DH/PBR evidence joined");
            confirmation=new TuningConfirmation(BenchmarkWorld.active()?"pose-"+(BenchmarkWorld.poseCount()-1):"UNAVAILABLE-private-scene");
            journal.confirmation=confirmation;journal.sessionId=sessionId;
            RuntimeMeasurement.request(true);
            for(var id:TuningCandidates.UNAVAILABLE){covered.add(id);journal.note(id.key(),TuningPolicy.Verdict.UNAVAILABLE,TuningCandidates.unavailableReason(id));}
            stage=Stage.NATIVE;trial="initial-native-Off";setPose(0);apply(false,OptimizationSnapshot.noneRequested());
            nativeEffectOptions=nativeOptions();
            for(var id:OptimizationId.values()) {
                var entry=TuningEligibility.classify(original.family(),id,nativeEffectOptions);
                journal.metadata.setProperty("request."+id.key()+".applicability",entry.state().name());
                journal.metadata.setProperty("request."+id.key()+".applicability_reason",entry.reason());
                var excluded=TuningTierPolicy.rejection(goal,original.family(),id,nativeEffectOptions,effectOptions);
                journal.metadata.setProperty("request."+id.key()+".goal_policy",excluded==null?"ELIGIBLE_REQUEST: runtime permission and pixels remain unproved":excluded);
            }
            if(full) {
                if(!BenchmarkWorld.active())continuationUnavailable="unversioned private scene";
                else if(!TuningContinuation.resourcesAllowResume(client.getResourcePackRepository().getSelectedIds()))
                    continuationUnavailable="external resource content is not bound by its pack ID; continuation disabled";
                if(continuationUnavailable.isEmpty())prepareContinuation();
                else journal.metadata.setProperty("full_continuation","UNAVAILABLE_SINGLE_SEGMENT: "+continuationUnavailable);
            }
            beginWindow();
        }
        void prepareContinuation()throws Exception {
            // Capture after real native-Off selection: generated graph flags and temporary
            // archive paths must not invalidate an otherwise identical later segment.
            var pins=MeasurementPins.capture("full-continuation");TuningApplyPins.requireValid(pins);var scope=new TreeMap<String,String>();
            for(String key:List.of("gpu_vendor","gpu_renderer","gl_version","resolution","render_distance","java","os","minecraft_options","mod_artifact_sha256"))
                scope.put(key,pins.getOrDefault(key,"unavailable"));
            scope.put("analysis_version",BenchmarkAnalysis.ANALYSIS_VERSION);
            scope.put("analysis_configuration",analysisConfiguration.canonicalDescription());
            scope.put("observer_mode","PERFORMANCE");scope.put("holdout_case",confirmation.heldOutCase());
            scope.put("goal",goal.name());scope.put("engine_preset",goal.enginePreset().name());scope.put("policy",TuningTierPolicy.VERSION);scope.put("raw_bands",bands.toString());scope.put("terrain_policy",terrainPolicy.toString());scope.put("effect_options",new TreeMap<>(effectOptions).toString());
            scope.put("user_video",original.fps()+":"+original.vsync()+":"+original.windowFps());
            scope.put("family",original.family());scope.put("original_source",original.source().originalSourceSha256());scope.put("original_file",original.source().originalFile());
            scope.put("native_options",new TreeMap<>(nativeOptions()).toString());
            scope.put("scene_version",BenchmarkWorld.active()?BenchmarkWorld.version():"private-user-scene");
            scope.put("loaded_mods",journal.metadata.getProperty("loaded_mods"));
            scope.put("resources",client.getResourcePackRepository().getSelectedIds().toString());
            scope.put("applicability",TuningEligibility.signature(original.family(),nativeEffectOptions));
            scope.put("inventory",candidates.stream().map(c->c.name()+":"+c.on().stream().map(OptimizationId::key).sorted().toList()+":"+c.off().stream().map(OptimizationId::key).sorted().toList()).toList().toString());
            continuationFile=parent().resolve("full-progress-"+original.family()+"-"+goal.name().toLowerCase(Locale.ROOT)+".properties");
            var saved=Files.exists(continuationFile)?TuningJournal.properties(continuationFile):new Properties();
            continuation=new TuningContinuation(TuningContinuation.scope(scope),candidates.stream().map(TuningCandidates.Candidate::name).toList(),saved,goal.enginePreset().name());
            if(!continuation.acceptsStart(original.enabled(),original.preset().name(),original.flags())
                    ||!TuningTierPolicy.rejectedVector(goal,original.family(),continuation.incumbent,nativeEffectOptions,effectOptions).isEmpty()) {
                continuation=new TuningContinuation(TuningContinuation.scope(scope),candidates.stream().map(TuningCandidates.Candidate::name).toList(),new Properties(),goal.enginePreset().name());
                journal.metadata.setProperty("full_restart_reason","Start profile/core or goal policy changed; prior coverage/Undo/incumbent not inherited");
            }
            if(continuation.completed.size()>=scheduledCount) {
                continuation.completed.clear();continuation.resumed=false;continuation.originalRecovery.clear();
            }
            if(continuation.resumed&&!continuation.originalRecovery.isEmpty()) {
                var campaign=TuningJournal.read(continuation.originalRecovery);
                if(!campaign.family().equals(original.family()))throw new IllegalStateException("Full campaign recovery family mismatch");
                campaign.source().requireCurrent(Iris.getShaderpacksDirectory(),selectedPack());
                var pending=new Properties();pending.putAll(continuation.originalRecovery);pending.setProperty("state","PENDING");pending.setProperty("run_directory",journal.directory.toString());
                TuningJournal.atomic(journal.activeFile,pending);TuningJournal.atomic(journal.directory.resolve("undo-original.properties"),pending);
                original=campaign;
            } else {
                var pending=TuningJournal.properties(journal.activeFile);
                for(String key:pending.stringPropertyNames())if(!key.startsWith("original_"))continuation.originalRecovery.setProperty(key,pending.getProperty(key));
            }
            best=continuation.incumbent;
            journal.metadata.setProperty("full_continuation",continuation.resumed?"RESUMED: fresh calibration and final proof required":"NEW: source/options/tuple/inventory scope");
            journal.metadata.setProperty("full_scope",continuation.scope);
            journal.metadata.setProperty("full_prior_completed",Integer.toString(continuation.completed.size()));
            journal.metadata.setProperty("full_incumbent_requests",best.flags().toString());journal.save();
        }
        void candidateComplete(){if(continuation!=null&&selected!=null&&index<=scheduledCount)continuation.completed.add(selected.name());}
        TuningBands rawBands(){return new TuningBands(ShaderLodConfig.NEAR.get(),ShaderLodConfig.FAR.get(),ShaderLodConfig.TRANSITION.get(),ShaderLodConfig.DEBUG.get());}
        TuningPreset.TerrainPolicy rawTerrainPolicy(){return new TuningPreset.TerrainPolicy(ShaderLodConfig.TERRAIN_VANILLA_CHUNK_CAP.get(),ShaderLodConfig.TERRAIN_WARMUP_SECONDS.get());}
        void requireBands(){
            if(bands!=null)bands.requireSame(rawBands());
            if(terrainPolicy!=null&&!terrainPolicy.equals(rawTerrainPolicy()))throw new IllegalStateException("Terrain cap/warmup changed externally; tuning aborted and user edits retained");
            if(!effectOptions.equals(ShaderLodEffectConfig.tuningValues()))throw new IllegalStateException("Effect modes/bands/ratios/protection changed externally; tuning aborted and user edits retained");
        }
        void recordBands(String prefix){var actual=rawBands();journal.metadata.setProperty(prefix+".near",Double.toString(actual.near()));journal.metadata.setProperty(prefix+".far",Double.toString(actual.far()));journal.metadata.setProperty(prefix+".transition",Double.toString(actual.transition()));journal.metadata.setProperty(prefix+".debug",Boolean.toString(actual.debug()));var terrain=rawTerrainPolicy();journal.metadata.setProperty(prefix+".terrain_cap",Integer.toString(terrain.chunkCap()));journal.metadata.setProperty(prefix+".terrain_warmup_seconds",Integer.toString(terrain.warmupSeconds()));}
        String settings(){return "enabled="+ShaderLodConfig.ENABLED.get()+";preset="+ShaderLodConfig.PRESET.get()+";raw_bands="+rawBands()+";terrain="+rawTerrainPolicy()+";effects="+new TreeMap<>(ShaderLodEffectConfig.tuningValues());}
        long elapsed(){return System.nanoTime()-started;}
        void tick()throws Exception {
            if(restoring){if(!recovery.parked()&&System.nanoTime()>=nextRestoreAt)restore();return;}
            if(cancelled){fail("Cancelled");return;}
            if(!client.isWindowActive()) {
                readinessPause.pause(System.nanoTime());
                if(!focusPaused){RuntimeMeasurement.pauseWindow();RuntimeMeasurement.invalidate();focusPaused=true;}
                if(elapsed()>(budget-20L)*1_000_000_000L)fail("Budget elapsed while the game was unfocused");
                return;
            }
            if(elapsed()>(budget-20L)*1_000_000_000L){fail("Wall-time budget exhausted before final proof; original settings restored");return;}
            if(stage==Stage.TRIAL&&elapsed()>(budget-finalReserveSeconds())*1_000_000_000L){
                RuntimeMeasurement.pauseWindow();windowInvalidity="candidate stopped at final reserve";flushWindow();
                journal.note(trial,TuningPolicy.Verdict.INCONCLUSIVE,"Candidate stopped at final-confirmation reserve; unfinished search evidence cannot be adopted");
                beginFinal();return;
            }
            if(client.screen instanceof net.minecraft.client.gui.screens.ChatScreen) {
                readinessPause.pause(System.nanoTime());
                if(!chatPaused){RuntimeMeasurement.pauseWindow();RuntimeMeasurement.invalidate();chatPaused=true;}
                RuntimeMeasurement.drain();
                if(elapsed()>(budget-20L)*1_000_000_000L)fail("Budget elapsed while chat was open");
                return;
            }
            if((focusPaused||chatPaused)&&(stage==Stage.FINAL||stage==Stage.MOTION)){
                fail("Frozen confirmation interrupted by focus/chat; allocation consumed, no holdout retry");return;
            }
            if(focusPaused||chatPaused){
                // Readiness cannot progress while rendering is deliberately paused. Keep
                // the elapsed active wait, and keep the independent whole-run wall budget.
                long paused=readinessPause.resume(System.nanoTime());readinessPauseNs+=paused;
                if(posePending)poseAt+=paused;
                if(meshPending){meshAt+=paused;meshPauseNs+=paused;}
                journal.metadata.setProperty("readiness_input_pause_ns",Long.toString(readinessPauseNs));
                focusPaused=false;chatPaused=false;
                windowInvalidity="input pause interrupted capture; excluded from decisions";flushWindow();retryCount++;
                if(stage==Stage.MOTION){motionStarted=false;motionQueued=false;setPose(0);}
                beginWindow();
            }
            if(posePending) {
                if(client.level!=world||lease==null||!lease.valid())throw new IllegalStateException("Scene ownership changed while settling pose");
                if(System.nanoTime()-poseAt>8_000_000_000L)throw new IllegalStateException("Pose did not settle within 8 seconds");
                if(!BenchmarkWorld.poseReady())return;
                posePending=false;expectedScene=MeasurementPins.scene();beginWindow();return;
            }
            if(rejectOwnedSildurSpatialFallback())return;
            if(rejectOwnedMakeUpSpatialFallback())return;
            if(rejectOwnedAoFallback())return;
            validateLive();
            if(meshPending) {
                if(System.nanoTime()-meshAt>12_000_000_000L)throw new IllegalStateException("Reload mesh readiness exceeded 12s wall cutoff");
                if(!client.levelRenderer.hasRenderedAllSections())return;
                long activeMeshWait=System.nanoTime()-meshAt;meshPending=false;
                journal.metadata.setProperty("apply-"+applyCalls+".mesh_settle_active_ns",Long.toString(activeMeshWait));
                journal.metadata.setProperty("apply-"+applyCalls+".mesh_settle_wall_ns",Long.toString(activeMeshWait+meshPauseNs));beginWindow();return;
            }
            if(elapsed()>(budget-20L)*1_000_000_000L){fail("Wall-time budget exhausted before final proof; original settings restored");return;}
            RuntimeMeasurement.pollSamples();
            if(draining) {
                var tail=RuntimeMeasurement.drain();journal.samples(trial,"TAIL-EXCLUDED",pose,tail);
                if(RuntimeMeasurement.pendingQueries()==0){completeWindow();return;}
                if(System.nanoTime()-drainAt>2_000_000_000L){journal.note(trial,TuningPolicy.Verdict.INCONCLUSIVE,"GPU tail did not drain within 2s; invalidated explicitly");windowInvalidity="asynchronous tail failed to drain";RuntimeMeasurement.invalidate();fail(windowInvalidity);}
                return;
            }
            var samples=RuntimeMeasurement.drain();
            if(stage==Stage.MOTION&&!motionStarted) {
                RuntimeMeasurement.pauseWindow();
                if(!motionQueued){RuntimeMeasurement.invalidate();BenchmarkWorld.beginFlythrough(80);motionQueued=true;motionAt=System.nanoTime();return;}
                if(System.nanoTime()-motionAt>8_000_000_000L)throw new IllegalStateException("Motion reset/history dwell exceeded 8s cutoff");
                if(!BenchmarkWorld.flythroughStarted())return;
                // The acknowledged reset and 20-client-tick rendered dwell warm native histories while clocks are paused.
                motionStarted=true;motionInitialInterval.reset();windowAt=System.nanoTime();firstSampleAt=0;
                journal.metadata.setProperty(trial+".motion_route",BenchmarkWorld.version()+":normalized-route-80ticks");
                journal.metadata.setProperty(trial+".motion_reset_and_history_wall_ns",Long.toString(System.nanoTime()-motionAt));
                RuntimeMeasurement.resumeWindow();return;
            }
            var warmRows=new ArrayList<FrameMeasurement.Sample>();
            var policy=windowPolicy();
            for(var sample:samples) {
                if(stage==Stage.MOTION&&motionInitialInterval.exclude(sample)){warmRows.add(sample);continue;}
                if(stage!=Stage.MOTION&&policy.warming(System.nanoTime()-windowAt,warm)){if(sample.frameWallNs()!=null&&sample.frameWallNs()>0)warm++;warmRows.add(sample);continue;}
                if(sample.frameWallNs()!=null&&sample.frameWallNs()>0){
                    if(firstSampleAt==0)firstSampleAt=System.nanoTime();
                    window.add(sample);validSampleFrames++;sampleNs+=sample.frameWallNs();
                } else window.add(sample);
            }
            excluded.addAll(warmRows); // File IO is deferred until timing is paused.
            if(stage==Stage.MOTION?BenchmarkWorld.flythroughDone():policy.complete(sampleNs,validSampleFrames)){
                sampleEndAt=System.nanoTime();RuntimeMeasurement.pauseWindow();draining=true;drainAt=System.nanoTime();return;
            }
            if(System.nanoTime()-windowAt>(stage==Stage.MOTION?12_000_000_000L:policy.cutoffNs())) {
                sampleEndAt=System.nanoTime();RuntimeMeasurement.pauseWindow();windowInvalidity="window progress cutoff";flushWindow();
                journal.note(trial,TuningPolicy.Verdict.INCONCLUSIVE,"window progress cutoff; warm="+warm+" sample="+window.size()+" measured_ns="+sampleNs);
                if(stage!=Stage.FINAL&&stage!=Stage.MOTION&&windowRetry++==0){retryCount++;trial+="-window-retry";if(stage==Stage.MOTION){motionStarted=false;motionQueued=false;setPose(0);}beginWindow();return;}
                journal.metadata.setProperty("search_outcome","INCOMPLETE: repeated window cutoff");
                if(stage==Stage.TRIAL){candidateComplete();apply(true,baseline);nextTrial();}else fail("Baseline/final window failed repeated wall-time progress cutoff");
            }
        }
        TuningWindow windowPolicy(){
            if(stage==Stage.FINAL)return TuningWindow.FINAL;
            if(stage==Stage.NATIVE||stage==Stage.SAFE||stage==Stage.REPEATABILITY||stage==Stage.TRIAL&&!confirm)return TuningWindow.SCREEN;
            return TuningWindow.CONFIRM;
        }

        void requireSelectedResources(){requireResourceSelection(expectedSelectedResources,client.getResourcePackRepository().getSelectedIds());}
        void validateLive() {
            requireBands();requireSelectedResources();
            if(lease==null||!lease.valid()||client.level!=world)throw new IllegalStateException("Private scene ownership/world changed");
            if(client.screen!=null)throw new IllegalStateException("GUI opened during tuning");
            if(Iris.getCurrentPack().orElse(null)!=expectedPack||!OptimizationConfig.activeFamily().equals(original.family()))throw new IllegalStateException("Shader pack/reload/profile changed externally");
            if(ShaderLodConfig.ENABLED.get()!=expectedEnabled||ShaderLodConfig.PRESET.get()!=expectedPreset||!OptimizationConfig.snapshot().equals(expectedFlags))throw new IllegalStateException("Core/request settings changed externally");
            if(client.options.enableVsync().get()||client.options.framerateLimit().get()!=260||client.getWindow().getFramerateLimit()!=260)throw new IllegalStateException("Timing video settings changed externally");
            if(!(stage==Stage.MOTION&&(motionStarted||motionQueued))&&!MeasurementPins.scene().equals(expectedScene))throw new IllegalStateException("Fixed camera changed externally");
        }
        boolean rejectOwnedSildurSpatialFallback()throws Exception {
            if(!"sildurs-vibrant-shaders".equals(original.family()))return false;
            var actualPack=Iris.getCurrentPack().orElse(null);
            if(actualPack==expectedPack)return false;
            var receipt=com.pp.shaderlod.optimization.sildurspatial.SildurSpatialRuntime.takeFallback();
            // A known safety reload is a refused activation, never permission to sample its fallback.
            if(receipt==null||stage!=Stage.TRIAL||phase!=1||candidate==null||baseline==null
                    ||candidate.enabled(OptimizationId.SPATIAL_LIGHTING_REUSE)==candidate.enabled(OptimizationId.SPATIAL_LIGHTING_RATE)
                    ||baseline.enabled(OptimizationId.SPATIAL_LIGHTING_REUSE)||baseline.enabled(OptimizationId.SPATIAL_LIGHTING_RATE)
                    ||client.level!=world||lease==null||!lease.valid()||client.screen!=null
                    ||!OptimizationConfig.activeFamily().equals(original.family())
                    ||ShaderLodConfig.ENABLED.get()!=expectedEnabled||!expectedEnabled
                    ||ShaderLodConfig.PRESET.get()!=expectedPreset||!OptimizationConfig.snapshot().equals(expectedFlags)
                    ||OptimizationConfig.generation()!=expectedFlagGeneration
                    ||!MeasurementPins.scene().equals(expectedScene)||Iris.isFallback()
                    ||!Objects.equals(selectedPack(),expectedSelectedName)||!Iris.getShaderPackOptionQueue().isEmpty())return false;
            var dimension=client.level.dimension().location();
            if(!receipt.matches(original.family(),expectedPack,actualPack,expectedPipeline,Iris.getPipelineManager().getPipelineNullable(),world,
                    new net.irisshaders.iris.shaderpack.materialmap.NamespacedId(dimension.getNamespace(),dimension.getPath()),
                    expectedFlagGeneration,expectedSelectedName,expectedEffectiveOptions,effectiveOptions()))return false;
            // Preserve the normal external settings/video/world checks except the positively identified pack replacement.
            requireBands();
            if(client.options.enableVsync().get()||client.options.framerateLimit().get()!=260||client.getWindow().getFramerateLimit()!=260)
                throw new IllegalStateException("Timing video settings changed externally");
            RuntimeMeasurement.pauseWindow();RuntimeMeasurement.invalidate();
            original.source().requireCurrent(Iris.getShaderpacksDirectory(),selectedPack());
            var fallbackPins=MeasurementPins.capture(settings());
            sourcePins.verify(true,TuningApplyPins.Graph.GENERATED,fallbackPins,nativeOptions());
            journal.pins(trial+"-owned-spatial-fallback",fallbackPins);
            journal.metadata.setProperty(trial+".owned_spatial_fallback",receipt.reason());
            // No fallback rows contribute to a decision. The normal Apply must acknowledge the full baseline first.
            journal.samples(trial,"OWNED-FALLBACK-EXCLUDED",pose,excluded);
            journal.samples(trial,"OWNED-FALLBACK-EXCLUDED",pose,window);
            window.clear();excluded.clear();windowFlushed=true;
            apply(true,baseline);validateLive();verifyRecoveredBaseline(receipt.reason());
            journal.note(trial,TuningPolicy.Verdict.UNAVAILABLE,"Owned "+original.family()+" native-spatial safety recovery; candidate refused: "+receipt.reason());
            for(var id:OptimizationId.values())if(candidate.enabled(id)!=baseline.enabled(id))
                journal.metadata.setProperty("request."+id.key()+".timing_status","UNAVAILABLE_OWNED_ACTIVATION_REFUSAL");
            candidateComplete();nextTrial();return true;
        }
        boolean rejectOwnedMakeUpSpatialFallback()throws Exception {
            if(!"makeup-ultra-fast-shaders".equals(original.family()))return false;
            var actualPack=Iris.getCurrentPack().orElse(null);
            if(actualPack==expectedPack)return false;
            requireSelectedResources();
            var receipt=com.pp.shaderlod.optimization.makeupspatial.MakeUpSpatialRuntime.takeFallback();
            // A known safety reload is a refused activation, never permission to sample its fallback.
            if(receipt==null||stage!=Stage.TRIAL||phase!=1||candidate==null||baseline==null
                    ||com.pp.shaderlod.optimization.makeupspatial.MakeUpSpatialFallback.requestCount(candidate.enabled(OptimizationId.SPATIAL_LIGHTING_REUSE),candidate.enabled(OptimizationId.SPATIAL_LIGHTING_RATE),candidate.enabled(OptimizationId.PIXEL_SCALE))!=1
                    ||baseline.enabled(OptimizationId.SPATIAL_LIGHTING_REUSE)||baseline.enabled(OptimizationId.SPATIAL_LIGHTING_RATE)||baseline.enabled(OptimizationId.PIXEL_SCALE)
                    ||client.level!=world||lease==null||!lease.valid()||client.screen!=null
                    ||!OptimizationConfig.activeFamily().equals(original.family())
                    ||ShaderLodConfig.ENABLED.get()!=expectedEnabled||!expectedEnabled
                    ||ShaderLodConfig.PRESET.get()!=expectedPreset||!OptimizationConfig.snapshot().equals(expectedFlags)
                    ||OptimizationConfig.generation()!=expectedFlagGeneration
                    ||!MeasurementPins.scene().equals(expectedScene)||Iris.isFallback()
                    ||!Objects.equals(selectedPack(),expectedSelectedName)||!Iris.getShaderPackOptionQueue().isEmpty()
                    ||!com.pp.shaderlod.optimization.makeupspatial.MakeUpSpatialRuntime.nativeEnvironment())return false;
            var dimension=client.level.dimension().location();
            if(!receipt.matches(original.family(),expectedPack,actualPack,expectedPipeline,Iris.getPipelineManager().getPipelineNullable(),world,
                    new net.irisshaders.iris.shaderpack.materialmap.NamespacedId(dimension.getNamespace(),dimension.getPath()),
                    expectedFlagGeneration,expectedSelectedName,expectedEffectiveOptions,effectiveOptions()))return false;
            // Preserve the normal external settings/video/world checks except the positively identified pack replacement.
            requireBands();
            if(client.options.enableVsync().get()||client.options.framerateLimit().get()!=260||client.getWindow().getFramerateLimit()!=260)
                throw new IllegalStateException("Timing video settings changed externally");
            RuntimeMeasurement.pauseWindow();RuntimeMeasurement.invalidate();
            original.source().requireCurrent(Iris.getShaderpacksDirectory(),selectedPack());
            var fallbackPins=MeasurementPins.capture(settings());
            sourcePins.verify(true,TuningApplyPins.Graph.GENERATED,fallbackPins,nativeOptions());
            journal.pins(trial+"-owned-spatial-fallback",fallbackPins);
            journal.metadata.setProperty(trial+".owned_spatial_fallback",receipt.reason());
            // No fallback rows contribute to a decision. The normal Apply must acknowledge the full baseline first.
            journal.samples(trial,"OWNED-FALLBACK-EXCLUDED",pose,excluded);
            journal.samples(trial,"OWNED-FALLBACK-EXCLUDED",pose,window);
            window.clear();excluded.clear();windowFlushed=true;
            apply(true,baseline);validateLive();verifyRecoveredBaseline(receipt.reason());
            journal.note(trial,TuningPolicy.Verdict.UNAVAILABLE,"Owned "+original.family()+" native-spatial safety recovery; candidate refused: "+receipt.reason());
            for(var id:OptimizationId.values())if(candidate.enabled(id)!=baseline.enabled(id))
                journal.metadata.setProperty("request."+id.key()+".timing_status","UNAVAILABLE_OWNED_ACTIVATION_REFUSAL");
            candidateComplete();nextTrial();return true;
        }
        boolean rejectOwnedAoFallback()throws Exception {
            var actualPack=Iris.getCurrentPack().orElse(null);
            if(actualPack==expectedPack)return false;
            var receipt=switch(original.family()) {
                case "solas-shader" -> com.pp.shaderlod.optimization.solasao.SolasAoRuntime.takeFallback();
                case "astralex" -> com.pp.shaderlod.optimization.astraao.AstraAoRuntime.takeFallback();
                case "makeup-ultra-fast-shaders" -> com.pp.shaderlod.optimization.makeupao.MakeUpAoRuntime.takeFallback();
                case "complementary-reimagined" -> com.pp.shaderlod.optimization.reimaginedao.ReimaginedAoRuntime.takeFallback();
                case "complementary-unbound" -> com.pp.shaderlod.optimization.unboundao.UnboundAoRuntime.takeFallback();
                case "sildurs-vibrant-shaders" -> com.pp.shaderlod.optimization.sildurao.SildurAoRuntime.takeFallback();
                case "bliss-shader" -> com.pp.shaderlod.optimization.blissao.BlissAoRuntime.takeFallback();
                case "bsl-shaders" -> com.pp.shaderlod.optimization.BslAoNativeRecovery.takeFallback();
                default -> null;
            };
            // A known safety reload is a refused activation, never permission to sample its fallback.
            if(receipt==null||stage!=Stage.TRIAL||phase!=1||candidate==null||baseline==null
                    ||!candidate.enabled(OptimizationId.AO_RENDER_SCALE)||baseline.enabled(OptimizationId.AO_RENDER_SCALE)
                    ||client.level!=world||lease==null||!lease.valid()||client.screen!=null
                    ||!OptimizationConfig.activeFamily().equals(original.family())
                    ||ShaderLodConfig.ENABLED.get()!=expectedEnabled||!expectedEnabled
                    ||ShaderLodConfig.PRESET.get()!=expectedPreset||!OptimizationConfig.snapshot().equals(expectedFlags)
                    ||OptimizationConfig.generation()!=expectedFlagGeneration
                    ||!MeasurementPins.scene().equals(expectedScene)||Iris.isFallback()
                    ||!Objects.equals(selectedPack(),expectedSelectedName)||!Iris.getShaderPackOptionQueue().isEmpty())return false;
            var dimension=client.level.dimension().location();
            if(!receipt.matches(original.family(),expectedPack,actualPack,expectedPipeline,Iris.getPipelineManager().getPipelineNullable(),world,
                    new net.irisshaders.iris.shaderpack.materialmap.NamespacedId(dimension.getNamespace(),dimension.getPath()),
                    expectedFlagGeneration,expectedSelectedName,expectedEffectiveOptions,effectiveOptions()))return false;
            // Preserve the normal external settings/video/world checks except the positively identified pack replacement.
            requireBands();
            if(client.options.enableVsync().get()||client.options.framerateLimit().get()!=260||client.getWindow().getFramerateLimit()!=260)
                throw new IllegalStateException("Timing video settings changed externally");
            RuntimeMeasurement.pauseWindow();RuntimeMeasurement.invalidate();
            original.source().requireCurrent(Iris.getShaderpacksDirectory(),selectedPack());
            sourcePins.verify(true,TuningApplyPins.Graph.GENERATED,MeasurementPins.capture(settings()),nativeOptions());
            journal.metadata.setProperty(trial+".owned_ao_fallback",receipt.reason());
            // No fallback rows contribute to a decision. The normal Apply must acknowledge the full baseline first.
            journal.samples(trial,"OWNED-FALLBACK-EXCLUDED",pose,excluded);
            journal.samples(trial,"OWNED-FALLBACK-EXCLUDED",pose,window);
            window.clear();excluded.clear();windowFlushed=true;
            apply(true,baseline);validateLive();verifyRecoveredBaseline(receipt.reason());
            journal.note(trial,TuningPolicy.Verdict.UNAVAILABLE,"Owned "+original.family()+" native-AO safety recovery; candidate refused: "+receipt.reason());
            for(var id:OptimizationId.values())if(candidate.enabled(id)!=baseline.enabled(id))
                journal.metadata.setProperty("request."+id.key()+".timing_status","UNAVAILABLE_OWNED_ACTIVATION_REFUSAL");
            candidateComplete();nextTrial();return true;
        }
        void apply(boolean enabled,OptimizationSnapshot flags)throws Exception {
            requireBands();requireSelectedResources();
            if(enabled) {
                var rejected=TuningTierPolicy.rejectedVector(goal,original.family(),flags,nativeEffectOptions,effectOptions);
                if(!rejected.isEmpty())throw new IllegalStateException("Complete goal vector refused: "+rejected);
            }
            RuntimeMeasurement.pauseWindow();RuntimeMeasurement.invalidate();
            long at=System.nanoTime(),reloadBefore=RuntimeMeasurement.reloadCount();applyCalls++;
            if(ShaderLodConfig.ENABLED.get()!=enabled||ShaderLodConfig.PRESET.get()!=goal.enginePreset())
                if(!ShaderLodSettings.apply(enabled,goal.enginePreset()))throw new IllegalStateException(ShaderLodSettings.failureMessage());
            var result=OptimizationEditTransaction.apply(flags,()->{long r=System.nanoTime();try{Iris.reload();}finally{reloadNs+=System.nanoTime()-r;reloadCalls++;}},Iris::isFallback,CompileOptionControl::open);
            long duration=System.nanoTime()-at;applyNs+=duration;journal.metadata.setProperty("apply-"+applyCalls+".requested_flags",flags.flags().toString());journal.metadata.setProperty("apply-"+applyCalls+".wall_ns",Long.toString(duration));
            if(!result.success())throw new CandidateApplyFailure(result.message(),result.recoveryComplete());
            TimingVideoSettings.apply(260,false);
            if(RuntimeMeasurement.reloadCount()>reloadBefore){meshPending=true;meshAt=System.nanoTime();meshPauseNs=0;}
            expectedEnabled=enabled;expectedPreset=goal.enginePreset();expectedFlags=flags;expectedPack=Iris.getCurrentPack().orElse(null);
            // Admit graph transitions only inside the original's strictly verified maintained pair.
            String selected=selectedPack();original.source().requireCurrent(Iris.getShaderpacksDirectory(),selected);
            var graph=selected.equals(original.source().originalFile())?TuningApplyPins.Graph.ORIGINAL:TuningApplyPins.Graph.GENERATED;
            // Keep every source/effective value in evidence, plus immutable per-graph and common native pins.
            var pins=MeasurementPins.capture(settings());journal.pins("apply-"+applyCalls,pins);
            sourcePins.verify(enabled,graph,pins,nativeOptions());
            expectedEffectiveOptions=effectiveOptions();expectedFlagGeneration=OptimizationConfig.generation();expectedSelectedName=selected;expectedPipeline=Iris.getPipelineManager().getPipelineNullable();
            if(expectedPipeline==null)throw new IllegalStateException("Apply did not prepare the actual world pipeline");
            String aoOption=AoNativeFallback.option(original.family());
            if(!AoNativeFallback.loadedGraphMatches(original.family(),enabled,
                    flags.enabled(OptimizationId.AO_RENDER_SCALE),expectedEffectiveOptions)) {
                // A synchronous safety refusal must not become the expected candidate and be timed as AO1.
                // This bounded recursive call restores AO0 (so cannot enter this branch again).
                String reason="Requested AO graph1 did not remain loaded: "+aoOption+"="+expectedEffectiveOptions.get(aoOption)
                    +"; "+OptimizationRuntimeContext.implementation(original.family(),OptimizationId.AO_RENDER_SCALE);
                if(stage==Stage.TRIAL&&phase==1&&baseline!=null&&!baseline.enabled(OptimizationId.AO_RENDER_SCALE)) {
                    apply(true,baseline);validateLive();verifyRecoveredBaseline(reason);
                    throw new CandidateApplyFailure(reason,true);
                }
                throw new IllegalStateException(reason+"; incumbent/final/motion graph cannot be sampled");
            }
            if(!com.pp.shaderlod.optimization.sildurspatial.SildurSpatialFallback.loadedGraphMatches(original.family(),enabled,
                    flags.enabled(OptimizationId.SPATIAL_LIGHTING_REUSE),flags.enabled(OptimizationId.SPATIAL_LIGHTING_RATE),expectedEffectiveOptions)) {
                String reason="Requested Sildur spatial graph did not remain loaded: reuse="+
                    expectedEffectiveOptions.get(com.pp.shaderlod.optimization.sildurspatial.SildurSpatialContract.REUSE)+" rate="+
                    expectedEffectiveOptions.get(com.pp.shaderlod.optimization.sildurspatial.SildurSpatialContract.RATE)+"; "+
                    com.pp.shaderlod.optimization.sildurspatial.SildurSpatialRuntime.status();
                if(stage==Stage.TRIAL&&phase==1&&baseline!=null&&
                        flags.enabled(OptimizationId.SPATIAL_LIGHTING_REUSE)!=flags.enabled(OptimizationId.SPATIAL_LIGHTING_RATE)&&
                        !baseline.enabled(OptimizationId.SPATIAL_LIGHTING_REUSE)&&!baseline.enabled(OptimizationId.SPATIAL_LIGHTING_RATE)) {
                    apply(true,baseline);validateLive();verifyRecoveredBaseline(reason);
                    throw new CandidateApplyFailure(reason,true);
                }
                throw new IllegalStateException(reason+"; incumbent/final/motion graph cannot be sampled");
            }
            if(!com.pp.shaderlod.optimization.makeupspatial.MakeUpSpatialFallback.loadedGraphMatches(original.family(),enabled,
                    flags.enabled(OptimizationId.SPATIAL_LIGHTING_REUSE),flags.enabled(OptimizationId.SPATIAL_LIGHTING_RATE),flags.enabled(OptimizationId.PIXEL_SCALE),expectedEffectiveOptions)) {
                // No owned transition receipt is bound to this synchronous mismatch. Never turn it into benign refusal.
                throw new IllegalStateException("Requested MakeUp spatial graph did not remain loaded; "+
                    com.pp.shaderlod.optimization.makeupspatial.MakeUpSpatialRuntime.status()+"; unmatched Apply graph cannot be sampled");
            }
            requireSelectedResources();
            journal.metadata.setProperty("apply-"+applyCalls+".selected_resource_ids",expectedSelectedResources.toString());
            journal.metadata.setProperty("owned_simulation",lease.provenance());
            // A synchronous driver reload cannot advance client pose acknowledgments.
            // Charge it to Apply, then start each asynchronous readiness timeout.
            if(posePending)poseAt=System.nanoTime();
            if(meshPending)meshAt=System.nanoTime();
        }
        Map<String,String> effectiveOptions() {
            var pack=Iris.getCurrentPack().orElseThrow();var opts=pack.getShaderPackOptions();var values=opts.getOptionValues();var all=new TreeMap<String,String>();
            opts.getOptionSet().getBooleanOptions().forEach((k,v)->all.put(k,Boolean.toString(values.getBooleanValueOrDefault(k))));
            opts.getOptionSet().getStringOptions().forEach((k,v)->all.put(k,values.getStringValueOrDefault(k)));
            return Map.copyOf(all);
        }
        Map<String,String> nativeOptions() {
            var all=new TreeMap<>(effectiveOptions());
            for(var id:OptimizationId.values())for(var key:CompileOptionControl.controlledOptions(id))all.remove(key);
            return Map.copyOf(all);
        }
        void setPose(int n) {
            pose=n;
            if(BenchmarkWorld.active()){RuntimeMeasurement.pauseWindow();RuntimeMeasurement.invalidate();BenchmarkWorld.pose(n);posePending=true;poseAt=System.nanoTime();}
            expectedScene=MeasurementPins.scene();
        }
        void beginWindow()throws Exception {
            journal.metadata.setProperty("window."+trial+".phase-"+phase+".focused",Boolean.toString(client.isWindowActive()));
            warm=0;validSampleFrames=0;sampleNs=0;sampleEndAt=0;window.clear();excluded.clear();windowFlushed=false;windowInvalidity="";firstSampleAt=0;draining=false;windowAt=System.nanoTime();expectedScene=MeasurementPins.scene();
            journal.metadata.setProperty("current",status());journal.save();
            if(posePending||meshPending||stage==Stage.MOTION&&!motionStarted)RuntimeMeasurement.pauseWindow();
            else {journal.metadata.setProperty(trial+".phase-"+phase+".pose-"+pose+".scene",expectedScene);RuntimeMeasurement.resumeWindow();}
        }
        void flushWindow()throws Exception {
            if(windowFlushed)return;windowFlushed=true;
            String prefix=trial+".phase-"+phase+".pose-"+pose;
            journal.metadata.setProperty(prefix+".window_wall_ns",Long.toString(System.nanoTime()-windowAt));
            if(firstSampleAt!=0){journal.metadata.setProperty(prefix+".warmup_wall_ns",Long.toString(firstSampleAt-windowAt));journal.metadata.setProperty(prefix+".sample_wall_ns",Long.toString((sampleEndAt==0?System.nanoTime():sampleEndAt)-firstSampleAt));}
            journal.metadata.setProperty(prefix+".sample_interval_ns",Long.toString(sampleNs));
            String captureTrial=trial+"-capture-"+captureSequence++;
            journal.samples(captureTrial,"WARMUP-EXCLUDED",pose,excluded);journal.samples(captureTrial,"PHASE-"+phase,pose,window);
            String comparison=stage==Stage.FINAL?"final-pose-"+pose:stage==Stage.MOTION?"final-matched-motion":stage==Stage.REPEATABILITY?"same-configuration-no-reload":trial;
            journal.capture(comparison,phase,pose,window,casePolicy(),captureStratum(),
                stage==Stage.FINAL?(confirmation.heldOutCase().equals(caseId())?BenchmarkResult.Stage.HOLDOUT:BenchmarkResult.Stage.CONFIRMATION):stage==Stage.MOTION?BenchmarkResult.Stage.CONFIRMATION:
                stage==Stage.TRIAL?(confirm?BenchmarkResult.Stage.CONFIRMATION:BenchmarkResult.Stage.SEARCH):BenchmarkResult.Stage.CONTROL,
                stage==Stage.MOTION,expectedEnabled,expectedFlags,RuntimeMeasurement.reloadCount(),client.level.dimension().location().toString(),expectedEffectiveOptions,client.options.simulationDistance().get(),windowInvalidity);excluded.clear();
            // Called after sampling pauses: retain actual AO producer/consumer status with its cost.
            if(expectedFlags!=null&&expectedFlags.enabled(OptimizationId.AO_RENDER_SCALE))
                journal.metadata.setProperty(prefix+".ao_runtime",OptimizationRuntimeContext.implementation(original.family(),OptimizationId.AO_RENDER_SCALE));
        }
        void completeWindow()throws Exception {
            flushWindow();
            if(stage==Stage.MOTION){completeMotion();return;}
            if(stage==Stage.NATIVE){windowRetry=0;stage=Stage.SAFE;trial="safe-Performance-all-Off";apply(true,best);beginWindow();return;}
            if(stage==Stage.SAFE){windowRetry=0;stage=Stage.REPEATABILITY;bracket.clear();repeatedBrackets.clear();round=0;phase=0;trial="repeatability-no-reload";beginWindow();return;}
            if(stage==Stage.REPEATABILITY){completeRepeatability();return;}
            if(stage==Stage.FINAL){completeFinal();return;}
            bracket.add(snapshotWindow());
            if(phase==0){windowRetry=0;phase=1;apply(true,candidate);beginWindow();return;}
            if(phase==1){windowRetry=0;phase=2;apply(true,baseline);beginWindow();return;}
            repeatedBrackets.add(rawBracket(trial+"-round-"+round,bracket));
            if(++round<(confirm?Math.max(2,casePolicy().analysis().minimumMatchedBrackets()):casePolicy().analysis().minimumMatchedBrackets())){
                phase=0;windowRetry=0;bracket.clear();apply(true,baseline);beginWindow();return;
            }
            var shared=analyze(trial,repeatedBrackets,BenchmarkAnalysis.ControlKind.OPTIMIZATION);
            journal.decision(trial,shared);
            var decision=TuningPolicy.facade(shared);
            for(var id:OptimizationId.values())if(candidate.enabled(id)!=baseline.enabled(id)) {
                measured.add(id);journal.metadata.setProperty("request."+id.key()+".timing_status","TIMED_REQUEST_DELTA_"+decision.verdict());
            }
            if(decision.verdict()==TuningPolicy.Verdict.INCONCLUSIVE)journal.metadata.setProperty("search_outcome","INCOMPLETE: unresolved candidate brackets");
            if(decision.verdict()==TuningPolicy.Verdict.POSITIVE) {
                if(!confirm){confirm=true;trialRetry=0;windowRetry=0;trial=selected.name()+"-confirm";phase=0;round=0;bracket.clear();repeatedBrackets.clear();
                    int confirmPose=BenchmarkWorld.active()&&BenchmarkWorld.poseCount()>2?1:0;
                    confirmation.searched("pose-"+confirmPose);setPose(confirmPose);beginWindow();return;}
                best=candidate;apply(true,best);
            }
            candidateComplete();nextTrial();
        }
        void completeRepeatability()throws Exception {
            bracket.add(snapshotWindow());windowRetry=0;
            if(++phase<3){beginWindow();return;}
            repeatedBrackets.add(rawBracket("null-round-"+round,bracket));
            if(++round<casePolicy().analysis().minimumMatchedBrackets()){
                phase=0;bracket.clear();beginWindow();return;
            }
            var d=analyze("same-configuration-no-reload",repeatedBrackets,BenchmarkAnalysis.ControlKind.KNOWN_NULL);
            journal.decision("same-configuration-no-reload",d);
            // A null is never a POSITIVE optimization. Require observed envelope and drift within
            // this declared control policy; an apparent null effect suspends search, without retries.
            nullControlPassed=d.validity()==BenchmarkAnalysis.Validity.VALID
                &&d.lowerEnvelopeMs()!=null&&d.upperEnvelopeMs()!=null
                &&Math.abs(d.lowerEnvelopeMs())<=casePolicy().criterion().maximumReturnDriftMs()
                &&Math.abs(d.upperEnvelopeMs())<=casePolicy().criterion().maximumReturnDriftMs()
                &&!casePolicy().analysis().independenceEvidence().isBlank()
                &&d.reasons().stream().noneMatch(r->r.contains("control failure")||r.contains("insufficient"));
            journal.metadata.setProperty("repeatability",nullControlPassed?"PASS: within-session unchanged-state control only":"INCONCLUSIVE/FAIL: search suspended; no retry until green");
            if(nullControlPassed){nextTrial();return;}
            fail("Unchanged-state control unqualified; original settings restored");
        }
        String caseId(){return stage==Stage.MOTION?"motion-route-80ticks":"pose-"+pose;}
        String controlClass(){return stage==Stage.REPEATABILITY?"unchanged-no-reload":stage==Stage.TRIAL?"masterOn-request-reload":"master-transition-reload";}
        TuningPolicy.CasePolicy casePolicy(){return analysisConfiguration.forCase(caseId(),controlClass());}
        BenchmarkAnalysis.Stratum captureStratum(){
            var pins=new TreeMap<String,String>(identityPins);
            pins.remove("scene");pins.remove("settings");pins.remove("effective_options");
            pins.put("case",caseId());pins.put("control_class",controlClass());
            pins.put("route",stage==Stage.MOTION?"normalized-route-80ticks":"fixed-pose");
            pins.put("gpu_query_mode","performance_off");pins.put("observer","PERFORMANCE");pins.put("clock","actual-frame-start-interval-ns");
            pins.put("temporal","native animations and AA; no diagnostic replay");pins.put("temperature","within-session warmed; cache reset unproved");
            return new BenchmarkAnalysis.Stratum(pins);
        }
        List<FrameMeasurement.Sample> snapshotWindow(){
            var rows=Collections.unmodifiableList(new ArrayList<>(window));captureIds.put(rows,journal.lastWindowId());return rows;
        }
        BenchmarkAnalysis.Bracket rawBracket(String id,List<List<FrameMeasurement.Sample>> rows){
            var raw=TuningPolicy.bracket(id,sessionId,captureStratum(),rows.get(0),rows.get(1),rows.get(2),casePolicy());
            return new BenchmarkAnalysis.Bracket(id,sessionId,capturedWindow(raw.before(),rows.get(0)),capturedWindow(raw.candidate(),rows.get(1)),capturedWindow(raw.returned(),rows.get(2)));
        }
        BenchmarkAnalysis.Window capturedWindow(BenchmarkAnalysis.Window window,List<FrameMeasurement.Sample> rows){
            return new BenchmarkAnalysis.Window(Objects.requireNonNull(captureIds.remove(rows),"capture/window join missing"),window.stratum(),window.summary());
        }
        BenchmarkAnalysis.Decision analyze(String id,List<BenchmarkAnalysis.Bracket> evidence,BenchmarkAnalysis.ControlKind kind){
            // ACTIVE names the guarded configuration/total-frame comparison. Executed feature
            // work remains UNKNOWN in every exported window until a scoped observer joins it.
            return TuningPolicy.analyze(id,evidence,casePolicy(),kind,BenchmarkAnalysis.Availability.AVAILABLE,
                BenchmarkAnalysis.Activation.ACTIVE,List.of());
        }
        void rejectUnavailable(String reason)throws Exception {
            // Only a failed candidate activation with positively completed transaction recovery is skippable.
            // Source changes, fallback, baseline failures and incomplete recovery still terminate the run.
            verifyRecoveredBaseline(reason);
            journal.note(trial,TuningPolicy.Verdict.UNAVAILABLE,"Candidate activation refused; baseline recovery verified: "+reason);
            candidateComplete();apply(true,baseline);nextTrial();
        }
        void verifyRecoveredBaseline(String reason)throws Exception {
            original.source().requireCurrent(Iris.getShaderpacksDirectory(),selectedPack());
            if(Iris.isFallback()||!OptimizationConfig.activeFamily().equals(original.family())||
                    !OptimizationConfig.snapshot().equals(baseline)||!ShaderLodConfig.ENABLED.get()||
                    ShaderLodConfig.PRESET.get()!=goal.enginePreset())
                throw new IllegalStateException("Candidate recovery did not restore the owned baseline: "+reason);
        }
        void nextTrial()throws Exception {
            if(elapsed()>(budget-finalReserveSeconds()-candidateReserveSeconds())*1_000_000_000L){beginFinal();return;}
            while(index<candidates.size()) {
                selected=candidates.get(index++);
                if(continuation!=null&&index<=scheduledCount&&continuation.completed.contains(selected.name())) {
                    journal.note(selected.name(),TuningPolicy.Verdict.SKIPPED,"Completed in a prior scope-matched Full segment; old timing not reused");continue;
                }
                candidate=selected.apply(best);baseline=best;
                var blocked=TuningTierPolicy.rejectedVector(goal,original.family(),candidate,nativeEffectOptions,effectOptions);
                if(!blocked.isEmpty()) {candidateComplete();journal.note(selected.name(),TuningPolicy.Verdict.SKIPPED,String.join("; ",blocked));continue;}
                if(candidate.equals(baseline)) {candidateComplete();journal.note(selected.name(),TuningPolicy.Verdict.SKIPPED,"Already present in incumbent; fresh combined native-relative final proof still required");continue;}
                covered.addAll(selected.on());covered.addAll(selected.off());
                for(var id:OptimizationId.values())if(candidate.enabled(id)!=baseline.enabled(id))covered.add(id);
                attemptCount++;journal.metadata.setProperty("attempted_candidates",Integer.toString(attemptCount));
                confirmation.searched("pose-0");
                stage=Stage.TRIAL;round=0;repeatedBrackets.clear();confirm=false;trialRetry=0;windowRetry=0;trial=selected.name()+"-screen";phase=0;bracket.clear();setPose(0);apply(true,baseline);beginWindow();return;
            }
            if(!cleanup){cleanup=true;candidates.addAll(TuningCandidates.cleanup(original.family(),best));if(index<candidates.size()){nextTrial();return;}}
            beginFinal();
        }
        long candidateReserveSeconds(){
            var search=analysisConfiguration.forCase("pose-0","masterOn-request-reload");
            var confirmPolicy=analysisConfiguration.forCase(BenchmarkWorld.active()&&BenchmarkWorld.poseCount()>2?"pose-1":"pose-0","masterOn-request-reload");
            return TuningWindow.candidateReserveSeconds(search.analysis().minimumMatchedBrackets(),Math.max(2,confirmPolicy.analysis().minimumMatchedBrackets()));
        }
        long finalReserveSeconds(){
            int poses=BenchmarkWorld.active()?(full?BenchmarkWorld.poseCount():1):1;
            int repeats=2;
            for(int p=0;p<(BenchmarkWorld.active()?BenchmarkWorld.poseCount():1);p++)
                repeats=Math.max(repeats,analysisConfiguration.forCase("pose-"+p,"master-transition-reload").analysis().minimumMatchedBrackets());
            int motionRepeats=Math.max(2,analysisConfiguration.forCase("motion-route-80ticks","master-transition-reload").analysis().minimumMatchedBrackets());
            return TuningWindow.finalReserveSeconds(poses,repeats,motionRepeats);
        }
        int finalCasePose(int n){return full?n:BenchmarkWorld.poseCount()-1;}
        void beginFinal()throws Exception {
            if(best.equals(OptimizationSnapshot.noneRequested())){finishNative("INCOMPLETE: no validated winning profile; native global Off retained, not proven optimal");return;}
            if(!BenchmarkWorld.active()){finishNative("INCOMPLETE: no authored held-out allocation in a private scene");return;}
            confirmation.freeze(best.toString());confirmation.consume(best.toString());
            journal.metadata.setProperty("frozen_profile",best.toString());
            journal.metadata.setProperty("holdout_consumed","true; failure/inconclusive/cancel cannot reuse this allocation");
            windowRetry=0;stage=Stage.FINAL;finalPoses=full?BenchmarkWorld.poseCount():1;finalPhase=0;finalPose=0;finalRound=0;phase=0;finalWindows.clear();finalEvidence.clear();
            for(int p=0;p<finalPoses;p++)finalEvidence.add(new ArrayList<>());
            trial="final-native-Off-round-0";apply(false,OptimizationSnapshot.noneRequested());setPose(finalCasePose(0));beginWindow();
        }
        void completeFinal()throws Exception {
            windowRetry=0;finalWindows.add(snapshotWindow());
            if(++finalPose<finalPoses){setPose(finalCasePose(finalPose));beginWindow();return;}
            finalPose=0;
            if(++finalPhase<3){phase=finalPhase;trial=(finalPhase==1?"final-combined":"final-native-return")+"-round-"+finalRound;apply(finalPhase==1,finalPhase==1?best:OptimizationSnapshot.noneRequested());setPose(finalCasePose(0));beginWindow();return;}
            int rounds=2;
            for(int p=0;p<finalPoses;p++){
                pose=finalCasePose(p);
                finalEvidence.get(p).add(rawBracket("final-pose-"+pose+"-round-"+finalRound,List.of(finalWindows.get(p),finalWindows.get(finalPoses+p),finalWindows.get(2*finalPoses+p))));
                rounds=Math.max(rounds,casePolicy().analysis().minimumMatchedBrackets());
            }
            if(++finalRound<rounds){
                finalPhase=0;phase=0;finalWindows.clear();trial="final-native-Off-round-"+finalRound;
                apply(false,OptimizationSnapshot.noneRequested());setPose(finalCasePose(0));beginWindow();return;
            }
            boolean accepted=true;double worst=Double.POSITIVE_INFINITY;
            for(int p=0;p<finalPoses;p++) {
                pose=finalCasePose(p);var d=analyze("final-pose-"+pose,finalEvidence.get(p),BenchmarkAnalysis.ControlKind.OPTIMIZATION);
                journal.decision("final-pose-"+pose,d);accepted&=d.positive();
                if(d.gainMs()!=null)worst=Math.min(worst,d.gainMs());
            }
            journal.metadata.setProperty("final_aggregation","NONE: each required case must be POSITIVE; no sample pooling or compensating weights");
            journal.metadata.setProperty("final_case_coverage",finalPoses+"/"+BenchmarkWorld.poseCount()+" authored poses; Quick confirms only reserved pose");
            journal.metadata.setProperty("final_worst_case_gain_ms",Double.isFinite(worst)?Double.toString(worst):"unavailable");
            confirmation.complete(accepted);journal.metadata.setProperty("holdout_result",accepted?"POSITIVE timing only":"FAILED_OR_INCONCLUSIVE; consumed, no reuse");
            if(accepted){beginMotion();return;}
            finishNative("Frozen-profile holdout failed/inconclusive; allocation consumed; no profile saved");
        }
        void beginMotion()throws Exception {
            windowRetry=0;stage=Stage.MOTION;phase=0;motionRound=0;trial="motion-native-Off-round-0";bracket.clear();repeatedBrackets.clear();motionStarted=false;motionQueued=false;
            apply(false,OptimizationSnapshot.noneRequested());setPose(0);beginWindow();
        }
        void completeMotion()throws Exception {
            windowRetry=0;bracket.add(snapshotWindow());
            if(++phase<3) {
                trial=(phase==1?"motion-combined":"motion-native-return")+"-round-"+motionRound;motionStarted=false;motionQueued=false;
                apply(phase==1,phase==1?best:OptimizationSnapshot.noneRequested());setPose(0);beginWindow();return;
            }
            repeatedBrackets.add(rawBracket("motion-round-"+motionRound,bracket));
            if(++motionRound<Math.max(2,casePolicy().analysis().minimumMatchedBrackets())){
                phase=0;bracket.clear();motionStarted=false;motionQueued=false;trial="motion-native-Off-round-"+motionRound;
                apply(false,OptimizationSnapshot.noneRequested());setPose(0);beginWindow();return;
            }
            var d=analyze("final-matched-motion",repeatedBrackets,BenchmarkAnalysis.ControlKind.OPTIMIZATION);
            journal.decision("final-matched-motion",d);journal.metadata.setProperty("motion_validation",d.verdict()+"; native animations; tick route, wall-time coverage not qualified; visual quality unverified");
            journal.metadata.setProperty("exploratory_timing_positive",Boolean.toString(d.positive()&&confirmation.passed()));
            var scope=new TuningQualification.Scope(sessionId,original.source().originalSourceSha256(),goal.name(),best.toString(),confirmation.heldOutCase(),journal.directory);
            var evidence=Objects.requireNonNull(qualification.apply(scope),"qualification evidence");
            journal.metadata.setProperty("qualification_scope",scope.toString());
            journal.metadata.setProperty("qualification_findings",evidence.findings().toString());
            journal.metadata.setProperty("qualification_scope_matches",Boolean.toString(evidence.matches(scope)));
            boolean qualified=evidence.matches(scope)&&TuningConfirmation.maySave(d.positive(),confirmation.passed(),nullControlPassed,
                evidence.has("known-effect-control")&&evidence.has("matching-transition-controls"),evidence.has("readiness-and-work")&&evidence.has("observer-controls")&&evidence.has("native-options-AA"),
                evidence.has("still-quality")&&evidence.has("motion-quality"),evidence.has("DH-PBR-compatibility")&&evidence.has("matched-motion-coverage"),true);
            if(qualified){
                journal.qualificationEvidence=evidence;apply(true,best);
                // finish closes/verifies the scene and restores video/observer mode before the
                // SAVED journal/library commit; failures enter the existing restoration owner.
                finish("SAVED",best,"Frozen-profile heldout and motion timing passed with scoped external qualification");return;
            }
            finishNative(d.positive()?"Exploratory timing positive; quality/readiness/controls/route coverage unqualified; no profile saved":"Matched motion failed/inconclusive; no profile saved");
        }
        void finishNative(String message)throws Exception {best=OptimizationSnapshot.noneRequested();apply(false,best);finish("INCOMPLETE",best,message);}
        void finish(String state,OptimizationSnapshot profile,String message)throws Exception {
            requireBands();
            if(BenchmarkWorld.active())setPose(0);
            RuntimeMeasurement.request(false);restoreObserverMode();restoreVideo();
            if(lease!=null){lease.close();lease=null;}
            for(var id:OptimizationId.values())if(!covered.contains(id))journal.note(id.key(),TuningPolicy.Verdict.UNTESTED,"Not independently timed in this segment; see explicit applicability and candidate skip/refusal rows");
            for(int i=index;i<candidates.size();i++)journal.note(candidates.get(i).name(),TuningPolicy.Verdict.UNTESTED,"finite budget/order; candidate not attempted");
            journal.metadata.setProperty("search_coverage",index<candidates.size()?"PARTIAL: finite budget/order":"Scheduled candidates attempted; unavailable and inconclusive outcomes retained");
            for(var id:OptimizationId.values())journal.metadata.putIfAbsent("request."+id.key()+".timing_status",measured.contains(id)?"TIMED_REQUEST_DELTA":"NOT_TIMED_THIS_SEGMENT");
            journal.metadata.setProperty("final_enabled",Boolean.toString(ShaderLodConfig.ENABLED.get()));
            journal.metadata.setProperty("final_preset",ShaderLodConfig.PRESET.get().name());
            journal.metadata.setProperty("final_selected_pack",selectedPack());
            var finalNative=new TreeMap<>(SavedTunings.nativeOptions());journal.metadata.setProperty("final_native_options",finalNative.toString());
            finalNative.forEach((key,value)->journal.metadata.setProperty("final.native_option."+key,value));recordBands("final");
            ShaderLodEffectConfig.tuningValues().forEach((key,value)->journal.metadata.setProperty("final.effect_option."+key,value));
            journal.metadata.setProperty("measured_canonical_requests",Integer.toString(measured.size()));
            journal.metadata.setProperty("measured_request_scope","Only canonical request deltas in completed timing brackets; not proven shader consumption or active pixels");
            if(continuation!=null) {
                // Commit only terminal segment results, never an interrupted in-flight search.
                continuation.terminal(ShaderLodConfig.ENABLED.get(),ShaderLodConfig.PRESET.get().name(),OptimizationConfig.snapshot());
                TuningJournal.atomic(continuationFile,continuation.saved(profile,"SAVED".equals(state)));
                journal.metadata.setProperty("full_completed_candidates",Integer.toString(continuation.completed.size()));
                journal.metadata.setProperty("full_total_candidates",Integer.toString(scheduledCount));
                journal.metadata.setProperty("full_coverage",continuation.completed.size()>=scheduledCount?"COMPLETE_CANDIDATE_PASS: includes explicit refused/inconclusive/skipped; not all combinations or active consumers":"PARTIAL_RESUMABLE: run Full again with the same source/options/tuple");
            }
            if(full&&continuation==null)journal.metadata.setProperty("full_coverage",(index<scheduledCount?"PARTIAL_NON_RESUMABLE: ":"COMPLETE_CANDIDATE_PASS_SINGLE_SEGMENT: ")+continuationUnavailable);
            journal.metadata.setProperty("accounted_requests_including_unavailable",Integer.toString(covered.size()));
            journal.metadata.setProperty("apply_calls",Integer.toString(applyCalls));journal.metadata.setProperty("apply_wall_ns",Long.toString(applyNs));
            journal.metadata.setProperty("optimization_reload_calls",Integer.toString(reloadCalls));journal.metadata.setProperty("optimization_reload_wall_ns",Long.toString(reloadNs));
            journal.metadata.setProperty("actual_reload_calls",Long.toString(RuntimeMeasurement.reloadCount()-initialReloadCount));
            journal.metadata.setProperty("reload_timing_scope","optimization reload wall is exact; core Apply wall includes preparation and reloads");
            journal.metadata.setProperty("attempted_candidates",Integer.toString(attemptCount));
            journal.metadata.setProperty("retry_count",Integer.toString(retryCount));
            journal.finish(state,profile);
            if("SAVED".equals(state))try {SavedTunings.recordValidated(journal.directory,profile,goal.name());}
            catch(Exception saveFailure) {
                journal.metadata.setProperty("named_tuning_save_warning",saveFailure.toString());
                try{journal.save();}catch(Exception metadataFailure){saveFailure.addSuppressed(metadataFailure);}
                message+="; measured result saved, named-tuning library save failed: "+saveFailure.getMessage();
            }
            last=goal.label()+": "+message+"; request deltas timed "+measured.size()+"/"+OptimizationId.values().length+(continuation==null?(full?"; Full single segment (not resumable): "+continuationUnavailable:"; Quick partial inventory"):"; Full candidates "+continuation.completed.size()+"/"+scheduledCount+(continuation.completed.size()<scheduledCount?"; run Full again to continue":"; pass complete"))+"; visual/motion quality unverified; Undo available; "+journal.directory;job=null;
        }
        void restoreObserverMode(){if(previousObserverMode!=null&&!RuntimeMeasurement.requested()){RuntimeMeasurement.observerMode(previousObserverMode);previousObserverMode=null;}}
        void restoreVideo(){if(original==null)return;TimingVideoSettings.apply(original.fps(),original.vsync(),original.windowFps());client.options.save();}
        void fail(String reason) {
            if(recovery.parked())return;
            windowInvalidity=reason;failure=reason;if(journal!=null)journal.metadata.setProperty("search_outcome",reason.startsWith("Cancelled")?"CANCELLED":"INCOMPLETE: "+reason);cancelled=true;restoring=true;RuntimeMeasurement.request(false);
            if(journal!=null){journal.metadata.setProperty("attempted_candidates",Integer.toString(attemptCount));journal.metadata.setProperty("retry_count",Integer.toString(retryCount));
                try{flushWindow();}catch(Exception ignored){}}
            stage=Stage.RESTORING;restore();
        }
        void explicitUndo() {
            if(!restoring){fail("Undo requested; restoring original profile");return;}
            recovery.explicitRetry();restore();
        }
        void restore() {
            if(recovery.parked())return;
            nextRestoreAt=System.nanoTime()+2_000_000_000L;
            var result=recovery.attempt(this::restoreOwnedState);
            if(result==TuningRecovery.Result.COMPLETE) {
                recoveryPending=false;last=failure+"; original state and scene restored"+(journal==null?"":"; "+journal.directory);job=null;return;
            }
            var error=recovery.failure();var details=error+"; suppressed="+Arrays.toString(error.getSuppressed());
            if(!failure.startsWith("Restoration pending"))failure="Restoration pending: "+error+"; cause: "+failure;
            if(journal!=null)journal.metadata.setProperty("recovery_latest_failure",details);
            last=failure;
            if(result==TuningRecovery.Result.PARKED) {
                recoveryPending=true;
                try {if(journal!=null)journal.parkRecovery(failure+"; latest: "+details,recovery.attempts());}
                catch(Exception writeFailure){failure+="; pending journal update failed: "+writeFailure;}
                last="RESTORATION PENDING after three automatic attempts; scene/profile ownership retained, NOT confirmed restored. Use Undo to retry. "+failure;
                // Keep this Job, including any unacknowledged SceneLease, as the sole owner.
                // The parked tick path performs no more automatic recovery work.
            }
        }
        void restoreOwnedState() throws Throwable {
            boolean sceneCloseAttempted=false;
            try {
                restoreObserverMode();
                if(original!=null) {
                    try{original.source().requireCurrent(Iris.getShaderpacksDirectory(),selectedPack());}
                    catch(java.io.IOException mismatch){OptimizationConfig.restoreFamily(original.family(),original.flags());throw mismatch;}
                    if(!original.family().equals(OptimizationConfig.activeFamily())) {
                        OptimizationConfig.restoreFamily(original.family(),original.flags());
                        throw new IllegalStateException("External shader family changed; original requests restored but graph recovery awaits original family selection");
                    }
                    if(!ShaderLodSettings.apply(original.enabled(),original.preset()))throw new IllegalStateException(ShaderLodSettings.failureMessage());
                    var applied=OptimizationEditTransaction.apply(original.flags(),Iris::reload,Iris::isFallback,CompileOptionControl::open);
                    if(!applied.success())throw new IllegalStateException(applied.message());
                    restoreVideo();
                }
                sceneCloseAttempted=true;
                if(lease!=null){lease.close();lease=null;}
                if(journal!=null)journal.finish("RESTORED",original.flags());
            }catch(Throwable error) {
                try{restoreVideo();}catch(Throwable video){if(video!=error)error.addSuppressed(video);}
                if(!sceneCloseAttempted&&lease!=null)try{lease.close();lease=null;}
                    catch(Throwable scene){if(scene!=error)error.addSuppressed(scene);}
                // A failed close leaves lease referenced; no acknowledgment or owner release.
                throw error;
            }
        }

    }
}

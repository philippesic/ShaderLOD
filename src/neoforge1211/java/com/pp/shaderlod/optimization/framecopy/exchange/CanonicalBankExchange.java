package com.pp.shaderlod.optimization.framecopy.exchange;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.pp.shaderlod.optimization.OptimizationConfig;
import com.pp.shaderlod.optimization.framecopy.MakeUpLeanGraph;
import com.pp.shaderlod.optimization.framecopy.FrameCopyRuntime;
import com.pp.shaderlod.optimization.framecopy.rotation.HistoryRotationPreparation;
import java.util.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;

/** Explicit experimental owner exchange. Default startup OFF; public G01 never opts in. */
public final class CanonicalBankExchange {
    public static final String PROPERTY="shaderlod.experimentalMakeUpHistoryExchange";
    private static final boolean STARTUP=Boolean.getBoolean(PROPERTY);
    // Capture actual immutable path consumers, not their later mutable enabled/done state.
    private static final Map<String,Boolean> CONFIGURED_PATHS=Map.of("shaderlod.dev.makeupMrtProbe",!System.getProperty("shaderlod.dev.makeupMrtProbe","").isBlank(),"shaderlod.dev.complementaryCopyGraph",!System.getProperty("shaderlod.dev.complementaryCopyGraph","").isBlank());
    private static final Set<String> BOOLEAN_DIAGNOSTICS=Set.of("shaderlod.dev.bslMipWitnessRuntime","shaderlod.dev.nativeSubmitObserver","shaderlod.dev.nativeMacroInspection");
    private static final Set<String> STARTUP_BOOLEAN_DIAGNOSTICS=enabledBooleanDiagnostics();
    private static final Map<Object,CanonicalDefinitionWitness> DEFINITIONS=new WeakHashMap<>();
    private static final FreshDefinitionGate DEFINITION_GATE=new FreshDefinitionGate();
    private static final Set<String> HOOKS=new HashSet<>();
    private static final ArrayDeque<Event> EVENTS=new ArrayDeque<>();
    private static Session session;
    private static Object blockedPipeline;
    private static long blockedGeneration=-1,prepared,discarded,repairs;
    private static boolean requested,quadScope,testUnknownAfterDraw,testSmallWriteStorage;
    private static long allocationEpoch,positiveDefinitions,readerSubmissions,nativeDrawAttempts,publications,outerAborts,readerAtFreshFailure=-1,publicationAtFreshFailure=-1;
    private static int currentReader;
    private static String freshFault="",fatalReason="";
    private static boolean faultTrace;
    private static String copyFault="",lastCopyObligation="NONE";
    private static String status="DEFAULT_OFF";
    private static String aliasTest="";
    private static final List<CanonicalAliasWitness.Result> ALIAS_CHECKS=new ArrayList<>();
    private static final List<Map<String,Object>> ALIAS_OBSERVATIONS=new ArrayList<>();
    private static Mode mode=Mode.OFF;
    public enum Mode { OFF,SHADOW,ACTIVE }
    public record Event(long sequence,String kind,int read,int write,int copyDestination,int nextCommitted,boolean copyCannotSupplyNextCommitted) {}
    public record Snapshot(String status,Mode mode,boolean requested,long prepared,long discarded,long repairs,
        long generation,long sessionEpoch,int pipelineIdentity,int targetIdentity,int committed,int write,long qualifiedDraws,long shadowFrames,long activeFrames,long retainedCopies,long omittedCopies,
        int linkedPrograms,int framebufferCount,int rt3Aliases,List<Event> events,Set<String> hooks,Safety safety,Completion completion,List<CanonicalAliasWitness.Result> aliasChecks,List<Map<String,Object>> aliasObservations) {}
    public record Safety(String definitionPhase,long allocationEpoch,long positiveDefinitions,int currentReader,long readerSubmissions,long nativeDrawAttempts,long publications,long outerAborts,long readerAtFreshFailure,long publicationAtFreshFailure,long repairs,long discarded,String fatalReason,String copyObligation) {}
    public record Completion(long copyAttempts,long copyReturns,long pairCompletions,long producerCompletions,long shadowRt3Completions,int pairStarted,int pairReturned,boolean pairCompleted,boolean copyStarted) {}
    private static final class Session {
        final CanonicalExchangeProof proof;
        final CanonicalHistoryState state=new CanonicalHistoryState();
        final CanonicalDrawGuard guard=new CanonicalDrawGuard();
        final RetainedCopyPair pair=new RetainedCopyPair();
        final CanonicalDrawGuard.Expected expected;
        final Object world=Minecraft.getInstance().level,pack=Iris.getCurrentPack().orElse(null);
        final long generation=OptimizationConfig.generation(),epoch=++prepared;
        long draws,shadowFrames,activeFrames,retained,omitted;
        int copyIndex,drawsThisFrame,readIndex,writeIndex;
        boolean frameOpen,finalReturned,copyStarted;
        long copyAttempts,copyReturns,pairCompletions,producerCompletions,shadowRt3Completions;
        Session(CanonicalExchangeProof proof) {
            this.proof=proof;
            expected=new CanonicalDrawGuard.Expected(proof.producer.getProgramId(),GL20.glGetUniformLocation(proof.producer.getProgramId(),"colortex3"),proof.rt1,proof.width,proof.height);
        }
        int texture(int bank){return bank==0?proof.a:proof.b;}
    }
    private CanonicalBankExchange() {}
    public static void hook(String name){HOOKS.add(name);}
    public static void compiled(ProgramSource source,Program program,Map<?,String> stages){if(STARTUP)CanonicalExchangeProof.compiled(source,program,stages);}
    public static Snapshot request(String action)throws Exception {
        if(!STARTUP)return snapshot();RenderSystem.assertOnRenderThread();
        if(Boolean.getBoolean("shaderlod.experimentalMakeUpHistoryRotation"))throw new IllegalStateException("MUTUALLY_EXCLUSIVE_OLD_PROTOTYPE");
        switch(action) {
            case "shadow" -> { inertDiagnostics();if(!DEFINITION_GATE.readAllowed())throw fatal("REQUEST_DURING_DEFINITION_OBLIGATION",null);closeKnown("NEW_SHADOW");requested=true;mode=Mode.SHADOW;blockedPipeline=null;status="WAIT_RETURNED_FULL_CLEAR";Iris.reload(); }
            case "enable" -> { require(session!=null&&session.shadowFrames>=8&&session.state.phase()==CanonicalHistoryState.Phase.READY&&context(session),"SAME_EPOCH_EIGHT_SHADOW_FRAMES_REQUIRED");mode=Mode.ACTIVE;status="ACTIVE"; }
            case "off" -> {requested=false;closeKnown("EXPLICIT_OFF");mode=Mode.OFF;}
            case "test_unknown_after_draw" -> {
                require(Boolean.getBoolean("shaderlod.dev.g01ExposureProbe")&&session!=null&&mode==Mode.ACTIVE,"UNTIMED_ACTIVE_TEST_ONLY");
                testUnknownAfterDraw=true;
            }
            case "test_small_write_storage" -> {
                require(Boolean.getBoolean("shaderlod.dev.g01ExposureProbe")&&session!=null&&mode==Mode.ACTIVE,"UNTIMED_ACTIVE_TEST_ONLY");
                testSmallWriteStorage=true;
            }
            case "test_prepare_gl_error", "test_missing_clear_off", "test_early_refusal", "test_benign_refusal", "test_diagnostic_refusal" -> {
                require(Boolean.getBoolean("shaderlod.dev.g01ExposureProbe")&&session!=null&&mode==Mode.ACTIVE,"UNTIMED_ACTIVE_TEST_ONLY");
                freshFault=action;faultTrace=true;testUnknownAfterDraw=true;
            }
            case "test_copy0_pending", "test_copy0_submission_error", "test_copy1_exception", "test_copy1_missing_return", "test_pair_completion_missing", "test_pair_completion_exception", "test_pair_completion_error", "test_pair_completion_reset" -> {
                require(Boolean.getBoolean("shaderlod.dev.g01ExposureProbe")&&session!=null&&mode==Mode.ACTIVE,"UNTIMED_ACTIVE_TEST_ONLY");
                copyFault=action;faultTrace=true;
            }
            case "test_return_odd", "test_return_even", "test_fallback_odd", "test_fallback_even", "test_fullclear_odd", "test_resize_odd", "test_repair_failure" -> {
                require(Boolean.getBoolean("shaderlod.dev.g01ExposureProbe")&&session!=null&&mode==Mode.ACTIVE,"UNTIMED_ACTIVE_TEST_ONLY");
                aliasTest=action;faultTrace=true;
            }
            case "test_alias_update_failure" -> {
                require(Boolean.getBoolean("shaderlod.dev.g01ExposureProbe")&&session!=null&&mode==Mode.ACTIVE,"UNTIMED_ACTIVE_TEST_ONLY");
                session.proof.testPartialAliasFailure();faultTrace=true;
            }
            case "test_alias_snapshot" -> {
                require(Boolean.getBoolean("shaderlod.dev.g01ExposureProbe")&&session!=null&&session.state.phase()==CanonicalHistoryState.Phase.READY,"UNTIMED_READY_TEST_ONLY");
                var banks=session.state.banks();var view=new HashMap<String,Object>(session.proof.observeAliases(session.texture(banks.committed()),session.texture(banks.write())));
                view.put("mode",mode.name());view.put("exchanges",session.state.exchanges());view.put("allocationEpoch",allocationEpoch);ALIAS_OBSERVATIONS.add(Map.copyOf(view));
            }
            case "status" -> {}
            default -> throw new IllegalArgumentException("shadow/enable/off/status");
        }
        return snapshot();
    }
    public static Snapshot snapshot() {
        Session s=session;var banks=s==null?null:s.state.banks();
        return new Snapshot(status,mode,requested,prepared,discarded,repairs,s==null?-1:s.generation,s==null?-1:s.epoch,s==null?0:System.identityHashCode(s.proof.pipeline),s==null?0:System.identityHashCode(s.proof.rt3),
            s==null?0:s.texture(banks.committed()),s==null?0:s.texture(banks.write()),s==null?0:s.draws,
            s==null?0:s.shadowFrames,s==null?0:s.activeFrames,s==null?0:s.retained,s==null?0:s.omitted,
            s==null?0:s.proof.programCount,s==null?0:s.proof.framebufferCount,s==null?0:s.proof.aliases.size(),List.copyOf(EVENTS),Set.copyOf(HOOKS),safety(),s==null?null:new Completion(s.copyAttempts,s.copyReturns,s.pairCompletions,s.producerCompletions,s.shadowRt3Completions,s.pair.started(),s.pair.returned(),s.pair.completed(),s.copyStarted),List.copyOf(ALIAS_CHECKS),List.copyOf(ALIAS_OBSERVATIONS));
    }
    private static Safety safety(){return new Safety(DEFINITION_GATE.phase().name(),allocationEpoch,positiveDefinitions,currentReader,readerSubmissions,nativeDrawAttempts,publications,outerAborts,readerAtFreshFailure,publicationAtFreshFailure,repairs,discarded,fatalReason,lastCopyObligation);}
    private static void trace(String event){if(faultTrace)System.err.println("SHADERLOD_EXCHANGE_SAFETY "+event+" "+new com.google.gson.Gson().toJson(safety()));}
    public static int mainTexture(Object target,int original) {
        Session s=session;return s!=null&&target==s.proof.rt3?s.texture(s.state.banks().committed()):original;
    }
    public static int altTexture(Object target,int original) {
        Session s=session;return s!=null&&target==s.proof.rt3?s.texture(s.state.banks().write()):original;
    }
    public static void beforeFrame(Object pipeline) {
        if(!STARTUP)return;
        if(DEFINITION_GATE.needsDefinition()&&freshFault.equals("test_diagnostic_refusal"))System.setProperty("shaderlod.dev.makeupMrtProbe","false");
        if(requested||!DEFINITION_GATE.readAllowed())try{inertDiagnostics();}catch(Exception|LinkageError failure){throw preparationFailure("NON_INERT_DIAGNOSTIC",failure);}
        Session s=session;
        if(s!=null&&!context(s))try{closeKnown("CONTEXT_CHANGE");}catch(Exception failure){throw discard("NATIVE_RETURN_FAILURE",failure);}
    }
    public static void beforeClear(Object targets) {
        if(!STARTUP)return;
        if(session!=null&&session.proof.targets==targets)try{closeKnown("BEFORE_FULL_CLEAR");}catch(Exception failure){throw discard("CLEAR_REPAIR_FAILURE",failure);}
        if(!requested&&!DEFINITION_GATE.needsDefinition())return;
        try {
            var witness=new CanonicalDefinitionWitness((net.irisshaders.iris.targets.RenderTargets)targets,++allocationEpoch);
            DEFINITIONS.put(targets,witness);
            if(DEFINITION_GATE.needsDefinition())DEFINITION_GATE.bind(targets,witness.epoch);
        }catch(Exception|LinkageError failure){throw preparationFailure("FRESH_CLEAR_START",failure);}
    }
    /** Constructor/loop return is deliberately not a positive definition witness. */
    public static void clearReturned(Object targets) {}
    public static boolean beforeClearSubmission(Object clear,int mask,boolean osx) {
        if(!STARTUP)return true;
        try {
            for(var d:DEFINITIONS.values())if(!d.defined&&d.contains(clear)) {
                if(DEFINITION_GATE.needsDefinition()&&freshFault.equals("test_missing_clear_off")){
                    requested=false;blockedPipeline=Iris.getPipelineManager().getPipelineNullable();blockedGeneration=OptimizationConfig.generation();
                    trace("SKIPPED_ACTUAL_FRESH_CLEAR_REQUEST_OFF_BLOCKED");return false;
                }
                d.start(clear,mask,osx);return true;
            }
            return true;
        }catch(Exception|LinkageError failure){throw preparationFailure("FRESH_CLEAR_SUBMISSION",failure);}
    }
    public static void clearSubmissionReturned(Object clear) {
        if(!STARTUP)return;
        try {for(var d:DEFINITIONS.values())if(d.pending.containsKey(clear)){d.returned(clear);return;}}
        catch(Exception|LinkageError failure){throw preparationFailure("FRESH_CLEAR_RETURN",failure);}
    }
    public static void beforeResize(Object targets,int depthVersion,int depthTexture,int width,int height,Object format) {
        if(!STARTUP||session==null||session.proof.targets!=targets)return;
        Session s=session;
        try {
            if(width!=s.proof.width||height!=s.proof.height
                ||depthVersion!=(int)CanonicalExchangeProof.field(targets,"cachedDepthBufferVersion")
                ||depthTexture!=(int)CanonicalExchangeProof.field(targets,"currentDepthTexture")
                ||format!=CanonicalExchangeProof.field(targets,"currentDepthFormat"))closeKnown("BEFORE_RESIZE");
        }catch(Exception failure){throw discard("RESIZE_REPAIR_FAILURE",failure);}
    }
    public static void destroyed(Object targets) {
        if(!STARTUP)return;
        DEFINITIONS.remove(targets);
        if(session!=null&&session.proof.targets==targets){session.state.discard();session=null;mode=requested?Mode.SHADOW:Mode.OFF;status="OLD_ALLOCATION_DISCARDED";quadScope=false;}
    }
    /** Exact after-clear/setup, before beginRenderer seam. No onFullClear intention is mistaken for completion. */
    public static void beforeGraph(Object pipeline,Object targets) {
        if(!STARTUP)return;
        try {
            // The recovery obligation is checked before request/eligibility/blocked shortcuts.
            if(requested||DEFINITION_GATE.needsDefinition()) {
                var d=DEFINITIONS.get(targets);
                require(d!=null,"MISSING_FRESH_CLEAR");
                if(!d.defined){
                    Object composite=CanonicalExchangeProof.field(pipeline,"compositeRenderer");
                    currentReader=0;
                    for(Object pass:(List<?>)CanonicalExchangeProof.field(composite,"passes"))if("composite3".equals(CanonicalExchangeProof.field(pass,"name")))currentReader=((Program)CanonicalExchangeProof.field(pass,"program")).getProgramId();
                }
                if(DEFINITION_GATE.needsDefinition()) {
                    DEFINITION_GATE.bind(targets,d.epoch);
                    if(freshFault.equals("test_prepare_gl_error")){trace("INJECT_FRESH_PREPARE_GL_ERROR_AFTER_CLEAR_RETURNS");GL11.glEnable(-1);}
                    if(freshFault.equals("test_early_refusal")){
                        requested=false;blockedPipeline=pipeline;blockedGeneration=OptimizationConfig.generation();
                        throw new IllegalStateException("UNKNOWN_EARLY_PREPARATION_REFUSAL");
                    }
                }
                if(!d.defined){
                    d.complete();positiveDefinitions++;trace("POSITIVE_BOTH_BANK_STORAGE_CLEAR_COMPLETION");
                }
            }
        }catch(Exception|LinkageError failure){throw preparationFailure("FRESH_DEFINITION_OR_PRE_READ",failure);}
        boolean benignRefusal=DEFINITION_GATE.needsDefinition()&&freshFault.equals("test_benign_refusal");
        if(!requested||benignRefusal||(session==null&&(!eligible()||blockedPipeline==pipeline&&blockedGeneration==OptimizationConfig.generation()))) {
            grantDefinition(targets);if(benignRefusal){requested=false;freshFault="";status="NATIVE_BENIGN_REFUSAL_AFTER_POSITIVE_DEFINITION";trace(status);}return;
        }
        if(session==null && !(blockedPipeline==pipeline&&blockedGeneration==OptimizationConfig.generation())) {
            try {
                require(HOOKS.containsAll(Set.of("source:CompositeRenderer","source:FinalPassRenderer","pipeline","targets","target","composite","final","draw","frame","clear")),"HOOKS_NOT_TRANSFORMED");
                var proof=new CanonicalExchangeProof(pipeline);session=new Session(proof);mode=Mode.SHADOW;status="SHADOW";
            }catch(Exception|LinkageError failure){throw preparationFailure("PRE_READ_PREPARATION_UNKNOWN",failure);}
        }
        grantDefinition(targets);
        Session s=session;if(s==null)return;
        require(s.state.phase()==CanonicalHistoryState.Phase.READY,"PRIOR_FRAME_NOT_COMMITTED");
        try{require(!s.copyStarted,"COPY_STARTED_AT_NEXT_FRAME");s.pair.nextFrame();}
        catch(Exception failure){throw discard("PENDING_COPY_AT_NEXT_FRAME",failure);}
        s.frameOpen=true;s.finalReturned=false;s.copyIndex=0;s.drawsThisFrame=0;
    }
    public static boolean beginQuad(Object renderer) {
        if(!DEFINITION_GATE.readAllowed())throw fatal("QUAD_WITHOUT_POSITIVE_DEFINITION",null);
        Session s=session;if(s==null||CanonicalProgram()!=s.proof.producer.getProgramId())return false;
        require(!quadScope,"NESTED_PRODUCER");quadScope=true;return true;
    }
    public static void endQuad(boolean owned) {
        if(!owned)return;quadScope=false;Session s=session;
        if(s!=null&&s.drawsThisFrame!=1)throw discard("MISSING_OR_EXTRA_TERMINAL_DRAW",null);
    }
    /** Last Java seam before native GL11.glDrawElements; no application callback in the witnessed interval. */
    public static boolean beforeDraw(int mode,int count,int type,long offset) {
        if(!DEFINITION_GATE.readAllowed())throw fatal("DRAW_WITHOUT_POSITIVE_DEFINITION",null);
        if(faultTrace){nativeDrawAttempts++;if(currentReader!=0&&CanonicalProgram()==currentReader)readerSubmissions++;}
        Session s=session;if(!quadScope||s==null)return false;
        if(!context(s))throw discard("DRAW_CONTEXT_CHANGED",null);
        var banks=s.state.banks();s.readIndex=banks.committed();s.writeIndex=banks.write();
        if(testSmallWriteStorage) {
            testSmallWriteStorage=false;
            int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),unpack=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
            try {
                GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,s.texture(s.writeIndex));
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_R11F_G11F_B10F,Math.max(1,s.proof.width/2),s.proof.height,0,GL11.GL_RGB,GL11.GL_FLOAT,0L);
                require(GL11.glGetError()==GL11.GL_NO_ERROR&&GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"TEST_SMALL_WRITE_NOT_COMPLETE");
                addEvent(new Event(s.state.exchanges(),"TEST_SMALLER_WRITE_STORAGE_FBO_STILL_COMPLETE",s.texture(s.readIndex),s.texture(s.writeIndex),0,0,false));
            }finally{GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,unpack);}
        }
        var coverage=s.guard.beforeQuad(s.expected,s.proof.producerFbo.getId(),s.texture(s.readIndex),s.texture(s.writeIndex),mode,count,type,offset);
        if(!coverage.complete()) {
            if(coverage.reason().contains("ERROR")||coverage.reason().contains("CONTEXT")||coverage.reason().contains("STORAGE")||coverage.reason().contains("ALLOCATION"))throw discard("UNKNOWN_DRAW_STATE:"+coverage.reason(),null);
            try {blockedPipeline=s.proof.pipeline;blockedGeneration=s.generation;closeKnown("DRAW_VETO:"+coverage.reason());return false;}
            catch(Exception failure){throw discard("VETO_NATIVE_REPAIR_FAILURE",failure);}
        }
        if(aliasTest.startsWith("test_fallback_")&&testParity(s)) {
            try {blockedPipeline=s.proof.pipeline;blockedGeneration=s.generation;closeKnown("TEST_PRE_DRAW_BENIGN_FALLBACK");return false;}
            catch(Exception failure){throw discard("TEST_FALLBACK_REPAIR_FAILURE",failure);}
        }
        s.state.producerStarted();s.drawsThisFrame++;return true;
    }
    public static void drawReturned(boolean owned) {
        if(!owned)return;Session s=session;require(s!=null,"SUBMISSION_OWNER_LOST");
        if(testUnknownAfterDraw) {
            testUnknownAfterDraw=false;
            addEvent(new Event(s.state.exchanges(),"TEST_UNWITNESSED_RETURNED_DRAW_DISCARD",s.texture(s.readIndex),s.texture(s.writeIndex),0,0,false));
            throw discard("TEST_RETURNED_DRAW_WITHOUT_SUCCESS_WITNESS",null);
        }
        // Conservative completion witness is intrinsic and charged in every experimental frame.
        GL11.glFinish();if(GL11.glGetError()!=GL11.GL_NO_ERROR||GL45.glGetGraphicsResetStatus()!=GL11.GL_NO_ERROR)throw discard("UNKNOWN_PRODUCER_RESULT",null);
        s.state.producerCompleted();s.draws++;s.producerCompletions++;
    }
    /** Called for each final copy; return true only to omit the individually qualified RT3 copy. */
    public static boolean beforeFinalCopy(Object owner,int target,int level,int xo,int yo,int x,int y,int width,int height) {
        Session s=session;if(s==null)return false;
        try{require(!s.copyStarted,"MISSING_PREVIOUS_COPY_RETURN");if(s.copyIndex==2)s.pair.requireCompletedBeforeHistory();inertDiagnostics();}
        catch(Exception|LinkageError failure){throw discard("COPY_ENTRY_OBLIGATION_OR_DIAGNOSTIC",failure);}
        if(target!=GL11.GL_TEXTURE_2D||level!=0||xo!=0||yo!=0||x!=0||y!=0)throw discard("FINAL_COPY_CALL",null);
        require(owner==s.proof.finalRenderer&&s.copyIndex<3,"FINAL_OWNER_OR_COUNT");
        var copy=s.proof.finalCopies.get(s.copyIndex);
        int expectedSource=copy.target()==3?s.texture(s.state.banks().write()):copy.from().getColorAttachment(0);
        int expectedDestination=copy.target()==3?s.texture(s.state.banks().committed()):copy.destination();
        int source=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        if(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)!=copy.from().getId()||source!=expectedSource
            ||GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)!=expectedDestination||width!=copy.width()||height!=copy.height()
            ||GL11.glGetError()!=GL11.GL_NO_ERROR||GL45.glGetGraphicsResetStatus()!=GL11.GL_NO_ERROR)throw discard("FINAL_ENDPOINT_OR_PRIOR_GL_ERROR_RESET",null);
        if(copy.target()==3&&s.state.phase()!=CanonicalHistoryState.Phase.PRODUCED)throw discard("NO_COMPLETE_HISTORY_PRODUCER",null);
        boolean omitted=copy.target()==3&&mode==Mode.ACTIVE;
        // Obligation precedes the native Operation: thrown/missing returns remain pending.
        try{if(s.copyIndex<2)s.pair.start(s.copyIndex);s.copyStarted=true;if(!omitted)s.copyAttempts++;}
        catch(Exception failure){throw discard("COPY_START_OBLIGATION",failure);}
        return omitted;
    }
    public static void beforeNativeCopy() {
        if(session!=null&&session.copyIndex==1&&copyFault.equals("test_copy1_exception")){
            copyFault="";trace("INJECT_RT1_NATIVE_OPERATION_EXCEPTION_WITH_RT8_PENDING");throw new IllegalStateException("TEST_RT1_OPERATION_EXCEPTION");
        }
    }
    public static RuntimeException copyFailed(Throwable failure) {
        if(failure instanceof Discard d)return d;if(failure instanceof FatalStop f)return f;
        return discard("NATIVE_COPY_OPERATION_OR_RETURN_FAILED",failure);
    }
    public static void finalCopyReturned(boolean omitted) {
        Session s=session;if(s==null)return;
        require(s.copyStarted,"COPY_RETURN_WITHOUT_START");
        if(s.copyIndex==1&&copyFault.equals("test_copy1_missing_return")){
            copyFault="";trace("INJECT_MISSING_RT1_RETURN_WITH_RT8_PENDING");return;
        }
        if(!omitted){
            if(s.copyIndex==0&&copyFault.equals("test_copy0_submission_error")){copyFault="";trace("INJECT_RT8_SUBMISSION_GL_ERROR");GL11.glEnable(-1);}
            CanonicalDefinitionWitness.clean("COPY_"+s.copyIndex+"_SUBMISSION");s.copyReturns++;
            if(s.copyIndex<2) {
                s.pair.returned(s.copyIndex);s.retained++;
                if(s.copyIndex==0&&copyFault.equals("test_copy0_pending")){copyFault="";trace("INJECT_RT8_RETURNED_BUT_COMPLETION_PENDING");throw discard("TEST_RT8_COMPLETION_PENDING",null);}
                if(s.copyIndex==1) {
                    if(copyFault.equals("test_pair_completion_missing")){copyFault="";trace("INJECT_BOTH_RETURNS_WITHOUT_SHARED_COMPLETION");s.copyStarted=false;s.copyIndex++;return;}
                    if(copyFault.equals("test_pair_completion_exception")){copyFault="";trace("INJECT_SHARED_COMPLETION_EXCEPTION");throw new IllegalStateException("TEST_PAIR_FINISH_EXCEPTION");}
                    GL11.glFinish();
                    if(copyFault.equals("test_pair_completion_error")){copyFault="";trace("INJECT_SHARED_COMPLETION_GL_ERROR");GL11.glEnable(-1);}
                    CanonicalDefinitionWitness.clean("PAIR_COMPLETION");
                    if(copyFault.equals("test_pair_completion_reset")){copyFault="";trace("INJECT_SHARED_COMPLETION_RESET_OUTCOME");throw new IllegalStateException("TEST_PAIR_RESET_WITNESS_FAILURE");}
                    s.pair.completionSucceeded();s.pairCompletions++;
                }
            }else {GL11.glFinish();CanonicalDefinitionWitness.clean("SHADOW_RT3_COMPLETION");s.shadowRt3Completions++;s.retained++;}
        } else {CanonicalDefinitionWitness.clean("RT3_PRE_OMISSION_PUBLICATION");s.omitted++;}
        s.copyStarted=false;
        if(s.copyIndex==2) {
            try {
                int read=s.texture(s.readIndex),write=s.texture(s.writeIndex);
                s.state.exchange(s.readIndex,s.writeIndex,omitted?null:s.readIndex);
                var banks=s.state.banks();s.proof.selectAliases(s.texture(banks.committed()),s.texture(banks.write()));
                addEvent(new Event(s.state.exchanges(),omitted?"OMITTED_RT3_EXCHANGE":"RETAINED_RT3_EXCHANGE",read,write,omitted?0:read,s.texture(banks.committed()),omitted||read!=s.texture(banks.committed())));
                publications++;
                if(!aliasTest.isEmpty()&&!aliasTest.startsWith("test_fallback_")&&testParity(s)) {
                    if(aliasTest.equals("test_fullclear_odd")) {
                        var f=s.proof.targets.getClass().getDeclaredField("fullClearRequired");f.setAccessible(true);f.setBoolean(s.proof.targets,true);
                    }else if(aliasTest.equals("test_resize_odd")) {
                        var window=Minecraft.getInstance().getWindow();if(window.isFullscreen())window.toggleFullScreen();
                        window.setWindowed(1280,800);org.lwjgl.glfw.GLFW.glfwSetWindowSize(window.getWindow(),1280,800);
                    }else {if(aliasTest.startsWith("test_return_"))requested=false;closeKnown("TEST_KNOWN_RETURN");}
                }
            }catch(Exception failure){throw discard("BANK_EXCHANGE_FAILURE",failure);}
        }
        s.copyIndex++;
    }
    public static void finalReturned(Object owner) {
        Session s=session;if(s==null)return;
        if(owner!=s.proof.finalRenderer||s.copyIndex!=3||s.copyStarted||!s.pair.completed()||s.state.phase()!=CanonicalHistoryState.Phase.READY)throw discard("INCOMPLETE_FINAL_PASS",null);
        if(mode==Mode.SHADOW)s.shadowFrames++;else s.activeFrames++;
        s.finalReturned=true;s.frameOpen=false;
    }
    public static void frameReturned() {
        if(session!=null&&session.frameOpen)throw discard("FRAME_WITHOUT_FINAL_COMMIT",null);
    }
    /** Catch only the controlled discard: no subsequent affected draw until a fresh normal reload. */
    public static void recoverDiscard(Discard failure) {
        try{
            outerAborts++;trace("OUTER_RENDER_UNWOUND_RECONSTRUCT_ONCE");
            // Only called after the outer GameRenderer.render stack has unwound. Native
            // finalizeLevelRendering did not return: destroy the abandoned pipelines first.
            // Their normal destroy hooks release retained-frame bookkeeping (including BSL),
            // without reading/copying the unknown RT3 generation. Reload then starts clean.
            Iris.getPipelineManager().destroyPipeline();
            com.pp.shaderlod.optimization.photon.PhotonSpatialNormal.abandonedPipelinesDestroyed();
            addEvent(new Event(0,"ABANDONED_PIPELINES_DESTROYED_WITHOUT_HISTORY_REPAIR",0,0,0,0,false));
            Iris.reload();DEFINITION_GATE.reconstructionReturned();addEvent(new Event(0,"RELOADED_STILL_AWAITING_POSITIVE_DEFINITION",0,0,0,0,false));status="REPLACEMENT_AWAITING_DEFINITION:"+failure.getMessage();trace(status);
        }
        catch(Exception|LinkageError reloadFailed){var stopped=fatal("RECONSTRUCTION_FAILED",reloadFailed);stopAfterUnwind(stopped);throw stopped;}
    }
    public static void frameStarting() {
        if(!STARTUP)return;
        if(DEFINITION_GATE.phase()==FreshDefinitionGate.Phase.STOPPED)throw new FatalStop(fatalReason,null);
        if(DEFINITION_GATE.phase()==FreshDefinitionGate.Phase.RELOAD_REQUIRED)recoverDiscard(new Discard("PENDING_UNKNOWN_GENERATION",null));
    }
    public static void unexpectedFrameFailure(Throwable failure) {
        if(failure instanceof Discard||failure instanceof FatalStop)return;
        if(DEFINITION_GATE.needsDefinition())throw fatal("REPLACEMENT_FRAME_FAILURE",failure);
        if(session!=null)discard("OUTER_FRAME_FAILURE",failure);quadScope=false;
    }
    public static void stopAfterUnwind(FatalStop failure) {
        outerAborts++;trace("FATAL_OUTER_RENDER_UNWOUND_ZERO_READ_PERMISSION");
        try{Iris.getPipelineManager().destroyPipeline();com.pp.shaderlod.optimization.photon.PhotonSpatialNormal.abandonedPipelinesDestroyed();}
        catch(Exception|LinkageError cleanup){failure.addSuppressed(cleanup);}
        trace("EXPLICIT_STOP_NO_SECOND_RECONSTRUCTION");throw failure;
    }
    private static void grantDefinition(Object targets) {
        if(!DEFINITION_GATE.needsDefinition())return;
        var d=DEFINITIONS.get(targets);require(d!=null&&d.defined,"POSITIVE_DEFINITION_REQUIRED");
        DEFINITION_GATE.positivelyDefined(targets,d.epoch);freshFault="";trace("FIRST_READ_PERMISSION_POSITIVELY_GRANTED");
    }
    private static RuntimeException preparationFailure(String reason,Throwable failure) {
        if(DEFINITION_GATE.needsDefinition()||DEFINITION_GATE.phase()==FreshDefinitionGate.Phase.STOPPED)return fatal(reason,failure);
        return discard(reason,failure);
    }
    private static FatalStop fatal(String reason,Throwable cause) {
        DEFINITION_GATE.stop();Session s=session;if(s!=null)s.state.discard();session=null;quadScope=false;
        fatalReason=reason+(cause==null?"":":"+cause.getMessage());readerAtFreshFailure=readerSubmissions;publicationAtFreshFailure=publications;
        status="STOPPED:"+fatalReason;trace("FRESH_DEFINITION_FAILED_NO_READ_PERMISSION");return new FatalStop(fatalReason,cause);
    }
    public static final class FatalStop extends RuntimeException {private FatalStop(String message,Throwable cause){super(message,cause);}}
    private static void closeKnown(String reason)throws Exception {
        Session s=session;if(s==null)return;
        require(!s.copyStarted&&s.pair.quiescent(),"PENDING_COPY_CANNOT_NATIVE_REPAIR");
        require(!quadScope||s.state.phase()==CanonicalHistoryState.Phase.READY,"RETURN_INSIDE_SUBMISSION");
        var repair=s.state.planNativeReturn();int source=s.texture(repair.source()),destination=s.texture(repair.destination());
        // One known C→other copy makes both physical banks native-equivalent for partial fallback.
        CanonicalDefinitionWitness.clean("KNOWN_RETURN_PRIOR");s.proof.verifyStorage();
        var alias=s.proof.clearSource(source);
        var witness=aliasTest.isEmpty()?null:new CanonicalAliasWitness(aliasTest,s.proof,s.state.exchanges(),source,destination);
        int observedRead=0,observedDestination=0;
        int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),oldUnit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),oldTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            alias.framebuffer().bindAsReadBuffer();int sourceRead=GL11.glGetInteger(GL11.GL_READ_BUFFER);
            try {
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+alias.slot());
                require(GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+alias.slot(),GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME)==source,"KNOWN_REPAIR_SOURCE");
                require(alias.framebuffer().getColorAttachment(alias.slot())==source&&GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE
                    &&GL11.glGetInteger(GL11.GL_READ_BUFFER)==GL30.GL_COLOR_ATTACHMENT0+alias.slot(),"KNOWN_REPAIR_CACHE_ROUTING_FBO");
                observedRead=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0+alias.slot(),GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
                RenderSystem.bindTexture(destination);observedDestination=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                require(observedDestination==destination,"KNOWN_REPAIR_DESTINATION");CanonicalDefinitionWitness.clean("KNOWN_REPAIR_PRE_SUBMISSION");
                GlStateManager._glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,0,0,s.proof.width,s.proof.height);
                if(aliasTest.equals("test_repair_failure")){trace("INJECT_REPAIR_SUBMISSION_GL_ERROR");GL11.glEnable(-1);}
                GL11.glFinish();
                require(GL11.glGetError()==GL11.GL_NO_ERROR&&GL45.glGetGraphicsResetStatus()==GL11.GL_NO_ERROR,"UNKNOWN_REPAIR_RESULT");repairs++;
            }finally{GL11.glReadBuffer(sourceRead);}
        }finally {
            RenderSystem.activeTexture(oldUnit);RenderSystem.bindTexture(oldTexture);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);
        }
        s.proof.selectAliases(s.proof.a,s.proof.b);s.proof.verifyNativeAliases();
        boolean samplerCanonical=true;
        // A pre-draw fallback follows Program.use: repair its already-bound dynamic history sampler too.
        if(CanonicalProgram()==s.proof.producer.getProgramId()) {
            int unit=GL20.glGetUniformi(s.expected.program(),s.expected.historyLocation()),priorUnit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            try{RenderSystem.activeTexture(GL13.GL_TEXTURE0+unit);RenderSystem.bindTexture(s.proof.a);samplerCanonical=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)==s.proof.a;require(samplerCanonical,"CANONICAL_BOUND_SAMPLER");}finally{RenderSystem.activeTexture(priorUnit);}
        }
        CanonicalDefinitionWitness.clean("CANONICAL_NATIVE_RETURN");
        if(witness!=null){s.proof.observeAliases(s.proof.a,s.proof.b);ALIAS_CHECKS.add(witness.finish(s.proof,alias,observedRead,observedDestination,samplerCanonical));trace("CANONICAL_RETURN_BEFORE_NATIVE_RESUMPTION");}
        s.state.nativeReturnCompleted();session=null;quadScope=false;aliasTest="";
        mode=requested?Mode.SHADOW:Mode.OFF;status="NATIVE_RETURN:"+reason;
        addEvent(new Event(s.state.exchanges(),"KNOWN_BOTH_BANK_RETURN",source,destination,destination,s.proof.a,true));
    }
    private static boolean testParity(Session s){return s.state.exchanges()%2==(aliasTest.endsWith("even")?0:1);}
    private static boolean eligible() {
        return com.pp.shaderlod.ShaderLodConfig.ENABLED.get()
            &&com.pp.shaderlod.ShaderLodConfig.PRESET.get()==com.pp.shaderlod.LodPreset.PERFORMANCE
            &&MakeUpLeanGraph.onlyDeadCopy(OptimizationConfig.snapshot())&&!FrameCopyRuntime.observing()
            &&HistoryRotationPreparation.pinnedCurrentContext();
    }
    /** Qualified callbacks must be inert; none may consume an error between pair submissions. */
    private static void inertDiagnostics() {
        require(!FrameCopyRuntime.observing()&&!com.pp.shaderlod.optimization.mips.BslMipWitness.observing(),"ACTIVE_COPY_OBSERVER");
        for(var path:CONFIGURED_PATHS.entrySet())require(!path.getValue()&&!CapturedConsumerPaths.PATHS.get(path.getKey())&&System.getProperty(path.getKey(),"").isBlank(),"DIAGNOSTIC_CALLBACK_NOT_INERT:"+path.getKey());
        for(String key:BOOLEAN_DIAGNOSTICS)require(!STARTUP_BOOLEAN_DIAGNOSTICS.contains(key)&&!Boolean.getBoolean(key),"DIAGNOSTIC_CALLBACK_NOT_INERT:"+key);
    }
    private static Set<String> enabledBooleanDiagnostics() {
        var enabled=new HashSet<String>();for(String key:BOOLEAN_DIAGNOSTICS)if(Boolean.getBoolean(key))enabled.add(key);return Set.copyOf(enabled);
    }
    // First accessed on render-thread admission, never during mixin transformation/postApply.
    private static final class CapturedConsumerPaths {static final Map<String,Boolean> PATHS=capturedPaths();}
    private static Map<String,Boolean> capturedPaths() {
        var paths=new HashMap<String,Boolean>();
        for(String[] spec:new String[][]{{"shaderlod.dev.makeupMrtProbe","MakeupMrtProbe","OUT"},{"shaderlod.dev.complementaryCopyGraph","ComplementaryCopyGraph","OUTPUT"}}) {
            boolean configured=!System.getProperty(spec[0],"").isBlank();
            try {configured|=!capturedPath(Class.forName("com.pp.shaderlod.dev."+spec[1]),spec[2]).isBlank();}
            catch(ClassNotFoundException absentDevClient) { /* No dev callback class in production. */ }
            catch(Exception|LinkageError unknown) {configured=true;}
            paths.put(spec[0],configured);
        }
        return Map.copyOf(paths);
    }

    private static String capturedPath(Class<?> type,String name)throws ReflectiveOperationException {
        var field=type.getDeclaredField(name);field.setAccessible(true);return (String)field.get(null);
    }
    private static boolean context(Session s) {
        return s.proof.pipeline==Iris.getPipelineManager().getPipelineNullable()&&s.world==Minecraft.getInstance().level
            &&s.pack==Iris.getCurrentPack().orElse(null)&&s.generation==OptimizationConfig.generation()&&!Iris.isFallback()
            &&com.pp.shaderlod.ShaderLodConfig.ENABLED.get()&&com.pp.shaderlod.ShaderLodConfig.PRESET.get()==com.pp.shaderlod.LodPreset.PERFORMANCE
            &&MakeUpLeanGraph.onlyDeadCopy(OptimizationConfig.snapshot())&&!FrameCopyRuntime.observing();
    }
    private static int CanonicalProgram(){return GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);}
    private static void addEvent(Event event){if(EVENTS.size()==64)EVENTS.removeFirst();EVENTS.addLast(event);}
    private static Discard discard(String reason,Throwable cause) {
        if(!DEFINITION_GATE.readAllowed())throw fatal("FAILED_REPLACEMENT:"+reason,cause);
        Session s=session;Object allocation=s==null?null:s.proof.targets;
        if(s!=null){lastCopyObligation="index="+s.copyIndex+",started="+s.copyStarted+",pairStarted="+s.pair.started()+",pairReturned="+s.pair.returned()+",pairCompleted="+s.pair.completed();s.state.discard();blockedPipeline=s.proof.pipeline;blockedGeneration=s.generation;}
        trace("UNCERTAIN_GENERATION_BEFORE_DISCARD:"+reason);DEFINITION_GATE.discard(allocation);session=null;quadScope=false;aliasTest="";discarded++;mode=requested?Mode.SHADOW:Mode.OFF;status="DISCARDED:"+reason;
        return new Discard(reason,cause);
    }
    public static final class Discard extends RuntimeException {private Discard(String message,Throwable cause){super(message,cause);}}
    private static void require(boolean valid,String reason){if(!valid)throw new IllegalStateException(reason);}
}

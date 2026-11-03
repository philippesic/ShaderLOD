package com.pp.shaderlod.optimization;
import java.util.function.BooleanSupplier;
import java.util.Set;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle;
/** Complete request Apply: prepare, persist, update proven frame uniforms or reload, and recover owned state on failure. */
public final class OptimizationEditTransaction {
    @FunctionalInterface public interface Reload { void run() throws Exception; }
    @FunctionalInterface public interface Consumer { CompileOptionControl.Session open(OptimizationSnapshot flags) throws Exception; }
    public record Result(boolean success,String message,boolean recoveryComplete) {
        public Result(boolean success,String message){this(success,message,success);}
    }
    private static volatile boolean liveUniformUpdates;
    private static final Set<OptimizationId> LIVE_UNIFORM_IDS=Set.of(
        OptimizationId.CLOUD_PRIMARY_DETAIL,OptimizationId.CLOUD_PRIMARY_BUDGET,
        OptimizationId.CLOUD_SECONDARY_DETAIL,OptimizationId.CLOUD_SECONDARY_BUDGET,
        OptimizationId.CLOUD_OPACITY_EXIT,OptimizationId.REFLECTION_CLOUD_DETAIL,OptimizationId.REFLECTION_CLOUD_BUDGET,
        OptimizationId.VOLUMETRIC_LIGHT_BUDGET,OptimizationId.AURORA_BUDGET,OptimizationId.AO_BUDGET,
        OptimizationId.REFLECTION_REFINEMENT,OptimizationId.ZERO_EMISSION_ARITHMETIC);
    /** Called only by the root NeoForge1.21.1 entry point. Other targets retain their existing reload behavior. */
    public static void enableLiveUniformUpdates(){liveUniformUpdates=true;}
    public static Set<OptimizationId> liveUniformIds(){return LIVE_UNIFORM_IDS;}
    public static boolean cpuOnlyRequests(OptimizationSnapshot flags){
        for(var id:OptimizationId.values())if(flags.enabled(id)&&id!=OptimizationId.QUAD_COMMAND_COMPACTION&&id!=OptimizationId.SHADOW_ADMISSION_CACHE)return false;
        return true;
    }
    public static boolean cpuOnlyFamily(String family){return Set.of("makeup-ultra-fast-shaders","sildurs-vibrant-shaders","bsl-shaders",
        "complementary-reimagined","complementary-unbound","bliss-shader","photon","solas-shader","astralex").contains(family);}
    static boolean liveCpuChangesAllowed(OptimizationSnapshot previous,OptimizationSnapshot next){return liveUniformUpdates&&cpuOnlyRequests(previous)&&cpuOnlyRequests(next);}
    /** Active unsafe peers can own generation-sensitive resources even when their flag is not edited. */
    static boolean liveUniformChangesAllowed(OptimizationSnapshot previous,OptimizationSnapshot next){
        if(!liveUniformUpdates)return false;
        for(var id:OptimizationId.values())if(!LIVE_UNIFORM_IDS.contains(id)&&(previous.enabled(id)||next.enabled(id)))return false;
        return true;
    }
    private OptimizationEditTransaction() {}
    /** Pure caller seam for authentic CPU tests and controls without a compile-time consumer. */
    public static Result apply(OptimizationSnapshot next,Reload reload,BooleanSupplier fallback) {
        return apply(next,reload,fallback,flags->null);
    }
    public static Result apply(OptimizationSnapshot next,Reload reload,BooleanSupplier fallback,Consumer consumer) {
        var previous=OptimizationConfig.snapshot();CompileOptionControl.Session session=null;
        boolean suspended=false,finishAttempted=false;
        try {
            session=consumer.open(next); // capture native availability before suspension
            if(next.equals(previous)&&session==null)return new Result(true,"Unchanged");
            MakeUpQuadLifecycle.beginSettingsApply();suspended=true;
            com.pp.shaderlod.optimization.makeuppresence.MakeUpPresencePrograms.clear();
            OptimizationConfig.replace(next);
            boolean prepared=session!=null&&session.prepare();
            boolean live=false,cpuLive=false,reloaded=false;
            if(!next.equals(previous)||prepared) {
                cpuLive=!prepared&&session!=null&&liveUniformUpdates&&(
                    liveCpuChangesAllowed(previous,next)&&session.allowLiveCpu(previous,next)
                    ||CompileOptionControl.normalCpuPermission(session,previous,next).allowed());
                live=!com.pp.shaderlod.optimization.makeuppresence.MakeUpTerrainPresence.changesPresence(previous,next)
                    &&(cpuLive||!prepared&&session!=null&&liveUniformChangesAllowed(previous,next));
                if(!live){if(session==null)reload.run();else session.reload(reload);reloaded=true;}
                if(fallback.getAsBoolean())throw new IllegalStateException(live?"Shader pipeline was in fallback after live preparation":"Shader reload fell back");
            }
            // A failed finish is a failed Apply too; do not replay a partially attempted finish.
            if(session!=null){finishAttempted=true;session.finish();}
            return new Result(true,cpuLive?"Applied certified native CPU requests without shader reload; source/admission guards remain active":live?
                "Applied live uniform requests; visible at the next render frame; source/admission guards remain active":
                reloaded?"Applied independent requests and reloaded the selected shader; source/admission guards remain active":
                "Requests and selected shader graph unchanged");
        }catch(Exception|Error failure) {
            String recoveryFailure="";boolean recovered=!finishAttempted;
            if(suspended) {
                com.pp.shaderlod.optimization.makeuppresence.MakeUpPresencePrograms.clear();
                try {OptimizationConfig.replace(previous);}catch(Exception|Error restore) {
                    recovered=false;OptimizationConfig.restoreInMemory(previous);suppress(failure,restore);
                    recoveryFailure="; previous flags could not be persisted: "+restore;
                }
                boolean mayReload=!(failure instanceof Exception exception&&com.pp.shaderlod.integration.KnownRuntimeBoundary.isRefusal(exception));
                if(!mayReload)recovered=false;
                if(session!=null)try {session.restore();}catch(Exception|Error restore) {
                    recovered=false;suppress(failure,restore);mayReload=false;recoveryFailure+="; owned consumer recovery refused: "+restore;
                }
                if(mayReload)try {
                    if(session==null)reload.run();else session.recoveryReload(reload);
                    if(fallback.getAsBoolean())throw new IllegalStateException("Recovery reload fell back");
                }catch(Exception|Error restore) {recovered=false;suppress(failure,restore);recoveryFailure+="; recovery reload failed: "+restore;}
            }
            if(session!=null&&!finishAttempted)try {finishAttempted=true;session.finish();}
            catch(Exception|Error restore){recovered=false;suppress(failure,restore);recoveryFailure+="; consumer finish failed: "+restore;}
            // Restoration may acquire a new deferred queue after finish was attempted. Release
            // that recovery ownership independently; never replay the failed finish itself.
            if(suspended&&session!=null)try {session.releaseRecovery();}
            catch(Exception|Error restore){recovered=false;suppress(failure,restore);recoveryFailure+="; consumer recovery release failed: "+restore;}
            if(failure instanceof Error error)throw error; // exact primary identity after all restoration attempts
            return new Result(false,(suspended?"Apply failed: ":"Consumer preparation refused; previous selection retained: ")+failure.getMessage()+recoveryFailure,recovered&&OptimizationConfig.snapshot().equals(previous));
        }finally {
            if(suspended)MakeUpQuadLifecycle.endSettingsApply();
        }
    }
    private static void suppress(Throwable primary,Throwable secondary){if(primary!=secondary)primary.addSuppressed(secondary);}
}

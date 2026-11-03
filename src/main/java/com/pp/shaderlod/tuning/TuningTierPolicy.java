package com.pp.shaderlod.tuning;
import com.pp.shaderlod.optimization.*;
import java.util.*;
import static com.pp.shaderlod.optimization.OptimizationId.*;
/** Explicit goal restrictions; speed never certifies visual fidelity or runtime permission. */
public final class TuningTierPolicy {
    public static final String VERSION="tier-policy-v1";
    private static final Set<OptimizationId> EXACT=Set.of(QUAD_COMMAND_COMPACTION,INDEX_SCALAR_CACHE,SHADOW_ADMISSION_CACHE,
        BINDING_CERTIFICATE_CACHE,BINDING_DIGEST_STREAMING,ZERO_EMISSION_ARITHMETIC);
    private static final Set<OptimizationId> BALANCED_BUDGETS=Set.of(CLOUD_PRIMARY_DETAIL,CLOUD_PRIMARY_BUDGET,
        CLOUD_SECONDARY_DETAIL,CLOUD_SECONDARY_BUDGET,CLOUD_OPACITY_EXIT,REFLECTION_CLOUD_DETAIL,REFLECTION_CLOUD_BUDGET,
        VOLUMETRIC_LIGHT_BUDGET,AURORA_BUDGET,AO_BUDGET,REFLECTION_REFINEMENT,SHADOW_SAMPLE_BUDGET);
    private static final Set<OptimizationId> PERFORMANCE_RENDERER=Set.of(QUAD_COMMAND_COMPACTION,INDEX_SCALAR_CACHE,SHADOW_ADMISSION_CACHE);
    private TuningTierPolicy(){}
    public static boolean allowsId(TuningGoal goal,OptimizationId id) {
        if(goal==TuningGoal.QUALITY)return EXACT.contains(id);
        if(goal==TuningGoal.BALANCED)return !PERFORMANCE_RENDERER.contains(id)&&(EXACT.contains(id)||BALANCED_BUDGETS.contains(id));
        return true;
    }
    public static String rejection(TuningGoal goal,String family,OptimizationId id,Map<String,String> nativeOptions) {return rejection(goal,family,id,nativeOptions,Map.of());}
    public static String rejection(TuningGoal goal,String family,OptimizationId id,Map<String,String> nativeOptions,Map<String,String> effectOptions) {
        if(Set.of("complementary-unbound","complementary-reimagined","bliss-shader").contains(family)
                &&Set.of(SPATIAL_LIGHTING_REUSE,SPATIAL_LIGHTING_RATE).contains(id)&&"FULL".equals(effectOptions.get("opaque_lighting.mode")))
            return "INACTIVE_EFFECT_POLICY: actual OpaqueLighting FULL requests native work; independent flag does not enable adaptive lighting";
        if(!allowsId(goal,id))return goal==TuningGoal.QUALITY?"Excluded by reviewed exact-only Quality search policy":
            PERFORMANCE_RENDERER.contains(id)?"Native CPU renderer requires engine PERFORMANCE; Balanced engine remains unchanged":
            "Not in the conservative Balanced request policy; manual control remains available";
        var entry=TuningEligibility.classify(family,id,nativeOptions);
        if(entry.state()!=TuningEligibility.State.DECLARED_CONSUMER)return entry.state()+": "+entry.reason();
        // A source consumer can exist while its exact selected budget removes no
        // work. Keep that distinct from unsupported source and an active trial.
        // These are the pinned kernels' known no-reduction cases, not estimates
        // of pixel coverage or permission to lower native quality for a trial.
        if(id==AO_BUDGET) {
            if("photon".equals(family)&&"SHADER_AO_GTAO".equals(nativeOptions.get("SHADER_AO"))
                    &&"3".equals(nativeOptions.get("GTAO_HORIZON_STEPS")))
                return "NO_WORK_REDUCTION: Photon native GTAO horizon3 remains3 after ceil(3*0.875/0.75); slices and native options remain unchanged";
            if("complementary-unbound".equals(family)&&"2".equals(nativeOptions.get("SSAO_QUALI_DEFINE")))
                return "NO_WORK_REDUCTION: Unbound native SSAO quality2 selects4 samples and this AO budget has minimum4";
            if("bliss-shader".equals(family)&&goal==TuningGoal.BALANCED)
                return "NO_WORK_REDUCTION: Bliss native SSAO7 remains7 at every distance under Balanced ceil(7*ratio), ratio>=0.875";
        }
        return null;
    }
    public static List<String> rejectedVector(TuningGoal goal,String family,OptimizationSnapshot flags,Map<String,String> nativeOptions) {return rejectedVector(goal,family,flags,nativeOptions,Map.of());}
    public static List<String> rejectedVector(TuningGoal goal,String family,OptimizationSnapshot flags,Map<String,String> nativeOptions,Map<String,String> effectOptions) {
        var reasons=new ArrayList<String>();
        for(var id:OptimizationId.values())if(flags.enabled(id)) {
            String why=rejection(goal,family,id,nativeOptions,effectOptions);if(why!=null)reasons.add(id.key()+": "+why);
        }
        return List.copyOf(reasons);
    }
}

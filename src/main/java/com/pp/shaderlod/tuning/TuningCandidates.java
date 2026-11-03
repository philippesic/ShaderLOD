package com.pp.shaderlod.tuning;
import com.pp.shaderlod.optimization.*;
import java.util.*;
import static com.pp.shaderlod.optimization.OptimizationId.*;
/** Fixed canonical search order. A candidate carries its complete legal coupled request change. */
public final class TuningCandidates {
    public record Candidate(String name,Set<OptimizationId> on,Set<OptimizationId> off) {
        public Candidate {on=Set.copyOf(on);off=Set.copyOf(off);}
        public OptimizationSnapshot apply(OptimizationSnapshot baseline) {
            var next=baseline;for(var id:off)next=next.with(id,false);for(var id:on)next=next.with(id,true);
            // Each renderer consumer calls the exact native-graph validator. Without its
            // metadata cache even a useful fill change can spend seconds validating sources.
            if(!off.contains(SHADOW_ADMISSION_CACHE)&&RENDERER_CONSUMERS.stream().anyMatch(next::enabled))
                next=next.with(SHADOW_ADMISSION_CACHE,true);
            if(off.contains(SHADOW_ADMISSION_CACHE))for(var id:RENDERER_CONSUMERS)next=next.with(id,false);
            return next;
        }
    }
    private static final Set<OptimizationId> RENDERER_CONSUMERS=Set.of(
        QUAD_COMMAND_COMPACTION,INDEX_SCALAR_CACHE,SHADOW_POSITIVE_VERTEX,
        SHADOW_VISIBILITY_LISTS,SHADOW_FRUSTUM_COHERENCE);
    // Only actual EarlyAlphaSourceCache callers (including delegated source readers).
    // Other families' implementations of the same control may use different guards.
    private static final Map<String,Set<OptimizationId>> BINDING_CONSUMERS=Map.ofEntries(
        Map.entry("photon",Set.of(EARLY_ALPHA,SHADOW_RECEIVER_FOOTPRINT,MERGED_TERRAIN,DEPTH_PRIMING)),
        Map.entry("complementary-reimagined",Set.of(AO_RENDER_SCALE,EARLY_ALPHA,SHADOW_RECEIVER_FOOTPRINT,TEMPORAL_SHADOW_FILTER,
            MERGED_TERRAIN,DEPTH_PRIMING,SPATIAL_LIGHTING_REUSE,SPATIAL_LIGHTING_RATE,PIXEL_SCALE,REFLECTION_PROJECTED_REFINEMENT)),
        Map.entry("complementary-unbound",Set.of(AO_RENDER_SCALE,EARLY_ALPHA,SHADOW_RECEIVER_FOOTPRINT,TEMPORAL_SHADOW_FILTER,
            MERGED_TERRAIN,SPATIAL_LIGHTING_REUSE,SPATIAL_LIGHTING_RATE,PIXEL_SCALE,REFLECTION_PROJECTED_REFINEMENT)),
        Map.entry("bsl-shaders",Set.of(EARLY_ALPHA,MERGED_TERRAIN,DEPTH_PRIMING,SPATIAL_LIGHTING_REUSE,
            SPATIAL_LIGHTING_RATE,PIXEL_SCALE,REFLECTION_PROJECTED_REFINEMENT,REFLECTION_NEXT_EVENT_REFINEMENT)),
        Map.entry("makeup-ultra-fast-shaders",Set.of(AO_RENDER_SCALE,EARLY_ALPHA,DEPTH_PRIMING,SHADOW_RECEIVER_FOOTPRINT,
            TEMPORAL_SHADOW_FILTER,SHADOW_SAMPLE_BUDGET,SPATIAL_LIGHTING_REUSE,SPATIAL_LIGHTING_RATE,PIXEL_SCALE)),
        Map.entry("sildurs-vibrant-shaders",Set.of(AO_RENDER_SCALE,EARLY_ALPHA,MERGED_TERRAIN,DEPTH_PRIMING,SPATIAL_LIGHTING_REUSE,SPATIAL_LIGHTING_RATE,PIXEL_SCALE,
            SHADOW_RECEIVER_FOOTPRINT,REFLECTION_PROJECTED_REFINEMENT,REFLECTION_NEXT_EVENT_REFINEMENT)),
        Map.entry("solas-shader",Set.of(AO_RENDER_SCALE,EARLY_ALPHA,MERGED_TERRAIN,DEPTH_PRIMING,SHADOW_RECEIVER_FOOTPRINT,
            REFLECTION_PROJECTED_REFINEMENT,REFLECTION_TRACE_FADE,SPATIAL_LIGHTING_REUSE,SPATIAL_LIGHTING_RATE,PIXEL_SCALE)),
        Map.entry("bliss-shader",Set.of(AO_RENDER_SCALE,EARLY_ALPHA,MERGED_TERRAIN,DEPTH_PRIMING)),
        Map.entry("astralex",Set.of(AO_RENDER_SCALE,EARLY_ALPHA,MERGED_TERRAIN,DEPTH_PRIMING,
            SPATIAL_LIGHTING_REUSE,SPATIAL_LIGHTING_RATE,PIXEL_SCALE)));
    private static Set<OptimizationId> bindingConsumers(String family) {
        return family==null?Set.of():BINDING_CONSUMERS.getOrDefault(family,Set.of());
    }
    public static final Set<OptimizationId> UNAVAILABLE=Set.of(SHADOW_PREFIX,PHYSICAL_RENDER_SCALE,SPATIAL_SHADOW_REUSE);
    public static String unavailableReason(OptimizationId id) {
        return switch(id) {
            case SHADOW_PREFIX->"DUPLICATE_LABEL: use pom_empty_height_prefix; no separate shadow-prefix algorithm";
            case PHYSICAL_RENDER_SCALE->"REJECTED_PROTOTYPE: fixed-scale trial failed fine-detail quality; window resizing is diagnostic";
            case SPATIAL_SHADOW_REUSE->"NO_INDEPENDENT_PRODUCER: existing spatial lighting field combines lighting with shadow/AO";
            default->throw new IllegalArgumentException("Not a globally unavailable request: "+id);
        };
    }
    private TuningCandidates() {}
    public static List<Candidate> ordered() {
        var list=new ArrayList<Candidate>();var seen=EnumSet.noneOf(OptimizationId.class);
        // Try the historically useful native renderer as a complete cache/consumer group
        // before spending the finite budget on isolated preparation flags.
        list.add(new Candidate("renderer-fill-and-admission-cache",Set.of(SHADOW_ADMISSION_CACHE,QUAD_COMMAND_COMPACTION),Set.of()));
        // Live switching still uses the transaction's existing unsafe-peer exclusion.
        for(var id:OptimizationId.values())if(OptimizationEditTransaction.liveUniformIds().contains(id))add(list,seen,id);
        for(var id:List.of(BINDING_CERTIFICATE_CACHE,BINDING_DIGEST_STREAMING,QUAD_COMMAND_COMPACTION,INDEX_SCALAR_CACHE,SHADOW_ADMISSION_CACHE))add(list,seen,id);
        list.add(new Candidate("cloud-detail-budget",Set.of(CLOUD_PRIMARY_DETAIL,CLOUD_PRIMARY_BUDGET,CLOUD_OPACITY_EXIT),Set.of()));
        list.add(new Candidate("cloud-secondary-detail-budget",Set.of(CLOUD_SECONDARY_DETAIL,CLOUD_SECONDARY_BUDGET,CLOUD_SECONDARY_EXTENT_CACHE),Set.of()));
        list.add(new Candidate("reflection-cloud-detail-budget",Set.of(REFLECTION_CLOUD_DETAIL,REFLECTION_CLOUD_BUDGET),Set.of()));
        list.add(new Candidate("binding-cache-consumers",Set.of(BINDING_CERTIFICATE_CACHE,BINDING_DIGEST_STREAMING,CLOUD_PRIMARY_BUDGET,AO_BUDGET,REFLECTION_REFINEMENT),Set.of()));
        // A cache alone cannot win without a consumer; a losing uncached consumer
        // would otherwise prevent this greedy search from ever measuring the pair.
        list.add(new Candidate("early-alpha-and-certificate-cache",Set.of(EARLY_ALPHA,BINDING_CERTIFICATE_CACHE),Set.of()));
        list.add(new Candidate("early-alpha-and-streaming-certificate-cache",Set.of(EARLY_ALPHA,BINDING_CERTIFICATE_CACHE,BINDING_DIGEST_STREAMING),Set.of()));
        list.add(new Candidate("shadow-cache-consumers",Set.of(SHADOW_ADMISSION_CACHE,INDEX_SCALAR_CACHE,QUAD_COMMAND_COMPACTION,SHADOW_POSITIVE_VERTEX,SHADOW_VISIBILITY_LISTS,SHADOW_FRUSTUM_COHERENCE),Set.of()));
        for(var id:OptimizationId.values())if(!UNAVAILABLE.contains(id))add(list,seen,id);
        return List.copyOf(list);
    }
    public static List<Candidate> ordered(String family) {
        var consumers=bindingConsumers(family);
        return assembled(family).stream().map(candidate->{
            if(candidate.off().contains(BINDING_CERTIFICATE_CACHE)||Collections.disjoint(candidate.on(),consumers))return candidate;
            var on=EnumSet.noneOf(OptimizationId.class);on.addAll(candidate.on());on.add(BINDING_CERTIFICATE_CACHE);
            return new Candidate(candidate.name(),on,candidate.off());
        }).toList();
    }
    private static List<Candidate> assembled(String family) {
        if("makeup-ultra-fast-shaders".equals(family)){
            var list=new ArrayList<>(ordered());
            // Keep the verified renderer first. Closed shadow experiments remain
            // available after the canonical inventory, with their explicit cache policy.
            list.add(new Candidate("renderer-fill-and-shadow-sample-budget",
                Set.of(QUAD_COMMAND_COMPACTION,SHADOW_ADMISSION_CACHE,SHADOW_SAMPLE_BUDGET),Set.of()));
            list.add(new Candidate("shadow-sample-budget-and-certificate-cache",
                Set.of(SHADOW_SAMPLE_BUDGET,BINDING_CERTIFICATE_CACHE),Set.of()));
            list.add(new Candidate("renderer-fill-and-shadow-sample-certificate-cache",
                Set.of(QUAD_COMMAND_COMPACTION,SHADOW_ADMISSION_CACHE,SHADOW_SAMPLE_BUDGET,BINDING_CERTIFICATE_CACHE),Set.of()));
            return List.copyOf(list);
        }
        if("complementary-unbound".equals(family)){
            var list=new ArrayList<>(ordered());
            // Preserve the verified native renderer first, then the accepted
            // combined cloud profile. Closed exploratory groups stay available later.
            list.add(1,new Candidate("renderer-fill-and-primary-cloud-budget",
                Set.of(QUAD_COMMAND_COMPACTION,SHADOW_ADMISSION_CACHE,CLOUD_PRIMARY_BUDGET),Set.of()));
            list.add(new Candidate("renderer-fill-and-sky-atmosphere-ownership",
                Set.of(QUAD_COMMAND_COMPACTION,SHADOW_ADMISSION_CACHE,SKY_ATMOSPHERE_OWNERSHIP),Set.of()));
            list.add(new Candidate("renderer-fill-and-reflection-deferred-metric",
                Set.of(QUAD_COMMAND_COMPACTION,SHADOW_ADMISSION_CACHE,REFLECTION_DEFERRED_METRIC),Set.of()));
            return List.copyOf(list);
        }
        if("bliss-shader".equals(family)){
            var list=new ArrayList<>(ordered());
            // The closed shader trials stay available without delaying the
            // normal renderer check or changing the canonical request inventory.
            list.add(new Candidate("renderer-fill-and-volume-light-budget",
                Set.of(QUAD_COMMAND_COMPACTION,SHADOW_ADMISSION_CACHE,VOLUMETRIC_LIGHT_BUDGET),Set.of()));
            list.add(new Candidate("renderer-fill-and-shadow-sample-budget",
                Set.of(QUAD_COMMAND_COMPACTION,SHADOW_ADMISSION_CACHE,SHADOW_SAMPLE_BUDGET),Set.of()));
            return List.copyOf(list);
        }
        if("complementary-reimagined".equals(family)){
            var list=new ArrayList<>(ordered());
            list.add(new Candidate("renderer-fill-and-ao-budget",
                Set.of(QUAD_COMMAND_COMPACTION,SHADOW_ADMISSION_CACHE,AO_BUDGET),Set.of()));
            list.add(new Candidate("depth-priming-and-certificate-cache",
                Set.of(DEPTH_PRIMING,BINDING_CERTIFICATE_CACHE),Set.of()));
            list.add(new Candidate("renderer-fill-and-depth-priming-certificate-cache",
                Set.of(QUAD_COMMAND_COMPACTION,SHADOW_ADMISSION_CACHE,DEPTH_PRIMING,BINDING_CERTIFICATE_CACHE),Set.of()));
            list.add(new Candidate("temporal-shadow-and-certificate-cache",
                Set.of(TEMPORAL_SHADOW_FILTER,BINDING_CERTIFICATE_CACHE),Set.of()));
            list.add(new Candidate("renderer-fill-and-temporal-shadow-certificate-cache",
                Set.of(QUAD_COMMAND_COMPACTION,SHADOW_ADMISSION_CACHE,TEMPORAL_SHADOW_FILTER,BINDING_CERTIFICATE_CACHE),Set.of()));
            return List.copyOf(list);
        }
        if(!"sildurs-vibrant-shaders".equals(family))return ordered();
        var list=new ArrayList<>(ordered());int position=1; // Preserve the renderer pair first.
        // Pinned native115 source: deferred funShadows uses12 taps; Clouds3 calls
        // the primary cloudsIT8 consumer. See performance-frontier-20261016.md.
        // This schedules existing hypotheses; actual loaded coverage still needs evidence.
        for(var id:List.of(SHADOW_SAMPLE_BUDGET,CLOUD_PRIMARY_BUDGET)) {
            for(int i=0;i<list.size();i++)if(list.get(i).name().equals(id.key())) {
                var candidate=list.remove(i);list.add(position++,candidate);break;
            }
        }
        return List.copyOf(list);
    }
    public static List<Candidate> ordered(String family,TuningGoal goal) {
        Objects.requireNonNull(goal);
        // Retain the complete bounded inventory. Disallowed candidates get explicit
        // SKIPPED rows; no union of individually winning goal profiles is constructed.
        return ordered(family);
    }
    private static void add(List<Candidate> list,Set<OptimizationId> seen,OptimizationId id) {
        if(!seen.add(id))return;
        Set<OptimizationId> off=switch(id) {
            case SPATIAL_LIGHTING_REUSE->Set.of(SPATIAL_LIGHTING_RATE,PIXEL_SCALE);
            case SPATIAL_LIGHTING_RATE->Set.of(SPATIAL_LIGHTING_REUSE,PIXEL_SCALE);
            case PIXEL_SCALE->Set.of(SPATIAL_LIGHTING_REUSE,SPATIAL_LIGHTING_RATE);
            default->Set.of();
        };
        list.add(new Candidate(id.key(),Set.of(id),off));
    }
    public static List<Candidate> cleanup(OptimizationSnapshot best) {
        var result=new ArrayList<Candidate>();
        for(var id:OptimizationId.values())if(best.enabled(id))result.add(new Candidate("remove-"+id.key(),Set.of(),Set.of(id)));
        return result.stream().limit(8).toList();
    }
    /** Removing the certificate cache must not leave this family's dependent work uncached. */
    public static List<Candidate> cleanup(String family,OptimizationSnapshot best) {
        var consumers=bindingConsumers(family);
        return cleanup(best).stream().map(candidate->{
            if(!candidate.off().contains(BINDING_CERTIFICATE_CACHE))return candidate;
            var off=EnumSet.noneOf(OptimizationId.class);off.addAll(candidate.off());
            for(var id:consumers)if(best.enabled(id))off.add(id);
            return new Candidate(candidate.name(),candidate.on(),off);
        }).toList();
    }
}

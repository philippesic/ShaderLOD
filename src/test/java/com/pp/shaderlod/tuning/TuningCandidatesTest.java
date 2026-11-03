package com.pp.shaderlod.tuning;
import com.pp.shaderlod.optimization.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.pp.shaderlod.optimization.OptimizationId.*;
class TuningCandidatesTest {
    @Test void sildurPrioritizesSourceBackedShadowAndPrimaryCloudCandidatesAfterRendererPair(){
        var defaults=TuningCandidates.ordered();var sildur=TuningCandidates.ordered("sildurs-vibrant-shaders");
        assertEquals(defaults.getFirst(),sildur.getFirst());
        assertEquals(SHADOW_SAMPLE_BUDGET.key(),sildur.get(1).name());
        assertEquals(CLOUD_PRIMARY_BUDGET.key(),sildur.get(2).name());
        assertEquals(Set.of(SHADOW_SAMPLE_BUDGET),sildur.get(1).on());
        assertEquals(Set.of(CLOUD_PRIMARY_BUDGET),sildur.get(2).on());
        assertTrue(sildur.indexOf(defaults.stream().filter(c->c.name().equals(REFLECTION_CLOUD_DETAIL.key())).findFirst().orElseThrow())>2);
        assertEquals(defaults.size(),sildur.size());assertEquals(new HashSet<>(names(defaults)),new HashSet<>(names(sildur)));
        assertEquals(sildur.size(),sildur.stream().map(TuningCandidates.Candidate::name).distinct().count());
        // Reordering retains every complete legal request vector and all non-priority relative order.
        var moved=Set.of(SHADOW_SAMPLE_BUDGET.key(),CLOUD_PRIMARY_BUDGET.key());
        assertEquals(names(defaults.stream().filter(c->!moved.contains(c.name())).toList()),names(sildur.stream().filter(c->!moved.contains(c.name())).toList()));
        for(var c:defaults)assertEquals(c.apply(OptimizationSnapshot.allRequested()),sildur.stream().filter(s->s.name().equals(c.name())).findFirst().orElseThrow().apply(OptimizationSnapshot.allRequested()));
        assertEquals(sildur,TuningCandidates.ordered("sildurs-vibrant-shaders"));
    }
    @Test void otherFamiliesAndLegacyApiRetainExactDefaultOrder(){
        var defaults=TuningCandidates.ordered();
        for(String family:List.of("bsl-shaders","photon","solas-shader","astralex","unknown"))
            assertEquals(names(defaults),names(TuningCandidates.ordered(family)),family);
        assertEquals(defaults,TuningCandidates.ordered(null));
    }
    @Test void makeupKeepsKnownRendererFirstAndRetainsCompleteCachedShadowGroups(){
        var defaults=TuningCandidates.ordered();var ordered=TuningCandidates.ordered("makeup-ultra-fast-shaders");
        assertEquals(names(defaults),names(ordered.subList(0,defaults.size())));
        assertEquals(defaults.getFirst(),ordered.getFirst());
        assertEquals(defaults.size()+3,ordered.size());
        assertEquals(ordered.size(),ordered.stream().map(TuningCandidates.Candidate::name).distinct().count());
        var cached=ordered.stream().filter(c->c.name().equals("shadow-sample-budget-and-certificate-cache")).findFirst().orElseThrow();
        var combined=ordered.stream().filter(c->c.name().equals("renderer-fill-and-shadow-sample-certificate-cache")).findFirst().orElseThrow();
        assertEquals(Set.of(SHADOW_SAMPLE_BUDGET,BINDING_CERTIFICATE_CACHE),cached.on());
        assertEquals(Set.of(QUAD_COMMAND_COMPACTION,SHADOW_ADMISSION_CACHE,SHADOW_SAMPLE_BUDGET,BINDING_CERTIFICATE_CACHE),combined.on());
        assertEquals(cached.on(),enabled(cached.apply(OptimizationSnapshot.noneRequested())));
        assertEquals(combined.on(),enabled(combined.apply(OptimizationSnapshot.noneRequested())));
    }
    @Test void reimaginedKeepsCanonicalRendererFirstAndExactCertificateCouplings(){
        var defaults=TuningCandidates.ordered();var ordered=TuningCandidates.ordered("complementary-reimagined");
        assertEquals(names(defaults),names(ordered.subList(0,defaults.size())));
        assertEquals(defaults.size()+5,ordered.size());
        assertEquals(ordered.size(),ordered.stream().map(TuningCandidates.Candidate::name).distinct().count());
        for(String name:List.of("temporal-shadow-and-certificate-cache","depth-priming-and-certificate-cache")){
            var c=ordered.stream().filter(v->v.name().equals(name)).findFirst().orElseThrow();
            assertTrue(c.on().contains(BINDING_CERTIFICATE_CACHE));assertFalse(c.on().contains(BINDING_DIGEST_STREAMING));
            assertEquals(c.on(),enabled(c.apply(OptimizationSnapshot.noneRequested())));
        }
        var actual=EnumSet.noneOf(OptimizationId.class);ordered.forEach(c->actual.addAll(c.on()));
        var expected=EnumSet.allOf(OptimizationId.class);expected.removeAll(TuningCandidates.UNAVAILABLE);assertEquals(expected,actual);
    }
    @Test void reimaginedRetainsClosedGroupsWithoutPromotingThemOverTheCanonicalInventory(){
        var defaults=TuningCandidates.ordered();var ordered=TuningCandidates.ordered("complementary-reimagined");
        assertEquals(Set.of(QUAD_COMMAND_COMPACTION,SHADOW_ADMISSION_CACHE,AO_BUDGET),ordered.get(defaults.size()).on());
        assertEquals(Set.of(DEPTH_PRIMING,BINDING_CERTIFICATE_CACHE),ordered.get(defaults.size()+1).on());
        assertEquals(Set.of(QUAD_COMMAND_COMPACTION,SHADOW_ADMISSION_CACHE,DEPTH_PRIMING,BINDING_CERTIFICATE_CACHE),ordered.get(defaults.size()+2).on());
        assertEquals(Set.of(TEMPORAL_SHADOW_FILTER,BINDING_CERTIFICATE_CACHE),ordered.get(defaults.size()+3).on());
        assertEquals(Set.of(QUAD_COMMAND_COMPACTION,SHADOW_ADMISSION_CACHE,TEMPORAL_SHADOW_FILTER,BINDING_CERTIFICATE_CACHE),ordered.getLast().on());
    }
    @Test void unboundPreservesKnownRendererAndCloudProfilesBeforeExploratoryGroups(){
        var defaults=TuningCandidates.ordered();var ordered=TuningCandidates.ordered("complementary-unbound");
        assertEquals(defaults.getFirst(),ordered.getFirst());
        assertEquals("renderer-fill-and-primary-cloud-budget",ordered.get(1).name());
        assertEquals(Set.of(QUAD_COMMAND_COMPACTION,SHADOW_ADMISSION_CACHE,CLOUD_PRIMARY_BUDGET),ordered.get(1).on());
        assertEquals(ordered.get(1).on(),enabled(ordered.get(1).apply(OptimizationSnapshot.noneRequested())));
        assertEquals(names(defaults.subList(1,defaults.size())),names(ordered.subList(2,ordered.size()-2)));
        assertEquals(defaults.size()+3,ordered.size());
        assertTrue(new HashSet<>(names(ordered)).containsAll(names(defaults)));
        assertEquals(ordered.size(),ordered.stream().map(TuningCandidates.Candidate::name).distinct().count());
        assertEquals(ordered,TuningCandidates.ordered("complementary-unbound"));
        assertEquals(Set.of(QUAD_COMMAND_COMPACTION,SHADOW_ADMISSION_CACHE,REFLECTION_DEFERRED_METRIC),ordered.getLast().on());
    }
    @Test void blissKeepsCanonicalRendererFirstAndRetainsCompleteShaderGroups(){
        var defaults=TuningCandidates.ordered();var ordered=TuningCandidates.ordered("bliss-shader");
        assertEquals(names(defaults),names(ordered.subList(0,defaults.size())));assertEquals(defaults.size()+2,ordered.size());
        assertEquals(ordered.size(),ordered.stream().map(TuningCandidates.Candidate::name).distinct().count());
        assertEquals(Set.of(QUAD_COMMAND_COMPACTION,SHADOW_ADMISSION_CACHE,VOLUMETRIC_LIGHT_BUDGET),ordered.get(defaults.size()).on());
        assertEquals(Set.of(QUAD_COMMAND_COMPACTION,SHADOW_ADMISSION_CACHE,SHADOW_SAMPLE_BUDGET),ordered.getLast().on());
        var none=OptimizationSnapshot.noneRequested();assertEquals(ordered.getLast().on(),enabled(ordered.getLast().apply(none)));
    }
    private static List<String> names(List<TuningCandidates.Candidate> candidates){
        return candidates.stream().map(TuningCandidates.Candidate::name).toList();
    }
    private static Set<OptimizationId> enabled(OptimizationSnapshot flags){
        var result=EnumSet.noneOf(OptimizationId.class);
        for(var id:OptimizationId.values())if(flags.enabled(id))result.add(id);
        return result;
    }
    @Test void earlyAlphaCacheVectorsAreReachableWithoutStandaloneCacheWins(){
        var expected=Map.of(
            "early-alpha-and-certificate-cache",Set.of(EARLY_ALPHA,BINDING_CERTIFICATE_CACHE),
            "early-alpha-and-streaming-certificate-cache",Set.of(EARLY_ALPHA,BINDING_CERTIFICATE_CACHE,BINDING_DIGEST_STREAMING));
        for(String family:List.of("photon","solas-shader","astralex","bsl-shaders","makeup-ultra-fast-shaders",
                "sildurs-vibrant-shaders","bliss-shader","complementary-reimagined","complementary-unbound")) {
            for(var entry:expected.entrySet()) {
                var candidate=TuningCandidates.ordered(family).stream().filter(c->c.name().equals(entry.getKey())).findFirst().orElseThrow();
                assertEquals(entry.getValue(),enabled(candidate.apply(OptimizationSnapshot.noneRequested())),family);
                var unrelated=OptimizationSnapshot.noneRequested().with(ZERO_EMISSION_ARITHMETIC,true);
                assertTrue(candidate.apply(unrelated).enabled(ZERO_EMISSION_ARITHMETIC));
                assertTrue(candidate.off().isEmpty());
                for(var id:entry.getValue())assertTrue(TuningTierPolicy.allowsId(TuningGoal.PERFORMANCE,id));
                assertFalse(TuningTierPolicy.allowsId(TuningGoal.BALANCED,EARLY_ALPHA));
                assertFalse(TuningTierPolicy.allowsId(TuningGoal.QUALITY,EARLY_ALPHA));
            }
        }
    }
    @Test void exactFamilyConsumersCarryCertificatesWithoutUnrelatedRequestsOrLostExclusions(){
        var expected=Map.ofEntries(
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
        assertEquals(TuningEligibility.FAMILIES,expected.keySet());
        for(var entry:expected.entrySet()) {
            var family=entry.getKey();var ordered=TuningCandidates.ordered(family);
            for(var id:entry.getValue()) {
                var best=OptimizationSnapshot.noneRequested().with(BINDING_CERTIFICATE_CACHE,true)
                    .with(id,true).with(ZERO_EMISSION_ARITHMETIC,true);
                var removal=TuningCandidates.cleanup(family,best).stream()
                    .filter(c->c.off().contains(BINDING_CERTIFICATE_CACHE)).findFirst().orElseThrow();
                assertEquals(Set.of(BINDING_CERTIFICATE_CACHE,id),removal.off(),family+" "+id);
                assertEquals(Set.of(ZERO_EMISSION_ARITHMETIC),enabled(removal.apply(best)));
            }
            for(var original:TuningCandidates.ordered()) {
                var actual=ordered.stream().filter(c->c.name().equals(original.name())).findFirst().orElseThrow();
                boolean coupled=!Collections.disjoint(original.on(),entry.getValue());
                var on=EnumSet.noneOf(OptimizationId.class);on.addAll(original.on());
                if(coupled)on.add(BINDING_CERTIFICATE_CACHE);
                assertEquals(on,actual.on(),family+" "+original.name());
                assertEquals(original.off(),actual.off(),family+" "+original.name());
                var none=OptimizationSnapshot.noneRequested();var expectedFlags=original.apply(none);
                if(coupled)expectedFlags=expectedFlags.with(BINDING_CERTIFICATE_CACHE,true);
                assertEquals(expectedFlags,actual.apply(none),family+" "+original.name());
                var unrelated=none.with(ZERO_EMISSION_ARITHMETIC,true);
                assertTrue(actual.apply(unrelated).enabled(ZERO_EMISSION_ARITHMETIC));
                for(var goal:TuningGoal.values()) {
                    boolean before=original.on().stream().allMatch(id->TuningTierPolicy.allowsId(goal,id));
                    boolean after=actual.on().stream().allMatch(id->TuningTierPolicy.allowsId(goal,id));
                    assertEquals(before,after,family+" "+goal+" "+actual.name());
                }
            }
        }
    }
    @Test void unprovenFamilyCellsAndUnknownFamiliesDoNotAcquireBindingCache(){
        var omissions=Map.of(
            "astralex",SHADOW_RECEIVER_FOOTPRINT,
            "makeup-ultra-fast-shaders",MERGED_TERRAIN,
            "complementary-unbound",DEPTH_PRIMING,
            "bliss-shader",SPATIAL_LIGHTING_REUSE,
            "sildurs-vibrant-shaders",SHADOW_SAMPLE_BUDGET,
            "photon",REFLECTION_PROJECTED_REFINEMENT);
        omissions.forEach((family,id)->{
            var candidate=TuningCandidates.ordered(family).stream().filter(c->c.name().equals(id.key())).findFirst().orElseThrow();
            assertEquals(Set.of(id),candidate.on(),family);
        });
        assertEquals(TuningCandidates.ordered(),TuningCandidates.ordered("unknown"));
        assertEquals(TuningCandidates.ordered(),TuningCandidates.ordered(null));
    }
    @Test void familyCleanupRemovesOnlyEnabledCertificateDependentsAndRetainsLegacyBehavior(){
        var best=OptimizationSnapshot.noneRequested().with(BINDING_CERTIFICATE_CACHE,true)
            .with(EARLY_ALPHA,true).with(SHADOW_RECEIVER_FOOTPRINT,true).with(PIXEL_SCALE,true)
            .with(SHADOW_SAMPLE_BUDGET,true).with(ZERO_EMISSION_ARITHMETIC,true);
        var legacy=TuningCandidates.cleanup(best);var cleanup=TuningCandidates.cleanup("solas-shader",best);
        assertEquals(names(legacy),names(cleanup));assertTrue(cleanup.size()<=8);
        var removed=cleanup.stream().filter(c->c.off().contains(BINDING_CERTIFICATE_CACHE)).findFirst().orElseThrow();
        assertEquals(Set.of(BINDING_CERTIFICATE_CACHE,EARLY_ALPHA,SHADOW_RECEIVER_FOOTPRINT,PIXEL_SCALE),removed.off());
        assertTrue(removed.on().isEmpty());
        assertEquals(Set.of(SHADOW_SAMPLE_BUDGET,ZERO_EMISSION_ARITHMETIC),enabled(removed.apply(best)));
        var removeAlpha=cleanup.stream().filter(c->c.name().equals("remove-"+EARLY_ALPHA.key())).findFirst().orElseThrow();
        assertEquals(Set.of(EARLY_ALPHA),removeAlpha.off());
        assertTrue(removeAlpha.apply(best).enabled(SHADOW_RECEIVER_FOOTPRINT));
        assertTrue(removeAlpha.apply(best).enabled(BINDING_CERTIFICATE_CACHE));
        var oldRemoval=legacy.stream().filter(c->c.off().contains(BINDING_CERTIFICATE_CACHE)).findFirst().orElseThrow();
        assertEquals(Set.of(BINDING_CERTIFICATE_CACHE),oldRemoval.off());
        assertTrue(oldRemoval.apply(best).enabled(EARLY_ALPHA));
        assertEquals(legacy,TuningCandidates.cleanup("unknown",best));
        assertEquals(legacy,TuningCandidates.cleanup(null,best));
        assertTrue(TuningCandidates.cleanup("solas-shader",OptimizationSnapshot.noneRequested()).isEmpty());
    }
    @Test void canonicalInventoryHasNoAliasesAndEveryAvailableRequestIsScheduled(){
        assertEquals(64,OptimizationId.values().length);
        var seen=EnumSet.noneOf(OptimizationId.class);TuningCandidates.ordered().forEach(c->seen.addAll(c.on()));
        var expected=EnumSet.allOf(OptimizationId.class);expected.removeAll(TuningCandidates.UNAVAILABLE);assertEquals(expected,seen);
        assertTrue(Collections.disjoint(seen,TuningCandidates.UNAVAILABLE));
    }
    @Test void spatialAndPixelModesAreLegalEvenWhenStartingFromAllRequested(){
        for(var c:TuningCandidates.ordered())if(c.on().contains(SPATIAL_LIGHTING_REUSE)||c.on().contains(SPATIAL_LIGHTING_RATE)||c.on().contains(PIXEL_SCALE)) {
            var s=c.apply(OptimizationSnapshot.allRequested());int count=(s.enabled(SPATIAL_LIGHTING_REUSE)?1:0)+(s.enabled(SPATIAL_LIGHTING_RATE)?1:0)+(s.enabled(PIXEL_SCALE)?1:0);
            assertEquals(1,count,c.name());
        }
    }
    @Test void knownRendererGroupPrecedesLiveCandidatesWithinFiniteBudget(){
        var ordered=TuningCandidates.ordered();int n=OptimizationEditTransaction.liveUniformIds().size();
        assertEquals(Set.of(QUAD_COMMAND_COMPACTION,SHADOW_ADMISSION_CACHE),ordered.getFirst().on());
        for(int i=1;i<=n;i++){var c=ordered.get(i);assertTrue(OptimizationEditTransaction.liveUniformIds().containsAll(c.on()));}
    }
    @Test void rendererTrialsCarryAdmissionCacheAndRemovalCannotLeaveUncachedConsumers(){
        var consumers=Set.of(QUAD_COMMAND_COMPACTION,INDEX_SCALAR_CACHE,SHADOW_POSITIVE_VERTEX,
            SHADOW_VISIBILITY_LISTS,SHADOW_FRUSTUM_COHERENCE);
        for(var c:TuningCandidates.ordered()){
            var s=c.apply(OptimizationSnapshot.noneRequested());
            if(consumers.stream().anyMatch(s::enabled))assertTrue(s.enabled(SHADOW_ADMISSION_CACHE),c.name());
            if(!Collections.disjoint(c.on(),consumers)){
                var unrelated=OptimizationSnapshot.noneRequested().with(ZERO_EMISSION_ARITHMETIC,true);
                assertTrue(c.apply(unrelated).enabled(ZERO_EMISSION_ARITHMETIC),c.name());
            }
        }
        var removed=new TuningCandidates.Candidate("remove-cache",Set.of(),Set.of(SHADOW_ADMISSION_CACHE)).apply(OptimizationSnapshot.allRequested());
        for(var id:consumers)assertFalse(removed.enabled(id),id.key());
        assertTrue(removed.enabled(ZERO_EMISSION_ARITHMETIC));
    }
    @Test void cleanupIsBoundedAndNeverEnablesAnUncertainFlag(){
        var all=OptimizationSnapshot.allRequested();var cleanup=TuningCandidates.cleanup(all);assertTrue(cleanup.size()<=8);
        for(var c:cleanup){assertTrue(c.on().isEmpty());assertEquals(1,c.off().size());}
    }
    @Test void sildurSpatialCacheJoinsPreserveModesAndExplicitCleanup(){
        for(var id:List.of(SPATIAL_LIGHTING_REUSE,SPATIAL_LIGHTING_RATE)) {
            var c=TuningCandidates.ordered("sildurs-vibrant-shaders").stream().filter(x->x.name().equals(id.key())).findFirst().orElseThrow();
            assertEquals(Set.of(id,BINDING_CERTIFICATE_CACHE),c.on());
            assertTrue(c.apply(OptimizationSnapshot.noneRequested()).enabled(BINDING_CERTIFICATE_CACHE));
            assertFalse(c.apply(OptimizationSnapshot.allRequested()).enabled(id==SPATIAL_LIGHTING_REUSE?SPATIAL_LIGHTING_RATE:SPATIAL_LIGHTING_REUSE));
            var best=OptimizationSnapshot.noneRequested().with(id,true).with(BINDING_CERTIFICATE_CACHE,true).with(ZERO_EMISSION_ARITHMETIC,true);
            var remove=TuningCandidates.cleanup("sildurs-vibrant-shaders",best).stream().filter(x->x.off().contains(BINDING_CERTIFICATE_CACHE)).findFirst().orElseThrow();
            assertEquals(Set.of(id,BINDING_CERTIFICATE_CACHE),remove.off());
            assertTrue(remove.apply(best).enabled(ZERO_EMISSION_ARITHMETIC));
        }
    }

    @Test void sildurPixelSourceCacheCouplesOnlyItsExistingConsumer(){
        var c=TuningCandidates.ordered("sildurs-vibrant-shaders").stream().filter(x->x.name().equals(PIXEL_SCALE.key())).findFirst().orElseThrow();
        assertEquals(Set.of(PIXEL_SCALE,BINDING_CERTIFICATE_CACHE),c.on());
        var selected=c.apply(OptimizationSnapshot.noneRequested());assertTrue(selected.enabled(PIXEL_SCALE));assertTrue(selected.enabled(BINDING_CERTIFICATE_CACHE));
        var all=c.apply(OptimizationSnapshot.allRequested());assertFalse(all.enabled(SPATIAL_LIGHTING_REUSE));assertFalse(all.enabled(SPATIAL_LIGHTING_RATE));
        var best=selected.with(ZERO_EMISSION_ARITHMETIC,true);
        var remove=TuningCandidates.cleanup("sildurs-vibrant-shaders",best).stream().filter(x->x.off().contains(BINDING_CERTIFICATE_CACHE)).findFirst().orElseThrow();
        assertEquals(Set.of(PIXEL_SCALE,BINDING_CERTIFICATE_CACHE),remove.off());assertTrue(remove.apply(best).enabled(ZERO_EMISSION_ARITHMETIC));
    }

}

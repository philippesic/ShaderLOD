package com.pp.shaderlod.tuning;

import com.google.gson.JsonParser;
import com.pp.shaderlod.optimization.OptimizationId;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Normal packaged declarations and authentic native option maps, without a game substitute. */
class TuningEligibilityDeclarationsTest {
    private Map<String,String> nativeOptions(String resource) throws Exception {
        try(var in=getClass().getResourceAsStream("/assets/shaderlod/"+resource)) {
            assertNotNull(in,resource);
            var map=new TreeMap<String,String>();
            JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject()
                .entrySet().forEach(e->map.put(e.getKey(),e.getValue().getAsString()));
            return map;
        }
    }
    @Test void everyCanonicalFamilyCellHasAnExplicitPackagedDisposition() {
        assertEquals(9,TuningEligibility.FAMILIES.size());assertEquals(64,OptimizationId.values().length);
        for(String family:TuningEligibility.FAMILIES)for(var id:OptimizationId.values()) {
            var result=TuningEligibility.classify(family,id);
            assertNotEquals(TuningEligibility.State.UNKNOWN,result.state(),family+"/"+id+": "+result.reason());
            assertFalse(result.reason().isBlank());
        }
        assertEquals(TuningEligibility.State.UNKNOWN,TuningEligibility.classify("unknown-pack",OptimizationId.AO_BUDGET).state());
    }
    @Test void actualBslNativeMaterialOptionsPreventTimingInactivePomAndAurora() throws Exception {
        var nativeMap=nativeOptions("bslquad/native384.json");
        assertEquals("false",nativeMap.get("ADVANCED_MATERIALS"));assertEquals("0",nativeMap.get("AURORA"));
        for(var id:List.of(OptimizationId.PROJECTED_POM,OptimizationId.POM_HEIGHT_CACHE,OptimizationId.POM_EMPTY_HEIGHT_PREFIX)) {
            assertEquals(TuningEligibility.State.INACTIVE_NATIVE_OPTION,TuningEligibility.classify("bsl-shaders",id,nativeMap).state());
            var operative=new TreeMap<>(nativeMap);operative.put("ADVANCED_MATERIALS","true");operative.put("PARALLAX","true");
            assertEquals(TuningEligibility.State.DECLARED_CONSUMER,TuningEligibility.classify("bsl-shaders",id,operative).state());
        }
        assertEquals(TuningEligibility.State.INACTIVE_NATIVE_OPTION,TuningEligibility.classify("bsl-shaders",OptimizationId.AURORA_BUDGET,nativeMap).state());
    }
    @Test void sildurOptionalPomAndVolumetricsDoNotConfuseOtherNativeEffects() throws Exception {
        var nativeMap=nativeOptions("sildurquad/native115.json");
        assertEquals("0",nativeMap.get("nMap"));assertEquals("true",nativeMap.get("Godrays"));assertEquals("false",nativeMap.get("Volumetric_Lighting"));
        assertEquals(TuningEligibility.State.INACTIVE_NATIVE_OPTION,TuningEligibility.classify("sildurs-vibrant-shaders",OptimizationId.VOLUMETRIC_LIGHT_BUDGET,nativeMap).state());
        for(var id:List.of(OptimizationId.PROJECTED_POM,OptimizationId.POM_HEIGHT_CACHE,OptimizationId.POM_EMPTY_HEIGHT_PREFIX)) {
            assertEquals(TuningEligibility.State.INACTIVE_NATIVE_OPTION,TuningEligibility.classify("sildurs-vibrant-shaders",id,nativeMap).state());
            var operative=new TreeMap<>(nativeMap);operative.put("nMap","2");
            assertEquals(TuningEligibility.State.DECLARED_CONSUMER,TuningEligibility.classify("sildurs-vibrant-shaders",id,operative).state());
        }
    }
    @Test void missingOptionValuesDoNotInventInactiveWorkAndAbsentKernelsStayUnsupported() {
        assertEquals(TuningEligibility.State.DECLARED_CONSUMER,TuningEligibility.classify("bsl-shaders",OptimizationId.AO_BUDGET,Map.of()).state());
        assertEquals(TuningEligibility.State.INACTIVE_NATIVE_OPTION,TuningEligibility.classify("bsl-shaders",OptimizationId.AO_BUDGET,Map.of("AO","false")).state());
        assertEquals(TuningEligibility.State.UNSUPPORTED,TuningEligibility.classify("makeup-ultra-fast-shaders",OptimizationId.PROJECTED_POM,Map.of("POM","true")).state());
        for(String family:TuningEligibility.FAMILIES) {
            assertEquals(TuningEligibility.State.UNQUALIFIED_PROTOTYPE,TuningEligibility.classify(family,OptimizationId.SHADOW_RAW_QUERY).state());
            assertEquals(TuningEligibility.State.DUPLICATE_LABEL,TuningEligibility.classify(family,OptimizationId.SHADOW_PREFIX).state());
        }
    }
    @Test void complementaryOptionalCloudAndMaterialDomainsUseActualNativeChoices() throws Exception {
        var original=nativeOptions("unboundquad/native398.json");
        assertEquals("4",original.get("SHADER_STYLE"));assertEquals("-1",original.get("CLOUD_STYLE_DEFINE"));assertEquals("1",original.get("RP_MODE"));
        for(var id:List.of(OptimizationId.CLOUD_SECONDARY_DETAIL,OptimizationId.CLOUD_SECONDARY_BUDGET)) {
            assertEquals(TuningEligibility.State.INACTIVE_NATIVE_OPTION,TuningEligibility.classify("complementary-unbound",id,original).state());
            var optional=new TreeMap<>(original);optional.put("CLOUD_STYLE_DEFINE","1");
            assertEquals(TuningEligibility.State.DECLARED_CONSUMER,TuningEligibility.classify("complementary-unbound",id,optional).state());
        }
        for(var id:List.of(OptimizationId.PROJECTED_POM,OptimizationId.POM_HEIGHT_CACHE,OptimizationId.POM_EMPTY_HEIGHT_PREFIX)) {
            assertEquals(TuningEligibility.State.INACTIVE_NATIVE_OPTION,TuningEligibility.classify("complementary-unbound",id,original).state());
            var optional=new TreeMap<>(original);optional.put("RP_MODE","2");
            assertEquals(TuningEligibility.State.DECLARED_CONSUMER,TuningEligibility.classify("complementary-unbound",id,optional).state());
        }
    }
    @Test void blissDisabledMaterialEffectsStayDistinctFromItsActiveCloudConsumer() throws Exception {
        var original=nativeOptions("blissquad/native463.json");
        assertEquals("false",original.get("POM"));assertEquals("false",original.get("Specular_Reflections"));
        assertEquals(TuningEligibility.State.INACTIVE_NATIVE_OPTION,TuningEligibility.classify("bliss-shader",OptimizationId.POM_EMPTY_HEIGHT_PREFIX,original).state());
        assertEquals(TuningEligibility.State.INACTIVE_NATIVE_OPTION,TuningEligibility.classify("bliss-shader",OptimizationId.REFLECTION_PROJECTED_REFINEMENT,original).state());
        assertEquals(TuningEligibility.State.DECLARED_CONSUMER,TuningEligibility.classify("bliss-shader",OptimizationId.CLOUD_SECONDARY_EXTENT_CACHE,original).state());
    }
    @Test void actualAstraNativeTaaShadowRouteKeepsIndependentBudgetRequestsSeparate() throws Exception {
        var original=nativeOptions("astraquad/native1015.json");
        assertEquals(1015,original.size());assertEquals("3",original.get("AA"));
        assertFalse(original.containsKey("TAA"));
        assertEquals("true",original.get("SHADOW_FILTER"));assertEquals("true",original.get("TAA_SHADOW_FILTER"));
        try(var in=getClass().getResourceAsStream("/assets/shaderlod/astraquad/native1015.json")) {
            assertNotNull(in);
            assertEquals("298347d107f05379e2ab255059f768cc5542ab215ccfaad5a9c121c8778fba71",
                HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));
        }
        var budget=TuningEligibility.classify("astralex",OptimizationId.SHADOW_SAMPLE_BUDGET,original);
        assertEquals(TuningEligibility.State.INACTIVE_NATIVE_OPTION,budget.state());
        assertTrue(budget.reason().contains("temporal_shadow_filter"));
        assertEquals(TuningEligibility.State.DECLARED_CONSUMER,
            TuningEligibility.classify("astralex",OptimizationId.TEMPORAL_SHADOW_FILTER,original).state());
    }
    @Test void legalAstraNonTaaShadowChoiceRetainsDeclaredSampleBudgetWithoutChangingAa() throws Exception {
        var original=nativeOptions("astraquad/native1015.json");
        var nonTaaShadow=new TreeMap<>(original);nonTaaShadow.put("TAA_SHADOW_FILTER","false");
        assertEquals("3",nonTaaShadow.get("AA"));assertEquals(original.size(),nonTaaShadow.size());
        assertEquals(TuningEligibility.State.DECLARED_CONSUMER,
            TuningEligibility.classify("astralex",OptimizationId.SHADOW_SAMPLE_BUDGET,nonTaaShadow).state());
        assertEquals("true",original.get("TAA_SHADOW_FILTER"));
    }
    @Test void uncertainAstraShadowOptionsDoNotInventInactiveWorkOrChangeOtherFamilies() throws Exception {
        var original=nativeOptions("astraquad/native1015.json");
        for(String key:List.of("SHADOW_FILTER","TAA_SHADOW_FILTER")) {
            var missing=new TreeMap<>(original);missing.remove(key);
            assertEquals(TuningEligibility.State.DECLARED_CONSUMER,
                TuningEligibility.classify("astralex",OptimizationId.SHADOW_SAMPLE_BUDGET,missing).state());
            var invalid=new TreeMap<>(original);invalid.put(key,"1");
            assertEquals(TuningEligibility.State.DECLARED_CONSUMER,
                TuningEligibility.classify("astralex",OptimizationId.SHADOW_SAMPLE_BUDGET,invalid).state());
        }
        var bsl=nativeOptions("bslquad/native384.json");
        assertEquals(TuningEligibility.State.UNSUPPORTED,
            TuningEligibility.classify("bsl-shaders",OptimizationId.SHADOW_SAMPLE_BUDGET,bsl).state());
    }
    @Test void automaticSearchCannotPretendToSupplyHumanInspectedDhCoverage() {
        for(String family:TuningEligibility.FAMILIES) {
            var result=TuningEligibility.classify(family,OptimizationId.TERRAIN_RADIUS);
            assertEquals(TuningEligibility.State.UNSUPPORTED,result.state());
            assertTrue(result.reason().startsWith("MANUAL_CONFIRMATION_REQUIRED"));
        }
    }
    @Test void photonNativeDefaultsCannotTimeAnUnqualifiedEarlyAlphaConsumer() throws Exception {
        var original=nativeOptions("photonquad/native366.json");
        assertEquals("false",original.get("POM"));
        assertEquals("false",original.get("NORMAL_MAPPING"));
        assertEquals("false",original.get("SPECULAR_MAPPING"));
        var entry=TuningEligibility.classify("photon",OptimizationId.EARLY_ALPHA,original);
        assertEquals(TuningEligibility.State.INACTIVE_NATIVE_OPTION,entry.state());
        var candidate=com.pp.shaderlod.optimization.OptimizationSnapshot.noneRequested()
            .with(OptimizationId.EARLY_ALPHA,true).with(OptimizationId.BINDING_CERTIFICATE_CACHE,true);
        var reasons=TuningTierPolicy.rejectedVector(TuningGoal.PERFORMANCE,"photon",candidate,original);
        assertTrue(reasons.stream().anyMatch(reason->reason.startsWith("early_alpha: INACTIVE_NATIVE_OPTION:")));
    }
    @Test void photonExactOptionalEarlyAlphaProfileStillReachesOrdinaryAdmission() throws Exception {
        var optional=nativeOptions("photonearlyalpha/optional394.json");
        assertTrue(com.pp.shaderlod.optimization.photonearlyalpha.PhotonEarlyAlphaProfile.accepts(optional));
        assertEquals(TuningEligibility.State.DECLARED_CONSUMER,
            TuningEligibility.classify("photon",OptimizationId.EARLY_ALPHA,optional).state());
        assertNull(TuningTierPolicy.rejection(TuningGoal.PERFORMANCE,"photon",OptimizationId.EARLY_ALPHA,optional));
    }
    @Test void photonEarlyAlphaKnownDisabledPrerequisitesStayDistinctFromMissingValues() {
        for(String key:List.of("POM","NORMAL_MAPPING","SPECULAR_MAPPING")) {
            assertEquals(TuningEligibility.State.INACTIVE_NATIVE_OPTION,
                TuningEligibility.classify("photon",OptimizationId.EARLY_ALPHA,Map.of(key,"false")).state());
            for(String uncertain:List.of("true","0","unavailable"))
                assertEquals(TuningEligibility.State.DECLARED_CONSUMER,
                    TuningEligibility.classify("photon",OptimizationId.EARLY_ALPHA,Map.of(key,uncertain)).state());
        }
        assertEquals(TuningEligibility.State.DECLARED_CONSUMER,
            TuningEligibility.classify("photon",OptimizationId.EARLY_ALPHA,Map.of()).state());
        assertEquals(TuningEligibility.State.DECLARED_CONSUMER,
            TuningEligibility.classify("photon",OptimizationId.BINDING_CERTIFICATE_CACHE,Map.of("POM","false")).state());
    }
}

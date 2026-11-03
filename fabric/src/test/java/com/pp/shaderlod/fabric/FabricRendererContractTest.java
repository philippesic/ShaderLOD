package com.pp.shaderlod.fabric;

import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadApiPins;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FabricRendererContractTest {
    @Test void completePinnedFabricStackRejectsChangedMissingExtraAndForeignLoaderInputs() {
        var actual = new TreeMap<>(MakeUpQuadRuntime.REQUIRED_STACK);
        actual.put("shaderlod", "product-version");
        assertTrue(MakeUpQuadRuntime.stackMatches(actual));
        actual.put("shaderlod_dev", "0.1.0-dev");
        assertTrue(MakeUpQuadRuntime.stackMatches(actual));
        actual.put("shaderlod_dev", "foreign");
        assertFalse(MakeUpQuadRuntime.stackMatches(actual));
        actual.remove("shaderlod_dev");
        for (String key : MakeUpQuadRuntime.REQUIRED_STACK.keySet()) {
            var missing = new TreeMap<>(actual); missing.remove(key);
            assertFalse(MakeUpQuadRuntime.stackMatches(missing), key);
            var changed = new TreeMap<>(actual); changed.put(key, "foreign");
            assertFalse(MakeUpQuadRuntime.stackMatches(changed), key);
        }
        actual.put("distanthorizons", "2.4.5");
        assertFalse(MakeUpQuadRuntime.stackMatches(actual));
        actual.remove("distanthorizons"); actual.put("neoforge", "21.1.253");
        assertFalse(MakeUpQuadRuntime.stackMatches(actual));
    }
    @Test void completeNativeResourceCompositionRejectsUserPacksAndMissingChildPacks() {
        var nativeIds = MakeUpQuadRuntime.REQUIRED_RESOURCES;
        assertTrue(MakeUpQuadRuntime.resourcesMatch(nativeIds));
        var missing = new HashSet<>(nativeIds); missing.remove("fabric");
        assertFalse(MakeUpQuadRuntime.resourcesMatch(missing));
        var extra = new HashSet<>(nativeIds); extra.add("file/PBR.zip");
        assertFalse(MakeUpQuadRuntime.resourcesMatch(extra));
        assertFalse(MakeUpQuadRuntime.resourcesMatch(List.of("vanilla", "mod_resources")));
    }
    @Test void authenticSavedFabricR2ResourceProducerSetAdmitsAndAnyChangeRefuses() throws Exception {
        try (var input = getClass().getResourceAsStream("/fixtures/fabric1211-r2-native-resources.json")) {
            assertNotNull(input);
            var fixture = com.google.gson.JsonParser.parseString(new String(input.readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var selected = new ArrayList<String>();
            fixture.getAsJsonArray("selectedResourcePacks").forEach(value -> selected.add(value.getAsString()));
            assertEquals(57, selected.size());
            assertTrue(selected.contains("fabric-gametest-api-v1"));
            assertTrue(MakeUpQuadRuntime.resourcesMatch(selected));
            for (String id : selected) {
                var missing = new ArrayList<>(selected); missing.remove(id);
                assertFalse(MakeUpQuadRuntime.resourcesMatch(missing), id);
            }
            for (String extra : List.of("file/PBR.zip", "distanthorizons", "shaderlod_dev")) {
                var changed = new ArrayList<>(selected); changed.add(extra);
                assertFalse(MakeUpQuadRuntime.resourcesMatch(changed), extra);
            }
            var duplicate = new ArrayList<>(selected); duplicate.add("fabric");
            assertFalse(MakeUpQuadRuntime.resourcesMatch(duplicate));
        }
    }
    @Test void onlyLoadedEnabledPerformanceAndNativeCapabilitiesAdmit() {
        for (var preset : LodPreset.values()) {
            assertEquals(preset == LodPreset.PERFORMANCE, MakeUpQuadRuntime.configAllows(true, true, preset));
            assertFalse(MakeUpQuadRuntime.configAllows(false, true, preset));
            assertFalse(MakeUpQuadRuntime.configAllows(true, false, preset));
        }
        assertTrue(MakeUpQuadRuntime.capabilitiesAllow(8, true, 1));
        assertFalse(MakeUpQuadRuntime.capabilitiesAllow(4, true, 1));
        assertFalse(MakeUpQuadRuntime.capabilitiesAllow(8, false, 1));
        assertFalse(MakeUpQuadRuntime.capabilitiesAllow(8, true, 0));
    }
    @Test void inspectedNamedClasspathMatchesWholeApiProfileAndAbsentApisRefuse() {
        assertTrue(MakeUpQuadApiPins.resourcesMatch(getClass().getClassLoader()));
        assertFalse(MakeUpQuadApiPins.resourcesMatch(new ClassLoader(null) {}));
    }
    @Test void fabricRuntimeDoesNotAdmitNeoForgeOnlyPrivateRasterExperiments() {
        var actual = new com.pp.shaderlod.integration.RasterExperimentSelection.RuntimePins(
                true, "fabric", "1.21.1", "0.19.5", "fabric-iris-hash", "fabric-sodium-hash");
        assertFalse(actual.exact());
    }
}

package com.pp.shaderlod.port;
import com.google.gson.JsonParser;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
/** Exact selected-repository fixture from the real262 untimed admission observation. */
class RendererResourceContractTest {
    static List<String> observedIds() {
        try(var input=RendererResourceContractTest.class.getResourceAsStream("/renderer-resource-observation-r1.json")) {
            assertNotNull(input,"actual resource fixture");
            var fixture=JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("dev/logs/speed-port-r1/262-admission-observation-r1/runtime-report.json",fixture.get("observationSource").getAsString());
            assertEquals("0fda6e862466deacb4b1da4c05586b3564f90edec6b32080cb0b87a76ff8ad97",fixture.get("observationSha256").getAsString());
            assertEquals("rendererAdmissionAtStartup.selectedRepositoryResourceIds",fixture.get("field").getAsString());
            var result=new ArrayList<String>();fixture.getAsJsonArray("selectedRepositoryResourceIds").forEach(value->result.add(value.getAsString()));
            assertEquals(52,fixture.get("selectedResourceCount").getAsInt());assertEquals(52,result.size());assertEquals(52,new HashSet<>(result).size());
            return List.copyOf(result);
        } catch(Exception failure) { throw new AssertionError("Could not read authentic selected-resource fixture",failure); }
    }
    @Test void actualObservedSetPassesInAnyOrder() {
        var observed=observedIds();assertTrue(MakeUpQuadRuntime.resourcesMatch(observed));
        var reordered=new ArrayList<>(observed);Collections.reverse(reordered);assertTrue(MakeUpQuadRuntime.resourcesMatch(reordered));
    }
    @ParameterizedTest @ValueSource(strings={"shaderlod","vanilla","fabric-api-base"})
    void missingRequiredMainVanillaOrApiResourceRefuses(String missing) {
        var changed=new ArrayList<>(observedIds());assertTrue(changed.remove(missing));assertFalse(MakeUpQuadRuntime.resourcesMatch(changed));
    }
    @ParameterizedTest @ValueSource(strings={"unknown","file/custom-pbr","distanthorizons","shaderlod_dev"})
    void extraUnknownPbrDhOrUnobservedDevResourceRefuses(String extra) {
        var changed=new ArrayList<>(observedIds());changed.add(extra);assertFalse(MakeUpQuadRuntime.resourcesMatch(changed));
    }
    @Test void duplicateFailsBothAddedAndSameCardinalityCases() {
        var added=new ArrayList<>(observedIds());added.add("vanilla");assertFalse(MakeUpQuadRuntime.resourcesMatch(added));
        var sameCount=new ArrayList<>(observedIds());sameCount.set(sameCount.indexOf("shaderlod"),"vanilla");
        assertEquals(52,sameCount.size());assertFalse(MakeUpQuadRuntime.resourcesMatch(sameCount));
    }
    @ParameterizedTest @ValueSource(strings={"file/custom-pbr","distanthorizons","shaderlod_dev","fabric-api-WRONG"})
    void wrongResourceIdAtSameCardinalityRefuses(String replacement) {
        var changed=new ArrayList<>(observedIds());changed.set(changed.indexOf("shaderlod"),replacement);
        assertEquals(52,changed.size());assertFalse(MakeUpQuadRuntime.resourcesMatch(changed));
    }
    @Test void absentAndBorrowedNeoForgeResourceInputsRefuse() {
        assertFalse(MakeUpQuadRuntime.resourcesMatch(null));assertFalse(MakeUpQuadRuntime.resourcesMatch(List.of()));
        assertFalse(MakeUpQuadRuntime.resourcesMatch(List.of("vanilla","mod_resources")));
    }
}

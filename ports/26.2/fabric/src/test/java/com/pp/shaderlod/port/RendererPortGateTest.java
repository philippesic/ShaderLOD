package com.pp.shaderlod.port;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.makeupquad.*;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RendererPortGateTest {
    @Test void offQualityMissingConfigAndCapabilitiesStayNative() {
        assertFalse(MakeUpQuadRuntime.configAllows(false,true,LodPreset.PERFORMANCE));
        assertFalse(MakeUpQuadRuntime.configAllows(true,false,LodPreset.PERFORMANCE));
        assertFalse(MakeUpQuadRuntime.configAllows(true,true,LodPreset.QUALITY));
        assertFalse(MakeUpQuadRuntime.configAllows(true,true,LodPreset.BALANCED));
        assertTrue(MakeUpQuadRuntime.configAllows(true,true,LodPreset.PERFORMANCE));
        assertFalse(MakeUpQuadRuntime.capabilitiesAllow(4,true,1));
        assertFalse(MakeUpQuadRuntime.capabilitiesAllow(8,false,1));
        assertFalse(MakeUpQuadRuntime.capabilitiesAllow(8,true,0));
    }
    @Test void launcherStackPinRejectsExtraModsDhAndDifferentPatch() {
        var stack=new TreeMap<>(MakeUpQuadRuntime.REQUIRED_STACK);stack.put("shaderlod","candidate");
        assertTrue(MakeUpQuadRuntime.stackMatches(stack));
        stack.put("distanthorizons","unknown");assertFalse(MakeUpQuadRuntime.stackMatches(stack));stack.remove("distanthorizons");
        stack.put("iris","different");assertFalse(MakeUpQuadRuntime.stackMatches(stack));
    }
    @Test void missingApiAndPbrResourcesStayNative() {
        assertFalse(MakeUpQuadApiPins.resourcesMatch(new ClassLoader(null) {}));
        assertFalse(MakeUpQuadRuntime.resourcesMatch(java.util.List.of("vanilla","custom-pbr")));
        assertFalse(MakeUpQuadRuntime.resourcesMatch(java.util.List.of("vanilla","mod_resources")));
        assertTrue(MakeUpQuadRuntime.resourcesMatch(RendererResourceContractTest.observedIds()));
    }
    @Test void defaultCandidateDoesNotLoadGraphicsAndTentativeApplyIsSuspended() {
        var old=System.getProperty(MakeUpQuadRuntime.RESEARCH_OPT_IN);
        try {
            System.clearProperty(MakeUpQuadRuntime.RESEARCH_OPT_IN);assertFalse(MakeUpQuadRuntime.enabled());
            System.setProperty(MakeUpQuadRuntime.RESEARCH_OPT_IN,"true");MakeUpQuadLifecycle.beginSettingsApply();
            try { assertFalse(MakeUpQuadRuntime.enabled()); } finally { MakeUpQuadLifecycle.endSettingsApply(); }
        } finally { if(old==null)System.clearProperty(MakeUpQuadRuntime.RESEARCH_OPT_IN);else System.setProperty(MakeUpQuadRuntime.RESEARCH_OPT_IN,old); }
    }
}

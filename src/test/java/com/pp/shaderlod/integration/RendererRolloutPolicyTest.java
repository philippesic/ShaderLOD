package com.pp.shaderlod.integration;

import com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy;
import com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.Route;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RendererRolloutPolicyTest {
    @Test void ordinaryManualPreviewPermitsEveryExistingCertifiedRoute() {
        for (Route route : Route.values()) assertTrue(RendererRolloutPolicy.allows(route, false), route.name());
    }
    @Test void diagnosticResearchPermissionRemainsCompatible() {
        for (Route route : Route.values()) assertTrue(RendererRolloutPolicy.allows(route, true), route.name());
    }
    @Test void unknownRouteFailsClosedForProductAndResearch() {
        assertFalse(RendererRolloutPolicy.allows(null, false));
        assertFalse(RendererRolloutPolicy.allows(null, true));
    }
    @Test void exactlyNineNamedRoutesAreRecognizedWithoutAdmittingUnknownNames() {
        assertEquals(java.util.Set.of("MAKEUP","SILDUR","UNBOUND","REIMAGINED","BSL","PHOTON","BLISS","ASTRA","SOLAS"),
            java.util.Arrays.stream(Route.values()).map(Enum::name).collect(java.util.stream.Collectors.toSet()));
        assertThrows(IllegalArgumentException.class, () -> Route.valueOf("UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> Route.valueOf("ASTRALEX")); // product enum is ASTRA, not a second route
    }
}

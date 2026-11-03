package com.pp.shaderlod.integration;

import com.pp.shaderlod.integration.makeupquad.*;
import com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility;
import com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility;
import com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility;
import com.pp.shaderlod.integration.bslquad.BslQuadEligibility;
import com.pp.shaderlod.integration.photonquad.PhotonQuadEligibility;
import com.pp.shaderlod.integration.blissquad.BlissQuadEligibility;
import com.pp.shaderlod.integration.solasquad.SolasQuadEligibility;
import com.pp.shaderlod.integration.astraquad.AstraQuadEligibility;
import com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.Route;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RendererRolloutCertificateTest {
    private Path archive(Route route) { return Path.of(switch(route) {
        case MAKEUP -> "dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip";
        case SILDUR -> "dependencies/upstream/pack-expansion/Sildur's Vibrant Shaders v2.02 Extreme.zip";
        case UNBOUND -> "dependencies/upstream/other-pack-research/ComplementaryUnbound_r5.9.3.zip";
        case REIMAGINED -> "dependencies/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip";
        case BSL -> "dependencies/upstream/other-pack-research/BSL_v10.1.8.zip";
        case BLISS -> "dependencies/upstream/pack-expansion/Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip";
        case PHOTON -> "dependencies/upstream/photon_v1.3b.zip";
        case SOLAS -> "dependencies/upstream/pack-expansion/Solas Shader V3.7b.zip";
        case ASTRA -> "dependencies/upstream/pack-expansion/§r§lAstra§4§lLex§r§l_By_LexBoosT_§4§lV93.0§r§l.zip";
    }); }
    private Map<String,String> options(Route route) { return switch(route) {
        case MAKEUP -> MakeUpQuadEligibility.nativeOptions();
        case SILDUR -> SildurQuadEligibility.nativeOptions();
        case UNBOUND -> UnboundQuadEligibility.nativeOptions();
        case REIMAGINED -> ReimaginedQuadEligibility.nativeOptions();
        case BSL -> BslQuadEligibility.nativeOptions();
        case BLISS -> BlissQuadEligibility.nativeOptions();
        case PHOTON -> PhotonQuadEligibility.nativeOptions();
        case SOLAS -> SolasQuadEligibility.nativeOptions();
        case ASTRA -> AstraQuadEligibility.nativeOptions();
    }; }
    private boolean certificate(Route route, Path archive, Map<String,String> options) { return switch(route) {
        case MAKEUP -> MakeUpQuadEligibility.validate(archive,options).eligible();
        case SILDUR -> SildurQuadEligibility.validate(archive,options).eligible();
        case UNBOUND -> UnboundQuadEligibility.validate(archive,options).eligible();
        case REIMAGINED -> ReimaginedQuadEligibility.validate(archive,options).eligible();
        case BSL -> BslQuadEligibility.validate(archive,options).eligible();
        case BLISS -> BlissQuadEligibility.validate(archive,options).eligible();
        case PHOTON -> PhotonQuadEligibility.validate(archive,options).eligible();
        case SOLAS -> SolasQuadEligibility.validate(archive,options).eligible();
        case ASTRA -> AstraQuadEligibility.validate(archive,options).eligible();
    }; }
    private boolean admitted(Route route, Path archive, Map<String,String> options, boolean research) {
        return RendererRolloutPolicy.allows(route,research) && certificate(route,archive,options);
    }
    @Test void ordinaryManualPreviewAndResearchRequireActualNamedCertificates() {
        for (Route route : Route.values()) {
            assertTrue(certificate(route,archive(route),options(route)), "fixture certificate " + route);
            assertTrue(admitted(route,archive(route),options(route),false), route.name());
            assertTrue(admitted(route,archive(route),options(route),true), route.name());
        }
    }
    @Test void photonNativeCertificateDoesNotRequireHistoricalRoutingOptIn() {
        assertTrue(admitted(Route.PHOTON,archive(Route.PHOTON),options(Route.PHOTON),false));
        assertFalse(admitted(Route.PHOTON,archive(Route.MAKEUP),options(Route.PHOTON),false));
        assertFalse(admitted(Route.PHOTON,archive(Route.PHOTON),options(Route.MAKEUP),false));
    }
    @Test void unknownRouteCannotBypassNamedCertification() {
        assertFalse(admitted(null,archive(Route.MAKEUP),options(Route.MAKEUP),false));
        assertFalse(admitted(null,archive(Route.MAKEUP),options(Route.MAKEUP),true));
    }
    @Test void productAndResearchCannotAdmitMissingSourcesOrChangedNativeOptions() {
        for (Route route : Route.values()) for (boolean research : new boolean[]{false,true}) {
            assertFalse(admitted(route,null,options(route),research), route.name());
            var wrongSource = route == Route.MAKEUP ? Route.BSL : Route.MAKEUP;
            assertFalse(admitted(route,archive(wrongSource),options(route),research), route.name());
            assertFalse(admitted(route,archive(route),Map.of(),research), route.name());
            var mismatch = new TreeMap<>(options(route));
            String key = mismatch.firstKey(); mismatch.put(key,"changed");
            assertFalse(admitted(route,archive(route),mismatch,research), route.name());
            assertFalse(admitted(route,archive(route),null,research), route.name());
        }
    }
}

package com.pp.shaderlod.fabric.dev;

import com.pp.shaderlod.integration.bslquad.BslQuadEligibility;
import java.nio.file.Path;
import java.util.*;

/** Parent-scheduled actual pinned-source/certificate checks after ordinary dev compilation. */
public final class FabricOriginalSourceChecksMain {
    private static void check(boolean value) { if (!value) throw new AssertionError("Original source admission mismatch"); }
    public static void main(String[] args) {
        var expected = BslQuadEligibility.nativeOptions(); check(expected.size() == 384);
        Path archive = Path.of("dependencies/upstream/other-pack-research/BSL_v10.1.8.zip");
        check(FabricOriginalSourceChecks.flagsAllow("bsl", false, false, false, false, false, false));
        for (String family : List.of("", "photon", "unknown"))
            check(!FabricOriginalSourceChecks.flagsAllow(family, false, false, false, false, false, false));
        for (int flag = 0; flag < 6; flag++)
            check(!FabricOriginalSourceChecks.flagsAllow("bsl", flag == 0, flag == 1, flag == 2, flag == 3, flag == 4, flag == 5));
        check(FabricOriginalSourceChecks.sourceMatches("bsl", archive, expected));
        check(!FabricOriginalSourceChecks.sourceMatches("unknown", archive, expected));
        check(!FabricOriginalSourceChecks.sourceMatches("bsl", Path.of("dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip"), expected));
        check(!FabricOriginalSourceChecks.sourceMatches("bsl", Path.of("missing.zip"), expected));
        check(!FabricOriginalSourceChecks.optionsMatch("bsl", null));
        check(!FabricOriginalSourceChecks.optionsMatch("bsl", Map.of()));
        for (String key : expected.keySet()) {
            var wrong = new TreeMap<>(expected); wrong.remove(key); check(!FabricOriginalSourceChecks.optionsMatch("bsl", wrong));
            wrong.put(key, "foreign"); check(!FabricOriginalSourceChecks.optionsMatch("bsl", wrong));
        }
        var extra = new TreeMap<>(expected); extra.put("SHADERLOD_PRIVATE_SOURCE", "1");
        check(!FabricOriginalSourceChecks.optionsMatch("bsl", extra));
        check(FabricOriginalSourceChecks.rendererFlagsAllow("bsl", true, false, false, false, false, true, true));
        check(FabricOriginalSourceChecks.rendererFlagsAllow("bsl", false, true, false, false, false, true, true));
        for (String family : List.of("", "unknown", "photon"))
            check(!FabricOriginalSourceChecks.rendererFlagsAllow(family, true, false, false, false, false, true, true));
        check(!FabricOriginalSourceChecks.rendererFlagsAllow("bsl", false, false, false, false, false, true, true));
        check(!FabricOriginalSourceChecks.rendererFlagsAllow("bsl", true, true, false, false, false, true, true));
        check(!FabricOriginalSourceChecks.rendererFlagsAllow("bsl", true, false, true, false, false, true, true));
        check(!FabricOriginalSourceChecks.rendererFlagsAllow("bsl", true, false, false, true, false, true, true));
        check(!FabricOriginalSourceChecks.rendererFlagsAllow("bsl", true, false, false, false, true, true, true));
        check(!FabricOriginalSourceChecks.rendererFlagsAllow("bsl", true, false, false, false, false, false, true));
        check(!FabricOriginalSourceChecks.rendererFlagsAllow("bsl", true, false, false, false, false, true, false));
        System.out.println("Original source flag/archive/384-map negatives passed; no driver/source export/render claim");
    }
}

package com.pp.shaderlod.optimization.g07;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime;

/** Private first-candidate admission. Does not register a shipping optimization ID. */
public final class G07SamplingAdmission {
    public static final String OPTION = "SHADERLOD_G07_AO_TEMPORAL";
    public static final String ARCHIVE_SHA = "2c1dbcc5fd77fd65feda23b88232f4162b14ae1d28c26b9805f72e7be5f47aa0";
    private static final Map<String,String> OPTIONAL = Map.of(
        "modernfix", "5.27.24+mc1.21.1", "ferritecore", "7.0.3",
        "immediatelyfast", "1.6.14+1.21.1", "entityculling", "1.11.2",
        // Exact jar-in-jar ModInfo from the hash-pinned EntityCulling1.11.2 input.
        "transition", "1.0.25", "trender", "1.0.17");
    private G07SamplingAdmission() {}

    public static boolean options(Map<String,String> actual) {
        if (actual == null || !"2".equals(actual.get(OPTION))) return false;
        var expected = MakeUpQuadEligibility.nativeOptions();
        if (expected.size() != 100 || actual.size() != 101) return false;
        for (var entry : expected.entrySet())
            if (!entry.getValue().equals(actual.get(entry.getKey()))) return false;
        return true;
    }

    public static boolean resources(Collection<String> ids) {
        return ids != null && ids.size() == 2 && ids.contains("vanilla") && ids.contains("mod_resources");
    }

    public static boolean stack(Map<String,String> actual) {
        if (actual == null) return false;
        // These nested libraries are admitted only as the complete pinned parent cohort.
        boolean entityCulling = actual.containsKey("entityculling");
        if (entityCulling != actual.containsKey("transition") || entityCulling != actual.containsKey("trender")) return false;
        var base = new TreeMap<>(actual);
        for (var entry : OPTIONAL.entrySet()) {
            var value = base.remove(entry.getKey());
            if (value != null && !value.equals(entry.getValue())) return false;
        }
        return MakeUpQuadRuntime.stackMatches(base, false);
    }
}

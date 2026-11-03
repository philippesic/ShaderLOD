package com.pp.shaderlod.integration;

import com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

/** Root NeoForge resource-repository adapter; other loader adapters retain their own predicates. */
final class SelectedResourceScanTest {
    private record Pack(String id, boolean hidden) {}

    @Test void livePackScanMatchesVisibleIdSetIncludingDuplicatesAndHiddenPacks() {
        var choices = List.of(new Pack("vanilla", false), new Pack("mod_resources", false),
            new Pack("vanilla", false), new Pack("file/external.zip", false),
            new Pack("file/hidden.zip", true), new Pack("mod_resources", true), new Pack(null, false));
        for (int mask = 0; mask < 1 << choices.size(); mask++) {
            var selected = new ArrayList<Pack>();
            var ids = new java.util.HashSet<String>();
            for (int i = 0; i < choices.size(); i++) if ((mask & 1 << i) != 0) {
                var pack = choices.get(i);
                selected.add(pack);
                if (!pack.hidden()) ids.add(pack.id());
            }
            boolean expected = !ids.contains(null) && MakeUpQuadRuntime.resourcesMatch(ids);
            assertEquals(expected, MakeUpQuadRuntime.resourcesMatchPacks(selected, Pack::hidden, Pack::id),
                "Selected pack mask " + mask);
        }
    }

    @Test void resourceScanReadsCurrentSelectionWithoutRetainingAdmission() {
        var selected = new ArrayList<>(List.of(new Pack("vanilla", false), new Pack("mod_resources", false)));
        assertTrue(MakeUpQuadRuntime.resourcesMatchPacks(selected, Pack::hidden, Pack::id));
        selected.add(new Pack("file/external.zip", false));
        assertFalse(MakeUpQuadRuntime.resourcesMatchPacks(selected, Pack::hidden, Pack::id));
        selected.removeLast();
        assertTrue(MakeUpQuadRuntime.resourcesMatchPacks(selected, Pack::hidden, Pack::id));
        assertFalse(MakeUpQuadRuntime.resourcesMatchPacks(null, Pack::hidden, Pack::id));
    }
}

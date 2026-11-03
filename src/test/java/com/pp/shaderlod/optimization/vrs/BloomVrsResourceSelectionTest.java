package com.pp.shaderlod.optimization.vrs;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BloomVrsResourceSelectionTest {
    @Test void acceptsUnchangedOrderedGenericCollectionWithoutWeakeningIdentityGuards() {
        var captured=List.of("vanilla","mod_resources");
        Collection<String> repository=Collections.unmodifiableCollection(new ArrayList<>(captured));
        assertFalse(captured.equals(repository)); // The original draw-time guard rejected this real API shape.
        assertTrue(BloomVrsPrograms.selectedResourcesMatch(captured,repository));
        assertTrue(BloomVrsPrograms.selectedResourcesMatch(captured,new LinkedHashSet<>(captured)));
    }
    @Test void changedOrderMembershipAndCountStillRefuse() {
        var captured=List.of("vanilla","mod_resources");
        assertFalse(BloomVrsPrograms.selectedResourcesMatch(captured,List.of("mod_resources","vanilla")));
        assertFalse(BloomVrsPrograms.selectedResourcesMatch(captured,List.of("vanilla","foreign")));
        assertFalse(BloomVrsPrograms.selectedResourcesMatch(captured,List.of("vanilla")));
        assertFalse(BloomVrsPrograms.selectedResourcesMatch(captured,List.of("vanilla","mod_resources","foreign")));
        assertFalse(BloomVrsPrograms.selectedResourcesMatch(captured,null));
    }
}

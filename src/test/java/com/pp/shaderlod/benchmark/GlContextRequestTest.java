package com.pp.shaderlod.benchmark;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GlContextRequestTest {
    @Test void defaultAndNativeDoNotChangeTheContext() {
        assertNull(GlContextRequest.parse(null, false));
        assertNull(GlContextRequest.parse("native", false));
    }
    @Test void explicitRequestsRequireTheOwnedController() {
        for (var value : new String[]{"3.2", "4.3", "4.5", "4.6"}) {
            assertThrows(IllegalArgumentException.class, () -> GlContextRequest.parse(value, false));
            var request = GlContextRequest.parse(value, true);
            assertEquals(value, request.major() + "." + request.minor());
        }
    }
    @Test void unrecognizedValuesNeverFallBackSilently() {
        for (var value : new String[]{"", "true", "4.4", "4.6spoof", " 4.6"})
            assertThrows(IllegalArgumentException.class, () -> GlContextRequest.parse(value, true));
    }
}

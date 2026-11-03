package com.pp.shaderlod.packs;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CapturedRendererInputsTest {
    @Test void snapshotDoesNotRetainCallerArraysMapsOrMutableViews() {
        byte[] source = "line\n".getBytes(StandardCharsets.UTF_8);
        var members = new HashMap<>(Map.of("shaders/main.fsh", source));
        var options = new HashMap<>(Map.of("QUALITY", "2"));
        var captured = new CapturedRendererInputs("family", options, members);
        source[0] = 'X'; members.clear(); options.clear();
        assertTrue(captured.matchesIrisLines("shaders/main.fsh", List.of("line")));
        assertEquals(Map.of("QUALITY", "2"), captured.effectiveOptions());
        assertThrows(UnsupportedOperationException.class, () -> captured.memberNames().clear());
        assertThrows(UnsupportedOperationException.class, () -> captured.effectiveOptions().clear());
    }

    @Test void matchesPinnedIrisLineSemanticsIncludingUnicodeAndTrailingEmpties() {
        for (String source : List.of("", "\n", "a\r\nb\r\n\r\n", "a\u0085b\u2028c\u2029", "a\nb")) {
            var captured = new CapturedRendererInputs("family", Map.of(),
                Map.of("shaders/main.fsh", source.getBytes(StandardCharsets.UTF_8)));
            assertTrue(captured.matchesIrisLines("shaders/main.fsh", List.of(source.split("\\R"))), source);
            assertFalse(captured.matchesIrisLines("shaders/main.fsh", List.of("different")));
        }
    }

    @Test void malformedUtf8MissingMemberAndAbsentObservationRefuse() {
        var captured = new CapturedRendererInputs("family", Map.of(),
            Map.of("shaders/main.fsh", new byte[]{(byte) 0xc3, 0x28}));
        assertFalse(captured.matchesIrisLines("shaders/main.fsh", List.of("\ufffd(")));
        assertFalse(captured.matchesIrisLines("missing", List.of()));
        assertFalse(captured.matchesIrisLines("shaders/main.fsh", null));
    }
}

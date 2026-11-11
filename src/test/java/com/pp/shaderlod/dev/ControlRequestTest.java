package com.pp.shaderlod.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class ControlRequestTest {
  private ControlRequest decode(String text) {
    return ControlRequest.decode(JsonParser.parseString(text).getAsJsonObject(), 1);
  }

  @Test
  void strictCommandsAndNoReplay() {
    String good =
        "{\"schema\":\"shaderlod-control-v1\",\"sequence\":1,\"action\":\"capture\",\"duration_ms\":1000}";
    assertEquals(1000, decode(good).durationMillis());
    assertThrows(
        IllegalArgumentException.class,
        () -> ControlRequest.decode(JsonParser.parseString(good).getAsJsonObject(), 2));
    for (String bad :
        java.util.List.of(
            good.replace(":1,", ":true,"),
            good.replace(":1000", ":1.5"),
            good.replace(":1000", ":null"),
            good.replace("capture", "unknown"),
            good.replace(":1000", ":60001"),
            good.replace("capture", "status"),
            good.replace("\"sequence\":1", "\"sequence\":\"1\""))) {
      assertThrows(IllegalArgumentException.class, () -> decode(bad));
    }
    assertThrows(IllegalArgumentException.class, () -> ControlRequest.decode(null, 1));
  }
}

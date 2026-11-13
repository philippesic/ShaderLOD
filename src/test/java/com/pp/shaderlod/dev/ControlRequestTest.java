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
            good.replace(":1000", ":300001"),
            good.replace("capture", "status"),
            good.replace("\"sequence\":1", "\"sequence\":\"1\""))) {
      assertThrows(IllegalArgumentException.class, () -> decode(bad));
    }
    assertThrows(IllegalArgumentException.class, () -> ControlRequest.decode(null, 1));
  }

  @Test
  void prepareAcceptsOnlyBoundedSurfacesAlongsideConditions() {
    String prefix =
        "{\"schema\":\"shaderlod-control-v2\",\"sequence\":1,\"action\":\"prepare\",\"duration_ms\":0,\"payload\":";
    for (String payload :
        java.util.List.of(
            "{\"surface\":[3840,2160]}",
            "{\"day_time\":6000,\"weather\":\"clear\",\"surface\":[2560,1440]}",
            "{\"day_time\":6000,\"weather\":\"rain\",\"pose\":[0,64,0,0,0],\"surface\":[2560,1600]}")) {
      assertEquals(
          payload.contains("3840") ? 3840 : 2560,
          decode(prefix + payload + "}").payload().getAsJsonArray("surface").get(0).getAsInt());
    }
    for (String payload :
        java.util.List.of(
            "{\"surface\":[3840]}",
            "{\"surface\":[0,2160]}",
            "{\"surface\":[3840.5,2160]}",
            "{\"surface\":[\"3840\",2160]}",
            "{\"surface\":[20000,2160]}",
            "{\"surface\":[3840,2160],\"weather\":\"clear\"}",
            "{\"surface\":[3840,2160],\"zoom\":1}")) {
      assertThrows(IllegalArgumentException.class, () -> decode(prefix + payload + "}"));
    }
  }
}

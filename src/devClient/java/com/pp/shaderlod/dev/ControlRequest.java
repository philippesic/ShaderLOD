package com.pp.shaderlod.dev;

import com.google.gson.JsonObject;
import java.util.Set;

public record ControlRequest(
    long sequence, String action, long durationMillis, JsonObject payload) {
  public static ControlRequest decode(JsonObject request, long expectedSequence) {
    if (request == null || !request.has("schema") || !request.get("schema").isJsonPrimitive()) {
      throw new IllegalArgumentException("Command schema required");
    }
    boolean extended = request.get("schema").getAsString().equals("shaderlod-control-v2");
    var fields =
        extended
            ? Set.of("schema", "sequence", "action", "duration_ms", "payload")
            : Set.of("schema", "sequence", "action", "duration_ms");
    if (!request.keySet().equals(fields)) {
      throw new IllegalArgumentException("Unexpected command fields");
    }
    for (String key : Set.of("schema", "sequence", "action", "duration_ms")) {
      if (!request.get(key).isJsonPrimitive()) {
        throw new IllegalArgumentException("Primitive command fields required");
      }
    }
    if (!request.get("schema").getAsJsonPrimitive().isString()
        || !(extended || request.get("schema").getAsString().equals("shaderlod-control-v1"))
        || !request.get("action").getAsJsonPrimitive().isString()
        || !request.get("sequence").toString().matches("[0-9]+")
        || !request.get("duration_ms").toString().matches("[0-9]+")) {
      throw new IllegalArgumentException("Invalid command field types");
    }
    long sequence = request.get("sequence").getAsLong();
    long duration = request.get("duration_ms").getAsLong();
    String action = request.get("action").getAsString();
    if (sequence != expectedSequence
        || !Set.of(
                "status",
                "capture",
                "close",
                "select",
                "restore",
                "screenshot",
                "prepare",
                "recover",
                "validate")
            .contains(action)
        || (action.equals("capture") ? duration < 100 || duration > 300000 : duration != 0)) {
      throw new IllegalArgumentException("Command bounds or identity disagree");
    }
    JsonObject payload = new JsonObject();
    if (extended) {
      if (!request.get("payload").isJsonObject()) {
        throw new IllegalArgumentException("Object payload required");
      }
      payload = request.getAsJsonObject("payload").deepCopy();
    }
    if (action.equals("select")) {
      requireFields(payload, Set.of("pack", "sha256", "modules"));
    } else if (action.equals("prepare") && !payload.isEmpty()) {
      requireFields(payload, Set.of("day_time", "weather"));
      if (!payload.get("day_time").toString().matches("[0-9]+")
          || payload.get("day_time").getAsLong() >= 24000
          || !payload.get("weather").isJsonPrimitive()
          || !payload.get("weather").getAsJsonPrimitive().isString()
          || !Set.of("clear", "rain", "thunder").contains(payload.get("weather").getAsString())) {
        throw new IllegalArgumentException("Invalid fixed world conditions");
      }
    } else if (action.equals("capture")) {
      if (!payload.isEmpty()) {
        if (payload.has("durable_chunks")) {
          requireFields(payload, Set.of("from", "to", "delay_ms", "durable_chunks"));
          if (!payload.get("durable_chunks").isJsonPrimitive()
              || !payload.get("durable_chunks").getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Boolean durability control required");
          }
        } else if (payload.has("quality_cadence_ms")) {
          requireFields(payload, Set.of("from", "to", "delay_ms", "quality_cadence_ms"));
          if (!payload.get("quality_cadence_ms").toString().matches("[0-9]+")) {
            throw new IllegalArgumentException("Integer quality cadence required");
          }
        } else if (payload.has("diagnostic")) {
          requireFields(payload, Set.of("from", "to", "delay_ms", "diagnostic"));
          if (!payload.get("diagnostic").isJsonPrimitive()
              || !payload.get("diagnostic").getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Boolean diagnostic selection required");
          }
        } else {
          requireFields(payload, Set.of("from", "to", "delay_ms"));
        }
      }
    } else if (!payload.isEmpty()) {
      throw new IllegalArgumentException("Unexpected payload");
    }
    return new ControlRequest(sequence, action, duration, payload);
  }

  private static void requireFields(JsonObject payload, Set<String> fields) {
    if (!payload.keySet().equals(fields)) {
      throw new IllegalArgumentException("Unexpected action payload");
    }
  }
}

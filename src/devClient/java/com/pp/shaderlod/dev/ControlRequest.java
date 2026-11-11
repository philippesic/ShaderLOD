package com.pp.shaderlod.dev;

import com.google.gson.JsonObject;
import java.util.Set;

public record ControlRequest(long sequence, String action, long durationMillis) {
  public static ControlRequest decode(JsonObject request, long expectedSequence) {
    if (request == null
        || !request.keySet().equals(Set.of("schema", "sequence", "action", "duration_ms"))) {
      throw new IllegalArgumentException("Unexpected command fields");
    }
    for (String key : request.keySet()) {
      if (!request.get(key).isJsonPrimitive()) {
        throw new IllegalArgumentException("Primitive command fields required");
      }
    }
    if (!request.get("schema").getAsJsonPrimitive().isString()
        || !request.get("schema").getAsString().equals("shaderlod-control-v1")
        || !request.get("action").getAsJsonPrimitive().isString()
        || !request.get("sequence").toString().matches("[0-9]+")
        || !request.get("duration_ms").toString().matches("[0-9]+")) {
      throw new IllegalArgumentException("Invalid command field types");
    }
    long sequence = request.get("sequence").getAsLong();
    long duration = request.get("duration_ms").getAsLong();
    String action = request.get("action").getAsString();
    if (sequence != expectedSequence
        || !Set.of("status", "capture", "close").contains(action)
        || (action.equals("capture") ? duration < 100 || duration > 60000 : duration != 0)) {
      throw new IllegalArgumentException("Command bounds or identity disagree");
    }
    return new ControlRequest(sequence, action, duration);
  }
}

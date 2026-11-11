package com.pp.shaderlod.dev;

import java.util.List;

public record CameraRoute(List<Double> from, List<Double> to) {
  public CameraRoute {
    from = validate(from);
    to = validate(to);
    double distance = 0;
    for (int i = 0; i < 3; i++) {
      distance += Math.pow(to.get(i) - from.get(i), 2);
    }
    if (distance > 4096) {
      throw new IllegalArgumentException("Route exceeds 64 block extent");
    }
  }

  private static List<Double> validate(List<Double> values) {
    if (values.size() != 5 || values.stream().anyMatch(v -> v == null || !Double.isFinite(v))) {
      throw new IllegalArgumentException("Finite x/y/z/yaw/pitch required");
    }
    if (Math.abs(values.get(0)) > 29000000
        || Math.abs(values.get(2)) > 29000000
        || values.get(1) < -64
        || values.get(1) > 1024
        || Math.abs(values.get(4)) > 90) {
      throw new IllegalArgumentException("Route outside supported world bounds");
    }
    return List.copyOf(values);
  }

  public List<Double> at(double progress) {
    if (!Double.isFinite(progress)) {
      throw new IllegalArgumentException("Finite route progression required");
    }
    double alpha = Math.clamp(progress, 0, 1);
    var pose = new java.util.ArrayList<Double>();
    for (int i = 0; i < 5; i++) {
      pose.add(from.get(i) + alpha * (to.get(i) - from.get(i)));
    }
    return List.copyOf(pose);
  }
}

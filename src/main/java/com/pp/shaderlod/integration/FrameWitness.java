package com.pp.shaderlod.integration;

import java.lang.ref.WeakReference;

public final class FrameWitness {
  private static volatile WeakReference<Object> last = new WeakReference<>(null);

  private FrameWitness() {}

  public static void completed(Object pipeline) {
    last = new WeakReference<>(pipeline);
  }

  public static boolean observedFor(Object pipeline) {
    return pipeline != null && last.refersTo(pipeline);
  }
}

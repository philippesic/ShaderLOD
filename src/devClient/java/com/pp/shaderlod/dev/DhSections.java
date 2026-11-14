package com.pp.shaderlod.dev;

public final class DhSections {
  static final int REPORTED_DEPTH = 2;
  private static int depth;

  private DhSections() {}

  static void reset() {
    depth = 0;
  }

  public static void began(String name) {
    depth++;
    if (depth <= REPORTED_DEPTH) {
      RenderGroups.began(label(name));
    }
  }

  public static void changed(String name) {
    if (depth >= 1 && depth <= REPORTED_DEPTH) {
      RenderGroups.ended();
      RenderGroups.began(label(name));
    }
  }

  public static void ended() {
    if (depth >= 1 && depth <= REPORTED_DEPTH) {
      RenderGroups.ended();
    }
    depth = Math.max(0, depth - 1);
  }

  static String label(String name) {
    return name != null && name.startsWith("DH") ? name : "DH " + name;
  }
}

package com.pp.shaderlod.dev;

import java.util.ArrayDeque;
import java.util.function.Consumer;

public final class RenderGroups {
  public static final String UNGROUPED = "ungrouped";
  private static final int DEPTH = 32;
  private static final int LABEL = 64;
  private static final ArrayDeque<String> OPEN = new ArrayDeque<>();
  private static int unrecorded;
  private static Consumer<String> observer;

  private RenderGroups() {}

  static void observe(Consumer<String> next) {
    observer = next;
  }

  static void reset() {
    OPEN.clear();
    unrecorded = 0;
  }

  static String owner() {
    String current = OPEN.peekLast();
    return current == null ? UNGROUPED : current;
  }

  public static void began(String name) {
    if (OPEN.size() == DEPTH) {
      unrecorded++;
      return;
    }
    String label = name == null || name.isBlank() ? "unnamed" : name.replace('/', '_');
    if (label.length() > LABEL) {
      label = label.substring(0, LABEL);
    }
    String parent = OPEN.peekLast();
    OPEN.addLast(parent == null ? label : parent + "/" + label);
    publish();
  }

  public static void ended() {
    if (unrecorded > 0) {
      unrecorded--;
      return;
    }
    if (OPEN.pollLast() != null) {
      publish();
    }
  }

  private static void publish() {
    if (observer != null) {
      observer.accept(owner());
    }
  }
}

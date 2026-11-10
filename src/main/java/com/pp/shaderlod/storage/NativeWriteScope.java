package com.pp.shaderlod.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;

public final class NativeWriteScope implements AutoCloseable {
  private static final ThreadLocal<NativeWriteScope> CURRENT = new ThreadLocal<>();
  private final FileJournal journal;
  private final NativeWriteScope previous;
  private IOException failure;

  public NativeWriteScope(FileJournal journal) {
    this.journal = journal;
    previous = CURRENT.get();
    CURRENT.set(this);
  }

  public static boolean active() {
    return CURRENT.get() != null;
  }

  public static OutputStream output(Path path, OpenOption[] options) throws IOException {
    var scope = CURRENT.get();
    if (scope == null) {
      return Files.newOutputStream(path, options);
    }
    return new ByteArrayOutputStream() {
      private boolean closed;

      @Override
      public void close() throws IOException {
        if (!closed) {
          closed = true;
          try {
            scope.journal.write(path, toByteArray());
          } catch (IOException error) {
            scope.failure = error;
            throw error;
          }
        }
      }
    };
  }

  public static void delete(Path path) throws IOException {
    var scope = CURRENT.get();
    if (scope == null) {
      Files.delete(path);
      return;
    }
    try {
      scope.journal.delete(path);
    } catch (IOException error) {
      scope.failure = error;
      throw error;
    }
  }

  public void verify() throws IOException {
    if (failure != null) {
      throw failure;
    }
  }

  @Override
  public void close() {
    CURRENT.set(previous);
  }
}

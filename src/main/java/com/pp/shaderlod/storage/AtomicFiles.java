package com.pp.shaderlod.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class AtomicFiles {
  private AtomicFiles() {}

  public static void publish(Path destination, byte[] bytes) throws IOException {
    Path temporary = stage(destination, bytes);
    try {
      Files.createLink(destination, temporary);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  public static void replace(Path destination, byte[] bytes) throws IOException {
    Path temporary = stage(destination, bytes);
    try {
      if (Files.exists(destination)
          && Files.getFileAttributeView(
                  destination, java.nio.file.attribute.PosixFileAttributeView.class)
              != null) {
        Files.setPosixFilePermissions(temporary, Files.getPosixFilePermissions(destination));
      }
      Files.move(
          temporary,
          destination,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static Path stage(Path destination, byte[] bytes) throws IOException {
    Path parent = destination.toAbsolutePath().getParent();
    if (Files.isSymbolicLink(destination) || !Files.isDirectory(parent)) {
      throw new IOException("Unsafe or absent publication parent");
    }
    Path temporary = Files.createTempFile(parent, ".shaderlod-", ".tmp");
    boolean complete = false;
    try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
      var buffer = ByteBuffer.wrap(bytes);
      while (buffer.hasRemaining()) {
        channel.write(buffer);
      }
      channel.force(true);
      complete = true;
      return temporary;
    } finally {
      if (!complete) {
        Files.deleteIfExists(temporary);
      }
    }
  }
}

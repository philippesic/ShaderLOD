package com.pp.shaderlod.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CaptureSpoolTest {
  @TempDir Path directory;

  @Test
  void chunksAreImmutableAndPublicationFailureIsNotSuccess() throws Exception {
    var frames = List.of(new FrameCapture.Frame(1, 100, 10, 1));
    var spool = new CaptureSpool(directory, 1);
    spool.offer(frames);
    assertTrue((Boolean) spool.finish().get("complete"));
    var first = Files.readAllBytes(directory.resolve("frames-1-1.json"));
    var collision = new CaptureSpool(directory, 1);
    collision.offer(frames);
    assertFalse((Boolean) collision.finish().get("complete"));
    assertEquals(
        new String(first, java.nio.charset.StandardCharsets.UTF_8),
        Files.readString(directory.resolve("frames-1-1.json")));
  }
}

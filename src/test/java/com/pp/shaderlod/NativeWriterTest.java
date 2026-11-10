package com.pp.shaderlod;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pp.shaderlod.storage.FileJournal;
import com.pp.shaderlod.storage.NativeWriteScope;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeWriterTest {
  @TempDir Path root;

  @Test
  void opaqueNativeWritesAndDeletionRecoverExactPreimage() throws IOException {
    Path config = root.resolve("iris.properties");
    byte[] original = "shaderPack=original.zip\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    Files.write(config, original);
    Path manifest = root.resolve("journal");
    var journal = FileJournal.begin(root, manifest, List.of(config));
    try (var scope = new NativeWriteScope(journal)) {
      try (var output = NativeWriteScope.output(config, new OpenOption[0])) {
        output.write(
            "opaque-native-serialization".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertArrayEquals(original, Files.readAllBytes(config));
      }
      NativeWriteScope.delete(config);
      scope.verify();
    }
    assertFalse(Files.exists(config));
    FileJournal.recover(root, manifest).restore();
    assertArrayEquals(original, Files.readAllBytes(config));
  }

  @Test
  void nativeSwallowedIoFailureStillReachesCaller() throws IOException {
    Path config = root.resolve("iris.properties");
    Files.writeString(config, "original");
    var journal = FileJournal.begin(root, root.resolve("journal"), List.of(config));
    try (var scope = new NativeWriteScope(journal)) {
      var output = NativeWriteScope.output(root.resolve("foreign"), new OpenOption[0]);
      output.write(1);
      assertThrows(IOException.class, output::close);
      assertThrows(IOException.class, scope::verify);
    }
    assertFalse(Files.exists(root.resolve("foreign")));
  }
}

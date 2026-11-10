package com.pp.shaderlod.engine;

import com.pp.shaderlod.domain.PackIdentity;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;

public final class PackCatalog {
  public record Primary(String id, String filename, String sha256) {
    public Primary {
      PackIdentity.requireDigest(sha256);
      if (id.isBlank() || filename.isBlank() || filename.contains("/") || filename.contains("\\")) {
        throw new IllegalArgumentException("Invalid pack specification");
      }
    }
  }

  private PackCatalog() {}

  public static Primary primary(ClassLoader loader) {
    var values = new Properties();
    try (var input = loader.getResourceAsStream("META-INF/shaderlod/primary.properties")) {
      if (input == null) {
        throw new IllegalStateException("Pack specification absent from artifact");
      }
      values.load(new InputStreamReader(input, StandardCharsets.UTF_8));
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot read pinned pack specification", failure);
    }
    if (!values.stringPropertyNames().equals(Set.of("id", "filename", "sha256", "schema"))
        || !"1".equals(values.getProperty("schema"))) {
      throw new IllegalStateException("Unsupported pack specification schema");
    }
    return new Primary(
        values.getProperty("id"), values.getProperty("filename"), values.getProperty("sha256"));
  }
}

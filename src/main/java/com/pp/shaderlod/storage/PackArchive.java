package com.pp.shaderlod.storage;

import com.pp.shaderlod.domain.PackIdentity;
import com.pp.shaderlod.domain.ResourcePatch;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class PackArchive {
  public record Limits(long archiveBytes, int memberBytes, long expandedBytes, int entries) {
    public Limits {
      if (archiveBytes < 1
          || archiveBytes >= Integer.MAX_VALUE
          || memberBytes < 1
          || memberBytes == Integer.MAX_VALUE
          || expandedBytes < 1
          || entries < 1) {
        throw new IllegalArgumentException("Positive archive limits required");
      }
    }

    public static Limits defaults() {
      return new Limits(64L * 1024 * 1024, 32 * 1024 * 1024, 256L * 1024 * 1024, 20000);
    }
  }

  private final PackIdentity identity;
  private final Map<String, byte[]> members;
  private final Limits limits;

  private PackArchive(byte[] bytes, Limits bounds) throws IOException {
    limits = bounds;
    var loaded = new TreeMap<String, byte[]>();
    long total = 0;
    int count = 0;
    try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (++count > bounds.entries()) {
          throw new IOException("Archive entry limit exceeded");
        }
        String name = entry.getName();
        if (name.isBlank()
            || name.startsWith("/")
            || name.contains("\\")
            || name.contains(":")
            || List.of(name.split("/")).stream()
                .anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."))) {
          throw new IOException("Unsafe archive member: " + name);
        }
        if (loaded.containsKey(name)) {
          throw new IOException("Duplicate archive member: " + name);
        }
        byte[] content = zip.readNBytes(bounds.memberBytes() + 1);
        total += content.length;
        if (content.length > bounds.memberBytes() || total > bounds.expandedBytes()) {
          throw new IOException("Expanded archive limit exceeded");
        }
        loaded.put(name, content);
      }
    }
    if (loaded.keySet().stream()
        .noneMatch(name -> name.startsWith("shaders/") && !name.endsWith("/"))) {
      throw new IOException("Archive has no shader sources");
    }
    members = Map.copyOf(loaded);
    var hashes = new TreeMap<String, String>();
    members.forEach((name, value) -> hashes.put(name, sha256(value)));
    identity = new PackIdentity(sha256(bytes), hashes);
  }

  public static PackArchive read(Path path, Limits bounds) throws IOException {
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
        || Files.size(path) > bounds.archiveBytes()) {
      throw new IOException("Expected bounded regular shader archive");
    }
    byte[] bytes;
    try (var input = Files.newInputStream(path)) {
      bytes = input.readNBytes(Math.toIntExact(bounds.archiveBytes() + 1));
    }
    if (bytes.length > bounds.archiveBytes()) {
      throw new IOException("Archive grew beyond admission limit");
    }
    return new PackArchive(bytes, bounds);
  }

  public PackIdentity identity() {
    return identity;
  }

  public byte[] member(String name) {
    byte[] value = members.get(name);
    if (value == null) {
      throw new IllegalArgumentException("Unknown archive member: " + name);
    }
    return value.clone();
  }

  public PackIdentity derive(Path output, List<ResourcePatch> patches) throws IOException {
    byte[] result = transformed(patches);
    var derived = new PackArchive(result, limits);
    AtomicFiles.publish(output, result);
    return derived.identity();
  }

  public record Derived(Path path, PackIdentity identity) {}

  public Derived cached(Path directory, List<ResourcePatch> patches) throws IOException {
    byte[] result = transformed(patches);
    var derived = new PackArchive(result, limits);
    Path target = directory.resolve("shaderlod-" + derived.identity().archiveSha256() + ".zip");
    if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
      try {
        AtomicFiles.publish(target, result);
      } catch (java.nio.file.FileAlreadyExistsException concurrent) {
        if (!read(target, limits).identity().equals(derived.identity())) {
          throw concurrent;
        }
      }
    }
    if (!read(target, limits).identity().equals(derived.identity())) {
      throw new IOException("Derived archive cache identity disagreement");
    }
    return new Derived(target, derived.identity());
  }

  private byte[] transformed(List<ResourcePatch> patches) throws IOException {
    var replacements = new HashMap<String, byte[]>();
    for (var patch : patches) {
      if (!patch.expectedSha256().equals(identity.memberSha256().get(patch.member()))
          || replacements.putIfAbsent(patch.member(), patch.replacement()) != null
          || patch.replacement().length > limits.memberBytes()) {
        throw new IOException("Stale, overlapping or oversized shader patch");
      }
    }
    var bytes = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(bytes)) {
      for (String name : members.keySet().stream().sorted().toList()) {
        var entry = new ZipEntry(name);
        entry.setTime(0);
        zip.putNextEntry(entry);
        zip.write(replacements.getOrDefault(name, members.get(name)));
        zip.closeEntry();
      }
    }
    byte[] result = bytes.toByteArray();
    if (result.length > limits.archiveBytes()) {
      throw new IOException("Derived archive exceeds admission limit");
    }
    return result;
  }

  public static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("Required SHA-256 unavailable", failure);
    }
  }
}

package com.pp.shaderlod.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

public final class FileJournal {
  private static final int MAX_BYTES = 16 * 1024 * 1024;
  private static final int MAX_FILES = 64;
  private final Path root;
  private final Path manifest;
  private final Map<Path, Optional<byte[]>> originals;
  private final Map<Path, String> expected;
  private final Map<String, String> metadata;

  private FileJournal(
      Path root,
      Path manifest,
      Map<Path, Optional<byte[]>> originals,
      Map<Path, String> expected,
      Map<String, String> metadata) {
    this.root = root;
    this.manifest = manifest;
    this.originals = originals;
    this.expected = expected;
    this.metadata = Map.copyOf(metadata);
  }

  public static FileJournal begin(Path root, Path manifest, List<Path> files) throws IOException {
    return begin(root, manifest, files, Map.of());
  }

  public static FileJournal begin(
      Path root, Path manifest, List<Path> files, Map<String, String> metadata) throws IOException {
    Path ownedRoot = root.toRealPath();
    Path ownedManifest = safe(ownedRoot, manifest);
    if (files.isEmpty() || files.size() > MAX_FILES || metadata.size() > 256) {
      throw new IOException("Journal requires bounded owned file inventory");
    }
    var before = new LinkedHashMap<Path, Optional<byte[]>>();
    var after = new LinkedHashMap<Path, String>();
    for (Path file : files) {
      Path path = safe(ownedRoot, file);
      if (path.equals(ownedManifest) || before.containsKey(path)) {
        throw new IOException("Duplicate or self-referential journal entry");
      }
      Optional<byte[]> bytes = read(path);
      before.put(path, bytes);
      after.put(path, digest(bytes));
    }
    var journal = new FileJournal(ownedRoot, ownedManifest, before, after, metadata);
    AtomicFiles.publish(ownedManifest, journal.encode());
    return journal;
  }

  public static FileJournal recover(Path root, Path manifest) throws IOException {
    Path ownedRoot = root.toRealPath();
    Path ownedManifest = safe(ownedRoot, manifest);
    var values = new Properties();
    values.load(
        new ByteArrayInputStream(
            read(ownedManifest).orElseThrow(() -> new IOException("Recovery journal absent"))));
    if (!"shaderlod-journal-v1".equals(values.getProperty("schema"))) {
      throw new IOException("Unsupported recovery journal version");
    }
    int count = integer(values.getProperty("count"));
    int metadataCount = integer(values.getProperty("metadata.count"));
    if (count < 1 || count > MAX_FILES || metadataCount > 256) {
      throw new IOException("Invalid recovery inventory size");
    }
    var keys = new HashSet<>(Set.of("schema", "count", "metadata.count"));
    var before = new LinkedHashMap<Path, Optional<byte[]>>();
    var after = new LinkedHashMap<Path, String>();
    var metadata = new LinkedHashMap<String, String>();
    for (int i = 0; i < count; i++) {
      String prefix = "file." + i + ".";
      keys.addAll(Set.of(prefix + "path", prefix + "before", prefix + "after"));
      String relative = values.getProperty(prefix + "path");
      if (relative == null || Path.of(relative).isAbsolute()) {
        throw new IOException("Journal path must be relative");
      }
      Path path = safe(ownedRoot, ownedRoot.resolve(relative));
      String encoded = values.getProperty(prefix + "before");
      String target = values.getProperty(prefix + "after");
      if (encoded == null
          || target == null
          || path.equals(ownedManifest)
          || before.containsKey(path)
          || !(target.equals("missing") || target.matches("[a-f0-9]{64}"))) {
        throw new IOException("Malformed recovery entry");
      }
      try {
        before.put(
            path,
            encoded.equals("missing")
                ? Optional.empty()
                : Optional.of(Base64.getDecoder().decode(encoded)));
      } catch (IllegalArgumentException failure) {
        throw new IOException("Invalid recovery preimage", failure);
      }
      after.put(path, target);
    }
    for (int i = 0; i < metadataCount; i++) {
      String key = "metadata." + i + ".key";
      String value = "metadata." + i + ".value";
      keys.addAll(Set.of(key, value));
      String name = values.getProperty(key);
      String content = values.getProperty(value);
      if (name == null || content == null || metadata.putIfAbsent(name, content) != null) {
        throw new IOException("Malformed recovery metadata");
      }
    }
    if (!keys.equals(values.stringPropertyNames())) {
      throw new IOException("Unknown recovery journal fields");
    }
    return new FileJournal(ownedRoot, ownedManifest, before, after, metadata);
  }

  public Map<String, String> metadata() {
    return metadata;
  }

  public Optional<byte[]> original(Path path) throws IOException {
    Path file = owned(path);
    return originals.get(file).map(byte[]::clone);
  }

  public synchronized void expect(Path path, byte[] intended) throws IOException {
    if (intended.length > MAX_BYTES) {
      throw new IOException("Mutation exceeds journal limit");
    }
    Path file = owned(path);
    requireExpected(file);
    expected.put(file, PackArchive.sha256(intended));
    AtomicFiles.replace(manifest, encode());
  }

  public synchronized void write(Path path, byte[] intended) throws IOException {
    Path file = owned(path);
    expect(file, intended);
    AtomicFiles.replace(file, intended);
  }

  public synchronized void delete(Path path) throws IOException {
    Path file = owned(path);
    requireExpected(file);
    expected.put(file, "missing");
    AtomicFiles.replace(manifest, encode());
    Files.deleteIfExists(file);
  }

  public synchronized void restore() throws IOException {
    restoreFiles();
    finish();
  }

  public synchronized void restoreFiles() throws IOException {
    for (Path path : originals.keySet()) {
      requireExpected(owned(path));
    }
    for (var entry : originals.entrySet()) {
      Path path = owned(entry.getKey());
      requireExpected(path);
      if (entry.getValue().isPresent()) {
        AtomicFiles.replace(path, entry.getValue().orElseThrow());
      } else {
        Files.deleteIfExists(path);
      }
    }
  }

  public synchronized void finish() throws IOException {
    for (Path path : originals.keySet()) {
      if (!digest(read(owned(path))).equals(digest(originals.get(path)))) {
        throw new IOException("Cannot finish before exact preimage restoration");
      }
    }
    Files.delete(manifest);
  }

  private Path owned(Path path) throws IOException {
    Path result = safe(root, path);
    if (!originals.containsKey(result)) {
      throw new IOException("File outside captured transaction inventory");
    }
    return result;
  }

  private void requireExpected(Path path) throws IOException {
    String current = digest(read(path));
    if (!current.equals(expected.get(path)) && !current.equals(digest(originals.get(path)))) {
      throw new IOException("Foreign edit prevents restoration: " + path.getFileName());
    }
  }

  private byte[] encode() throws IOException {
    var values = new Properties();
    values.setProperty("schema", "shaderlod-journal-v1");
    values.setProperty("count", Integer.toString(originals.size()));
    values.setProperty("metadata.count", Integer.toString(metadata.size()));
    int i = 0;
    for (var entry : originals.entrySet()) {
      String prefix = "file." + i++ + ".";
      values.setProperty(prefix + "path", root.relativize(entry.getKey()).toString());
      values.setProperty(
          prefix + "before",
          entry.getValue().map(Base64.getEncoder()::encodeToString).orElse("missing"));
      values.setProperty(prefix + "after", expected.get(entry.getKey()));
    }
    i = 0;
    for (var entry : metadata.entrySet()) {
      values.setProperty("metadata." + i + ".key", entry.getKey());
      values.setProperty("metadata." + i++ + ".value", entry.getValue());
    }
    var output = new ByteArrayOutputStream();
    values.store(output, null);
    byte[] bytes = output.toByteArray();
    if (bytes.length > MAX_BYTES) {
      throw new IOException("Journal exceeds bounded storage");
    }
    return bytes;
  }

  private static Optional<byte[]> read(Path path) throws IOException {
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      return Optional.empty();
    }
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_BYTES) {
      throw new IOException("Expected bounded regular journal input");
    }
    try (var input = Files.newInputStream(path)) {
      byte[] bytes = input.readNBytes(MAX_BYTES + 1);
      if (bytes.length > MAX_BYTES) {
        throw new IOException("Journal input grew beyond limit");
      }
      return Optional.of(bytes);
    }
  }

  private static String digest(Optional<byte[]> value) {
    return value.map(PackArchive::sha256).orElse("missing");
  }

  private static int integer(String value) throws IOException {
    try {
      int result = Integer.parseInt(value);
      if (result < 0) {
        throw new IOException("Negative journal count");
      }
      return result;
    } catch (NumberFormatException failure) {
      throw new IOException("Invalid journal count", failure);
    }
  }

  private static Path safe(Path root, Path path) throws IOException {
    Path resolved = path.toAbsolutePath().normalize();
    if (!resolved.startsWith(root) || resolved.equals(root)) {
      throw new IOException("Path outside owned journal root");
    }
    Path current = root;
    for (Path part : root.relativize(resolved)) {
      current = current.resolve(part);
      if (Files.isSymbolicLink(current)) {
        throw new IOException("Symlink in journal path");
      }
    }
    return resolved;
  }
}

package com.pp.shaderlod;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pp.shaderlod.domain.Activation;
import com.pp.shaderlod.domain.NativeState;
import com.pp.shaderlod.domain.PackIdentity;
import com.pp.shaderlod.domain.ResourcePatch;
import com.pp.shaderlod.domain.Selection;
import com.pp.shaderlod.engine.OptimizationProvider;
import com.pp.shaderlod.engine.OptimizationRegistry;
import com.pp.shaderlod.engine.SelectionTransaction;
import com.pp.shaderlod.storage.FileJournal;
import com.pp.shaderlod.storage.PackArchive;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceContractsTest {
  @TempDir Path directory;
  private static final String ZERO = "0".repeat(64);
  private static final String ONE = "1".repeat(64);

  public static final class IdentityProvider implements OptimizationProvider {
    public IdentityProvider() {}

    @Override
    public String id() {
      return "test-identity";
    }

    @Override
    public boolean supports(PackIdentity identity) {
      return true;
    }

    @Override
    public List<ResourcePatch> patches(PackIdentity identity, Map<String, String> parameters) {
      if (!parameters.isEmpty()) {
        throw new IllegalArgumentException("No parameters supported");
      }
      return List.of();
    }
  }

  private Path zip(String filename, Map<String, byte[]> members) throws IOException {
    Path path = directory.resolve(filename);
    try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
      for (var entry : members.entrySet()) {
        zip.putNextEntry(new ZipEntry(entry.getKey()));
        zip.write(entry.getValue());
        zip.closeEntry();
      }
    }
    return path;
  }

  @Test
  void archiveIdentityAndDerivedPatchPreserveOriginal() throws IOException {
    byte[] source = "original".getBytes(StandardCharsets.UTF_8);
    Path original = zip("native.zip", Map.of("shaders/a.fsh", source, "LICENSE", new byte[] {7}));
    byte[] originalBytes = Files.readAllBytes(original);
    var archive = PackArchive.read(original, PackArchive.Limits.defaults());
    assertEquals(
        "0682c5f2076f099c34cfdd15a9e063849ed437a49677e6fcc5b4198c76575be5",
        archive.identity().memberSha256().get("shaders/a.fsh"));
    var patch =
        new ResourcePatch(
            "shaders/a.fsh",
            archive.identity().memberSha256().get("shaders/a.fsh"),
            new byte[] {1, 2});
    Path derived = directory.resolve("derived.zip");
    var changed = archive.derive(derived, List.of(patch));
    assertArrayEquals(originalBytes, Files.readAllBytes(original));
    assertArrayEquals(
        new byte[] {1, 2},
        PackArchive.read(derived, PackArchive.Limits.defaults()).member("shaders/a.fsh"));
    assertArrayEquals(
        new byte[] {7}, PackArchive.read(derived, PackArchive.Limits.defaults()).member("LICENSE"));
    assertFalse(changed.archiveSha256().equals(archive.identity().archiveSha256()));
    Path repeated = directory.resolve("repeated.zip");
    archive.derive(repeated, List.of(patch));
    assertArrayEquals(Files.readAllBytes(derived), Files.readAllBytes(repeated));
    assertThrows(
        IOException.class,
        () ->
            archive.derive(
                directory.resolve("bad.zip"),
                List.of(new ResourcePatch("shaders/a.fsh", ZERO, new byte[] {1}))));
    assertFalse(Files.exists(directory.resolve("bad.zip")));
    assertThrows(
        java.nio.file.FileAlreadyExistsException.class,
        () -> archive.derive(derived, List.of(patch)));
  }

  @Test
  void archiveBoundsAndTraversalRefuse() throws IOException {
    Path unsafe = zip("unsafe.zip", Map.of("../shaders/a.fsh", new byte[] {1}));
    assertThrows(IOException.class, () -> PackArchive.read(unsafe, PackArchive.Limits.defaults()));
    Path normal = zip("normal.zip", Map.of("shaders/a.fsh", new byte[] {1, 2}));
    assertThrows(
        IOException.class,
        () -> PackArchive.read(normal, new PackArchive.Limits(4096, 1, 100, 10)));
    assertThrows(
        IOException.class, () -> PackArchive.read(normal, new PackArchive.Limits(1, 10, 100, 10)));
    assertThrows(
        IOException.class, () -> PackArchive.read(normal, new PackArchive.Limits(4096, 10, 1, 10)));
    byte[] mutable = {1};
    var patch = new ResourcePatch("shaders/a.fsh", ZERO, mutable);
    mutable[0] = 2;
    patch.replacement()[0] = 3;
    assertArrayEquals(new byte[] {1}, patch.replacement());
  }

  @Test
  void duplicateArchiveMembersRefuseBeforeDerivedPublication() throws IOException {
    Path source =
        zip(
            "duplicate.zip",
            Map.of("shaders/a.fsh", new byte[] {1}, "shaders/b.fsh", new byte[] {2}));
    byte[] archive = Files.readAllBytes(source);
    byte[] second = "shaders/b.fsh".getBytes(StandardCharsets.UTF_8);
    for (int offset = 0; offset <= archive.length - second.length; offset++) {
      boolean matches = true;
      for (int index = 0; index < second.length; index++) {
        matches &= archive[offset + index] == second[index];
      }
      if (matches) {
        archive[offset + "shaders/".length()] = (byte) 'a';
      }
    }
    Files.write(source, archive);
    assertThrows(IOException.class, () -> PackArchive.read(source, PackArchive.Limits.defaults()));
  }

  @Test
  void derivedArchiveCacheReusesOnlyIdenticalContent() throws IOException {
    var source = zip("cache-source.zip", Map.of("shaders/a.fsh", new byte[] {1}));
    var archive = PackArchive.read(source, PackArchive.Limits.defaults());
    var first = archive.cached(directory, List.of());
    var second = archive.cached(directory, List.of());
    assertEquals(first, second);
    Files.write(first.path(), new byte[] {0});
    assertThrows(IOException.class, () -> archive.cached(directory, List.of()));
  }

  @Test
  void packagedPackDataIsConsumedAndUnknownSchemaRefuses() throws IOException {
    var primary = com.pp.shaderlod.engine.PackCatalog.primary(getClass().getClassLoader());
    assertEquals("complementary-reimagined", primary.id());
    assertEquals(
        "fed6c879e732cf7fc7bc6570ebb1def43235aa7e96f62859fbdbdbfa0b484b38", primary.sha256());
    Path metadata = directory.resolve("META-INF/shaderlod");
    Files.createDirectories(metadata);
    Files.writeString(
        metadata.resolve("primary.properties"),
        "schema=999\nid=unknown\nfilename=pack.zip\nsha256=" + ZERO + "\n");
    try (var loader =
        new URLClassLoader(
            new java.net.URL[] {directory.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
      assertThrows(
          IllegalStateException.class, () -> com.pp.shaderlod.engine.PackCatalog.primary(loader));
    }
  }

  @Test
  void discoveryPlantAndWeedUseActualServiceLoader() throws IOException {
    var baseline = OptimizationRegistry.load(getClass().getClassLoader()).ids();
    var planted = new java.util.ArrayList<>(baseline);
    planted.add("test-identity");
    planted.sort(String::compareTo);
    Path services = directory.resolve("META-INF/services");
    Files.createDirectories(services);
    Path descriptor = services.resolve(OptimizationProvider.class.getName());
    Files.writeString(descriptor, IdentityProvider.class.getName() + "\n");
    try (var loader =
        new URLClassLoader(
            new java.net.URL[] {directory.toUri().toURL()}, getClass().getClassLoader())) {
      var loaded = OptimizationRegistry.load(loader);
      assertEquals(planted, loaded.ids());
      assertEquals(
          List.of(),
          loaded.prepare(new PackIdentity(ZERO, Map.of()), List.of("test-identity"), Map.of()));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              loaded.prepare(
                  new PackIdentity(ZERO, Map.of()),
                  List.of("test-identity"),
                  Map.of("test-identity", Map.of("unknown", "1"))));
    }
    Files.delete(descriptor);
    try (var loader =
        new URLClassLoader(
            new java.net.URL[] {directory.toUri().toURL()}, getClass().getClassLoader())) {
      assertEquals(baseline, OptimizationRegistry.load(loader).ids());
    }
    assertThrows(
        IllegalArgumentException.class,
        () -> OptimizationRegistry.of(List.of(new IdentityProvider(), new IdentityProvider())));
  }

  @Test
  void journalRecoversPreimageAndAbsenceAfterFreshReader() throws IOException {
    Path nativeFile = directory.resolve("iris.properties");
    Path generated = directory.resolve("candidate.properties");
    Path manifest = directory.resolve("selection.journal");
    Files.writeString(nativeFile, "native=true");
    var journal =
        FileJournal.begin(
            directory, manifest, List.of(nativeFile, generated), Map.of("queue", "original"));
    assertThrows(
        java.nio.file.FileAlreadyExistsException.class,
        () -> FileJournal.begin(directory, manifest, List.of(nativeFile)));
    journal.write(nativeFile, "changed=true".getBytes(StandardCharsets.UTF_8));
    journal.write(generated, new byte[] {7});
    var recovered = FileJournal.recover(directory, manifest);
    assertEquals(Map.of("queue", "original"), recovered.metadata());
    recovered.restore();
    assertEquals("native=true", Files.readString(nativeFile));
    assertFalse(Files.exists(generated));
    assertFalse(Files.exists(manifest));
  }

  @Test
  void journalWriteGapAndForeignEditHandling() throws IOException {
    Path file = directory.resolve("iris.properties");
    Path manifest = directory.resolve("selection.journal");
    Files.writeString(file, "native");
    var journal = FileJournal.begin(directory, manifest, List.of(file));
    journal.expect(file, "after".getBytes(StandardCharsets.UTF_8));
    FileJournal.recover(directory, manifest).restore();
    assertEquals("native", Files.readString(file));
    journal = FileJournal.begin(directory, manifest, List.of(file));
    journal.write(file, "after".getBytes(StandardCharsets.UTF_8));
    Files.writeString(file, "foreign");
    var recovered = FileJournal.recover(directory, manifest);
    assertThrows(IOException.class, recovered::restore);
    assertEquals("foreign", Files.readString(file));
    assertTrue(Files.exists(manifest));
    assertThrows(
        IOException.class,
        () ->
            FileJournal.begin(
                directory,
                directory.resolve("other.journal"),
                List.of(directory.resolve("../escape"))));
  }

  @Test
  void restorationJournalSurvivesReloadBoundaryAndRequiresExactFinish() throws IOException {
    Path file = directory.resolve("iris.properties");
    Path manifest = directory.resolve("selection.journal");
    Files.writeString(file, "native");
    var journal = FileJournal.begin(directory, manifest, List.of(file));
    journal.write(file, "owned".getBytes(StandardCharsets.UTF_8));
    assertThrows(IOException.class, journal::finish);
    journal.restoreFiles();
    assertTrue(Files.exists(manifest));
    var recovered = FileJournal.recover(directory, manifest);
    Files.writeString(file, "foreign reload");
    assertThrows(IOException.class, recovered::restoreFiles);
    assertThrows(IOException.class, recovered::finish);
    assertTrue(Files.exists(manifest));
    Files.writeString(file, "native");
    recovered.restoreFiles();
    recovered.finish();
    assertFalse(Files.exists(manifest));
  }

  private static final class Backend implements SelectionTransaction.Backend {
    private NativeState state =
        new NativeState(Optional.empty(), false, Map.of("queued", "native"), Map.of());
    private boolean failLink;
    private boolean submitted;
    private String digest = ZERO;
    private long generation;

    @Override
    public NativeState snapshot() {
      return state;
    }

    @Override
    public void select(Selection selection) {
      state =
          new NativeState(
              Optional.of(selection.packName()),
              selection.enabled(),
              Map.of(),
              selection.options());
    }

    @Override
    public void reload() {
      generation++;
    }

    @Override
    public Activation observe() {
      return new Activation(
          state.packName().orElseThrow(),
          digest,
          generation,
          !failLink,
          state.effectiveOptions(),
          submitted ? Set.of("observed", "submitted-frame:" + digest) : Set.of("observed"));
    }

    @Override
    public void restore(NativeState original) {
      state = original;
    }
  }

  @Test
  void transactionActivationAndOffRestoreOriginalNativeQueue() throws IOException {
    var backend = new Backend();
    var original = backend.snapshot();
    var transaction =
        SelectionTransaction.prepare(
            backend,
            new Selection("native.zip", true, Map.of("AA", "native"), List.of()),
            ZERO,
            Set.of("observed"),
            0);
    assertEquals(SelectionTransaction.State.PREPARED, transaction.state());
    assertThrows(IllegalStateException.class, transaction::activation);
    assertEquals(1, transaction.apply().generation());
    assertEquals(SelectionTransaction.State.LINKED, transaction.state());
    assertThrows(IOException.class, transaction::confirmRendered);
    assertThrows(IllegalStateException.class, transaction::activation);
    backend.submitted = true;
    transaction.confirmRendered();
    assertEquals(SelectionTransaction.State.ACTIVE, transaction.state());
    transaction.off();
    assertEquals(original, backend.snapshot());
    assertEquals(SelectionTransaction.State.RESTORED, transaction.state());
    transaction.off();
  }

  @Test
  void failedLinkAndWrongSourceRestoreWithoutPositiveWitness() throws IOException {
    var backend = new Backend();
    var original = backend.snapshot();
    backend.failLink = true;
    var transaction =
        SelectionTransaction.prepare(
            backend,
            new Selection("native.zip", true, Map.of(), List.of()),
            ZERO,
            Set.of("observed"));
    assertThrows(IOException.class, transaction::apply);
    assertEquals(original, backend.snapshot());
    assertEquals(SelectionTransaction.State.RESTORED, transaction.state());
    assertThrows(IllegalStateException.class, transaction::activation);
    backend.failLink = false;
    backend.digest = ONE;
    var wrong =
        SelectionTransaction.prepare(
            backend,
            new Selection("native.zip", true, Map.of(), List.of()),
            ZERO,
            Set.of("observed"));
    assertThrows(IOException.class, wrong::apply);
    assertEquals(original, backend.snapshot());
  }
}

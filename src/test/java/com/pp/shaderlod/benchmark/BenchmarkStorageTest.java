package com.pp.shaderlod.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkStorageTest {
    @TempDir Path saves;

    @Test void interruptedOwnedBuildResumesAndCompletedCacheReopens() throws Exception {
        var initial = BenchmarkStorage.prepare(saves);
        assertFalse(initial.complete());
        assertFalse(initial.hasLevel());
        assertEquals(initial.directory(), BenchmarkStorage.prepare(saves).directory());
        Files.writeString(initial.directory().resolve("level.dat"), "engine-owned-save-placeholder");
        initial.markComplete();
        var reopened = BenchmarkStorage.prepare(saves);
        assertTrue(reopened.complete());
        assertTrue(reopened.hasLevel());
        assertEquals("engine-owned-save-placeholder", Files.readString(reopened.directory().resolve("level.dat")));
    }

    @Test void occupiedUnmarkedSaveIsNeverAdoptedOrChanged() throws Exception {
        Path personal = Files.createDirectory(saves.resolve(BenchmarkStorage.WORLD));
        Files.writeString(personal.resolve("level.dat"), "personal");
        assertThrows(IOException.class, () -> BenchmarkStorage.prepare(saves));
        assertEquals("personal", Files.readString(personal.resolve("level.dat")));
        try (var entries = Files.list(personal)) { assertEquals(1, entries.count()); }
    }

    @Test void symlinkedSaveAndReplacedOwnershipAreRefused() throws Exception {
        Path outside = Files.createDirectory(saves.resolve("personal"));
        Files.createSymbolicLink(saves.resolve(BenchmarkStorage.WORLD), outside);
        assertThrows(IOException.class, () -> BenchmarkStorage.prepare(saves));
        Files.delete(saves.resolve(BenchmarkStorage.WORLD));
        var owned = BenchmarkStorage.prepare(saves);
        Files.writeString(owned.directory().resolve(".shaderlod-benchmark-owner"), "foreign");
        assertThrows(IOException.class, owned::markComplete);
        assertThrows(IOException.class, () -> BenchmarkStorage.prepare(saves));
        assertEquals("foreign", Files.readString(owned.directory().resolve(".shaderlod-benchmark-owner")));
    }

    @Test void newSceneVersionPreservesEarlierCachedWorld() throws Exception {
        Path earlier = Files.createDirectory(saves.resolve("ShaderLOD-Benchmark-overworld-1"));
        Files.writeString(earlier.resolve("level.dat"), "earlier-version-preserved");
        var current = BenchmarkStorage.prepare(saves);
        assertEquals("ShaderLOD-Benchmark-overworld-3-flat", current.directory().getFileName().toString());
        assertNotEquals(earlier, current.directory());
        assertEquals("earlier-version-preserved", Files.readString(earlier.resolve("level.dat")));
    }
}

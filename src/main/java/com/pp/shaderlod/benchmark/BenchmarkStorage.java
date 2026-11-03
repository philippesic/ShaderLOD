package com.pp.shaderlod.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Ownership guard, not a replacement for Minecraft's LevelStorageAccess/session lock. */
final class BenchmarkStorage {
    static final String WORLD = "ShaderLOD-Benchmark-" + BenchmarkScene.VERSION;
    private static final String MARKER = ".shaderlod-benchmark-owner";
    private static final String ID = "ShaderLOD private benchmark\n" + BenchmarkScene.VERSION + "\n"
            + BenchmarkScene.recipeHash() + "\n";
    private final Path directory;
    private boolean complete;

    private BenchmarkStorage(Path directory, boolean complete) {
        this.directory = directory;
        this.complete = complete;
    }

    static BenchmarkStorage prepare(Path saves) throws IOException {
        Path root = saves.toRealPath();
        Path directory = root.resolve(WORLD);
        Path marker = directory.resolve(MARKER);
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(marker) || Files.isSymbolicLink(directory.resolve("level.dat"))
                    || !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Benchmark save name is occupied without ShaderLOD ownership: " + directory);
            String value = Files.readString(marker);
            if (!value.equals(ID + "building\n") && !value.equals(ID + "complete\n"))
                throw new IOException("Benchmark ownership/version mismatch; refusing to modify " + directory);
            boolean complete = value.equals(ID + "complete\n");
            if (complete && !Files.isRegularFile(directory.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Owned benchmark has no level.dat; preserve it for recovery: " + directory);
            return new BenchmarkStorage(directory, complete);
        }
        Files.createDirectory(directory);
        Files.writeString(marker, ID + "building\n", StandardOpenOption.CREATE_NEW);
        return new BenchmarkStorage(directory, false);
    }

    boolean complete() { return complete; }
    boolean hasLevel() { return Files.isRegularFile(directory.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS); }
    Path directory() { return directory; }

    void markComplete() throws IOException {
        Path marker = directory.resolve(MARKER);
        if (Files.isSymbolicLink(directory) || Files.isSymbolicLink(marker)
                || !Files.readString(marker).equals(ID + "building\n"))
            throw new IOException("Benchmark ownership changed during construction");
        Path temporary = Files.createTempFile(directory, MARKER + ".", ".tmp");
        try {
            Files.writeString(temporary, ID + "complete\n", StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(temporary, marker, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        complete = true;
    }
}

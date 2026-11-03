package com.pp.shaderlod.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.TreeSet;

/** Complete bounded settings bytes at workload boundaries; no interpretation or exclusions. */
final class ConfigurationFilesEvidence {
    private ConfigurationFilesEvidence() {}

    static JsonObject capture(Path profile) {
        var result = new JsonObject();
        var files = new JsonObject();
        var errors = new JsonArray();
        result.add("files", files);
        result.add("errors", errors);
        result.addProperty("limits", "options.txt, all config files and shader option sidecars; 128 files / 1 MiB; unknown formats retain exact bytes hash");
        try {
            var paths = new TreeSet<Path>();
            paths.add(profile.resolve("options.txt"));
            Path config = profile.resolve("config");
            if (!Files.isDirectory(config) || Files.isSymbolicLink(config)) throw new IllegalStateException("Missing or symlink configuration root");
            try (var entries = Files.walk(config)) {
                var iterator = entries.iterator();
                int visited = 0;
                while (iterator.hasNext()) {
                    Path path = iterator.next();
                    if (++visited > 512) throw new IllegalStateException("Configuration tree exceeds complete traversal limit");
                    if (Files.isSymbolicLink(path)) throw new IllegalStateException("Symlink configuration entry: " + profile.relativize(path));
                    if (Files.isRegularFile(path)) paths.add(path);
                    if (paths.size() > 128) throw new IllegalStateException("Configuration file count exceeds complete capture limit");
                }
            }
            Path packs = profile.resolve("shaderpacks");
            if (!Files.isDirectory(packs) || Files.isSymbolicLink(packs)) throw new IllegalStateException("Missing or symlink shaderpack root");
            try (var entries = Files.list(packs)) {
                var iterator = entries.iterator();
                int visited = 0;
                while (iterator.hasNext()) {
                    Path path = iterator.next();
                    if (++visited > 512) throw new IllegalStateException("Shaderpack directory exceeds complete traversal limit");
                    if (path.getFileName().toString().endsWith(".txt")) paths.add(path);
                    if (paths.size() > 128) throw new IllegalStateException("Configuration file count exceeds complete capture limit");
                }
            }
            if (paths.size() > 128) throw new IllegalStateException("Configuration file count exceeds complete capture limit");
            long total = 0;
            for (Path path : paths) {
                if (Files.isSymbolicLink(path) || !Files.isRegularFile(path)) throw new IllegalStateException("Unknown configuration file: " + profile.relativize(path));
                long size = Files.size(path);
                if (size > 1024 * 1024 || (total += size) > 1024 * 1024) throw new IllegalStateException("Configuration bytes exceed complete capture limit");
                byte[] bytes = Files.readAllBytes(path);
                if (bytes.length != size) throw new IllegalStateException("Configuration changed during capture: " + profile.relativize(path));
                var item = new JsonObject();
                item.addProperty("bytes", bytes.length);
                item.addProperty("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
                try { item.addProperty("utf8", StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()); }
                catch (java.nio.charset.CharacterCodingException binary) { item.addProperty("encoding", "binary; exact hash only"); }
                files.add(profile.relativize(path).toString().replace('\\', '/'), item);
            }
        } catch (Exception failure) {
            errors.add(failure.toString());
        }
        result.addProperty("complete", errors.isEmpty());
        return result;
    }
}

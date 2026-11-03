package com.pp.shaderlod.fabric.dev;

import com.pp.shaderlod.integration.bslquad.BslQuadEligibility;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Named BSL certificates and dev-mode admission; no arbitrary map or source rewrite. */
final class FabricOriginalSourceChecks {
    private FabricOriginalSourceChecks() {}
    static boolean flagsAllow(String family, boolean speed, boolean roundtripSource, boolean dh,
            boolean ui, boolean candidate, boolean research) {
        return "bsl".equals(family) && !speed && !roundtripSource && !dh && !ui && !candidate && !research;
    }
    static boolean optionsMatch(String family, Map<String,String> actual) {
        return "bsl".equals(family) && BslQuadEligibility.optionsMatch(actual);
    }
    static boolean rendererFlagsAllow(String family, boolean speed, boolean source, boolean originalOnly,
            boolean dh, boolean ui, boolean candidate, boolean research) {
        return "bsl".equals(family) && speed != source && !originalOnly && !dh && !ui && candidate && research;
    }
    static boolean sourceMatches(String family, Path archive, Map<String,String> actual) {
        return "bsl".equals(family) && BslQuadEligibility.validate(archive, actual).eligible();
    }
    static void requireFreshExportDirectory(Path profile) throws Exception {
        Path exports = profile.toAbsolutePath().normalize().resolve("patched_shaders");
        if (Files.isSymbolicLink(exports)) throw new IllegalStateException("Source export directory must not be a symlink");
        if (Files.exists(exports)) try (var files = Files.list(exports)) {
            if (files.findAny().isPresent()) throw new IllegalStateException("Use fresh empty original source exports before launch");
        }
    }
    static Map<String,String> terrainExports(Path profile, long startedMillis) throws Exception {
        Path root = profile.toAbsolutePath().normalize().resolve("patched_shaders");
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) throw new IllegalStateException("Compiler source export directory absent");
        var result = new TreeMap<String,String>(); var pairs = new TreeMap<String,Set<String>>();
        try (var files = Files.list(root)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (!name.matches("[0-9]{3}_sodium_gbuffers_terrain(?:_solid|_cutout)?\\.(?:vsh|fsh)")) continue;
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)
                        || Files.getLastModifiedTime(file).toMillis() < startedMillis - 1000
                        || Files.size(file) == 0 || Files.size(file) > 1024 * 1024)
                    throw new IllegalStateException("Stale/invalid terrain compiler export: " + file);
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
                result.put(file.toAbsolutePath().toString(), hash);
                int dot = name.lastIndexOf('.');
                pairs.computeIfAbsent(name.substring(0, dot), key -> new HashSet<>()).add(name.substring(dot + 1));
            }
        }
        if (result.size() != 4 || pairs.size() != 2 || pairs.values().stream().anyMatch(p -> !p.equals(Set.of("vsh", "fsh"))))
            throw new IllegalStateException("Expected two fresh BSL opaque/cutout terrain compiler pairs, found " + result.size());
        return Map.copyOf(result);
    }
}

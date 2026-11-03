package com.pp.shaderlod;

import com.pp.shaderlod.packs.SupportedPackCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Optional;
import java.util.zip.ZipFile;
import net.irisshaders.iris.Iris;

/** Source inspection happens when opening settings, never in a render-frame callback. */
public final class ShaderLodPackDetection {
    private ShaderLodPackDetection() {}

    /** Development label only: admission never promotes this source to supported catalog status. */
    public static boolean privateRasterExperiment() {
        if (!Boolean.getBoolean(com.pp.shaderlod.integration.RasterExperimentSelection.OPT_IN)) return false;
        try {
            var backend = new com.pp.shaderlod.integration.RasterExperimentSelection.Backend() {
                public String selected() { return Iris.getIrisConfig().getShaderPackName().orElse(null); }
                public java.util.Map<String, String> queue() { return java.util.Map.of(); }
                public void select(String name) { throw new UnsupportedOperationException(); }
                public void save() { throw new UnsupportedOperationException(); }
            };
            return com.pp.shaderlod.integration.RasterExperimentSelection.open(Iris.getShaderpacksDirectory(),
                    backend, true, com.pp.shaderlod.integration.RasterExperimentRuntime.current()) != null;
        } catch (Exception | LinkageError unavailable) { return false; }
    }

    public static Optional<SupportedPackCatalog.Recognition> current() {
        var selected = Iris.getIrisConfig().getShaderPackName();
        if (selected.isEmpty()) return Optional.empty();
        Path directory = Iris.getShaderpacksDirectory().toAbsolutePath().normalize();
        Path pack = directory.resolve(selected.get()).normalize();
        if (!pack.startsWith(directory) || !Files.isRegularFile(pack)) return Optional.empty();
        try (var archive = new ZipFile(pack.toFile())) {
            var hashes = new HashMap<String, String>();
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory() || !SupportedPackCatalog.isSourceMember(entry.getName())) continue;
                try (var input = archive.getInputStream(entry)) {
                    var digest = MessageDigest.getInstance("SHA-256");
                    byte[] buffer = new byte[8192];
                    for (int read; (read = input.read(buffer)) != -1;) digest.update(buffer, 0, read);
                    if (hashes.put(entry.getName(), HexFormat.of().formatHex(digest.digest())) != null)
                        return Optional.empty();
                }
            }
            return SupportedPackCatalog.recognize(hashes);
        } catch (Exception unavailable) {
            ShaderLod.LOGGER.debug("Unable to identify selected ShaderLOD shader source {}", selected.get(), unavailable);
            return Optional.empty();
        }
    }
}

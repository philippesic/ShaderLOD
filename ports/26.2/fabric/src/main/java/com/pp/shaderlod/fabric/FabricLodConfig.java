package com.pp.shaderlod.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pp.shaderlod.CoreUniformFrame;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.LodSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fabric persistence adapter; immutable snapshots use the shared core contract. */
public final class FabricLodConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("shaderlod");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public record Values(boolean enabled, LodPreset preset, double near, double far,
                         double transition, boolean debug) {
        public Values {
            if (preset == null) throw new IllegalArgumentException("Missing preset");
            new LodSettings(enabled, near, far, transition, debug);
        }
        public CoreUniformFrame.Core core() {
            return new CoreUniformFrame.Core(new LodSettings(enabled, near, far, transition, debug), preset);
        }
    }
    private static volatile Values values = defaults();
    private static Path path;
    private static boolean backedUpThisLoad;

    private FabricLodConfig() {}
    public static Values defaults() { return new Values(true, LodPreset.PERFORMANCE, 32, 96, 8, false); }
    public static Values snapshot() { return values; }
    public static boolean loaded() { return path != null; }

    public static synchronized void load(Path configPath) {
        path = configPath;
        backedUpThisLoad = false;
        if (!Files.exists(path)) { values = defaults(); save(); return; }
        try (var reader = Files.newBufferedReader(path)) {
            var loaded = GSON.fromJson(reader, Values.class);
            if (loaded == null) throw new IllegalArgumentException("Empty config");
            values = loaded;
        } catch (IOException | RuntimeException malformed) {
            values = defaults();
            // Preserve malformed/personal config until the user explicitly edits settings.
            LOGGER.warn("Invalid ShaderLOD Fabric config {}; using defaults", path, malformed);
        }
    }

    public static synchronized void setEnabled(boolean enabled) {
        var v = values;
        update(new Values(enabled, v.preset(), v.near(), v.far(), v.transition(), v.debug()));
    }
    public static synchronized void setPreset(LodPreset preset) {
        var v = values;
        update(new Values(v.enabled(), preset, v.near(), v.far(), v.transition(), v.debug()));
    }
    public static synchronized void setDebug(boolean debug) {
        var v = values;
        update(new Values(v.enabled(), v.preset(), v.near(), v.far(), v.transition(), debug));
    }
    private static void update(Values updated) { values = updated; save(); }
    static synchronized boolean replace(Values updated) { values = updated; return save(); }
    private static boolean save() {
        if (path == null) return true;
        var temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            if (!backedUpThisLoad && Files.exists(path)) {
                Path backup = path.resolveSibling(path.getFileName() + ".bak");
                int suffix = 1;
                while (Files.exists(backup)) backup = path.resolveSibling(path.getFileName() + ".bak." + suffix++);
                Files.copy(path, backup);
                backedUpThisLoad = true;
            }
            Files.writeString(temporary, GSON.toJson(values) + "\n");
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException failure) {
            LOGGER.error("Could not save ShaderLOD Fabric settings {}", path, failure);
            return false;
        }
    }
}

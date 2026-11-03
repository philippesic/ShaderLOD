package com.pp.shaderlod.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import net.neoforged.fml.ModList;

/**
 * Dev-only ModList artifact evidence, independent of the ShaderLOD production mod.
 * Disk/root identities do not prove which classes were loaded or transformed.
 * Capture outside a timed measurement window. Failed observations remain in JSON.
 */
public final class LoadedArtifactEvidence {
    private static final String DIRECTORY_KIND = "development directory; regular files only, symlinks excluded";
    private static final String DIRECTORY_LIMIT = "5000 files / 32 MiB; no partial tree hash advertised";
    private static final long MAX_DIRECTORY_BYTES = 32L * 1024 * 1024;
    private LoadedArtifactEvidence() {}

    /** Captures actual pinned NeoForge ModList roots; safe when the production mod is absent. */
    public static JsonObject capture() {
        var mods = new JsonObject();
        var roots = new ArrayList<JsonObject>();
        var captureErrors = new JsonArray();
        try {
            var loaded = ModList.get();
            for (var mod : loaded.getMods()) {
                if (mods.has(mod.getModId())) captureErrors.add("Duplicate loaded registry mod: " + mod.getModId());
                mods.addProperty(mod.getModId(), mod.getVersion().toString());
            }
            for (var modFile : loaded.getModFiles()) {
                var rootMods = new JsonObject();
                for (var mod : modFile.getMods()) {
                    if (rootMods.has(mod.getModId())) captureErrors.add("Duplicate mod within artifact: " + mod.getModId());
                    rootMods.addProperty(mod.getModId(), mod.getVersion().toString());
                }
                try {
                    roots.add(observe(modFile.getFile().getFilePath(), rootMods));
                } catch (RuntimeException failure) {
                    var item = observe(null, rootMods);
                    item.addProperty("locator_error", failure.toString());
                    roots.add(item);
                }
            }
        } catch (RuntimeException failure) {
            captureErrors.add("ModList capture failed: " + failure);
        }
        var result = snapshot(mods, roots);
        if (!captureErrors.isEmpty()) {
            result.getAsJsonArray("errors").addAll(captureErrors);
            result.addProperty("complete_mod_coverage", false);
            result.addProperty("valid", false);
        }
        return result;
    }

    /** Untimed original resource bytes/locations, never transformed VM bytecode. */
    public static JsonObject shadowOriginalResources() {
        var result = new JsonObject();
        for (String name : List.of(
                "net.irisshaders.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum",
                "net.irisshaders.iris.shadows.frustum.advanced.ReversedAdvancedShadowCullingFrustum",
                "net.irisshaders.iris.shadows.frustum.BoxCuller")) {
            var evidence = new JsonObject();
            try {
                var type = Class.forName(name, false, LoadedArtifactEvidence.class.getClassLoader());
                evidence.addProperty("actual_type", type.getName());
                var resource = type.getResource("/" + name.replace('.', '/') + ".class");
                var code = type.getProtectionDomain().getCodeSource();
                evidence.addProperty("resource_url", resource == null ? null : resource.toExternalForm());
                evidence.addProperty("code_source_location", code == null ? null : code.getLocation().toExternalForm());
                if (resource == null) throw new IllegalStateException("Original class resource unavailable");
                try (var stream = resource.openStream()) {
                    byte[] bytes = stream.readNBytes(1024 * 1024 + 1);
                    if (bytes.length == 0 || bytes.length > 1024 * 1024)
                        throw new IllegalStateException("Original class resource outside size limit");
                    evidence.addProperty("sha256", HexFormat.of().formatHex(
                            MessageDigest.getInstance("SHA-256").digest(bytes)));
                }
            } catch (Exception | LinkageError failure) { evidence.addProperty("error", failure.toString()); }
            result.add(name, evidence);
        }
        return result;
    }

    /** Independently observes one root. No lookup in a legacy path-keyed map. */
    public static JsonObject observe(Path root, JsonObject mods) {
        var result = new JsonObject();
        result.add("mods", mods == null ? JsonNull.INSTANCE : mods.deepCopy());
        result.add("path", JsonNull.INSTANCE);
        result.add("filesystem_provider", JsonNull.INSTANCE);
        result.add("uri", JsonNull.INSTANCE);
        result.add("identity", JsonNull.INSTANCE);
        if (root == null) {
            result.addProperty("locator_error", "Artifact root is null");
            result.addProperty("identity_error", "Artifact root is null");
            return result;
        }
        try {
            result.addProperty("path", root.toAbsolutePath().toString());
            result.addProperty("filesystem_provider", root.getFileSystem().provider().getScheme());
            result.addProperty("uri", root.toUri().toString());
        } catch (RuntimeException failure) {
            result.addProperty("locator_error", failure.toString());
        }
        try {
            if (Files.isRegularFile(root)) result.addProperty("identity", fileSha256(root));
            else if (Files.isDirectory(root)) result.add("identity", directoryIdentity(root));
            else result.addProperty("identity_error", "Artifact root is neither a regular file nor a directory");
        } catch (Exception failure) {
            result.addProperty("identity_error", failure.toString());
        }
        return result;
    }

    /**
     * Assembles detached observations. Same provider/URI, path and complete identity
     * may coalesce only with disjoint mod IDs. Unknown/conflicting entries are retained.
     * The expected registry is checked by exact mod ID AND version, not just count.
     */
    public static JsonObject snapshot(JsonObject expectedMods, List<JsonObject> observations) {
        var result = new JsonObject();
        var inventory = new JsonArray();
        var errors = new JsonArray();
        var observedMods = new JsonObject();
        var locators = new HashMap<String, JsonObject>();
        var conflictedLocators = new HashSet<String>();
        boolean identitiesComplete = true;
        boolean uniqueMods = true;
        result.addProperty("schema", 1);
        result.add("loaded_mods", expectedMods == null ? JsonNull.INSTANCE : expectedMods.deepCopy());
        result.add("loaded_artifact_inventory", inventory);
        result.add("errors", errors);
        result.addProperty("limits", "Artifact bytes/root identities at capture time; not loaded/transformed class or shader execution proof");
        if (!validMods(expectedMods)) errors.add("Loaded mod registry missing, empty or malformed");
        if (observations == null) {
            identitiesComplete = false;
            errors.add("Artifact observations missing");
        }
        else for (int index = 0; index < observations.size(); index++) {
            JsonObject source = observations.get(index);
            if (source == null) {
                inventory.add(JsonNull.INSTANCE);
                identitiesComplete = false;
                uniqueMods = false;
                errors.add("Null artifact observation at index " + index);
                continue;
            }
            var item = source.deepCopy();
            boolean itemValid = true;
            JsonObject mods = item.has("mods") && item.get("mods").isJsonObject() ? item.getAsJsonObject("mods") : null;
            if (!validMods(mods)) {
                errors.add("Missing, empty or malformed artifact mods at index " + index);
                uniqueMods = false;
                itemValid = false;
            } else for (var mod : mods.entrySet()) {
                if (observedMods.has(mod.getKey())) {
                    errors.add("Duplicate artifact mod: " + mod.getKey());
                    uniqueMods = false;
                    itemValid = false;
                } else observedMods.add(mod.getKey(), mod.getValue().deepCopy());
            }
            boolean completeIdentity = completeIdentity(item.get("identity"));
            if (!completeIdentity || item.has("identity_error")) {
                identitiesComplete = false;
                itemValid = false;
                errors.add("Unknown, incomplete or failed artifact identity at index " + index);
            }
            String provider = string(item, "filesystem_provider");
            String uri = string(item, "uri");
            String path = string(item, "path");
            // Empty absolute paths are legitimate for distinct virtual filesystem roots.
            if (provider == null || provider.isBlank() || uri == null || uri.isBlank() || path == null || item.has("locator_error")) {
                itemValid = false;
                errors.add("Unknown or failed artifact locator at index " + index);
                inventory.add(item);
                continue;
            }
            String locator = provider + "\n" + uri;
            var previous = locators.get(locator);
            if (previous == null) {
                locators.put(locator, item);
                if (!itemValid) conflictedLocators.add(locator);
                inventory.add(item);
            } else if (!itemValid || conflictedLocators.contains(locator)
                    || !completeIdentity(previous.get("identity")) || previous.has("identity_error")
                    || !previous.get("path").equals(item.get("path"))
                    || !previous.get("identity").equals(item.get("identity"))) {
                errors.add("Conflicting or invalid artifact records at locator: " + locator);
                conflictedLocators.add(locator);
                inventory.add(item);
            } else {
                for (var mod : mods.entrySet()) previous.getAsJsonObject("mods").add(mod.getKey(), mod.getValue().deepCopy());
            }
        }
        boolean coverage = uniqueMods && validMods(expectedMods) && expectedMods.equals(observedMods);
        if (!coverage) errors.add("Artifact mods do not exactly cover loaded mod registry and versions");
        result.addProperty("complete_mod_coverage", coverage);
        result.addProperty("complete_identities", identitiesComplete);
        result.addProperty("valid", errors.isEmpty() && coverage && identitiesComplete);
        return result;
    }

    /** Caller writes the snapshot before invoking this guard, preserving failures. */
    public static void requireValid(JsonObject snapshot) {
        if (snapshot == null || !snapshot.has("valid") || !snapshot.get("valid").isJsonPrimitive()
                || !snapshot.getAsJsonPrimitive("valid").isBoolean() || !snapshot.get("valid").getAsBoolean()) {
            throw new IllegalStateException("Invalid loaded artifact evidence: " + (snapshot == null ? "null" : snapshot.get("errors")));
        }
    }

    public static String fileSha256(Path file) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (InputStream stream = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            for (int length; (length = stream.read(buffer)) != -1;) digest.update(buffer, 0, length);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Exact BenchmarkSession schema and natural Path sort; no partial tree hash. */
    public static JsonObject directoryIdentity(Path root) throws Exception {
        var result = new JsonObject();
        result.addProperty("kind", DIRECTORY_KIND);
        var digest = MessageDigest.getInstance("SHA-256");
        int count = 0;
        long bytes = 0;
        try (var paths = Files.walk(root)) {
            var iterator = paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).sorted().iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                bytes += Files.size(path);
                if (++count > 5000 || bytes > MAX_DIRECTORY_BYTES) {
                    result.addProperty("complete", false);
                    result.addProperty("limit", DIRECTORY_LIMIT);
                    return result;
                }
                String entry = root.relativize(path).toString().replace('\\', '/') + "\0" + fileSha256(path) + "\n";
                digest.update(entry.getBytes(StandardCharsets.UTF_8));
            }
        }
        result.addProperty("complete", true);
        result.addProperty("files", count);
        result.addProperty("bytes", bytes);
        result.addProperty("tree_sha256", HexFormat.of().formatHex(digest.digest()));
        return result;
    }

    private static boolean validMods(JsonObject mods) {
        if (mods == null || mods.isEmpty()) return false;
        for (var entry : mods.entrySet()) {
            if (entry.getKey().isBlank() || !entry.getValue().isJsonPrimitive()
                    || !entry.getValue().getAsJsonPrimitive().isString() || entry.getValue().getAsString().isBlank()) return false;
        }
        return true;
    }

    private static boolean completeIdentity(JsonElement identity) {
        if (identity == null || identity.isJsonNull()) return false;
        if (identity.isJsonPrimitive()) return identity.getAsJsonPrimitive().isString() && sha256(identity.getAsString());
        if (!identity.isJsonObject()) return false;
        var directory = identity.getAsJsonObject();
        if (!DIRECTORY_KIND.equals(string(directory, "kind")) || !sha256(string(directory, "tree_sha256"))
                || !directory.has("complete") || !directory.get("complete").isJsonPrimitive()
                || !directory.getAsJsonPrimitive("complete").isBoolean() || !directory.get("complete").getAsBoolean()) return false;
        return boundedInteger(directory.get("files"), 5000) && boundedInteger(directory.get("bytes"), MAX_DIRECTORY_BYTES);
    }

    private static boolean boundedInteger(JsonElement value, long maximum) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return false;
        try {
            long number = value.getAsBigDecimal().longValueExact();
            return number >= 0 && number <= maximum;
        } catch (ArithmeticException | NumberFormatException invalid) {
            return false;
        }
    }

    private static String string(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }

    private static boolean sha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }
}

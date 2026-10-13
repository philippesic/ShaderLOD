package com.pp.shaderlod.packs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pp.shaderlod.LodPreset;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Explicit Apply-only local pack generation. No Iris, Minecraft, downloads or watching. */
public final class PackSelectionService {
    public enum Status { APPLIED, UNCHANGED, UNSUPPORTED, REFUSED }
    public record Result(Status status, String packFile, String adapterId,
            boolean generatedArchive, boolean optionsChanged, String detail) {
        public boolean accepted() { return status == Status.APPLIED || status == Status.UNCHANGED; }
        public boolean requiresReload() { return status == Status.APPLIED; }
    }
    /** Private default-off original-graph route; existing Photon effect routing is preserved. */
    public static final String PHOTON_QUAD_ORIGINAL_GRAPH = "shaderlod.photonQuadOriginalGraphR1";
    private static final String RESOURCES = "/assets/shaderlod/adapters/";
    private static final String MARKER = "shaderlod-selection.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> RECIPES = Set.of("photon", "complementary-reimagined",
        "complementary-unbound", "makeup-ultra-fast-shaders", "bsl-shaders", "solas-shader",
        "astralex", "bliss-shader", "sildurs-vibrant-shaders");
    private static final long LIMIT = 256L * 1024 * 1024;
    private static final Map<String, String> PHOTON_DEFAULTS = Map.of(
        "TAA", "true", "TAAU", "false", "TAAU_RENDER_SCALE", "0.75", "VOXY", "false");

    /** Supply actual selected Iris values, including unsaved options, when applying an original. */
    public synchronized Result apply(Path directory, String selected, boolean enabled,
            Map<String, String> effectiveSelectedOptions) {
        return apply(directory, selected, enabled, LodPreset.PERFORMANCE, effectiveSelectedOptions);
    }
    public synchronized Result apply(Path directory, String selected, boolean enabled, LodPreset preset,
            Map<String, String> effectiveSelectedOptions) {
        return apply(directory, selected, enabled, preset, effectiveSelectedOptions, false);
    }
    /** Renderer availability must describe the selected original before Apply suspends rendering. */
    public synchronized Result apply(Path directory, String selected, boolean enabled, LodPreset preset,
            Map<String, String> effectiveSelectedOptions, boolean nativeRendererAvailable) {
        return apply(directory, selected, enabled, preset, effectiveSelectedOptions,
                nativeRendererAvailable, Map.of(), Set.of());
    }
    /** Only actual declared defaults may be omitted; queued selections remain explicit. */
    public synchronized Result apply(Path directory, String selected, boolean enabled, LodPreset preset,
            Map<String, String> effectiveSelectedOptions, boolean nativeRendererAvailable,
            Map<String, String> declaredDefaults, Set<String> explicitOptionKeys) {
        try {
            java.util.Objects.requireNonNull(preset, "preset");
            return applyChecked(directory.toAbsolutePath().normalize(), selected, enabled, preset,
                effectiveSelectedOptions == null ? Map.of() : Map.copyOf(effectiveSelectedOptions),
                nativeRendererAvailable, Map.copyOf(declaredDefaults), Set.copyOf(explicitOptionKeys));
        } catch (IOException | RuntimeException exception) {
            return new Result(Status.REFUSED, selected, null, false, false, exception.getMessage());
        }
    }
    public Result apply(Path directory, String selected, boolean enabled) {
        return apply(directory, selected, enabled, Map.of());
    }

    private static boolean nativeRendererGraph(String adapterId, LodPreset preset, boolean available) {
        return preset == LodPreset.PERFORMANCE && available && RECIPES.contains(adapterId);
    }

    private static boolean nativeQualityGraph(String adapterId, LodPreset preset) {
        return preset == LodPreset.QUALITY && RECIPES.contains(adapterId);
    }

    private Result applyChecked(Path dir, String selected, boolean enabled, LodPreset preset,
            Map<String, String> effective, boolean nativeRendererAvailable,
            Map<String, String> declaredDefaults, Set<String> explicitOptionKeys) throws IOException {
        Path selectedPath = child(dir, selected);
        Map<String, byte[]> selectedMembers = archive(selectedPath);
        State state = null;
        boolean generatedSelected = selectedMembers.containsKey(MARKER);
        if (generatedSelected) {
            JsonObject marker = JsonParser.parseString(text(selectedMembers.get(MARKER))).getAsJsonObject();
            state = readState(child(dir, string(marker, "stateFile")));
            if (!selected.equals(state.generatedFile)) throw new IOException("Generated selection identity mismatch");
            verifyState(dir, state);
        } else {
            // Older manual adapters carry no reversible selection state. Never adapt an adapter.
            Map<String, String> hashes = hashes(selectedMembers);
            var recognized = SupportedPackCatalog.recognize(hashes);
            if (recognized.isEmpty()) return unsupported(selected);
            if (recognized.get().stage() != SupportedPackCatalog.Stage.RECOGNIZED_ORIGINAL)
                throw new IOException("Manual adapter has no known original selection; choose the original pack");
            String id = recognized.get().pack().id();
            if (!RECIPES.contains(id)) return unsupported(selected);
            Recipe recipe = recipe(id);
            recipe.validate(selectedMembers);
            String sourceHash = hash(Files.readAllBytes(selectedPath));
            String stateFile = stateFile(selected, sourceHash, recipe.digest);
            Path statePath = child(dir, stateFile);
            if (Files.exists(statePath, LinkOption.NOFOLLOW_LINKS)) {
                state = readState(statePath);
                if (!state.originalFile.equals(selected) || !state.originalSha256.equals(sourceHash))
                    throw new IOException("Original selection state mismatch");
                verifyState(dir, state);
            }
            if ((!enabled || nativeQualityGraph(id, preset) || nativeRendererGraph(id, preset, nativeRendererAvailable)) && (state == null || !state.active)) {
                if (enabled && !nativeRendererGraph(id, preset, nativeRendererAvailable)) {
                    var nativeValues = new TreeMap<>(options(optionalBytes(child(dir, selected + ".txt"))));
                    nativeValues.putAll(effective);
                    overrides(recipe, nativeValues); // Preserve existing eligibility before bypass.
                }
                return new Result(Status.UNCHANGED, selected, id, false, false, "Original pack retained");
            }
            if (state == null) {
                state = new State(); state.schema = 1; state.adapterId = id;
                state.originalFile = selected; state.originalSha256 = sourceHash;
                state.recipeSha256 = recipe.digest; state.stateFile = stateFile;
                state.generatedFile = "ShaderLOD-" + id + "-" + hash((selected + sourceHash + recipe.digest)
                    .getBytes(StandardCharsets.UTF_8)).substring(0, 16) + ".zip";
                Path target = child(dir, state.generatedFile);
                Path targetOptions = child(dir, state.generatedFile + ".txt");
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                        || Files.exists(targetOptions, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Generated pack or options filename collision");
            }
        }
        Recipe recipe = recipe(state.adapterId);
        verifyRecipeState(recipe, state);
        Path original = child(dir, state.originalFile);
        Path originalOptions = child(dir, state.originalFile + ".txt");
        Path adapter = child(dir, state.generatedFile);
        Path adapterOptions = child(dir, state.generatedFile + ".txt");
        Path statePath = child(dir, state.stateFile);
        byte[] originalBytes = optionalBytes(originalOptions);
        Map<String, String> originalValues = options(originalBytes);
        Map<String, String> selectedValues = new TreeMap<>(originalValues);
        if (state.active) {
            selectedValues = merge(state, originalValues, options(optionalBytes(adapterOptions)));
        }
        // An explicit original selection supplies its currently effective options; never overlay
        // generated TAAU overrides onto the recorded original state on restart.
        if (!generatedSelected) for (var entry : effective.entrySet()) {
            String key = entry.getKey(), value = entry.getValue();
            // Existing sidecar keys and explicit pending edits are user choices. An absent key
            // equal to Iris's declared default is implicit, not an option edit to serialize.
            if (originalValues.containsKey(key) || explicitOptionKeys.contains(key)
                    || !value.equals(declaredDefaults.get(key))) selectedValues.put(key, value);
        }
        if (enabled && nativeQualityGraph(state.adapterId, preset) && !nativeRendererGraph(state.adapterId, preset, nativeRendererAvailable))
            overrides(recipe, selectedValues); // Validate eligibility without applying owned overrides.
        if (!enabled || nativeQualityGraph(state.adapterId, preset) || nativeRendererGraph(state.adapterId, preset, nativeRendererAvailable)) {
            boolean changed = !originalValues.equals(selectedValues);
            if (changed) writeAtomic(originalOptions, renderOptions(originalBytes, selectedValues));
            state.active = false;
            writeAtomic(statePath, GSON.toJson(state).getBytes(StandardCharsets.UTF_8));
            return new Result(Status.APPLIED, state.originalFile, state.adapterId, false, changed,
                "Original graph restored; non-owned option edits retained");
        }
        // Merge recorded ownership before recomputing policy: older copies may own TAA=true
        // even though their selected base explicitly disabled it. Migrate options, not the ZIP.
        Map<String, String> owned = overrides(recipe, selectedValues);
        if (generatedSelected && state.active && owned.equals(state.appliedOverrides)) {
            return new Result(Status.UNCHANGED, selected, state.adapterId, false, false,
                "Adapter and original selection state retained");
        }
        Map<String, String> adapterValues = new TreeMap<>(selectedValues);
        adapterValues.putAll(owned);
        boolean created = !Files.exists(adapter, LinkOption.NOFOLLOW_LINKS);
        byte[] output = null;
        if (created) {
            Map<String, byte[]> members = recipe.patch(archive(original));
            JsonObject marker = new JsonObject(); marker.addProperty("schema", 1);
            marker.addProperty("stateFile", state.stateFile); marker.addProperty("originalFile", state.originalFile);
            marker.addProperty("originalSha256", state.originalSha256); marker.addProperty("adapterId", state.adapterId);
            marker.addProperty("recipeSha256", recipe.digest);
            members.put(MARKER, GSON.toJson(marker).getBytes(StandardCharsets.UTF_8));
            members.put("SHADERLOD_PRIVATE_SELECTION.md", ("Private local ShaderLOD adapter. Original: "
                + recipe.id + ". Upstream files and license retained. Do not redistribute this copy.\n"
                + "Rendering, quality, DH and PBR validation remain separate checks.\n").getBytes(StandardCharsets.UTF_8));
            output = zip(members); state.generatedSha256 = hash(output);
        }
        // Backup exact original sidecar bytes before any service mutation; never overwritten.
        if (state.originalOptionsBackup == null)
            state.originalOptionsBackup = originalBytes == null ? "ABSENT" : Base64.getEncoder().encodeToString(originalBytes);
        state.baseOptions = new TreeMap<>(selectedValues); state.appliedOverrides = owned;
        state.active = true;
        byte[] adapterBytes = optionalBytes(adapterOptions);
        byte[] optionsOutput = renderOptions(originalBytes, adapterValues);
        boolean optionsChanged = adapterBytes == null || !java.util.Arrays.equals(adapterBytes, optionsOutput);
        // Every read, pin, collision, graph and merge check above precedes mutations.
        // Each file replacement is atomic. An interrupted multi-file action fails safe next Apply.
        writeAtomic(statePath, GSON.toJson(state).getBytes(StandardCharsets.UTF_8));
        if (!originalValues.equals(selectedValues))
            writeAtomic(originalOptions, renderOptions(originalBytes, selectedValues));
        if (created) writeNew(adapter, output);
        if (optionsChanged) writeAtomic(adapterOptions, optionsOutput);
        writeAtomic(statePath, GSON.toJson(state).getBytes(StandardCharsets.UTF_8));
        return new Result(Status.APPLIED, state.generatedFile, state.adapterId, created, optionsChanged,
            "Private adapter selected; reload required. " + SupportedPackCatalog.inventory().stream()
                .filter(pack -> pack.id().equals(recipe.id)).findFirst().orElseThrow().localRuntimeBoundary());
    }

    private static Map<String, String> overrides(Recipe recipe, Map<String, String> selected) throws IOException {
        // Activate only the private budget. Preserve native presets/effects, including disabled VL.
        if (!recipe.id.equals("photon")) return Map.of(recipe.optionKey(), "1");
        Map<String, String> effective = new TreeMap<>(PHOTON_DEFAULTS); effective.putAll(selected);
        for (String key : List.of("TAA", "TAAU", "VOXY")) {
            if (!Set.of("true", "false").contains(effective.get(key)))
                throw new IOException("Unknown Photon boolean option: " + key);
        }
        if (effective.get("VOXY").equals("true"))
            throw new IOException("Photon Voxy graph is outside this adapter");
        double scale;
        try { scale = Double.parseDouble(effective.get("TAAU_RENDER_SCALE")); }
        catch (NumberFormatException exception) { throw new IOException("Invalid Photon selected TAAU scale"); }
        if (!Double.isFinite(scale) || scale <= 0 || scale > 1) throw new IOException("Invalid Photon selected TAAU scale");
        // Cloud rejection also works with native TAAU/AA off. The existing pixel
        // helper activates only when the user already selected native TAA+TAAU.
        // Own only the private switch; never enable upscaling or replace its scale.
        return Map.of("SHADERLOD_LIVE_PIXEL_BUDGET", "true");
    }

    private static Map<String, String> merge(State state, Map<String, String> original,
            Map<String, String> adapter) throws IOException {
        Map<String, String> restored = new TreeMap<>(adapter);
        state.appliedOverrides.forEach((key, value) -> {
            if (value.equals(restored.get(key))) {
                if (state.baseOptions.containsKey(key)) restored.put(key, state.baseOptions.get(key));
                else restored.remove(key);
            }
        });
        Map<String, String> merged = new TreeMap<>(original);
        Set<String> keys = new HashSet<>(state.baseOptions.keySet()); keys.addAll(restored.keySet());
        for (String key : keys) {
            String base = state.baseOptions.get(key), current = original.get(key), edited = restored.get(key);
            if (java.util.Objects.equals(base, edited)) continue;
            if (!java.util.Objects.equals(base, current) && !java.util.Objects.equals(current, edited))
                throw new IOException("Conflicting original/adapter shader option edit: " + key);
            if (edited == null) merged.remove(key); else merged.put(key, edited);
        }
        return merged;
    }

    private static void verifyState(Path dir, State state) throws IOException {
        if (state.schema != 1 || !RECIPES.contains(state.adapterId) || state.baseOptions == null
                || state.appliedOverrides == null || state.originalOptionsBackup == null)
            throw new IOException("Missing or unsupported original selection state");
        Recipe recipe = recipe(state.adapterId); verifyRecipeState(recipe, state);
        if (!state.stateFile.equals(stateFile(state.originalFile, state.originalSha256, recipe.digest)))
            throw new IOException("Selection state identity mismatch");
        if (!state.generatedFile.equals("ShaderLOD-" + recipe.id + "-" + hash((state.originalFile
                + state.originalSha256 + recipe.digest).getBytes(StandardCharsets.UTF_8)).substring(0, 16) + ".zip"))
            throw new IOException("Generated filename identity mismatch");
        Path original = child(dir, state.originalFile), adapter = child(dir, state.generatedFile);
        if (!hash(Files.readAllBytes(original)).equals(state.originalSha256)
                || !hash(Files.readAllBytes(adapter)).equals(state.generatedSha256))
            throw new IOException("Original or generated pack was changed; files left untouched");
        recipe.validate(archive(original)); archive(adapter);
        if (Files.exists(child(dir, state.stateFile), LinkOption.NOFOLLOW_LINKS))
            optionalBytes(child(dir, state.stateFile));
    }
    private static void verifyRecipeState(Recipe recipe, State state) throws IOException {
        if (!recipe.digest.equals(state.recipeSha256)) throw new IOException("Adapter recipe changed; choose original safely");
    }
    private static State readState(Path path) throws IOException {
        byte[] bytes = optionalBytes(path);
        if (bytes == null) throw new IOException("Manual adapter lacks persistent original state; choose original pack");
        try {
            State state = GSON.fromJson(text(bytes), State.class);
            if (state == null) throw new IllegalArgumentException();
            return state;
        } catch (RuntimeException exception) { throw new IOException("Invalid original selection state", exception); }
    }
    private static final class State {
        int schema; String adapterId, originalFile, generatedFile, originalSha256, generatedSha256;
        String stateFile, recipeSha256, originalOptionsBackup; boolean active;
        Map<String, String> baseOptions, appliedOverrides;
    }

    private static Recipe recipe(String id) throws IOException {
        String path = id.equals("photon") ? "patches/photon-experiments/live-pixel-budget.json"
            : "patches/adapters/" + id + ".json";
        byte[] bytes = resource(path);
        Recipe recipe = new Recipe(id, hash(bytes), JsonParser.parseString(text(bytes)).getAsJsonObject());
        recipe.validateManifest();
        return recipe;
    }
    private record Recipe(String id, String digest, JsonObject manifest) {
        String optionKey() throws IOException {
            String expected = switch (string(manifest, "kind")) {
                case "ssr-hit-refinement" -> "SHADERLOD_REFINEMENT_ADAPTER";
                case "volumetric-sample-budget" -> "SHADERLOD_EFFECT_ADAPTER";
                default -> throw new IOException("Unknown maintained adapter kind");
            };
            // Older maintained refinement manifests predate the explicit option_key member.
            if (manifest.has("option_key") && !expected.equals(string(manifest, "option_key")))
                throw new IOException("Maintained adapter kind/option mismatch");
            return expected;
        }
        void validateManifest() throws IOException {
            if (manifest.get("schema").getAsInt() != 1) throw new IOException("Unknown maintained adapter schema");
            if (!id.equals("photon")) {
                if (!id.equals(string(manifest, "id"))) throw new IOException("Maintained adapter identity mismatch");
                optionKey();
            }
        }
        void validate(Map<String, byte[]> files) throws IOException {
            var recognition = SupportedPackCatalog.recognize(hashes(files));
            if (recognition.isEmpty() || !recognition.get().pack().id().equals(id)
                    || recognition.get().stage() != SupportedPackCatalog.Stage.RECOGNIZED_ORIGINAL)
                throw new IOException("Unrecognized original source version");
            for (String reserved : List.of(MARKER, "shaderlod-adapter.json", "shaderlod-live-pixel-budget.json",
                    "SHADERLOD_PRIVATE_SELECTION.md"))
                if (files.containsKey(reserved)) throw new IOException("Original contains reserved adapter member");
            if (id.equals("photon")) {
                pin(files, "LICENSE", string(manifest, "license_sha256"));
                for (var member : manifest.getAsJsonArray("members")) {
                    var entry = member.getAsJsonObject(); pin(files, string(entry, "path"), string(entry, "source_sha256"));
                }
                for (var entry : manifest.getAsJsonObject("source_dependencies").entrySet())
                    pin(files, entry.getKey(), entry.getValue().getAsString());
            } else for (var entry : manifest.getAsJsonObject("source_members").entrySet())
                pin(files, entry.getKey(), entry.getValue().getAsString());
        }
        Map<String, byte[]> patch(Map<String, byte[]> files) throws IOException {
            validate(files); Map<String, byte[]> result = new TreeMap<>(files);
            if (id.equals("photon")) {
                for (var member : manifest.getAsJsonArray("members")) {
                    JsonObject entry = member.getAsJsonObject(); String name = string(entry, "path");
                    for (var replacement : entry.getAsJsonArray("replacements"))
                        replace(result, name, replacement.getAsJsonObject());
                    pin(result, name, string(entry, "patched_sha256"));
                    if (name.equals("shaders/settings.glsl")) {
                        JsonObject on = new JsonObject(); on.addProperty("before", "//#define SHADERLOD_LIVE_PIXEL_BUDGET\n");
                        on.addProperty("after", "#define SHADERLOD_LIVE_PIXEL_BUDGET\n"); replace(result, name, on);
                        pin(result, name, string(entry, "enabled_sha256"));
                    }
                }
                for (String key : List.of("addition", "common_addition")) {
                    JsonObject addition = manifest.getAsJsonObject(key);
                    add(result, string(addition, "path"), string(addition, "source_path"), string(addition, "sha256"));
                }
                if (manifest.has("additions")) {
                    for (var entry : manifest.getAsJsonObject("additions").entrySet()) {
                        JsonObject addition = entry.getValue().getAsJsonObject();
                        validateMember(entry.getKey());
                        add(result, entry.getKey(), string(addition, "path"), string(addition, "sha256"));
                    }
                }
            } else {
                for (var patch : manifest.getAsJsonArray("patches")) {
                    JsonObject entry = patch.getAsJsonObject(); String name = string(entry, "path");
                    if (!manifest.getAsJsonObject("source_members").has(name)) throw new IOException("Unpinned patch");
                    replace(result, name, entry);
                }
                for (var entry : manifest.getAsJsonObject("additions").entrySet()) {
                    JsonObject addition = entry.getValue().getAsJsonObject();
                    add(result, entry.getKey(), string(addition, "path"), string(addition, "sha256"));
                }
            }
            return result;
        }
    }
    private static void replace(Map<String, byte[]> result, String name, JsonObject replacement) throws IOException {
        String source = text(result.get(name)), before = string(replacement, "before");
        int index = source.indexOf(before);
        if (before.isEmpty() || index < 0 || source.indexOf(before, index + before.length()) >= 0)
            throw new IOException("Ambiguous patch anchor: " + name);
        result.put(name, (source.substring(0, index) + string(replacement, "after")
            + source.substring(index + before.length())).getBytes(StandardCharsets.UTF_8));
    }
    private static void add(Map<String, byte[]> files, String name, String path, String expected) throws IOException {
        if (files.containsKey(name)) throw new IOException("Adapter member collision: " + name);
        byte[] bytes = resource(path);
        if (!hash(bytes).equals(expected)) throw new IOException("Maintained adapter helper changed: " + path);
        files.put(name, bytes);
    }
    private static void pin(Map<String, byte[]> files, String name, String expected) throws IOException {
        if (!files.containsKey(name) || !hash(files.get(name)).equals(expected))
            throw new IOException("Pinned source changed: " + name);
    }
    private static byte[] resource(String path) throws IOException {
        try (InputStream input = PackSelectionService.class.getResourceAsStream(RESOURCES + path)) {
            if (input == null) throw new IOException("Missing maintained adapter resource: " + path);
            return input.readAllBytes();
        }
    }
    private static Map<String, byte[]> archive(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > LIMIT)
            throw new IOException("Pack is missing, symlinked or too large");
        Map<String, byte[]> files = new TreeMap<>(); Set<String> names = new HashSet<>(); long total = 0;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(path))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName(); validateMember(name);
                if (!names.add(name.toLowerCase(Locale.ROOT))) throw new IOException("Duplicate ZIP member: " + name);
                byte[] data = zip.readNBytes((int)Math.min(LIMIT - total + 1, LIMIT)); total += data.length;
                if (total >= LIMIT) throw new IOException("Expanded pack exceeds size limit");
                if (!entry.isDirectory()) files.put(name, data);
            }
        }
        if (files.isEmpty()) throw new IOException("Empty or invalid ZIP archive");
        return files;
    }
    private static void validateMember(String name) throws IOException {
        if (name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains(":")
                || name.contains("\0") || name.contains("\n") || name.contains("\r")) throw new IOException("Unsafe ZIP path");
        for (String part : name.split("/")) if (part.equals("..") || part.equals(".")) throw new IOException("Unsafe ZIP path");
    }
    private static byte[] zip(Map<String, byte[]> files) throws IOException {
        var output = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (var entry : new TreeMap<>(files).entrySet()) {
                ZipEntry member = new ZipEntry(entry.getKey()); member.setTime(315532800000L);
                zip.putNextEntry(member); zip.write(entry.getValue()); zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
    private static Path child(Path dir, String name) throws IOException {
        if (name == null || name.isBlank() || name.equals(".") || name.equals("..") || name.contains("/")
                || name.contains("\\") || name.contains(":") || name.indexOf(0) >= 0)
            throw new IOException("Invalid local pack filename");
        Path path = dir.resolve(name);
        if (Files.isSymbolicLink(path)) throw new IOException("Symlinked selection file refused");
        return path;
    }
    private static byte[] optionalBytes(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 1024 * 1024)
            throw new IOException("Unsafe or oversized sidecar/state file");
        return Files.readAllBytes(path);
    }
    private static Map<String, String> options(byte[] bytes) throws IOException {
        Map<String, String> result = new TreeMap<>();
        if (bytes != null) {
            Properties properties = new Properties(); properties.load(new ByteArrayInputStream(bytes));
            for (String key : properties.stringPropertyNames()) result.put(key, properties.getProperty(key));
        }
        return result;
    }
    /** Keep unrelated lines/comments byte for byte; serialize only changed properties. */
    private static byte[] renderOptions(byte[] original, Map<String, String> desired) throws IOException {
        Map<String, String> prior = options(original);
        if (prior.equals(desired)) return original == null ? new byte[0] : original;
        StringBuilder kept = new StringBuilder();
        if (original != null) {
            String[] lines = new String(original, StandardCharsets.ISO_8859_1).split("(?<=\\n)", -1);
            for (int i = 0; i < lines.length; i++) {
                StringBuilder group = new StringBuilder(lines[i]);
                String last = lines[i].replaceFirst("[\\r\\n]+$", "");
                while (continued(last) && i + 1 < lines.length) {
                    last = lines[++i].replaceFirst("[\\r\\n]+$", ""); group.append(lines[i]);
                }
                Map<String, String> parsed = options(group.toString().getBytes(StandardCharsets.ISO_8859_1));
                if (parsed.isEmpty() || parsed.keySet().stream().allMatch(key ->
                        desired.containsKey(key) && java.util.Objects.equals(prior.get(key), desired.get(key))))
                    kept.append(group);
            }
        }
        Map<String, String> added = new TreeMap<>(desired);
        added.entrySet().removeIf(entry -> java.util.Objects.equals(prior.get(entry.getKey()), entry.getValue()));
        if (!added.isEmpty() && !kept.isEmpty() && kept.charAt(kept.length() - 1) != '\n') kept.append('\n');
        for (var entry : added.entrySet()) {
            Properties one = new Properties(); one.setProperty(entry.getKey(), entry.getValue());
            var output = new java.io.ByteArrayOutputStream(); one.store(output, null);
            String serialized = output.toString(StandardCharsets.ISO_8859_1);
            kept.append(serialized.substring(serialized.indexOf('\n') + 1)); // Strip timestamp only.
        }
        return kept.toString().getBytes(StandardCharsets.ISO_8859_1);
    }
    private static boolean continued(String line) {
        int slashes = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) slashes++;
        return slashes % 2 == 1;
    }
    private static void writeNew(Path path, byte[] bytes) throws IOException {
        Files.write(path, bytes, java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
    }
    private static void writeAtomic(Path path, byte[] bytes) throws IOException {
        Path temp = Files.createTempFile(path.getParent(), ".shaderlod-", ".tmp");
        try {
            Files.write(temp, bytes);
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
    private static String stateFile(String name, String source, String recipe) {
        return ".shaderlod-selection-" + hash(name.getBytes(StandardCharsets.UTF_8)).substring(0, 24) + ".json";
    }
    private static Result unsupported(String selected) {
        return new Result(Status.UNSUPPORTED, selected, null, false, false, "No maintained adapter for this original source version");
    }
    private static Map<String, String> hashes(Map<String, byte[]> files) {
        Map<String, String> result = new TreeMap<>(); files.forEach((name, data) -> result.put(name, hash(data))); return result;
    }
    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static String text(byte[] bytes) { return new String(bytes, StandardCharsets.UTF_8); }
    private static String string(JsonObject object, String key) throws IOException {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) throw new IOException("Missing adapter identity: " + key);
        return object.get(key).getAsString();
    }
}

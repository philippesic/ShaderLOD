package com.pp.shaderlod.packs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.mips.BslLeanGraph;
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
            boolean generatedArchive, boolean optionsChanged, String detail,
            Map<String,String> firstLoadCompileOptions) {
        public Result { firstLoadCompileOptions = Map.copyOf(firstLoadCompileOptions); }
        public Result(Status status,String packFile,String adapterId,boolean generatedArchive,
                boolean optionsChanged,String detail) {
            this(status,packFile,adapterId,generatedArchive,optionsChanged,detail,Map.of());
        }
        public boolean accepted() { return status == Status.APPLIED || status == Status.UNCHANGED; }
        public boolean requiresReload() { return status == Status.APPLIED; }
    }
    /** Private default-off original-graph route; existing Photon effect routing is preserved. */
    public static final String PHOTON_QUAD_ORIGINAL_GRAPH = "shaderlod.photonQuadOriginalGraphR1";
    // Enabled only by the root NeoForge1.21.1 initializer; shared consumers keep legacy behavior.
    private static volatile boolean recipeScopedSelections;
    public static void enableRecipeScopedSelections() { recipeScopedSelections = true; }
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
                nativeRendererAvailable, Map.copyOf(declaredDefaults), Set.copyOf(explicitOptionKeys), null, BslLeanGraph.LEGACY);
        } catch (IOException | RuntimeException exception) {
            return new Result(Status.REFUSED, selected, null, false, false, exception.getMessage());
        }
    }
    public Result apply(Path directory, String selected, boolean enabled) {
        return apply(directory, selected, enabled, Map.of());
    }

    /** Exact read-only identity, shared with the transaction before any file/mode preimages. */
    public record SelectionIdentity(String stateFile, String originalFile, String generatedFile,
            boolean historicalOriginalOnly) {}
    public static SelectionIdentity planSelection(Path directory, String selected, boolean enabled,
            LodPreset preset) throws IOException {
        Path dir = directory.toAbsolutePath().normalize();
        try { return planSelection(dir, selected, archive(child(dir, selected)), enabled, preset); }
        catch (RuntimeException invalid) { throw new IOException("Invalid selection identity", invalid); }
    }
    private static SelectionIdentity planSelection(Path dir, String selected, Map<String,byte[]> members,
            boolean enabled, LodPreset preset) throws IOException {
        if (members.containsKey(MARKER)) {
            JsonObject marker = JsonParser.parseString(text(members.get(MARKER))).getAsJsonObject();
            State state = readState(child(dir, string(marker, "stateFile")));
            if (!selected.equals(state.generatedFile) || !state.stateFile.equals(string(marker, "stateFile"))
                    || !state.adapterId.equals(string(marker, "adapterId"))
                    || !state.recipeSha256.equals(string(marker, "recipeSha256"))
                    || !state.originalFile.equals(string(marker, "originalFile"))
                    || !state.originalSha256.equals(string(marker, "originalSha256")))
                throw new IOException("Generated selection identity mismatch");
            boolean historical = recipeScopedSelections && !recipe(state.adapterId).digest.equals(state.recipeSha256);
            if (historical) {
                if (enabled && !nativeQualityGraph(state.adapterId, preset))
                    throw new IOException("Adapter recipe changed; select the original pack before enabling");
                restoreHistoricalOriginal(dir, selected, marker, state); // Full read-only native/marker/raw proof.
            } else verifyState(dir, state);
            return new SelectionIdentity(state.stateFile, state.originalFile, state.generatedFile, historical);
        }
        var recognized = SupportedPackCatalog.recognize(hashes(members));
        if (recognized.isEmpty() || !RECIPES.contains(recognized.get().pack().id())) return null;
        if (recognized.get().stage() != SupportedPackCatalog.Stage.RECOGNIZED_ORIGINAL)
            throw new IOException("Manual adapter has no known original selection; choose the original pack");
        Recipe recipe = recipe(recognized.get().pack().id()); recipe.validate(members);
        String sourceHash = hashArchive(child(dir, selected));
        String name = stateFile(selected, sourceHash, recipe.digest);
        if (recipeScopedSelections && !Files.exists(child(dir, name), LinkOption.NOFOLLOW_LINKS)) {
            String legacyName = legacyStateFile(selected);
            if (Files.exists(child(dir, legacyName), LinkOption.NOFOLLOW_LINKS)) {
                try {
                    State legacy = readState(child(dir, legacyName));
                    if (legacyName.equals(legacy.stateFile) && selected.equals(legacy.originalFile)
                            && sourceHash.equals(legacy.originalSha256) && recipe.digest.equals(legacy.recipeSha256)) {
                        verifyState(dir, legacy); name = legacyName;
                    }
                } catch (IOException | RuntimeException invalidHistoricalState) { /* Retain history untouched. */ }
            }
        }
        String generated = "ShaderLOD-" + recipe.id + "-" + hash((selected + sourceHash + recipe.digest)
            .getBytes(StandardCharsets.UTF_8)).substring(0, 16) + ".zip";
        if (Files.exists(child(dir, name), LinkOption.NOFOLLOW_LINKS)) {
            State state = readState(child(dir, name));
            if (!name.equals(state.stateFile) || !selected.equals(state.originalFile) || !sourceHash.equals(state.originalSha256))
                throw new IOException("Original selection state mismatch");
            verifyState(dir, state); generated = state.generatedFile;
        }
        return new SelectionIdentity(name, selected, generated, false);
    }
    /** Recheck the captured identity before service writes; concurrent identity changes refuse. */
    public synchronized Result applyPlanned(Path directory, String selected, boolean enabled, LodPreset preset,
            Map<String,String> effective, boolean nativeRendererAvailable, Map<String,String> defaults,
            Set<String> explicit, SelectionIdentity planned) {
        return applyPlanned(directory,selected,enabled,preset,effective,nativeRendererAvailable,defaults,explicit,planned,BslLeanGraph.LEGACY);
    }
    public synchronized Result applyPlanned(Path directory,String selected,boolean enabled,LodPreset preset,
            Map<String,String> effective,boolean nativeRendererAvailable,Map<String,String> defaults,
            Set<String> explicit,SelectionIdentity planned,BslLeanGraph.Route route) {
        try {
            return applyChecked(directory.toAbsolutePath().normalize(), selected, enabled, preset,
                Map.copyOf(effective), nativeRendererAvailable, Map.copyOf(defaults), Set.copyOf(explicit), planned,route);
        } catch (IOException | RuntimeException failure) {
            return new Result(Status.REFUSED, selected, null, false, false, failure.getMessage());
        }
    }

    private static boolean nativeRendererGraph(String adapterId,LodPreset preset,boolean available) {
        return nativeRendererGraph(adapterId,preset,available,BslLeanGraph.LEGACY);
    }
    private static boolean nativeRendererGraph(String adapterId,LodPreset preset,boolean available,BslLeanGraph.Route route) {
        boolean loaded=route==BslLeanGraph.LEGACY?com.pp.shaderlod.optimization.OptimizationConfig.loaded():route.loaded();
        var flags=route==BslLeanGraph.LEGACY?com.pp.shaderlod.optimization.OptimizationConfig.snapshot():route.requests();
        return nativeRendererGraph(adapterId,preset,available,loaded,flags);
    }

    /** A loaded CPU-only preview retains the certified native source; any GLSL request needs its adapter. */
    static boolean nativeRendererGraph(String adapterId,LodPreset preset,boolean available,boolean loaded,
            com.pp.shaderlod.optimization.OptimizationSnapshot flags) {
        if(preset!=LodPreset.PERFORMANCE||!available||!RECIPES.contains(adapterId))return false;
        if(!loaded)return true; // Preserve the existing legacy settings route.
        if(!Set.of("makeup-ultra-fast-shaders","sildurs-vibrant-shaders","bsl-shaders",
                "complementary-reimagined","complementary-unbound","bliss-shader","photon","solas-shader","astralex").contains(adapterId))return false;
        return nativeRendererRequests(flags);
    }

    /** Graph-routing classification only: source/runtime admission and reload-free edit safety
     * remain separate. Startup must wait for the same native certificate as explicit Apply. */
    public static boolean nativeRendererRequests(com.pp.shaderlod.optimization.OptimizationSnapshot flags) {
        for(var id:com.pp.shaderlod.optimization.OptimizationId.values()) {
            if(!flags.enabled(id))continue;
            // Explicit renderer/proof controls only; absence of compile options does not imply CPU-only.
            switch(id) {
                case QUAD_COMMAND_COMPACTION, INDEX_SCALAR_CACHE, SHADOW_ADMISSION_CACHE,
                        SHADOW_POSITIVE_VERTEX, SHADOW_VISIBILITY_LISTS, SHADOW_FRUSTUM_COHERENCE,
                        BINDING_CERTIFICATE_CACHE, BINDING_DIGEST_STREAMING -> { }
                default -> { return false; }
            }
        }
        return true;
    }

    /** Source/state/owned-option proof only. Renderer callers must additionally certify the complete
     * live effective option map, vertex/include behavior and exact API contract; this is not admission. */
    public static String verifyGeneratedRendererGraph(Path selectedArchive, Map<String, String> effectiveOptions) {
        return verifyGeneratedRendererGraph(selectedArchive, effectiveOptions, null);
    }

    /**
     * Capture the exact member map verified by the normal producer check, without reopening it.
     * This is a loading-time snapshot, not runtime admission or proof that Iris consumed it.
     */
    public static CapturedRendererInputs captureGeneratedRendererInputs(Path selectedArchive,
            Map<String, String> effectiveOptions) {
        try {
            Map<String, String> options = Map.copyOf(effectiveOptions);
            CapturedRendererInputs[] captured = new CapturedRendererInputs[1];
            verifyGeneratedRendererGraph(selectedArchive, options,
                (id, members, provenance) -> captured[0] = new CapturedRendererInputs(id, options, members, provenance));
            return captured[0];
        } catch (RuntimeException invalid) { return null; }
    }

    public static boolean ownedBslLean(Path selected,Map<String,String> actual) {
        if(!BslLeanGraph.profile(actual)||!"bsl-shaders".equals(verifyGeneratedRendererGraph(selected,actual)))return false;
        try {
            var members=archive(selected);var marker=JsonParser.parseString(text(members.get(MARKER))).getAsJsonObject();
            return BslLeanGraph.OWNED.equals(readState(child(selected.toAbsolutePath().normalize().getParent(),string(marker,"stateFile"))).appliedOverrides);
        } catch(IOException|RuntimeException invalid) {return false;}
    }

    private interface InputCapture {
        void accept(String id, Map<String, byte[]> members, CapturedRendererInputs.Provenance provenance);
    }
    private static String verifyGeneratedRendererGraph(Path selectedArchive, Map<String, String> effectiveOptions,
            InputCapture capture) {
        try {
            Path selected = selectedArchive.toAbsolutePath().normalize();
            Path dir = selected.getParent();
            if (dir == null || Files.isSymbolicLink(selected)) return null;
            ArchiveSnapshot selectedSnapshot = capture == null ? null : archiveSnapshot(selected);
            var actual = selectedSnapshot == null ? archive(selected) : selectedSnapshot.members;
            JsonObject marker = JsonParser.parseString(text(actual.get(MARKER))).getAsJsonObject();
            if (marker.get("schema").getAsInt() != 1) return null;
            String id = string(marker, "adapterId");
            if (!RECIPES.contains(id)) return null;
            Path statePath = child(dir, string(marker, "stateFile"));
            byte[] stateBytes = capture == null ? null : optionalBytes(statePath);
            State state = capture == null ? readState(statePath) : parseState(stateBytes);
            Recipe recipe;
            Map<String, byte[]> originalMembers;
            if (capture == null) {
                verifyState(dir, state);
                recipe = recipe(id);
                originalMembers = archive(child(dir, state.originalFile));
            } else {
                recipe = recipe(id);
                verifyStateIdentity(recipe, state);
                if (!statePath.getFileName().toString().equals(state.stateFile)) return null;
                ArchiveSnapshot original = archiveSnapshot(child(dir, state.originalFile));
                if (!original.sha256.equals(state.originalSha256)
                        || !selectedSnapshot.sha256.equals(state.generatedSha256)) return null;
                recipe.validate(original.members);
                originalMembers = original.members;
            }
            if (!state.active || !state.adapterId.equals(id)
                    || !state.generatedFile.equals(selected.getFileName().toString())
                    || !state.originalFile.equals(string(marker, "originalFile"))
                    || !state.originalSha256.equals(string(marker, "originalSha256"))
                    || !state.recipeSha256.equals(string(marker, "recipeSha256"))) return null;
            var expected = recipe.patch(originalMembers);
            var names = new HashSet<>(expected.keySet());
            names.add(MARKER); names.add("SHADERLOD_PRIVATE_SELECTION.md");
            if (!actual.keySet().equals(names)) return null;
            for (var entry : expected.entrySet())
                if (!java.util.Arrays.equals(entry.getValue(), actual.get(entry.getKey()))) return null;
            String note = "Private local ShaderLOD adapter. Original: " + id
                    + ". Upstream files and license retained. Do not redistribute this copy.\n"
                    + "Rendering, quality, DH and PBR validation remain separate checks.\n";
            if (!java.util.Arrays.equals(note.getBytes(StandardCharsets.UTF_8), actual.get("SHADERLOD_PRIVATE_SELECTION.md"))) return null;
            boolean lean=recipe.id.equals("bsl-shaders")&&BslLeanGraph.OWNED.equals(state.appliedOverrides);
            boolean copyLean=recipe.id.equals("makeup-ultra-fast-shaders")&&BslLeanGraph.OWNED.equals(state.appliedOverrides);
            boolean solasMipLean=recipe.id.equals("solas-shader")&&BslLeanGraph.OWNED.equals(state.appliedOverrides);
            boolean astraMipLean=recipe.id.equals("astralex")&&BslLeanGraph.OWNED.equals(state.appliedOverrides);
            Map<String,String> owned = lean||copyLean||solasMipLean||astraMipLean ? BslLeanGraph.OWNED : overrides(recipe, state.baseOptions);
            if (!owned.equals(state.appliedOverrides) || lean&&!BslLeanGraph.profile(effectiveOptions)
                    || solasMipLean&&!com.pp.shaderlod.optimization.solasmips.SolasMipLeanGraph.profile(effectiveOptions)
                    || astraMipLean&&!com.pp.shaderlod.optimization.mips.AstraMipLeanGraph.profile(effectiveOptions)
                    || copyLean&&!(com.pp.shaderlod.optimization.framecopy.MakeUpLeanGraph.profile(effectiveOptions)
                        ||com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaLeanGraph.profile(effectiveOptions)
                        ||com.pp.shaderlod.optimization.makeupexact.MakeUpExactGraph.profile(effectiveOptions))) return null;
            for (var entry : owned.entrySet())
                if (!entry.getValue().equals(effectiveOptions.get(entry.getKey()))) return null;
            // Personal, undeclared sidecar entries are preserved but cannot qualify a native shader option.
            var compileChoices = compileChoices(recipe);
            for (var entry : compileChoices.entrySet()) {
                String actualChoice = effectiveOptions.get(entry.getKey());
                if (actualChoice != null && !entry.getValue().contains(actualChoice)) return null;
                if (state.baseOptions.containsKey(entry.getKey()) && actualChoice == null) return null;
            }
            // Foreign known graph controls cannot masquerade as this pinned family's declarations.
            for (var feature : com.pp.shaderlod.optimization.OptimizationId.values())
                    for (String key : com.pp.shaderlod.optimization.CompileOptionControl.controlledOptions(feature))
                        if (effectiveOptions.containsKey(key) && !compileChoices.containsKey(key)) return null;
            for (var entry : state.baseOptions.entrySet())
                if (effectiveOptions.containsKey(entry.getKey()) && !owned.containsKey(entry.getKey()) && !compileChoices.containsKey(entry.getKey())
                        && !entry.getValue().equals(effectiveOptions.get(entry.getKey()))) return null;
            if (capture != null) capture.accept(id, actual, new CapturedRendererInputs.Provenance(
                state.originalSha256, selectedSnapshot.sha256, recipe.digest, hash(stateBytes)));
            return id;
        } catch (IOException | RuntimeException invalid) { return null; }
    }

    /** Source-only exact requests fail back to the selected original for unsupported tuples/options. */
    static boolean asahiAlphaOriginalFallback(String os,String arch,String id,boolean enabled,LodPreset preset,
            Map<String,String> effective,BslLeanGraph.Route route,boolean generated) {
        return asahiAlphaOriginalFallback(os,arch,id,enabled,preset,effective,route,generated,false);
    }
    static boolean asahiAlphaOriginalFallback(String os,String arch,String id,boolean enabled,LodPreset preset,
            Map<String,String> effective,BslLeanGraph.Route route,boolean generated,boolean qualifiedWindowsHost) {
        return (("Linux".equals(os)&&"aarch64".equals(arch))
                ||qualifiedWindowsHost&&com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpPostSampleAlphaSource.platform(os,arch))
            &&"bsl-shaders".equals(id)
            &&com.pp.shaderlod.optimization.bslearlyalpha.BslPostSampleAlpha.singleton(route.requests())
            ||"Linux".equals(os)&&"aarch64".equals(arch)&&"makeup-ultra-fast-shaders".equals(id)
            &&com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaLeanGraph.onlyEarlyAlpha(route.requests())
            &&(!com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaLeanGraph.eligible(route,enabled,preset)
                ||!(generated?com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaLeanGraph.transitionProfile(effective)
                    :com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.optionsMatch(effective)));
    }
    private static boolean exactOriginalFallback(String id, boolean enabled, LodPreset preset,
            Map<String,String> effective, BslLeanGraph.Route route, boolean generated) {
        return asahiAlphaOriginalFallback(System.getProperty("os.name"),System.getProperty("os.arch"),
                id,enabled,preset,effective,route,generated,
                "bsl-shaders".equals(id)
                    &&com.pp.shaderlod.optimization.bslearlyalpha.BslPostSampleAlpha.singleton(route.requests())
                    &&com.pp.shaderlod.optimization.bslearlyalpha.BslPostSampleAlpha.hostAvailable())
            ||"makeup-ultra-fast-shaders".equals(id)
            && com.pp.shaderlod.optimization.makeupexact.MakeUpExactGraph.singleton(route.requests())
            && (!com.pp.shaderlod.optimization.makeupexact.MakeUpExactGraph.eligible(route,enabled,preset)
                || !(generated ? com.pp.shaderlod.optimization.makeupexact.MakeUpExactGraph.transitionProfile(effective)
                    : com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.optionsMatch(effective)));
    }

    private static boolean nativeQualityGraph(String adapterId, LodPreset preset) {
        return preset == LodPreset.QUALITY && RECIPES.contains(adapterId);
    }

    private Result applyChecked(Path dir, String selected, boolean enabled, LodPreset preset,
            Map<String, String> effective, boolean nativeRendererAvailable,
            Map<String, String> declaredDefaults, Set<String> explicitOptionKeys, SelectionIdentity planned, BslLeanGraph.Route route) throws IOException {
        Path selectedPath = child(dir, selected);
        Map<String, byte[]> selectedMembers = archive(selectedPath);
        SelectionIdentity identity = planSelection(dir, selected, selectedMembers, enabled, preset);
        if (planned != null && !planned.equals(identity)) throw new IOException("Selection identity changed before Apply");
        State state = null;
        boolean generatedSelected = selectedMembers.containsKey(MARKER);
        if (generatedSelected) {
            JsonObject marker = JsonParser.parseString(text(selectedMembers.get(MARKER))).getAsJsonObject();
            state = readState(child(dir, string(marker, "stateFile")));
            if (!selected.equals(state.generatedFile) || !state.stateFile.equals(string(marker, "stateFile")))
                throw new IOException("Generated selection identity mismatch");
            if (recipeScopedSelections && !recipe(state.adapterId).digest.equals(state.recipeSha256)) {
                if (!enabled || nativeQualityGraph(state.adapterId, preset))
                    return restoreHistoricalOriginal(dir, selected, marker, state);
                throw new IOException("Adapter recipe changed; select the original pack before enabling. Historical generated option edits remain in its sidecar");
            }
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
            String sourceHash = hashArchive(selectedPath);
            String stateFile = identity.stateFile();
            Path statePath = child(dir, stateFile);
            if (Files.exists(statePath, LinkOption.NOFOLLOW_LINKS)) {
                state = readState(statePath);
                if (!stateFile.equals(state.stateFile) || !state.originalFile.equals(selected) || !state.originalSha256.equals(sourceHash))
                    throw new IOException("Original selection state mismatch");
                verifyState(dir, state);
            }
            boolean exactFallback = exactOriginalFallback(id,enabled,preset,effective,route,false);
            if ((!enabled || nativeQualityGraph(id, preset) || nativeRendererGraph(id,preset,nativeRendererAvailable,route) || exactFallback) && (state == null || !state.active)) {
                if (enabled && !exactFallback && !nativeRendererGraph(id,preset,nativeRendererAvailable,route)) {
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
                state.generatedFile = identity.generatedFile();
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
        boolean exactFallback = exactOriginalFallback(state.adapterId,enabled,preset,effective,route,generatedSelected);
        if (enabled && !exactFallback && nativeQualityGraph(state.adapterId, preset) && !nativeRendererGraph(state.adapterId,preset,nativeRendererAvailable,route))
            overrides(recipe, selectedValues); // Validate eligibility without applying owned overrides.
        if (!enabled || nativeQualityGraph(state.adapterId, preset) || nativeRendererGraph(state.adapterId,preset,nativeRendererAvailable,route) || exactFallback) {
            boolean changed = !originalValues.equals(selectedValues);
            if (changed) writeAtomic(originalOptions, renderOptions(originalBytes, selectedValues));
            state.active = false;
            writeAtomic(statePath, GSON.toJson(state).getBytes(StandardCharsets.UTF_8));
            return new Result(Status.APPLIED, state.originalFile, state.adapterId, false, changed,
                "Original graph restored; non-owned option edits retained");
        }
        // Merge recorded ownership before recomputing policy: older copies may own TAA=true
        // even though their selected base explicitly disabled it. Migrate options, not the ZIP.
        boolean bslLean=recipe.id.equals("bsl-shaders")&&route.eligible(enabled,preset)
            &&(generatedSelected?(BslLeanGraph.profile(effective)||com.pp.shaderlod.optimization.mips.DeadMipAdmission.expectedC6Options(effective)):BslLeanGraph.nativeProfile(effective));
        boolean solasLean=recipe.id.equals("solas-shader")&&route.eligible(enabled,preset)
            &&(generatedSelected?com.pp.shaderlod.optimization.solasmips.SolasDeadMipAdmission.profile(effective):com.pp.shaderlod.optimization.solasmips.SolasMipLeanGraph.nativeProfile(effective));
        boolean astraLean=recipe.id.equals("astralex")&&route.eligible(enabled,preset)
            &&(generatedSelected?com.pp.shaderlod.optimization.mips.AstraMipLeanGraph.transitionProfile(effective):com.pp.shaderlod.optimization.mips.AstraMipLeanGraph.nativeProfile(effective));
        boolean lean=bslLean||solasLean||astraLean;
        boolean copyLean=recipe.id.equals("makeup-ultra-fast-shaders")
            &&com.pp.shaderlod.optimization.framecopy.MakeUpLeanGraph.eligible(route,enabled,preset)
            &&(generatedSelected?com.pp.shaderlod.optimization.framecopy.MakeUpLeanGraph.transitionProfile(effective)
                :com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.optionsMatch(effective));
        boolean earlyAlphaLean=recipe.id.equals("makeup-ultra-fast-shaders")
            &&com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaLeanGraph.eligible(route,enabled,preset)
            &&(generatedSelected?com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaLeanGraph.transitionProfile(effective)
                :com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.optionsMatch(effective));
        boolean exactLean=recipe.id.equals("makeup-ultra-fast-shaders")
            &&com.pp.shaderlod.optimization.makeupexact.MakeUpExactGraph.eligible(route,enabled,preset)
            &&(generatedSelected?com.pp.shaderlod.optimization.makeupexact.MakeUpExactGraph.transitionProfile(effective)
                :com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.optionsMatch(effective));
        Map<String, String> owned = lean||copyLean||earlyAlphaLean||exactLean?BslLeanGraph.OWNED:overrides(recipe, selectedValues);
        if (generatedSelected && state.active && owned.equals(state.appliedOverrides)) {
            return new Result(Status.UNCHANGED, selected, state.adapterId, false, false,
                "Adapter and original selection state retained");
        }
        Map<String, String> adapterValues = new TreeMap<>(selectedValues);
        adapterValues.putAll(owned);
        // Bootstrap only native graph values; original undeclared private preferences are not setup admission.
        compileChoices(recipe).forEach((key, choices) -> adapterValues.put(key, choices.contains("0") ? "0" : "false"));
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
                .filter(pack -> pack.id().equals(recipe.id)).findFirst().orElseThrow().localRuntimeBoundary(),
            earlyAlphaLean && "Linux".equals(System.getProperty("os.name")) && "aarch64".equals(System.getProperty("os.arch"))
                ? Map.of(com.pp.shaderlod.optimization.CompileOptionControl.MAKEUP_EARLY_ALPHA,"1") : Map.of());
    }

    /** Explicit known family + pinned recipe declaration. Never a SHADERLOD prefix exemption. */
    private static Map<String, Set<String>> compileChoices(Recipe recipe) {
        var result = new TreeMap<String, Set<String>>();
        try {
            var family = com.pp.shaderlod.optimization.CompileOptionControl.Family.fromId(recipe.id);
            for (String key : com.pp.shaderlod.optimization.CompileOptionControl.recipeKeys(family, recipe.manifest))
                result.put(key, key.equals(com.pp.shaderlod.optimization.CompileOptionControl.PHOTON_SPATIAL)
                    || key.equals(com.pp.shaderlod.optimization.CompileOptionControl.PHOTON_LIGHTING_RATE)
                    ? Set.of("false", "true") : Set.of("0", "1"));
        } catch (IllegalArgumentException unsupportedFamily) { /* No known compile graph in this family. */ }
        return Map.copyOf(result);
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
        // These are generated graph choices, not native preference edits. Keep original personal values byte-faithful.
        for (String key : compileChoices(recipe(state.adapterId)).keySet()) {
            if (state.baseOptions.containsKey(key)) restored.put(key, state.baseOptions.get(key));
            else restored.remove(key);
        }
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
        Recipe recipe = recipe(state.adapterId);
        verifyStateIdentity(recipe, state);
        Path original = child(dir, state.originalFile), adapter = child(dir, state.generatedFile);
        if (!hashArchive(original).equals(state.originalSha256)
                || !hashArchive(adapter).equals(state.generatedSha256))
            throw new IOException("Original or generated pack was changed; files left untouched");
        recipe.validate(archive(original)); archive(adapter);
        if (Files.exists(child(dir, state.stateFile), LinkOption.NOFOLLOW_LINKS))
            optionalBytes(child(dir, state.stateFile));
    }
    private static void verifyStateIdentity(Recipe recipe, State state) throws IOException {
        if (state.schema != 1 || !RECIPES.contains(state.adapterId) || state.baseOptions == null
                || state.appliedOverrides == null || state.originalOptionsBackup == null)
            throw new IOException("Missing or unsupported original selection state");
        if (!recipe.id.equals(state.adapterId)) throw new IOException("Selection adapter identity mismatch");
        if(recipe.id.equals("bsl-shaders")&&!state.appliedOverrides.equals(BslLeanGraph.OWNED)
                &&!state.appliedOverrides.equals(Map.of(BslLeanGraph.ADAPTER,"1")))
            throw new IOException("Invalid BSL graph ownership");
        verifyRecipeState(recipe, state);
        if (!state.stateFile.equals(stateFile(state.originalFile, state.originalSha256, recipe.digest))
                && !(recipeScopedSelections && state.stateFile.equals(legacyStateFile(state.originalFile))))
            throw new IOException("Selection state identity mismatch");
        if (!state.generatedFile.equals("ShaderLOD-" + recipe.id + "-" + hash((state.originalFile
                + state.originalSha256 + recipe.digest).getBytes(StandardCharsets.UTF_8)).substring(0, 16) + ".zip"))
            throw new IOException("Generated filename identity mismatch");
    }
    private static void verifyRecipeState(Recipe recipe, State state) throws IOException {
        if (!recipe.digest.equals(state.recipeSha256)) throw new IOException("Adapter recipe changed; choose original safely");
    }
    private static State readState(Path path) throws IOException {
        return parseState(optionalBytes(path));
    }
    private static State parseState(byte[] bytes) throws IOException {
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
        return archive(Files.newInputStream(path));
    }
    private record ArchiveSnapshot(String sha256, Map<String, byte[]> members) {}
    private static ArchiveSnapshot archiveSnapshot(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > LIMIT)
            throw new IOException("Pack is missing, symlinked or too large");
        byte[] raw;
        try (InputStream input = Files.newInputStream(path)) { raw = input.readNBytes((int)LIMIT + 1); }
        if (raw.length > LIMIT) throw new IOException("Pack exceeds size limit");
        return new ArchiveSnapshot(hash(raw), archive(new ByteArrayInputStream(raw)));
    }
    private static Map<String, byte[]> archive(InputStream input) throws IOException {
        Map<String, byte[]> files = new TreeMap<>(); Set<String> names = new HashSet<>(); long total = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
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
    private static String legacyStateFile(String name) {
        return ".shaderlod-selection-" + hash(name.getBytes(StandardCharsets.UTF_8)).substring(0, 24) + ".json";
    }
    private static String scopedStateFile(String name, String source, String recipe) {
        return ".shaderlod-selection-v2-" + hash((name + "\0" + source + "\0" + recipe)
            .getBytes(StandardCharsets.UTF_8)) + ".json";
    }
    private static String stateFile(String name, String source, String recipe) {
        return recipeScopedSelections ? scopedStateFile(name, source, recipe) : legacyStateFile(name);
    }
    /** Stale code is never admitted. Restore only the proven current native selection, without
     * interpreting historical option ownership or rewriting any historical/original file. */
    private static Result restoreHistoricalOriginal(Path dir, String selected,
            JsonObject marker, State state) throws IOException {
        if (state.schema != 1 || !RECIPES.contains(state.adapterId) || state.baseOptions == null
                || state.appliedOverrides == null || state.originalOptionsBackup == null
                || !state.active || !selected.equals(state.generatedFile)
                || !hexDigest(state.originalSha256) || !hexDigest(state.generatedSha256)
                || !hexDigest(state.recipeSha256))
            throw new IOException("Historical selection state is not a complete active identity");
        if (!marker.keySet().equals(Set.of("schema", "stateFile", "originalFile", "originalSha256", "adapterId", "recipeSha256"))
                || marker.get("schema").getAsInt() != 1
                || !state.stateFile.equals(string(marker, "stateFile"))
                || !state.originalFile.equals(string(marker, "originalFile"))
                || !state.originalSha256.equals(string(marker, "originalSha256"))
                || !state.adapterId.equals(string(marker, "adapterId"))
                || !state.recipeSha256.equals(string(marker, "recipeSha256")))
            throw new IOException("Historical marker/state identity mismatch");
        if (!state.stateFile.equals(legacyStateFile(state.originalFile))
                && !state.stateFile.equals(scopedStateFile(state.originalFile, state.originalSha256, state.recipeSha256)))
            throw new IOException("Historical state filename identity mismatch");
        String expected = "ShaderLOD-" + state.adapterId + "-" + hash((state.originalFile
            + state.originalSha256 + state.recipeSha256).getBytes(StandardCharsets.UTF_8)).substring(0, 16) + ".zip";
        if (!selected.equals(expected)) throw new IOException("Historical generated filename identity mismatch");
        Path original = child(dir, state.originalFile), generated = child(dir, selected);
        var nativeMembers = archive(original);
        if (!hashArchive(original).equals(state.originalSha256) || !hashArchive(generated).equals(state.generatedSha256))
            throw new IOException("Historical original or generated raw bytes changed; files left untouched");
        recipe(state.adapterId).validate(nativeMembers); // Current pinned native source, never the stale algorithm.
        optionalBytes(child(dir, state.stateFile));
        optionalBytes(child(dir, state.originalFile + ".txt"));
        optionalBytes(child(dir, selected + ".txt"));
        if (!state.originalOptionsBackup.equals("ABSENT")) Base64.getDecoder().decode(state.originalOptionsBackup);
        return new Result(Status.APPLIED, state.originalFile, state.adapterId, false, false,
            "Original graph restored from verified historical selection. Original and historical files are unchanged; generated option edits remain in its sidecar and are not merged");
    }
    private static boolean hexDigest(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
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
    private static String hashArchive(Path path) throws IOException {
        try { return com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(path); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static String text(byte[] bytes) { return new String(bytes, StandardCharsets.UTF_8); }
    private static String string(JsonObject object, String key) throws IOException {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) throw new IOException("Missing adapter identity: " + key);
        return object.get(key).getAsString();
    }
}

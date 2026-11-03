package com.pp.shaderlod.integration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pp.shaderlod.LodPreset;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/** Private local R1 selection, not catalog recognition or a renderer certificate.
 * All three archives retain their exact bytes. Only the private shader option is owned.
 * Caller serializes prepare/reload/restore and supplies actual effective Iris options.
 */
public final class RasterExperimentSelection {
    public static final String OPT_IN = "shaderlod.rasterR1.selectionEnabled";
    public static final String RECEIPT = ".shaderlod-raster-r1-selection.json";
    public static final String OPTION = "SHADERLOD_RASTER_R1";
    public static final String INTERNAL_HELPER_OPTION = "SHADERLOD_RASTER_R1_HELPER";
    public static final String ORIGINAL = "fed6c879e732cf7fc7bc6570ebb1def43235aa7e96f62859fbdbdbfa0b484b38";
    public static final String CANDIDATE = "5d183a5a2ec49713578a136143f4d16518570777e425aa3d46134a2fc159fe0f";
    public static final String CONTROL = "812d571e0bf62429eb4110848e852fafd3a3b2106120ecb4146d53de49507a2b";
    public static final String NATIVE_OPTIONS = "7744d25cca74c4fee09e7ba383c2f2348fbcfd9bde82e9508bc2e2d9e0b74bef";
    public static final String CANDIDATE_DRIVER_FIX = "286d9a565028cee58e19109e4172ce057ca5d6162a51c6a219c3c4f75fe11310";
    public static final String CONTROL_DRIVER_FIX = "7ce81956c59352714387991d8b5b2008990192be1b5249d46d6e4d14e8210762";
    public static final String CANDIDATE_DRIVER_FIX_SOURCE = "8d70cdd022442dd6f77e30719bfa5beeb470e2c6abe7bb909cb2b2b8ac7c43b6";
    public static final String CONTROL_DRIVER_FIX_SOURCE = "3e3ff8da1be2bbe06573f2ebfd20404c637393e1c29c0630b5e85b75ea58f754";
    public static final String CANDIDATE_INITIALIZER_FIX = "14eb76b558b917568a5ddb8651dd154dcfe1e45edd3867923af906c06d685061";
    public static final String CONTROL_INITIALIZER_FIX = "521bca3f3d38e85a7292bf0ddfd974b21e876905c97a61247deaaf44cf67a5bc";
    public static final String CANDIDATE_INITIALIZER_FIX_SOURCE = "7878307e968857008c7307d421d79cb7ed80dbbe39000be044723a4d6a2c23c8";
    public static final String CONTROL_INITIALIZER_FIX_SOURCE = "cab04045b504a7d144a2ff56fdddef4d3c5f603a8e99e0132451c90b3ffd5591";
    public static final String CANDIDATE_FORMAT_FIX = "b8eb893c6dac70c01db26bb4d80cfcebcc947207a591813db6e66de9d0cce3e1";
    public static final String CANDIDATE_FORMAT_FIX_SOURCE = "8df42cba58fd52cf691cbd94bc970a8b3aa957b8a577a85899487c487fda34db";
    public static final String CONTROL_FORMAT_FIX = "ba139a6baa0cbb613af672dfa2484533f972a3d1df83e7a8f0f193ae30523252";
    public static final String CONTROL_FORMAT_FIX_SOURCE = "d1b85796e52be5b15a7a1f0ce9c27589ff83bf1e2d0d6bca9a2e4c78ffe7e2bf";
    public static final String CANDIDATE_FORMAT_LINES_FIX = "0c25d9e839789be9ea449922678abb0dce4f3e81d7b7994c5da4c2814f588195";
    public static final String CANDIDATE_FORMAT_LINES_FIX_SOURCE = "61d596a22a22a4c80298698f2c07c41582db450be6e7d7f5a755e207bc1eeaf5";
    public static final String CONTROL_FORMAT_LINES_FIX = "102aecb7b3dcb0b5607b59692a18cc52f9269adf3b26ec096450289f5c2c009c";
    public static final String CONTROL_FORMAT_LINES_FIX_SOURCE = "e6265737753b6db0169655b08ebeafccafb6b9ec3797225ed03533397d9ec875";
    public static final String CANDIDATE_SINGLETON_FIX = "49fce7c5ef26712dd43e0339c978f3f36f3c9be29d69e7b67dcf63ef68c71990";
    public static final String CANDIDATE_SINGLETON_FIX_SOURCE = "a3e9b9845687455a2b6e3876789621ba231b36a2bc05e4279a7f8be74049ebc0";
    public static final String CONTROL_SINGLETON_FIX = "927f0e669fc78ab285ee6c2e18c7548d37ddb2a3e607d1e42aaa7b54c97a4090";
    public static final String CONTROL_SINGLETON_FIX_SOURCE = "3f8cd05099d2cdbf45686229c309a14784fec9706011e955a28c0093d6a95c7b";
    public static final String CANDIDATE_RECONSTRUCTION = "d88e31c433d9e46d2dac63ee3b2f4f3ff1352c708f7891a353b7e61ff23c55eb";
    public static final String CANDIDATE_RECONSTRUCTION_SOURCE = "360bfde2d05ba33d742362503193e6755cf5850828393684df2a8a330545b61d";
    public static final String CONTROL_RECONSTRUCTION = "7761347c36bd8ce5609b942951c61f4697d82153d16987871f18e20f4ca4eecc";
    public static final String CONTROL_RECONSTRUCTION_SOURCE = "30347cfd4d0f23b73b9929fe6bb49fdd5b786d96bae052f47aad4491a2bdd847";
    private static boolean candidateArchive(String sha) { return CANDIDATE.equals(sha) || CANDIDATE_DRIVER_FIX.equals(sha) || CANDIDATE_INITIALIZER_FIX.equals(sha) || CANDIDATE_FORMAT_FIX.equals(sha) || CANDIDATE_FORMAT_LINES_FIX.equals(sha) || CANDIDATE_SINGLETON_FIX.equals(sha) || CANDIDATE_RECONSTRUCTION.equals(sha); }
    private static boolean controlArchive(String sha) { return CONTROL.equals(sha) || CONTROL_DRIVER_FIX.equals(sha) || CONTROL_INITIALIZER_FIX.equals(sha) || CONTROL_FORMAT_FIX.equals(sha) || CONTROL_FORMAT_LINES_FIX.equals(sha) || CONTROL_SINGLETON_FIX.equals(sha) || CONTROL_RECONSTRUCTION.equals(sha); }
    private static String sourceForArchive(String sha) {
        if (CANDIDATE_RECONSTRUCTION.equals(sha)) return CANDIDATE_RECONSTRUCTION_SOURCE;
        if (CONTROL_RECONSTRUCTION.equals(sha)) return CONTROL_RECONSTRUCTION_SOURCE;
        if (CANDIDATE_SINGLETON_FIX.equals(sha)) return CANDIDATE_SINGLETON_FIX_SOURCE;
        if (CONTROL_SINGLETON_FIX.equals(sha)) return CONTROL_SINGLETON_FIX_SOURCE;
        if (CANDIDATE_FORMAT_LINES_FIX.equals(sha)) return CANDIDATE_FORMAT_LINES_FIX_SOURCE;
        if (CONTROL_FORMAT_LINES_FIX.equals(sha)) return CONTROL_FORMAT_LINES_FIX_SOURCE;
        if (CANDIDATE_FORMAT_FIX.equals(sha)) return CANDIDATE_FORMAT_FIX_SOURCE;
        if (CONTROL_FORMAT_FIX.equals(sha)) return CONTROL_FORMAT_FIX_SOURCE;
        if (CANDIDATE_INITIALIZER_FIX.equals(sha)) return CANDIDATE_INITIALIZER_FIX_SOURCE;
        if (CONTROL_INITIALIZER_FIX.equals(sha)) return CONTROL_INITIALIZER_FIX_SOURCE;
        if (CANDIDATE_DRIVER_FIX.equals(sha)) return CANDIDATE_DRIVER_FIX_SOURCE;
        if (CONTROL_DRIVER_FIX.equals(sha)) return CONTROL_DRIVER_FIX_SOURCE;
        return CANDIDATE.equals(sha) ? CANDIDATE_SOURCE : CONTROL_SOURCE;
    }
    public static final String ORIGINAL_SOURCE = "95e2a1eabb73a72b01422d06a6f636de85d4ee136358e16a1c762a55633cfef0";
    public static final String CANDIDATE_SOURCE = "3ea11d22e4a15618975b9bd1535b07548dd72489a6037386aca040215388f457";
    public static final String CONTROL_SOURCE = "f502e712dbfed67aa55584a6b3d7778769d4e8faa210fe18c534bf351bf4b324";
    public static final String IRIS = "3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83";
    public static final String SODIUM = "7acd7af2ef75ec883a6872c17494ead65e38025151eb08a1d60e36d34377f403";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public record RuntimePins(boolean mainMod, String loader, String minecraft, String neoforge,
            String irisSha256, String sodiumSha256) {
        public boolean exact() { return mainMod && "neoforge".equals(loader) && "1.21.1".equals(minecraft)
                && "21.1.253".equals(neoforge) && IRIS.equals(irisSha256) && SODIUM.equals(sodiumSha256); }
    }
    /** Selection facts, never driver/quality/performance support. Bootstrap writes the immutable pins. */
    public static final class Receipt {
        public int schema;
        public String experiment, originalFile, experimentalFile, originalSha256, experimentalSha256;
        public String originalSourceSha256, experimentalSourceSha256, nativeOptionsSha256;
        public String minecraft, loader, neoforge, irisSha256, sodiumSha256;
        public boolean active;
        public String originalOptionsBackup;
        public Map<String, String> baseOptions;
    }
    public interface Backend {
        String selected();
        Map<String, String> queue();
        void select(String file) throws IOException;
        void save() throws IOException;
    }
    private record Image(byte[] bytes, Set<PosixFilePermission> mode) {}
    private final Path receiptPath, originalOptions, experimentalOptions;
    private final Path originalArchive, experimentalArchive;
    private final Backend backend;
    private final String previousSelection;
    private final Map<String, String> previousQueue;
    private final Receipt receipt;
    private final Map<Path, Image> before = new LinkedHashMap<>(), after = new LinkedHashMap<>();
    private String selectedByAction;
    private boolean prepared;
    private boolean restored, reloadRecovery, reloadInProgress, writerStarted, writerObserved;
    private String expectedSelection;
    private Path reloadPath;
    private Image reloadDesired;
    private byte[] canonicalWriterAtHead;
    private Image writerBefore;

    /** No receipt/default opt-out/unknown runtime returns no private route. Corrupt admitted
     * receipt or changed archive refuses Apply before any config or selection mutation.
     */
    public static RasterExperimentSelection open(Path directory, Backend backend, boolean optIn,
            RuntimePins runtime) throws IOException {
        if (!optIn || runtime == null || !runtime.exact()) return null;
        Path dir = directory.toAbsolutePath().normalize();
        Path path = child(dir, RECEIPT);
        Image receiptImage = image(path);
        byte[] bytes = receiptImage.bytes();
        if (bytes == null) return null;
        Receipt receipt;
        try { receipt = JSON.fromJson(new String(bytes, StandardCharsets.UTF_8), Receipt.class); }
        catch (RuntimeException error) { throw new IOException("Invalid private raster receipt", error); }
        if (receipt == null) throw new IOException("Missing private raster receipt");
        if (!Objects.equals(backend.selected(), receipt.originalFile)
                && !Objects.equals(backend.selected(), receipt.experimentalFile)) return null;
        validatePins(receipt);
        Path original = child(dir, receipt.originalFile), experiment = child(dir, receipt.experimentalFile);
        if (original.equals(experiment) || !ORIGINAL.equals(hashFile(original))
                || !receipt.experimentalSha256.equals(hashFile(experiment)))
            throw new IOException("Private raster archive changed; original/candidate left untouched");
        return new RasterExperimentSelection(dir, backend, receipt, receiptImage);
    }
    private RasterExperimentSelection(Path directory, Backend backend, Receipt receipt, Image receiptImage) throws IOException {
        this.backend = backend; this.receipt = receipt;
        previousSelection = backend.selected(); previousQueue = Map.copyOf(backend.queue());
        expectedSelection = previousSelection;
        originalArchive = child(directory, receipt.originalFile);
        experimentalArchive = child(directory, receipt.experimentalFile);
        receiptPath = child(directory, RECEIPT);
        originalOptions = child(directory, receipt.originalFile + ".txt");
        experimentalOptions = child(directory, receipt.experimentalFile + ".txt");
        if (!same(image(receiptPath), receiptImage)) throw new IOException("Private raster receipt changed during admission");
        before.put(receiptPath, receiptImage); before.put(originalOptions, image(originalOptions));
        before.put(experimentalOptions, image(experimentalOptions));
    }
    private static void validatePins(Receipt r) throws IOException {
        boolean candidate = candidateArchive(r.experimentalSha256);
        if (r.schema != 1 || !"ComplementaryRasterR1".equals(r.experiment)
                || !ORIGINAL.equals(r.originalSha256) || !(candidate || controlArchive(r.experimentalSha256))
                || !ORIGINAL_SOURCE.equals(r.originalSourceSha256)
                || !sourceForArchive(r.experimentalSha256).equals(r.experimentalSourceSha256)
                || !NATIVE_OPTIONS.equals(r.nativeOptionsSha256) || !"1.21.1".equals(r.minecraft)
                || !"neoforge".equals(r.loader) || !"21.1.253".equals(r.neoforge)
                || !IRIS.equals(r.irisSha256) || !SODIUM.equals(r.sodiumSha256))
            throw new IOException("Unsupported private raster receipt pins");
        if (r.active && (r.baseOptions == null || r.originalOptionsBackup == null))
            throw new IOException("Missing private raster restoration state");
        if (r.baseOptions != null && (r.baseOptions.containsKey(OPTION) || r.baseOptions.containsKey(INTERNAL_HELPER_OPTION)))
            throw new IOException("Private option cannot be a native base option");
    }

    /** Current option maps can differ from native398: preserve them; the renderer separately
     * declines the .75 budget. This method never certifies a modified native preset.
     */
    public boolean prepare(boolean enabled, LodPreset preset, Map<String, String> effective) throws IOException {
        Objects.requireNonNull(preset);
        if (prepared) throw new IllegalStateException("Raster action already prepared");
        prepared = true;
        verifyBoundary();
        if (!backend.queue().equals(previousQueue)) throw new IOException("Private raster queue changed before Apply");
        boolean experimentalSelected = previousSelection.equals(receipt.experimentalFile);
        boolean nativeGraph = !enabled || preset == LodPreset.QUALITY;
        Map<String, String> persisted = options(before.get(
                experimentalSelected ? experimentalOptions : originalOptions).bytes());
        Map<String, String> selectedValues = new TreeMap<>();
        if (experimentalSelected) {
            selectedValues.putAll(effective); selectedValues.putAll(persisted);
        } else {
            selectedValues.putAll(persisted); selectedValues.putAll(effective);
        }
        selectedValues.putAll(previousQueue); selectedValues.remove(OPTION); selectedValues.remove(INTERNAL_HELPER_OPTION);
        Map<String, String> original = options(before.get(originalOptions).bytes());
        // No native key ownership: on return copy only explicit changes to the original sparse
        // sidecar. With no edits Off/Quality retain its exact bytes, absence and permissions.
        if (experimentalSelected) {
            if (receipt.baseOptions == null || receipt.originalOptionsBackup == null)
                throw new IOException("Experimental selection lacks original receipt state");
            mergeNativeChanges(original, selectedValues);
        }
        String target = nativeGraph ? receipt.originalFile : receipt.experimentalFile;
        Map<Path, byte[]> writes = new LinkedHashMap<>();
        if (nativeGraph) {
            if (!experimentalSelected) previousQueue.forEach((k, v) -> { if (!OPTION.equals(k) && !INTERNAL_HELPER_OPTION.equals(k)) original.put(k, v); });
            if (!original.equals(options(before.get(originalOptions).bytes())))
                writes.put(originalOptions, render(before.get(originalOptions).bytes(), original));
            if (receipt.active) {
                receipt.active = false;
                writes.put(receiptPath, JSON.toJson(receipt).getBytes(StandardCharsets.UTF_8));
            }
        } else {
            if (!experimentalSelected || !receipt.active) {
                receipt.baseOptions = new TreeMap<>(selectedValues);
                byte[] sourceBytes = before.get(originalOptions).bytes();
                receipt.originalOptionsBackup = sourceBytes == null ? "ABSENT"
                        : Base64.getEncoder().encodeToString(sourceBytes);
            }
            receipt.active = true;
            selectedValues.put(OPTION, candidateArchive(receipt.experimentalSha256) ? "1" : "0");
            byte[] prior = before.get(experimentalOptions).bytes();
            // First copy preserves original comments and formatting; future edits preserve its own.
            byte[] output = render(prior == null ? before.get(originalOptions).bytes() : prior, selectedValues);
            if (!Arrays.equals(prior, output)) writes.put(experimentalOptions, output);
            byte[] state = JSON.toJson(receipt).getBytes(StandardCharsets.UTF_8);
            if (!Arrays.equals(before.get(receiptPath).bytes(), state)) writes.put(receiptPath, state);
        }
        // Complete validation before writing. Capture each successful atomic write's afterimage
        // immediately, so partial I/O/select/save failures can restore only owned files.
        for (var entry : writes.entrySet()) {
            if (!same(image(entry.getKey()), before.get(entry.getKey())))
                throw new IOException("Private raster file changed during Apply");
            after.put(entry.getKey(), atomic(entry.getKey(), entry.getValue(), before.get(entry.getKey()).mode()));
        }
        boolean changedPack = !previousSelection.equals(target);
        previousQueue.forEach((key, value) -> backend.queue().remove(key, value));
        if (changedPack) {
            selectedByAction = target; backend.select(target); backend.save();
        }
        expectedSelection = target;
        return changedPack || !writes.isEmpty() || !previousQueue.isEmpty();
    }

    /** Revalidate every protected image, both unchanged archives and actual selected name.
     * No path that was read only at admission may silently change before Apply/reload.
     */
    private void verifyBoundary() throws IOException {
        if (!ORIGINAL.equals(hashFile(originalArchive))
                || !receipt.experimentalSha256.equals(hashFile(experimentalArchive)))
            throw new IOException("Private raster archive changed at transaction boundary");
        if (!Objects.equals(backend.selected(), expectedSelection))
            throw new IOException("Private raster selection changed at transaction boundary");
        for (var entry : before.entrySet()) {
            Image expected = after.containsKey(entry.getKey()) ? after.get(entry.getKey()) : entry.getValue();
            if (!same(image(entry.getKey()), expected))
                throw new IOException("Private raster file changed at transaction boundary: " + entry.getKey());
        }
    }
    public void beginReload(boolean recovery) throws IOException {
        verifyBoundary();
        if (!backend.queue().isEmpty()) throw new IOException("Private raster queue changed before reload");
        reloadRecovery = recovery;
        reloadPath = Objects.equals(expectedSelection, receipt.originalFile) ? originalOptions : experimentalOptions;
        reloadDesired = image(reloadPath);
        writerStarted = writerObserved = false;
        reloadInProgress = true;
    }
    /** Exact pinned writer HEAD must precede RETURN so swallowed IOException/partial truncation
     * has a witnessed mutation interval. Independent pre-writer changes stop the writer itself.
     */
    public void writerStarting(Path path, Properties values) throws IOException {
        if (!reloadInProgress || !reloadPath.equals(path.toAbsolutePath().normalize())) return;
        if (writerStarted) throw new IOException("Nested private raster Iris writer");
        verifyBoundary();
        if (!backend.queue().isEmpty()) throw new IOException("Private raster queue changed at Iris writer");
        canonicalWriterAtHead = canonical(values);
        writerBefore = image(reloadPath);
        writerStarted = true;
    }
    public void writerWritten(Path path, Properties values) throws IOException {
        if (!reloadInProgress || !reloadPath.equals(path.toAbsolutePath().normalize())) return;
        if (!writerStarted) throw new IOException("Private raster Iris writer HEAD witness missing");
        // Record the RETURN afterimage before validating completeness. The pinned writer catches
        // IOException, including a partial store, and returns; that output is still owned.
        Image actual = image(reloadPath);
        byte[] expected = canonical(values);
        writerStarted = false;
        if (actual.bytes() != null && writerBefore.bytes() != null
                && !Objects.equals(actual.mode(), writerBefore.mode()))
            throw new IOException("Private raster sidecar permissions changed during Iris writer");
        boolean compatible = values.isEmpty() ? actual.bytes() == null || same(actual, reloadDesired)
                : compatiblePartial(actual.bytes(), canonicalWriterAtHead, expected);
        if (!compatible)
            throw new IOException("Private raster Iris output has no compatible writer afterimage");
        after.put(reloadPath, actual);
        writerObserved = true;
        if (values.isEmpty()) {
            if (actual.bytes() != null) throw new IOException("Private raster Iris deletion failed");
        } else {
            if (actual.bytes() == null || !Arrays.equals(PackSelectionTransaction.propertiesBody(actual.bytes()),
                    PackSelectionTransaction.propertiesBody(expected)))
                throw new IOException("Private raster Iris writer returned incomplete or different bytes");
        }
    }
    private static byte[] canonical(Properties values) throws IOException {
        // The pinned native writer receives plain Properties. Clone the entries so fault-injection
        // subclass methods cannot change the expected serialization used to prove its output.
        var copy = new Properties(); copy.putAll(values);
        var output = new ByteArrayOutputStream(); copy.store(output, null); return output.toByteArray();
    }
    private static boolean compatiblePartial(byte[] actual, byte[] atHead, byte[] atReturn) {
        if (actual == null) return false;
        if (prefix(actual, atHead) || prefix(actual, atReturn)) return true;
        try {
            // Complete canonical timestamp may differ during an I/O interval. Validate its exact
            // native header shape, then require every available body byte to match a native prefix.
            return prefix(PackSelectionTransaction.propertiesBody(actual),
                    PackSelectionTransaction.propertiesBody(atReturn));
        } catch (IOException incompleteOrForeignHeader) { return false; }
    }
    private static boolean prefix(byte[] actual, byte[] expected) {
        if (actual.length > expected.length) return false;
        for (int i = 0; i < actual.length; i++) if (actual[i] != expected[i]) return false;
        return true;
    }
    /** A native return and every recovery reload preserve the exact desired pre-writer image.
     * Candidate success may retain Iris normalization, with its owned afterimage for rollback.
     */
    public void finishReload(boolean successful) throws IOException {
        try {
            if (writerStarted) throw new IOException("Private raster Iris writer did not return");
            verifyBoundary();
            if (successful && !writerObserved) throw new IOException("Private raster Iris writer witness missing on reload");
            if (writerObserved && (reloadRecovery || reloadPath.equals(originalOptions))) {
                Image preserved;
                if (reloadDesired.bytes() == null) {
                    Files.deleteIfExists(reloadPath); preserved = new Image(null, null);
                } else preserved = atomic(reloadPath, reloadDesired.bytes(), reloadDesired.mode());
                // When rolling back, keep the desired afterimage until restore() below has finished.
                if (!restored) after.put(reloadPath, preserved);
                else after.remove(reloadPath);
            }
        } finally { reloadInProgress = false; }
    }
    private void mergeNativeChanges(Map<String, String> original, Map<String, String> selected) throws IOException {
        Map<String, String> preimage = "ABSENT".equals(receipt.originalOptionsBackup) ? Map.of()
                : options(Base64.getDecoder().decode(receipt.originalOptionsBackup));
        Set<String> keys = new HashSet<>(receipt.baseOptions.keySet()); keys.addAll(selected.keySet());
        for (String key : keys) {
            String base = receipt.baseOptions.get(key), edit = selected.get(key);
            if (Objects.equals(base, edit)) continue;
            String current = original.get(key);
            if (!Objects.equals(preimage.get(key), current) && !Objects.equals(current, edit))
                throw new IOException("Conflicting original/raster native option: " + key);
            if (edit == null) original.remove(key); else original.put(key, edit);
        }
    }
    /** Cancel before Apply never invokes prepare. Failed Apply restores exact witnessed bytes,
     * modes, selection and captured queue; independently changed files are refused.
     */
    public void restore() throws IOException {
        IOException failure = null;
        for (var entry : after.entrySet()) try {
            if (!same(image(entry.getKey()), entry.getValue()))
                throw new IOException("Private raster file changed after Apply: " + entry.getKey());
            Image old = before.get(entry.getKey());
            if (old.bytes() == null) Files.deleteIfExists(entry.getKey());
            else atomic(entry.getKey(), old.bytes(), old.mode());
        } catch (IOException error) { failure = collect(failure, error); }
        if (selectedByAction != null) try {
            if (!Objects.equals(backend.selected(), selectedByAction))
                throw new IOException("Private raster Iris selection changed after Apply");
            backend.select(previousSelection); backend.save();
        } catch (IOException error) { failure = collect(failure, error); }
        previousQueue.forEach((key, value) -> backend.queue().putIfAbsent(key, value));
        if (failure != null) throw failure;
        after.clear(); restored = true; expectedSelection = previousSelection;
    }
    private static boolean same(Image a, Image b) {
        return Arrays.equals(a.bytes(), b.bytes()) && Objects.equals(a.mode(), b.mode());
    }
    private static Image image(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return new Image(null, null);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 1024 * 1024)
            throw new IOException("Unsafe private raster sidecar/receipt");
        Set<PosixFilePermission> mode = null;
        try { mode = Set.copyOf(Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS)); }
        catch (UnsupportedOperationException ignored) { }
        return new Image(Files.readAllBytes(path), mode);
    }
    private static Path child(Path dir, String name) throws IOException {
        if (name == null || name.isBlank() || name.equals(".") || name.equals("..") || name.contains("/")
                || name.contains("\\") || name.contains(":") || name.indexOf(0) >= 0
                || name.contains("\n") || name.contains("\r")) throw new IOException("Unsafe private raster filename");
        Path path = dir.resolve(name);
        if (Files.isSymbolicLink(path)) throw new IOException("Symlinked private raster file");
        return path;
    }
    public static String hashFile(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 256L * 1024 * 1024)
            throw new IOException("Unsafe private raster archive");
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                for (int count; (count = input.read(buffer)) != -1;) digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static Map<String, String> options(byte[] bytes) throws IOException {
        var values = new TreeMap<String, String>();
        if (bytes != null) {
            var properties = new Properties(); properties.load(new ByteArrayInputStream(bytes));
            properties.stringPropertyNames().forEach(k -> values.put(k, properties.getProperty(k)));
        }
        return values;
    }
    /** Keep unchanged property groups/comments verbatim, including continued values. */
    private static byte[] render(byte[] priorBytes, Map<String, String> desired) throws IOException {
        var prior = options(priorBytes);
        if (prior.equals(desired)) return priorBytes == null ? new byte[0] : priorBytes;
        var kept = new StringBuilder();
        if (priorBytes != null) {
            String[] lines = new String(priorBytes, StandardCharsets.ISO_8859_1).split("(?<=\\n)", -1);
            for (int i = 0; i < lines.length; i++) {
                var group = new StringBuilder(lines[i]);
                String last = lines[i].replaceFirst("[\\r\\n]+$", "");
                while (continued(last) && i + 1 < lines.length) {
                    last = lines[++i].replaceFirst("[\\r\\n]+$", ""); group.append(lines[i]);
                }
                var parsed = options(group.toString().getBytes(StandardCharsets.ISO_8859_1));
                if (parsed.isEmpty() || parsed.keySet().stream().allMatch(k -> desired.containsKey(k)
                        && Objects.equals(prior.get(k), desired.get(k)))) kept.append(group);
            }
        }
        for (var entry : new TreeMap<>(desired).entrySet()) {
            if (Objects.equals(prior.get(entry.getKey()), entry.getValue())) continue;
            if (!kept.isEmpty() && kept.charAt(kept.length() - 1) != '\n') kept.append('\n');
            var one = new Properties(); one.setProperty(entry.getKey(), entry.getValue());
            var output = new ByteArrayOutputStream(); one.store(output, null);
            String serialized = output.toString(StandardCharsets.ISO_8859_1);
            kept.append(serialized.substring(serialized.indexOf('\n') + 1));
        }
        return kept.toString().getBytes(StandardCharsets.ISO_8859_1);
    }
    private static boolean continued(String line) {
        int slashes = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) slashes++;
        return slashes % 2 == 1;
    }
    private static Image atomic(Path path, byte[] bytes, Set<PosixFilePermission> mode) throws IOException {
        Path temporary = Files.createTempFile(path.getParent(), ".shaderlod-raster-r1-", ".tmp");
        try {
            Files.write(temporary, bytes);
            if (mode != null) Files.setPosixFilePermissions(temporary, mode);
            Image written = image(temporary);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return written;
        } finally { Files.deleteIfExists(temporary); }
    }
    private static IOException collect(IOException prior, IOException next) {
        if (prior == null) return next; prior.addSuppressed(next); return prior;
    }
}

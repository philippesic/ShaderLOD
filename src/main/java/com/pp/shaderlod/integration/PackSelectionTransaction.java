package com.pp.shaderlod.integration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.mips.BslLeanGraph;
import com.pp.shaderlod.packs.PackSelectionService;
import com.pp.shaderlod.packs.SupportedPackCatalog;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.zip.ZipFile;
import net.irisshaders.iris.Iris;

/** One explicit Apply, serialized by the loader owner. Config persistence and reload stay with it.
 * File replacements are individually atomic; a process crash or partial service I/O can leave
 * prepared state. Rollback refuses to overwrite files edited after this action.
 */
public final class PackSelectionTransaction {
    public interface Backend {
        Path directory();
        String selected();
        Map<String, String> queue();
        Map<String, String> effectiveOptions();
        default boolean nativeRendererAvailable() { return false; }
        default boolean sourceReloadRequired(boolean enabled,LodPreset preset){return false;}
        default BslLeanGraph.Route routingContext() { return com.pp.shaderlod.optimization.CompileOptionControl.captureRouting(com.pp.shaderlod.optimization.OptimizationConfig.snapshot()); }
        /** Actual Iris OptionSet declarations; empty means defaults are unknown. */
        default Map<String, String> declaredDefaultOptions() { return Map.of(); }
        default KnownRuntimeBoundary.Facts runtimeFacts() { return KnownRuntimeBoundary.Facts.unknown(); }
        default RasterExperimentSelection.RuntimePins privateRasterRuntime() { return null; }
        void select(String name) throws IOException;
        void save() throws IOException;
    }
    @FunctionalInterface interface Apply {
        PackSelectionService.Result run(Path directory, String selected, boolean enabled, LodPreset preset,
                Map<String, String> effective);
    }
    private final Backend backend;
    private final Apply service;
    private final Path directory;
    private final String previousPack;
    private final Map<String, String> previousQueue;
    private final Map<Path, byte[]> before = new LinkedHashMap<>();
    private final Map<Path, byte[]> after = new LinkedHashMap<>();
    // Capture modes with every owned file before service I/O can fail. Native writer
    // witnesses remain responsible for subsequent reload writes.
    private final Map<Path, Set<PosixFilePermission>> beforeModes = new HashMap<>();
    private final Map<Path, Set<PosixFilePermission>> afterModes = new HashMap<>();
    private Path newArchive;
    private String newArchiveHash;
    private String selectedByAction;
    private boolean prepared;
    private PackSelectionService.SelectionIdentity plannedIdentity;
    private boolean historicalOriginalOnly;
    private Map<String,String> bootstrapCompileOptions=Map.of();
    public Map<String,String> bootstrapCompileOptions(){return bootstrapCompileOptions;}
    private RasterExperimentSelection privateRaster;
    private static final ThreadLocal<PackSelectionTransaction> IRIS_WRITE_OWNER = new ThreadLocal<>();
    @FunctionalInterface public interface Reload { void run() throws Exception; }
    private Path preparedInputPath;
    private String preparedInputDigest;
    private Path preparedOutputPath;
    private String preparedOutputDigest;
    private Path queuedNativePath;
    private Set<PosixFilePermission> nativeBeforeMode, nativePreparedMode;
    private Path recoveryNativePath;
    private Set<PosixFilePermission> recoveryNativeBeforeMode;
    private byte[] nativeWritten, recoveryWritten;
    private Set<PosixFilePermission> nativeWrittenMode, recoveryWrittenMode;
    private boolean nativeWriteObserved, recoveryWriteObserved, observingRecovery, pendingDeferred;
    private IOException writeWitnessFailure;
    private Map<String, String> deferredRecoveryQueue = Map.of();


    public PackSelectionTransaction(Backend backend) {
        this(backend, null);
    }
    private Apply defaultApply(Backend backend) {
        boolean nativeRendererAvailableSnapshot = backend.nativeRendererAvailable();
        var defaults = Map.copyOf(backend.declaredDefaultOptions());
        var explicit = Set.copyOf(backend.queue().keySet());
        var selection = new PackSelectionService();
        var route = backend.routingContext();
        return (directory, selected, enabled, preset, effective) -> selection.applyPlanned(
                directory, selected, enabled, preset, effective, nativeRendererAvailableSnapshot, defaults, explicit, plannedIdentity,route);
    }
    PackSelectionTransaction(Backend backend, Apply service) {
        this.backend = Objects.requireNonNull(backend);
        this.service = service == null ? defaultApply(backend) : service;
        directory = backend.directory().toAbsolutePath().normalize();
        previousPack = backend.selected();
        previousQueue = Map.copyOf(backend.queue());
    }
    public static PackSelectionTransaction iris() {
        return iris(com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.nativeGraphAvailable());
    }
    /** Caller captures availability before beginning the suspended Apply lifecycle. */
    public static PackSelectionTransaction iris(boolean nativeRendererAvailableSnapshot) {
        return iris(nativeRendererAvailableSnapshot,com.pp.shaderlod.optimization.OptimizationConfig.snapshot());
    }
    public static PackSelectionTransaction iris(boolean nativeRendererAvailableSnapshot,com.pp.shaderlod.optimization.OptimizationSnapshot next) {
        var route=com.pp.shaderlod.optimization.CompileOptionControl.captureRouting(next);
        return new PackSelectionTransaction(new Backend() {
            public BslLeanGraph.Route routingContext(){return route;}
            public boolean sourceReloadRequired(boolean enabled,LodPreset preset){
                return com.pp.shaderlod.optimization.bslearlyalpha.BslPostSampleAlpha.requiresReload(enabled,preset,next);
            }
            public boolean nativeRendererAvailable() { return nativeRendererAvailableSnapshot; }
            public Path directory() { return Iris.getShaderpacksDirectory(); }
            public String selected() { return Iris.getIrisConfig().getShaderPackName().orElse(null); }
            public Map<String, String> queue() { return Iris.getShaderPackOptionQueue(); }
            public KnownRuntimeBoundary.Facts runtimeFacts() { return KnownRuntimeBoundary.currentIrisFacts(); }
            public RasterExperimentSelection.RuntimePins privateRasterRuntime() { return RasterExperimentRuntime.current(); }
            public Map<String, String> declaredDefaultOptions() {
                var defaults = new HashMap<String, String>();
                Iris.getCurrentPack().ifPresent(pack -> {
                    var options = pack.getShaderPackOptions().getOptionSet();
                    options.getBooleanOptions().forEach((key, option) ->
                            defaults.put(key, Boolean.toString(option.getOption().getDefaultValue())));
                    options.getStringOptions().forEach((key, option) ->
                            defaults.put(key, option.getOption().getDefaultValue()));
                });
                return defaults;
            }
            public Map<String, String> effectiveOptions() {
                var effective = new HashMap<String, String>();
                Iris.getCurrentPack().ifPresent(pack -> {
                    var options = pack.getShaderPackOptions();
                    var values = options.getOptionValues();
                    options.getOptionSet().getBooleanOptions().keySet().forEach(key ->
                            effective.put(key, Boolean.toString(values.getBooleanValueOrDefault(key))));
                    options.getOptionSet().getStringOptions().keySet().forEach(key ->
                            effective.put(key, values.getStringValueOrDefault(key)));
                });
                return effective;
            }
            public void select(String name) { Iris.getIrisConfig().setShaderPackName(name); }
            public void save() throws IOException { Iris.getIrisConfig().save(); }
        });
    }

    /** Returns whether the loader needs a fresh shader graph, including pending generated edits. */
    public boolean prepare(boolean enabled) throws IOException {
        return prepare(enabled, LodPreset.PERFORMANCE);
    }
    public boolean prepare(boolean enabled, LodPreset preset) throws IOException {
        Objects.requireNonNull(preset, "preset");
        if (prepared) throw new IllegalStateException("Selection action already prepared");
        prepared = true;
        if (previousPack == null) return false;
        // Explicit receipt + unchanged archive whitelist, never broad catalog recognition.
        if (Boolean.getBoolean(RasterExperimentSelection.OPT_IN)) {
            try {
                privateRaster = RasterExperimentSelection.open(directory, new RasterExperimentSelection.Backend() {
                    public String selected() { return backend.selected(); }
                    public Map<String, String> queue() { return backend.queue(); }
                    public void select(String name) throws IOException { backend.select(name); }
                    public void save() throws IOException { backend.save(); }
                }, true, backend.privateRasterRuntime());
            } catch (IOException admissionFailure) {
                // Admission performs no mutations. Reuse the settings caller's refusal contract,
                // retaining the loaded graph without an unowned "recovery" normalization reload.
                throw new KnownRuntimeBoundary.Refusal("Private raster selection was not applied: "
                        + admissionFailure.getMessage());
            }
            if (privateRaster != null)
                return privateRaster.prepare(enabled, preset, backend.effectiveOptions());
        }
        Path selected = child(previousPack);
        JsonObject marker = marker(selected);
        var source = PackSourceReader.read(selected);
        preparedInputPath = selected; preparedInputDigest = source.sourceSha256();
        String sourceDigest = SupportedPackCatalog.shaderSourceDigest(source.memberSha256());
        if (KnownRuntimeBoundary.pinnedSource(sourceDigest)) {
            var refusal = KnownRuntimeBoundary.refusal(sourceDigest, backend.runtimeFacts());
            if (refusal.isPresent()) throw new KnownRuntimeBoundary.Refusal(refusal.get());
        }
        boolean generated = marker != null;
        if (!generated && !enabled && SupportedPackCatalog.recognize(source.memberSha256()).isEmpty()) return false;
        try {
            plannedIdentity = PackSelectionService.planSelection(directory, previousPack, enabled, preset);
        } catch (IOException failure) {
            throw new KnownRuntimeBoundary.Refusal(failure.getMessage());
        }
        if (plannedIdentity == null) {
            if (!enabled) return false;
            throw new SelectionFailure(PackSelectionService.Status.UNSUPPORTED, "No maintained adapter");
        }
        historicalOriginalOnly = plannedIdentity.historicalOriginalOnly();
        Path statePath = child(plannedIdentity.stateFile());
        String originalName = plannedIdentity.originalFile();
        String generatedName = plannedIdentity.generatedFile();
        remember(statePath);
        remember(child(originalName + ".txt"));
        Set<PosixFilePermission> originalOptionsMode = mode(child(originalName + ".txt"));
        Set<PosixFilePermission> previousOptionsMode = mode(child(previousPack + ".txt"));
        if (generatedName != null) {
            remember(child(generatedName + ".txt"));
            Path archive = child(generatedName);
            if (!Files.exists(archive, LinkOption.NOFOLLOW_LINKS)) newArchive = archive;
        }
        var effective = new HashMap<>(backend.effectiveOptions());
        effective.putAll(previousQueue);
        PackSelectionService.Result result;
        try {
            // Service intentionally ignores generated effective values. Persist its pending edits
            // before Off's three-way merge, preserving comments and unrelated properties.
            if (generated && !historicalOriginalOnly && !previousQueue.isEmpty()) {
                Path sidecar = child(previousPack + ".txt");
                remember(sidecar);
                var properties = new Properties(); properties.putAll(previousQueue);
                var writer = new StringWriter(); properties.store(writer, null);
                String serialized = writer.toString();
                byte[] prior = before.get(sidecar);
                String prefix = prior == null ? "" : new String(prior, StandardCharsets.ISO_8859_1);
                atomic(sidecar, (prefix + "\n" + serialized.substring(serialized.indexOf('\n') + 1))
                        .getBytes(StandardCharsets.ISO_8859_1));
            }
            result = service.run(directory, previousPack, enabled, preset, effective);
        } finally {
            // Exact planned files only, including service partial-I/O failures.
            for (Path path : before.keySet()) {
                after.put(path, bytes(path));
                afterModes.put(path, mode(path));
            }
            if (newArchive != null && Files.isRegularFile(newArchive, LinkOption.NOFOLLOW_LINKS)) {
                // Only a complete archive matching this action's recorded state is owned.
                byte[] stateBytes = after.get(statePath);
                if (stateBytes != null) {
                    JsonObject state = json(stateBytes);
                    String expected = string(state, "generatedSha256");
                    if (hashFile(newArchive).equals(expected)) newArchiveHash = expected;
                }
            }
        }
        if (!result.accepted()) {
            if (!enabled && result.status() == PackSelectionService.Status.UNSUPPORTED) return false;
            throw new SelectionFailure(result.status(), result.detail());
        }
        boolean changedPack = !previousPack.equals(result.packFile());
        // Issued only after exact G03 route/native-map/ancestry preparation. The queue
        // activation remains owned by this selection and must pass actual post-load proof.
        if(result.requiresReload() && Objects.equals(generatedName,result.packFile()) && !result.firstLoadCompileOptions().isEmpty())
            bootstrapCompileOptions=result.firstLoadCompileOptions();
        if(result.requiresReload()&&"bsl-shaders".equals(result.adapterId())&&generatedName.equals(result.packFile())) {
            var zeros=new HashMap<String,String>();
            for(var id:com.pp.shaderlod.optimization.OptimizationId.values())
                for(String key:com.pp.shaderlod.optimization.CompileOptionControl.controlledOptions(id))
                    if(key.startsWith("SHADERLOD_BSL_"))zeros.put(key,"0");
            bootstrapCompileOptions=Map.copyOf(zeros);
        }
        if (!historicalOriginalOnly && (changedPack || generated && !previousQueue.isEmpty())) {
            // Consume only the captured values; preserve any independently queued new edits.
            previousQueue.forEach((key, value) -> backend.queue().remove(key, value));
        }
        // An accepted original graph, including Quality for every maintained family, owns
        // its exact native Iris writer output. Reused generated archives also report
        // generatedArchive=false, so the original filename is an essential boundary.
        boolean nativeGraph = result.adapterId() != null && maintained(result.adapterId()) && !result.generatedArchive()
                && originalName.equals(result.packFile());
        if (result.adapterId() != null && maintained(result.adapterId())) {
            // Both forward loads own the actual Iris writer on the selected graph sidecar.
            queuedNativePath = child(result.packFile() + ".txt");
            nativeBeforeMode = beforeModes.get(queuedNativePath);
            nativePreparedMode = mode(queuedNativePath);
            if (changedPack) {
                recoveryNativePath = child(previousPack + ".txt");
                recoveryNativeBeforeMode = previousOptionsMode;
            }
        }
        if (changedPack) {
            selectedByAction = result.packFile();
            backend.select(selectedByAction);
            backend.save();
        }
        preparedOutputPath = child(result.packFile());
        preparedOutputDigest = PackSourceReader.read(preparedOutputPath).sourceSha256();
        return backend.sourceReloadRequired(enabled,preset) || result.requiresReload() || generated && !previousQueue.isEmpty()
                || enabled && nativeGraph && !previousQueue.isEmpty();
    }

    /** Scope owned native reloads; the pinned Iris writer witnesses its own output. */
    public void reload(Reload action) throws Exception {
        if (privateRaster != null) { reloadPrivate(action, false); return; }
        verifyPreparedSelection();
        if (queuedNativePath == null) { action.run(); verifyPreparedSelection(); return; }
        if (!Arrays.equals(bytes(queuedNativePath), after.get(queuedNativePath))
                || !Objects.equals(mode(queuedNativePath), nativePreparedMode))
            throw new IOException("Native options changed before Iris reload");
        if (historicalOriginalOnly) {
            var deferred = Map.copyOf(backend.queue());
            // Only captured intent is safely attributable to this historical selection.
            if (!deferred.equals(previousQueue)) throw new IOException("Queued options changed before historical restoration");
            deferred.forEach((key, value) -> backend.queue().remove(key, value));
            try { observeReload(action, false); }
            finally {
                try {
                    if (nativeWriteObserved) {
                        restoreNativePreimage(nativeWritten, nativeWrittenMode);
                        after.put(queuedNativePath, before.get(queuedNativePath));
                        afterModes.put(queuedNativePath, beforeModes.get(queuedNativePath));
                        nativePreparedMode = beforeModes.get(queuedNativePath);
                        nativeWriteObserved = false;
                    }
                } finally { deferred.forEach((key, value) -> backend.queue().putIfAbsent(key, value)); }
            }
        } else observeReload(action, false);
        verifyPreparedSelection();
    }
    /** Before/after each forward load: retain only this action's source, selection, files and exact witnessed writes. */
    public void verifyPreparedSelection() throws IOException {
        if (privateRaster != null) return;
        String expected = selectedByAction == null ? previousPack : selectedByAction;
        if (!Objects.equals(backend.selected(), expected)) throw new IOException("Iris selection changed during compile Apply");
        if (preparedInputPath != null && !preparedInputDigest.equals(PackSourceReader.read(preparedInputPath).sourceSha256()))
            throw new IOException("Input shader source changed during compile Apply");
        if (preparedOutputPath != null && !preparedOutputDigest.equals(PackSourceReader.read(preparedOutputPath).sourceSha256()))
            throw new IOException("Selected output shader source changed during compile Apply");
        if (newArchiveHash != null && !newArchiveHash.equals(hashFile(newArchive)))
            throw new IOException("Prepared shader source changed during compile Apply");
        for (var entry : after.entrySet())
            if (!Arrays.equals(bytes(entry.getKey()), entry.getValue()) || !Objects.equals(mode(entry.getKey()), afterModes.get(entry.getKey())))
                throw new IOException("Owned selection/options changed during compile Apply: " + entry.getKey());
    }
    private void reloadPrivate(Reload action, boolean recovery) throws Exception {
        privateRaster.beginReload(recovery);
        boolean successful = false;
        try { observeReload(action, recovery); successful = true; }
        finally { privateRaster.finishReload(successful); }
    }
    private void observeReload(Reload action, boolean recovery) throws Exception {
        if (IRIS_WRITE_OWNER.get() != null) throw new IllegalStateException("Nested Iris option transaction");
        observingRecovery = recovery; writeWitnessFailure = null;
        IRIS_WRITE_OWNER.set(this);
        try {
            action.run();
            if (writeWitnessFailure != null) throw writeWitnessFailure;
        } finally {
            IRIS_WRITE_OWNER.remove(); observingRecovery = false;
        }
    }
    /** Called only at the exact pinned Iris writer RETURN; idle/other reloads do no I/O. */
    public static void irisOptionsWriteStarted(Path path, Properties values) {
        var owner = IRIS_WRITE_OWNER.get();
        if (owner == null || owner.privateRaster == null) return;
        try { owner.privateRaster.writerStarting(path, values); }
        catch (IOException failure) {
            owner.writeWitnessFailure = failure;
            throw new IllegalStateException("Private raster Iris writer boundary refused", failure);
        }
    }
    public static void irisOptionsWritten(Path path, Properties values) {
        var owner = IRIS_WRITE_OWNER.get();
        if (owner != null && owner.privateRaster != null) {
            try { owner.privateRaster.writerWritten(path, values); }
            catch (IOException failure) { owner.writeWitnessFailure = failure; }
            return;
        }
        if (owner == null || owner.queuedNativePath == null || !owner.queuedNativePath.equals(path.toAbsolutePath().normalize())) return;
        try {
            byte[] written = bytes(owner.queuedNativePath);
            if (values.isEmpty()) {
                if (written != null) throw new IOException("Iris option deletion was not observed");
            } else {
                var output = new ByteArrayOutputStream(); values.store(output, null);
                if (written == null || !Arrays.equals(propertiesBody(written), propertiesBody(output.toByteArray())))
                    throw new IOException("Native options differ from the witnessed Iris writer");
            }
            var writtenMode = mode(owner.queuedNativePath);
            if (owner.observingRecovery) {
                owner.recoveryWritten = written; owner.recoveryWrittenMode = writtenMode;
                owner.recoveryWriteObserved = true;
            } else {
                owner.nativeWritten = written; owner.nativeWrittenMode = writtenMode;
                owner.nativeWriteObserved = true;
                owner.after.put(owner.queuedNativePath, written);
                owner.afterModes.put(owner.queuedNativePath, writtenMode);
                owner.nativePreparedMode = writtenMode; // The second load validates the first writer output, not the first prepare output.
            }
        } catch (IOException error) { owner.writeWitnessFailure = error; }
    }
    static byte[] propertiesBody(byte[] value) throws IOException {
        int end = -1;
        for (int i = 0; i < value.length; i++) if (value[i] == '\n') { end = i; break; }
        if (end < 0 || value.length == 0 || value[0] != '#')
            throw new IOException("Missing canonical Iris Properties header");
        String header = new String(value, 0, end, StandardCharsets.ISO_8859_1);
        if (header.endsWith("\r")) header = header.substring(0, header.length() - 1);
        if (!header.matches("#[A-Za-z]{3} [A-Za-z]{3} [ 0-9][0-9] [0-9:]{8} .+ [0-9]{4}"))
            throw new IOException("Unexpected Iris Properties header");
        return Arrays.copyOfRange(value, end + 1, value.length);
    }
    private static Set<PosixFilePermission> mode(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null;
        try { return Set.copyOf(Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS)); }
        catch (UnsupportedOperationException unavailable) { return null; }
    }
    private void restoreNativePreimage(byte[] witnessed, Set<PosixFilePermission> witnessedMode) throws IOException {
        if (!Arrays.equals(bytes(queuedNativePath), witnessed)
                || !Objects.equals(mode(queuedNativePath), witnessedMode))
            throw new IOException("Native options changed after the Iris write");
        byte[] prior = before.get(queuedNativePath);
        if (prior == null) Files.deleteIfExists(queuedNativePath);
        else {
            atomic(queuedNativePath, prior);
            if (nativeBeforeMode != null) Files.setPosixFilePermissions(queuedNativePath, nativeBeforeMode);
        }
    }
    /** Restore files/selection first, keeping rejected native edits out of the recovery reload. */
    public void restoreForRecovery() throws IOException {
        if (privateRaster != null) {
            pendingDeferred = true;
            try { privateRaster.restore(); }
            finally {
                deferredRecoveryQueue = Map.copyOf(backend.queue());
                deferredRecoveryQueue.forEach((key, value) -> backend.queue().remove(key, value));
            }
            return;
        }
        // Preparation can fail before the native writer path is published. That still
        // must not let a recovery reload consume captured or newer queued user intent.
        pendingDeferred = true;
        try {
            if (queuedNativePath != null) {
                if (writeWitnessFailure != null) throw writeWitnessFailure;
                if (!nativeWriteObserved && (!Arrays.equals(bytes(queuedNativePath), after.get(queuedNativePath))
                        || !Objects.equals(mode(queuedNativePath), nativePreparedMode)))
                    throw new IOException("Native options changed without an Iris write witness");
            }
            restore();
        } finally {
            // Defer newer independent intent too: recovery must load the prior valid graph,
            // and consuming newer values here would let older captured edits replace them later.
            deferredRecoveryQueue = Map.copyOf(backend.queue());
            deferredRecoveryQueue.forEach((key, value) -> backend.queue().remove(key, value));
        }
    }
    /** The valid native reload can canonicalize its file too; restore only that witnessed output. */
    public void recoveryReload(Reload action) throws Exception {
        if (privateRaster != null) { reloadPrivate(action, true); return; }
        if (queuedNativePath == null) { action.run(); return; }
        Path forwardPath = queuedNativePath;
        Set<PosixFilePermission> forwardMode = nativeBeforeMode;
        if (recoveryNativePath != null) {
            queuedNativePath = recoveryNativePath;
            nativeBeforeMode = recoveryNativeBeforeMode;
        }
        try {
            observeReload(action, true);
        } finally {
            try {
                if (writeWitnessFailure != null) throw writeWitnessFailure;
                if (recoveryWriteObserved) restoreNativePreimage(recoveryWritten, recoveryWrittenMode);
                else if (!Arrays.equals(bytes(queuedNativePath), before.get(queuedNativePath))
                        || !Objects.equals(mode(queuedNativePath), nativeBeforeMode))
                    throw new IOException("Recovery options changed without an Iris write witness");
            } finally {
                queuedNativePath = forwardPath; nativeBeforeMode = forwardMode;
            }
        }
    }
    public boolean requiresOwnedNativeRecovery() { return queuedNativePath != null || privateRaster != null; }
    public void finishRecovery() {
        if (pendingDeferred) {
            deferredRecoveryQueue.forEach((key, value) -> backend.queue().putIfAbsent(key, value));
            previousQueue.forEach((key, value) -> backend.queue().putIfAbsent(key, value));
            pendingDeferred = false;
            deferredRecoveryQueue = Map.of();
        }
    }

    /** Best-effort rollback of only owned changes; concurrent edits and partial ZIPs stay intact. */
    public void restore() throws IOException {
        if (privateRaster != null) { privateRaster.restore(); return; }
        IOException failure = null;
        if (nativeWriteObserved) {
            try { restoreNativePreimage(nativeWritten, nativeWrittenMode); }
            catch (IOException error) { failure = collect(failure, error); }
        }
        for (var entry : after.entrySet()) {
            if (nativeWriteObserved && entry.getKey().equals(queuedNativePath)) continue;
            Path path = entry.getKey();
            byte[] prior = before.get(path), written = entry.getValue();
            Set<PosixFilePermission> priorMode = beforeModes.get(path), writtenMode = afterModes.get(path);
            if (Arrays.equals(prior, written) && Objects.equals(priorMode, writtenMode)) continue;
            try {
                if (!Arrays.equals(bytes(path), written) || !Objects.equals(mode(path), writtenMode))
                    throw new IOException("Selection file or permissions changed after Apply: " + path);
                if (prior == null) Files.deleteIfExists(path); else {
                    if (!Arrays.equals(prior, written)) atomic(path, prior);
                    if (priorMode != null) Files.setPosixFilePermissions(path, priorMode);
                }
            } catch (IOException error) { failure = collect(failure, error); }
        }
        if (newArchiveHash != null) {
            try {
                if (Files.exists(newArchive, LinkOption.NOFOLLOW_LINKS)) {
                    if (!Files.isRegularFile(newArchive, LinkOption.NOFOLLOW_LINKS)
                            || !hashFile(newArchive).equals(newArchiveHash))
                        throw new IOException("Generated archive changed after Apply: " + newArchive);
                    Files.delete(newArchive);
                }
            } catch (IOException error) { failure = collect(failure, error); }
        }
        if (selectedByAction != null) {
            try {
                if (!Objects.equals(backend.selected(), selectedByAction))
                    throw new IOException("Iris selection changed after Apply");
                backend.select(previousPack); backend.save();
            } catch (IOException error) { failure = collect(failure, error); }
        }
        // A failed Iris.reload can consume the original queue even when prepare kept it intact.
        previousQueue.forEach((key, value) -> backend.queue().putIfAbsent(key, value));
        if (failure != null) throw failure;
    }
    public static String failureMessage(Exception failure) {
        if (KnownRuntimeBoundary.isRefusal(failure)) return failure.getMessage();
        if (failure instanceof SelectionFailure selection) return selection.status == PackSelectionService.Status.UNSUPPORTED
                ? "LOD is not available for this shader version yet."
                : "Could not apply LOD to this shader. Choose the original pack; see the game log.";
        return "Could not load these settings. Previous settings were restored where possible; see the game log.";
    }
    private static final class SelectionFailure extends IOException {
        final PackSelectionService.Status status;
        SelectionFailure(PackSelectionService.Status status, String detail) { super(detail); this.status = status; }
    }
    private void remember(Path path) throws IOException {
        if (!before.containsKey(path)) {
            byte[] prior = bytes(path);
            Set<PosixFilePermission> priorMode = mode(path);
            before.put(path, prior); beforeModes.put(path, priorMode);
        }
    }
    private Path child(String name) throws IOException {
        if (name == null || name.isBlank() || name.contains("/") || name.contains("\\")
                || name.contains(":") || name.indexOf(0) >= 0 || name.equals(".") || name.equals(".."))
            throw new IOException("Unsafe selection filename");
        Path path = directory.resolve(name);
        if (Files.isSymbolicLink(path)) throw new IOException("Symlinked selection file");
        return path;
    }
    private static byte[] bytes(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 1024 * 1024)
            throw new IOException("Unsafe selection sidecar/state: " + path);
        return Files.readAllBytes(path);
    }
    private static JsonObject marker(Path path) throws IOException {
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Selected shader archive is missing");
        try (var zip = new ZipFile(path.toFile())) {
            var entry = zip.getEntry("shaderlod-selection.json");
            if (entry == null) return null;
            try (var input = zip.getInputStream(entry)) {
                byte[] data = input.readNBytes(1024 * 1024 + 1);
                if (data.length > 1024 * 1024) throw new IOException("Oversized selection marker");
                return json(data);
            }
        }
    }
    private static JsonObject json(byte[] data) throws IOException {
        try {
            if (data == null) throw new IllegalArgumentException("Missing selection state");
            return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException error) { throw new IOException("Invalid selection state", error); }
    }
    private static String string(JsonObject json, String key) throws IOException {
        try { return json.get(key).getAsString(); }
        catch (RuntimeException error) { throw new IOException("Missing selection identity: " + key, error); }
    }
    private static boolean maintained(String id) {
        return java.util.Set.of("photon", "complementary-reimagined", "complementary-unbound", "makeup-ultra-fast-shaders", "sildurs-vibrant-shaders", "bsl-shaders", "bliss-shader", "solas-shader", "astralex").contains(id);
    }
    private static String hash(byte[] bytes) { return HexFormat.of().formatHex(digest().digest(bytes)); }
    private static String hashFile(Path path) throws IOException {
        var digest = digest();
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static void atomic(Path path, byte[] data) throws IOException {
        Path temp = Files.createTempFile(path.getParent(), ".shaderlod-selection-", ".tmp");
        try { Files.write(temp, data); Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(temp); }
    }
    private static IOException collect(IOException previous, IOException next) {
        if (previous == null) return next;
        previous.addSuppressed(next); return previous;
    }
}

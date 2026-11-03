package com.pp.shaderlod.dev;

import com.google.gson.*;
import com.pp.shaderlod.*;
import com.pp.shaderlod.integration.PackSelectionTransaction;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.function.*;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;

/** Untimed explicit development fault after a real Iris reload; no production hooks or compiler defect. */
final class SettingsFailedApplyProbe {
    private SettingsFailedApplyProbe() {}
    static void run(Minecraft client) throws Exception {
        if (client.level == null || client.player == null || client.screen != null
                || !ShaderLodConfig.ENABLED.get() || ShaderLodConfig.PRESET.get() != LodPreset.QUALITY
                || !Iris.getShaderPackOptionQueue().isEmpty())
            throw new IllegalStateException("Failed Apply probe requires world, enabled Quality and empty queue");
        var loaded = WorkloadInputs.effectiveOptions();
        var nativeValues = new TreeMap<String,String>(); loaded.entrySet().forEach(e -> nativeValues.put(e.getKey(), e.getValue().getAsString()));
        if (!com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.optionsMatch(nativeValues)
                || !"2".equals(nativeValues.get("AA_TYPE")))
            throw new IllegalStateException("Failed Apply probe requires the exact native100 baseline with AA_TYPE=2");
        var option = Iris.getCurrentPack().orElseThrow().getShaderPackOptions().getOptionSet().getStringOptions().get("AA_TYPE");
        if (option == null || !option.getOption().getAllowedValues().contains("0"))
            throw new IllegalStateException("AA_TYPE=0 is not a declared legal native option");
        var before = snapshot(client);
        var report = new JsonObject(); report.addProperty("probe", "failedApplyProbe");
        report.addProperty("fault", "synthetic IOException after first completed real Iris.reload");
        report.add("before", before); var reloadEvidence = new JsonArray(); report.add("realReloads", reloadEvidence);
        int[] completed = {0}; boolean[] injected = {false}; boolean syntheticQueued = false;
        Exception failure = null; boolean applied = true;
        try {
            Class<?> reloadType = Class.forName("com.pp.shaderlod.ShaderLodSettings$Reload", true, ShaderLodSettings.class.getClassLoader());
            Method apply = ShaderLodSettings.class.getDeclaredMethod("apply", boolean.class, LodPreset.class,
                    Supplier.class, reloadType, BooleanSupplier.class);
            if (!apply.trySetAccessible()) throw new IllegalAccessException("Main module does not open package-private ShaderLodSettings.apply to the dev probe");
            Object reload = Proxy.newProxyInstance(reloadType.getClassLoader(), new Class<?>[]{reloadType}, (proxy, method, args) -> {
                if (!method.getName().equals("run")) return switch (method.getName()) {
                    case "toString" -> "ShaderLOD explicit failed Apply probe";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new IllegalStateException("Unexpected reload proxy method");
                };
                Iris.reload(); completed[0]++;
                var evidence = new JsonObject(); evidence.addProperty("number", completed[0]);
                evidence.addProperty("fallback", Iris.isFallback()); evidence.addProperty("loadedPack", Iris.getCurrentPackName());
                evidence.add("loadedOptions", WorkloadInputs.effectiveOptions());
                var pipeline = Iris.getPipelineManager().getPipeline().orElseThrow();
                evidence.addProperty("pipelineClass", pipeline.getClass().getName());
                evidence.addProperty("pipelineIdentity", System.identityHashCode(pipeline));
                evidence.add("sidecar", file(Iris.getShaderpacksDirectory().resolve(Iris.getCurrentPackName() + ".txt")));
                reloadEvidence.add(evidence);
                if (completed[0] == 1) {
                    if (Iris.isFallback() || !"0".equals(WorkloadInputs.effectiveOptions().get("AA_TYPE").getAsString())
                            || !Iris.getShaderPackOptionQueue().isEmpty())
                        throw new IllegalStateException("Real first reload did not consume AA_TYPE=0 successfully");
                    var properties = new Properties();
                    Path sidecar = Iris.getShaderpacksDirectory().resolve(Iris.getCurrentPackName() + ".txt");
                    try (var input = Files.newInputStream(sidecar)) { properties.load(input); }
                    String bytes = Files.readString(sidecar, java.nio.charset.StandardCharsets.ISO_8859_1);
                    if (!"0".equals(properties.getProperty("AA_TYPE")) || !bytes.startsWith("#")
                            || before.getAsJsonObject("optionFile").equals(evidence.getAsJsonObject("sidecar")))
                        throw new IllegalStateException("Real canonical option-file write was not observed");
                    injected[0] = true;
                    throw new IOException("Explicit development fault after real canonical write and completed Iris reload");
                }
                return null;
            });
            Iris.getShaderPackOptionQueue().put("AA_TYPE", "0"); syntheticQueued = true;
            applied = (Boolean) apply.invoke(null, true, LodPreset.PERFORMANCE,
                    (Supplier<PackSelectionTransaction>) PackSelectionTransaction::iris, reload,
                    (BooleanSupplier) Iris::isFallback);
            var recovered = snapshot(client); report.add("afterRecoveryBeforeQueueCleanup", recovered);
            report.addProperty("applyReturned", applied); report.addProperty("faultInjected", injected[0]);
            report.addProperty("completedRealReloads", completed[0]);
            var expected = before.deepCopy(); expected.add("queue", new Gson().toJsonTree(Map.of("AA_TYPE", "0")));
            if (applied || !injected[0] || completed[0] != 2 || !expected.equals(recovered) || Iris.isFallback())
                throw new IllegalStateException("Failed Apply did not restore exact config/source/options/modes and rejected queue");
        } catch (Exception error) {
            failure = error instanceof InvocationTargetException wrapped && wrapped.getCause() instanceof Exception cause ? cause : error;
            report.addProperty("failure", failure.toString());
        } finally {
            if (syntheticQueued) {
                var queue = Iris.getShaderPackOptionQueue();
                boolean owned = queue.equals(Map.of("AA_TYPE", "0")); report.addProperty("syntheticQueueOwned", owned);
                if (owned) report.addProperty("syntheticQueueRemoved", queue.remove("AA_TYPE", "0"));
                else {
                    report.addProperty("cleanupRefused", "Queue differs from the single owned synthetic edit");
                    if (failure == null) failure = new IllegalStateException("Refusing concurrent queue edit overwrite");
                }
            }
        }
        var after = snapshot(client); report.add("afterOwnedQueueCleanup", after);
        boolean preserved = before.equals(after); report.addProperty("preservedAfterCleanup", preserved);
        report.addProperty("passed", failure == null && preserved);
        Path output = client.gameDirectory.toPath().resolve("shaderlod-failed-apply-probe.json");
        Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
        Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        com.mojang.logging.LogUtils.getLogger().info("ShaderLOD real reload failed Apply probe: passed={}, realReloads={}, report={}", failure == null && preserved, completed[0], output);
        if (failure != null) throw failure;
        if (!preserved) throw new IllegalStateException("Probe cleanup did not retain the original state");
    }
    private static JsonObject snapshot(Minecraft client) throws Exception {
        var json = new Gson(); var result = new JsonObject();
        result.addProperty("enabled", ShaderLodConfig.ENABLED.get()); result.addProperty("preset", ShaderLodConfig.PRESET.get().name());
        result.add("core", json.toJsonTree(Map.ofEntries(
                Map.entry("near", ShaderLodConfig.NEAR.get()), Map.entry("far", ShaderLodConfig.FAR.get()), Map.entry("transition", ShaderLodConfig.TRANSITION.get()),
                Map.entry("debug", ShaderLodConfig.DEBUG.get()), Map.entry("terrainEnabled", ShaderLodConfig.TERRAIN_ENABLED.get()),
                Map.entry("terrainChunkCap", ShaderLodConfig.TERRAIN_VANILLA_CHUNK_CAP.get()), Map.entry("warmSourceConfirmed", ShaderLodConfig.TERRAIN_WARM_SOURCE_CONFIRMED.get()),
                Map.entry("terrainWarmupSeconds", ShaderLodConfig.TERRAIN_WARMUP_SECONDS.get()), Map.entry("optimizeShadowCulling", ShaderLodConfig.OPTIMIZE_SHADOW_CULLING.get()),
                Map.entry("separateShadowVisibilityLists", ShaderLodConfig.SEPARATE_SHADOW_VISIBILITY_LISTS.get()), Map.entry("shadowFrustumCoherence", ShaderLodConfig.SHADOW_FRUSTUM_COHERENCE.get()))));
        result.add("effects", json.toJsonTree(ShaderLodEffectConfig.snapshot()));
        String selected = Iris.getIrisConfig().getShaderPackName().orElseThrow(); result.addProperty("selected", selected);
        result.addProperty("loadedPack", Iris.getCurrentPackName()); result.addProperty("fallback", Iris.isFallback());
        result.addProperty("sourceSha256", com.pp.shaderlod.integration.PackSourceReader.read(Iris.getShaderpacksDirectory().resolve(selected)).sourceSha256());
        result.add("queue", json.toJsonTree(new TreeMap<>(Iris.getShaderPackOptionQueue()))); result.add("loadedOptions", WorkloadInputs.effectiveOptions());
        result.add("optionFile", file(Iris.getShaderpacksDirectory().resolve(selected + ".txt")));
        result.add("coreConfigFile", file(client.gameDirectory.toPath().resolve("config/shaderlod-client.toml")));
        return result;
    }
    private static JsonObject file(Path path) throws Exception {
        var result = new JsonObject(); boolean present = Files.exists(path, LinkOption.NOFOLLOW_LINKS); result.addProperty("present", present);
        if (present) {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 65536)
                throw new IllegalStateException("Probe file must be ordinary and at most 64 KiB: " + path);
            result.addProperty("bytesBase64", Base64.getEncoder().encodeToString(Files.readAllBytes(path)));
            try { result.addProperty("mode", PosixFilePermissions.toString(Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS))); }
            catch (UnsupportedOperationException unavailable) { result.addProperty("mode", "UNAVAILABLE"); }
        }
        return result;
    }
}

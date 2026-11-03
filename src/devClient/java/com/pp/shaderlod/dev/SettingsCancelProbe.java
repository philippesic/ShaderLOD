package com.pp.shaderlod.dev;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.pp.shaderlod.ShaderLodConfig;
import com.pp.shaderlod.ShaderLodSettingsScreen;
import java.nio.file.*;
import java.util.*;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.neoforged.fml.ModList;

/** Explicit untimed development inspection of the real screen callbacks. Never calls Apply. */
final class SettingsCancelProbe {
    private SettingsCancelProbe() {}
    static void run(Minecraft client, String packId) throws Exception {
        if (client.level == null || client.player == null || client.screen != null)
            throw new IllegalStateException("Cancel settings probe requires the world with no open screen");
        var before = snapshot();
        var report = new JsonObject(); report.addProperty("probe", "cancelSettingsProbe");
        report.addProperty("packId", packId); report.addProperty("untimed", true);
        report.add("before", before);
        var screen = new ShaderLodSettingsScreen(ModList.get().getModContainerById("shaderlod").orElseThrow(), null);
        Exception failure = null;
        try {
            client.setScreen(screen); // Initializes the actual current main-mod screen and its widgets.
            report.addProperty("actualScreen", client.screen.getClass().getName());
            var cycles = new ArrayList<CycleButton<?>>(); Button cancel = null;
            for (var child : screen.children()) {
                if (child instanceof CycleButton<?> cycle) cycles.add(cycle);
                if (child instanceof Button button && button.getMessage().getString().equals("Cancel")) cancel = button;
            }
            if (cycles.size() != 2 || cancel == null || !(cycles.get(0).getValue() instanceof Boolean)
                    || !(cycles.get(1).getValue() instanceof com.pp.shaderlod.LodPreset))
                throw new IllegalStateException("Unexpected real settings controls");
            Object priorEnabled = cycles.get(0).getValue(), priorPreset = cycles.get(1).getValue();
            cycles.get(0).onPress(); cycles.get(1).onPress();
            report.addProperty("stagedEnabled", (Boolean) cycles.get(0).getValue());
            report.addProperty("stagedPreset", cycles.get(1).getValue().toString());
            if (Objects.equals(priorEnabled, cycles.get(0).getValue())
                    || Objects.equals(priorPreset, cycles.get(1).getValue()))
                throw new IllegalStateException("Actual cycle buttons did not change their staged values");
            cancel.onPress(); report.addProperty("actualCancelPressed", true);
        } catch (Exception error) {
            failure = error; report.addProperty("failure", error.toString());
        } finally {
            if (client.screen == screen) client.setScreen(null);
        }
        var after = snapshot(); report.add("after", after);
        boolean preserved = before.equals(after) && client.screen == null;
        report.addProperty("returnedToWorld", client.screen == null);
        report.addProperty("preserved", preserved);
        report.addProperty("passed", failure == null && preserved);
        Path output = client.gameDirectory.toPath().resolve("shaderlod-cancel-settings-probe.json");
        Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
        Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        com.mojang.logging.LogUtils.getLogger().info("ShaderLOD actual Cancel settings probe: passed={}, preserved={}, report={}",
                failure == null && preserved, preserved, output);
        if (failure != null) throw failure;
        if (!preserved) throw new IllegalStateException("Cancel changed persisted settings, selection, queue or loaded options");
    }
    private static JsonObject snapshot() throws Exception {
        var json = new GsonBuilder().create(); var result = new JsonObject();
        result.addProperty("enabled", ShaderLodConfig.ENABLED.get());
        result.addProperty("preset", ShaderLodConfig.PRESET.get().name());
        result.add("core", json.toJsonTree(Map.ofEntries(
                Map.entry("near", ShaderLodConfig.NEAR.get()), Map.entry("far", ShaderLodConfig.FAR.get()),
                Map.entry("transition", ShaderLodConfig.TRANSITION.get()), Map.entry("debug", ShaderLodConfig.DEBUG.get()),
                Map.entry("terrainEnabled", ShaderLodConfig.TERRAIN_ENABLED.get()),
                Map.entry("terrainChunkCap", ShaderLodConfig.TERRAIN_VANILLA_CHUNK_CAP.get()),
                Map.entry("warmSourceConfirmed", ShaderLodConfig.TERRAIN_WARM_SOURCE_CONFIRMED.get()),
                Map.entry("terrainWarmupSeconds", ShaderLodConfig.TERRAIN_WARMUP_SECONDS.get()),
                Map.entry("optimizeShadowCulling", ShaderLodConfig.OPTIMIZE_SHADOW_CULLING.get()),
                Map.entry("separateShadowVisibilityLists", ShaderLodConfig.SEPARATE_SHADOW_VISIBILITY_LISTS.get()),
                Map.entry("shadowFrustumCoherence", ShaderLodConfig.SHADOW_FRUSTUM_COHERENCE.get()))));
        result.add("effects", json.toJsonTree(com.pp.shaderlod.ShaderLodEffectConfig.snapshot()));
        String selected = Iris.getIrisConfig().getShaderPackName().orElse("");
        result.addProperty("selected", selected); result.addProperty("loadedPack", Iris.getCurrentPackName());
        result.add("queue", json.toJsonTree(new TreeMap<>(Iris.getShaderPackOptionQueue())));
        result.add("loadedOptions", WorkloadInputs.effectiveOptions());
        Path sidecar = Iris.getShaderpacksDirectory().resolve(selected + ".txt");
        if (Files.exists(sidecar, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(sidecar, LinkOption.NOFOLLOW_LINKS) || Files.size(sidecar) > 65536)
                throw new IllegalStateException("Cancel probe sidecar must be an ordinary bounded file");
            result.addProperty("optionBytesBase64", Base64.getEncoder().encodeToString(Files.readAllBytes(sidecar)));
        } else result.addProperty("optionBytesBase64", "ABSENT");
        return result;
    }
}

package com.pp.shaderlod.dev;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;

/** Retained actual original option producer output; no Minecraft/client or GL calls. */
public final class OriginalNullControlGuardTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var report = JsonParser.parseString(Files.readString(Path.of("run-workloads/native-stability-shared-host-r2/native/workload-measurement.json"))).getAsJsonObject();
        var state = new JsonObject();
        state.add("actual_shader_sha256", report.get("actual_shader_sha256").deepCopy());
        state.add("effective_shader_options", report.get("effective_shader_options").deepCopy());
        state.add("effective_shader_options_sha256", report.get("effective_shader_options_sha256").deepCopy());
        WorkloadInputs.validateOriginalNullOptions(state); checks++;
        var changed = state.deepCopy(); changed.addProperty("actual_shader_sha256", "0".repeat(64));
        reject(changed);
        changed = state.deepCopy(); changed.getAsJsonObject("effective_shader_options").remove("VL"); reject(changed);
        changed = state.deepCopy(); changed.getAsJsonObject("effective_shader_options").addProperty("SHADERLOD_FOG", "false"); reject(changed);
        changed = state.deepCopy(); changed.getAsJsonObject("effective_shader_options").addProperty("VL", "false"); reject(changed);
        changed = state.deepCopy(); changed.addProperty("effective_shader_options_sha256", "0".repeat(64)); reject(changed);
        try { WorkloadInputs.validateAlloff(state); throw new AssertionError("Original incomplete state bypassed legacy alloff guard"); }
        catch (RuntimeException expected) { checks++; }
        System.setProperty("shaderlod.dev.gpuSampleInterval", "5"); System.setProperty("shaderlod.dev.fixedWetness", "1");
        for (String flag : new String[]{"cpuLayerTiming", "gpuPassTiming", "gpuAttribution"}) System.setProperty("shaderlod.dev." + flag, "false");
        BenchmarkSession.validateAirFogAblationRecipe(30, 8, 20, false, true, true); checks++;
        try { BenchmarkSession.validateAirFogAblationRecipe(30, 8, 20, true, true, true); throw new AssertionError("ON start accepted"); }
        catch (IllegalStateException expected) { checks++; }
        System.out.println("OriginalNullControlGuardTest: " + checks + " checks passed; options/recipe only, not runtime evidence");
    }
    private static void reject(JsonObject state) {
        try { WorkloadInputs.validateOriginalNullOptions(state); throw new AssertionError("Unpinned original state accepted"); }
        catch (IllegalStateException expected) { checks++; }
    }
}

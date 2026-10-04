package com.pp.shaderlod.dev;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.pp.shaderlod.ShaderLodConfig;
import com.pp.shaderlod.dev.timing.GpuTimers;
import com.pp.shaderlod.dev.timing.CpuTimers;
import net.minecraft.client.Minecraft;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Single-client OFF/ON/OFF/ON measurement; raw samples stay in the run directory. */
final class BenchmarkSession {
    private final Path directory;
    private final List<Phase> phases = new ArrayList<>();
    private final double initialWarmup = Double.parseDouble(System.getProperty("shaderlod.dev.initialWarmup", "60"));
    private final double switchWarmup = 8, measurement = 20;
    private long started = System.nanoTime(), phaseStarted;
    private int phase = -1;
    private boolean sampling, done;
    private Phase current;
    BenchmarkSession(Path directory) { this.directory = directory; }
    String status() {
        if (done) return "Serial benchmark finished; results in benchmark.json";
        if (phase < 0) return "Benchmark: initial landscape warmup";
        return "Benchmark " + (phase + 1) + "/4 LOD " + (phase % 2 == 1 ? "ON" : "OFF") + (sampling ? " measuring" : " settling");
    }
    void tick() {
        var results = GpuTimers.drainSamples();
        var cpuResults = CpuTimers.drainSamples();
        if (done) return;
        long now = System.nanoTime();
        if (phase < 0) {
            if ((now - started) * 1e-9 < initialWarmup) return;
            var snapshot = GpuTimers.snapshot();
            if (snapshot.backend().equals("UNSUPPORTED")) { done = true; write("GPU queries unavailable: " + snapshot.status()); return; }
            startPhase(now); return;
        }
        if (!sampling && (now - phaseStarted) * 1e-9 >= switchWarmup) {
            GpuTimers.resetSamples(); sampling = true;
            current = new Phase(phase % 2 == 1, new ArrayList<>(), new ArrayList<>());
            return;
        }
        if (sampling) {
            current.samples().addAll(results);
            current.cpuSamples().addAll(cpuResults);
            if ((now - phaseStarted) * 1e-9 >= switchWarmup + measurement) {
                phases.add(current);
                LogUtils.getLogger().info("ShaderLOD benchmark phase {} complete: enabled={}, samples={}", phase + 1, current.enabled(), current.samples().size());
                if (phase == 3) {
                    done = true; ShaderLodConfig.ENABLED.set(true); write("complete");
                    if (Boolean.getBoolean("shaderlod.dev.benchmarkOnly")) Minecraft.getInstance().stop();
                } else startPhase(now);
            }
        }
    }
    private void startPhase(long now) {
        ++phase; phaseStarted = now; sampling = false;
        ShaderLodConfig.ENABLED.set(phase % 2 == 1);
        GpuTimers.resetSamples();
        LogUtils.getLogger().info("ShaderLOD benchmark phase {} settling: enabled={}", phase + 1, phase % 2 == 1);
    }
    private void write(String status) {
        try {
            var gson = new GsonBuilder().setPrettyPrinting().create();
            JsonObject report = new JsonObject();
            report.addProperty("status", status);
            report.addProperty("scope", "OpenGL game-render and Iris pass intervals; excludes swap/simulation");
            report.addProperty("method", "One client; same patched pack; OFF/ON/OFF/ON; 8s settling + 20s sampling per phase");
            report.addProperty("preset", ShaderLodConfig.PRESET.get().name());
            report.addProperty("backend", GpuTimers.snapshot().backend());
            report.addProperty("sampling_policy", GpuTimers.samplingPolicy());
            report.addProperty("initial_warmup_seconds", initialWarmup);
            report.add("phases", gson.toJsonTree(phases));
            Path path = directory.resolve("benchmark.json");
            Files.writeString(path, gson.toJson(report));
            LogUtils.getLogger().info("ShaderLOD benchmark written: {} ({})", path, status);
        } catch (Exception exception) { LogUtils.getLogger().error("Cannot save GPU benchmark", exception); }
    }
    private record Phase(boolean enabled, List<GpuTimers.Sample> samples, List<CpuTimers.Sample> cpuSamples) {}
}

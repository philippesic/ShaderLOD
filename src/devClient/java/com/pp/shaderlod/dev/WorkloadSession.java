package com.pp.shaderlod.dev;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.pp.shaderlod.dev.timing.GpuTimers;
import com.pp.shaderlod.dev.timing.CpuTimers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.fml.ModList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fixed configuration run: independent of ShaderLOD, CPU data separate from GPU. */
final class WorkloadSession {
    private final Path directory;
    private final List<GpuTimers.Sample> gpu = new ArrayList<>();
    private final List<CpuTimers.Sample> cpu = new ArrayList<>();
    private final double warmup = Double.parseDouble(System.getProperty("shaderlod.dev.initialWarmup", "60"));
    private final double duration = Double.parseDouble(System.getProperty("shaderlod.dev.measurementSeconds", "30"));
    private final long started = System.nanoTime();
    private long measurementStarted;
    private boolean measuring, done;
    private int startFrames;

    WorkloadSession(Path directory) { this.directory = directory; }
    String status() { return done ? "Workload capture finished" : measuring ? "Workload measuring CPU/GPU" : "Workload warming"; }
    void tick() {
        var gpuSamples = GpuTimers.drainSamples();
        var cpuSamples = CpuTimers.drainSamples();
        if (done) return;
        long now = System.nanoTime();
        if (!measuring) {
            if ((now - started) * 1e-9 < warmup) return;
            GpuTimers.resetSamples();
            measuring = true; measurementStarted = now;
            startFrames = (int) GpuTimers.snapshot().renderFrames();
            LogUtils.getLogger().info("ShaderLOD workload measurement started: {}", directory);
            return;
        }
        gpu.addAll(gpuSamples); cpu.addAll(cpuSamples);
        if ((now - measurementStarted) * 1e-9 >= duration) {
            done = true;
            write((now - measurementStarted) * 1e-9);
            var client = Minecraft.getInstance();
            Screenshot.grab(client.gameDirectory, "workload-final.png", client.getMainRenderTarget(), message -> {
                LogUtils.getLogger().info("Workload screenshot: {}", message.getString());
                if (Boolean.getBoolean("shaderlod.dev.measureOnly")) client.execute(client::stop);
            });
        }
    }
    private void write(double seconds) {
        try {
            var gson = new GsonBuilder().setPrettyPrinting().create();
            var client = Minecraft.getInstance();
            JsonObject report = new JsonObject();
            report.addProperty("status", "complete");
            report.addProperty("profile", directory.getFileName().toString());
            report.addProperty("modPresent", ModList.get().isLoaded("shaderlod"));
            report.addProperty("dhPresent", ModList.get().isLoaded("distanthorizons"));
            report.addProperty("elapsed_seconds", seconds);
            report.addProperty("warmup_seconds", warmup);
            report.addProperty("render_frames", GpuTimers.snapshot().renderFrames() - startFrames);
            report.addProperty("backend", GpuTimers.snapshot().backend());
            report.addProperty("sampling_policy", GpuTimers.samplingPolicy());
            report.addProperty("framebuffer_width", client.getWindow().getWidth());
            report.addProperty("framebuffer_height", client.getWindow().getHeight());
            report.addProperty("render_distance_chunks", client.options.renderDistance().get());
            report.addProperty("effective_render_distance_chunks", client.options.getEffectiveRenderDistance());
            if (ModList.get().isLoaded("shaderlod")) report.add("geometry_policy", gson.toJsonTree(com.pp.shaderlod.GeometryLodController.status()));
            report.add("gpu_samples", gson.toJsonTree(gpu)); report.add("cpu_samples", gson.toJsonTree(cpu));
            Path root = directory.getParent();
            for (String file : List.of("control.json", "workload-manifest.json")) {
                if (Files.exists(root.resolve(file))) report.add(file, JsonParser.parseString(Files.readString(root.resolve(file))));
            }
            Path iris = directory.resolve("config/iris.properties");
            report.addProperty("iris_configuration", Files.readString(iris));
            var properties = new java.util.Properties();
            try (var reader = Files.newBufferedReader(iris)) { properties.load(reader); }
            String pack = properties.getProperty("shaderPack", "");
            Path options = directory.resolve("shaderpacks").resolve(pack + ".txt");
            if (Files.exists(options)) report.addProperty("actual_shader_options", Files.readString(options));
            Path archive = directory.resolve("shaderpacks").resolve(pack);
            if (Files.isRegularFile(archive)) report.addProperty("actual_shader_sha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
            report.addProperty("actual_dh_configuration", Files.readString(directory.resolve("config/DistantHorizons.toml")));
            Path path = directory.resolve("workload-measurement.json");
            Files.writeString(path, gson.toJson(report));
            LogUtils.getLogger().info("ShaderLOD workload measurement written: {} GPU={} CPU={}", path, gpu.size(), cpu.size());
        } catch (Exception exception) { LogUtils.getLogger().error("Cannot save workload measurement", exception); }
    }
}

package com.pp.shaderlod.dev;

import com.pp.shaderlod.dev.config.DevelopmentSettings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "shaderlod_dev", dist = Dist.CLIENT)
public final class DevelopmentProfiler {
  private static final BoundedProfiler BUFFER = new BoundedProfiler(4096);
  private static com.pp.shaderlod.dev.measurement.GpuCollector gpu;
  private final BenchmarkControl benchmark;
  private long start;
  private long frame;

  public DevelopmentProfiler() {
    benchmark = DevelopmentSettings.benchmarkDirectory().map(BenchmarkControl::new).orElse(null);
    if (benchmark != null) {
      NeoForge.EVENT_BUS.addListener(benchmark::tick);
    }
    NeoForge.EVENT_BUS.addListener(ProfileExport::register);
    NeoForge.EVENT_BUS.addListener(this::begin);
    NeoForge.EVENT_BUS.addListener(this::end);
    if (DevelopmentSettings.functionalProbe()) {
      NeoForge.EVENT_BUS.addListener(new FunctionalProbe()::tick);
    }
  }

  private void begin(RenderFrameEvent.Pre event) {
    if (benchmark != null) {
      benchmark.frame(
          System.nanoTime(),
          net.irisshaders.iris.Iris.getPipelineManager().getVersionCounterForSodiumShaderReload());
      return;
    }
    if (gpu == null && org.lwjgl.opengl.GL.getCapabilities().OpenGL33) {
      gpu =
          new com.pp.shaderlod.dev.measurement.GpuCollector(
              new com.pp.shaderlod.dev.measurement.OpenGlQueries(), 64);
    }
    long generation =
        net.irisshaders.iris.Iris.getPipelineManager().getVersionCounterForSodiumShaderReload();
    if (gpu != null) {
      gpu.begin(generation, frame);
    }
    start = System.nanoTime();
  }

  private void end(RenderFrameEvent.Post event) {
    if (benchmark != null) {
      return;
    }
    long elapsed = System.nanoTime() - start;
    long generation =
        net.irisshaders.iris.Iris.getPipelineManager().getVersionCounterForSodiumShaderReload();
    if (gpu != null) {
      gpu.end(generation, frame);
    }
    if (start != 0 && elapsed >= 0) {
      BUFFER.record(
          new BoundedProfiler.Sample(
              net.irisshaders.iris.Iris.getPipelineManager()
                  .getVersionCounterForSodiumShaderReload(),
              frame++,
              elapsed));
    }
    start = 0;
  }

  public static BoundedProfiler.Snapshot snapshot() {
    return BUFFER.snapshot();
  }

  public sealed interface GpuStream permits GpuAvailable, GpuUnavailable {}

  public record GpuAvailable(com.pp.shaderlod.dev.measurement.GpuCollector.Snapshot observations)
      implements GpuStream {}

  public record GpuUnavailable(String reason) implements GpuStream {}

  public static GpuStream gpuSnapshot() {
    return gpu == null
        ? new GpuUnavailable("OpenGL timestamp collector not initialized")
        : new GpuAvailable(gpu.snapshot());
  }
}

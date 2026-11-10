package com.pp.shaderlod.dev;

import com.google.gson.Gson;
import com.pp.shaderlod.integration.DhObservation;
import com.pp.shaderlod.integration.IrisSelection;
import com.pp.shaderlod.integration.ShaderController;
import com.pp.shaderlod.storage.AtomicFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;

final class FunctionalProbe {
  private final ShaderController controller = new ShaderController();
  private int ticks;
  private boolean completed;

  void tick(ClientTickEvent.Post event) {
    var minecraft = Minecraft.getInstance();
    if (completed || minecraft.level == null || ++ticks < 80) {
      return;
    }
    completed = true;
    var result = new LinkedHashMap<String, Object>();
    result.put("schema", "shaderlod-functional-v1");
    result.put("scope", "live-functional; no performance qualification");
    result.put("dh", DhObservation.current());
    result.put("profiler", DevelopmentProfiler.snapshot());
    result.put("gpu_profiler", DevelopmentProfiler.gpuSnapshot());
    result.put("source", "ComplementaryReimagined_r5.9.3.zip");
    result.put("native_width", minecraft.getMainRenderTarget().width);
    result.put("native_height", minecraft.getMainRenderTarget().height);
    var backend = new IrisSelection();
    var original = backend.snapshot();
    try {
      try (var image =
          net.minecraft.client.Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
        byte[] png = image.asByteArray();
        AtomicFiles.publish(FMLPaths.GAMEDIR.get().resolve("shaderlod-functional.png"), png);
        result.put("rendered_output_sha256", com.pp.shaderlod.storage.PackArchive.sha256(png));
        result.put(
            "image_has_variation",
            java.util.Arrays.stream(image.getPixelsRGBA()).distinct().limit(2).count() > 1);
      }
      result.put("native_activation", backend.observe());
      result.put("selection", controller.apply("ComplementaryReimagined_r5.9.3.zip", List.of()));
      controller.off();
      if (!original.equals(backend.snapshot())) {
        throw new IOException("Native state not restored");
      }
      result.put(
          "identity_probe",
          controller.apply("ComplementaryReimagined_r5.9.3.zip", List.of("identity-probe")));
      controller.off();
      if (!original.equals(backend.snapshot())) {
        throw new IOException("Identity probe state not restored");
      }
      result.put("outcome", "PASS");
      result.put(
          "limits",
          "No image-equivalence or performance claim; inspect rendered output separately");
    } catch (IOException | IllegalArgumentException | IllegalStateException failure) {
      result.put("outcome", "FAIL");
      result.put("reason", failure.toString());
    } finally {
      try {
        controller.off();
      } catch (IOException | IllegalArgumentException | IllegalStateException failure) {
        result.put("restoration", "FAIL: " + failure);
        result.put("outcome", "FAIL");
      }
      var restored = backend.snapshot();
      result.put(
          "restored_state",
          Map.of(
              "pack",
              restored.packName().orElse(""),
              "enabled",
              restored.shadersEnabled(),
              "queued",
              restored.queuedOptions(),
              "effective",
              restored.effectiveOptions()));
      try {
        AtomicFiles.publish(
            FMLPaths.GAMEDIR.get().resolve("shaderlod-functional.json"),
            new Gson().toJson(result).getBytes(StandardCharsets.UTF_8));
      } catch (IOException failure) {
        net.irisshaders.iris.Iris.logger.error("Functional result publication failed", failure);
      }
      minecraft.stop();
    }
  }
}

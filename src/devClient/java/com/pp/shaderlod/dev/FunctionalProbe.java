package com.pp.shaderlod.dev;

import com.google.gson.Gson;
import com.pp.shaderlod.domain.NativeState;
import com.pp.shaderlod.integration.DhObservation;
import com.pp.shaderlod.integration.IrisSelection;
import com.pp.shaderlod.integration.SettingsScreen;
import com.pp.shaderlod.integration.ShaderController;
import com.pp.shaderlod.storage.AtomicFiles;
import com.pp.shaderlod.storage.PackArchive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;

final class FunctionalProbe {
  private enum Phase {
    START,
    SELECTION,
    NATIVE_AGAIN,
    IDENTITY,
    FAULT,
    RECOVERED,
    DONE
  }

  private final ShaderController controller = new ShaderController();
  private final IrisSelection backend = new IrisSelection();
  private final LinkedHashMap<String, Object> result = new LinkedHashMap<>();
  private Phase phase = Phase.START;
  private NativeState original;
  private int ticks;
  private int waited;
  private final boolean stopOnComplete;

  FunctionalProbe() {
    this(true);
  }

  FunctionalProbe(boolean stopOnComplete) {
    this.stopOnComplete = stopOnComplete;
  }

  boolean done() {
    return phase == Phase.DONE;
  }

  void tick(ClientTickEvent.Post event) {
    var minecraft = Minecraft.getInstance();
    if (phase == Phase.DONE || minecraft.level == null || ++ticks < 80) {
      return;
    }
    try {
      controller.tick();
      if (++waited > 120) {
        throw new IOException("No submitted frame before phase deadline: " + phase);
      }
      switch (phase) {
        case START -> start();
        case SELECTION -> {
          if (!controller.status().equals("ACTIVE")) {
            return;
          }
          result.put("selection", controller.activation());
          result.put(
              "options_preserved",
              controller.activation().effectiveOptions().equals(original.effectiveOptions()));
          press(screen("", ""), "Off");
          minecraft.setScreen(null);
          requireOriginal();
          result.put("off_restored", true);
          advance(Phase.NATIVE_AGAIN);
        }
        case NATIVE_AGAIN -> {
          if (!backend.hasSubmittedFrame()) {
            return;
          }
          press(screen("identity-probe", ""), "Apply");
          advance(Phase.IDENTITY);
        }
        case IDENTITY -> {
          if (!controller.status().equals("ACTIVE")
              || waited < 20
              || minecraft.levelRenderer.countRenderedSections() < 1) {
            return;
          }
          result.put("identity_rendered_sections", minecraft.levelRenderer.countRenderedSections());
          result.put("identity_probe", controller.activation());
          capture("shaderlod-functional-identity.png", "identity_output_sha256");
          var active = backend.snapshot();
          var activation = controller.activation();
          var staged = screen("nonexistent-probe", "invalid");
          press(staged, "Cancel");
          if (!active.equals(backend.snapshot()) || !activation.equals(controller.activation())) {
            throw new IOException("Cancel changed active selection");
          }
          result.put("cancel_preserved_active", true);
          press(screen("", ""), "Off");
          minecraft.setScreen(null);
          requireOriginal();
          advance(Phase.FAULT);
        }
        case FAULT -> {
          if (!backend.hasSubmittedFrame()) {
            return;
          }
          var faulty = screen("reload-failure-probe", "");
          press(faulty, "Apply");
          if (!controller.status().equals("RESTORED")
              || !faulty.feedbackText().contains("Iris rejected")) {
            throw new IOException(
                "Fault fixture did not exercise failed reload restoration: "
                    + faulty.feedbackText());
          }
          requireOriginal();
          result.put("failed_apply_restored", true);
          result.put("failed_apply_reason", faulty.feedbackText());
          press(faulty, "Cancel");
          advance(Phase.RECOVERED);
        }
        case RECOVERED -> {
          if (!backend.hasSubmittedFrame()) {
            return;
          }
          requireOriginal();
          result.put("recovery_activation", backend.observe());
          result.put("outcome", "PASS");
          finish();
        }
        case DONE -> throw new IllegalStateException("Already completed");
      }
    } catch (IOException | IllegalArgumentException | IllegalStateException failure) {
      result.put("outcome", "FAIL");
      result.put("reason", failure.toString());
      finish();
    }
  }

  private void start() throws IOException {
    if (Minecraft.getInstance().levelRenderer.countRenderedSections() < 1) {
      return;
    }
    result.put(
        "native_rendered_sections", Minecraft.getInstance().levelRenderer.countRenderedSections());
    result.put("schema", "shaderlod-functional-v3");
    result.put("scope", "live-functional; no image-equivalence or performance qualification");
    result.put("source", "ComplementaryReimagined_r5.9.3.zip");
    result.put("dh", DhObservation.current());
    result.put("sampler_index_policy", com.pp.shaderlod.integration.SamplerIndexPolicy.observed());
    var target = Minecraft.getInstance().getMainRenderTarget();
    result.put("native_width", target.width);
    result.put("native_height", target.height);
    original = backend.snapshot();
    result.put("native_activation", backend.observe());
    if (!backend.observe().linked() || !backend.hasSubmittedFrame()) {
      throw new IOException("Original native shader graph has no submitted frame");
    }
    capture("shaderlod-functional.png", "rendered_output_sha256");
    press(screen("nonexistent-probe", "invalid"), "Cancel");
    requireOriginal();
    result.put("cancel_staged_unchanged", true);
    String taa = original.effectiveOptions().get("TAA");
    if (taa == null) {
      throw new IOException("Missing native AA option");
    }
    press(screen("", "TAA=" + taa), "Apply");
    advance(Phase.SELECTION);
  }

  private SettingsScreen screen(String ids, String options) {
    var screen = new SettingsScreen(null, controller);
    Minecraft.getInstance().setScreen(screen);
    var fields =
        screen.children().stream()
            .filter(EditBox.class::isInstance)
            .map(EditBox.class::cast)
            .toList();
    if (fields.size() != 3) {
      throw new IllegalStateException("Unexpected actual settings widgets");
    }
    fields.get(0).setValue("ComplementaryReimagined_r5.9.3.zip");
    fields.get(1).setValue(ids);
    fields.get(2).setValue(options);
    return screen;
  }

  private static void press(SettingsScreen screen, String label) {
    var buttons =
        screen.children().stream()
            .filter(Button.class::isInstance)
            .map(Button.class::cast)
            .filter(button -> button.getMessage().getString().equals(label))
            .toList();
    if (buttons.size() != 1) {
      throw new IllegalStateException("Missing actual button: " + label);
    }
    buttons.getFirst().onPress();
  }

  private void requireOriginal() throws IOException {
    if (!original.equals(backend.snapshot())) {
      throw new IOException("Native settings differ from original preimage");
    }
  }

  private void capture(String filename, String key) throws IOException {
    try (var image =
        net.minecraft.client.Screenshot.takeScreenshot(
            Minecraft.getInstance().getMainRenderTarget())) {
      if (java.util.Arrays.stream(image.getPixelsRGBA()).distinct().limit(2).count() < 2) {
        throw new IOException("Rendered output has no variation");
      }
      byte[] png = image.asByteArray();
      AtomicFiles.publish(FMLPaths.GAMEDIR.get().resolve(filename), png);
      result.put(key, PackArchive.sha256(png));
      result.put("image_has_variation", true);
    }
  }

  private void advance(Phase next) {
    phase = next;
    waited = 0;
  }

  private void finish() {
    phase = Phase.DONE;
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
    result.put("profiler", DevelopmentProfiler.snapshot());
    result.put("gpu_profiler", DevelopmentProfiler.gpuSnapshot());
    result.put("limits", "No image-equivalence, per-pass cost or performance claim");
    try {
      AtomicFiles.publish(
          FMLPaths.GAMEDIR.get().resolve("shaderlod-functional.json"),
          new Gson().toJson(result).getBytes(StandardCharsets.UTF_8));
    } catch (IOException failure) {
      net.irisshaders.iris.Iris.logger.error("Functional result publication failed", failure);
    }
    if (stopOnComplete) {
      Minecraft.getInstance().stop();
    }
  }
}

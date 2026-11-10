package com.pp.shaderlod.integration;

import java.io.IOException;
import java.util.Arrays;
import net.irisshaders.iris.Iris;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SettingsScreen extends Screen {
  private final Screen parent;
  private final ShaderController controller;
  private EditBox pack;
  private EditBox optimizations;
  private EditBox options;
  private String feedback = "";

  public SettingsScreen(Screen parent, ShaderController controller) {
    super(Component.literal("ShaderLOD"));
    this.parent = parent;
    this.controller = controller;
  }

  @Override
  protected void init() {
    int left = width / 2 - 150;
    int top = height / 2 - 50;
    pack = new EditBox(font, left, top, 300, 20, Component.literal("Shader archive"));
    pack.setMaxLength(240);
    pack.setValue(
        Iris.getIrisConfig().getShaderPackName().orElse("ComplementaryReimagined_r5.9.3.zip"));
    addRenderableWidget(pack);
    optimizations =
        new EditBox(font, left, top + 35, 300, 20, Component.literal("Optimization IDs"));
    optimizations.setMaxLength(512);
    addRenderableWidget(optimizations);
    options =
        new EditBox(
            font, left, top + 60, 300, 20, Component.literal("Shader options key=value;key=value"));
    options.setMaxLength(2048);
    addRenderableWidget(options);
    addRenderableWidget(
        Button.builder(Component.literal("Apply"), button -> apply())
            .bounds(left, top + 90, 95, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("Off"), button -> restore())
            .bounds(left + 102, top + 90, 95, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("Cancel"), button -> onClose())
            .bounds(left + 205, top + 90, 95, 20)
            .build());
  }

  private void apply() {
    try {
      var ids =
          Arrays.stream(optimizations.getValue().split(","))
              .map(String::trim)
              .filter(id -> !id.isEmpty())
              .toList();
      var overrides = new java.util.TreeMap<String, String>();
      if (!options.getValue().isBlank()) {
        for (String entry : options.getValue().split(";", -1)) {
          String[] pair = entry.split("=", 2);
          if (pair.length != 2
              || pair[0].isBlank()
              || overrides.putIfAbsent(pair[0].trim(), pair[1].trim()) != null) {
            throw new IllegalArgumentException(
                "Use distinct key=value pairs separated by semicolons");
          }
        }
      }
      var active = controller.apply(pack.getValue(), ids, overrides);
      feedback = "Linked " + active.packName() + "; generation " + active.generation();
      if (minecraft != null) {
        minecraft.setScreen(parent);
      }
    } catch (IOException | IllegalArgumentException | IllegalStateException failure) {
      feedback = "Apply failed: " + failure.getMessage();
    }
  }

  private boolean restore() {
    try {
      controller.off();
      feedback = "Native preimage restored";
      return true;
    } catch (IOException | IllegalArgumentException | IllegalStateException failure) {
      feedback = "Recovery required: " + failure.getMessage();
      return false;
    }
  }

  @Override
  public void onClose() {
    if (minecraft != null) {
      minecraft.setScreen(parent);
    }
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    super.render(graphics, mouseX, mouseY, partialTick);
    graphics.drawCenteredString(font, title, width / 2, height / 2 - 90, 0xffffff);
    graphics.drawString(
        font,
        "Archive / optimization IDs (comma separated)",
        width / 2 - 150,
        height / 2 - 65,
        0xffffff);
    graphics.drawString(
        font, "Available: " + controller.providers(), width / 2 - 150, height / 2 + 65, 0xffffff);
    graphics.drawString(font, feedback, width / 2 - 150, height / 2 + 65, 0xffffff);
    graphics.drawString(
        font,
        "DH: " + DhObservation.current().readiness(),
        width / 2 - 150,
        height / 2 + 85,
        0xffffff);
  }
}

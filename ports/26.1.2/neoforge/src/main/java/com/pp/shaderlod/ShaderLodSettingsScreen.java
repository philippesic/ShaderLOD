package com.pp.shaderlod;

import net.irisshaders.iris.Iris;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;

/** Staged preset controls; all edits apply through the same safe reload transaction. */
public final class ShaderLodSettingsScreen extends Screen {
    private final Screen parent;
    private boolean enabled;
    private LodPreset preset;
    private Component feedback = Component.empty();
    private Component packStatus = Component.empty();
    private boolean photon;

    public ShaderLodSettingsScreen(ModContainer container, Screen parent) {
        super(Component.literal("ShaderLOD"));
        this.parent = parent;
        enabled = ShaderLodConfig.ENABLED.get();
        preset = ShaderLodConfig.PRESET.get();
        packStatus = ShaderLodPackDetection.current().map(recognition -> {
            photon = recognition.pack().id().equals("photon");
            return Component.literal(
                recognition.stage() == com.pp.shaderlod.packs.SupportedPackCatalog.Stage.ASSEMBLED_ADAPTER
                        ? "LOD adapter detected" : "Ready to apply LOD to this shader");
        })
                .orElseGet(() -> Component.literal("No known ShaderLOD adapter detected"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Optimizations / timing"), button -> minecraft.setScreen(new OptimizationScreen(this)))
                .bounds(width - 170, 6, 164, 20).build());
        int left = width / 2 - 150;
        int top = height / 2 - 24;
        addRenderableWidget(CycleButton.onOffBuilder(enabled)
                .create(left, top, 300, 20, Component.literal("LOD"),
                        (button, value) -> enabled = value));
        addRenderableWidget(CycleButton.<LodPreset>builder(ShaderLodSettingsScreen::presetName, () -> preset)
                .withValues(preset == LodPreset.AGGRESSIVE
                        ? java.util.List.of(LodPreset.AGGRESSIVE, LodPreset.PERFORMANCE, LodPreset.BALANCED, LodPreset.QUALITY)
                        : java.util.List.of(LodPreset.PERFORMANCE, LodPreset.BALANCED, LodPreset.QUALITY))
                .create(left, top + 26, 300, 20, Component.literal("LOD preset"),
                        (button, value) -> preset = value));
        addRenderableWidget(Button.builder(Component.literal("Apply"), button -> {
            if (ShaderLodSettings.apply(enabled, preset)) onClose();
            else feedback = Component.literal(ShaderLodSettings.failureMessage());
        }).bounds(left, top + 62, 145, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(left + 155, top + 62, 145, 20).build());
    }

    private static Component presetName(LodPreset value) {
        return Component.literal(switch (value) {
            case PERFORMANCE -> "Performance";
            case AGGRESSIVE -> "Aggressive (legacy)";
            case BALANCED -> "Balanced";
            case QUALITY -> "Quality";
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, height / 2 - 90, 0xffffffff);
        graphics.centeredText(font, Component.literal("Shader: " + Iris.getCurrentPackName()),
                width / 2, height / 2 - 70, 0xffc0c0c0);
        graphics.centeredText(font, Component.literal(photon
                        ? "Pixel savings require Photon anti-aliasing"
                        : "Uses your shader preset as the base"),
                width / 2, height / 2 - 54, 0xffc0c0c0);
        graphics.centeredText(font, packStatus, width / 2, height / 2 - 38, 0xffc0c0c0);
        graphics.centeredText(font, feedback, width / 2, height / 2 + 101, 0xffff8080);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}

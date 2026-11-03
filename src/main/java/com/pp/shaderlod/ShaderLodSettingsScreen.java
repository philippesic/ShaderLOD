package com.pp.shaderlod;

import net.irisshaders.iris.Iris;
import net.minecraft.client.gui.GuiGraphics;
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
            boolean photonQuad = recognition.pack().id().equals("photon")
                    && Boolean.getBoolean(com.pp.shaderlod.packs.PackSelectionService.PHOTON_QUAD_ORIGINAL_GRAPH);
            photon = recognition.pack().id().equals("photon") && !photonQuad;
            if (recognition.pack().id().equals("makeup-ultra-fast-shaders")
                    || recognition.pack().id().equals("complementary-unbound")
                    || recognition.pack().id().equals("complementary-reimagined")
                    || recognition.pack().id().equals("sildurs-vibrant-shaders")
                    || recognition.pack().id().equals("bsl-shaders") || photonQuad) {
                return Component.literal(com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled()
                        ? "Experimental renderer active" : "Original shader active");
            }
            return Component.literal(
                recognition.stage() == com.pp.shaderlod.packs.SupportedPackCatalog.Stage.ASSEMBLED_ADAPTER
                        ? "LOD adapter detected" : "Ready to apply LOD to this shader");
        })
                .orElseGet(() -> Component.literal("No known ShaderLOD adapter detected"));
        if (ShaderLodPackDetection.privateRasterExperiment()) {
            photon = false;
            packStatus = Component.literal("Private raster experiment; validation pending");
        }
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Benchmark / auto tune"),button->minecraft.setScreen(new BenchmarkScreen(this)))
                .bounds(6,6,168,20).build());
        addRenderableWidget(Button.builder(Component.literal("Optimizations / timing"), button -> minecraft.setScreen(new OptimizationScreen(this)))
                .bounds(width - 170, 6, 164, 20).build());
        int left = width / 2 - 150;
        int top = height / 2 - 24;
        addRenderableWidget(CycleButton.onOffBuilder(enabled)
                .create(left, top, 300, 20, Component.literal("LOD"),
                        (button, value) -> enabled = value));
        addRenderableWidget(CycleButton.<LodPreset>builder(ShaderLodSettingsScreen::presetName)
                .withValues(preset == LodPreset.AGGRESSIVE
                        ? java.util.List.of(LodPreset.AGGRESSIVE, LodPreset.PERFORMANCE, LodPreset.BALANCED, LodPreset.QUALITY)
                        : java.util.List.of(LodPreset.PERFORMANCE, LodPreset.BALANCED, LodPreset.QUALITY))
                .withInitialValue(preset)
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 90, 0xffffff);
        graphics.drawCenteredString(font, Component.literal("Shader: " + Iris.getCurrentPackName()),
                width / 2, height / 2 - 70, 0xc0c0c0);
        graphics.drawCenteredString(font, Component.literal(photon
                        ? "Pixel savings require Photon anti-aliasing"
                        : "Uses your shader preset as the base"),
                width / 2, height / 2 - 54, 0xc0c0c0);
        graphics.drawCenteredString(font, packStatus, width / 2, height / 2 - 38, 0xc0c0c0);
        graphics.drawCenteredString(font, feedback, width / 2, height / 2 + 101, 0xff8080);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}

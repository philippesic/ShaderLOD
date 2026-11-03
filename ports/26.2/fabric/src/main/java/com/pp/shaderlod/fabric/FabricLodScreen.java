package com.pp.shaderlod.fabric;

import com.pp.shaderlod.LodPreset;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.irisshaders.iris.Iris;

/** F8 / Controls key binding opens this screen without Mod Menu being required. */
public final class FabricLodScreen extends Screen {
    private final Screen parent;
    private boolean enabled;
    private LodPreset selectedPreset;
    private Component feedback = Component.empty();
    private final String recognition;
    public FabricLodScreen(Screen parent) {
        super(Component.literal("ShaderLOD"));
        this.parent = parent;
        enabled = FabricLodConfig.snapshot().enabled();
        selectedPreset = FabricLodConfig.snapshot().preset();
        recognition = FabricPackSelection.recognition();
    }

    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Optimizations / timing"), button -> minecraft.gui.setScreen(new OptimizationScreen(this)))
                .bounds(width - 170, 6, 164, 20).build());
        int left = width / 2 - 100;
        addRenderableWidget(Button.builder(enabledLabel(), button -> {
            enabled = !enabled;
            button.setMessage(enabledLabel());
        }).bounds(left, height / 2 - 64, 200, 20).build());
        var presets = new LodPreset[]{LodPreset.PERFORMANCE, LodPreset.BALANCED, LodPreset.QUALITY};
        for (int i = 0; i < presets.length; i++) {
            var preset = presets[i];
            addRenderableWidget(Button.builder(Component.literal(label(preset)), button -> {
                selectedPreset = preset;
                rebuildWidgets();
            }).bounds(left, height / 2 - 38 + i * 24, 200, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Apply"), button -> {
            if (FabricLodSettings.apply(enabled, selectedPreset)) onClose();
            else feedback = Component.literal(FabricLodSettings.failureMessage());
        }).bounds(left, height / 2 + 54, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(left + 105, height / 2 + 54, 95, 20).build());
    }
    private Component enabledLabel() {
        return Component.literal("LOD: " + (enabled ? "On" : "Off"));
    }
    private String label(LodPreset preset) {
        String label = switch (preset) {
            case QUALITY -> "Quality";
            case BALANCED -> "Balanced";
            case PERFORMANCE -> "Performance";
            case AGGRESSIVE -> "Aggressive (legacy)";
        };
        return label + (selectedPreset == preset ? " (selected)" : "");
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, height / 2 - 126, 0xffffffff);
        graphics.centeredText(font, "Shader: " + Iris.getCurrentPackName(), width / 2, height / 2 - 112, 0xffaaaaaa);
        graphics.centeredText(font, recognition, width / 2, height / 2 - 96, 0xffaaaaaa);
        graphics.centeredText(font, recognition.startsWith("Photon")
                ? "Pixel savings require Photon anti-aliasing"
                : "Uses your shader preset as the base", width / 2, height / 2 - 78, 0xffaaaaaa);
        graphics.centeredText(font, feedback, width / 2, height / 2 + 85, 0xffff8080);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}

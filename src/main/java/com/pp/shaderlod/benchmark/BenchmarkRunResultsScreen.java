package com.pp.shaderlod.benchmark;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Locale;

/** First-class per-case results; raw evidence stays in the versioned report. */
public final class BenchmarkRunResultsScreen extends Screen {
    private final Screen parent;
    private int page;
    public BenchmarkRunResultsScreen(Screen parent) { super(Component.literal("FAST benchmark results")); this.parent = parent; }
    private int perPage() { return Math.max(1, (height - 112) / 26); }
    @Override protected void init() {
        int left = width / 2 - 155;
        addRenderableWidget(Button.builder(Component.literal("Previous"), b -> { page = Math.max(0, page - 1); }).bounds(left, height - 52, 74, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Next"), b -> {
            var result = BenchmarkRunSession.lastManifest();
            if (result != null && (page + 1) * perPage() < result.windows().size()) page++;
        }).bounds(left + 78, height - 52, 74, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Open report"), b -> {
            if (BenchmarkRunSession.lastResult() != null) Util.getPlatform().openPath(BenchmarkRunSession.lastResult());
            else Util.getPlatform().openPath(BenchmarkRunSession.resultsDirectory());
        }).bounds(left + 156, height - 52, 154, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(left, height - 27, 310, 20).build());
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xffffff);
        line(graphics, "Selected configuration only; quality and compatibility untested", 29, 0xc0c0c0);
        var report = BenchmarkRunSession.lastManifest();
        if (report == null) { line(graphics, BenchmarkRunSession.status(), 57, 0xb0d8ff); return; }
        int offset = page * perPage();
        for (int i = offset; i < Math.min(report.windows().size(), offset + perPage()); i++) {
            var window = report.windows().get(i);
            var stats = window.statistics();
            int y = 49 + (i - offset) * 26;
            String state = window.hostObservations().getOrDefault("case_verdict", window.validity().name());
            String metric = stats.count() == 0 ? "No timing samples" : String.format(Locale.ROOT,
                    "%.2f ms mean | %.1f FPS | p95 %.2f ms | %d frames", stats.meanMs(), stats.throughputFps(), stats.p95Ms(), stats.count());
            line(graphics, window.scenario().caseId() + " — " + state + ": " + metric, y, window.validity() == com.pp.shaderlod.benchmark.core.BenchmarkAnalysis.Validity.VALID ? 0xb0d8ff : 0xffd399);
            String reason = window.reasons().isEmpty() ? "" : window.reasons().getFirst();
            if (!window.warmup().missingSignals().isEmpty()) reason = String.join("; ", window.warmup().missingSignals());
            line(graphics, reason, y + 11, 0xc0c0c0);
        }
    }
    private void line(GuiGraphics graphics, String text, int y, int color) {
        graphics.drawCenteredString(font, font.plainSubstrByWidth(text, width - 18), width / 2, y, color);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }
}

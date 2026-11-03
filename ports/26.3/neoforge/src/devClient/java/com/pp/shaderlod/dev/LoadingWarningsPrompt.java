package com.pp.shaderlod.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pp.shaderlod.integration.RendererBackend;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.i18n.FMLTranslations;
import net.neoforged.neoforge.client.gui.LoadingErrorScreen;
import net.neoforged.neoforge.client.gui.widget.ExtendedButton;
import com.mojang.blaze3d.platform.InputConstants;

/** Exact single pinned Iris1.11.7 developer logo warning; never accepts another loading issue. */
final class LoadingWarningsPrompt {
    private static final String WARNING = "Mod %s uses the deprecated `logoFile` property; change to `bannerFile` and/or (for square icons) `iconFile`";
    private static final Map<String, String> VERSIONS = Map.of("iris", "1.11.7+mc26.3");
    private static final Map<String, String> HASHES = Map.of("iris", "2260506334a17ac566ea30169ab491201208f55c1c7212bb987bc0c7c59a8d93");
    private final String run;
    private final JsonObject report;
    private LoadingErrorScreen screen;

    LoadingWarningsPrompt(String run, JsonObject report) { this.run = run; this.report = report; }

    void beforeWorld(Minecraft client, Runnable persist, BooleanSupplier active, Consumer<Exception> fail) throws Exception {
        if (client.gui.screen() == null || client.gui.screen().getClass() != LoadingErrorScreen.class) return;
        if (screen != null) {
            if (client.gui.screen() != screen) throw new IllegalStateException("Unexpected second loading warning screen");
            return;
        }
        var state = guardedState(client);
        screen = (LoadingErrorScreen) client.gui.screen();
        var continuation = continueButton(); // Pinned source only creates this widget when errors are empty.
        state.addProperty("screenClass", screen.getClass().getName());
        state.addProperty("callbackWidget", continuation.getMessage().getString());
        state.addProperty("continueCallbackInvoked", false);
        report.add("loadingWarningsPrompt", state);
        String name = "shaderlod-neoforge-roundtrip-" + run + "-known-loading-warnings.png";
        Path screenshot = client.gameDirectory.toPath().resolve("screenshots").resolve(name);
        state.addProperty("screenshot", screenshot.toAbsolutePath().toString());
        state.addProperty("screenshotScale", 1);
        state.addProperty("screenshotStatus", "requested");
        report.addProperty("status", "capturing-exact-pinned-loading-warnings");
        persist.run();
        Screenshot.grab(client.gameDirectory, name, client.gameRenderer.mainRenderTarget(), 1, message -> client.execute(() -> {
            try {
                if (!active.getAsBoolean()) return;
                if (!Files.isRegularFile(screenshot)) throw new IllegalStateException("Loading warning screenshot failed");
                state.addProperty("screenshotStatus", "saved");
                state.addProperty("screenshotMessage", message.getString());
                if (client.gui.screen() != screen) throw new IllegalStateException("Loading warning screen changed before callback");
                state.add("immediatePreCallback", guardedState(client));
                if (continueButton() != continuation) throw new IllegalStateException("Loading Continue widget changed");
                if (!active.getAsBoolean()) throw new IllegalStateException("Original probe/startup deadline expired before Continue callback");
                continuation.onPress(new KeyEvent(InputConstants.KEY_RETURN, InputConstants.KEYCODE_RETURN, 0));
                state.addProperty("continueCallbackInvoked", true);
                report.addProperty("status", "waiting-for-private-world-after-known-warnings");
                persist.run();
            } catch (Exception failure) { fail.accept(failure); }
        }));
    }

    private ExtendedButton continueButton() {
        Component label = Component.literal(FMLTranslations.parseMessage("fml.button.continue.launch"));
        var button = screen.children().stream().filter(ExtendedButton.class::isInstance).map(ExtendedButton.class::cast)
                .filter(widget -> label.equals(widget.getMessage()))
                .reduce((a, b) -> { throw new IllegalStateException("Duplicate Continue Launch widgets"); })
                .orElseThrow(() -> new IllegalStateException("Exact Continue Launch widget missing; errors remain untouched"));
        if (!button.active || !button.visible) throw new IllegalStateException("Continue Launch widget unavailable");
        return button;
    }

    private JsonObject guardedState(Minecraft client) throws Exception {
        RendererBackend.requireApplySupported();
        Path profile = client.gameDirectory.toPath().toAbsolutePath().normalize();
        if (!profile.endsWith(Path.of("run-workloads", "port-263-runtime-r1", "neoforge")) || !profile.equals(profile.toRealPath()))
            throw new IllegalStateException("Warning callback requires the exact canonical private 26.3 NeoForge profile");
        Path world = profile.resolve("saves/ShaderLOD-Landscape");
        try (var entries = Files.list(profile.resolve("saves"))) {
            var worlds = entries.filter(Files::isDirectory).toList();
            if (worlds.size() != 1 || !worlds.getFirst().equals(world)) throw new IllegalStateException("Unexpected private-world inventory");
        }
        if (!world.equals(world.toRealPath()) || !world.resolve("level.dat").equals(world.resolve("level.dat").toRealPath()))
            throw new IllegalStateException("Private warning world must not alias another save");
        var viewport = SdlViewport.observe(client);
        if (client.level != null || client.player != null || !viewport.eligible())
            throw new IllegalStateException("Loading warning world/native SDL viewport/focus gate failed");
        var issues = List.copyOf(ModLoader.getLoadingIssues());
        if (!ModLoader.getLoadingErrors().isEmpty() || issues.size() != 1) throw new IllegalStateException("Only the single pinned Iris logo warning may continue");
        var ids = new TreeSet<String>(); var rows = new JsonArray();
        var state = new JsonObject(); state.add("issues", rows); state.addProperty("loadingErrors", 0);
        report.add("loadingWarningsIssueObservation", state); // Retain actual path/hash diagnostics even on refusal.
        for (var issue : issues) {
            var mod = issue.affectedMod();
            if (mod == null || issue.severity() != ModLoadingIssue.Severity.WARNING || issue.cause() != null || !WARNING.equals(issue.translationKey()))
                throw new IllegalStateException("Unknown loading issue; no continuation allowed");
            String id = mod.getModId();
            if (!VERSIONS.containsKey(id) || !ids.add(id) || !issue.translationArgs().equals(List.of(id)) || !VERSIONS.get(id).equals(mod.getVersion().toString()))
                throw new IllegalStateException("Loading warning is not for the exact pinned Iris1.11.7");
            Path archive = mod.getOwningFile().getFile().getFilePath();
            byte[] actualBytes = Files.readAllBytes(archive);
            String hash = digest(actualBytes);
            var row = new JsonObject(); row.addProperty("severity", issue.severity().name()); row.addProperty("translationKey", issue.translationKey());
            row.addProperty("translationArg", id); row.addProperty("affectedMod", id); row.addProperty("version", mod.getVersion().toString());
            row.addProperty("archive", archive.toString()); row.addProperty("archiveUri", archive.toUri().toString());
            row.addProperty("archiveSha256", hash); row.addProperty("causeAbsent", true); rows.add(row);
            if (!HASHES.get(id).equals(hash))
                throw new IllegalStateException("Affected loading-warning archive differs from the immutable Iris pin");
        }
        state.addProperty("profile", profile.toString()); state.addProperty("privateWorld", world.toString());
        viewport.appendTo(state);
        state.addProperty("observedOpenGlPreflightPassed", true);
        return state;
    }

    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}

package com.pp.shaderlod.fabric.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pp.shaderlod.integration.RendererBackend;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.zip.ZipFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.lwjgl.glfw.GLFW;

/** One exact vanilla backup prompt, only for the authorized private 26.2 profile. */
final class BackupPrompt {
    private static final String WORLD = "ShaderLOD-Landscape";
    private final String loader;
    private final String run;
    private final JsonObject report;
    private BackupConfirmScreen prompt;
    private Set<Path> beforeBackups;
    private Path backupDirectory;
    private Path worldDirectory;
    private String levelHash;
    private boolean proceeded;
    private boolean verified;
    private ConfirmScreen upgradeConfirmation;
    BackupPrompt(String loader, String run, JsonObject report) {
        this.loader = loader; this.run = run; this.report = report;
    }
    void beforeWorld(Minecraft client, Runnable persist, java.util.function.BooleanSupplier active, Consumer<Exception> fail) throws Exception {
        if (client.gui.screen() != null && client.gui.screen().getClass() == ConfirmScreen.class) {
            confirmUpgrade(client, persist, active, fail);
            return;
        }
        if (client.gui.screen() == null || client.gui.screen().getClass() != BackupConfirmScreen.class) return;
        if (prompt != null) {
            if (client.gui.screen() != prompt) throw new IllegalStateException("Unexpected second backup prompt");
            return;
        }
        RendererBackend.requireApplySupported();
        Path profile = client.gameDirectory.toPath().toAbsolutePath().normalize();
        if (!profile.endsWith(Path.of("run-workloads", "port-262-runtime-r1", loader))
                || !profile.equals(profile.toRealPath()))
            throw new IllegalStateException("Backup callback requires the exact private 26.2 profile without symlinks");
        worldDirectory = profile.resolve("saves").resolve(WORLD);
        try (var saves = Files.list(profile.resolve("saves"))) {
            var worlds = saves.filter(Files::isDirectory).toList();
            if (worlds.size() != 1 || !worlds.get(0).equals(worldDirectory))
                throw new IllegalStateException("Private profile must contain only the explicit target world");
        }
        Path levelDat = worldDirectory.resolve("level.dat");
        if (!worldDirectory.equals(worldDirectory.toRealPath()) || !levelDat.equals(levelDat.toRealPath()))
            throw new IllegalStateException("Private world/level.dat must not alias another save");
        var window = client.getWindow(); var target = client.gameRenderer.mainRenderTarget();
        if (!client.isWindowActive() || !window.isFullscreen() || GLFW.glfwGetWindowMonitor(window.handle()) == 0
                || window.getWidth() != 1920 || window.getHeight() != 1200 || target == null
                || target.width != 1920 || target.height != 1200)
            throw new IllegalStateException("Backup prompt native viewport/focus gate failed");
        prompt = (BackupConfirmScreen) client.gui.screen();
        Button backup = prompt.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> BackupConfirmScreen.BACKUP_AND_JOIN.equals(button.getMessage()))
                .reduce((a, b) -> { throw new IllegalStateException("Duplicate backup-and-join widgets"); })
                .orElseThrow(() -> new IllegalStateException("Exact backup=true widget missing"));
        if (prompt.children().size() != 3 || !backup.active || !backup.visible)
            throw new IllegalStateException("Unexpected backup prompt controls/cache-erase route");
        backupDirectory = profile.resolve("backups");
        beforeBackups = backupFiles();
        var state = new JsonObject(); report.add("backupPrompt", state);
        state.addProperty("profile", profile.toString()); state.addProperty("privateWorld", worldDirectory.toString());
        state.addProperty("screenClass", prompt.getClass().getName());
        state.addProperty("callbackWidget", backup.getMessage().getString());
        state.addProperty("backupRequested", true); state.addProperty("cacheEraseRequested", false);
        state.addProperty("backupCallbackInvoked", false);
        state.add("privateLevelDatBefore", schema(levelDat));
        levelHash = hash(levelDat); state.addProperty("privateLevelDatBeforeSha256", levelHash);
        var existing = new JsonArray(); beforeBackups.forEach(p -> existing.add(p.toString()));
        state.add("existingBackupPaths", existing);
        String name = "shaderlod-" + loader + "-roundtrip-" + run + "-backup-prompt.png";
        Path screenshot = profile.resolve("screenshots").resolve(name);
        state.addProperty("screenshot", screenshot.toString()); state.addProperty("screenshotStatus", "requested");
        report.addProperty("status", "capturing-exact-private-backup-prompt"); persist.run();
        Screenshot.grab(client.gameDirectory, name, target, 1, message -> client.execute(() -> {
            try {
                if (!active.getAsBoolean()) return; // Never proceed after the original probe failed/deadline expired.
                state.addProperty("screenshotMessage", message.getString());
                if (!Files.isRegularFile(screenshot)) throw new IllegalStateException("Backup prompt screenshot failed");
                state.addProperty("screenshotStatus", "saved"); persist.run();
                if (client.gui.screen() != prompt) throw new IllegalStateException("Backup prompt changed before its callback");
                RendererBackend.requireApplySupported();
                if (!active.getAsBoolean()) throw new IllegalStateException("Probe deadline expired before backup callback");
                backup.onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)); // Pinned widget invokes proceed(true,false).
                proceeded = true; state.addProperty("backupCallbackInvoked", true);
                report.addProperty("status", "waiting-for-private-world-upgrade"); persist.run();
            } catch (Exception failure) { fail.accept(failure); }
        }));
    }
    void afterWorld(Minecraft client, Runnable persist) throws Exception {
        if (!proceeded || verified) return;
        verifyBackup(client, persist);
    }
    private void verifyBackup(Minecraft client, Runnable persist) throws Exception {
        RendererBackend.requireApplySupported();
        Set<Path> added = backupFiles(); added.removeAll(beforeBackups);
        if (added.size() != 1) throw new IllegalStateException("Expected one actual new world backup ZIP, found " + added.size());
        Path zip = added.iterator().next();
        var state = report.getAsJsonObject("backupPrompt");
        try (var archive = new ZipFile(zip.toFile())) {
            var entry = archive.getEntry(WORLD + "/level.dat");
            if (entry == null) throw new IllegalStateException("New ZIP lacks the exact private world's level.dat");
            try (var input = archive.getInputStream(entry)) {
                String archived = digest(input);
                state.addProperty("backupLevelDatSha256", archived);
                if (!levelHash.equals(archived)) throw new IllegalStateException("Backup level.dat differs from pre-callback private world");
            }
            try (var input = archive.getInputStream(entry)) {
                var data = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap()).getCompoundOrEmpty("Data");
                state.addProperty("backupDataVersion", data.getInt("DataVersion").orElseThrow());
            }
        }
        state.addProperty("newBackupPath", zip.toString()); state.addProperty("newBackupSha256", hash(zip));
        state.addProperty("newBackupBytes", Files.size(zip));
        state.add("privateLevelDatAtBackupVerification", schema(worldDirectory.resolve("level.dat")));
        state.addProperty("privateLevelDatAtBackupVerificationSha256", hash(worldDirectory.resolve("level.dat")));
        state.addProperty("backupVerified", true);
        state.addProperty("backupVerificationWorldPresent", client.level != null);
        verified = true; persist.run();
    }
    private void confirmUpgrade(Minecraft client, Runnable persist, java.util.function.BooleanSupplier active,
            Consumer<Exception> fail) throws Exception {
        ConfirmScreen screen = (ConfirmScreen) client.gui.screen();
        if (!Component.translatable("upgradeWorld.done").equals(screen.getTitle()) || !proceeded)
            throw new IllegalStateException("Unexpected confirmation or missing this-run backup provenance");
        if (upgradeConfirmation != null) {
            if (screen != upgradeConfirmation) throw new IllegalStateException("Unexpected second upgrade confirmation");
            return;
        }
        RendererBackend.requireApplySupported();
        var window = client.getWindow(); var target = client.gameRenderer.mainRenderTarget();
        if (!client.isWindowActive() || !window.isFullscreen() || GLFW.glfwGetWindowMonitor(window.handle()) == 0
                || window.getWidth() != 1920 || window.getHeight() != 1200 || target == null
                || target.width != 1920 || target.height != 1200)
            throw new IllegalStateException("Upgrade confirmation viewport/focus gate failed");
        verifyBackup(client, persist); // Verify this run's ZIP/hash before the true callback.
        var yes = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> CommonComponents.GUI_YES.equals(button.getMessage()))
                .reduce((a, b) -> { throw new IllegalStateException("Duplicate upgrade Yes widgets"); })
                .orElseThrow(() -> new IllegalStateException("Exact upgrade Yes widget missing"));
        if (!yes.active || !yes.visible) throw new IllegalStateException("Upgrade Yes widget unavailable");
        upgradeConfirmation = screen;
        var state = new JsonObject(); report.add("upgradeConfirmation", state);
        state.addProperty("title", screen.getTitle().getString()); state.addProperty("yesCallbackInvoked", false);
        String name = "shaderlod-" + loader + "-roundtrip-" + run + "-upgrade-confirm.png";
        Path screenshot = client.gameDirectory.toPath().resolve("screenshots").resolve(name);
        state.addProperty("screenshot", screenshot.toString()); state.addProperty("screenshotStatus", "requested"); persist.run();
        Screenshot.grab(client.gameDirectory, name, target, 1, message -> client.execute(() -> {
            try {
                if (!Files.isRegularFile(screenshot)) throw new IllegalStateException("Upgrade confirmation screenshot failed");
                state.addProperty("screenshotStatus", "saved"); persist.run();
                if (client.gui.screen() != screen) throw new IllegalStateException("Upgrade confirmation changed before callback");
                RendererBackend.requireApplySupported();
                if (!active.getAsBoolean()) throw new IllegalStateException("Probe deadline expired before upgrade Yes callback");
                yes.onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                state.addProperty("yesCallbackInvoked", true); persist.run();
            } catch (Exception failure) { fail.accept(failure); }
        }));
    }
    private Set<Path> backupFiles() throws Exception {
        var files = new TreeSet<Path>();
        if (!Files.exists(backupDirectory)) return files;
        if (!backupDirectory.equals(backupDirectory.toRealPath())) throw new IllegalStateException("Backup directory aliases another path");
        try (var paths = Files.list(backupDirectory)) {
            for (var p : paths.filter(path -> path.getFileName().toString().endsWith(".zip")).toList()) {
                if (!p.equals(p.toRealPath())) throw new IllegalStateException("Backup ZIP aliases another path");
                files.add(p);
            }
        }
        return files;
    }
    private static JsonObject schema(Path level) throws Exception {
        var data = NbtIo.readCompressed(level, NbtAccounter.unlimitedHeap()).getCompoundOrEmpty("Data");
        var state = new JsonObject(); state.addProperty("path", level.toString());
        state.addProperty("dataVersion", data.getInt("DataVersion").orElseThrow());
        state.addProperty("versionName", data.getCompoundOrEmpty("Version").getString("Name").orElse("unknown"));
        return state;
    }
    private static String hash(Path path) throws Exception {
        try (var input = Files.newInputStream(path)) { return digest(input); }
    }
    private static String digest(java.io.InputStream input) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[65536]; int n;
        while ((n = input.read(buffer)) >= 0) if (n > 0) digest.update(buffer, 0, n);
        return HexFormat.of().formatHex(digest.digest());
    }
}

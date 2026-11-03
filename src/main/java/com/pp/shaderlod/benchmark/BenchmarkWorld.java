package com.pp.shaderlod.benchmark;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.pp.shaderlod.ShaderLod;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Mod-owned normal Overworld session, entered from menus. Generation and loading
 * happen before readiness; fixed-pose measurement does not move/reset the scene.
 * Register {@link #tick} once on the client event bus.
 */
public final class BenchmarkWorld {
    private enum Stage { IDLE, OPENING, PRELOAD, BUILD, LIGHTING, WARMING, READY, CLOSING }
    private static Stage stage = Stage.IDLE;
    private static String status = "Benchmark is closed";
    private static BenchmarkStorage storage;
    private static IntegratedServer owner;
    private static Screen returnScreen;
    private static CameraType previousCamera;
    private static Runnable readyCallback;
    private static CompletableFuture<?> task;
    private static CompletableFuture<?> poseTask;
    private static int poseSettledTicks;
    private static List<String> recipe;
    private static int chunkIndex, commandIndex, warmTicks, stableTicks, warmPose;
    private static long startedAt;
    private static boolean closeRequested, callbackDelivered, building, flying, flyDone, flyStarted;
    private static boolean entitiesCleared;
    private static int flyTick;
    private static final int CHUNKS_X = 8, CHUNKS_Z = 27;
    private static int flyDurationTicks = 240;
    private static String failureMessage;
    private static BenchmarkScene.Pose appliedPose;

    private BenchmarkWorld() {}

    /**
     * Menu-only entry deliberately preserves personal worlds: Save & Quit first.
     * Refusals throw before any world/save/config is changed. No multiplayer disconnect.
     */
    public static void open(Runnable ready) {
        var client = Minecraft.getInstance();
        if (!client.isSameThread()) throw new IllegalStateException("Open the benchmark on the client thread");
        if (active()) throw new IllegalStateException("A benchmark session already owns the scene");
        if (client.level != null || client.getConnection() != null || client.getSingleplayerServer() != null)
            throw new IllegalStateException("Save & Quit to the title menu before opening the ShaderLOD benchmark");
        try {
            storage = BenchmarkStorage.prepare(client.getLevelSource().getBaseDir());
        } catch (Exception failure) {
            status = "Benchmark refused: " + failure.getMessage();
            throw new IllegalStateException(status, failure);
        }
        returnScreen = client.screen == null ? new TitleScreen() : client.screen;
        previousCamera = client.options.getCameraType();
        readyCallback = ready == null ? () -> {} : ready;
        owner = null;
        task = null;
        poseTask = null;
        poseSettledTicks = 0;
        chunkIndex = commandIndex = warmTicks = stableTicks = warmPose = flyTick = 0;
        closeRequested = callbackDelivered = flying = flyDone = flyStarted = false;
        entitiesCleared = false;
        failureMessage = null;
        building = !storage.complete();
        recipe = building ? BenchmarkScene.commands() : List.of();
        startedAt = System.nanoTime();
        stage = Stage.OPENING;
        status = building ? "Creating the private benchmark world" : "Opening the cached benchmark world";
        try {
            if (storage.hasLevel()) {
                client.createWorldOpenFlows().openWorld(BenchmarkStorage.WORLD, BenchmarkWorld::openAborted);
            } else {
                var settings = new LevelSettings("ShaderLOD Benchmark " + version(), GameType.SPECTATOR,
                        false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                client.createWorldOpenFlows().createFreshLevel(BenchmarkStorage.WORLD, settings,
                        new WorldOptions(BenchmarkScene.SEED, false, false),
                        registries -> registries.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), returnScreen);
            }
        } catch (RuntimeException failure) { fail(failure); }
    }

    private static void openAborted() {
        status = "Benchmark opening was cancelled";
        closeRequested = true;
        stage = Stage.CLOSING;
    }

    public static boolean active() { return stage != Stage.IDLE; }
    public static boolean ready() { return stage == Stage.READY && !closeRequested; }
    public static String status() { return status; }
    public static String version() { return BenchmarkScene.VERSION; }
    public static int poseCount() { return BenchmarkScene.POSES.size(); }
    public static String poseName(int index) { return BenchmarkScene.POSES.get(index).name(); }
    /** Server acknowledgement plus a full second for correction/weather packets to settle. */
    public static boolean poseReady() {
        return ready() && !flying && poseTask != null && poseTask.isDone()
                && !poseTask.isCompletedExceptionally() && poseSettledTicks >= 20;
    }

    public static void tick(ClientTickEvent.Post ignored) {
        if (!active()) return;
        var client = Minecraft.getInstance();
        try {
            if (stage == Stage.CLOSING || closeRequested) { closeTick(client); return; }
            if (stage == Stage.OPENING) {
                var server = client.getSingleplayerServer();
                if (client.level == null || client.player == null || server == null) {
                    // Vanilla owns its opening screens and storage lock until it completes/aborts.
                    if ((System.nanoTime() - startedAt) > 300_000_000_000L)
                        fail(new IllegalStateException("World opening exceeded five minutes; waiting for safe close"));
                    return;
                }
                if (!server.getWorldPath(LevelResource.ROOT).toRealPath().equals(storage.directory().toRealPath())
                        || client.level.dimension() != Level.OVERWORLD)
                    throw new IllegalStateException("Opened world is not the owned benchmark Overworld");
                owner = server;
                client.options.setCameraType(CameraType.FIRST_PERSON);
                client.level.setRainLevel(0.0F);
                client.level.setThunderLevel(0.0F);
                stage = Stage.PRELOAD;
                task = owner.submit(() -> configureServer());
                return;
            }
            requireOwner();
            if (poseTask != null && poseTask.isDone() && !poseTask.isCompletedExceptionally()) poseSettledTicks++;
            if (task != null) {
                if (!task.isDone()) return;
                task.join();
                task = null;
            }
            switch (stage) {
                case PRELOAD -> preload();
                case BUILD -> build();
                case LIGHTING -> {
                    task = owner.submit(() -> owner.overworld().getChunkSource().getLightEngine().hasLightWork());
                    stage = Stage.WARMING;
                    warmTicks = stableTicks = 0;
                    warmPose = -1;
                    status = "Waiting for lighting and route meshes";
                }
                case WARMING -> warm(client);
                case READY -> {
                    if (flying) fly(client);
                }
                default -> {}
            }
        } catch (Exception failure) { fail(failure); }
    }

    private static void configureServer() {
        if (owner.overworld().getSeed() != BenchmarkScene.SEED)
            throw new IllegalStateException("Benchmark seed does not match its shipped recipe");
        for (String command : List.of("gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                "gamerule doMobSpawning false", "gamerule randomTickSpeed 0", "gamerule spawnChunkRadius 0",
                "gamerule doMobLoot false", "gamerule doEntityDrops false", "gamerule doTileDrops false",
                "gamerule sendCommandFeedback false", "gamerule commandModificationBlockLimit 262144"))
            command(command, false);
        var level = owner.overworld();
        level.setDefaultSpawnPos(new BlockPos(0, 202 + BenchmarkScene.Y_OFFSET, -20), 0);
        for (var player : owner.getPlayerList().getPlayers()) {
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(level, 0.5, 202 + BenchmarkScene.Y_OFFSET, -20.5, 0, 4);
        }
        level.setDayTime(6000);
        level.setWeatherParameters(1_000_000, 0, false, false);
        level.setRainLevel(0.0F);
        level.setThunderLevel(0.0F);
    }

    private static void preload() {
        if (chunkIndex >= CHUNKS_X * CHUNKS_Z) {
            stage = building ? Stage.BUILD : Stage.LIGHTING;
            if (!building) task = owner.submit(BenchmarkWorld::verifyAnchors);
            return;
        }
        int index = chunkIndex++;
        int x = -4 + index % CHUNKS_X, z = -2 + index / CHUNKS_X;
        status = "Preparing terrain chunks " + chunkIndex + "/" + (CHUNKS_X * CHUNKS_Z);
        task = owner.submit(() -> {
            owner.overworld().setChunkForced(x, z, true);
            owner.overworld().getChunk(x, z);
        });
    }

    private static void build() {
        if (commandIndex >= recipe.size()) {
            status = "Saving the authored benchmark scene";
            task = owner.submit(() -> {
                verifyAnchors();
                // Flush region writes before publishing the completed scene marker.
                owner.saveEverything(true, true, false);
                try { storage.markComplete(); }
                catch (java.io.IOException failure) { throw new IllegalStateException("Could not seal benchmark recipe", failure); }
            });
            building = false;
            stage = Stage.LIGHTING;
            return;
        }
        int from = commandIndex;
        commandIndex = Math.min(recipe.size(), from + 4);
        int to = commandIndex;
        status = "Authoring benchmark scene " + to + "/" + recipe.size();
        task = owner.submit(() -> {
            for (int i = from; i < to; i++) {
                String command = recipe.get(i);
                command(command, command.endsWith("minecraft:air"));
            }
        });
    }

    private static void command(String text, boolean emptyAirAllowed) {
        try {
            owner.getCommands().getDispatcher().execute(text, owner.createCommandSourceStack().withSuppressedOutput());
        } catch (CommandSyntaxException failure) {
            // A clear of an already-empty tile is the sole expected no-change operation.
            if (!emptyAirAllowed) throw new IllegalStateException("Benchmark recipe failed: " + text, failure);
        }
    }

    private static void verifyAnchors() {
        var level = owner.overworld();
        if (!level.getBlockState(new BlockPos(0, 210 + BenchmarkScene.Y_OFFSET, 320)).is(Blocks.STONE_BRICKS)
                || !level.getBlockState(new BlockPos(0, 195 + BenchmarkScene.Y_OFFSET, 120)).is(Blocks.WATER)
                || !level.getBlockState(new BlockPos(-1, 192 + BenchmarkScene.Y_OFFSET, 145)).is(Blocks.SEA_LANTERN)
                || !level.getBlockState(new BlockPos(36, 200 + BenchmarkScene.Y_OFFSET, 149)).is(Blocks.BLUE_ICE)
                || !level.getBlockState(new BlockPos(-10, 210 + BenchmarkScene.Y_OFFSET, 318)).is(Blocks.DIRT)
                || !level.getBlockState(new BlockPos(-20, 198 + BenchmarkScene.Y_OFFSET, 72)).is(Blocks.DIRT))
            throw new IllegalStateException("Benchmark geometry is incomplete or was modified; preserve its save for recovery");
    }

    private static void warm(Minecraft client) {
        // Visit every fixed pose (and its environment) so its nearby chunks are received/meshed.
        // Sodium uses its own queues; the conservative dwell complements vanilla's queue signal.
        int next = warmTicks / 50;
        if (next < poseCount()) {
            if (next != warmPose) {
                warmPose = next;
                applyPose(BenchmarkScene.POSES.get(next), true);
            }
            status = "Warming " + poseName(next) + " (" + (next + 1) + "/" + poseCount() + ")";
            warmTicks++;
            return;
        }
        if (warmPose != poseCount()) {
            warmPose = poseCount();
            applyPose(BenchmarkScene.POSES.getFirst(), true);
            stableTicks = 0;
            return;
        }
        stableTicks++;
        status = "Settling the day screening pose (" + stableTicks + "/100 ticks)";
        if (stableTicks < (entitiesCleared ? 20 : 100) || !client.levelRenderer.hasRenderedAllSections()) return;
        // Recheck lighting on the server after all edits, not only at the beginning of warmup.
        if (!owner.submit(() -> !owner.overworld().getChunkSource().getLightEngine().hasLightWork()).join()) return;
        if (!entitiesCleared) {
            task = owner.submit(() -> {
                try { owner.getCommands().getDispatcher().execute("kill @e[type=!minecraft:player]",owner.createCommandSourceStack().withSuppressedOutput()); }
                catch (CommandSyntaxException noEntities) { /* An empty owned scene is the desired state. */ }
            });
            entitiesCleared = true;
            stableTicks = 0;
            status = "Settling the cleaned benchmark scene";
            return;
        }
        stage = Stage.READY;
        status = "Ready: " + version() + "; " + poseName(0);
        if (!callbackDelivered) {
            callbackDelivered = true;
            var callback = readyCallback;
            readyCallback = null;
            callback.run();
        }
    }

    /** Sets environment/pose once. Caller must settle native shader history before timing. */
    public static void pose(int index) {
        if (!ready()) throw new IllegalStateException("Benchmark world is not ready");
        requireOwner();
        flying = false;
        flyDone = false;
        flyStarted = false;
        applyPose(BenchmarkScene.POSES.get(index), true);
        status = "Ready: " + version() + "; " + poseName(index);
    }

    private static void applyPose(BenchmarkScene.Pose pose, boolean environment) {
        appliedPose = pose;
        var client = Minecraft.getInstance();
        applyCamera(client, pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch());
        if (environment) {
            // Set vanilla precipitation endpoints once; native shader wetness/history still evolves.
            client.level.setRainLevel(pose.rain() ? 1.0F : 0.0F);
            client.level.setThunderLevel(0.0F);
        }
        var server = owner;
        var uuid = client.player.getUUID();
        task = server.submit(() -> {
            var player = server.getPlayerList().getPlayer(uuid);
            if (player == null) throw new IllegalStateException("Benchmark player disappeared");
            player.teleportTo(server.overworld(), pose.x(), pose.y(), pose.z(), Set.of(), pose.yaw(), pose.pitch());
            if (environment) {
                server.overworld().setDayTime(pose.time());
                server.overworld().setWeatherParameters(pose.rain() ? 0 : 1_000_000,
                        pose.rain() ? 1_000_000 : 0, pose.rain(), false);
                server.overworld().setRainLevel(pose.rain() ? 1.0F : 0.0F);
                server.overworld().setThunderLevel(0.0F);
            }
        });
        poseTask = task;
        poseSettledTicks = 0;
    }

    /** Twelve-second 20Hz camera route, without readback, screenshots or encoders. */
    public static void beginFlythrough() {
        beginFlythrough(240);
    }

    /** Same normalized route at a caller-selected duration; at least two seconds. */
    public static void beginFlythrough(int durationTicks) {
        if (!ready()) throw new IllegalStateException("Benchmark world is not ready");
        if (durationTicks < 40 || durationTicks > 1200)
            throw new IllegalArgumentException("Benchmark route duration must be 40..1200 ticks");
        requireOwner();
        flyDurationTicks = durationTicks;
        flyTick = 0;
        flyDone = false;
        flyStarted = false;
        flying = true;
        applyPose(BenchmarkScene.POSES.getFirst(), true);
        status = "Running the benchmark camera route";
    }

    public static boolean flythroughDone() { return flyDone; }

    /** Actual 20Hz route phase for matching motion evidence; independent of rendered-frame count. */
    public static int flythroughElapsedTicks() { return flyTick; }

    /** Start the existing route from an already acknowledged day/clear origin, without a second teleport. */
    public static void beginPreparedFlythrough(int durationTicks) {
        if (!poseReady() || flyStarted || appliedPose != BenchmarkScene.POSES.getFirst())
            throw new IllegalStateException("Prepared route requires the acknowledged day/clear origin");
        if (durationTicks < 40 || durationTicks > 1200)
            throw new IllegalArgumentException("Benchmark route duration must be 40..1200 ticks");
        requireOwner();
        flyDurationTicks = durationTicks;
        flyTick = 0;
        flyDone = flyStarted = false;
        flying = true;
        status = "Running the prepared benchmark camera route";
    }

    /**
     * True from the first moving tick through completion, after the initial
     * teleport/environment acknowledgement and twenty client ticks of settlement.
     * Sampling owners should stay paused until this becomes true.
     */
    public static boolean flythroughStarted() { return ready() && flyStarted; }

    private static void fly(Minecraft client) {
        // The route starts only after its one-time teleport/environment acknowledgement.
        if (poseTask == null || !poseTask.isDone() || poseSettledTicks < 20) return;
        flyStarted = true;
        double t = Math.min(1.0, ++flyTick / (double)flyDurationTicks);
        // Slow forward/side motion through foliage, gallery, shore and distant valley faces.
        double x = -18 * Math.sin(t * Math.PI * 2), z = -20.5 + 200 * t;
        double y = 202 + BenchmarkScene.Y_OFFSET + 5 * Math.sin(t * Math.PI);
        float yaw = (float)(12 * Math.sin(t * Math.PI * 2));
        applyCamera(client, x, y, z, yaw, 7);
        // Ordinary spectator movement packets keep server tracking/streaming with this camera.
        if (flyTick >= flyDurationTicks) {
            flying = false;
            flyDone = true;
            status = "Benchmark camera route complete";
        }
    }

    private static void applyCamera(Minecraft client, double x, double y, double z, float yaw, float pitch) {
        client.player.setDeltaMovement(Vec3.ZERO);
        client.player.setPos(x, y, z);
        client.player.setYRot(yaw);
        client.player.setXRot(pitch);
        client.player.setYHeadRot(yaw);
    }

    private static void requireOwner() {
        var client = Minecraft.getInstance();
        if (owner == null || client.getSingleplayerServer() != owner || client.level == null || client.player == null
                || client.level.dimension() != Level.OVERWORLD || owner.isPublished())
            throw new IllegalStateException("Private benchmark session ownership was lost");
    }

    /** Normal save/close only; never deletes/replaces a save or disconnects another server. */
    public static void exit() {
        if (!active()) return;
        flying = false;
        flyStarted = false;
        closeRequested = true;
        stage = Stage.CLOSING;
        status = "Saving and closing the private benchmark world";
        closeTick(Minecraft.getInstance());
    }

    private static void closeTick(Minecraft client) {
        var server = client.getSingleplayerServer();
        if (server != null) {
            // An opening cancelled before its first tick can still finish asynchronously.
            try {
                if (!server.getWorldPath(LevelResource.ROOT).toRealPath().equals(storage.directory().toRealPath())) {
                    finishClose(client, "Benchmark closed; another world was preserved");
                    return;
                }
            } catch (java.io.IOException failure) {
                status = "Cannot verify benchmark ownership for closing: " + failure.getMessage();
                return;
            }
            if (task != null && !task.isDone()) return;
            if (client.level == null) return;
            // Match vanilla Save & Quit: disconnect level, wait/close integrated storage, return menu.
            client.level.disconnect();
            client.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
            finishClose(client, "Benchmark closed and saved");
        } else if (client.level == null && (owner != null || client.screen == returnScreen
                || client.screen instanceof TitleScreen)) {
            finishClose(client, "Benchmark closed");
        } else {
            status = "Waiting for the owned world loader to finish before safe close";
        }
    }

    private static void finishClose(Minecraft client, String message) {
        if (previousCamera != null) client.options.setCameraType(previousCamera);
        if (client.level == null) client.setScreen(returnScreen == null ? new TitleScreen() : returnScreen);
        stage = Stage.IDLE;
        status = failureMessage == null ? message : failureMessage + "; private world closed safely";
        owner = null;
        storage = null;
        task = null;
        poseTask = null;
        recipe = null;
        readyCallback = null;
        returnScreen = null;
        previousCamera = null;
        flying = false;
        flyStarted = false;
    }

    private static void fail(Exception failure) {
        ShaderLod.LOGGER.error("ShaderLOD benchmark session failed", failure);
        status = "Benchmark failed: " + failure.getMessage();
        failureMessage = status;
        flying = false;
        flyStarted = false;
        closeRequested = true;
        stage = Stage.CLOSING;
    }
}

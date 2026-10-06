package com.pp.shaderlod.dev;

import com.pp.shaderlod.ShaderLod;
import com.pp.shaderlod.ShaderLodConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Development-only, opt-in reproducible scene; no native UI automation needed. */
@Mod(value = "shaderlod_dev", dist = Dist.CLIENT)
public final class SmokeClient {
    private boolean started;
    private int ticks;
    private boolean configured;
    private boolean dhStatsFailed;
    private static final String WORLD = "ShaderLOD-Smoke";

    public SmokeClient() {
        com.pp.shaderlod.dev.timing.WorkTraceEvidence.initialize();
        ComparisonClient.register();
        if (Boolean.getBoolean("shaderlod.dev.smoke")) {
            NeoForge.EVENT_BUS.addListener(this::tick);
        }
    }

    private void tick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        client.options.pauseOnLostFocus = false;
        if (!started && client.screen instanceof TitleScreen) {
            started = true;
            if (client.getLevelSource().levelExists(WORLD)) {
                client.createWorldOpenFlows().openWorld(WORLD, () -> {});
            } else {
                var settings = new LevelSettings(WORLD, GameType.CREATIVE, false,
                        Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                client.createWorldOpenFlows().createFreshLevel(WORLD, settings,
                        new WorldOptions(8675309L, true, false), WorldPresets::createNormalWorldDimensions,
                        client.screen);
            }
        }
        if (!started || client.level == null || client.player == null || client.getSingleplayerServer() == null) {
            return;
        }
        if (!configured) {
            configured = true;
            ShaderLodConfig.DEBUG.set(false);
            ShaderLodConfig.ENABLED.set(true);
            var server = client.getSingleplayerServer();
            server.execute(() -> {
                var commands = server.getCommands();
                var source = server.createCommandSourceStack();
                for (String command : new String[] {"time set noon", "weather clear", "gamerule doDaylightCycle false",
                        "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
                        "fill -12 130 -4 12 130 160 minecraft:stone", "fill -8 131 16 -6 136 18 minecraft:stone_bricks",
                        "fill 6 131 56 8 136 58 minecraft:stone_bricks", "fill -8 131 110 -6 136 112 minecraft:stone_bricks",
                        "tp @a 0 132 0 0 0"}) {
                    commands.performPrefixedCommand(source, command);
                }
            });
            client.options.hideGui = true;
            ShaderLod.LOGGER.info("ShaderLOD smoke scene configured (seed 8675309; camera 0,132,0)");
        }
        ++ticks;
        if (ticks == 200 || ticks == 320) {
            String name = ticks == 200 ? "shaderlod-smoke-quality.png" : "shaderlod-smoke-debug.png";
            Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(),
                    message -> ShaderLod.LOGGER.info("ShaderLOD smoke screenshot: {}", message.getString()));
        }
        if (ticks == 240) {
            ShaderLodConfig.DEBUG.set(true);
        }
        if (ticks == 360) {
            ShaderLodConfig.ENABLED.set(false);
        }
        if (ticks == 460) {
            Screenshot.grab(client.gameDirectory, "shaderlod-smoke-disabled-debug.png", client.getMainRenderTarget(),
                    message -> ShaderLod.LOGGER.info("ShaderLOD disabled screenshot: {}", message.getString()));
        }
        if (ticks == 520) {
            ShaderLodConfig.DEBUG.set(false);
            ShaderLodConfig.ENABLED.set(true);
        }
        if (ticks == 560) {
            var server = client.getSingleplayerServer();
            server.execute(() -> server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack(), "tp @a 0 200 0 -45 30"));
        }
        if (ticks == 900 || ticks == 1500) {
            Screenshot.grab(client.gameDirectory, "shaderlod-smoke-horizon-" + ticks + ".png",
                    client.getMainRenderTarget(), message -> ShaderLod.LOGGER.info(
                            "ShaderLOD horizon screenshot: {}", message.getString()));
        }
        if (ticks == 1560) {
            client.options.hideGui = false;
            ShaderLod.LOGGER.info("ShaderLOD smoke capture finished; client remains open for inspection");
        }
        if (ticks <= 1800 && ticks % 200 == 0 && !dhStatsFailed
                && net.neoforged.fml.ModList.get().isLoaded("distanthorizons")) {
            reportDhStats();
        }
    }

    // Isolated from the distributable mod and optional at class-load time.
    // Inspect the actual DH render-list statistics, not just its presence.
    private void reportDhStats() {
        try {
            var sharedApi = Class.forName("com.seibel.distanthorizons.core.api.internal.SharedApi");
            var world = sharedApi.getMethod("tryGetDhClientWorld").invoke(null);
            var clientApi = Class.forName("com.seibel.distanthorizons.core.api.internal.ClientApi");
            var state = clientApi.getField("RENDER_STATE").get(null);
            var wrapper = state.getClass().getField("clientLevelWrapper").get(state);
            var wrapperType = Class.forName("com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper");
            var level = world.getClass().getMethod("getClientLevel", wrapperType).invoke(world, wrapper);
            var buffers = level.getClass().getMethod("getRenderBufferHandler").invoke(level);
            String stats = (String) buffers.getClass().getMethod("getVboRenderDebugMenuString").invoke(buffers);
            ShaderLod.LOGGER.info("ShaderLOD DH render statistics: {}", stats);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            dhStatsFailed = true;
            ShaderLod.LOGGER.warn("ShaderLOD DH statistics unavailable", exception);
        }
    }
}

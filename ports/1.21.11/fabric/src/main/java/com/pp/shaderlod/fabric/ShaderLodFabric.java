package com.pp.shaderlod.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

public final class ShaderLodFabric implements ClientModInitializer {
    @Override public void onInitializeClient() {
        com.pp.shaderlod.measurement.ModArtifactIdentity.configure(() -> {
            var mod=net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("shaderlod").orElseThrow();
            var origin=mod.getOrigin();var roots=mod.getRootPaths();
            var paths=origin.getKind()==net.fabricmc.loader.api.metadata.ModOrigin.Kind.PATH?origin.getPaths():roots;
            return new com.pp.shaderlod.measurement.ModArtifactIdentity.Origins("Fabric ModContainer", "loader origin="+origin.getKind()+"; declared module roots", paths, roots, origin.getKind()==net.fabricmc.loader.api.metadata.ModOrigin.Kind.PATH);
        });
        FabricLodConfig.load(FabricLoader.getInstance().getConfigDir().resolve("shaderlod-client.json"));
        com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation(com.pp.shaderlod.optimization.OptimizationId.QUAD_COMMAND_COMPACTION,
                () -> "native in-place fill compaction (eligible=" + com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled() + "; use counters not reported)");
        com.pp.shaderlod.optimization.OptimizationRuntimeContext.configure(() -> FabricLodConfig.snapshot().core());
        com.pp.shaderlod.optimization.mips.DeadMipAdmission.initialize();
        try { com.pp.shaderlod.optimization.OptimizationConfig.load(FabricLoader.getInstance().getConfigDir().resolve("shaderlod-optimizations.properties")); }
        catch (java.io.IOException failure) { LoggerFactory.getLogger("shaderlod").error("Could not load independent optimization flags", failure); }
        var settings = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.shaderlod.settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("shaderlod", "settings"))));
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher,registry)->com.pp.shaderlod.lod.terrain.GeometryLodCommands.register(dispatcher));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            com.pp.shaderlod.shadowcoherence12111.ShadowCullingRuntime.onClientTick(client);
            com.pp.shaderlod.lod.terrain.GeometryLodController.tick();
            while (settings.consumeClick()) {
                if (!(client.screen instanceof FabricLodScreen)) client.setScreen(new FabricLodScreen(client.screen));
            }
        });
        LoggerFactory.getLogger("shaderlod").info("ShaderLOD Fabric 1.21.11 initialized; Iris required; DH present={}",
                FabricLoader.getInstance().isModLoaded("distanthorizons"));
    }
}

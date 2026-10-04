package com.pp.shaderlod;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;

@Mod(value = ShaderLod.MOD_ID, dist = Dist.CLIENT)
public final class ShaderLod {
    public static final String MOD_ID = "shaderlod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ShaderLod(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ShaderLodConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(ShaderLodCommands::register);
        NeoForge.EVENT_BUS.addListener(ShaderLodCommands::reportRuntime);
        NeoForge.EVENT_BUS.addListener(GeometryLodController::onClientTick);
        LOGGER.info("ShaderLOD loaded; Iris required, Distant Horizons present={}",
                ModList.get().isLoaded("distanthorizons"));
    }
}

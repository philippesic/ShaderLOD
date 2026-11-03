package com.pp.shaderlod;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;
/** 26.3 shader service port; 1.21.1 experimental renderer hooks are excluded. */
@Mod(value = ShaderLod.MOD_ID, dist = Dist.CLIENT)
public final class ShaderLod {
 public static final String MOD_ID = "shaderlod";
 public static final Logger LOGGER = LogUtils.getLogger();
 public ShaderLod(ModContainer container) {
        com.pp.shaderlod.measurement.ModArtifactIdentity.configure(() -> {
            var info=net.neoforged.fml.ModList.get().getModFileById("shaderlod");
            if(info==null)throw new IllegalStateException("ShaderLOD loader origin unavailable");var file=info.getFile();
            var contents=file.getContents();return new com.pp.shaderlod.measurement.ModArtifactIdentity.Origins("NeoForge IModFile/JarContents", "declared full JarContents input roots (including filtered-out bytes); backend anchors checked", java.util.List.copyOf(contents.getContentRoots()), java.util.List.copyOf(contents.getContentRoots())); 
        });
        com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation(com.pp.shaderlod.optimization.OptimizationId.QUAD_COMMAND_COMPACTION,
                () -> "private submission copy (eligible=" + com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled() + "; use counters not reported)");
  com.pp.shaderlod.optimization.OptimizationRuntimeContext.configure(() -> new CoreUniformFrame.Core(ShaderLodConfig.snapshot(), ShaderLodConfig.PRESET.get()), com.pp.shaderlod.integration.RendererBackend::requireApplySupported);
        com.pp.shaderlod.optimization.mips.DeadMipAdmission.initialize();
        try { com.pp.shaderlod.optimization.OptimizationConfig.load(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("shaderlod-optimizations.properties")); }
        catch (java.io.IOException failure) { LOGGER.error("Could not load independent optimization flags", failure); }
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post event)->com.pp.shaderlod.lod.terrain.GeometryLodController.tick());
  net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RegisterClientCommandsEvent event)->com.pp.shaderlod.lod.terrain.GeometryLodCommands.register(event.getDispatcher()));
  container.registerConfig(ModConfig.Type.CLIENT, ShaderLodConfig.SPEC);
  container.registerConfig(ModConfig.Type.CLIENT, ShaderLodEffectConfig.SPEC, "shaderlod-effects-client.toml");
  container.registerExtensionPoint(IConfigScreenFactory.class, ShaderLodSettingsScreen::new);
 }
}

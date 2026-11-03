package com.pp.shaderlod;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;

@Mod(value = ShaderLod.MOD_ID, dist = Dist.CLIENT)
public final class ShaderLod {
    public static final String MOD_ID = "shaderlod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ShaderLod(ModContainer container) {
        com.pp.shaderlod.measurement.ModArtifactIdentity.configure(() -> {
            var info=net.neoforged.fml.ModList.get().getModFileById("shaderlod");
            if(info==null)throw new IllegalStateException("ShaderLOD loader origin unavailable");var file=info.getFile();
            var jar=file.getSecureJar();return new com.pp.shaderlod.measurement.ModArtifactIdentity.Origins("NeoForge IModFile/SecureJar", "effective bounded SecureJar module view +primary input; shadowed secondary physical bytes not exposed by public API", java.util.List.of(jar.getPrimaryPath()), java.util.List.of(jar.getRootPath().toAbsolutePath().normalize()),false);
        });
        com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation(com.pp.shaderlod.optimization.OptimizationId.QUAD_COMMAND_COMPACTION,
                com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime::compactionStatus);
        com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation(com.pp.shaderlod.optimization.OptimizationId.SHADOW_POSITIVE_VERTEX,
                () -> "native shadow frustum positive-vertex test; active=" + ShadowCullingController.isActive());
        com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation(com.pp.shaderlod.optimization.OptimizationId.SHADOW_VISIBILITY_LISTS,
                () -> "separate native shadow visibility lists; active=" + ShadowVisibilityListsController.isActive());
        com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation(com.pp.shaderlod.optimization.OptimizationId.SHADOW_FRUSTUM_COHERENCE,
                () -> "native frustum coherent-result reuse; active=" + ShadowFrustumCoherenceController.isActive());
        com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation(com.pp.shaderlod.optimization.OptimizationId.TERRAIN_RADIUS,
                () -> "detailed terrain radius with distant-terrain coverage guard; " + GeometryLodController.status());
        com.pp.shaderlod.optimization.OptimizationRuntimeContext.configure(() -> new CoreUniformFrame.Core(ShaderLodConfig.snapshot(), ShaderLodConfig.PRESET.get()));
        com.pp.shaderlod.optimization.mips.DeadMipAdmission.initialize();
        com.pp.shaderlod.optimization.framecopy.MakeUpDeadCopySetup.initialize();
        com.pp.shaderlod.packs.PackSelectionService.enableRecipeScopedSelections();
        com.pp.shaderlod.optimization.OptimizationEditTransaction.enableLiveUniformUpdates();
        com.pp.shaderlod.optimization.OptimizationConfig.enableShaderProfiles();
        try { com.pp.shaderlod.optimization.OptimizationConfig.load(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("shaderlod-optimizations.properties")); }
        catch (java.io.IOException failure) { LOGGER.error("Could not load independent optimization flags", failure); }
        container.registerConfig(ModConfig.Type.CLIENT, ShaderLodConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ShaderLodEffectConfig.SPEC, "shaderlod-effects-client.toml");
        container.registerExtensionPoint(IConfigScreenFactory.class, ShaderLodSettingsScreen::new);
        com.pp.shaderlod.tuning.AutoTuner.setBenchmarkOwnerCheck(()->com.pp.shaderlod.benchmark.BenchmarkRunSession.active()
                ||com.pp.shaderlod.benchmark.BenchmarkRunSession.restorationPending());
        NeoForge.EVENT_BUS.addListener(ShaderOptimizationProfiles::tick);
        NeoForge.EVENT_BUS.addListener(com.pp.shaderlod.benchmark.BenchmarkWorld::tick);
        NeoForge.EVENT_BUS.addListener(com.pp.shaderlod.benchmark.BenchmarkRunSession::tick);
        NeoForge.EVENT_BUS.addListener(com.pp.shaderlod.tuning.AutoTuner::tick);
        NeoForge.EVENT_BUS.addListener(BenchmarkUi::title);
        NeoForge.EVENT_BUS.addListener(BenchmarkUi::overlay);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.GameShuttingDownEvent event)->com.pp.shaderlod.tuning.AutoTuner.shutdown());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.GameShuttingDownEvent event)->com.pp.shaderlod.benchmark.BenchmarkRunSession.shutdown());
        NeoForge.EVENT_BUS.addListener(ShaderLodCommands::register);
        NeoForge.EVENT_BUS.addListener(ShaderLodCommands::reportRuntime);
        NeoForge.EVENT_BUS.addListener(GeometryLodController::onClientTick);
        NeoForge.EVENT_BUS.addListener(ShadowCullingRuntime::onClientTick);
        NeoForge.EVENT_BUS.addListener(ShadowVisibilityListsRuntime::onClientTick);
        LOGGER.info("ShaderLOD loaded; Iris required, Distant Horizons present={}",
                ModList.get().isLoaded("distanthorizons"));
    }
}

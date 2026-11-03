package com.pp.shaderlod.integration.makeupquad;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.fabric.FabricLodConfig;
import java.util.Map;
import java.util.TreeMap;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.fabricmc.loader.api.FabricLoader;
/** Live ordinary core config, original pack and pinned effective inputs; no dev mode or observer. */
public final class MakeUpQuadRuntime {
    private static Object checkedPipeline, checkedPack, checkedValues, checkedWorld;
    private static String checkedName;
    private static Map<String,Boolean> checkedBooleanOverrides;
    private static Map<String,String> checkedStringOverrides;
    private static long checkedGeneration = -1;
    private static boolean checkedResult, checkedPhotonRoute, checkedResearchRoutes;
    private static boolean checkedAstra;
    private static boolean checkedSolas;
    private MakeUpQuadRuntime() {}
    public static final String PORT_OPT_IN = "shaderlod.rendererSpeedPortR1";
    public static final Map<String,String> REQUIRED_STACK=Map.ofEntries(
        Map.entry("fabric-api","0.141.6+1.21.11"),
        Map.entry("fabric-api-base","1.0.5+4ebb5c083e"),
        Map.entry("fabric-api-lookup-api-v1","1.6.114+20dc27073e"),
        Map.entry("fabric-biome-api-v1","17.1.1+4fc5413f3e"),
        Map.entry("fabric-block-api-v1","1.1.10+4ebb5c083e"),
        Map.entry("fabric-block-view-api-v2","1.0.39+4ebb5c083e"),
        Map.entry("fabric-client-gametest-api-v1","4.3.5+bfe9fcf43e"),
        Map.entry("fabric-command-api-v2","2.4.7+6b42a6003e"),
        Map.entry("fabric-content-registries-v0","10.2.14+4fc5413f3e"),
        Map.entry("fabric-convention-tags-v1","2.1.55+7f945d5b3e"),
        Map.entry("fabric-convention-tags-v2","2.17.3+8ef948ba3e"),
        Map.entry("fabric-crash-report-info-v1","0.3.23+4ebb5c083e"),
        Map.entry("fabric-data-attachment-api-v1","1.8.48+eed0806f3e"),
        Map.entry("fabric-data-generation-api-v1","23.5.0+88d7da613e"),
        Map.entry("fabric-dimensions-v1","4.0.28+4fc5413f3e"),
        Map.entry("fabric-entity-events-v1","3.1.1+1d0ab4303e"),
        Map.entry("fabric-events-interaction-v0","4.1.1+3b89ecf63e"),
        Map.entry("fabric-game-rule-api-v1","2.0.3+4fc5413f3e"),
        Map.entry("fabric-gametest-api-v1","3.1.27+4fc5413f3e"),
        Map.entry("fabric-item-api-v1","11.6.0+67f17bb43e"),
        Map.entry("fabric-item-group-api-v1","4.2.36+4fc5413f3e"),
        Map.entry("fabric-key-binding-api-v1","1.1.7+4fc5413f3e"),
        Map.entry("fabric-lifecycle-events-v1","2.6.15+4ebb5c083e"),
        Map.entry("fabric-loot-api-v2","3.0.73+3f89f5a53e"),
        Map.entry("fabric-loot-api-v3","2.0.20+78c8b4663e"),
        Map.entry("fabric-message-api-v1","6.1.12+4ebb5c083e"),
        Map.entry("fabric-model-loading-api-v1","6.0.15+4fc5413f3e"),
        Map.entry("fabric-networking-api-v1","5.1.6+6b6d71a53e"),
        Map.entry("fabric-object-builder-api-v1","21.1.40+4fc5413f3e"),
        Map.entry("fabric-particles-v1","4.2.12+4fc5413f3e"),
        Map.entry("fabric-recipe-api-v1","8.2.4+4ebb5c083e"),
        Map.entry("fabric-registry-sync-v0","6.2.6+1718722b3e"),
        Map.entry("fabric-renderer-api-v1","8.0.3+f4ffd2e53e"),
        Map.entry("fabric-renderer-indigo","5.0.3+f4ffd2e53e"),
        Map.entry("fabric-rendering-fluids-v1","3.1.43+4ebb5c083e"),
        Map.entry("fabric-rendering-v1","16.2.10+0290ad933e"),
        Map.entry("fabric-resource-conditions-api-v1","5.0.35+4fc5413f3e"),
        Map.entry("fabric-resource-loader-v0","3.3.4+4fc5413f3e"),
        Map.entry("fabric-resource-loader-v1","1.0.10+78c8b4663e"),
        Map.entry("fabric-screen-api-v1","3.1.7+4ebb5c083e"),
        Map.entry("fabric-screen-handler-api-v1","1.3.162+4fc5413f3e"),
        Map.entry("fabric-serialization-api-v1","1.0.5+4ebb5c083e"),
        Map.entry("fabric-sound-api-v1","1.0.51+4fc5413f3e"),
        Map.entry("fabric-tag-api-v1","1.3.0+88d7da613e"),
        Map.entry("fabric-transfer-api-v1","6.0.25+4fc5413f3e"),
        Map.entry("fabric-transitive-access-wideners-v1","7.1.0+014c8cec3e"),
        Map.entry("fabricloader","0.19.5"),
        Map.entry("io_github_douira_glsl-transformer","3.0.0-pre3"),
        Map.entry("iris","1.10.8+mc1.21.11"),
        Map.entry("minecraft","1.21.11"),
        Map.entry("mixinextras","0.5.5"),
        Map.entry("org_anarres_jcpp","1.4.14"),
        Map.entry("org_antlr_antlr4-runtime","4.13.1"),
        Map.entry("sodium","0.8.14+mc1.21.11"));
    /** Fresh loader-native snapshot using the same enumeration as this exact stack contract. */
    public static Map<String,String> loadedStack() {
        var stack=new TreeMap<String,String>();
        for (var mod:FabricLoader.getInstance().getAllMods()) {
        var metadata=mod.getMetadata(); if (!("java".equals(metadata.getId())&&"builtin".equals(metadata.getType())))
        stack.put(metadata.getId(),metadata.getVersion().getFriendlyString());
        }
        return Map.copyOf(stack);
    }
    public static boolean stackMatches(Map<String,String> stack) {
        if (stack == null || !stack.containsKey("shaderlod")) return false;
        var actual = new TreeMap<>(stack); actual.remove("shaderlod");
        String dev=actual.remove("shaderlod_dev");
        if (dev != null && !dev.equals("0.1.0-dev-12111")) return false;
        return actual.equals(REQUIRED_STACK);
    }
    public static boolean resourcesMatch(java.util.Collection<String> selected) {
        return selected != null && selected.size() == 51 &&
            new java.util.HashSet<>(selected).equals(java.util.Set.of("vanilla", "fabric-api", "fabric-api-base", "fabric-api-lookup-api-v1", "fabric-biome-api-v1", "fabric-block-api-v1", "fabric-block-view-api-v2", "fabric-client-gametest-api-v1", "fabric-command-api-v2", "fabric-content-registries-v0", "fabric-convention-tags-v1", "fabric-convention-tags-v2", "fabric-crash-report-info-v1", "fabric-data-attachment-api-v1", "fabric-data-generation-api-v1", "fabric-dimensions-v1", "fabric-entity-events-v1", "fabric-events-interaction-v0", "fabric-game-rule-api-v1", "fabric-gametest-api-v1", "fabric-item-api-v1", "fabric-item-group-api-v1", "fabric-key-binding-api-v1", "fabric-lifecycle-events-v1", "fabric-loot-api-v2", "fabric-loot-api-v3", "fabric-message-api-v1", "fabric-model-loading-api-v1", "fabric-networking-api-v1", "fabric-object-builder-api-v1", "fabric-particles-v1", "fabric-recipe-api-v1", "fabric-registry-sync-v0", "fabric-renderer-api-v1", "fabric-renderer-indigo", "fabric-rendering-fluids-v1", "fabric-rendering-v1", "fabric-resource-conditions-api-v1", "fabric-resource-loader-v0", "fabric-resource-loader-v1", "fabric-screen-api-v1", "fabric-screen-handler-api-v1", "fabric-serialization-api-v1", "fabric-sound-api-v1", "fabric-tag-api-v1", "fabric-transfer-api-v1", "fabric-transitive-access-wideners-v1", "fabricloader", "iris", "shaderlod", "sodium"));
    }
    public static boolean configAllows(boolean loaded, boolean enabled, LodPreset preset) {
        return loaded && enabled && preset == LodPreset.PERFORMANCE;
    }
    public static boolean capabilitiesAllow(int pointerSize, boolean gl41, long multiDrawFunction) {
        return pointerSize == 8 && gl41 && multiDrawFunction != 0;
    }
    /** Private recording permission only; every consumer/mutation retains full current admission. */
    public static boolean scalarRecordingRequested() {
        try {
            if (!com.pp.shaderlod.optimization.OptimizationConfig.requested(
                    com.pp.shaderlod.optimization.OptimizationId.INDEX_SCALAR_CACHE)) return false;
            var config=com.pp.shaderlod.fabric.FabricLodConfig.snapshot();
            return configAllows(com.pp.shaderlod.fabric.FabricLodConfig.loaded(),config.enabled(),config.preset());
        } catch (Exception | LinkageError unavailable) { return false; }
    }
    /** This loader already caches native metadata independently; retain its recording policy. */
    public static boolean compactionRecordingRequested() {
        return com.pp.shaderlod.optimization.OptimizationConfig.requested(
            com.pp.shaderlod.optimization.OptimizationId.QUAD_COMMAND_COMPACTION);
    }
    /** Individual request plus every existing native source/API/resource/config gate. */
    public static boolean enabled(com.pp.shaderlod.optimization.OptimizationId id) {
        return com.pp.shaderlod.optimization.OptimizationConfig.requested(id) && enabled();
    }
    public static boolean enabled() {
        try {
            var config=com.pp.shaderlod.fabric.FabricLodConfig.snapshot();
            return configAllows(com.pp.shaderlod.fabric.FabricLodConfig.loaded(),config.enabled(),config.preset()) && nativeGraphAvailable();
        } catch (Exception | LinkageError unavailable) { return false; }
    }
    /** Actual current certified native graph, independent of the pending Core On/preset commit. */
    public static boolean nativeGraphAvailable() {
        try {
            if (MakeUpQuadLifecycle.suspended()) return false;
            var client=Minecraft.getInstance();
            if (client.level == null || client.level.dimension() != Level.OVERWORLD ||
                !resourcesMatch(client.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList()) ||
                !MakeUpQuadApiPins.exactApi() || Iris.isFallback()) return false;
            Object pipeline=Iris.getPipelineManager().getPipelineNullable();
            var pack=Iris.getCurrentPack().orElse(null);
            if (pipeline == null || pack == null || pipeline.getClass() != IrisRenderingPipeline.class ||
                ((IrisRenderingPipeline)pipeline).getSodiumPrograms().getClass() != SodiumPrograms.class) return false;
            var caps=org.lwjgl.opengl.GL.getCapabilities();
            if (!capabilitiesAllow(org.lwjgl.system.Pointer.POINTER_SIZE,caps.OpenGL41,caps.glMultiDrawElementsBaseVertex)) return false;
            var values=pack.getShaderPackOptions().getOptionValues();
            // Pin actual mutable option class, then compare immutable snapshots of both raw override maps on every fill.
            if (values.getClass() != MutableOptionValues.class) return false;
            String name=Iris.getCurrentPackName();
            long generation=MakeUpQuadLifecycle.generation();
            boolean photonRoute=Boolean.getBoolean(com.pp.shaderlod.packs.PackSelectionService.PHOTON_QUAD_ORIGINAL_GRAPH);
            boolean researchRoutes=Boolean.getBoolean(RendererRolloutPolicy.RESEARCH_ROUTES);
            var mutable=(MutableOptionValues)values;
            boolean overridesUnchanged=mutable.getBooleanValues().equals(checkedBooleanOverrides) &&
                mutable.getStringValues().equals(checkedStringOverrides);
            if (pipeline != checkedPipeline || pack != checkedPack || values != checkedValues || client.level != checkedWorld ||
                !name.equals(checkedName) || generation != checkedGeneration || !overridesUnchanged || photonRoute != checkedPhotonRoute ||
                researchRoutes != checkedResearchRoutes) {
                checkedPipeline=pipeline; checkedPack=pack; checkedValues=values; checkedWorld=client.level;
                checkedName=name; checkedGeneration=generation; checkedPhotonRoute=photonRoute; checkedResearchRoutes=researchRoutes; checkedResult=false;
                checkedBooleanOverrides=Map.copyOf(mutable.getBooleanValues());
                checkedStringOverrides=Map.copyOf(mutable.getStringValues());
                if (!SharedConstants.getCurrentVersion().name().equals("1.21.11")) return false;
                var stack=loadedStack();
                if (!stackMatches(stack)) return false;
                var options=pack.getShaderPackOptions(); var declared=options.getOptionSet();
                var actual=com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(options);
                var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();
                var archive=dir.resolve(name).normalize();
                if (!archive.startsWith(dir) || archive.equals(dir)) return false;
                // Rollout permission is separate from exact source/options safety certification.
                checkedResult=RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.MAKEUP,researchRoutes) &&
                    com.pp.shaderlod.optimization.sourcecache.LoadedRendererSources.allows("MAKEUP") ||
                    RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.UNBOUND,researchRoutes) &&
                    com.pp.shaderlod.optimization.sourcecache.LoadedRendererSources.allows("UNBOUND") ||
                    RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.REIMAGINED,researchRoutes) &&
                    com.pp.shaderlod.optimization.sourcecache.LoadedRendererSources.allows("REIMAGINED") ||
                    RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.SILDUR,researchRoutes) &&
                    com.pp.shaderlod.optimization.sourcecache.LoadedRendererSources.allows("SILDUR") ||
                    RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.BLISS,researchRoutes) &&
                    com.pp.shaderlod.optimization.sourcecache.LoadedRendererSources.allows("BLISS") ||
                    RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.BSL,researchRoutes) &&
                    com.pp.shaderlod.optimization.sourcecache.LoadedRendererSources.allows("BSL") ||
                    RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.PHOTON,researchRoutes) &&
                    com.pp.shaderlod.optimization.sourcecache.LoadedRendererSources.allows("PHOTON") ||
                    com.pp.shaderlod.optimization.sourcecache.LoadedRendererSources.allows("GENERATED") ||
                    RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.ASTRA,researchRoutes) &&
                    com.pp.shaderlod.optimization.sourcecache.LoadedRendererSources.allows("ASTRA") ||
                    RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.SOLAS,researchRoutes) && com.pp.shaderlod.optimization.sourcecache.LoadedRendererSources.allows("SOLAS");
                checkedSolas=com.pp.shaderlod.integration.solasquad.SolasQuadEligibility.recognizes(actual);
                if(checkedSolas)checkedResult &= com.pp.shaderlod.integration.solasquad.SolasQuadInputProof.current(((IrisRenderingPipeline)pipeline).getSodiumPrograms(),pack);
                checkedAstra=com.pp.shaderlod.integration.astraquad.AstraQuadEligibility.recognizes(actual);
                if(checkedAstra)
                    checkedResult &= com.pp.shaderlod.integration.astraquad.AstraQuadInputProof.current(((IrisRenderingPipeline)pipeline).getSodiumPrograms(),pack);
            }
            return checkedResult && (!checkedSolas || com.pp.shaderlod.integration.solasquad.SolasQuadInputProof.current(((IrisRenderingPipeline)pipeline).getSodiumPrograms(),pack)) && (!checkedAstra || com.pp.shaderlod.integration.astraquad.AstraQuadInputProof.current(((IrisRenderingPipeline)pipeline).getSodiumPrograms(),pack)) && !MakeUpQuadLifecycle.suspended() && !Iris.isFallback();
        } catch (Exception | LinkageError unavailable) { return false; }
    }
}

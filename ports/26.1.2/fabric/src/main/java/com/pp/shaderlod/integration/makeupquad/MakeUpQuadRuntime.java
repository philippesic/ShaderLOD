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
        Map.entry("fabric-api","0.155.3+26.1.2"),
        Map.entry("fabric-api-base","2.0.3+ece063234c"),
        Map.entry("fabric-api-lookup-api-v1","2.0.12+d5a053b64c"),
        Map.entry("fabric-biome-api-v1","18.0.5+2fa62b4e4c"),
        Map.entry("fabric-block-api-v1","3.0.2+ec56b6014c"),
        Map.entry("fabric-block-getter-api-v2","2.0.6+ec56b6014c"),
        Map.entry("fabric-client-gametest-api-v1","5.1.0+0a5283664c"),
        Map.entry("fabric-command-api-v2","3.0.5+e2bdee784c"),
        Map.entry("fabric-content-registries-v0","11.3.1+af8a297c4c"),
        Map.entry("fabric-convention-tags-v2","4.6.2+4f11f7994c"),
        Map.entry("fabric-crash-report-info-v1","1.0.3+9f78a5a84c"),
        Map.entry("fabric-creative-tab-api-v1","5.0.12+a981f2874c"),
        Map.entry("fabric-data-attachment-api-v1","2.2.10+44a0bd1d4c"),
        Map.entry("fabric-data-generation-api-v1","24.3.4+cda368334c"),
        Map.entry("fabric-debug-api-v1","1.0.1+c792624d4c"),
        Map.entry("fabric-dimensions-v1","5.1.7+ad343a8d4c"),
        Map.entry("fabric-entity-events-v1","5.0.2+e2bdee784c"),
        Map.entry("fabric-events-interaction-v0","5.2.3+07b380be4c"),
        Map.entry("fabric-game-rule-api-v1","4.0.5+d871b99e4c"),
        Map.entry("fabric-gametest-api-v1","4.0.18+867b87624c"),
        Map.entry("fabric-item-api-v1","14.3.0+6d1aaa724c"),
        Map.entry("fabric-key-mapping-api-v1","2.0.4+e2bdee784c"),
        Map.entry("fabric-lifecycle-events-v1","4.1.1+df84eb3d4c"),
        Map.entry("fabric-loot-api-v3","3.0.12+00a1fba64c"),
        Map.entry("fabric-menu-api-v1","2.0.15+d871b99e4c"),
        Map.entry("fabric-message-api-v1","7.0.5+dae8ce3e4c"),
        Map.entry("fabric-model-loading-api-v1","8.0.11+c80601bb4c"),
        Map.entry("fabric-networking-api-v1","6.3.2+a217cccf4c"),
        Map.entry("fabric-object-builder-api-v1","23.1.1+abb459f14c"),
        Map.entry("fabric-particles-v1","5.0.16+b61fef434c"),
        Map.entry("fabric-permission-api-v1","1.0.3+5730ecca4c"),
        Map.entry("fabric-recipe-api-v1","9.0.17+be4b75ae4c"),
        Map.entry("fabric-registry-sync-v0","7.1.1+2fa62b4e4c"),
        Map.entry("fabric-renderer-api-v1","13.0.8+c80601bb4c"),
        Map.entry("fabric-renderer-indigo","8.1.5+1403e82c4c"),
        Map.entry("fabric-rendering-fluids-v1","6.0.1+d871b99e4c"),
        Map.entry("fabric-rendering-v1","23.3.1+e9207d814c"),
        Map.entry("fabric-resource-conditions-api-v1","6.1.0+83dd0ba34c"),
        Map.entry("fabric-resource-loader-v0","3.3.17+4fc5413f4c"),
        Map.entry("fabric-resource-loader-v1","2.0.10+7c44c7324c"),
        Map.entry("fabric-screen-api-v1","5.1.0+981dd9b24c"),
        Map.entry("fabric-serialization-api-v1","2.0.3+11a26f314c"),
        Map.entry("fabric-sound-api-v1","2.0.4+11a26f314c"),
        Map.entry("fabric-tag-api-v1","2.1.1+371cf5db4c"),
        Map.entry("fabric-transfer-api-v1","8.0.6+357ea7334c"),
        Map.entry("fabric-transitive-access-wideners-v1","8.1.3+3ff549fb4c"),
        Map.entry("fabricloader","0.19.5"),
        Map.entry("io_github_douira_glsl-transformer","3.0.0-pre3"),
        Map.entry("iris","1.11.4+mc26.1.2"),
        Map.entry("minecraft","26.1.2"),
        Map.entry("mixinextras","0.5.5"),
        Map.entry("org_anarres_jcpp","1.4.14"),
        Map.entry("org_antlr_antlr4-runtime","4.13.1"),
        Map.entry("sodium","0.9.2+mc26.1.2"));
    /** Pinned raw Fabric API0.155.3 release omits the two Maven-only gametest modules. */
    private static final Map<String,String> STANDARD_STACK = standardStack();
    private static Map<String,String> standardStack() {
        var standard = new TreeMap<>(REQUIRED_STACK);
        standard.remove("fabric-client-gametest-api-v1");
        standard.remove("fabric-gametest-api-v1");
        return Map.copyOf(standard);
    }
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
        if (dev != null && !dev.equals("0.1.0-dev-2612")) return false;
        return actual.equals(REQUIRED_STACK) || actual.equals(STANDARD_STACK);
    }
    private static final java.util.Set<String> DEVELOPMENT_RESOURCES = java.util.Set.of("vanilla", "fabric-api", "fabric-api-base", "fabric-api-lookup-api-v1", "fabric-biome-api-v1", "fabric-block-api-v1", "fabric-block-getter-api-v2", "fabric-client-gametest-api-v1", "fabric-command-api-v2", "fabric-content-registries-v0", "fabric-convention-tags-v2", "fabric-crash-report-info-v1", "fabric-creative-tab-api-v1", "fabric-data-attachment-api-v1", "fabric-data-generation-api-v1", "fabric-debug-api-v1", "fabric-dimensions-v1", "fabric-entity-events-v1", "fabric-events-interaction-v0", "fabric-game-rule-api-v1", "fabric-gametest-api-v1", "fabric-item-api-v1", "fabric-key-mapping-api-v1", "fabric-lifecycle-events-v1", "fabric-loot-api-v3", "fabric-menu-api-v1", "fabric-message-api-v1", "fabric-model-loading-api-v1", "fabric-networking-api-v1", "fabric-object-builder-api-v1", "fabric-particles-v1", "fabric-permission-api-v1", "fabric-recipe-api-v1", "fabric-registry-sync-v0", "fabric-renderer-api-v1", "fabric-renderer-indigo", "fabric-rendering-fluids-v1", "fabric-rendering-v1", "fabric-resource-conditions-api-v1", "fabric-resource-loader-v0", "fabric-resource-loader-v1", "fabric-screen-api-v1", "fabric-serialization-api-v1", "fabric-sound-api-v1", "fabric-tag-api-v1", "fabric-transfer-api-v1", "fabric-transitive-access-wideners-v1", "fabricloader", "iris", "shaderlod", "sodium");
    private static final java.util.Set<String> STANDARD_RESOURCES = standardResources();
    private static java.util.Set<String> standardResources() {
        var standard = new java.util.HashSet<>(DEVELOPMENT_RESOURCES);
        standard.remove("fabric-client-gametest-api-v1");
        standard.remove("fabric-gametest-api-v1");
        return java.util.Set.copyOf(standard);
    }
    public static boolean resourcesMatch(java.util.Collection<String> selected) {
        if (selected == null) return false;
        var actual = new java.util.HashSet<>(selected);
        return actual.size() == selected.size() &&
            (actual.equals(DEVELOPMENT_RESOURCES) || actual.equals(STANDARD_RESOURCES));
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
                if (!SharedConstants.getCurrentVersion().name().equals("26.1.2")) return false;
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

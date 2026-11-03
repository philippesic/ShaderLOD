package com.pp.shaderlod.integration.makeupquad;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.ShaderLodConfig;
import java.util.Map;
import java.util.TreeMap;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
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
        Map.entry("iris","1.10.8+mc1.21.11"),
        Map.entry("minecraft","1.21.11"),
        Map.entry("neoforge","21.11.45"),
        Map.entry("sodium","0.8.14+mc1.21.11"));
    /** Fresh loader-native snapshot using the same enumeration as this exact stack contract. */
    public static Map<String,String> loadedStack() {
        var stack=new TreeMap<String,String>();
        for (var mod:ModList.get().getMods()) stack.put(mod.getModId(),mod.getVersion().toString());
        return Map.copyOf(stack);
    }
    public static boolean stackMatches(Map<String,String> stack) {
        if (stack == null || !stack.containsKey("shaderlod")) return false;
        var actual = new TreeMap<>(stack); actual.remove("shaderlod");
        String dev=actual.remove("shaderlod_dev");
        if (dev != null && !dev.equals("0.1.0-dev-12111")) return false;
        String mixins=actual.remove("mixinextras.neoforge");
        if (mixins != null && !mixins.equals("0.5.3")) return false;
        // FML synthetic container contributes no game resources; real mods stay exact.
        actual.remove("net.neoforged.fml.generated");
        return actual.equals(REQUIRED_STACK);
    }
    public static boolean resourcesMatch(java.util.Collection<String> selected) {
        if (selected == null) return false;
        var actual=new java.util.ArrayList<>(selected); actual.remove("mod/shaderlod_dev");
        return actual.size() == 6 && new java.util.HashSet<>(actual).equals(java.util.Set.of("vanilla", "mod_resources", "mod/sodium", "mod/iris", "mod/shaderlod", "mod/neoforge"));

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
            return configAllows(com.pp.shaderlod.ShaderLodConfig.SPEC.isLoaded(),
                com.pp.shaderlod.ShaderLodConfig.ENABLED.get(),com.pp.shaderlod.ShaderLodConfig.PRESET.get());
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
            return configAllows(com.pp.shaderlod.ShaderLodConfig.SPEC.isLoaded(),com.pp.shaderlod.ShaderLodConfig.ENABLED.get(),com.pp.shaderlod.ShaderLodConfig.PRESET.get()) && nativeGraphAvailable();
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

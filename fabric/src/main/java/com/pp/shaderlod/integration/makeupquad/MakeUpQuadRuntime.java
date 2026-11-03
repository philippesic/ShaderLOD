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
/** Fabric 1.21.1 adapter: exact Fabric stack and APIs, unchanged shared source/options certificates. */
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
    public static final Map<String,String> REQUIRED_STACK=Map.ofEntries(
        Map.entry("fabric-api","0.116.17+1.21.1"),
        Map.entry("fabric-api-base","0.4.42+6573ed8c19"),
        Map.entry("fabric-api-lookup-api-v1","1.6.72+7b3d111d19"),
        Map.entry("fabric-biome-api-v1","13.0.31+d527f9fd19"),
        Map.entry("fabric-block-api-v1","1.1.0+0bc3503219"),
        Map.entry("fabric-block-view-api-v2","1.0.11+ebb2264e19"),
        Map.entry("fabric-blockrenderlayer-v1","1.1.52+0af3f5a719"),
        Map.entry("fabric-client-tags-api-v1","1.2.0+1eb36c0719"),
        Map.entry("fabric-command-api-v1","1.2.49+f71b366f19"),
        Map.entry("fabric-command-api-v2","2.2.28+6ced4dd919"),
        Map.entry("fabric-commands-v0","0.2.66+df3654b319"),
        Map.entry("fabric-content-registries-v0","8.0.19+b559734419"),
        Map.entry("fabric-convention-tags-v1","2.1.7+7f945d5b19"),
        Map.entry("fabric-convention-tags-v2","2.12.0+c3656daa19"),
        Map.entry("fabric-crash-report-info-v1","0.2.29+0af3f5a719"),
        Map.entry("fabric-data-attachment-api-v1","1.4.7+5b36e0f719"),
        Map.entry("fabric-data-generation-api-v1","20.3.0+1eb36c0719"),
        Map.entry("fabric-dimensions-v1","4.0.1+65213ef819"),
        Map.entry("fabric-entity-events-v1","1.8.0+2b27e0a419"),
        Map.entry("fabric-events-interaction-v0","0.7.14+ba9dae0619"),
        Map.entry("fabric-game-rule-api-v1","1.0.53+6ced4dd919"),
        Map.entry("fabric-gametest-api-v1","2.0.5+6fc22b9919"),
        Map.entry("fabric-item-api-v1","11.3.0+467044f319"),
        Map.entry("fabric-item-group-api-v1","4.1.7+def88e3a19"),
        Map.entry("fabric-key-binding-api-v1","1.0.47+0af3f5a719"),
        Map.entry("fabric-keybindings-v0","0.2.45+df3654b319"),
        Map.entry("fabric-lifecycle-events-v1","2.6.0+0865547519"),
        Map.entry("fabric-loot-api-v2","3.0.15+3f89f5a519"),
        Map.entry("fabric-loot-api-v3","1.0.3+3f89f5a519"),
        Map.entry("fabric-message-api-v1","6.0.14+8aaf3aca19"),
        Map.entry("fabric-model-loading-api-v1","2.1.0+b4d813fc19"),
        Map.entry("fabric-networking-api-v1","4.3.1+d30f6a7919"),
        Map.entry("fabric-object-builder-api-v1","15.2.1+40875a9319"),
        Map.entry("fabric-particles-v1","4.0.2+6573ed8c19"),
        Map.entry("fabric-recipe-api-v1","5.0.16+2475392c19"),
        Map.entry("fabric-registry-sync-v0","5.3.2+e3eddc2119"),
        Map.entry("fabric-renderer-api-v1","3.4.1+b4d813fc19"),
        Map.entry("fabric-renderer-indigo","1.7.1+c705a49c19"),
        Map.entry("fabric-renderer-registries-v1","3.2.71+df3654b319"),
        Map.entry("fabric-rendering-data-attachment-v1","0.3.49+73761d2e19"),
        Map.entry("fabric-rendering-fluids-v1","3.1.6+1daea21519"),
        Map.entry("fabric-rendering-v0","1.1.74+df3654b319"),
        Map.entry("fabric-rendering-v1","5.2.1+f44c2eea19"),
        Map.entry("fabric-resource-conditions-api-v1","4.3.0+8dc279b119"),
        Map.entry("fabric-resource-loader-v0","1.3.1+5b5275af19"),
        Map.entry("fabric-screen-api-v1","2.0.26+639b4c0719"),
        Map.entry("fabric-screen-handler-api-v1","1.3.91+b559734419"),
        Map.entry("fabric-sound-api-v1","1.0.23+6573ed8c19"),
        Map.entry("fabric-tag-api-v1","1.3.0+1eb36c0719"),
        Map.entry("fabric-transfer-api-v1","5.4.4+7b3d111d19"),
        Map.entry("fabric-transitive-access-wideners-v1","6.2.0+45b9699719"),
        Map.entry("fabricloader","0.19.5"),
        Map.entry("iris","1.8.8+mc1.21.1"),
        Map.entry("java","21"),
        Map.entry("minecraft","1.21.1"),
        Map.entry("mixinextras","0.5.5"),
        Map.entry("sodium","0.6.13+mc1.21.1"));
    /** Fresh loader-native snapshot using the same enumeration as this exact stack contract. */
    public static Map<String,String> loadedStack() {
        var stack=new TreeMap<String,String>();
        for (var mod:FabricLoader.getInstance().getAllMods())
        stack.put(mod.getMetadata().getId(),mod.getMetadata().getVersion().getFriendlyString());
        return Map.copyOf(stack);
    }
    /** Loader-specific live policy remains unchanged; shared callers need this entry point. */
    public static boolean loadedStackMatches(){return stackMatches(loadedStack());}
    public static boolean stackMatches(Map<String,String> stack) {
        if (stack == null || !stack.containsKey("shaderlod")) return false;
        var nativeOnly = new TreeMap<>(stack);
        nativeOnly.remove("shaderlod"); // product version does not alter pinned renderer/source APIs
        // Optional development observer is tolerated, never required for ordinary packaged operation.
        String dev=nativeOnly.remove("shaderlod_dev");
        return (dev == null || dev.equals("0.1.0-dev")) && nativeOnly.equals(REQUIRED_STACK);
    }
    public static final String FABRIC_CANDIDATE = "shaderlod.experimentalFabricRenderer1211";
    public static final java.util.Set<String> REQUIRED_RESOURCES = java.util.Set.of(
        "vanilla", "fabric", "shaderlod", "fabric-api", "fabric-api-base", "fabric-api-lookup-api-v1", "fabric-biome-api-v1", "fabric-block-api-v1", "fabric-block-view-api-v2", "fabric-blockrenderlayer-v1", "fabric-client-tags-api-v1", "fabric-command-api-v1", "fabric-command-api-v2", "fabric-commands-v0", "fabric-content-registries-v0", "fabric-convention-tags-v1", "fabric-convention-tags-v2", "fabric-crash-report-info-v1", "fabric-data-attachment-api-v1", "fabric-data-generation-api-v1", "fabric-dimensions-v1", "fabric-entity-events-v1", "fabric-events-interaction-v0", "fabric-game-rule-api-v1", "fabric-gametest-api-v1", "fabric-item-api-v1", "fabric-item-group-api-v1", "fabric-key-binding-api-v1", "fabric-keybindings-v0", "fabric-lifecycle-events-v1", "fabric-loot-api-v2", "fabric-loot-api-v3", "fabric-message-api-v1", "fabric-model-loading-api-v1", "fabric-networking-api-v1", "fabric-object-builder-api-v1", "fabric-particles-v1", "fabric-recipe-api-v1", "fabric-registry-sync-v0", "fabric-renderer-api-v1", "fabric-renderer-indigo", "fabric-renderer-registries-v1", "fabric-rendering-data-attachment-v1", "fabric-rendering-fluids-v1", "fabric-rendering-v0", "fabric-rendering-v1", "fabric-resource-conditions-api-v1", "fabric-resource-loader-v0", "fabric-screen-api-v1", "fabric-screen-handler-api-v1", "fabric-sound-api-v1", "fabric-tag-api-v1", "fabric-transfer-api-v1", "fabric-transitive-access-wideners-v1", "fabricloader", "iris", "sodium");
    public static boolean resourcesMatch(java.util.Collection<String> selected) {
        return selected != null && selected.size() == REQUIRED_RESOURCES.size() &&
            REQUIRED_RESOURCES.equals(new java.util.HashSet<>(selected));
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
                !resourcesMatch(client.getResourcePackRepository().getSelectedIds()) ||
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
                if (!SharedConstants.getCurrentVersion().getName().equals("1.21.1")) return false;
                var stack=loadedStack();
                if (!stackMatches(stack)) return false;
                var options=pack.getShaderPackOptions(); var declared=options.getOptionSet();
                var actual=new TreeMap<String,String>();
                for (String key:declared.getBooleanOptions().keySet()) actual.put(key,Boolean.toString(values.getBooleanValueOrDefault(key)));
                for (String key:declared.getStringOptions().keySet()) actual.put(key,values.getStringValueOrDefault(key));
                var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();
                var archive=dir.resolve(name).normalize();
                if (!archive.startsWith(dir) || archive.equals(dir)) return false;
                // Rollout permission is separate from exact source/options safety certification.
                checkedResult=RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.MAKEUP,researchRoutes) &&
                    MakeUpQuadEligibility.validate(archive,actual).eligible() ||
                    RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.UNBOUND,researchRoutes) &&
                    com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.validate(archive,actual).eligible() ||
                    RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.REIMAGINED,researchRoutes) &&
                    com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility.validate(archive,actual).eligible() ||
                    RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.SILDUR,researchRoutes) &&
                    com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility.validate(archive,actual).eligible() ||
                    RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.BLISS,researchRoutes) &&
                    com.pp.shaderlod.integration.blissquad.BlissQuadEligibility.validate(archive,actual).eligible() ||
                    RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.BSL,researchRoutes) &&
                    com.pp.shaderlod.integration.bslquad.BslQuadEligibility.validate(archive,actual).eligible() ||
                    RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.PHOTON,researchRoutes) &&
                    com.pp.shaderlod.integration.photonquad.PhotonQuadEligibility.validate(archive,actual).eligible() ||
                    GeneratedRendererEligibility.validate(archive,actual) ||
                    RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.ASTRA,researchRoutes) &&
                    com.pp.shaderlod.integration.astraquad.AstraQuadEligibility.validate(archive,actual).eligible() ||
                    RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.SOLAS,researchRoutes) && com.pp.shaderlod.integration.solasquad.SolasQuadEligibility.validate(archive,actual).eligible();
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

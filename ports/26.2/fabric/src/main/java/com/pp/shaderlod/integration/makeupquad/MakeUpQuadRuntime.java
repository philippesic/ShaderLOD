package com.pp.shaderlod.integration.makeupquad;
import com.pp.shaderlod.LodPreset;
import java.util.Map;
import java.util.TreeMap;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
/** 26.2 fabric manual preview; native fallback unless every pinned safety certificate passes. */
public final class MakeUpQuadRuntime {
    public static final String RESEARCH_OPT_IN="shaderlod.experimentalRendererPort092";
    public static final Map<String,String> REQUIRED_STACK=Map.ofEntries(
        Map.entry("fabric-advancement-api-v1","1.0.0+58719a3b9e"),
        Map.entry("fabric-api","0.161.0+26.2"),
        Map.entry("fabric-api-base","2.0.4+ece063239e"),
        Map.entry("fabric-api-lookup-api-v1","2.0.18+11e1c9a39e"),
        Map.entry("fabric-biome-api-v1","18.0.6+c7bd5b8e9e"),
        Map.entry("fabric-block-api-v1","3.1.0+53515aab9e"),
        Map.entry("fabric-block-getter-api-v2","2.0.7+ec56b6019e"),
        Map.entry("fabric-client-gametest-api-v1","6.0.2+0f4f1dca9e"),
        Map.entry("fabric-command-api-v2","3.1.0+00cb03469e"),
        Map.entry("fabric-content-registries-v0","11.3.2+37b1aa249e"),
        Map.entry("fabric-convention-tags-v2","4.7.1+1f023a9e9e"),
        Map.entry("fabric-crash-report-info-v1","1.0.5+086d547a9e"),
        Map.entry("fabric-creative-tab-api-v1","5.0.15+6468574f9e"),
        Map.entry("fabric-data-attachment-api-v1","2.2.19+515ac5339e"),
        Map.entry("fabric-data-generation-api-v1","25.6.0+c0da0b829e"),
        Map.entry("fabric-debug-api-v1","1.0.2+c792624d9e"),
        Map.entry("fabric-dimensions-v1","5.1.12+515ac5339e"),
        Map.entry("fabric-entity-events-v1","5.0.5+06488ac19e"),
        Map.entry("fabric-events-interaction-v0","5.2.8+515ac5339e"),
        Map.entry("fabric-game-rule-api-v1","4.0.8+46a6d00c9e"),
        Map.entry("fabric-gametest-api-v1","4.0.22+4a7fa0819e"),
        Map.entry("fabric-item-api-v1","14.5.0+c68f6cbe9e"),
        Map.entry("fabric-key-mapping-api-v1","2.0.5+e2bdee789e"),
        Map.entry("fabric-lifecycle-events-v1","4.1.4+29b6eb019e"),
        Map.entry("fabric-loot-api-v3","3.0.17+06488ac19e"),
        Map.entry("fabric-menu-api-v1","2.0.17+086d547a9e"),
        Map.entry("fabric-message-api-v1","7.0.8+bb2ed3f79e"),
        Map.entry("fabric-model-loading-api-v1","8.0.17+c80601bb9e"),
        Map.entry("fabric-networking-api-v1","6.3.4+2989c6a09e"),
        Map.entry("fabric-object-builder-api-v1","24.1.1+6fcd5f039e"),
        Map.entry("fabric-particles-v1","5.0.19+d1756aa99e"),
        Map.entry("fabric-permission-api-v1","1.0.5+e442d8d09e"),
        Map.entry("fabric-recipe-api-v1","9.0.22+11e1c9a39e"),
        Map.entry("fabric-registry-sync-v0","7.1.1+c7bd5b8e9e"),
        Map.entry("fabric-renderer-api-v1","14.1.4+2b0d8a229e"),
        Map.entry("fabric-renderer-indigo","9.1.4+2b0d8a229e"),
        Map.entry("fabric-rendering-fluids-v1","6.0.4+06488ac19e"),
        Map.entry("fabric-rendering-v1","25.3.3+515ac5339e"),
        Map.entry("fabric-resource-conditions-api-v1","6.1.0+10e9a28b9e"),
        Map.entry("fabric-resource-loader-v0","3.3.20+4fc5413f9e"),
        Map.entry("fabric-resource-loader-v1","2.0.13+9edec1269e"),
        Map.entry("fabric-screen-api-v1","5.2.1+5087f8249e"),
        Map.entry("fabric-serialization-api-v1","2.0.4+11a26f319e"),
        Map.entry("fabric-sound-api-v1","2.0.5+11a26f319e"),
        Map.entry("fabric-tag-api-v1","2.1.4+515ac5339e"),
        Map.entry("fabric-transfer-api-v1","8.0.13+11dbb7d79e"),
        Map.entry("fabric-transitive-access-wideners-v1","8.1.4+67c847259e"),
        Map.entry("fabricloader","0.19.5"),
        Map.entry("io_github_douira_glsl-transformer","3.0.0-pre3"),
        Map.entry("iris","1.11.4+mc26.2"),
        Map.entry("minecraft","26.2"),
        Map.entry("mixinextras","0.5.5"),
        Map.entry("org_anarres_jcpp","1.4.14"),
        Map.entry("org_antlr_antlr4-runtime","4.13.1"),
        Map.entry("sodium","0.9.2+mc26.2"));
    private static Object checkedPack,checkedPipeline,checkedValues,checkedWorld;
    private static String checkedName;
    private static Map<String,Boolean> checkedBooleans;
    private static Map<String,String> checkedStrings;
    private static long checkedGeneration=-1;
    private static boolean checkedResult;
    private static Boolean checkedStack;
    private static boolean checkedAstra;
    private static boolean checkedSolas;
    private MakeUpQuadRuntime() {}
    /** Exact raw0.161.0 aggregate lacks the complete Maven-only gametest pair. */
    private static final Map<String,String> STANDARD_STACK=standardStack();
    private static Map<String,String> standardStack(){
        var standard=new TreeMap<>(REQUIRED_STACK);
        standard.remove("fabric-client-gametest-api-v1");standard.remove("fabric-gametest-api-v1");
        return Map.copyOf(standard);
    }
    // Fabric Loader's actual Java entry uses builtin metadata, never an ordinary mod.
    private static boolean builtinJava(net.fabricmc.loader.api.metadata.ModMetadata metadata){
        return metadata!=null&&"java".equals(metadata.getId())&&"builtin".equals(metadata.getType());
    }
    /** Fresh loader-native snapshot using the same enumeration as this exact stack contract. */
    public static Map<String,String> loadedStack() {
        var stack=new TreeMap<String,String>(); for (var mod:net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods()) { var metadata=mod.getMetadata();if(!builtinJava(metadata))stack.put(metadata.getId(),metadata.getVersion().getFriendlyString()); }
        return Map.copyOf(stack);
    }
    public static boolean stackMatches(Map<String,String> stack) {
        if (stack==null || !stack.containsKey("shaderlod")) return false;
        var actual=new TreeMap<>(stack); actual.remove("shaderlod");
        var dev=actual.remove("shaderlod_dev");
        return (dev==null || dev.equals("0.1.0-dev-262")) && (actual.equals(REQUIRED_STACK)||actual.equals(STANDARD_STACK));
    }
    public static boolean configAllows(boolean loaded,boolean enabled,LodPreset preset) { return loaded && enabled && preset==LodPreset.PERFORMANCE; }
    public static boolean capabilitiesAllow(int pointerSize,boolean gl41,long multiDraw) { return pointerSize==8 && gl41 && multiDraw!=0; }
    // Actual262Fabric selected-repository observation; see renderer-resource-observation-r1.json provenance.
    private static final java.util.Set<String> NATIVE_RESOURCE_IDS=java.util.Set.of(
        "vanilla",
        "fabric-advancement-api-v1",
        "fabric-api",
        "fabric-api-base",
        "fabric-api-lookup-api-v1",
        "fabric-biome-api-v1",
        "fabric-block-api-v1",
        "fabric-block-getter-api-v2",
        "fabric-client-gametest-api-v1",
        "fabric-command-api-v2",
        "fabric-content-registries-v0",
        "fabric-convention-tags-v2",
        "fabric-crash-report-info-v1",
        "fabric-creative-tab-api-v1",
        "fabric-data-attachment-api-v1",
        "fabric-data-generation-api-v1",
        "fabric-debug-api-v1",
        "fabric-dimensions-v1",
        "fabric-entity-events-v1",
        "fabric-events-interaction-v0",
        "fabric-game-rule-api-v1",
        "fabric-gametest-api-v1",
        "fabric-item-api-v1",
        "fabric-key-mapping-api-v1",
        "fabric-lifecycle-events-v1",
        "fabric-loot-api-v3",
        "fabric-menu-api-v1",
        "fabric-message-api-v1",
        "fabric-model-loading-api-v1",
        "fabric-networking-api-v1",
        "fabric-object-builder-api-v1",
        "fabric-particles-v1",
        "fabric-permission-api-v1",
        "fabric-recipe-api-v1",
        "fabric-registry-sync-v0",
        "fabric-renderer-api-v1",
        "fabric-renderer-indigo",
        "fabric-rendering-fluids-v1",
        "fabric-rendering-v1",
        "fabric-resource-conditions-api-v1",
        "fabric-resource-loader-v0",
        "fabric-resource-loader-v1",
        "fabric-screen-api-v1",
        "fabric-serialization-api-v1",
        "fabric-sound-api-v1",
        "fabric-tag-api-v1",
        "fabric-transfer-api-v1",
        "fabric-transitive-access-wideners-v1",
        "fabricloader",
        "iris",
        "shaderlod",
        "sodium");
    private static final java.util.Set<String> STANDARD_RESOURCE_IDS=standardResources();
    private static java.util.Set<String> standardResources(){
        var standard=new java.util.HashSet<>(NATIVE_RESOURCE_IDS);
        standard.remove("fabric-client-gametest-api-v1");standard.remove("fabric-gametest-api-v1");
        return java.util.Set.copyOf(standard);
    }
    public static boolean resourcesMatch(java.util.Collection<String> selected) {
        if(selected==null)return false;
        var actual=new java.util.HashSet<>(selected);
        return actual.size()==selected.size()&&
            (actual.equals(NATIVE_RESOURCE_IDS)||actual.equals(STANDARD_RESOURCE_IDS));
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
            if (client.level==null || client.level.dimension()!=Level.OVERWORLD || Iris.isFallback() || !MakeUpQuadApiPins.exactApi() ||
                !resourcesMatch(client.getResourcePackRepository().getSelectedIds())) return false;
            com.pp.shaderlod.integration.RendererBackend.requireOpenGl();
            var caps=org.lwjgl.opengl.GL.getCapabilities();
            if (!capabilitiesAllow(org.lwjgl.system.Pointer.POINTER_SIZE,caps.OpenGL41,caps.glMultiDrawElementsBaseVertex)) return false;
            var pipeline=Iris.getPipelineManager().getPipelineNullable(); var pack=Iris.getCurrentPack().orElse(null);
            if (pipeline==null || pipeline.getClass()!=IrisRenderingPipeline.class || pack==null) return false;
            var values=pack.getShaderPackOptions().getOptionValues();
            if (values.getClass()!=MutableOptionValues.class) return false;
            if (checkedStack==null) {
                var stack=loadedStack();
                checkedStack=stackMatches(stack);
            }
            if (!checkedStack) return false;
            var mutable=(MutableOptionValues)values; var name=Iris.getCurrentPackName(); var generation=MakeUpQuadLifecycle.generation();
            if (pack!=checkedPack || pipeline!=checkedPipeline || values!=checkedValues || client.level!=checkedWorld ||
                !name.equals(checkedName) || generation!=checkedGeneration || !mutable.getBooleanValues().equals(checkedBooleans) || !mutable.getStringValues().equals(checkedStrings)) {
                checkedPack=pack;checkedPipeline=pipeline;checkedValues=values;checkedWorld=client.level;
                checkedName=name;checkedGeneration=generation;checkedResult=false;
                checkedBooleans=Map.copyOf(mutable.getBooleanValues());checkedStrings=Map.copyOf(mutable.getStringValues());
                var options=pack.getShaderPackOptions();var declared=options.getOptionSet();var actual=com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(options);
                var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var source=dir.resolve(name).normalize();
                if (source.equals(dir) || !source.startsWith(dir)) return false;
                // Ordinary preview permission never substitutes for exact native source/options certificates.
                boolean researchRoutes=Boolean.getBoolean(RendererRolloutPolicy.RESEARCH_ROUTES);
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
                if(checkedSolas)checkedResult &= com.pp.shaderlod.integration.solasquad.SolasQuadInputProof.current(pipeline,pack);
                checkedAstra=com.pp.shaderlod.integration.astraquad.AstraQuadEligibility.recognizes(actual);
                if(checkedAstra)
                    checkedResult &= com.pp.shaderlod.integration.astraquad.AstraQuadInputProof.current(pipeline,pack);
            }
            return checkedResult && (!checkedSolas || com.pp.shaderlod.integration.solasquad.SolasQuadInputProof.current(pipeline,pack)) && (!checkedAstra || com.pp.shaderlod.integration.astraquad.AstraQuadInputProof.current(pipeline,pack)) && !MakeUpQuadLifecycle.suspended() && !Iris.isFallback();
        } catch (Exception | LinkageError unavailable) { return false; }
    }
}

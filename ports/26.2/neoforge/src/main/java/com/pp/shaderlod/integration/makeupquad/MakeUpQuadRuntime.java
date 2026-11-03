package com.pp.shaderlod.integration.makeupquad;
import com.pp.shaderlod.LodPreset;
import java.util.Map;
import java.util.TreeMap;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
/** 26.2 neoforge manual preview; native fallback unless every pinned safety certificate passes. */
public final class MakeUpQuadRuntime {
    public static final String RESEARCH_OPT_IN="shaderlod.experimentalRendererPort092";
    public static final Map<String,String> REQUIRED_STACK=Map.ofEntries(
        Map.entry("iris","1.11.4+mc26.2"),
        Map.entry("minecraft","26.2"),
        Map.entry("neoforge","26.2.0.88"),
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
    /** Fresh loader-native snapshot using the same enumeration as this exact stack contract. */
    public static Map<String,String> loadedStack() {
        var stack=new TreeMap<String,String>(); for (var mod:net.neoforged.fml.ModList.get().getMods()) stack.put(mod.getModId(),mod.getVersion().toString());
        return Map.copyOf(stack);
    }
    public static boolean stackMatches(Map<String,String> stack) {
        if (stack==null || !stack.containsKey("shaderlod")) return false;
        var actual=new TreeMap<>(stack); actual.remove("shaderlod");
        var dev=actual.remove("shaderlod_dev");
        return (dev==null || dev.equals("0.1.0-dev-262")) && actual.equals(REQUIRED_STACK);
    }
    public static boolean configAllows(boolean loaded,boolean enabled,LodPreset preset) { return loaded && enabled && preset==LodPreset.PERFORMANCE; }
    public static boolean capabilitiesAllow(int pointerSize,boolean gl41,long multiDraw) { return pointerSize==8 && gl41 && multiDraw!=0; }
    public static boolean resourcesMatch(java.util.Collection<String> selected) {
        return selected!=null && selected.contains("vanilla") && selected.stream().allMatch(id ->
            id.equals("vanilla") || id.equals("mod_resources") || id.equals("fabric") || REQUIRED_STACK.containsKey(id));
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

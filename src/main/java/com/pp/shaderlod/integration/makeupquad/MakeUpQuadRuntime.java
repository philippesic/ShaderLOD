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
    private static boolean checkedResult, checkedDhExperiment;
    private static boolean checkedAstra;
    private static boolean checkedSolas;
    private static long admissionCacheHits, admissionValidations;
    static {
        com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation(
            com.pp.shaderlod.optimization.OptimizationId.SHADOW_ADMISSION_CACHE,
            MakeUpQuadRuntime::admissionCacheStatus);
    }
    /** Metadata replay only. All current world/API/GL/resource/option-map guards still run. */
    public static boolean admissionCacheAllows(boolean loaded, boolean globallyEnabled,
            LodPreset preset, boolean requested) {
        return requested && configAllows(loaded, globallyEnabled, preset);
    }
    private static boolean admissionCacheEnabled() {
        return admissionCacheAllows(ShaderLodConfig.SPEC.isLoaded(), ShaderLodConfig.ENABLED.get(),
            ShaderLodConfig.PRESET.get(), com.pp.shaderlod.optimization.OptimizationConfig.requested(
                com.pp.shaderlod.optimization.OptimizationId.SHADOW_ADMISSION_CACHE));
    }
    public static String admissionCacheStatus() {
        return "Native graph source/options metadata cache; hits=" + admissionCacheHits
            + "; full validations=" + admissionValidations
            + "; current world/API/GL/resources/mutable overrides/lifecycle checked on every use";
    }
    /** Read-only boundary snapshot; counters are cumulative, compare interval deltas. */
    public static Map<String,Long> admissionCacheCounters() {
        return Map.of("hits",admissionCacheHits,"validations",admissionValidations);
    }
    private MakeUpQuadRuntime() {}
    /** Bounded qualification switch; other modules and renderer/source contracts remain pinned. */
    public static final String DH_EXPERIMENT = "shaderlod.experimentalMakeUpCpu2Dh333";
    public static final String DH_VERSION = "3.3.3";
    public static final Map<String,String> REQUIRED_STACK=Map.ofEntries(
        Map.entry("fabric_api_base","0.4.42+d1308ded19"),
        Map.entry("fabric_block_view_api_v2","1.0.10+9afaaf8c19"),
        Map.entry("fabric_renderer_api_v1","3.4.0+acb05a3919"),
        Map.entry("fabric_rendering_data_attachment_v1","0.3.48+73761d2e19"),
        Map.entry("iris","1.8.12-snapshot+mc1.21.1-local"),
        Map.entry("minecraft","1.21.1"), Map.entry("neoforge","21.1.253"),
        Map.entry("sodium","0.6.13+mc1.21.1"));
    /** Fresh loader-native snapshot using the same enumeration as this exact stack contract. */
    public static Map<String,String> loadedStack() {
        var stack=new TreeMap<String,String>();
        for (var mod:ModList.get().getMods()) stack.put(mod.getModId(),mod.getVersion().toString());
        return Map.copyOf(stack);
    }
    public static boolean stackMatches(Map<String,String> stack) {
        return stackMatches(stack,Boolean.getBoolean(DH_EXPERIMENT));
    }
    public static boolean stackMatches(Map<String,String> stack,boolean allowDh) {
        if (stack == null || !stack.containsKey("shaderlod")) return false;
        var nativeOnly = new TreeMap<>(stack);
        nativeOnly.remove("shaderlod"); // product version does not alter pinned renderer/source APIs
        // Optional development observer is tolerated, never required for ordinary packaged operation.
        String dev=nativeOnly.remove("shaderlod_dev");
        String dh=nativeOnly.remove("distanthorizons");
        if(dh!=null&&(!allowDh||!DH_VERSION.equals(dh)))return false;
        return (dev == null || dev.equals("0.1.0")) && nativeOnly.equals(REQUIRED_STACK);
    }
    private static final Map<String,Integer> STACK_BITS;
    static {var bits=new java.util.HashMap<String,Integer>();int bit=1;for(var id:REQUIRED_STACK.keySet()){bits.put(id,bit);bit<<=1;}STACK_BITS=Map.copyOf(bits);}
    /** Same live loader enumeration/version checks, without rebuilding two sorted maps per draw. */
    public static boolean loadedStackMatches(){return stackMatchesModules(ModList.get().getMods(),mod->mod.getModId(),mod->mod.getVersion().toString());}
    public static <T> boolean stackMatchesModules(Iterable<T> modules,java.util.function.Function<T,String> idOf,java.util.function.Function<T,String> versionOf){
        return stackMatchesModules(modules,idOf,versionOf,Boolean.getBoolean(DH_EXPERIMENT));
    }
    public static <T> boolean stackMatchesModules(Iterable<T> modules,java.util.function.Function<T,String> idOf,java.util.function.Function<T,String> versionOf,boolean allowDh){
        int seen=0;boolean product=false,observer=false,dh=false;
        for(var module:modules){var id=idOf.apply(module);
            if("shaderlod".equals(id)){if(product)return false;product=true;continue;}
            if("shaderlod_dev".equals(id)){if(observer||!"0.1.0".equals(versionOf.apply(module)))return false;observer=true;continue;}
            if("distanthorizons".equals(id)){if(dh||!allowDh||!DH_VERSION.equals(versionOf.apply(module)))return false;dh=true;continue;}
            var bit=STACK_BITS.get(id);if(bit==null||(seen&bit)!=0||!REQUIRED_STACK.get(id).equals(versionOf.apply(module)))return false;seen|=bit;
        }
        return product&&seen==(1<<REQUIRED_STACK.size())-1;
    }
    /** Optional DH qualification is scoped to the native MakeUp map and CPU-only requests. */
    public static boolean dhContextAllows(boolean dhPresent,boolean nativeMakeUp,boolean cpuOnly){return !dhPresent||nativeMakeUp&&cpuOnly;}
    public static boolean resourcesMatch(java.util.Collection<String> selected) {
        return selected != null && selected.size() == 2 && selected.contains("vanilla") && selected.contains("mod_resources");
    }
    /** Live visible-ID set check, preserving duplicate IDs without materializing a set. */
    public static <T> boolean resourcesMatchPacks(Iterable<T> selected,
            java.util.function.Predicate<T> hidden, java.util.function.Function<T,String> idOf) {
        if (selected == null) return false;
        int seen = 0;
        for (var pack : selected) {
            if (hidden.test(pack)) continue;
            var id = idOf.apply(pack);
            if ("vanilla".equals(id)) seen |= 1;
            else if ("mod_resources".equals(id)) seen |= 2;
            else return false;
        }
        return seen == 3;
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
    /** The uncached Root validator is too costly for command compaction alone. */
    public static boolean compactionRequestsAllow(boolean quadRequested,boolean cacheRequested) {
        return quadRequested && cacheRequested;
    }
    /** Cheap recording selection; full current graph admission remains at the consumer. */
    public static boolean compactionRecordingRequested() {
        try {
            var flags=com.pp.shaderlod.optimization.OptimizationConfig.snapshot();
            return compactionRequestsAllow(flags.enabled(com.pp.shaderlod.optimization.OptimizationId.QUAD_COMMAND_COMPACTION),
                flags.enabled(com.pp.shaderlod.optimization.OptimizationId.SHADOW_ADMISSION_CACHE)) && admissionCacheEnabled();
        } catch (Exception | LinkageError unavailable) { return false; }
    }
    public static String compactionStatus() {
        var flags=com.pp.shaderlod.optimization.OptimizationConfig.snapshot();
        if(!flags.enabled(com.pp.shaderlod.optimization.OptimizationId.QUAD_COMMAND_COMPACTION))return "Native command compaction not requested";
        if(!flags.enabled(com.pp.shaderlod.optimization.OptimizationId.SHADOW_ADMISSION_CACHE))return "Requested; refused: enable Admission Cache for native command compaction";
        return "Native in-place fill compaction; eligible="+enabled(com.pp.shaderlod.optimization.OptimizationId.QUAD_COMMAND_COMPACTION)+"; use counters not reported";
    }
    /** Individual request plus every existing native source/API/resource/config gate. */
    /** Diagnostic wrapper only at the two existing terrain consumer call sites. */
    public static boolean enabledAtTerrainConsumer(com.pp.shaderlod.optimization.OptimizationId id) {
        if(!com.pp.shaderlod.optimization.NativeAdmissionCost.ENABLED)return enabled(id);
        int kind=id==com.pp.shaderlod.optimization.OptimizationId.QUAD_COMMAND_COMPACTION?0:
            id==com.pp.shaderlod.optimization.OptimizationId.INDEX_SCALAR_CACHE?1:-1;
        if(kind<0)return enabled(id);
        long wall=com.pp.shaderlod.optimization.NativeAdmissionCost.wallStart();
        long cpu=com.pp.shaderlod.optimization.NativeAdmissionCost.cpuStart(wall);
        boolean allowed=false;
        try{return allowed=enabled(id);}
        finally{com.pp.shaderlod.optimization.NativeAdmissionCost.finish(kind,wall,cpu,allowed);}
    }
    public static boolean enabled(com.pp.shaderlod.optimization.OptimizationId id) {
        return com.pp.shaderlod.optimization.OptimizationConfig.requested(id)
            && (id!=com.pp.shaderlod.optimization.OptimizationId.QUAD_COMMAND_COMPACTION || compactionRecordingRequested()) && enabled();
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
                !resourcesMatchPacks(client.getResourcePackRepository().getSelectedPacks(),
                    net.minecraft.server.packs.repository.Pack::isHidden, net.minecraft.server.packs.repository.Pack::getId) ||
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
            boolean dhExperiment=Boolean.getBoolean(DH_EXPERIMENT);
            var mutable=(MutableOptionValues)values;
            boolean overridesUnchanged=mutable.getBooleanValues().equals(checkedBooleanOverrides) &&
                mutable.getStringValues().equals(checkedStringOverrides);
            boolean admissionCache = admissionCacheEnabled();
            if (!admissionCache || pipeline != checkedPipeline || pack != checkedPack || values != checkedValues || client.level != checkedWorld ||
                !name.equals(checkedName) || generation != checkedGeneration || !overridesUnchanged || dhExperiment != checkedDhExperiment) {
                admissionValidations++;
                checkedPipeline=pipeline; checkedPack=pack; checkedValues=values; checkedWorld=client.level;
                checkedName=name; checkedGeneration=generation; checkedDhExperiment=dhExperiment; checkedResult=false;
                checkedBooleanOverrides=Map.copyOf(mutable.getBooleanValues());
                checkedStringOverrides=Map.copyOf(mutable.getStringValues());
                if (!SharedConstants.getCurrentVersion().getName().equals("1.21.1")) return false;
                var stack=loadedStack();
                if (!stackMatches(stack,dhExperiment)) return false;
                var options=pack.getShaderPackOptions(); var declared=options.getOptionSet();
                var actual=com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(options);
                if(!dhContextAllows(stack.containsKey("distanthorizons"),MakeUpQuadEligibility.optionsMatch(actual),
                    com.pp.shaderlod.optimization.OptimizationEditTransaction.cpuOnlyRequests(com.pp.shaderlod.optimization.OptimizationConfig.snapshot())))return false;
                var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();
                var archive=dir.resolve(name).normalize();
                if (!archive.startsWith(dir) || archive.equals(dir)) return false;
                // Rollout permission is separate from exact source/options safety certification.
                checkedResult=RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.MAKEUP,false) &&
                    com.pp.shaderlod.optimization.sourcecache.LoadedRendererSources.allows("MAKEUP") ||
                    RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.UNBOUND,false) &&
                    com.pp.shaderlod.optimization.sourcecache.LoadedRendererSources.allows("UNBOUND") ||
                    RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.REIMAGINED,false) &&
                    com.pp.shaderlod.optimization.sourcecache.LoadedRendererSources.allows("REIMAGINED") ||
                    RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.SILDUR,false) &&
                    com.pp.shaderlod.optimization.sourcecache.LoadedRendererSources.allows("SILDUR") ||
                    RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.BLISS,false) &&
                    com.pp.shaderlod.optimization.sourcecache.LoadedRendererSources.allows("BLISS") ||
                    RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.BSL,false) &&
                    com.pp.shaderlod.optimization.sourcecache.LoadedRendererSources.allows("BSL") ||
                    RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.PHOTON,false) &&
                    com.pp.shaderlod.optimization.sourcecache.LoadedRendererSources.allows("PHOTON") ||
                    com.pp.shaderlod.optimization.sourcecache.LoadedRendererSources.allows("GENERATED") ||
                    RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.ASTRA,false) &&
                    com.pp.shaderlod.optimization.sourcecache.LoadedRendererSources.allows("ASTRA") ||
                    RendererRolloutPolicy.allows(RendererRolloutPolicy.Route.SOLAS,false) && com.pp.shaderlod.optimization.sourcecache.LoadedRendererSources.allows("SOLAS");
                checkedSolas=com.pp.shaderlod.integration.solasquad.SolasQuadEligibility.recognizes(actual);
                if(checkedSolas)checkedResult &= com.pp.shaderlod.integration.solasquad.SolasQuadInputProof.current(((IrisRenderingPipeline)pipeline).getSodiumPrograms(),pack);
                checkedAstra=com.pp.shaderlod.integration.astraquad.AstraQuadEligibility.recognizes(actual);
                if(checkedAstra)
                    checkedResult &= com.pp.shaderlod.integration.astraquad.AstraQuadInputProof.current(((IrisRenderingPipeline)pipeline).getSodiumPrograms(),pack);
            } else {
                admissionCacheHits++;
            }
            return checkedResult && (!checkedSolas || com.pp.shaderlod.integration.solasquad.SolasQuadInputProof.current(((IrisRenderingPipeline)pipeline).getSodiumPrograms(),pack)) && (!checkedAstra || com.pp.shaderlod.integration.astraquad.AstraQuadInputProof.current(((IrisRenderingPipeline)pipeline).getSodiumPrograms(),pack)) && !MakeUpQuadLifecycle.suspended() && !Iris.isFallback();
        } catch (Exception | LinkageError unavailable) { return false; }
    }
}

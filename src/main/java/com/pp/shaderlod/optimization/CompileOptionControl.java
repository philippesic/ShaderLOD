package com.pp.shaderlod.optimization;
import com.pp.shaderlod.*;
import com.pp.shaderlod.optimization.mips.*;
import com.pp.shaderlod.integration.*;
import com.pp.shaderlod.packs.SupportedPackCatalog;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.platform.IrisPlatformHelpers;
import net.minecraft.client.Minecraft;
/** Explicit pinned-family compile consumers. No inferred source rewriting or synthetic resource qualification. */
public final class CompileOptionControl {
    public static final String MAKEUP_TRANSLUCENT_FASTPATH="SHADERLOD_G04_TRANSLUCENT_FASTPATH",MAKEUP_AO_ATTENUATION="SHADERLOD_G06_AO_ATTENUATION";
    public static final String MAKEUP_DEAD_FRAME_COPY="SHADERLOD_MAKEUP_DEAD_FRAME_COPY";
    public static final String BSL_AO_HALF="SHADERLOD_BSL_AO_HALF_RES",BSL_SHADOW_FIELD="SHADERLOD_BSL_SHADOW_FIELD",BSL_RAW_QUERY="SHADERLOD_BSL_RAW_QUERY",BSL_DEAD_MIP="SHADERLOD_BSL_DEAD_MIP_CHAIN",BSL_DEPTH="SHADERLOD_BSL_DEPTH_PRIMING";
    public static final String PHOTON_DEAD_MIP="SHADERLOD_PHOTON_DEAD_MIP_CHAIN",SILDUR_QUERY="SHADERLOD_SILDUR_SHADOW_QUERY_GRAPH",ASTRALEX_DEAD_MIP="SHADERLOD_ASTRALEX_DEAD_MIP_CHAIN",MAKEUP_AO_HALF="SHADERLOD_MAKEUP_AO_HALF_RES",MAKEUP_DEPTH="SHADERLOD_MAKEUP_DEPTH_PRIMING",MAKEUP_EARLY_ALPHA="SHADERLOD_MAKEUP_EARLY_ALPHA";
    public static final String PHOTON_DEPTH="SHADERLOD_PHOTON_DEPTH_PRIMING";
    public static final String PHOTON_SPATIAL="SHADERLOD_SHARED_SPATIAL",PHOTON_LIGHTING_RATE="SHADERLOD_LIGHTING_RATE_PROBE";
    public static final String BLISS_DEPTH="SHADERLOD_BLISS_DEPTH_PRIMING";
    public static final String ASTRALEX_DYNAMIC_GAUSSIAN="SHADERLOD_ASTRALEX_DYNAMIC_GAUSSIAN";
    public static final String ASTRALEX_WORLD_GRAPH="SHADERLOD_ASTRALEX_WORLD_GRAPH";
    public static final String ASTRALEX_DEPTH="SHADERLOD_ASTRALEX_DEPTH_PRIMING";
    public static final String SILDUR_DEPTH="SHADERLOD_SILDUR_DEPTH_PRIMING";
    public static final String SOLAS_DEAD_MIP="SHADERLOD_SOLAS_DEAD_MIP_CHAIN";
    public static final String SOLAS_DEPTH="SHADERLOD_SOLAS_DEPTH_PRIMING";
    public static final String REIMAGINED_DEAD_MIP="SHADERLOD_REIMAGINED_DEAD_MIP_CHAIN",UNBOUND_DEAD_MIP="SHADERLOD_UNBOUND_DEAD_MIP_CHAIN";
    public static final String UNBOUND_DEPTH="SHADERLOD_UNBOUND_DEPTH_PRIMING",REIMAGINED_DEPTH="SHADERLOD_REIMAGINED_DEPTH_PRIMING";
    public static final String SILDUR_PIXEL_GRAPH="SHADERLOD_SILDUR_PIXEL_GRAPH",MAKEUP_SPATIAL_REUSE="SHADERLOD_MAKEUP_SPATIAL_REUSE",MAKEUP_SPATIAL_RATE="SHADERLOD_MAKEUP_SPATIAL_RATE",REIMAGINED_SPATIAL_REUSE="SHADERLOD_REIMAGINED_SPATIAL_REUSE",REIMAGINED_SPATIAL_RATE="SHADERLOD_REIMAGINED_SPATIAL_RATE",UNBOUND_SPATIAL_REUSE="SHADERLOD_UNBOUND_SPATIAL_REUSE",UNBOUND_SPATIAL_RATE="SHADERLOD_UNBOUND_SPATIAL_RATE";
    public static final String UNBOUND_PIXEL_GRAPH="SHADERLOD_UNBOUND_PIXEL_GRAPH",REIMAGINED_PIXEL_GRAPH="SHADERLOD_REIMAGINED_PIXEL_GRAPH",MAKEUP_WORLD_PIXEL="SHADERLOD_MAKEUP_WORLD_PIXEL";
    public static final String SOLAS_PIXEL_GRAPH="SHADERLOD_SOLAS_PIXEL_GRAPH";
    public static final String BLISS_PIXEL_GRAPH="SHADERLOD_BLISS_PIXEL_GRAPH";
    public static final String SILDUR_SPATIAL_REUSE="SHADERLOD_SILDUR_SPATIAL_REUSE",SILDUR_LIGHTING_RATE="SHADERLOD_SILDUR_LIGHTING_RATE";
    public static final String BLISS_SPATIAL_REUSE="SHADERLOD_BLISS_SPATIAL_REUSE",BLISS_LIGHTING_RATE="SHADERLOD_BLISS_LIGHTING_RATE";
    public static final String REIMAGINED_AO_HALF="SHADERLOD_REIMAGINED_AO_HALF_RES",BLISS_AO_HALF="SHADERLOD_BLISS_AO_HALF_RES";
    public static final String UNBOUND_AO_HALF="SHADERLOD_UNBOUND_AO_HALF_RES",SOLAS_AO_HALF="SHADERLOD_SOLAS_AO_HALF_RES",ASTRALEX_AO_HALF="SHADERLOD_ASTRALEX_AO_HALF_RES",SILDUR_AO_HALF="SHADERLOD_SILDUR_AO_HALF_RES";
    public enum Family {
        BSL("bsl-shaders","patches/adapters/bsl-shaders.json",Set.of(BSL_AO_HALF,BSL_SHADOW_FIELD,BSL_RAW_QUERY,BSL_DEAD_MIP,BSL_DEPTH)),
        PHOTON("photon","patches/photon-experiments/live-pixel-budget.json",Set.of(PHOTON_DEAD_MIP,PHOTON_SPATIAL,PHOTON_LIGHTING_RATE,PHOTON_DEPTH)),
        SILDUR("sildurs-vibrant-shaders","patches/adapters/sildurs-vibrant-shaders.json",Set.of(SILDUR_QUERY,SILDUR_DEPTH,SILDUR_AO_HALF,SILDUR_SPATIAL_REUSE,SILDUR_LIGHTING_RATE,SILDUR_PIXEL_GRAPH)),
        ASTRALEX("astralex","patches/adapters/astralex.json",Set.of(ASTRALEX_DEAD_MIP,ASTRALEX_DEPTH,ASTRALEX_AO_HALF,ASTRALEX_DYNAMIC_GAUSSIAN,ASTRALEX_WORLD_GRAPH)),
        MAKEUP("makeup-ultra-fast-shaders","patches/adapters/makeup-ultra-fast-shaders.json",Set.of(MAKEUP_AO_HALF,MAKEUP_DEPTH,MAKEUP_SPATIAL_REUSE,MAKEUP_SPATIAL_RATE,MAKEUP_WORLD_PIXEL,MAKEUP_EARLY_ALPHA,MAKEUP_DEAD_FRAME_COPY,MAKEUP_TRANSLUCENT_FASTPATH,MAKEUP_AO_ATTENUATION)),
        REIMAGINED("complementary-reimagined","patches/adapters/complementary-reimagined.json",Set.of(REIMAGINED_DEPTH,REIMAGINED_AO_HALF,REIMAGINED_DEAD_MIP,REIMAGINED_SPATIAL_REUSE,REIMAGINED_SPATIAL_RATE,REIMAGINED_PIXEL_GRAPH)),
        SOLAS("solas-shader","patches/adapters/solas-shader.json",Set.of(SOLAS_DEPTH,SOLAS_AO_HALF,SOLAS_DEAD_MIP,SOLAS_PIXEL_GRAPH)),
        UNBOUND("complementary-unbound","patches/adapters/complementary-unbound.json",Set.of(UNBOUND_DEPTH,UNBOUND_AO_HALF,UNBOUND_DEAD_MIP,UNBOUND_SPATIAL_REUSE,UNBOUND_SPATIAL_RATE,UNBOUND_PIXEL_GRAPH)),
        BLISS("bliss-shader","patches/adapters/bliss-shader.json",Set.of(BLISS_PIXEL_GRAPH,BLISS_DEPTH,BLISS_AO_HALF,BLISS_SPATIAL_REUSE,BLISS_LIGHTING_RATE));
        final String id,recipe;final Set<String> keys;
        Family(String id,String recipe,Set<String> keys){this.id=id;this.recipe=recipe;this.keys=keys;}
        public String id(){return id;}
        public static Family fromId(String id){for(var family:values())if(family.id.equals(id))return family;throw new IllegalArgumentException("Unsupported compile family: "+id);}
    }
    public record NativeOption(String type,Set<String> allowed,String defaultValue,String value) {
        public NativeOption {allowed=Set.copyOf(allowed);}
        public boolean binary(){return type.equals("STRING")&&allowed.equals(Set.of("0","1"))&&defaultValue.equals("0");}
    }
    public record Facts(boolean qualifiedSource,boolean declaredOption,boolean overworld,boolean nativeAo,boolean dhPresent,boolean voxyPresent) {}
    public interface Session {boolean prepare() throws Exception;default boolean allowLiveCpu(OptimizationSnapshot previous,OptimizationSnapshot next)throws Exception{return false;}void reload(OptimizationEditTransaction.Reload reload) throws Exception;void restore() throws Exception;void recoveryReload(OptimizationEditTransaction.Reload reload) throws Exception;void finish();
        /** Release intent deferred by restoration even when the successful-path finish already failed.
         * This is cleanup only; it must not replay validation or commit side effects. */
        default void releaseRecovery() {}
    }
    private static final Map<Family,EnumMap<OptimizationId,BooleanSupplier>> ADMISSIONS=new EnumMap<>(Family.class);
    private static boolean changingDimension;
    private record SavedInputRecovery(long generation) {}
    private static final ThreadLocal<SavedInputRecovery> savedInputRecovery=new ThreadLocal<>();
    private record OwnedActivation(Thread thread,Map<String,String> queue,Map<String,String> expected,long generation) {}
    private static volatile OwnedActivation ownedActivation;
    private record IncomingReloadPlan(Map<String,String> desired,long generation) {}
    private static final ThreadLocal<IncomingReloadPlan> incomingReloadPlan=new ThreadLocal<>();
    /** Only an admitted synchronous selection/activation load owns this scope; never external queued intent. */
    static void activateOwned(Map<String,String> queue,Map<String,String> changes,Map<String,String> staged,OptimizationEditTransaction.Reload reload)throws Exception {
        final OwnedActivation owner;
        synchronized(CompileOptionControl.class) {
            if(ownedActivation!=null||savedInputRecovery.get()!=null)throw new IllegalStateException("Nested compile activation");
            if(!queue.isEmpty())throw new IllegalStateException("Foreign queue before compile activation");
            owner=new OwnedActivation(Thread.currentThread(),queue,Map.copyOf(changes),OptimizationConfig.generation());
            ownedActivation=owner;
        }
        try {
            for(var entry:changes.entrySet()) {
                // Record only writes attempted after ownership was acquired. A put may mutate then throw.
                try {queue.put(entry.getKey(),entry.getValue());}
                catch(RuntimeException|Error failure) {
                    try {if(Objects.equals(queue.get(entry.getKey()),entry.getValue()))staged.put(entry.getKey(),entry.getValue());}
                    catch(RuntimeException|Error observation){if(observation!=failure)failure.addSuppressed(observation);}
                    throw failure;
                }
                staged.put(entry.getKey(),entry.getValue());
            }
            reload.run();
            // Iris consumes the queue, so content equality belongs at HEAD; generation must also survive the load.
            if(ownedActivation!=owner||owner.thread()!=Thread.currentThread()||owner.generation()!=OptimizationConfig.generation())
                throw new IllegalStateException("Owned compile activation changed during reload");
        }
        finally {synchronized(CompileOptionControl.class){if(ownedActivation==owner)ownedActivation=null;}}
    }
    /** Actual reload-head path, also callable by headless transaction boundary tests. */
    static void synchronizeBeforeReload(Map<String,String> queue,java.util.function.Supplier<Map<String,String>> desired,
            java.util.function.Supplier<Map<String,NativeOption>> loaded) {
        var owner=ownedActivation;
        if(owner!=null) {
            if(owner.thread()!=Thread.currentThread()||owner.queue()!=queue||owner.generation()!=OptimizationConfig.generation()
                    ||!owner.expected().equals(queue))throw new IllegalStateException("Owned compile activation queue/thread/generation changed");
            return;
        }
        var plan=desired.get();var options=loaded.get();
        plan.forEach((key,value)->{var current=options.get(key);if(current==null||!value.equals(current.value())||queue.containsKey(key))queue.put(key,value);});
    }
    private record LeanLoadScope(BslLeanGraph.Route route,boolean enabled,LodPreset preset,long generation) {}
    private static final ThreadLocal<LeanLoadScope> leanLoadScope=new ThreadLocal<>();
    public static BslLeanGraph.Route captureRouting(OptimizationSnapshot next) {
        boolean ow=false;try{ow="minecraft:overworld".equals(dimension());}catch(RuntimeException|LinkageError unavailable){}
        return BslLeanGraph.capture(next,ow);
    }
    static boolean leanRequestAllowed(BslLeanGraph.Route route,CoreUniformFrame.Core core) {
        return recoveringSavedInputs() || core!=null&&route.eligible(core.settings().enabled(),core.preset());
    }
    private static boolean normalLeanRequest() {
        if(recoveringSavedInputs())return true;
        var scope=leanLoadScope.get();
        if(scope!=null) {
            if(scope.generation()!=OptimizationConfig.generation())throw new IllegalStateException("Lean request generation changed");
            var actualCore=OptimizationRuntimeContext.core();
            return scope.route().eligible(scope.enabled(),scope.preset())
                &&OptimizationConfig.loaded()&&scope.route().family().equals(OptimizationConfig.activeFamily())
                &&scope.route().requests().equals(OptimizationConfig.snapshot())&&"minecraft:overworld".equals(dimension())
                &&actualCore!=null&&actualCore.settings().enabled()==scope.enabled()&&actualCore.preset()==scope.preset();
        }
        var core=OptimizationRuntimeContext.core();
        return core!=null&&captureRouting(OptimizationConfig.snapshot()).eligible(core.settings().enabled(),core.preset());
    }
    /** Real constructor source, independently bound to the selected archive and its producer state. */
    public static boolean normalLeanConstructor(java.nio.file.Path path) {
        try {
            if(!BslMipWitnessPins.exactApi()||!DeadMipAdmission.bslCompiler(DeadMipAdmission.apiTuple(),
                net.irisshaders.iris.gl.shader.StandardMacros.getMcVersion(),net.irisshaders.iris.gl.shader.StandardMacros.getFormattedIrisVersion()))return false;
            if(!normalLeanRequest())return false;
            var name=Iris.getIrisConfig().getShaderPackName().orElse(null);if(name==null)return false;
            var selected=Iris.getShaderpacksDirectory().resolve(name).toAbsolutePath().normalize();
            return BslMipRuntimeGuard.ownedConstructorSource(path,selected);
        } catch(Exception|LinkageError unavailable) {return false;}
    }
    private static net.irisshaders.iris.shaderpack.materialmap.NamespacedId dimensionOverride;
    private CompileOptionControl() {}
    /** Checked source/API/graph setup; actual allocated target/epoch/readiness remains the producer's separate protocol. */
    public static synchronized void configureAdmission(String family,OptimizationId id,BooleanSupplier supplier) {
        var target=Family.fromId(family);if(controlledOptions(id).stream().noneMatch(target.keys::contains))throw new IllegalArgumentException("Feature has no compile consumer in "+family);
        ADMISSIONS.computeIfAbsent(target,ignored->new EnumMap<>(OptimizationId.class)).put(id,Objects.requireNonNull(supplier));
    }
    /** Root setup join only. Context must describe the current selected, source-certified generated pack.
     * No registration leaves G01 closed. Typed values are read from the loaded graph, never the pending queue. */
    public static void configureMakeUpDeadCopyAdmission(java.util.function.Supplier<com.pp.shaderlod.optimization.framecopy.MakeUpDeadCopyAdmission.Context> setupContext) {
        Objects.requireNonNull(setupContext);
        configureAdmission(Family.MAKEUP.id(),OptimizationId.DEAD_FRAME_COPY,()->
            com.pp.shaderlod.optimization.framecopy.FrameCopyPins.exactApi()&&
            com.pp.shaderlod.optimization.framecopy.MakeUpDeadCopyAdmission.eligible(setupContext.get(),liveOptions(false)));
    }
    private static boolean admitted(Family family,OptimizationId id) {
        try {var entries=ADMISSIONS.get(family);var supplier=entries==null?null:entries.get(id);return supplier!=null&&supplier.getAsBoolean();}catch(RuntimeException|LinkageError unavailable){return false;}
    }
    public static boolean overworld(net.irisshaders.iris.shaderpack.materialmap.NamespacedId dimension){return dimension!=null&&dimension.getNamespace().equals("minecraft")&&dimension.getName().equals("overworld");}
    private static String dimension() {
        if(dimensionOverride!=null)return dimensionOverride.getNamespace()+":"+dimensionOverride.getName();
        var level=Minecraft.getInstance().level;if(level==null)return "unavailable";
        if(level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD))return "minecraft:overworld";
        if(level.dimension().equals(net.minecraft.world.level.Level.NETHER))return "minecraft:the_nether";
        if(level.dimension().equals(net.minecraft.world.level.Level.END))return "minecraft:the_end";
        return "unknown";
    }
    public static String desired(CoreUniformFrame.Core core,OptimizationSnapshot flags,Facts facts) {
        boolean reduce=core!=null&&core.settings().enabled()&&core.preset()!=LodPreset.QUALITY&&
                flags.enabled(OptimizationId.AO_RENDER_SCALE)&&facts.qualifiedSource()&&facts.declaredOption()&&
                facts.overworld()&&facts.nativeAo()&&!facts.dhPresent()&&!facts.voxyPresent();
        return reduce?"1":"0";
    }
    public static Set<String> controlledOptions(OptimizationId selected) {
        return switch(selected) {
            case SHADOW_SAMPLE_BUDGET, TEMPORAL_SHADOW_FILTER -> Set.of(ASTRALEX_DYNAMIC_GAUSSIAN);
            case TRANSLUCENT_FASTPATH -> Set.of(MAKEUP_TRANSLUCENT_FASTPATH);
            case AO_ATTENUATION -> Set.of(MAKEUP_AO_ATTENUATION);
            case EARLY_ALPHA -> Set.of(ASTRALEX_DYNAMIC_GAUSSIAN,MAKEUP_EARLY_ALPHA);
            case DEPTH_PRIMING -> Set.of(MAKEUP_DEPTH,UNBOUND_DEPTH,REIMAGINED_DEPTH,ASTRALEX_DEPTH,BSL_DEPTH,SOLAS_DEPTH,PHOTON_DEPTH,SILDUR_DEPTH,BLISS_DEPTH);
            case PIXEL_SCALE -> Set.of(UNBOUND_PIXEL_GRAPH,REIMAGINED_PIXEL_GRAPH,MAKEUP_WORLD_PIXEL,BLISS_PIXEL_GRAPH,SOLAS_PIXEL_GRAPH,SILDUR_PIXEL_GRAPH,ASTRALEX_WORLD_GRAPH);
            case AO_RENDER_SCALE -> Set.of(BSL_AO_HALF,MAKEUP_AO_HALF,REIMAGINED_AO_HALF,BLISS_AO_HALF,UNBOUND_AO_HALF,SOLAS_AO_HALF,ASTRALEX_AO_HALF,SILDUR_AO_HALF);
            case SPATIAL_LIGHTING_REUSE -> Set.of(PHOTON_SPATIAL,BLISS_SPATIAL_REUSE,SILDUR_SPATIAL_REUSE,MAKEUP_SPATIAL_REUSE,UNBOUND_SPATIAL_REUSE,REIMAGINED_SPATIAL_REUSE,ASTRALEX_WORLD_GRAPH);
            case SPATIAL_LIGHTING_RATE -> Set.of(PHOTON_LIGHTING_RATE,BLISS_LIGHTING_RATE,SILDUR_LIGHTING_RATE,UNBOUND_SPATIAL_RATE,MAKEUP_SPATIAL_RATE,REIMAGINED_SPATIAL_RATE,ASTRALEX_WORLD_GRAPH);
            case DEAD_MIP_CHAIN -> Set.of(BSL_DEAD_MIP,PHOTON_DEAD_MIP,ASTRALEX_DEAD_MIP,SOLAS_DEAD_MIP,REIMAGINED_DEAD_MIP,UNBOUND_DEAD_MIP);
            case DEAD_FRAME_COPY -> Set.of(MAKEUP_DEAD_FRAME_COPY);
            case SHADOW_MIN_FIELD -> Set.of(BSL_SHADOW_FIELD,SILDUR_QUERY);
            case SHADOW_RAW_QUERY -> Set.of(BSL_SHADOW_FIELD,BSL_RAW_QUERY,SILDUR_QUERY);
            default -> Set.of();
        };
    }
    public static Map<String,String> desiredCompileOptions(CoreUniformFrame.Core core,OptimizationSnapshot flags,Facts observed,
            boolean fieldDeclared,boolean rawDeclared,boolean fieldAdmission,boolean rawAdmission) {
        var result=new LinkedHashMap<String,String>();
        if(observed.declaredOption())result.put(BSL_AO_HALF,desired(core,flags,observed));
        boolean global=core!=null&&core.settings().enabled()&&core.preset()!=LodPreset.QUALITY&&observed.qualifiedSource()&&observed.overworld()&&!observed.dhPresent()&&!observed.voxyPresent();
        boolean minimum=global&&flags.enabled(OptimizationId.SHADOW_MIN_FIELD)&&fieldAdmission;
        boolean raw=global&&flags.enabled(OptimizationId.SHADOW_RAW_QUERY)&&rawAdmission;
        if(fieldDeclared)result.put(BSL_SHADOW_FIELD,minimum||raw?"1":"0");
        if(rawDeclared)result.put(BSL_RAW_QUERY,raw&&fieldDeclared?"1":"0");
        return Map.copyOf(result);
    }
    public static Map<String,String> plan(Family family,CoreUniformFrame.Core core,OptimizationSnapshot flags,Set<String> declared,
            Map<String,NativeOption> nativeOptions,String dimension,boolean dh,boolean voxy,Map<OptimizationId,Boolean> admissions) {
        var result=new LinkedHashMap<String,String>();for(var key:family.keys)if(declared.contains(key))result.put(key,disabled(key));
        boolean vanilla=Set.of("minecraft:overworld","minecraft:the_nether","minecraft:the_end").contains(dimension);
        boolean global=core!=null&&core.settings().enabled()&&core.preset()!=LodPreset.QUALITY&&vanilla;
        if(!global)return Map.copyOf(result);
        boolean ow=dimension.equals("minecraft:overworld"),clean=!dh&&!voxy;
        switch(family) {
            case BSL -> {
                enable(result,BSL_DEPTH,core.preset()==LodPreset.PERFORMANCE&&ow&&clean&&flags.enabled(OptimizationId.DEPTH_PRIMING)&&permission(admissions,OptimizationId.DEPTH_PRIMING));
                boolean ao=bool(nativeOptions,"AO")&&string(nativeOptions,"AO_METHOD",Set.of("0","1"));
                enable(result,BSL_AO_HALF,flags.enabled(OptimizationId.AO_RENDER_SCALE)&&ow&&clean&&ao&&permission(admissions,OptimizationId.AO_RENDER_SCALE));
                boolean minimum=flags.enabled(OptimizationId.SHADOW_MIN_FIELD)&&ow&&clean&&permission(admissions,OptimizationId.SHADOW_MIN_FIELD);
                boolean raw=flags.enabled(OptimizationId.SHADOW_RAW_QUERY)&&ow&&clean&&permission(admissions,OptimizationId.SHADOW_RAW_QUERY);
                enable(result,BSL_SHADOW_FIELD,minimum||raw);enable(result,BSL_RAW_QUERY,raw&&declared.contains(BSL_SHADOW_FIELD));
                enable(result,BSL_DEAD_MIP,flags.enabled(OptimizationId.DEAD_MIP_CHAIN)&&ow&&permission(admissions,OptimizationId.DEAD_MIP_CHAIN));
            }
            case PHOTON -> {
                enable(result,PHOTON_DEPTH,ow&&clean&&flags.enabled(OptimizationId.DEPTH_PRIMING)&&permission(admissions,OptimizationId.DEPTH_PRIMING));
                enable(result,PHOTON_DEAD_MIP,flags.enabled(OptimizationId.DEAD_MIP_CHAIN)&&!ow&&permission(admissions,OptimizationId.DEAD_MIP_CHAIN));
                boolean spatial=result.containsKey(PHOTON_SPATIAL)&&clean&&flags.enabled(OptimizationId.SPATIAL_LIGHTING_REUSE)&&permission(admissions,OptimizationId.SPATIAL_LIGHTING_REUSE);
                enable(result,PHOTON_SPATIAL,spatial);
                enable(result,PHOTON_LIGHTING_RATE,clean&&!spatial&&flags.enabled(OptimizationId.SPATIAL_LIGHTING_RATE)&&permission(admissions,OptimizationId.SPATIAL_LIGHTING_RATE));
            }
            case SILDUR -> { enable(result,SILDUR_QUERY,ow&&clean&&bool(nativeOptions,"Shadows")&&
                    ((flags.enabled(OptimizationId.SHADOW_MIN_FIELD)&&permission(admissions,OptimizationId.SHADOW_MIN_FIELD))||
                     (flags.enabled(OptimizationId.SHADOW_RAW_QUERY)&&permission(admissions,OptimizationId.SHADOW_RAW_QUERY)))); enable(result,SILDUR_DEPTH,ow&&clean&&flags.enabled(OptimizationId.DEPTH_PRIMING)&&permission(admissions,OptimizationId.DEPTH_PRIMING));
                boolean ao=bool(nativeOptions,"SSDO")&&bool(nativeOptions,"TAA")&&string(nativeOptions,"nMap",Set.of("0"))&&
                    string(nativeOptions,SILDUR_QUERY,Set.of("0"))&&!"1".equals(result.get(SILDUR_QUERY));
                enable(result,SILDUR_AO_HALF,ow&&clean&&ao&&flags.enabled(OptimizationId.AO_RENDER_SCALE)&&permission(admissions,OptimizationId.AO_RENDER_SCALE));
                boolean spatialNative=ow&&clean&&ao;
                boolean spatial=spatialNative&&flags.enabled(OptimizationId.SPATIAL_LIGHTING_REUSE)&&permission(admissions,OptimizationId.SPATIAL_LIGHTING_REUSE);
                enable(result,SILDUR_SPATIAL_REUSE,spatial);
                enable(result,SILDUR_LIGHTING_RATE,spatialNative&&!spatial&&flags.enabled(OptimizationId.SPATIAL_LIGHTING_RATE)&&permission(admissions,OptimizationId.SPATIAL_LIGHTING_RATE));
                enable(result,SILDUR_PIXEL_GRAPH,ow&&clean&&bool(nativeOptions,"TAA")&&bool(nativeOptions,"Bloom")&&string(nativeOptions,"nMap",Set.of("0"))&&boolValue(nativeOptions,"Motionblur",false)&&boolValue(nativeOptions,"Depth_of_Field",false)&&flags.enabled(OptimizationId.PIXEL_SCALE)&&permission(admissions,OptimizationId.PIXEL_SCALE));
            }
            case ASTRALEX -> {
                // Compile presence follows request union, never currently absent graph0 draw ownership.
                enable(result,ASTRALEX_WORLD_GRAPH,ow&&clean&&(flags.enabled(OptimizationId.SPATIAL_LIGHTING_REUSE)||flags.enabled(OptimizationId.SPATIAL_LIGHTING_RATE)||flags.enabled(OptimizationId.PIXEL_SCALE)));
                // A requested peer may gain per-draw permission later: never specialize from transient admission.
                enable(result,ASTRALEX_DYNAMIC_GAUSSIAN,flags.enabled(OptimizationId.SHADOW_SAMPLE_BUDGET)||flags.enabled(OptimizationId.TEMPORAL_SHADOW_FILTER)||flags.enabled(OptimizationId.EARLY_ALPHA));
                enable(result,ASTRALEX_DEAD_MIP,flags.enabled(OptimizationId.DEAD_MIP_CHAIN)&&permission(admissions,OptimizationId.DEAD_MIP_CHAIN));
                enable(result,ASTRALEX_DEPTH,ow&&clean&&flags.enabled(OptimizationId.DEPTH_PRIMING)&&permission(admissions,OptimizationId.DEPTH_PRIMING));
                boolean ao=bool(nativeOptions,"AO")&&string(nativeOptions,"AO_QUALITY",Set.of("1","2"))&&string(nativeOptions,"AO_FOVSCALE",Set.of("1","2","3","4","5"));
                enable(result,ASTRALEX_AO_HALF,ow&&clean&&ao&&flags.enabled(OptimizationId.AO_RENDER_SCALE)&&permission(admissions,OptimizationId.AO_RENDER_SCALE));
            }
            case REIMAGINED -> {
                enable(result,REIMAGINED_DEAD_MIP,clean&&flags.enabled(OptimizationId.DEAD_MIP_CHAIN)&&permission(admissions,OptimizationId.DEAD_MIP_CHAIN));
                enable(result,REIMAGINED_DEPTH,ow&&clean&&flags.enabled(OptimizationId.DEPTH_PRIMING)&&permission(admissions,OptimizationId.DEPTH_PRIMING));
                boolean ao=string(nativeOptions,"RP_MODE",Set.of("1"))&&string(nativeOptions,"SSAO_QUALI_DEFINE",Set.of("2"))&&
                    bool(nativeOptions,"TAA")&&boolValue(nativeOptions,"GENERATED_NORMALS",false)&&boolValue(nativeOptions,"COATED_TEXTURES",false);
                enable(result,REIMAGINED_AO_HALF,ow&&clean&&ao&&flags.enabled(OptimizationId.AO_RENDER_SCALE)&&permission(admissions,OptimizationId.AO_RENDER_SCALE));
                boolean spatialNative=ow&&clean&&string(nativeOptions,"RP_MODE",Set.of("1"))&&bool(nativeOptions,"TAA");
                boolean spatial=spatialNative&&flags.enabled(OptimizationId.SPATIAL_LIGHTING_REUSE)&&permission(admissions,OptimizationId.SPATIAL_LIGHTING_REUSE);
                enable(result,REIMAGINED_SPATIAL_REUSE,spatial);enable(result,REIMAGINED_SPATIAL_RATE,spatialNative&&!spatial&&flags.enabled(OptimizationId.SPATIAL_LIGHTING_RATE)&&permission(admissions,OptimizationId.SPATIAL_LIGHTING_RATE));
                enable(result,REIMAGINED_PIXEL_GRAPH,spatialNative&&!flags.enabled(OptimizationId.SPATIAL_LIGHTING_REUSE)&&!flags.enabled(OptimizationId.SPATIAL_LIGHTING_RATE)&&!flags.enabled(OptimizationId.DEPTH_PRIMING)&&!flags.enabled(OptimizationId.SHADOW_RECEIVER_FOOTPRINT)&&!flags.enabled(OptimizationId.TEMPORAL_SHADOW_FILTER)&&!flags.enabled(OptimizationId.SHADOW_SAMPLE_BUDGET)&&!flags.enabled(OptimizationId.MERGED_TERRAIN)&&flags.enabled(OptimizationId.PIXEL_SCALE)&&permission(admissions,OptimizationId.PIXEL_SCALE));
            }
            case SOLAS -> {
                enable(result,SOLAS_DEAD_MIP,clean&&boolValue(nativeOptions,"MOTION_BLUR",false)&&flags.enabled(OptimizationId.DEAD_MIP_CHAIN)&&permission(admissions,OptimizationId.DEAD_MIP_CHAIN));
                enable(result,SOLAS_DEPTH,ow&&clean&&flags.enabled(OptimizationId.DEPTH_PRIMING)&&permission(admissions,OptimizationId.DEPTH_PRIMING));
                enable(result,SOLAS_AO_HALF,ow&&clean&&bool(nativeOptions,"SSAO")&&flags.enabled(OptimizationId.AO_RENDER_SCALE)&&permission(admissions,OptimizationId.AO_RENDER_SCALE));
                boolean pixel=bool(nativeOptions,"TAA")&&bool(nativeOptions,"BLOOM")&&bool(nativeOptions,"LENS_FLARE")&&boolValue(nativeOptions,"FXAA",false)&&boolValue(nativeOptions,"MOTION_BLUR",false)&&boolValue(nativeOptions,"DOF",false)&&((boolValue(nativeOptions,"ADVANCED_MATERIALS",false)&&boolValue(nativeOptions,"PARALLAX",false))||(bool(nativeOptions,"ADVANCED_MATERIALS")&&bool(nativeOptions,"PARALLAX")));enable(result,SOLAS_PIXEL_GRAPH,ow&&clean&&pixel&&flags.enabled(OptimizationId.PIXEL_SCALE)&&permission(admissions,OptimizationId.PIXEL_SCALE));
            }
            case UNBOUND -> {
                enable(result,UNBOUND_DEAD_MIP,clean&&flags.enabled(OptimizationId.DEAD_MIP_CHAIN)&&permission(admissions,OptimizationId.DEAD_MIP_CHAIN));
                enable(result,UNBOUND_DEPTH,ow&&clean&&flags.enabled(OptimizationId.DEPTH_PRIMING)&&permission(admissions,OptimizationId.DEPTH_PRIMING));
                boolean ao=string(nativeOptions,"RP_MODE",Set.of("1"))&&string(nativeOptions,"SSAO_QUALI_DEFINE",Set.of("2"))&&
                    bool(nativeOptions,"TAA")&&boolValue(nativeOptions,"GENERATED_NORMALS",false)&&boolValue(nativeOptions,"COATED_TEXTURES",false);
                enable(result,UNBOUND_AO_HALF,ow&&clean&&ao&&flags.enabled(OptimizationId.AO_RENDER_SCALE)&&permission(admissions,OptimizationId.AO_RENDER_SCALE));
                boolean spatialNative=ow&&clean&&string(nativeOptions,"RP_MODE",Set.of("1"))&&bool(nativeOptions,"TAA");
                boolean spatial=spatialNative&&flags.enabled(OptimizationId.SPATIAL_LIGHTING_REUSE)&&permission(admissions,OptimizationId.SPATIAL_LIGHTING_REUSE);
                enable(result,UNBOUND_SPATIAL_REUSE,spatial);enable(result,UNBOUND_SPATIAL_RATE,spatialNative&&!spatial&&flags.enabled(OptimizationId.SPATIAL_LIGHTING_RATE)&&permission(admissions,OptimizationId.SPATIAL_LIGHTING_RATE));
                enable(result,UNBOUND_PIXEL_GRAPH,spatialNative&&!flags.enabled(OptimizationId.SPATIAL_LIGHTING_REUSE)&&!flags.enabled(OptimizationId.SPATIAL_LIGHTING_RATE)&&!flags.enabled(OptimizationId.DEPTH_PRIMING)&&!flags.enabled(OptimizationId.SHADOW_RECEIVER_FOOTPRINT)&&!flags.enabled(OptimizationId.TEMPORAL_SHADOW_FILTER)&&!flags.enabled(OptimizationId.SHADOW_SAMPLE_BUDGET)&&!flags.enabled(OptimizationId.MERGED_TERRAIN)&&flags.enabled(OptimizationId.PIXEL_SCALE)&&permission(admissions,OptimizationId.PIXEL_SCALE));
            }
            case BLISS -> {
                enable(result,BLISS_PIXEL_GRAPH,flags.enabled(OptimizationId.PIXEL_SCALE)&&ow&&clean&&permission(admissions,OptimizationId.PIXEL_SCALE));
                enable(result,BLISS_DEPTH,ow&&clean&&flags.enabled(OptimizationId.DEPTH_PRIMING)&&permission(admissions,OptimizationId.DEPTH_PRIMING));
                boolean ao=string(nativeOptions,"indirect_effect",Set.of("1"))&&boolValue(nativeOptions,"TAA_UPSCALING",false)&&bool(nativeOptions,"TAA");
                enable(result,BLISS_AO_HALF,ow&&clean&&ao&&flags.enabled(OptimizationId.AO_RENDER_SCALE)&&permission(admissions,OptimizationId.AO_RENDER_SCALE));
                boolean spatialNative=ow&&clean&&string(nativeOptions,"RESOURCEPACK_SKY",Set.of("0"))&&string(nativeOptions,"indirect_effect",Set.of("1"))&&
                    bool(nativeOptions,"TAA")&&boolValue(nativeOptions,"TAA_UPSCALING",false)&&boolValue(nativeOptions,"POM",false)&&boolValue(nativeOptions,"LPV_ENABLED",false);
                boolean spatial=result.containsKey(BLISS_SPATIAL_REUSE)&&spatialNative&&flags.enabled(OptimizationId.SPATIAL_LIGHTING_REUSE)&&permission(admissions,OptimizationId.SPATIAL_LIGHTING_REUSE);
                enable(result,BLISS_SPATIAL_REUSE,spatial);
                enable(result,BLISS_LIGHTING_RATE,spatialNative&&!spatial&&flags.enabled(OptimizationId.SPATIAL_LIGHTING_RATE)&&permission(admissions,OptimizationId.SPATIAL_LIGHTING_RATE));
            }
            case MAKEUP -> {
                boolean exactBase=core.preset()==LodPreset.PERFORMANCE&&ow&&clean&&
                    com.pp.shaderlod.optimization.makeupexact.MakeUpExactGraph.nativeProfile(nativeOptions);
                enable(result,MAKEUP_TRANSLUCENT_FASTPATH,exactBase&&com.pp.shaderlod.optimization.makeupexact.MakeUpExactGraph.only(flags,OptimizationId.TRANSLUCENT_FASTPATH));
                enable(result,MAKEUP_AO_ATTENUATION,exactBase&&com.pp.shaderlod.optimization.makeupexact.MakeUpExactGraph.only(flags,OptimizationId.AO_ATTENUATION));
                // Setup-only permission must prove the selected generated graph and exact platform/API.
                // No supplier means false: a family match alone never authorizes terminal-copy removal.
                enable(result,MAKEUP_DEAD_FRAME_COPY,core.preset()==LodPreset.PERFORMANCE&&ow&&clean&&
                    flags.enabled(OptimizationId.DEAD_FRAME_COPY)&&permission(admissions,OptimizationId.DEAD_FRAME_COPY)&&
                    !flags.enabled(OptimizationId.AO_RENDER_SCALE)&&!flags.enabled(OptimizationId.DEPTH_PRIMING)&&
                    (!flags.enabled(OptimizationId.EARLY_ALPHA)||com.pp.shaderlod.optimization.makeupexact.MakeUpCombinationGraph.selected(flags,OptimizationId.DEAD_FRAME_COPY))&&!flags.enabled(OptimizationId.SPATIAL_LIGHTING_REUSE)&&
                    !flags.enabled(OptimizationId.SPATIAL_LIGHTING_RATE)&&!flags.enabled(OptimizationId.PIXEL_SCALE)&&
                    com.pp.shaderlod.optimization.framecopy.MakeUpDeadCopyAdmission.options(nativeOptions));
                // Compile presence cannot depend on owners created by this very load.
                enable(result,MAKEUP_EARLY_ALPHA,ow&&clean&&flags.enabled(OptimizationId.EARLY_ALPHA));
                enable(result,MAKEUP_AO_HALF,flags.enabled(OptimizationId.AO_RENDER_SCALE)&&clean&&string(nativeOptions,"AO",Set.of("1"))&&
                    !bool(nativeOptions,"VOXY_PATCH")&&permission(admissions,OptimizationId.AO_RENDER_SCALE));
                enable(result,MAKEUP_DEPTH,core.preset()==LodPreset.PERFORMANCE&&ow&&clean&&flags.enabled(OptimizationId.DEPTH_PRIMING)&&permission(admissions,OptimizationId.DEPTH_PRIMING));
                enable(result,MAKEUP_SPATIAL_REUSE,core.preset()==LodPreset.PERFORMANCE&&ow&&clean&&flags.enabled(OptimizationId.SPATIAL_LIGHTING_REUSE)&&bool(nativeOptions,"FOLIAGE_V")&&bool(nativeOptions,"MATERIAL_GLOSS")&&bool(nativeOptions,"SHADOW_CASTING")&&string(nativeOptions,"SHADOW_LOCK",Set.of("0"))&&string(nativeOptions,"SHADOW_TYPE",Set.of("1"))&&string(nativeOptions,"AA_TYPE",Set.of("2"))&&permission(admissions,OptimizationId.SPATIAL_LIGHTING_REUSE));
                enable(result,MAKEUP_SPATIAL_RATE,!"1".equals(result.get(MAKEUP_SPATIAL_REUSE))&&core.preset()==LodPreset.PERFORMANCE&&ow&&clean&&flags.enabled(OptimizationId.SPATIAL_LIGHTING_RATE)&&bool(nativeOptions,"FOLIAGE_V")&&bool(nativeOptions,"MATERIAL_GLOSS")&&bool(nativeOptions,"SHADOW_CASTING")&&string(nativeOptions,"SHADOW_LOCK",Set.of("0"))&&string(nativeOptions,"SHADOW_TYPE",Set.of("1"))&&string(nativeOptions,"AA_TYPE",Set.of("2"))&&permission(admissions,OptimizationId.SPATIAL_LIGHTING_RATE));
                enable(result,MAKEUP_WORLD_PIXEL,!"1".equals(result.get(MAKEUP_SPATIAL_REUSE))&&!"1".equals(result.get(MAKEUP_SPATIAL_RATE))&&core.preset()==LodPreset.PERFORMANCE&&ow&&clean&&flags.enabled(OptimizationId.PIXEL_SCALE)&&bool(nativeOptions,"FOLIAGE_V")&&bool(nativeOptions,"MATERIAL_GLOSS")&&bool(nativeOptions,"SHADOW_CASTING")&&bool(nativeOptions,"FOG_ACTIVE")&&string(nativeOptions,"SHADOW_LOCK",Set.of("0"))&&string(nativeOptions,"SHADOW_TYPE",Set.of("1"))&&string(nativeOptions,"AA_TYPE",Set.of("2"))&&permission(admissions,OptimizationId.PIXEL_SCALE)); }
        }
        return Map.copyOf(result);
    }
    private static boolean permission(Map<OptimizationId,Boolean> permissions,OptimizationId id){return Boolean.TRUE.equals(permissions.get(id));}
    private static String disabled(String key){return key.equals(PHOTON_SPATIAL)||key.equals(PHOTON_LIGHTING_RATE)?"false":"0";}
    private static String enabled(String key){return key.equals(PHOTON_SPATIAL)||key.equals(PHOTON_LIGHTING_RATE)?"true":"1";}
    public static boolean declaredContract(String key,NativeOption option) {
        if(key.equals(PHOTON_SPATIAL)||key.equals(PHOTON_LIGHTING_RATE))return option.type().equals("BOOLEAN")&&option.allowed().equals(Set.of("false","true"))&&option.defaultValue().equals(key.equals(PHOTON_SPATIAL)?"true":"false");
        return option.binary();
    }
    private static void enable(Map<String,String> result,String key,boolean value){if(result.containsKey(key)&&value)result.put(key,enabled(key));}
    private static boolean boolValue(Map<String,NativeOption> options,String key,boolean value){var option=options.get(key);return option!=null&&option.type().equals("BOOLEAN")&&option.value().equals(Boolean.toString(value));}
    private static boolean bool(Map<String,NativeOption> options,String key){var option=options.get(key);return option!=null&&option.type().equals("BOOLEAN")&&option.value().equals("true");}
    private static boolean string(Map<String,NativeOption> options,String key,Set<String> values){var option=options.get(key);return option!=null&&option.type().equals("STRING")&&values.contains(option.value());}
    /** Never evaluate a typed getter before its real OptionSet declaration. Covers non-BSL AO and recovery old-pack states. */
    private static Map<String,NativeOption> liveOptions() { return liveOptions(true); }
    private static Map<String,NativeOption> liveOptions(boolean includeQueue) {
        var pack=Iris.getCurrentPack().orElse(null);if(pack==null)return Map.of();
        return readOptions(pack.getShaderPackOptions(),Iris.getShaderPackOptionQueue(),includeQueue);
    }
    /** Actual declared typed option reader; loaded graphs and staged edits are distinct inputs. */
    static Map<String,NativeOption> readOptions(net.irisshaders.iris.shaderpack.option.ShaderPackOptions options,
            Map<String,String> queue,boolean includeQueue) {
        var result=new HashMap<String,NativeOption>();var values=options.getOptionValues();
        options.getOptionSet().getBooleanOptions().forEach((key,entry)->{
            String value=includeQueue&&queue.containsKey(key)?queue.get(key):Boolean.toString(values.getBooleanValueOrDefault(key));
            result.put(key,new NativeOption("BOOLEAN",Set.of("false","true"),Boolean.toString(entry.getOption().getDefaultValue()),value));
        });
        options.getOptionSet().getStringOptions().forEach((key,entry)->{
            String value=includeQueue&&queue.containsKey(key)?queue.get(key):values.getStringValueOrDefault(key);
            result.put(key,new NativeOption("STRING",Set.copyOf(entry.getOption().getAllowedValues()),entry.getOption().getDefaultValue(),value));
        });return Map.copyOf(result);
    }
    /** Incoming graph veto only. Preserve every other planned control and every native value. */
    static Map<String,String> filterIncomingMakeUpDeadCopy(Map<String,String> plan,Map<String,NativeOption> incoming) {
        if(!"1".equals(plan.get(MAKEUP_DEAD_FRAME_COPY))||
                com.pp.shaderlod.optimization.framecopy.MakeUpDeadCopyAdmission.options(incoming))return plan;
        var filtered=new LinkedHashMap<>(plan);filtered.put(MAKEUP_DEAD_FRAME_COPY,"0");return Map.copyOf(filtered);
    }
    /** ShaderPack constructor boundary, before properties or programs consume the parsed options.
     * The caller supplies the original raw include graph and the actual incoming overrides.
     * Reparse rejected mod-owned controls so properties and rewritten GLSL consume the same zero.
     */
    public static net.irisshaders.iris.shaderpack.option.ShaderPackOptions parsedShaderPackOptions(
            net.irisshaders.iris.shaderpack.include.IncludeGraph raw,Map<String,String> changed,
            net.irisshaders.iris.shaderpack.option.ShaderPackOptions incoming) {
        return parsedShaderPackOptions(null,raw,changed,incoming);
    }
    public static net.irisshaders.iris.shaderpack.option.ShaderPackOptions parsedShaderPackOptions(java.nio.file.Path path,
            net.irisshaders.iris.shaderpack.include.IncludeGraph raw,Map<String,String> changed,
            net.irisshaders.iris.shaderpack.option.ShaderPackOptions incoming) {
        incoming=filterIncomingBslC6(raw,changed,incoming,
                Boolean.getBoolean("shaderlod.experimentalBslC6Mip")&&BslMipWitnessPins.exactApi(),
                path!=null&&normalLeanConstructor(path));
        var declarations=incoming.getOptionSet();
        if(!declarations.getStringOptions().containsKey(MAKEUP_DEAD_FRAME_COPY)&&
                !declarations.getBooleanOptions().containsKey(MAKEUP_DEAD_FRAME_COPY))return incoming;
        var owner=ownedActivation;
        // Recovery replays the transaction's saved inputs; do not replace its rollback protocol.
        if(recoveringSavedInputs())return incoming;
        Map<String,String> desired;
        // This expected plan already passed setup admission in the synchronous two-phase Apply.
        // Re-evaluating the request snapshot here would lose the Apply's explicit next flags.
        if(owner!=null) {
            if(owner.thread()!=Thread.currentThread()||owner.generation()!=OptimizationConfig.generation())
                throw new IllegalStateException("Owned compile activation thread/generation changed");
            desired=owner.expected();
        } else {
            // Reuse reload HEAD's source/API/dimension proof. No extra archive or GL queries here.
            var observed=incomingReloadPlan.get();incomingReloadPlan.remove();
            desired=observed!=null&&observed.generation()==OptimizationConfig.generation()?observed.desired():Map.of();
        }
        return parsedShaderPackOptions(raw,changed,incoming,desired);
    }
    /** Initial Iris load has no reload HEAD. Veto unqualified persisted c6 controls here too. */
    static net.irisshaders.iris.shaderpack.option.ShaderPackOptions filterIncomingBslC6(
            net.irisshaders.iris.shaderpack.include.IncludeGraph raw,Map<String,String> changed,
            net.irisshaders.iris.shaderpack.option.ShaderPackOptions incoming,boolean exactExperiment) {
        return filterIncomingBslC6(raw,changed,incoming,exactExperiment,false);
    }
    static net.irisshaders.iris.shaderpack.option.ShaderPackOptions filterIncomingBslC6(
            net.irisshaders.iris.shaderpack.include.IncludeGraph raw,Map<String,String> changed,
            net.irisshaders.iris.shaderpack.option.ShaderPackOptions incoming,boolean exactExperiment,boolean normalLean) {
        var values=new TreeMap<String,String>();readOptions(incoming,Map.of(),false).forEach((key,value)->values.put(key,value.value()));
        boolean lean="0".equals(values.get(BslLeanGraph.ADAPTER));
        if(!exactExperiment&&!lean)return incoming;
        var declarations=incoming.getOptionSet();
        if(!declarations.getStringOptions().containsKey(BSL_DEAD_MIP)
                &&!declarations.getBooleanOptions().containsKey(BSL_DEAD_MIP))return incoming;
        var typed=readOptions(incoming,Map.of(),false);var control=typed.get(BSL_DEAD_MIP);
        if(control==null||!control.binary())throw new IllegalStateException("Invalid incoming BSL dead-mip option declaration");
        if(!"1".equals(control.value()))return incoming;
        var actual=new TreeMap<String,String>();typed.forEach((key,value)->actual.put(key,value.value()));
        if(lean?normalLean&&DeadMipAdmission.expectedLeanOptions(actual):exactExperiment&&DeadMipAdmission.expectedC6Options(actual))return incoming;
        com.mojang.logging.LogUtils.getLogger().info("ShaderLOD BSL c6 constructor veto: full native map or peer mismatch; incoming control 1 -> 0");
        var safe=new LinkedHashMap<>(changed);safe.put(BSL_DEAD_MIP,"0");
        return new net.irisshaders.iris.shaderpack.option.ShaderPackOptions(raw,safe);
    }
    /** Headless boundary seam; production supplies the current setup plan or owned activation above. */
    static net.irisshaders.iris.shaderpack.option.ShaderPackOptions parsedShaderPackOptions(
            net.irisshaders.iris.shaderpack.include.IncludeGraph raw,Map<String,String> changed,
            net.irisshaders.iris.shaderpack.option.ShaderPackOptions incoming,Map<String,String> desired) {
        var typed=readOptions(incoming,Map.of(),false);
        var filtered=filterIncomingMakeUpDeadCopy(desired,typed);
        var control=typed.get(MAKEUP_DEAD_FRAME_COPY);
        if(control==null)return incoming; // No declaration means no G01 consumer.
        if(!control.binary())throw new IllegalStateException("Invalid incoming G01 compile option declaration");
        if("1".equals(filtered.get(MAKEUP_DEAD_FRAME_COPY))||"0".equals(control.value()))return incoming;
        var safe=new LinkedHashMap<>(changed);safe.put(MAKEUP_DEAD_FRAME_COPY,"0");
        return new net.irisshaders.iris.shaderpack.option.ShaderPackOptions(raw,safe);
    }
    /** Inspect only actual loaded known controls. Queue intent and previous dimension are not graph state. */
    static boolean loadedGraphNeedsDimensionRollback(net.irisshaders.iris.shaderpack.option.ShaderPackOptions options,String next) {
        boolean overworld="minecraft:overworld".equals(next);
        boolean netherEnd="minecraft:the_nether".equals(next)||"minecraft:the_end".equals(next);
        boolean vanilla=overworld||netherEnd;
        var declarations=options.getOptionSet();var values=options.getOptionValues();
        for(var family:Family.values())for(var key:family.keys) {
            boolean compatible=switch(key) {
                case ASTRALEX_WORLD_GRAPH,UNBOUND_PIXEL_GRAPH,REIMAGINED_PIXEL_GRAPH,MAKEUP_WORLD_PIXEL,SILDUR_PIXEL_GRAPH,MAKEUP_SPATIAL_REUSE,MAKEUP_SPATIAL_RATE,REIMAGINED_SPATIAL_REUSE,REIMAGINED_SPATIAL_RATE,UNBOUND_SPATIAL_REUSE,UNBOUND_SPATIAL_RATE,SOLAS_PIXEL_GRAPH,SILDUR_SPATIAL_REUSE,SILDUR_LIGHTING_RATE,BLISS_SPATIAL_REUSE,BLISS_LIGHTING_RATE,UNBOUND_AO_HALF,SOLAS_AO_HALF,ASTRALEX_AO_HALF,SILDUR_AO_HALF,REIMAGINED_AO_HALF,BLISS_AO_HALF,PHOTON_DEPTH,MAKEUP_DEPTH,BSL_AO_HALF,BSL_SHADOW_FIELD,BSL_RAW_QUERY,BSL_DEAD_MIP,BSL_DEPTH,SILDUR_QUERY,BLISS_PIXEL_GRAPH,UNBOUND_DEPTH,REIMAGINED_DEPTH,ASTRALEX_DEPTH,SOLAS_DEPTH,SILDUR_DEPTH,BLISS_DEPTH -> overworld;
                case PHOTON_DEAD_MIP -> netherEnd;
                case MAKEUP_EARLY_ALPHA,MAKEUP_DEAD_FRAME_COPY,MAKEUP_TRANSLUCENT_FASTPATH,MAKEUP_AO_ATTENUATION -> overworld;
                case ASTRALEX_DYNAMIC_GAUSSIAN -> true; // Source-side exclusions preserve all other dimensions.
                case PHOTON_SPATIAL,PHOTON_LIGHTING_RATE,ASTRALEX_DEAD_MIP,SOLAS_DEAD_MIP,REIMAGINED_DEAD_MIP,UNBOUND_DEAD_MIP,MAKEUP_AO_HALF -> vanilla;
                default -> false;
            };
            if(compatible)continue;
            if(key.equals(PHOTON_SPATIAL)||key.equals(PHOTON_LIGHTING_RATE)) {
                var entry=declarations.getBooleanOptions().get(key);
                if(entry!=null&&entry.getOption().getDefaultValue()==key.equals(PHOTON_SPATIAL)&&values.getBooleanValueOrDefault(key))return true;
            } else {
                var entry=declarations.getStringOptions().get(key);
                if(entry!=null&&entry.getOption().getDefaultValue().equals("0")&&Set.copyOf(entry.getOption().getAllowedValues()).equals(Set.of("0","1"))&&values.getStringValueOrDefault(key).equals("1"))return true;
            }
        }
        return false;
    }
    /** Use the passed Apply snapshot, never the previous global request state. */
    static Map<OptimizationId,Boolean> permissions(Family family,OptimizationSnapshot flags,Set<String> keys) {
        var result=new EnumMap<OptimizationId,Boolean>(OptimizationId.class);
        for(var id:List.of(OptimizationId.AO_RENDER_SCALE,OptimizationId.SHADOW_MIN_FIELD,OptimizationId.SHADOW_RAW_QUERY,OptimizationId.DEAD_MIP_CHAIN,OptimizationId.DEAD_FRAME_COPY,OptimizationId.SPATIAL_LIGHTING_REUSE,OptimizationId.SPATIAL_LIGHTING_RATE,OptimizationId.PIXEL_SCALE,OptimizationId.DEPTH_PRIMING))
            result.put(id,flags.enabled(id)&&controlledOptions(id).stream().anyMatch(keys::contains)&&admitted(family,id));
        return result;
    }
    private static Family qualifiedSelectedFamily() {
        try {
            String loaded=com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.qualifiedFamily();
            if(loaded!=null)return Family.fromId(loaded);
            var selected=Iris.getIrisConfig().getShaderPackName().orElse(null);if(selected==null)return null;
            var directory=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var source=directory.resolve(selected).normalize();if(source.equals(directory)||!source.startsWith(directory))return null;
            var fingerprint=PackSourceReader.read(source);var recognized=SupportedPackCatalog.recognize(fingerprint.memberSha256());
            if(recognized.isEmpty()||recognized.get().stage()!=SupportedPackCatalog.Stage.ASSEMBLED_ADAPTER||
                    !SupportedPackCatalog.shaderSourceDigest(fingerprint.memberSha256()).equals(recognized.get().pack().adapterSourceDigest()))return null;
            return Family.fromId(recognized.get().pack().id());
        }catch(Exception unavailable){return null;}
    }
    /** Explicit recipe declarations, constrained to the named family's known controls and binary0(default)/1 contracts. */
    public static Set<String> recipeKeys(Family family,com.google.gson.JsonObject recipe) {
        var keys=new HashSet<String>();
        for(var schema:List.of("optimization_compile_controls","union_compile_options")) {
            if(!recipe.has(schema)||!recipe.get(schema).isJsonObject())continue;
            for(var entry:recipe.getAsJsonObject(schema).entrySet()) {
                var control=entry.getValue().getAsJsonObject();String disabled=control.has("disabled")?control.get("disabled").getAsString():control.has("native")?control.get("native").getAsString():null;
                if(control.has("option")&&control.has("enabled")){String key=control.get("option").getAsString();if(disabled(key).equals(disabled)&&control.get("enabled").getAsString().equals(enabled(key)))keys.add(key);}
                if(control.has("options")&&control.has("disabled_options")) {
                    var off=control.getAsJsonObject("disabled_options");control.getAsJsonObject("options").entrySet().forEach(option->{if(option.getValue().getAsString().equals("1")&&off.has(option.getKey())&&off.get(option.getKey()).getAsString().equals("0"))keys.add(option.getKey());});
                }
            }
        }
        keys.retainAll(family.keys);return Set.copyOf(keys);
    }
    private static Set<String> recipeKeys(Family family) {
        try(var input=CompileOptionControl.class.getResourceAsStream("/assets/shaderlod/adapters/"+family.recipe)) {
            if(input==null)return Set.of();return recipeKeys(family,com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
        }catch(Exception unavailable){return Set.of();}
    }
    private static Map<String,String> desiredCurrent(CoreUniformFrame.Core core,OptimizationSnapshot flags) {return desiredCurrent(core,flags,true);}
    private static Map<String,String> desiredCurrent(CoreUniformFrame.Core core,OptimizationSnapshot flags,boolean includeQueue) {
        var family=qualifiedSelectedFamily();var nativeOptions=liveOptions(includeQueue);
        if(family==null) {var fallback=new HashMap<String,String>();for(var known:Family.values())for(var key:known.keys){var option=nativeOptions.get(key);if(option!=null&&declaredContract(key,option))fallback.put(key,disabled(key));}return Map.copyOf(fallback);}
        var keys=new HashSet<>(recipeKeys(family));
        keys.removeIf(key->!nativeOptions.containsKey(key)||!declaredContract(key,nativeOptions.get(key)));
        var permissions=permissions(family,flags,keys);
        var platform=IrisPlatformHelpers.getInstance();return plan(family,core,flags,keys,nativeOptions,dimension(),platform.isModLoaded("distanthorizons"),platform.isModLoaded("voxy"),permissions);
    }
    /** Cheap entry classification only; desiredCurrent still proves the family/source/environment. */
    static boolean makeUpAlphaWorldEntry(net.irisshaders.iris.shaderpack.option.ShaderPackOptions options,
            String next,CoreUniformFrame.Core core,OptimizationSnapshot flags) {
        if(!"minecraft:overworld".equals(next)||core==null||!core.settings().enabled()
                ||core.preset()==LodPreset.QUALITY||!flags.enabled(OptimizationId.EARLY_ALPHA))return false;
        var entry=options.getOptionSet().getStringOptions().get(MAKEUP_EARLY_ALPHA);
        return entry!=null && !options.getOptionSet().getBooleanOptions().containsKey(MAKEUP_EARLY_ALPHA)
            && entry.getOption().getDefaultValue().equals("0")
            && Set.copyOf(entry.getOption().getAllowedValues()).equals(Set.of("0","1"))
            && options.getOptionValues().getStringValueOrDefault(MAKEUP_EARLY_ALPHA).equals("0");
    }
    static boolean dimensionCompileChange(String key,String loaded,String desired) {
        return loaded.equals(enabled(key))&&desired.equals(disabled(key))
            ||key.equals(MAKEUP_EARLY_ALPHA)&&loaded.equals(disabled(key))&&desired.equals(enabled(key));
    }
    public static net.irisshaders.iris.pipeline.WorldRenderingPipeline prepareDimension(net.irisshaders.iris.shaderpack.materialmap.NamespacedId next) {
        if(changingDimension)return null;var pack=Iris.getCurrentPack().orElse(null);if(pack==null)return null;
        String nextId=next==null?"unavailable":next.getNamespace()+":"+next.getName();
        // A source-specialized CUTOUT is not a native compile option. Its cached Overworld
        // pipeline can outlive an On request made in the Nether, so join this reload seam too.
        boolean sildurMismatch=com.pp.shaderlod.optimization.sildurearlyalpha.SildurAlphaAbsence.activeOnMismatch(OptimizationConfig.snapshot());
        if(!loadedGraphNeedsDimensionRollback(pack.getShaderPackOptions(),nextId)
                &&!makeUpAlphaWorldEntry(pack.getShaderPackOptions(),nextId,OptimizationRuntimeContext.core(),OptimizationConfig.snapshot())
                &&!sildurMismatch)return null;
        var prior=dimensionOverride;dimensionOverride=next;
        Map<String,String> desired;
        try {desired=desiredCurrent(OptimizationRuntimeContext.core(),OptimizationConfig.snapshot());}finally{dimensionOverride=prior;}
        // Dimension safety follows the loaded graph, even when the pending queue requests native values.
        var options=liveOptions(false);boolean unsafe=false;
        for(var entry:desired.entrySet()){var current=options.get(entry.getKey());if(current!=null&&dimensionCompileChange(entry.getKey(),current.value(),entry.getValue()))unsafe=true;}
        if(!unsafe&&!sildurMismatch)return null;
        var queue=Iris.getShaderPackOptionQueue();var pending=Map.copyOf(queue);pending.forEach((key,value)->queue.remove(key,value));
        changingDimension=true;dimensionOverride=next;
        try {
            Iris.reload();
            if(sildurMismatch&&(Iris.isFallback()||Iris.getCurrentPack().isEmpty()||
                com.pp.shaderlod.optimization.sildurearlyalpha.SildurAlphaAbsence.activeOnMismatch(OptimizationConfig.snapshot())))
                throw new IllegalStateException("Cached Sildur alpha absence survived dimension reload");
            return Iris.getPipelineManager().preparePipeline(next);
        }
        catch(java.io.IOException failure){throw new IllegalStateException("Could not restore native family compile options before dimension preparation",failure);}
        finally {dimensionOverride=prior;changingDimension=false;pending.forEach((key,value)->{if(Arrays.stream(Family.values()).noneMatch(family->family.keys.contains(key)))queue.putIfAbsent(key,value);});}
    }
    public static void beforeReload() {
        incomingReloadPlan.remove();
        com.pp.shaderlod.optimization.makeuppresence.MakeUpPresencePrograms.clear();
        if(recoveringSavedInputs())return;
        synchronizeBeforeReload(Iris.getShaderPackOptionQueue(),
                ()->{var desired=desiredCurrent(OptimizationRuntimeContext.core(),OptimizationConfig.snapshot());
                    incomingReloadPlan.set(new IncomingReloadPlan(desired,OptimizationConfig.generation()));return desired;},CompileOptionControl::liveOptions);
    }
    public static Session open(OptimizationSnapshot next) throws Exception {
        OptimizationRuntimeContext.requireApplySupported();
        var core=OptimizationRuntimeContext.core();
        if(core==null)throw new IllegalStateException("Core settings unavailable");
        var queue=Iris.getShaderPackOptionQueue();
        if(!queue.isEmpty())throw new IllegalStateException("Apply pending native shader options before isolating optimization controls");
        // Uniform-only edits consume the immutable input receipt of the graph that remains linked.
        // Selection transactions continue to own every operation that can change archives or compile options.
        String loadedFamily=com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.qualifiedFamily();
        var routing=captureRouting(next);
        if(linkedModeMatchesNext(loadedFamily,liveOptions(false),routing,core)&&loadedFamily!=null&&loadedFamily.equals(OptimizationConfig.activeFamily())
                &&OptimizationEditTransaction.liveUniformChangesAllowed(OptimizationConfig.snapshot(),next))
            return uniformSession(com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.qualifiedReceipt(),
                com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle::qualifiedReceipt);
        var changes=new LinkedHashMap<String,String>();
        var desired=desiredCurrent(core,next);var options=liveOptions();
        desired.forEach((key,value)->{var current=options.get(key);if(current==null||!value.equals(current.value()))changes.put(key,value);});
        final boolean changed=!changes.isEmpty()||
            com.pp.shaderlod.optimization.sildurearlyalpha.SildurAlphaAbsence.activeOnMismatch(next);
        boolean nativeAvailable=MakeUpNativeAvailability.capture();
        boolean cpuCertificate=cpuLiveSource(Iris.getShaderpacksDirectory(),Iris.getIrisConfig().getShaderPackName().orElse(null),
            OptimizationConfig.activeFamily(),core,OptimizationConfig.snapshot(),next,nativeAvailable);
        final PackSelectionTransaction selection=openSelection(queue,changes,()->PackSelectionTransaction.iris(nativeAvailable,next));
        return new NormalCpuSession(session(selection,core,next,changed,queue,changes,cpuCertificate),
            changed?null:NativeCpuApplyProof.capture(next),selection);
    }
    static boolean linkedModeMatchesNext(String family,Map<String,NativeOption> loaded,BslLeanGraph.Route route,CoreUniformFrame.Core core) {
        var adapter=loaded.get(BslLeanGraph.ADAPTER);
        boolean leanLoaded="bsl-shaders".equals(family)&&adapter!=null&&"0".equals(adapter.value());
        return leanLoaded==route.eligible(core.settings().enabled(),core.preset());
    }
    static boolean sameLoadedGraph(Object expected,Object current){return expected!=null&&expected==current;}
    static Session uniformSession(Object receipt,java.util.function.Supplier<?> current){
        return uniformSession(()->sameLoadedGraph(receipt,current.get()));
    }
    static Session uniformSession(BooleanSupplier sameLoadedGraph){
        return new Session(){
            private void verify(){if(!sameLoadedGraph.getAsBoolean())throw new IllegalStateException("Loaded shader inputs changed during uniform Apply");}
            public boolean prepare(){verify();return false;}
            public void reload(OptimizationEditTransaction.Reload reload)throws Exception{reload.run();}
            public void restore(){} // This session changed neither selection nor queued compile options.
            public void recoveryReload(OptimizationEditTransaction.Reload reload){verify();}
            public void finish(){verify();}
        };
    }
    /** Only open() constructs this wrapper; generated/custom Consumer sessions retain their old policy. */
    private static final class NormalCpuSession implements Session {
        final Session delegate;final NativeCpuApplyProof proof;final PackSelectionTransaction selection;boolean prepared,requiresReload,live;
        NormalCpuSession(Session delegate,NativeCpuApplyProof proof,PackSelectionTransaction selection){this.delegate=delegate;this.proof=proof;this.selection=selection;}
        public boolean prepare()throws Exception{requiresReload=delegate.prepare();prepared=true;return requiresReload;}
        public boolean allowLiveCpu(OptimizationSnapshot previous,OptimizationSnapshot next)throws Exception{return delegate.allowLiveCpu(previous,next);}
        NativeCpuApplyProof.Permission permission(OptimizationSnapshot previous,OptimizationSnapshot next)throws Exception {
            if(proof==null||!prepared||requiresReload)return NativeCpuApplyProof.DENIED;
            selection.verifyPreparedSelection();
            var permission=proof.permission(previous,next);live=permission.allowed();return permission;
        }
        public void reload(OptimizationEditTransaction.Reload reload)throws Exception{delegate.reload(reload);}
        public void restore()throws Exception{live=false;delegate.restore();}
        public void recoveryReload(OptimizationEditTransaction.Reload reload)throws Exception{delegate.recoveryReload(reload);}
        public void finish(){if(live){proof.verify();try{selection.verifyPreparedSelection();}catch(java.io.IOException failure){throw new IllegalStateException("Native CPU source changed before finish",failure);}}delegate.finish();}
        public void releaseRecovery(){delegate.releaseRecovery();}
    }
    static NativeCpuApplyProof.Permission normalCpuPermission(Session session,OptimizationSnapshot previous,OptimizationSnapshot next)throws Exception {
        return session instanceof NormalCpuSession normal?normal.permission(previous,next):NativeCpuApplyProof.DENIED;
    }
    static boolean cpuLiveSource(java.nio.file.Path directory,String selected,String family,CoreUniformFrame.Core core,
            OptimizationSnapshot previous,OptimizationSnapshot next,boolean available)throws java.io.IOException {
        if(!available||selected==null||!core.settings().enabled()||core.preset()!=LodPreset.PERFORMANCE
                ||!OptimizationEditTransaction.cpuOnlyFamily(family)||!OptimizationEditTransaction.cpuOnlyRequests(previous)
                ||!OptimizationEditTransaction.cpuOnlyRequests(next))return false;
        var pair=com.pp.shaderlod.packs.PackSelectionService.planSelection(directory,selected,false,LodPreset.QUALITY);
        return pair!=null&&selected.equals(pair.originalFile());
    }
    @FunctionalInterface interface SelectionOpener {PackSelectionTransaction open() throws Exception;}
    /** The actual opening mutation/caller boundary; no world or resource admission is supplied here. */
    static PackSelectionTransaction openSelection(Map<String,String> queue,Map<String,String> changes,SelectionOpener opener)throws Exception {
        Throwable primary=null;
        try {queue.putAll(changes);return opener.open();}
        catch(Exception|Error failure){primary=failure;throw failure;}
        finally {if(primary!=null)cleanupQueue(queue,changes,Map.of(),primary);}
    }
    static Session session(PackSelectionTransaction selection,CoreUniformFrame.Core core,OptimizationSnapshot next,
            boolean changed,Map<String,String> queue,Map<String,String> changes) {
        return session(selection,core,next,changed,queue,changes,false);
    }
    static Session session(PackSelectionTransaction selection,CoreUniformFrame.Core core,OptimizationSnapshot next,
            boolean changed,Map<String,String> queue,Map<String,String> changes,boolean cpuCertificate) {
        return new Session() {
            boolean prepared,requiresReload,cpuLive;
            public boolean prepare() throws Exception {requiresReload=selection.prepare(core.settings().enabled(),core.preset())||changed;prepared=true;return requiresReload;}
            public boolean allowLiveCpu(OptimizationSnapshot previous,OptimizationSnapshot requested)throws Exception {
                if(!cpuCertificate||!prepared||requiresReload||changed||!changes.isEmpty()||!queue.isEmpty()
                        ||!core.settings().enabled()||core.preset()!=LodPreset.PERFORMANCE
                        ||!OptimizationEditTransaction.cpuOnlyRequests(previous)||!OptimizationEditTransaction.cpuOnlyRequests(requested))return false;
                selection.verifyPreparedSelection();cpuLive=true;return true;
            }
            public void reload(OptimizationEditTransaction.Reload reload) throws Exception {
                reloadTwoPhase(selection,reload,liveReloadInputs(core,next));
                com.pp.shaderlod.optimization.bslearlyalpha.BslPostSampleAlpha.verifyAfterReload();
                if(com.pp.shaderlod.optimization.sildurearlyalpha.SildurAlphaAbsence.activeOnMismatch(next))
                    throw new java.io.IOException("Cached Sildur alpha absence program survived requested On reload");
            }
            public void restore() throws Exception {
                Throwable primary=null;
                try {selection.restoreForRecovery();}catch(Exception|Error failure){primary=failure;throw failure;}
                finally {cleanupQueue(queue,changes,Map.of(),primary);}
            }
            public void recoveryReload(OptimizationEditTransaction.Reload reload) throws Exception {
                CompileOptionControl.recoveryReload(selection,reload);
            }
            public void finish() {complete(true);}
            public void releaseRecovery() {complete(false);}
            private void complete(boolean validate) {
                Throwable primary=null;
                try {if(validate&&cpuLive)selection.verifyPreparedSelection();
                    if(validate&&com.pp.shaderlod.optimization.sildurearlyalpha.SildurAlphaAbsence.activeOnMismatch(next))
                        throw new IllegalStateException("Cached Sildur alpha absence program survived Apply");
                    selection.finishRecovery();}
                catch(java.io.IOException failure){primary=failure;throw new IllegalStateException("CPU live source changed before finish",failure);}
                catch(RuntimeException|Error failure){primary=failure;throw failure;}
                finally {cleanupQueue(queue,changes,Map.of(),primary);}
            }
        };
    }
    /** Replay only a selection transaction's owned saved inputs, shared by request and core Apply.
     * The transaction retains file witnesses and queue isolation. Loads without owned recovery
     * inputs keep the normal incoming veto; this scope is never a cold-load admission path. */
    public static void recoveryReload(PackSelectionTransaction selection,OptimizationEditTransaction.Reload reload)throws Exception {
        Objects.requireNonNull(selection,"selection");Objects.requireNonNull(reload,"reload");
        if(savedInputRecovery.get()!=null||ownedActivation!=null)
            throw new IllegalStateException("Nested compile recovery");
        if(!selection.requiresOwnedNativeRecovery()) {selection.recoveryReload(reload::run);return;}
        selection.recoveryReload(()->{
            if(savedInputRecovery.get()!=null||ownedActivation!=null)
                throw new IllegalStateException("Nested compile recovery");
            var scope=new SavedInputRecovery(OptimizationConfig.generation());
            incomingReloadPlan.remove();savedInputRecovery.set(scope);
            try {
                reload.run();
                recoveringSavedInputs(); // The generation must also survive the completed load.
            } finally {savedInputRecovery.remove();incomingReloadPlan.remove();}
        });
    }
    static boolean recoveringSavedInputs() {
        var scope=savedInputRecovery.get();if(scope==null)return false;
        if(ownedActivation!=null||scope.generation()!=OptimizationConfig.generation())
            throw new IllegalStateException("Owned compile recovery owner/generation changed");
        return true;
    }
    /** Normal core/preset Apply uses the same owned post-load activation as request Apply. */
    public static void reloadSettings(PackSelectionTransaction selection,OptimizationEditTransaction.Reload reload)throws Exception {
        var core=OptimizationRuntimeContext.core();
        if(core==null)throw new IllegalStateException("Core settings unavailable for selected compile options");
        reloadSettings(selection,reload,liveReloadInputs(core,OptimizationConfig.snapshot()));
    }
    static void reloadSettings(PackSelectionTransaction selection,OptimizationEditTransaction.Reload reload,ReloadInputs inputs)throws Exception {
        reloadTwoPhase(selection,reload,inputs);
        com.pp.shaderlod.optimization.bslearlyalpha.BslPostSampleAlpha.verifyAfterReload();
        if(com.pp.shaderlod.optimization.sildurearlyalpha.SildurAlphaAbsence.activeOnMismatch(OptimizationConfig.snapshot()))
            throw new java.io.IOException("Cached Sildur alpha absence program survived settings reload");
    }
    private static ReloadInputs liveReloadInputs(CoreUniformFrame.Core core,OptimizationSnapshot next){
        return new ReloadInputs(){
                    public BslLeanGraph.Route route(){return captureRouting(next);}
                    public Object receipt(){return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.qualifiedReceipt();}
                    public boolean requireFreshReceipt(){return true;}
                    public CoreUniformFrame.Core core(){return core;}
                    public Map<String,NativeOption> loaded(){return liveOptions(false);}
                    public Map<String,String> desired(){return desiredCurrent(core,next,false);}
                    public Map<String,String> queue(){return Iris.getShaderPackOptionQueue();}
                    public boolean fallback(){return Iris.isFallback();}
                    public String admissionDetails(Set<String> keys){
                        var details=new TreeMap<String,String>();
                        for(var family:Family.values())for(var id:OptimizationId.values())
                            if(controlledOptions(id).stream().anyMatch(key->keys.contains(key)&&family.keys.contains(key)))
                                details.put(family.id()+"/"+id,OptimizationRuntimeContext.implementation(family.id(),id));
                        return details.toString();
                    }
        };
    }
    /** Real load boundary factored for CPU checks. Normal caller always reads the newly loaded typed Iris OptionSet. */
    interface ReloadInputs {
        Map<String,NativeOption> loaded(); Map<String,String> desired(); Map<String,String> queue(); boolean fallback();
        default BslLeanGraph.Route route(){return BslLeanGraph.LEGACY;}
        default CoreUniformFrame.Core core(){return null;}
        default Object receipt(){return null;}
        default boolean requireFreshReceipt(){return false;}
        default String admissionDetails(Set<String> keys){return "implementation details unavailable";}
    }
    static void reloadTwoPhase(PackSelectionTransaction selection,OptimizationEditTransaction.Reload reload,ReloadInputs inputs) throws Exception {
        if(ownedActivation!=null)throw new IllegalStateException("Nested compile reload transaction");
        if(leanLoadScope.get()!=null)throw new IllegalStateException("Nested lean load scope");
        var core=inputs.core();
        var scope=new LeanLoadScope(inputs.route(),core!=null&&core.settings().enabled(),core==null?LodPreset.QUALITY:core.preset(),OptimizationConfig.generation());
        leanLoadScope.set(scope);
        try { reloadTwoPhaseScoped(selection,reload,inputs); }
        finally {leanLoadScope.remove();incomingReloadPlan.remove();}
    }
    private static void reloadTwoPhaseScoped(PackSelectionTransaction selection,OptimizationEditTransaction.Reload reload,ReloadInputs inputs)throws Exception {
        var previousReceipt=inputs.receipt();
        var bootstrap=selection.bootstrapCompileOptions();
        if(bootstrap.isEmpty())selection.reload(reload::run);
        else {
            var queue=inputs.queue();var deferred=Map.copyOf(queue);var staged=new LinkedHashMap<String,String>();
            deferred.forEach((key,value)->queue.remove(key,value));Throwable primary=null;
            try {activateOwned(queue,bootstrap,staged,()->selection.reload(reload::run));}
            catch(Exception|Error failure){primary=failure;throw failure;}
            finally {cleanupQueue(queue,staged,deferred,primary);}
        }
        if(inputs.fallback())throw new java.io.IOException("Initial selected consumer reload fell back");
        selection.verifyPreparedSelection();
        var queue=inputs.queue();var independent=Map.copyOf(queue);var changes=new LinkedHashMap<String,String>();var staged=new LinkedHashMap<String,String>();
        Throwable primary=null;
        try {
            independent.forEach((key,value)->queue.remove(key,value));
            // Family setup callbacks may read Iris queue; hide newer unrelated intent for the whole post-load proof.
            var loaded=inputs.loaded();
            if(!bootstrap.isEmpty()&&inputs.requireFreshReceipt()&&(inputs.receipt()==null||inputs.receipt()==previousReceipt))
                throw new java.io.IOException("Compile bootstrap has no new loaded source receipt");
            var desired=inputs.desired();
            // A positive G03 first-load capability is not speculative intent. Any loss
            // of its admission or loaded value fails Apply and restores saved inputs;
            // never silently accept a second correcting load as successful admission.
            if("1".equals(bootstrap.get(MAKEUP_EARLY_ALPHA))) {
                var option=loaded.get(MAKEUP_EARLY_ALPHA);
                if(!"1".equals(desired.get(MAKEUP_EARLY_ALPHA)) || option==null
                        || !declaredContract(MAKEUP_EARLY_ALPHA,option) || !"1".equals(option.value()))
                    throw new java.io.IOException("Admitted G03 first-load activation/source/profile did not survive reload");
            }
            for(var entry:desired.entrySet()){
                var option=loaded.get(entry.getKey());
                if(option==null||!declaredContract(entry.getKey(),option))throw new java.io.IOException("Generated compile option declaration changed: "+entry.getKey());
                if(!option.value().equals(entry.getValue()))changes.put(entry.getKey(),entry.getValue());
            }
            if(changes.isEmpty())return; // Unchanged, unsupported, Off and Quality have no unconditional second reload.
            activateOwned(queue,changes,staged,()->selection.reload(reload::run));
            if(inputs.fallback())throw new java.io.IOException("Compile-option reload fell back");
            selection.verifyPreparedSelection();
            var actual=inputs.loaded();
            for(var entry:desired.entrySet()){
                var option=actual.get(entry.getKey());
                if(option==null||!declaredContract(entry.getKey(),option)||!option.value().equals(entry.getValue()))
                    throw new java.io.IOException("Requested compile option did not load: "+entry.getKey());
            }
            var after=inputs.desired();
            if(!desired.equals(after)){
                var affected=new TreeSet<String>();affected.addAll(desired.keySet());affected.addAll(after.keySet());
                affected.removeIf(key->Objects.equals(desired.get(key),after.get(key)));
                throw new java.io.IOException("Compile admission/source/profile changed after activation: requested="+desired+", admitted="+after+"; "+inputs.admissionDetails(affected));
            }
        }catch(Exception|Error failure){primary=failure;throw failure;}
        finally {cleanupQueue(queue,staged,independent,primary);}
    }
    /** Best-effort exact-value cleanup; never replace newer intent or mask a primary native failure. */
    static void cleanupQueue(Map<String,String> queue,Map<String,String> changes,Map<String,String> independent,Throwable primary) {
        Throwable cleanupFailure=null;
        for(var entry:changes.entrySet())try {queue.remove(entry.getKey(),entry.getValue());}
        catch(RuntimeException|Error failure){if(primary!=null)suppress(primary,failure);else if(cleanupFailure==null)cleanupFailure=failure;else suppress(cleanupFailure,failure);}
        for(var entry:independent.entrySet())try {queue.putIfAbsent(entry.getKey(),entry.getValue());}
        catch(RuntimeException|Error failure){if(primary!=null)suppress(primary,failure);else if(cleanupFailure==null)cleanupFailure=failure;else suppress(cleanupFailure,failure);}
        if(primary==null&&cleanupFailure instanceof Error error)throw error;
        if(primary==null&&cleanupFailure instanceof RuntimeException exception)throw exception;
    }
    private static void suppress(Throwable primary,Throwable secondary){if(primary!=secondary)primary.addSuppressed(secondary);}

    private static final class MakeUpNativeAvailability {
        static boolean capture() {return com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.nativeGraphAvailable();}
    }
}

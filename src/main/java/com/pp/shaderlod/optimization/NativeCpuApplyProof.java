package com.pp.shaderlod.optimization;

import com.pp.shaderlod.*;
import com.pp.shaderlod.integration.makeupquad.*;
import java.util.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/** One normal Apply's native identity witness; it never grants a draw or bypasses admission. */
final class NativeCpuApplyProof {
    record Permission(boolean allowed,String reason) {}
    static final Permission DENIED=new Permission(false,"Normal native CPU identity unavailable");
    private static final Set<OptimizationId> IDS=Set.of(OptimizationId.QUAD_COMMAND_COMPACTION,
        OptimizationId.SHADOW_ADMISSION_CACHE,OptimizationId.BINDING_CERTIFICATE_CACHE,
        OptimizationId.INDEX_SCALAR_CACHE,OptimizationId.SHADOW_POSITIVE_VERTEX);
    static boolean requests(OptimizationSnapshot flags) {
        for(var id:OptimizationId.values())if(flags.enabled(id)&&!IDS.contains(id))return false;
        return true;
    }
    record State(Object pack,Object pipeline,Object programs,Object world,Object values,Object declared,Object caps,
        List<Object> programObjects,List<Integer> programHandles,String selected,String family,
        LodSettings settings,LodPreset preset,Map<String,CompileOptionControl.NativeOption> options,
        Map<String,Boolean> booleans,Map<String,String> strings,boolean photonRoute,boolean researchRoutes,
        Object programMap,NativeCpuGraphIdentity.Snapshot graph) {
        State(Object pack,Object pipeline,Object programs,Object world,Object values,Object declared,Object caps,
            List<Object> programObjects,List<Integer> programHandles,String selected,String family,
            LodSettings settings,LodPreset preset,Map<String,CompileOptionControl.NativeOption> options,
            Map<String,Boolean> booleans,Map<String,String> strings,boolean photonRoute,boolean researchRoutes) {
            this(pack,pipeline,programs,world,values,declared,caps,programObjects,programHandles,selected,family,
                settings,preset,options,booleans,strings,photonRoute,researchRoutes,null,null);
        }
        State(Object pack,Object pipeline,Object programs,Object world,Object values,Object declared,Object caps,
            List<Object> programObjects,List<Integer> programHandles,String selected,String family,
            LodSettings settings,LodPreset preset,Map<String,CompileOptionControl.NativeOption> options,
            Map<String,Boolean> booleans,Map<String,String> strings,boolean photonRoute,boolean researchRoutes,
            Object programMap) {
            this(pack,pipeline,programs,world,values,declared,caps,programObjects,programHandles,selected,family,
                settings,preset,options,booleans,strings,photonRoute,researchRoutes,programMap,null);
        }
        boolean same(State other) {
            if(other==null||pack!=other.pack||pipeline!=other.pipeline||programs!=other.programs||world!=other.world
                ||values!=other.values||declared!=other.declared||caps!=other.caps||programMap!=other.programMap
                ||programObjects.size()!=other.programObjects.size())return false;
            if((graph==null)!=(other.graph==null)||graph!=null&&!graph.same(other.graph))return false;
            for(int i=0;i<programObjects.size();i++)if(programObjects.get(i)!=other.programObjects.get(i))return false;
            return programHandles.equals(other.programHandles)&&selected.equals(other.selected)&&family.equals(other.family)
                &&settings.equals(other.settings)&&preset==other.preset&&options.equals(other.options)
                &&booleans.equals(other.booleans)&&strings.equals(other.strings)
                &&photonRoute==other.photonRoute&&researchRoutes==other.researchRoutes;
        }
    }
    private final State before;
    private final java.util.function.Supplier<State> observe;
    private final Thread owner=Thread.currentThread();
    private final OptimizationSnapshot previous,next;
    private final long flagsGeneration=OptimizationConfig.generation();
    private long grantedGeneration=-1,grantedLifecycle=-1;
    private NativeCpuApplyProof(State before,OptimizationSnapshot previous,OptimizationSnapshot next,java.util.function.Supplier<State> observe) {
        this.before=before;this.previous=previous;this.next=next;this.observe=observe;
    }
    static NativeCpuApplyProof capture(OptimizationSnapshot next) {
        try {
            var previous=OptimizationConfig.snapshot();
            if(!requests(previous)||!requests(next)||MakeUpQuadLifecycle.suspended())return null;
            // Force a fresh full native source/options/stack certificate, even with admission caching enabled.
            MakeUpQuadLifecycle.invalidate();
            if(!MakeUpQuadRuntime.nativeGraphAvailable())return null;
            var before=read();if(before==null)return null;
            var pair=com.pp.shaderlod.packs.PackSelectionService.planSelection(Iris.getShaderpacksDirectory(),before.selected(),false,LodPreset.QUALITY);
            if(pair==null||!before.selected().equals(pair.originalFile()))return null;
            return new NativeCpuApplyProof(before,previous,next,NativeCpuApplyProof::read);
        } catch(Exception|LinkageError unavailable) {return null;}
    }
    Permission permission(OptimizationSnapshot previous,OptimizationSnapshot next) {
        if(Thread.currentThread()!=owner||!this.previous.equals(previous)||!this.next.equals(next)
            ||!requests(previous)||!requests(next)||!OptimizationConfig.snapshot().equals(next)
            ||OptimizationConfig.generation()!=flagsGeneration+1||!MakeUpQuadLifecycle.suspended())return DENIED;
        if(!current())return DENIED;
        grantedGeneration=OptimizationConfig.generation();grantedLifecycle=MakeUpQuadLifecycle.epoch();
        return new Permission(true,"Original native CPU5; unchanged pack/program/options and current lifecycle guards");
    }
    void verify() {
        if(Thread.currentThread()!=owner||!OptimizationConfig.snapshot().equals(next)||!current()
            ||OptimizationConfig.generation()!=grantedGeneration||MakeUpQuadLifecycle.epoch()!=grantedLifecycle||!MakeUpQuadLifecycle.suspended())
            throw new IllegalStateException("Native CPU Apply identity/options changed before finish");
    }
    private boolean current() {
        try {return before.same(observe.get());}catch(Exception|LinkageError unavailable){return false;}
    }
    private static State read() {
        var core=OptimizationRuntimeContext.core();var client=Minecraft.getInstance();
        if(core==null||!core.settings().enabled()||core.preset()!=LodPreset.PERFORMANCE||Iris.isFallback()
            ||!Iris.getShaderPackOptionQueue().isEmpty()||client.level==null||client.level.dimension()!=Level.OVERWORLD
            ||net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()
            ||!MakeUpQuadApiPins.exactApi()||!MakeUpQuadRuntime.loadedStackMatches()
            ||!MakeUpQuadRuntime.resourcesMatchPacks(client.getResourcePackRepository().getSelectedPacks(),
                net.minecraft.server.packs.repository.Pack::isHidden,net.minecraft.server.packs.repository.Pack::getId))return null;
        var pipeline=Iris.getPipelineManager().getPipelineNullable();var pack=Iris.getCurrentPack().orElse(null);
        if(pipeline==null||pipeline.getClass()!=IrisRenderingPipeline.class||pack==null)return null;
        var programs=((IrisRenderingPipeline)pipeline).getSodiumPrograms();
        if(programs==null||programs.getClass()!=SodiumPrograms.class)return null;
        var graph=NativeCpuGraphIdentity.capture((IrisRenderingPipeline)pipeline);
        if(graph==null)return null;
        var caps=org.lwjgl.opengl.GL.getCapabilities();
        if(!MakeUpQuadRuntime.capabilitiesAllow(org.lwjgl.system.Pointer.POINTER_SIZE,caps.OpenGL41,caps.glMultiDrawElementsBaseVertex))return null;
        var optionSet=pack.getShaderPackOptions();var values=optionSet.getOptionValues();
        if(values.getClass()!=MutableOptionValues.class)return null;
        var mutable=(MutableOptionValues)values;var selected=Iris.getIrisConfig().getShaderPackName().orElse(null);
        if(selected==null||!selected.equals(Iris.getCurrentPackName()))return null;
        // The exact pinned SodiumPrograms class owns every terrain and shadow program in
        // this private EnumMap. A public terrain-only witness misses SHADOW/CUTOUT churn.
        final Object sodiumProgramMap;
        final java.util.EnumMap<?,?> shaderPrograms;
        try {
            var field=SodiumPrograms.class.getDeclaredField("shaders");
            if(field.getType()!=java.util.EnumMap.class)return null;
            field.setAccessible(true);
            sodiumProgramMap=field.get(programs);
            if(!(sodiumProgramMap instanceof java.util.EnumMap<?,?> found))return null;
            shaderPrograms=found;
        } catch(ReflectiveOperationException|RuntimeException unavailable) {return null;}
        var objects=new ArrayList<Object>();var handles=new ArrayList<Integer>();
        for(var pass:SodiumPrograms.Pass.values()) {
            var value=shaderPrograms.get(pass);
            if(value!=null && !(value instanceof net.caffeinemc.mods.sodium.client.gl.shader.GlProgram<?>))return null;
            objects.add(value);
            handles.add(value==null?0:((net.caffeinemc.mods.sodium.client.gl.shader.GlProgram<?>)value).handle());
        }
        for(var pass:List.of(DefaultTerrainRenderPasses.SOLID,DefaultTerrainRenderPasses.CUTOUT,DefaultTerrainRenderPasses.TRANSLUCENT)) {
            var program=programs.getProgram(pass);if(program==null||program.handle()<=0)return null;
        }
        return new State(pack,pipeline,programs,client.level,values,optionSet.getOptionSet(),caps,
            java.util.Collections.unmodifiableList(new ArrayList<>(objects)),List.copyOf(handles),
            selected,OptimizationConfig.activeFamily(),core.settings(),core.preset(),Map.copyOf(CompileOptionControl.readOptions(optionSet,Map.of(),false)),
            Map.copyOf(mutable.getBooleanValues()),Map.copyOf(mutable.getStringValues()),
            Boolean.getBoolean(com.pp.shaderlod.packs.PackSelectionService.PHOTON_QUAD_ORIGINAL_GRAPH),
            Boolean.getBoolean(RendererRolloutPolicy.RESEARCH_ROUTES),sodiumProgramMap,graph);
    }
}

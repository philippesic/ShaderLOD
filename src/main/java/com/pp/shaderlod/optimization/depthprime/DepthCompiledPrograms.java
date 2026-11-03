package com.pp.shaderlod.optimization.depthprime;

import com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource;
import com.pp.shaderlod.optimization.sourcecache.LinkedCompilationProof;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.gl.shader.GlShader;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;

/** Independent depth purpose proof. No early-alpha readiness/certificate grants depth permission. */
public final class DepthCompiledPrograms {
    private final String family, programName, recipeResource, recipeSha, option;
    private final java.util.function.Predicate<Map<String,String>> profile;
    private final LinkedCompilationProof proof;
    /** Captured preparation options bind family-specific invariant exceptions to this purpose. */
    @FunctionalInterface public interface StageInvariant {
        boolean test(String source, boolean vertex, Map<String,String> options);
    }
    private final StageInvariant invariant;
    private record Resources(Object manager, List<?> packs) {
        static Resources capture() {
            var manager=Minecraft.getInstance().getResourceManager();
            try (var packs=manager.listPacks()) {
                return new Resources(manager,packs.toList());
            }
        }
        boolean current() {
            var manager=Minecraft.getInstance().getResourceManager();
            if (manager!=this.manager) return false;
            try (var stream=manager.listPacks()) {
                var iterator=stream.iterator();
                for (var pack:packs) if (!iterator.hasNext() || iterator.next()!=pack) return false;
                return !iterator.hasNext();
            }
        }
    }
    private record Derived(IrisCompiledSource.Certificate source, int vertex) {}
    private record Owner(IrisCompiledSource.Certificate source, Resources resources,
                         DepthProgramLease<GlProgram<?>> lease,Object gl) {}
    private final Map<ProgramSource,LinkedCompilationProof.Prepared> pending=new IdentityHashMap<>();
    private final Set<ProgramSource> consumed=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Map<PatchShaderType,String>> transformedText=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<IrisCompiledSource.Certificate,Resources> resources=new IdentityHashMap<>();
    private final Map<GlProgram<?>,Derived> derivatives=new IdentityHashMap<>();
    private final Map<GlProgram<?>,Owner> owners=new IdentityHashMap<>();
    private final DepthShaderInputs<PatchShaderType,GlShader> shaderInputs=new DepthShaderInputs<>(
        GlShader::handle,GL20C::glGetShaderSource,id->GL20C.glGetShaderi(id,GL20C.GL_COMPILE_STATUS)==GL11C.GL_TRUE);
    private long qualifiedLookups, refusedLookups;
    // Fixed vocabulary and one aggregate per stage: no shader text, source objects or draw work.
    private enum CompileStage {
        WRONG_PROGRAM, RECIPE_SOURCE, CONSTRUCTION, PREPARE, PROFILE, PREPARED,
        CONSUMED_ARGUMENTS, ARGUMENTS_MISMATCH, MISSING_PREPARATION, MISSING_CONSUMED_ARGUMENTS,
        TRANSFORM_STAGES, INVARIANT_VERTEX, INVARIANT_FRAGMENT, TRANSFORMED,
        DRIVER_UNQUALIFIED_TRANSFORM, DRIVER_CAPTURE, DRIVER_CAPTURED,
        PREPARED_LINK, PREPARED_PACK, PREPARED_RESOURCES, PREPARED_DRIVER, PREPARED_CURRENCY,
        PREPARED_CURRENT, MISSING_DERIVATIVE, DERIVATIVE, LINKED_NATIVE_OWNER,
        OWNER_CERTIFICATE, OWNER_VERTEX, OWNED, EXCEPTION
    }
    private final EnumMap<CompileStage,Long> compileCounts=new EnumMap<>(CompileStage.class);
    private String compileLast="not observed";
    private void compilation(CompileStage stage,ProgramSource source,SodiumPrograms.Pass pass) {
        if (!"photon".equals(family) && !"bsl-shaders".equals(family)) return;
        compileCounts.compute(stage,(key,count)->count==null?1L:count==Long.MAX_VALUE?count:count+1);
        String name=source==null?"null":source.getName();
        String program="gbuffers_terrain".equals(name)?"terrain":
            "gbuffers_terrain_solid".equals(name)?"terrain_solid":source==null?"null":"other";
        compileLast=stage+" program="+program+" pass="+(pass==null?"unassigned":pass.name());
    }
    private final DepthOwnerScopes<DepthCompiledPrograms> scopes = new DepthOwnerScopes<>(DepthCompiledPrograms::clear);
    public DepthCompiledPrograms(String family,String programName,String recipeResource,String recipeSha,String option,java.util.function.Predicate<Map<String,String>> profile,BiPredicate<String,Boolean> invariant) {
        this(family,programName,recipeResource,recipeSha,option,profile,(source,vertex,options)->invariant.test(source,vertex));
    }
    public DepthCompiledPrograms(String family,String programName,String recipeResource,String recipeSha,String option,java.util.function.Predicate<Map<String,String>> profile,StageInvariant invariant) {
        this.family=family; this.programName=programName; this.recipeResource=recipeResource; this.recipeSha=recipeSha; this.option=option; this.profile=profile; this.proof=new LinkedCompilationProof(family,programName); this.invariant=invariant;
    }
    public DepthCompiledPrograms session(Object owner) {
        var existing=scopes.get(owner);
        if (existing!=null) return existing;
        var created=new DepthCompiledPrograms(family,programName,recipeResource,recipeSha,option,profile,invariant);
        // All callbacks run on the render thread. Outside construction a missing scope stays inert.
        if (com.pp.shaderlod.optimization.makeupmerged.MergedLinkageGate.trackConstructionCleanup(created,()->release(owner)))
            scopes.put(owner,created);
        return created;
    }
    public void release(Object owner) { scopes.release(owner); }
    public boolean hasSessions() { return !scopes.isEmpty(); }
    /** Missing compilation evidence must refuse without allocating a new scope on a terrain draw. */
    public boolean currentOwner(Object programs,TerrainRenderPass pass,GlProgram<?> nativeProgram,GlProgram<?> depth,long epoch) {
        var scope=scopes.get(programs);
        if (scope==null) { refusedLookups++; return false; }
        return scope.current(programs,pass,nativeProgram,depth,epoch);
    }
    public void clear() { scopes.invalidate(); proof.clear(); shaderInputs.clear(); pending.clear(); consumed.clear(); transformedText.clear(); resources.clear(); derivatives.clear(); owners.clear(); compileCounts.clear(); compileLast="invalidated"; }
    public String status() {
        var children=scopes.values();
        if (!children.isEmpty()) return "depth compiled scopes="+children.size()+"; "+children.stream().map(DepthCompiledPrograms::status).toList();
        return "depth compiled owners="+owners.size()+" sourceGrants="+qualifiedLookups+" sourceRefused="+refusedLookups+"; "+proof.status()+(("photon".equals(family)||"bsl-shaders".equals(family))?"; compilationLast="+compileLast+" compilationCounts="+compileCounts:"");
    }
    private boolean recipePinned() {
        try(var input=DepthCompiledPrograms.class.getResourceAsStream(recipeResource)) {
            return input!=null && recipeSha.equals(java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException unavailable) { return false; }
    }
    public void prepare(ProgramSource source,ProgramSet set,AlphaTest alpha,String vertex,String geometry,
            String control,String evaluation,String fragment,Object textures,long epoch,BooleanSupplier exactDepthSource) {
        proof.discardPreparation(source); pending.remove(source); consumed.remove(source);
        if (source==null || !programName.equals(source.getName())) { compilation(CompileStage.WRONG_PROGRAM,source,null); return; }
        try {
        // Existing independent depth source/profile/recipe/API checks run at compilation, including helper pins.
        if (!recipePinned() || !exactDepthSource.getAsBoolean()) { compilation(CompileStage.RECIPE_SOURCE,source,null); return; }
        if (!com.pp.shaderlod.optimization.makeupmerged.MergedLinkageGate.trackConstructionCleanup(this,this::clear)) { compilation(CompileStage.CONSTRUCTION,source,null); return; }
        var prepared=proof.prepare(source,set,alpha,vertex,geometry,control,evaluation,fragment,textures,epoch);
        if (prepared!=null) {
            var actual=prepared.source().input().options().values();
            if (!profile.test(actual) || !"1".equals(actual.get(option)) || !recipePinned()) { proof.discardPreparation(source); compilation(CompileStage.PROFILE,source,null); return; }
            resources.put(prepared.source(),Resources.capture()); pending.put(source,prepared);
            compilation(CompileStage.PREPARED,source,null);
        } else compilation(CompileStage.PREPARE,source,null);
        } catch (RuntimeException | LinkageError unavailable) { proof.discardPreparation(source); compilation(CompileStage.EXCEPTION,source,null); }
    }
    public void arguments(ProgramSource source,ProgramSet set,AlphaTest alpha,String vertex,String geometry,
            String control,String evaluation,String fragment,Object textures) {
        arguments(source,set,alpha,vertex,geometry,control,evaluation,fragment,textures,()->false);
    }
    public void arguments(ProgramSource source,ProgramSet set,AlphaTest alpha,String vertex,String geometry,
            String control,String evaluation,String fragment,Object textures,BooleanSupplier specializedReceipt) {
        consumed.remove(source);
        var prepared=pending.get(source);
        if(prepared==null)return;
        var stages=prepared.source().stages();
        boolean exact=prepared.source().set()==set && prepared.alpha().equals(alpha)
            && Objects.equals(stages.vertex(),vertex) && Objects.equals(stages.geometry(),geometry)
            && Objects.equals(stages.control(),control) && Objects.equals(stages.evaluation(),evaluation)
            && textures==set.getPackDirectives().getTextureMap()
            && DepthPatchArguments.fragment(family,stages.fragment(),fragment,alpha,specializedReceipt);
        if(exact) { consumed.add(source); compilation(CompileStage.CONSUMED_ARGUMENTS,source,null); }
        else { pending.remove(source); proof.discardPreparation(source); compilation(CompileStage.ARGUMENTS_MISMATCH,source,null); }
    }
    public void transformed(ProgramSource source,ProgramSet set,AlphaTest alpha,Map<PatchShaderType,String> text) {
        var prepared=pending.remove(source);
        boolean observed=consumed.remove(source);
        boolean stagesPresent=prepared!=null && observed && text!=null
            && text.get(PatchShaderType.VERTEX)!=null && text.get(PatchShaderType.FRAGMENT)!=null
            && text.entrySet().stream().allMatch(e->e.getValue()==null || e.getKey()==PatchShaderType.VERTEX || e.getKey()==PatchShaderType.FRAGMENT);
        boolean vertexInvariant=stagesPresent && invariant.test(text.get(PatchShaderType.VERTEX),true,prepared.source().input().options().values());
        boolean fragmentInvariant=vertexInvariant && invariant.test(text.get(PatchShaderType.FRAGMENT),false,prepared.source().input().options().values());
        boolean accepted=stagesPresent && vertexInvariant && fragmentInvariant;
        CompileStage result=prepared==null?CompileStage.MISSING_PREPARATION:
            !observed?CompileStage.MISSING_CONSUMED_ARGUMENTS:
            !stagesPresent?CompileStage.TRANSFORM_STAGES:
            !vertexInvariant?CompileStage.INVARIANT_VERTEX:
            !fragmentInvariant?CompileStage.INVARIANT_FRAGMENT:CompileStage.TRANSFORMED;
        compilation(result,source,null);
        if(text!=null) { transformedText.remove(text); if(accepted)transformedText.add(text); }
        proof.transformed(source,set,alpha,text,accepted);
    }
    public void compiled(Map<PatchShaderType,String> text,Map<PatchShaderType,GlShader> stages) {
        // Reused map identities must not retain a previous successful capture after refusal.
        proof.compiled(null,stages);
        shaderInputs.capture(null,stages);
        if (!transformedText.contains(text)) { compilation(CompileStage.DRIVER_UNQUALIFIED_TRANSFORM,null,null); return; }
        if (stages!=null && stages.size()==2 && shaderInputs.capture(text,stages)) {
            proof.compiled(text,stages); compilation(CompileStage.DRIVER_CAPTURED,null,null);
        } else compilation(CompileStage.DRIVER_CAPTURE,null,null);
    }
    public boolean prepared(ProgramSource source,AlphaTest alpha,Map<PatchShaderType,GlShader> stages,long epoch) {
        try {
        var certificate=proof.linked(source,alpha,stages);
        var captured=resources.get(certificate);
        var pack=Iris.getCurrentPack().orElse(null);
        boolean resourcesCurrent=certificate!=null && pack!=null && captured!=null && captured.current();
        boolean driverCurrent=resourcesCurrent && shaderInputs.unchangedDriverInput(stages);
        boolean sourceCurrent=driverCurrent && certificate.current(pack,Iris.getIrisConfig().getShaderPackName().orElse(null),epoch);
        CompileStage result=certificate==null?CompileStage.PREPARED_LINK:
            pack==null?CompileStage.PREPARED_PACK:
            !resourcesCurrent?CompileStage.PREPARED_RESOURCES:
            !driverCurrent?CompileStage.PREPARED_DRIVER:
            !sourceCurrent?CompileStage.PREPARED_CURRENCY:CompileStage.PREPARED_CURRENT;
        compilation(result,source,null);
        return resourcesCurrent && driverCurrent && sourceCurrent;
        } catch (RuntimeException | LinkageError unavailable) { compilation(CompileStage.EXCEPTION,source,null); return false; }
    }
    /** Called only after the family-specific pass/alpha/invariant checks and pinned coverage link succeed. */
    public void derived(GlProgram<?> depth,ProgramSource source,AlphaTest alpha,Map<PatchShaderType,GlShader> stages) {
        var certificate=proof.linked(source,alpha,stages);
        if (depth!=null && certificate!=null) {
            derivatives.put(depth,new Derived(certificate,stages.get(PatchShaderType.VERTEX).handle()));
            compilation(CompileStage.DERIVATIVE,source,null);
        } else compilation(CompileStage.MISSING_DERIVATIVE,source,null);
    }
    private boolean linkedTo(GlProgram<?> program,Map<PatchShaderType,GlShader> stages) {
        if (program==null || stages==null || stages.size()!=2 || !stages.containsKey(PatchShaderType.VERTEX) || !stages.containsKey(PatchShaderType.FRAGMENT)) return false;
        int id=program.handle();
        if (id<=0 || GL20C.glGetProgrami(id,GL20C.GL_LINK_STATUS)!=GL11C.GL_TRUE || GL20C.glGetProgrami(id,GL20C.GL_ATTACHED_SHADERS)!=2) return false;
        int[] count=new int[1],attached=new int[2]; GL20C.glGetAttachedShaders(id,count,attached);
        var handles=shaderInputs.current(stages); if(handles==null)return false;
        int vertex=handles.get(PatchShaderType.VERTEX),fragment=handles.get(PatchShaderType.FRAGMENT);
        return count[0]==2 && ((attached[0]==vertex&&attached[1]==fragment)||(attached[1]==vertex&&attached[0]==fragment));
    }
    public void own(IrisRenderingPipeline pipeline,Object programs,SodiumPrograms.Pass pass,ProgramSource source,
            AlphaTest alpha,Map<PatchShaderType,GlShader> stages,GlProgram<?> nativeProgram,GlProgram<?> depth,long epoch) {
        owners.remove(depth);
        try {
        var derived=derivatives.get(depth);
        if (derived==null) { compilation(CompileStage.MISSING_DERIVATIVE,source,pass); return; }
        if (!prepared(source,alpha,stages,epoch)) return;
        if (!linkedTo(nativeProgram,stages)) { compilation(CompileStage.LINKED_NATIVE_OWNER,source,pass); return; }
        if (derived.source()!=proof.linked(source,alpha,stages)) { compilation(CompileStage.OWNER_CERTIFICATE,source,pass); return; }
        if (derived.vertex()!=shaderInputs.current(stages).get(PatchShaderType.VERTEX)) { compilation(CompileStage.OWNER_VERTEX,source,pass); return; }
        owners.put(depth,new Owner(derived.source(),resources.get(derived.source()),
            new DepthProgramLease<>(pipeline,programs,pass,nativeProgram,depth,epoch,GlProgram::handle),org.lwjgl.opengl.GL.getCapabilities()));
        compilation(CompileStage.OWNED,source,pass);
        } catch (RuntimeException | LinkageError unavailable) { owners.remove(depth); compilation(CompileStage.EXCEPTION,source,pass); }
    }
    public boolean drawContext() {
        try {
        var client=Minecraft.getInstance();
        return com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread() && !Iris.isFallback()
            && Iris.getShaderPackOptionQueue().isEmpty()
            && !com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended()
            && !net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()
            && client.level!=null && client.level.dimension()==net.minecraft.world.level.Level.OVERWORLD
            && MakeUpQuadRuntime.loadedStackMatches()
            && MakeUpQuadRuntime.resourcesMatch(client.getResourcePackRepository().getSelectedIds());
        } catch (RuntimeException | LinkageError unavailable) { return false; }
    }
    public boolean current(Object programs,TerrainRenderPass pass,GlProgram<?> nativeProgram,GlProgram<?> depth,long epoch) {
        try {
        var owner=owners.get(depth);
        var nativePass=pass==DefaultTerrainRenderPasses.SOLID?SodiumPrograms.Pass.TERRAIN:
            pass==DefaultTerrainRenderPasses.CUTOUT?SodiumPrograms.Pass.TERRAIN_CUTOUT:null;
        var pack=Iris.getCurrentPack().orElse(null);
        boolean accepted=owner!=null && nativePass!=null && pack!=null && drawContext()
            && owner.lease().current(Iris.getPipelineManager().getPipelineNullable(),programs,nativePass,nativeProgram,depth,epoch)
            && owner.source().current(pack,Iris.getIrisConfig().getShaderPackName().orElse(null),epoch)
            && owner.resources().current() && owner.gl()==org.lwjgl.opengl.GL.getCapabilities()
            && GL20C.glGetProgrami(nativeProgram.handle(),GL20C.GL_LINK_STATUS)==GL11C.GL_TRUE
            && GL20C.glGetProgrami(depth.handle(),GL20C.GL_LINK_STATUS)==GL11C.GL_TRUE;
        if (accepted) qualifiedLookups++; else refusedLookups++;
        return accepted;
        } catch (RuntimeException | LinkageError unavailable) { refusedLookups++; return false; }
    }
}

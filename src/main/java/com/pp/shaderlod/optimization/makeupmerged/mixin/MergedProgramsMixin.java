package com.pp.shaderlod.optimization.makeupmerged.mixin;
import com.google.common.collect.ImmutableSet;
import com.pp.shaderlod.optimization.makeupmerged.MakeupMergedVertexInterface;
import com.pp.shaderlod.optimization.makeupmerged.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;
import net.caffeinemc.mods.sodium.client.gl.shader.*;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.shaderpack.programs.*;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL20C;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Explicit original-source-gated clone. Shared original FRAGMENT object; exact native Iris builder. */
@Mixin(value=SodiumPrograms.class,remap=false)
public abstract class MergedProgramsMixin implements MergedProgramAccess {
    @Shadow @Final private EnumMap<SodiumPrograms.Pass,GlProgram<ChunkShaderInterface>> shaders;
    @Shadow private SodiumPrograms.Pass mapTerrainRenderPass(TerrainRenderPass pass){throw new AssertionError();}
    @Shadow private GlProgram<ChunkShaderInterface> buildProgram(GlProgram.Builder builder,IrisRenderingPipeline pipeline,SodiumPrograms.Pass pass,
        ProgramSource source,AlphaTest alpha,CustomUniforms custom,Supplier<ImmutableSet<Integer>> flip,boolean tess){throw new AssertionError();}
    @Unique private final EnumMap<SodiumPrograms.Pass,GlProgram<ChunkShaderInterface>> shaderlod$merged=new EnumMap<>(SodiumPrograms.Pass.class);
    @Unique private final Set<Integer> shaderlod$earlyOwners=new HashSet<>();
    @Unique private Map<PatchShaderType,String> shaderlod$lastStages;
    @Unique private ProgramSource shaderlod$lastSource;
    @Inject(method="transformShaders",at=@At("RETURN"),require=1,allow=1)
    private void shaderlod$capture(ProgramSource source,AlphaTest alpha,ProgramSet set,CallbackInfoReturnable<Map<PatchShaderType,String>> ci) {
        shaderlod$lastStages=null;shaderlod$lastSource=null;
        boolean packPinned=source.getParent()!=null && MergedLinkageGate.packPinned(source.getParent().getPack());
        MergedLinkageGate.noteOnce("transform source="+source.getName()+" pack="+packPinned+" stages="+ci.getReturnValue().size());
        if(!packPinned || !"gbuffers_terrain".equals(source.getName()))return;
        shaderlod$lastStages=new EnumMap<>(ci.getReturnValue());shaderlod$lastSource=source;
    }
    @Inject(method="createShader",at=@At("HEAD"),require=1,allow=1)
    private void shaderlod$clone(IrisRenderingPipeline pipeline,SodiumPrograms.Pass pass,ProgramSource source,AlphaTest alpha,CustomUniforms custom,
        Supplier<ImmutableSet<Integer>> flip,Map<PatchShaderType,GlShader> nativeShaders,CallbackInfoReturnable<GlProgram<ChunkShaderInterface>> ci) {
        Map<PatchShaderType,String> stages=shaderlod$lastStages;ProgramSource transformed=shaderlod$lastSource;
        shaderlod$lastStages=null;shaderlod$lastSource=null;
        if(pass==SodiumPrograms.Pass.TERRAIN || pass==SodiumPrograms.Pass.TERRAIN_CUTOUT)
            MergedLinkageGate.noteOnce("clone pass="+pass+" source="+source.getName()+" sameCapturedSource="+(source==transformed)+" stages="+(stages==null?-1:stages.size())+" nativeStages="+nativeShaders.size());
        if((pass!=SodiumPrograms.Pass.TERRAIN && pass!=SodiumPrograms.Pass.TERRAIN_CUTOUT) || source!=transformed || stages==null ||
            !shaderlod$twoGraphicsSources(stages) || nativeShaders.size()!=2 || !nativeShaders.containsKey(PatchShaderType.FRAGMENT) || !nativeShaders.containsKey(PatchShaderType.VERTEX))return;
        if(!MergedLinkageGate.trackConstruction(this))return; // Unwrapped construction stays native.
        int vertex=0,program=0;GlProgram<ChunkShaderInterface> derivative=null;
        try {
            String fragmentPin=MergedLinkageGate.sha(stages.get(PatchShaderType.FRAGMENT).getBytes(StandardCharsets.UTF_8));
            String nativeVertex=stages.get(PatchShaderType.VERTEX);
            if(!nativeVertex.equals(GL20C.glGetShaderSource(nativeShaders.get(PatchShaderType.VERTEX).handle()))){MergedLinkageGate.noteOnce("clone actual-native-vertex=false pass="+pass);return;}
            String adapted=MakeupMergedVertexInterface.adapt(nativeVertex,MergedLinkageGate.helper(),MergedLinkageGate.packPinned(source.getParent().getPack()));if(adapted==null){MergedLinkageGate.noteOnce("clone adapt-helper-or-vertex-pin=false pass="+pass);return;}
            // Raw shader lifetime avoids pinned GlShader constructor's leak on compilation failure.
            vertex=GL20C.glCreateShader(GL20C.GL_VERTEX_SHADER);if(vertex==0){MergedLinkageGate.noteOnce("clone create-vertex-zero pass="+pass);return;}
            GL20C.glShaderSource(vertex,adapted);GL20C.glCompileShader(vertex);
            if(GL20C.glGetShaderi(vertex,GL20C.GL_COMPILE_STATUS)==0){MergedLinkageGate.noteOnce("clone vertex-compile=false pass="+pass+" log="+GL20C.glGetShaderInfoLog(vertex));return;}
            GlProgram.Builder builder=GlProgram.builder(ResourceLocation.fromNamespaceAndPath("shaderlod","makeup_merged_"+pass.name().toLowerCase(Locale.ROOT)));
            program=((BuilderHandle)(Object)builder).shaderlod$program();
            GL20C.glAttachShader(program,vertex);builder.attachShader(nativeShaders.get(PatchShaderType.FRAGMENT));
            derivative=buildProgram(builder,pipeline,pass,source,alpha,custom,flip,false);
            int fragment=nativeShaders.get(PatchShaderType.FRAGMENT).handle();
            int[] attached=new int[2],count=new int[1];GL20C.glGetAttachedShaders(program,count,attached);
            if(GL20C.glGetProgrami(program,GL20C.GL_ATTACHED_SHADERS)!=2 || count[0]!=2 ||
                !((attached[0]==vertex&&attached[1]==fragment)||(attached[1]==vertex&&attached[0]==fragment)) ||
                !adapted.equals(GL20C.glGetShaderSource(vertex)) ||
                !fragmentPin.equals(MergedLinkageGate.sha(GL20C.glGetShaderSource(fragment).getBytes(StandardCharsets.UTF_8))) ||
                org.lwjgl.opengl.GL11C.glGetError()!=org.lwjgl.opengl.GL11C.GL_NO_ERROR)
                throw new IllegalStateException("linked derivative source admission rejected");
            // Own this exact current program after its own actual link/source/attachment proof.
            // Native createShader RETURN hooks do not observe buildProgram derivatives.
            if(GL20C.glGetProgrami(program,GL20C.GL_LINK_STATUS)!=org.lwjgl.opengl.GL11C.GL_TRUE)
                throw new IllegalStateException("merged derivative unlinked");
            var pack=source.getParent().getPack();
            com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowRuntime.own(pipeline,pack,source.getName(),program);
            if(!com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowRuntime.ownsDerived(pipeline,pack,program))
                throw new IllegalStateException("merged shadow owner registration rejected");
            if(pass==SodiumPrograms.Pass.TERRAIN_CUTOUT &&
                stages.get(PatchShaderType.FRAGMENT).contains("shaderlodMakeupEarlyAlphaPermission")) {
                if(!com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpAlphaInterface.transformed(stages.get(PatchShaderType.FRAGMENT),alpha))
                    throw new IllegalStateException("merged early-alpha transformed interface rejected");
                com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaRuntime.own(pipeline,pass,source,program);
                if(!com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaRuntime.ownsDerived(pipeline,pack,program))
                    throw new IllegalStateException("merged early-alpha owner registration rejected");
                shaderlod$earlyOwners.add(program);
            }
            System.out.println("[ShaderLOD merged] linked pass="+pass+" program="+program+" vertexSha="+
                MergedLinkageGate.sha(adapted.getBytes(StandardCharsets.UTF_8))+" originalFragmentSha="+fragmentPin);
            shaderlod$merged.put(pass,derivative);derivative=null;program=0;
        }catch(Exception|LinkageError failure) {
            System.out.println("[ShaderLOD merged] derivative unavailable: "+failure.getClass().getSimpleName());
        }finally {
            if(program!=0){shaderlod$earlyOwners.remove(program);com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaRuntime.forgetDerived(program);com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowRuntime.forgetDerived(program);}
            if(derivative!=null)derivative.delete();else if(program!=0)GL20C.glDeleteProgram(program);
            if(vertex!=0)GL20C.glDeleteShader(vertex);
        }
    }
    /** Pinned transform emits five nullable graphic slots; only the two non-null sources are stages. */
    @Unique private static boolean shaderlod$twoGraphicsSources(Map<PatchShaderType,String> stages) {
        if(stages==null || stages.get(PatchShaderType.VERTEX)==null || stages.get(PatchShaderType.FRAGMENT)==null)return false;
        var two=Set.of(PatchShaderType.VERTEX,PatchShaderType.FRAGMENT);
        var five=Set.of(PatchShaderType.VERTEX,PatchShaderType.FRAGMENT,PatchShaderType.GEOMETRY,PatchShaderType.TESS_CONTROL,PatchShaderType.TESS_EVAL);
        if(!stages.keySet().equals(two) && !stages.keySet().equals(five))return false;
        for(var stage:stages.entrySet())if(stage.getKey()!=PatchShaderType.VERTEX && stage.getKey()!=PatchShaderType.FRAGMENT && stage.getValue()!=null)return false;
        return true;
    }
    public GlProgram<ChunkShaderInterface> originalProgram(TerrainRenderPass pass){return shaders.get(mapTerrainRenderPass(pass));}
    public GlProgram<ChunkShaderInterface> mergedProgram(TerrainRenderPass pass){
        if(!MergedLinkageGate.enabled())return null;
        var program=shaderlod$merged.get(mapTerrainRenderPass(pass));if(program==null)return null;
        Object pipeline=net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable();Object pack=net.irisshaders.iris.Iris.getCurrentPack().orElse(null);
        // Epoch/reload/failed-Apply ownership loss must take the untouched native geometry path.
        if(!com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowRuntime.ownsDerived(pipeline,pack,program.handle()) ||
            shaderlod$earlyOwners.contains(program.handle())&&!com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaRuntime.ownsDerived(pipeline,pack,program.handle()))return null;
        return program;
    }
    public void deleteMergedPrograms(){for(var program:shaderlod$merged.values()){MergeUniforms.forget(program.handle());com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaRuntime.forgetDerived(program.handle());com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowRuntime.forgetDerived(program.handle());program.delete();}shaderlod$merged.clear();shaderlod$earlyOwners.clear();}
    @Mixin(value=GlProgram.Builder.class,remap=false)
    public interface BuilderHandle { @Accessor("program") int shaderlod$program(); }
}

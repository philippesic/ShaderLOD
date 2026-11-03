package com.pp.shaderlod.optimization.solasmerged.mixin;
import com.google.common.collect.ImmutableSet;
import com.pp.shaderlod.optimization.solasmerged.SolasMergedVertexInterface;
import com.pp.shaderlod.optimization.solasmerged.*;
import com.pp.shaderlod.optimization.makeupmerged.MergePlan;
import com.pp.shaderlod.optimization.makeupmerged.MergedBuffers;
import com.pp.shaderlod.optimization.makeupmerged.BufferBindings;
import com.pp.shaderlod.optimization.makeupmerged.MergeUniforms;
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
public abstract class SolasMergedProgramsMixin implements SolasMergedProgramAccess {
    @Shadow @Final private EnumMap<SodiumPrograms.Pass,GlProgram<ChunkShaderInterface>> shaders;
    @Shadow private SodiumPrograms.Pass mapTerrainRenderPass(TerrainRenderPass pass){throw new AssertionError();}
    @Shadow private GlProgram<ChunkShaderInterface> buildProgram(GlProgram.Builder builder,IrisRenderingPipeline pipeline,SodiumPrograms.Pass pass,
        ProgramSource source,AlphaTest alpha,CustomUniforms custom,Supplier<ImmutableSet<Integer>> flip,boolean tess){throw new AssertionError();}
    @Unique private final EnumMap<SodiumPrograms.Pass,GlProgram<ChunkShaderInterface>> shaderlodSolas$merged=new EnumMap<>(SodiumPrograms.Pass.class);
    @Unique private final Set<Integer> shaderlodSolas$earlyOwners=new HashSet<>();
    @Unique private Map<PatchShaderType,String> shaderlodSolas$lastStages;
    @Unique private final EnumMap<SodiumPrograms.Pass,Map<PatchShaderType,String>> shaderlodSolas$receiptStages=new EnumMap<>(SodiumPrograms.Pass.class);
    @Unique private ProgramSource shaderlodSolas$lastSource;
    @Unique private final EnumMap<SodiumPrograms.Pass,int[]> shaderlodSolas$receiptIds=new EnumMap<>(SodiumPrograms.Pass.class);
    @Inject(method="transformShaders",at=@At("RETURN"),require=1,allow=1)
    private void shaderlodSolas$capture(ProgramSource source,AlphaTest alpha,ProgramSet set,CallbackInfoReturnable<Map<PatchShaderType,String>> ci) {
        shaderlodSolas$lastStages=null;shaderlodSolas$lastSource=null;
        boolean packPinned=source.getParent()!=null && SolasMergedLinkageGate.packPinned(source.getParent().getPack());
        SolasMergedLinkageGate.noteOnce("transform source="+source.getName()+" pack="+packPinned+" stages="+ci.getReturnValue().size());
        if(!packPinned || !"gbuffers_terrain".equals(source.getName()))return;
        shaderlodSolas$lastStages=new EnumMap<>(ci.getReturnValue());shaderlodSolas$lastSource=source;
    }
    @Inject(method="createShader",at=@At("HEAD"),require=1,allow=1)
    private void shaderlodSolas$clone(IrisRenderingPipeline pipeline,SodiumPrograms.Pass pass,ProgramSource source,AlphaTest alpha,CustomUniforms custom,
        Supplier<ImmutableSet<Integer>> flip,Map<PatchShaderType,GlShader> nativeShaders,CallbackInfoReturnable<GlProgram<ChunkShaderInterface>> ci) {
        Map<PatchShaderType,String> stages=shaderlodSolas$lastStages;ProgramSource transformed=shaderlodSolas$lastSource;
        shaderlodSolas$lastStages=null;shaderlodSolas$lastSource=null;
        if(pass==SodiumPrograms.Pass.TERRAIN || pass==SodiumPrograms.Pass.TERRAIN_CUTOUT)
            SolasMergedLinkageGate.noteOnce("clone pass="+pass+" source="+source.getName()+" sameCapturedSource="+(source==transformed)+" stages="+(stages==null?-1:stages.size())+" nativeStages="+nativeShaders.size());
        if((pass!=SodiumPrograms.Pass.TERRAIN && pass!=SodiumPrograms.Pass.TERRAIN_CUTOUT) || source!=transformed || stages==null ||
            !shaderlodSolas$twoGraphicsSources(stages) || nativeShaders.size()!=2 || !nativeShaders.containsKey(PatchShaderType.FRAGMENT) || !nativeShaders.containsKey(PatchShaderType.VERTEX))return;
        if(!System.getProperty("shaderlod.dev.controlCaptureReceipt","").isBlank()){shaderlodSolas$receiptStages.put(pass,new EnumMap<>(stages));shaderlodSolas$receiptIds.put(pass,new int[]{nativeShaders.get(PatchShaderType.VERTEX).handle(),nativeShaders.get(PatchShaderType.FRAGMENT).handle()});}
        if(!SolasMergedLinkageGate.trackConstruction(this))return; // Unwrapped construction stays native.
        int vertex=0,program=0;GlProgram<ChunkShaderInterface> derivative=null;
        try {
            String fragmentPin=SolasMergedLinkageGate.sha(stages.get(PatchShaderType.FRAGMENT).getBytes(StandardCharsets.UTF_8));
            String nativeVertex=stages.get(PatchShaderType.VERTEX);
            if(!nativeVertex.equals(GL20C.glGetShaderSource(nativeShaders.get(PatchShaderType.VERTEX).handle()))){SolasMergedLinkageGate.noteOnce("clone actual-native-vertex=false pass="+pass);return;}
            String adapted=SolasMergedVertexInterface.adapt(nativeVertex,SolasMergedLinkageGate.helper(),SolasMergedLinkageGate.packPinned(source.getParent().getPack()));if(adapted==null){SolasMergedLinkageGate.noteOnce("clone adapt-helper-or-vertex-pin=false pass="+pass);return;}
            // Raw shader lifetime avoids pinned GlShader constructor's leak on compilation failure.
            vertex=GL20C.glCreateShader(GL20C.GL_VERTEX_SHADER);if(vertex==0){SolasMergedLinkageGate.noteOnce("clone create-vertex-zero pass="+pass);return;}
            GL20C.glShaderSource(vertex,adapted);GL20C.glCompileShader(vertex);
            if(GL20C.glGetShaderi(vertex,GL20C.GL_COMPILE_STATUS)==0){SolasMergedLinkageGate.noteOnce("clone vertex-compile=false pass="+pass+" log="+GL20C.glGetShaderInfoLog(vertex));return;}
            GlProgram.Builder builder=GlProgram.builder(ResourceLocation.fromNamespaceAndPath("shaderlod","solas_merged_"+pass.name().toLowerCase(Locale.ROOT)));
            program=((BuilderHandle)(Object)builder).shaderlodSolas$program();
            GL20C.glAttachShader(program,vertex);builder.attachShader(nativeShaders.get(PatchShaderType.FRAGMENT));
            derivative=buildProgram(builder,pipeline,pass,source,alpha,custom,flip,false);
            int fragment=nativeShaders.get(PatchShaderType.FRAGMENT).handle();
            int[] attached=new int[2],count=new int[1];GL20C.glGetAttachedShaders(program,count,attached);
            if(GL20C.glGetProgrami(program,GL20C.GL_ATTACHED_SHADERS)!=2 || count[0]!=2 ||
                !((attached[0]==vertex&&attached[1]==fragment)||(attached[1]==vertex&&attached[0]==fragment)) ||
                !adapted.equals(GL20C.glGetShaderSource(vertex)) ||
                !fragmentPin.equals(SolasMergedLinkageGate.sha(GL20C.glGetShaderSource(fragment).getBytes(StandardCharsets.UTF_8))) ||
                org.lwjgl.opengl.GL11C.glGetError()!=org.lwjgl.opengl.GL11C.GL_NO_ERROR)
                throw new IllegalStateException("linked derivative source admission rejected");
            // Own this exact current program after its own actual link/source/attachment proof.
            // Native createShader RETURN hooks do not observe buildProgram derivatives.
            if(GL20C.glGetProgrami(program,GL20C.GL_LINK_STATUS)!=org.lwjgl.opengl.GL11C.GL_TRUE)
                throw new IllegalStateException("merged derivative unlinked");
            var pack=source.getParent().getPack();
            if(pass==SodiumPrograms.Pass.TERRAIN_CUTOUT &&
                stages.get(PatchShaderType.FRAGMENT).contains("shaderlodSolasEarlyAlphaPermission")) {
                if(!com.pp.shaderlod.optimization.solasearlyalpha.SolasAlphaInterface.transformed(stages.get(PatchShaderType.FRAGMENT),alpha))
                    throw new IllegalStateException("merged early-alpha transformed interface rejected");
                com.pp.shaderlod.optimization.solasearlyalpha.SolasEarlyAlphaRuntime.own(pipeline,pass,source,program);
                if(!com.pp.shaderlod.optimization.solasearlyalpha.SolasEarlyAlphaRuntime.ownsDerived(pipeline,pack,program))
                    throw new IllegalStateException("merged early-alpha owner registration rejected");
                shaderlodSolas$earlyOwners.add(program);
            }
            System.out.println("[ShaderLOD merged] linked pass="+pass+" program="+program+" vertexSha="+
                SolasMergedLinkageGate.sha(adapted.getBytes(StandardCharsets.UTF_8))+" originalFragmentSha="+fragmentPin);
            shaderlodSolas$merged.put(pass,derivative);derivative=null;program=0;
        }catch(Exception|LinkageError failure) {
            System.out.println("[ShaderLOD merged] derivative unavailable: "+failure.getClass().getSimpleName());
        }finally {
            if(program!=0){shaderlodSolas$earlyOwners.remove(program);com.pp.shaderlod.optimization.solasearlyalpha.SolasEarlyAlphaRuntime.forgetDerived(program);}
            if(derivative!=null)derivative.delete();else if(program!=0)GL20C.glDeleteProgram(program);
            if(vertex!=0)GL20C.glDeleteShader(vertex);
        }
    }
    @Inject(method="createShader",at=@At("RETURN"),require=1,allow=1)
    private void shaderlodSolas$receipt(IrisRenderingPipeline pipeline,SodiumPrograms.Pass pass,ProgramSource source,AlphaTest alpha,CustomUniforms custom,Supplier<ImmutableSet<Integer>> flip,Map<PatchShaderType,GlShader> nativeShaders,CallbackInfoReturnable<GlProgram<ChunkShaderInterface>> ci){
      var stages=shaderlodSolas$receiptStages.remove(pass);var ids=shaderlodSolas$receiptIds.remove(pass);if(ids==null||System.getProperty("shaderlod.dev.controlCaptureReceipt","").isBlank()||stages==null||ci.getReturnValue()==null)return;
      try{int nativeProgram=ci.getReturnValue().handle(),nv=ids[0],nf=ids[1];var derived=shaderlodSolas$merged.get(pass);String actualV=GL20C.glGetShaderSource(nv),actualF=GL20C.glGetShaderSource(nf);int[] d=new int[8],count=new int[1];if(derived!=null)GL20C.glGetAttachedShaders(derived.handle(),count,d);
      System.out.println("[ShaderLOD Solas merged construction] pass="+pass+" nativeProgram="+nativeProgram+" nativeVertex="+nv+" sharedFragment="+nf+" derivedProgram="+(derived==null?0:derived.handle())+" derivedAttachments="+Arrays.toString(Arrays.copyOf(d,count[0]))+" nativeLink="+GL20C.glGetProgrami(nativeProgram,GL20C.GL_LINK_STATUS)+" derivedLink="+(derived==null?0:GL20C.glGetProgrami(derived.handle(),GL20C.GL_LINK_STATUS))+" nativeVSourceEqual="+actualV.equals(stages.get(PatchShaderType.VERTEX))+" nativeFSourceEqual="+actualF.equals(stages.get(PatchShaderType.FRAGMENT))+" vertexSha="+SolasMergedLinkageGate.sha(actualV.getBytes(StandardCharsets.UTF_8))+" fragmentSha="+SolasMergedLinkageGate.sha(actualF.getBytes(StandardCharsets.UTF_8))+" "+SolasMergedLinkageGate.captureIdentity(source.getParent().getPack()));}catch(Exception|LinkageError e){System.out.println("[ShaderLOD Solas merged construction] receiptRefused="+e.getClass().getSimpleName());}
    }
    /** Pinned transform emits five nullable graphic slots; only the two non-null sources are stages. */
    @Unique private static boolean shaderlodSolas$twoGraphicsSources(Map<PatchShaderType,String> stages) {
        if(stages==null || stages.get(PatchShaderType.VERTEX)==null || stages.get(PatchShaderType.FRAGMENT)==null)return false;
        var two=Set.of(PatchShaderType.VERTEX,PatchShaderType.FRAGMENT);
        var five=Set.of(PatchShaderType.VERTEX,PatchShaderType.FRAGMENT,PatchShaderType.GEOMETRY,PatchShaderType.TESS_CONTROL,PatchShaderType.TESS_EVAL);
        if(!stages.keySet().equals(two) && !stages.keySet().equals(five))return false;
        for(var stage:stages.entrySet())if(stage.getKey()!=PatchShaderType.VERTEX && stage.getKey()!=PatchShaderType.FRAGMENT && stage.getValue()!=null)return false;
        return true;
    }
    public GlProgram<ChunkShaderInterface> solasOriginalProgram(TerrainRenderPass pass){return shaders.get(mapTerrainRenderPass(pass));}
    public GlProgram<ChunkShaderInterface> solasMergedProgram(TerrainRenderPass pass){
        if(!SolasMergedLinkageGate.enabled())return null;
        var program=shaderlodSolas$merged.get(mapTerrainRenderPass(pass));if(program==null)return null;
        Object pipeline=net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable();Object pack=net.irisshaders.iris.Iris.getCurrentPack().orElse(null);
        // Epoch/reload/failed-Apply ownership loss must take the untouched native geometry path.
        if(shaderlodSolas$earlyOwners.contains(program.handle())&&!com.pp.shaderlod.optimization.solasearlyalpha.SolasEarlyAlphaRuntime.ownsDerived(pipeline,pack,program.handle()))return null;
        return program;
    }
    public void solasDeleteMergedPrograms(){SolasMergedLinkageGate.invalidateSourceCache();for(var program:shaderlodSolas$merged.values()){MergeUniforms.forget(program.handle());com.pp.shaderlod.optimization.solasearlyalpha.SolasEarlyAlphaRuntime.forgetDerived(program.handle());program.delete();}shaderlodSolas$merged.clear();shaderlodSolas$earlyOwners.clear();shaderlodSolas$receiptStages.clear();shaderlodSolas$receiptIds.clear();}
    @Mixin(value=GlProgram.Builder.class,remap=false)
    public interface BuilderHandle { @Accessor("program") int shaderlodSolas$program(); }
}

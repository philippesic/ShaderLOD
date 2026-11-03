package com.pp.shaderlod.optimization.sildurmerged.mixin;
import com.google.common.collect.ImmutableSet;
import com.pp.shaderlod.optimization.sildurmerged.SildurMergedVertexInterface;
import com.pp.shaderlod.optimization.sildurmerged.*;
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
public abstract class SildurMergedProgramsMixin implements SildurMergedProgramAccess {
    @Shadow @Final private EnumMap<SodiumPrograms.Pass,GlProgram<ChunkShaderInterface>> shaders;
    @Shadow private SodiumPrograms.Pass mapTerrainRenderPass(TerrainRenderPass pass){throw new AssertionError();}
    @Shadow private GlProgram<ChunkShaderInterface> buildProgram(GlProgram.Builder builder,IrisRenderingPipeline pipeline,SodiumPrograms.Pass pass,
        ProgramSource source,AlphaTest alpha,CustomUniforms custom,Supplier<ImmutableSet<Integer>> flip,boolean tess){throw new AssertionError();}
    @Unique private final EnumMap<SodiumPrograms.Pass,GlProgram<ChunkShaderInterface>> shaderlodSildurMerged$merged=new EnumMap<>(SodiumPrograms.Pass.class);
    @Unique private final Set<Integer> shaderlodSildurMerged$earlyOwners=new HashSet<>();
    @Unique private Map<PatchShaderType,String> shaderlodSildurMerged$lastStages;
    @Unique private final EnumMap<SodiumPrograms.Pass,Map<PatchShaderType,String>> shaderlodSildurMerged$receiptStages=new EnumMap<>(SodiumPrograms.Pass.class);
    @Unique private ProgramSource shaderlodSildurMerged$lastSource;
    @Unique private final EnumMap<SodiumPrograms.Pass,int[]> shaderlodSildurMerged$receiptIds=new EnumMap<>(SodiumPrograms.Pass.class);
    @Inject(method="transformShaders",at=@At("RETURN"),require=1,allow=1)
    private void shaderlodSildurMerged$capture(ProgramSource source,AlphaTest alpha,ProgramSet set,CallbackInfoReturnable<Map<PatchShaderType,String>> ci) {
        shaderlodSildurMerged$lastStages=null;shaderlodSildurMerged$lastSource=null;
        boolean packPinned=source.getParent()!=null && SildurMergedLinkageGate.packPinned(source.getParent().getPack());
        SildurMergedLinkageGate.noteOnce("transform source="+source.getName()+" pack="+packPinned+" stages="+ci.getReturnValue().size());
        if(!packPinned || !"gbuffers_textured".equals(source.getName()))return;
        shaderlodSildurMerged$lastStages=new EnumMap<>(ci.getReturnValue());shaderlodSildurMerged$lastSource=source;
    }
    @Inject(method="createShader",at=@At("HEAD"),require=1,allow=1)
    private void shaderlodSildurMerged$clone(IrisRenderingPipeline pipeline,SodiumPrograms.Pass pass,ProgramSource source,AlphaTest alpha,CustomUniforms custom,
        Supplier<ImmutableSet<Integer>> flip,Map<PatchShaderType,GlShader> nativeShaders,CallbackInfoReturnable<GlProgram<ChunkShaderInterface>> ci) {
        Map<PatchShaderType,String> stages=shaderlodSildurMerged$lastStages;ProgramSource transformed=shaderlodSildurMerged$lastSource;
        shaderlodSildurMerged$lastStages=null;shaderlodSildurMerged$lastSource=null;
        if(pass==SodiumPrograms.Pass.TERRAIN || pass==SodiumPrograms.Pass.TERRAIN_CUTOUT)
            SildurMergedLinkageGate.noteOnce("clone pass="+pass+" source="+source.getName()+" sameCapturedSource="+(source==transformed)+" stages="+(stages==null?-1:stages.size())+" nativeStages="+nativeShaders.size());
        if((pass!=SodiumPrograms.Pass.TERRAIN && pass!=SodiumPrograms.Pass.TERRAIN_CUTOUT) || source!=transformed || stages==null ||
            !shaderlodSildurMerged$twoGraphicsSources(stages) || nativeShaders.size()!=2 || !nativeShaders.containsKey(PatchShaderType.FRAGMENT) || !nativeShaders.containsKey(PatchShaderType.VERTEX))return;
        if(!System.getProperty("shaderlod.dev.controlCaptureReceipt","").isBlank()){shaderlodSildurMerged$receiptStages.put(pass,new EnumMap<>(stages));shaderlodSildurMerged$receiptIds.put(pass,new int[]{nativeShaders.get(PatchShaderType.VERTEX).handle(),nativeShaders.get(PatchShaderType.FRAGMENT).handle()});}
        if(!SildurMergedLinkageGate.trackConstruction(this))return; // Unwrapped construction stays native.
        int vertex=0,program=0;GlProgram<ChunkShaderInterface> derivative=null;
        try {
            String fragmentPin=SildurMergedLinkageGate.sha(stages.get(PatchShaderType.FRAGMENT).getBytes(StandardCharsets.UTF_8));
            String nativeVertex=stages.get(PatchShaderType.VERTEX);
            if(!nativeVertex.equals(GL20C.glGetShaderSource(nativeShaders.get(PatchShaderType.VERTEX).handle()))){SildurMergedLinkageGate.noteOnce("clone actual-native-vertex=false pass="+pass);return;}
            String adapted=SildurMergedVertexInterface.adapt(nativeVertex,SildurMergedLinkageGate.helper(),SildurMergedLinkageGate.packPinned(source.getParent().getPack()));if(adapted==null){SildurMergedLinkageGate.noteOnce("clone adapt-helper-or-vertex-pin=false pass="+pass);return;}
            // Raw shader lifetime avoids pinned GlShader constructor's leak on compilation failure.
            vertex=GL20C.glCreateShader(GL20C.GL_VERTEX_SHADER);if(vertex==0){SildurMergedLinkageGate.noteOnce("clone create-vertex-zero pass="+pass);return;}
            GL20C.glShaderSource(vertex,adapted);GL20C.glCompileShader(vertex);
            if(GL20C.glGetShaderi(vertex,GL20C.GL_COMPILE_STATUS)==0){SildurMergedLinkageGate.noteOnce("clone vertex-compile=false pass="+pass+" log="+GL20C.glGetShaderInfoLog(vertex));return;}
            GlProgram.Builder builder=GlProgram.builder(ResourceLocation.fromNamespaceAndPath("shaderlod","sildur_merged_"+pass.name().toLowerCase(Locale.ROOT)));
            program=((BuilderHandle)(Object)builder).shaderlodSildurMerged$program();
            GL20C.glAttachShader(program,vertex);builder.attachShader(nativeShaders.get(PatchShaderType.FRAGMENT));
            derivative=buildProgram(builder,pipeline,pass,source,alpha,custom,flip,false);
            int fragment=nativeShaders.get(PatchShaderType.FRAGMENT).handle();
            int[] attached=new int[2],count=new int[1];GL20C.glGetAttachedShaders(program,count,attached);
            if(GL20C.glGetProgrami(program,GL20C.GL_ATTACHED_SHADERS)!=2 || count[0]!=2 ||
                !((attached[0]==vertex&&attached[1]==fragment)||(attached[1]==vertex&&attached[0]==fragment)) ||
                !adapted.equals(GL20C.glGetShaderSource(vertex)) ||
                !fragmentPin.equals(SildurMergedLinkageGate.sha(GL20C.glGetShaderSource(fragment).getBytes(StandardCharsets.UTF_8))) ||
                org.lwjgl.opengl.GL11C.glGetError()!=org.lwjgl.opengl.GL11C.GL_NO_ERROR)
                throw new IllegalStateException("linked derivative source admission rejected");
            // Own this exact current program after its own actual link/source/attachment proof.
            // Native createShader RETURN hooks do not observe buildProgram derivatives.
            if(GL20C.glGetProgrami(program,GL20C.GL_LINK_STATUS)!=org.lwjgl.opengl.GL11C.GL_TRUE)
                throw new IllegalStateException("merged derivative unlinked");
            var pack=source.getParent().getPack();
            com.pp.shaderlod.sildurprefix.SildurPrefixRuntime.built(pipeline,pass,source,derivative);
            if(pass==SodiumPrograms.Pass.TERRAIN_CUTOUT &&
                stages.get(PatchShaderType.FRAGMENT).contains("shaderlodSildurEarlyAlphaPermission")) {
                if(!com.pp.shaderlod.optimization.sildurearlyalpha.SildurAlphaInterface.transformed(stages.get(PatchShaderType.FRAGMENT),alpha))
                    throw new IllegalStateException("merged early-alpha transformed interface rejected");
                com.pp.shaderlod.optimization.sildurearlyalpha.SildurEarlyAlphaRuntime.own(pipeline,pass,source,program);
                if(!com.pp.shaderlod.optimization.sildurearlyalpha.SildurEarlyAlphaRuntime.ownsDerived(pipeline,pack,program))
                    throw new IllegalStateException("merged early-alpha owner registration rejected");
                shaderlodSildurMerged$earlyOwners.add(program);
            }
            System.out.println("[ShaderLOD merged] linked pass="+pass+" program="+program+" vertexSha="+
                SildurMergedLinkageGate.sha(adapted.getBytes(StandardCharsets.UTF_8))+" originalFragmentSha="+fragmentPin);
            shaderlodSildurMerged$merged.put(pass,derivative);derivative=null;program=0;
        }catch(Exception|LinkageError failure) {
            System.out.println("[ShaderLOD merged] derivative unavailable: "+failure.getClass().getSimpleName());
        }finally {
            if(program!=0){shaderlodSildurMerged$earlyOwners.remove(program);com.pp.shaderlod.optimization.sildurearlyalpha.SildurEarlyAlphaRuntime.forgetDerived(program);}
            if(derivative!=null)derivative.delete();else if(program!=0)GL20C.glDeleteProgram(program);
            if(vertex!=0)GL20C.glDeleteShader(vertex);
        }
    }
    @Inject(method="createShader",at=@At("RETURN"),require=1,allow=1)
    private void shaderlodSildurMerged$receipt(IrisRenderingPipeline pipeline,SodiumPrograms.Pass pass,ProgramSource source,AlphaTest alpha,CustomUniforms custom,Supplier<ImmutableSet<Integer>> flip,Map<PatchShaderType,GlShader> nativeShaders,CallbackInfoReturnable<GlProgram<ChunkShaderInterface>> ci){
      var stages=shaderlodSildurMerged$receiptStages.remove(pass);var ids=shaderlodSildurMerged$receiptIds.remove(pass);if(ids==null||System.getProperty("shaderlod.dev.controlCaptureReceipt","").isBlank()||stages==null||ci.getReturnValue()==null)return;
      try{int nativeProgram=ci.getReturnValue().handle(),nv=ids[0],nf=ids[1];var derived=shaderlodSildurMerged$merged.get(pass);String actualV=GL20C.glGetShaderSource(nv),actualF=GL20C.glGetShaderSource(nf);int[] d=new int[8],count=new int[1];if(derived!=null)GL20C.glGetAttachedShaders(derived.handle(),count,d);
      System.out.println("[ShaderLOD Sildur merged construction] pass="+pass+" nativeProgram="+nativeProgram+" nativeVertex="+nv+" sharedFragment="+nf+" derivedProgram="+(derived==null?0:derived.handle())+" derivedAttachments="+Arrays.toString(Arrays.copyOf(d,count[0]))+" nativeLink="+GL20C.glGetProgrami(nativeProgram,GL20C.GL_LINK_STATUS)+" derivedLink="+(derived==null?0:GL20C.glGetProgrami(derived.handle(),GL20C.GL_LINK_STATUS))+" nativeVSourceEqual="+actualV.equals(stages.get(PatchShaderType.VERTEX))+" nativeFSourceEqual="+actualF.equals(stages.get(PatchShaderType.FRAGMENT))+" vertexSha="+SildurMergedLinkageGate.sha(actualV.getBytes(StandardCharsets.UTF_8))+" fragmentSha="+SildurMergedLinkageGate.sha(actualF.getBytes(StandardCharsets.UTF_8))+" "+SildurMergedLinkageGate.captureIdentity(source.getParent().getPack()));}catch(Exception|LinkageError e){System.out.println("[ShaderLOD Sildur merged construction] receiptRefused="+e.getClass().getSimpleName());}
    }
    /** Pinned transform emits five nullable graphic slots; only the two non-null sources are stages. */
    @Unique private static boolean shaderlodSildurMerged$twoGraphicsSources(Map<PatchShaderType,String> stages) {
        if(stages==null || stages.get(PatchShaderType.VERTEX)==null || stages.get(PatchShaderType.FRAGMENT)==null)return false;
        var two=Set.of(PatchShaderType.VERTEX,PatchShaderType.FRAGMENT);
        var five=Set.of(PatchShaderType.VERTEX,PatchShaderType.FRAGMENT,PatchShaderType.GEOMETRY,PatchShaderType.TESS_CONTROL,PatchShaderType.TESS_EVAL);
        if(!stages.keySet().equals(two) && !stages.keySet().equals(five))return false;
        for(var stage:stages.entrySet())if(stage.getKey()!=PatchShaderType.VERTEX && stage.getKey()!=PatchShaderType.FRAGMENT && stage.getValue()!=null)return false;
        return true;
    }
    public GlProgram<ChunkShaderInterface> sildurOriginalProgram(TerrainRenderPass pass){return shaders.get(mapTerrainRenderPass(pass));}
    public GlProgram<ChunkShaderInterface> sildurMergedProgram(TerrainRenderPass pass){
        if(!SildurMergedLinkageGate.enabled())return null;
        var program=shaderlodSildurMerged$merged.get(mapTerrainRenderPass(pass));if(program==null)return null;
        Object pipeline=net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable();Object pack=net.irisshaders.iris.Iris.getCurrentPack().orElse(null);
        // Epoch/reload/failed-Apply ownership loss must take the untouched native geometry path.
        if(shaderlodSildurMerged$earlyOwners.contains(program.handle())&&!com.pp.shaderlod.optimization.sildurearlyalpha.SildurEarlyAlphaRuntime.ownsDerived(pipeline,pack,program.handle()))return null;
        return program;
    }
    public void sildurDeleteMergedPrograms(){SildurMergedLinkageGate.invalidateSourceCache();for(var program:shaderlodSildurMerged$merged.values()){MergeUniforms.forget(program.handle());com.pp.shaderlod.optimization.sildurearlyalpha.SildurEarlyAlphaRuntime.forgetDerived(program.handle());program.delete();}shaderlodSildurMerged$merged.clear();shaderlodSildurMerged$earlyOwners.clear();shaderlodSildurMerged$receiptStages.clear();shaderlodSildurMerged$receiptIds.clear();}
    @Mixin(value=GlProgram.Builder.class,remap=false)
    public interface BuilderHandle { @Accessor("program") int shaderlodSildurMerged$program(); }
}

package com.pp.shaderlod.optimization.reimaginedprime.mixin;
import com.google.common.collect.ImmutableSet;
import com.pp.shaderlod.optimization.reimaginedprime.ReimaginedDepthPriming;
import com.pp.shaderlod.optimization.reimaginedprime.DepthProgramAccess;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.gl.shader.GlShader;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderType;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTests;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Shares the already invariant-qualified native vertex object before native stage deletion. */
@Mixin(value=SodiumPrograms.class,remap=false)
public abstract class DepthSodiumProgramsMixin implements DepthProgramAccess {
 @Unique private final EnumMap<SodiumPrograms.Pass,GlProgram<ChunkShaderInterface>> shaderlod$reimagineddepth = new EnumMap<>(SodiumPrograms.Pass.class);
 @Unique private final Object shaderlod$reimaginedDepthCleanup=new Object();
 @Shadow private GlProgram<ChunkShaderInterface> buildProgram(GlProgram.Builder b,IrisRenderingPipeline p,SodiumPrograms.Pass pass,ProgramSource source,AlphaTest alpha,CustomUniforms custom,Supplier<ImmutableSet<Integer>> flips,boolean tess){throw new AssertionError();}
 @Inject(method="createShader",at=@At("HEAD"),require=1)
 private void shaderlod$reimaginedbuildDepth(IrisRenderingPipeline p,SodiumPrograms.Pass pass,ProgramSource source,AlphaTest alpha,CustomUniforms custom,Supplier<ImmutableSet<Integer>> flips,Map<PatchShaderType,GlShader> stages,CallbackInfoReturnable<GlProgram<ChunkShaderInterface>> ci) {
  if (!ReimaginedDepthPriming.candidatePack() || (pass!=SodiumPrograms.Pass.TERRAIN && pass!=SodiumPrograms.Pass.TERRAIN_CUTOUT)) return;
  if(!com.pp.shaderlod.optimization.makeupmerged.MergedLinkageGate.trackConstructionCleanup(shaderlod$reimaginedDepthCleanup,this::shaderlod$reimagineddeleteDepthPrograms))return;
  if (stages.size()!=2 || !stages.containsKey(PatchShaderType.VERTEX) || !stages.containsKey(PatchShaderType.FRAGMENT)) return;
  if(source.getDirectives().getBlendModeOverride().isPresent() || !source.getDirectives().getBufferBlendOverrides().isEmpty())return;
  if(!ReimaginedDepthPriming.invariantStage(org.lwjgl.opengl.GL20C.glGetShaderSource(stages.get(PatchShaderType.VERTEX).handle()),true) ||
     !ReimaginedDepthPriming.invariantStage(org.lwjgl.opengl.GL20C.glGetShaderSource(stages.get(PatchShaderType.FRAGMENT).handle()),false))return;
  boolean cutout=pass==SodiumPrograms.Pass.TERRAIN_CUTOUT;
  if (!(cutout?AlphaTests.ONE_TENTH_ALPHA:AlphaTest.ALWAYS).equals(alpha)) return;
  var name=ResourceLocation.fromNamespaceAndPath("shaderlod","reimagined_depth_stone_"+pass.name().toLowerCase(java.util.Locale.ROOT));
  int fragment=0,program=0;
  try {
   fragment=org.lwjgl.opengl.GL20C.glCreateShader(org.lwjgl.opengl.GL20C.GL_FRAGMENT_SHADER);if(fragment==0)return;
   org.lwjgl.opengl.GL20C.glShaderSource(fragment,ReimaginedDepthPriming.fragment(cutout));org.lwjgl.opengl.GL20C.glCompileShader(fragment);
   if(org.lwjgl.opengl.GL20C.glGetShaderi(fragment,org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS)==0)return;
   var builder=GlProgram.builder(name).attachShader(stages.get(PatchShaderType.VERTEX));
   program=((DepthBuilderAccess)(Object)builder).shaderlod$reimaginedprogramId();org.lwjgl.opengl.GL20C.glAttachShader(program,fragment);
   var linked=buildProgram(builder,p,pass,source,alpha,custom,flips,false);
   if(org.lwjgl.opengl.GL20C.glGetProgrami(program,org.lwjgl.opengl.GL20C.GL_LINK_STATUS)!=org.lwjgl.opengl.GL11C.GL_TRUE)throw new IllegalStateException("coverage link rejected");
   int[] attached=new int[2],count=new int[1];org.lwjgl.opengl.GL20C.glGetAttachedShaders(program,count,attached);int vertex=stages.get(PatchShaderType.VERTEX).handle();
   if(count[0]!=2||!((attached[0]==vertex&&attached[1]==fragment)||(attached[1]==vertex&&attached[0]==fragment)))throw new IllegalStateException("coverage native vertex ownership rejected");
   var previous=shaderlod$reimagineddepth.put(pass,linked);program=0;if(previous!=null)previous.delete();
  }catch(RuntimeException|LinkageError failed){ /* No submission; native colour construction continues. */ }
  finally{if(program!=0)org.lwjgl.opengl.GL20C.glDeleteProgram(program);if(fragment!=0)org.lwjgl.opengl.GL20C.glDeleteShader(fragment);}


 }
 @Override public GlProgram<ChunkShaderInterface> shaderlod$reimagineddepthProgram(TerrainRenderPass pass) {
  return shaderlod$reimagineddepth.get(pass==DefaultTerrainRenderPasses.SOLID?SodiumPrograms.Pass.TERRAIN:pass==DefaultTerrainRenderPasses.CUTOUT?SodiumPrograms.Pass.TERRAIN_CUTOUT:null);
 }
 @Override public void shaderlod$reimagineddeleteDepthPrograms(){shaderlod$reimagineddepth.values().forEach(GlProgram::delete);shaderlod$reimagineddepth.clear();}
}

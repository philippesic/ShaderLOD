package com.pp.shaderlod.optimization.makeupprime.mixin;
import com.google.common.collect.ImmutableSet;
import com.pp.shaderlod.optimization.makeupprime.MakeUpDepthPriming;
import com.pp.shaderlod.optimization.makeupprime.DepthProgramAccess;
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
 @Unique private final EnumMap<SodiumPrograms.Pass,GlProgram<ChunkShaderInterface>> shaderlod$makeupdepthdepth = new EnumMap<>(SodiumPrograms.Pass.class);
 @Unique private final Object shaderlod$makeupdepthCleanupOwner=new Object();
 @Shadow private GlProgram<ChunkShaderInterface> buildProgram(GlProgram.Builder b,IrisRenderingPipeline p,SodiumPrograms.Pass pass,ProgramSource source,AlphaTest alpha,CustomUniforms custom,Supplier<ImmutableSet<Integer>> flips,boolean tess){throw new AssertionError();}
 @Inject(method="createShader",at=@At("HEAD"),require=1)
 private void shaderlod$makeupdepthbuildDepth(IrisRenderingPipeline p,SodiumPrograms.Pass pass,ProgramSource source,AlphaTest alpha,CustomUniforms custom,Supplier<ImmutableSet<Integer>> flips,Map<PatchShaderType,GlShader> stages,CallbackInfoReturnable<GlProgram<ChunkShaderInterface>> ci) {
  if (!MakeUpDepthPriming.candidatePack() || pass!=SodiumPrograms.Pass.TERRAIN) return;
  if(source==null || source.getParent()==null || source.getParent().getPack()!=net.irisshaders.iris.Iris.getCurrentPack().orElse(null) || !"gbuffers_terrain".equals(source.getName()))return;
  if (stages.size()!=2 || !stages.containsKey(PatchShaderType.VERTEX) || !stages.containsKey(PatchShaderType.FRAGMENT)) return;
  if(source.getDirectives().getBlendModeOverride().isPresent() || !source.getDirectives().getBufferBlendOverrides().isEmpty())return;
  if(!MakeUpDepthPriming.invariantStage(org.lwjgl.opengl.GL20C.glGetShaderSource(stages.get(PatchShaderType.VERTEX).handle()),true) ||
     !MakeUpDepthPriming.invariantStage(org.lwjgl.opengl.GL20C.glGetShaderSource(stages.get(PatchShaderType.FRAGMENT).handle()),false))return;
  boolean cutout=false;
  if (!AlphaTest.ALWAYS.equals(alpha)) return;
  if(!com.pp.shaderlod.optimization.makeupmerged.MergedLinkageGate.trackConstructionCleanup(shaderlod$makeupdepthCleanupOwner,this::shaderlod$makeupdepthdeleteDepthPrograms))return;
  var name=ResourceLocation.fromNamespaceAndPath("shaderlod","makeup_depth_opaque_"+pass.name().toLowerCase(java.util.Locale.ROOT));
  int fragment=0,program=0;GlProgram<ChunkShaderInterface> derivative=null;Throwable failure=null;boolean cleanupFailed=false;
  try {
   // Raw allocation keeps the shader deletable even if its compiler throws.
   fragment=org.lwjgl.opengl.GL20C.glCreateShader(org.lwjgl.opengl.GL20C.GL_FRAGMENT_SHADER);if(fragment==0)return;
   String coverage=MakeUpDepthPriming.fragment(false);org.lwjgl.opengl.GL20C.glShaderSource(fragment,coverage);org.lwjgl.opengl.GL20C.glCompileShader(fragment);
   if(org.lwjgl.opengl.GL20C.glGetShaderi(fragment,org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS)==0)return;
   GlProgram.Builder builder=GlProgram.builder(name);program=((DepthBuilderAccess)(Object)builder).shaderlod$makeupdepthprogramId();
   builder.attachShader(stages.get(PatchShaderType.VERTEX));org.lwjgl.opengl.GL20C.glAttachShader(program,fragment);
   derivative=buildProgram(builder,p,pass,source,alpha,custom,flips,false);
   int[] attached=new int[2],count=new int[1];org.lwjgl.opengl.GL20C.glGetAttachedShaders(program,count,attached);int vertex=stages.get(PatchShaderType.VERTEX).handle();
   if(org.lwjgl.opengl.GL20C.glGetProgrami(program,org.lwjgl.opengl.GL20C.GL_LINK_STATUS)!=org.lwjgl.opengl.GL11C.GL_TRUE || count[0]!=2 || org.lwjgl.opengl.GL20C.glGetProgrami(program,org.lwjgl.opengl.GL20C.GL_ATTACHED_SHADERS)!=2 ||
      !((attached[0]==vertex&&attached[1]==fragment)||(attached[1]==vertex&&attached[0]==fragment)) || !coverage.equals(org.lwjgl.opengl.GL20C.glGetShaderSource(fragment)))throw new IllegalStateException("MAKEUP depth linked-stage ownership rejected");
   MakeUpDepthPriming.assertNoGlError("MAKEUP depth link");
   var previous=shaderlod$makeupdepthdepth.put(pass,derivative);derivative=null;program=0;if(previous!=null)previous.delete();
  }catch(RuntimeException|Error failed){failure=failed;}
  finally{
   try{if(derivative!=null)derivative.delete();else if(program!=0)org.lwjgl.opengl.GL20C.glDeleteProgram(program);}catch(RuntimeException|Error secondary){cleanupFailed=true;if(failure==null)failure=secondary;else if(failure!=secondary)try{failure.addSuppressed(secondary);}catch(Throwable unavailable){}}
   try{if(fragment!=0)org.lwjgl.opengl.GL20C.glDeleteShader(fragment);}catch(RuntimeException|Error secondary){cleanupFailed=true;if(failure==null)failure=secondary;else if(failure!=secondary)try{failure.addSuppressed(secondary);}catch(Throwable unavailable){}}
   if(failure!=null){MakeUpDepthPriming.disable();if(cleanupFailed || !(failure instanceof RuntimeException || failure instanceof LinkageError)){if(failure instanceof Error fatal)throw fatal;throw (RuntimeException)failure;}}
  }


 }
 @Override public GlProgram<ChunkShaderInterface> shaderlod$makeupdepthdepthProgram(TerrainRenderPass pass) {
  return shaderlod$makeupdepthdepth.get(pass==DefaultTerrainRenderPasses.SOLID?SodiumPrograms.Pass.TERRAIN:pass==DefaultTerrainRenderPasses.CUTOUT?SodiumPrograms.Pass.TERRAIN_CUTOUT:null);
 }
 @Override public void shaderlod$makeupdepthdeleteDepthPrograms(){var owned=new java.util.ArrayList<>(shaderlod$makeupdepthdepth.values());shaderlod$makeupdepthdepth.clear();Throwable failure=null;for(var value:owned)try{value.delete();}catch(RuntimeException|Error e){if(failure==null)failure=e;else if(failure!=e)failure.addSuppressed(e);}if(failure instanceof RuntimeException e)throw e;if(failure instanceof Error e)throw e;}
}

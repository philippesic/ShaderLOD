package com.pp.shaderlod.optimization.makeupspatial.mixin;
import com.google.common.collect.ImmutableSet;
import com.pp.shaderlod.optimization.makeupspatial.MakeUpSpatialRuntime;
import com.pp.shaderlod.optimization.makeupspatial.MakeUpSpatialPrograms;
import com.pp.shaderlod.optimization.makeupspatial.SpatialProgramAccess;
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
public abstract class SpatialSodiumProgramsMixin implements SpatialProgramAccess {
 @Unique private final EnumMap<SodiumPrograms.Pass,GlProgram<ChunkShaderInterface>> shaderlod$makeupspatialdepth = new EnumMap<>(SodiumPrograms.Pass.class);
 @Unique private GlProgram<ChunkShaderInterface> shaderlod$makeupRate;
 @Unique private final Object shaderlod$makeupspatialCleanupOwner=new Object();
 @Shadow private GlProgram<ChunkShaderInterface> buildProgram(GlProgram.Builder b,IrisRenderingPipeline p,SodiumPrograms.Pass pass,ProgramSource source,AlphaTest alpha,CustomUniforms custom,Supplier<ImmutableSet<Integer>> flips,boolean tess){throw new AssertionError();}
 @Inject(method="createShader",at=@At("HEAD"),require=1)
 private void shaderlod$makeupspatialbuildDepth(IrisRenderingPipeline p,SodiumPrograms.Pass pass,ProgramSource source,AlphaTest alpha,CustomUniforms custom,Supplier<ImmutableSet<Integer>> flips,Map<PatchShaderType,GlShader> stages,CallbackInfoReturnable<GlProgram<ChunkShaderInterface>> ci) {
  if (!MakeUpSpatialRuntime.candidatePack() || pass!=SodiumPrograms.Pass.TERRAIN) return;MakeUpSpatialRuntime.factory(false);
  if(source==null || source.getParent()==null || source.getParent().getPack()!=net.irisshaders.iris.Iris.getCurrentPack().orElse(null) || !"gbuffers_terrain".equals(source.getName()))return;
  if (stages.size()!=2 || !stages.containsKey(PatchShaderType.VERTEX) || !stages.containsKey(PatchShaderType.FRAGMENT)) return;
  if(source.getDirectives().getBlendModeOverride().isPresent() || !source.getDirectives().getBufferBlendOverrides().isEmpty())return;
  if(!com.pp.shaderlod.optimization.makeupprime.MakeUpDepthPriming.invariantStage(org.lwjgl.opengl.GL20C.glGetShaderSource(stages.get(PatchShaderType.VERTEX).handle()),true) ||
     !com.pp.shaderlod.optimization.makeupprime.MakeUpDepthPriming.invariantStage(org.lwjgl.opengl.GL20C.glGetShaderSource(stages.get(PatchShaderType.FRAGMENT).handle()),false))return;
  boolean cutout=false;
  if (!AlphaTest.ALWAYS.equals(alpha)) return;
  if(!com.pp.shaderlod.optimization.makeupmerged.MergedLinkageGate.trackConstructionCleanup(shaderlod$makeupspatialCleanupOwner,this::shaderlod$deleteSpatialProgram))return;
  var name=ResourceLocation.fromNamespaceAndPath("shaderlod","makeup_spatial_input_light_"+pass.name().toLowerCase(java.util.Locale.ROOT));
  int fragment=0,program=0;GlProgram<ChunkShaderInterface> derivative=null;Throwable failure=null;boolean cleanupFailed=false;
  try {
   // Raw allocation keeps the shader deletable even if its compiler throws.
   fragment=org.lwjgl.opengl.GL20C.glCreateShader(org.lwjgl.opengl.GL20C.GL_FRAGMENT_SHADER);if(fragment==0){MakeUpSpatialRuntime.refuse("native derived fragment allocation refused");return;}
   String coverage=MakeUpSpatialPrograms.fragment(org.lwjgl.opengl.GL20C.glGetShaderSource(stages.get(PatchShaderType.FRAGMENT).handle()));org.lwjgl.opengl.GL20C.glShaderSource(fragment,coverage);org.lwjgl.opengl.GL20C.glCompileShader(fragment);
   if(org.lwjgl.opengl.GL20C.glGetShaderi(fragment,org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS)==0){MakeUpSpatialRuntime.refuse("native derived fragment compile refused: "+org.lwjgl.opengl.GL20C.glGetShaderInfoLog(fragment));return;}
   GlProgram.Builder builder=GlProgram.builder(name);program=((SpatialBuilderAccess)(Object)builder).shaderlod$spatialProgramId();
   builder.attachShader(stages.get(PatchShaderType.VERTEX));org.lwjgl.opengl.GL20C.glAttachShader(program,fragment);
   derivative=buildProgram(builder,p,pass,source,alpha,custom,flips,false);
   int[] attached=new int[2],count=new int[1];org.lwjgl.opengl.GL20C.glGetAttachedShaders(program,count,attached);int vertex=stages.get(PatchShaderType.VERTEX).handle();
   if(org.lwjgl.opengl.GL20C.glGetProgrami(program,org.lwjgl.opengl.GL20C.GL_LINK_STATUS)!=org.lwjgl.opengl.GL11C.GL_TRUE || count[0]!=2 || org.lwjgl.opengl.GL20C.glGetProgrami(program,org.lwjgl.opengl.GL20C.GL_ATTACHED_SHADERS)!=2 ||
      !((attached[0]==vertex&&attached[1]==fragment)||(attached[1]==vertex&&attached[0]==fragment)) || !coverage.equals(org.lwjgl.opengl.GL20C.glGetShaderSource(fragment)))throw new IllegalStateException("MAKEUP depth linked-stage ownership rejected");
   for(int output=0;output<(MakeUpSpatialRuntime.worldMode()?8:6);output++)if(org.lwjgl.opengl.GL30C.glGetFragDataLocation(program,"iris_FragData"+output)!=output)throw new IllegalStateException("native derived output mapping refused");
   com.pp.shaderlod.optimization.makeupprime.MakeUpDepthPriming.assertNoGlError("MAKEUP spatial link");
   com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowRuntime.own(p,source.getParent().getPack(),source.getName(),program);var previous=shaderlod$makeupspatialdepth.put(pass,derivative);derivative=null;program=0;MakeUpSpatialRuntime.factory(true);if(previous!=null){com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowRuntime.forgetDerived(previous.handle());previous.delete();}if(MakeUpSpatialRuntime.reducedMode())shaderlod$makeupRate=shaderlod$buildRate(p,pass,source,alpha,custom,flips,org.lwjgl.opengl.GL20C.glGetShaderSource(stages.get(PatchShaderType.FRAGMENT).handle()));
  }catch(RuntimeException|Error failed){failure=failed;}
  finally{
   try{if(derivative!=null)derivative.delete();else if(program!=0)org.lwjgl.opengl.GL20C.glDeleteProgram(program);}catch(RuntimeException|Error secondary){cleanupFailed=true;if(failure==null)failure=secondary;else if(failure!=secondary)try{failure.addSuppressed(secondary);}catch(Throwable unavailable){}}
   try{if(fragment!=0)org.lwjgl.opengl.GL20C.glDeleteShader(fragment);}catch(RuntimeException|Error secondary){cleanupFailed=true;if(failure==null)failure=secondary;else if(failure!=secondary)try{failure.addSuppressed(secondary);}catch(Throwable unavailable){}}
   if(failure!=null){MakeUpSpatialRuntime.refuse("native derived construction refused: "+failure);if(cleanupFailed || !(failure instanceof RuntimeException || failure instanceof LinkageError)){if(failure instanceof Error fatal)throw fatal;throw (RuntimeException)failure;}}
  }


 }
 @Unique private GlProgram<ChunkShaderInterface> shaderlod$buildRate(IrisRenderingPipeline pipe,SodiumPrograms.Pass pass,ProgramSource source,AlphaTest alpha,CustomUniforms custom,Supplier<ImmutableSet<Integer>> flips,String nativeFragment){
  int vertex=0,fragment=0,program=0;GlProgram<ChunkShaderInterface> result=null;
  try{vertex=org.lwjgl.opengl.GL20C.glCreateShader(org.lwjgl.opengl.GL20C.GL_VERTEX_SHADER);fragment=org.lwjgl.opengl.GL20C.glCreateShader(org.lwjgl.opengl.GL20C.GL_FRAGMENT_SHADER);if(vertex==0||fragment==0)throw new IllegalStateException("rate stage allocation refused");String v=MakeUpSpatialPrograms.rateVertex(),f=MakeUpSpatialRuntime.worldMode()?com.pp.shaderlod.optimization.makeupspatial.MakeUpWorldPixelPrograms.fragment(nativeFragment):MakeUpSpatialPrograms.rateFragment(nativeFragment);org.lwjgl.opengl.GL20C.glShaderSource(vertex,v);org.lwjgl.opengl.GL20C.glShaderSource(fragment,f);org.lwjgl.opengl.GL20C.glCompileShader(vertex);org.lwjgl.opengl.GL20C.glCompileShader(fragment);for(int shader:new int[]{vertex,fragment})if(org.lwjgl.opengl.GL20C.glGetShaderi(shader,org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS)==0)throw new IllegalStateException("rate native closure compile: "+org.lwjgl.opengl.GL20C.glGetShaderInfoLog(shader));
   var builder=GlProgram.builder(ResourceLocation.fromNamespaceAndPath("shaderlod",MakeUpSpatialRuntime.worldMode()?"makeup_world_pixel_native_rgb":"makeup_spatial_rate_native_light"));program=((SpatialBuilderAccess)(Object)builder).shaderlod$spatialProgramId();org.lwjgl.opengl.GL20C.glAttachShader(program,vertex);org.lwjgl.opengl.GL20C.glAttachShader(program,fragment);result=buildProgram(builder,pipe,pass,source,alpha,custom,flips,false);int[] attached=new int[2],count=new int[1];org.lwjgl.opengl.GL20C.glGetAttachedShaders(program,count,attached);
   int linked=org.lwjgl.opengl.GL20C.glGetProgrami(program,org.lwjgl.opengl.GL20C.GL_LINK_STATUS),output=org.lwjgl.opengl.GL30C.glGetFragDataLocation(program,"iris_FragData0");
   boolean vertexExact=v.equals(org.lwjgl.opengl.GL20C.glGetShaderSource(vertex)),fragmentExact=f.equals(org.lwjgl.opengl.GL20C.glGetShaderSource(fragment)),owned=(attached[0]==vertex&&attached[1]==fragment)||(attached[0]==fragment&&attached[1]==vertex);
   if(count[0]!=2||linked!=1||output!=0||!vertexExact||!fragmentExact||!owned)throw new IllegalStateException("native rate linked owner/output/source refused: attached="+count[0]+", linked="+linked+", output="+output+", vertexExact="+vertexExact+", fragmentExact="+fragmentExact+", owned="+owned);
   com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowRuntime.own(pipe,source.getParent().getPack(),source.getName(),program);var returned=result;result=null;program=0;return returned;
  }finally{try{if(result!=null)result.delete();else if(program!=0)org.lwjgl.opengl.GL20C.glDeleteProgram(program);}finally{try{if(vertex!=0)org.lwjgl.opengl.GL20C.glDeleteShader(vertex);}finally{if(fragment!=0)org.lwjgl.opengl.GL20C.glDeleteShader(fragment);}}}
 }
 @Override public GlProgram<ChunkShaderInterface> shaderlod$rateProgram(){return shaderlod$makeupRate;}
 @Override public GlProgram<ChunkShaderInterface> shaderlod$spatialProgram() {return shaderlod$makeupspatialdepth.get(SodiumPrograms.Pass.TERRAIN);}
 @Override public void shaderlod$deleteSpatialProgram(){var owned=new java.util.ArrayList<>(shaderlod$makeupspatialdepth.values());if(shaderlod$makeupRate!=null){owned.add(shaderlod$makeupRate);shaderlod$makeupRate=null;}shaderlod$makeupspatialdepth.clear();Throwable failure=null;for(var value:owned)try{com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowRuntime.forgetDerived(value.handle());value.delete();}catch(RuntimeException|Error e){if(failure==null)failure=e;else if(failure!=e)failure.addSuppressed(e);}if(failure instanceof RuntimeException e)throw e;if(failure instanceof Error e)throw e;}
}

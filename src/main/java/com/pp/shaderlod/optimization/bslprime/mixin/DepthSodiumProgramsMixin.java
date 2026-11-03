package com.pp.shaderlod.optimization.bslprime.mixin;
import com.google.common.collect.ImmutableSet;
import com.pp.shaderlod.optimization.bslprime.BslDepthPriming;
import com.pp.shaderlod.optimization.bslprime.DepthProgramAccess;
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
 @Unique private final EnumMap<SodiumPrograms.Pass,GlProgram<ChunkShaderInterface>> shaderlod$bsldepthdepth = new EnumMap<>(SodiumPrograms.Pass.class);
 @Unique private final Object shaderlod$bsldepthCleanupOwner=new Object();

 @org.spongepowered.asm.mixin.injection.ModifyArgs(method="transformShaders",order=900,at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pipeline/transform/TransformPatcher;patchSodium(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/gl/blending/AlphaTest;Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;)Ljava/util/Map;"),require=1,allow=1)
 private void shaderlod$bslDepthPrepare(org.spongepowered.asm.mixin.injection.invoke.arg.Args args,
   @com.llamalad7.mixinextras.sugar.Local(argsOnly=true) ProgramSource source,
   @com.llamalad7.mixinextras.sugar.Local(argsOnly=true) net.irisshaders.iris.shaderpack.programs.ProgramSet set) {
  BslDepthPriming.COMPILED.session(this).prepare(source,set,args.get(6),args.get(1),args.get(2),args.get(3),args.get(4),args.get(5),args.get(7),BslDepthPriming.sourceEpoch(),BslDepthPriming::candidatePack);
 }
 @org.spongepowered.asm.mixin.injection.ModifyArgs(method="transformShaders",order=1100,at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pipeline/transform/TransformPatcher;patchSodium(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/gl/blending/AlphaTest;Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;)Ljava/util/Map;"),require=1,allow=1)
 private void shaderlod$bslDepthConsumedArguments(org.spongepowered.asm.mixin.injection.invoke.arg.Args args,
   @com.llamalad7.mixinextras.sugar.Local(argsOnly=true) ProgramSource source,
   @com.llamalad7.mixinextras.sugar.Local(argsOnly=true) net.irisshaders.iris.shaderpack.programs.ProgramSet set) {
  BslDepthPriming.COMPILED.session(this).arguments(source,set,args.get(6),args.get(1),args.get(2),args.get(3),args.get(4),args.get(5),args.get(7));
 }
 @Inject(method="transformShaders",at=@At("RETURN"),require=1)
 private void shaderlod$bslDepthTransformed(ProgramSource source,AlphaTest alpha,net.irisshaders.iris.shaderpack.programs.ProgramSet set,CallbackInfoReturnable<Map<PatchShaderType,String>> ci) {
  BslDepthPriming.COMPILED.session(this).transformed(source,set,alpha,ci.getReturnValue());
 }
 @Inject(method="createGlShaders",at=@At("RETURN"),require=1)
 private void shaderlod$bslDepthCompiled(String name,Map<PatchShaderType,String> input,CallbackInfoReturnable<Map<PatchShaderType,GlShader>> ci) {
  BslDepthPriming.COMPILED.session(this).compiled(input,ci.getReturnValue());
 }
 @Inject(method="createShader",at=@At("RETURN"),require=1)
 private void shaderlod$bslDepthNativeLinked(IrisRenderingPipeline p,SodiumPrograms.Pass pass,ProgramSource source,AlphaTest alpha,CustomUniforms custom,Supplier<ImmutableSet<Integer>> flips,Map<PatchShaderType,GlShader> stages,CallbackInfoReturnable<GlProgram<ChunkShaderInterface>> ci) {
  BslDepthPriming.COMPILED.session(this).own(p,this,pass,source,alpha,stages,ci.getReturnValue(),shaderlod$bsldepthdepth.get(pass),BslDepthPriming.sourceEpoch());
 }
 @Shadow private GlProgram<ChunkShaderInterface> buildProgram(GlProgram.Builder b,IrisRenderingPipeline p,SodiumPrograms.Pass pass,ProgramSource source,AlphaTest alpha,CustomUniforms custom,Supplier<ImmutableSet<Integer>> flips,boolean tess){throw new AssertionError();}
 @Inject(method="createShader",at=@At("HEAD"),require=1)
 private void shaderlod$bsldepthbuildDepth(IrisRenderingPipeline p,SodiumPrograms.Pass pass,ProgramSource source,AlphaTest alpha,CustomUniforms custom,Supplier<ImmutableSet<Integer>> flips,Map<PatchShaderType,GlShader> stages,CallbackInfoReturnable<GlProgram<ChunkShaderInterface>> ci) {
  if (!BslDepthPriming.COMPILED.session(this).prepared(source,alpha,stages,BslDepthPriming.sourceEpoch()) || pass!=SodiumPrograms.Pass.TERRAIN) return;
  if(source==null || source.getParent()==null || source.getParent().getPack()!=net.irisshaders.iris.Iris.getCurrentPack().orElse(null) || !"gbuffers_terrain".equals(source.getName()))return;
  if (stages.size()!=2 || !stages.containsKey(PatchShaderType.VERTEX) || !stages.containsKey(PatchShaderType.FRAGMENT)) return;
  if(source.getDirectives().getBlendModeOverride().isPresent() || !source.getDirectives().getBufferBlendOverrides().isEmpty())return;
  if(!BslDepthPriming.invariantStage(org.lwjgl.opengl.GL20C.glGetShaderSource(stages.get(PatchShaderType.VERTEX).handle()),true) ||
     !BslDepthPriming.invariantStage(org.lwjgl.opengl.GL20C.glGetShaderSource(stages.get(PatchShaderType.FRAGMENT).handle()),false))return;
  boolean cutout=false;
  if (!AlphaTest.ALWAYS.equals(alpha)) return;
  if(!com.pp.shaderlod.optimization.makeupmerged.MergedLinkageGate.trackConstructionCleanup(shaderlod$bsldepthCleanupOwner,this::shaderlod$bsldepthdeleteDepthPrograms))return;
  var name=ResourceLocation.fromNamespaceAndPath("shaderlod","bsl_depth_opaque_"+pass.name().toLowerCase(java.util.Locale.ROOT));
  int fragment=0,program=0;GlProgram<ChunkShaderInterface> derivative=null;Throwable failure=null;boolean cleanupFailed=false;
  try {
   // Raw allocation keeps the shader deletable even if its compiler throws.
   fragment=org.lwjgl.opengl.GL20C.glCreateShader(org.lwjgl.opengl.GL20C.GL_FRAGMENT_SHADER);if(fragment==0)return;
   String coverage=BslDepthPriming.fragment(false);org.lwjgl.opengl.GL20C.glShaderSource(fragment,coverage);org.lwjgl.opengl.GL20C.glCompileShader(fragment);
   if(org.lwjgl.opengl.GL20C.glGetShaderi(fragment,org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS)==0)return;
   GlProgram.Builder builder=GlProgram.builder(name);program=((DepthBuilderAccess)(Object)builder).shaderlod$bsldepthprogramId();
   builder.attachShader(stages.get(PatchShaderType.VERTEX));org.lwjgl.opengl.GL20C.glAttachShader(program,fragment);
   derivative=buildProgram(builder,p,pass,source,alpha,custom,flips,false);
   int[] attached=new int[2],count=new int[1];org.lwjgl.opengl.GL20C.glGetAttachedShaders(program,count,attached);int vertex=stages.get(PatchShaderType.VERTEX).handle();
   if(org.lwjgl.opengl.GL20C.glGetProgrami(program,org.lwjgl.opengl.GL20C.GL_LINK_STATUS)!=org.lwjgl.opengl.GL11C.GL_TRUE || count[0]!=2 || org.lwjgl.opengl.GL20C.glGetProgrami(program,org.lwjgl.opengl.GL20C.GL_ATTACHED_SHADERS)!=2 ||
      !((attached[0]==vertex&&attached[1]==fragment)||(attached[1]==vertex&&attached[0]==fragment)) || !coverage.equals(org.lwjgl.opengl.GL20C.glGetShaderSource(fragment)))throw new IllegalStateException("BSL depth linked-stage ownership rejected");
   BslDepthPriming.assertNoGlError("BSL depth link");
   BslDepthPriming.COMPILED.session(this).derived(derivative,source,alpha,stages);
   var previous=shaderlod$bsldepthdepth.put(pass,derivative);derivative=null;program=0;if(previous!=null)previous.delete();
  }catch(RuntimeException|Error failed){failure=failed;}
  finally{
   try{if(derivative!=null)derivative.delete();else if(program!=0)org.lwjgl.opengl.GL20C.glDeleteProgram(program);}catch(RuntimeException|Error secondary){cleanupFailed=true;if(failure==null)failure=secondary;else if(failure!=secondary)try{failure.addSuppressed(secondary);}catch(Throwable unavailable){}}
   try{if(fragment!=0)org.lwjgl.opengl.GL20C.glDeleteShader(fragment);}catch(RuntimeException|Error secondary){cleanupFailed=true;if(failure==null)failure=secondary;else if(failure!=secondary)try{failure.addSuppressed(secondary);}catch(Throwable unavailable){}}
   if(failure!=null){BslDepthPriming.disable();if(cleanupFailed || !(failure instanceof RuntimeException || failure instanceof LinkageError)){if(failure instanceof Error fatal)throw fatal;throw (RuntimeException)failure;}}
  }


 }
 @Override public GlProgram<ChunkShaderInterface> shaderlod$bsldepthdepthProgram(TerrainRenderPass pass) {
  var nativeProgram=((SodiumPrograms)(Object)this).getProgram(pass);
  var depthProgram=shaderlod$bsldepthdepth.get(pass==DefaultTerrainRenderPasses.SOLID?SodiumPrograms.Pass.TERRAIN:pass==DefaultTerrainRenderPasses.CUTOUT?SodiumPrograms.Pass.TERRAIN_CUTOUT:null);
  if(!BslDepthPriming.COMPILED.currentOwner(this,pass,nativeProgram,depthProgram,BslDepthPriming.sourceEpoch()))return null;
  return shaderlod$bsldepthdepth.get(pass==DefaultTerrainRenderPasses.SOLID?SodiumPrograms.Pass.TERRAIN:pass==DefaultTerrainRenderPasses.CUTOUT?SodiumPrograms.Pass.TERRAIN_CUTOUT:null);
 }
 @Override public void shaderlod$bsldepthdeleteDepthPrograms(){BslDepthPriming.COMPILED.release(this);var owned=new java.util.ArrayList<>(shaderlod$bsldepthdepth.values());shaderlod$bsldepthdepth.clear();Throwable failure=null;for(var value:owned)try{value.delete();}catch(RuntimeException|Error e){if(failure==null)failure=e;else if(failure!=e)failure.addSuppressed(e);}if(failure instanceof RuntimeException e)throw e;if(failure instanceof Error e)throw e;}
}

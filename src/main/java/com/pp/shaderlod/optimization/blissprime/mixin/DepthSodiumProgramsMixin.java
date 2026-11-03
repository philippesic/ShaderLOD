package com.pp.shaderlod.optimization.blissprime.mixin;
import com.google.common.collect.ImmutableSet;
import com.pp.shaderlod.optimization.blissprime.BlissDepthPriming;
import com.pp.shaderlod.optimization.blissprime.DepthProgramAccess;
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
 @Unique private final EnumMap<SodiumPrograms.Pass,GlProgram<ChunkShaderInterface>> shaderlod$blissdepth = new EnumMap<>(SodiumPrograms.Pass.class);
 @Unique private final Object shaderlod$blissDepthCleanup=new Object();

 @org.spongepowered.asm.mixin.injection.ModifyArgs(method="transformShaders",order=900,at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pipeline/transform/TransformPatcher;patchSodium(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/gl/blending/AlphaTest;Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;)Ljava/util/Map;"),require=1,allow=1)
 private void shaderlod$blissDepthPrepare(org.spongepowered.asm.mixin.injection.invoke.arg.Args args,
   @com.llamalad7.mixinextras.sugar.Local(argsOnly=true) ProgramSource source,
   @com.llamalad7.mixinextras.sugar.Local(argsOnly=true) net.irisshaders.iris.shaderpack.programs.ProgramSet set) {
  BlissDepthPriming.COMPILED.session(this).prepare(source,set,args.get(6),args.get(1),args.get(2),args.get(3),args.get(4),args.get(5),args.get(7),BlissDepthPriming.sourceEpoch(),BlissDepthPriming::candidatePack);
 }
 @org.spongepowered.asm.mixin.injection.ModifyArgs(method="transformShaders",order=1100,at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pipeline/transform/TransformPatcher;patchSodium(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/gl/blending/AlphaTest;Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;)Ljava/util/Map;"),require=1,allow=1)
 private void shaderlod$blissDepthConsumedArguments(org.spongepowered.asm.mixin.injection.invoke.arg.Args args,
   @com.llamalad7.mixinextras.sugar.Local(argsOnly=true) ProgramSource source,
   @com.llamalad7.mixinextras.sugar.Local(argsOnly=true) net.irisshaders.iris.shaderpack.programs.ProgramSet set) {
  BlissDepthPriming.COMPILED.session(this).arguments(source,set,args.get(6),args.get(1),args.get(2),args.get(3),args.get(4),args.get(5),args.get(7));
 }
 @Inject(method="transformShaders",at=@At("RETURN"),require=1)
 private void shaderlod$blissDepthTransformed(ProgramSource source,AlphaTest alpha,net.irisshaders.iris.shaderpack.programs.ProgramSet set,CallbackInfoReturnable<Map<PatchShaderType,String>> ci) {
  BlissDepthPriming.COMPILED.session(this).transformed(source,set,alpha,ci.getReturnValue());
 }
 @Inject(method="createGlShaders",at=@At("RETURN"),require=1)
 private void shaderlod$blissDepthCompiled(String name,Map<PatchShaderType,String> input,CallbackInfoReturnable<Map<PatchShaderType,GlShader>> ci) {
  BlissDepthPriming.COMPILED.session(this).compiled(input,ci.getReturnValue());
 }
 @Inject(method="createShader",at=@At("RETURN"),require=1)
 private void shaderlod$blissDepthNativeLinked(IrisRenderingPipeline p,SodiumPrograms.Pass pass,ProgramSource source,AlphaTest alpha,CustomUniforms custom,Supplier<ImmutableSet<Integer>> flips,Map<PatchShaderType,GlShader> stages,CallbackInfoReturnable<GlProgram<ChunkShaderInterface>> ci) {
  BlissDepthPriming.COMPILED.session(this).own(p,this,pass,source,alpha,stages,ci.getReturnValue(),shaderlod$blissdepth.get(pass),BlissDepthPriming.sourceEpoch());
 }
 @Shadow private GlProgram<ChunkShaderInterface> buildProgram(GlProgram.Builder b,IrisRenderingPipeline p,SodiumPrograms.Pass pass,ProgramSource source,AlphaTest alpha,CustomUniforms custom,Supplier<ImmutableSet<Integer>> flips,boolean tess){throw new AssertionError();}
 @Inject(method="createShader",at=@At("HEAD"),require=1)
 private void shaderlod$blissbuildDepth(IrisRenderingPipeline p,SodiumPrograms.Pass pass,ProgramSource source,AlphaTest alpha,CustomUniforms custom,Supplier<ImmutableSet<Integer>> flips,Map<PatchShaderType,GlShader> stages,CallbackInfoReturnable<GlProgram<ChunkShaderInterface>> ci) {
  if (!BlissDepthPriming.COMPILED.session(this).prepared(source,alpha,stages,BlissDepthPriming.sourceEpoch()) || pass!=SodiumPrograms.Pass.TERRAIN) return;
  if(!com.pp.shaderlod.optimization.makeupmerged.MergedLinkageGate.trackConstructionCleanup(shaderlod$blissDepthCleanup,this::shaderlod$blissdeleteDepthPrograms))return;
  if (stages.size()!=2 || !stages.containsKey(PatchShaderType.VERTEX) || !stages.containsKey(PatchShaderType.FRAGMENT)) return;
  if(!BlissDepthPriming.blendContract(source.getDirectives().getBlendModeOverride(),source.getDirectives().getBufferBlendOverrides()))return;
  if(!BlissDepthPriming.invariantStage(org.lwjgl.opengl.GL20C.glGetShaderSource(stages.get(PatchShaderType.VERTEX).handle()),true) ||
     !BlissDepthPriming.invariantStage(org.lwjgl.opengl.GL20C.glGetShaderSource(stages.get(PatchShaderType.FRAGMENT).handle()),false))return;
  boolean cutout=pass==SodiumPrograms.Pass.TERRAIN_CUTOUT;
  if (!AlphaTests.ONE_TENTH_ALPHA.equals(alpha)) return;
  var name=ResourceLocation.fromNamespaceAndPath("shaderlod","bliss_depth_slabs_"+pass.name().toLowerCase(java.util.Locale.ROOT));
  int fragment=0,program=0;
  try {
   fragment=org.lwjgl.opengl.GL20C.glCreateShader(org.lwjgl.opengl.GL20C.GL_FRAGMENT_SHADER);if(fragment==0)return;
   org.lwjgl.opengl.GL20C.glShaderSource(fragment,BlissDepthPriming.fragment(cutout));org.lwjgl.opengl.GL20C.glCompileShader(fragment);
   if(org.lwjgl.opengl.GL20C.glGetShaderi(fragment,org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS)==0)return;
   var builder=GlProgram.builder(name).attachShader(stages.get(PatchShaderType.VERTEX));
   program=((DepthBuilderAccess)(Object)builder).shaderlod$blissprogramId();org.lwjgl.opengl.GL20C.glAttachShader(program,fragment);
   var linked=buildProgram(builder,p,pass,source,alpha,custom,flips,false);
   if(org.lwjgl.opengl.GL20C.glGetProgrami(program,org.lwjgl.opengl.GL20C.GL_LINK_STATUS)!=org.lwjgl.opengl.GL11C.GL_TRUE)throw new IllegalStateException("coverage link rejected");
   int[] attached=new int[2],count=new int[1];org.lwjgl.opengl.GL20C.glGetAttachedShaders(program,count,attached);int vertex=stages.get(PatchShaderType.VERTEX).handle();
   if(count[0]!=2||!((attached[0]==vertex&&attached[1]==fragment)||(attached[1]==vertex&&attached[0]==fragment)))throw new IllegalStateException("coverage native vertex ownership rejected");
   BlissDepthPriming.COMPILED.session(this).derived(linked,source,alpha,stages);
   var previous=shaderlod$blissdepth.put(pass,linked);program=0;if(previous!=null)previous.delete();
  }catch(RuntimeException|LinkageError failed){ /* No submission; native colour construction continues. */ }
  finally{if(program!=0)org.lwjgl.opengl.GL20C.glDeleteProgram(program);if(fragment!=0)org.lwjgl.opengl.GL20C.glDeleteShader(fragment);}


 }
 @Override public GlProgram<ChunkShaderInterface> shaderlod$blissdepthProgram(TerrainRenderPass pass) {
  var nativeProgram=((SodiumPrograms)(Object)this).getProgram(pass);
  var depthProgram=shaderlod$blissdepth.get(pass==DefaultTerrainRenderPasses.SOLID?SodiumPrograms.Pass.TERRAIN:pass==DefaultTerrainRenderPasses.CUTOUT?SodiumPrograms.Pass.TERRAIN_CUTOUT:null);
  if(!BlissDepthPriming.COMPILED.currentOwner(this,pass,nativeProgram,depthProgram,BlissDepthPriming.sourceEpoch()))return null;
  return shaderlod$blissdepth.get(pass==DefaultTerrainRenderPasses.SOLID?SodiumPrograms.Pass.TERRAIN:pass==DefaultTerrainRenderPasses.CUTOUT?SodiumPrograms.Pass.TERRAIN_CUTOUT:null);
 }
 @Override public void shaderlod$blissdeleteDepthPrograms(){BlissDepthPriming.COMPILED.release(this);shaderlod$blissdepth.values().forEach(GlProgram::delete);shaderlod$blissdepth.clear();}
}

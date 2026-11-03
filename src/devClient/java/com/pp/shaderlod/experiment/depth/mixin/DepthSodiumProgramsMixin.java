package com.pp.shaderlod.experiment.depth.mixin;
import com.google.common.collect.ImmutableSet;
import com.pp.shaderlod.experiment.depth.DepthPrimingR2;
import com.pp.shaderlod.experiment.depth.DepthProgramAccess;
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
 @Unique private final EnumMap<SodiumPrograms.Pass,GlProgram<ChunkShaderInterface>> shaderlod$depth = new EnumMap<>(SodiumPrograms.Pass.class);
 @Shadow private GlProgram<ChunkShaderInterface> buildProgram(GlProgram.Builder b,IrisRenderingPipeline p,SodiumPrograms.Pass pass,ProgramSource source,AlphaTest alpha,CustomUniforms custom,Supplier<ImmutableSet<Integer>> flips,boolean tess){throw new AssertionError();}
 @Inject(method="createShader",at=@At("HEAD"),require=1)
 private void shaderlod$buildDepth(IrisRenderingPipeline p,SodiumPrograms.Pass pass,ProgramSource source,AlphaTest alpha,CustomUniforms custom,Supplier<ImmutableSet<Integer>> flips,Map<PatchShaderType,GlShader> stages,CallbackInfoReturnable<GlProgram<ChunkShaderInterface>> ci) {
  if (!DepthPrimingR2.candidatePack() || (pass!=SodiumPrograms.Pass.TERRAIN && pass!=SodiumPrograms.Pass.TERRAIN_CUTOUT)) return;
  if (stages.size()!=2 || !stages.containsKey(PatchShaderType.VERTEX) || !stages.containsKey(PatchShaderType.FRAGMENT)) return;
  if(source.getDirectives().getBlendModeOverride().isPresent() || !source.getDirectives().getBufferBlendOverrides().isEmpty())return;
  if(!DepthPrimingR2.invariantStage(org.lwjgl.opengl.GL20C.glGetShaderSource(stages.get(PatchShaderType.VERTEX).handle()),true) ||
     !DepthPrimingR2.invariantStage(org.lwjgl.opengl.GL20C.glGetShaderSource(stages.get(PatchShaderType.FRAGMENT).handle()),false))return;
  boolean cutout=pass==SodiumPrograms.Pass.TERRAIN_CUTOUT;
  if (!(cutout?AlphaTests.ONE_TENTH_ALPHA:AlphaTest.ALWAYS).equals(alpha)) return;
  var name=ResourceLocation.fromNamespaceAndPath("shaderlod_dev","unbound_depth_r2_"+pass.name().toLowerCase(java.util.Locale.ROOT));
  GlShader depth = new GlShader(ShaderType.FRAGMENT,name,DepthPrimingR2.fragment(cutout));
  var builder=GlProgram.builder(name).attachShader(stages.get(PatchShaderType.VERTEX)).attachShader(depth);
  try {
   shaderlod$depth.put(pass,buildProgram(builder,p,pass,source,alpha,custom,flips,false));
  } catch(RuntimeException|Error failed){org.lwjgl.opengl.GL20C.glDeleteProgram(((DepthBuilderAccess)(Object)builder).shaderlod$programId());throw failed;}
  finally { depth.delete(); }
 }
 @Override public GlProgram<ChunkShaderInterface> shaderlod$depthProgram(TerrainRenderPass pass) {
  return shaderlod$depth.get(pass==DefaultTerrainRenderPasses.SOLID?SodiumPrograms.Pass.TERRAIN:pass==DefaultTerrainRenderPasses.CUTOUT?SodiumPrograms.Pass.TERRAIN_CUTOUT:null);
 }
 @Override public void shaderlod$deleteDepthPrograms(){shaderlod$depth.values().forEach(GlProgram::delete);shaderlod$depth.clear();}
}

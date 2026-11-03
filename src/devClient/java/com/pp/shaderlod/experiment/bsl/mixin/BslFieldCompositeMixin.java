package com.pp.shaderlod.experiment.bsl.mixin;
import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.pp.shaderlod.experiment.bsl.BslShadowFieldBridge;
import com.pp.shaderlod.experiment.bsl.BslFieldCullScope;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.uniform.DynamicUniformHolder;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shadows.ShadowCompositeRenderer;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=ShadowCompositeRenderer.class,remap=false)
public abstract class BslFieldCompositeMixin {
 @Shadow @Final private WorldRenderingPipeline pipeline;
 @Unique private boolean shaderlod$bslFieldPass;
 @Unique private Program shaderlod$bslFieldProgram;
 @WrapOperation(method="createProgram",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/uniforms/CommonUniforms;addDynamicUniforms(Lnet/irisshaders/iris/gl/uniform/DynamicUniformHolder;Lnet/irisshaders/iris/gl/state/FogMode;)V"),require=1)
 private void register(DynamicUniformHolder holder,FogMode fog,Operation<Void> original,
   ProgramSource source,ImmutableSet<Integer> reads,ImmutableSet<Integer> flipped,ShadowRenderTargets targets) {
  original.call(holder,fog);
  BslShadowFieldBridge.registerProducer(holder,pipeline,source,targets);
 }
 @Inject(method="createProgram",at=@At("RETURN"),require=1)
 private void built(ProgramSource source,ImmutableSet<Integer> reads,ImmutableSet<Integer> flipped,ShadowRenderTargets targets,CallbackInfoReturnable<Program> ci) {
  BslShadowFieldBridge.producerBuilt(pipeline,source,ci.getReturnValue());
 }
 @WrapOperation(method="renderAll()V",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/gl/program/Program;use()V"),require=1)
 private void use(Program program,Operation<Void> original) {
  shaderlod$bslFieldPass=BslShadowFieldBridge.isProducer(pipeline,program);
  shaderlod$bslFieldProgram=shaderlod$bslFieldPass?program:null;
  try {original.call(program);}
  catch(RuntimeException|Error failure){shaderlod$bslFieldPass=false;shaderlod$bslFieldProgram=null;throw failure;}
 }
 @WrapOperation(method="renderAll()V",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V"),require=1)
 private void quad(FullScreenQuadRenderer renderer,Operation<Void> original) {
  try {
   BslFieldCullScope.run(shaderlod$bslFieldPass,
     ()->GL11.glIsEnabled(GL11.GL_CULL_FACE),RenderSystem::disableCull,RenderSystem::enableCull,
     ()->BslShadowFieldBridge.afterProducerUse(pipeline,shaderlod$bslFieldProgram),
     ()->original.call(renderer),()->BslShadowFieldBridge.fieldReturned(pipeline));
  } finally {shaderlod$bslFieldPass=false;shaderlod$bslFieldProgram=null;}
 }
}

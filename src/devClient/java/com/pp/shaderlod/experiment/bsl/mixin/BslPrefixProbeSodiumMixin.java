package com.pp.shaderlod.experiment.bsl.mixin;
import com.pp.shaderlod.experiment.bsl.BslPrefixBinding;
import com.pp.shaderlod.experiment.bsl.BslPrefixOriginalProbe;
import net.irisshaders.iris.pipeline.programs.SodiumShader;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.gl.uniform.DynamicUniformHolder;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=SodiumShader.class,remap=false)
public abstract class BslPrefixProbeSodiumMixin {
 @WrapOperation(method="buildUniforms",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/uniforms/CommonUniforms;addDynamicUniforms(Lnet/irisshaders/iris/gl/uniform/DynamicUniformHolder;Lnet/irisshaders/iris/gl/state/FogMode;)V"),require=1,allow=1)
 private void uniforms(DynamicUniformHolder holder,FogMode fog,Operation<Void> original,SodiumPrograms.Pass pass,int program,CustomUniforms custom){
  original.call(holder,fog);
  if(BslPrefixOriginalProbe.requested()&&(pass==SodiumPrograms.Pass.TERRAIN||pass==SodiumPrograms.Pass.TERRAIN_CUTOUT))BslPrefixBinding.register(holder);
 }
 @Inject(method="setupState()V",at=@At("RETURN"),require=1)
 private void ready(CallbackInfo ci){BslPrefixOriginalProbe.ready(this);}
}

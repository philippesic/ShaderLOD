package com.pp.shaderlod.optimization.makeupao.mixin;
import com.pp.shaderlod.optimization.makeupao.MakeUpAoRuntime;import net.irisshaders.iris.pipeline.WorldRenderingPipeline;import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="net.irisshaders.iris.pipeline.PipelineManager",remap=false)
public abstract class MakeUpAoPrepareMixin {
 @Inject(method="preparePipeline(Lnet/irisshaders/iris/shaderpack/materialmap/NamespacedId;)Lnet/irisshaders/iris/pipeline/WorldRenderingPipeline;",at=@At("HEAD"),require=1,cancellable=true)
 private void shaderlod$nativeBoundary(NamespacedId d,CallbackInfoReturnable<WorldRenderingPipeline> ci){var p=MakeUpAoRuntime.beforePrepare(d);if(p!=null)ci.setReturnValue(p);}
}

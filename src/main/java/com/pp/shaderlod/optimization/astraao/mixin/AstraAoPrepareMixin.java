package com.pp.shaderlod.optimization.astraao.mixin;
import com.pp.shaderlod.optimization.astraao.AstraAoRuntime;import net.irisshaders.iris.pipeline.WorldRenderingPipeline;import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="net.irisshaders.iris.pipeline.PipelineManager",remap=false)
public abstract class AstraAoPrepareMixin {
 @Inject(method="preparePipeline(Lnet/irisshaders/iris/shaderpack/materialmap/NamespacedId;)Lnet/irisshaders/iris/pipeline/WorldRenderingPipeline;",at=@At("HEAD"),require=1,cancellable=true)
 private void shaderlod$nativeBoundary(NamespacedId d,CallbackInfoReturnable<WorldRenderingPipeline> ci){var p=AstraAoRuntime.beforePrepare(d);if(p!=null)ci.setReturnValue(p);}
}

package com.pp.shaderlod.mixin;
import com.pp.shaderlod.optimization.bsl.BslAoNormalBridge;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Reload native0 only before Iris' actual pipeline prepare/capture. */
@Mixin(value=PipelineManager.class,remap=false,priority=1100)
public abstract class BslAoCaptureMixin {
 @Inject(method="preparePipeline(Lnet/irisshaders/iris/shaderpack/materialmap/NamespacedId;)Lnet/irisshaders/iris/pipeline/WorldRenderingPipeline;",at=@At("HEAD"),require=1,allow=1)
 private void capture(NamespacedId dimension,CallbackInfoReturnable<WorldRenderingPipeline> ci){BslAoNormalBridge.beforeCapture(dimension);}
}

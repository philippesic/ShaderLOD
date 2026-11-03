package com.pp.shaderlod.optimization.photon.mixin;
import com.pp.shaderlod.optimization.photon.PhotonSpatialNormal;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Reload native0 only before Iris' actual pipeline prepare/capture. */
@Mixin(value=PipelineManager.class,remap=false,priority=1100)
public abstract class PhotonSpatialCaptureMixin {
 @Inject(method="preparePipeline(Lnet/irisshaders/iris/shaderpack/materialmap/NamespacedId;)Lnet/irisshaders/iris/pipeline/WorldRenderingPipeline;",at=@At("HEAD"),require=1,allow=1)
 private void capture(NamespacedId dimension,CallbackInfoReturnable<WorldRenderingPipeline> ci){PhotonSpatialNormal.beforeCapture(dimension.getNamespace()+":"+dimension.getName());}
 @Inject(method="preparePipeline(Lnet/irisshaders/iris/shaderpack/materialmap/NamespacedId;)Lnet/irisshaders/iris/pipeline/WorldRenderingPipeline;",at=@At("RETURN"),require=1,allow=1)
 private void after(NamespacedId dimension,CallbackInfoReturnable<WorldRenderingPipeline> ci){PhotonSpatialNormal.afterCapture();}
}

package com.pp.shaderlod.mixin;
import com.pp.shaderlod.optimization.CompileOptionControl;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Pinned real dimension admission; native0 reload precedes unsupported world pipeline construction. */
@Mixin(value=PipelineManager.class,remap=false)
public abstract class CompileDimensionMixin {
    @Inject(method="preparePipeline(Lnet/irisshaders/iris/shaderpack/materialmap/NamespacedId;)Lnet/irisshaders/iris/pipeline/WorldRenderingPipeline;",at=@At("HEAD"),cancellable=true,require=1,allow=1,remap=false)
    private void shaderlod$dimension(NamespacedId dimension,CallbackInfoReturnable<WorldRenderingPipeline> ci) {
        var replacement=CompileOptionControl.prepareDimension(dimension);if(replacement!=null)ci.setReturnValue(replacement);
    }
}

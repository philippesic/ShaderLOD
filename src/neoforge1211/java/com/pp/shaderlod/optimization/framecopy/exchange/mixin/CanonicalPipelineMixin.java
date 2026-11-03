package com.pp.shaderlod.optimization.framecopy.exchange.mixin;
import com.pp.shaderlod.optimization.framecopy.exchange.CanonicalBankExchange;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.pipeline.IrisRenderingPipeline",remap=false)
public abstract class CanonicalPipelineMixin {
    @Shadow @Final private RenderTargets renderTargets;
    @Inject(method="<init>",at=@At("RETURN"),require=1,allow=1)
    private void exchange$constructed(CallbackInfo ci){CanonicalBankExchange.clearReturned(renderTargets);}
    @Inject(method="beginLevelRendering()V",at=@At("HEAD"),require=1,allow=1)
    private void exchange$before(CallbackInfo ci){CanonicalBankExchange.beforeFrame(this);}
    @Inject(method="beginLevelRendering()V",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pipeline/CompositeRenderer;renderAll()V"),require=1,allow=1)
    private void exchange$ready(CallbackInfo ci){CanonicalBankExchange.beforeGraph(this,renderTargets);}
}

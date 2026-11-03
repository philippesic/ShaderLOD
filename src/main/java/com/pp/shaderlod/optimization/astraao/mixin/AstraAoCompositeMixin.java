package com.pp.shaderlod.optimization.astraao.mixin;
import com.pp.shaderlod.optimization.astraao.AstraAoRuntime;import net.irisshaders.iris.targets.RenderTargets;import com.google.common.collect.ImmutableList;
import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.pipeline.CompositeRenderer",remap=false)
public abstract class AstraAoCompositeMixin {
 @Shadow @Final private RenderTargets renderTargets;@Shadow @Final private ImmutableList<?> passes;
 @Inject(method="renderAll()V",at=@At("HEAD"),require=1)private void shaderlod$start(CallbackInfo ci){AstraAoRuntime.beginComposite(this,renderTargets,passes);}
 @Inject(method="renderAll()V",at=@At("RETURN"),require=1)private void shaderlod$end(CallbackInfo ci){AstraAoRuntime.endComposite();}
}

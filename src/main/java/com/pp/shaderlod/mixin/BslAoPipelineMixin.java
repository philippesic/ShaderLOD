package com.pp.shaderlod.mixin;
import com.pp.shaderlod.optimization.bsl.BslAoNormalBridge;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=IrisRenderingPipeline.class,remap=false)
public abstract class BslAoPipelineMixin {
 @Inject(method="beginLevelRendering()V",at=@At("HEAD"),require=1) private void begin(CallbackInfo ci){BslAoNormalBridge.frameStarted();}
 @Inject(method="finalizeLevelRendering()V",at=@At("RETURN"),require=1) private void returned(CallbackInfo ci){BslAoNormalBridge.frameReturned();}
 @Inject(method="destroy()V",at=@At("HEAD"),require=1) private void destroyed(CallbackInfo ci){BslAoNormalBridge.destroyed(this);}
}

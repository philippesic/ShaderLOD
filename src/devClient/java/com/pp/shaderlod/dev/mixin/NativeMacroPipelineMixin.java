package com.pp.shaderlod.dev.mixin;
import com.pp.shaderlod.dev.NativeMacroInspection;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=IrisRenderingPipeline.class,remap=false)
public abstract class NativeMacroPipelineMixin {
    @Inject(method="destroy()V",at=@At("HEAD"),require=1)
    private void invalidate(CallbackInfo ci){NativeMacroInspection.invalidate();}
}

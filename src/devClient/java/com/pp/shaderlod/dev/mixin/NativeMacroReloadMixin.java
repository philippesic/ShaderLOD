package com.pp.shaderlod.dev.mixin;
import com.pp.shaderlod.dev.NativeMacroInspection;
import net.irisshaders.iris.Iris;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=Iris.class,remap=false)
public abstract class NativeMacroReloadMixin {
    @Inject(method="reload()V",at=@At("HEAD"),require=1)
    private static void invalidate(CallbackInfo ci){NativeMacroInspection.invalidate();}
}

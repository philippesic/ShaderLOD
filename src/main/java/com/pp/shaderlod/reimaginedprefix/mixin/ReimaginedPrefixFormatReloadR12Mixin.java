package com.pp.shaderlod.reimaginedprefix.mixin;
import com.pp.shaderlod.reimaginedprefix.ReimaginedPrefixRuntime;
import net.irisshaders.iris.pbr.format.TextureFormatLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Format may change null→nonnull→null while objects/GL IDs are reused. Retire first. */
@Mixin(value=TextureFormatLoader.class,remap=false)
public abstract class ReimaginedPrefixFormatReloadR12Mixin {
 @Inject(method="reload",at=@At("HEAD"),require=1)
 private static void reload(CallbackInfo ci){ReimaginedPrefixRuntime.allMutating();}
}

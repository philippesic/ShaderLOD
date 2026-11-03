package com.pp.shaderlod.astraprefix.mixin;
import com.pp.shaderlod.astraprefix.AstraPrefixRuntime;
import net.irisshaders.iris.pbr.format.TextureFormatLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Format may change null→nonnull→null while objects/GL IDs are reused. Retire first. */
@Mixin(value=TextureFormatLoader.class,remap=false)
public abstract class AstraPrefixFormatReloadR12Mixin {
 @Inject(method="reload(Lnet/minecraft/server/packs/resources/ResourceManager;)V",at=@At("HEAD"),require=1)
 private static void reload(CallbackInfo ci){AstraPrefixRuntime.allMutating();}
}

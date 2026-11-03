package com.pp.shaderlod.photonprefix.mixin;
import com.pp.shaderlod.photonprefix.PhotonPrefixRuntime;
import net.irisshaders.iris.pbr.format.TextureFormatLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Format may change null→nonnull→null while objects/GL IDs are reused. Retire first. */
@Mixin(value=TextureFormatLoader.class,remap=false)
public abstract class PhotonPrefixFormatReloadR12Mixin {
 @Inject(method="reload",at=@At("HEAD"),require=1)
 private static void reload(CallbackInfo ci){PhotonPrefixRuntime.allMutating();}
}

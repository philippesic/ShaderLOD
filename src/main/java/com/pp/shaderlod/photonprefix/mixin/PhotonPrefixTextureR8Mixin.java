package com.pp.shaderlod.photonprefix.mixin;
import com.pp.shaderlod.photonprefix.PhotonPrefixRuntime;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractTexture.class)
public abstract class PhotonPrefixTextureR8Mixin {
 @Inject(method={"setFilter","releaseId"},at=@At("HEAD"),require=1)
 private void mutate(CallbackInfo ci){PhotonPrefixRuntime.atlasMutating(this);}
}

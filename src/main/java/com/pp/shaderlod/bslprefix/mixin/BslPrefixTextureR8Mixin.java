package com.pp.shaderlod.bslprefix.mixin;
import com.pp.shaderlod.bslprefix.BslPrefixRuntime;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractTexture.class)
public abstract class BslPrefixTextureR8Mixin {
 @Inject(method={"setFilter","releaseId"},at=@At("HEAD"),require=1)
 private void mutate(CallbackInfo ci){BslPrefixRuntime.atlasMutating(this);}
}

package com.pp.shaderlod.solasprefix.mixin;
import com.pp.shaderlod.solasprefix.SolasPrefixRuntime;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractTexture.class)
public abstract class SolasPrefixTextureR8Mixin {
 @Inject(method={"setFilter","releaseId"},at=@At("HEAD"),require=1)
 private void mutate(CallbackInfo ci){SolasPrefixRuntime.atlasMutating(this);}
}

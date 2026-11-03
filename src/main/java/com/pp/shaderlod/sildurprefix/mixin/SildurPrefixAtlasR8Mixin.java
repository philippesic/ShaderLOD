package com.pp.shaderlod.sildurprefix.mixin;
import com.pp.shaderlod.sildurprefix.SildurPrefixRuntime;
import net.irisshaders.iris.pbr.texture.PBRAtlasTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=PBRAtlasTexture.class,remap=false)
public abstract class SildurPrefixAtlasR8Mixin {
 @Inject(method={"upload(III)V","uploadSprite(Lnet/irisshaders/iris/pbr/loader/AtlasPBRLoader$PBRTextureAtlasSprite;)V","clear()V","close()V"},at=@At("HEAD"),require=1)
 private void mutate(CallbackInfo ci){SildurPrefixRuntime.atlasMutating(this);}
}

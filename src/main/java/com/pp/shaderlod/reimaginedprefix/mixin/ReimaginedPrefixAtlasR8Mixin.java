package com.pp.shaderlod.reimaginedprefix.mixin;
import com.pp.shaderlod.reimaginedprefix.ReimaginedPrefixRuntime;
import net.irisshaders.iris.pbr.texture.PBRAtlasTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=PBRAtlasTexture.class,remap=false)
public abstract class ReimaginedPrefixAtlasR8Mixin {
 @Inject(method={"upload(III)V","uploadSprite(Lnet/irisshaders/iris/pbr/loader/AtlasPBRLoader$PBRTextureAtlasSprite;)V","clear()V","close()V"},at=@At("HEAD"),require=1)
 private void mutate(CallbackInfo ci){ReimaginedPrefixRuntime.atlasMutating(this);}
}

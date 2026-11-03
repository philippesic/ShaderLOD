package com.pp.shaderlod.solasprefix.mixin;
import com.pp.shaderlod.solasprefix.SolasPrefixRuntime;
import net.irisshaders.iris.pbr.texture.PBRTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=PBRTextureManager.class,remap=false)
public abstract class SolasPrefixManagerR8Mixin {
 @Inject(method={"clear()V","close()V","onDeleteTexture(I)V"},at=@At("HEAD"),require=1)private void mutate(CallbackInfo ci){SolasPrefixRuntime.allMutating();}
}

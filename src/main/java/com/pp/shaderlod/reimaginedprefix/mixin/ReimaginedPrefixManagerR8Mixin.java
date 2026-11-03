package com.pp.shaderlod.reimaginedprefix.mixin;
import com.pp.shaderlod.reimaginedprefix.ReimaginedPrefixRuntime;
import net.irisshaders.iris.pbr.texture.PBRTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=PBRTextureManager.class,remap=false)
public abstract class ReimaginedPrefixManagerR8Mixin {
 @Inject(method={"clear()V","close()V","onDeleteTexture(I)V"},at=@At("HEAD"),require=1)private void mutate(CallbackInfo ci){ReimaginedPrefixRuntime.allMutating();}
}

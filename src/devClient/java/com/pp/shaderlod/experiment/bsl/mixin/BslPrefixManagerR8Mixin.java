package com.pp.shaderlod.experiment.bsl.mixin;
import com.pp.shaderlod.experiment.bsl.BslPrefixSustainedR8;
import net.irisshaders.iris.pbr.texture.PBRTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=PBRTextureManager.class,remap=false)
public abstract class BslPrefixManagerR8Mixin {
 @Inject(method={"clear()V","close()V","onDeleteTexture(I)V"},at=@At("HEAD"),require=1)private void mutate(CallbackInfo ci){BslPrefixSustainedR8.allMutating();}
}

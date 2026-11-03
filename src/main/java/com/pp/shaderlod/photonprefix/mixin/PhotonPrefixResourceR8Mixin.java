package com.pp.shaderlod.photonprefix.mixin;
import com.pp.shaderlod.photonprefix.PhotonPrefixRuntime;
import net.minecraft.server.packs.resources.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ReloadableResourceManager.class)
public abstract class PhotonPrefixResourceR8Mixin {
 @Inject(method="createReload",at=@At("HEAD"),require=1)private void reload(CallbackInfoReturnable<ReloadInstance> ci){PhotonPrefixRuntime.resourceReloading(this);}
}

package com.pp.shaderlod.optimization.photon.mixin;
import com.pp.shaderlod.optimization.photon.PhotonSpatialNormal;
import net.irisshaders.iris.Iris;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Installed before shared CompileOptionControl's reload planning. */
@Mixin(value=Iris.class,remap=false,priority=1100)
public abstract class PhotonSpatialReloadMixin {
 @Inject(method="reload()V",at=@At("HEAD"),require=1,allow=1)
 private static void reload(CallbackInfo ci){PhotonSpatialNormal.install();PhotonSpatialNormal.onReload();}
}

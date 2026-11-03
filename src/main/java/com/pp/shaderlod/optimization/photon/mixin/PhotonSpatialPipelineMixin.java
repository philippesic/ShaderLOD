package com.pp.shaderlod.optimization.photon.mixin;
import com.pp.shaderlod.optimization.photon.PhotonSpatialNormal;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=IrisRenderingPipeline.class,remap=false)
public abstract class PhotonSpatialPipelineMixin {
 @Inject(method="beginLevelRendering()V",at=@At("HEAD"),require=1) private void begin(CallbackInfo ci){PhotonSpatialNormal.frameStarted();}
 @Inject(method="finalizeLevelRendering()V",at=@At("RETURN"),require=1) private void returned(CallbackInfo ci){PhotonSpatialNormal.frameReturned();}
 @Inject(method="destroy()V",at=@At("HEAD"),require=1) private void destroyed(CallbackInfo ci){PhotonSpatialNormal.destroyed(this);}
}

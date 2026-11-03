package com.pp.shaderlod.optimization.photon.mixin;
import com.pp.shaderlod.optimization.photon.PhotonSpatialNormal;import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Actual GL backend boundary after pass pipeline/uniform/sampler/viewport setup. */
@Mixin(targets="com.mojang.blaze3d.opengl.GlCommandEncoder",remap=false)public abstract class PhotonSpatialModernDrawMixin{
 @Inject(method="drawFromBuffers",at=@At("HEAD"),cancellable=true,require=1)private void before(CallbackInfo ci){if(!PhotonSpatialNormal.beforeDraw())ci.cancel();}
 @Inject(method="drawFromBuffers",at=@At("RETURN"),require=1)private void returned(CallbackInfo ci){PhotonSpatialNormal.afterDraw();}}

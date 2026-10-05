package com.pp.shaderlod.mixin;

import net.irisshaders.iris.shadows.frustum.BoxCuller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exact immutable Sodium-relative distance constraint; no reflection in AABB testing. */
@Mixin(value = BoxCuller.class, remap = false)
public interface ShadowBoxDistanceAccessor {
    @Accessor(value = "maxDistance", remap = false)
    double shaderlod$maxDistance();
}

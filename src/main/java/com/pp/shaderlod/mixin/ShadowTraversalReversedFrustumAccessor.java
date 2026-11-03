package com.pp.shaderlod.mixin;

import net.irisshaders.iris.shadows.frustum.BoxCuller;
import net.irisshaders.iris.shadows.frustum.advanced.ReversedAdvancedShadowCullingFrustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reversed distance rejection is separate from the inherited primary box acceptance. */
@Mixin(value = ReversedAdvancedShadowCullingFrustum.class, remap = false)
public interface ShadowTraversalReversedFrustumAccessor {
    @Accessor(value = "distanceCuller", remap = false)
    BoxCuller shaderlod$traversalDistanceBox();
}

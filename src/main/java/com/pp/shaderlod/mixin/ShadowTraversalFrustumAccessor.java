package com.pp.shaderlod.mixin;

import net.irisshaders.iris.shadows.frustum.BoxCuller;
import net.irisshaders.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only private inputs of the exact Iris 1.8.12 Sodium testAab path. */
@Mixin(value = AdvancedShadowCullingFrustum.class, remap = false)
public interface ShadowTraversalFrustumAccessor {
    @Accessor(value = "planes", remap = false)
    float[][] shaderlod$traversalPlanes();

    @Accessor(value = "planeCount", remap = false)
    int shaderlod$traversalPlaneCount();

    @Accessor(value = "boxCuller", remap = false)
    BoxCuller shaderlod$traversalPrimaryBox();

    @Accessor(value = "FMA_SUPPORT", remap = false)
    static boolean shaderlod$traversalFmaSupport() {
        throw new IllegalStateException("Shadow traversal FMA accessor was not transformed");
    }
}

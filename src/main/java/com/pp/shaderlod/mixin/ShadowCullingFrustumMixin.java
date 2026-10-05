package com.pp.shaderlod.mixin;

import com.pp.shaderlod.ShadowCullingController;
import com.pp.shaderlod.ShadowCullingMath;
import net.irisshaders.iris.shadows.frustum.BoxCuller;
import net.irisshaders.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum;
import net.irisshaders.iris.shadows.frustum.advanced.ReversedAdvancedShadowCullingFrustum;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Exact boolean specialization of Iris 1.8.12's Sodium entry point. */
@Mixin(value = AdvancedShadowCullingFrustum.class, remap = false)
public abstract class ShadowCullingFrustumMixin implements ShadowCullingController.PlaneAccess {
    @Shadow @Final protected BoxCuller boxCuller;
    @Shadow @Final private float[][] planes;
    @Shadow private int planeCount;
    @Shadow @Final private static boolean FMA_SUPPORT;
    @Shadow protected abstract int checkCornerVisibility(float minX, float minY, float minZ,
                                                       float maxX, float maxY, float maxZ);

    /**
     * @author ShaderLOD
     * @reason Preserve Iris's exact visibility decision while omitting unused inside/intersect classification.
     */
    @Overwrite(remap = false)
    public boolean testAab(float minX, float minY, float minZ,
                           float maxX, float maxY, float maxZ) {
        if (boxCuller != null && boxCuller.isCulledSodium(minX, minY, minZ, maxX, maxY, maxZ)) {
            return false;
        }
        if (!ShadowCullingController.isActive()) {
            return checkCornerVisibility(minX, minY, minZ, maxX, maxY, maxZ) > 0;
        }
        return shaderlod$testPositiveVertices(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Unique
    @Override
    public boolean shaderlod$testPositiveVertices(float minX, float minY, float minZ,
                                                 float maxX, float maxY, float maxZ) {
        return ShadowCullingMath.visible(planes, planeCount, FMA_SUPPORT,
                minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Unique
    @Override
    public boolean shaderlod$testOriginalVertices(float minX, float minY, float minZ,
                                                 float maxX, float maxY, float maxZ) {
        return checkCornerVisibility(minX, minY, minZ, maxX, maxY, maxZ) > 0;
    }

    @Unique
    @Override
    public boolean shaderlod$acceptReversedBox(float minX, float minY, float minZ,
                                              float maxX, float maxY, float maxZ) {
        return boxCuller != null && !boxCuller.isCulledSodium(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Reversed frusta have distinct box acceptance semantics; preserve them exactly. */
    @Mixin(value = ReversedAdvancedShadowCullingFrustum.class, remap = false)
    public abstract static class Reversed {
        @Shadow @Final private BoxCuller distanceCuller;

        /**
         * @author ShaderLOD
         * @reason Apply the exact boolean specialization after the original reversed distance/box decisions.
         */
        @Overwrite(remap = false)
        public boolean testAab(float minX, float minY, float minZ,
                               float maxX, float maxY, float maxZ) {
            if (distanceCuller != null
                    && distanceCuller.isCulledSodium(minX, minY, minZ, maxX, maxY, maxZ)) {
                return false;
            }
            // Those members belong to the base target. Resolve them there,
            // rather than declaring @Shadow on inherited subclass members.
            ShadowCullingController.PlaneAccess access = (ShadowCullingController.PlaneAccess) this;
            if (access.shaderlod$acceptReversedBox(minX, minY, minZ, maxX, maxY, maxZ)) {
                return true;
            }
            if (!ShadowCullingController.isActive()) {
                return access.shaderlod$testOriginalVertices(minX, minY, minZ, maxX, maxY, maxZ);
            }
            return access.shaderlod$testPositiveVertices(
                    minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}

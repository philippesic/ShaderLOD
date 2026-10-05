package com.pp.shaderlod.mixin;

import com.pp.shaderlod.ShadowCullingController;
import com.pp.shaderlod.ShadowCullingMath;
import com.pp.shaderlod.ShadowFrustumCoherence;
import com.pp.shaderlod.ShadowFrustumCoherenceController;
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
    @Shadow public double x;
    @Shadow public double y;
    @Shadow public double z;
    @Unique private ShadowFrustumCoherence shaderlod$coherence;
    @Shadow protected abstract int checkCornerVisibility(float minX, float minY, float minZ,
                                                       float maxX, float maxY, float maxZ);

    /**
     * @author ShaderLOD
     * @reason Preserve Iris's exact visibility decision while omitting unused inside/intersect classification.
     */
    @Overwrite(remap = false)
    public boolean testAab(float minX, float minY, float minZ,
                           float maxX, float maxY, float maxZ) {
        if (ShadowFrustumCoherenceController.isActive() && shaderlod$coherentAccept(false,
                Double.POSITIVE_INFINITY, minX, minY, minZ, maxX, maxY, maxZ)) return true;
        if (boxCuller != null && boxCuller.isCulledSodium(minX, minY, minZ, maxX, maxY, maxZ)) {
            return false;
        }
        if (!ShadowCullingController.isActive()) {
            return checkCornerVisibility(minX, minY, minZ, maxX, maxY, maxZ) > 0;
        }
        return shaderlod$testPositiveVertices(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * @author ShaderLOD
     * @reason Preserve prepared position and invalidate frame-local proofs without per-AABB hooks.
     */
    @Overwrite(remap = false)
    public void prepare(double x, double y, double z) {
        if (boxCuller != null) boxCuller.setPosition(x, y, z);
        this.x = x;
        this.y = y;
        this.z = z;
        if (shaderlod$coherence != null) shaderlod$coherence.invalidate();
    }

    /**
     * @author ShaderLOD
     * @reason Preserve the pinned sole plane mutator and invalidate any preceding proofs.
     */
    @Overwrite(remap = false)
    private void addPlane(float[] plane) {
        planes[planeCount] = plane;
        planeCount++;
        if (shaderlod$coherence != null) shaderlod$coherence.invalidate();
    }

    @Unique @Override
    public boolean shaderlod$coherentAccept(boolean reversed, double distanceLimit,
                                           float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        // The reversed primary box is an early ACCEPT, not a rejecting constraint.
        double limit = reversed ? distanceLimit : boxCuller == null ? Double.POSITIVE_INFINITY
                : ((ShadowBoxDistanceAccessor) boxCuller).shaderlod$maxDistance();
        if (shaderlod$coherence == null) {
            shaderlod$coherence = new ShadowFrustumCoherence(planes, planeCount, FMA_SUPPORT, limit);
        } else if (!shaderlod$coherence.matches(planes, planeCount, FMA_SUPPORT, limit)) {
            shaderlod$coherence.reset(planes, planeCount, FMA_SUPPORT, limit);
        }
        return shaderlod$coherence.canAccept(minX, minY, minZ, maxX, maxY, maxZ);
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
            ShadowCullingController.PlaneAccess access = (ShadowCullingController.PlaneAccess) this;
            if (ShadowFrustumCoherenceController.isActive()) {
                double limit = distanceCuller == null ? Double.POSITIVE_INFINITY
                        : ((ShadowBoxDistanceAccessor) distanceCuller).shaderlod$maxDistance();
                if (access.shaderlod$coherentAccept(true, limit, minX, minY, minZ, maxX, maxY, maxZ)) return true;
            }
            if (distanceCuller != null
                    && distanceCuller.isCulledSodium(minX, minY, minZ, maxX, maxY, maxZ)) {
                return false;
            }
            // Those members belong to the base target. Resolve them there,
            // rather than declaring @Shadow on inherited subclass members.
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

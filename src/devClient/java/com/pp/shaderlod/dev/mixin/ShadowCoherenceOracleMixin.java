package com.pp.shaderlod.dev.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.pp.shaderlod.dev.timing.GpuTimers;
import com.pp.shaderlod.dev.timing.ShadowCoherenceOracle;
import net.irisshaders.iris.shadows.frustum.BoxCuller;
import net.irisshaders.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum;
import net.irisshaders.iris.shadows.frustum.advanced.ReversedAdvancedShadowCullingFrustum;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Original classifier comparison after the actual testAab result, always returning that identical result. */
@Mixin(value = AdvancedShadowCullingFrustum.class, priority = 900, remap = false)
public abstract class ShadowCoherenceOracleMixin implements ShadowCoherenceOracle.OriginalAccess {
    @Shadow @Final protected BoxCuller boxCuller;
    @Shadow private int planeCount;
    @Shadow @Final private static boolean FMA_SUPPORT;
    @Shadow public double x;
    @Shadow public double y;
    @Shadow public double z;
    @Shadow protected abstract int checkCornerVisibility(float minX, float minY, float minZ,
                                                       float maxX, float maxY, float maxZ);
    @Unique private long shaderlod$oracleGeneration;

    @ModifyReturnValue(method = "testAab(FFFFFF)Z", at = @At("RETURN"), require = 1, remap = false)
    private boolean shaderlod$oracleBase(boolean actual, float minX, float minY, float minZ,
                                         float maxX, float maxY, float maxZ) {
        if (!ShadowCoherenceOracle.recording()) return actual;
        try {
            boolean rejected = boxCuller != null && boxCuller.isCulledSodium(minX, minY, minZ, maxX, maxY, maxZ);
            boolean original = !rejected && checkCornerVisibility(minX, minY, minZ, maxX, maxY, maxZ) > 0;
            ShadowCoherenceOracle.observe(false, this, GpuTimers.frameId(), this, actual, original,
                    rejected ? "PRIMARY_DISTANCE_REJECT" : original ? "ORIGINAL_CORNER_VISIBLE" : "ORIGINAL_CORNER_REJECT",
                    minX, minY, minZ, maxX, maxY, maxZ);
        } catch (RuntimeException | LinkageError failure) { ShadowCoherenceOracle.unavailable(false, failure); }
        return actual;
    }

    @Inject(method = "prepare(DDD)V", at = @At("RETURN"), require = 1, remap = false)
    private void shaderlod$oraclePrepareGeneration(double x, double y, double z, CallbackInfo ci) {
        if (ShadowCoherenceOracle.enabled()) shaderlod$oracleGeneration++;
    }
    @Inject(method = "addPlane([F)V", at = @At("RETURN"), require = 1, remap = false)
    private void shaderlod$oraclePlaneGeneration(float[] plane, CallbackInfo ci) {
        if (ShadowCoherenceOracle.enabled()) shaderlod$oracleGeneration++;
    }

    @Unique @Override public boolean shaderlod$oracleOriginalCorners(float minX, float minY, float minZ,
                                                                   float maxX, float maxY, float maxZ) {
        return checkCornerVisibility(minX, minY, minZ, maxX, maxY, maxZ) > 0;
    }
    @Unique @Override public boolean shaderlod$oraclePrimaryAccept(float minX, float minY, float minZ,
                                                                 float maxX, float maxY, float maxZ) {
        return boxCuller != null && !boxCuller.isCulledSodium(minX, minY, minZ, maxX, maxY, maxZ);
    }
    @Unique @Override public long shaderlod$oracleGeneration() { return shaderlod$oracleGeneration; }
    @Unique @Override public int shaderlod$oraclePlaneCount() { return planeCount; }
    @Unique @Override public boolean shaderlod$oracleFma() { return FMA_SUPPORT; }
    @Unique @Override public double shaderlod$oracleX() { return x; }
    @Unique @Override public double shaderlod$oracleY() { return y; }
    @Unique @Override public double shaderlod$oracleZ() { return z; }

    /** Only distanceCuller belongs to this subclass; inherited seams resolve through the BASE dev interface. */
    @Mixin(value = ReversedAdvancedShadowCullingFrustum.class, priority = 900, remap = false)
    public abstract static class Reversed {
        @Shadow @Final private BoxCuller distanceCuller;

        @ModifyReturnValue(method = "testAab(FFFFFF)Z", at = @At("RETURN"), require = 1, remap = false)
        private boolean shaderlod$oracleReversed(boolean actual, float minX, float minY, float minZ,
                                                 float maxX, float maxY, float maxZ) {
            if (!ShadowCoherenceOracle.recording()) return actual;
            try {
                var access = (ShadowCoherenceOracle.OriginalAccess) this;
                boolean original;
                String branch;
                if (distanceCuller != null && distanceCuller.isCulledSodium(minX, minY, minZ, maxX, maxY, maxZ)) {
                    original = false; branch = "REVERSED_DISTANCE_REJECT";
                } else if (access.shaderlod$oraclePrimaryAccept(minX, minY, minZ, maxX, maxY, maxZ)) {
                    original = true; branch = "REVERSED_PRIMARY_BOX_ACCEPT";
                } else {
                    original = access.shaderlod$oracleOriginalCorners(minX, minY, minZ, maxX, maxY, maxZ);
                    branch = original ? "ORIGINAL_CORNER_VISIBLE" : "ORIGINAL_CORNER_REJECT";
                }
                ShadowCoherenceOracle.observe(true, this, GpuTimers.frameId(), access, actual, original,
                        branch, minX, minY, minZ, maxX, maxY, maxZ);
            } catch (RuntimeException | LinkageError failure) { ShadowCoherenceOracle.unavailable(true, failure); }
            return actual;
        }
    }
}

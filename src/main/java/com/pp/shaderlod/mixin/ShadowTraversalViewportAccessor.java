package com.pp.shaderlod.mixin;

import com.pp.shaderlod.ShadowTraversalInputs;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.render.viewport.frustum.Frustum;
import net.irisshaders.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum;
import net.irisshaders.iris.shadows.frustum.advanced.ReversedAdvancedShadowCullingFrustum;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only exact stored viewport access for the pinned no-skip diagnostic. */
@Mixin(value = Viewport.class, remap = false)
public interface ShadowTraversalViewportAccessor {
    @Accessor(value = "frustum", remap = false)
    Frustum shaderlod$traversalFrustum();

    static ShadowTraversalInputs.ViewportState captureViewport(Viewport viewport) {
        if (viewport == null) return null;
        CameraTransform transform = viewport.getTransform();
        SectionPos section = viewport.getChunkCoord();
        BlockPos block = viewport.getBlockCoord();
        if (transform == null || section == null || block == null) return null;
        return new ShadowTraversalInputs.ViewportState(ShadowTraversalInputs.StoredTransform.stored(
                transform.intX, transform.intY, transform.intZ,
                transform.fracX, transform.fracY, transform.fracZ,
                transform.x, transform.y, transform.z),
                section.getX(), section.getY(), section.getZ(), block.getX(), block.getY(), block.getZ());
    }

    static ShadowTraversalInputs.CameraState captureCamera(Camera camera) {
        if (camera == null) return null;
        Vec3 position = camera.getPosition();
        BlockPos block = camera.getBlockPosition();
        if (position == null || block == null) return null;
        return ShadowTraversalInputs.CameraState.stored(position.x, position.y, position.z,
                block.getX(), block.getY(), block.getZ());
    }

    /** Actual viewport frustum, not a renderer observer or a newly constructed approximation. */
    static ShadowTraversalInputs.FrustumState captureFrustum(Viewport viewport) {
        if (viewport == null || !((Object) viewport instanceof ShadowTraversalViewportAccessor access))
            return ShadowTraversalInputs.FrustumState.unknown("missing viewport accessor");
        return captureFrustum(access.shaderlod$traversalFrustum());
    }

    /**
     * Client-owner callsite only. Exact class equality refuses unknown subclasses,
     * even if they inherit accessors. A second copy refuses changes during capture;
     * producer/ownership fences still must prove stability until native consumption.
     */
    static ShadowTraversalInputs.FrustumState captureFrustum(Frustum frustum) {
        if (frustum == null) return ShadowTraversalInputs.FrustumState.unknown("missing frustum");
        if (frustum.getClass() != AdvancedShadowCullingFrustum.class
                && frustum.getClass() != ReversedAdvancedShadowCullingFrustum.class)
            return ShadowTraversalInputs.FrustumState.unknown(frustum.getClass().getName());
        try {
            ShadowTraversalInputs.FrustumState first = copyFrustum(frustum);
            ShadowTraversalInputs.FrustumState second = copyFrustum(frustum);
            return first.equals(second) ? first
                    : ShadowTraversalInputs.FrustumState.unknown("frustum changed during capture");
        } catch (RuntimeException | LinkageError failure) {
            return ShadowTraversalInputs.FrustumState.unknown("unavailable exact frustum accessor: "
                    + failure.getClass().getName());
        }
    }

    private static ShadowTraversalInputs.FrustumState copyFrustum(Frustum frustum) {
        if (!(frustum instanceof ShadowTraversalFrustumAccessor access))
            return ShadowTraversalInputs.FrustumState.unknown("missing base frustum accessor");
        AdvancedShadowCullingFrustum advanced = (AdvancedShadowCullingFrustum) frustum;
        boolean reversed = frustum.getClass() == ReversedAdvancedShadowCullingFrustum.class;
        ShadowTraversalInputs.BoxState distanceBox = ShadowTraversalInputs.BoxState.absent();
        if (reversed) {
            if (!(frustum instanceof ShadowTraversalReversedFrustumAccessor reversedAccess))
                return ShadowTraversalInputs.FrustumState.unknown("missing reversed frustum accessor");
            distanceBox = ShadowTraversalBoxAccessor.captureBox(reversedAccess.shaderlod$traversalDistanceBox());
        }
        return ShadowTraversalInputs.FrustumState.stored(reversed
                        ? ShadowTraversalInputs.FrustumKind.REVERSED_ADVANCED
                        : ShadowTraversalInputs.FrustumKind.ADVANCED,
                advanced.x, advanced.y, advanced.z,
                ShadowTraversalFrustumAccessor.shaderlod$traversalFmaSupport(),
                access.shaderlod$traversalPlanes(), access.shaderlod$traversalPlaneCount(),
                ShadowTraversalBoxAccessor.captureBox(access.shaderlod$traversalPrimaryBox()), distanceBox);
    }
}

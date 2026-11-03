package com.pp.shaderlod.mixin;

import com.pp.shaderlod.ShadowTraversalInputs;
import net.irisshaders.iris.shadows.frustum.BoxCuller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Capture the actual prepared bounds as well as the Sodium-relative distance. */
@Mixin(value = BoxCuller.class, remap = false)
public interface ShadowTraversalBoxAccessor {
    @Accessor(value = "maxDistance", remap = false)
    double shaderlod$traversalMaxDistance();
    @Accessor(value = "minAllowedX", remap = false)
    double shaderlod$traversalMinX();
    @Accessor(value = "maxAllowedX", remap = false)
    double shaderlod$traversalMaxX();
    @Accessor(value = "minAllowedY", remap = false)
    double shaderlod$traversalMinY();
    @Accessor(value = "maxAllowedY", remap = false)
    double shaderlod$traversalMaxY();
    @Accessor(value = "minAllowedZ", remap = false)
    double shaderlod$traversalMinZ();
    @Accessor(value = "maxAllowedZ", remap = false)
    double shaderlod$traversalMaxZ();

    static ShadowTraversalInputs.BoxState captureBox(BoxCuller box) {
        if (box == null) return ShadowTraversalInputs.BoxState.absent();
        if (box.getClass() != BoxCuller.class || !((Object) box instanceof ShadowTraversalBoxAccessor access))
            return null;
        return ShadowTraversalInputs.BoxState.stored(access.shaderlod$traversalMaxDistance(),
                access.shaderlod$traversalMinX(), access.shaderlod$traversalMaxX(),
                access.shaderlod$traversalMinY(), access.shaderlod$traversalMaxY(),
                access.shaderlod$traversalMinZ(), access.shaderlod$traversalMaxZ());
    }
}

package com.pp.shaderlod.dev.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.pp.shaderlod.dev.timing.GpuTimers;
import com.pp.shaderlod.dev.timing.ShadowInputObserver;
import net.irisshaders.iris.mixin.LevelRendererAccessor;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/** Reads existing expressions and returns the identical value; no callback allocations or cancellation. */
@Mixin(value = ShadowRenderer.class, remap = false)
public abstract class ShadowInputObserverMixin {
    @Shadow @Final private int resolution;
    @Shadow @Final private boolean shouldRenderEntities;
    @Shadow @Final private boolean shouldRenderPlayer;
    @Shadow @Final private boolean shouldRenderBlockEntities;
    @Shadow @Final private boolean shouldRenderLightBlockEntities;
    @Shadow private boolean packHasVoxelization;
    @Shadow private int renderedShadowEntities;
    @Shadow private int renderedShadowBlockEntities;

    // After Iris's shadow-distance-zero early return, before ACTIVE and renderer mutations.
    @ModifyExpressionValue(method = "renderShadows", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/Minecraft;getInstance()Lnet/minecraft/client/Minecraft;"),
            require = 1, expect = 1, remap = false)
    private Minecraft shaderlod$observeBegin(Minecraft original, LevelRendererAccessor accessor, Camera camera) {
        if (ShadowInputObserver.enabled()) {
            var world = original.level;
            Vec3 position = camera.getPosition(); // Existing camera vector; no new object.
            ShadowInputObserver.beginFrame(GpuTimers.frameId(), world, this, camera.getEntity(),
                    position.x, position.y, position.z, camera.getYRot(), camera.getXRot(),
                    world == null ? 0 : world.getDayTime(), world == null ? 0 : world.getGameTime(),
                    SystemTimeUniforms.TIMER.getFrameTimeCounter(), resolution,
                    shouldRenderEntities, shouldRenderPlayer, shouldRenderBlockEntities,
                    shouldRenderLightBlockEntities, packHasVoxelization);
        }
        return original;
    }

    // Second getLevel after renderAll: all geometry/mipmaps/composites and renderer restoration completed.
    @ModifyExpressionValue(method = "renderShadows", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/irisshaders/iris/mixin/LevelRendererAccessor;getLevel()Lnet/minecraft/client/multiplayer/ClientLevel;"),
            slice = @Slice(from = @At(value = "INVOKE",
                    target = "Lnet/irisshaders/iris/shadows/ShadowCompositeRenderer;renderAll()V", shift = At.Shift.AFTER)),
            require = 1, expect = 1, remap = false)
    private ClientLevel shaderlod$observeEnd(ClientLevel original) {
        if (ShadowInputObserver.enabled()) {
            long frame = GpuTimers.frameId();
            Matrix4fc model = ShadowRenderer.MODELVIEW, projection = ShadowRenderer.PROJECTION;
            if (model != null) shaderlod$recordMatrix(frame, false, model);
            if (projection != null) shaderlod$recordMatrix(frame, true, projection);
            ShadowInputObserver.endRender(frame, renderedShadowEntities, renderedShadowBlockEntities);
        }
        return original;
    }

    @Unique private static void shaderlod$recordMatrix(long frame, boolean projection, Matrix4fc matrix) {
        ShadowInputObserver.matrix(frame, false, projection,
                matrix.m00(), matrix.m01(), matrix.m02(), matrix.m03(),
                matrix.m10(), matrix.m11(), matrix.m12(), matrix.m13(),
                matrix.m20(), matrix.m21(), matrix.m22(), matrix.m23(),
                matrix.m30(), matrix.m31(), matrix.m32(), matrix.m33());
    }
}

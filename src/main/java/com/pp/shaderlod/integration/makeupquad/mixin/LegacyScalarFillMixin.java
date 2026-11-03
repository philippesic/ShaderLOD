package com.pp.shaderlod.integration.makeupquad.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.pp.shaderlod.integration.makeupquad.LegacyScalarState;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime;
import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import org.spongepowered.asm.mixin.Mixin;
import com.pp.shaderlod.optimization.NativeAdmissionCost;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class LegacyScalarFillMixin {
    @WrapMethod(method = "fillCommandBuffer", require = 1)
    private static void scope(MultiDrawBatch batch, RenderRegion region, SectionRenderDataStorage storage,
            ChunkRenderList list, CameraTransform camera, TerrainRenderPass pass, boolean cull,
            Operation<Void> original) {
        var scalar = (LegacyScalarState.Owner) (Object) batch;
        boolean profile=NativeAdmissionCost.ENABLED && !ShadowRenderingState.areShadowsCurrentlyBeingRendered()
            && (pass==DefaultTerrainRenderPasses.SOLID || pass==DefaultTerrainRenderPasses.CUTOUT);
        long fill=profile?NativeAdmissionCost.wallStart():0,fillCpu=NativeAdmissionCost.cpuStart(fill);
        long wall=profile?NativeAdmissionCost.wallStart():0,cpu=NativeAdmissionCost.cpuStart(wall);
        try {scalar.shaderlod$enterScalarFill();}
        finally {NativeAdmissionCost.finish(NativeAdmissionCost.SCALAR_LIFECYCLE,wall,cpu,true);}
        boolean completed = false;
        try {
            original.call(batch, region, storage, list, camera, pass, cull);
            completed = true;
        } finally {
            // Runs after all native RETURN injections (including checked compaction), or on failure.
            wall=profile?NativeAdmissionCost.wallStart():0;cpu=NativeAdmissionCost.cpuStart(wall);
            try {scalar.shaderlod$leaveScalarFill(completed);}
            finally {
                NativeAdmissionCost.finish(NativeAdmissionCost.SCALAR_LIFECYCLE,wall,cpu,true);
                NativeAdmissionCost.finish(NativeAdmissionCost.FILL,fill,fillCpu,completed);
            }
        }
    }

    @WrapOperation(method = "fillCommandBuffer", at = @At(value = "INVOKE",
        target = "Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;clear()V"), require = 1, allow = 1)
    private static void begin(MultiDrawBatch batch, Operation<Void> original,
            @Local(argsOnly = true) TerrainRenderPass pass) {
        original.call(batch);
        long wall=NativeAdmissionCost.wallStart(),cpu=NativeAdmissionCost.cpuStart(wall);
        try {
        if ((pass == DefaultTerrainRenderPasses.SOLID || pass == DefaultTerrainRenderPasses.CUTOUT)
                && !ShadowRenderingState.areShadowsCurrentlyBeingRendered()
                && MakeUpQuadRuntime.scalarRecordingRequested()) {
            // Request selects a private recording mode, never admission. Quad RETURN and the
            // scalar consumer retain full current gates. No publication means native fallback.
            ((LegacyScalarState.Owner) (Object) batch).shaderlod$beginScalar(MakeUpQuadLifecycle.generation(),
                MakeUpQuadRuntime.compactionRecordingRequested());
        }
        } finally {NativeAdmissionCost.finish(NativeAdmissionCost.SCALAR_LIFECYCLE,wall,cpu,true);}
    }

    // Pinned Sodium 0.6.13: the unique live int local "facing" (native slot 11) supplies
    // the getter's face argument. Capture by name so another mixin's added locals cannot
    // silently select mask/size. The original getter/wrapper chain executes before this observer.
    @ModifyExpressionValue(method = "addNonIndexedDrawCommands(Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;JI)V",
        at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/data/SectionRenderDataUnsafe;getElementCount(JI)J"),
        require = 1, expect = 1, allow = 1, order = 1000)
    private static long count(long value, @Local(name = "facing") int face,
            @Local(argsOnly = true) int mask, @Local(argsOnly = true) MultiDrawBatch batch) {
        var scalar = ((LegacyScalarState.Owner) (Object) batch).shaderlod$recordingScalar();
        // Native writes every face but advances size only for masked-in faces, narrowing to signed int.
        if (scalar != null && face >= 0 && face < 32 && ((mask >>> face) & 1) != 0) {
            scalar.emitted((int) value);
        }
        return value;
    }
}

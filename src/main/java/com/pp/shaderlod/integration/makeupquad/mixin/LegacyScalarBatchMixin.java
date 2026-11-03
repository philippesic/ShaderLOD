package com.pp.shaderlod.integration.makeupquad.mixin;

import com.pp.shaderlod.integration.makeupquad.LegacyScalarBatch;
import com.pp.shaderlod.optimization.NativeAdmissionCost;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.pp.shaderlod.integration.makeupquad.LegacyScalarState;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime;
import com.pp.shaderlod.optimization.OptimizationId;
import com.pp.shaderlod.optimization.RendererWorkDiagnostics;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch", remap = false)
public abstract class LegacyScalarBatchMixin implements LegacyScalarBatch, LegacyScalarState.Owner {
    @Shadow public int size;
    @Shadow @Final private int capacity;
    @Unique private LegacyScalarState shaderlod$scalar;
    @Unique private LegacyScalarState shaderlod$recording;
    @Unique private int shaderlod$fillDepth;
    @Unique private boolean shaderlod$nestedFill;

    public int shaderlod$capacity() { return capacity; }
    public int shaderlod$size() { return size; }

    public LegacyScalarState shaderlod$scalar() {
        // The coalescer may publish with scalar caching disabled. It must not allocate or activate state.
        return shaderlod$scalar != null ? shaderlod$scalar : LegacyScalarState.INACTIVE;
    }

    public void shaderlod$enterScalarFill() {
        if (shaderlod$fillDepth++ != 0) shaderlod$nestedFill = true;
        shaderlod$recording = null;
        if (shaderlod$scalar != null) shaderlod$scalar.invalidate();
    }

    public void shaderlod$beginScalar(long generation) {
        shaderlod$beginScalar(generation, false);
    }

    public void shaderlod$beginScalar(long generation, boolean compactionOnly) {
        if (shaderlod$fillDepth != 1 || shaderlod$nestedFill) return;
        if (shaderlod$scalar == null) shaderlod$scalar = new LegacyScalarState();
        shaderlod$scalar.begin(true, capacity, generation, compactionOnly);
        shaderlod$recording = shaderlod$scalar.recording() ? shaderlod$scalar : null;
    }

    public LegacyScalarState shaderlod$recordingScalar() {
        return shaderlod$recording;
    }

    public void shaderlod$leaveScalarFill(boolean completed) {
        shaderlod$recording = null;
        if (shaderlod$scalar != null) {
            if (completed && shaderlod$fillDepth == 1 && !shaderlod$nestedFill) shaderlod$scalar.finish(size);
            else shaderlod$scalar.invalidate();
        }
        if (--shaderlod$fillDepth == 0) shaderlod$nestedFill = false;
    }

    @Inject(method = {"clear()V", "delete()V"}, at = @At("HEAD"), require = 2)
    private void invalidate(CallbackInfo ci) {
        shaderlod$recording = null;
        if (shaderlod$scalar != null) shaderlod$scalar.invalidate();
    }

    @WrapMethod(method="getIndexBufferSize", require=1)
    private int shaderlod$profileMaximum(Operation<Integer> original) {
        long wall=NativeAdmissionCost.ENABLED&&!ShadowRenderingState.areShadowsCurrentlyBeingRendered()?NativeAdmissionCost.wallStart():0;
        long cpu=NativeAdmissionCost.cpuStart(wall);boolean completed=false;
        try {int result=original.call();completed=true;return result;}
        finally {NativeAdmissionCost.finish(NativeAdmissionCost.MAXIMUM_QUERY,wall,cpu,completed);}
    }

    @Inject(method = "getIndexBufferSize()I", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void maximum(CallbackInfoReturnable<Integer> ci) {
        if (!ShadowRenderingState.areShadowsCurrentlyBeingRendered()
                && MakeUpQuadRuntime.enabledAtTerrainConsumer(OptimizationId.INDEX_SCALAR_CACHE)) {
            boolean hit = shaderlod$scalar != null && shaderlod$scalar.reusable(size, MakeUpQuadLifecycle.generation());
            if (RendererWorkDiagnostics.ENABLED) RendererWorkDiagnostics.scalarQuery(hit, size);
            if (hit) ci.setReturnValue(shaderlod$scalar.maximum());
        }
    }
}


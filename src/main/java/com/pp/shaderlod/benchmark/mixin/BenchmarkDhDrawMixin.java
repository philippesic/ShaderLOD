package com.pp.shaderlod.benchmark.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.pp.shaderlod.benchmark.BenchmarkLiveWork;
import net.irisshaders.iris.shadows.ShadowRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exact DH 3.3.3 ILWJGLService boundary, after native skip decisions, same pattern as the dev evidence hook. */
@Pseudo
@Mixin(targets = {"com.seibel.distanthorizons.common.render.openGl.terrain.GlDhTerrainShaderProgram",
        "com.seibel.distanthorizons.common.render.openGl.terrain.GlDhTerrainShaderProgram_neoforge"}, remap = false)
public abstract class BenchmarkDhDrawMixin {
    @Inject(method = "render(Lcom/seibel/distanthorizons/core/render/RenderParams;ZLcom/seibel/distanthorizons/core/util/objects/SortedArraySet;Lcom/seibel/distanthorizons/core/wrapperInterfaces/minecraft/IProfilerWrapper;)V",
            at = @At("HEAD"), require = 1, remap = false)
    private void shaderlod$benchmarkEntered(@Coerce Object params, boolean opaque, @Coerce Object buffers,
                                            @Coerce Object profiler, CallbackInfo ci) {
        if (BenchmarkLiveWork.enabled()) BenchmarkLiveWork.dhRendererEntered();
    }
    @WrapOperation(method = "render(Lcom/seibel/distanthorizons/core/render/RenderParams;ZLcom/seibel/distanthorizons/core/util/objects/SortedArraySet;Lcom/seibel/distanthorizons/core/wrapperInterfaces/minecraft/IProfilerWrapper;)V",
            at = @At(value = "INVOKE", target = "Lcom/seibel/distanthorizons/lwjgl/ILWJGLService;glDrawElements(IIII)V", ordinal = 0),
            require = 1, expect = 1, remap = false)
    private void shaderlod$benchmarkDraw(@Coerce Object service, int mode, int indices, int type, int offset,
                                         Operation<Void> original, @Coerce Object params, boolean opaque,
                                         @Coerce Object buffers, @Coerce Object profiler) {
        if (!BenchmarkLiveWork.enabled()) { original.call(service, mode, indices, type, offset); return; }
        boolean shadow = ShadowRenderer.ACTIVE;
        original.call(service, mode, indices, type, offset);
        BenchmarkLiveWork.dhSubmitted(indices, opaque, shadow);
    }
}

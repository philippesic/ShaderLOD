package com.pp.shaderlod.dev.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.pp.shaderlod.dev.timing.DhRenderEvidence;
import net.irisshaders.iris.shadows.ShadowRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DH 3.3.3: record the actual positive-index terrain draw delegate after all buffer skips. */
@Pseudo
@Mixin(targets = {
        "com.seibel.distanthorizons.common.render.openGl.terrain.GlDhTerrainShaderProgram",
        "com.seibel.distanthorizons.common.render.openGl.terrain.GlDhTerrainShaderProgram_neoforge"
}, remap = false)
public abstract class DhDrawEvidenceMixin {
    @Inject(method = "render(Lcom/seibel/distanthorizons/core/render/RenderParams;ZLcom/seibel/distanthorizons/core/util/objects/SortedArraySet;Lcom/seibel/distanthorizons/core/wrapperInterfaces/minecraft/IProfilerWrapper;)V",
            at = @At("HEAD"), require = 1, remap = false)
    private void shaderlod$noteTerrainEvidenceHook(@Coerce Object params, boolean opaque, @Coerce Object buffers,
                                                 @Coerce Object profiler, CallbackInfo callback) {
        if (DhRenderEvidence.enabled()) DhRenderEvidence.rendererEntered(getClass().getName(), opaque, ShadowRenderer.ACTIVE);
    }

    @WrapOperation(method = "render(Lcom/seibel/distanthorizons/core/render/RenderParams;ZLcom/seibel/distanthorizons/core/util/objects/SortedArraySet;Lcom/seibel/distanthorizons/core/wrapperInterfaces/minecraft/IProfilerWrapper;)V",
            at = @At(value = "INVOKE", target = "Lcom/seibel/distanthorizons/lwjgl/ILWJGLService;glDrawElements(IIII)V", ordinal = 0),
            require = 1, expect = 1, remap = false)
    private void shaderlod$recordTerrainDraw(@Coerce Object service, int mode, int indices, int type, int offset,
                                             Operation<Void> original, @Coerce Object params, boolean opaque,
                                             @Coerce Object buffers, @Coerce Object profiler) {
        boolean observe = DhRenderEvidence.enabled();
        boolean shadow = observe && ShadowRenderer.ACTIVE;
        original.call(service, mode, indices, type, offset);
        if (observe) DhRenderEvidence.drawSubmitted(indices, opaque, shadow);
    }
}

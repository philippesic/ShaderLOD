package com.pp.shaderlod.optimization.vrs.mixin;

import com.pp.shaderlod.optimization.vrs.BloomVrsRuntime;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets="net.irisshaders.iris.pipeline.CompositeRenderer",remap=false)
public abstract class BloomVrsDrawMixin {
    @Shadow @Final private RenderTargets renderTargets;
    @WrapOperation(method="renderAll()V",at=@At(value="INVOKE",
            target="Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V"),require=1,allow=1)
    private void shaderlod$vrsDraw(FullScreenQuadRenderer quad,Operation<Void> original) {
        if (!BloomVrsRuntime.requested()) { original.call(quad); return; }
        try (var scope=BloomVrsRuntime.beforeDraw(this,renderTargets)) { original.call(quad); }
    }
}

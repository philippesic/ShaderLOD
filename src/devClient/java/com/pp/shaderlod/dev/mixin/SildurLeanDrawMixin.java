package com.pp.shaderlod.dev.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.pp.shaderlod.dev.SildurLeanAlphaProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Private opt-in only: current sampler proof at the native Sodium delegate. */
@Mixin(targets="net.caffeinemc.mods.sodium.client.gl.device.GLRenderDevice$ImmediateDrawCommandList", remap=false)
public abstract class SildurLeanDrawMixin {
    @WrapOperation(method="multiDrawElementsBaseVertex", at=@At(value="INVOKE",
        target="Lorg/lwjgl/opengl/GL32C;nglMultiDrawElementsBaseVertex(IJIJIJ)V"), require=1, allow=1)
    private void shaderlod$leanAlpha(int mode,long counts,int type,long indices,int draws,long bases,Operation<Void> original) {
        try {
            SildurLeanAlphaProbe.draw();
            original.call(mode,counts,type,indices,draws,bases);
        } finally { SildurLeanAlphaProbe.revoke(); }
    }
}

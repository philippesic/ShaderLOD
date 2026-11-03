package com.pp.shaderlod.dev.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.pp.shaderlod.dev.NativeSubmitObserver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Order 900 must be verified as the innermost operation in the fresh exported target. */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.device.GLRenderDevice$ImmediateDrawCommandList", remap = false)
public abstract class NativeSubmitEntryMixin {
    @WrapOperation(method = "multiDrawElementsBaseVertex",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL32C;nglMultiDrawElementsBaseVertex(IJIJIJ)V"),
            order = 900, require = 1, allow = 1)
    private void shaderlod$native(int mode, long counts, int type, long indices, int drawCount,
                                  long bases, Operation<Void> original) {
        Object sample = null;
        try { sample = NativeSubmitObserver.nativeEntry(mode, counts, type, indices, drawCount, bases); }
        catch (Throwable observerFailure) {
            try { NativeSubmitObserver.externalFailure(observerFailure); } catch (Throwable reportingFailure) { /* Original execution keeps precedence. */ }
        }
        original.call(mode, counts, type, indices, drawCount, bases);
        try { NativeSubmitObserver.nativeReturned(sample); }
        catch (Throwable observerFailure) {
            try { NativeSubmitObserver.externalFailure(observerFailure); } catch (Throwable reportingFailure) { /* Original execution keeps precedence. */ }
        }
    }
}

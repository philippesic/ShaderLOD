package com.pp.shaderlod.dev.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.pp.shaderlod.dev.NativeSubmitObserver;
import net.caffeinemc.mods.sodium.client.gl.tessellation.GlIndexType;
import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import org.spongepowered.asm.mixin.Mixin;

/** Outer public-method finally includes normal return and exceptional production cleanup. */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.device.GLRenderDevice$ImmediateDrawCommandList", remap = false)
public abstract class NativeSubmitScopeMixin {
    @WrapMethod(method = "multiDrawElementsBaseVertex", require = 1)
    private void shaderlod$scope(MultiDrawBatch batch, GlIndexType type, Operation<Void> original) {
        Object scope = null;
        try { scope = NativeSubmitObserver.enter(); }
        catch (Throwable observerFailure) {
            try { NativeSubmitObserver.externalFailure(observerFailure); } catch (Throwable reportingFailure) { /* Original execution keeps precedence. */ }
        }
        try { original.call(batch, type); }
        finally {
            try { NativeSubmitObserver.leave(scope); }
            catch (Throwable observerFailure) {
            try { NativeSubmitObserver.externalFailure(observerFailure); } catch (Throwable reportingFailure) { /* Original execution keeps precedence. */ }
        }
        }
    }
}

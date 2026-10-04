package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.GpuTimers;
import com.pp.shaderlod.dev.timing.CpuTimers;
import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Preserve Iris's groups and use their pinned real program names as timer labels. */
@Mixin(value = CompositeRenderer.class, remap = false)
public abstract class CompositeRendererTimingMixin {
    @Redirect(method = "renderAll", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/gl/GLDebug;pushGroup(ILjava/lang/String;)V"), require = 2, remap = false)
    private void shaderlod$begin(int id, String name) {
        GLDebug.pushGroup(id, name);
        CpuTimers.beginIrisGroup(name);
        GpuTimers.beginIrisGroup(name);
    }

    @Redirect(method = "renderAll", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/gl/GLDebug;popGroup()V"), require = 3, remap = false)
    private void shaderlod$end() {
        GpuTimers.endIrisGroup();
        CpuTimers.endIrisGroup();
        GLDebug.popGroup();
    }
}

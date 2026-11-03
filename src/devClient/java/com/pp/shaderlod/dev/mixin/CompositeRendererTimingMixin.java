package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.GpuTimers;
import com.pp.shaderlod.dev.timing.CpuTimers;
import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Preserve Iris's groups and use their pinned real program names as timer labels. */
@Mixin(value = CompositeRenderer.class, remap = false)
public abstract class CompositeRendererTimingMixin {
    @Unique private static final boolean shaderlod$samplerProbe = Boolean.getBoolean("shaderlod.dev.cloudSamplerProbe");
    @Redirect(method = "renderAll", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/gl/GLDebug;pushGroup(ILjava/lang/String;)V"), require = 2, remap = false)
    private void shaderlod$begin(int id, String name) {
        com.pp.shaderlod.experiment.ComplementaryRasterR1.debugGroup(name);
        if (shaderlod$samplerProbe) com.pp.shaderlod.dev.timing.CloudSamplerStateProbe.beginNamedPass(name);
        GLDebug.pushGroup(id, name);
        CpuTimers.beginIrisGroup(name);
        GpuTimers.beginIrisGroup(name);
    }

    @Redirect(method = "renderAll", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/gl/GLDebug;popGroup()V"), require = 3, remap = false)
    private void shaderlod$end() {
        if (shaderlod$samplerProbe) com.pp.shaderlod.dev.timing.CloudSamplerStateProbe.endNamedPass();
        GpuTimers.endIrisGroup();
        CpuTimers.endIrisGroup();
        GLDebug.popGroup();
    }
}

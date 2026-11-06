package com.pp.shaderlod.dev.mixin;

import com.mojang.blaze3d.platform.Window;
import com.pp.shaderlod.benchmark.GlContextRequest;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Request a real GLFW context before window creation; all other hints stay native. */
@Mixin(Window.class)
public abstract class BenchmarkGlContextMixin {
    @ModifyArgs(method = "<init>", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V"))
    private void shaderlod$benchmarkContext(Args arguments) {
        var request = GlContextRequest.configured();
        if (request == null) return;
        int hint = arguments.get(0);
        if (hint == GLFW.GLFW_CONTEXT_VERSION_MAJOR) arguments.set(1, request.major());
        else if (hint == GLFW.GLFW_CONTEXT_VERSION_MINOR) arguments.set(1, request.minor());
    }
}

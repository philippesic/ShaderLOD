package com.pp.shaderlod.dev.mixin;

import com.mojang.blaze3d.platform.Window;
import com.pp.shaderlod.dev.BackgroundLaunch;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The early NeoForge splash is disabled by the launcher before this later window is made. */
@Mixin(Window.class)
public abstract class BackgroundWindowMixin {
    @Inject(method = "<init>", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/glfw/GLFW;glfwDefaultWindowHints()V", shift = At.Shift.AFTER))
    private void shaderlod$nonactivatingWindow(CallbackInfo callback) {
        if (!BackgroundLaunch.enabled()) return;
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
    }
}

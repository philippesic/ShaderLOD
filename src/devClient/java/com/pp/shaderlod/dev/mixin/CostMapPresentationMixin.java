package com.pp.shaderlod.dev.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.pp.shaderlod.dev.timing.CpuTimers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Child wall intervals within swap-and-events; never inferred GPU wait. */
@Mixin(RenderSystem.class)
public abstract class CostMapPresentationMixin {
    @Inject(method="flipFrame",at=@At(value="INVOKE",target="Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V"))
    private static void shaderlod$swapBegin(long window,CallbackInfo ci){CpuTimers.begin("swap-buffers");}
    @Inject(method="flipFrame",at=@At(value="INVOKE",target="Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V",shift=At.Shift.AFTER))
    private static void shaderlod$swapEnd(long window,CallbackInfo ci){CpuTimers.end("swap-buffers");}
    @Inject(method="pollEvents",at=@At("HEAD"))
    private static void shaderlod$eventsBegin(CallbackInfo ci){CpuTimers.begin("poll-events");}
    @Inject(method="pollEvents",at=@At("RETURN"))
    private static void shaderlod$eventsEnd(CallbackInfo ci){CpuTimers.end("poll-events");}
}

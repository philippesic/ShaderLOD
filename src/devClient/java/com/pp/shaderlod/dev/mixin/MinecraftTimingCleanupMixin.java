package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.GpuTimers;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftTimingCleanupMixin {
    @Inject(method = "close", at = @At("HEAD"))
    private void shaderlod$close(CallbackInfo ci) { GpuTimers.close(); }
}

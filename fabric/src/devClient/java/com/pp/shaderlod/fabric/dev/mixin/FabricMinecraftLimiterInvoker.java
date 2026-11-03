package com.pp.shaderlod.fabric.dev.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Pinned MC1.21.1 private effective limiter, not the newer AFK tracker API. */
@Mixin(Minecraft.class)
public interface FabricMinecraftLimiterInvoker {
    @Invoker("getFramerateLimit") int shaderlod$effectiveFramerateLimit();
}

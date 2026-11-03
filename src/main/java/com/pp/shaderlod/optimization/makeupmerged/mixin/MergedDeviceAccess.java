package com.pp.shaderlod.optimization.makeupmerged.mixin;
import net.caffeinemc.mods.sodium.client.gl.device.GLRenderDevice;
import net.caffeinemc.mods.sodium.client.gl.state.GlStateTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=GLRenderDevice.class,remap=false)
public interface MergedDeviceAccess {
 @Accessor("stateTracker") GlStateTracker shaderlod$stateTracker();
}

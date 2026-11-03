package com.pp.shaderlod.port.dev.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.pp.shaderlod.fabric.dev.budget.BslGpuBudget;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(GameRenderer.class)
public abstract class PortBslGpuFrameMixin {
    @WrapMethod(method="render(Lnet/minecraft/client/DeltaTracker;Z)V")
    private void shaderlod$nativeGpu(DeltaTracker tracker,boolean renderLevel,Operation<Void> original) {
        BslGpuBudget.before(renderLevel);
        boolean completed=false;
        try { original.call(tracker,renderLevel); completed=true; } finally { BslGpuBudget.after(completed); }
    }
}

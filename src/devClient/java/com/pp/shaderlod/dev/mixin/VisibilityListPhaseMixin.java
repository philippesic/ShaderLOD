package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.GpuTimers;
import com.pp.shaderlod.dev.timing.VisibilityListPhaseObserver;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A phase-boundary read after every original traversal; no cancellation or field writes. */
@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class VisibilityListPhaseMixin {
    @Shadow private SortedRenderLists renderLists;
    @Shadow private int lastUpdatedFrame;
    @Shadow @Final private ClientLevel level;

    @Inject(method = "createTerrainRenderList(Lnet/minecraft/client/Camera;Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;IZ)V",
            at = @At("RETURN"), require = 1, expect = 1, remap = false)
    private void shaderlod$observePhase(Camera camera, Viewport viewport, int traversalFrame,
                                       boolean spectator, CallbackInfo ci) {
        if (!VisibilityListPhaseObserver.enabled()) return;
        VisibilityListPhaseObserver.phaseBoundary(this, level, renderLists, lastUpdatedFrame, traversalFrame,
                ShadowRenderingState.areShadowsCurrentlyBeingRendered(), GpuTimers.frameId());
    }
}

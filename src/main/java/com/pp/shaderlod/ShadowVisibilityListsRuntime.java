package com.pp.shaderlod;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** All pack/config inspection stays outside the per-region getter. */
public final class ShadowVisibilityListsRuntime {
    private ShadowVisibilityListsRuntime() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        boolean enabled = com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled(com.pp.shaderlod.optimization.OptimizationId.SHADOW_VISIBILITY_LISTS);
        if (!enabled || Minecraft.getInstance().level == null) {
            ShadowVisibilityListsController.configure(false);
            return;
        }
        String pack = Iris.getCurrentPackName();
        boolean photon = com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.nativeGraphAvailable();
        ShadowVisibilityListsController.configure(photon && IrisApi.getInstance().isShaderPackInUse()
                && !Iris.isFallback());
    }
}

package com.pp.shaderlod;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** All pack/config inspection stays outside the per-region getter. */
public final class ShadowVisibilityListsRuntime {
    private ShadowVisibilityListsRuntime() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        boolean enabled = ShaderLodConfig.ENABLED.get() && ShaderLodConfig.SEPARATE_SHADOW_VISIBILITY_LISTS.get();
        if (!enabled || Minecraft.getInstance().level == null) {
            ShadowVisibilityListsController.configure(false);
            return;
        }
        String pack = Iris.getCurrentPackName();
        boolean photon = "Photon-ShaderLOD.zip".equals(pack) || "photon_v1.3b.zip".equals(pack);
        ShadowVisibilityListsController.configure(photon && IrisApi.getInstance().isShaderPackInUse()
                && !Iris.isFallback());
    }
}

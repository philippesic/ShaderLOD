package com.pp.shaderlod;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Eligibility/config inspection at client ticks; the hot culling path reads only cached state. */
public final class ShadowCullingRuntime {
    private ShadowCullingRuntime() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        boolean configured = ShaderLodConfig.ENABLED.get() && ShaderLodConfig.OPTIMIZE_SHADOW_CULLING.get();
        boolean coherence = ShaderLodConfig.ENABLED.get() && ShaderLodConfig.SHADOW_FRUSTUM_COHERENCE.get();
        ShadowCullingController.configure(configured);
        if ((!configured && !coherence) || Minecraft.getInstance().level == null) {
            ShadowCullingController.setEligible(false);
            ShadowFrustumCoherenceController.configure(false);
            return;
        }
        String pack = Iris.getCurrentPackName();
        boolean photon = "Photon-ShaderLOD.zip".equals(pack) || "photon_v1.3b.zip".equals(pack);
        boolean eligible = photon && IrisApi.getInstance().isShaderPackInUse() && !Iris.isFallback();
        ShadowCullingController.setEligible(eligible);
        ShadowFrustumCoherenceController.configure(coherence && eligible);
    }
}

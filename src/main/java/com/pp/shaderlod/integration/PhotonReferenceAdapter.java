package com.pp.shaderlod.integration;

import com.pp.shaderlod.lod.adapter.AdapterDescriptor;
import com.pp.shaderlod.lod.adapter.AdapterIdentity;
import com.pp.shaderlod.lod.adapter.ProtocolRequirement;
import com.pp.shaderlod.lod.adapter.WorkCapability;
import com.pp.shaderlod.lod.adapter.WorkKey;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** First explicit consumer declaration, kept out of generic resolver logic. No source support inferred. */
public final class PhotonReferenceAdapter {
    public static final AdapterIdentity IDENTITY = new AdapterIdentity("shaderlod:photon-volume-integration",
            "photon-v1.3b/shared-work-v1");
    public static final WorkKey VOLUME_INTEGRATION = LodOperations.VOLUME_INTEGRATION;

    private PhotonReferenceAdapter() {}

    public static AdapterDescriptor descriptor() {
        return new AdapterDescriptor(IDENTITY, List.of(
                new ProtocolRequirement("shaderlod:core", Set.of(2)),
                new ProtocolRequirement("shaderlod:effects", Set.of(1, 2)),
                new ProtocolRequirement("shaderlod:capabilities", Set.of(1))),
                List.of(new WorkCapability(VOLUME_INTEGRATION, new WorkCapability.CountFloor(8))));
    }

    /** Exact reviewed reference archives. Metadata and display names do not grant support. */
    public static List<SourceBinding> sourceBindings() {
        return List.of(
                new SourceBinding(IDENTITY, "5de7561dd24cdca80860a1d5f2c60400e74163a489cc163e916f3fd47feb27de",
                        Map.ofEntries(
                                Map.entry("LICENSE",
                                        "514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5"),
                                Map.entry("shaders/world0/deferred1.fsh",
                                        "23fc8e1c4a3bad9bf11b68b9d73ffb71db922b9f0438c5991f048e51635edee8"),
                                Map.entry("shaders/program/d1_clouds.fsh",
                                        "1f0dd330b837becff393380283eb767e3a55fe48e9b04705bf825fde535d0cae"),
                                Map.entry("shaders/include/sky/clouds.glsl",
                                        "07bac05a93b29cff55841d6f7425fe46d1555fe1e6b2cfc037d9fb9fee992972"),
                                Map.entry("shaders/include/sky/clouds/cumulus.glsl",
                                        "5290d3c0c56693608c6cfe8efec831b67023334729d4f549b3551f430603d8e2"),
                                Map.entry("shaders/settings.glsl",
                                        "b9b1c432b484d190c036f7cf243059f1f5d33c5c2c0564a9592c5323e05dd348"),
                                Map.entry("shaders/include/shaderlod/volume_budget.glsl",
                                        "cf37ff0af1b3ebb6cc3f3f95ae83c708518a8e48d03545e8665962d14cf731ac"),
                                Map.entry("shaders/include/shaderlod/photon_volume_adapter.glsl",
                                        "74349a36793081be528b34f9f047fc9ed195a8f2b12545caade0d4637af75905"),
                                Map.entry("shaders/include/shaderlod/effect_policy.glsl",
                                        "5df5427a1cfa51e68446978040235061ab6db60ec3f5079d40ffa0c39bad6943"),
                                Map.entry("shaders/include/shaderlod/work_policy.glsl",
                                        "1b70d1b34959778ac34ae9316797c23d858531ff366b55373bae1c7ee55ee309")
                        ), requiredOptions()),
                new SourceBinding(IDENTITY, "5f3772c8e97a89590b9a55a24d330de93543324befec6419aba9aed69991e5d3",
                        Map.ofEntries(
                                Map.entry("LICENSE",
                                        "514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5"),
                                Map.entry("shaders/world0/deferred1.fsh",
                                        "23fc8e1c4a3bad9bf11b68b9d73ffb71db922b9f0438c5991f048e51635edee8"),
                                Map.entry("shaders/program/d1_clouds.fsh",
                                        "1f0dd330b837becff393380283eb767e3a55fe48e9b04705bf825fde535d0cae"),
                                Map.entry("shaders/include/sky/clouds.glsl",
                                        "07bac05a93b29cff55841d6f7425fe46d1555fe1e6b2cfc037d9fb9fee992972"),
                                Map.entry("shaders/include/sky/clouds/cumulus.glsl",
                                        "5290d3c0c56693608c6cfe8efec831b67023334729d4f549b3551f430603d8e2"),
                                Map.entry("shaders/settings.glsl",
                                        "e69f2812c0c1ef49eeeef9d43c2748cfb06d173a9dfa0ef7d77daec09bc76160"),
                                Map.entry("shaders/include/shaderlod/volume_budget.glsl",
                                        "cf37ff0af1b3ebb6cc3f3f95ae83c708518a8e48d03545e8665962d14cf731ac"),
                                Map.entry("shaders/include/shaderlod/photon_volume_adapter.glsl",
                                        "74349a36793081be528b34f9f047fc9ed195a8f2b12545caade0d4637af75905"),
                                Map.entry("shaders/include/shaderlod/effect_policy.glsl",
                                        "5df5427a1cfa51e68446978040235061ab6db60ec3f5079d40ffa0c39bad6943"),
                                Map.entry("shaders/include/shaderlod/work_policy.glsl",
                                        "1b70d1b34959778ac34ae9316797c23d858531ff366b55373bae1c7ee55ee309")
                        ), requiredOptions()));
    }

    private static Map<String, Set<String>> requiredOptions() {
        return Map.of("SHADERLOD_VOLUMETRIC_INTEGRATION_LOD", Set.of("true"),
                "CLOUDS_CUMULUS", Set.of("true"), "BLOCKY_CLOUDS", Set.of("false"));
    }
}

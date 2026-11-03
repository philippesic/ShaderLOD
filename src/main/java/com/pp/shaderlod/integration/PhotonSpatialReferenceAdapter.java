package com.pp.shaderlod.integration;

import com.pp.shaderlod.lod.Effect;
import com.pp.shaderlod.lod.adapter.*;
import com.pp.shaderlod.lod.adapter.layout.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit frozen Photon spatial consumer. Declarations certify no driver allocations or savings. */
public final class PhotonSpatialReferenceAdapter {
    public static final WorkKey OPAQUE_SPATIAL_IRRADIANCE = new WorkKey(Effect.OPAQUE_LIGHTING,
            "shaderlod:opaque_spatial_irradiance", WorkKind.RATE);
    public static final AdapterIdentity IDENTITY = new AdapterIdentity("shaderlod:photon-shared-spatial",
            "v1.3b-shared-spatial-cf9acc6617dbfdf388b0c54894914242270ce8e582eeef565e0a7bd08039139e");
    public static final String PRIVATE_OPTION = "SHADERLOD_SHARED_SPATIAL";
    public static final String COMPOSITION = "shaderlod:photon_spatial_composition";
    public static final String CACHE = "shaderlod:photon_spatial_cache";
    public static final String AUXILIARY = "shaderlod:photon_spatial_auxiliary";
    public static final long MAXIMUM_EXACT_INTEGER = 1L << 24;
    private static final TargetSize FULL = TargetSize.relative(1, 1, TargetSize.Rounding.CEIL);

    private PhotonSpatialReferenceAdapter() {}

    public static AdapterDescriptor descriptor() {
        return new AdapterDescriptor(IDENTITY, List.of(
                new ProtocolRequirement("shaderlod:core", Set.of(2)),
                new ProtocolRequirement("shaderlod:effects", Set.of(2)),
                new ProtocolRequirement("shaderlod:capabilities", Set.of(1))),
                List.of(new WorkCapability(OPAQUE_SPATIAL_IRRADIANCE,
                        new WorkCapability.DiscreteRates(Set.of(1.0, 0.25)))));
    }

    public static List<CapabilityExport> exports() {
        return List.of(new CapabilityExport(OPAQUE_SPATIAL_IRRADIANCE, "shaderlodOpaqueSpatialAvailable"));
    }

    public static RuntimeLayoutRequirement layoutRequirement() {
        return new RuntimeLayoutRequirement(layout(), new Dimensions(2, 2), new Dimensions(8192, 8192));
    }

    /** Both frozen derivatives can load the private option ON; their effective default is checked separately. */
    public static List<SourceBinding> sourceBindings() {
        return List.of(new SourceBinding(IDENTITY,
                "db5a81b57e584237cc6e2f47434215a0d33ae365dde1677a82d3420971c861b9", consumingMembers(false), requiredOptions()),
                new SourceBinding(IDENTITY,
                "50c134eca19cdd587cc8e317c57e68b4c76ace5c9cb155970f7640cf4345fc99", consumingMembers(true), requiredOptions()));
    }

    private static Map<String, String> consumingMembers(boolean enabled) {
        return Map.ofEntries(
                Map.entry("LICENSE",
                        "514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5"),
                Map.entry("shaders/include/buffers.glsl",
                        "c096d28c2c2c5232fea8770af133f6dec22901d6a3c3e25b951dede10be5672b"),
                Map.entry("shaders/include/lighting/shadows/pcss.glsl",
                        "fb5a28fd5594c5465e3b4e1b30ccbdee61957a6c86d8e64fd70f047d28a9db9f"),
                Map.entry("shaders/include/lighting/shadows/ssrt.glsl",
                        "00b43e59666a8a7d1f35cb01c5463a4e6f624a1e7eeea8adc2d73810eb939a2c"),
                Map.entry("shaders/include/shaderlod/effect_policy.glsl",
                        "5df5427a1cfa51e68446978040235061ab6db60ec3f5079d40ffa0c39bad6943"),
                Map.entry("shaders/include/shaderlod/photon_linear_basis.glsl",
                        "c3dcddf89570759be947be1e98d6c2c983cfa4948d0df2bd0b1c1099b660b5a2"),
                Map.entry("shaders/include/shaderlod/photon_original_sky.glsl",
                        "830671927217110800d91f45c7f5074d20ea9820c03b783b068aaaf6efd8e629"),
                Map.entry("shaders/include/shaderlod/photon_spatial_adapter.glsl",
                        "f0e0c5917a061cf6d999b07c8c937424ad083104c694552d4f32050799f6bb78"),
                Map.entry("shaders/include/shaderlod/spatial_pipeline.glsl",
                        "89706bd84d258058dad6e81f05e125aed5af3019ca5b303d879a802037a4d124"),
                Map.entry("shaders/include/shaderlod/spatial_plan.glsl",
                        "b3e92b9a26f59c03dc1b14a90e5ab40012fe38fc5b226664429f7a138bab46c9"),
                Map.entry("shaders/include/shaderlod/surface_region_continuity.glsl",
                        "497c38b723df0255e9b07b5b62e695d644f0d0eb19538f14db5f95846e9cc040"),
                Map.entry("shaders/include/shaderlod/work_policy.glsl",
                        "1b70d1b34959778ac34ae9316797c23d858531ff366b55373bae1c7ee55ee309"),
                Map.entry("shaders/program/d4_deferred_shading.fsh",
                        "0c7bff3c6369df0601781ea08488e8a9cd2f6e7634e43b526ae2815c899a9def"),
                Map.entry("shaders/program/shaderlod_spatial_prepass.fsh",
                        "034f7cc924608720dd760397b4f621579a08ccdfee3cf595a90f1444cbefabae"),
                Map.entry("shaders/settings.glsl",
                        enabled ? "2aec76ec624573ee793834b5849f1d0cb1f215aca188750a82650373bb02e981"
                                : "bf612686d1c9b79a71e3b1338600d67739fb5455a0612c2b1caea944e324d186"),
                Map.entry("shaders/shaders.properties",
                        "cd83f7009b5b254865468fd3a3d3b7bbf621d143833fe7f3780de438fa29db47"),
                Map.entry("shaders/world-1/deferred4.fsh",
                        "965b7698e385459af2af916455b27f08b76583c694fa949138e62dd2cc61bad0"),
                Map.entry("shaders/world-1/deferred5.fsh",
                        "91a5c585d8756d1ad085e1faf6d5419e929f1e00336da436a5bf77323b6c8c56"),
                Map.entry("shaders/world-1/deferred5.vsh",
                        "c076400a410a980af1f505ec6f9bf863518141014a302b55c919215773113097"),
                Map.entry("shaders/world0/deferred4.fsh",
                        "70fe571207f38ead75d5223eab3088d3b11d0fa65894b64c73842e15069c5e69"),
                Map.entry("shaders/world0/deferred5.fsh",
                        "7e2ad5e02559b740b661f7d5fdd92fe32e54f40ed84ab0f7dad0e626236e5b95"),
                Map.entry("shaders/world0/deferred5.vsh",
                        "70e1a7a4657765cff24e50aa92be4ae481ea4994e961416cf7fe434ac0660187"),
                Map.entry("shaders/world1/deferred4.fsh",
                        "0cd8f95ff73c14e130e83705d322d8fefdd9e9ade7ffc7950fa455f74e26d8a6"),
                Map.entry("shaders/world1/deferred5.fsh",
                        "41246aedab1a3020c82bfb347b0a6efbeaca2a8c2b2efa199fca0c8f331c4cd0"),
                Map.entry("shaders/world1/deferred5.vsh",
                        "dd5d9becf6aae7cce4237fa2afcd339d30976457ff8f6500a7b844fdc7cd3975")
        );
    }

    private static Map<String, Set<String>> requiredOptions() {
        return Map.ofEntries(
                Map.entry("BLOCKY_CLOUDS", Set.of("false")),
                Map.entry("BORDER_FOG", Set.of("false")),
                Map.entry("CLOUD_SHADOWS", Set.of("false")),
                Map.entry("COLORED_LIGHTS", Set.of("false")),
                Map.entry("ENVIRONMENT_REFLECTIONS", Set.of("false")),
                Map.entry("GALAXY", Set.of("false")),
                Map.entry("NORMAL_MAPPING", Set.of("false")),
                Map.entry("SHADERLOD_SHARED_SPATIAL", Set.of("true")),
                Map.entry("SHADOW_COLOR", Set.of("false")),
                Map.entry("SHADOW_VPS", Set.of("false")),
                Map.entry("SH_SKYLIGHT", Set.of("false")),
                Map.entry("SKY_REFLECTIONS", Set.of("false")),
                Map.entry("SPECULAR_MAPPING", Set.of("false")),
                Map.entry("TAAU", Set.of("false"))
        );
    }

    /** Producer logical MRT 0/2, then consumer 0/3 or conditional target 0 alone. */
    public static AdapterLayout layout() {
        var quantity = new TargetDeclaration.Quantity("shaderlod:opaque_spatial_linear_basis",
                TargetDeclaration.Role.LINEAR_QUANTITY, 4);
        var certificate = new TargetDeclaration.Quantity("shaderlod:opaque_spatial_certificate",
                TargetDeclaration.Role.EXACT_BINARY32_BOUNDED_INTEGER_METADATA, 3, MAXIMUM_EXACT_INTEGER);
        var minimumDistance = new TargetDeclaration.Quantity("shaderlod:opaque_spatial_minimum_distance",
                TargetDeclaration.Role.BINARY32_MINIMUM_DISTANCE, 1);
        return new AdapterLayout(List.of(
                new TargetDeclaration(COMPOSITION, FULL, TargetFormat.R11F_G11F_B10F,
                        new TargetDeclaration.Quantity("shaderlod:photon_composition_rgb",
                                TargetDeclaration.Role.GENERAL, 3)),
                new TargetDeclaration(CACHE, FULL, TargetFormat.RGBA32F,
                        List.of(quantity, certificate, minimumDistance)),
                new TargetDeclaration(AUXILIARY, FULL, TargetFormat.RGBA8,
                        new TargetDeclaration.Quantity("shaderlod:photon_auxiliary_rgba",
                                TargetDeclaration.Role.GENERAL, 4))), List.of(
                new PassDeclaration("shaderlod:photon_spatial_producer",
                        List.of(new PassDeclaration.Read(COMPOSITION, PassDeclaration.Fetch.FLOAT_TEXEL_FETCH),
                                new PassDeclaration.Read(CACHE, PassDeclaration.Fetch.FLOAT_TEXEL_FETCH)),
                        List.of(new PassDeclaration.DrawGroup(List.of(COMPOSITION, CACHE), FULL)),
                        PassDeclaration.FeedbackPolicy.DISTINCT_IMAGES_EXTERNALLY_VERIFIED),
                new PassDeclaration("shaderlod:photon_spatial_consumer",
                        List.of(new PassDeclaration.Read(COMPOSITION, PassDeclaration.Fetch.FLOAT_TEXEL_FETCH),
                                new PassDeclaration.Read(CACHE, PassDeclaration.Fetch.FLOAT_TEXEL_FETCH),
                                new PassDeclaration.Read(AUXILIARY, PassDeclaration.Fetch.FLOAT_TEXEL_FETCH)),
                        List.of(new PassDeclaration.DrawGroup(List.of(COMPOSITION, AUXILIARY), FULL),
                                new PassDeclaration.DrawGroup(List.of(COMPOSITION), FULL)),
                        PassDeclaration.FeedbackPolicy.DISTINCT_IMAGES_EXTERNALLY_VERIFIED)));
    }

    /** Matches the shader cache_contract domain; no declaration can establish physical feedback safety. */
    public static boolean acceptsRenderedDimensions(Dimensions renderedSurface) {
        return layoutRequirement().accepts(renderedSurface);
    }
}

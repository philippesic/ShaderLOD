package com.pp.shaderlod.optimization;

/** Stable persistence IDs and GLSL120-safe individual integer uniforms. No ordinal protocol. */
public enum OptimizationId {
    CLOUD_SCATTERING_ORDER("cloud_scattering_order", "CloudScatteringOrder", false),
    VOLUME_LIGHT_REUSE("volume_light_reuse", "VolumeLightReuse", false),
    PROCEDURAL_WATER("procedural_water", "ProceduralWater", false),
    AO_HISTORY_REUSE("ao_history_reuse", "AoHistoryReuse", false),
    AO_DEPTH_REUSE("ao_depth_reuse", "AoDepthReuse", false),
    REFLECTION_TRACE_BUDGET("reflection_trace_budget", "ReflectionTraceBudget", false),
    SPATIAL_LIGHTING_REUSE("spatial_lighting_reuse", "SpatialLightingReuse", false),
    SPATIAL_SHADOW_REUSE("spatial_shadow_reuse", "SpatialShadowReuse", false),
    REFLECTION_PROJECTED_REFINEMENT("reflection_projected_refinement", "ReflectionProjectedRefinement", false),
    SHADOW_SAMPLE_BUDGET("shadow_sample_budget", "ShadowSampleBudget", false),
    REFLECTION_CLOUD_DETAIL("reflection_cloud_detail", "ReflectionCloudDetail", true),
    REFLECTION_CLOUD_BUDGET("reflection_cloud_budget", "ReflectionCloudBudget", true),
    WATER_SHADOW_ZERO("water_shadow_zero", "WaterShadowZero", false),
    SKY_ATMOSPHERE_OWNERSHIP("sky_atmosphere_ownership", "SkyAtmosphereOwnership", false),
    WATER_WAVE_BUDGET("water_wave_budget", "WaterWaveBudget", false),
    SPATIAL_LIGHTING_RATE("spatial_lighting_rate", "SpatialLightingRate", false),
    ZERO_EMISSION_ARITHMETIC("zero_emission_arithmetic", "ZeroEmissionArithmetic", false),
    TEMPORAL_SHADOW_FILTER("temporal_shadow_filter", "TemporalShadowFilter", false),
    REFLECTED_CLOUD_DISTANCE_EXIT("reflected_cloud_distance_exit", "ReflectedCloudDistanceExit", false),
    POM_HEIGHT_CACHE("pom_height_cache", "PomHeightCache", false),
    SHADOW_RECEIVER_FOOTPRINT("shadow_receiver_footprint", "ShadowReceiverFootprint", false),
    SHADOW_MIN_FIELD("shadow_min_field", "ShadowMinField", false),
    SHADOW_RAW_QUERY("shadow_raw_query", "ShadowRawQuery", false),
    AO_RENDER_SCALE("ao_render_scale", "AoRenderScale", false),
    ROUGH_REFLECTION_GATHER("rough_reflection_gather", "RoughReflectionGather", false),
    SSR_PROJECTED_FOOTPRINT_EXIT("ssr_projected_footprint_exit", "SsrProjectedFootprintExit", false),
    SSR_PROJECTION_ARITHMETIC("ssr_projection_arithmetic", "SsrProjectionArithmetic", false),
    AO_SKY_ELISION("ao_sky_elision", "AoSkyElision", false),
    WATER_PARALLAX_BUDGET("water_parallax_budget", "WaterParallaxBudget", false),
    REFLECTION_DEFERRED_METRIC("reflection_deferred_metric", "ReflectionDeferredMetric", false),
    DEAD_MIP_CHAIN("dead_mip_chain", "DeadMipChain", false),
    DEAD_FRAME_COPY("dead_frame_copy", "DeadFrameCopy", false),
    POM_EMPTY_HEIGHT_PREFIX("pom_empty_height_prefix", "PomEmptyHeightPrefix", false),
    REFLECTION_NEXT_EVENT_REFINEMENT("reflection_next_event_refinement", "ReflectionNextEventRefinement", false),
    REFLECTION_RAY_BUDGET("reflection_ray_budget", "ReflectionRayBudget", false),
    CLOUD_SECONDARY_EXTENT_CACHE("cloud_secondary_extent_cache", "CloudSecondaryExtentCache", false),
    BINDING_CERTIFICATE_CACHE("binding_certificate_cache", "BindingCertificateCache", true),
    BINDING_DIGEST_STREAMING("binding_digest_streaming", "BindingDigestStreaming", false),
    CLOUD_PRIMARY_DETAIL("cloud_primary_detail", "CloudPrimaryDetail", true),
    CLOUD_PRIMARY_BUDGET("cloud_primary_budget", "CloudPrimaryBudget", true),
    CLOUD_SECONDARY_DETAIL("cloud_secondary_detail", "CloudSecondaryDetail", true),
    CLOUD_SECONDARY_BUDGET("cloud_secondary_budget", "CloudSecondaryBudget", true),
    CLOUD_OPACITY_EXIT("cloud_opacity_exit", "CloudOpacityExit", true),
    VOLUMETRIC_LIGHT_BUDGET("volumetric_light_budget", "VolumetricLightBudget", true),
    REFLECTION_TRACE_FADE("reflection_trace_fade", "ReflectionTraceFade", false),
    REFLECTION_REFINEMENT("reflection_refinement", "ReflectionRefinement", true),
    AURORA_BUDGET("aurora_budget", "AuroraBudget", true),
    AO_BUDGET("ao_budget", "AoBudget", true),
    PIXEL_SCALE("pixel_scale", "PixelScale", true),
    PROJECTED_POM("projected_pom", "ProjectedPom", true),
    QUAD_COMMAND_COMPACTION("quad_command_compaction", "QuadCommandCompaction", true),
    INDEX_SCALAR_CACHE("index_scalar_cache", "IndexScalarCache", false),
    SHADOW_ADMISSION_CACHE("shadow_admission_cache", "ShadowAdmissionCache", false),
    SHADOW_POSITIVE_VERTEX("shadow_positive_vertex", "ShadowPositiveVertex", false),
    SHADOW_VISIBILITY_LISTS("shadow_visibility_lists", "ShadowVisibilityLists", false),
    SHADOW_FRUSTUM_COHERENCE("shadow_frustum_coherence", "ShadowFrustumCoherence", false),
    TERRAIN_RADIUS("terrain_radius", "TerrainRadius", false),
    TRANSLUCENT_FASTPATH("translucent_fastpath", "TranslucentFastpath", false),
    AO_ATTENUATION("ao_attenuation", "AoAttenuation", false),
    EARLY_ALPHA("early_alpha", "EarlyAlpha", false),
    DEPTH_PRIMING("depth_priming", "DepthPriming", false),
    MERGED_TERRAIN("merged_terrain", "MergedTerrain", false),
    SHADOW_PREFIX("shadow_prefix", "ShadowPrefix", false),
    PHYSICAL_RENDER_SCALE("physical_render_scale", "PhysicalRenderScale", false);
    /** Source-backed compatibility aliases; values() exposes each measured transform once. */
    public static final OptimizationId QUAD_INDEX_FILL=QUAD_COMMAND_COMPACTION;
    public static final OptimizationId QUAD_SUBMISSION_COPY=QUAD_COMMAND_COMPACTION;
    public static final OptimizationId COMMAND_METADATA_CACHE=SHADOW_ADMISSION_CACHE;
    public static java.util.Map<String,OptimizationId> keyAliases() {
        return java.util.Map.of("quad_index_fill",QUAD_COMMAND_COMPACTION,"quad_submission_copy",QUAD_COMMAND_COMPACTION,"command_metadata_cache",SHADOW_ADMISSION_CACHE);
    }
    public static java.util.Map<String,OptimizationId> uniformAliases() {
        return java.util.Map.of("shaderlodOptQuadIndexFill",QUAD_COMMAND_COMPACTION,"shaderlodOptQuadSubmissionCopy",QUAD_COMMAND_COMPACTION,"shaderlodOptCommandMetadataCache",SHADOW_ADMISSION_CACHE);
    }
    private final String key, uniform; private final boolean defaultEnabled;
    OptimizationId(String key, String suffix, boolean defaultEnabled) {
        this.key=key; this.uniform="shaderlodOpt"+suffix; this.defaultEnabled=defaultEnabled;
    }
    public String key() { return key; }
    public String uniform() { return uniform; }
    public boolean defaultEnabled() { return defaultEnabled; }
    public static OptimizationId fromKey(String key) {
        for (var id:values()) if(id.key.equals(key)) return id;
        var alias=keyAliases().get(key);if(alias!=null)return alias;
        throw new IllegalArgumentException("Unknown optimization: "+key);
    }
}

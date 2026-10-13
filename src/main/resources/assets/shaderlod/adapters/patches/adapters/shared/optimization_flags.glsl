#ifndef SHADERLOD_OPTIMIZATION_FLAGS_INCLUDED
#define SHADERLOD_OPTIMIZATION_FLAGS_INCLUDED
// Individual integers keep this transport valid in GLSL120. Missing version/flags retain native work.
uniform int shaderlodOptimizationFlagsVersion;
uniform int shaderlodOptReflectionCloudDetail;
uniform int shaderlodOptReflectionCloudBudget;

uniform int shaderlodOptReflectionProjectedRefinement;
uniform int shaderlodOptShadowSampleBudget;

uniform int shaderlodOptCloudScatteringOrder;
uniform int shaderlodOptVolumeLightReuse;
uniform int shaderlodOptProceduralWater;
uniform int shaderlodOptAoHistoryReuse;
uniform int shaderlodOptAoDepthReuse;
uniform int shaderlodOptReflectionTraceBudget;
uniform int shaderlodOptSpatialLightingReuse;
uniform int shaderlodOptSpatialShadowReuse;

uniform int shaderlodOptCloudPrimaryDetail;
uniform int shaderlodOptCloudPrimaryBudget;
uniform int shaderlodOptCloudSecondaryDetail;
uniform int shaderlodOptCloudSecondaryBudget;
uniform int shaderlodOptCloudOpacityExit;
uniform int shaderlodOptVolumetricLightBudget;
uniform int shaderlodOptReflectionRefinement;
uniform int shaderlodOptReflectionTraceFade;
uniform int shaderlodOptAuroraBudget;
uniform int shaderlodOptAoBudget;
uniform int shaderlodOptPixelScale;
uniform int shaderlodOptProjectedPom;
uniform int shaderlodOptQuadIndexFill;
uniform int shaderlodOptQuadSubmissionCopy;
uniform int shaderlodOptQuadCommandCompaction;
uniform int shaderlodOptIndexScalarCache;
uniform int shaderlodOptCommandMetadataCache;
uniform int shaderlodOptShadowPositiveVertex;
uniform int shaderlodOptShadowVisibilityLists;
uniform int shaderlodOptShadowFrustumCoherence;
uniform int shaderlodOptTerrainRadius;
uniform int shaderlodOptEarlyAlpha;
uniform int shaderlodOptDepthPriming;
uniform int shaderlodOptMergedTerrain;
uniform int shaderlodOptShadowPrefix;
uniform int shaderlodOptPhysicalRenderScale;
bool shaderlod_optimization_enabled(int flag) {
    return shaderlodOptimizationFlagsVersion == 1 && flag == 1 &&
        shaderlodBridgeVersion == 2 && shaderlodEnabled == 1 &&
        shaderlodPreset >= 0 && shaderlodPreset <= 2;
}
#endif

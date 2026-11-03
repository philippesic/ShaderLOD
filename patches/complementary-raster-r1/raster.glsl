// ShaderLOD private Complementary raster R1, fixed .75; source prototype.
// Upstream algorithms remain Complementary Shaders by EminGT.
// No distance band: side scale is dimensionless. Off/Quality/unknown bridge,
// unknown option map, native AA off and unsupported runtime retain native paths.
// Geometry and current/history changes require a renderer-owned epoch reset.
#ifndef SHADERLOD_RASTER_R1_HELPER
#define SHADERLOD_RASTER_R1_HELPER
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;
uniform int shaderlodPreset;
uniform int shaderlodRasterR1Active;
uniform sampler2D colortex9;
uniform sampler2D colortex10;
bool shaderlodRasterR1On() {
#if SHADERLOD_RASTER_R1 == 1 && defined OVERWORLD && defined TAA && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH && !defined SHADOW && !defined SHADOWCOMP
    return shaderlodRasterR1Active == 1 && shaderlodBridgeVersion == 2 &&
           shaderlodEnabled == 1 && shaderlodPreset >= 0 && shaderlodPreset <= 2;
#else
    return false;
#endif
}
vec2 shaderlodRasterR1Output() { return vec2(viewWidth, viewHeight); }
vec2 shaderlodRasterR1Size(vec2 allocation) {
    return shaderlodRasterR1On() ? max(floor(allocation * .75), vec2(1.0)) : allocation;
}
vec2 shaderlodRasterR1Scene() { return shaderlodRasterR1Size(shaderlodRasterR1Output()); }
vec2 shaderlodRasterR1Uv(sampler2D target, vec2 uv) {
    vec2 allocation = vec2(textureSize(target, 0));
    vec2 activeSize = shaderlodRasterR1Size(allocation);
    return clamp(uv * activeSize, vec2(.5), activeSize - .5) / allocation;
}
vec4 shaderlodRasterR1Texture(sampler2D target, vec2 uv) {
    // Uniform control only. Newly introduced current reads use explicit base
    // LOD; no implicit derivatives inside native per-fragment ray branches.
    if (shaderlodRasterR1On()) return texture2DLod(target, shaderlodRasterR1Uv(target, uv), 0.0);
    return texture2D(target, uv);
}
vec4 shaderlodRasterR1Lod(sampler2D target, vec2 uv, float lod) {
    if (shaderlodRasterR1On()) return texture2DLod(target, shaderlodRasterR1Uv(target, uv), lod);
    return texture2DLod(target, uv, lod);
}
vec4 shaderlodRasterR1ReflectionLod(sampler2D target, vec2 uv, float lod) {
#if defined COMPOSITE
    if (shaderlodRasterR1On()) return texture2DLod(colortex10, uv, lod);
#endif
    return texture2DLod(target, uv, lod);
}
vec4 shaderlodRasterR1Bloom(sampler2D target, vec2 uv, vec2 gradient) {
    // Native c4 full-screen noperspective UV is affine. Its BloomTile caller
    // passes the exact tile scale * output-rescale / output-extent gradients.
    // This remains defined inside native tile bounds; no new derivatives.
    if (shaderlodRasterR1On()) return textureGrad(colortex9, uv, vec2(gradient.x, 0.0), vec2(0.0, gradient.y));
    return texture2D(target, uv);
}

ivec2 shaderlodRasterR1Texel(sampler2D target, ivec2 coord) {
    if (!shaderlodRasterR1On()) return coord;
    ivec2 allocation = textureSize(target, 0);
    ivec2 activeSize = ivec2(shaderlodRasterR1Size(vec2(allocation)));
#if defined GBUFFERS_ARMOR_GLINT || defined GBUFFERS_BASIC || defined GBUFFERS_BEACONBEAM || defined GBUFFERS_BLOCK || defined GBUFFERS_BLOCK_TRANSLUCENT || defined GBUFFERS_CLOUDS || defined GBUFFERS_DAMAGEDBLOCK || defined GBUFFERS_ENTITIES || defined GBUFFERS_ENTITIES_GLOWING || defined GBUFFERS_ENTITIES_TRANSLUCENT || defined GBUFFERS_HAND || defined GBUFFERS_LIGHTNING || defined GBUFFERS_LINE || defined GBUFFERS_SKYBASIC || defined GBUFFERS_SKYTEXTURED || defined GBUFFERS_SPIDEREYES || defined GBUFFERS_TERRAIN || defined GBUFFERS_TEXTURED || defined GBUFFERS_WATER || defined GBUFFERS_WEATHER || defined DEFERRED1 || defined COMPOSITE || defined COMPOSITE1 || defined COMPOSITE3
    return clamp(coord, ivec2(0), activeSize - 1);
#else
    return clamp(ivec2((vec2(coord) + .5) * vec2(activeSize) / shaderlodRasterR1Output()), ivec2(0), activeSize - 1);
#endif
}
vec4 shaderlodRasterR1Fetch(sampler2D target, ivec2 coord, int lod) {
    return texelFetch(target, shaderlodRasterR1Texel(target, coord), lod);
}
#if defined GBUFFERS_ARMOR_GLINT || defined GBUFFERS_BASIC || defined GBUFFERS_BEACONBEAM || defined GBUFFERS_BLOCK || defined GBUFFERS_BLOCK_TRANSLUCENT || defined GBUFFERS_CLOUDS || defined GBUFFERS_DAMAGEDBLOCK || defined GBUFFERS_ENTITIES || defined GBUFFERS_ENTITIES_GLOWING || defined GBUFFERS_ENTITIES_TRANSLUCENT || defined GBUFFERS_HAND || defined GBUFFERS_LIGHTNING || defined GBUFFERS_LINE || defined GBUFFERS_SKYBASIC || defined GBUFFERS_SKYTEXTURED || defined GBUFFERS_SPIDEREYES || defined GBUFFERS_TERRAIN || defined GBUFFERS_TEXTURED || defined GBUFFERS_WATER || defined GBUFFERS_WEATHER || defined DEFERRED1 || defined COMPOSITE || defined COMPOSITE1 || defined COMPOSITE3
    #define viewWidth (shaderlodRasterR1Scene().x)
    #define viewHeight (shaderlodRasterR1Scene().y)
#endif
#endif

uniform int shaderlodOptSpatialLightingRate;
uniform int shaderlodBlissSpatialReady;
uniform vec2 shaderlodBlissSpatialSize;
#include "/lib/shaderlod/bliss_surface_continuity.glsl"
uniform int shaderlodEffectsVersion;
uniform int shaderlodOpaqueLightingMode;
uniform float shaderlodOpaqueLightingNear;
uniform float shaderlodOpaqueLightingFar;
uniform float shaderlodOpaqueLightingTransition;
uniform float shaderlodOpaqueLightingMediumWorkRatio;
uniform float shaderlodOpaqueLightingFarWorkRatio;

bool shaderlod_bliss_rate_finite(float x) { return !isnan(x) && !isinf(x); }
bool shaderlod_bliss_rate_ratio(float x) {
    // Legal realized rates are K4/full and K1/four-pixel tile. K2/K3/zero unsupported.
    return shaderlod_bliss_rate_finite(x) && (x == 1.0 || (x > 0.0 && x <= 0.25));
}
bool shaderlod_bliss_rate_parameters(out vec3 bands, out vec2 ratios) {
    bands = vec3(shaderlodNear, shaderlodFar, shaderlodTransition);
    ratios = vec2(1.0, 0.25); // Explicit private-pack INHERIT policy; public option defaults off.
    if (shaderlodBridgeVersion != 2 || shaderlodEnabled != 1 || shaderlodEffectsVersion != 2)
        return false;
    if (shaderlodOpaqueLightingMode == 1) return false; // FULL
    if (shaderlodOpaqueLightingMode == 2) {
        bands = vec3(shaderlodOpaqueLightingNear, shaderlodOpaqueLightingFar, shaderlodOpaqueLightingTransition);
        ratios = vec2(shaderlodOpaqueLightingMediumWorkRatio, shaderlodOpaqueLightingFarWorkRatio);
    } else if (shaderlodOpaqueLightingMode != 0) return false;
    return !any(isnan(bands)) && !any(isinf(bands)) && bands.x >= 1.0 && bands.y > bands.x &&
        bands.z >= 0.0 && bands.z <= min(2.0 * bands.x, bands.y - bands.x) &&
        shaderlod_bliss_rate_finite(bands.y + 0.5 * bands.z) &&
        shaderlod_bliss_rate_ratio(ratios.x) && shaderlod_bliss_rate_ratio(ratios.y) && ratios.y <= ratios.x;
}
float shaderlod_bliss_rate_weight(float distance_blocks) {
    vec3 bands; vec2 ratios;
    if (!shaderlod_bliss_rate_parameters(bands, ratios) || !shaderlod_bliss_rate_finite(distance_blocks)) return 0.0;
    float half_width = 0.5 * bands.z;
    float medium = bands.z == 0.0 ? float(distance_blocks >= bands.x) :
        smoothstep(bands.x - half_width, bands.x + half_width, distance_blocks);
    float far_weight = bands.z == 0.0 ? float(distance_blocks >= bands.y) :
        smoothstep(bands.y - half_width, bands.y + half_width, distance_blocks);
    return ratios.x <= 0.25 ? medium : (ratios.y <= 0.25 ? far_weight : 0.0);
}

int shaderlodBlissSpatialMode() {
#if SHADERLOD_BLISS_SPATIAL_REUSE == 1
    return shaderlod_optimization_enabled(shaderlodOptSpatialLightingReuse)?1:0;
#elif SHADERLOD_BLISS_LIGHTING_RATE == 1
    return shaderlod_optimization_enabled(shaderlodOptSpatialLightingRate)?2:0;
#else
    return 0;
#endif
}
bool shaderlodBlissSpatialActive() {
    return shaderlodBlissSpatialMode()>0 && shaderlodBlissSpatialReady==1 &&
        shaderlodBlissSpatialSize==vec2(viewWidth,viewHeight) &&
        viewWidth>=2.0 && viewHeight>=2.0 && viewWidth<=8192.0 && viewHeight<=8192.0 &&
        frameCounter>=0 && frameCounter<=16777216 && isEyeInWater==0;
}
bool shaderlodBlissSpatialTile(ivec2 origin) {
    if(!shaderlodBlissSpatialActive() || any(lessThan(origin,ivec2(0))) ||
        
#if SHADERLOD_BLISS_PIXEL_GRAPH == 1
any(greaterThanEqual(vec2(origin+ivec2(1))+0.5,vec2(viewWidth,viewHeight)*RENDER_SCALE))
#else
any(greaterThanEqual(origin+ivec2(1),ivec2(viewWidth,viewHeight)))
#endif
)return false;
    vec3 positions[4];vec3 normals[4];int materials[4];vec2 lightmaps[4];bool protectedMembers[4];
    float aoMin=1.0,aoMax=0.0;
    for(int i=0;i<4;i++) {
        ivec2 pixel=origin+ivec2(i-(i/2)*2,i/2);
        vec2 uv=(vec2(pixel)+0.5)*texelSize;
        float depth=texelFetch2D(depthtex1,pixel,0).r;
        vec4 shaderlodBlissSpatialPackedData=texelFetch2D(colortex1,pixel,0);
        vec4 d0=vec4(decodeVec2(shaderlodBlissSpatialPackedData.x),decodeVec2(shaderlodBlissSpatialPackedData.y));
        vec4 d1=vec4(decodeVec2(shaderlodBlissSpatialPackedData.z),decodeVec2(shaderlodBlissSpatialPackedData.w));
        // Exact native normalized-coordinate sample under current sampler LOD parity.
        vec4 spec=texture2DLod(colortex8,uv,0.0);
        vec4 flatData=texelFetch2D(colortex15,pixel,0);
        float translucent=texture2DLod(colortex7,uv,0.0).a;
        positions[i]=toScreenSpace(vec3(uv/RENDER_SCALE-TAA_Offset*texelSize*0.5,depth));
        normals[i]=normalize(worldToView(decode(d0.yw)));
        materials[i]=int(floor(d1.w*255.0+0.5));lightmaps[i]=min(max(d1.yz-0.05,0.0)*1.06,1.0);
        float labSss=clamp((-65.0+spec.z*255.0)/190.0,0.0,1.0);
        // Material0 is ordinary opaque terrain; every special native mask stays full.
        protectedMembers[i]=!(depth>0.0 && depth<1.0 && d1.w>=0.0 && d1.w<0.01 &&
            translucent==0.0 && labSss==0.0 && spec.a==0.0 && spec.r>=0.0 && spec.r<=0.25 &&
            spec.g>=0.0 && spec.g<229.5/255.0 && all(greaterThanEqual(flatData,vec4(0.0))) &&
            all(lessThanEqual(flatData,vec4(1.0))) && shaderlod_bliss_rate_weight(length(positions[i]))>=1.0);
        vec2 nativeAo=texelFetch2D(colortex14,pixel,0).rg;
        if(!all(greaterThanEqual(nativeAo,vec2(0.0))) || !all(lessThanEqual(nativeAo,vec2(1.0))))return false;
        aoMin=min(aoMin,nativeAo.r);aoMax=max(aoMax,nativeAo.r);
    }
    // Native bilateral AO visits the center and four diagonal neighbors. The
    // four-member union is bounded by this complete4x4 rectangle; reject edges
    // before any fetch. Checking centers alone would miss adjacent AO changes.
    if(any(lessThan(origin-ivec2(1),ivec2(0))) ||
        
#if SHADERLOD_BLISS_PIXEL_GRAPH == 1
any(greaterThanEqual(vec2(origin+ivec2(2))+0.5,vec2(viewWidth,viewHeight)*RENDER_SCALE))
#else
any(greaterThanEqual(origin+ivec2(2),ivec2(viewWidth,viewHeight)))
#endif
)return false;
    for(int y=-1;y<=2;y++)for(int x=-1;x<=2;x++) {
        vec2 a=texelFetch2D(colortex14,origin+ivec2(x,y),0).rg;
        if(!all(greaterThanEqual(a,vec2(0.0))) || !all(lessThanEqual(a,vec2(1.0))))return false;
        aoMin=min(aoMin,a.r);aoMax=max(aoMax,a.r);
    }
    // Same established geometry/material classifier; AO spread is heuristic only,
    // not a claim that the tile cannot cross a thin cast-shadow boundary.
    return shaderlod_surface_tile_eligible(true,positions,normals,materials,lightmaps,
        protectedMembers,max(256.0,shaderlodNear),true,vec2(aoMax-aoMin,0.0),shaderlod_surface_default_limits());
}

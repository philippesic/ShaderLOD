// Native AstraLex forward lighting basis; exact native color tail retained.
uniform int shaderlodAstraSpatialPhase;
uniform float shaderlodAstraSpatialAlphaRef;
uniform int shaderlodAstraSpatialMode;
uniform vec2 shaderlodAstraSpatialSize;
uniform sampler2D shaderlodAstraSpatial0;
uniform sampler2D shaderlodAstraSpatial1;
uniform sampler2D shaderlodAstraSpatial2;
uniform sampler2D shaderlodAstraSpatial3;
uniform sampler2D shaderlodAstraSpatial4;
uniform sampler2D shaderlodAstraSpatial5;
// Full-rate native receiver dFdx.xy/dFdy.xy, carried without rescaling.
uniform sampler2D shaderlodAstraSpatial6;
uniform int shaderlodOptSpatialLightingRate;
uniform int shaderlodEffectsVersion;
uniform int shaderlodOpaqueLightingMode;
uniform float shaderlodOpaqueLightingNear,shaderlodOpaqueLightingFar,shaderlodOpaqueLightingTransition;
uniform float shaderlodOpaqueLightingMediumWorkRatio,shaderlodOpaqueLightingFarWorkRatio;
uniform vec2 shaderlodAstraPixelSize;
vec2 shaderlodAstraSpatialNativeCoord(){if(shaderlodAstraSpatialPhase==4 && all(greaterThan(shaderlodAstraPixelSize,vec2(0.0))))return gl_FragCoord.xy*shaderlodAstraSpatialSize/shaderlodAstraPixelSize;return shaderlodAstraSpatialPhase==3 ? 2.0*gl_FragCoord.xy-vec2(0.5) : gl_FragCoord.xy;}
vec3 shaderlodAstraSpatialFactor=vec3(0.0);
float shaderlodAstraSpatialShade=0.0;
vec4 shaderlodAstraSpatialDesat=vec4(0.0,0.0,0.0,1.0);
#include "/lib/shaderlod/astra_spatial_continuity.glsl"
bool shaderlod_astra_rate_finite(float x) { return !isnan(x) && !isinf(x); }
bool shaderlod_astra_rate_ratio(float x) {
    // Legal realized rates are K4/full and K1/four-pixel tile. K2/K3/zero unsupported.
    return shaderlod_astra_rate_finite(x) && (x == 1.0 || (x > 0.0 && x <= 0.25));
}
bool shaderlod_astra_rate_parameters(out vec3 bands, out vec2 ratios) {
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
        shaderlod_astra_rate_finite(bands.y + 0.5 * bands.z) &&
        shaderlod_astra_rate_ratio(ratios.x) && shaderlod_astra_rate_ratio(ratios.y) && ratios.y <= ratios.x;
}
float shaderlod_astra_rate_weight(float distance_blocks) {
    vec3 bands; vec2 ratios;
    if (!shaderlod_astra_rate_parameters(bands, ratios) || !shaderlod_astra_rate_finite(distance_blocks)) return 0.0;
    float half_width = 0.5 * bands.z;
    float medium = bands.z == 0.0 ? float(distance_blocks >= bands.x) :
        smoothstep(bands.x - half_width, bands.x + half_width, distance_blocks);
    float far_weight = bands.z == 0.0 ? float(distance_blocks >= bands.y) :
        smoothstep(bands.y - half_width, bands.y + half_width, distance_blocks);
    return ratios.x <= 0.25 ? medium : (ratios.y <= 0.25 ? far_weight : 0.0);
}


bool shaderlodAstraSpatialFar(float distanceBlocks) {
    if(!(shaderlodBridgeVersion==2 && shaderlodEnabled==1 && isEyeInWater==0 &&
        shaderlodNear>=1.0 && shaderlodFar>shaderlodNear && shaderlodFar<=4096.0 &&
        shaderlodTransition>=0.0 && shaderlodTransition<=shaderlodFar-shaderlodNear))return false;
    if(shaderlodAstraSpatialMode==1)return shaderlod_optimization_enabled(shaderlodOptSpatialLightingReuse) && distanceBlocks>max(256.0,shaderlodFar+0.5*shaderlodTransition);
    return shaderlodAstraSpatialMode==2 && shaderlod_optimization_enabled(shaderlodOptSpatialLightingRate) &&
        distanceBlocks>256.0 && shaderlod_astra_rate_weight(distanceBlocks)>=1.0;
}
bool shaderlodAstraSpatialConsume(inout vec3 albedo,inout vec3 shadow,vec3 nativeNormal,vec3 nativePosition,vec2 nativeLm,float nativeAo,float nativeDiffuse) {
    if(shaderlodAstraSpatialPhase!=2 || !all(equal(shaderlodAstraSpatialSize,vec2(viewWidth,viewHeight))) ||
        !shaderlodAstraSpatialFar(length(nativePosition)))return false;
    ivec2 pixel=ivec2(gl_FragCoord.xy),origin=(pixel/2)*2;
    if(any(lessThan(origin,ivec2(0)))||any(greaterThanEqual(origin+ivec2(1),ivec2(shaderlodAstraSpatialSize))))return false;
    vec3 positions[4],normals[4];int materials[4];vec2 lightmaps[4];bool protect[4];
    float aoMin=1.0,aoMax=0.0;
    for(int i=0;i<4;i++) {
        ivec2 p=origin+ivec2(i%2,i/2);
        vec4 surface=texelFetch(shaderlodAstraSpatial3,p,0),lm=texelFetch(shaderlodAstraSpatial4,p,0),position=texelFetch(shaderlodAstraSpatial5,p,0);
        positions[i]=position.xyz;normals[i]=surface.xyz;lightmaps[i]=lm.xy;materials[i]=int(position.w);
        protect[i]=position.w!=10003.0||!(surface.w>0.0&&surface.w<1.0)||!shaderlodAstraSpatialFar(length(position.xyz));
        if(!shaderlod_surface_finite(lm.zw)||lm.z<0.0||lm.z>1.0||lm.w<0.0||lm.w>4.0)return false;
        aoMin=min(aoMin,lm.z);aoMax=max(aoMax,lm.z);
        if(all(equal(p,pixel)) && !(surface.w==gl_FragCoord.z && all(equal(surface.xyz,nativeNormal)) &&
            all(equal(position.xyz,nativePosition))&&all(equal(lm,vec4(nativeLm,nativeAo,nativeDiffuse)))))return false;
    }
    if(!shaderlod_surface_tile_eligible(true,positions,normals,materials,lightmaps,protect,256.0,true,
        vec2(aoMax-aoMin,0.0),shaderlod_surface_default_limits()))return false;
    ivec2 lightingPixel=shaderlodAstraSpatialMode==2?origin/2:origin;
    vec4 factor=texelFetch(shaderlodAstraSpatial0,lightingPixel,0),shadowShade=texelFetch(shaderlodAstraSpatial1,lightingPixel,0),desat=texelFetch(shaderlodAstraSpatial2,lightingPixel,0);
    if(!shaderlod_surface_finite(factor.rgb)||!shaderlod_surface_finite(shadowShade.rgb)||!shaderlod_surface_finite(desat.rgb)||
        !shaderlod_surface_finite(shadowShade.a)||!shaderlod_surface_finite(desat.a)||factor.a<0.999||
        any(lessThan(factor.rgb,vec3(0.0)))||any(greaterThan(factor.rgb,vec3(1e10)))||
        shadowShade.a<0.0||shadowShade.a>4.0||desat.a<0.0||desat.a>1.0)return false;
    // Exact native multiply order and native night-desaturation reconstruction.
    albedo*=factor.rgb;
    float nativeSmoothLighting=clamp01(nativeAo);
    albedo*=shadowShade.a*nativeSmoothLighting*nativeSmoothLighting;
#if defined ENABLE_DARKNESS_EFFECT && MC_VERSION>=11900
    albedo*=1.0-clamp(darknessLightFactor*2.0,0.0,1.0);
#endif
#if defined NIGHT_DESATURATION && defined OVERWORLD
    vec3 desatAlbedo=mix(albedo,GetLuminance(albedo)*desat.rgb,1.0-DESATURATION_FACTOR*0.4);
    albedo=mix(desatAlbedo,albedo,desat.a);
#endif
    shadow=shadowShade.rgb;
    return true;
}

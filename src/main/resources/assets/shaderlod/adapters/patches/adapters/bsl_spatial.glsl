// Native BSL forward lighting basis with distinct full replay REUSE and half-viewport RATE.
uniform int shaderlodBslSpatialPhase;
uniform float shaderlodBslSpatialAlphaRef;
uniform int shaderlodBslSpatialMode;
uniform vec2 shaderlodBslSpatialSize;
uniform sampler2D shaderlodBslSpatial0;
uniform sampler2D shaderlodBslSpatial1;
uniform sampler2D shaderlodBslSpatial2;
uniform sampler2D shaderlodBslSpatial3;
uniform sampler2D shaderlodBslSpatial4;
uniform sampler2D shaderlodBslSpatial5;
uniform int shaderlodOptSpatialLightingRate;
uniform int shaderlodEffectsVersion;
uniform int shaderlodOpaqueLightingMode;
uniform float shaderlodOpaqueLightingNear,shaderlodOpaqueLightingFar,shaderlodOpaqueLightingTransition;
uniform float shaderlodOpaqueLightingMediumWorkRatio,shaderlodOpaqueLightingFarWorkRatio;
vec3 shaderlodBslSpatialFactor=vec3(0.0);
float shaderlodBslSpatialShade=0.0;
vec4 shaderlodBslSpatialDesat=vec4(0.0,0.0,0.0,1.0);
#include "/lib/shaderlod/bsl_spatial_continuity.glsl"
bool shaderlodBslSpatialFar(float distanceBlocks) {
    if(!(shaderlodBridgeVersion==2 && shaderlodEnabled==1 && isEyeInWater==0 &&
        shaderlodNear>=1.0 && shaderlodFar>shaderlodNear && shaderlodFar<=4096.0 &&
        shaderlodTransition>=0.0 && shaderlodTransition<=shaderlodFar-shaderlodNear))return false;
    if(shaderlodBslSpatialMode==1)return shaderlod_optimization_enabled(shaderlodOptSpatialLightingReuse) && distanceBlocks>max(256.0,shaderlodFar+0.5*shaderlodTransition);
    return shaderlodBslSpatialMode==2 && shaderlod_optimization_enabled(shaderlodOptSpatialLightingRate) && distanceBlocks>max(256.0,shaderlodFar+0.5*shaderlodTransition);
}
vec2 shaderlodBslSpatialPixel(){if(shaderlodBslSpatialMode==3 && shaderlodBslSpatialPhase==4)return gl_FragCoord.xy*vec2(viewWidth,viewHeight)/vec2((ivec2(viewWidth,viewHeight)*85)/100);return shaderlodBslSpatialPhase==3?vec2(2*ivec2(gl_FragCoord.xy))+vec2(0.5):gl_FragCoord.xy;}
bool shaderlodBslSpatialConsume(inout vec3 albedo,inout vec3 shadow,vec3 nativeNormal,vec3 nativePosition,vec2 nativeLm,float nativeAo,float nativeDiffuse) {
    if(shaderlodBslSpatialPhase!=2 || !all(equal(shaderlodBslSpatialSize,vec2(viewWidth,viewHeight))) ||
        !shaderlodBslSpatialFar(length(nativePosition)))return false;
    ivec2 pixel=ivec2(gl_FragCoord.xy),origin=(pixel/2)*2;
    if(any(lessThan(origin,ivec2(0)))||any(greaterThanEqual(origin+ivec2(1),ivec2(shaderlodBslSpatialSize))))return false;
    vec3 positions[4],normals[4];int materials[4];vec2 lightmaps[4];bool protect[4];
    float aoMin=1.0,aoMax=0.0;
    for(int i=0;i<4;i++) {
        ivec2 p=origin+ivec2(i%2,i/2);
        vec4 surface=texelFetch(shaderlodBslSpatial3,p,0),lm=texelFetch(shaderlodBslSpatial4,p,0),position=texelFetch(shaderlodBslSpatial5,p,0);
        positions[i]=position.xyz;normals[i]=surface.xyz;lightmaps[i]=lm.xy;materials[i]=int(position.w);
        protect[i]=position.w!=25099.0||!(surface.w>0.0&&surface.w<1.0)||!shaderlodBslSpatialFar(length(position.xyz));
        if(!shaderlod_surface_finite(lm.zw)||lm.z<0.0||lm.z>1.0||lm.w<0.0||lm.w>4.0)return false;
        aoMin=min(aoMin,lm.z);aoMax=max(aoMax,lm.z);
        if(all(equal(p,pixel)) && !(surface.w==gl_FragCoord.z && all(equal(surface.xyz,nativeNormal)) &&
            all(equal(position.xyz,nativePosition))&&all(equal(lm,vec4(nativeLm,nativeAo,nativeDiffuse)))))return false;
    }
    if(!shaderlod_surface_tile_eligible(true,positions,normals,materials,lightmaps,protect,256.0,true,
        vec2(aoMax-aoMin,0.0),shaderlod_surface_default_limits()))return false;
    ivec2 lightingOrigin=shaderlodBslSpatialMode==2?origin/2:origin;
    vec4 factor=texelFetch(shaderlodBslSpatial0,lightingOrigin,0),shadowShade=texelFetch(shaderlodBslSpatial1,lightingOrigin,0),desat=texelFetch(shaderlodBslSpatial2,lightingOrigin,0);
    if(!shaderlod_surface_finite(factor.rgb)||!shaderlod_surface_finite(shadowShade.rgb)||!shaderlod_surface_finite(desat.rgb)||
        !shaderlod_surface_finite(shadowShade.a)||!shaderlod_surface_finite(desat.a)||factor.a<0.999||
        any(lessThan(factor.rgb,vec3(0.0)))||any(greaterThan(factor.rgb,vec3(1e10)))||
        shadowShade.a<0.0||shadowShade.a>4.0||desat.a<0.0||desat.a>1.0)return false;
    // Exact native multiply order and native night-desaturation reconstruction.
#ifdef ALBEDO_BALANCING
    float albedoBrightness=max(max(albedo.r,albedo.g),albedo.b);
    albedo/=1.0+albedoBrightness*0.25;
#endif
    albedo*=factor.rgb;
    float nativeSmoothLighting=nativeAo;
    albedo*=shadowShade.a*nativeSmoothLighting*nativeSmoothLighting;
#if defined DESATURATION && defined OVERWORLD
    vec3 desatAlbedo=mix(albedo,GetLuminance(albedo)*desat.rgb,1.0-DESATURATION_FACTOR*0.4);
    albedo=mix(desatAlbedo,albedo,desat.a);
#endif
    shadow=shadowShade.rgb;
    return true;
}

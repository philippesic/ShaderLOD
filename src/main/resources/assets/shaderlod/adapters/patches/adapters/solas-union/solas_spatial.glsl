uniform int shaderlodOptSpatialLightingRate;
// Solas native prealbedo lighting basis; RGB inversion is never used.
uniform int shaderlodSolasSpatialMode;
uniform float shaderlodSolasSpatialAlphaRef;
uniform vec2 shaderlodSolasSpatialSize;
uniform sampler2D shaderlodSolasSpatial0,shaderlodSolasSpatial1,shaderlodSolasSpatial2;
uniform sampler2D shaderlodSolasSpatial3,shaderlodSolasSpatial4,shaderlodSolasSpatial5;
vec3 shaderlodSolasSpatialScene=vec3(0.0),shaderlodSolasSpatialBlock=vec3(0.0);
float shaderlodSolasSpatialFlash=0.0;
#include "/lib/shaderlod/solas_spatial_continuity.glsl"
bool shaderlodSolasSpatialDomain(vec3 viewPos,vec3 worldPos,vec2 lightmap,float NoL) {
    return ((shaderlodSolasSpatialMode==1 && shaderlod_optimization_enabled(shaderlodOptSpatialLightingReuse)) ||
         (shaderlodSolasSpatialMode==2 && shaderlod_optimization_enabled(shaderlodOptSpatialLightingRate))) &&
        shaderlodBridgeVersion==2 && shaderlodEnabled==1 && isEyeInWater==0 &&
        shaderlodNear>=1.0 && shaderlodFar>shaderlodNear && shaderlodFar<=4096.0 &&
        shaderlodTransition>=0.0 && shaderlodTransition<=shaderlodFar-shaderlodNear &&
        length(viewPos)>max(256.0,shaderlodFar+0.5*shaderlodTransition) &&
        lightmap.y==1.0 && NoL>0.02 && far>0.0 && far<=65536.0 &&
        all(lessThan(abs(viewPos),vec3(65536.0))) &&
        maxOf(abs(worldPos))>=min(shadowDistance,far) &&
        all(lessThan(abs(worldPos),vec3(65536.0))) && all(lessThan(abs(cameraPosition),vec3(1e8))) &&
        wetness>=0.0 && wetness<=1.0 && shadowFade>=0.0 && shadowFade<=1.0 &&
        timeAngle>=0.0 && timeAngle<=1.0 && frameTimeCounter>=0.0 && frameTimeCounter<1e9;
}
bool shaderlodSolasSpatialRateInput(ivec2 origin,out vec3 position,out vec3 normal,out vec4 lm,out float depth) {
    if(shaderlodSolasSpatialMode!=2 || !all(equal(shaderlodSolasSpatialSize,vec2(viewWidth,viewHeight))))return false;
    if(any(lessThan(origin,ivec2(0)))||any(greaterThanEqual(origin+ivec2(1),ivec2(shaderlodSolasSpatialSize))))return false;
    vec3 positions[4],normals[4];int materials[4];vec2 lightmaps[4];bool protect[4];
    float aoMin=1.0,aoMax=0.0;
    for(int i=0;i<4;i++) {
        ivec2 p=origin+ivec2(i%2,i/2);
        vec4 surface=texelFetch(shaderlodSolasSpatial3,p,0),lm=texelFetch(shaderlodSolasSpatial4,p,0),position=texelFetch(shaderlodSolasSpatial5,p,0);
        positions[i]=position.xyz;normals[i]=surface.xyz;lightmaps[i]=lm.xy;materials[i]=int(position.w);
        protect[i]=position.w!=20302.0||!(surface.w>0.0&&surface.w<1.0)||lm.y!=1.0||lm.w<=0.02;
        if(!shaderlod_surface_finite(lm.zw)||lm.z<0.0||lm.z>1.0||lm.w<0.0||lm.w>1.0)return false;
        aoMin=min(aoMin,lm.z);aoMax=max(aoMax,lm.z);
    }
    if(!shaderlod_surface_tile_eligible(true,positions,normals,materials,lightmaps,protect,256.0,true,vec2(aoMax-aoMin,0.0),shaderlod_surface_default_limits()))return false;
    vec4 surface=texelFetch(shaderlodSolasSpatial3,origin,0);
    position=positions[0];normal=normals[0];lm=texelFetch(shaderlodSolasSpatial4,origin,0);depth=surface.w;
    return true;
}

bool shaderlodSolasSpatialLoad(vec3 nativePosition,vec3 nativeNormal,vec2 nativeLm,float nativeAo,float nativeNoL,
    out vec3 sceneLighting,out vec3 blockLighting,out vec3 shadowValue,out float lightningFlash) {
    if(shaderlodSolasSpatialPhase!=2 || !all(equal(shaderlodSolasSpatialSize,vec2(viewWidth,viewHeight))))return false;
    ivec2 pixel=ivec2(gl_FragCoord.xy),origin=(pixel/2)*2;
    if(any(lessThan(origin,ivec2(0)))||any(greaterThanEqual(origin+ivec2(1),ivec2(shaderlodSolasSpatialSize))))return false;
    vec3 positions[4],normals[4];int materials[4];vec2 lightmaps[4];bool protect[4];
    float aoMin=1.0,aoMax=0.0;
    for(int i=0;i<4;i++) {
        ivec2 p=origin+ivec2(i%2,i/2);
        vec4 surface=texelFetch(shaderlodSolasSpatial3,p,0),lm=texelFetch(shaderlodSolasSpatial4,p,0),position=texelFetch(shaderlodSolasSpatial5,p,0);
        positions[i]=position.xyz;normals[i]=surface.xyz;lightmaps[i]=lm.xy;materials[i]=int(position.w);
        protect[i]=position.w!=20302.0||!(surface.w>0.0&&surface.w<1.0)||lm.y!=1.0||lm.w<=0.02;
        if(!shaderlod_surface_finite(lm.zw)||lm.z<0.0||lm.z>1.0||lm.w<0.0||lm.w>1.0)return false;
        aoMin=min(aoMin,lm.z);aoMax=max(aoMax,lm.z);
        if(all(equal(p,pixel))&&!(surface.w==gl_FragCoord.z&&all(equal(surface.xyz,nativeNormal))&&all(equal(position.xyz,nativePosition))&&all(equal(lm,vec4(nativeLm,nativeAo,nativeNoL)))))return false;
    }
    if(!shaderlod_surface_tile_eligible(true,positions,normals,materials,lightmaps,protect,256.0,true,vec2(aoMax-aoMin,0.0),shaderlod_surface_default_limits()))return false;
    ivec2 basis=shaderlodSolasSpatialMode==2?origin/2:origin;
    vec4 scene=texelFetch(shaderlodSolasSpatial0,basis,0),block=texelFetch(shaderlodSolasSpatial1,basis,0),shade=texelFetch(shaderlodSolasSpatial2,basis,0);
    if(scene.a<0.999||shade.a!=1.0||!shaderlod_surface_finite(scene.rgb)||!shaderlod_surface_finite(block.rgb)||!shaderlod_surface_finite(shade.rgb)||!shaderlod_surface_finite(block.a)||
        any(lessThan(scene.rgb,vec3(0.0)))||any(greaterThan(scene.rgb,vec3(1e10)))||any(lessThan(block.rgb,vec3(0.0)))||any(greaterThan(block.rgb,vec3(1e10)))||
        !all(greaterThan(shade.rgb,vec3(0.0)))||block.a<0.0||block.a>1e10)return false;
    sceneLighting=scene.rgb;blockLighting=block.rgb;shadowValue=shade.rgb;lightningFlash=block.a;return true;
}

bool shaderlodSolasSpatialConsume(vec4 color,inout vec4 albedo,vec3 viewPos,vec3 newNormal,inout vec3 shadow,vec2 lightmap,
    float NoU,float NoL,float NoE,float emission,float smoothness,float metalness,float f0) {
    vec3 sceneLighting,blockLighting,shadowValue;float lightningFlash;
    if(!shaderlodSolasSpatialLoad(viewPos,newNormal,lightmap,color.a,NoL,sceneLighting,blockLighting,shadowValue,lightningFlash))return false;
    shadow=shadowValue;
    // Native specular, AO and gamma run at the current pixel/material, never cached RGB.
    metalness=clamp(metalness,0.0,0.85);
    float lAlbedo=length(albedo.rgb),vanillaAo=color.a*color.a;
    float vanillaDiffuse=(0.25*NoU+0.75)+(0.667-abs(NoE))*(1.0-abs(NoU))*0.15;
    vanillaDiffuse*=vanillaDiffuse;vanillaDiffuse=fmix(1.0,vanillaDiffuse,lightmap.y);
    float blockLightMap=pow6(lightmap.x*lightmap.x)*2.0+max(lightmap.x-0.05,0.0);
    blockLightMap*=blockLightMap*0.5;
    NoL=clamp(NoL*1.01-0.01,0.0,1.0);
    //Specular Highlight
    vec3 specularHighlight = vec3(0.0);

    #if (defined GBUFFERS_TERRAIN || defined GBUFFERS_ENTITIES || defined GBUFFERS_BLOCK || defined VOXY_OPAQUE) && !defined NETHER && defined SPECULAR_HIGHLIGHTS
    if (emission < 0.01) {
        float smoothnessF = 0.10 + lAlbedo * 0.15;
        #ifdef END
              smoothness -= 0.10;
        #endif
              smoothnessF = fmix(smoothnessF, 1.0, smoothness);

        float effectiveF0 = (f0 > 0.0) ? f0 : (metalness > 0.5 ? 1.0 : 0.5);

        #ifdef OVERWORLD
        specularHighlight = getSpecularHighlight(
            newNormal, viewPos, smoothnessF, metalness, albedo.rgb,
            effectiveF0, lightColSqrt, shadow / max(NoL + 0.001, 0.001), color.a
        );
        #else
        specularHighlight = getSpecularHighlight(
            newNormal, viewPos, smoothnessF, metalness, albedo.rgb,
            effectiveF0, endLightColSqrt * 0.125, shadow / max(NoL + 0.001, 0.001), color.a
        );
        #endif

        // Metalness hotfix
        if (metalness > 0.5) {
            specularHighlight = max(specularHighlight * 0.5, vec3(0.0));
        } else {
            specularHighlight = max(specularHighlight * 1.1, vec3(0.0));
            specularHighlight *= albedo.rgb;
        }
    }
    #endif

    //Vanilla AO
    #ifdef VANILLA_AO
    float aoMixer = (1.0 - vanillaAo) * (1.0 - blockLightMap) * (1.0 - float(emission > 0.0));
    #if defined OVERWORLD || defined END
            aoMixer *= 1.0 - float(length(shadow) > 0.0) * 0.5;
    #endif

    albedo.rgb = fmix(albedo.rgb, albedo.rgb * pow(vanillaAo, 1.0 + lightmap.y), aoMixer);
    #endif

    albedo.rgb = pow(albedo.rgb, vec3(2.2));

    vec3 diffuseAlbedo = albedo.rgb * (1.0 - metalness);
#if SHADERLOD_REFINEMENT_ADAPTER == 1 && defined IS_IRIS && MC_VERSION == 12101 && IRIS_VERSION == 10812
    // A finite positive native strength preserves the sign of either exact zero.
    // Keep albedo gamma, all lighting, and every nonzero multiplication native.
    float shaderlodEmissionLighting = emission;
    if (!(shaderlod_optimization_enabled(shaderlodOptZeroEmissionArithmetic) &&
          emission == 0.0 && EMISSION_STRENGTH >= 1.0 && EMISSION_STRENGTH <= 10.0)) {
        shaderlodEmissionLighting = emission * EMISSION_STRENGTH;
    }
            diffuseAlbedo *= sceneLighting + blockLighting + shaderlodEmissionLighting + lightningFlash;
#else
            diffuseAlbedo *= sceneLighting + blockLighting + emission * EMISSION_STRENGTH + lightningFlash;
#endif
            diffuseAlbedo *= vanillaDiffuse;

    vec3 finalColor = diffuseAlbedo + specularHighlight * vanillaDiffuse;

    albedo.rgb = pow(max(finalColor, vec3(0.0)), vec3(1.0 / 2.2));

    return true;
}

#include "/lib/shaderlod/solas_pixel_world.glsl"

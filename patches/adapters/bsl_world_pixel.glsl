// BSL far opaque world-pixel85 producer with full-native geometry/current-depth fallback.
vec4 shaderlodBslWorldPosition=vec4(0.0);
vec4 shaderlodBslWorldSurface=vec4(0.0);
vec4 shaderlodBslWorldLightmap=vec4(0.0);
vec4 shaderlodBslWorldRawAlbedo=vec4(0.0);
bool shaderlodBslWorldDomain(vec3 pos){
#ifndef TAA
    return false;
#else
    return shaderlodBslSpatialMode==3 && shaderlod_optimization_enabled(shaderlodOptPixelScale) &&
        shaderlodBridgeVersion==2 && shaderlodEnabled==1 && isEyeInWater==0 && rainStrength==0.0 &&
        shaderlodNear>0.0 && shaderlodFar>shaderlodNear && shaderlodFar<=4096.0 &&
        shaderlodTransition>=0.0 && shaderlodTransition<=shaderlodFar-shaderlodNear &&
        shaderlod_surface_finite(pos) && length(pos)>max(256.0,shaderlodFar+0.5*shaderlodTransition)
#if DYNAMIC_HANDLIGHT>0
        && heldBlockLightValue==0 && heldBlockLightValue2==0
#endif
#ifdef IRIS_FEATURE_FADE_VARIABLE
        && (chunkFade<0.0 || chunkFade>=1.0)
#endif
        ;
#endif
}
bool shaderlodBslWorldResolve(vec4 nativeAlbedo,vec3 pos){
    if(shaderlodBslSpatialPhase!=5 || shaderlodBslSpatialMode!=3 || shaderlodBslSpatialMaterial!=25099 ||
        !all(equal(color,vec4(1.0))) || !all(equal(shaderlodBslSpatialSize,vec2(viewWidth,viewHeight))) ||
        !(gl_FragCoord.z>=0.56 && gl_FragCoord.z<1.0))return false;
    vec2 full=vec2(viewWidth,viewHeight),small=vec2((ivec2(full)*85)/100);
    vec2 lm=clamp(lmCoord,vec2(0.0),vec2(1.0));
    if(!shaderlodBslWorldDomain(pos)||lm.y!=1.0)return false;
    vec2 samplePos=(gl_FragCoord.xy/full)*small-0.5,base=floor(samplePos),part=fract(samplePos);
    if(any(lessThan(base,vec2(0.0)))||any(greaterThanEqual(base+1.0,small)))return false;
    vec3 positions[4],normals[4];int materials[4];vec2 lightmaps[4];bool protect[4];
    vec3 outColor=vec3(0.0),firstColor=vec3(0.0);
    for(int i=0;i<4;i++){
        ivec2 o=ivec2(i%2,i/2),p=ivec2(base)+o;
        vec4 surface=texelFetch(shaderlodBslSpatial1,p,0),position=texelFetch(shaderlodBslSpatial2,p,0),lights=texelFetch(shaderlodBslSpatial3,p,0);
        positions[i]=position.xyz;normals[i]=surface.xyz;materials[i]=int(surface.w);lightmaps[i]=lights.xy;
        protect[i]=surface.w!=25099.0 || !all(equal(surface.xyz,normal)) || !all(equal(lights,vec4(lm,1.0,1.0))) ||
            !(position.w>=0.56&&position.w<1.0) || abs(dot(position.xyz-pos,normal))>0.08 || !shaderlodBslWorldDomain(position.xyz);
        if(!all(equal(texelFetch(shaderlodBslSpatial4,p,0),nativeAlbedo)))return false;
        vec3 value=texelFetch(shaderlodBslSpatial0,p,0).rgb;
        if(!shaderlod_surface_finite(value)||any(lessThan(value,vec3(0.0)))||any(greaterThan(value,vec3(1e6))))return false;
        if(i==0)firstColor=value;
        if(any(greaterThan(abs(value-firstColor),vec3(0.025)+abs(firstColor)*0.05)))return false;
        float weight=mix(1.0-part.x,part.x,float(o.x))*mix(1.0-part.y,part.y,float(o.y));outColor+=value*weight;
    }
    if(!shaderlod_surface_tile_eligible(true,positions,normals,materials,lightmaps,protect,256.0,false,vec2(0.0),shaderlod_surface_default_limits()))return false;
    // Current native full-resolution geometry still owns depth/coverage; only qualified RGB uses low field.
    gl_FragData[0]=vec4(outColor,1.0);return true;
}

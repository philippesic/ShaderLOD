// Restricted world-stage raster scale; native full-resolution depth/deferred/TAA/history domain is canonical.
bool shaderlodSolasWorldDomain(vec3 viewPos) {
    return SHADERLOD_SOLAS_PIXEL_GRAPH==1 && shaderlodSolasSpatialMode==3 &&
        shaderlod_optimization_enabled(shaderlodOptPixelScale) && shaderlodBridgeVersion==2 && shaderlodEnabled==1 &&
        isEyeInWater==0 && wetness==0.0 && shaderlodNear>0.0 && shaderlodFar>shaderlodNear && shaderlodFar<=4096.0 &&
        shaderlodTransition>=0.0 && shaderlodTransition<=shaderlodFar-shaderlodNear &&
        shaderlod_surface_finite(viewPos) && length(viewPos)>max(256.0,shaderlodFar+0.5*shaderlodTransition);
}
bool shaderlodSolasWorldResolve(vec3 current,vec3 currentNormal,vec2 currentLm,float currentSmoothness,float currentMetalness,float currentEmission,float currentSubsurface,float currentF0,float currentAo,vec3 currentFresnel,vec4 currentAlbedo) {
#ifndef PARALLAX
    if(SHADERLOD_SOLAS_PIXEL_GRAPH!=1 || shaderlodSolasSpatialMode!=3 || mat!=20302 || !all(equal(color,vec4(1.0))) || !(currentAlbedo.a>=0.999&&currentAlbedo.a<=1.0) || !shaderlod_surface_finite(currentAlbedo.rgb) ||
        any(lessThan(currentAlbedo.rgb,vec3(0.0))) || any(greaterThan(currentAlbedo.rgb,vec3(1e6))) ||
        !shaderlod_surface_finite(currentSmoothness) || currentSmoothness<0.0 || currentSmoothness>1.0 ||
        !shaderlod_surface_finite(currentFresnel) || !shaderlod_surface_finite(currentLm) || currentMetalness!=0.0 || currentEmission!=0.0 || currentSubsurface!=0.0 ||
        currentAo!=1.0 || !(currentF0>=0.0&&currentF0<=0.04) ||
        !all(equal(shaderlodSolasSpatialSize,vec2(viewWidth,viewHeight))))return false;
    vec2 full=vec2(viewWidth,viewHeight),small=vec2(ivec2(full)*85/100);
    if(!(gl_FragCoord.z>=0.56 && gl_FragCoord.z<1.0))return false;
    if(!shaderlodSolasWorldDomain(current) || currentLm.y!=1.0)return false;
    vec2 samplePos=(gl_FragCoord.xy/full)*small-0.5,base=floor(samplePos),part=fract(samplePos);
    if(any(lessThan(base,vec2(0.0)))||any(greaterThanEqual(base+1.0,small)))return false;
    vec3 positions[4],normals[4];int materials[4];vec2 lightmaps[4];bool protect[4];
    vec4 out0=vec4(0.0),out1=vec4(0.0),out2=vec4(0.0),out3=vec4(0.0);vec3 firstColor=vec3(0.0);
    for(int i=0;i<4;i++) {
        ivec2 offset=ivec2(i%2,i/2),p=ivec2(base)+offset;
        vec4 position=texelFetch(shaderlodSolasSpatial4,p,0),surface=texelFetch(shaderlodSolasSpatial5,p,0);
        positions[i]=position.xyz;normals[i]=surface.xyz;materials[i]=int(position.w);lightmaps[i]=vec2(surface.w,1.0);
        protect[i]=position.w!=20302.0 || !all(equal(surface.xyz,currentNormal)) || abs(surface.w-currentLm.x)>0.003921568627451 ||
            abs(dot(position.xyz-current,currentNormal))>0.08;
        vec4 a=texelFetch(shaderlodSolasSpatial0,p,0),b=texelFetch(shaderlodSolasSpatial1,p,0),
            c=texelFetch(shaderlodSolasSpatial2,p,0),d=texelFetch(shaderlodSolasSpatial3,p,0);
        if(!shaderlod_surface_finite(a.rgb)||any(lessThan(a.rgb,vec3(0.0)))||any(greaterThan(a.rgb,vec3(1e6)))||
            !shaderlod_surface_finite(b.xyz)||!shaderlod_surface_finite(c.xyz)||!shaderlod_surface_finite(d.xyz)||
            abs(b.y-0.5)>0.0000152591 || c.z!=1.0 ||
            abs(b.x-clamp(currentSmoothness,0.0,0.95))>0.0000152591 ||
            any(greaterThan(abs(d.rgb-currentFresnel),vec3(0.0000152591))))return false;
        if(i==0)firstColor=a.rgb;
        if(any(greaterThan(abs(a.rgb-firstColor),vec3(0.025)+abs(firstColor)*0.05)))return false;
        float weight=mix(1.0-part.x,part.x,float(offset.x))*mix(1.0-part.y,part.y,float(offset.y));
        out0+=a*weight;out1+=b*weight;out2+=c*weight;out3+=d*weight;
    }
    if(!shaderlod_surface_tile_eligible(true,positions,normals,materials,lightmaps,protect,256.0,false,vec2(0.0),shaderlod_surface_default_limits()))return false;
    // Native normal/material/depth consumers receive a canonical full-resolution G-buffer.
    gl_FragData[0]=out0;gl_FragData[1]=out1;gl_FragData[2]=out2;gl_FragData[3]=out3;
    return true;
#else
    return false;
#endif
}

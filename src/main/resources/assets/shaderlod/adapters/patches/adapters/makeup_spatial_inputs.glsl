#if SHADERLOD_MAKEUP_SPATIAL_SOURCE == 1
bool shaderlodMakeupSpatialFinite(vec3 v){return !any(isnan(v))&&!any(isinf(v))&&all(lessThan(abs(v),vec3(1e10)));}
vec3 shaderlodMakeupSpatialPosition(){vec4 p=gbufferProjectionInverse*vec4(gl_FragCoord.xy*vec2(pixelSizeX,pixelSizeY)*2.0-1.0,gl_FragCoord.z*2.0-1.0,1.0);return p.xyz/p.w;}
int shaderlodMakeupSpatialNormal(){vec3 n=mat3(gbufferModelViewInverse)*flatNormal;if(!shaderlodMakeupSpatialFinite(n)||abs(dot(n,n)-1.0)>0.001)return -1;int a=abs(n.x)>abs(n.y)?0:1;a=abs(n[a])>abs(n.z)?a:2;if(abs(abs(n[a])-1.0)>0.0001)return -1;for(int j=0;j<3;j++)if(j!=a&&abs(n[j])>0.0001)return -1;return a*2+int(n[a]<0.0);}
float shaderlodMakeupSpatialMaterial(float block_luma){
    // Literal native material calculation, before expensive shadow work; no albedo division.
    float final_gloss_power=glossPower;block_luma*=lumaFactor;
    if(lumaPower<0.0)final_gloss_power-=(block_luma*73.334);else block_luma=pow(block_luma,lumaPower);
    float material_gloss_factor=materialGloss(reflect(viewPositionNormalized,flatNormal),lmcoordAlt,final_gloss_power,flatNormal)*glossFactor;
    return material_gloss_factor*block_luma;
}
bool shaderlodMakeupSpatialDistance(float d){
    if(shaderlodEffectsVersion!=2||shaderlodOpaqueLightingMode==1)return false;
    float nearBand=shaderlodNear,farBand=shaderlodFar,width=shaderlodTransition;
    float medium=1.0,farRatio=0.25;
    if(shaderlodOpaqueLightingMode==2){nearBand=shaderlodOpaqueLightingNear;farBand=shaderlodOpaqueLightingFar;width=shaderlodOpaqueLightingTransition;medium=shaderlodOpaqueLightingMediumWorkRatio;farRatio=shaderlodOpaqueLightingFarWorkRatio;}
    else if(shaderlodOpaqueLightingMode!=0)return false;
    if(!(nearBand>=1.0&&farBand>nearBand&&width>=0.0&&width<=min(nearBand*2.0,farBand-nearBand)&&farRatio>0.0&&farRatio<=medium&&medium<=1.0))return false;
    return d>max(256.0,(medium<=0.25?nearBand:farBand)+0.5*width)&&(medium<=0.25||farRatio<=0.25);
}
bool shaderlodMakeupSpatialEligible(vec4 color,float lumaValue,out float material,out int normalCode){
    material=1.0;normalCode=-1;
    if(shaderlodMakeupSpatialReady!=1||!shaderlod_optimization_enabled(
#if SHADERLOD_MAKEUP_WORLD_PIXEL == 1
shaderlodOptPixelScale
#elif SHADERLOD_MAKEUP_SPATIAL_RATE == 1
shaderlodOptSpatialLightingRate
#else
shaderlodOptSpatialLightingReuse
#endif
)||shaderlodPreset!=1||
       shaderlodMakeupSpatialSize!=vec2(viewWidth,viewHeight)||frameCounter<0||frameCounter>16777216||
       isEyeInWater!=0||isEndPortal!=0.0||isFoliage!=0.0||isEmissiveEntity!=0.0||!shaderlodMakeupSpatialFinite(color.rgb)||!(color.a>=0.999&&color.a<=1.0)||
       !shaderlodMakeupSpatialDistance(gl_FogFragCoord))return false;
    if(!shaderlodMakeupSpatialFinite(shadowPos)||!shaderlodMakeupSpatialFinite(directLightColor)||!shaderlodMakeupSpatialFinite(omniLight)||!shaderlodMakeupSpatialFinite(candleColor)||
       !(shadowDiffuse>=0.0&&shadowDiffuse<=1.0&&abs(directLightStrength)<1e6))return false;
    material=shaderlodMakeupSpatialMaterial(lumaValue);normalCode=shaderlodMakeupSpatialNormal();
    return floatBitsToUint(material)==0u&&normalCode>=0&&shaderlodMakeupSpatialFinite(shaderlodMakeupSpatialPosition());
}
void shaderlodMakeupSpatialExportInputs(vec4 color,float block_luma,float dither){
    gl_FragData[0]=vec4(0.0,0.0,0.0,-1.0);gl_FragData[1]=vec4(0.0,0.0,0.0,-1.0);gl_FragData[2]=vec4(0.0);gl_FragData[3]=vec4(0.0);gl_FragData[4]=vec4(0.0,0.0,0.0,-1.0);gl_FragData[5]=vec4(0.0,0.0,0.0,-1.0);
    float material;int normalCode;if(!shaderlodMakeupSpatialEligible(color,block_luma,material,normalCode))return;
#if SHADERLOD_MAKEUP_WORLD_PIXEL == 1
    if(color.a!=1.0||!(frogAdjust>=0.0&&frogAdjust<=1.0))return;
#endif
    gl_FragData[0]=vec4(shaderlodMakeupSpatialPosition(),float(normalCode));
    gl_FragData[1]=vec4(shadowPos,shadowDiffuse);gl_FragData[2]=vec4(directLightColor,directLightStrength);
    gl_FragData[3]=vec4(omniLight,
#if SHADERLOD_MAKEUP_SPATIAL_RATE == 1 || SHADERLOD_MAKEUP_WORLD_PIXEL == 1
gl_FogFragCoord
#else
material
#endif
);gl_FragData[4]=vec4(candleColor,float(frameCounter));
#if SHADERLOD_MAKEUP_SPATIAL_RATE == 1 || SHADERLOD_MAKEUP_WORLD_PIXEL == 1
#if SHADERLOD_MAKEUP_WORLD_PIXEL == 1
    gl_FragData[6]=color;vec3 nativeFog=vec3(0.0);if(darknessFactor<=0.01)nativeFog=shaderlodMakeupFog(gl_FragCoord.xy*vec2(pixelSizeX,pixelSizeY)).rgb;gl_FragData[7]=vec4(nativeFog,frogAdjust);if(!shaderlodMakeupSpatialFinite(nativeFog)){gl_FragData[0].a=-1.0;gl_FragData[4].a=-1.0;return;}
#endif
#if !defined COLORED_SHADOW
    gl_FragData[5]=vec4(shaderlodMakeupShadowDx,shaderlodMakeupShadowDy);
#else
    gl_FragData[5]=vec4(0.0);
#endif
#else
    ivec2 pixel=ivec2(gl_FragCoord.xy);if(any(notEqual(pixel,(pixel/2)*2)))return;
    #ifdef COLORED_SHADOW
    vec3 shadowValue=get_colored_shadow(shadowPos,dither);shadowValue=mix(shadowValue,vec3(1.0),shadowDiffuse);
    #else
    float shadowValue=get_shadow(shadowPos,dither);shadowValue=mix(shadowValue,1.0,shadowDiffuse);
    #endif
    // Native pre-albedo linear realLight, including the actual +0 gloss term and native grouping.
    vec3 realLight=omniLight+(shadowValue*((directLightColor*directLightStrength)+(directLightColor*material)))*(1.0-(rainStrength*0.75))+candleColor;
    if(shaderlodMakeupSpatialFinite(realLight))gl_FragData[5]=vec4(realLight,float(frameCounter));
#endif
}
bool shaderlodMakeupSpatialTry(vec4 color,float block_luma,out vec3 light){
    light=vec3(0.0);float material;int normalCode;if(!shaderlodMakeupSpatialEligible(color,block_luma,material,normalCode))return false;
    ivec2 pixel=ivec2(gl_FragCoord.xy),origin=(pixel/2)*2;
    if(any(lessThan(origin,ivec2(0)))||any(greaterThanEqual(origin+ivec2(1),ivec2(viewWidth,viewHeight))))return false;
    vec4 ownG=texelFetch2D(colortex8,pixel,0),ownS=texelFetch2D(colortex12,pixel,0),ownD=texelFetch2D(colortex13,pixel,0),ownO=texelFetch2D(colortex14,pixel,0),ownC=texelFetch2D(colortex15,pixel,0);
    if(ownC.a!=float(frameCounter)||any(notEqual(ownG,vec4(shaderlodMakeupSpatialPosition(),float(normalCode))))||any(notEqual(ownS,vec4(shadowPos,shadowDiffuse)))||any(notEqual(ownD,vec4(directLightColor,directLightStrength)))||any(notEqual(ownO,vec4(omniLight,
#if SHADERLOD_MAKEUP_SPATIAL_RATE == 1 || SHADERLOD_MAKEUP_WORLD_PIXEL == 1
gl_FogFragCoord
#else
material
#endif
)))||any(notEqual(ownC.rgb,candleColor)))return false;
    vec3 positions[4];vec4 s[4],d[4],o[4],c[4];
    for(int i=0;i<4;i++){ivec2 p=origin+ivec2(i-(i/2)*2,i/2);vec4 g=texelFetch2D(colortex8,p,0);s[i]=texelFetch2D(colortex12,p,0);d[i]=texelFetch2D(colortex13,p,0);o[i]=texelFetch2D(colortex14,p,0);c[i]=texelFetch2D(colortex15,p,0);positions[i]=g.xyz;
        if(g.a!=float(normalCode)||c[i].a!=float(frameCounter)||!shaderlodMakeupSpatialFinite(positions[i])||!shaderlodMakeupSpatialFinite(s[i].rgb)||!shaderlodMakeupSpatialFinite(d[i].rgb)||!shaderlodMakeupSpatialFinite(o[i].rgb)||!shaderlodMakeupSpatialFinite(c[i].rgb)
#if SHADERLOD_MAKEUP_SPATIAL_RATE == 1
||!(o[i].a>=256.0&&o[i].a<1e10)||((o[i].a>shaderlodFar)!=(ownO.a>shaderlodFar))
#else
||floatBitsToUint(o[i].a)!=0u
#endif
)return false;}
    vec3 ex=positions[1]-positions[0],ey=positions[2]-positions[0];float lx=length(ex),ly=length(ey);if(lx<=1e-6||ly<=1e-6||length(cross(ex,ey))<=0.0001*lx*ly)return false;
    vec3 normalView=normalize(flatNormal);float tolerance=min(0.08,0.015+0.015*min(lx,ly));
    for(int i=0;i<4;i++)for(int j=i+1;j<4;j++){if(abs(dot(positions[j]-positions[i],normalView))>tolerance||any(greaterThan(abs(d[j]-d[i]),vec4(1.0/255.0)))||any(greaterThan(abs(o[j].rgb-o[i].rgb),vec3(1.0/255.0)))||any(greaterThan(abs(c[j].rgb-c[i].rgb),vec3(1.0/255.0)))||abs(s[j].a-s[i].a)>1.0/255.0)return false;}
    vec4 value=texelFetch2D(colortex10,
#if SHADERLOD_MAKEUP_SPATIAL_RATE == 1
ivec2(origin.x,origin.y/2)
#else
origin
#endif
,0);if(value.a!=float(frameCounter)||!shaderlodMakeupSpatialFinite(value.rgb))return false;light=value.rgb;return true;
}
#if SHADERLOD_MAKEUP_SPATIAL_RATE == 1
void shaderlodMakeupSpatialRateEvaluate(){
 gl_FragData[0]=vec4(0.0,0.0,0.0,-1.0);
 ivec2 pixel=ivec2(gl_FragCoord.xy),origin=ivec2(pixel.x,pixel.y*2);
 if(shaderlodEffectsVersion!=2||shaderlodMakeupSpatialReady!=1||!shaderlod_optimization_enabled(shaderlodOptSpatialLightingRate)||shaderlodPreset!=1||shaderlodMakeupSpatialSize!=vec2(viewWidth,viewHeight)||frameCounter<0||frameCounter>16777216||((pixel.x/2)*2)!=pixel.x||origin.x+1>=int(viewWidth)||origin.y+1>=int(viewHeight))return;
 vec4 geometry=texelFetch2D(colortex8,origin,0),s=texelFetch2D(colortex12,origin,0),d=texelFetch2D(colortex13,origin,0),o=texelFetch2D(colortex14,origin,0),c=texelFetch2D(colortex15,origin,0);
 if(c.a!=float(frameCounter)||geometry.a<0.0||!shaderlodMakeupSpatialFinite(geometry.xyz)||!shaderlodMakeupSpatialFinite(s.xyz)||!shaderlodMakeupSpatialFinite(d.xyz)||!shaderlodMakeupSpatialFinite(o.xyz)||!shaderlodMakeupSpatialFinite(c.xyz)||!(o.a>=256.0&&o.a<1e10)||!(s.a>=0.0&&s.a<=1.0)||!(abs(d.a)<1e6))return;
 float dither=shiftedDither13(vec2(origin)+vec2(0.5));
#ifdef COLORED_SHADOW
 vec3 shadowValue=get_colored_shadow(s.xyz,dither);shadowValue=mix(shadowValue,vec3(1.0),s.a);
#else
 vec4 gradients=texelFetch2D(colortex10,origin,0);if(any(isnan(gradients))||any(isinf(gradients))||any(greaterThan(abs(gradients),vec4(1e10))))return;
 shaderlodMakeupShadowDx=gradients.xy;shaderlodMakeupShadowDy=gradients.zw;
 float shadowValue=get_shadow(s.xyz,dither);shadowValue=mix(shadowValue,1.0,s.a);
#endif
 vec3 realLight=o.rgb+(shadowValue*((d.rgb*d.a)+(d.rgb*0.0)))*(1.0-(rainStrength*0.75))+c.rgb;
 if(shaderlodMakeupSpatialFinite(realLight))gl_FragData[0]=vec4(realLight,float(frameCounter));
}
#endif
#endif

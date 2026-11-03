#if SHADERLOD_MAKEUP_WORLD_PIXEL == 1 && SHADERLOD_MAKEUP_SPATIAL_SOURCE == 1
ivec2 shaderlodMakeupWorldSize(){return max(ivec2(1),(ivec2(viewWidth,viewHeight)*85)/100);}
ivec2 shaderlodMakeupWorldLo(ivec2 p){ivec2 full=ivec2(viewWidth,viewHeight),small=shaderlodMakeupWorldSize();return (p*full+small-1)/small;}
bool shaderlodMakeupWorldTry(vec4 color,float block_luma,out vec3 rgb){
 rgb=vec3(0.0);float material;int normalCode;if(color.a!=1.0||!shaderlodMakeupSpatialEligible(color,block_luma,material,normalCode)||!(frogAdjust>=0.0&&frogAdjust<=1.0))return false;
 ivec2 pixel=ivec2(gl_FragCoord.xy),full=ivec2(viewWidth,viewHeight),small=shaderlodMakeupWorldSize(),cache=(pixel*small)/full;
 if(any(lessThan(pixel,ivec2(0)))||any(greaterThanEqual(pixel,full)))return false;
 ivec2 lo=shaderlodMakeupWorldLo(cache),hi=min(full-1,shaderlodMakeupWorldLo(cache+1)-1),span=hi-lo;
 if(any(lessThan(span,ivec2(0)))||any(greaterThan(span,ivec2(1))))return false;
 vec4 ownG=texelFetch2D(colortex8,pixel,0),ownS=texelFetch2D(colortex12,pixel,0),ownD=texelFetch2D(colortex13,pixel,0),ownO=texelFetch2D(colortex14,pixel,0),ownC=texelFetch2D(colortex15,pixel,0),ownA=texelFetch2D(colortex9,pixel,0),ownF=texelFetch2D(colortex11,pixel,0);
 if(ownC.a!=float(frameCounter)||any(notEqual(ownG,vec4(shaderlodMakeupSpatialPosition(),float(normalCode))))||any(notEqual(ownS,vec4(shadowPos,shadowDiffuse)))||any(notEqual(ownD,vec4(directLightColor,directLightStrength)))||any(notEqual(ownO,vec4(omniLight,gl_FogFragCoord)))||any(notEqual(ownC.rgb,candleColor))||any(notEqual(ownA,color))||ownF.a!=frogAdjust||!shaderlodMakeupSpatialFinite(ownF.rgb))return false;
 vec3 positions[4];vec4 s[4],d[4],o[4],c[4],f[4];
 for(int i=0;i<4;i++){ivec2 p=lo+ivec2(min(i-(i/2)*2,span.x),min(i/2,span.y));vec4 g=texelFetch2D(colortex8,p,0);s[i]=texelFetch2D(colortex12,p,0);d[i]=texelFetch2D(colortex13,p,0);o[i]=texelFetch2D(colortex14,p,0);c[i]=texelFetch2D(colortex15,p,0);f[i]=texelFetch2D(colortex11,p,0);positions[i]=g.xyz;vec4 albedo=texelFetch2D(colortex9,p,0);
  if(g.a!=float(normalCode)||c[i].a!=float(frameCounter)||!shaderlodMakeupSpatialFinite(g.xyz)||!shaderlodMakeupSpatialFinite(s[i].rgb)||!shaderlodMakeupSpatialFinite(d[i].rgb)||!shaderlodMakeupSpatialFinite(o[i].rgb)||!shaderlodMakeupSpatialFinite(c[i].rgb)||!shaderlodMakeupSpatialFinite(f[i].rgb)||any(notEqual(albedo,color))||!(f[i].a>=0.0&&f[i].a<=1.0)||((o[i].a>shaderlodFar)!=(ownO.a>shaderlodFar)))return false;
 }
 vec3 ex=positions[1]-positions[0],ey=positions[2]-positions[0];float lx=length(ex),ly=length(ey);if((span.x>0&&lx<=1e-6)||(span.y>0&&ly<=1e-6)||(span.x>0&&span.y>0&&length(cross(ex,ey))<=0.0001*lx*ly))return false;
 float tangent=span.x>0&&span.y>0?min(lx,ly):max(lx,ly),tolerance=min(0.08,0.015+0.015*tangent);vec3 n=normalize(flatNormal);
 for(int i=0;i<4;i++)for(int j=i+1;j<4;j++)if(abs(dot(positions[j]-positions[i],n))>tolerance||any(greaterThan(abs(d[j]-d[i]),vec4(1.0/255.0)))||any(greaterThan(abs(o[j].rgb-o[i].rgb),vec3(1.0/255.0)))||any(greaterThan(abs(c[j].rgb-c[i].rgb),vec3(1.0/255.0)))||abs(s[j].a-s[i].a)>1.0/255.0||any(greaterThan(abs(f[j]-f[i]),vec4(1.0/255.0))))return false;
 vec4 value=texelFetch2D(colortex10,cache,0);if(value.a!=float(frameCounter)||!shaderlodMakeupSpatialFinite(value.rgb)||any(lessThan(value.rgb,vec3(0.0)))||any(greaterThan(value.rgb,vec3(50.0))))return false;rgb=value.rgb;return true;
}
#endif

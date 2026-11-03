#include "/lib/shaderlod/unbound_spatial_lighting.glsl"
uniform sampler2D shaderlodUnboundSpatialDiffuse;
uniform sampler2D shaderlodUnboundSpatialHighlight;
uniform sampler2D shaderlodUnboundSpatialScene;
/* RENDERTARGETS:11 */
void main(){
 ivec2 p=ivec2(gl_FragCoord.xy);vec4 color=texelFetch(shaderlodUnboundSpatialScene,p,0);
 vec4 a,b,c,m;vec3 d,h;bool valid=shaderlodUnboundSpatialInput(p,a,b,c,m);
 vec4 payload;valid=valid&&shaderlodUnboundSpatialPayload(p,payload);
 if(valid){
  ivec2 anchor=shaderlodUnboundSpatialMode==3?p:(p/2)*2;
  ivec2 address=shaderlodUnboundSpatialMode==2?anchor/2:anchor;
  vec4 cachedD=texelFetch(shaderlodUnboundSpatialDiffuse,address,0),cachedH=texelFetch(shaderlodUnboundSpatialHighlight,address,0);
  bool cached=(shaderlodUnboundSpatialMode==3||shaderlodUnboundSpatialTile(anchor))&&
   cachedD.a==float(frameCounter)&&cachedH.a==float(anchor.x+int(viewWidth)*anchor.y)&&shaderlodUnboundSpatialFinite(cachedD)&&shaderlodUnboundSpatialFinite(cachedH);
  if(cached){d=cachedD.rgb;h=cachedH.rgb;}else valid=shaderlodUnboundSpatialEvaluate(p,d,h);
  if(valid){color=vec4(payload.rgb,1.0);color.rgb*=d;color.rgb+=h;color.rgb*=pow2(1.0-darknessLightFactor);}
 }
 gl_FragData[0]=color;
}

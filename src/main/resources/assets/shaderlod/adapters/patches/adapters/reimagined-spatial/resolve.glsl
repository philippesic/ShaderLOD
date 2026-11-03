#include "/lib/shaderlod/reimagined_spatial_lighting.glsl"
uniform sampler2D shaderlodReimaginedSpatialDiffuse;
uniform sampler2D shaderlodReimaginedSpatialHighlight;
uniform sampler2D shaderlodReimaginedSpatialScene;
/* RENDERTARGETS:11 */
void main(){
 ivec2 p=ivec2(gl_FragCoord.xy);vec4 color=texelFetch(shaderlodReimaginedSpatialScene,p,0);
 vec4 a,b,c,m;vec3 d,h;bool valid=shaderlodReimaginedSpatialInput(p,a,b,c,m);
 vec4 payload;valid=valid&&shaderlodReimaginedSpatialPayload(p,payload);
 if(valid){
  ivec2 anchor=shaderlodReimaginedSpatialMode==3?p:(p/2)*2;
  ivec2 address=shaderlodReimaginedSpatialMode==2?anchor/2:anchor;
  vec4 cachedD=texelFetch(shaderlodReimaginedSpatialDiffuse,address,0),cachedH=texelFetch(shaderlodReimaginedSpatialHighlight,address,0);
  bool cached=(shaderlodReimaginedSpatialMode==3||shaderlodReimaginedSpatialTile(anchor))&&
   cachedD.a==float(frameCounter)&&cachedH.a==float(anchor.x+int(viewWidth)*anchor.y)&&shaderlodReimaginedSpatialFinite(cachedD)&&shaderlodReimaginedSpatialFinite(cachedH);
  if(cached){d=cachedD.rgb;h=cachedH.rgb;}else valid=shaderlodReimaginedSpatialEvaluate(p,d,h);
  if(valid){color=vec4(payload.rgb,1.0);color.rgb*=d;color.rgb+=h;color.rgb*=pow2(1.0-darknessLightFactor);}
 }
 gl_FragData[0]=color;
}

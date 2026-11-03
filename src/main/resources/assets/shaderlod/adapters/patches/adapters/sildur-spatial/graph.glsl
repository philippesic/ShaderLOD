
uniform vec2 shaderlodSildurSpatialSize;
uniform int shaderlodOptSpatialLightingRate;
int shaderlodSildurSpatialMode(){
 if(shaderlodSildurSpatialReady!=1 || !all(equal(shaderlodSildurSpatialSize,vec2(viewWidth,viewHeight))))return 0;
#if SHADERLOD_SILDUR_SPATIAL_REUSE == 1
 return shaderlod_optimization_enabled(shaderlodOptSpatialLightingReuse)?1:0;
#elif SHADERLOD_SILDUR_LIGHTING_RATE == 1
 return shaderlod_optimization_enabled(shaderlodOptSpatialLightingRate)?2:0;
#else
 return 0;
#endif
}
bool shaderlodSildurSpatialTile(ivec2 origin){
 vec2 size=vec2(viewWidth,viewHeight);
 if(origin.x<1 || origin.y<1 || origin.x+2>=int(viewWidth) || origin.y+2>=int(viewHeight) || isEyeInWater!=0 || isNether ||
  !(shaderlodFar>shaderlodNear && shaderlodNear>0.0 && shaderlodFar<=4096.0) ||
  frameCounter<0 || frameCounter>=16777216)return false;
 vec2 uv=(vec2(origin)+0.5)/size;
 vec3 first=texture2DLod(colortex1,uv,0.0).xyz;
 vec3 normal=utilDecode(texture2DLod(colortex2,uv,0.0).xy);
 float depth=texture2DLod(depthtex0,uv,0.0).x;
 vec3 position=utilScreenSpace(vec3(uv,depth));
 if(!(first.b==0.0 && depth>0.0 && depth<1.0 && -position.z>=max(shaderlodFar,32.0) && -position.z<1e6 && dot(normal,normal)>0.99 && dot(normal,normal)<1.01))return false;
 // Bounded material/depth/normal/lightmap continuity; shadows remain a manual
 // quality boundary, as in existing Photon/Bliss spatial experiments.
 for(int y=-1;y<=2;y++)for(int x=-1;x<=2;x++){
  vec2 p=(vec2(origin+ivec2(x,y))+0.5)/size;
  vec3 material=texture2DLod(colortex1,p,0.0).xyz;
  vec3 n=utilDecode(texture2DLod(colortex2,p,0.0).xy);
  float d=texture2DLod(depthtex0,p,0.0).x;
  if(!all(equal(material,first)) || dot(normal,n)<0.9995 || !(d>0.0 && d<1.0) || abs(d-depth)>0.00025 || texture2DLod(depthtex2,p,0.0).x!=d)return false;
 }
 return true;
}

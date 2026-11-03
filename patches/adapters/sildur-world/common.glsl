#ifndef SHADERLOD_SILDUR_WORLD_READY_DECLARED
uniform int shaderlodSildurWorldReady;
#endif
uniform vec2 shaderlodSildurWorldSize;
vec2 shaderlodSildurWorldLow(){return floor(vec2(viewWidth,viewHeight)*0.85);}
vec2 shaderlodSildurWorldPixel(ivec2 cell){return floor((vec2(cell)+0.5)/shaderlodSildurWorldLow()*vec2(viewWidth,viewHeight))+0.5;}
bool shaderlodSildurWorldActive(){return shaderlodSildurWorldReady==1 && shaderlod_optimization_enabled(shaderlodOptPixelScale) && all(equal(shaderlodSildurWorldSize,vec2(viewWidth,viewHeight))) && frameCounter>=0 && frameCounter<16777216 && viewWidth>=4.0 && viewHeight>=4.0 && viewWidth<=8192.0 && viewHeight<=8192.0 && isEyeInWater==0 && !isNether && shaderlodNear>0.0;}
bool shaderlodSildurWorldSurface(vec2 pixel,out vec3 position,out vec3 normal,out vec4 albedo,out vec2 lm){
 vec2 uv=pixel/vec2(viewWidth,viewHeight);float depth=texture2DLod(depthtex0,uv,0.0).x;
 float hand=texture2DLod(depthtex2,uv,0.0).x;vec3 shaderlodSurfaceData=texture2DLod(colortex1,uv,0.0).xyz;
 normal=utilDecode(texture2DLod(colortex2,uv,0.0).xy);albedo=texture2DLod(colortex4,uv,0.0);lm=shaderlodSurfaceData.xy;
#ifdef TAA
 position=utilScreenSpace(vec3(uv-offsets[framemod8]/vec2(viewWidth,viewHeight)*0.5,depth));
#else
 position=utilScreenSpace(vec3(uv,depth));
#endif
 return pixel.x>=0.5 && pixel.y>=0.5 && pixel.x<viewWidth && pixel.y<viewHeight && depth>0.0 && depth<1.0 && depth==hand && shaderlodSurfaceData.z==0.0 && -position.z>max(32.0,shaderlodNear) && -position.z<1e6 && dot(normal,normal)>0.99 && dot(normal,normal)<1.01 && all(greaterThanEqual(albedo,vec4(0.0))) && all(lessThan(albedo,vec4(1e6)));
}

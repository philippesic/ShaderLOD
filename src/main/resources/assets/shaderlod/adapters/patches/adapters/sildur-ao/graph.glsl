// Same existing scalarAO graph law; field validity requires actual completed native draw.
uniform int shaderlodOptAoRenderScale;
uniform int shaderlodSildurAoTargetReady;
uniform vec2 shaderlodSildurAoTargetSize;
bool shaderlodSildurAoGraphActive(vec2 fullSize) {
 return shaderlod_optimization_enabled(shaderlodOptAoRenderScale) && shaderlodSildurAoTargetReady==1 &&
  all(equal(shaderlodSildurAoTargetSize,fullSize)) && all(greaterThanEqual(fullSize,vec2(2.0))) &&
  all(lessThanEqual(fullSize,vec2(16384.0))) && shaderlodNear>0.0 && shaderlodNear<shaderlodFar &&
  shaderlodFar<=4096.0 && frameCounter>=0 && frameCounter<16777216 &&
  gbufferProjection[0].z==0.0 && gbufferProjection[1].z==0.0 && gbufferProjection[0].w==0.0 &&
  gbufferProjection[1].w==0.0 && gbufferProjection[2].w==-1.0 && gbufferProjection[3].w==0.0;
}

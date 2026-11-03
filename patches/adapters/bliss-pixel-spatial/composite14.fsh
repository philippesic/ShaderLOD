#version 120
#include "/lib/shaderlod/bliss_pixel_spatial_option.glsl"
#if SHADERLOD_BLISS_PIXEL_GRAPH == 1 && (SHADERLOD_BLISS_SPATIAL_REUSE == 1 || SHADERLOD_BLISS_LIGHTING_RATE == 1)

#define OVERWORLD_SHADER

#include "/dimensions/composite12.fsh"
#else
void main(){}

#endif

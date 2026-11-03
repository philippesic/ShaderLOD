// Private native SOLID full-coverage prepass. Native vertex arithmetic and AA stay intact.
#define SHADERLOD_MAKEUP_DEPTH_PRIMING 0 // [0 1]
#if (SHADERLOD_MAKEUP_DEPTH_PRIMING == 1 || SHADERLOD_MAKEUP_SPATIAL_REUSE == 1 || SHADERLOD_MAKEUP_SPATIAL_RATE == 1 || SHADERLOD_MAKEUP_WORLD_PIXEL == 1) && defined IS_IRIS && defined GBUFFER_TERRAIN && !defined NETHER && !defined THE_END && !defined UNKNOWN_DIM && MC_VERSION == 12101 && (IRIS_VERSION == 10808 || IRIS_VERSION == 10812) && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH
invariant gl_Position;
#endif

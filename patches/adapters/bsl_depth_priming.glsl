// Private opaque-subset depth prepass. Native colour arithmetic is unchanged.
#define SHADERLOD_BSL_DEPTH_PRIMING 0 // [0 1]
#if SHADERLOD_BSL_DEPTH_PRIMING == 1 && defined IS_IRIS && defined OVERWORLD && MC_VERSION == 12101 && (IRIS_VERSION == 10808 || IRIS_VERSION == 10812) && !defined ADVANCED_MATERIALS && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH
#ifdef VSH
invariant gl_Position;
#endif
#endif

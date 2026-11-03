// ShaderLOD G06, LGPL-3.0-or-later. Exact source archive pinned by builder.
#if G06_AO_SPATIAL > 0 && G06_AO_SPATIAL <= 3 && AO == 1 && AOSTEPS >= 5 && defined IS_IRIS && IRIS_VERSION == 10812 && MC_VERSION == 12101 && defined MC_OS_MAC && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH && !defined NETHER && !defined THE_END && !defined UNKNOWN_DIM
#define G06_AO_ACTIVE
#endif

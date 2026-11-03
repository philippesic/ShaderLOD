// Literal pinned astralex native AO function; only its name differs.
// Upstream shader authorship/license is retained in the original archive.
float ShaderlodAstraNativeAmbientOcclusion(float z0, float linearZ0, float dither) {
			if (z0 < 0.56) return 1.0;
			float ao = 0.0;
			float fovScaleAO = 0.0;
			float radius = 0.5;
			int samples = 0;
			float scm = 0.0;

			float sampleDepth = 0.0, angle = 0.0, dist = 0.0;

			#if AO_FOVSCALE ==1
				fovScaleAO = 3.425;
			#elif AO_FOVSCALE ==2
				fovScaleAO = 2.74;
			#elif AO_FOVSCALE ==3
				fovScaleAO = 2.055;
			#elif AO_FOVSCALE ==4
				fovScaleAO = 1.37;
			#elif AO_FOVSCALE ==5
				fovScaleAO = 0.685;
			#endif

			#if AO_QUALITY == 1
				samples = 4;
				scm = 0.4;
			#elif AO_QUALITY == 2
				samples = 12;
				scm = 0.6;
			#endif

			float fovScale = gbufferProjection[1][1] / fovScaleAO;
			float distScale = max(farMinusNear * linearZ0 + near, 3.0);
			vec2 scale = radius * vec2(scm / aspectRatio, scm) * fovScale / distScale;

			for (int i = 1; i <= samples; i++) {
				vec2 offset = OffsetDist(i + dither, samples) * scale;
				if (i % 2 == 0) offset.y = -offset.y;

				vec2 coord1 = texCoord + offset;
				vec2 coord2 = texCoord - offset;

				sampleDepth = GetLinearDepth(texture2D(depthtex0, coord1).r);
				float aosample = farMinusNear * (linearZ0 - sampleDepth) * 2.0;
				angle = clamp(0.5 - aosample, 0.0, 1.0);
				dist = clamp(0.5 * aosample - 1.0, 0.0, 1.0);

				sampleDepth = GetLinearDepth(texture2D(depthtex0, coord2).r);
				aosample = farMinusNear * (linearZ0 - sampleDepth) * 2.0;
				angle += clamp(0.5 - aosample, 0.0, 1.0);
				dist += clamp(0.5 * aosample - 1.0, 0.0, 1.0);

				ao += clamp(angle + dist, 0.0, 1.0);
			}
			ao *= 1.0 / float(samples);
			ao *= sqrt(ao) * 0.9 + 0.1;

				return pow(ao, AO_STRENGTH * 2.0);
		}

float shaderlodAstraGraph0AmbientOcclusion(float z0, float linearZ0, float dither) {
    // Draw-uniform dispatch; no fragment coordinates, material or LOD enter the condition.
    if (!shaderlod_optimization_enabled(shaderlodOptAoBudget) &&
        !shaderlod_optimization_enabled(shaderlodOptAoDepthReuse))
        return ShaderlodAstraNativeAmbientOcclusion(z0, linearZ0, dither);
    return DoAmbientOcclusion(z0, linearZ0, dither);
}

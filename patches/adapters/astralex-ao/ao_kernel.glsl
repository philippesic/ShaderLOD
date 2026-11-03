	float GetLinearDepth(float depth) {
		return (2.0 * near) / (far + near - depth * (far - near));
	}

	float BlueNoise(vec2 coord) {
		return texelFetch(noisetex, ivec2(coord)% 64, 0).b;
	}

		float farMinusNear = far - near;

    #include "/lib/shaderlod/adapter.glsl"

		vec2 OffsetDist(float x, int s) {
		float n = fract(x * 1.414) * 3.1415;
		return pow2(vec2(cos(n), sin(n)) * x / s);
		}

		float shaderlodAstraAoKernel(vec2 sourceUv, float z0, float linearZ0, float dither) {
			if (z0 < 0.56) return 1.0;
			float ao = 0.0;
			float fovScaleAO = 0.0;
			float radius = 0.5;
			int samples = 0;
			float scm = 0.0;

			float sampleDepth = 0.0, angle = 0.0, dist = 0.0;
            #if SHADERLOD_REFINEMENT_ADAPTER == 1 && defined DEFERRED && defined FRAGMENT && defined OVERWORLD && !defined DISTANT_HORIZONS && !defined VOXY
                // Actual caller already computed GetLinearDepth(z0); native reads remain unconditional.
                bool shaderlodAstraLinearReuse = shaderlod_optimization_enabled(shaderlodOptAoDepthReuse) && near > 0.0 && far > near && far < 1e8;
                float shaderlodAstraPreviousRawDepth = z0;
                float shaderlodAstraPreviousLinearDepth = linearZ0;
                bool shaderlodAstraPreviousLinearValid = z0 >= 0.0 && z0 <= 1.0 && abs(linearZ0) < 1e8;
            #endif


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

            #if SHADERLOD_REFINEMENT_ADAPTER == 1 && defined DEFERRED && defined FRAGMENT && defined OVERWORLD && AO_QUALITY == 2 && AO_FOVSCALE >= 1 && AO_FOVSCALE <= 5 && !defined DISTANT_HORIZONS && !defined VOXY
            // Budget complete two-iteration groups: retain +/- pairs and even y populations.
            int shaderlodAoSamples = 2 * shaderlod_astralex_ao_groups(samples / 2);
            #endif
			float fovScale = gbufferProjection[1][1] / fovScaleAO;
			float distScale = max(farMinusNear * linearZ0 + near, 3.0);
			vec2 scale = radius * vec2(scm / aspectRatio, scm) * fovScale / distScale;

            #if SHADERLOD_REFINEMENT_ADAPTER == 1 && defined DEFERRED && defined FRAGMENT && defined OVERWORLD && AO_QUALITY == 2 && AO_FOVSCALE >= 1 && AO_FOVSCALE <= 5 && !defined DISTANT_HORIZONS && !defined VOXY
            for (int i = 1; i <= shaderlodAoSamples; i++) {
                vec2 offset;
                if (shaderlodAoSamples < samples) {
                    // Same native radial endpoint and dither/N margin, redistributed strata.
                    float shaderlodAoIndex = float(i) * float(samples) / float(shaderlodAoSamples) + dither;
                    offset = OffsetDist(shaderlodAoIndex, samples) * scale;
                } else {
                    offset = OffsetDist(i + dither, samples) * scale;
                }
            #else
			for (int i = 1; i <= samples; i++) {
				vec2 offset = OffsetDist(i + dither, samples) * scale;
            #endif
				if (i % 2 == 0) offset.y = -offset.y;

				vec2 coord1 = sourceUv + offset;
				vec2 coord2 = sourceUv - offset;

                #if SHADERLOD_REFINEMENT_ADAPTER == 1 && defined DEFERRED && defined FRAGMENT && defined OVERWORLD && !defined DISTANT_HORIZONS && !defined VOXY
                {
                    float shaderlodAstraCurrentRawDepth = texture2D(depthtex0, coord1).r;
                    bool shaderlodAstraCurrentDepthValid = shaderlodAstraCurrentRawDepth >= 0.0 && shaderlodAstraCurrentRawDepth <= 1.0;
                    if (shaderlodAstraLinearReuse && shaderlodAstraCurrentDepthValid && shaderlodAstraPreviousLinearValid && shaderlodAstraCurrentRawDepth == shaderlodAstraPreviousRawDepth)
                        sampleDepth = shaderlodAstraPreviousLinearDepth;
                    else
                        sampleDepth = GetLinearDepth(shaderlodAstraCurrentRawDepth);
                    shaderlodAstraPreviousRawDepth = shaderlodAstraCurrentRawDepth;
                    shaderlodAstraPreviousLinearDepth = sampleDepth;
                    shaderlodAstraPreviousLinearValid = shaderlodAstraCurrentDepthValid && abs(sampleDepth) < 1e8;
                }
                #else
				sampleDepth = GetLinearDepth(texture2D(depthtex0, coord1).r);
                #endif
				float aosample = farMinusNear * (linearZ0 - sampleDepth) * 2.0;
				angle = clamp(0.5 - aosample, 0.0, 1.0);
				dist = clamp(0.5 * aosample - 1.0, 0.0, 1.0);

                #if SHADERLOD_REFINEMENT_ADAPTER == 1 && defined DEFERRED && defined FRAGMENT && defined OVERWORLD && !defined DISTANT_HORIZONS && !defined VOXY
                {
                    float shaderlodAstraCurrentRawDepth = texture2D(depthtex0, coord2).r;
                    bool shaderlodAstraCurrentDepthValid = shaderlodAstraCurrentRawDepth >= 0.0 && shaderlodAstraCurrentRawDepth <= 1.0;
                    if (shaderlodAstraLinearReuse && shaderlodAstraCurrentDepthValid && shaderlodAstraPreviousLinearValid && shaderlodAstraCurrentRawDepth == shaderlodAstraPreviousRawDepth)
                        sampleDepth = shaderlodAstraPreviousLinearDepth;
                    else
                        sampleDepth = GetLinearDepth(shaderlodAstraCurrentRawDepth);
                    shaderlodAstraPreviousRawDepth = shaderlodAstraCurrentRawDepth;
                    shaderlodAstraPreviousLinearDepth = sampleDepth;
                    shaderlodAstraPreviousLinearValid = shaderlodAstraCurrentDepthValid && abs(sampleDepth) < 1e8;
                }
                #else
				sampleDepth = GetLinearDepth(texture2D(depthtex0, coord2).r);
                #endif
				aosample = farMinusNear * (linearZ0 - sampleDepth) * 2.0;
				angle += clamp(0.5 - aosample, 0.0, 1.0);
				dist += clamp(0.5 * aosample - 1.0, 0.0, 1.0);

				ao += clamp(angle + dist, 0.0, 1.0);
			}
            #if SHADERLOD_REFINEMENT_ADAPTER == 1 && defined DEFERRED && defined FRAGMENT && defined OVERWORLD && AO_QUALITY == 2 && AO_FOVSCALE >= 1 && AO_FOVSCALE <= 5 && !defined DISTANT_HORIZONS && !defined VOXY
            if (shaderlodAoSamples < samples)
                ao *= 1.0 / float(shaderlodAoSamples);
            else
            #endif
			ao *= 1.0 / float(samples);
			ao *= sqrt(ao) * 0.9 + 0.1;

				return pow(ao, AO_STRENGTH * 2.0);
		}

float DoAmbientOcclusion(float z0, float linearZ0, float dither) {
    return shaderlodAstraAoKernel(texCoord, z0, linearZ0, dither);
}

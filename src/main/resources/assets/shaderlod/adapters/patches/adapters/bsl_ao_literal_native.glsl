// Literal BSL v10.1.8 AmbientOcclusion body; only the function name differs.
// Original by Capt Tatsu, https://capttatsu.com ; upstream license/credits retained.
float ShaderlodBslNativeAmbientOcclusion(float z, sampler2D depthtex, mat4 projectionInverse, float near, float far, float radius, float dither, bool isLod) {
	float ao = 0.0;
	float pointiness = 0.0;
	
	if(z >= 1.0) return 1.0;

	bool new = texCoord.x > 0.5;

	float hand = float(z < 0.56 && !isLod);
	float linZ = GetLinearDepth(z, projectionInverse);

	#ifdef TAA
	#if TAA_MODE == 0
	dither = fract(dither + frameCounter * 0.618);
	#else
	dither = fract(dither + frameCounter * 0.5);
	#endif
	#endif

	float currentStep = 0.2475 * dither + 0.01;

	float uncappedDist = linZ;
	float distanceScale = max(uncappedDist, 2.5);
	float fovScale = gbufferProjection[1][1] / 1.37;
	vec2 scale = radius * vec2(1.0 / aspectRatio, 1.0) * fovScale / distanceScale;
	float differenceScale = uncappedDist / distanceScale;

	vec2 baseOffset = GetDirection(dither);
	
	#if AO_METHOD == 0
	vec3 viewPos = GetViewPos(vec3(texCoord.xy, z), projectionInverse);
	vec3 normal = GetReconstructedNormal(depthtex, linZ, viewPos, projectionInverse);
	float angleThreshold = isLod ? 0.15 : (0.15 + linZ * 0.01);

	for (int i = 0; i < 4; i++) {
		vec2 offset = baseOffset * currentStep * scale;
		float visibility = 0.0;

		for(int j = 0; j < 2; j++){
			vec2 sampleCoord = texCoord + offset;
			float sampleZ = texture2D(depthtex, sampleCoord).r;
			vec3 sampleViewPos = GetViewPos(vec3(sampleCoord, sampleZ), projectionInverse);
			vec3 difference = (sampleViewPos.xyz - viewPos.xyz) / (radius * currentStep * differenceScale);
			float attenuation = clamp(1.0 + 0.5 / currentStep - 0.25 * length(difference), 0.0, 1.0);
			
			if (hand > 0.5) {
				visibility += clamp(0.5 - difference.z * 4096.0, 0.0, 1.0);
			}else {
				float angle = dot(normal, normalize(difference)) * (1.0 + angleThreshold);
				visibility += 0.5 - max(angle - angleThreshold, 0.0) * attenuation;
				pointiness += max(-angle - angleThreshold, 0.0);
			}

			offset = -offset;
		}
		
		ao += clamp(visibility, 0.0, 1.0);

		currentStep += 0.2475;
		baseOffset = vec2(baseOffset.x - baseOffset.y, baseOffset.x + baseOffset.y) * 0.7071;
	}
	#else
	float mult = (0.7 / radius) * (hand > 0.5 ? 1024.0 : 1.0);

	for(int i = 0; i < 4; i++) {
		vec2 offset = baseOffset * currentStep * scale;
		float angle = 0.0, dist = 0.0;

		for(int i = 0; i < 2; i++){
			float sampleDepth = GetLinearDepth(texture2D(depthtex, texCoord + offset).r, projectionInverse);
			float aoSample = (linZ - sampleDepth) * mult / currentStep;

			angle += clamp(0.5 - aoSample, 0.0, 1.0);
			dist += clamp(0.25 * aoSample - 1.0, 0.0, 1.0);

			offset = -offset;
		}

		ao += clamp(angle + dist, 0.0, 1.0);

		currentStep += 0.2475;
		baseOffset = vec2(baseOffset.x - baseOffset.y, baseOffset.x + baseOffset.y) * 0.7071;
	}
	#endif

	ao *= 0.25;
	pointiness *= 0.25;

	ao = mix(ao, 1.0, pointiness);
	
	return ao;
}
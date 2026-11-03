// Literal pinned solas-shader native AO function; only its name differs.
// Upstream shader authorship/license is retained in the original archive.
float ShaderlodSolasNativeCalculateAO(float z, sampler2D depthtex, mat4 projectionInverse, float near, float far, float radius, bool isLod) {
	if (z >= 1.0) return 1.0;

	float ao = 0.0;
	float pointiness = 0.0;

	float hand = float(z < 0.56 && !isLod);
	float linZ = GetLinearDepth(z, projectionInverse);

    float dither = Bayer8(gl_FragCoord.xy);
    #ifdef TAA
            dither = fract(frameTimeCounter * 16.0 + dither);
    #endif

	float currentStep = 0.2475 * dither + 0.01;

	float distanceScale = max(linZ, 2.5);
	float fovScale = gbufferProjection[1][1] / 1.37;
	vec2 scale = radius * vec2(1.0 / aspectRatio, 1.0) * fovScale / distanceScale;
	float differenceScale = linZ / distanceScale;

    dither *= 6.28;
	vec2 baseOffset = vec2(cos(dither), sin(dither));

	vec3 viewPos = ToView2(vec3(texCoord.xy, z), projectionInverse);
	vec3 normal = reconstructNormal(depthtex, linZ, viewPos, projectionInverse);
	float angleThreshold = isLod ? 0.15 : (0.15 + linZ * 0.01);

	for (int i = 0; i < 4; i++) {
		vec2 offset = baseOffset * currentStep * scale;
		float visibility = 0.0;

		for(int j = 0; j < 2; j++){
			vec2 sampleCoord = texCoord + offset;
			float sampleZ = texture2D(depthtex, sampleCoord).r;
			vec3 sampleViewPos = ToView2(vec3(sampleCoord, sampleZ), projectionInverse);
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
	ao *= 0.25;
	pointiness *= 0.25;

	return mix(ao, 1.0, pointiness);
}

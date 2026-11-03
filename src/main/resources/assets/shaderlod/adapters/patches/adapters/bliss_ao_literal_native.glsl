// Literal Bliss v2.1.2 SSAO body; only the function name differs.
// Original Chocapic13 / Xonk ancestry and pack license/credits retained; private local use.
vec2 shaderlodBlissNativeSSAO(
	vec3 viewPos, vec3 normal, bool hand, bool leaves, float noise
){
	// if(hand) return vec2(1.0,0.0);
	int samples = 7;
	float occlusion = 0.0; 
	float sss = 0.0;


	float dist = 1.0 + clamp(viewPos.z*viewPos.z/50.0,0,5); // shrink sample size as distance increases
	float mulfov2 = gbufferProjection[1][1]/(3 * dist);
	float maxR2 = viewPos.z*viewPos.z*mulfov2*2.0 * 5.0 / mix(4.0, 50.0, clamp(viewPos.z*viewPos.z - 0.1,0,1));

	#ifdef Ambient_SSS
		float maxR2_2 = viewPos.z;//*viewPos.z*mulfov2*2.*2./4.0;

		float dist3 = clamp(1-exp( viewPos.z*viewPos.z / -50),0,1);
		// if(leaves) maxR2_2 = 0.1;
		// if(leaves) maxR2_2 = mix(10, maxR2_2, dist3);
	#endif

	vec2 acc = -(TAA_Offset*(texelSize/2.0))*RENDER_SCALE ;
	
	// vec2 BLUENOISE = blueNoise(gl_FragCoord.xy).rg;

	int n = 0;

	float leaf = leaves ? -0.5 : 0.0;

	for (int i = 0; i < samples; i++) {
		
		// vec2 sampleOffset = (SpiralSample(i, 7, 8 , noise)) * mulfov2  * clamp(0.05 + i*0.095, 0.0,0.3)  ;
		vec2 sampleOffset = CleanSample(i, samples - 1, noise) * mulfov2 * 0.3 ;

		ivec2 offset = ivec2(gl_FragCoord.xy + sampleOffset*vec2(viewWidth,viewHeight*aspectRatio)*RENDER_SCALE);

		if (offset.x >= 0 && offset.y >= 0 && offset.x < viewWidth*RENDER_SCALE.x && offset.y < viewHeight*RENDER_SCALE.y ) {
			#ifdef DISTANT_HORIZONS
				float dhdepth = texelFetch2D(dhDepthTex1, offset,0).x;
			#else
				float dhdepth = 0.0;
			#endif

			vec3 t0 = toScreenSpace_DH((offset*texelSize+acc+0.5*texelSize) * (1.0/RENDER_SCALE), convertHandDepth_2(texelFetch2D(depthtex1, offset,0).x, hand), dhdepth);
		
			vec3 vec = (t0.xyz - viewPos);
			float dsquared = dot(vec, vec);
			
			if (dsquared > 1e-5){
				
				if( dsquared < maxR2){
					float NdotV = clamp(dot(vec*inversesqrt(dsquared), normalize(normal)),0.,1.);
					occlusion += NdotV * clamp(1.0-dsquared/maxR2,0.0,1.0);
				}

				#ifdef Ambient_SSS
					sss += clamp(leaf - dot(vec, normalize(normal)),0.0,1.0);
				#endif

				n += 1;
			}
		}
	}
	return max(1.0 - vec2(occlusion*AO_Strength, sss)/n, 0.0);
}

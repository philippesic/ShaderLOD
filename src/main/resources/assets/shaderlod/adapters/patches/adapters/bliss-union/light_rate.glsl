// Bliss2.1.2 native noise functions below are retained verbatim except explicitLOD0.
// Authors/upstream license/credits remain in the original archive. Private local port.
#if !defined SHADERLOD_BLISS_LIGHT_RATE_INCLUDED
#define SHADERLOD_BLISS_LIGHT_RATE_INCLUDED
#if SHADERLOD_EFFECT_ADAPTER == 1 && defined OVERWORLD_SHADER && defined EXCLUDE_WRITE_TO_LUT && !defined ReflectedFog && !defined CLOUDSHADOWSONLY && !defined DISTANT_HORIZONS && !defined VOXY && defined GL_ARB_shader_texture_lod
float shaderlod_light_densityAtPos(in vec3 pos){
	pos /= 18.;
	pos.xz *= 0.5;
	vec3 p = floor(pos);
	vec3 f = fract(pos);
	vec2 uv =  p.xz + f.xz + p.y * vec2(0.0,193.0);
	vec2 coord =  uv / 512.0;
	
	//The y channel has an offset to avoid using two textures fetches
	vec2 xy = texture2DLod(noisetex, coord,0.0).yx;

	return mix(xy.r,xy.g, f.y);
}


float shaderlod_light_GetAltostratusDensity(vec3 pos){

	float large = 1.0 - texture2DLod(noisetex, (pos.xz + cloud_movement)/100000.,0.0).b;
	large = max(large + LAYER2_COVERAGE - 0.7, 0.0);
	
	float medium = 1.0 - texture2DLod(noisetex, (pos.xz - cloud_movement)/7500. + vec2(-large,1.0-large)/5.0,0.0).b;

	float shape = max(large - medium*0.4 * clamp(1.5-large,0.0,1.0),0.0);

	return shape*shape;
}

float shaderlod_light_cloudCov(int layer, in vec3 pos, vec3 samplePos, float minHeight, float maxHeight){
	float FinalCloudCoverage = 0.0;
	float coverage = 0.0;
	float Topshape = 0.0;
	float Baseshape = 0.0;

	float LAYER0_minHEIGHT_FOG = CloudLayer0_height; 
	float LAYER0_maxHEIGHT_FOG = 100 + LAYER0_minHEIGHT_FOG;
	LAYER0_minHEIGHT_FOG = LAYER0_minHEIGHT;
	LAYER0_maxHEIGHT_FOG = LAYER0_maxHEIGHT;

	float LAYER1_minHEIGHT_FOG = max(CloudLayer1_height, LAYER0_maxHEIGHT); 
	float LAYER1_maxHEIGHT_FOG = 100 + LAYER1_minHEIGHT_FOG;
	LAYER1_minHEIGHT_FOG = LAYER1_minHEIGHT;
	LAYER1_maxHEIGHT_FOG = LAYER1_maxHEIGHT;


	vec2 SampleCoords0 = vec2(0.0); vec2 SampleCoords1 = vec2(0.0);

	float CloudSmall = 0.0;
	if(layer == 0){
		SampleCoords0 = (samplePos.xz + cloud_movement) / 5000 ;
		SampleCoords1 = (samplePos.xz - cloud_movement) / 500 ;
		CloudSmall = texture2DLod(noisetex, SampleCoords1 ,0.0).r;
	}

	if(layer == 1){
		SampleCoords0 = -( (samplePos.zx + cloud_movement*2) / 10000);
		SampleCoords1 = -( (samplePos.zx - cloud_movement*2) / 2500);
		CloudSmall = texture2DLod(noisetex, SampleCoords1 ,0.0).b;
	}

	if(layer == -1){
		float otherlayer = max(pos.y - (LAYER0_minHEIGHT_FOG+99.5), 0.0) > 0 ? 0.0 : 1.0;
		if(otherlayer > 0.0){
			SampleCoords0 = (samplePos.xz + cloud_movement) / 5000 ;
			SampleCoords1 = (samplePos.xz - cloud_movement) / 500 ;
			CloudSmall = texture2DLod(noisetex, SampleCoords1 ,0.0).r;
		}else{
			SampleCoords0 = -( (samplePos.zx + cloud_movement*2) / 10000);
			SampleCoords1 = -( (samplePos.zx - cloud_movement*2) / 2500);
			CloudSmall = texture2DLod(noisetex, SampleCoords1 ,0.0).b;
		}
	}

	float CloudLarge = texture2DLod(noisetex, SampleCoords0,0.0).b;

	if(layer == 0){
		coverage = abs(CloudLarge*2.0 - 1.2)*0.5 - (1.0-CloudSmall);

		float layer0 = min(min(coverage + LAYER0_COVERAGE, clamp(LAYER0_maxHEIGHT_FOG - pos.y,0,1)), 1.0 - clamp(LAYER0_minHEIGHT_FOG - pos.y,0,1));

		Topshape = max(pos.y - (LAYER0_maxHEIGHT_FOG - 75),0.0) / 200.0;
		Topshape += max(pos.y - (LAYER0_maxHEIGHT_FOG - 10),0.0) / 15.0;
		Baseshape = max(LAYER0_minHEIGHT_FOG + 12.5 - pos.y, 0.0) / 50.0;

		FinalCloudCoverage = max(layer0 - Topshape - Baseshape * (1.0-rainStrength),0.0);
	}

	if(layer == 1){
		
		coverage = abs(CloudLarge-0.8) - CloudSmall;

		float layer1 = min(min(coverage + LAYER1_COVERAGE - 0.5,clamp(LAYER1_maxHEIGHT_FOG - pos.y,0,1)), 1.0 - clamp(LAYER1_minHEIGHT_FOG - pos.y,0,1));

		Topshape = max(pos.y - (LAYER1_maxHEIGHT_FOG - 75),0.0) / 200.0;
		Topshape += max(pos.y - (LAYER1_maxHEIGHT_FOG - 10), 0.0) / 15.0;
		Baseshape = max(LAYER1_minHEIGHT_FOG + 15.5 - pos.y, 0.0) / 50.0;

		FinalCloudCoverage = max(layer1 - Topshape*Topshape - Baseshape * (1.0-rainStrength), 0.0);
	}


	if(layer == -1){
	
		#ifdef CloudLayer0 
			float layer0_coverage =  abs(CloudLarge*2.0 - 1.2)*0.5 - (1.0-CloudSmall);
			float layer0 = min(min(layer0_coverage + LAYER0_COVERAGE, clamp(LAYER0_maxHEIGHT_FOG - pos.y,0,1)), 1.0 - clamp(LAYER0_minHEIGHT_FOG - pos.y,0,1));

			Topshape = max(pos.y - (LAYER0_maxHEIGHT_FOG - 75),0.0) / 200.0;
			Topshape += max(pos.y - (LAYER0_maxHEIGHT_FOG - 10),0.0) / 15.0;
			Baseshape = max(LAYER0_minHEIGHT_FOG + 12.5 - pos.y, 0.0) / 50.0;

			FinalCloudCoverage = max(layer0 - Topshape - Baseshape * (1.0-rainStrength),0.0);
		#endif
		
		#ifdef CloudLayer1
			float layer1_coverage = abs(CloudLarge-0.8) - CloudSmall;
			float layer1 = min(min(layer1_coverage + LAYER1_COVERAGE - 0.5,clamp(LAYER1_maxHEIGHT_FOG - pos.y,0,1)), 1.0 - clamp(LAYER1_minHEIGHT_FOG - pos.y,0,1));

			Topshape = max(pos.y - (LAYER1_maxHEIGHT_FOG - 75), 0.0) / 200;
			Topshape += max(pos.y - (LAYER1_maxHEIGHT_FOG - 10 ), 0.0) / 50;
			Baseshape = max(LAYER1_minHEIGHT_FOG + 12.5 - pos.y, 0.0) / 50.0;

			FinalCloudCoverage += max(layer1 - Topshape*Topshape - Baseshape * (1.0-rainStrength), 0.0);
		#endif
	}

	return FinalCloudCoverage;
}

//Erode cloud with 3d Perlin-worley noise, actual cloud value
float shaderlod_light_cloudVol(int layer, in vec3 pos, in vec3 samplePos, in float cov, in int LoD, float minHeight, float maxHeight){
	
	// float curvature = 1-exp(-25*pow(clamp(1.0 - length(pos - cameraPosition)/(32*80),0.0,1.0),2));
	// curvature = clamp(1.0 - length(pos - cameraPosition)/(32*128),0.0,1.0);

	float otherlayer = max(pos.y - (CloudLayer0_height+99.5), 0.0) > 0 ? 0.0 : 1.0;
	float upperPlane = otherlayer;

	float noise = 0.0 ;
	float totalWeights = 0.0;
	float pw =  log(fbmPower1);
	float pw2 = log(fbmPower2);

	samplePos.xz -= cloud_movement/4;

	samplePos.xz += pow( max(pos.y - (minHeight+20), 0.0) / 20.0,1.50) ;

	noise += (1.0-shaderlod_light_densityAtPos(samplePos * mix(100.0,200.0,upperPlane)) ) * sqrt(1.0-cov);

	if (LoD > 0){
		noise += abs( shaderlod_light_densityAtPos(samplePos * mix(450.0,600.0,upperPlane) ) - (1.0-clamp(((maxHeight - pos.y) / 100.0),0.0,1.0))) * 0.75 * (1.0-cov);
	}

	noise = noise*noise;
	float cloud = max(cov - noise*noise*fbmAmount,0.0);

	return cloud;
}

float shaderlod_light_GetCumulusDensity(int layer, in vec3 pos, in int LoD, float minHeight, float maxHeight){

	vec3 samplePos =  pos*vec3(1.0,1./48.,1.0)/4;
	
	float coverageSP = shaderlod_light_cloudCov(layer, pos,samplePos, minHeight, maxHeight);

	// return coverageSP;
	if (coverageSP > 0.001) {
		if (LoD < 0) return max(coverageSP - 0.27*fbmAmount,0.0);
		return shaderlod_light_cloudVol(layer, pos,samplePos,coverageSP,LoD	,minHeight, maxHeight) ;
	} else return 0.0;
}



float shaderlod_bliss_cell_pixels(vec3 position, vec3 stepVector) {
    if (!(all(lessThan(abs(position),vec3(1e6))) && all(lessThan(abs(stepVector),vec3(1e6))) &&
        all(lessThan(abs(cameraPosition),vec3(1e6))))) return 1e6;
    vec4 a=gbufferProjection*gbufferModelView*vec4(position-cameraPosition,1.0);
    vec4 b=gbufferProjection*gbufferModelView*vec4(position+stepVector-cameraPosition,1.0);
    if (!(a.w>1e-4 && b.w>1e-4)) return 1e6;
    return length((a.xy/a.w-b.xy/b.w)*0.5/texelSize);
}
bool shaderlod_bliss_light_reuse(float distanceBlocks,float cellPixels,float rank,
    float density,float previousDensity,float altitude,float previousAltitude) {
    // Same fixed experimental gates as Photon; no claim of shadow/motion quality.
    if (!(distanceBlocks>512.0 && distanceBlocks<1e6 && cellPixels>=0.0 && cellPixels<48.0 &&
        rank>=0.0 && rank<1.0 && density>=0.08 && previousDensity>=0.08 &&
        altitude>=0.1 && altitude<=0.9 && previousAltitude>=0.1 && previousAltitude<=0.9 &&
        abs(altitude-previousAltitude)<=0.05 &&
        abs(density-previousDensity)<=0.1*max(density,previousDensity))) return false;
    return rank<clamp((distanceBlocks-512.0)/1024.0,0.0,1.0)*clamp((48.0-cellPixels)/16.0,0.0,1.0);
}
#endif
#endif

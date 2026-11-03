// Exact current Bliss AO setup/kernel ancestry; authors/credits remain in pack.
vec4 shaderlodBlissAoFragCoord;
#define gl_FragCoord shaderlodBlissAoFragCoord
#include "/lib/settings.glsl"
#include "/lib/shaderlod/adapter.glsl"

#ifndef DH_AMBIENT_OCCLUSION
	#undef DISTANT_HORIZONS
#endif


flat varying vec3 WsunVec;
flat varying vec2 TAA_Offset;

#include "/lib/res_params.glsl"

uniform sampler2D depthtex0;
uniform sampler2D depthtex1;

#ifdef DISTANT_HORIZONS
	uniform sampler2D dhDepthTex;
	uniform sampler2D dhDepthTex1;
#endif

uniform sampler2D colortex1;
uniform sampler2D colortex3; // Noise
uniform sampler2D colortex6; // Noise
uniform sampler2D colortex8; // Noise
uniform sampler2D colortex14; // Noise
uniform sampler2D colortex12; // Noise
uniform sampler2D colortex15; // Noise

uniform sampler2D shadow;

#ifdef TRANSLUCENT_COLORED_SHADOWS
	uniform sampler2D shadowcolor0;
	uniform sampler2D shadowtex0;
	uniform sampler2D shadowtex1;
#endif


uniform sampler2D noisetex;
uniform vec3 sunVec;
uniform vec2 texelSize;
uniform float frameTimeCounter;
uniform float rainStrength;
uniform int frameCounter;


uniform mat4 gbufferModelViewInverse;
uniform mat4 gbufferModelView;


uniform vec3 cameraPosition;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;

uniform vec3 previousCameraPosition;
uniform mat4 gbufferPreviousProjection;
uniform mat4 gbufferPreviousModelView;

uniform mat4 shadowModelView;
uniform mat4 shadowProjection;
uniform float viewWidth;
uniform float aspectRatio;
uniform float viewHeight;

// uniform float far;
uniform float near;
uniform float dhFarPlane;
uniform float dhNearPlane;

#define ffstep(x,y) clamp((y - x) * 1e35,0.0,1.0)
#define diagonal3(m) vec3((m)[0].x, (m)[1].y, m[2].z)
#define  projMAD(m, v) (diagonal3(m) * (v) + (m)[3].xyz)
vec3 toScreenSpace(vec3 p) {
	vec4 iProjDiag = vec4(gbufferProjectionInverse[0].x, gbufferProjectionInverse[1].y, gbufferProjectionInverse[2].zw);
    vec3 p3 = p * 2. - 1.;
    vec4 fragposition = iProjDiag * p3.xyzz + gbufferProjectionInverse[3];
    return fragposition.xyz / fragposition.w;
}


vec3 worldToView(vec3 worldPos) {
    vec4 pos = vec4(worldPos, 0.0);
    pos = gbufferModelView * pos;
    return pos.xyz;
}

vec2 tapLocation(int sampleNumber,int nb, float nbRot,float jitter,float distort)
{
    float alpha = (sampleNumber+jitter)/nb;
    float angle = jitter*6.28+alpha * nbRot * 6.28;
    float sin_v, cos_v;

	sin_v = sin(angle);
	cos_v = cos(angle);

    return vec2(cos_v, sin_v)*alpha;
}
vec2 tapLocation2(int sampleNumber, int nb, float jitter){
    float alpha = (sampleNumber+jitter)/nb;
    float angle = jitter*6.28 + alpha * 84.0 * 6.28;

    float sin_v, cos_v;

	sin_v = sin(angle);
	cos_v = cos(angle);

    return vec2(cos_v, sin_v)*sqrt(alpha);
}

vec3 decode (vec2 encn){
    vec3 n = vec3(0.0);
    encn = encn * 2.0 - 1.0;
    n.xy = abs(encn);
    n.z = 1.0 - n.x - n.y;
    n.xy = n.z <= 0.0 ? (1.0 - n.yx) * sign(encn) : encn;
    return clamp(normalize(n.xyz),-1.0,1.0);
}
vec2 decodeVec2(float a){
    const vec2 constant1 = 65535. / vec2( 256., 65536.);
    const float constant2 = 256. / 255.;
    return fract( a * constant1 ) * constant2 ;
}



float interleaved_gradientNoise_temporal(){
	return fract(52.9829189*fract(0.06711056*gl_FragCoord.x + 0.00583715*gl_FragCoord.y)+frameTimeCounter*51.9521);
}
float interleaved_gradientNoise(){
	vec2 coord = gl_FragCoord.xy;
	float noise = fract(52.9829189*fract(0.06711056*coord.x + 0.00583715*coord.y));
	return noise;
}

float R2_dither(){
  	#ifdef TAA
		vec2 coord = gl_FragCoord.xy + (frameCounter%40000) * 2.0;
	#else
		vec2 coord = gl_FragCoord.xy;
	#endif
	vec2 alpha = vec2(0.75487765, 0.56984026);
	return fract(alpha.x * coord.x + alpha.y * coord.y ) ;
}
float blueNoise(){
  return fract(texelFetch2D(noisetex, ivec2(gl_FragCoord.xy)%512, 0).a + 1.0/1.6180339887 * frameCounter);
}
vec4 blueNoise(vec2 coord){
  return texelFetch2D(colortex6, ivec2(coord )%512  , 0);
}
vec2 R2_samples(int n){
	vec2 alpha = vec2(0.75487765, 0.56984026);
	return fract(alpha * n);
}








vec3 viewToWorld(vec3 viewPos) {
    vec4 pos;
    pos.xyz = viewPos;
    pos.w = 0.0;
    pos = gbufferModelViewInverse * pos;
    return pos.xyz;
}

#include "/lib/Shadow_Params.glsl"


const float PI = 3.141592653589793238462643383279502884197169;
vec2 SpiralSample(
	int samples, int totalSamples, float rotation, float Xi
){
	Xi = max(Xi,0.0015);
	
    float alpha = float(samples + Xi) * (1.0 / float(totalSamples));
	
    float theta = (2.0 *3.14159265359) * alpha * rotation;

    float r = sqrt(Xi);
	float x = r * sin(theta);
	float y = r * cos(theta);

    return vec2(x, y);
}
vec2 CleanSample(
	int samples, float totalSamples, float noise
){

	// this will be used to make 1 full rotation of the spiral. the mulitplication is so it does nearly a single rotation, instead of going past where it started
	float variance = noise * 0.897;

	// for every sample input, it will have variance applied to it.
	float variedSamples = float(samples) + variance;
	
	// for every sample, the sample position must change its distance from the origin.
	// otherwise, you will just have a circle.
    float spiralShape = variedSamples / (totalSamples + variance);

	float shape = 2.26;
    float theta = variedSamples * (PI * shape);

	float x =  cos(theta) * spiralShape;
	float y =  sin(theta) * spiralShape;

    return vec2(x, y);
}



#include "/lib/DistantHorizons_projections.glsl"

float DH_ld(float dist) {
    return (2.0 * dhNearPlane) / (dhFarPlane + dhNearPlane - dist * (dhFarPlane - dhNearPlane));
}
float DH_inv_ld (float lindepth){
	return -((2.0*dhNearPlane/lindepth)-dhFarPlane-dhNearPlane)/(dhFarPlane-dhNearPlane);
}

float linearizeDepthFast(const in float depth, const in float near, const in float far) {
    return (near * far) / (depth * (near - far) + far);
}

void convertHandDepth(inout float depth) {
    float ndcDepth = depth * 2.0 - 1.0;
    ndcDepth /= MC_HAND_DEPTH;
    depth = ndcDepth * 0.5 + 0.5;
}

float convertHandDepth_2(in float depth, bool hand) {
    if(!hand) return depth;
	
	float ndcDepth = depth * 2.0 - 1.0;
    ndcDepth /= MC_HAND_DEPTH;
    return ndcDepth * 0.5 + 0.5;
}
#include "/lib/shaderlod/bliss_ao_literal_native.glsl"
vec2 SSAO(
	vec3 viewPos, vec3 normal, bool hand, bool leaves, float noise
){
	// if(hand) return vec2(1.0,0.0);
	int samples = 7;
	#if SHADERLOD_EFFECT_ADAPTER == 1 && defined OVERWORLD_SHADER && !defined DISTANT_HORIZONS && !defined VOXY
	if (!hand && !leaves) samples = shaderlod_bliss_distance_budget(shaderlodOptAoBudget, length(viewPos), samples, 4);
	#endif
    // The selected seven-tap kernel is exactly native when depth reuse is inactive.
    // Keep its literal bound, positions and accepted-neighbor normalization.
    #if SHADERLOD_EFFECT_ADAPTER == 1 && defined OVERWORLD_SHADER && !defined DISTANT_HORIZONS && !defined VOXY
    if (samples == 7 && !shaderlod_optimization_enabled(shaderlodOptAoDepthReuse))
        return shaderlodBlissNativeSSAO(viewPos, normal, hand, leaves, noise);
    #else
    return shaderlodBlissNativeSSAO(viewPos, normal, hand, leaves, noise);
    #endif
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
    #if SHADERLOD_EFFECT_ADAPTER == 1 && defined OVERWORLD_SHADER && !defined DISTANT_HORIZONS && !defined VOXY
    bool shaderlodAoCacheEnabled = shaderlod_optimization_enabled(shaderlodOptAoDepthReuse);
    bool shaderlodAoCacheValid = false;
    ivec2 shaderlodAoCachedOffset = ivec2(0);
    float shaderlodAoCachedDepth = 0.0;
    #endif

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

            float shaderlodAoDepth;
            #if SHADERLOD_EFFECT_ADAPTER == 1 && defined OVERWORLD_SHADER && !defined DISTANT_HORIZONS && !defined VOXY
            if (shaderlodAoCacheEnabled && shaderlodAoCacheValid && all(equal(offset,shaderlodAoCachedOffset))) {
                shaderlodAoDepth = shaderlodAoCachedDepth;
            } else {
                shaderlodAoDepth = texelFetch2D(depthtex1, offset,0).x;
                if (shaderlodAoCacheEnabled) {
                    shaderlodAoCacheValid = true;
                    shaderlodAoCachedOffset = offset;
                    shaderlodAoCachedDepth = shaderlodAoDepth;
                }
            }
            #else
            shaderlodAoDepth = texelFetch2D(depthtex1, offset,0).x;
            #endif
			vec3 t0 = toScreenSpace_DH((offset*texelSize+acc+0.5*texelSize) * (1.0/RENDER_SCALE), convertHandDepth_2(shaderlodAoDepth, hand), dhdepth);
		
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

vec4 encode (vec3 n, vec2 lightmaps){
	n.xy = n.xy / dot(abs(n), vec3(1.0));
	n.xy = n.z <= 0.0 ? (1.0 - abs(n.yx)) * sign(n.xy) : n.xy;
    vec2 encn = clamp(n.xy * 0.5 + 0.5,-1.0,1.0);
	
    return vec4(encn,vec2(lightmaps.x,lightmaps.y));
}

//encoding by jodie
float encodeVec2(vec2 a){
    const vec2 constant1 = vec2( 1., 256.) / 65535.;
    vec2 temp = floor( a * 255. );
	return temp.x*constant1.x+temp.y*constant1.y;
}
float encodeVec2(float x,float y){
    return encodeVec2(vec2(x,y));
}

// #include "/lib/indirect_lighting_effects.glsl"

vec3 toClipSpace3Prev(vec3 viewSpacePosition) {
    return projMAD(gbufferPreviousProjection, viewSpacePosition) / -viewSpacePosition.z * 0.5 + 0.5;
}

vec3 closestToCamera5taps(vec2 texcoord, sampler2D depth, bool hand)
{
	vec2 du = vec2(texelSize.x*2., 0.0);
	vec2 dv = vec2(0.0, texelSize.y*2.);

	vec3 dtl = vec3(texcoord,0.) + vec3(-texelSize, 				texture2D(depth, texcoord - dv - du).x);
	vec3 dtr = vec3(texcoord,0.) + vec3( texelSize.x, -texelSize.y, texture2D(depth, texcoord - dv + du).x);
	vec3 dmc = vec3(texcoord,0.) + vec3( 0.0, 0.0, 					texture2D(depth, texcoord).x);
	vec3 dbl = vec3(texcoord,0.) + vec3(-texelSize.x, texelSize.y, 	texture2D(depth, texcoord + dv - du).x);
	vec3 dbr = vec3(texcoord,0.) + vec3( texelSize.x, texelSize.y, 	texture2D(depth, texcoord + dv + du).x);
	
	if(hand){
		convertHandDepth(dtl.z);
		convertHandDepth(dtr.z);
		convertHandDepth(dmc.z);
		convertHandDepth(dbl.z);
		convertHandDepth(dbr.z);
	}

	vec3 dmin = dmc;
	dmin = dmin.z > dtr.z ? dtr : dmin;
	dmin = dmin.z > dtl.z ? dtl : dmin;
	dmin = dmin.z > dbl.z ? dbl : dmin;
	dmin = dmin.z > dbr.z ? dbr : dmin;
	
	#ifdef TAA_UPSCALING
		dmin.xy = dmin.xy/RENDER_SCALE;
#elif SHADERLOD_EFFECT_ADAPTER == 1 && defined TAA && defined OVERWORLD_SHADER && RESOURCEPACK_SKY == 0 && DOF_QUALITY != 5 && !defined SCREENSHOT_MODE && !defined VOXY && !defined DISTANT_HORIZONS
if (shaderlod_bliss_pixel_selected()) {
		dmin.xy = dmin.xy/RENDER_SCALE;
}
	#endif

	return dmin;
}

float ld(float dist) {
    return (2.0 * near) / (far + near - dist * (far - near));
}
float sampleDepth(sampler2D depthTex, vec2 texcoord, bool hand){
	// return texture2D(depthTex, texcoord).r;
	return convertHandDepth_2(texture2D(depthTex, texcoord).r, hand);
}




#undef gl_FragCoord

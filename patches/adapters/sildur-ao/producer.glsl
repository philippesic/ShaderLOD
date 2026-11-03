/*
const int colortex10Format = RGBA32F;
const bool colortex10Clear = true;
const vec4 colortex10ClearColor = vec4(1.0,0.0,0.0,-1.0);
*/
// Native Sildur notices and SSDO attribution remain in the source recipe/lighting member.
/* RENDERTARGETS:10 */
#define deferred0
#define AA_settings
#include "/shaders.settings"
#include "/lib/shaderlod/adapter.glsl"
uniform sampler2D depthtex0,colortex2,colortex1;
uniform mat4 gbufferProjection,gbufferProjectionInverse;
uniform float viewWidth,viewHeight,frameTimeCounter;
uniform int isEyeInWater;
vec2 texelSize = vec2(1.0/viewWidth,1.0/viewHeight);
vec2 shaderlodSildurAoCoord;
#ifdef TAA
uniform int framemod8;
const vec2[8] offsets = vec2[8](vec2(1./8.,-3./8.),
								vec2(-1.,3.)/8.,
								vec2(5.0,1.)/8.,
								vec2(-3,-5.)/8.,
								vec2(-5.,5.)/8.,
								vec2(-7.,-1.)/8.,
								vec2(3,7.)/8.,
								vec2(7.,-7.)/8.);
#endif								


vec3 utilScreenSpace(vec3 pos) {
	vec4 iProjDiag = vec4(gbufferProjectionInverse[0].x, gbufferProjectionInverse[1].y, gbufferProjectionInverse[2].zw);
	vec3 p3 = pos * 2.0 - 1.0;
    vec4 fragposition = iProjDiag * p3.xyzz + gbufferProjectionInverse[3];
    return fragposition.xyz / fragposition.w;
}
vec3 utilDecode (vec2 enc){
    vec2 fenc = enc*4.0-2.0;
    float f = dot(fenc,fenc);
    float g = sqrt(1.0-f/4.0);
    vec3 n;
    n.xy = fenc*g;
    n.z = 1.0-f/2.0;
    return n;
}
#include "/lib/shaderlod/sildur_ao_graph.glsl"
#ifdef SSDO
uniform float aspectRatio;
const vec2 check_offsets[25] = vec2[25](vec2(-0.4894566f,-0.3586783f),
									vec2(-0.1717194f,0.6272162f),
									vec2(-0.4709477f,-0.01774091f),
									vec2(-0.9910634f,0.03831699f),
									vec2(-0.2101292f,0.2034733f),
									vec2(-0.7889516f,-0.5671548f),
									vec2(-0.1037751f,-0.1583221f),
									vec2(-0.5728408f,0.3416965f),
									vec2(-0.1863332f,0.5697952f),
									vec2(0.3561834f,0.007138769f),
									vec2(0.2868255f,-0.5463203f),
									vec2(-0.4640967f,-0.8804076f),
									vec2(0.1969438f,0.6236954f),
									vec2(0.6999109f,0.6357007f),
									vec2(-0.3462536f,0.8966291f),
									vec2(0.172607f,0.2832828f),
									vec2(0.4149241f,0.8816f),
									vec2(0.136898f,-0.9716249f),
									vec2(-0.6272043f,0.6721309f),
									vec2(-0.8974028f,0.4271871f),
									vec2(0.5551881f,0.324069f),
									vec2(0.9487136f,0.2605085f),
									vec2(0.7140148f,-0.312601f),
									vec2(0.0440252f,0.9363738f),
									vec2(0.620311f,-0.6673451f)
									);

//modified version of Yuriy O'Donnell's SSDO (License MIT -> https://github.com/kayru/dssdo)
#if SHADERLOD_EFFECT_ADAPTER == 1 && !defined DISTANT_HORIZONS && !defined VOXY
// One-entry pure depth-component reconstruction cache. Every native depth query executes first.
vec3 shaderlod_sildur_ao_position(vec3 pos,bool selected,vec4 diag,vec4 translation,
    inout bool valid,inout float previousDepth,inout float previousW,inout float previousZ) {
    if(!selected) return utilScreenSpace(pos);
    bool inputValid=pos.z>=0.0 && pos.z<=1.0 && all(lessThan(abs(pos.xy),vec2(1e8)));
    if(inputValid && valid && pos.z==previousDepth) {
        // Different AO coordinates retain their own literal native XY reconstruction/division.
        vec2 p2=pos.xy*2.0-1.0;
        vec2 numerator=diag.xy*p2+translation.xy;
        return vec3(numerator/previousW,previousZ);
    }
    // Exact native utilScreenSpace body with the same immutable projection inputs.
    vec3 p3=pos*2.0-1.0;
    vec4 fragposition=diag*p3.xyzz+translation;
    vec3 result=fragposition.xyz/fragposition.w;
    previousDepth=pos.z;previousW=fragposition.w;previousZ=result.z;
    valid=inputValid && abs(previousW)>=1e-8 && abs(previousW)<1e8 && all(lessThan(abs(result),vec3(1e8)));
    return result;
}
#endif
float shaderlodSildurNativeSSDO(float noise, vec3 fragpos, vec3 normal){
	float finalAO = 0.0;
	float radius = 0.05 / (fragpos.z);
	const float attenuation_angle_threshold = 0.1;
	const int num_samples = 16;	
	const float ao_weight = 1.0;

	for( int i=0; i<num_samples; ++i ){
	    vec2 texOffset = pow(length(check_offsets[i].xy),0.5)*radius*vec2(1.0,aspectRatio)*normalize(check_offsets[i].xy);
		vec2 newTC = shaderlodSildurAoCoord+texOffset*noise;
	#ifdef TAA
		vec3 t0 = utilScreenSpace(vec3(newTC-offsets[framemod8]*texelSize*0.5, texture2D(depthtex0, newTC).x));
	#else
		vec3 t0 = utilScreenSpace(vec3(newTC, texture2D(depthtex0, newTC).x));
	#endif	
		vec3 center_to_sample = t0.xyz - fragpos.xyz;

		float dist = length(center_to_sample);

		vec3 center_to_sample_normalized = center_to_sample / dist;
		float attenuation = 1.0-clamp(dist/6.0,0.0,1.0);
		float dp = dot(normal, center_to_sample_normalized);

		attenuation = sqrt(max(dp,0.0))*attenuation*attenuation * step(attenuation_angle_threshold, dp);
		finalAO += attenuation * (ao_weight / num_samples);
	}
	return finalAO;
}
float shaderlodSildurDynamicSSDO(float noise, vec3 fragpos, vec3 normal){
	float finalAO = 0.0;
	float radius = 0.05 / (fragpos.z);
	const float attenuation_angle_threshold = 0.1;
	#if SHADERLOD_EFFECT_ADAPTER == 1 && !defined DISTANT_HORIZONS && !defined VOXY
	int num_samples = shaderlod_sildur_distance_count(shaderlodOptAoBudget, 16, 12, length(fragpos));
	#else
	const int num_samples = 16;
	#endif
	const float ao_weight = 1.0;
    #if SHADERLOD_EFFECT_ADAPTER == 1 && !defined DISTANT_HORIZONS && !defined VOXY
    vec4 shaderlodAoDiag=vec4(gbufferProjectionInverse[0].x,gbufferProjectionInverse[1].y,gbufferProjectionInverse[2].zw);
    vec4 shaderlodAoTranslation=gbufferProjectionInverse[3];
    bool shaderlodAoSelected=shaderlod_optimization_enabled(shaderlodOptAoDepthReuse) &&
        all(lessThan(abs(shaderlodAoDiag),vec4(1e8))) && all(lessThan(abs(shaderlodAoTranslation),vec4(1e8)));
    bool shaderlodAoPreviousValid=false;
    float shaderlodAoPreviousDepth=0.0,shaderlodAoPreviousW=0.0,shaderlodAoPreviousZ=0.0;
    #endif

	for( int i=0; i<num_samples; ++i ){
	    vec2 texOffset = pow(length(check_offsets[i].xy),0.5)*radius*vec2(1.0,aspectRatio)*normalize(check_offsets[i].xy);
		vec2 newTC = shaderlodSildurAoCoord+texOffset*noise;
        #if SHADERLOD_EFFECT_ADAPTER == 1 && !defined DISTANT_HORIZONS && !defined VOXY && (defined GL_ARB_shader_texture_lod || (defined IS_IRIS && defined MC_GLSL_VERSION && MC_GLSL_VERSION >= 330))
        float shaderlodAoDepth = shaderlod_optimization_enabled(shaderlodOptAoSkyElision)
            ? texture2DLod(depthtex0,newTC,0.0).x : texture2D(depthtex0,newTC).x;
        #endif
	#ifdef TAA
        #if SHADERLOD_EFFECT_ADAPTER == 1 && !defined DISTANT_HORIZONS && !defined VOXY && (defined GL_ARB_shader_texture_lod || (defined IS_IRIS && defined MC_GLSL_VERSION && MC_GLSL_VERSION >= 330))
#if SHADERLOD_EFFECT_ADAPTER == 1 && !defined DISTANT_HORIZONS && !defined VOXY
vec3 t0 = shaderlod_sildur_ao_position(vec3(newTC-offsets[framemod8]*texelSize*0.5, shaderlodAoDepth),shaderlodAoSelected,shaderlodAoDiag,shaderlodAoTranslation,shaderlodAoPreviousValid,shaderlodAoPreviousDepth,shaderlodAoPreviousW,shaderlodAoPreviousZ);
#else
		vec3 t0 = utilScreenSpace(vec3(newTC-offsets[framemod8]*texelSize*0.5, shaderlodAoDepth));
#endif
        #else
#if SHADERLOD_EFFECT_ADAPTER == 1 && !defined DISTANT_HORIZONS && !defined VOXY
vec3 t0 = shaderlod_sildur_ao_position(vec3(newTC-offsets[framemod8]*texelSize*0.5, texture2D(depthtex0, newTC).x),shaderlodAoSelected,shaderlodAoDiag,shaderlodAoTranslation,shaderlodAoPreviousValid,shaderlodAoPreviousDepth,shaderlodAoPreviousW,shaderlodAoPreviousZ);
#else
		vec3 t0 = utilScreenSpace(vec3(newTC-offsets[framemod8]*texelSize*0.5, texture2D(depthtex0, newTC).x));
#endif
        #endif
	#else
        #if SHADERLOD_EFFECT_ADAPTER == 1 && !defined DISTANT_HORIZONS && !defined VOXY && (defined GL_ARB_shader_texture_lod || (defined IS_IRIS && defined MC_GLSL_VERSION && MC_GLSL_VERSION >= 330))
#if SHADERLOD_EFFECT_ADAPTER == 1 && !defined DISTANT_HORIZONS && !defined VOXY
vec3 t0 = shaderlod_sildur_ao_position(vec3(newTC, shaderlodAoDepth),shaderlodAoSelected,shaderlodAoDiag,shaderlodAoTranslation,shaderlodAoPreviousValid,shaderlodAoPreviousDepth,shaderlodAoPreviousW,shaderlodAoPreviousZ);
#else
		vec3 t0 = utilScreenSpace(vec3(newTC, shaderlodAoDepth));
#endif
        #else
#if SHADERLOD_EFFECT_ADAPTER == 1 && !defined DISTANT_HORIZONS && !defined VOXY
vec3 t0 = shaderlod_sildur_ao_position(vec3(newTC, texture2D(depthtex0, newTC).x),shaderlodAoSelected,shaderlodAoDiag,shaderlodAoTranslation,shaderlodAoPreviousValid,shaderlodAoPreviousDepth,shaderlodAoPreviousW,shaderlodAoPreviousZ);
#else
		vec3 t0 = utilScreenSpace(vec3(newTC, texture2D(depthtex0, newTC).x));
#endif
        #endif
	#endif	
		vec3 center_to_sample = t0.xyz - fragpos.xyz;

		float dist = length(center_to_sample);

		vec3 center_to_sample_normalized = center_to_sample / dist;
		float attenuation = 1.0-clamp(dist/6.0,0.0,1.0);
		float dp = dot(normal, center_to_sample_normalized);

		attenuation = sqrt(max(dp,0.0))*attenuation*attenuation * step(attenuation_angle_threshold, dp);
		finalAO += attenuation * (ao_weight / num_samples);
	}
	return finalAO;
}
float funSSDO(float noise, vec3 fragpos, vec3 normal){
    #if SHADERLOD_EFFECT_ADAPTER == 1 && !defined DISTANT_HORIZONS && !defined VOXY
    if (shaderlod_optimization_enabled(shaderlodOptAoBudget) ||
        shaderlod_optimization_enabled(shaderlodOptAoDepthReuse) ||
        shaderlod_optimization_enabled(shaderlodOptAoSkyElision))
        return shaderlodSildurDynamicSSDO(noise,fragpos,normal);
    #endif
    return shaderlodSildurNativeSSDO(noise,fragpos,normal);
}
#endif

void main() {
 vec4 field=vec4(1.0,0.0,0.0,-1.0);
 vec2 fullSize=vec2(viewWidth,viewHeight);
 #if defined SSDO && defined IS_IRIS && defined MC_GLSL_VERSION && MC_GLSL_VERSION >= 330 && !defined DISTANT_HORIZONS && !defined VOXY
 if (shaderlodSildurAoGraphActive(fullSize) && isEyeInWater < 0.9) {
  vec2 pixel=floor(gl_FragCoord.xy)*2.0+vec2(1.5);
  shaderlodSildurAoCoord=pixel/fullSize;
  float depth0=texture2DLod(depthtex0,shaderlodSildurAoCoord,0.0).x;
  vec3 normal=utilDecode(texture2DLod(colortex2,shaderlodSildurAoCoord,0.0).xy);
  #ifdef TAA
  vec3 fragpos0=utilScreenSpace(vec3(shaderlodSildurAoCoord-offsets[framemod8]*texelSize*0.5,depth0));
  float noise=fract(0.75487765*pixel.x+0.56984026*pixel.y+frameTimeCounter*16.0);
  #else
  vec3 fragpos0=utilScreenSpace(vec3(shaderlodSildurAoCoord,depth0));
  float noise=fract(pixel.x*0.618033988749895+pixel.y*0.24412852441);
  #endif
  float blocks=-fragpos0.z;
  if (depth0>=0.0 && depth0<1.0 && blocks>shaderlodNear+max(0.05,shaderlodNear*0.001) && blocks<1e6) {
   float ao=pow(1.0-funSSDO(noise,fragpos0,normal),ao_strength);
   if (ao>=0.0 && ao<=1.0) {
    // Same representative UV/LOD as the former consumer material read.
    float material=texture2DLod(colortex1,shaderlodSildurAoCoord,0.0).b;
    field=vec4(ao,blocks,material,float(frameCounter));
   }
  }
 }
 #endif
 gl_FragData[0]=field;
}

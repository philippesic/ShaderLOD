#include "/lib/common.glsl"

#define COMPOSITE14
#if SHADERLOD_SOLAS_PIXEL_GRAPH == 1 && (!defined OVERWORLD || !defined TAA || defined FXAA || defined MOTION_BLUR || defined DOF || !defined BLOOM || !defined LENS_FLARE || defined DISTANT_HORIZONS || defined VOXY || !defined IS_IRIS || MC_VERSION != 12101 || IRIS_VERSION != 10812)
#error ShaderLOD Solas pixel graph unsupported native profile; restore private graph0.
#endif

#ifdef FSH

// VSH Data //
in vec2 texCoord;

// Uniforms //
uniform int isEyeInWater;

#if defined LENS_FLARE || defined BLOOM
#ifdef OVERWORLD
uniform float shadowFade;
#endif

uniform float timeBrightness, timeAngle, wetness;
uniform float blindFactor;

#if MC_VERSION >= 11900
uniform float darknessFactor;
#endif
#endif

uniform float aspectRatio;
uniform float viewWidth, viewHeight;

#ifdef DOF
#ifndef MANUAL_FOCUS
uniform float centerDepthSmooth;
#else
uniform float far, near;

float centerDepthSmooth = ((DOF_FOCUS - near) * far) / ((far - near) * DOF_FOCUS);
#endif
#endif

uniform ivec2 eyeBrightnessSmooth;

#if defined LENS_FLARE || defined BLOOM
uniform vec3 cameraPosition, sunPosition;
#endif

#ifdef NETHER
uniform vec3 fogColor;
#endif

uniform sampler2D colortex0;
uniform sampler2D colortex2;
uniform sampler2D depthtex1;

#ifdef BLOOM
uniform sampler2D colortex1;

uniform mat4 gbufferProjectionInverse;
#endif

#if defined DOF || defined LENS_FLARE
uniform mat4 gbufferProjection;
#endif

#if defined LENS_FLARE || defined BLOOM
uniform mat4 gbufferModelView;
#endif

// Pipeline Options //
const bool colortex0MipmapEnabled = true;
const bool colortex1MipmapEnabled = true;
const bool colortex2Clear = false;

// Global Variables //
#if defined LENS_FLARE || defined BLOOM
#if defined OVERWORLD
const vec2 sunRotationData = vec2(cos(sunPathRotation * 0.01745329251994), -sin(sunPathRotation * 0.01745329251994));
float fractTimeAngle = fract(timeAngle - 0.25);
float ang = (fractTimeAngle + (cos(fractTimeAngle * 3.14159265358979) * -0.5 + 0.5 - fractTimeAngle) / 3.0) * 6.28318530717959;
vec3 sunVec = normalize((gbufferModelView * vec4(vec3(-sin(ang), cos(ang) * sunRotationData) * 2000.0, 1.0)).xyz);
#elif defined END
const vec2 sunRotationData = vec2(cos(sunPathRotation * 0.01745329251994), -sin(sunPathRotation * 0.01745329251994));
vec3 sunVec = normalize((gbufferModelView * vec4(1.0, sunRotationData * 2000.0, 1.0)).xyz);
#else
vec3 sunVec = vec3(0.0);
#endif

vec3 upVec = normalize(gbufferModelView[1].xyz);

float eBS = eyeBrightnessSmooth.y / 240.0;
float caveFactor = mix(clamp((cameraPosition.y - 56.0) / 16.0, float(sign(isEyeInWater)), 1.0), 1.0, sqrt(eBS));
float sunVisibility = clamp((dot( sunVec, upVec) + 0.15) * 3.0, 0.0, 1.0);
float moonVisibility = clamp((dot(-sunVec, upVec) + 0.15) * 3.0, 0.0, 1.0);
#endif

// Includes //
#include "/lib/util/bayerDithering.glsl"
#include "/lib/post/tonemap.glsl"

#ifdef BLOOM
#include "/lib/post/getBloom.glsl"
#endif

#ifdef DOF
#include "/lib/util/ToView.glsl"
#include "/lib/post/computeDOF.glsl"
#endif

#ifdef LENS_FLARE
#include "/lib/color/lightColor.glsl"
#include "/lib/post/lensFlare.glsl"
#endif

// Main //

uniform sampler2D colortex12;
uniform int shaderlodSolasPixelReady, shaderlodSolasPixelHistoryReset;
uniform vec2 shaderlodSolasPixelSize;
#include "/lib/shaderlod/adapter.glsl"
#include "/lib/shaderlod/solas_pixel_bloom.glsl"
float shaderlodSolasPixelEye(float z) {
    float denominator=gbufferProjection[2].z+2.0*z-1.0;
    if (!(gbufferProjection[0].z==0.0 && gbufferProjection[1].z==0.0 && gbufferProjection[0].w==0.0 && gbufferProjection[1].w==0.0 && gbufferProjection[2].w==-1.0 && gbufferProjection[3].w==0.0 && abs(denominator)>1e-10 && abs(denominator)<1e10)) return -1.0;
    float blocks=gbufferProjection[3].z/denominator;
    return blocks>0.0 && blocks<1e6?blocks:-1.0;
}
bool shaderlodSolasPixelActive() {
    return shaderlodSolasPixelReady==1 && shaderlod_optimization_enabled(shaderlodOptPixelScale) &&
        all(equal(shaderlodSolasPixelSize,vec2(viewWidth,viewHeight))) &&
        shaderlodNear>0.0 && shaderlodNear<shaderlodFar && shaderlodFar<=4096.0;
}
vec3 shaderlodSolasPixelColor(vec2 sourceUv,float z1) {
    vec3 color=textureGrad(colortex0,sourceUv,vec2(1.0/viewWidth,0.0),vec2(0.0,1.0/viewHeight)).rgb;
	//Depth of Field & Tilt Shift
	#ifdef DOF
	color = getDepthOfField(color, sourceUv, z1);
	#endif

	//Bloom
	#ifdef BLOOM
	shaderlodSolasPixelBloom(color, sourceUv, z1);
	#endif

	//Tonemap & Film Grain
	color = Uncharted2Tonemap(color * TONEMAP_BRIGHTNESS) / Uncharted2Tonemap(vec3(TONEMAP_WHITE_THRESHOLD));
	color = pow(color, vec3(1.0 / 2.2));
	colorSaturation(color);

    return color;
}
vec3 shaderlodSolasPixelResolve(vec2 sourceUv,float z1) {
    float blocks=shaderlodSolasPixelEye(z1);
    if (!shaderlodSolasPixelActive() || !(z1>=0.56 && z1<1.0 && blocks>shaderlodNear+max(0.05,shaderlodNear*0.001)))
        return shaderlodSolasPixelColor(sourceUv,z1);
    vec2 full=vec2(viewWidth,viewHeight), small=floor(full*0.85);
    vec2 position=sourceUv*small-0.5, base=floor(position), part=fract(position);
    float tolerance=max(0.25,blocks*0.01), sumWeight=0.0, confidence=1.0;
    vec3 sum=vec3(0.0);
    for(int i=0;i<4;++i){
        vec2 corner=vec2(float(i-(i/2)*2),float(i/2));
        vec2 cell=clamp(base+corner,vec2(0.0),small-1.0);
        vec4 field=texture2DLod(colortex12,(cell+0.5)/full,0.0);
        float valid=0.0;
        if(all(greaterThanEqual(field.rgb,vec3(0.0))) && all(lessThan(field.rgb,vec3(1e6))) && field.a>shaderlodNear && field.a<1e6)
            valid=1.0-smoothstep(0.25,1.0,abs(field.a-blocks)/tolerance);
        float weight=mix(1.0-part.x,part.x,corner.x)*mix(1.0-part.y,part.y,corner.y);
        confidence=min(confidence,valid);
        if(valid>0.0){sum+=field.rgb*(weight*valid);sumWeight+=weight*valid;}
    }
    if(!(confidence>0.0 && sumWeight>0.0 && sumWeight<=1.00001))return shaderlodSolasPixelColor(sourceUv,z1);
    vec3 resolved=sum/sumWeight;
    if(confidence<1.0)return mix(shaderlodSolasPixelColor(sourceUv,z1),resolved,confidence);
    return resolved;
}

#ifdef SHADERLOD_SOLAS_PIXEL_PRODUCER
/*
const int colortex12Format = RGBA32F;
*/
const bool colortex12Clear=true;
const vec4 colortex12ClearColor=vec4(0.0,0.0,0.0,-1.0);
void main() {
    vec2 full=vec2(viewWidth,viewHeight), small=floor(full*0.85);
    vec2 sourceUv=(floor(gl_FragCoord.xy/max(small,vec2(1.0))*full)+0.5)/full;
    float z=texture2DLod(depthtex1,sourceUv,0.0).r;
    float blocks=shaderlodSolasPixelEye(z);
    vec4 field=vec4(0.0,0.0,0.0,-1.0);
    if(shaderlodSolasPixelActive() && z>=0.56 && z<1.0 && blocks>shaderlodNear+max(0.05,shaderlodNear*0.001)) {
        vec3 color=shaderlodSolasPixelColor(sourceUv,z);
        if(all(greaterThanEqual(color,vec3(0.0))) && all(lessThan(color,vec3(1e6)))) field=vec4(color,blocks);
    }
    /* RENDERTARGETS:12 */
    gl_FragData[0]=field;
}
#else
void main() {
	vec3 color = vec3(0.0);

	vec3 temporalColor = vec3(0.0);
	#ifdef TAA
		 temporalColor = texture2D(colortex2, texCoord).gba;
	#endif

	float temporalData = 0.0;
	float z1 = texture2D(depthtex1, texCoord).r;

	//Lens Flare Parameters
	#ifdef LENS_FLARE
	float pixelWidth = 1.0 / viewWidth;
	float pixelHeight = 1.0 / viewHeight;
	float tempVisibleSun = texture2D(colortex2, vec2(3.0 * pixelWidth, pixelHeight)).r;
	#endif

	color = shaderlodSolasPixelResolve(texCoord,z1);

	color += (Bayer8(gl_FragCoord.xy) - 0.25) / 128.0;

	//Lens Flare
	#ifdef LENS_FLARE
	vec2 lightPos = getLightPos();
	float truePos = sign(sunVec.z);
	      
    float visibleSun = float(texture2D(depthtex1, lightPos + 0.5).r >= 1.0);
		  visibleSun *= max(1.0 - isEyeInWater, eBS) * (1.0 - wetness) * caveFactor;

	#if MC_VERSION >= 11900
		  visibleSun *= (1.0 - max(blindFactor, darknessFactor));
	#endif
	
	float multiplier = tempVisibleSun * LENS_FLARE_STRENGTH * (length(color) * 0.25 + 0.25);

	#ifdef OVERWORLD
		  multiplier *= shadowFade;
	#endif

	if (multiplier > 0.001) LensFlare(color, lightPos, truePos, multiplier);

	if (texCoord.x > 2.0 * pixelWidth && texCoord.x < 4.0 * pixelWidth && texCoord.y < 2.0 * pixelHeight)
		temporalData = mix(tempVisibleSun, visibleSun, 0.125);
	#endif

	/* DRAWBUFFERS:12 */
	gl_FragData[0].rgb = color;
	gl_FragData[1] = vec4(temporalData, shaderlodSolasPixelHistoryReset==1?vec3(0.0):temporalColor);
}
#endif


#endif

//**//**//**//**//**//**//**//**//**//**//**//**//**//**//

#ifdef VSH

// VSH Data //
out vec2 texCoord;

void main() {
	texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;

	gl_Position = ftransform();
}

#endif
#define DEFERRED_0
#define SHADERLOD_SOLAS_AO_HALF_PRODUCER (1)

//Settings//
#include "/lib/common.glsl"

#ifdef FSH

// VSH Data //
in vec2 texCoord;
vec2 shaderlodSolasAoCoord;
#define texCoord shaderlodSolasAoCoord
vec2 shaderlodSolasAoSourcePixel;
vec3 shaderlodSolasAoProducedNormal = vec3(0.0);
uniform int frameCounter;

// Uniforms //
uniform float far, near;
uniform float frameTimeCounter;
uniform float viewWidth, viewHeight, aspectRatio;

uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;

uniform sampler2D depthtex0;

#ifdef VOXY
uniform mat4 vxProjInv;

uniform sampler2D vxDepthTexOpaque;
#endif

#ifdef DISTANT_HORIZONS
uniform float dhFarPlane, dhNearPlane;

uniform mat4 dhProjectionInverse;
uniform sampler2D dhDepthTex0;
#endif

// Includes //
#include "/lib/util/bayerDithering.glsl"
#include "/lib/lighting/ambientOcclusion.glsl"

// ShaderLOD graph boundary
#include "/lib/shaderlod/solas_ao_graph.glsl"
/*
const int colortex10Format = RGBA32F;
const int colortex11Format = RGBA32F;
*/
const bool colortex10Clear = true;
const bool colortex11Clear = true;
const vec4 colortex10ClearColor = vec4(1.0,0.0,0.0,-1.0);
const vec4 colortex11ClearColor = vec4(0.0,0.0,0.0,0.0);
// Main //
void main() {
    vec2 fullSize=vec2(viewWidth,viewHeight), halfSize=floor(fullSize*0.5);
    shaderlodSolasAoSourcePixel=floor(gl_FragCoord.xy)*2.0+vec2(1.5);
    texCoord=shaderlodSolasAoSourcePixel/fullSize;
    float z=texture2DLod(depthtex0,texCoord,0.0).r;
    float ao=1.0, valid=0.0, linearDepth=GetLinearDepth(z,gbufferProjectionInverse);
    if(shaderlodSolasAoGraphActive() && z>=0.56 && z<1.0 && linearDepth>shaderlodNear && linearDepth<1e6) {
        ao=calculateAO(z,depthtex0,gbufferProjectionInverse,near,far,0.25,false);
        if(ao>=0.0 && ao<=1.0 && dot(shaderlodSolasAoProducedNormal,shaderlodSolasAoProducedNormal)>0.99 && dot(shaderlodSolasAoProducedNormal,shaderlodSolasAoProducedNormal)<1.01) valid=1.0;
    }
    /* RENDERTARGETS:10,11 */
    gl_FragData[0]=vec4(ao,linearDepth,valid,float(frameCounter));
    gl_FragData[1]=vec4(shaderlodSolasAoProducedNormal,valid);
}

#endif


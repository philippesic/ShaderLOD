#include "/lib/config.glsl"
/*
const int colortex8Format = R16F;
*/
/*
const int colortex9Format = R32F;
*/
const bool colortex8Clear = false;
const bool colortex9Clear = false;
const bool colortex8Nearest = true;
const bool colortex9Nearest = true;
const bool colortex1MipmapEnabled = true;
varying float exposure;
void main() {
    /* RENDERTARGETS: 8,9 */
    gl_FragData[0] = vec4(exposure, 0.0, 0.0, 0.0);
    gl_FragData[1] = vec4(exposure, 0.0, 0.0, 0.0);
}

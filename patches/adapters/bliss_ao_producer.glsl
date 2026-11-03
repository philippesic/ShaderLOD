#include "/lib/shaderlod/bliss_ao_kernel.glsl"
#include "/lib/shaderlod/bliss_ao_graph.glsl"
/* RENDERTARGETS:12 */
void main() {
    shaderlodBlissAoFragCoord = vec4(floor(gl_FragCoord.xy)*2.0+1.5,gl_FragCoord.zw);
    vec2 pixel = shaderlodBlissAoFragCoord.xy;
    vec2 uv = pixel*texelSize;
    float z = texture2D(depthtex1,uv).r;
    vec4 shaderlodBlissAoPackedData = texelFetch2D(colortex1,ivec2(pixel),0);
    float material = decodeVec2(shaderlodBlissAoPackedData.w).y;
    bool hand = abs(material-0.75)<0.01;
    bool leaf = abs(material-0.55)<0.01;
    vec3 viewPos = toScreenSpace_DH(uv/RENDER_SCALE-TAA_Offset*texelSize*0.5,z,1.0);
    vec3 normal = texture2D(colortex15,uv).rgb*2.0-1.0;
    vec2 ao = vec2(1.0,0.0);
    float valid = 0.0;
    float eyeDepth = -viewPos.z;
    if (shaderlodBlissAoActive() && !hand && !leaf && z>=0.0 && z<1.0 &&
        all(lessThan(pixel,vec2(viewWidth,viewHeight)*RENDER_SCALE)) && eyeDepth>max(32.0,shaderlodNear) && eyeDepth<1e6 &&
        all(lessThan(abs(normal),vec3(1.01))) && dot(normal,normal)>0.5) {
        ao=SSAO(viewPos,normal,false,false,R2_dither());
        if(all(greaterThanEqual(ao,vec2(0.0))) && all(lessThanEqual(ao,vec2(1.0))))valid=1.0;
    }
    gl_FragData[0]=vec4(ao,valid,eyeDepth);
}

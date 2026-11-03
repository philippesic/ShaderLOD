#include "/lib/shaderlod/bliss_ao_graph.glsl"
vec2 shaderlodBlissReconstructAo(vec3 viewPos,vec3 normal,bool hand,bool leaf,float noise) {
    float eyeDepth=-viewPos.z;
    if(!shaderlodBlissAoActive() || hand || leaf || !all(lessThan(gl_FragCoord.xy,vec2(viewWidth,viewHeight)*RENDER_SCALE)) || !(eyeDepth>max(32.0,shaderlodNear) && eyeDepth<1e6))
        return SSAO(viewPos,normal,hand,leaf,noise);
    vec2 halfSize=floor(vec2(viewWidth,viewHeight)*0.5);
    vec2 halfPos=(gl_FragCoord.xy-1.5)*0.5;
    vec2 base=floor(halfPos),fraction=fract(halfPos);
    float material=decodeVec2(texelFetch2D(colortex1,ivec2(gl_FragCoord.xy),0).w).y;
    vec2 sum=vec2(0.0);float weightSum=0.0;
    for(int i=0;i<4;i++) {
        vec2 corner=vec2(float(i-(i/2)*2),float(i/2));
        ivec2 cell=ivec2(clamp(base+corner,vec2(0.0),halfSize-1.0));
        vec4 stored=texelFetch2D(colortex12,cell,0);
        ivec2 nativePixel=cell*2+ivec2(1);
        // Do not blend a representative outside the current frozen native raster rectangle.
        // Edge receivers retain native SSAO, including a full-size warmup/recovery frame.
        if(!all(lessThan(vec2(nativePixel)+0.5,vec2(viewWidth,viewHeight)*RENDER_SCALE)))
            return SSAO(viewPos,normal,hand,leaf,noise);
        vec3 candidateNormal=texelFetch2D(colortex15,nativePixel,0).rgb*2.0-1.0;
        float candidateMaterial=decodeVec2(texelFetch2D(colortex1,nativePixel,0).w).y;
        float tolerance=max(0.25,eyeDepth*0.005);
        if(!(stored.b==1.0 && all(greaterThanEqual(stored.rg,vec2(0.0))) &&
            all(lessThanEqual(stored.rg,vec2(1.0))) && stored.a>0.0 && stored.a<1e6 &&
            abs(stored.a-eyeDepth)<tolerance && abs(material-candidateMaterial)<1e-5 &&
            dot(normalize(normal),normalize(candidateNormal))>0.999))
            return SSAO(viewPos,normal,hand,leaf,noise);
        float weight=mix(1.0-fraction.x,fraction.x,corner.x)*mix(1.0-fraction.y,fraction.y,corner.y);
        sum+=stored.rg*weight;weightSum+=weight;
    }
    if(!(weightSum>0.9999 && weightSum<1.0001))return SSAO(viewPos,normal,hand,leaf,noise);
    return sum/weightSum;
}

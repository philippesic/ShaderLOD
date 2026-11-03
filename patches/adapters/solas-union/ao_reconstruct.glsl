#include "/lib/shaderlod/solas_ao_graph.glsl"
uniform sampler2D colortex10;
uniform sampler2D colortex11;
bool shaderlodSolasTryHalfAo(float z,out float result) {
    result=1.0;
    if(!shaderlodSolasAoGraphActive() || !(z>=0.56 && z<1.0)) return false;
    float depth=GetLinearDepth(z,gbufferProjectionInverse);
    if(!(depth>shaderlodNear+max(0.05,shaderlodNear*0.001) && depth<1e6)) return false;
    vec3 viewPos=ToView2(vec3(texCoord,z),gbufferProjectionInverse);
    vec3 normal=reconstructNormal(depthtex0,depth,viewPos,gbufferProjectionInverse);
    vec2 fullSize=vec2(viewWidth,viewHeight),halfSize=floor(fullSize*0.5),pos=(gl_FragCoord.xy - vec2(1.5)) * 0.5,base=floor(pos),f=fract(pos);
    float sum=0.0,weightSum=0.0,confidence=1.0,tolerance=max(0.25,depth*0.01);
    for(int i=0;i<4;i++) {
        vec2 corner=vec2(float(i-(i/2)*2),float(i/2));
        vec2 cell=clamp(base+corner,vec2(0.0),halfSize-1.0),uv=(cell+0.5)/fullSize;
        vec4 value=texelFetch(colortex10,ivec2(cell),0),fieldNormal=texelFetch(colortex11,ivec2(cell),0);
        float c=0.0;
        if(value.r>=0.0 && value.r<=1.0 && value.g>shaderlodNear && value.g<1e6 && value.b==1.0 && value.a==float(frameCounter) && fieldNormal.a==1.0 && dot(fieldNormal.xyz,fieldNormal.xyz)>0.99 && dot(fieldNormal.xyz,fieldNormal.xyz)<1.01)
            c=(1.0-smoothstep(0.25,1.0,abs(value.g-depth)/tolerance))*smoothstep(0.99,0.999,dot(normal,fieldNormal.xyz));
        float w=mix(1.0-f.x,f.x,corner.x)*mix(1.0-f.y,f.y,corner.y);
        confidence=min(confidence,c);
        // Zero confidence cannot recover; keep the post-loop native fallback unchanged.
        if (confidence == 0.0) break;sum+=value.r*w*c;weightSum+=w*c;
    }
    // Partial continuity retains the literal native full-resolution kernel.
    if(!(confidence>=0.99999 && weightSum>0.0 && weightSum<=1.00001)) return false;
    result=sum/weightSum;return result>=0.0 && result<=1.0;
}

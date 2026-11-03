
uniform sampler2D colortex13;
uniform int shaderlodSildurPixelReady;
uniform vec2 shaderlodSildurPixelSize;
#include "/lib/shaderlod/adapter.glsl"
bool shaderlodSildurPixelActive(){return shaderlodSildurPixelReady==1 && shaderlod_optimization_enabled(shaderlodOptPixelScale) && all(equal(shaderlodSildurPixelSize,vec2(viewWidth,viewHeight))) && shaderlodNear>0.0 && shaderlodNear<shaderlodFar && shaderlodFar<=4096.0;}
float shaderlodSildurPixelDistance(vec2 uv,float z){vec3 v=utilScreenSpace(vec3(uv,z));float d=length(v);return d>0.0&&d<1e6?d:-1.0;}
vec3 shaderlodSildurPixelNative(vec2 sourceUv){
    const float weights[5] = float[](0.1974, 0.1746, 0.1209, 0.0617, 0.0219);
    const float offsets[5] = float[](0.0, 1.3846, 3.2307, 5.0769, 6.9230);
    vec2 newTC = sourceUv * 0.25;
    vec2 stepSize = vec2(0.0, 1.0 / viewHeight);

    vec3 blur = texture2DGradARB(colortex5, newTC,vec2(0.25/viewWidth,0.0),vec2(0.0,0.25/viewHeight)).rgb * weights[0];
    for (int i = 1; i < 5; i++) {
        vec2 offs = stepSize * offsets[i];
        vec3 sample1 = texture2DGradARB(colortex5, newTC + offs,vec2(0.25/viewWidth,0.0),vec2(0.0,0.25/viewHeight)).rgb;
        vec3 sample2 = texture2DGradARB(colortex5, newTC - offs,vec2(0.25/viewWidth,0.0),vec2(0.0,0.25/viewHeight)).rgb;
        blur += (sample1 + sample2) * weights[i];
    }

    vec3 glow = blur * 12.0 * bloom_strength;
    vec3 overglow = glow * pow(length(glow) * 1.5, 2.0);
    vec3 finalBloom = (overglow + glow * 1.15) * (1.0 + (rainStrength * 2.0)) * 1.2;

    return finalBloom * 0.00001;
}
vec3 shaderlodSildurPixelResolve(vec2 uv,float z){
 float blocks=shaderlodSildurPixelDistance(uv,z);
 if(!shaderlodSildurPixelActive()||!(z>=0.56&&z<1.0&&blocks>shaderlodNear+max(0.05,shaderlodNear*0.001)))return shaderlodSildurPixelNative(uv);
 vec2 full=vec2(viewWidth,viewHeight),small=floor(full*0.85),position=uv*small-0.5,base=floor(position),part=fract(position);
 float tolerance=max(0.25,blocks*0.01),sumWeight=0.0,confidence=1.0;vec3 sum=vec3(0.0);
 for(int i=0;i<4;++i){vec2 corner=vec2(float(i-(i/2)*2),float(i/2)),cell=clamp(base+corner,vec2(0.0),small-1.0);vec4 field=texture2DLod(colortex13,(cell+0.5)/full,0.0);float valid=0.0;
 if(all(greaterThanEqual(field.rgb,vec3(0.0)))&&all(lessThan(field.rgb,vec3(1e6)))&&field.a>shaderlodNear&&field.a<1e6)valid=1.0-smoothstep(0.25,1.0,abs(field.a-blocks)/tolerance);
 float weight=mix(1.0-part.x,part.x,corner.x)*mix(1.0-part.y,part.y,corner.y);confidence=min(confidence,valid);if(valid>0.0){sum+=field.rgb*(weight*valid);sumWeight+=weight*valid;}}
 if(!(confidence>0.0&&sumWeight>0.0&&sumWeight<=1.00001))return shaderlodSildurPixelNative(uv);vec3 value=sum/sumWeight;if(confidence<1.0)return mix(shaderlodSildurPixelNative(uv),value,confidence);return value;
}

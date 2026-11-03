#!/usr/bin/env python3
"""G05 pinned native glass zero-border tail certificate; default off/private.

Glass uses fourth powers for all four border edges, unlike water's undefined
negative-base pow. Admit monotone outward rays only before native fallback76
can lie behind the certified point. No visible-ray traversal/quality reduction.
"""
import argparse,json
from pathlib import Path
from build_makeup_unused_sky import read_archive,archive_bytes,once,sha
OPTION='SHADERLOD_G05_GLASS_MISS'
DEBUG='SHADERLOD_G05_GLASS_DEBUG'
WATER='shaders/lib/water.glsl'
CONFIG='shaders/lib/config.glsl'
PROPS='shaders/shaders.properties'
HELPER='shaders/lib/shaderlod/g05_glass_miss.glsl'
GUARD=f'{OPTION} == 1 && defined IS_IRIS && IRIS_VERSION == 10812 && MC_VERSION == 12101 && defined MC_OS_MAC && defined GBUFFER_WATER && defined WATER_F && REFLECTION == 1 && SSR_TYPE == 1 && RAYMARCH_STEPS == 10 && !defined NETHER && !defined THE_END && !defined UNKNOWN_DIM && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH'
CERT=r'''
bool shaderlodG05GlassProjection() {
    return all(equal(gbufferProjection[0].yzw, vec3(0.0))) &&
        all(equal(gbufferProjection[1].xzw, vec3(0.0))) &&
        all(equal(gbufferProjection[2].xy, vec2(0.0))) && gbufferProjection[2].w == -1.0 &&
        all(equal(gbufferProjection[3].xyw, vec3(0.0))) &&
        all(lessThan(abs(gbufferProjection[0]), vec4(1.0e4))) &&
        all(lessThan(abs(gbufferProjection[1]), vec4(1.0e4))) &&
        all(lessThan(abs(gbufferProjection[2]), vec4(1.0e4))) &&
        all(lessThan(abs(gbufferProjection[3]), vec4(1.0e4))) &&
        gbufferProjection[0].x > 1.0e-4 && gbufferProjection[1].y > 1.0e-4;
}
bool shaderlodG05GlassZeroTail(vec3 point, vec3 direction, vec3 errorBound) {
    float px = gbufferProjection[0].x;
    float py = gbufferProjection[1].y;
    vec4 edge = vec4(px * point.x, -px * point.x, py * point.y, -py * point.y) + point.z;
    vec4 slope = vec4(px * direction.x, -px * direction.x, py * direction.y, -py * direction.y) + direction.z;
    vec4 bound = vec4(px * errorBound.x, px * errorBound.x, py * errorBound.y, py * errorBound.y) + errorBound.z;
    vec4 slopeError = 1.0e-5 * (vec4(px * abs(direction.x), px * abs(direction.x), py * abs(direction.y), py * abs(direction.y)) + abs(direction.z) + 1.0);
    return point.z < -errorBound.z && direction.z <= 0.0 &&
        any(bvec4(edge.x > bound.x && slope.x > slopeError.x,
                  edge.y > bound.y && slope.y > slopeError.y,
                  edge.z > bound.z && slope.z > slopeError.z,
                  edge.w > bound.w && slope.w > slopeError.w));
}
'''

def patch(native):
    files=dict(native);water=native[WATER].decode();start=water.index('vec3 fastRaymarch(');end=water.index('\n#if SUN_REFLECTION',start);trace=water[start:end]
    clone=once(trace,'vec3 fastRaymarch(','vec3 shaderlodG05GlassRaymarch(')
    clone=once(clone,'    // Ray marching', '''    bool shaderlodG05Checked = false;
    bool shaderlodG05Eligible = false;
    vec3 shaderlodG05Error = vec3(0.0);
    // Ray marching''')
    anchor='    for (int i = 0; i < RAYMARCH_STEPS; i++) {\n'
    clone=once(clone,anchor,anchor+f'''        // At loop entry i<=6, the last coarse parameter is at most63.
        // Native glass fallback projects hitPoint+direction*76: it remains
        // forward of this point and outside the same certified border edge.
        if (i > 0 && i <= 6 && !searchFlag && outOfEyeFlag) {{
            if (!shaderlodG05Checked) {{
                shaderlodG05Checked = true;
                shaderlodG05Eligible = shaderlodG05GlassProjection() &&
                    all(lessThan(abs(hitPoint), vec3(65536.0))) &&
                    all(lessThanEqual(abs(direction), vec3(1.0))) && dither >= 0.0 && dither <= 1.0;
                shaderlodG05Error = 0.0001 * (abs(hitPoint) + abs(direction) * 2048.0 + vec3(1.0));
            }}
            if (shaderlodG05Eligible && shaderlodG05GlassZeroTail(currentMarchPoint, direction, shaderlodG05Error)) {{
                infinite = 1.0;
                #if {DEBUG} == 1
                shaderlodG05GlassSavedSteps = float(RAYMARCH_STEPS - i);
                #endif
                return screenMarchPos;
            }}
        }}
''')
    # Native depthtex1 and colortex4 have no mip producers in this tuple.
    # Runtime sampler joins must qualify level0 before trusting this variant.
    clone=clone.replace('texture2D(depthtex1, screenMarchPos.xy)','texture2DLod(depthtex1, screenMarchPos.xy, 0.0)')
    files[HELPER]=(f'#if {GUARD}\n#if {DEBUG} == 1\nfloat shaderlodG05GlassSavedSteps = 0.0;\n#endif\n'+CERT+clone+'\n#endif\n').encode()
    water=water[:end]+'\n#include "/lib/shaderlod/g05_glass_miss.glsl"\n'+water[end:]
    anchor='        vec3 pos = fastRaymarch(reflectedVector, fragpos, infinite, dither);'
    water=once(water,anchor,f'        #if {GUARD}\n        vec3 pos = shaderlodG05GlassRaymarch(reflectedVector, fragpos, infinite, dither);\n        #else\n'+anchor+'\n        #endif')
    fetch='    return vec4(texture2D(gaux1, pos.xy, 0.0).rgb, border);'
    water=once(water,fetch,f'    #if {GUARD}\n    return vec4(texture2DLod(gaux1, pos.xy, 0.0).rgb, border);\n    #else\n'+fetch+'\n    #endif');files[WATER]=water.encode()
    caller='shaders/common/water_blocks_fragment.glsl';anchor='    // Avoid render in DH transition'
    files[caller]=once(native[caller].decode(),anchor,f'''    #if ({GUARD}) && {DEBUG} == 1
    // Untimed glass-only source omission witness: red skipped/10, green executed/10.
    if (blockType > 1.5 && blockType <= 2.5) blockColor.rgb = vec3(shaderlodG05GlassSavedSteps / 10.0, 1.0 - shaderlodG05GlassSavedSteps / 10.0, 0.0);
    #endif
'''+anchor).encode()
    files[CONFIG]+=f'\n// Private exact G05 glass-only projected tail, default OFF.\n#define {OPTION} 0 // [0 1]\n#define {DEBUG} 0 // [0 1]\n'.encode()
    files[PROPS]=once(native[PROPS].decode(),'screen = ','screen = [SHADERLOD_G05_GLASS] ').encode()+f'\nscreen.SHADERLOD_G05_GLASS = {OPTION} {DEBUG}\n'.encode()
    return files

def build(original,options,output):
    native=read_archive(original);raw=Path(options).read_bytes();output=Path(output);paths=[output,Path(str(output)+'.txt'),output.with_suffix('.json')]
    if any(p.exists() for p in paths) or b'SHADERLOD_' in raw:raise ValueError('Fresh output and native-only options required')
    files=patch(native);meta={'id':'G05','variant':'glass-zero-border-tail-r1','native_sha256':sha(Path(original).read_bytes()),'default_off':True,'changed_members':[n for n in native if files[n]!=native[n]],'scope':'Root Mac MakeUp9.5g MC1.21.1/Iris1.8.12 world0 glass class2, native100 AA2/SSR10, noDH/Voxy; water stays native.','certificate':'Symmetric finite perspective; outward monotone ray beyond any of four glass border planes, z forward; no prior refinement, current coarse parameter<=63 so native76 fallback is also beyond certified edge; native sticky outOfEye forces infinite1; native fourth-power border zero.','limits':'Conservative FP32 envelope; actual native sampler level0/no-mip joins and real coverage needed. Extra guards/branches/explicitLOD cost count; no physical fetch/speed/compatibility claim.'}
    files['SHADERLOD_G05_GLASS_NOTICE.txt']=('Derivative of MakeUp9.5g by Javier Garduno. Original LGPLv3 licenses, ancestry, editable sources, native quality and AA preserved.\n'+json.dumps(meta,indent=2)+'\n').encode();data=archive_bytes(files);output.parent.mkdir(parents=True,exist_ok=True);output.write_bytes(data);paths[1].write_bytes(raw+f'\n{OPTION}=0\n{DEBUG}=0\n'.encode());meta['sha256']=sha(data);paths[2].write_text(json.dumps(meta,indent=2)+'\n');return meta

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--original',required=True,type=Path);p.add_argument('--options',required=True,type=Path);p.add_argument('--output',required=True,type=Path);print(json.dumps(build(**vars(p.parse_args())),indent=2))

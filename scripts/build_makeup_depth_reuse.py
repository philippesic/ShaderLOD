#!/usr/bin/env python3
"""Private G05 exact native depth-query reuse, preserving the entire trace.

1 identical finite normalized coordinates;2 identical nearest/clamped texel with
boundary slack;3 identical clamped corner;4 coarse corner and refined nearest hybrid. All native iterations and arithmetic
stay ordered. Actual immutable depthtex1 nearest/clamp/level0 joins required.
"""
import argparse,json
from pathlib import Path
from build_makeup_unused_sky import read_archive,archive_bytes,once,sha
from build_makeup_projected_miss import NATIVE_SHA256
OPTION='SHADERLOD_G05_DEPTH_REUSE'
DEBUG='SHADERLOD_G05_REUSE_DEBUG'
HELPER='shaders/lib/shaderlod/g05_depth_reuse.glsl'
GUARD=f'{OPTION} > 0 && {OPTION} <= 4 && defined IS_IRIS && IRIS_VERSION == 10812 && MC_VERSION == 12101 && defined MC_OS_MAC && defined GBUFFER_WATER && defined WATER_F && REFLECTION == 1 && SSR_TYPE == 1 && RAYMARCH_STEPS == 10 && !defined NETHER && !defined THE_END && !defined UNKNOWN_DIM && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH'

def patch(native,parity=False):
    files=dict(native);name='shaders/lib/water.glsl';text=files[name].decode();start=text.index('vec3 fastRaymarch(');end=text.index('\n#if SUN_REFLECTION',start);clone=text[start:end].replace('vec3 fastRaymarch(','vec3 shaderlodG05ReuseRaymarch(',1)
    clone=once(clone,'    // Ray marching','''    #if SHADERLOD_G05_DEPTH_REUSE == 2 || SHADERLOD_G05_DEPTH_REUSE == 4
    vec2 previousKey = vec2(0.0);
    bool previousValid = false;
    #endif
    // Ray marching''')
    clone=once(clone,'        screenDepth = texture2D(depthtex1, screenMarchPos.xy).x;',f'''        vec2 uv = screenMarchPos.xy;
        bool validKey = all(lessThan(abs(uv),vec2(8192.0)));
        bool reuse = false;
        #if {OPTION} == 1
        // Native state already retains the previous sampled depth and UV.
        reuse = i>0 && validKey && all(equal(uv,lastScreenMarchPos.xy));
        #elif {OPTION} == 3
        vec2 oldUv = lastScreenMarchPos.xy;
        reuse = i>0 && validKey && all(lessThan(abs(oldUv),vec2(8192.0))) &&
            (uv.x<=0.0 || uv.x>=1.0) && (uv.y<=0.0 || uv.y>=1.0) &&
            (oldUv.x<=0.0 || oldUv.x>=1.0) && (oldUv.y<=0.0 || oldUv.y>=1.0) &&
            (uv.x>=1.0)==(oldUv.x>=1.0) && (uv.y>=1.0)==(oldUv.y>=1.0);
        #else
        vec2 size = vec2(viewWidth,viewHeight);
        vec2 key = vec2(0.0);
        #if {OPTION} == 4
        if (!searchFlag) {{
            // Coarse steps avoid per-fragment floor/fract work in the interior.
            validKey = validKey && (uv.x<=0.0 || uv.x>=1.0) && (uv.y<=0.0 || uv.y>=1.0);
            key = vec2(uv.x>=1.0 ? size.x-1.0 : 0.0,uv.y>=1.0 ? size.y-1.0 : 0.0);
        }} else
        #endif
        {{
            vec2 position = clamp(uv,vec2(0.0),vec2(1.0))*size;
            vec2 phase = fract(position);
            key = min(floor(position),size-vec2(1.0));
            bvec2 outside = bvec2(uv.x<=0.0 || uv.x>=1.0, uv.y<=0.0 || uv.y>=1.0);
            validKey = validKey && (outside.x || (phase.x>0.02 && phase.x<0.98)) &&
                (outside.y || (phase.y>0.02 && phase.y<0.98));
        }}
        validKey = validKey && all(greaterThan(size,vec2(1.0))) && all(lessThanEqual(size,vec2(4096.0)));
        reuse = validKey && previousValid && all(equal(key,previousKey));
        previousKey = key;
        previousValid = validKey;
        #endif
        if (reuse) {{
            screenDepth = prevScreenDepth;
            #if {DEBUG} == 1
            shaderlodG05ReuseSavedQueries += 1.0;
            #endif
        }} else {{
            screenDepth = texture2DLod(depthtex1,uv,0.0).x;
        }}''')
    files[HELPER]=(f'#if {GUARD}\n#if {OPTION} == 2 || {OPTION} == 4\nuniform float viewWidth;\nuniform float viewHeight;\n#endif\n#if {DEBUG} == 1\nfloat shaderlodG05ReuseSavedQueries = 0.0;\n#endif\n'+clone+'\n#endif\n').encode()
    text=text[:end]+'\n#include "/lib/shaderlod/g05_depth_reuse.glsl"\n'+text[end:]
    for direction in ['reflected','reflectedVector']:
        anchor=f'        vec3 pos = fastRaymarch({direction}, fragpos, infinite, dither);'
        reference=f'''        float referenceInfinite = infinite;
        vec3 referencePosition = fastRaymarch({direction},fragpos,referenceInfinite,dither);
''' if parity else ''
        check=f'''        #if {DEBUG} == 1
        shaderlodG05ReuseSavedQueries = (any(notEqual(pos,referencePosition)) || infinite!=referenceInfinite) ? 256.0 : 0.0;
        #endif
''' if parity else ''
        text=once(text,anchor,f'        #if {GUARD}\n'+reference+f'        vec3 pos = shaderlodG05ReuseRaymarch({direction},fragpos,infinite,dither);\n'+check+'        #else\n'+anchor+'\n        #endif')
    # The actual pinned color sampler is unmipped too. Keep derivatives out of
    # this divergent return path without altering its selected native level.
    for query in ['texture2D(gaux1, pos.xy)','texture2D(gaux1, pos.xy, 0.0)']:
        anchor=f'    return vec4({query}.rgb, border);'
        text=once(text,anchor,f'    #if {GUARD}\n    return vec4(texture2DLod(gaux1,pos.xy,0.0).rgb,border);\n    #else\n'+anchor+'\n    #endif')
    files[name]=text.encode();files['shaders/lib/config.glsl']+=f'\n// Exact trace-local G05 query reuse, native by default.\n#define {OPTION} 0 // [0 1 2 3 4]\n#define {DEBUG} 0 // [0 1]\n'.encode()
    p='shaders/shaders.properties';files[p]=once(native[p].decode(),'screen = ','screen = [SHADERLOD_G05_REUSE] ').encode()+f'\nscreen.SHADERLOD_G05_REUSE = {OPTION} {DEBUG}\n'.encode()
    return files

def build(original,options,output,parity=False):
    original,options,output=map(Path,(original,options,output));raw=options.read_bytes()
    if any(p.exists() for p in [output,Path(str(output)+'.txt'),output.with_suffix('.json')]) or b'SHADERLOD_' in raw:raise ValueError('Fresh output/native-only sidecar required')
    native=read_archive(original);files=patch(native,parity)
    meta={'id':'G05','variant':'native-state-reuse-cheap-corner-and-hybrid-r2','native_sha256':NATIVE_SHA256,'default_off':True,'parity_diagnostic':parity,
          'scope':'Root Mac MC1.21.1/Iris1.8.12/MakeUp9.5g native100 AA2 SSR10, glass+water, noDH/Voxy.',
          'contract':'Actual immutable depthtex1 nearest/clamp/level0 and dimensions; color unmipped. Full native iteration/position/depthDifference/state/output order retained. Every extra key/guard/branch counts.',
          'limits':'Source alternatives and conservative FP32 margin, not broad tuple, hardware-fetch, image parity or speed acceptance.'}
    files['G05-build.json']=(json.dumps(meta,indent=2)+'\n').encode();files['depth-reuse-source/build_makeup_depth_reuse.py']=Path(__file__).read_bytes()
    output.write_bytes(archive_bytes(files));Path(str(output)+'.txt').write_bytes(raw+f'\n{OPTION}=0\n{DEBUG}=0\n'.encode());meta['sha256']=sha(output.read_bytes());output.with_suffix('.json').write_text(json.dumps(meta,indent=2)+'\n');return meta

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--original',required=True,type=Path);p.add_argument('--options',required=True,type=Path);p.add_argument('--output',required=True,type=Path);p.add_argument('--parity',action='store_true');print(json.dumps(build(**vars(p.parse_args())),indent=2))

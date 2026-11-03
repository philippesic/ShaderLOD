#!/usr/bin/env python3
"""Private default-off G08 signed travel-bin cloud bounds for MakeUp9.5g.

Two bound queries per admitted ray. Native positions, order, animation, quality
and AA stay unchanged. Physical sampler joins and runtime economics required.
"""
import argparse, json, struct, zlib
from pathlib import Path
from build_cloud_volume_makeup_saturation import decoded_cloud_rgba
from build_makeup_unused_sky import read_archive, archive_bytes, once, sha

NATIVE='0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8'
CLOUD='shaders/lib/volumetric_clouds.glsl'
OPTION='SHADERLOD_G08_DIRECTIONAL'
DEBUG='SHADERLOD_G08_DIRECTIONAL_DEBUG'
ATLAS='shaders/textures/shaderlod_g08_directional.png'
GATE=f'{OPTION} > 0 && defined IS_IRIS && IRIS_VERSION == 10812 && MC_VERSION == 12101 && defined MC_OS_MAC && defined DEFERRED_SHADER && !defined THE_END && !defined NETHER && !defined UNKNOWN_DIM && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH && V_CLOUDS == 2 && CLOUD_VOL_STYLE == 0 && CLOUD_SPEED == 0'

NATIVE100_GATE=GATE.replace('defined MC_OS_MAC','defined MC_OS_LINUX').replace('V_CLOUDS == 2','V_CLOUDS == 1 && CLOUD_STEPS_AVG == 10')

def interval(case):
    """Native start tile16 plus signed bin4/8/12/16, 3-texel apron."""
    travel=4*(case%4+1)
    return (-3-travel,19) if case>=4 else (-3,19+travel)

def bounds(rgba):
    if len(rgba)!=512*512*4:raise ValueError('Native 512 RGBA required')
    reds=rgba[::4]
    # Separable rectangular maxima, preserving repeat wrap in both axes.
    horizontal=[]
    for case in range(8):
        lo,hi=interval(case)
        horizontal.append([max(reds[y*512+(tx*16+x)%512] for x in range(lo,hi))
                           for y in range(512) for tx in range(32)])
    out=bytearray(256*256)
    for cy in range(8):
        lo,hi=interval(cy)
        for cx in range(8):
            h=horizontal[cx]
            for ty in range(32):
                for tx in range(32):
                    peak=max(h[((ty*16+y)%512)*32+tx] for y in range(lo,hi))
                    out[(cy*32+ty)*256+cx*32+tx]=min(255,peak+1)
    return bytes(out)

def png(values):
    if len(values)!=65536:raise ValueError('256 atlas required')
    def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    raw=b''.join(b'\0'+b''.join(bytes((r,53,197,255)) for r in values[y*256:(y+1)*256]) for y in range(256))
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',256,256,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')

HELPER='''
float shaderlodG08DirectionalBound(vec2 uv, vec2 travel) {
    // 4/8/12/16 texel signed bins; tile covers every native starting texel.
    vec2 bin = clamp(ceil(abs(travel) * 128.0) - 1.0, vec2(0.0), vec2(3.0));
    bin += vec2(travel.x < 0.0 ? 4.0 : 0.0, travel.y < 0.0 ? 4.0 : 0.0);
    vec2 pixel = floor(fract(uv) * 32.0) + bin * 32.0;
    vec4 data = texture2DLod(noisetex, (pixel + 0.5) * 0.00390625, 0.0);
    bool valid = abs(data.g - 0.207843137255) < 0.0001 && abs(data.b - 0.772549019608) < 0.0001 && data.a > 0.9999;
    return valid ? data.r : 1.0;
}
'''

def patch(native,tail_steps=0,native_queries=False,split_tail=False,masked_tail=False,zero_guard=False,native100=False):
    gate=NATIVE100_GATE if native100 else GATE
    if tail_steps not in [0,4,8]:raise ValueError('Only bounded4/8-step tail alternatives')
    if masked_tail and (tail_steps not in [4,8] or split_tail or not native_queries):raise ValueError('Masked native-query tail4/8 requires independent scheduling')
    if split_tail and tail_steps!=4:raise ValueError('Split scheduling is the four-step tail alternative')
    files=dict(native)
    files['shaders/lib/config.glsl']+=f'\n// G08 private directional bounds:0 native,1 bounds-only,2 skip.\n#define {OPTION} 0 // [0 1 2]\n#define {DEBUG} 0 // [0 1]\n'.encode()
    if zero_guard:
        if tail_steps or split_tail or masked_tail or not native_queries:
            raise ValueError('Zero-density ALU guard is independent and retains all native queries')
        source=native[CLOUD].decode()
        start=source.index('            float surface_inf = ')
        end=source.index('            intersection_pos += increment;',start)
        body=source[start:end]
        guarded=(f'            #if ({gate}) && {OPTION} == 2\n'
            +'            // Nonpositive normalized density has no positive thickness or opacity.\n'
            +'            // Unordered values retain the native body. Native lookups/positions remain.\n'
            +'            if (!(current_value <= 0.0)) {\n            #endif\n'+body
            +f'            #if ({gate}) && {OPTION} == 2\n'
            +'            } else {\n'
            +f'                #if {DEBUG} == 1\n                shaderlodG08SavedSteps += 1.0;\n                #endif\n'
            +'            }\n            #endif\n')
        source=source[:start]+guarded+source[end:]
        source=(f'#if ({gate}) && {DEBUG} == 1\nfloat shaderlodG08Eligible = 1.0;\nfloat shaderlodG08SavedSteps = 0.0;\n#endif\n'+source)
        files[CLOUD]=source.encode()
        p='shaders/shaders.properties'
        files[p]+=f'\nscreen.SHADERLOD_G08_DIRECTIONAL = {OPTION} {DEBUG}\n'.encode()
        files[p]=once(files[p].decode(),'screen = ','screen = [SHADERLOD_G08_DIRECTIONAL] ').encode()
        return files
    source=f'#if {gate}\n'+HELPER+f'\n#if {DEBUG} == 1\nfloat shaderlodG08Eligible = 0.0;\nfloat shaderlodG08SavedSteps = 0.0;\n#endif\n#endif\n'+native[CLOUD].decode()
    anchor='        for (int i = 0; i < samples; i++) {'
    before=f'''        #if {gate}
        float shaderlodG08Ceiling = 1.0e30;
        vec2 shaderlodG08Travel = increment.xz * float(samples) * 0.0002777777777777778;
        bool shaderlodG08Allowed = samples >= 7 && samples <= 16 && umbral >= 0.0 && umbral <= 0.9 &&
            all(lessThan(abs(shaderlodG08Travel), vec2(0.03125))) &&
            increment.y > 0.0 && increment.y < 256.0 &&
            dither >= 0.0 && dither <= 1.0 && abs(frameTimeCounter) < 8192.0 &&
            all(lessThan(abs(intersection_pos), vec3(32768.0))) &&
            all(lessThan(abs(intersection_pos_sup), vec3(32768.0)));
        if (shaderlodG08Allowed) {{
            vec2 uvHi = intersection_pos.xz * 0.0002777777777777778 + frameTimeCounter * CLOUD_HI_FACTOR;
            vec2 uvLow = intersection_pos.zx * 0.0002777777777777778 + frameTimeCounter * CLOUD_LOW_FACTOR;
            float upper = smoothstep(0.05, 0.95, 0.5 *
                (shaderlodG08DirectionalBound(uvHi, shaderlodG08Travel) + shaderlodG08DirectionalBound(uvLow, shaderlodG08Travel.yx)));
            upper = max((upper - umbral) / (1.0 - umbral), 0.0);
            shaderlodG08Ceiling = CLOUD_PLANE_CENTER + upper * dif_sup + dist_aux_coeff_blur + 0.01;
            #if {DEBUG} == 1
            shaderlodG08Eligible = 1.0;
            #endif
        }}
        #endif

'''
    suffix=f'''
            #if ({gate}) && {OPTION} == 2
            if (intersection_pos.y > shaderlodG08Ceiling) {{
                #if {DEBUG} == 1
                shaderlodG08SavedSteps = float(samples - i);
                #endif
                break;
            }}
            #endif'''
    if native100:
        before=once(before,'float upper = smoothstep(0.05, 0.95, 0.5 *\n                (shaderlodG08DirectionalBound(uvHi, shaderlodG08Travel) + shaderlodG08DirectionalBound(uvLow, shaderlodG08Travel.yx)));',
                    'float upper = shaderlodG08DirectionalBound(uvHi, shaderlodG08Travel);')
    if masked_tail:
        # Keep all native loop iterations and position additions. Omit only
        # the certified zero-opacity iteration body; do not break the loop.
        start=source.index(anchor);brace=source.index('{',start);depth=1;end=brace+1
        while depth:
            if source[end]=='{':depth+=1
            elif source[end]=='}':depth-=1
            end+=1
        original=source[start:end];body=source[brace+1:end-1]
        update='            intersection_pos += increment;'
        if body.count(update)!=1:raise ValueError('Native position update changed')
        body=body.replace(update,'')
        ceiling='        float shaderlodG08Ceiling = 1.0e30;\n'
        checkpoint=before.removeprefix(f'        #if {gate}\n').removesuffix('        #endif\n\n')
        checkpoint=once(checkpoint,ceiling,'').replace('float(samples) * 0.0002777777777777778','float(samples - i) * 0.0002777777777777778')
        selected=(f'        #if {gate}\n'+ceiling+anchor+'\n'
            +f'            if (i == samples - {tail_steps}) {{\n'+checkpoint+'            }\n'
            +f'            #if {OPTION} == 2\n'
            +'            if (!(intersection_pos.y > shaderlodG08Ceiling)) {\n'+body+'            } else {\n'
            +f'                #if {DEBUG} == 1\n                shaderlodG08SavedSteps += 1.0;\n                #endif\n'
            +'            }\n            #else\n'+body+'            #endif\n'+update+'\n        }\n'
            +'        #else\n'+original+'\n        #endif')
        source=source[:start]+selected+source[end:]
    elif split_tail:
        # Preserve every native prefix operation without a checkpoint or ceiling
        # guard in that loop. The second loop retains original i values and all
        # original position/state updates, including for samples smaller than4.
        start=source.index(anchor);brace=source.index('{',start);depth=1;end=brace+1
        while depth:
            if source[end]=='{':depth+=1
            elif source[end]=='}':depth-=1
            end+=1
        original=source[start:end];body=source[brace+1:end-1]
        bound=before.removeprefix(f'        #if {gate}\n').removesuffix('        #endif\n\n')
        bound=bound.replace('float(samples) * 0.0002777777777777778','4.0 * 0.0002777777777777778')
        split=(f'        #if {gate}\n'
               '        for (int i = 0; i < (samples > 4 ? samples - 4 : 0); i++) {'+body+'}\n'
               +bound+'        for (int i = (samples > 4 ? samples - 4 : 0); i < samples; i++) {'+suffix+body+'}\n'
               '        #else\n'+original+'\n        #endif')
        source=source[:start]+split+source[end:]
    elif tail_steps:
        # Defer the two atlas reads to the final native iterations. A short
        # rectangle admits shallower legitimate rays while retaining their
        # original contributing prefix and every native position update.
        ceiling='        float shaderlodG08Ceiling = 1.0e30;\n'
        body=before.removeprefix(f'        #if {gate}\n').removesuffix('        #endif\n\n')
        body=once(body,ceiling,'').replace('float(samples) * 0.0002777777777777778','float(samples - i) * 0.0002777777777777778')
        outside=f'        #if {gate}\n'+ceiling+'        #endif\n'
        checkpoint=f'\n            #if {gate}\n            if (i == samples - {tail_steps}) {{\n'+body+'            }\n            #endif\n'
        source=once(source,anchor,outside+anchor+checkpoint+suffix)
    else:
        source=once(source,anchor,before+anchor+suffix)
    for op,axes,factor,decl in [('=','xz','HI','float '),('+=','zx','LOW','')]:
        uv=f'(intersection_pos.{axes} * 0.0002777777777777778) + (frameTimeCounter * CLOUD_{factor}_FACTOR)'
        old=f'{decl}current_value {op} texture2D(gaux2, {uv}).r;'
        new=f'#if {gate}\n            {decl}current_value {op} texture2DLod(gaux2, {uv}, 0.0).r;\n            #else\n            {old}\n            #endif'
        if not native_queries:
            if split_tail:
                if source.count(old)!=3:raise ValueError('Prefix/tail/native fallback query ancestry changed')
                source=source.replace(old,new)
            else:source=once(source,old,new)
    files[CLOUD]=source.encode()
    files[ATLAS]=png(bounds(decoded_cloud_rgba(native['shaders/textures/clouds_natural_512_R_8bit.png'])))
    files[ATLAS+'.mcmeta']=b'{"texture":{"blur":false,"clamp":true}}\n'
    p='shaders/shaders.properties'
    files[p]+=f'\n#if {OPTION} > 0\ntexture.deferred.noisetex = textures/shaderlod_g08_directional.png\n#endif\nscreen.SHADERLOD_G08_DIRECTIONAL = {OPTION} {DEBUG}\n'.encode()
    files[p]=once(files[p].decode(),'screen = ','screen = [SHADERLOD_G08_DIRECTIONAL] ').encode()
    return files

def build(original,options,output,tail_steps=0,native_queries=False,split_tail=False,masked_tail=False,zero_guard=False):
    original,options,output=map(Path,(original,options,output));raw=options.read_bytes()
    if any(p.exists() for p in [output,Path(str(output)+'.txt'),output.with_suffix('.json')]) or b'SHADERLOD_' in raw:raise ValueError('Fresh output/native options required')
    native=read_archive(original);files=patch(native,tail_steps,native_queries,split_tail,masked_tail,zero_guard)
    meta={'id':'G08','variant':'signed-4texel-bin-two-fetch-ray-ceiling','tail_steps':tail_steps,'native_queries':native_queries,'split_tail':split_tail,'masked_tail':masked_tail,'zero_guard':zero_guard,'native':NATIVE,'default_off':True,
          'scope':'Root Mac MC1.21.1/Iris1.8.12/MakeUp9.5g Overworld deferred, V_CLOUDS2/style0/speed0/native7..16samples, noDH/Voxy.',
          'work':'Two immutable nearest atlas reads per admitted ray; only proven zero-opacity suffix omitted. No CPU permission uniform, added pass or lean attachment.',
          'bound':'32x32 start tiles, eight signed travel bins per axis, 3texel apron and one byte margin; atlas256RGBA8.',
          'limits':'Require actual source/sampler proof. Native animation and quality unchanged. All guard/query/branch/startup costs count. No runtime result claimed.'}
    if zero_guard:meta.update(variant='native-nonpositive-density-ALU-guard',work='Retain all native texturequeries and positions; omit only geometry/thickness/opacity ALU when native normalized density<=0. No atlas/additionalqueries/pass/targets/history/Java.')
    files['SHADERLOD_G08_DIRECTIONAL_NOTICE.txt']=('Derivative of MakeUp9.5g, Javier Garduno, original LGPLv3/license retained.\n'+json.dumps(meta,indent=2)+'\n').encode()
    files['directional-source/build_makeup_directional_cloud_bounds.py']=Path(__file__).read_bytes()
    output.write_bytes(archive_bytes(files));Path(str(output)+'.txt').write_bytes(raw+f'\n{OPTION}=0\n{DEBUG}=0\n'.encode());meta['sha256']=sha(output.read_bytes());output.with_suffix('.json').write_text(json.dumps(meta,indent=2)+'\n');return meta

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--original',required=True,type=Path);p.add_argument('--options',required=True,type=Path);p.add_argument('--output',required=True,type=Path);p.add_argument('--tail-steps',type=int,default=0,choices=[0,4,8]);p.add_argument('--native-queries',action='store_true');p.add_argument('--split-tail',action='store_true');p.add_argument('--masked-tail',action='store_true');p.add_argument('--zero-guard',action='store_true')
    print(json.dumps(build(**vars(p.parse_args())),indent=2))

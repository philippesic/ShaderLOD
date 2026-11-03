#!/usr/bin/env python3
"""Pinned G04 ordinary translucent native-result fast path; private/default off.

Unlike the earlier sky-only guard, bypass all unused normal/wave/reflection
setup. Copy the exact native ordinary-material body and output suffix. Water,
glass, NaN/intermediate material classes and unsupported tuples stay native.
"""
import argparse,json
from pathlib import Path
from build_makeup_unused_sky import read_archive,archive_bytes,once,sha

OPTION='SHADERLOD_G04_TRANSLUCENT_FASTPATH'
CALLER='shaders/common/water_blocks_fragment.glsl'
CONFIG='shaders/lib/config.glsl'
PROPS='shaders/shaders.properties'
VERTEX='shaders/common/water_blocks_vertex.glsl'
CONDITION=f'{OPTION} == 1 && defined IS_IRIS && IRIS_VERSION == 10812 && MC_VERSION == 12101 && defined MC_OS_MAC && defined WATER_F && !defined UNKNOWN_DIM && !defined THE_END && !defined NETHER && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH && V_CLOUDS == 1 && CLOUD_VOL_STYLE == 0 && CLOUD_STEPS_AVG == 10 && AA_TYPE == 2'
# This pinned pack defines WATER_F in both water stages (including .vsh).
VERTEX_CONDITION=CONDITION

def vertex_patch(native):
    source=native[VERTEX].decode()
    classification='''    // Special entities
    blockType = 0.0;  // 3 - Water, 2 - Glass, ? - Other
    if(mc_Entity.x == ENTITY_WATER) {  // Water
        blockType = 3.0;
    } else if(mc_Entity.x == ENTITY_STAINED) {  // Glass
        blockType = 2.0;
    }'''
    source=once(source,classification,f'#if !({VERTEX_CONDITION})\n'+classification+'\n#endif')
    anchor='    #include "/src/position_vertex_water.glsl"'
    source=once(source,anchor,anchor+f'\n#if {VERTEX_CONDITION}\n'+classification+'\n#endif')
    # Ordinary fragment fast-return consumes none of these outputs. Keep the
    # ZenithSkyColorRGB/light/fog/shadow computations that it actually needs.
    groups=[('    #include "/src/low_sky.glsl"','    horizonSkyColor = vec3(0.0);'),
        ('''    waterNormal = normal;

    tangent = normalize(gl_NormalMatrix * at_tangent.xyz);
    binormal = normalize(gl_NormalMatrix * -cross(gl_Normal, at_tangent.xyz));''',
         '    waterNormal = vec3(0.0);\n    tangent = vec3(0.0);\n    binormal = vec3(0.0);'),
        ('        #include "/lib/volumetric_clouds_vertex.glsl"',
         '        umbral = 0.0;\n        cloudColor = vec3(0.0);\n        darkCloudColor = vec3(0.0);')]
    for original,zero in groups:
        source=once(source,original,f'#if {VERTEX_CONDITION}\n    if (blockType == 0.0) {{\n'+zero+'\n    } else {\n#endif\n'+original+f'\n#if {VERTEX_CONDITION}\n    }}\n#endif')
    return source.encode()

def native_parts(source):
    begin=source.index('    } else {  // Otros translúcidos\n')+len('    } else {  // Otros translúcidos\n')
    end=source.index('        if(blockType > 1.5) {  // Glass\n',begin)
    ordinary=source[begin:end]
    suffix=source[source.index('    #include "/src/finalcolor.glsl"'):source.rindex('\n}')]
    if any(x in ordinary+suffix for x in ['skyColorReflect','fresnel','surfaceNormal','waterNormalBase','reflectWaterVector']):
        raise ValueError('New native dependency; re-review required')
    return ordinary,suffix

def patch(native,cue=False,vertex_skip=False):
    files=dict(native);source=native[CALLER].decode();ordinary,suffix=native_parts(source)
    paint='    blockColor.rgb = vec3(0.0, 1.0, 0.0);\n' if cue else ''
    # Dither remains computed at the original point, before all shadow calls.
    # The native vertex sets exactly 0/2/3 per primitive; equality admits only0.
    early=f'''    #if {CONDITION}
    if (blockType == 0.0) {{
{ordinary}{suffix.replace('    #include "/src/writebuffers.glsl"',paint+'    #include "/src/writebuffers.glsl"')}
        return;
    }}
    #endif

'''
    anchor='    #ifdef VANILLA_WATER\n        vec3 waterNormalBase'
    files[CALLER]=once(source,anchor,early+anchor).encode()
    if cue:
        # Paint after native finalcolor, so the diagnostic cannot enter timing.
        tail=files[CALLER].decode();anchor='    #include "/src/writebuffers.glsl"'
        pos=tail.rindex(anchor);tail=tail[:pos]+f'    #if {CONDITION}\n    blockColor.rgb = vec3(1.0, 0.0, 1.0);\n    #endif\n'+tail[pos:];files[CALLER]=tail.encode()
    files[CONFIG]+=f'\n// Private G04 native-result early path; default OFF.\n#define {OPTION} 0 // [0 1]\n'.encode()
    files[PROPS]=once(native[PROPS].decode(),'screen = ','screen = [SHADERLOD_G04_FAST] ').encode()+f'\nscreen.SHADERLOD_G04_FAST = {OPTION}\n'.encode()
    if vertex_skip:files[VERTEX]=vertex_patch(native)
    return files

def build(original,options,output,cue=False,vertex_skip=False):
    native=read_archive(original);raw=Path(options).read_bytes()
    if b'SHADERLOD_' in raw:raise ValueError('Native-only options required')
    output=Path(output);paths=[output,Path(str(output)+'.txt'),output.with_suffix('.json')]
    if any(p.exists() for p in paths):raise ValueError('Fresh output required')
    files=patch(native,cue,vertex_skip);meta={'id':'G04','variant':'translucent-fastpath-vertexskip-r4' if vertex_skip else 'translucent-fastpath-r1','vertex_skip':vertex_skip,'native_sha256':sha(Path(original).read_bytes()),'option':OPTION,'cue':cue,'default_off':True,'changed_members':[n for n in native if files[n]!=native[n]],'scope':'Private root Mac MC1.21.1/Iris1.8.12 MakeUp9.5g world0 WATER_F/native100 AA2/static10 clouds; other dimensions/DH/Voxy/options native fallback.','mechanism':'Exact blockType0 native ordinary light/shadow/color/output then return before unused normal waves, surface-normal, reflection, sky/cloud setup; all native water/glass math untouched.','limits':'Driver may already eliminate unused expressions; primitive-class branching needs real source/quality/work/economics checks. No broad support, physical-fetch or speed claim.'}
    if vertex_skip:meta['additional_mechanism']='Classify native0/2/3 earlier in vertex stage; only0 omits unused lower-sky/tangent/binormal/waterNormal/cloudcolor outputs. Native upper-sky RGB, illumination/fog/shadow and positions stay unchanged; triangle-constant primitiveclass required.'
    files['SHADERLOD_G04_FAST_NOTICE.txt']=('Private derivative of MakeUp Ultra Fast9.5g by Javier Garduno. Original LGPLv3 license, attribution, native shader sources and quality/AA retained.\n'+json.dumps(meta,indent=2)+'\n').encode()
    output.parent.mkdir(parents=True,exist_ok=True);data=archive_bytes(files);output.write_bytes(data);paths[1].write_bytes(raw+(b'' if raw.endswith(b'\n') else b'\n')+f'{OPTION}=0\n'.encode());meta['sha256']=sha(data);paths[2].write_text(json.dumps(meta,indent=2)+'\n');return meta

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--original',required=True,type=Path);p.add_argument('--options',required=True,type=Path);p.add_argument('--output',required=True,type=Path);p.add_argument('--cue',action='store_true');p.add_argument('--vertex-skip',action='store_true');print(json.dumps(build(**vars(p.parse_args())),indent=2))

#!/usr/bin/env python3
"""Pinned, default-off G06 alternative: omit AO only when native attenuation is exactly one."""
import argparse
import json
from pathlib import Path
from build_makeup_unused_sky import read_archive, archive_bytes, once, sha, NATIVE

CONFIG='shaders/lib/config.glsl'
CALLER='shaders/common/deferred_fragment.glsl'
PROPS='shaders/shaders.properties'
VERTEX='shaders/common/deferred_vertex.glsl'
OPTION='SHADERLOD_G06_AO_ATTENUATION'
DEFINE=f'\n// Private exact AO attenuation skip. Native quality/AA/kernel retained.\n#define {OPTION} 0 // [0 1]\n'
CONDITION=f'{OPTION} == 1 && defined IS_IRIS && IRIS_VERSION == 10812 && MC_VERSION == 12101 && defined MC_OS_MAC && !defined NETHER && !defined THE_END && !defined UNKNOWN_DIM && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH && AO == 1 && AOSTEPS == 5 && AA_TYPE == 2'
NATIVE_CALL='        float final_ao = mix(dbao(dither), 1.0, ao_att);'
# Ordered comparisons reject nonfinite/degenerate camera uniforms. Depthtex0 is
# Iris's normalized depth attachment, hence all sampled depths are finite [0,1].
# These bounds keep ld denominators, offsets and the native sqrt finite in FP32.
PREDICATE='ao_att == 1.0 && near >= 0.01 && near <= 1.0 && far >= 16.0 && far <= 4096.0 && aspectRatioInverse > 0.0 && aspectRatioInverse <= 16.0 && fovYInverse > 0.0 && fovYInverse <= 64.0 && AO_STRENGTH >= 0.0 && AO_STRENGTH <= 1.0'
CALL=f'''        #if {CONDITION}
        bool shaderlodG06Skipped = {PREDICATE};
        float final_ao = 1.0;
        if (!shaderlodG06Skipped) final_ao = mix(dbao(dither), 1.0, ao_att);
        #else
{NATIVE_CALL}
        #endif'''
PAINT=f'''    #if {CONDITION}
    // Untimed branch coverage, not a hardware texture-fetch counter.
    blockColor.rgb = shaderlodG06Skipped ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 1.0);
    #endif
'''

NATIVE_ATTENUATION = """        float ao_att =
            pow(clamp(linearDepth * 1.6, 0.0, 1.0), mix(fogDensityCoeff, 1.0, rainStrength));

"""
# Conservative subset of native base==1: no round-down threshold widening.
# Ordered fog/rain comparisons reject NaN/inf and bound the native exponent.
PRE_POW_PREDICATE = 'linearDepth >= 0.625 && shaderlodG06FiniteCamera > 0.5 && fogDensityCoeff >= 0.0 && fogDensityCoeff <= 16.0 && rainStrength >= 0.0 && rainStrength <= 1.0'
NATIVE_ASSIGNMENT = NATIVE_CALL.replace('float final_ao =', 'final_ao =')
PRE_POW_CALL = f"""        #if {CONDITION}
        bool shaderlodG06Skipped = {PRE_POW_PREDICATE};
        float final_ao = 1.0;
        if (!shaderlodG06Skipped) {{
{NATIVE_ATTENUATION}{NATIVE_ASSIGNMENT}
        }}
        #else
{NATIVE_ATTENUATION}{NATIVE_CALL}
        #endif"""

PRE_POW_VERTEX_FOG_PREDICATE = 'linearDepth >= 0.625 && shaderlodG06FiniteCamera > 0.5'

def pre_pow_call(vertex_fog_guard=False):
    return PRE_POW_CALL.replace(PRE_POW_PREDICATE, PRE_POW_VERTEX_FOG_PREDICATE) if vertex_fog_guard else PRE_POW_CALL

def candidate_call(vertex_fog_guard=False,reuse_center=False,raw_center=False):
    call=pre_pow_call(vertex_fog_guard)
    if reuse_center:
        qualified,separator,fallback=call.partition('        #else\n')
        assert separator
        cached='shaderlodG06DbaoCached(dither, depth)' if raw_center else 'shaderlodG06DbaoCached(dither, depth, linearDepth)'
        call=once(qualified,NATIVE_ASSIGNMENT,NATIVE_ASSIGNMENT.replace('dbao(dither)',cached))+separator+fallback
    return call

def cached_center_kernel(native,raw_center=False):
    """Keep native tap arithmetic; caller already sampled the identical LOD0 UV."""
    source=native['shaders/lib/ao.glsl'].decode()
    if raw_center:
        # Preserve the AO-local ld arithmetic rather than sharing the caller's
        # linearization, which showed rare actual-GPU scalar differences.
        source=once(source,'float dbao(float dither) {','float shaderlodG06DbaoCached(float dither, float shaderlodCenterDepth) {')
        return once(source,'float linearDepth = texture2DLod(depthtex0, texcoord.xy, 0.0).r;','float linearDepth = shaderlodCenterDepth;')
    source=once(source,'float dbao(float dither) {','float shaderlodG06DbaoCached(float dither, float shaderlodCenterDepth, float linearDepth) {')
    source=once(source,'    float linearDepth = texture2DLod(depthtex0, texcoord.xy, 0.0).r;\n    float handCheck = linearDepth < 0.56 ? 1024.0 : 1.0;\n    linearDepth = ld(linearDepth);','    float handCheck = shaderlodCenterDepth < 0.56 ? 1024.0 : 1.0;')
    return source

def patch(native, cue=False, vertex_guard=False, pre_pow=False, vertex_fog_guard=False,reuse_center=False,raw_center=False):
    if raw_center and not reuse_center:raise ValueError('Raw center requires center reuse')
    if reuse_center and not pre_pow:raise ValueError('Center reuse requires pre-pow path')
    if vertex_fog_guard and not pre_pow:raise ValueError('Vertex fog guard requires pre-pow path')
    if pre_pow and not vertex_guard:raise ValueError('Pre-pow requires bounded finite-camera vertex guard')
    files=dict(native)
    files[CONFIG]+=DEFINE.encode()
    call=CALL
    if vertex_guard:
        finite=PREDICATE.removeprefix('ao_att == 1.0 && ')
        call=call.replace(PREDICATE,'ao_att == 1.0 && shaderlodG06FiniteCamera > 0.5')
        vertex=files[VERTEX].decode()
        declarations=f'\n#if {CONDITION}\nuniform float near;\nuniform float far;\nuniform float aspectRatioInverse;\nuniform float fovYInverse;\nvarying float shaderlodG06FiniteCamera;\n#endif\n'
        vertex=once(vertex,'uniform mat4 gbufferModelView;','uniform mat4 gbufferModelView;'+declarations)
        assignment=f'\n    #if {CONDITION}\n    shaderlodG06FiniteCamera = ({finite}) ? 1.0 : 0.0;\n    #endif\n'
        vertex=once(vertex,'    texcoord = gl_MultiTexCoord0.xy;','    texcoord = gl_MultiTexCoord0.xy;'+assignment)
        if vertex_fog_guard:
            # Both stages share the same linked rainStrength uniform. Native
            # fogDensityCoeff is a draw-constant varying assigned below; all
            # vertices agree and interpolation stays finite in the proved range.
            rain = '#if !((V_CLOUDS != 0 && !defined UNKNOWN_DIM) && !defined NO_CLOUDY_SKY)\nuniform float rainStrength;\n#endif\n'
            vertex = once(vertex, 'varying float shaderlodG06FiniteCamera;\n', 'varying float shaderlodG06FiniteCamera;\n'+rain)
            native_fog = """    #if AO == 1
        #if (VOL_LIGHT == 1 && !defined NETHER) || (VOL_LIGHT == 2 && defined SHADOW_CASTING && !defined NETHER)
            fogDensityCoeff = FOG_DENSITY * FOG_ADJUST;
        #else
            fogDensityCoeff = dayBlendFloat(FOG_SUNSET, FOG_DAY, FOG_NIGHT) * FOG_ADJUST;
        #endif
    #endif"""
            fog_guard = f"""
    #if {CONDITION}
    shaderlodG06FiniteCamera = (shaderlodG06FiniteCamera > 0.5 &&
        fogDensityCoeff >= 0.0 && fogDensityCoeff <= 16.0 &&
        rainStrength >= 0.0 && rainStrength <= 1.0) ? 1.0 : 0.0;
    #endif"""
            vertex = once(vertex, native_fog, native_fog+fog_guard)
        files[VERTEX]=vertex.encode()
    source=once(files[CALLER].decode(),NATIVE_ATTENUATION+NATIVE_CALL,candidate_call(vertex_fog_guard,reuse_center,raw_center)) if pre_pow else once(files[CALLER].decode(),NATIVE_CALL,call)
    if reuse_center:
        # Only the certified On branch calls the cached kernel. Off/mismatch
        # retains the complete original kernel/caller, including its center read.
        kernel=native['shaders/lib/ao.glsl'].decode()
        files['shaders/lib/ao.glsl']=(kernel+'\n#if '+CONDITION+'\n'+cached_center_kernel(native,raw_center)+'\n#endif\n').encode()
    if vertex_guard:source=once(source,'varying vec2 texcoord;',f'varying vec2 texcoord;\n#if {CONDITION}\nvarying float shaderlodG06FiniteCamera;\n#endif')
    if cue:source=once(source,'    /* DRAWBUFFERS:14 */\n',PAINT+'    /* DRAWBUFFERS:14 */\n')
    files[CALLER]=source.encode()
    files[PROPS]=once(files[PROPS].decode(),'screen.AO_SCREEN = AOSTEPS AO_STRENGTH',f'screen.AO_SCREEN = AOSTEPS AO_STRENGTH {OPTION}').encode()
    return files

def build(original,options,output,cue=False,vertex_guard=False,pre_pow=False,vertex_fog_guard=False,reuse_center=False,raw_center=False):
    native=read_archive(original);files=patch(native,cue,vertex_guard,pre_pow,vertex_fog_guard,reuse_center,raw_center);original_options=Path(options).read_bytes()
    if b'SHADERLOD_' in original_options or b'G06_AO_SPATIAL' in original_options:raise ValueError('Native options only')
    output=Path(output);paths=[output,Path(str(output)+'.txt'),output.with_suffix('.json')]
    if any(p.exists() for p in paths):raise ValueError('Fresh output paths required')
    changed=sorted(n for n in native if files[n]!=native[n]);assert changed==sorted([CONFIG,CALLER,PROPS]+([VERTEX] if vertex_guard else [])+(['shaders/lib/ao.glsl'] if reuse_center else []))
    metadata={'id':'G06','variant':'exact-attenuation-prepow-vertexfog-r4' if vertex_fog_guard else ('exact-attenuation-prepow-r3' if pre_pow else ('exact-attenuation-vertexguard-r2' if vertex_guard else 'exact-attenuation-fragmentguard-r1')),'vertex_fog_guard':vertex_fog_guard,'pre_pow':pre_pow,'vertex_guard':vertex_guard,'base_sha256':NATIVE,'default_off':True,'cue':cue,'option':OPTION,'changed_members':changed,'preimage_hashes':{n:sha(native[n]) for n in changed},'source_hashes':{n:sha(files[n]) for n in changed},'scope':'MakeUp9.5g native100 AA2/AO1/5steps root Mac MC1.21.1 Iris1.8.12 Overworld; no DH/Voxy. Original pass graph/kernel/dither/depth/quality unchanged.','mechanism':'At exact native ao_att==1 with bounded finite camera uniforms and normalized depth, native dbao is finite and mix(dbao,1,1)==1; omit entire unconsumed AO call. Uniform guards and divergent/helper execution still cost.','additional_pre_pow_mechanism':'For finite bounded camera/fog/rain and linearDepth>=0.625 native clamp base is exactly1; omit native pow and AO. Only a conservative subset of old ao_att==1 coverage.' if pre_pow else None,'physical_work_limit':'Branch cue/source control flow, not direct physical texture-fetch count; driver may already optimize native work. Runtime untested.'}
    metadata['reuse_center']=reuse_center
    metadata['raw_center']=raw_center
    if reuse_center:
        metadata['variant']='exact-attenuation-prepow-centerreuse-r5'
        metadata['center_reuse_mechanism']='Pass caller identical LOD0 depth and ld(depth) to native AO tap kernel; omit duplicate center sample/linearization on non-skipped pixels. Compiler may already CSE it; no hardware saving claim.'
        if raw_center:
            metadata['variant']='exact-attenuation-prepow-rawcenter-r11'
            metadata['center_reuse_mechanism']='Share only caller rawdepth query, preserve AO-local native ld evaluation/order. Compiler may already CSE center sample; no hardware saving claim.'
    files['SHADERLOD_G06_EXACT_NOTICE.txt']=('Private MakeUp Ultra Fast9.5g derivative. Native LGPL-3.0-or-later licenses/attribution retained.\nAdditions LGPL-3.0-or-later. No spatial/temporal approximation, native AO/AA unchanged.\nBuilder: scripts/build_makeup_ao_attenuation.py. Restore original archive/options.\n'+json.dumps(metadata,indent=2)+'\n').encode()
    raw=archive_bytes(files);output.parent.mkdir(parents=True,exist_ok=True);output.write_bytes(raw);paths[1].write_bytes(original_options+(b'' if original_options.endswith(b'\n') else b'\n')+f'{OPTION}=0\n'.encode());metadata.update(artifact=str(output.resolve()),sha256=sha(raw),native_sidecar_sha256=sha(original_options));paths[2].write_text(json.dumps(metadata,indent=2)+'\n');return metadata

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--original',required=True,type=Path);p.add_argument('--options',required=True,type=Path);p.add_argument('--output',required=True,type=Path);p.add_argument('--cue',action='store_true');p.add_argument('--vertex-guard',action='store_true');p.add_argument('--pre-pow',action='store_true');p.add_argument('--vertex-fog-guard',action='store_true');p.add_argument('--reuse-center',action='store_true');p.add_argument('--raw-center',action='store_true');print(json.dumps(build(**vars(p.parse_args())),indent=2))

#!/usr/bin/env python3
"""Untimed same-fragment native AO scalar witnesses for the pinned exact pilots.

All native reference queries execute. RGB uses the reference. RT8 is diagnostic
only; this archive cannot establish performance or hardware texture savings.
"""
import argparse
import json
from pathlib import Path

import build_makeup_ao_attenuation as attenuation
import build_makeup_ao_tap_skip as tap
from build_makeup_unused_sky import NATIVE, archive_bytes, once, read_archive, sha


def patch(native, kind, query_only=False,direct_first=False,error_bins=False,native_control=False,vertex_fog_guard=False,reuse_center=False,raw_center=False):
    candidate_block=None
    if raw_center and not reuse_center:raise ValueError('Raw center requires center reuse')
    if reuse_center and kind!='G06-prepow':raise ValueError('Center reuse requires G06-prepow')
    if vertex_fog_guard and kind!='G06-prepow':raise ValueError('Vertex fog guard requires G06-prepow')
    if query_only and not kind.startswith('G07-'):
        raise ValueError('Query-only witness requires G07')
    if kind in ('G06-fragment', 'G06-vertex', 'G06-prepow'):
        vertex = kind in ('G06-vertex', 'G06-prepow')
        pre_pow = kind == 'G06-prepow'
        files = attenuation.patch(native, vertex_guard=vertex, pre_pow=pre_pow, vertex_fog_guard=vertex_fog_guard,reuse_center=reuse_center,raw_center=raw_center)
        guard, option = attenuation.CONDITION, attenuation.OPTION
        predicate = attenuation.PREDICATE
        old_call = attenuation.CALL
        if vertex:
            predicate = 'ao_att == 1.0 && shaderlodG06FiniteCamera > 0.5'
            old_call = old_call.replace(attenuation.PREDICATE, predicate)
        if pre_pow:
            predicate = attenuation.PRE_POW_VERTEX_FOG_PREDICATE if vertex_fog_guard else attenuation.PRE_POW_PREDICATE
            old_call = attenuation.candidate_call(vertex_fog_guard,reuse_center,raw_center)
            # Replay the actual lean candidate control flow, including its
            # output assignment. An algebraic stand-in cannot catch scope bugs.
            qualified=old_call.partition('        #else\n')[0]
            candidate_block=qualified.split('\n',1)[1].replace('final_ao','shaderlodAoCandidate')
        selected = f'({predicate}) ? 1.0 : shaderlodAoReference'
        reference = 'mix(dbao(dither), 1.0, ao_att)'
        omitted = f'({predicate}) ? 11.0 : 0.0'
        if reuse_center:
            # Diagnostic keeps native AO/RGB and independently evaluates the
            # cached-center candidate. The lean artifact omits the native call.
            selected=f'({predicate}) ? 1.0 : mix(shaderlodG06DbaoCached(dither, depth, linearDepth), 1.0, ao_att)'
            omitted=f'({predicate}) ? 11.0 : 1.0'
    elif kind in ('G07-positive', 'G07-negative'):
        negative = kind == 'G07-negative'
        files = tap.patch(native, negative_first=negative,query_only=query_only,direct_first=direct_first)
        guard, option = tap.CONDITION, tap.OPTION
        candidate = tap.kernel(native, cue=True, negative_first=negative,query_only=query_only,direct_first=direct_first).decode()
        if native_control:candidate='float shaderlodG07Skipped = 0.0;\n'+native['shaders/lib/ao.glsl'].decode()
        candidate = once(candidate, 'float dbao(', 'float shaderlodAoCandidateKernel(')
        reference_kernel = once(native['shaders/lib/ao.glsl'].decode(),
                                'float dbao(', 'float shaderlodAoReferenceKernel(')
        files[tap.KERNEL] = (candidate + '\n' + reference_kernel).encode()
        old_call = attenuation.NATIVE_CALL
        selected = 'mix(shaderlodAoCandidateKernel(dither), 1.0, ao_att)'
        reference = 'mix(shaderlodAoReferenceKernel(dither), 1.0, ao_att)'
        omitted = 'shaderlodG07Skipped'
    else:
        raise ValueError('Unsupported pinned AO witness')
    reference_attenuation = attenuation.NATIVE_ATTENUATION if kind == 'G06-prepow' else ''
    candidate_statement=candidate_block if candidate_block is not None else f'        float shaderlodAoCandidate = {selected};'
    new_call = f'''{reference_attenuation}        #if {guard}
        float shaderlodAoReference = {reference};
{candidate_statement}
        float shaderlodAoOmitted = {omitted};
        // Unordered/NaN scalars fail equality too. RGB stays native reference.
        float shaderlodAoMismatch = shaderlodAoReference == shaderlodAoCandidate ? 0.0 : 1.0;
        float final_ao = shaderlodAoReference;
        #else
{attenuation.NATIVE_CALL}
        #endif'''
    caller = 'shaders/common/deferred_fragment.glsl'
    text = once(files[caller].decode(), old_call, new_call)
    text = once(text, '#include "/lib/config.glsl"', '#include "/lib/config.glsl"\n'+f'''#if {guard}
/*
const int colortex8Format = R16F;
const bool colortex8Clear = true;
const vec4 colortex8ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
*/
#endif
''')
    text = once(text, '/* DRAWBUFFERS:14 */', f'''#if {guard}
    /* DRAWBUFFERS:148 */
    #else
    /* DRAWBUFFERS:14 */
    #endif''')
    text = once(text, '    gl_FragData[1] = blockColor;',
                '    gl_FragData[1] = blockColor;\n'+f'''    #if {guard}
    gl_FragData[2] = vec4(shaderlodAoMismatch > 0.0 ? 257.0 : 1.0 + shaderlodAoOmitted);
    #endif''')
    if error_bins:
        old='gl_FragData[2] = vec4(shaderlodAoMismatch > 0.0 ? 257.0 : 1.0 + shaderlodAoOmitted);'
        new='float shaderlodAoError = abs(shaderlodAoReference - shaderlodAoCandidate);\n    float shaderlodAoCode = shaderlodAoReference == shaderlodAoCandidate ? 1.0 :\n        (shaderlodAoError <= 0.0000001 ? 2.0 : (shaderlodAoError <= 0.000001 ? 3.0 :\n        (shaderlodAoError <= 0.00001 ? 4.0 : (shaderlodAoError <= 0.0001 ? 5.0 :\n        (shaderlodAoError <= 0.001 ? 6.0 : (shaderlodAoError <= 0.01 ? 7.0 : 8.0))))));\n    gl_FragData[2] = vec4(1.0 + 16.0 * (shaderlodAoCode - 1.0) + shaderlodAoOmitted);'
        text=once(text,old,new)
    files[caller] = text.encode()
    files['shaders/shaders.properties'] += b'\nblend.deferred.colortex8=off\n'
    return files, option


def build(original, options, output, kind, query_only=False,direct_first=False,error_bins=False,native_control=False,vertex_fog_guard=False,reuse_center=False,raw_center=False):
    original, options, output = Path(original), Path(options), Path(output)
    if sha(original.read_bytes()) != NATIVE:
        raise ValueError('Exact pinned native MakeUp9.5g archive required')
    if any(p.exists() for p in (output, Path(str(output)+'.txt'))):
        raise ValueError('Fresh witness output required')
    files, option = patch(read_archive(original),kind,query_only,direct_first,error_bins,native_control,vertex_fog_guard,reuse_center,raw_center)
    sidecar = options.read_bytes()
    if b'SHADERLOD_' in sidecar:
        raise ValueError('Untouched native sidecar required')
    marker = {'diagnostic_only': True, 'native': NATIVE, 'kind': kind,
              'encoding_mode': 'predicate-bits', 'query_only':query_only,'direct_first':direct_first,'error_bins':error_bins,'native_control':native_control,'vertex_fog_guard':vertex_fog_guard,'reuse_center':reuse_center,'raw_center':raw_center,
              'encoding': '0 cleared; 257 any AO-scalar mismatch, otherwise 1+omitted-source-query-count. Native reference always executes. G06 count11 includes a center-depth expression that a compiler may CSE against the caller; G07 count is omitted second-tap expressions.',
              'limits': 'Same-fragment scalar equality only, not full-image parity, hardware fetches, bandwidth or speed. Reference RGB and extra RT8/readback excluded from lean timing.',
              'default_off': True, 'option': option}
    if error_bins:marker['encoding']='0 clear; positive code=1+16*errorBin+omittedSourceQueries (low4bits); errorBin0 exact,1 abs<=1e-7,2<=1e-6,3<=1e-5,4<=1e-4,5<=1e-3,6<=1e-2,7 larger or unordered. Reference RGB/all reference queries retained. Candidate omitted expressions are not hardware fetch counts or lean timing.'
    files['SHADERLOD_FRAGMENT_WORK_DIAGNOSTIC.json'] = (json.dumps(marker, indent=2)+'\n').encode()
    files['ao-witness-source/build_makeup_ao_parity.py'] = Path(__file__).read_bytes()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(archive_bytes(files))
    Path(str(output)+'.txt').write_bytes(sidecar+(b'' if sidecar.endswith(b'\n') else b'\n')+f'{option}=0\n'.encode())
    return dict(marker, artifact=str(output), sha256=sha(output.read_bytes()))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--original', type=Path, required=True)
    parser.add_argument('--options', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--kind', choices=['G06-fragment','G06-vertex','G06-prepow','G07-positive','G07-negative'], required=True)
    parser.add_argument('--query-only',action='store_true')
    parser.add_argument('--direct-first',action='store_true')
    parser.add_argument('--error-bins',action='store_true')
    parser.add_argument('--native-control',action='store_true')
    parser.add_argument('--vertex-fog-guard',action='store_true')
    parser.add_argument('--reuse-center',action='store_true')
    parser.add_argument('--raw-center',action='store_true')
    print(json.dumps(build(**vars(parser.parse_args())), indent=2))

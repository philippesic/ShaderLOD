#!/usr/bin/env python3
"""Pinned default-off G07 exact alternative: skip a saturated native AO pair's second tap."""
import argparse
import json
from pathlib import Path
from build_makeup_unused_sky import read_archive, archive_bytes, once, sha, NATIVE

CONFIG='shaders/lib/config.glsl';CALLER='shaders/common/deferred_fragment.glsl';PROPS='shaders/shaders.properties';KERNEL='shaders/lib/g07/ao_tap_skip.glsl'
OPTION='SHADERLOD_G07_AO_TAP_SKIP'
DEFINE=f'\n// Private exact saturated AO pair skip; native quality and AA retained.\n#define {OPTION} 0 // [0 1]\n'
CONDITION=f'{OPTION} == 1 && defined IS_IRIS && IRIS_VERSION == 10812 && MC_VERSION == 12101 && defined MC_OS_MAC && !defined NETHER && !defined THE_END && !defined UNKNOWN_DIM && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH && AO == 1 && AOSTEPS == 5 && AA_TYPE == 2'
SECOND='''        sampleLinearDepth = ld(texture2DLod(depthtex0, texcoord.xy - offset, 0.0).r);
        checkDepth = (linearDepth - sampleLinearDepth) * farAndCheck;
        angle += clamp(0.5 - checkDepth, 0.0, 1.0);
        dist += clamp(0.25 * checkDepth - 1.0, 0.0, 1.0);'''
INCLUDE='    #include "/lib/ao.glsl"'
SELECT=f'''    #if {CONDITION}
    #include "/lib/g07/ao_tap_skip.glsl"
    #else
{INCLUDE}
    #endif'''
PAINT=f'''    #if {CONDITION}
    // Untimed any-skipped-second-tap coverage; not a hardware fetch counter.
    blockColor.rgb = shaderlodG07Skipped > 0.0 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 1.0);
    #endif
'''

def kernel(native,cue=False,negative_first=False,query_only=False,direct_first=False):
    if direct_first and (negative_first or query_only):raise ValueError('Direct conservative positive-first is an independent variant')
    source=native['shaders/lib/ao.glsl'].decode()
    guarded='''        // Both native directional terms are nonnegative. Once the first
        // saturates their final clamp, a second depth sample cannot change it.
        // Negated >= retains the original path for unordered first values.
        if (!(angle + dist >= 1.0)) {
'''+SECOND+'\n        }'+ (' else { shaderlodG07Skipped += 1.0; }' if cue else '')
    if query_only:
        query,alu=SECOND.split('\n',1)
        guarded='''        // Keep native second-direction ALU/accumulation layout. Only its
        // depth query is conditional. A saturated first sum permits center
        // depth as a witness, whose nonnegative contribution cannot unsaturate.
        if (!(angle + dist >= 1.0)) {
'''+query+'\n        } else { sampleLinearDepth = linearDepth;'+(' shaderlodG07Skipped += 1.0;' if cue else '')+' }\n'+alu
    if direct_first:
        anchor='        angle = clamp(0.5 - checkDepth, 0.0, 1.0);'
        certificate='        // A margin beyond native clamp saturation avoids the first two\n        // clamps, second query and remaining ALU. Unordered values retain\n        // native evaluation; later iterations overwrite angle/dist.\n        if (checkDepth < -1.0) {\n            ambientOcclusion += 1.0;\n'+('            shaderlodG07Skipped += 1.0;\n' if cue else '')+'            continue;\n        }\n'
        source=once(source,anchor,certificate+anchor)
    else:
        source=once(source,SECOND,guarded)
    if negative_first:
        # Both native sums have exactly two finite nonnegative operands.
        # Swap query order and reuse the same native additions/clamp.
        assert source.count('texcoord.xy + offset')==source.count('texcoord.xy - offset')==1
        source=source.replace('texcoord.xy + offset','texcoord.xy SHADERLOD_G07_SWAP offset').replace('texcoord.xy - offset','texcoord.xy + offset').replace('texcoord.xy SHADERLOD_G07_SWAP offset','texcoord.xy - offset')
    if cue:source='float shaderlodG07Skipped = 0.0;\n'+source
    return source.encode()

def patch(native,cue=False,negative_first=False,query_only=False,direct_first=False):
    files=dict(native);files[CONFIG]+=DEFINE.encode();files[KERNEL]=kernel(native,cue,negative_first,query_only,direct_first)
    source=once(files[CALLER].decode(),INCLUDE,SELECT)
    if cue:source=once(source,'    /* DRAWBUFFERS:14 */\n',PAINT+'    /* DRAWBUFFERS:14 */\n')
    files[CALLER]=source.encode();files[PROPS]=once(files[PROPS].decode(),'screen.AO_SCREEN = AOSTEPS AO_STRENGTH',f'screen.AO_SCREEN = AOSTEPS AO_STRENGTH {OPTION}').encode();return files

def build(original,options,output,cue=False,negative_first=False,query_only=False,direct_first=False):
    native=read_archive(original);files=patch(native,cue,negative_first,query_only,direct_first);side=Path(options).read_bytes()
    if b'SHADERLOD_' in side or b'G07_' in side:raise ValueError('Native options only')
    output=Path(output);paths=[output,Path(str(output)+'.txt'),output.with_suffix('.json')]
    if any(p.exists() for p in paths):raise ValueError('Fresh output paths required')
    changed=sorted(n for n in native if files[n]!=native[n]);assert changed==sorted([CONFIG,CALLER,PROPS])
    metadata={'id':'G07','variant':'conservative-direct-first-r4' if direct_first else ('exact-query-only-negative-r3' if negative_first else 'exact-query-only-positive-r3') if query_only else ('exact-pair-negative-first-r2' if negative_first else 'exact-pair-saturation-r1'),'query_only':query_only,'direct_first':direct_first,'negative_first':negative_first,'base_sha256':NATIVE,'default_off':True,'cue':cue,'option':OPTION,'changed_members':changed,'added_members':[KERNEL],'preimage_hashes':{n:sha(native[n]) for n in changed},'source_hashes':{n:sha(files[n]) for n in changed+[KERNEL]},'scope':'MakeUp9.5g native100 AA2/AO1/5steps root Mac MC1.21.1 Iris1.8.12 Overworld; DH/Voxy native fallback. No temporal thinning/spatial approximation/new passes/targets/history/Java runtime.','mechanism':('Conservative positive first checkDepth<-1 saturates the native pair; omit first two clamps, second explicit LOD query and remaining pair ALU, accumulate1 and continue. Native fallback/tap order retained.' if direct_first else ('Saturated first pair permits center-depth witness; omit only second depth query, retain native second ALU/accumulation.' if query_only else 'First direction angle+dist>=1 saturates native clamp regardless of nonnegative second direction; omit second explicit-LOD depth fetch and its ALU only then. Original native summation and every remaining tap retained.')),'limits':'Cue proves branch activity, not physical speculative fetch removal. No runtime quality/speed result yet; branch and divergence costs count.'}
    files['SHADERLOD_G07_EXACT_NOTICE.txt']=('Private MakeUp Ultra Fast9.5g derivative. Native LGPL-3.0-or-later licenses/attribution retained.\nAdditions LGPL-3.0-or-later; builder scripts/build_makeup_ao_tap_skip.py. Native quality/AA retained.\n'+json.dumps(metadata,indent=2)+'\n').encode();raw=archive_bytes(files);output.parent.mkdir(parents=True,exist_ok=True);output.write_bytes(raw);paths[1].write_bytes(side+(b'' if side.endswith(b'\n') else b'\n')+f'{OPTION}=0\n'.encode());metadata.update(artifact=str(output.resolve()),sha256=sha(raw),native_sidecar_sha256=sha(side));paths[2].write_text(json.dumps(metadata,indent=2)+'\n');return metadata

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--original',required=True,type=Path);p.add_argument('--options',required=True,type=Path);p.add_argument('--output',required=True,type=Path);p.add_argument('--cue',action='store_true');p.add_argument('--negative-first',action='store_true');p.add_argument('--query-only',action='store_true');p.add_argument('--direct-first',action='store_true');print(json.dumps(build(**vars(p.parse_args())),indent=2))

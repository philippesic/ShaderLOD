#!/usr/bin/env python3
"""Wrap private MakeUp G05 variants with an untimed integer work attachment.

RT8 is cleared every frame and never blended. The readback counts the last
visible fragment, not hidden overdraw or hardware texture transactions.
Never use this archive for timing or quality comparisons.
"""
import argparse, json, zipfile
from pathlib import Path
from build_makeup_unused_sky import archive_bytes, once, sha
from build_makeup_glass_miss import GUARD as GLASS_GUARD
from build_makeup_projected_miss import NATIVE_SHA256

def build(source, output, kind, predicates=False):
    source, output = Path(source), Path(output)
    if output.exists() or Path(str(output)+'.txt').exists():
        raise ValueError('Fresh diagnostic output required')
    with zipfile.ZipFile(source) as archive:
        if len(archive.namelist()) != len(set(archive.namelist())) or sum(e.file_size for e in archive.infolist()) > 32*1024*1024:
            raise ValueError('Bounded unambiguous private archive required')
        files = {name: archive.read(name) for name in archive.namelist()}
    notice = files.get('SHADERLOD_G05_GLASS_NOTICE.txt', files.get('G05-build.json', b''))
    if NATIVE_SHA256.encode() not in notice:
        raise ValueError('Pinned native ancestry absent')
    if kind == 'G05-glass':
        guard, debug, value, eligible = GLASS_GUARD, 'SHADERLOD_G05_GLASS_DEBUG', 'shaderlodG05GlassSavedSteps', 'blockType > 1.5 && blockType <= 2.5'
    elif kind == 'G05-water':
        helper = files['shaders/lib/shaderlod/g05_projected_miss.glsl'].decode()
        guard = next(line[4:] for line in helper.splitlines() if line.startswith('#if '))
        debug, value, eligible = 'SHADERLOD_G05_DEBUG', 'shaderlodG05SavedSteps', 'blockType > 2.5'
    elif kind == 'G05-reuse':
        from build_makeup_depth_reuse import GUARD as guard
        debug,value,eligible='SHADERLOD_G05_REUSE_DEBUG','shaderlodG05ReuseSavedQueries','blockType > 1.5'
    else:
        raise ValueError('Only pinned G05 diagnostics supported')
    if predicates:
        if kind=='G05-reuse':raise ValueError('Separate reuse parity archive supplies its encoding')
        helper='shaders/lib/shaderlod/'+('g05_glass_miss.glsl' if kind=='G05-glass' else 'g05_projected_miss.glsl')
        projection='shaderlodG05GlassProjection' if kind=='G05-glass' else 'shaderlodG05Projection'
        h=files[helper].decode()
        h=f'float workProjection=0.0,workDirection=0.0,workOut=0.0,workSearch=0.0;\n'+h
        h=once(h,'    // Ray marching',f'    workProjection=float({projection}());\n    workDirection=float(direction.z <= 0.0);\n    // Ray marching')
        anchor='    for (int i = 0; i < RAYMARCH_STEPS; i++) {\n'
        h=once(h,anchor,anchor+'        workOut=max(workOut,float(outOfEyeFlag));\n        workSearch=max(workSearch,float(searchFlag));\n')
        files[helper]=h.encode();value='(workProjection + 2.0*workDirection + 4.0*workOut + 8.0*workSearch)'
    condition = f'({guard}) && {debug} == 1'
    name = 'shaders/common/water_blocks_fragment.glsl'
    text = files[name].decode()
    text = once(text, '#include "/lib/config.glsl"', '#include "/lib/config.glsl"\n'+f'''#if {condition}
/*
const int colortex8Format = R16F;
const bool colortex8Clear = true;
const vec4 colortex8ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
*/
#endif
''')
    text = once(text, '    #include "/src/writebuffers.glsl"', f'''    #if {condition}
    // Same native primary write. Independent unblended integer work image.
    blockColor = clamp(blockColor, vec4(0.0), vec4(vec3(50.0), 1.0));
    /* DRAWBUFFERS:18 */
    gl_FragData[0] = blockColor;
    gl_FragData[1] = vec4(({eligible}) ? 1.0 + {value} : 0.0);
    #else
    #include "/src/writebuffers.glsl"
    #endif''')
    files[name] = text.encode()
    files['shaders/shaders.properties'] += b'\n# Untimed integer work witness only.\nblend.gbuffers_water.colortex8=off\n'
    marker = {'diagnostic_only': True, 'native': NATIVE_SHA256, 'kind': kind,
              'source_sha256': sha(source.read_bytes()), 'encoding': '0 background,1 eligible,1+omitted native depth queries',
              'limits': 'Last visible fragment per pixel; not overdraw/hardware counts, quality or timing evidence.'}
    if predicates:
        marker.update(encoding_mode='predicate-bits',encoding='0 background,1+bits for target class:1 projection admitted,2 directionz<=0,4 outOfEye at a later loop entry,8 searchFlag at a later loop entry. Sticky witnesses; no omission count.')
    if kind=='G05-reuse' and json.loads(files['G05-build.json']).get('parity_diagnostic'):
        marker.update(encoding_mode='predicate-bits',encoding='0 background,1 both trace endpoint vec3 and infinite scalar exactly equal within the same native-input fragment,257 any unequal/NaN component. Native loop reference vs reuse loop; not a full image or speed claim.')
    files['SHADERLOD_FRAGMENT_WORK_DIAGNOSTIC.json'] = (json.dumps(marker,indent=2)+'\n').encode()
    files['fragment-work-source/build_makeup_fragment_work.py'] = Path(__file__).read_bytes()
    output.write_bytes(archive_bytes(files))
    Path(str(output)+'.txt').write_bytes(Path(str(source)+'.txt').read_bytes())
    return {**marker, 'artifact': str(output), 'sha256': sha(output.read_bytes())}

if __name__ == '__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source',required=True,type=Path);p.add_argument('--output',required=True,type=Path)
    p.add_argument('--kind',required=True,choices=['G05-glass','G05-water','G05-reuse'])
    p.add_argument('--predicates',action='store_true')
    print(json.dumps(build(**vars(p.parse_args())),indent=2))

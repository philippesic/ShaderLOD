#!/usr/bin/env python3
"""Build the pinned, default-off G06 AO pilot; never installs or runs a client."""
import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASE_SHA = "0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8"
CONFIG = 'shaders/lib/config.glsl'
CALLER = 'shaders/common/deferred_fragment.glsl'
PROPS = 'shaders/shaders.properties'
OPTION = '#define G06_AO_SPATIAL 0 // [0 1 2 3] Off / producer cost control / selective AO / untimed coverage\n'
# Properties preprocessing receives the entire option map, including MakeUp's
# NETHER/THE_END/UNKNOWN_DIM boolean defines from other dimension entry points.
# Those are NOT the current dimension here. The program key itself names world0;
# dimension-local admission remains unchanged in the actual producer/consumer.
PROPERTY_GUARD = '#if G06_AO_SPATIAL > 0 && G06_AO_SPATIAL <= 3 && AO == 1 && AOSTEPS >= 5 && defined IS_IRIS && IRIS_VERSION == 10812 && MC_VERSION == 12101 && defined MC_OS_MAC && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH'
INCLUDE = '#include "/lib/g06/reconstruct.glsl"\n'
NATIVE_CALL = '        float final_ao = mix(dbao(dither), 1.0, ao_att);'
CALL = '''        #ifdef G06_AO_ACTIVE
        float final_ao = mix(g06Ao(depth, dither), 1.0, ao_att);
        #else
''' + NATIVE_CALL + '''
        #endif'''
PAINT = '''    #if defined G06_AO_ACTIVE && G06_AO_SPATIAL == 3
    // Untimed semantic coverage only, after native AO and color processing.
    blockColor.rgb = g06Decision == 2.0 ? vec3(0.0, 1.0, 0.0) :
        (g06Decision == 1.0 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 0.0, 1.0));
    #endif
'''


def sha(data):
    return hashlib.sha256(data).hexdigest()


def read(base):
    if sha(Path(base).read_bytes()) != BASE_SHA:
        raise ValueError('Exact pinned native MakeUp Ultra Fast 9.5g required')
    with zipfile.ZipFile(base) as z:
        names = z.namelist()
        if len(names) != len(set(names)) or any(PurePosixPath(n).is_absolute() or
                '..' in PurePosixPath(n).parts or '\\' in n for n in names):
            raise ValueError('Unsafe archive')
        return {n: z.read(n) for n in names}


def once(data, before, after):
    if data.count(before.encode()) != 1:
        raise ValueError('Unexpected source anchor')
    return data.replace(before.encode(), after.encode(), 1)


def patch(native):
    files = dict(native)
    files[CONFIG] = OPTION.encode() + files[CONFIG]
    files[CALLER] = once(files[CALLER], '    #include "/lib/ao.glsl"\n',
                         '    #include "/lib/ao.glsl"\n' + INCLUDE)
    files[CALLER] = once(files[CALLER], NATIVE_CALL, CALL)
    files[CALLER] = once(files[CALLER], '    /* DRAWBUFFERS:14 */\n', PAINT + '    /* DRAWBUFFERS:14 */\n')
    files[PROPS] = once(files[PROPS], 'screen.AO_SCREEN = AOSTEPS AO_STRENGTH',
                        'screen.AO_SCREEN = AOSTEPS AO_STRENGTH G06_AO_SPATIAL')
    # The exact same entry and common code run at deferred1. Only world0 is moved.
    for suffix in ('fsh', 'vsh'):
        name = 'shaders/world0/deferred.' + suffix
        moved = 'shaders/world0/deferred1.' + suffix
        if moved in files:
            raise ValueError('Native deferred1 unexpectedly occupied')
        files[moved] = native[name]
    files['shaders/world0/deferred.fsh'] = b'''#version 120
#extension GL_ARB_shader_texture_lod : enable
// ShaderLOD G06 native AO producer, LGPL-3.0-or-later.
#include "/lib/g06/producer.glsl"
'''
    files['shaders/world0/deferred.vsh'] = b'''#version 120
// ShaderLOD G06 fullscreen producer, LGPL-3.0-or-later.
void main() { gl_Position = ftransform(); }
'''
    files[PROPS] += ('\n# ShaderLOD G06 r2: world0 program key supplies dimension scope.\n' + PROPERTY_GUARD + '''
program.world0/deferred.enabled=true
size.buffer.colortex8=0.5 0.5
#else
program.world0/deferred.enabled=false
#endif
''').encode()
    for p in sorted((ROOT / 'shaderpacks/makeup-g06').rglob('*')):
        if p.is_file():
            n = p.relative_to(ROOT / 'shaderpacks/makeup-g06').as_posix()
            if n in files:
                raise ValueError('Overlay collision')
            files[n] = p.read_bytes()
    files['LICENSE.GPL-3.0'] = (ROOT / 'patches/makeup/LICENSE.GPL-3.0').read_bytes()
    files['G06-NOTICE.txt'] = b'''ShaderLOD G06 r4 selective AO pilot, MakeUp Ultra Fast 9.5g.
LGPL-3.0-or-later; retained native LICENSE and attribution in editable source.
Source revision ad2d9327ad485429e4eaf2b3f98cd738ada609de.
Native AO kernel, sample distribution, options, TAA and other dimensions retained.
Only world0: quarter-pixel-count RG32F AO producer before native deferred shading.
Option G06_AO_SPATIAL defaults 0. 1 costs producer with native consumer; 2 enables
depth-rejected distant reconstruction; 3 is an UNTIMED green=reused/red=rejected/
blue=near/sky coverage cue. Native fallback for DH, Voxy, unpinned Iris/MC/OS,
AO off, AOSTEPS below five, odd viewport sizes. Root validates NeoForge/Sodium.
No rendered-quality, hardware-work-counter or performance claim. Inspect motion,
thin geometry and disocclusion. All pass, binding and classification costs count.
Restore by selecting preserved native pack and its unchanged option sidecar.
R2 fixes properties-only admission: dimension-local shader guards cannot be used
as global properties guards because Iris injects all discovered option defines.
World0 program namespace scopes the producer. Shader guards, threshold, native
kernel and rejection thresholds retained; r4 stops fetching after the first rejected tap.
'''
    return files


def archive(files):
    out = io.BytesIO()
    with zipfile.ZipFile(out, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 26, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            z.writestr(info, data)
    return out.getvalue()


def build(base, options, output):
    native = read(base)
    files = patch(native)
    sidecar = Path(options).read_bytes()
    # Options are copied byte-for-byte, without imposing a replacement preset.
    if any(l.strip().startswith(b'G06_AO_SPATIAL=') for l in sidecar.splitlines()):
        raise ValueError('Native sidecar already has experimental option')
    output = Path(output).resolve()
    paths = [output, Path(str(output) + '.txt'), output.with_suffix('.json')]
    if any(p.exists() for p in paths) or output == Path(base).resolve():
        raise ValueError('Refusing to overwrite artifacts')
    data = archive(files)
    receipt = {'id': 'G06', 'revision': 'r4-early-rejection', 'base': str(Path(base).resolve()), 'base_sha256': BASE_SHA,
        'output': str(output), 'sha256': sha(data), 'sidecar_sha256': sha(sidecar),
        'changed_members': {n: {'preimage': sha(native[n]), 'sha256': sha(files[n])}
                            for n in native if native[n] != files[n]},
        'added_members': {n: sha(files[n]) for n in files.keys() - native.keys()},
        'default_off': True, 'built': True, 'driver_compiled': False, 'rendered': False,
        'mechanism': 'One quarter-resolution RG32F native AO producer; four-tap hard depth rejection, native near/fallback, no Java/protocol/frame stamp or full-size classifier pass.'}
    output.parent.mkdir(parents=True, exist_ok=True)
    paths[0].write_bytes(data)
    paths[1].write_bytes(sidecar)
    paths[2].write_text(json.dumps(receipt, indent=2, sort_keys=True) + '\n')
    return receipt


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--base', required=True, type=Path)
    p.add_argument('--options', required=True, type=Path)
    p.add_argument('--output', required=True, type=Path)
    print(json.dumps(build(**vars(p.parse_args())), indent=2))

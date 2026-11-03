#!/usr/bin/env python3
"""Build G05's pinned default-off, water-only projected zero-weight-tail candidate.

No install, download, game, GL context or mod changes. Run through resource_gate.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import tempfile
import zipfile
from build_makeup_adapter import read_archive

ROOT = Path(__file__).resolve().parents[1]
NATIVE_SHA256 = '0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8'
WATER = 'shaders/lib/water.glsl'
HELPER = 'shaders/lib/shaderlod/g05_projected_miss.glsl'
OPTIONS = '\n// ShaderLOD G05: native options and AA remain unchanged.\n#define SHADERLOD_G05_PROJECTED_MISS 0 // [0 1]\n#define SHADERLOD_G05_DEBUG 0 // [0 1]\n'

def sha(data):
    return hashlib.sha256(data).hexdigest()

def transform(original, root=ROOT):
    result = original.copy()
    water = original[WATER].decode()
    begin = water.index('vec3 fastRaymarch(')
    end = water.index('\n#if SUN_REFLECTION', begin)
    native = water[begin:end]
    clone = native.replace('vec3 fastRaymarch(', 'vec3 shaderlodG05Raymarch(', 1)
    clone = clone.replace('    // Ray marching', '''    // Lazy certificate: immutable inputs are checked at most once, only after
    // a coarse upper-screen exit. No persistent state survives this ray.
    bool shaderlodG05Checked = false;
    bool shaderlodG05Eligible = false;
    vec3 shaderlodG05Error = vec3(0.0);

    // Ray marching''', 1)
    anchor = '    for (int i = 0; i < RAYMARCH_STEPS; i++) {\n'
    clone = clone.replace(anchor, anchor + '''        if (i > 0 && !searchFlag && outOfEyeFlag && screenMarchPos.y > 1.0) {
            if (!shaderlodG05Checked) {
                shaderlodG05Checked = true;
                shaderlodG05Eligible = shaderlodG05Projection() &&
                    all(lessThan(abs(hitPoint), vec3(65536.0))) &&
                    all(lessThanEqual(abs(direction), vec3(1.0))) && dither >= 0.0 && dither <= 1.0;
                shaderlodG05Error = 0.0001 * (abs(hitPoint) + abs(direction) * 2048.0 + vec3(1.0));
            }
            if (shaderlodG05Eligible &&
                shaderlodG05ZeroTail(currentMarchPoint, direction, shaderlodG05Error)) {
                infinite = 1.0;
                #if SHADERLOD_G05_DEBUG == 1
                shaderlodG05SavedSteps = float(RAYMARCH_STEPS - i);
                #endif
                return screenMarchPos;
            }
        }
''', 1)
    clone = clone.replace('texture2D(depthtex1, screenMarchPos.xy)', 'texture2DLod(depthtex1, screenMarchPos.xy, 0.0)')
    helper = (root / 'patches/makeup/g05_projected_miss.glsl').read_text()
    if helper.count('// GENERATED_NATIVE_TRACE') != 1:
        raise ValueError('Unique generated-trace anchor required')
    result[HELPER] = helper.replace('// GENERATED_NATIVE_TRACE', clone).encode()
    water = water[:end] + '\n#include "/lib/shaderlod/g05_projected_miss.glsl"\n' + water[end:]
    anchor = '        vec3 pos = fastRaymarch(reflected, fragpos, infinite, dither);'
    if water.count(anchor) != 1:
        raise ValueError('Unique native water caller required')
    water = water.replace(anchor, '''        #ifdef SHADERLOD_G05_ACTIVE
        vec3 pos = shaderlodG05Raymarch(reflected, fragpos, infinite, dither);
        #else
''' + anchor + '\n        #endif', 1)
    anchor = '    return vec4(texture2D(gaux1, pos.xy).rgb, border);'
    if water.count(anchor) != 1:
        raise ValueError('Unique water color query required')
    water = water.replace(anchor, '''    #ifdef SHADERLOD_G05_ACTIVE
    // gaux1 is colortex4: no native mip producer; keep explicit LOD for all
    // enabled water lanes after divergent trace exits. Glass remains native.
    return vec4(texture2DLod(gaux1, pos.xy, 0.0).rgb, border);
    #else
''' + anchor + '\n    #endif', 1)
    result[WATER] = water.encode()
    config = 'shaders/lib/config.glsl'
    result[config] = original[config] + OPTIONS.encode()
    props = 'shaders/shaders.properties'
    lines = original[props].decode().splitlines(keepends=True)
    result[props] = ''.join(line.rstrip('\n') + ' SHADERLOD_G05_PROJECTED_MISS SHADERLOD_G05_DEBUG\n'
                            if line.startswith('screen.WATER_SCREEN =') else line for line in lines).encode()
    common = 'shaders/common/water_blocks_fragment.glsl'
    anchor = '    // Avoid render in DH transition'
    if original[common].decode().count(anchor) != 1:
        raise ValueError('Unique water diagnostic anchor required')
    result[common] = original[common].decode().replace(anchor, '''    #if defined SHADERLOD_G05_ACTIVE && SHADERLOD_G05_DEBUG == 1
    // Untimed work witness: red = omitted queries/10, green = executed/10.
    // Only water is colored; glass/translucents retain native behavior.
    if (blockType > 2.5) blockColor.rgb = vec3(shaderlodG05SavedSteps / 10.0,
        1.0 - shaderlodG05SavedSteps / 10.0, 0.0);
    #endif

''' + anchor).encode()
    # Iris discovers internal boolean defines as user options too. Repeat the
    # complete pinned activation expression rather than expose a mutable alias.
    condition = next(line[4:] for line in helper.splitlines() if line.startswith('#if '))
    result[HELPER] = result[HELPER].replace(b'#define SHADERLOD_G05_ACTIVE\n', b'')
    for name in (WATER, common):
        result[name] = result[name].replace(b'#ifdef SHADERLOD_G05_ACTIVE', ('#if ' + condition).encode())
        result[name] = result[name].replace(b'#if defined SHADERLOD_G05_ACTIVE &&', ('#if (' + condition + ') &&').encode())
    return result

def write_zip(output, files):
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output.parent, suffix='.zip', delete=False) as tmp:
        staging = Path(tmp.name)
    try:
        with zipfile.ZipFile(staging, 'w', compression=zipfile.ZIP_DEFLATED) as z:
            for name, data in sorted(files.items()):
                info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.create_system = 3
                info.external_attr = 0o100644 << 16
                z.writestr(info, data, compresslevel=9)
        staging.replace(output)
    finally:
        staging.unlink(missing_ok=True)

def build(base, output, options=None, root=ROOT):
    if sha(base.read_bytes()) != NATIVE_SHA256:
        raise ValueError('Exact original MakeUp Ultra Fast 9.5g archive required')
    if output.resolve() == base.resolve() or any(p.exists() for p in [output, Path(str(output)+'.txt'), Path(str(output)+'.json')]):
        raise ValueError('Fresh output distinct from original required')
    options_data = options.read_bytes() if options else None
    if options_data is not None and b'SHADERLOD_G05_' in options_data:
        raise ValueError('Source options already contain G05 controls')
    original = read_archive(base)
    files = transform(original, root)
    patches = {name: {'preimage_sha256': sha(original[name]), 'postimage_sha256': sha(data)}
               for name, data in files.items() if name in original and data != original[name]}
    metadata = {'id': 'G05', 'family': 'MakeUp Ultra Fast', 'version': '9.5g',
                'base_sha256': NATIVE_SHA256, 'default_off': True, 'changed_members': patches,
                'active_scope': 'MC1.21.1 Iris1.8.12 Overworld gbuffers_water, native SSR10, noDH/noVoxy',
                'validation': {'driver_compiled': False, 'rendered': False, 'performance_measured': False},
                'restoration': 'Option0/native archive; no runtime controller or shared state modified'}
    files['G05-build.json'] = (json.dumps(metadata, indent=2, sort_keys=True) + '\n').encode()
    files['g05-source/scripts/build_makeup_projected_miss.py'] = Path(__file__).read_bytes()
    files['g05-source/patches/makeup/g05_projected_miss.glsl'] = (root / 'patches/makeup/g05_projected_miss.glsl').read_bytes()
    files['g05-source/scripts/build_makeup_adapter.py'] = (root / 'scripts/build_makeup_adapter.py').read_bytes()
    files['G05-NOTICE.txt'] = b'G05 experimental derivative of MakeUp Ultra Fast 9.5g by Javier Garduno.\nRetains original LICENSE (LGPLv3), attribution, options, AA and editable source.\nModified files listed in G05-build.json. No runtime/quality/performance acceptance.\nBuilder source and helper retained here; source pin: original MakeUp 9.5g archive.\n'
    write_zip(output, files)
    receipt = {**metadata, 'artifact': str(output.resolve()), 'artifact_sha256': sha(output.read_bytes()),
               'native_archive': str(base.resolve()), 'added_members': sorted(set(files) - set(original))}
    if options:
        data = options_data
        # Preserve every native byte; only this experiment's default-off controls are appended.
        Path(str(output) + '.txt').write_bytes(data + b'\nSHADERLOD_G05_PROJECTED_MISS=0\nSHADERLOD_G05_DEBUG=0\n')
        receipt['native_options_sha256'] = sha(data)
    Path(str(output) + '.json').write_text(json.dumps(receipt, indent=2) + '\n')
    return receipt

if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--base', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--options', type=Path)
    a = p.parse_args()
    print(json.dumps(build(a.base, a.output, a.options), indent=2))

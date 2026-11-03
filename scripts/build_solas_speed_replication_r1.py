#!/usr/bin/env python3
"""Pinned private Solas Mac metadata baseline and sparse rough-gather candidate."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ORIGINAL = ROOT / 'dependencies/upstream/pack-expansion/Solas Shader V3.7b.zip'
ORIGINAL_SHA = 'b7dfe4c7353d677a92cc4c86b87bc0633f1014305d927ca6a482c303427bf825'
HELPER = '''#ifndef SHADERLOD_ROUGH_GATHER_R1_INCLUDED
#define SHADERLOD_ROUGH_GATHER_R1_INCLUDED
// Private local ShaderLOD experiment; upstream credits and licenses retained.
#define SHADERLOD_ROUGH_GATHER_R1 0 // [0 1]
#if SHADERLOD_ROUGH_GATHER_R1 == 1
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;
uniform int shaderlodPreset;
bool shaderlod_sparse_rough_gather_r1() {
    return shaderlodBridgeVersion == 2 && shaderlodEnabled == 1 &&
        shaderlodPreset >= 0 && shaderlodPreset <= 2;
}
#endif
#endif
'''


def sha(data):
    return hashlib.sha256(data).hexdigest()


def replace_once(data, before, after):
    before, after = before.encode(), after.encode()
    if data.count(before) != 1:
        raise ValueError('Expected one exact pinned source anchor: ' + before.decode())
    return data.replace(before, after, 1)


def prepare(original, kind):
    result = dict(original)
    props = original['shaders/shaders.properties']
    # Native common.glsl already excludes VX on MC_OS_MAC. Align only metadata.
    props = replace_once(props, '#ifdef VX_SUPPORT\n#Custom images for voxelization',
                         '#if defined VX_SUPPORT && !defined MC_OS_MAC\n#Custom images for voxelization')
    predicates = ('program.world0/shadowcomp.enabled=VX_SUPPORT\n'
                  'program.world-1/shadowcomp.enabled=VX_SUPPORT\n'
                  'program.world1/shadowcomp.enabled=VX_SUPPORT')
    props = replace_once(props, predicates,
        '#ifdef MC_OS_MAC\n'
        'program.shadowcomp.enabled=false\n'
        'program.world0/shadowcomp.enabled=false\n'
        'program.world-1/shadowcomp.enabled=false\n'
        'program.world1/shadowcomp.enabled=false\n'
        '#else\n' + predicates + '\n#endif')
    result['shaders/shaders.properties'] = props
    if kind == 'rough-gather':
        path = 'shaders/lib/pbr/simpleReflection.glsl'
        body = original[path]
        body = b'#include "/lib/shaderlod/rough_gather_r1.glsl"\n' + body
        body = replace_once(body,
            '                for (int i = -2; i <= 2; i++) {\n'
            '                    for (int j = -2; j <= 2; j++) {',
            '                #if SHADERLOD_ROUGH_GATHER_R1 == 1\n'
            '                int shaderlodGatherStep = 1;\n'
            '                float shaderlodGatherCount = 25.0;\n'
            '                if (shaderlod_sparse_rough_gather_r1()) {\n'
            '                    shaderlodGatherStep = 2;\n'
            '                    shaderlodGatherCount = 9.0;\n'
            '                }\n'
            '                for (int i = -2; i <= 2; i += shaderlodGatherStep) {\n'
            '                    for (int j = -2; j <= 2; j += shaderlodGatherStep) {\n'
            '                #else\n'
            '                for (int i = -2; i <= 2; i++) {\n'
            '                    for (int j = -2; j <= 2; j++) {\n'
            '                #endif')
        body = replace_once(body, '                reflection /= 25.0;',
            '                #if SHADERLOD_ROUGH_GATHER_R1 == 1\n'
            '                reflection /= shaderlodGatherCount;\n'
            '                #else\n'
            '                reflection /= 25.0;\n'
            '                #endif')
        result[path] = body
        result['shaders/lib/shaderlod/rough_gather_r1.glsl'] = HELPER.encode()
        props = replace_once(props,
            'screen=SOLAS_BY_SEPTONIOUS <profile> <empty> [ATMOSPHERICS] [WATER] [PBR] [LIGHTING] [COLOR] [POST] [OTHER]',
            'screen=SOLAS_BY_SEPTONIOUS <profile> <empty> [ATMOSPHERICS] [WATER] [PBR] [LIGHTING] [COLOR] [POST] [OTHER] [SHADERLOD_R1]')
        result['shaders/shaders.properties'] = props + b'\n# Private experiment; native settings retained.\nscreen.SHADERLOD_R1=SHADERLOD_ROUGH_GATHER_R1\n'
    elif kind != 'metadata-baseline':
        raise ValueError('Unknown private artifact kind')
    return result


def build(original_path, output, kind, receipt_path):
    if output.exists() or receipt_path.exists():
        raise ValueError('Refuse to overwrite existing output or receipt')
    if sha(original_path.read_bytes()) != ORIGINAL_SHA:
        raise ValueError('Pinned original archive identity differs')
    with zipfile.ZipFile(original_path) as z:
        if z.testzip() is not None:
            raise ValueError('Original archive CRC failure')
        if len(z.namelist()) != len(set(z.namelist())):
            raise ValueError('Duplicate archive member')
        original = {n: z.read(n) for n in z.namelist()}
    result = prepare(original, kind)
    receipt = {
        'kind': kind, 'original_archive': str(original_path), 'original_sha256': ORIGINAL_SHA,
        'modified_members': {n: {'before': sha(original[n]), 'after': sha(result[n])}
                             for n in original if original[n] != result[n]},
        'added_members': {n: sha(result[n]) for n in result if n not in original},
        'preserved_members': sum(original[n] == result[n] for n in original),
        'runtime_verified': False, 'quality_verified': False, 'speed_verified': False,
        'baseline_label': 'Solas 3.7b original GLSL on private native-Mac metadata correction',
        'native_options_changed': [], 'option_default': 0 if kind == 'rough-gather' else None,
        'redistribution_permission': False,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    # Exclusive file creation prevents concurrent overwrite of experiment evidence.
    with output.open('xb') as f:
        with zipfile.ZipFile(f, 'w', zipfile.ZIP_DEFLATED) as z:
            for name, data in sorted(result.items()):
                info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o644 << 16
                z.writestr(info, data)
    receipt['output'] = str(output)
    receipt['output_sha256'] = sha(output.read_bytes())
    receipt_path.parent.mkdir(parents=True, exist_ok=True)
    with receipt_path.open('x') as f:
        json.dump(receipt, f, indent=2, sort_keys=True)
        f.write('\n')
    return receipt


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--original', type=Path, default=ORIGINAL)
    p.add_argument('--kind', choices=['metadata-baseline', 'rough-gather'], required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--receipt', type=Path, required=True)
    a = p.parse_args()
    print(json.dumps(build(a.original, a.output, a.kind, a.receipt), indent=2))


if __name__ == '__main__':
    main()

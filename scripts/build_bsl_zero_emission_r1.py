#!/usr/bin/env python3
"""Pinned BSL10.1.8/native384 private zero-emission arithmetic control/candidate."""
import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'dependencies/upstream/other-pack-research/BSL_v10.1.8.zip'
BASE_SHA = '36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5'
OPTIONS = ROOT / 'src/main/resources/assets/shaderlod/bslquad/native384.json'
OPTIONS_SHA = '0aaa1276ea6e24106e6ed062a3bd149d1b53c09693d04a4637dcb19e6c43c394'
OUTPUT_ROOT = ROOT / 'dev/logs/speed-port-r1/12111-fabric-bsl-zero-emission-20261011'
ENTRY = 'shaders/world0/gbuffers_terrain.fsh'
TERRAIN = 'shaders/program/gbuffers_terrain.glsl'
PRIVATE = 'shaders/shaderlod/bsl_zero_emission_r1/'
ENTRY_SHA = 'fb8bd36225c0b74ab5befc33f7097e02e10196a20d3441a93819729792580801'
TERRAIN_SHA = 'c47d350132261e8f7b271f99ba8d060e4b38ee12a5da50597b664245f212c8d7'
DECLARATIONS = (b'uniform bool shaderlodBslZeroEmissionEnabled;\r\n'
                b'uniform int shaderlodBslZeroEmissionVersion;\r\n'
                b'uniform int shaderlodBslZeroEmissionPreset;\r\n')
NATIVE_ARITHMETIC = (b'\t\tvec3 hsv = RGB2HSV(albedo.rgb);\r\n'
                     b'\t\temission *= GetHardcodedEmission(albedo.rgb, hsv);')
SKIP_HELPER = (
    b'bool shaderlodBslZeroEmissionSkip(float nativeEmission, vec3 nativeRgb) {\r\n'
    b'    return shaderlodBslZeroEmissionEnabled && shaderlodBslZeroEmissionVersion == 1 &&\r\n'
    b'           shaderlodBslZeroEmissionPreset == 1 && nativeEmission == 0.0 &&\r\n'
    b'           all(greaterThanEqual(nativeRgb, vec3(0.0))) &&\r\n'
    b'           all(lessThanEqual(nativeRgb, vec3(2.0)));\r\n'
    b'}\r\n')
# Exact emission equality, fixed arithmetic safety domain, and actual mod bridge.
CANDIDATE_ARITHMETIC = (
    b'\t\t#if !defined(ADVANCED_MATERIALS) && !defined(GLOWING_ORES) && EMISSIVE_HARDCODED == 0\r\n'
    b'\t\tif (!shaderlodBslZeroEmissionSkip(emission, albedo.rgb)) {\r\n'
    b'\t\t\tvec3 hsv = RGB2HSV(albedo.rgb);\r\n'
    b'\t\t\temission *= GetHardcodedEmission(albedo.rgb, hsv);\r\n'
    b'\t\t}\r\n'
    b'\t\t#else\r\n' + NATIVE_ARITHMETIC + b'\r\n'
    b'\t\t#endif')
NOTICE = (b'Private ShaderLOD zero-emission arithmetic experiment derived from BSL Shaders v10 Series\n'
          b'by Capt Tatsu, https://capttatsu.com . All original source credits and license terms retained.\n'
          b'Native384 source only; no shader-quality, driver or performance acceptance implied.\n')

def sha(data):
    return hashlib.sha256(data).hexdigest()

def replace_once(source, before, after):
    if source.count(before) != 1:
        raise ValueError('Missing or ambiguous pinned source anchor: ' + repr(before[:100]))
    return source.replace(before, after, 1)

def load(base=BASE, options=OPTIONS):
    if sha(Path(base).read_bytes()) != BASE_SHA or sha(Path(options).read_bytes()) != OPTIONS_SHA:
        raise ValueError('Pinned original archive/native384 resource mismatch')
    values = json.loads(Path(options).read_bytes())
    if len(values) != 384 or values.get('ADVANCED_MATERIALS') != 'false' or values.get('GLOWING_ORES') != 'false' or values.get('EMISSIVE_HARDCODED') != '0':
        raise ValueError('Unsupported native emission graph')
    with zipfile.ZipFile(base) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or any(PurePosixPath(n).is_absolute() or '..' in PurePosixPath(n).parts or '\\' in n or '\x00' in n for n in names):
            raise ValueError('Unsafe or duplicate archive inventory')
        files = {n: archive.read(n) for n in names if not n.endswith('/')}
    if sha(files.get(ENTRY, b'')) != ENTRY_SHA or sha(files.get(TERRAIN, b'')) != TERRAIN_SHA:
        raise ValueError('Pinned BSL terrain entry/body changed')
    return files

def derive(original, candidate):
    if sha(original.get(ENTRY, b'')) != ENTRY_SHA or sha(original.get(TERRAIN, b'')) != TERRAIN_SHA:
        raise ValueError('Uncertified terrain entry/body')
    result = dict(original)
    terrain = replace_once(original[TERRAIN], b'uniform int bedrockLevel;', DECLARATIONS + b'uniform int bedrockLevel;')
    if terrain.count(NATIVE_ARITHMETIC) != 1:
        raise ValueError('Native HSV/arithmetic sequence changed')
    if candidate:
        terrain = replace_once(terrain, b'uniform int bedrockLevel;', SKIP_HELPER + b'uniform int bedrockLevel;')
        terrain = replace_once(terrain, NATIVE_ARITHMETIC, CANDIDATE_ARITHMETIC)
    # Control is the native expression: unused protocol uniforms may be pruned; no artificial work.
    result[ENTRY] = replace_once(original[ENTRY], b'#include "/program/gbuffers_terrain.glsl"', b'#include "/shaderlod/bsl_zero_emission_r1/terrain.glsl"')
    if any(n in original for n in (PRIVATE + 'terrain.glsl', PRIVATE + 'NOTICE')):
        raise ValueError('Private namespace already occupied')
    result[PRIVATE + 'terrain.glsl'] = terrain
    result[PRIVATE + 'NOTICE'] = NOTICE
    if [n for n in original if result[n] != original[n]] != [ENTRY]:
        raise ValueError('Only world0 terrain fragment entry may change')
    return result

def archive_bytes(files):
    memory = io.BytesIO()
    with zipfile.ZipFile(memory, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            archive.writestr(info, data, compresslevel=9)
    return memory.getvalue()

def build(output, base=BASE, options=OPTIONS):
    destination = Path(output).absolute()
    for parent in (destination, *destination.parents):
        if parent.is_symlink():
            raise ValueError('Output path aliases another location')
    destination = destination.resolve()
    if not destination.is_relative_to(OUTPUT_ROOT.resolve()) or destination == OUTPUT_ROOT.resolve() or destination.exists():
        raise ValueError('Fresh child output directory under the named ignored experiment is required')
    original = load(base, options)
    arms = {arm: derive(original, candidate) for arm, candidate in [('control', False), ('candidate', True)]}
    data = {arm: archive_bytes(files) for arm, files in arms.items()}
    record = {'kind': 'private-bsl-native384-zero-emission-r1', 'base_path': str(Path(base).resolve()),
              'base_sha256': BASE_SHA, 'native384_sha256': OPTIONS_SHA,
              'builder_sha256': sha(Path(__file__).read_bytes()), 'runtime_accepted': False,
              'quality_accepted': False, 'performance_accepted': False, 'arms': {}}
    destination.mkdir(parents=True)
    for arm, files in arms.items():
        path = destination / ('BSL-zero-emission-r1-' + arm + '.zip')
        with path.open('xb') as stream:
            stream.write(data[arm])
        with Path(str(path) + '.txt').open('xb') as stream:
            stream.write(b'')  # Zero overrides, never an empty effective options map.
        record['arms'][arm] = {'path': str(path), 'sha256': sha(data[arm]),
            'sidecar_sha256': sha(b''), 'modified_original_members': [ENTRY],
            'added_members': [PRIVATE + 'NOTICE', PRIVATE + 'terrain.glsl'],
            'member_sha256': {n: sha(b) for n, b in sorted(files.items())},
            'control_unused_uniforms_may_be_pruned': arm == 'control'}
    with (destination / 'manifest.json').open('x') as stream:
        stream.write(json.dumps(record, indent=2, sort_keys=True) + '\n')
    return record

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--base', default=BASE, type=Path)
    parser.add_argument('--options', default=OPTIONS, type=Path)
    arguments = parser.parse_args()
    print(json.dumps(build(arguments.output, arguments.base, arguments.options), indent=2))

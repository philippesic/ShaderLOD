#!/usr/bin/env python3
"""Build a private fixed-graph Photon air-fog body cost diagnostic; no client."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASE = Path('dependencies/upstream/photon_v1.3b.zip')
BASE_SHA256 = '120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead'
MANIFEST = Path('patches/photon-experiments/air-fog-cost-ablation.json')
SWITCH = 'SHADERLOD_AIR_FOG_COST_ABLATION'
PROGRAM = 'shaders/program/c0_vl.fsh'
SETTINGS = 'shaders/settings.glsl'
MEMBERS = (SETTINGS, PROGRAM)
HELPER = 'shaders/include/shaderlod/air_fog_cost_ablation.glsl'
METADATA = 'shaderlod-air-fog-cost-ablation.json'
ARMS = ('original', 'disabled', 'enabled')
STAGE = '#if defined WORLD_OVERWORLD && defined PROGRAM_COMPOSITE0 && defined VL && !defined COLORED_LIGHTS && !defined AIR_FOG_COLORED_LIGHT_SHAFTS'
DEPENDENCIES = ('shaders/include/fog/overworld/raymarched.glsl',
    'shaders/include/fog/overworld/parameters.glsl', 'shaders/program/c0_vl.vsh',
    'shaders/program/c1_blend_layers.fsh', 'shaders/shaders.properties',
    'shaders/world0/composite.fsh', 'shaders/world0/composite.vsh')

def sha256(data):
    return hashlib.sha256(data).hexdigest()

def archive_members(path):
    with zipfile.ZipFile(path) as archive:
        names = [n for n in archive.namelist() if not n.endswith('/')]
        if len(names) != len(set(names)) or any(n.startswith('/') or '..' in Path(n).parts for n in names):
            raise ValueError('Duplicate or unsafe archive member')
        return {n: archive.read(n) for n in names}

def patch_members(files, manifest, arm, root=ROOT):
    if (arm not in ARMS or manifest['schema'] != 1 or
        manifest['experiment'] != 'photon-fixed-graph-air-fog-cost-ablation' or
        manifest['base_sha256'] != BASE_SHA256 or manifest['base_version'] != 'v1.3b' or
        manifest['switch'] != SWITCH or tuple(e['path'] for e in manifest['members']) != MEMBERS or
        manifest['addition']['path'] != HELPER or
        manifest['addition']['source_path'] != 'shaderpacks/photon-air-fog-cost-ablation/' + HELPER or
        tuple(manifest['source_dependencies']) != DEPENDENCIES or manifest['profile_condition'] != STAGE or
        manifest['activation'] != 'private ON; core ABI 2/global 1; ordinary Overworld air camera only'):
        raise ValueError('Unreviewed air-fog-cost-ablation scope')
    if sha256(files['LICENSE']) != manifest['license_sha256']:
        raise ValueError('Upstream license changed')
    for name, expected in manifest['source_dependencies'].items():
        if sha256(files[name]) != expected: raise ValueError('Source dependency changed: ' + name)
    result = files.copy()
    for entry in manifest['members']:
        name = entry['path']
        if sha256(files[name]) != entry['source_sha256']: raise ValueError('Source pin changed: ' + name)
        source = files[name].decode()
        for replacement in entry['replacements']:
            before = replacement['before']
            if not before or source.count(before) != 1: raise ValueError('Ambiguous source anchor')
            source = source.replace(before, replacement['after'], 1)
        payload = source.encode()
        if sha256(payload) != entry['patched_sha256']: raise ValueError('Patched source pin changed')
        if name == SETTINGS and arm == 'enabled':
            payload = payload.replace(f'//#define {SWITCH}\n'.encode(), f'#define {SWITCH}\n'.encode(), 1)
            if sha256(payload) != entry['enabled_sha256']: raise ValueError('Enabled settings pin changed')
        result[name] = files[name] if arm == 'original' else payload
    helper = (root / manifest['addition']['source_path']).read_bytes()
    if HELPER in files or sha256(helper) != manifest['addition']['sha256']:
        raise ValueError('Conflicting or changed helper')
    if arm != 'original': result[HELPER] = helper
    return result

def build(output=None, base=None, arm='disabled', root=ROOT):
    base = (base or root / BASE).resolve()
    output = (output or root / 'build' / f'air-fog-cost-ablation-{arm}.zip').resolve()
    if (not output.is_relative_to((root / 'build').resolve()) or
        not output.name.startswith('air-fog-cost-ablation-') or output.suffix != '.zip' or output == base):
        raise ValueError('Output must be ignored build/air-fog-cost-ablation-*.zip')
    if sha256(base.read_bytes()) != BASE_SHA256: raise ValueError('Pinned archive changed')
    manifest_bytes = (root / MANIFEST).read_bytes(); manifest = json.loads(manifest_bytes)
    original = archive_members(base)
    if METADATA in original: raise ValueError('Conflicting metadata')
    files = patch_members(original, manifest, arm, root)
    modified = sorted(n for n in original if original[n] != files[n])
    if modified != ([] if arm == 'original' else sorted(MEMBERS)): raise ValueError('Unexpected changes')
    metadata = {
        'experiment': manifest['experiment'], 'arm': arm, 'base_sha256': BASE_SHA256,
        'manifest_sha256': sha256(manifest_bytes), 'modified_upstream_files': modified,
        'member_sha256': {n: sha256(b) for n,b in sorted(files.items())},
        'activation': manifest['activation'], 'profile_condition': STAGE,
        'fallback': 'private OFF/nonconsumer/unsupported/unknown ABI/global OFF execute original body',
        'fixed_graph': 'VL=true required by consuming path; C1/cached sky/passes/allocations unchanged',
        'distance_units': 'original world positions and ray length are blocks; no distance gate',
        'transition': 'hard uniform diagnostic toggle; no quality-preserving fade',
        'runtime_compilation': 'unverified', 'driver_consumption': 'unverified', 'gpu_benefit': 'unmeasured',
        'measurement_limit': 'register allocation and branch microarchitecture can affect the measured cost ceiling',
        'attribution': 'Photon v1.3b by Benjamin Stott (SixthSurge); complete LICENSE retained',
    }
    files[METADATA] = (json.dumps(metadata,indent=2,sort_keys=True)+'\n').encode()
    output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output.parent,suffix='.zip',delete=False) as f: temporary=Path(f.name)
    try:
        with zipfile.ZipFile(temporary,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
            for name,payload in sorted(files.items()):
                info=zipfile.ZipInfo(name,date_time=(1980,1,1,0,0,0)); info.compress_type=zipfile.ZIP_DEFLATED
                info.create_system=3; info.external_attr=0o100644 << 16
                archive.writestr(info,payload,compresslevel=9)
        temporary.replace(output)
    finally: temporary.unlink(missing_ok=True)
    return metadata

def main():
    p=argparse.ArgumentParser(description=__doc__); p.add_argument('--arm',choices=ARMS,default='disabled')
    p.add_argument('--base',type=Path); p.add_argument('--output',type=Path); a=p.parse_args()
    print(json.dumps(build(a.output,a.base,a.arm),indent=2))

if __name__ == '__main__': main()

#!/usr/bin/env python3
"""Build pinned Photon private frame-history/raster-scale prototype; never launches a client."""
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
MANIFEST = Path('patches/photon-experiments/live-pixel-history.json')
SWITCH = 'SHADERLOD_LIVE_PIXEL_HISTORY'
SETTINGS = 'shaders/settings.glsl'
GLOBAL = 'shaders/include/global.glsl'
MEMBERS = (SETTINGS, GLOBAL, *('shaders/program/'+n+'.fsh' for n in
    ('gbuffers_all_solid','gbuffers_all_translucent','gbuffers_damagedblock','gbuffers_armor_glint','d2_clouds_upscaling','c4_taa_exposure')))
HELPER = 'shaders/include/shaderlod/live_pixel_history.glsl'
METADATA = 'shaderlod-live-pixel-history.json'
ARMS = ('original','disabled','enabled')
BUILD_FAMILY = 'live-pixel-history-r2'


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def archive_members(path):
    with zipfile.ZipFile(path) as archive:
        names = [n for n in archive.namelist() if not n.endswith('/')]
        if len(names) != len(set(names)) or any(n.startswith('/') or '..' in Path(n).parts for n in names):
            raise ValueError('Unsafe or duplicate archive member')
        return {n: archive.read(n) for n in names}


def patch_members(files, manifest, arm, root=ROOT):
    if (arm not in ARMS or manifest['schema'] != 1 or manifest['base_sha256'] != BASE_SHA256 or
        manifest['base_version'] != 'v1.3b' or manifest['experiment'] != 'photon-live-pixel-history' or
        manifest['switch'] != SWITCH or tuple(e['path'] for e in manifest['members']) != MEMBERS or
        manifest['addition']['path'] != HELPER or
        manifest['addition']['source_path'] != 'shaderpacks/photon-live-pixel-history/' + HELPER or
        manifest['active_scale'] != .75 or manifest['disabled_scale'] != 1.0):
        raise ValueError('Unreviewed live pixel-history scope/arm')
    if manifest.get('revision') != 2 or manifest.get('temporal_contract') != dict(version=1, version_uniform='shaderlodTemporalVersion', reset_uniform='shaderlodHistoryReset', valid_reset_values=[0,1], frame_stable_required=True, unavailable_history='reject_every_frame'):
        raise ValueError('Unreviewed temporal contract')
    if sha256(files['LICENSE']) != manifest['license_sha256']:
        raise ValueError('Upstream license changed')
    for name, digest in manifest['source_dependencies'].items():
        if sha256(files[name]) != digest:
            raise ValueError('Pinned producer/consumer dependency changed: ' + name)
    result = files.copy()
    for entry in manifest['members']:
        name = entry['path']
        if sha256(files[name]) != entry['source_sha256']:
            raise ValueError('Pinned source changed: ' + name)
        source = files[name].decode()
        for replacement in entry['replacements']:
            before = replacement['before']
            if not before or source.count(before) != 1:
                raise ValueError('Missing/ambiguous source anchor: ' + name)
            source = source.replace(before,replacement['after'],1)
        if sha256(source.encode()) != entry['patched_sha256']:
            raise ValueError('Patched source changed: ' + name)
        if name == SETTINGS and arm == 'enabled':
            source = source.replace('//#define ' + SWITCH + '\n', '#define ' + SWITCH + '\n',1)
            if sha256(source.encode()) != entry['enabled_sha256']:
                raise ValueError('Private enabled setting changed')
        result[name] = files[name] if arm == 'original' else source.encode()
    helper = (root / manifest['addition']['source_path']).read_bytes()
    if HELPER in files or sha256(helper) != manifest['addition']['sha256']:
        raise ValueError('Conflicting/changed helper')
    if arm != 'original':
        result[HELPER] = helper
    return result


def build(output=None, base=None, arm='disabled', root=ROOT):
    base = (base or root / BASE).resolve()
    output = (output or root / 'build' / (BUILD_FAMILY+'-'+arm+'.zip')).resolve()
    if (not output.is_relative_to((root / 'build').resolve()) or
        not output.name.startswith(BUILD_FAMILY+'-') or output.suffix != '.zip' or output == base):
        raise ValueError('Output must be ignored build/live-pixel-history-r2-*.zip')
    if sha256(base.read_bytes()) != BASE_SHA256:
        raise ValueError('Pinned original archive changed')
    manifest_bytes = (root / MANIFEST).read_bytes()
    manifest = json.loads(manifest_bytes)
    original = archive_members(base)
    if METADATA in original:
        raise ValueError('Conflicting metadata')
    files = patch_members(original, manifest, arm, root)
    modified = sorted(n for n in original if files[n] != original[n])
    if modified != ([] if arm == 'original' else sorted(MEMBERS)):
        raise ValueError('Unexpected upstream changes')
    metadata = dict(experiment=manifest['experiment'],revision=2,arm=arm,base_sha256=BASE_SHA256,
        manifest_sha256=sha256(manifest_bytes),modified_upstream_files=modified,
        member_sha256={n:sha256(files[n]) for n in (*MEMBERS,*manifest['source_dependencies'],'LICENSE')},
        helper_sha256=manifest['addition']['sha256'] if arm != 'original' else None,
        activation='Private ON, TAA+TAAU, !VOXY; core 2, global 0/1, temporal 1, reset 0/1; fixed graph',
        fallback=manifest['unknown_bridge_fallback'],voxy_status=manifest['voxy_status'],
        transition_status=manifest['transition_status'],
        disabled_baseline='Private OFF is upstream source; private ON/global OFF is scale1 fixed TAAU graph, NOT original native',
        mod_linkage='Core version/global only; no per-effect or capability/source registration proof',
        runtime_compilation='unverified',quality='unaccepted',performance='unmeasured',
        attribution='Photon v1.3b by Benjamin Stott (SixthSurge); upstream LICENSE retained')
    files[METADATA] = (json.dumps(metadata,indent=2,sort_keys=True)+'\n').encode()
    output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output.parent,suffix='.zip',delete=False) as temporary:
        temp = Path(temporary.name)
    try:
        with zipfile.ZipFile(temp,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
            for name,payload in sorted(files.items()):
                info=zipfile.ZipInfo(name,date_time=(1980,1,1,0,0,0)); info.compress_type=zipfile.ZIP_DEFLATED
                info.create_system=3; info.external_attr=0o100644 << 16
                archive.writestr(info,payload,compresslevel=9)
        temp.replace(output)
    finally:
        temp.unlink(missing_ok=True)
    return metadata


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--arm',choices=ARMS,default='disabled')
    parser.add_argument('--base',type=Path); parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    print(json.dumps(build(args.output,args.base,args.arm),indent=2))

if __name__ == '__main__':
    main()

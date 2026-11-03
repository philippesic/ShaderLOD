#!/usr/bin/env python3
"""Build pinned private ray-metric work-avoidance experiments, with no runtime claim."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import tempfile
import zipfile
from build_supported_pack_adapters import read_archive, shader_source_digest

ROOT = Path(__file__).resolve().parents[1]
PATCHES = ROOT / 'patches/solas-astralex-lod-r1'

def sha(data):
    return hashlib.sha256(data).hexdigest()

def apply(files, manifest):
    if manifest['schema'] != 1 or manifest['kind'] != 'output-only-ray-metric-deferral':
        raise ValueError('Unknown schema/kind')
    if shader_source_digest(files) != manifest['shader_source_digest']:
        raise ValueError('Pinned shader source identity changed')
    for name, digest in manifest['source_members'].items():
        if name not in files or sha(files[name]) != digest:
            raise ValueError('Pinned member changed: ' + name)
    result = dict(files)
    for patch in manifest['patches']:
        name, before, after = patch['path'], patch['before'].encode(), patch['after'].encode()
        if name not in manifest['source_members']:
            raise ValueError('Unpinned patch member')
        if not before or result[name].count(before) != 1:
            raise ValueError('Patch anchor must occur exactly once: ' + name)
        result[name] = result[name].replace(before, after, 1)
    for name, local in manifest['additions'].items():
        if name in result:
            raise ValueError('Addition collision: ' + name)
        data = (ROOT / local['path']).read_bytes()
        if sha(data) != local['sha256']:
            raise ValueError('Helper changed')
        result[name] = data
    return result

def build(base, output, manifest_path):
    base, output = Path(base), Path(output)
    if base.resolve() == output.resolve():
        raise ValueError('Cannot overwrite original')
    if output.exists():
        raise ValueError('Output already exists')
    manifest = json.loads(Path(manifest_path).read_text())
    if sha(base.read_bytes()) != manifest['original_archive_sha256']:
        raise ValueError('Pinned original archive changed')
    original = read_archive(base)
    result = apply(original, manifest)
    changed = sorted(name for name in original if original[name] != result[name])
    protected = {name: sha(data) for name, data in original.items()
        if name not in changed and not name.startswith('shaders/')}
    receipt = {'schema':1, 'id':manifest['id'], 'kind':manifest['kind'],
        'pack':manifest['pack'], 'version':manifest['version'], 'primary_page':manifest['primary_page'],
        'original_archive':str(base), 'original_archive_sha256':sha(base.read_bytes()),
        'original_shader_source_digest':shader_source_digest(original),
        'candidate_shader_source_digest':shader_source_digest(result),
        'source_members':manifest['source_members'], 'modified_members':changed,
        'preserved_non_shader_members':protected,
        'option_key':manifest['option_key'], 'default_enabled':False,
        'transport':'Existing globally registered Iris core bridge version 2; runtime consumption unverified',
        'validation':{'source_assembled':True, 'driver_compiled':False, 'iris_rendered':False,
                      'mod_consumption_verified':False, 'quality_verified':False, 'gpu_measured':False,
                      'dh_verified':False, 'pbr_verified':False},
        'promotion':'Private experiment only; production source recognizer/catalog has not been updated'}
    for reserved in ('shaderlod-ray-metric-r1.json', 'SHADERLOD_PRIVATE_RAY_METRIC_R1.md'):
        if reserved in result:
            raise ValueError('Reserved member already exists')
    result['shaderlod-ray-metric-r1.json'] = (json.dumps(receipt,indent=2,sort_keys=True)+'\n').encode()
    result['SHADERLOD_PRIVATE_RAY_METRIC_R1.md'] = (
        '# Private ShaderLOD ray-metric experiment\n\nOriginal '+manifest['pack']+' '+manifest['version']+
        '\n'+manifest['primary_page']+'\n\nAll original members/author credits retained. '
        'All rights reserved; no redistribution permission established. Personal local adaptation only.\n\n'
        'Default Off. Enable SHADERLOD_DEFERRED_METRIC=1 in Iris options; ShaderLOD enabled with '
        'BALANCED/PERFORMANCE/AGGRESSIVE requests output-only metric deferral. Off/QUALITY/unknown '
        'bridge/preset restores upstream work. No native AA, quality, PBR or DH option changes. '
        'No distance threshold or transition; mathematically the same returned metric at every distance.\n\n'
        'Driver rendering, consumption, quality and GPU savings are unverified. The compiler may '
        'already remove repeated metric work; the uniform branch/state may cost more.\n').encode()
    output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output.parent,delete=False) as f:
        staging = Path(f.name)
    try:
        with zipfile.ZipFile(staging,'w',zipfile.ZIP_DEFLATED) as archive:
            for name,data in sorted(result.items()):
                entry=zipfile.ZipInfo(name,(1980,1,1,0,0,0))
                entry.compress_type=zipfile.ZIP_DEFLATED
                entry.external_attr=0o644 << 16
                archive.writestr(entry,data)
        with output.open('xb') as f:
            f.write(staging.read_bytes())
    finally:
        staging.unlink(missing_ok=True)
    return {**receipt,'output':str(output),'output_sha256':sha(output.read_bytes())}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pack',required=True,choices=('solas-shader','astralex'))
    parser.add_argument('--base',type=Path)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--receipt',type=Path)
    args=parser.parse_args()
    manifest_path=PATCHES/(args.pack+'.json')
    manifest=json.loads(manifest_path.read_text())
    receipt=build(args.base or ROOT/manifest['original_archive'],args.output,manifest_path)
    payload=json.dumps(receipt,indent=2,sort_keys=True)+'\n'
    if args.receipt:
        args.receipt.parent.mkdir(parents=True,exist_ok=True)
        args.receipt.write_text(payload)
    print(payload)

if __name__=='__main__':
    main()

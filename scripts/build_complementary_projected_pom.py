#!/usr/bin/env python3
"""Build one pinned, default-Off PRIVATE projected POM experiment; no catalog registration."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import tempfile
import zipfile
from build_supported_pack_adapters import read_archive, shader_source_digest

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / 'patches/adapters/complementary-projected-pom.json'

def sha256(data):
    return hashlib.sha256(data).hexdigest()

def apply(files, manifest, root=ROOT):
    if manifest.get('schema') != 1 or manifest.get('kind') != 'projected-material-primary-march':
        raise ValueError('Unsupported private experiment schema/kind')
    if shader_source_digest(files) != manifest['shader_source_digest']:
        raise ValueError('Pinned shader source identity changed')
    for member, expected in manifest['source_members'].items():
        if member not in files or sha256(files[member]) != expected:
            raise ValueError('Pinned source changed: ' + member)
    result = files.copy()
    for patch in manifest['patches']:
        member = patch['path']
        if member not in manifest['source_members']:
            raise ValueError('Unpinned patch source: ' + member)
        before, after = patch['before'].encode(), patch['after'].encode()
        if not before or result[member].count(before) != 1:
            raise ValueError('Patch anchor must occur exactly once: ' + member)
        result[member] = result[member].replace(before, after, 1)
    for member, local in manifest['additions'].items():
        if member in result:
            raise ValueError('Experiment addition collides: ' + member)
        data = (root / local['path']).read_bytes()
        if sha256(data) != local['sha256']:
            raise ValueError('Experiment helper changed: ' + member)
        result[member] = data
    return result

def build(base, output, manifest_path=MANIFEST):
    base, output = Path(base), Path(output)
    if base.resolve() == output.resolve():
        raise ValueError('Cannot overwrite original pack')
    if output.exists():
        raise ValueError('Output already exists; choose a separate generated copy')
    manifest = json.loads(Path(manifest_path).read_text())
    if sha256(base.read_bytes()) != manifest['release_archive_sha256']:
        raise ValueError('Pinned original archive changed')
    original = read_archive(base)
    files = apply(original, manifest)
    metadata = {'schema': 1, 'kind': manifest['kind'], 'private_experiment_id': manifest['id'],
        'source_version': manifest['version'], 'original_archive_sha256': manifest['release_archive_sha256'],
        'original_shader_source_digest': manifest['shader_source_digest'],
        'experiment_shader_source_digest': shader_source_digest(files),
        'modified_upstream_members': sorted(n for n in original if original[n] != files[n]),
        'added_members_sha256': {n: sha256(files[n]) for n in manifest['additions']},
        'source_members': manifest['source_members'], 'default_enabled': False,
        'transport': 'coreBridgeVersion=2, globalEnabled=1, preset0/1/2; Quality3 and absent bridge full',
        'validation': {'iris_rendered': False, 'dh_rendered': False, 'pbr_rendered': False,
            'quality_checked': False, 'gpu_performance_measured': False}}
    files['shaderlod-private-projected-pom.json'] = (json.dumps(metadata, indent=2, sort_keys=True)+'\n').encode()
    files['SHADERLOD_PRIVATE_PROJECTED_POM.md'] = (
        '# Private projected POM approximation\n\nOriginal Complementary Reimagined r5.9.3 by EminGT.\n'
        + manifest['primary_page']+'\n\n'+manifest['license_note']+'\n\n'
        'Original License.txt and all attribution retained. Private local research only.\n'
        'Default Off: SHADERLOD_PROJECTED_POM=0. Enable1 in Iris then reload with root runtime owner.\n'
        'Global Off, Quality3, missing/invalid core2 bridge preserve full selected primary work.\n'
        'POM_QUALITY, normal/specular equations, four-step self-shadow and slope code retained.\n'
        'Coarser whole-depth primary traversal may change first hit, UV, lighting and shadow outputs.\n'
        'No exact-hit, full-relief pixel-error or performance guarantee. Native terrain raster only.\n'
        'No DH/VOXY/cutout consumption. Driver, effective path, still/motion and GPU timing pending.\n').encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output.parent, delete=False) as temp:
        staging = Path(temp.name)
    try:
        with zipfile.ZipFile(staging, 'w', compression=zipfile.ZIP_DEFLATED) as z:
            for name, data in sorted(files.items()):
                entry = zipfile.ZipInfo(name, (1980,1,1,0,0,0))
                entry.compress_type = zipfile.ZIP_DEFLATED
                entry.external_attr = 0o644 << 16
                z.writestr(entry, data)
        with output.open('xb') as final:
            final.write(staging.read_bytes())
    finally:
        staging.unlink(missing_ok=True)
    return {**metadata, 'output': str(output.resolve()), 'output_sha256': sha256(output.read_bytes())}

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(build(args.base, args.output), indent=2))

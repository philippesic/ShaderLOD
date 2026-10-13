#!/usr/bin/env python3
"""Build explicit member-pinned private adapters; never download or infer a GLSL graph."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
import re
from pathlib import Path, PurePosixPath
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ADAPTERS = ('complementary-reimagined', 'complementary-unbound', 'makeup-ultra-fast-shaders',
    'bsl-shaders', 'solas-shader', 'astralex', 'bliss-shader', 'sildurs-vibrant-shaders')
CATALOG = 'src/main/java/com/pp/shaderlod/packs/SupportedPackCatalog.java'
RESOURCE_ROOT = 'src/main/resources/assets/shaderlod/adapters/'

def sha256(data):
    return hashlib.sha256(data).hexdigest()

def shader_source_digest(files):
    identity = ''.join(name + "\0" + sha256(data) + "\n" for name, data in sorted(files.items())
        if name.endswith((".glsl", ".fsh", ".vsh", ".gsh", ".csh", ".tcs", ".tes", ".vert", ".frag", ".geom", ".comp", ".tesc", ".tese"))
        or (name.startswith("shaders/") and name.endswith(".properties")))
    return sha256(identity.encode())

def read_archive(path):
    with zipfile.ZipFile(path) as archive:
        entries = [entry for entry in archive.infolist() if not entry.is_dir()]
        names = [entry.filename for entry in entries]
        if len(names) != len(set(names)):
            raise ValueError('Duplicate source members')
        for name in names:
            p = PurePosixPath(name)
            if p.is_absolute() or '..' in p.parts or '\\' in name:
                raise ValueError('Unsafe source member: ' + name)
        return {name: archive.read(name) for name in names}

def apply(files, manifest, root=ROOT):
    if manifest.get('schema') != 1 or manifest.get('kind') not in ('ssr-hit-refinement', 'volumetric-sample-budget'):
        raise ValueError('Unsupported adapter schema/kind')
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
            raise ValueError('Adapter addition collides: ' + member)
        data = (root / local['path']).read_bytes()
        if sha256(data) != local['sha256']:
            raise ValueError('Adapter helper changed: ' + member)
        result[member] = data
    return result

def build(base, output, manifest_path, root=ROOT):
    base, output = Path(base), Path(output)
    if base.resolve() == output.resolve():
        raise ValueError('Cannot overwrite original pack')
    if output.exists():
        raise ValueError('Output already exists; choose a separate generated copy')
    manifest = json.loads(Path(manifest_path).read_text())
    files = apply(read_archive(base), manifest, root)
    metadata = {'schema': 1, 'adapter_id': manifest['id'], 'source_version': manifest['version'],
        'original_archive_sha256': sha256(base.read_bytes()), 'source_members': manifest['source_members'],
        'original_shader_source_digest': manifest['shader_source_digest'],
        'adapter_shader_source_digest': shader_source_digest(files),
        'modified_upstream_members': sorted({p['path'] for p in manifest['patches']}),
        'adapter_members': {name: sha256(data) for name, data in files.items()
            if name in manifest['source_members'] or name in manifest['additions']},
        'kind': manifest['kind'], 'option_key': manifest.get('option_key', 'SHADERLOD_REFINEMENT_ADAPTER'),
        'default_enabled': False, 'transport': 'Iris core uniforms bridge 2, reload required',
        'validation': {'glsl_compiled': False, 'iris_rendered': False,
            'dh_rendered': False, 'pbr_rendered': False, 'quality_checked': False, 'performance_measured': False}}
    for reserved in ('shaderlod-adapter.json', 'SHADERLOD_PRIVATE_ADAPTER.md'):
        if reserved in files: raise ValueError('Reserved adapter metadata already present')
    files['shaderlod-adapter.json'] = (json.dumps(metadata, indent=2, sort_keys=True)+'\n').encode()
    files['SHADERLOD_PRIVATE_ADAPTER.md'] = (
        '# ShaderLOD private local adapter\n\nOriginal: '+manifest['name']+' '+manifest['version']+'\n'+
        manifest['primary_page']+'\n\n'+manifest['license_note']+'\n\n'+
        'Default Off. Enable '+metadata['option_key']+'=1 in Iris shader options, then use mod presets. '
        'Off/QUALITY or absent/unknown bridge retains full selected upstream work. Reload after changing controls. '
        'Compilation, rendering, DH, PBR, quality and gain are unverified. Reduced work can change reflection hits or volumetric integration.\n').encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output.parent, delete=False) as temporary:
        staging = Path(temporary.name)
    try:
        with zipfile.ZipFile(staging, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
            for name, data in sorted(files.items()):
                entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                entry.compress_type = zipfile.ZIP_DEFLATED
                entry.external_attr = 0o644 << 16
                archive.writestr(entry, data)
        # Recheck to avoid replacing a concurrent output creation.
        with output.open('xb') as final:
            final.write(staging.read_bytes())
    finally:
        staging.unlink(missing_ok=True)
    return {**metadata, 'output_sha256': sha256(output.read_bytes()), 'output': str(output)}

def contained(root, name):
    """Authoring targets are literal repository paths, never traversal or symlinks."""
    pure = PurePosixPath(name)
    if pure.is_absolute() or '..' in pure.parts or '\\' in name or not name or '\0' in name:
        raise ValueError('Unsafe authoring path: ' + name)
    path = root / name
    if not path.resolve().is_relative_to(root.resolve()):
        raise ValueError('Authoring path escapes root: ' + name)
    if any((root / Path(*pure.parts[:i])).is_symlink() for i in range(1, len(pure.parts) + 1)):
        raise ValueError('Symlink authoring path: ' + name)
    return path

def catalog_update(source, adapter, original, assembled):
    """Change one current identity and retain its previous exact recognition."""
    pattern = (r'(new Pack\("' + re.escape(adapter) + r'",[^)]*?'
        r'"([0-9a-f]{64})",\s*")([0-9a-f]{64})("\))')
    matches = list(re.finditer(pattern, source))
    if len(matches) != 1:
        raise ValueError('Expected one exact current catalog entry: ' + adapter)
    match = matches[0]
    if match.group(2) != original:
        raise ValueError('Catalog original source identity mismatch: ' + adapter)
    previous = match.group(3)
    if previous == assembled:
        return source
    updated = source[:match.start(3)] + assembled + source[match.end(3):]
    blocks = list(re.finditer(r'(LEGACY_ADAPTER_DIGESTS = Map\.ofEntries\()(.*?)(\);)', updated, re.S))
    if len(blocks) != 1:
        raise ValueError('Expected one legacy identity map')
    block = blocks[0]
    body = block.group(2)
    entries = list(re.finditer(r'Map\.entry\("' + re.escape(adapter) + r'", List\.of\(([^)]*)\)\)', body))
    if len(entries) > 1:
        raise ValueError('Ambiguous legacy identity entry: ' + adapter)
    if entries:
        entry = entries[0]
        values = entry.group(1)
        if not re.fullmatch(r'\s*"[0-9a-f]{64}"(?:\s*,\s*"[0-9a-f]{64}")*\s*', values):
            raise ValueError('Unsupported legacy identity syntax: ' + adapter)
        if previous not in re.findall(r'"([0-9a-f]{64})"', values):
            body = body[:entry.end(1)] + ', "' + previous + '"' + body[entry.end(1):]
    else:
        body = '\n        Map.entry("' + adapter + '", List.of("' + previous + '")),' + body
    return updated[:block.start(2)] + body + updated[block.end(2):]

def sync_resources(base, adapter, root=ROOT, write=False):
    """Propagate one pinned adapter into shared resources/catalog; dry-run by default.

    All inputs are validated before writes. Files replace atomically individually;
    this is not a cross-file transaction. An interrupted run can be rerun to finish.
    """
    root = Path(root)
    if adapter not in ADAPTERS:
        raise ValueError('Unsupported maintained adapter: ' + adapter)
    recipe_name = 'patches/adapters/' + adapter + '.json'
    recipe_bytes = contained(root, recipe_name).read_bytes()
    manifest = json.loads(recipe_bytes)
    if manifest.get('id') != adapter:
        raise ValueError('Maintained adapter identity mismatch')
    inputs = {recipe_name: recipe_bytes}
    for addition in manifest['additions'].values():
        name = addition['path']
        if not name.startswith('patches/adapters/'):
            raise ValueError('Helper outside maintained adapters: ' + name)
        inputs[name] = contained(root, name).read_bytes()
    # A shared helper cannot be propagated while another maintained consumer pins
    # different bytes. This operation never silently refreshes source/helper pins.
    for other in sorted((root / 'patches/adapters').glob('*.json')):
        consumer = json.loads(other.read_bytes())
        for addition in consumer.get('additions', {}).values():
            name = addition['path']
            if name in inputs and sha256(inputs[name]) != addition['sha256']:
                raise ValueError('Shared helper consumer pin mismatch: ' + other.name + ': ' + name)
        if other.name != adapter + '.json':
            packaged = contained(root, RESOURCE_ROOT + 'patches/adapters/' + other.name)
            if packaged.exists():
                for addition in json.loads(packaged.read_bytes()).get('additions', {}).values():
                    name = addition['path']
                    if name in inputs and sha256(inputs[name]) != addition['sha256']:
                        raise ValueError('Packaged shared helper consumer pin mismatch: ' + other.name + ': ' + name)
    assembled = apply(read_archive(base), manifest, root)
    digest = shader_source_digest(assembled)
    catalog_path = contained(root, CATALOG)
    catalog_bytes = catalog_path.read_bytes()
    catalog_after = catalog_update(catalog_bytes.decode(), adapter,
        manifest['shader_source_digest'], digest).encode()
    outputs = {RESOURCE_ROOT + name: data for name, data in inputs.items()}
    outputs[CATALOG] = catalog_after
    targets = {name: contained(root, name) for name in outputs}
    before = {name: path.read_bytes() if path.exists() else None for name, path in targets.items()}
    changes = [name for name in outputs if outputs[name] != before[name]]
    if write:
        # Detect edits made during validation before mutating any target.
        for name, data in inputs.items():
            if contained(root, name).read_bytes() != data:
                raise ValueError('Authoring input changed during sync: ' + name)
        if catalog_path.read_bytes() != catalog_bytes:
            raise ValueError('Catalog changed during sync')
        for name, path in targets.items():
            current = path.read_bytes() if path.exists() else None
            if current != before[name]:
                raise ValueError('Authoring target changed during sync: ' + name)
        for name in changes:
            path = targets[name]
            path.parent.mkdir(parents=True, exist_ok=True)
            with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as temporary:
                staging = Path(temporary.name)
                temporary.write(outputs[name])
            try:
                staging.chmod(path.stat().st_mode & 0o777 if path.exists() else 0o644)
                os.replace(staging, path)
            finally:
                staging.unlink(missing_ok=True)
    return {'adapter': adapter, 'adapter_shader_source_digest': digest,
        'applied': write, 'changed_files': changes}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adapter', required=True, choices=ADAPTERS)
    parser.add_argument('--base', type=Path, required=True)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--sync-resources', action='store_true', help='Plan shared resource/catalog propagation instead of a ZIP')
    parser.add_argument('--apply', action='store_true', help='Write the validated resource sync (otherwise dry-run)')
    args = parser.parse_args()
    if args.sync_resources:
        if args.output: parser.error('--output cannot be combined with --sync-resources')
        result = sync_resources(args.base, args.adapter, write=args.apply)
    else:
        if args.apply: parser.error('--apply requires --sync-resources')
        if not args.output: parser.error('--output is required unless --sync-resources is selected')
        result = build(args.base, args.output, ROOT / 'patches/adapters' / (args.adapter+'.json'))
    print(json.dumps(result, indent=2))

if __name__ == '__main__': main()

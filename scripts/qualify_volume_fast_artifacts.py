#!/usr/bin/env python3
"""Independently pin the volume diagnostic's physical and nested mod artifacts.

No client report is an input. Only the pinned JarJar/securejar mount counter is
canonicalized; outer path, nested member, mod metadata and bytes remain exact.
"""
import argparse
import copy
import hashlib
import io
import json
import os
from pathlib import Path
import re
import tomllib
from urllib.parse import quote
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RECORD_FIELDS = {'mods', 'path', 'uri', 'filesystem_provider', 'identity'}
KIND = 'development directory; regular files only, symlinks excluded'
PINS = {
    'build/moddev/artifacts/neoforge-21.1.253.jar': 'a3f878f97a229b543a2c872c4a3c03b19ebd3efbd11af2caed9c258c08aebf55',
    'dependencies/runtime/iris-neoforge-1.8.12+mc1.21.1.jar': '3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83',
    'dependencies/runtime/sodium-neoforge-0.6.13+mc1.21.1.jar': '7acd7af2ef75ec883a6872c17494ead65e38025151eb08a1d60e36d34377f403',
}
MODS = {
    'minecraft': '1.21.1', 'neoforge': '21.1.253',
    'iris': '1.8.12-snapshot+mc1.21.1-local', 'sodium': '0.6.13+mc1.21.1',
    'shaderlod': '0.1.0-dev', 'shaderlod_dev': '0.1.0',
    'fabric_api_base': '0.4.42+d1308ded19',
    'fabric_block_view_api_v2': '1.0.10+9afaaf8c19',
    'fabric_renderer_api_v1': '3.4.0+acb05a3919',
    'fabric_rendering_data_attachment_v1': '0.3.48+73761d2e19',
}
DIRS = ('build/classes/java/main', 'build/classes/java/devClient',
        'build/resources/main', 'build/resources/devClient')


def digest(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''): h.update(block)
    return h.hexdigest()


def directory_identity(root):
    root = Path(root)
    if not root.is_dir() or root.is_symlink(): raise ValueError('Missing ordinary directory: ' + str(root))
    files = []
    for base, dirs, names in os.walk(root, followlinks=False):
        dirs[:] = [n for n in dirs if not (Path(base) / n).is_symlink()]
        files.extend(Path(base) / n for n in names if not (Path(base) / n).is_symlink() and (Path(base) / n).is_file())
    files.sort(key=str)
    count, size = len(files), sum(p.stat().st_size for p in files)
    if not 1 <= count <= 5000 or not 1 <= size <= 32 * 1024 * 1024:
        raise ValueError('Directory exceeds complete Java identity bounds or is empty')
    h = hashlib.sha256()
    for p in files: h.update((p.relative_to(root).as_posix() + '\0' + digest(p) + '\n').encode())
    return dict(kind=KIND, complete=True, files=count, bytes=size, tree_sha256=h.hexdigest())


def canonical_nested_uri(uri, *, expected=False):
    """Only accept the exact single-level jij layout reproduced with pinned jars.

    Expected records omit the process-global parent Union index. Actual records
    must have the escaped numeric index immediately before !/. There is no
    suffix, percent-unquoting, generic normalization or descendant matching.
    """
    if not isinstance(uri, str): raise ValueError('Missing nested URI')
    match = re.fullmatch(r'jij:(/[^%!#?~]+\.jar)(?:%23([0-9]+))?!/(META-INF/jarjar/[^/%!#?~]+\.jar)', uri)
    if not match or (not expected and match[2] is None): raise ValueError('Unknown pinned jij locator layout')
    if any(part in ('.', '..') for part in match[1].split('/')):
        raise ValueError('Noncanonical parent path')
    return 'jij:' + match[1] + '!/' + match[3]


def java_file_uri(path, *, directory=False):
    """Exact Unix JDK Path.toUri encoding, derived independently for future receipts.

    RFC path characters such as literal '+' stay literal. A '%' in the physical
    filename is encoded as %25; no decoding/alias acceptance is performed.
    """
    if not isinstance(path, str) or not Path(path).is_absolute() or str(Path(path)) != path or '\0' in path:
        raise ValueError('Ordinary artifact absolute path missing')
    if any(part in ('.', '..') for part in path.split('/')) or path.startswith('//'):
        raise ValueError('Noncanonical ordinary artifact path')
    return 'file://' + quote(path, safe="/:@&=+$,;!'()*") + ('/' if directory else '')


def normalize_record_locator(record, *, expected=False):
    if not isinstance(record, dict) or set(record) != RECORD_FIELDS: raise ValueError('Artifact locator schema differs')
    mods = record['mods']
    if not isinstance(mods, dict) or not mods or any(not isinstance(k, str) or not k or not isinstance(v, str) or not v for k, v in mods.items()):
        raise ValueError('Artifact mod identity missing')
    result = copy.deepcopy(record)
    if record['filesystem_provider'] == 'jij':
        if record['path'] != '' or not re.fullmatch('[0-9a-f]{64}', str(record['identity'])):
            raise ValueError('Nested path/content identity unknown')
        result['uri'] = canonical_nested_uri(record['uri'], expected=expected)
    elif record['filesystem_provider'] == 'file':
        path = record['path']
        if not isinstance(path, str) or not Path(path).is_absolute() or str(Path(path)) != path:
            raise ValueError('Ordinary artifact absolute path missing')
        if record['uri'] != java_file_uri(path, directory=isinstance(record['identity'], dict)):
            raise ValueError('Ordinary artifact URI/path mismatch')
        value = record['identity']
        if isinstance(value, dict):
            if set(value) != {'kind', 'complete', 'files', 'bytes', 'tree_sha256'} or value['kind'] != KIND or value['complete'] is not True:
                raise ValueError('Directory identity incomplete')
            if type(value['files']) is not int or type(value['bytes']) is not int or not 1 <= value['files'] <= 5000 or not 1 <= value['bytes'] <= 32 * 1024 * 1024:
                raise ValueError('Directory identity outside bounds')
            value = value['tree_sha256']
        if not isinstance(value, str) or not re.fullmatch('[0-9a-f]{64}', value): raise ValueError('Content identity missing')
    else: raise ValueError('Unqualified filesystem provider')
    return result


def artifact_locator_matches(actual_record, expected_record):
    return normalize_record_locator(actual_record) == normalize_record_locator(expected_record, expected=True)


def inventory_group_alternatives(group):
    if isinstance(group, dict) and set(group) == {'alternatives'}:
        alternatives = group['alternatives']
        if not isinstance(alternatives, list) or not alternatives: raise ValueError('Empty independent alternatives')
        first = alternatives[0]['mods']
        for record in alternatives:
            normalize_record_locator(record, expected=True)
            if record['mods'] != first or record['filesystem_provider'] != 'jij': raise ValueError('Alternative mod coverage/provider differs')
        if len({json.dumps(r, sort_keys=True) for r in alternatives}) != len(alternatives): raise ValueError('Duplicate independent alternative')
        return alternatives
    normalize_record_locator(group, expected=True)
    return [group]


def validate_inventory_against_receipt(actual_inventory, expected_inventory, loaded_mods):
    if not isinstance(actual_inventory, list) or not isinstance(expected_inventory, list) or not expected_inventory:
        raise ValueError('Complete independent inventory required')
    if len(actual_inventory) != len(expected_inventory): raise ValueError('Artifact root count differs')
    used, locators, mods = set(), set(), {}
    for actual in actual_inventory:
        normalize_record_locator(actual)
        locator = (actual['filesystem_provider'], actual['uri'])
        if locator in locators: raise ValueError('Duplicate actual artifact locator')
        locators.add(locator)
        if set(mods) & set(actual['mods']): raise ValueError('Duplicate loaded mod identity')
        mods.update(actual['mods'])
        matches = [i for i, group in enumerate(expected_inventory)
                   if any(artifact_locator_matches(actual, candidate) for candidate in inventory_group_alternatives(group))]
        if len(matches) != 1 or matches[0] in used: raise ValueError('Actual artifact differs from independent frozen inventory')
        used.add(matches[0])
    if mods != loaded_mods: raise ValueError('Incomplete actual mod coverage')
    return True


def metadata_mods(data):
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        metadata = next((n for n in ('META-INF/neoforge.mods.toml', 'META-INF/mods.toml') if n in z.namelist()), None)
        if metadata is None: return {}
        unfolded = z.read('META-INF/MANIFEST.MF').decode().replace('\r\n ', '') if 'META-INF/MANIFEST.MF' in z.namelist() else ''
        manifest = dict(line.split(': ', 1) for line in unfolded.splitlines() if ': ' in line)
        result = {}
        for mod in tomllib.loads(z.read(metadata).decode())['mods']:
            version = mod['version']
            if version == '${file.jarVersion}': version = manifest.get('Implementation-Version')
            if not version or '${' in version: raise ValueError('Unresolved mod version')
            if mod['modId'] in result: raise ValueError('Duplicate metadata mod')
            result[mod['modId']] = version
        return result


def record(path, mods, identity):
    return dict(mods=mods, path=str(path), uri=java_file_uri(str(path), directory=isinstance(identity, dict)), filesystem_provider='file', identity=identity)


def expected_mods(include_shaderlod=True):
    if type(include_shaderlod) is not bool: raise ValueError('include_shaderlod must be an explicit boolean')
    return {name: version for name, version in MODS.items() if include_shaderlod or name != 'shaderlod'}


def mod_absent_launch_contract(root):
    root = Path(root).resolve()
    return dict(gradle_task='runClientLodOff', classpath_file=str(root / 'build/moddev/clientLodOffLegacyClasspath.txt'),
        registered_development_mods=['shaderlod_dev'],
        fml_mod_folders='shaderlod_dev%%' + str(root / 'build/classes/java/devClient') + ':shaderlod_dev%%' + str(root / 'build/resources/devClient'))


def build_receipt(root=ROOT, *, include_shaderlod=True):
    root = Path(root).resolve()
    required_mods = expected_mods(include_shaderlod)
    classpath_relative = 'build/moddev/clientLodOnLegacyClasspath.txt' if include_shaderlod else 'build/moddev/clientLodOffLegacyClasspath.txt'
    evidence, directories, inventory, nested = {}, {}, [], {}
    for relative, pin in PINS.items():
        path = root / relative
        if digest(path) != pin: raise ValueError('Pinned external artifact changed: ' + str(path))
        evidence[str(path)] = pin
        if 'neoforge-21' in relative:
            inventory.append(record(path, {k: MODS[k] for k in ('minecraft', 'neoforge')}, pin))
            continue
        data = path.read_bytes()
        mods = metadata_mods(data)
        inventory.append(record(path, mods, pin))
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            jarjar = json.loads(z.read('META-INF/jarjar/metadata.json'))
            for declaration in jarjar['jars']:
                member = declaration['path']
                b = z.read(member)
                ids = metadata_mods(b)
                if not ids: continue  # Libraries are not ModList entries.
                if any(MODS.get(k) != v for k, v in ids.items()): continue  # Renderer older Sodium candidate is not this preregistered mod version.
                item = dict(mods=ids, path='', uri='jij:' + str(path) + '!/' + member, filesystem_provider='jij', identity=hashlib.sha256(b).hexdigest())
                normalize_record_locator(item, expected=True)
                key = tuple(sorted(ids.items()))
                nested.setdefault(key, []).append(item)
    inventory.extend({'alternatives': entries} for key, entries in sorted(nested.items()))
    for relative in DIRS:
        path = root / relative
        directories[str(path)] = directory_identity(path)
        if relative.startswith('build/classes'):
            mod = 'shaderlod' if relative.endswith('/main') else 'shaderlod_dev'
            metadata = root / ('build/resources/main/META-INF/neoforge.mods.toml' if mod == 'shaderlod' else 'build/resources/devClient/META-INF/neoforge.mods.toml')
            actual = tomllib.loads(metadata.read_text())['mods'][0]
            if (actual['modId'], actual['version']) != (mod, MODS[mod]): raise ValueError('Development metadata pin differs')
            evidence[str(metadata)] = digest(metadata)
            if include_shaderlod or mod != 'shaderlod':
                inventory.append(record(path, {mod: MODS[mod]}, directories[str(path)]))
    proofs = ['scripts/qualify_volume_fast_artifacts.py', 'build.gradle', 'dependencies/runtime.lock.json',
              classpath_relative]
    # Bytecode and standalone real-provider reproduction establish the only URI transformation.
    proofs.extend(str(p.relative_to(root)) for p in sorted((root / 'build/volume-fast-evidence-source').glob('*.txt')))
    proofs.extend(str(p.relative_to(root)) for p in sorted((root / 'build/volume-fast-evidence-source').glob('*.java')))
    proofs.extend(str(p.relative_to(root)) for p in sorted((root / 'build/volume-fast-evidence-source').glob('*.log')))
    # Separate JDK-only URI reproduction retained after the failed r1 receipt.
    for suffix in ('*.txt', '*.java', '*.log'):
        proofs.extend(str(p.relative_to(root)) for p in sorted((root / 'build/volume-fast-evidence-r2').glob(suffix)))
    if not include_shaderlod:
        proofs.extend(['src/devClient/java/com/pp/shaderlod/dev/LoadedArtifactEvidence.java',
            'src/devClient/java/com/pp/shaderlod/dev/WorkloadSession.java'])
        for suffix in ('*.txt', '*.java', '*.log', 'mod-absent-launch-source.json'):
            proofs.extend(str(p.relative_to(root)) for p in sorted((root / 'build/taau-artifact-evidence-r1').glob(suffix)))
    classpath = root / classpath_relative
    for name in classpath.read_text().splitlines():
        if any(s in name for s in ('securejarhandler/3.0.8/', 'JarJarFileSystems/0.4.1/', 'fancymodloader/loader/4.0.45/')):
            evidence[name] = digest(name)
    for relative in proofs: evidence[str(root / relative)] = digest(root / relative)
    receipt = dict(schema=1, qualification='independent pinned external receipt; build/runtime acceptance remains separate',
        loaded_directory_identities=directories, loaded_artifact_inventory=inventory,
        include_shaderlod=include_shaderlod, expected_loaded_mods=required_mods, independent_evidence_files_sha256=evidence,
        provenance='No client report used. Exact physical pins + upstream nested metadata/member bytes; two modFiles sharing one physical NeoForge primary path coalesced. Exact frozen alternatives accommodate independently pinned JarJar selection. Only source-proven parent numeric Union mount index canonicalized; no path/member/content/mod normalization. Class/resources are fresh filesystem freezes, not runtime loaded proof.')
    if not include_shaderlod:
        receipt['launch_artifact_contract'] = mod_absent_launch_contract(root)
        receipt['provenance'] += ' Explicit mod-absent mode omits only production shaderlod; clientLodOff Gradle loadedMods/sourceSet declaration and Off legacy classpath are independently frozen. All four auxiliary roots remain frozen; shaderlod_dev remains required.'
    validate_expected_receipt(receipt, directories, include_shaderlod=include_shaderlod)
    return receipt


def validate_expected_receipt(receipt, directories, *, include_shaderlod=True):
    required_mods = expected_mods(include_shaderlod)
    if not isinstance(receipt, dict) or receipt.get('expected_loaded_mods') != required_mods:
        raise ValueError('Independent expected complete mod set differs')
    if receipt.get('include_shaderlod', True) is not include_shaderlod:
        raise ValueError('Independent explicit mod presence mode differs')
    if receipt.get('loaded_directory_identities') != directories or len(directories) != 4:
        raise ValueError('Independent four-root freeze differs')
    inventory = receipt.get('loaded_artifact_inventory')
    if not isinstance(inventory, list): raise ValueError('Expected inventory missing')
    mods = {}
    pinned_paths = {name: pin for name, pin in receipt.get('independent_evidence_files_sha256', {}).items()
                    if any(name.endswith('/' + relative) and pin == wanted for relative, wanted in PINS.items())}
    for group in inventory:
        candidates = inventory_group_alternatives(group)
        identities = candidates[0]['mods']
        if set(mods) & set(identities): raise ValueError('Duplicate expected mod coverage')
        mods.update(identities)
        for candidate in candidates:
            if candidate['filesystem_provider'] == 'file':
                path = candidate['path']
                expected = directories.get(path) if isinstance(candidate['identity'], dict) else digest(path)
                if candidate['identity'] != expected: raise ValueError('Expected physical artifact freeze differs')
                if isinstance(candidate['identity'], dict):
                    relative = 'build/classes/java/main' if candidate['mods'] == {'shaderlod': MODS['shaderlod']} else 'build/classes/java/devClient' if candidate['mods'] == {'shaderlod_dev': MODS['shaderlod_dev']} else None
                    if relative is None or not path.endswith('/' + relative): raise ValueError('Unqualified development artifact root')
                else:
                    if pinned_paths.get(path) != candidate['identity']: raise ValueError('Unqualified physical artifact pin')
                    if 'neoforge-21.1.253.jar' in path:
                        if candidate['mods'] != {k: MODS[k] for k in ('minecraft', 'neoforge')}: raise ValueError('NeoForge/Minecraft coalesced coverage differs')
                    elif metadata_mods(Path(path).read_bytes()) != candidate['mods']: raise ValueError('Physical mod metadata differs')
            else:
                uri = canonical_nested_uri(candidate['uri'], expected=True)
                parent, member = uri[4:].split('!/', 1)
                if parent not in pinned_paths or '/dependencies/runtime/' not in parent: raise ValueError('Unqualified nested parent artifact')
                with zipfile.ZipFile(parent) as z:
                    declarations = json.loads(z.read('META-INF/jarjar/metadata.json'))['jars']
                    if member not in {d['path'] for d in declarations}: raise ValueError('Nested member absent from parent JarJar declaration')
                    b = z.read(member)
                    if candidate['identity'] != hashlib.sha256(b).hexdigest(): raise ValueError('Expected nested member bytes differ')
                    if metadata_mods(b) != candidate['mods']: raise ValueError('Expected nested mod metadata differs')
    if mods != required_mods: raise ValueError('Incomplete expected mod coverage')
    if not include_shaderlod:
        dev_roots = [name for name in directories if name.endswith('/build/classes/java/devClient')]
        if len(dev_roots) != 1: raise ValueError('Mod-absent dev root is not exact')
        contract = mod_absent_launch_contract(Path(dev_roots[0]).parents[3])
        if receipt.get('launch_artifact_contract') != contract:
            raise ValueError('Mod-absent launch contract differs')
        if contract['classpath_file'] not in receipt.get('independent_evidence_files_sha256', {}):
            raise ValueError('Mod-absent Off legacy classpath provenance missing')
    evidence = receipt.get('independent_evidence_files_sha256')
    if not isinstance(evidence, dict) or not evidence: raise ValueError('Independent source proof files missing')
    for name, pin in evidence.items():
        if not Path(name).is_absolute() or digest(name) != pin: raise ValueError('Independent proof file changed: ' + name)
    for relative, pin in PINS.items():
        names = [p for p in evidence if p.endswith('/' + relative)]
        if len(names) != 1 or evidence[names[0]] != pin: raise ValueError('Pinned artifact provenance missing')
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--include-shaderlod', choices=('true', 'false'), default='true')
    args = parser.parse_args()
    if args.output.exists(): parser.error('Refuse to overwrite an independent receipt; choose a new revision path')
    receipt = build_receipt(include_shaderlod=args.include_shaderlod == 'true')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(receipt, indent=2, sort_keys=True) + '\n')
    print(json.dumps({'receipt': str(args.output.resolve()), 'sha256': digest(args.output), 'expected_mods': len(receipt['expected_loaded_mods']), 'artifact_roots': len(receipt['loaded_artifact_inventory']), 'runtime_verified': False}))


if __name__ == '__main__': main()

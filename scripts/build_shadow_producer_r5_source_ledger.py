#!/usr/bin/env python3
"""Reproduce the R5 private, source-only shadow inventory; builds no shader pack.

The literal-include graph and token inventory deliberately do not claim compiler
reachability or effective options. Original archives are read only, hash pinned,
and extracted evidence remains under ignored dev/logs/.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import posixpath
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PINS = {
    "photon": "120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead",
    "complementary-reimagined": "fed6c879e732cf7fc7bc6570ebb1def43235aa7e96f62859fbdbdbfa0b484b38",
    "complementary-unbound": "98ac741dee294d2a3c82d153349148c3bb7fb688c30eb3b49c3b9ac37e968c5a",
    "bsl-shaders": "36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5",
    "makeup-ultra-fast-shaders": "0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8",
    "sildurs-vibrant-shaders": "824504bc937f9570277c0a809f5059ae4a5fe70c413cf4459b6ccff4dfed7ebe",
    "solas-shader": "b7dfe4c7353d677a92cc4c86b87bc0633f1014305d927ca6a482c303427bf825",
    "bliss-shader": "f41db92acc585fe9ffe0cc124d6ecaea27cef9ded91129ebc2a8e60226947b2c",
    "astralex": "70b6d8127a6733f0a19bd1d351b92709d6ee7496967ff49c4793d2c28c7a6c58",
}
IDS = ("B1", "B2", "B3", "B4", "B5", "B26")
COMMENT = re.compile(r'/\*.*?\*/|//[^\n]*', re.S)
INCLUDE = re.compile(r'^\s*#\s*include\s+["<]([^">]+)[">]', re.M)
TOKEN = re.compile(r'\b(?:shadowtex\d*|shadowcolor\d*|gshadow|shadow)\b')
CALL = re.compile(r'\b(\w+)\s*\(\s*(shadowtex\d*|shadowcolor\d*|gshadow|shadow)\b')
PROGRAM = re.compile(r'^shaders/(?:world[^/]+/)?[^/]+\.(?:fsh|vsh|gsh|csh|tcs|tes)$')
OUTPUT = ROOT / 'dev/logs/mechanism-evaluation-r5/shadow-producer'


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def code_only(text: str) -> str:
    # Keep line numbers. GLSL source has no string literals except directives;
    # quoted include paths in these pinned archives do not contain comment tokens.
    return COMMENT.sub(lambda m: '\n' * m.group().count('\n'), text)


def include_target(member: str, target: str) -> str:
    result = posixpath.normpath('shaders/' + target.lstrip('/') if target.startswith('/')
                               else posixpath.join(posixpath.dirname(member), target))
    if not result.startswith('shaders/'):
        raise ValueError('include escapes shader root: ' + result)
    return result


def closure(start: str, graph: dict[str, list[str]]) -> set[str]:
    seen, pending = set(), [start]
    while pending:
        current = pending.pop()
        if current in seen:
            continue
        seen.add(current)
        pending.extend(graph.get(current, ()))
    return seen


def inspect_pack(pin: dict, output: Path) -> dict:
    pack = pin['pack']
    path = ROOT / pin['file']
    raw = path.read_bytes()
    if pack not in PINS or pin['sha256'] != PINS[pack] or sha(raw) != PINS[pack]:
        raise ValueError('archive identity mismatch: ' + pack)
    texts, members, license_members, binary_members = {}, [], [], []
    with zipfile.ZipFile(path) as archive:
        for item in archive.infolist():
            if item.is_dir():
                continue
            name = item.filename
            if name.startswith('/') or '..' in Path(name).parts:
                raise ValueError('unsafe archive member: ' + name)
            data = archive.read(item)
            record = {'member': name, 'sha256': sha(data), 'bytes': len(data)}
            members.append(record)
            if re.search(r'license|licence|terms|copyright', name, re.I):
                license_members.append(record)
            try:
                text = data.decode('utf-8-sig')
                if '\0' in text:
                    raise UnicodeError('binary NUL')
            except UnicodeError:
                binary_members.append(name)
                continue
            dest = output / 'sources' / pack / name
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(data)
            if name.startswith('shaders/'):
                texts[name] = text
    codes = {k: code_only(v) for k, v in texts.items()}
    graph = {k: sorted({include_target(k, t) for t in INCLUDE.findall(v)})
             for k, v in codes.items()}
    unresolved = [{'from': k, 'include': dep} for k, ds in graph.items()
                  for dep in ds if dep not in codes]
    programs = {k: closure(k, graph) for k in codes if PROGRAM.match(k)}
    reads, tokens, side_effects = [], [], []
    for name, code in codes.items():
        for line, source in enumerate(code.splitlines(), 1):
            if TOKEN.search(source):
                tokens.append({'member': name, 'line': line, 'code': source.strip()})
            matches = CALL.findall(source)
            if matches:
                reads.append({'member': name, 'line': line, 'code': source.strip(),
                              'calls': matches,
                              'literal_include_callers': sorted(p for p, deps in programs.items() if name in deps)})
            if re.search(r'\b(?:imageStore|imageAtomic\w*|atomic\w*|[Uu]pdate\w*Voxel\w*|PopulateShadowVoxel)\s*\(', source):
                side_effects.append({'member': name, 'line': line, 'code': source.strip(),
                                     'literal_include_callers': sorted(p for p, deps in programs.items() if name in deps)})
    result = {'pin': pin, 'verified_sha256': sha(raw), 'license_members': license_members,
              'all_members': members, 'text_shader_members': len(texts), 'binary_members': binary_members,
              'literal_include_graph': graph, 'unresolved_literal_includes': unresolved,
              'shadow_token_sites': tokens, 'shadow_first_argument_call_sites': reads,
              'image_or_voxel_sites': side_effects,
              'scope': 'Lexical superset, includes inactive branches and generic sampler parameters. No linked-program or alias-completeness claim.',
              'runtime_certificate': None, 'candidate_created': False}
    (output / f'{pack}-source-ledger.json').write_text(json.dumps(result, indent=2) + '\n')
    return {'pack': pack, 'archive_sha256': sha(raw), 'members': len(members),
            'token_sites': len(tokens), 'first_argument_call_sites': len(reads),
            'unresolved_includes': len(unresolved), 'license_metadata': pin['license_metadata'],
            'license_members': license_members}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=OUTPUT)
    args = parser.parse_args()
    output = args.output.resolve()
    if not output.is_relative_to(OUTPUT.resolve()):
        raise ValueError('evidence output must stay in the assigned ignored directory')
    output.mkdir(parents=True, exist_ok=True)
    pin_file = ROOT / 'dev/logs/generic-lod-strategy-r1/lead/artifact-pins.json'
    pins = [p for p in json.loads(pin_file.read_text()) if 'pack' in p]
    if len(pins) != len(PINS) or {p['pack'] for p in pins} != set(PINS):
        raise ValueError('nine exact variant pins required')
    results = [inspect_pack(p, output) for p in pins]
    receipt = {'source_ledger_input_sha256': sha(pin_file.read_bytes()), 'packs': results,
               'mechanism_ids': IDS, 'required_cells': 54, 'runtime_tested': False,
               'candidate_created': False, 'local_jobs_started': [],
               'limitations': ['No preprocessing/effective-option inference.',
                               'Generic sampler aliases require semantic review.',
                               'Lexical inclusion is not function liveness or runtime execution.']}
    (output / 'source-receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(json.dumps({'variants': len(results), 'required_cells': 54, 'candidate_created': False,
                      'receipt': str(output / 'source-receipt.json')}))


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""Build a private pinned MakeUp bloom-copy liveness experiment, never install it.

An extra logical flip after the last bloom read removes target 0 from Iris's
terminal persistence-copy set. It does not rotate physical resources or change
shader arithmetic. Runtime graph, quality and cost validation remain required.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NATIVE = "0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8"
NATIVE_OPTIONS = "2497984ec9a706f6a21f5f853c44a886dcbd83bcb74eea9be8c31bcdef861ba2"
MEMBER = "shaders/shaders.properties"
COMMENT = b"\n# ShaderLOD private G01 dead persistence-copy experiment\n"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def build(original: Path, output: Path, sidecar: Path, targets=(0,)):
    if not targets or len(set(targets)) != len(targets) or not set(targets) <= {0, 7}:
        raise ValueError("Only distinct proven candidate targets 0 and 7 are supported")
    directive = COMMENT + ''.join(f'flip.composite2.colortex{n}=true\n' for n in targets).encode()
    if sha(original.read_bytes()) != NATIVE:
        raise ValueError("Pinned native MakeUp archive required")
    if output.exists() or Path(str(output) + '.txt').exists():
        raise ValueError("Fresh output and sidecar required")
    if not output.resolve().is_relative_to(ROOT):
        raise ValueError("Use an ignored project artifact directory")
    relative = str(output.resolve().relative_to(ROOT))
    if subprocess.run(['git', 'check-ignore', '-q', '--', relative], cwd=ROOT).returncode:
        raise ValueError("Generated archive must be ignored by Git")
    options = sidecar.read_bytes()
    if sha(options) != NATIVE_OPTIONS:
        raise ValueError("Pinned native100 AA2/BLOOM-on option sidecar required")
    with zipfile.ZipFile(original) as source:
        names = source.namelist()
        if len(names) != len(set(names)) or MEMBER not in names:
            raise ValueError("Duplicate members or properties missing")
        if any(n.startswith('/') or '..' in Path(n).parts for n in names):
            raise ValueError("Unsafe source archive member")
        text = source.read(MEMBER)
        if b'flip.' in text:
            raise ValueError("Unexpected native flip directives")
        output.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(output, 'x') as candidate:
            for info in source.infolist():
                data = source.read(info.filename)
                candidate.writestr(copy.copy(info), data + directive if info.filename == MEMBER else data)
        with zipfile.ZipFile(output) as candidate:
            assert candidate.namelist() == names
            changed = [n for n in names if source.read(n) != candidate.read(n)]
            assert changed == [MEMBER]
            assert candidate.read(MEMBER) == text + directive
    Path(str(output) + '.txt').write_bytes(options)
    receipt = {
        'scope': 'Private source experiment; no installation or runtime acceptance',
        'original': str(original), 'original_sha256': NATIVE,
        'candidate': str(output), 'candidate_sha256': sha(output.read_bytes()),
        'changed_members': changed, 'appended_directive': directive.decode(), 'targets': list(targets),
        'native_sidecar_sha256': sha(options),
        'mechanism': 'Composite2 explicit flips of non-output dead targets after their last reads; terminal flipped membership becomes false, removing persistence copies. Earlier pass FBOs and samplers unchanged.',
        'source_liveness': {'0': 'Composite bloom producer -> composite1 sole bloom sampler; composite2/final do not sample0.', '7': 'Prepare writes fog via DRAWBUFFERS17 without sampling7; subsequent geometry samplesgaux4; composite2/final do not sample7. Shadow does not consume7. Earlier geometry sampler snapshots remain unchanged.'},
        'conditions': 'Exact native source, selected native100 options, Overworld AA2/BLOOM on; runtime source/graph/copy count and quality/cost checks still required.',
        'restoration': 'Select preserved original archive and native sidecar, reload; no runtime ownership or native object mutation.',
    }
    Path(str(output) + '.json').write_text(json.dumps(receipt, indent=2) + '\n')
    return receipt


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('original', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--options', type=Path, required=True)
    parser.add_argument('--targets', type=int, nargs='+', choices=(0, 7), default=[0])
    args = parser.parse_args()
    print(json.dumps(build(args.original, args.output, args.options, tuple(args.targets)), indent=2))

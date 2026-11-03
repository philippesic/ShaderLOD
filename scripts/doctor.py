#!/usr/bin/env python3
"""Check pinned downloads and summarize actual local runtime evidence."""
import hashlib
import json
import platform
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    lock = json.loads((ROOT / 'dependencies/runtime.lock.json').read_text())
    result = {'host': platform.platform(), 'stack': {}, 'runs': {}}
    failures = []
    for name, artifact in lock['artifacts'].items():
        path = ROOT / ('dependencies/upstream' if name == 'photon' else 'dependencies/runtime') / artifact['filename']
        valid = path.exists() and hashlib.sha512(path.read_bytes()).hexdigest() == artifact['hashes']['sha512']
        result['stack'][name] = {'version': artifact['version'], 'checksum_valid': valid}
        if not valid:
            failures.append(name)
    for directory in ['run', 'run-iris', 'run-landscape/lod-on', 'run-landscape/lod-off']:
        log = ROOT / directory / 'logs/latest.log'
        lines = log.read_text(errors='replace').splitlines() if log.exists() else []
        patterns = [r'ShaderLOD', r'OpenGL (Vendor|Renderer|Version)', r'Creating pipeline',
                    r'Failed to create shader rendering pipeline', r'Renderer setup complete',
                    r'DH Iris events bound', r'Using shaderpack']
        evidence = [line for line in lines if any(re.search(pattern, line, re.I) for pattern in patterns)]
        states = [line for line in lines if 'ShaderLOD runtime:' in line or 'landscape ready:' in line]
        counts = [list(map(int, match.groups())) for line in lines
                  if (match := re.search(r'VBO Render Count: \[(\d+)/(\d+)\]', line))]
        result['runs'][directory] = {
            'log_exists': log.exists(),
            'last_runtime_status': states[-1] if states else None,
            'dh_visible_total_buffer_counts': counts,
            'screenshots': sorted(path.name for path in (ROOT / directory / 'screenshots').glob('*.png')),
            'evidence': evidence[-25:],
        }
    print(json.dumps(result, indent=2))
    if failures:
        raise SystemExit('Missing or invalid downloads: ' + ', '.join(failures))


if __name__ == '__main__':
    main()

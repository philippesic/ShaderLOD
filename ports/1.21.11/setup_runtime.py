#!/usr/bin/env python3
"""Fetch exactly locked 1.21.11 artifacts; verify before replacing local files."""
import hashlib, json, pathlib, urllib.request, zipfile
root = pathlib.Path(__file__).resolve().parent
lock = json.loads((root / 'dependencies.lock.json').read_text())
runtime = root / 'runtime'; runtime.mkdir(exist_ok=True)
seen = set()
for artifact in lock['artifacts']:
    if artifact['filename'] in seen: continue
    seen.add(artifact['filename'])
    target = runtime / artifact['filename']
    data = target.read_bytes() if target.exists() else urllib.request.urlopen(artifact['url'], timeout=60).read()
    for algorithm in ('sha256', 'sha512'):
        if artifact.get(algorithm) and hashlib.new(algorithm, data).hexdigest() != artifact[algorithm]:
            raise SystemExit('Checksum mismatch: ' + artifact['filename'])
    target.write_bytes(data)
libraries = runtime / 'iris-libraries'; libraries.mkdir(exist_ok=True)
with zipfile.ZipFile(runtime / 'iris-fabric-1.10.8+mc1.21.11.jar') as archive:
    for name in archive.namelist():
        if name.startswith('META-INF/jars/') and any(lib in name for lib in ('antlr4-runtime', 'glsl-transformer', 'jcpp')):
            (libraries / pathlib.Path(name).name).write_bytes(archive.read(name))
print('Verified pinned 1.21.11 artifacts and extracted exact Iris runtime libraries.')

#!/usr/bin/env python3
"""Install the exact official Gradle needed by pinned Iris's Loom build."""
import hashlib, json, pathlib, urllib.request, zipfile
root = pathlib.Path(__file__).resolve().parent
runtime = root / 'runtime'; runtime.mkdir(exist_ok=True)
version = '9.7.1'; name = 'gradle-' + version + '-bin.zip'
url = 'https://downloads.gradle.org/distributions/' + name
expected = json.loads((root / 'dependencies.lock.json').read_text())['tooling']['fabricGradleDistributionSha256']
target = runtime / name
if not target.exists(): target.write_bytes(urllib.request.urlopen(url, timeout=120).read())
actual = hashlib.sha256(target.read_bytes()).hexdigest()
if actual != expected: raise SystemExit('Gradle distribution checksum mismatch')
(root / 'evidence').mkdir(exist_ok=True)
(root / 'evidence' / 'gradle-9.7.1.sha256').write_text(actual + '\n')
with zipfile.ZipFile(target) as archive: archive.extractall(runtime)
executable = runtime / ('gradle-' + version) / 'bin/gradle'; executable.chmod(0o755)
print(executable)

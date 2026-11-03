#!/usr/bin/env python3
"""Install pinned official OpenJDK25 without changing personal/global configuration."""
import hashlib,json,pathlib,tarfile,urllib.request
root=pathlib.Path(__file__).resolve().parents[2]
lock=json.loads((pathlib.Path(__file__).parent/'dependencies.lock.json').read_text())['tooling']
runtime=root/'dependencies/runtime/jdk25';runtime.mkdir(parents=True,exist_ok=True)
target=runtime/'openjdk-25.0.2_macos-aarch64_bin.tar.gz'
if not target.exists():target.write_bytes(urllib.request.urlopen(lock['jdkUrl'],timeout=120).read())
assert hashlib.sha256(target.read_bytes()).hexdigest()==lock['jdkSha256'],'JDK checksum mismatch'
with tarfile.open(target) as archive:archive.extractall(runtime,filter='data')
print(runtime/'jdk-25.0.2.jdk/Contents/Home')

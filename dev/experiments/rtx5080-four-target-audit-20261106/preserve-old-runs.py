"""Archive old 5080 ShaderLOD runs off-host and verify every regular file.

No deletion happens here. The verified manifest supports separately reviewed
source-identity checks and deletion. Requires configured verified-key SSH aliases.
"""
import base64
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tarfile

HERE = Path(__file__).resolve().parent
ROOT = r'C:\Users\philip\AppData\Roaming\PrismLauncher'
PYTHON = r'C:\Users\philip\.local\share\p-stack\python\cpython-3.13.15-windows-x86_64-none\python.exe'


def native(code, timeout=600):
    encoded = base64.b64encode(b"import sys;exec(sys.stdin.read())").decode()
    ps = ("$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue'; & '"
          + PYTHON + "' -c ([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('"
          + encoded + "'))); exit $LASTEXITCODE")
    return subprocess.run(
        ['ssh', '-o', 'BatchMode=yes', '-o', 'RemoteCommand=none', 'pstack-pc',
         'powershell.exe', '-NoProfile', '-EncodedCommand',
         base64.b64encode(ps.encode('utf-16le')).decode()],
        input=code, capture_output=True, text=True, timeout=timeout, check=True).stdout


def main():
    roots = json.loads((HERE / 'cleanup-selected-roots.json').read_text())
    code = '''import hashlib,json,os
from pathlib import Path
root=Path(ROOT_VALUE)
rows=[]
for selected in SELECTED_VALUE:
 p=root/selected
 assert p.is_dir() and not p.is_symlink() and not os.path.isjunction(p),str(p)
 for base,dirs,files in os.walk(p,followlinks=False):
  for d in dirs:
   q=Path(base,d)
   assert not q.is_symlink() and not os.path.isjunction(q),str(q)
  for f in files:
   q=Path(base,f)
   assert not q.is_symlink(),str(q)
   stat=q.stat()
   with q.open('rb') as stream: digest=hashlib.file_digest(stream,'sha256').hexdigest()
   assert stat.st_size==q.stat().st_size and stat.st_mtime_ns==q.stat().st_mtime_ns,str(q)
   rows.append(dict(path=q.relative_to(root).as_posix(),bytes=stat.st_size,mtime_ns=stat.st_mtime_ns,sha256=digest))
print(json.dumps(rows))
'''.replace('ROOT_VALUE', repr(ROOT)).replace('SELECTED_VALUE', repr(roots))
    archive = HERE / 'old-5080-runs.tar.gz'
    if '--verify-existing' in sys.argv:
        manifest = json.loads((HERE / 'cleanup-source-manifest.json').read_text())
    else:
        manifest = json.loads(native(code))
        (HERE / 'cleanup-source-manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
        assert not archive.exists(), 'Preserve existing archive; inspect it before retrying'
        posix_roots = [r.replace('\\', '/') for r in roots]
        remote = ('import os;os.chdir(' + repr('/mnt/c/Users/philip/AppData/Roaming/PrismLauncher')
                  + ');os.execvp("tar",["tar","-czf","-","--",*' + repr(posix_roots) + '])')
        command = "python3 -c \"$(printf '%s' '" + base64.b64encode(remote.encode()).decode() + "' | base64 -d)\"\n"
        with archive.open('xb') as target, (HERE / 'cleanup-archive-stderr.log').open('w') as errors:
            subprocess.run(['ssh', '-T', '-o', 'BatchMode=yes', 'pstack-5080'],
                           input=command.encode(), stdout=target, stderr=errors, timeout=1800, check=True)
    expected = {r['path']: r for r in manifest}
    observed = set()
    with tarfile.open(archive, 'r|gz') as tf:
        for member in tf:
            if member.isdir():
                continue
            assert (member.isfile() or member.islnk()) and member.name in expected, member.name
            if member.islnk():
                assert member.linkname in observed and member.linkname in expected, member.name
            assert member.name not in observed, member.name
            row = expected[member.name]
            if member.isfile():
                assert member.size == row['bytes'], member.name
            if member.islnk():
                target = expected[member.linkname]
                assert (row['bytes'], row['sha256']) == (target['bytes'], target['sha256']), member.name
            else:
                with tf.extractfile(member) as stream:
                    assert hashlib.file_digest(stream, 'sha256').hexdigest() == row['sha256'], member.name
            observed.add(member.name)
    assert observed == set(expected)
    with archive.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    receipt_path = HERE / 'cleanup-backup-verification.json'
    prior = json.loads(receipt_path.read_text()) if receipt_path.exists() else {}
    receipt = dict(archive=str(archive), sha256=digest, archive_bytes=archive.stat().st_size,
                   source_bytes=sum(r['bytes'] for r in manifest), files=len(manifest),
                   verified=True, retirement_completed=prior.get('retirement_completed', False), roots=roots)
    receipt_path.write_text(json.dumps(receipt, indent=2)+'\n')
    print(json.dumps(receipt))


if __name__ == '__main__':
    main()

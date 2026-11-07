"""Preserve terminal ShaderLOD restoration copies off-host before retiring them.

Retains report metadata/results and all live/canonical worlds. No benchmark logic.
Streams the archive without allocating temporary space on the Windows volume.
"""
import base64, hashlib, json, runpy, subprocess, sys, tarfile
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
sys.path.insert(0,str(ROOT/'scripts'))
import run_storage as storage
native=runpy.run_path(str(HERE/'preserve-old-runs.py'))['native']
code=r'''
import hashlib,json,os,shutil,subprocess
from pathlib import Path
here=Path(r'C:\ShaderLODBench\ShaderLOD\dev\experiments\rtx5080-four-target-audit-20261106')
def released():
 probe="import sys;sys.path.insert(0,'scripts');import dev_client as d;assert not d.clients(d.process_table()),'Client active'"
 subprocess.run([r'C:\ShaderLODBench\tools\python\Scripts\python.exe','-c',probe],cwd=here.parents[2],check=True)
def qualified(report):
 op=json.loads((report/'operation.json').read_text())
 assert op['state'] in ('complete','failed-restored') and op.get('configuration_restored') and op.get('release_verified') and op.get('iteration_profile',{}).get('reset_verified'),str(report)
def inventory(roots):
 rows=[]
 for name in roots:
  directory=here/name;assert directory.name=='world-restore' and directory.parent.parent==here
  qualified(directory.parent);assert directory.is_dir() and not directory.is_symlink() and not os.path.isjunction(directory)
  for base,dirs,files in os.walk(directory,followlinks=False):
   for name in dirs:
    p=Path(base,name);assert not p.is_symlink() and not os.path.isjunction(p)
   for name in files:
    p=Path(base,name);assert not p.is_symlink();before=p.stat()
    with p.open('rb') as stream:sha=hashlib.file_digest(stream,'sha256').hexdigest()
    after=p.stat();assert (before.st_size,before.st_mtime_ns)==(after.st_size,after.st_mtime_ns)
    rows.append(dict(path=p.relative_to(here).as_posix(),bytes=before.st_size,sha256=sha))
 return sorted(rows,key=lambda r:r['path'])
released()
'''
archive=HERE/'terminal-world-copies-v1.tar.gz'
manifest_path=HERE/'terminal-world-copies-v1-source.json'
assert not archive.exists() and not manifest_path.exists(),'Fresh preservation outputs required'
selection=code+r'''
roots=[]
for report in sorted(here.iterdir()):
 if not report.is_dir() or not (report/'operation.json').exists() or not (report/'world-restore').is_dir():continue
 try:qualified(report)
 except (AssertionError,KeyError,ValueError):continue
 roots.append((report/'world-restore').relative_to(here).as_posix())
rows=inventory(roots);print(json.dumps(dict(roots=roots,files=rows)))
'''
manifest=json.loads(native(selection,timeout=300));assert manifest['roots'] and manifest['files']
manifest_path.write_text(json.dumps(manifest,indent=2)+'\n')
size=sum(r['bytes'] for r in manifest['files']);storage.admit(HERE,size+256*2**20,root=ROOT)
print('Preserving',len(manifest['roots']),'released world-restore copies, GiB',round(size/2**30,2),flush=True)
remote="import os;os.chdir('/mnt/c/ShaderLODBench/ShaderLOD/dev/experiments/rtx5080-four-target-audit-20261106');os.execvp('tar',['tar','-czf','-','--',*"+repr(manifest['roots'])+"])"
command="python3 -c \"$(printf '%s' '"+base64.b64encode(remote.encode()).decode()+"' | base64 -d)\"\n"
with archive.open('xb') as stream,(HERE/'terminal-world-copies-v1-tar.log').open('w') as errors:
 subprocess.run(['ssh','-T','-o','BatchMode=yes','pstack-5080'],input=command.encode(),stdout=stream,stderr=errors,timeout=1800,check=True)
expected={r['path']:r for r in manifest['files']};observed=set()
with tarfile.open(archive,'r|gz') as tar:
 for member in tar:
  if member.isdir():continue
  assert member.isfile() and member.name in expected and member.name not in observed,member.name
  row=expected[member.name];assert member.size==row['bytes']
  with tar.extractfile(member) as stream:assert hashlib.file_digest(stream,'sha256').hexdigest()==row['sha256']
  observed.add(member.name)
assert observed==set(expected)
with archive.open('rb') as stream:sha=hashlib.file_digest(stream,'sha256').hexdigest()
receipt=dict(archive=str(archive),archive_bytes=archive.stat().st_size,archive_sha256=sha,source_bytes=size,files=len(expected),roots=manifest['roots'],verified=True,retired=False)
receipt_path=HERE/'terminal-world-copies-v1-verification.json';receipt_path.write_text(json.dumps(receipt,indent=2)+'\n')
print('Off-host archive verified; rechecking native identities before retirement',flush=True)
retire=code+'\nroots='+repr(manifest['roots'])+'\nexpected='+repr(manifest['files'])+r'''
assert inventory(roots)==expected,'Native preimages changed; preserve everything'
released();before=shutil.disk_usage('C:/').free
for name in roots:
 directory=here/name;qualified(directory.parent);shutil.rmtree(directory)
print(json.dumps(dict(free_before=before,free_after=shutil.disk_usage('C:/').free,retired_roots=len(roots))))
'''
result=json.loads(native(retire,timeout=300));receipt.update(retired=True,retirement=result);receipt_path.write_text(json.dumps(receipt,indent=2)+'\n')
print(json.dumps(dict(verified=True,retired_roots=result['retired_roots'],reclaimed_gib=round((result['free_after']-result['free_before'])/2**30,2),free_gib=round(result['free_after']/2**30,2))))

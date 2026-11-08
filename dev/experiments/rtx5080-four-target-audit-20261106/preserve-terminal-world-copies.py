"""Preserve enumerated terminal ShaderLOD artifacts before retiring native copies.

Retains report metadata/results and all live/canonical worlds. No benchmark logic.
Streams the archive without allocating temporary space on the Windows volume.
"""
import argparse, base64, hashlib, json, re, runpy, subprocess, sys, tarfile
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
sys.path.insert(0,str(ROOT/'scripts'))
import run_storage as storage
native=runpy.run_path(str(HERE/'preserve-old-runs.py'))['native']
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--tag',default='terminal-world-copies-v1')
parser.add_argument('--kind',choices=['world-restore','heavy','quality','graphs'],default='world-restore')
parser.add_argument('--content-addressed',action='store_true',help='Store unique file bytes once; preserve all original paths in the manifest')
parser.add_argument('--resume-inventory',action='store_true',help='Reuse a completed inventory after a failed transport; native originals are still rehashed before any retirement')
parser.add_argument('--reports',nargs='*')
args=parser.parse_args()
assert re.fullmatch('[a-z0-9-]{4,100}',args.tag)
assert not args.reports or all(re.fullmatch('[a-z0-9-]{4,120}',s) for s in args.reports)
code=r'''
import hashlib,json,os,re,shutil,subprocess
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
here=Path(r'C:\ShaderLODBench\ShaderLOD\dev\experiments\rtx5080-four-target-audit-20261106')
def released():
 probe="import sys;sys.path.insert(0,'scripts');import dev_client as d;assert not d.clients(d.process_table()),'Client active'"
 subprocess.run([r'C:\ShaderLODBench\tools\python\Scripts\python.exe','-c',probe],cwd=here.parents[2],check=True)
def qualified(report):
 op=json.loads((report/'operation.json').read_text())
 assert op['state'] in ('complete','failed-restored') and op.get('configuration_restored') and op.get('release_verified') and op.get('iteration_profile',{}).get('reset_verified'),str(report)
def inventory(roots):
 paths=[]
 for name in roots:
  directory=here/name;assert directory.parent.parent==here
  allowed=directory.name in ('world-restore','world-before','world-after','quality-native','quality-candidate','quality-return') or re.fullmatch('native-graph-[a-zA-Z0-9_-]+',directory.name) or directory.is_file() and directory.suffix=='.png'
  assert allowed,str(directory)
  qualified(directory.parent);assert not directory.is_symlink() and not os.path.isjunction(directory)
  walk=os.walk(directory,followlinks=False) if directory.is_dir() else [(directory.parent,[],[directory.name])]
  for base,dirs,files in walk:
   for name in dirs:
    p=Path(base,name);assert not p.is_symlink() and not os.path.isjunction(p)
   for name in files:
    p=Path(base,name);assert not p.is_symlink();paths.append(p)
 def row(p):
  before=p.stat()
  with p.open('rb') as stream:sha=hashlib.file_digest(stream,'sha256').hexdigest()
  after=p.stat();assert (before.st_size,before.st_mtime_ns)==(after.st_size,after.st_mtime_ns)
  return dict(path=p.relative_to(here).as_posix(),bytes=before.st_size,sha256=sha)
 with ThreadPoolExecutor(max_workers=4) as pool:rows=list(pool.map(row,paths))
 return sorted(rows,key=lambda r:r['path'])
released()
'''
archive=HERE/(args.tag+('.zip' if args.content_addressed else '.tar.gz'))
manifest_path=HERE/(args.tag+'-source.json')
assert not archive.exists(),'Fresh archive required; preserve any failed transport separately'
assert args.resume_inventory or not manifest_path.exists(),'Fresh preservation inventory required'
selection=code+'\nkind='+repr(args.kind)+'\nreports='+repr(args.reports)+'\ntag='+repr(args.tag)+r'''
roots=[]
for report in sorted(here.iterdir()):
 if not report.is_dir() or not (report/'operation.json').exists() or reports and report.name not in reports:continue
 try:qualified(report)
 except (AssertionError,KeyError,ValueError):continue
 selected=[report/'world-restore'] if kind=='world-restore' else list(report.glob('quality-*')) if kind=='quality' else list(report.glob('native-graph-*')) if kind=='graphs' else [*report.glob('*.png'),report/'world-before',report/'world-after',report/'world-restore']
 roots.extend(p.relative_to(here).as_posix() for p in selected if p.exists())
rows=inventory(roots);data=dict(roots=roots,files=rows)
target=here/(tag+'-source.json');assert not target.exists();target.write_text(json.dumps(data,indent=2)+'\n');print(json.dumps(data))
'''
if args.resume_inventory:
 manifest=json.loads(manifest_path.read_text())
else:
 manifest=json.loads(native(selection,timeout=1800))
 manifest_path.write_text(json.dumps(manifest,indent=2)+'\n')
assert manifest['roots'] and manifest['files']
size=sum(r['bytes'] for r in manifest['files']);storage.admit(HERE,128*2**20,root=ROOT)
print('Preserving',len(manifest['roots']),'terminal',args.kind,'artifacts, GiB',round(size/2**30,2),flush=True)
remote="import os,json;os.chdir('/mnt/c/ShaderLODBench/ShaderLOD/dev/experiments/rtx5080-four-target-audit-20261106');data=json.load(open("+repr(args.tag+'-source.json')+"));os.execvp('tar',['tar','-czf','-','--',*data['roots']])"
if args.content_addressed:
 remote="""import os,json,sys,zipfile,hashlib
os.chdir('/mnt/c/ShaderLODBench/ShaderLOD/dev/experiments/rtx5080-four-target-audit-20261106')
data=json.load(open(MANIFEST_PATH));seen=set()
with zipfile.ZipFile(sys.stdout.buffer,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for row in data['files']:
  if row['sha256'] in seen:continue
  with open(row['path'],'rb') as f:content=f.read()
  assert len(content)==row['bytes'] and hashlib.sha256(content).hexdigest()==row['sha256']
  z.writestr('objects/'+row['sha256'],content);seen.add(row['sha256'])
 z.writestr('PRESERVATION-MANIFEST.json',json.dumps(data,indent=2)+'\\n')
""".replace('MANIFEST_PATH',repr(args.tag+'-source.json'))
compile(remote,'archive transport','exec')
command="python3 -c \"$(printf '%s' '"+base64.b64encode(remote.encode()).decode()+"' | base64 -d)\"\n"
with archive.open('xb') as stream,(HERE/(args.tag+'-tar.log')).open('w') as errors:
 child=subprocess.Popen(['ssh','-T','-o','BatchMode=yes','pstack-5080'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=errors)
 try:
  child.stdin.write(command.encode());child.stdin.close()
  while chunk:=child.stdout.read(4*2**20):
   storage.admit(HERE,128*2**20,root=ROOT)
   stream.write(chunk)
  assert child.wait(timeout=60)==0,'Archive transport failed; native originals retained'
 finally:
  if child.poll() is None:child.terminate();child.wait(timeout=30)
expected={r['path']:r for r in manifest['files']};observed=set()
if args.content_addressed:
 import zipfile
 with zipfile.ZipFile(archive) as z:
  assert json.loads(z.read('PRESERVATION-MANIFEST.json'))==manifest
  unique={r['sha256']:r['bytes'] for r in manifest['files']}
  assert set(z.namelist())=={'objects/'+s for s in unique}|{'PRESERVATION-MANIFEST.json'}
  for sha,size in unique.items():
   assert z.getinfo('objects/'+sha).file_size==size
   with z.open('objects/'+sha) as stream:assert hashlib.file_digest(stream,'sha256').hexdigest()==sha
  observed=set(expected)
else:
 with tarfile.open(archive,'r|gz') as tar:
  for member in tar:
   if member.isdir():continue
   assert member.isfile() and member.name in expected and member.name not in observed,member.name
   row=expected[member.name];assert member.size==row['bytes']
   with tar.extractfile(member) as stream:assert hashlib.file_digest(stream,'sha256').hexdigest()==row['sha256']
   observed.add(member.name)
assert observed==set(expected)
with archive.open('rb') as stream:sha=hashlib.file_digest(stream,'sha256').hexdigest()
receipt=dict(kind=args.kind,archive=str(archive),archive_bytes=archive.stat().st_size,archive_sha256=sha,source_bytes=size,files=len(expected),roots=manifest['roots'],verified=True,retired=False)
if args.content_addressed:receipt.update(format='content-addressed-zip',restore='For each files[] row in PRESERVATION-MANIFEST.json, write objects/<sha256> independently to its original path.')
receipt_path=HERE/(args.tag+'-verification.json');receipt_path.write_text(json.dumps(receipt,indent=2)+'\n')
print('Off-host archive verified; rechecking native identities before retirement',flush=True)
retire=code+'\nroots='+repr(manifest['roots'])+'\nexpected='+repr(manifest['files'])+r'''
assert inventory(roots)==expected,'Native preimages changed; preserve everything'
released();before=shutil.disk_usage('C:/').free
for name in roots:
 directory=here/name;qualified(directory.parent)
 if directory.is_dir():shutil.rmtree(directory)
 else:directory.unlink()
print(json.dumps(dict(free_before=before,free_after=shutil.disk_usage('C:/').free,retired_roots=len(roots))))
'''
result=json.loads(native(retire,timeout=1800));receipt.update(retired=True,retirement=result);receipt_path.write_text(json.dumps(receipt,indent=2)+'\n')
print(json.dumps(dict(verified=True,retired_roots=result['retired_roots'],reclaimed_gib=round((result['free_after']-result['free_before'])/2**30,2),free_gib=round(result['free_after']/2**30,2))))

"""Retire exactly the archived ShaderLOD roots after complete identity checks."""
import hashlib
import json
from pathlib import Path
import runpy

HERE = Path(__file__).resolve().parent
helper = runpy.run_path(str(HERE / 'preserve-old-runs.py'))
receipt = json.loads((HERE / 'cleanup-backup-verification.json').read_text())
assert receipt['verified'] and not receipt['retirement_completed']
archive = Path(receipt['archive'])
with archive.open('rb') as stream:
    assert hashlib.file_digest(stream, 'sha256').hexdigest() == receipt['sha256']
manifest = json.loads((HERE / 'cleanup-source-manifest.json').read_text())
code = '''import csv,hashlib,io,json,os,shutil,subprocess
from pathlib import Path
root=Path(ROOT_VALUE)
rows=MANIFEST_VALUE
selected=SELECTED_VALUE
processes=subprocess.check_output(['tasklist','/FO','CSV','/NH'],text=True)
assert not any(r[0].lower() in ('java.exe','javaw.exe','prismlauncher.exe') for r in csv.reader(io.StringIO(processes)) if r),'Client/launcher running; stop cleanup'
expected={r['path']:r for r in rows}
actual=set()
for name in selected:
 p=root/name
 assert p.is_dir() and not p.is_symlink() and not os.path.isjunction(p)
 for base,dirs,files in os.walk(p,followlinks=False):
  for d in dirs:
   q=Path(base,d)
   assert not q.is_symlink() and not os.path.isjunction(q),str(q)
  for f in files:
   q=Path(base,f);key=q.relative_to(root).as_posix()
   assert not q.is_symlink() and key in expected,str(q)
   row=expected[key];stat=q.stat()
   assert stat.st_size==row['bytes'] and stat.st_mtime_ns==row['mtime_ns'],str(q)
   with q.open('rb') as stream:assert hashlib.file_digest(stream,'sha256').hexdigest()==row['sha256'],str(q)
   actual.add(key)
assert actual==set(expected),'Source membership changed; preserve it'
free_before=shutil.disk_usage(root).free
removed=[];removed_files=0;removed_bytes=0;retained=[]
for name in selected:
 p=root/name
 assert p.parent==root or name==r'instances\\ShaderLOD-5080-Tuning\\.minecraft\\shaderlod-tuning',str(p)
 assert p.name.lower().startswith('shaderlod')
 if p.parent!=root:
  # Current profile's recovery files remain usable in place. Only retire bulky
  # historical measurement/metadata files, whose exact bytes were verified.
  for row in rows:
   q=root/row['path']
   if p in q.parents and q.name in ('samples.csv','metadata.properties') and q.parent.name.startswith('tune-'):
    q.unlink();removed_files+=1;removed_bytes+=row['bytes']
  retained.append(name)
 else:
  subtree=[r for r in rows if (root/r['path']).is_relative_to(p)]
  shutil.rmtree(p)
  removed.append(name);removed_files+=len(subtree);removed_bytes+=sum(r['bytes'] for r in subtree)
print(json.dumps(dict(removed_roots=removed,retained_recovery_root=retained,free_before_bytes=free_before,removed_files=removed_files,removed_bytes=removed_bytes,free_bytes=shutil.disk_usage(root).free)))
'''.replace('ROOT_VALUE', repr(helper['ROOT'])).replace('MANIFEST_VALUE', repr(manifest)).replace('SELECTED_VALUE', repr(receipt['roots']))
result = json.loads(helper['native'](code, timeout=900))
(HERE / 'cleanup-deletion-receipt.json').write_text(json.dumps(result, indent=2)+'\n')
receipt['retirement_completed'] = True
(HERE / 'cleanup-backup-verification.json').write_text(json.dumps(receipt, indent=2)+'\n')
print(json.dumps(result))

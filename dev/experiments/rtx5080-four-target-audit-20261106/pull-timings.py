"""Copy completed timing evidence with independent hashes; leave native reports intact.

This is artifact transport only. It never starts, controls or measures a client.
Large source/image/log evidence remains in the native report until separately pulled.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import runpy
import subprocess
import sys
import uuid
import zipfile

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
sys.path.insert(0,str(ROOT/'scripts'))
import run_storage as storage

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('reports',nargs='+')
a=p.parse_args()
assert all(re.fullmatch(r'[a-z0-9][a-z0-9-]{1,100}',name) for name in a.reports)
tag=uuid.uuid4().hex
def sha256(path):
    with Path(path).open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
transport=runpy.run_path(str(HERE/'preserve-old-runs.py'))['native']
code=r'''import hashlib,json,zipfile,shutil,subprocess
from pathlib import Path
root=Path(r'C:\ShaderLODBench\ShaderLOD\dev\experiments\rtx5080-four-target-audit-20261106')
# Never compete with a scored capture. Startup/conditioning are outside its windows.
for ledger in root.glob('queue-*.json'):
 data=json.loads(ledger.read_text())
 for job in data.get('runs',[]) if isinstance(data,dict) else []:
  if job['state']!='running':continue
  opfile=Path(r'C:\ShaderLODBench\ShaderLOD')/job['output']/'operation.json'
  if not opfile.exists():continue
  commands=json.loads(opfile.read_text())['commands']
  boundaries=[row for row in commands if row['action'] in ('window_start','window_stop')]
  assert not boundaries or boundaries[-1]['action']=='window_stop' and boundaries[-1]['status']=='complete','A capture is active; retry transport outside timing'
for name in NAMES:
 report=root/name
 op=json.loads((report/'operation.json').read_text())
 assert op['state'] in ('complete','failed-restored') and op.get('configuration_restored') and op.get('release_verified'),name
 summary=json.loads((report/'summary.json').read_text())
 assert summary.get('finished') and summary.get('evaluation'),'Report finalization still active: '+name
# Terminal operation state precedes report evaluation/disposal. Require the exact
# old controllers absent before exporting immutable evidence, including recovery.
check="""import sys,json
from pathlib import Path
repo=Path(r'C:\\ShaderLODBench\\ShaderLOD');sys.path.insert(0,str(repo/'scripts'))
import dev_client as dev
import experiment_lifecycle as life
table=dev.process_table()
for report in json.loads(sys.argv[1]):
 op=json.loads(Path(report,'operation.json').read_text())
 life.process_absent(op['processes'].get('controller'),table,dev.identity)
"""
subprocess.run([r'C:\ShaderLODBench\tools\python\Scripts\python.exe','-c',check,json.dumps([str(root/name) for name in NAMES])],check=True)
export=Path(r'C:\ShaderLODBench\exports')/('timings-'+TAG+'.zip');export.parent.mkdir(exist_ok=True)
assert shutil.disk_usage('C:/').free>=32*2**30+256*2**20,'Native export needs32GiB reserve plus256MiB temporary headroom'
rows=[]
with zipfile.ZipFile(export,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=1) as z:
 for name in NAMES:
  report=root/name;op=json.loads((report/'operation.json').read_text())
  assert op['state'] in ('complete','failed-restored') and op.get('configuration_restored') and op.get('release_verified'),name
  files=[report/'summary.json',report/'operation.json',report/'build-binding.json',*sorted((report/'windows').rglob('*'))]
  for file in files:
   if not file.is_file():continue
   assert not file.is_symlink(),str(file)
   before=file.stat();data=file.read_bytes();after=file.stat()
   assert (before.st_size,before.st_mtime_ns)==(after.st_size,after.st_mtime_ns),str(file)
   relative=file.relative_to(root).as_posix();z.writestr(relative,data)
   rows.append(dict(path=relative,bytes=len(data),sha256=hashlib.sha256(data).hexdigest()))
print(json.dumps(dict(native_archive=str(export),archive_bytes=export.stat().st_size,archive_sha256=hashlib.sha256(export.read_bytes()).hexdigest(),files=rows)))
'''.replace('TAG',repr(tag)).replace('NAMES',repr(a.reports))
try:receipt=json.loads(transport(code,timeout=120))
except subprocess.CalledProcessError as error:
    print(error.stdout,error.stderr,file=sys.stderr);raise SystemExit(error.returncode)
storage.admit(HERE,receipt['archive_bytes']+sum(r['bytes'] for r in receipt['files']),root=ROOT)
archive=HERE/('timings-export-'+tag+'.zip')
subprocess.run(['scp','-q','-o','BatchMode=yes','-o','RemoteCommand=none','pstack-pc:'+receipt['native_archive'].replace('\\','/'),str(archive)],check=True)
assert archive.stat().st_size==receipt['archive_bytes'] and sha256(archive)==receipt['archive_sha256']
expected={row['path']:row for row in receipt['files']}
with zipfile.ZipFile(archive) as z:
    assert len(z.namelist())==len(expected) and set(z.namelist())==set(expected)
    for name,row in expected.items():
        relative=PurePosixPath(name)
        assert relative.parts[0] in a.reports and '..' not in relative.parts and not relative.is_absolute()
        data=z.read(name)
        assert len(data)==row['bytes'] and hashlib.sha256(data).hexdigest()==row['sha256']
        target=storage.safe_path(HERE/name)
        if target.exists():assert sha256(target)==row['sha256'],'Existing different evidence preserved: '+str(target)
        else:
            target.parent.mkdir(parents=True,exist_ok=True)
            with target.open('xb') as stream:stream.write(data)
        assert sha256(target)==row['sha256']
receipt['local_archive']=str(archive);receipt['verified_files']=len(expected)
(HERE/('timings-export-'+tag+'.json')).write_text(json.dumps(receipt,indent=2)+'\n')
# Only this task's redundant export is retired; originals and local archive remain.
cleanup=r'''from pathlib import Path
import hashlib
p=Path(NAME)
assert p.parent==Path(r'C:\ShaderLODBench\exports') and not p.is_symlink()
assert hashlib.sha256(p.read_bytes()).hexdigest()==SHA
p.unlink();print('Retired verified redundant ShaderLOD export only')
'''.replace('NAME',repr(receipt['native_archive'])).replace('SHA',repr(receipt['archive_sha256']))
print(transport(cleanup,timeout=60).strip())
print(json.dumps(dict(reports=a.reports,verified_files=len(expected),archive=str(archive),native_reports_unchanged=True)))

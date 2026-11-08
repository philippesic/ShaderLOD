"""Serialize maintained quality checks, preserve captures, and collect diagnostics.

No alternative measurement implementation: run_benchmark.py check owns capture,
admission and restoration. JFR is restricted to separately labeled untimed checks.
All raw PNG bytes remain recoverable in independently verified ZIPs.
"""
import base64, hashlib, json, runpy, subprocess, time
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
native=runpy.run_path(str(HERE/'preserve-old-runs.py'))['native']
STATUS=HERE/'quality-v5-status.json'
def stage(name,**data):
 STATUS.write_text(json.dumps(dict(stage=name,time=time.time(),**data),indent=2)+'\n');print(name,data.get('job',''),flush=True)
def command(code,timeout=120):
 try:return native(code,timeout=timeout)
 except subprocess.CalledProcessError as error:
  print(error.stdout,error.stderr,flush=True);raise

def main():
 assert not STATUS.exists(),'Fresh orchestration status required'
 stage('waiting-for-artifact-preservation')
 deadline=time.monotonic()+3600
 while True:
  p=HERE/'terminal-heavy-v2-verification.json'
  if p.exists() and json.loads(p.read_text()).get('retired'):break
  assert time.monotonic()<deadline,'Artifact preservation not complete'
  time.sleep(5)
 payload=[]
 for name in ['scripts/benchmark_quality_motion.py','tests/test_benchmark_quality_motion.py']:
  before=subprocess.check_output(['git','show','4b97d5a:'+name],cwd=ROOT);data=(ROOT/name).read_bytes()
  payload.append(dict(path=name,before=hashlib.sha256(before).hexdigest(),after=hashlib.sha256(data).hexdigest(),data=base64.b64encode(data).decode()))
 deploy=r'''
import base64,hashlib,json,subprocess
from pathlib import Path
root=Path(r'C:\ShaderLODBench\ShaderLOD');here=root/'dev/experiments/rtx5080-four-target-audit-20261106'
d=json.loads((here/'queue-runtime-v4-timings.json').read_text());assert d.get('finished') and all(r.get('restored') and r.get('clients_remaining')==0 for r in d['runs'])
venv=r'C:\ShaderLODBench\tools\python\Scripts\python.exe'
p=subprocess.run([venv,str(here/'freeze-owned-inputs.py'),'--tag','native-before-quality-v5-20261107','--report',d['runs'][-1]['name']],cwd=root,text=True,capture_output=True,timeout=180);assert p.returncode==0,p.stdout+p.stderr
rows=PAYLOAD
for r in rows:assert hashlib.sha256((root/r['path']).read_bytes()).hexdigest()==r['before'],r['path']
for r in rows:
 data=base64.b64decode(r['data']);assert hashlib.sha256(data).hexdigest()==r['after'];(root/r['path']).write_bytes(data)
(here/'quality-v5-source-sync.json').write_text(json.dumps([{k:v for k,v in r.items() if k!='data'} for r in rows],indent=2)+'\n')
p=subprocess.run([venv,'-m','unittest','discover','-s','tests','-p','test_benchmark_quality_motion.py','-q'],cwd=root,text=True,capture_output=True,timeout=60);assert p.returncode==0,p.stdout+p.stderr
print(json.dumps(dict(frozen=True,tests_passed=True)))
'''.replace('PAYLOAD',repr(payload))
 print(command(deploy,timeout=260),flush=True)
 plan=json.loads((ROOT/'benchmarks/campaigns/rtx5080-quality-v5-plan.json').read_text())
 previous='ShaderLOD-RTX5080-Runtime-V4-Timings';outcomes=[]
 for index,job in enumerate(plan,1):
  stage('launching-check',job=job['name'],index=index,total=len(plan))
  launch=r'''
import json,shutil,subprocess,time
from pathlib import Path
root=Path(r'C:\ShaderLODBench\ShaderLOD');here=root/'dev/experiments/rtx5080-four-target-audit-20261106';job=JOB
assert shutil.disk_usage('C:/').free>=(45 if not job['profile_cpu'] else 33)*2**30,'Need measured headroom for lossless native captures'
assert not (root/job['output']).exists()
request=Path(r'C:\ShaderLODBench\requests')/(job['name']+'.json');assert not request.exists();request.write_text(json.dumps(dict(root=str(root),arguments=job['command'][3:]))+'\n')
for i in range(30):
 p=subprocess.run(['powershell','-NoProfile','-Command',"(Get-ScheduledTask -TaskName 'PREVIOUS').State"],capture_output=True,text=True,timeout=15)
 if 'Running' not in p.stdout:break
 time.sleep(1)
p=subprocess.run(['powershell','-NoProfile','-ExecutionPolicy','Bypass','-File',r'C:\ShaderLODBench\launch-native.ps1','-Name','TASK','-Request',str(request),'-PreviousName','PREVIOUS','-TimeLimitMinutes','30'],capture_output=True,text=True,timeout=60);assert p.returncode==0,p.stdout+p.stderr
print(json.dumps(dict(request=str(request),output=str(root/job['output']))))
'''.replace('JOB',repr(job)).replace('PREVIOUS',previous).replace('TASK',f'ShaderLOD-RTX5080-Quality-V5-{index:02}')
  current=json.loads(command(launch));previous=f'ShaderLOD-RTX5080-Quality-V5-{index:02}'
  stage('check-running',job=job['name'],index=index,total=len(plan))
  if job['profile_cpu']:
   profile=r'''
import json,subprocess,time
from pathlib import Path
root=Path(r'C:\ShaderLODBench\ShaderLOD');output=Path(OUTPUT);deadline=time.monotonic()+450
while time.monotonic()<deadline:
 opfile=output/'operation.json'
 if opfile.exists():
  try:op=json.loads(opfile.read_text())
  except (ValueError,PermissionError):time.sleep(1);continue
  if op.get('state') in ('complete','failed-restored'):raise RuntimeError('Check ended before candidate CPU profile')
  binding=op.get('runtime_binding',{});selected=op.get('runtime_plan',{}).get('pack_name');original=op.get('plan',{}).get('pack_name')
  if binding.get('client_pid') and selected and original and selected!=original:break
 time.sleep(2)
else:raise RuntimeError('No owned candidate phase for CPU profile')
verify="import sys,psutil,datetime; p=psutil.Process(int(sys.argv[1])); expected=datetime.datetime.fromisoformat(sys.argv[2].replace('Z','+00:00')).timestamp(); assert abs(p.create_time()-expected)<.2"
p=subprocess.run([r'C:\ShaderLODBench\tools\python\Scripts\python.exe','-c',verify,str(binding['client_pid']),binding['client_started_at']],cwd=root,text=True,capture_output=True,timeout=15);assert p.returncode==0,p.stderr
jdk=Path(json.loads(Path(r'C:\ShaderLODBench\tools\jdk21.json').read_text())['java_home']);recording=output/'candidate-cpu.jfr';assert not recording.exists()
p=subprocess.run([str(jdk/'bin/jcmd.exe'),str(binding['client_pid']),'JFR.start','name=ShaderLODAudit','settings=profile','duration=15s','dumponexit=true','filename='+str(recording)],capture_output=True,text=True,timeout=30);assert p.returncode==0,p.stdout+p.stderr
(output/'cpu-profile-start.json').write_text(json.dumps(dict(client_pid=binding['client_pid'],client_started_at=binding['client_started_at'],selection=selected,stdout=p.stdout,scope='Untimed candidate-phase Render-thread sampling; never scored performance evidence'),indent=2)+'\n');print(json.dumps(dict(profile_started=True)))
'''.replace('OUTPUT',repr(current['output']))
   try:print(command(profile,timeout=500),flush=True)
   except Exception as error:(HERE/(job['name']+'-profile-error.log')).write_text(str(error))
  wait=r'''
import ctypes,json,time,subprocess
from ctypes import wintypes
from pathlib import Path
request=Path(REQUEST);result=request.with_suffix('.exit.json');deadline=time.monotonic()+1800
while not result.exists() and not request.with_suffix('.process.json').exists():
 assert time.monotonic()<deadline;time.sleep(1)
if not result.exists():
 row=json.loads(request.with_suffix('.process.json').read_text());kernel=ctypes.WinDLL('kernel32',use_last_error=True);kernel.OpenProcess.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.DWORD];kernel.OpenProcess.restype=wintypes.HANDLE;kernel.WaitForSingleObject.argtypes=[wintypes.HANDLE,wintypes.DWORD];kernel.WaitForSingleObject.restype=wintypes.DWORD;kernel.CloseHandle.argtypes=[wintypes.HANDLE]
 handle=kernel.OpenProcess(0x00100000,False,row['pid'])
 if handle:
  try:
   while True:
    code=kernel.WaitForSingleObject(handle,30000)
    if code==0:break
    assert code==258 and time.monotonic()<deadline
  finally:kernel.CloseHandle(handle)
for i in range(10):
 if result.exists():break
 time.sleep(1)
assert result.exists(),'Interactive helper did not finalize'
output=Path(OUTPUT);summary=output/'summary.json';d=json.loads(summary.read_text()) if summary.exists() else {}
if d:
 op=json.loads((output/'operation.json').read_text());assert op.get('release_verified') and op.get('configuration_restored') and op.get('iteration_profile',{}).get('reset_verified')
print(json.dumps(dict(name=output.name,returncode=json.loads(result.read_text())['returncode'],validity=d.get('evaluation',{}).get('validity'),error=d.get('error'),restored=d.get('configuration_restored'),quality=d.get('quality_motion'))))
'''.replace('REQUEST',repr(current['request'])).replace('OUTPUT',repr(current['output']))
  outcome=json.loads(command(wait,timeout=1900));outcomes.append(outcome);(HERE/'quality-v5-outcomes.json').write_text(json.dumps(outcomes,indent=2)+'\n')
  stage('preserving-completed-check',job=job['name'],index=index,total=len(plan),validity=outcome.get('validity'))
  if outcome.get('restored'):
   subprocess.run(['python',str(HERE/'pull-timings.py'),job['name']],cwd=ROOT,check=True)
  # Full-resolution raw captures are compressed and verified on the source volume
  # before native PNG retirement. The ZIP is optionally mirrored if local reserve
  # permits; an archive never becomes a substitute for a successful capture.
  if not job['profile_cpu'] and outcome.get('restored'):
   preserve=r'''
import concurrent.futures,hashlib,json,os,shutil,zipfile
from pathlib import Path
root=Path(r'C:\ShaderLODBench\ShaderLOD');output=Path(OUTPUT);op=json.loads((output/'operation.json').read_text());assert op.get('configuration_restored') and op.get('release_verified') and op['iteration_profile'].get('reset_verified')
paths=[]
for directory in output.glob('quality-*'):
 assert directory.name in ('quality-native','quality-candidate','quality-return') and directory.is_dir() and not directory.is_symlink() and not os.path.isjunction(directory)
 paths.extend(p for p in directory.rglob('*') if p.is_file())
assert paths,'No owned quality artifacts to preserve'
def row(path):
 assert not path.is_symlink();before=path.stat()
 with path.open('rb') as stream:sha=hashlib.file_digest(stream,'sha256').hexdigest()
 after=path.stat();assert (before.st_size,before.st_mtime_ns)==(after.st_size,after.st_mtime_ns)
 return dict(path=path.relative_to(output).as_posix(),bytes=before.st_size,sha256=sha)
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:rows=list(pool.map(row,paths))
archive=Path(r'C:\ShaderLODBench\quality-archives')/(output.name+'.zip');archive.parent.mkdir(exist_ok=True);assert not archive.exists()
with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=1) as z:
 for path in sorted(paths):
  assert shutil.disk_usage('C:/').free>=32*2**30+128*2**20,'Native reserve reached; raw captures retained'
  z.write(path,path.relative_to(output).as_posix())
 z.writestr('PRESERVATION-MANIFEST.json',json.dumps(rows,indent=2)+'\n')
with zipfile.ZipFile(archive) as z:
 assert len(z.namelist())==len(rows)+1
 for r in rows:
  with z.open(r['path']) as stream:assert hashlib.file_digest(stream,'sha256').hexdigest()==r['sha256']
  assert row(output/r['path'])==r
with archive.open('rb') as stream:sha=hashlib.file_digest(stream,'sha256').hexdigest()
receipt=dict(archive=str(archive),archive_bytes=archive.stat().st_size,archive_sha256=sha,files=len(rows),source_bytes=sum(r['bytes'] for r in rows),verified=True,retired=False)
(output/'quality-preservation.json').write_text(json.dumps(receipt,indent=2)+'\n')
for directory in output.glob('quality-*'):
 if directory.is_dir():shutil.rmtree(directory)
receipt['retired']=True;(output/'quality-preservation.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(receipt))
'''.replace('OUTPUT',repr(current['output']))
   receipt=json.loads(command(preserve,timeout=1200));(HERE/(job['name']+'-preservation.json')).write_text(json.dumps(receipt,indent=2)+'\n')
   # Avoid accumulating every archive on one volume. Native archives remain
   # verified, byte-exact and recoverable when the Asahi reserve blocks mirroring.
   if __import__('shutil').disk_usage(HERE).free>=32*2**30+receipt['archive_bytes']+512*2**20:
    target=HERE/(job['name']+'-raw.zip')
    subprocess.run(['scp','-q','-o','BatchMode=yes','-o','RemoteCommand=none','pstack-pc:'+receipt['archive'].replace('\\','/'),str(target)],check=True)
    with target.open('rb') as stream:assert hashlib.file_digest(stream,'sha256').hexdigest()==receipt['archive_sha256']
    assert target.stat().st_size==receipt['archive_bytes'];receipt['local_archive']=str(target)
    retire="from pathlib import Path;import hashlib;p=Path("+repr(receipt['archive'])+");assert p.parent==Path(r'C:\\ShaderLODBench\\quality-archives');assert hashlib.sha256(p.read_bytes()).hexdigest()=="+repr(receipt['archive_sha256'])+";p.unlink();print('Retired verified redundant capture ZIP')"
    print(command(retire,timeout=120),flush=True);receipt['native_archive_retired']=True
   (HERE/(job['name']+'-preservation.json')).write_text(json.dumps(receipt,indent=2)+'\n')
 stage('captures-ready-analysis-and-regression-review-pending',outcomes=outcomes)

if __name__=='__main__':
 try:main()
 except Exception as error:stage('failed',error=str(error));raise

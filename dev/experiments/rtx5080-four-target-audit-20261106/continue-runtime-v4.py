"""Continue maintained acquisition after verified preservation, then collect data.

Orchestration/artifact transport only: existing run_benchmark.py and run-queue.py
own every admission, timing, capture and restoration. The passive Windows process
wait creates no polling/transfer workload during measured windows.
"""
import json, runpy, subprocess, time
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
native=runpy.run_path(str(HERE/'preserve-old-runs.py'))['native']
status=HERE/'runtime-v4-continuation-status.json'
def stage(name,**extra):
 status.write_text(json.dumps(dict(stage=name,time=time.time(),**extra),indent=2)+'\n')
 print(name,flush=True)

def main():
 assert not status.exists(),'Fresh continuation status required'
 stage('waiting-for-verified-preservation')
 deadline=time.monotonic()+3600
 while True:
  receipt=HERE/'terminal-world-copies-v1-verification.json'
  if receipt.exists() and json.loads(receipt.read_text()).get('retired'):break
  assert time.monotonic()<deadline,'Preservation did not complete; no benchmark launched'
  time.sleep(5)
 jobs=json.loads((ROOT/'benchmarks/campaigns/rtx5080-runtime-v4-timings.json').read_text())
 launch=r'''
import json,shutil,subprocess,time
from pathlib import Path
root=Path(r'C:\ShaderLODBench\ShaderLOD');here=root/'dev/experiments/rtx5080-four-target-audit-20261106'
d=json.loads((here/'queue-runtime-v4-checks.json').read_text());assert d.get('finished') and len(d['runs'])==8 and all(r.get('restored') and r.get('clients_remaining')==0 and r.get('evaluation',{}).get('validity')=='VALID' for r in d['runs'])
assert shutil.disk_usage('C:/').free>=38*2**30,'Need measured batch headroom beyond32GiB reserve'
verify="import sys;sys.path.insert(0,'scripts');import dev_client as d;assert not d.clients(d.process_table());d.validate_built()"
p=subprocess.run([r'C:\ShaderLODBench\tools\python\Scripts\python.exe','-c',verify],cwd=root,capture_output=True,text=True,timeout=30);assert p.returncode==0,p.stderr
jobs=JOBS
for r in jobs:
 c=r['command'];assert c[1]=='-u' and c[3]=='test' and c[c.index('--output')+1]==r['output'];assert not (root/r['output']).exists()
manifest=root/'benchmarks/campaigns/rtx5080-runtime-v4-timings.json';assert not manifest.exists();manifest.write_text(json.dumps(jobs,indent=2)+'\n')
req=Path(r'C:\ShaderLODBench\requests\queue-runtime-v4-timings.json');assert not req.exists();req.write_text(json.dumps(dict(root=str(root),queue=str(manifest.relative_to(root)),ledger=str((here/'queue-runtime-v4-timings.json').relative_to(root))))+'\n')
p=subprocess.run(['powershell','-NoProfile','-ExecutionPolicy','Bypass','-File',r'C:\ShaderLODBench\launch-native.ps1','-Name','ShaderLOD-RTX5080-Runtime-V4-Timings','-Request',str(req),'-PreviousName','ShaderLOD-RTX5080-Runtime-V4-Checks','-TimeLimitMinutes','480'],capture_output=True,text=True,timeout=60);assert p.returncode==0,p.stdout+p.stderr
for i in range(15):
 if (here/'queue-runtime-v4-timings.json').exists():break
 time.sleep(1)
else:raise RuntimeError('No acquisition ledger; inspect scheduler')
print((here/'queue-runtime-v4-timings.json').read_text())
'''.replace('JOBS',repr(jobs))
 ledger=json.loads(native(launch,timeout=120));assert ledger.get('runs')
 stage('timings-running',operations=len(jobs),first=ledger['runs'][0]['name'])
 wait=r'''
import ctypes,json,time,psutil
from ctypes import wintypes
from pathlib import Path
here=Path(r'C:\ShaderLODBench\ShaderLOD\dev\experiments\rtx5080-four-target-audit-20261106');ledger=here/'queue-runtime-v4-timings.json'
d=json.loads(ledger.read_text())
if not d.get('finished'):
 request=Path(r'C:\ShaderLODBench\requests\queue-runtime-v4-timings.process.json');row=json.loads(request.read_text());proc=psutil.Process(row['pid']);birth=proc.create_time();cmd=proc.cmdline()
 assert any(s.replace('\\','/').endswith('/run-queue.py') for s in cmd) and '--ledger' in cmd and Path(cmd[cmd.index('--ledger')+1])==ledger
 kernel=ctypes.WinDLL('kernel32',use_last_error=True);kernel.OpenProcess.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.DWORD];kernel.OpenProcess.restype=wintypes.HANDLE;kernel.WaitForSingleObject.argtypes=[wintypes.HANDLE,wintypes.DWORD];kernel.WaitForSingleObject.restype=wintypes.DWORD;kernel.CloseHandle.argtypes=[wintypes.HANDLE]
 handle=kernel.OpenProcess(0x00100000,False,row['pid']);assert handle and proc.create_time()==birth
 try:
  deadline=time.monotonic()+8*3600
  while True:
   result=kernel.WaitForSingleObject(handle,30000)
   if result==0:break
   assert result==258 and time.monotonic()<deadline,'Queue wait failed or expired'
 finally:kernel.CloseHandle(handle)
d=json.loads(ledger.read_text());assert d.get('finished');print(json.dumps(d))
'''
 outer="import subprocess; p=subprocess.run([r'C:\\ShaderLODBench\\tools\\python\\Scripts\\python.exe','-c',"+repr(wait)+"],capture_output=True,text=True,timeout=29000); print(p.stdout); print(p.stderr) if p.returncode else None; assert p.returncode==0"
 ledger=json.loads(native(outer,timeout=29100));(HERE/'runtime-v4-final-ledger.json').write_text(json.dumps(ledger,indent=2)+'\n')
 stage('collecting-completed-evidence',completed=len(ledger['runs']))
 reports=[r['name'] for r in ledger['runs'] if r.get('restored') and r.get('clients_remaining')==0]
 assert reports,'No released reports to collect'
 subprocess.run(['python',str(HERE/'pull-timings.py'),*reports],cwd=ROOT,check=True)
 stage('reducing-variance-and-rendering')
 plotting=ROOT/'dev/experiments/asahi-four-target-audit-20261104/chart-env/bin/python'
 subprocess.run([str(plotting),str(HERE/'analyze.py')],cwd=ROOT,check=True)
 subprocess.run(['python',str(HERE/'render-report.py')],cwd=ROOT,check=True)
 stage('descriptive-charts-ready-quality-and-final-audit-pending',reports=len(reports),valid=sum(r.get('evaluation',{}).get('validity')=='VALID' for r in ledger['runs']))

if __name__=='__main__':
 try:main()
 except Exception as error:
  stage('failed',error=str(error));raise

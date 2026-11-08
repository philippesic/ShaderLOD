"""Continue maintained untimed checks after recorder repair; preserve each arm."""
import hashlib
import json
from pathlib import Path
import runpy
import shutil
import subprocess
import time

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
native = runpy.run_path(str(HERE/'preserve-old-runs.py'))['native']
STATUS = HERE/'quality-v6-status.json'


def stage(stage, **fields):
    STATUS.write_text(json.dumps(dict(stage=stage, time=time.time(), **fields), indent=2)+'\n')
    print(stage, fields.get('job', ''), flush=True)


def call(code, timeout=120):
    try:
        return native(code, timeout=timeout)
    except subprocess.CalledProcessError as error:
        print(error.stdout, error.stderr, flush=True)
        raise RuntimeError('Native quality step failed; see retained log') from None


def main():
    assert not STATUS.exists(), 'Fresh acquisition status required'
    plan = json.loads((ROOT/'benchmarks/campaigns/rtx5080-quality-v6-plan.json').read_text())
    previous = 'ShaderLOD-RTX5080-Quality-V5-06'
    outcomes = []
    for index, job in enumerate(plan, 1):
        stage('launching-check', job=job['name'], index=index, total=len(plan))
        task = f'ShaderLOD-RTX5080-Quality-V6-{index:02}'
        code = r'''
import ctypes,json,shutil,subprocess,time
from ctypes import wintypes
from pathlib import Path
root=Path(r'C:\ShaderLODBench\ShaderLOD');job=JOB
assert shutil.disk_usage('C:/').free >= (32+job['capture_headroom_gib'])*2**30,'Insufficient measured headroom; existing evidence retained'
output=root/job['output'];assert not output.exists()
request=Path(r'C:\ShaderLODBench\requests')/(job['name']+'.json');assert not request.exists()
request.write_text(json.dumps(dict(root=str(root),arguments=job['command'][3:]))+'\n')
p=subprocess.run(['powershell','-NoProfile','-ExecutionPolicy','Bypass','-File',r'C:\ShaderLODBench\launch-native.ps1','-Name','TASK','-Request',str(request),'-PreviousName','PREVIOUS','-TimeLimitMinutes','30'],capture_output=True,text=True,timeout=60);assert p.returncode==0,p.stdout+p.stderr
deadline=time.monotonic()+1800;result=request.with_suffix('.exit.json');identity=request.with_suffix('.process.json')
while not result.exists() and not identity.exists():
 assert time.monotonic()<deadline;time.sleep(1)
if not result.exists():
 row=json.loads(identity.read_text());k=ctypes.WinDLL('kernel32',use_last_error=True);k.OpenProcess.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.DWORD];k.OpenProcess.restype=wintypes.HANDLE;k.WaitForSingleObject.argtypes=[wintypes.HANDLE,wintypes.DWORD];k.WaitForSingleObject.restype=wintypes.DWORD;k.CloseHandle.argtypes=[wintypes.HANDLE]
 handle=k.OpenProcess(0x00100000,False,row['pid'])
 if handle:
  try:
   while True:
    code=k.WaitForSingleObject(handle,30000)
    if code==0:break
    assert code==258 and time.monotonic()<deadline
  finally:k.CloseHandle(handle)
for i in range(10):
 if result.exists():break
 time.sleep(1)
assert result.exists(),'Native helper did not finalize'
s=json.loads((output/'summary.json').read_text());op=json.loads((output/'operation.json').read_text())
assert op.get('release_verified') and op.get('configuration_restored') and op['iteration_profile'].get('reset_verified')
print(json.dumps(dict(name=job['name'],returncode=json.loads(result.read_text())['returncode'],validity=s.get('evaluation',{}).get('validity'),restored=True,error=s.get('error'),quality=s.get('quality_motion'))))
'''.replace('JOB', repr(job)).replace('TASK', task).replace('PREVIOUS', previous)
        outcome = json.loads(call(code, timeout=1900))
        outcomes.append(outcome)
        (HERE/'quality-v6-outcomes.json').write_text(json.dumps(outcomes, indent=2)+'\n')
        previous = task
        stage('preserving-completed-check', job=job['name'], validity=outcome['validity'])
        subprocess.run(['python', str(HERE/'pull-timings.py'), job['name']], cwd=ROOT, check=True)
        code = r'''
import json,subprocess
from pathlib import Path
root=Path(r'C:\ShaderLODBench\ShaderLOD');output=root/OUTPUT
p=subprocess.run([r'C:\ShaderLODBench\tools\python\Scripts\python.exe',str(root/'scripts/benchmark_quality_archive.py'),str(output),'--archive-root',r'C:\ShaderLODBench\quality-archives'],cwd=root,capture_output=True,text=True,timeout=900);assert p.returncode==0,p.stdout+p.stderr
print(p.stdout)
'''.replace('OUTPUT', repr(job['output']))
        receipt = json.loads(call(code, timeout=1000))
        for archive in receipt['archives']:
            if shutil.disk_usage(HERE).free < 32*2**30 + archive['archive_bytes'] + 512*2**20:
                continue
            target = HERE/Path(archive['archive'].replace('\\', '/')).name
            subprocess.run(['scp', '-q', '-o', 'BatchMode=yes', '-o', 'RemoteCommand=none',
                            'pstack-pc:'+archive['archive'].replace('\\', '/'), str(target)], check=True)
            with target.open('rb') as stream:
                assert hashlib.file_digest(stream, 'sha256').hexdigest() == archive['archive_sha256']
            archive['local_archive'] = str(target)
            code = "from pathlib import Path;import hashlib;p=Path("+repr(archive['archive'])+");assert p.parent==Path(r'C:\\ShaderLODBench\\quality-archives');assert p.stat().st_size=="+repr(archive['archive_bytes'])+";assert hashlib.file_digest(p.open('rb'),'sha256').hexdigest()=="+repr(archive['archive_sha256'])+";p.unlink()"
            call(code, timeout=120)
            archive['native_archive_retired'] = True
        (HERE/(job['name']+'-preservation.json')).write_text(json.dumps(receipt, indent=2)+'\n')
    stage('captures-ready-analysis-pending', outcomes=outcomes)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        stage('failed', error=str(error))
        raise

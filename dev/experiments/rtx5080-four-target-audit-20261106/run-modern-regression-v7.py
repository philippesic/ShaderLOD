"""Chain two explicit modern-context comparisons after released quality checks."""
import argparse
import json
from pathlib import Path
import runpy
import subprocess
import time

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
native = runpy.run_path(str(HERE/'preserve-old-runs.py'))['native']
STATUS = HERE/'modern-regression-v7-status.json'


def stage(name, **fields):
    STATUS.write_text(json.dumps(dict(stage=name, time=time.time(), **fields), indent=2)+'\n')
    print(name, flush=True)


def main():
    global STATUS
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--quality-status',default='quality-v6-status.json')
    parser.add_argument('--quality-outcomes',nargs='+',default=['quality-v6-outcomes.json'])
    parser.add_argument('--status',default='modern-regression-v7-status.json')
    args=parser.parse_args()
    assert all(Path(name).name==name for name in [args.quality_status,args.status,*args.quality_outcomes])
    STATUS=HERE/args.status
    assert not STATUS.exists(), 'Fresh orchestration status required'
    stage('waiting-for-quality-checks')
    deadline = time.monotonic()+5400
    while True:
        current = json.loads((HERE/args.quality_status).read_text())
        if current['stage'] == 'captures-ready-analysis-pending':
            break
        assert current['stage'] != 'failed', 'Quality continuation needs repair before diagnostics'
        assert time.monotonic() < deadline, 'Quality continuation deadline exceeded'
        time.sleep(5)
    outcomes = [row for filename in args.quality_outcomes for row in json.loads((HERE/filename).read_text())]
    assert len(outcomes) == 5 and all(row['restored'] for row in outcomes)
    relative = 'benchmarks/campaigns/rtx5080-modern-regression-v7-plan.json'
    subprocess.run(['scp', '-q', '-o', 'BatchMode=yes', '-o', 'RemoteCommand=none',
                    str(ROOT/relative), 'pstack-pc:C:/ShaderLODBench/ShaderLOD/'+relative], check=True)
    stage('modern-context-comparisons-running')
    code = r'''
import ctypes,json,subprocess,time
from ctypes import wintypes
from pathlib import Path
root=Path(r'C:\ShaderLODBench\ShaderLOD');here=root/'dev/experiments/rtx5080-four-target-audit-20261106'
request=Path(r'C:\ShaderLODBench\requests\queue-modern-regression-v7.json');assert not request.exists()
ledger=here/'queue-modern-regression-v7.json';assert not ledger.exists()
request.write_text(json.dumps(dict(root=str(root),queue=str(root/'benchmarks/campaigns/rtx5080-modern-regression-v7-plan.json'),ledger=str(ledger)))+'\n')
p=subprocess.run(['powershell','-NoProfile','-ExecutionPolicy','Bypass','-File',r'C:\ShaderLODBench\launch-native.ps1','-Name','ShaderLOD-RTX5080-Modern-Regression-V7','-Request',str(request),'-PreviousName','ShaderLOD-RTX5080-Quality-V6-05','-TimeLimitMinutes','120'],capture_output=True,text=True,timeout=60);assert p.returncode==0,p.stdout+p.stderr
deadline=time.monotonic()+7200;result=request.with_suffix('.exit.json');identity=request.with_suffix('.process.json')
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
d=json.loads(ledger.read_text());assert d.get('finished') and len(d['runs'])==2 and all(r.get('restored') and r.get('clients_remaining')==0 for r in d['runs'])
print(json.dumps(dict(finished=True,runs=[{k:r.get(k) for k in ('name','validity','restored')} for r in d['runs']])))
'''
    try:
        outcome = json.loads(native(code, timeout=7400))
    except subprocess.CalledProcessError as error:
        print(error.stdout, error.stderr)
        raise RuntimeError('Modern-context queue failed; see retained native ledger') from None
    (HERE/'modern-regression-v7-outcomes.json').write_text(json.dumps(outcome, indent=2)+'\n')
    names = [job['name'] for job in json.loads((ROOT/relative).read_text())]
    stage('collecting-comparisons')
    subprocess.run(['python', str(HERE/'pull-timings.py'), *names], cwd=ROOT, check=True)
    stage('diagnostics-collected-quality-analysis-and-final-audit-pending')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        stage('failed', error=str(error))
        raise

"""Preserve released native inputs, CAS-deploy untimed recorder repair, test it."""
import base64
import hashlib
import json
from pathlib import Path
import runpy
import subprocess

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
native = runpy.run_path(str(HERE/'preserve-old-runs.py'))['native']
names = [
    'src/devClient/java/com/pp/shaderlod/dev/CameraQualitySession.java',
    'src/benchmarkCore/java/com/pp/shaderlod/benchmark/core/CaptureMotionClock.java',
    'src/test/java/com/pp/shaderlod/benchmark/CaptureMotionClockTest.java',
    'scripts/benchmark_quality_motion.py',
]
old_quality = {r['path']: r['after'] for r in json.loads((HERE/'quality-v5-source-sync.json').read_text())}
rows = []
for name in names:
    data = (ROOT/name).read_bytes()
    old = subprocess.run(['git', 'show', '4b97d5a:'+name], cwd=ROOT, capture_output=True)
    before = old_quality.get(name, hashlib.sha256(old.stdout).hexdigest() if old.returncode == 0 else None)
    rows.append(dict(path=name, before=before, after=hashlib.sha256(data).hexdigest(), data=base64.b64encode(data).decode()))
code = r'''
import base64,hashlib,json,os,subprocess
from pathlib import Path
root=Path(r'C:\ShaderLODBench\ShaderLOD');here=root/'dev/experiments/rtx5080-four-target-audit-20261106'
venv=r'C:\ShaderLODBench\tools\python\Scripts\python.exe'
probe="import sys;sys.path.insert(0,'scripts');import dev_client as d;assert not d.clients(d.process_table())"
p=subprocess.run([venv,'-c',probe],cwd=root,capture_output=True,text=True,timeout=30);assert p.returncode==0,p.stdout+p.stderr
p=subprocess.run([venv,str(here/'freeze-owned-inputs.py'),'--tag','native-before-quality-clock-v6-20261107','--report','reimagined-bloom-motion-quality-v5'],cwd=root,capture_output=True,text=True,timeout=240);assert p.returncode==0,p.stdout+p.stderr
rows=PAYLOAD
for r in rows:
 p=root/r['path'];assert (hashlib.sha256(p.read_bytes()).hexdigest() if p.exists() else None)==r['before'],r['path']
for r in rows:
 data=base64.b64decode(r['data']);assert hashlib.sha256(data).hexdigest()==r['after'];p=root/r['path'];p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
(here/'quality-clock-v6-source-sync.json').write_text(json.dumps([{k:v for k,v in r.items() if k!='data'} for r in rows],indent=2)+'\n')
env=os.environ.copy();env['JAVA_HOME']=json.loads(Path(r'C:\ShaderLODBench\tools\jdk21.json').read_text())['java_home'];env['PATH']=str(Path(env['JAVA_HOME'])/'bin')+os.pathsep+env['PATH'];env['GRADLE_USER_HOME']=r'C:\ShaderLODBench\gradle-cache'
commands=[['cmd','/c',str(root/'gradlew.bat'),'test','--tests','com.pp.shaderlod.benchmark.CaptureMotionClockTest','compileDevClientJava','--offline','--console','plain'],[venv,'-m','unittest','discover','-s','tests','-p','test_benchmark_quality_motion.py','-q']]
for i,command in enumerate(commands):
 p=subprocess.run(command,cwd=root,env=env,capture_output=True,text=True,timeout=600);(here/('quality-clock-v6-test-'+str(i)+'.log')).write_text(p.stdout+p.stderr);assert p.returncode==0,p.stdout[-3000:]+p.stderr[-3000:]
print(json.dumps(dict(deployed=True,native_clock_tests=3,native_python_tests=2,dev_compilation=True)))
'''.replace('PAYLOAD', repr(rows))
try:
    print(native(code, timeout=1000))
except subprocess.CalledProcessError as error:
    print(error.stdout, error.stderr)
    raise SystemExit(error.returncode)

"""Run maintained benchmark commands in the active Windows console session.

This supplies session placement and a console log only. A queue serializes the
existing CLI; admission, conditioning, capture, recovery and measurement remain
in scripts/run_benchmark.py.
"""
import ctypes
import json
import os
from pathlib import Path
import subprocess
import sys

request_path = Path(sys.argv[1]).resolve()
request = json.loads(request_path.read_text())
root = Path(request['root']).resolve()
campaign = root / 'dev/experiments/rtx5080-four-target-audit-20261106'
if 'queue' in request:
    jobs = (root / request['queue']).resolve()
    output = (root / request['ledger']).resolve()
    assert output.is_relative_to(campaign) and not output.exists(), 'Fresh campaign ledger required'
    assert jobs.is_relative_to(root / 'benchmarks/campaigns')
    for job in json.loads(jobs.read_text()):
        command = job['command']
        assert Path(command[0]).resolve() == Path(sys.executable).resolve()
        assert command[1] == '-u' and Path(command[2]).resolve() == root / 'scripts/run_benchmark.py'
        assert command[3] in ('native-startup', 'baseline', 'check', 'test')
        target = (root / job['output']).resolve()
        assert target.is_relative_to(campaign) and not target.exists(), 'Fresh campaign output required'
        assert (root / command[command.index('--output') + 1]).resolve() == target
    args = ['--jobs', str(jobs), '--ledger', str(output)]
    entry = root / 'dev/experiments/asahi-four-target-audit-20261104/run-queue.py'
else:
    args = request['arguments']
    assert args[0] in ('native-startup', 'baseline', 'check', 'test', 'recover')
    assert all(isinstance(arg, str) for arg in args)
    assert '--output' in args
    output = Path(args[args.index('--output') + 1]).resolve()
    assert output.is_relative_to(campaign)
    assert not output.exists() or args[0] == 'recover', 'Fresh maintained output required'
    entry = root / 'scripts/run_benchmark.py'
console_session = ctypes.windll.kernel32.WTSGetActiveConsoleSessionId()
process_session = ctypes.c_uint()
assert ctypes.windll.kernel32.ProcessIdToSessionId(os.getpid(), ctypes.byref(process_session))
assert process_session.value == console_session != 0xFFFFFFFF, 'Interactive console session required'
jdk = json.loads((root.parent / 'tools/jdk21.json').read_text())['java_home']
env = dict(os.environ, JAVA_HOME=jdk, GRADLE_USER_HOME=str(root.parent / 'gradle-cache'))
env['PATH'] = str(Path(jdk) / 'bin') + os.pathsep + str(Path(sys.executable).parent) + os.pathsep + env['PATH']
console = request_path.with_suffix('.console.log')
result_path = request_path.with_suffix('.exit.json')
assert not console.exists() and not result_path.exists(), 'Job replay refused'
with console.open('x') as stream:
    child = subprocess.Popen([sys.executable, '-u', str(entry), *args],
                             cwd=root, env=env, stdout=stream, stderr=subprocess.STDOUT)
    request_path.with_suffix('.process.json').write_text(json.dumps(dict(
        pid=child.pid, session_id=process_session.value, command=args)))
    result = child.wait()
result_path.write_text(json.dumps(dict(returncode=result, session_id=process_session.value,
                                      output=str(output))) + '\n')
sys.exit(result)

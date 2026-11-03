#!/usr/bin/env python3
"""Sequential scoped Java25 builds. Obtain the client/GPU owner's CPU window first."""
import json, os, pathlib, subprocess, sys
root = pathlib.Path(__file__).resolve().parent
repository = root.parent.parent
env = os.environ.copy()
env['JAVA_HOME'] = str(repository / 'dependencies/runtime/jdk25/jdk-25.0.2.jdk/Contents/Home')
# The environment cap reaches NeoForm/decompiler JVMs as well as the Gradle daemon.
env['JAVA_TOOL_OPTIONS'] = env.get('JAVA_TOOL_OPTIONS', '') + ' -XX:ActiveProcessorCount=2'
loaders = sys.argv[1:] or ['fabric', 'neoforge']
if any(loader not in ('fabric', 'neoforge') for loader in loaders):
    raise SystemExit('Loader must be fabric or neoforge')
for loader in loaders:
    command = [str(root / 'runtime/gradle-9.7.1/bin/gradle'), 'test', 'jar', '--no-daemon', '--max-workers=2']
    with (root / 'evidence' / (loader + '-build.log')).open('w') as log:
        process = subprocess.Popen(command, cwd=root / loader, env=env, stdout=log, stderr=subprocess.STDOUT)
        birth = subprocess.check_output(['ps', '-p', str(process.pid), '-o', 'lstart='], text=True).strip()
        (root / 'evidence/build-owner.json').write_text(json.dumps(dict(owner='port_263', loader=loader, pid=process.pid, birth=birth, argv=command, stop='Verify exact PID/birth/argv and descendants, then terminate this process only; preserve Gradle67529'), indent=2))
        status = process.wait()
    if status: raise SystemExit(status)
(root / 'evidence/build-owner.json').write_text(json.dumps(dict(owner='port_263', status='ended', liveProcesses=[]), indent=2))

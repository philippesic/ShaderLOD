#!/usr/bin/env python3
"""Run a bounded loader build and retain exact PID/birth/argv evidence."""
import pathlib,subprocess,os,json,sys,datetime
root=pathlib.Path(__file__).resolve().parent
loader=sys.argv[1];tag=sys.argv[2]
assert loader in ('fabric','neoforge')
env=dict(os.environ,JAVA_HOME=str(root.parent.parent/'dependencies/runtime/jdk25/jdk-25.0.2.jdk/Contents/Home'),JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=2')
cmd=[str(root/'runtime/gradle-9.5.0/bin/gradle'),'-p',str(root/loader),'--no-daemon','--max-workers=2','build']
log=root/'evidence'/f'{loader}-{tag}.log'
with log.open('w') as f:
 p=subprocess.Popen(cmd,env=env,stdout=f,stderr=subprocess.STDOUT)
 identity=subprocess.check_output(['ps','-p',str(p.pid),'-o','pid=,ppid=,lstart=,command='],text=True).strip()
 (root/'evidence'/f'{loader}-{tag}-owner.json').write_text(json.dumps(dict(owner='port_262',pid=p.pid,identity=identity,launchArgv=cmd,javaToolOptions=env['JAVA_TOOL_OPTIONS'],startedUtc=datetime.datetime.now(datetime.timezone.utc).isoformat(),stop='Reprove exact PID/birth/argv then SIGTERM exact owned CLI only'),indent=2)+'\n')
 print('Owned CLI',p.pid,flush=True);code=p.wait()
 print('Exit',code,flush=True);print('\n'.join(log.read_text().splitlines()[-45:]))
 raise SystemExit(code)

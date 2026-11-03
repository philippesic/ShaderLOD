#!/usr/bin/env python3
"""Owned bounded compile lane; JAVA_TOOL_OPTIONS scopes processor cap to all forks."""
import json,pathlib,subprocess,os,sys
root=pathlib.Path(__file__).resolve().parent
loader=sys.argv[1]; suffix=sys.argv[2]
assert loader in ('fabric','neoforge') and '/' not in suffix
repo=root.parents[1]; java=repo/'dependencies/runtime/jdk25/jdk-25.0.2.jdk/Contents/Home'
env=dict(os.environ,JAVA_HOME=str(java),JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=2')
log=root/'evidence'/f'{loader}-build-{suffix}.log';receipt=log.with_name(log.stem+'-owner.json')
tasks=sys.argv[3:] or ['build']
assert all(task in ('build','compileDevClientJava','processDevClientResources') for task in tasks)
cmd=[str(root/'runtime/gradle-9.4.0/bin/gradle'),'-p',str(root/loader),'--no-daemon','--max-workers=2',*tasks]
with log.open('w') as out:
 child=subprocess.Popen(cmd,env=env,stdout=out,stderr=subprocess.STDOUT)
 owner={'owner':'port_2612','launcherPid':os.getpid(),'pid':child.pid,'birth':subprocess.check_output(['ps','-p',str(child.pid),'-o','lstart='],text=True).strip(),'argv':cmd,'actualArgv':subprocess.check_output(['ps','-p',str(child.pid),'-o','command='],text=True).strip(),'log':str(log),'stop':'Reverify PID birth/argv then SIGTERM exact CLI only. Preserve67529.'}
 receipt.write_text(json.dumps(owner,indent=2)+'\n')
 print(json.dumps(owner),flush=True)
 result=child.wait()
 owner['exitCode']=result;owner['terminal']=True;receipt.write_text(json.dumps(owner,indent=2)+'\n')
 print('Build exit',result,flush=True)
 sys.exit(result)

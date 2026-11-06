"""Serialize maintained benchmark CLI calls; contains no benchmark implementation."""
import argparse
import hashlib
import json
from pathlib import Path
import signal
import subprocess
import time

p=argparse.ArgumentParser();p.add_argument('--jobs',required=True,type=Path);p.add_argument('--ledger',required=True,type=Path)
a=p.parse_args();jobs=json.loads(a.jobs.read_text());root=Path.cwd();stopping=False
def stop(signum,frame):
    global stopping
    stopping=True
signal.signal(signal.SIGTERM,stop)
signal.signal(signal.SIGINT,stop)
ledger={'jobs_sha256':hashlib.sha256(a.jobs.read_bytes()).hexdigest(),'started':time.time(),'runs':[]}
if a.ledger.exists():raise RuntimeError('Fresh queue ledger required')
def save():
    temp=a.ledger.with_suffix('.tmp');temp.write_text(json.dumps(ledger,indent=2)+'\n');temp.replace(a.ledger)
save()
for job in jobs:
    if stopping or (a.ledger.parent/'stop-queue').exists():break
    output=root/job['output']
    if output.exists():raise RuntimeError('Refusing existing operation '+str(output))
    row=job|{'started':time.time(),'state':'running'};ledger['runs'].append(row);save()
    print('START',job['name'],flush=True)
    log=a.ledger.parent/('queue-'+job['name']+'.log')
    with log.open('x') as stream:
        result=subprocess.run(job['command'],cwd=root,stdin=subprocess.DEVNULL,stdout=stream,stderr=subprocess.STDOUT)
    row.update(returncode=result.returncode,finished=time.time(),state='finished',log=str(log))
    summary=output/'summary.json'
    if summary.is_file():
        data=json.loads(summary.read_text());row['evaluation']=data.get('evaluation');row['error']=data.get('error')
        row['restored']=data.get('configuration_restored');row['clients_remaining']=data.get('clients_remaining')
        if not row['restored'] or row['clients_remaining']:
            row['state']='recovery-required';save();print('STOP recovery required',job['name'],flush=True);break
    else:
        row['state']='no-launch-or-preflight-failure'
    save();print('END',job['name'],'exit',result.returncode,'error',row.get('error'),flush=True)
ledger['finished']=time.time();ledger['stopped']=stopping;save()

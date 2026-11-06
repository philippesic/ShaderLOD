import json,subprocess,sys,datetime
from pathlib import Path
root=Path('C:/ShaderLODBench/ShaderLOD');out=root/'dev/experiments/rtx2080ti-individuals-20261104'
import dev_client as dev
import experiment_lifecycle as life
matrix=json.loads((out/'matrix.json').read_text());results=[]
for row in matrix['runs']:
 destination=root/row['output']
 if destination.exists():raise RuntimeError('Fresh report required: '+str(destination))
 if dev.clients(dev.process_table()):raise RuntimeError('Runtime occupied')
 result=subprocess.run([sys.executable,'scripts/run_benchmark.py','test',*row['arguments']],cwd=root)
 summary=json.loads((destination/'summary.json').read_text()) if (destination/'summary.json').exists() else {}
 operation=json.loads((destination/'operation.json').read_text()) if (destination/'operation.json').exists() else {}
 results.append({'goal':row['goal'],'exit_code':result.returncode,'summary':str(destination/'summary.json'),'error':summary.get('error'),'release_error':summary.get('release_error'),'evaluation':summary.get('evaluation'),'restored':summary.get('configuration_restored')})
 life.atomic_json(out/'batch-progress.json',{'updated':datetime.datetime.now(datetime.timezone.utc).isoformat(),'completed':results})
 if dev.clients(dev.process_table()) or operation and (not summary.get('configuration_restored') or operation.get('state') not in ('complete','failed-restored')):
  raise RuntimeError('Restore/release needs attention; remaining matrix withheld')
print('All requested matrix entries attempted')

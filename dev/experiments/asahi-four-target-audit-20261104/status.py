"""Read only small queue/control records; avoid parsing large source captures during timing."""
import json
from pathlib import Path
import time

root=Path(__file__).resolve().parent
checkpoint=json.loads((root/'checkpoint.json').read_text())
queue=json.loads((root/checkpoint['active_queue']).read_text())
rows=queue['runs'];done=[r for r in rows if r['state']!='running']
current=rows[-1] if rows and rows[-1]['state']=='running' else None
result={'queue':checkpoint['active_queue'],'finished_operations':len(done),'valid_operations':sum(r.get('evaluation',{}).get('validity')=='VALID' for r in done),
        'failed_operations':[(r['name'],str(r.get('error'))[:140]) for r in done if r.get('returncode')],
        'queue_finished':bool(queue.get('finished'))}
if current:
    output=Path(current['output']);output=output if output.is_absolute() else root.parents[2]/output
    control=output/'control.json';request=json.loads(control.read_text()) if control.exists() else {}
    result['current']={'job':current['name'],'kind':current['kind'],'shader':current['shader'],'goal':current.get('goal'),
                       'elapsed_minutes':round((time.time()-current['started'])/60,1),
                       'completed_windows':len(list((output/'windows').glob('*/guard.json'))),
                       'action':request.get('action'),'window':request.get('label')}
print(json.dumps(result))

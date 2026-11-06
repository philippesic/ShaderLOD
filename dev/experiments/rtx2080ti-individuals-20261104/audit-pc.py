import json,datetime
from pathlib import Path
import dev_client as dev
import experiment_lifecycle as life
import benchmark_iteration as iteration
root=Path('C:/ShaderLODBench/ShaderLOD/dev/experiments/rtx2080ti-individuals-20261104')
rows=[];profiles={}
for p in sorted(root.glob('*/operation.json')):
    o=json.loads(p.read_text());s=json.loads((p.parent/'summary.json').read_text())
    row={'run':p.parent.name,'state':o['state'],'restored':s.get('configuration_restored'),
         'release_verified':o.get('release_verified'),'clients_at_completion':s.get('clients_remaining'),
         'release_error':s.get('release_error')}
    assert row['state'] in ('complete','failed-restored') and row['restored'] and row['release_verified'] and row['clients_at_completion']==0 and not row['release_error'],row
    record=o.get('iteration_profile',{})
    if record:
        assert record.get('restored') and record.get('reset_verified'),p
        profiles[o['profile']]=record['source']
    rows.append(row)
for profile,source in profiles.items():
    assert iteration.profile_map(Path(profile))==iteration.profile_map(Path(source)),profile
clients=dev.clients(dev.process_table());assert not clients,clients
life.atomic_json(root/'terminal-audit.json',{'updated':datetime.datetime.now(datetime.timezone.utc).isoformat(),'operations':rows,'clients_remaining':0,'canonical_slots_verified':profiles})
print('Verified restored/released operations:',len(rows),'remaining clients:',len(clients))

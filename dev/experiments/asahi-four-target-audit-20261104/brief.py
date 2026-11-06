"""Small terminal-report reader for progress updates; never launches a benchmark."""
import json
from pathlib import Path
import statistics
import sys
HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE.parents[2]/'scripts'))
from benchmark_iteration import arm_metrics
for name in sys.argv[1:]:
    out=HERE/name;s=json.loads((out/'summary.json').read_text())
    result={'name':name,'validity':s.get('evaluation',{}).get('validity'),'restored':s.get('configuration_restored'),'error':s.get('error')}
    if result['validity']=='VALID' and s.get('purpose')=='benchmark':
        arms={p.name:arm_metrics(p,p.name) for p in (out/'windows').iterdir() if (p/'result.json').exists()}
        result['arms']={k:{key:round(v[key],4) for key in ('mean_total_frame_ms','sd_total_frame_ms','variance_total_frame_ms2','p95_total_frame_ms')} for k,v in arms.items()}
        native=[arms[k]['mean_total_frame_ms'] for k in sorted(arms) if k.startswith('native') or k=='return']
        candidate=[v['mean_total_frame_ms'] for k,v in arms.items() if k.startswith('candidate')]
        if native and candidate:
            result['frame_time_reduction_pct']=round(100*(statistics.mean(native)-statistics.mean(candidate))/statistics.mean(native),4)
            result['native_drift_pct']=round(100*(native[-1]-native[0])/native[0],4)
    if s.get('ao_scale_work',{}).get('candidate'):result['ao_work']=s['ao_scale_work']['candidate']['window_delta']
    if s.get('family_early_alpha_work',{}).get('candidate'):result['alpha_work']=s['family_early_alpha_work']['candidate']
    print(json.dumps(result))

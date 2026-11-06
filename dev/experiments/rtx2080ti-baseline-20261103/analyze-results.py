import json, statistics
from pathlib import Path
root=Path(__file__).resolve().parent
rows=[]
for name in ('null-p0-02','null-p0-03'):
 s=json.loads((root/name/'summary.json').read_text())
 e=s['evaluation']
 assert e['validity']=='VALID' and s['configuration_restored'] and s['clients_remaining']==0
 windows=[{'label':k, 'mean_ms':v['mean_total_frame_ms'],'p95_ms':v['p95_total_frame_ms']} for k,v in e['arms'].items()]
 means=[w['mean_ms'] for w in windows]
 telemetry=[]
 for label,pair in s.get('host_telemetry',{}).items():
  for phase,obs in pair.items():
   if phase not in ('before','after') or not isinstance(obs,dict):continue
   telemetry.append({'label':label,'phase':phase,'nvidia':obs.get('nvidia')})
 rows.append({'run':name,'order':e['order'],'windows':windows,'mean_ms':statistics.mean(means),'sd_window_means_ms':statistics.stdev(means),'window_cv_percent':100*statistics.stdev(means)/statistics.mean(means),'apparent_null_gain_percent':e['reduction_percent'],'same_arm_spread_percent':e['same_arm_spread_percent'],'gpu_endpoints':telemetry,'restored':True,'clients_remaining':0})
result={'scope':'Two independent native Windows RTX 2080 Ti process blocks; original-byte Bliss native463, mixed pose0, no DH, 2560x1600, 1536m heap. Four 60-second windows per block after 180-second conditioning; ABBA then BAAB.','variation_definition':'Sample standard deviation of four equally weighted window means, not per-frame variation or a confidence interval.','runs':rows,'process_mean_change_percent':100*(rows[1]['mean_ms']/rows[0]['mean_ms']-1),'limits':'Two blocks cannot establish a reliable universal noise floor. No optimization was enabled; apparent A/B gain is measurement noise. Different hardware speeds are not optimization gains.'}
(root/'result.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))

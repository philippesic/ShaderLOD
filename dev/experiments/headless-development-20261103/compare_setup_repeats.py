import json,statistics
from pathlib import Path
p=Path(__file__).resolve().parent
old=json.loads((p/'cooled-repeat-comparison.json').read_text())
new=[]
for i in range(1,4):
 run=p/f'alternate-setup-null-p0-{i:02d}';s=json.loads((run/'summary.json').read_text())
 assert s['evaluation']['validity']=='VALID' and s['configuration_restored'] and s['clients_remaining']==0
 v=[a['mean_total_frame_ms'] for a in s['evaluation']['arms'].values()]
 new.append({'run':run.name,'mean_ms':statistics.mean(v),'sd_window_means_ms':statistics.stdev(v),'windows':4,'processes':1,'window_means_ms':v,'null_effect_percent':s['evaluation']['reduction_percent'],'same_arm_spread_percent':s['evaluation']['same_arm_spread_percent'],'battery_C':{k:{t:int(a[t]['/sys/class/thermal/thermal_zone0/temp'])/1000 for t in ['before','after']} for k,a in s['host_telemetry'].items()}})
m=[r['mean_ms'] for r in new]
r={'original':old['previous'],'ice_first':old['cooled_first'],'ice_repeat':old['repeat'],'current_setup_runs':new,'current_setup_process_mean_ms':statistics.mean(m),'current_setup_process_sd_ms':statistics.stdev(m),'current_setup_process_cv_percent':100*statistics.stdev(m)/statistics.mean(m),'current_setup_process_range_ms':[min(m),max(m)],'limits':'Three fresh-process same-order ABBA runs, first run from preceding request. Not a balanced six-block calibration. Setup description not supplied. SD error bars describe60-second window means within each run, not frame jitter or confidence intervals. Sequential conditions do not isolate thermal causation.'}
(p/'setup-repeat-comparison.json').write_text(json.dumps(r,indent=2)+'\n')
groups=[r['original'],r['ice_first'],r['ice_repeat'],*new];labels=['Original','Ice 1','Ice 2','Setup 1','Setup 2','Setup 3'];colors=['var(--chart-1)','var(--chart-2)','var(--chart-2)','var(--chart-4)','var(--chart-4)','var(--chart-4)']
maximum=max(g['mean_ms']+g['sd_window_means_ms'] for g in groups)*1.15
scale=lambda v:255-v/maximum*215
svg=['<svg viewBox="0 0 850 320" role="img" aria-label="Average frame time and variation for original baseline, ice-pack checks, and three current-setup runs">']
for v in range(0,int(maximum)+1,20):
 y=scale(v);svg.append(f'<line x1="45" x2="835" y1="{y}" y2="{y}" stroke="var(--border)"/><text x="35" y="{y+4}" text-anchor="end">{v}</text>')
svg.append('<text x="45" y="18">Frame time (ms) · lower is faster</text>')
for i,g in enumerate(groups):
 x=115+i*135;m=g['mean_ms'];sd=g['sd_window_means_ms'];top=scale(m+sd);bottom=scale(m-sd)
 svg.append(f'<line x1="{x}" x2="{x}" y1="{top}" y2="{bottom}" stroke="{colors[i]}" stroke-width="3"/><path d="M{x-12},{top}h24 M{x-12},{bottom}h24" stroke="{colors[i]}" stroke-width="3"/><circle cx="{x}" cy="{scale(m)}" r="6" fill="{colors[i]}"/><text x="{x}" y="{top-12}" text-anchor="middle" class="value">{m:.1f} ± {sd:.1f}</text><text x="{x}" y="282" text-anchor="middle" class="label">{labels[i]}</text><text x="{x}" y="305" text-anchor="middle">{g["windows"]} windows</text>')
svg.append('</svg>')
html='<!doctype html><html><head><meta charset="utf-8"><style>body{font-family:var(--font-sans);color:var(--foreground)}svg{width:100%;height:320px;display:block}text{font-size:14px;fill:var(--muted-foreground)}.value,.label{fill:var(--foreground);font-weight:600}p{font-size:13px;line-height:1.5;margin:8px 0}</style></head><body>'+''.join(svg)+'<p>Dots: average. Whiskers: ±1 standard deviation of 60-second window averages. Original combines six runs; each other point is one fresh run. Same benchmark settings.</p><p>Setup 1 is the preceding test; Setup 2 and 3 are the two new repeats. Brief checks do not establish full calibration.</p></body></html>'
(p/'setup-repeat-comparison.html').write_text(html)
print(json.dumps({k:v for k,v in r.items() if k not in ['original','ice_first','ice_repeat']},indent=2))

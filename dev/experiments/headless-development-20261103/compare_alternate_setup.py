import json,statistics
from pathlib import Path
p=Path(__file__).resolve().parent
old=json.loads((p/'cooled-repeat-comparison.json').read_text())
s=json.loads((p/'alternate-setup-null-p0-01/summary.json').read_text())
assert s['evaluation']['validity']=='VALID' and s['configuration_restored'] and s['clients_remaining']==0
values=[v['mean_total_frame_ms'] for v in s['evaluation']['arms'].values()]
new={'mean_ms':statistics.mean(values),'sd_window_means_ms':statistics.stdev(values),'windows':4,'processes':1,'window_means_ms':values}
r={'previous':old['previous'],'cooled_first':old['cooled_first'],'cooled_repeat':old['repeat'],'new_setup':new,'variation_definition':old['variation_definition'],'limits':'Sequential sanity checks; four windows in each cooled process do not establish between-process reliability or isolate cooling causally. User changed physical setup; lid open at start. See alternate-setup-plan.json for description.','repeat_valid_restored':True,'battery_C':{k:{t:int(v[t]['/sys/class/thermal/thermal_zone0/temp'])/1000 for t in ['before','after']} for k,v in s['host_telemetry'].items()}}
(p/'alternate-setup-comparison.json').write_text(json.dumps(r,indent=2)+'\n')
groups=[r['previous'],r['cooled_first'],r['cooled_repeat'],r['new_setup']];labels=['Original','Ice pack 1','Ice pack 2','New setup'];colors=['var(--chart-1)','var(--chart-2)','var(--chart-3)','var(--chart-4)']
maximum=max(g['mean_ms']+g['sd_window_means_ms'] for g in groups)*1.15
scale=lambda v:260-v/maximum*220
svg=['<svg viewBox="0 0 760 325" role="img" aria-label="Average frame time and variation for original baseline and three setup checks">']
for v in range(0,int(maximum)+1,20):
 y=scale(v);svg.append(f'<line x1="55" x2="740" y1="{y}" y2="{y}" stroke="var(--border)"/><text x="45" y="{y+4}" text-anchor="end">{v}</text>')
svg.append('<text x="55" y="18">Frame time (ms) · lower is faster</text>')
for i,g in enumerate(groups):
 x=130+i*175;m=g['mean_ms'];sd=g['sd_window_means_ms'];top=scale(m+sd);bottom=scale(m-sd)
 svg.append(f'<line x1="{x}" x2="{x}" y1="{top}" y2="{bottom}" stroke="{colors[i]}" stroke-width="3"/><path d="M{x-14},{top}h28 M{x-14},{bottom}h28" stroke="{colors[i]}" stroke-width="3"/><circle cx="{x}" cy="{scale(m)}" r="6" fill="{colors[i]}"/><text x="{x}" y="{top-12}" text-anchor="middle" class="value">{m:.1f} ± {sd:.1f} ms</text><text x="{x}" y="286" text-anchor="middle" class="label">{labels[i]}</text><text x="{x}" y="309" text-anchor="middle">{g["windows"]} windows · {g["processes"]} '+('runs' if g['processes']>1 else 'run')+'</text>')
svg.append('</svg>')
html='<!doctype html><html><head><meta charset="utf-8"><style>body{font-family:var(--font-sans);color:var(--foreground)}svg{width:100%;height:325px;display:block}text{font-size:13px;fill:var(--muted-foreground)}.value,.label{fill:var(--foreground);font-weight:600}p{font-size:13px;line-height:1.5;margin:8px 0}</style></head><body>'+''.join(svg)+'<p>Dots: average. Whiskers: ±1 standard deviation across 60-second window averages. Same shader, scene, resolution and heap.</p><p>Brief sequential checks; variation within a run does not establish reliability across fresh runs.</p></body></html>'
(p/'alternate-setup-comparison.html').write_text(html)
print(json.dumps(r,indent=2))

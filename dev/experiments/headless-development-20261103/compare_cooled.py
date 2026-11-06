import json,statistics,datetime
from pathlib import Path
p=Path(__file__).resolve().parent
old=json.loads((p/'baseline-analysis.json').read_text())
s=json.loads((p/'cooled-null-p0-01/summary.json').read_text())
assert s['evaluation']['validity']=='VALID' and s['configuration_restored'] and s['clients_remaining']==0
new=[a['mean_total_frame_ms'] for a in s['evaluation']['arms'].values()]
previous=[r['mean_ms'] for r in old['windows']]
def summary(values,processes):
 return {'mean_ms':statistics.mean(values),'sd_window_means_ms':statistics.stdev(values),'windows':len(values),'processes':processes,'window_means_ms':values}
r={'previous':summary(previous,6),'cooled':summary(new,1),'variation_definition':'Sample standard deviation of60-second window means, not frame-to-frame SD or confidence interval. Windows within processes are correlated.','mean_reduction_percent':100*(statistics.mean(previous)-statistics.mean(new))/statistics.mean(previous),'limits':'Brief sequential sanity check; one cooled process versus six earlier processes. Cooling is not isolated from elapsed time or run effects.','new_valid_restored':True,'battery_C':{k:{t:int(v[t]['/sys/class/thermal/thermal_zone0/temp'])/1000 for t in ['before','after']} for k,v in s['host_telemetry'].items()}}
(p/'cooled-comparison.json').write_text(json.dumps(r,indent=2)+'\n')
colors=['var(--chart-1)','var(--chart-2)']; groups=[r['previous'],r['cooled']]; labels=['Previous baseline','With ice pack']
maximum=max(g['mean_ms']+g['sd_window_means_ms'] for g in groups)*1.12
scale=lambda v:270-v/maximum*230
svg=['<svg viewBox="0 0 650 330" role="img" aria-label="Average frame time and standard deviation before and with ice pack">']
for v in range(0,int(maximum)+1,20):
 y=scale(v);svg.append(f'<line x1="60" x2="620" y1="{y}" y2="{y}" stroke="var(--border)"/><text x="50" y="{y+4}" text-anchor="end">{v}</text>')
svg.append('<text x="60" y="18">Frame time (ms) · lower is faster</text>')
for i,g in enumerate(groups):
 x=220+i*250;m=g['mean_ms'];sd=g['sd_window_means_ms'];top=scale(m+sd);bottom=scale(m-sd)
 svg.append(f'<line x1="{x}" x2="{x}" y1="{top}" y2="{bottom}" stroke="{colors[i]}" stroke-width="3"/><path d="M{x-14},{top}h28 M{x-14},{bottom}h28" stroke="{colors[i]}" stroke-width="3"/><circle cx="{x}" cy="{scale(m)}" r="7" fill="{colors[i]}"/><text x="{x}" y="{top-12}" text-anchor="middle" class="value">{m:.1f} ± {sd:.1f} ms</text><text x="{x}" y="295" text-anchor="middle" class="label">{labels[i]}</text><text x="{x}" y="317" text-anchor="middle">{g["windows"]} windows · {g["processes"]} '+('runs' if g['processes']>1 else 'run')+'</text>')
svg.append('</svg>')
html='<!doctype html><html><head><meta charset="utf-8"><style>body{font-family:var(--font-sans);color:var(--foreground)}svg{width:100%;height:330px;display:block}text{font-size:13px;fill:var(--muted-foreground)}.value,.label{fill:var(--foreground);font-weight:600}p{font-size:13px;line-height:1.5;margin:8px 0}</style></head><body>'+''.join(svg)+f'<p>Dots: average. Whiskers: ±1 standard deviation across 60-second window averages. Same shader, scene, resolution and heap.</p><p><strong>{r["mean_reduction_percent"]:.1f}% lower average frame time with the ice pack.</strong> One cooled run is a sanity check, not proof of causation.</p></body></html>'
(p/'cooled-comparison.html').write_text(html)
print(json.dumps(r,indent=2))

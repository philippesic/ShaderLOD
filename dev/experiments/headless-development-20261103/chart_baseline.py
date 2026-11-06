"""Render retained baseline results; no benchmark execution or filtering."""
import json, math
from pathlib import Path
p=Path(__file__).resolve().parent
s=json.loads((p/'baseline-analysis.json').read_text()); rows=s['windows']
low=math.floor(min(r['mean_ms'] for r in rows)/5)*5-5
high=math.ceil(max(r['mean_ms'] for r in rows)/5)*5+5
x=lambda i: 48+i*626/(len(rows)-1)
y=lambda v: 240-(v-low)*205/(high-low)
svg=['<svg viewBox="0 0 710 280" role="img" aria-label="Mean frame time by capture window; lower is faster">']
for v in range(low,high+1,5):
 svg.append(f'<line x1="48" x2="674" y1="{y(v)}" y2="{y(v)}" stroke="var(--border)"/><text x="40" y="{y(v)+4}" text-anchor="end">{v}</text>')
for b in range(6):
 pts=' '.join(f'{x(i):.2f},{y(rows[i]["mean_ms"]):.2f}' for i in range(b*4,b*4+4))
 svg.append(f'<polyline points="{pts}" fill="none" stroke="var(--chart-1)" stroke-width="2"/>')
 svg.append(f'<text x="{x(b*4+1.5)}" y="263" text-anchor="middle">Run {b+1}</text>')
for i,r in enumerate(rows):
 svg.append(f'<circle tabindex="0" cx="{x(i)}" cy="{y(r["mean_ms"])}" r="4" fill="var(--chart-1)"><title>{r["block"]} {r["window"]}: {r["mean_ms"]:.2f} ms; p95 {r["p95_ms"]:.2f} ms</title></circle>')
svg.append('<text x="48" y="18">Mean frame time (ms) · lower is faster</text></svg>')
html='''<!doctype html><html><head><meta charset="utf-8"><style>body{font-family:var(--font-sans);color:var(--foreground)}svg{width:100%;height:280px;display:block}text{fill:var(--muted-foreground);font-size:12px}p{font-size:14px;line-height:1.5;margin:8px 0}strong{color:var(--foreground)}</style></head><body>'''+''.join(svg)+f'<p><strong>Identical shaders throughout.</strong> Six fresh Minecraft processes, four 60-second captures each, with alternating ABBA/BAAB order. Closed lid; native 2560×1600. Run-average frame times: '+', '.join(f'{m:.1f}' for m in s['process_mean_ms'])+' ms.</p><p>One scene only. All valid runs retained, including a brief Wi-Fi settings save during run 2. Lines connect captures within each process; gaps separate restarts.</p></body></html>'
(p/'baseline-chart.html').write_text(html)

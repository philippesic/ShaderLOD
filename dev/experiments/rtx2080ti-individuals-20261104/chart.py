import json,math,html
from pathlib import Path
root=Path(__file__).resolve().parent
results=json.loads((root/'results.json').read_text());rows=[r for r in results['rows'] if r['valid']]
low=min([0]+[min(r['speedup_against_controls_percent']) for r in rows]);high=max([0]+[max(r['speedup_against_controls_percent']) for r in rows]);span=max(high-low,1);low-=span*.08;high+=span*.08
x=lambda v:155+(v-low)/(high-low)*450
height=48+len(rows)*34
parts=[f'<svg viewBox="0 0 710 {height}" role="img" aria-label="Observed percentage speedup on RTX 2080 Ti"><line x1="{x(0)}" x2="{x(0)}" y1="18" y2="{height-28}" stroke="var(--border)"/>']
for i,r in enumerate(rows):
    y=30+i*34;v=r['speedup_percent'];a,b=sorted(r['speedup_against_controls_percent']);color='var(--success)' if v>0 else 'var(--destructive)'
    parts.append(f'<text x="0" y="{y+4}">{html.escape(r["goal"])}</text><rect x="{min(x(0),x(v))}" y="{y-5}" width="{max(abs(x(v)-x(0)),1)}" height="10" fill="{color}" opacity=".4"/><line x1="{x(a)}" x2="{x(b)}" y1="{y}" y2="{y}" stroke="{color}" stroke-width="2"/><circle cx="{x(v)}" cy="{y}" r="3" fill="{color}"/><text x="625" y="{y+4}">{v:+.2f}%</text>')
for v in (low,0,high):parts.append(f'<text x="{x(v)}" y="{height-6}" text-anchor="middle" class="axis">{v:.1f}%</text>')
parts.append('</svg>')
page='''<!doctype html><html><head><meta charset="utf-8"><style>:root{font-family:var(--font-sans)}body{margin:0;color:var(--foreground)}svg{width:100%;display:block}text{fill:var(--foreground);font-size:14px;font-family:var(--font-sans)}.axis{fill:var(--muted-foreground);font-size:12px}p{color:var(--muted-foreground);font-size:12px;line-height:1.5;margin:8px 0}</style></head><body>'''+''.join(parts)+'''<p>Positive = faster. Points use the mean of the two native controls; line endpoints show comparison with each control, not a confidence interval. One 20-second native/candidate/return bracket per tested variant. Different candidates use different packs/scenes. Refused candidates have no plotted value.</p></body></html>'''
(root/'speedups.html').write_text(page)

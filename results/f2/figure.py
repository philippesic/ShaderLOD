import json
from pathlib import Path

rows = json.loads(Path(__file__).with_name("data.json").read_text())
print('<!doctype html><html><head><meta charset="utf-8"><style>body{font-family:var(--font-sans, sans-serif);color:var(--foreground);line-height:1.5}svg{width:100%;height:300px}text{fill:currentColor;font-size:14px}.bar{fill:var(--chart-1,steelblue)}.axis{stroke:var(--border,gray)}</style></head><body><h3>Unchanged shaders produced apparent gains and losses</h3><p>Six fresh processes; expected effect: 0%. Each bar summarizes a four-window comparison.</p><svg viewBox="0 0 650 300" role="img" aria-label="Apparent performance gains range from minus 9.70 to plus 10.24 percent with unchanged shaders">')
for x in (-10, -5, 0, 5, 10):
    pos = 325 + x * 20
    print(f'<line class="axis" x1="{pos}" x2="{pos}" y1="10" y2="260"/><text x="{pos}" y="285" text-anchor="middle">{x}%</text>')
for i, row in enumerate(rows):
    value = row["apparent_gain_percent"]
    y = 16 + i * 40
    x = 325 + min(value, 0) * 20
    print(f'<text x="5" y="{y + 19}">Run {i + 1}</text><rect class="bar" x="{x}" y="{y}" width="{abs(value) * 20}" height="25"/><text x="570" y="{y + 19}">{value:+.2f}%</text>')
print('</svg><p>Positive means apparently faster; negative means apparently slower. No optimization was applied. These are individual process effects, not error bars.</p></body></html>')

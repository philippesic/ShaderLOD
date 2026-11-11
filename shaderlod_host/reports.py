import hashlib
import json
from pathlib import Path

from shaderlod_host.benchmark import verify_frame_receipt
from shaderlod_host.storage import publish, publish_bytes


def history(runs: list[Path], output: Path) -> dict[str, object]:
    if output.exists():
        raise ValueError("fresh history output required")
    groups: dict[str, list[dict[str, object]]] = {}
    failures = []
    for path in runs:
        result = json.loads(path.read_bytes())
        if result.get("schema") != "shaderlod-comparison-result-v1":
            raise ValueError("comparison result required")
        receipt = json.loads((path.parent / "receipt.json").read_bytes())
        if receipt.get("sha256") != hashlib.sha256(path.read_bytes()).hexdigest():
            raise ValueError("comparison digest disagreement")
        plan = json.loads((path.parent / "plan.json").read_bytes())["definition"]
        if result.get("state") != "complete":
            failures.append(
                {
                    "source": str(path),
                    "failure": result.get("failure"),
                    "started_unix_ns": result.get("started_unix_ns"),
                }
            )
        for row in result.get("observations", []):
            selection = row["selection"]
            directory = path.parent / row["attempt"]
            verify_frame_receipt(directory / "receipt.json")
            if row["summary"] != json.loads((directory / "summary.json").read_bytes()):
                raise ValueError("history summary differs from raw reduction")
            case = next(c for c in plan["cases"] if c["id"] == row["case"])
            context = {
                "case": case,
                "renderer": selection.get("renderer"),
                "runtime": selection.get("runtime"),
                "resource_packs": selection.get("resource_packs"),
                "options": selection.get("options"),
                "client_options": selection.get("client_options"),
                "host": selection.get("session_identity", {}).get("host"),
                "instrument": row.get("instrument", "frames"),
                "quality_only": row.get("quality_only", False),
                "window_ms": plan["window_ms"],
                "warmup_ms": plan["warmup_ms"],
            }
            identity = hashlib.sha256(
                json.dumps(context, sort_keys=True).encode()
            ).hexdigest()
            record = {
                "source": str(path),
                "context": context,
                "case": row["case"],
                "arm": row["arm"],
                "block": row["block"],
                "started_unix_ns": result.get("started_unix_ns"),
                "fps": row["summary"].get("fps"),
                "frame_ms_p95": row["summary"].get("frame_ms_p95"),
                "valid": row.get("workload_valid") is True
                and row["summary"].get("valid") is True,
                "build": selection.get("loaded_main_class_sha256"),
                "timeline": str(directory / "report.html"),
            }
            groups.setdefault(identity, []).append(record)
    for series in groups.values():
        series.sort(key=lambda row: int(str(row["started_unix_ns"])))
    result = {
        "schema": "shaderlod-history-v1",
        "groups": groups,
        "failures": failures,
        "limits": "Incompatible contexts are separate. Chronological points are exploratory; blocks and frames are not independent process replicates. No pooled cross-machine curve.",
    }
    publish(output / "history.json", result)
    data = json.dumps(result, allow_nan=False).replace("<", "\\u003c")
    html = """<!doctype html><meta charset="utf-8"><title>ShaderLOD comparison history</title><style>body{font:16px system-ui;margin:2rem;background:#121720;color:white}svg{width:100%;height:320px}pre{white-space:pre-wrap}</style><h1>Performance across comparisons</h1><p id="limits"></p><div id="groups"></div><h2>Failed / incomplete attempts</h2><pre id="failures"></pre><script>const data=DATA;document.getElementById('limits').textContent=data.limits;document.getElementById('failures').textContent=JSON.stringify(data.failures,null,2);let count=0;for(const [id,rows] of Object.entries(data.groups)){const area=document.createElement('section'),h=document.createElement('h2');h.textContent='Compatible series '+(++count)+' — '+rows[0].case;area.append(h);const ns='http://www.w3.org/2000/svg',svg=document.createElementNS(ns,'svg');svg.setAttribute('viewBox','0 0 1200 320');const maximum=Math.max(1,...rows.map(r=>r.fps||0)),earliest=rows[0].started_unix_ns,span=Math.max(1,rows.at(-1).started_unix_ns-earliest),arms=[...new Set(rows.map(r=>r.arm))],colors=['#6bd2ff','#ffad62','#8ef0af','#e9a2ed'];for(const r of rows){const x=50+(r.started_unix_ns-earliest)/span*1080,y=270-(r.fps||0)/maximum*230,p=document.createElementNS(ns,r.valid?'circle':'rect');p.setAttribute('cx',x);p.setAttribute('cy',y);p.setAttribute('r',5);p.setAttribute('x',x-4);p.setAttribute('y',y-4);p.setAttribute('width',8);p.setAttribute('height',8);p.setAttribute('fill',colors[arms.indexOf(r.arm)%colors.length]);const title=document.createElementNS(ns,'title');title.textContent=r.arm+' / '+new Date(r.started_unix_ns/1e6).toISOString()+' / '+r.fps+' FPS / block '+r.block+' / '+r.source;p.append(title);svg.append(p);}const label=document.createElementNS(ns,'text');label.setAttribute('x',20);label.setAttribute('y',20);label.setAttribute('fill','white');label.textContent='FPS (0–'+maximum.toFixed(1)+'); chronological time; '+arms.join(' / ');svg.append(label);area.append(svg);const d=document.createElement('details'),s=document.createElement('summary'),pre=document.createElement('pre');s.textContent='Exact contexts, points and evidence links';pre.textContent=JSON.stringify(rows,null,2);d.append(s,pre);area.append(d);document.getElementById('groups').append(area);}</script>""".replace(
        "DATA", data
    )
    publish_bytes(output / "report.html", html.encode())
    return result

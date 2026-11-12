import hashlib
import json
from pathlib import Path

from shaderlod_host.benchmark import (
    verify_frame_receipt,
    build_identity,
    collector_identity,
)
from shaderlod_host.storage import publish, publish_bytes
from shaderlod_host.comparison import configuration


def history(
    runs: list[Path], output: Path, recorded_assessment: dict[str, object] | None = None
) -> dict[str, object]:
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
                "native_configuration": configuration(selection),
                "collector_sha256": selection.get("loaded_dev_class_sha256"),
                "collector_inputs": collector_identity(
                    selection.get("session_identity")
                ),
                "renderer": selection.get("renderer"),
                "runtime": selection.get("runtime"),
                "runtime_mods": selection.get("runtime_mods"),
                "world_seed": selection.get("world_seed"),
                "world_path": selection.get("world_path"),
                "resource_packs": selection.get("resource_packs"),
                "options": selection.get("options"),
                "client_options": selection.get("client_options"),
                "host": selection.get("session_identity", {}).get("host"),
                "instrumentation": selection.get("session_identity", {}).get(
                    "instrumentation"
                ),
                "instrument": row.get("instrument", "frames"),
                "quality_only": row.get("quality_only", False),
                "quality_cadence_ms": plan.get("quality_cadence_ms", 0),
                "mapping": row["summary"].get("mapping"),
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
                "build": build_identity(selection.get("session_identity")),
                "timeline": str(directory / "report.html"),
            }
            groups.setdefault(identity, []).append(record)
    for series in groups.values():
        series.sort(key=lambda row: int(str(row["started_unix_ns"])))
    result = {
        "schema": "shaderlod-history-v1",
        "recorded_assessment": recorded_assessment,
        "groups": groups,
        "failures": failures,
        "limits": "Incompatible contexts are separate. Chronological points are exploratory; blocks and frames are not independent process replicates. No pooled cross-machine curve.",
    }
    publish(output / "history.json", result)
    data = json.dumps(result, allow_nan=False).replace("<", "\\u003c")
    html = """<!doctype html><meta charset="utf-8"><title>ShaderLOD comparison history</title><style>body{font:16px system-ui;margin:2rem;background:#121720;color:white}svg{width:100%;height:320px}pre{white-space:pre-wrap}</style><h1>Performance across comparisons</h1><p id="limits"></p><section id="assessment"></section><div id="groups"></div><h2>Failed / incomplete attempts</h2><pre id="failures"></pre><script>const data=DATA;const assessment=data.recorded_assessment;if(assessment){const area=document.getElementById('assessment'),h=document.createElement('h2');h.textContent='Recorded campaign decision: '+assessment.recommendation;area.append(h);const note=document.createElement('p');note.textContent='Declared policy only; quality review complete: '+assessment.quality_review_complete+'. Problems: '+JSON.stringify(assessment.problems||[]);area.append(note);const table=document.createElement('table');for(const item of assessment.cases||[]){const row=table.insertRow(),interval=item.interval;for(const value of [item.case,item.state,interval&&typeof interval.median==='number'?(interval.median*100).toFixed(2)+'% median throughput change':'estimate unavailable',interval&&typeof interval.low==='number'&&typeof interval.high==='number'?'interval '+(interval.low*100).toFixed(2)+'% to '+(interval.high*100).toFixed(2)+'%':'interval inconclusive']){const cell=row.insertCell();cell.textContent=value;cell.style.padding='.5rem';}}area.append(table);}document.getElementById('limits').textContent=data.limits;document.getElementById('failures').textContent=JSON.stringify(data.failures,null,2);let count=0;for(const [id,rows] of Object.entries(data.groups)){const area=document.createElement('section'),h=document.createElement('h2');h.textContent='Compatible series '+(++count)+' — '+rows[0].case;area.append(h);const ns='http://www.w3.org/2000/svg',svg=document.createElementNS(ns,'svg');svg.setAttribute('viewBox','0 0 1200 320');const maximum=Math.max(1,...rows.map(r=>r.fps||0)),earliest=rows[0].started_unix_ns,span=Math.max(1,rows.at(-1).started_unix_ns-earliest),arms=[...new Set(rows.map(r=>r.arm))],colors=['#6bd2ff','#ffad62','#8ef0af','#e9a2ed'];for(const r of rows){const x=50+(r.started_unix_ns-earliest)/span*1080,y=270-(r.fps||0)/maximum*230,p=document.createElementNS(ns,r.valid?'circle':'rect');p.setAttribute('cx',x);p.setAttribute('cy',y);p.setAttribute('r',5);p.setAttribute('x',x-4);p.setAttribute('y',y-4);p.setAttribute('width',8);p.setAttribute('height',8);p.setAttribute('fill',colors[arms.indexOf(r.arm)%colors.length]);const title=document.createElementNS(ns,'title');title.textContent=r.arm+' / '+new Date(r.started_unix_ns/1e6).toISOString()+' / '+r.fps+' FPS / block '+r.block+' / '+r.source;p.append(title);svg.append(p);}for(const [x1,y1,x2,y2] of [[50,40,50,270],[50,270,1130,270]]){const axis=document.createElementNS(ns,'line');for(const [key,value] of Object.entries({x1,y1,x2,y2,stroke:'#567'}))axis.setAttribute(key,value);svg.append(axis);}function caption(x,y,text,color='white'){const node=document.createElementNS(ns,'text');node.setAttribute('x',x);node.setAttribute('y',y);node.setAttribute('fill',color);node.setAttribute('font-size',12);node.textContent=text;svg.append(node);}for(let tick=0;tick<=4;tick++)caption(5,274-tick*230/4,(maximum*tick/4).toFixed(1));caption(50,290,new Date(earliest/1e6).toISOString());caption(800,290,new Date(rows.at(-1).started_unix_ns/1e6).toISOString());arms.forEach((arm,index)=>caption(50+index*180,312,arm,colors[index%colors.length]));const label=document.createElementNS(ns,'text');label.setAttribute('x',20);label.setAttribute('y',20);label.setAttribute('fill','white');label.textContent='FPS (0–'+maximum.toFixed(1)+'); chronological time; '+arms.join(' / ');svg.append(label);area.append(svg);const d=document.createElement('details'),s=document.createElement('summary'),pre=document.createElement('pre');s.textContent='Exact contexts, points and evidence links';pre.textContent=JSON.stringify(rows,null,2);d.append(s,pre);area.append(d);document.getElementById('groups').append(area);}</script>""".replace(
        "DATA", data
    )
    publish_bytes(output / "report.html", html.encode())
    return result


def rebuild(source: Path, output: Path) -> dict[str, object]:
    from shaderlod_host.benchmark import read_frames, render_frames
    from shaderlod_host.comparison import verify_comparison, render_comparison

    if output.exists():
        raise ValueError("fresh report output required")
    record = json.loads(source.read_bytes())
    kind = record.get("schema")
    if kind == "shaderlod-frame-capture-v1":
        verify_frame_receipt(source)
        summary = json.loads((source.parent / "summary.json").read_bytes())
        rendered = render_frames(read_frames(source.parent / "frames.jsonl"), summary)
        publish(output / "summary.json", summary)
    elif kind == "shaderlod-comparison-result-v1":
        record = verify_comparison(source)
        view = json.loads(json.dumps(record))
        for row in view["observations"]:
            row["attempt"] = (source.parent / row["attempt"]).resolve().as_uri()
        rendered = render_comparison(view)
        publish_bytes(output / "result.json", source.read_bytes())
    elif kind == "shaderlod-campaign-result-v1":
        from shaderlod_host.campaign import campaign_results

        assessment = record.get("assessment")
        if assessment is not None and not isinstance(assessment, dict):
            raise ValueError("malformed recorded campaign assessment")
        return history(campaign_results(source.parent), output, assessment)
    else:
        raise ValueError("completed capture/comparison or campaign result required")
    publish_bytes(output / "report.html", rendered.encode())
    manifest: dict[str, object] = {
        "schema": "shaderlod-derived-report-v1",
        "source": str(source.resolve()),
        "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
        "renderer_sha256": {
            path.name: hashlib.sha256(path.read_bytes()).hexdigest()
            for path in (
                Path(__file__),
                *Path(__file__).with_name("templates").glob("*.html"),
            )
        },
        "report_sha256": hashlib.sha256(rendered.encode()).hexdigest(),
        "limits": "Offline derivative; no new evidence or qualification. Linked raw assets remain at their preserved source location.",
    }
    publish(output / "manifest.json", manifest)
    return manifest

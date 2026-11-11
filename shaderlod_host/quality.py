import hashlib
import json
from pathlib import Path

from shaderlod_host.benchmark import label, positive
from shaderlod_host.storage import publish, publish_bytes


def save_sequence(
    observed: dict[str, object],
    session: Path,
    output: Path,
    cadence_ms: int,
    duration_ms: int,
) -> dict[str, object]:
    frames = observed.get("quality_frames")
    if not isinstance(frames, list) or not frames:
        raise ValueError("quality capture has no frames")
    records: list[dict[str, object]] = []
    previous = 0
    gaps = []
    for frame in frames:
        if not isinstance(frame, dict):
            raise ValueError("malformed quality frame")
        name = label(frame.get("image"), "image")
        if Path(name).name != name or "\\" in name:
            raise ValueError("quality asset must be a local filename")
        source = session / name
        if source.is_symlink():
            raise ValueError("quality asset is a symlink")
        payload = source.read_bytes()
        if hashlib.sha256(payload).hexdigest() != frame.get("image_sha256"):
            raise ValueError("quality frame digest disagreement")
        timestamp = positive(frame.get("elapsed_ns"), "elapsed_ns")
        if timestamp <= previous:
            raise ValueError("quality sequence clock disorder")
        gaps.append(timestamp - previous)
        previous = timestamp
        publish_bytes(output / name, payload)
        records.append(frame)
    gaps.append(max(0, duration_ms * 1000000 - previous))
    record: dict[str, object] = {
        "schema": "shaderlod-quality-sequence-v1",
        "cadence_ms": cadence_ms,
        "duration_ms": duration_ms,
        "frames": records,
        "max_gap_ms": max(gaps) / 1000000,
        "cadence_met": max(gaps) <= cadence_ms * 1500000,
        "delivery": observed.get(
            "quality_status", {"complete": False, "reason": "delivery status missing"}
        ),
        "alignment": "client simulation ticks and actual captured pose; frame histories may differ",
        "limits": "IO/readback included here; excluded from acceptance timing. Brief defects between sampled frames can be missed. Human perceptual review required.",
    }
    publish(output / "quality.json", record)
    data = json.dumps(record, allow_nan=False).replace("<", "\\u003c")
    html = """<!doctype html><meta charset="utf-8"><title>ShaderLOD quality sequence</title><style>body{font:16px system-ui;background:#121720;color:white}img{max-width:100%}pre{white-space:pre-wrap}</style><h1>Untimed quality sequence</h1><button id="play">Play / pause</button><input id="frame" type="range" min="0" value="0"><p id="state"></p><img id="image"><pre id="limits"></pre><script>const d=DATA,s=document.getElementById('frame'),i=document.getElementById('image'),t=document.getElementById('state');s.max=d.frames.length-1;document.getElementById('limits').textContent=JSON.stringify(d,null,2);function draw(){const f=d.frames[+s.value];i.src=f.image;t.textContent=(f.elapsed_ns/1e9).toFixed(3)+' s; simulation tick '+f.client_ticks+'; max gap '+d.max_gap_ms+' ms';}s.oninput=draw;let playing=false;document.getElementById('play').onclick=()=>playing=!playing;setInterval(()=>{if(playing){s.value=(+s.value+1)%d.frames.length;draw();}},d.cadence_ms);draw();</script>""".replace(
        "DATA", data
    )
    publish_bytes(output / "quality.html", html.encode())
    return record

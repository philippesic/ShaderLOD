from dataclasses import dataclass
import hashlib
import json
import math
import socket
import time
from pathlib import Path
from typing import Iterable

from shaderlod_host.storage import publish, publish_bytes


def object_fields(value: object, fields: set[str], name: str) -> dict[str, object]:
    if not isinstance(value, dict) or set(value) != fields:
        raise ValueError(name + ": unexpected or missing fields")
    if not all(isinstance(key, str) for key in value):
        raise ValueError(name + ": non-string key")
    return value


def positive(value: object, name: str) -> int:
    if type(value) is not int or value < 1:
        raise ValueError(name + ": positive integer required")
    return value


def label(value: object, name: str) -> str:
    if not isinstance(value, str) or not value or len(value) > 256:
        raise ValueError(name + ": bounded nonempty string required")
    return value


@dataclass(frozen=True)
class WorkflowPlan:
    scope: str
    workload: str
    arms: tuple[str, ...]
    blocks: int
    window_ms: int
    budget_ms: int
    instrument: str

    @classmethod
    def decode(cls, value: object) -> "WorkflowPlan":
        data = object_fields(
            value,
            {
                "schema",
                "scope",
                "workload",
                "arms",
                "blocks",
                "window_ms",
                "budget_ms",
                "instrument",
            },
            "workflow",
        )
        if data["schema"] != "shaderlod-workflow-v1":
            raise ValueError("unsupported workflow schema")
        scope = label(data["scope"], "scope")
        if scope not in {"profile", "development", "contribution", "release"}:
            raise ValueError("unsupported workflow scope")
        raw_arms = data["arms"]
        if not isinstance(raw_arms, list) or not 1 <= len(raw_arms) <= 16:
            raise ValueError("one to sixteen arms required")
        arms = tuple(label(arm, "arm") for arm in raw_arms)
        if len(set(arms)) != len(arms) or (scope != "profile" and len(arms) < 2):
            raise ValueError("distinct comparison arms required")
        blocks = positive(data["blocks"], "blocks")
        window = positive(data["window_ms"], "window_ms")
        budget = positive(data["budget_ms"], "budget_ms")
        if blocks > 100 or window > 3600000 or budget > 86400000:
            raise ValueError("workflow exceeds supported finite bounds")
        instrument = label(data["instrument"], "instrument")
        if instrument not in {"frames", "diagnostic"}:
            raise ValueError("unknown instrument")
        if scope in {"contribution", "release"} and instrument != "frames":
            raise ValueError("qualification requires minimally instrumented outcomes")
        if len(arms) * blocks * window >= budget:
            raise ValueError("budget must leave time beyond capture for lifecycle work")
        return cls(
            scope,
            label(data["workload"], "workload"),
            arms,
            blocks,
            window,
            budget,
            instrument,
        )

    def describe(self) -> dict[str, object]:
        return {
            "schema": "shaderlod-workflow-plan-v1",
            "scope": self.scope,
            "workload": self.workload,
            "arms": list(self.arms),
            "blocks": self.blocks,
            "window_ms": self.window_ms,
            "capture_ms": len(self.arms) * self.blocks * self.window_ms,
            "budget_ms": self.budget_ms,
            "instrument": self.instrument,
            "live_capability": "not yet qualified",
            "acceptance": "no retention policy supplied",
        }


@dataclass(frozen=True)
class Frame:
    sequence: int
    timestamp_ns: int
    interval_ns: int
    generation: int

    @classmethod
    def decode(cls, value: object) -> "Frame":
        data = object_fields(
            value, {"sequence", "timestamp_ns", "interval_ns", "generation"}, "frame"
        )
        generation = data["generation"]
        if type(generation) is not int or generation < 0:
            raise ValueError("nonnegative generation required")
        return cls(
            positive(data["sequence"], "sequence"),
            positive(data["timestamp_ns"], "timestamp_ns"),
            positive(data["interval_ns"], "interval_ns"),
            generation,
        )


def read_frames(path: Path, limit_bytes: int = 67108864) -> tuple[Frame, ...]:
    if path.stat().st_size > limit_bytes:
        raise ValueError("frame stream exceeds declared input limit")
    return decode_frames(path.read_bytes())


def decode_frames(payload: bytes) -> tuple[Frame, ...]:
    rows = []
    if len(payload) > 67108864:
        raise ValueError("frame stream exceeds supported limit")
    for line in payload.splitlines(keepends=True):
        if len(line) > 4096 or not line.endswith(b"\n"):
            raise ValueError("oversized or incomplete frame record")
        rows.append(Frame.decode(json.loads(line)))
    return tuple(rows)


def analyze_frames(frames: Iterable[Frame]) -> dict[str, object]:
    rows = tuple(frames)
    if not rows:
        raise ValueError("empty frame stream")
    generations = {row.generation for row in rows}
    missing = 0
    previous = None
    bins: dict[int, list[int]] = {}
    origin = rows[0].timestamp_ns - rows[0].interval_ns
    for row in rows:
        if previous is not None:
            if (
                row.sequence <= previous.sequence
                or row.timestamp_ns <= previous.timestamp_ns
            ):
                raise ValueError("frame sequence and time must increase")
            gap = row.sequence - previous.sequence - 1
            missing += gap
            if gap == 0 and row.timestamp_ns - previous.timestamp_ns != row.interval_ns:
                raise ValueError("frame interval disagrees with clock")
        elapsed = (row.timestamp_ns - origin - 1) // 1000000000
        bins.setdefault(elapsed, []).append(row.interval_ns)
        previous = row
    elapsed_ns = rows[-1].timestamp_ns - origin
    durations = sorted(row.interval_ns / 1000000 for row in rows)
    valid = missing == 0 and len(generations) == 1
    return {
        "schema": "shaderlod-frame-analysis-v1",
        "frames": len(rows),
        "elapsed_ns": elapsed_ns,
        "missing_frames": missing,
        "generations": sorted(generations),
        "valid": valid,
        "fps": len(rows) * 1000000000 / elapsed_ns if valid else None,
        "frame_ms_p50": durations[math.ceil(len(rows) * 0.5) - 1],
        "frame_ms_p95": durations[math.ceil(len(rows) * 0.95) - 1],
        "frame_ms_max": durations[-1],
        "tail_limit": "descriptive samples, not independent trials",
        "bins": [
            {
                "second": second,
                "frames": len(values),
                "observed_interval_ns": sum(values),
            }
            for second, values in sorted(bins.items())
        ],
        "scope": "application frame production; no GPU attribution or display guarantee",
    }


def import_capture(source: Path, destination: Path) -> dict[str, object]:
    if destination.exists():
        raise ValueError("fresh report directory required")
    if source.stat().st_size > 67108864:
        raise ValueError("frame input exceeds supported limit")
    payload = source.read_bytes()
    frames = decode_frames(payload)
    analysis = analyze_frames(frames)
    destination.mkdir(parents=True)
    publish_bytes(destination / "frames.jsonl", payload)
    publish_bytes(destination / "report.html", render_frames(frames, analysis).encode())
    publish(destination / "summary.json", analysis)
    identity = {
        "schema": "shaderlod-frame-import-v1",
        "source": str(source.resolve()),
        "sha256": hashlib.sha256(payload).hexdigest(),
        "bytes": len(payload),
        "evidence": "imported observations; provenance not independently qualified",
        "analysis": analysis,
    }
    publish(destination / "receipt.json", identity)
    return identity


def render_frames(frames: tuple[Frame, ...], analysis: dict[str, object]) -> str:
    origin = frames[0].timestamp_ns - frames[0].interval_ns
    points = [
        [(row.timestamp_ns - origin) / 1e9, row.interval_ns / 1e6, row.sequence]
        for row in frames
    ]
    data = json.dumps(points, allow_nan=False)
    summary = json.dumps(analysis, allow_nan=False).replace("<", "\\u003c")
    return """<!doctype html><html lang="en"><meta charset="utf-8"><title>ShaderLOD frame observations</title>
<style>body{font:16px system-ui;margin:2rem;background:rgb(18,23,32);color:rgb(231,237,245)}canvas{width:100%;height:380px;border:1px solid gray}input{width:35%}pre{white-space:pre-wrap}button,select{font:inherit}</style>
<h1>ShaderLOD frame observations</h1><p>Application frame production. Imported observations do not establish loaded-source provenance, GPU cost or display smoothness.</p>
<label>View <select id="metric"><option value="ms">Frame time (ms)</option><option value="fps">FPS in elapsed-time bins</option></select></label>
<p><label>Start <input id="start" type="range" min="0" max="1000" value="0"></label> <label>End <input id="end" type="range" min="0" max="1000" value="1000"></label></p>
<canvas id="plot" width="1600" height="380" aria-label="Performance timeline"></canvas><p id="detail"></p><details><summary>Structured analysis and limitations</summary><pre id="summary"></pre></details>
<script>
const points=POINTS, summary=SUMMARY, canvas=document.getElementById('plot'), context=canvas.getContext('2d');
const start=document.getElementById('start'),end=document.getElementById('end'),metric=document.getElementById('metric'),detail=document.getElementById('detail');
document.getElementById('summary').textContent=JSON.stringify(summary,null,2);
let visible=[];
function draw(){
const low=Math.min(+start.value,+end.value)/1000*points.at(-1)[0],high=Math.max(+start.value,+end.value)/1000*points.at(-1)[0];
visible=points.filter(p=>p[0]>=low&&p[0]<=high);
let values=visible.map(p=>[p[0],p[1],p[2]]);
if(metric.value==='fps'){
const bins=new Map();for(const p of points){const b=Math.max(0,Math.ceil(p[0])-1);bins.set(b,(bins.get(b)||0)+1);}
values=[...bins].filter(p=>p[0]>=low&&p[0]<=high).map(p=>[p[0],p[1]/Math.min(1,points.at(-1)[0]-p[0]),0]);
if(!summary.valid){values=[];}
}
context.clearRect(0,0,1600,380);const ymax=values.reduce((m,p)=>Math.max(m,p[1]),1), span=Math.max(.001,high-low);context.fillStyle='white';context.fillText(ymax.toFixed(2)+' '+metric.value,10,18);context.fillText(low.toFixed(2)+' s',10,375);context.fillText(high.toFixed(2)+' s',1480,375);
context.strokeStyle='rgb(107,210,255)';context.beginPath();let previous=null;for(const p of values){const x=40+(p[0]-low)/span*1520,y=350-p[1]/ymax*320;if(previous===null||(metric.value==='ms'&&p[2]!==previous+1)){context.moveTo(x,y);}else{context.lineTo(x,y);}previous=p[2];}context.stroke();
detail.textContent=visible.length+' frames; '+low.toFixed(2)+'–'+high.toFixed(2)+' s. Valid: '+summary.valid+'. Raw points retained; no smoothing.';
}
for(const input of [start,end,metric]){input.addEventListener('input',draw);}draw();
</script></html>""".replace("POINTS", data).replace("SUMMARY", summary)


def session_command(
    session: Path, action: str, duration_ms: int, timeout: float
) -> dict[str, object]:
    from shaderlod_host.runtime_live import executable_inputs
    from shaderlod_host.storage import BusyRefusal
    from shaderlod_host.worker import birth

    metadata = json.loads((session / "session.json").read_bytes())
    if (
        metadata.get("schema") != "shaderlod-session-v1"
        or metadata["host"] != socket.gethostname()
    ):
        raise ValueError("foreign or unsupported session")
    if (
        metadata["boot"] != Path("/proc/sys/kernel/random/boot_id").read_text().strip()
        or birth(metadata["owner_pid"]) != metadata["owner_birth"]
        or (session / "closed.json").exists()
    ):
        raise ValueError("session owner is not live")
    launch_path = Path(metadata["launch"])
    launch = json.loads(launch_path.read_bytes())
    root = Path(__file__).resolve().parents[1]
    if (
        executable_inputs(launch, root, Path(metadata["profile"]))
        != metadata["executable_inputs"]
    ):
        raise ValueError("live executable input drift; restart session")
    if (
        action not in {"status", "capture", "close"}
        or type(duration_ms) is not int
        or not 0 <= duration_ms <= 60000
        or not 0 < timeout <= 65
    ):
        raise ValueError("invalid bounded session command")
    with BusyRefusal(session / "ownership", "command"):
        commands = sorted(session.glob("command-*.json"))
        sequence = len(commands) + 1
        if (
            commands
            and not (session / ("result-" + str(sequence - 1) + ".json")).exists()
        ):
            raise ValueError("prior command outcome unknown; refusing replay")
        if (session / "failure.json").exists():
            raise ValueError("session has a preserved controller failure")
        publish(
            session / ("command-" + str(sequence) + ".json"),
            {
                "schema": "shaderlod-control-v1",
                "sequence": sequence,
                "action": action,
                "duration_ms": duration_ms,
            },
        )
        deadline = time.monotonic() + timeout
        result = session / ("result-" + str(sequence) + ".json")
        while not result.exists():
            if (session / "failure.json").exists() or (
                session / "closed.json"
            ).exists():
                raise ValueError("session failed or closed; inspect preserved evidence")
            if time.monotonic() >= deadline:
                raise ValueError(
                    "command timed out; reconcile outcome before another command"
                )
            time.sleep(0.02)
        observed = json.loads(result.read_bytes())
        if (
            not isinstance(observed, dict)
            or observed.get("schema") != "shaderlod-control-result-v1"
            or observed.get("sequence") != sequence
        ):
            raise ValueError("command acknowledgement identity mismatch")
        if (
            executable_inputs(launch, root, Path(metadata["profile"]))
            != metadata["executable_inputs"]
        ):
            raise ValueError(
                "executable input drift during command; raw result retained in session"
            )
        observed["session_identity"] = metadata
        return observed


def save_live_capture(
    observed: dict[str, object], destination: Path
) -> dict[str, object]:
    capture = observed.get("capture")
    if not isinstance(capture, dict) or not isinstance(capture.get("frames"), list):
        raise ValueError("missing capture records")
    frames = tuple(Frame.decode(row) for row in capture["frames"])
    analysis = analyze_frames(frames)
    analysis["benchmark_qualified"] = False
    if (
        observed.get("valid") is not True
        or capture.get("overflow") is not False
        or capture.get("generationChanged") is not False
        or observed.get("ready") is not True
    ):
        analysis["valid"] = False
        analysis["fps"] = None
    if destination.exists():
        raise ValueError("fresh output directory required")
    payload = b"".join(
        json.dumps(
            {
                "sequence": row.sequence,
                "timestamp_ns": row.timestamp_ns,
                "interval_ns": row.interval_ns,
                "generation": row.generation,
            }
        ).encode()
        + b"\n"
        for row in frames
    )
    destination.mkdir(parents=True)
    publish_bytes(destination / "frames.jsonl", payload)
    publish_bytes(destination / "report.html", render_frames(frames, analysis).encode())
    publish(destination / "summary.json", analysis)
    publish(destination / "observation.json", observed)
    publish(
        destination / "receipt.json",
        {
            "schema": "shaderlod-frame-capture-v1",
            "qualified": False,
            "files": {
                name: hashlib.sha256((destination / name).read_bytes()).hexdigest()
                for name in (
                    "frames.jsonl",
                    "summary.json",
                    "observation.json",
                    "report.html",
                )
            },
        },
    )
    return analysis


def verify_frame_receipt(path: Path) -> dict[str, object]:
    record = object_fields(
        json.loads(path.read_bytes()),
        {"schema", "qualified", "files"},
        "capture receipt",
    )
    if (
        record["schema"] != "shaderlod-frame-capture-v1"
        or record["qualified"] is not False
    ):
        raise ValueError("unsupported capture qualification or schema")
    files = object_fields(
        record["files"],
        {"frames.jsonl", "summary.json", "observation.json", "report.html"},
        "capture files",
    )
    for name, expected in files.items():
        asset = path.parent / name
        if (
            asset.is_symlink()
            or hashlib.sha256(asset.read_bytes()).hexdigest() != expected
        ):
            raise ValueError("capture digest disagreement: " + name)
    recomputed = analyze_frames(read_frames(path.parent / "frames.jsonl"))
    summary = json.loads((path.parent / "summary.json").read_bytes())
    for field in (
        "frames",
        "elapsed_ns",
        "missing_frames",
        "generations",
        "frame_ms_max",
        "frame_ms_p50",
        "frame_ms_p95",
    ):
        if summary.get(field) != recomputed[field]:
            raise ValueError("capture analysis disagreement: " + field)
    if summary.get("valid") is True and (
        recomputed["valid"] is not True or summary.get("fps") != recomputed["fps"]
    ):
        raise ValueError("invalid stream promoted by summary")
    return {
        "schema": "shaderlod-capture-verification-v1",
        "integrity": "PASS",
        "benchmark_qualified": False,
        "scope": "file integrity and frame reduction only",
    }

from dataclasses import dataclass
import hashlib
import html
import json
import math
import socket
import time
from pathlib import Path
from typing import Callable, Iterable

from shaderlod_host.storage import publish, publish_bytes


class CommandIncomplete(RuntimeError):
    pass


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
        if blocks > 100 or window > 300000 or budget > 86400000:
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
            "live_capability": "profile capture; comparison uses shaderlod-comparison-v1",
            "acceptance": "no retention policy supplied",
        }


@dataclass(frozen=True)
class Frame:
    sequence: int
    timestamp_ns: int
    interval_ns: int
    generation: int

    def __post_init__(self) -> None:
        for name, value in (
            ("sequence", self.sequence),
            ("timestamp_ns", self.timestamp_ns),
            ("interval_ns", self.interval_ns),
            ("generation", self.generation),
        ):
            if (
                type(value) is not int
                or not (0 if name == "generation" else 1)
                <= value
                <= 9223372036854775807
            ):
                raise ValueError(
                    "frame field is outside its signed 64-bit contract: " + name
                )

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


def half_window_drift(frames: tuple[Frame, ...]) -> dict[str, object]:
    origin = frames[0].timestamp_ns - frames[0].interval_ns
    midpoint = origin + (frames[-1].timestamp_ns - origin) / 2
    first = [row for row in frames if row.timestamp_ns <= midpoint]
    if not first or len(first) == len(frames):
        return {
            "relative_change": None,
            "reason": "fewer than one frame in each half of the window",
        }
    first_ns = first[-1].timestamp_ns - origin
    second_ns = frames[-1].timestamp_ns - first[-1].timestamp_ns
    first_fps = len(first) * 1000000000 / first_ns
    second_fps = (len(frames) - len(first)) * 1000000000 / second_ns
    return {
        "first_half_fps": first_fps,
        "second_half_fps": second_fps,
        "relative_change": second_fps / first_fps - 1,
    }


UNLIMITED_FRAME_RATE = 260


def frame_limit(
    observed: dict[str, object], summary: dict[str, object]
) -> dict[str, object]:
    options = observed.get("client_options")
    cap = options.get("max_fps") if isinstance(options, dict) else None
    vsync = options.get("vsync") if isinstance(options, dict) else None
    if type(cap) is not int or cap < 1 or type(vsync) is not bool:
        return {
            "state": "unobserved",
            "limit_fps": None,
            "reason": "frame limiter and vsync settings were not recorded",
        }
    limits = [cap] if cap < UNLIMITED_FRAME_RATE else []
    monitor = observed.get("monitor")
    refresh = monitor[2] if isinstance(monitor, list) and len(monitor) == 3 else None
    median = summary.get("frame_ms_p50")
    tail = summary.get("frame_ms_p95")
    if vsync:
        if type(refresh) is not int or refresh < 1:
            return {
                "state": "unobserved",
                "limit_fps": None,
                "reason": "vsync is on and the display refresh rate was not recorded",
            }
        limits.append(refresh)
    if not limits:
        if (
            type(refresh) is int
            and refresh > 0
            and isinstance(median, (int, float))
            and isinstance(tail, (int, float))
            and not isinstance(median, bool)
            and not isinstance(tail, bool)
            and abs(median - 1000 / refresh) <= 15 / refresh
            and tail <= median * 1.05
        ):
            return {
                "state": "limited",
                "limit_fps": refresh,
                "reason": "frame intervals match the display refresh although the game's limiter and vsync are off; frames are being capped outside the game",
            }
        return {
            "state": "unlimited",
            "limit_fps": None,
            "reason": "frame limiter unlimited and vsync off",
        }
    limit = min(limits)
    if (
        not isinstance(median, (int, float))
        or isinstance(median, bool)
        or not math.isfinite(median)
        or median <= 0
    ):
        return {
            "state": "unobserved",
            "limit_fps": limit,
            "reason": "median frame interval unavailable",
        }
    if median <= 1050 / limit:
        return {
            "state": "limited",
            "limit_fps": limit,
            "reason": "median frame interval sits at the configured frame limit; faster rendering cannot raise this throughput",
        }
    return {
        "state": "below_limit",
        "limit_fps": limit,
        "reason": "median frame interval is longer than the frame limit period, so the limiter does not bound most frames",
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
    template = (Path(__file__).with_name("templates") / "frames.html").read_text()
    fallback = html.escape(
        "Frames: "
        + str(analysis["frames"])
        + "; FPS: "
        + str(analysis.get("fps"))
        + "; valid: "
        + str(analysis["valid"])
        + ". Application frame production; qualification is separate."
    )
    return (
        template.replace("@@POINTS@@", data)
        .replace("@@SUMMARY@@", summary)
        .replace("@@FALLBACK@@", fallback)
    )


def settle_surface(
    call: Callable[..., dict[str, object]],
    surface: list[int],
    observed: dict[str, object],
    timeout: float,
) -> dict[str, object]:
    if observed.get("surface") == surface:
        return observed
    if observed.get("ready") is not True:
        raise ValueError(
            "session is not ready; wait for ready status before comparison"
        )
    call("prepare", payload={"surface": surface})
    deadline = time.monotonic() + timeout
    while True:
        observed = call("status")
        if observed.get("surface") == surface and observed.get("ready") is True:
            return observed
        if time.monotonic() >= deadline:
            raise ValueError("requested native surface did not take effect")
        time.sleep(0.2)


def session_command(
    session: Path,
    action: str,
    duration_ms: int,
    timeout: float,
    payload: dict[str, object] | None = None,
) -> dict[str, object]:
    from shaderlod_host.runtime_live import executable_inputs
    from shaderlod_host.storage import BusyRefusal
    from shaderlod_host.worker import birth, boot

    metadata = json.loads((session / "session.json").read_bytes())
    if (
        metadata.get("schema") != "shaderlod-session-v1"
        or metadata["host"] != socket.gethostname()
    ):
        raise ValueError("foreign or unsupported session")
    if (
        metadata["boot"] != boot()
        or birth(metadata["owner_pid"]) != metadata["owner_birth"]
        or (session / "closed.json").exists()
    ):
        raise ValueError("session owner is not live")
    child_path = session / "child.json"
    if "child_record" in metadata and not child_path.exists():
        raise ValueError(
            "client ownership publication pending; retry status without replaying commands"
        )
    if child_path.exists():
        child = json.loads(child_path.read_bytes())
        if (
            child.get("host") != metadata["host"]
            or child.get("boot") != metadata["boot"]
            or birth(child["pid"]) != child["birth"]
        ):
            raise ValueError("recorded client process is not live")
        metadata["child"] = child
    launch_path = Path(metadata["launch"])
    launch = json.loads(launch_path.read_bytes())
    root = Path(__file__).resolve().parents[1]
    if (
        executable_inputs(launch, root, Path(metadata["profile"]))
        != metadata["executable_inputs"]
    ):
        raise ValueError("live executable input drift; restart session")
    if (
        action
        not in {
            "status",
            "capture",
            "close",
            "select",
            "restore",
            "prepare",
            "screenshot",
            "recover",
            "validate",
        }
        or type(duration_ms) is not int
        or not 0 <= duration_ms <= 300000
        or not 0 < timeout <= 310
    ):
        raise ValueError("invalid bounded session command")
    with BusyRefusal(session / "ownership", "command"):
        commands = sorted(session.glob("command-*.json"))
        sequence = len(commands) + 1
        terminal_failure = None
        if (session / "failure.json").exists():
            terminal_failure = json.loads((session / "failure.json").read_bytes())
        for failure_path in session.glob("failure-*.json"):
            candidate = json.loads(failure_path.read_bytes())
            if (
                not isinstance(candidate, dict)
                or type(candidate.get("sequence")) is not int
            ):
                raise ValueError("malformed controller failure identity")
            if (
                terminal_failure is None
                or candidate["sequence"] > terminal_failure["sequence"]
            ):
                terminal_failure = candidate
        prior_result = session / ("result-" + str(sequence - 1) + ".json")
        if isinstance(terminal_failure, dict) and prior_result.exists():
            prior = json.loads(prior_result.read_bytes())
            if prior.get("recovered_failure_sequence") == terminal_failure.get(
                "sequence"
            ):
                terminal_failure = None
        recover_close = (
            action in {"close", "recover"}
            and isinstance(terminal_failure, dict)
            and terminal_failure.get("sequence") == sequence - 1
        )
        if (
            commands
            and not (session / ("result-" + str(sequence - 1) + ".json")).exists()
            and not recover_close
        ):
            raise ValueError("prior command outcome unknown; refusing replay")
        if terminal_failure is not None and not recover_close:
            raise ValueError("session has a preserved controller failure")
        publish(
            session / ("command-" + str(sequence) + ".json"),
            {
                "schema": "shaderlod-control-v2",
                "sequence": sequence,
                "action": action,
                "duration_ms": duration_ms,
                "payload": {} if payload is None else payload,
            },
        )
        deadline = time.monotonic() + timeout
        result = session / ("result-" + str(sequence) + ".json")
        while not result.exists():
            if (
                (session / ("failure-" + str(sequence) + ".json")).exists()
                and not recover_close
            ) or (session / "closed.json").exists():
                raise CommandIncomplete(
                    "session failed or closed; inspect preserved evidence"
                )
            if time.monotonic() >= deadline:
                raise CommandIncomplete(
                    "command timed out; reconcile outcome before another command"
                )
            time.sleep(0.02)
        observed = json.loads(result.read_bytes())
        if (
            not isinstance(observed, dict)
            or observed.get("schema") != "shaderlod-control-result-v1"
            or observed.get("sequence") != sequence
        ):
            raise CommandIncomplete("command acknowledgement identity mismatch")
        if action == "close":
            while not (session / "closed.json").exists():
                if time.monotonic() >= deadline:
                    raise CommandIncomplete(
                        "close acknowledged; process finalization still pending"
                    )
                time.sleep(0.02)
            closed = json.loads((session / "closed.json").read_bytes())
            if (
                closed.get("returncode") != 0
                or closed.get("inputs_unchanged") is not True
                or closed.get("profile_artifact_restored") is not True
                or closed.get("shader_recovery_pending") is not False
                or closed.get("world_recovery_pending") is not False
            ):
                raise CommandIncomplete(
                    "session closed with an execution or artifact-restoration failure"
                )
            observed["closed"] = closed
        elif (
            executable_inputs(launch, root, Path(metadata["profile"]))
            != metadata["executable_inputs"]
        ):
            raise CommandIncomplete(
                "executable input drift during command; raw result retained in session"
            )
        for name, path in {
            "loaded_main_class_sha256": root
            / "build/classes/java/main/com/pp/shaderlod/storage/AtomicFiles.class",
            "loaded_dev_class_sha256": root
            / "build/classes/java/devClient/com/pp/shaderlod/dev/BenchmarkControl.class",
        }.items():
            if observed.get(name) != hashlib.sha256(path.read_bytes()).hexdigest():
                raise CommandIncomplete(
                    "loaded class disagrees with declared build: " + name
                )
        observed["session_identity"] = metadata
        return observed


def stage_summary(value: object) -> list[dict[str, object]]:
    if not isinstance(value, dict):
        return []
    groups: dict[tuple[str, str], dict[str, list[float]]] = {}
    for stream, field in (("cpu", "wall_ns"), ("gpu", "gpu_elapsed_ns")):
        rows = value.get(stream)
        if not isinstance(rows, list):
            raise ValueError("invalid stage stream")
        for row in rows:
            if (
                not isinstance(row, dict)
                or not isinstance(row.get("from"), str)
                or not isinstance(row.get("to"), str)
            ):
                raise ValueError("invalid stage boundary")
            raw = row.get(field)
            if type(raw) is not int or raw < 0:
                raise ValueError("invalid stage clock")
            group = groups.setdefault((row["from"], row["to"]), {"cpu": [], "gpu": []})
            group[stream].append(raw / 1000000)
    return [
        {
            "from": key[0],
            "to": key[1],
            "cpu_samples": len(group["cpu"]),
            "cpu_mean_ms": sum(group["cpu"]) / len(group["cpu"])
            if group["cpu"]
            else None,
            "gpu_samples": len(group["gpu"]),
            "gpu_mean_ms": sum(group["gpu"]) / len(group["gpu"])
            if group["gpu"]
            else None,
        }
        for key, group in groups.items()
    ]


def pass_summary(value: object) -> dict[str, object]:
    if not isinstance(value, dict):
        raise ValueError("pass profile required")
    cpu, gpu = value.get("cpu"), value.get("gpu")
    if not isinstance(cpu, list) or not isinstance(gpu, list):
        raise ValueError("invalid pass stream")
    completed: dict[int, int] = {}
    for row in gpu:
        if (
            not isinstance(row, dict)
            or type(row.get("span_id")) is not int
            or type(row.get("gpu_elapsed_ns")) is not int
            or row["gpu_elapsed_ns"] < 0
        ):
            raise ValueError("invalid pass GPU interval")
        completed[row["span_id"]] = row["gpu_elapsed_ns"]
    frames: dict[int, list[tuple[str, int, int | None]]] = {}
    for row in cpu:
        if (
            not isinstance(row, dict)
            or not isinstance(row.get("from"), str)
            or type(row.get("submission")) is not int
            or type(row.get("span_id")) is not int
            or type(row.get("wall_ns")) is not int
            or row["wall_ns"] < 0
        ):
            raise ValueError("invalid pass CPU interval")
        frames.setdefault(row["submission"], []).append(
            (row["from"], row["wall_ns"], completed.get(row["span_id"]))
        )
    complete = [
        rows for rows in frames.values() if all(row[2] is not None for row in rows)
    ]
    totals: dict[str, dict[str, int]] = {}
    for rows in frames.values():
        for owner, wall, _ in rows:
            group = totals.setdefault(owner, {"spans": 0, "cpu": 0, "gpu": 0})
            group["spans"] += 1
            group["cpu"] += wall
    for rows in complete:
        for owner, _, elapsed in rows:
            totals[owner]["gpu"] += elapsed or 0
    gpu_total = sum(group["gpu"] for group in totals.values())
    groups: list[dict[str, object]] = [
        {
            "group": owner,
            "intervals_per_frame": group["spans"] / len(frames),
            "cpu_ms_per_frame": group["cpu"] / len(frames) / 1000000,
            "gpu_ms_per_frame": group["gpu"] / len(complete) / 1000000
            if complete
            else None,
            "gpu_share": group["gpu"] / gpu_total if gpu_total else None,
        }
        for owner, group in sorted(
            totals.items(), key=lambda item: (-item[1]["gpu"], -item[1]["cpu"], item[0])
        )
    ]
    return {
        "sampled_frames": len(frames),
        "complete_frames": len(complete),
        "gpu_ms_per_frame": gpu_total / len(complete) / 1000000 if complete else None,
        "groups": groups,
        "health": {
            key: item for key, item in value.items() if key not in {"cpu", "gpu"}
        },
        "meaning": "Time between consecutive Iris render-group events on sampled frames, owned by the innermost open group. GPU values use only frames whose every interval completed; they follow command-stream timestamps, include waits and are not exclusive occupancy",
    }


def save_live_capture(
    observed: dict[str, object], destination: Path
) -> dict[str, object]:
    capture = observed.get("capture")
    if not isinstance(capture, dict) or not isinstance(capture.get("frames"), list):
        raise ValueError("missing capture records")
    frames = tuple(Frame.decode(row) for row in capture["frames"])
    analysis = analyze_frames(frames)
    analysis["benchmark_qualified"] = False
    analysis["frame_limit"] = frame_limit(observed, analysis)
    analysis["half_window_drift"] = half_window_drift(frames)
    analysis["events"] = observed.get("checkpoints", [])
    if "stage_profile" in observed:
        analysis["stage_summary"] = stage_summary(observed["stage_profile"])
        from shaderlod_host.adapters import semantic_mapping

        mapping = semantic_mapping(
            observed, Path(__file__).resolve().parents[1] / "benchmarks/mappings"
        )
        analysis["mapping"] = mapping
        profile = observed["stage_profile"]
        if not isinstance(profile, dict):
            raise ValueError("stage profile required")
        analysis["stage_health"] = {
            key: value for key, value in profile.items() if key not in {"cpu", "gpu"}
        }
        gpu = {row["span_id"]: row for row in profile["gpu"]}
        origin = frames[0].timestamp_ns - frames[0].interval_ns
        samples = []
        for row in profile["cpu"]:
            if "started_ns" not in row:
                continue
            semantic = "mixed / unmapped"
            definition = mapping.get("definition")
            if isinstance(definition, dict):
                for label_record in definition["labels"]:
                    if row["from"].startswith(label_record["namespace"]) and row[
                        "to"
                    ].startswith(label_record["namespace"]):
                        semantic = label_record["label"]
            samples.append(
                {
                    "start_s": (row["started_ns"] - origin) / 1e9,
                    "from": row["from"],
                    "to": row["to"],
                    "cpu_ms": row["wall_ns"] / 1e6,
                    "gpu_ms": gpu[row["span_id"]]["gpu_elapsed_ns"] / 1e6
                    if row["span_id"] in gpu
                    else None,
                    "submission": row["submission"],
                    "label": semantic,
                }
            )
        analysis["stage_samples"] = samples
        events = analysis["events"]
        if not isinstance(events, list):
            raise ValueError("event list required")
        starts: dict[int, float] = {}
        for sample in samples:
            starts.setdefault(sample["submission"], sample["start_s"])
        analysis["events"] = events + [
            {
                "elapsed_ns": round(value * 1e9),
                "kind": "diagnostic-sample",
                "submission": submission,
            }
            for submission, value in starts.items()
        ]
        analysis["diagnostic_limits"] = (
            "Sampled stage intervals; missing GPU completions and shared shader effects prevent exclusive cost attribution"
        )
    if "pass_profile" in observed:
        analysis["pass_summary"] = pass_summary(observed["pass_profile"])
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
    retained = read_frames(path.parent / "frames.jsonl")
    recomputed = analyze_frames(retained)
    summary = json.loads((path.parent / "summary.json").read_bytes())
    observed = json.loads((path.parent / "observation.json").read_bytes())
    capture = observed.get("capture")
    if not isinstance(capture, dict) or not isinstance(capture.get("frames"), list):
        raise ValueError("missing original capture")
    if tuple(Frame.decode(row) for row in capture["frames"]) != read_frames(
        path.parent / "frames.jsonl"
    ):
        raise ValueError("capture/raw frame disagreement")
    if summary.get("valid") is True and (
        observed.get("valid") is not True
        or observed.get("ready") is not True
        or capture.get("overflow") is not False
        or capture.get("generationChanged") is not False
    ):
        raise ValueError("invalid observation promoted by summary")
    for field in (
        "frames",
        "elapsed_ns",
        "missing_frames",
        "generations",
        "bins",
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
    if "frame_limit" in summary and summary["frame_limit"] != frame_limit(
        observed, recomputed
    ):
        raise ValueError("capture analysis disagreement: frame_limit")
    if "half_window_drift" in summary and summary[
        "half_window_drift"
    ] != half_window_drift(retained):
        raise ValueError("capture analysis disagreement: half_window_drift")
    return {
        "schema": "shaderlod-capture-verification-v1",
        "integrity": "PASS",
        "benchmark_qualified": False,
        "scope": "file integrity and frame reduction only",
    }


def wait_ready(session: Path, timeout: float) -> dict[str, object]:
    prepared = False
    deadline = time.monotonic() + timeout
    while not (session / "session.json").exists():
        if time.monotonic() >= deadline or (session / "closed.json").exists():
            raise ValueError(
                "session startup did not publish ownership before deadline"
            )
        time.sleep(0.05)
    metadata = json.loads((session / "session.json").read_bytes())
    recover_needed = (
        Path(metadata["profile"]) / "shaderlod-benchmark-world.json"
    ).exists() and not any(session.glob("world-preimage-*.json"))
    while True:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise ValueError("readiness deadline exhausted")
        if "child_record" in metadata and not (session / "child.json").exists():
            if (session / "closed.json").exists():
                raise ValueError("session closed before child ownership publication")
            time.sleep(min(0.05, remaining))
            continue
        observed = session_command(session, "status", 0, min(65, remaining))
        if observed.get("world_loaded") is True and observed.get("fallback") is True:
            raise ValueError(
                "native Iris graph fell back; inspect preserved client.log instead of treating fallback as readiness"
            )
        if observed.get("world_loaded") is True and recover_needed:
            session_command(session, "recover", 0, min(65, remaining))
            recover_needed = False
            continue
        if observed.get("ready") is True:
            return observed
        if observed.get("world_loaded") is True and not prepared:
            session_command(
                session, "prepare", 0, min(65, max(0.1, deadline - time.monotonic()))
            )
            prepared = True
        time.sleep(min(0.2, max(0, deadline - time.monotonic())))


def build_identity(metadata: object) -> str | None:
    if not isinstance(metadata, dict):
        return None
    declared = metadata.get("build_sha256")
    if (
        isinstance(declared, str)
        and len(declared) == 64
        and all(c in "0123456789abcdef" for c in declared)
    ):
        return declared
    inputs = metadata.get("executable_inputs")
    if not isinstance(inputs, dict):
        return None
    matches = {
        value
        for path, value in inputs.items()
        if isinstance(path, str)
        and isinstance(value, str)
        and "/mods/shaderlod-" in path.replace("\\", "/").lower()
        and path.endswith(".jar")
    }
    return next(iter(matches)) if len(matches) == 1 else None


def collector_identity(metadata: object) -> dict[str, str]:
    if not isinstance(metadata, dict) or not isinstance(
        metadata.get("executable_inputs"), dict
    ):
        return {}
    result = {}
    for name, digest in metadata["executable_inputs"].items():
        normalized = str(name).replace("\\", "/")
        for marker in (
            "/build/classes/java/devClient/",
            "/build/classes/java/benchmarkCore/",
            "/build/resources/devClient/",
        ):
            if marker in normalized:
                result[marker + normalized.split(marker, 1)[1]] = str(digest)
    return result

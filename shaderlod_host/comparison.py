from dataclasses import dataclass
import hashlib
import json
import math
from pathlib import Path
import random
import time
from typing import Callable

from shaderlod_host.benchmark import (
    Frame,
    analyze_frames,
    object_fields,
    label,
    positive,
    session_command,
    save_live_capture,
)
from shaderlod_host.storage import publish, publish_bytes


def vector(value: object) -> tuple[float, ...]:
    if (
        not isinstance(value, list)
        or len(value) != 5
        or any(
            type(v) not in (int, float) or abs(v) > 1e12 or not math.isfinite(v)
            for v in value
        )
    ):
        raise ValueError("five finite pose coordinates required")
    result = tuple(float(v) for v in value)
    if (
        abs(result[0]) > 29000000
        or abs(result[2]) > 29000000
        or not -64 <= result[1] <= 1024
        or abs(result[4]) > 90
    ):
        raise ValueError("route outside admitted world bounds")
    return result


@dataclass(frozen=True)
class Case:
    identity: str
    pack: str
    sha256: str
    start: tuple[float, ...]
    end: tuple[float, ...]
    dimension: str
    surface: tuple[int, int]
    day_time: int = 6000
    weather: str = "clear"

    @classmethod
    def decode(cls, value: object) -> "Case":
        data = object_fields(
            value,
            {"id", "pack", "sha256", "from", "to", "dimension", "surface"}
            | (
                {"conditions"}
                if isinstance(value, dict) and "conditions" in value
                else set()
            ),
            "case",
        )
        pack = label(data["pack"], "pack")
        digest = label(data["sha256"], "sha256")
        if (
            "/" in pack
            or "\\" in pack
            or len(digest) != 64
            or any(c not in "0123456789abcdef" for c in digest)
        ):
            raise ValueError("regular pack filename and sha256 required")
        start, end = vector(data["from"]), vector(data["to"])
        if sum((end[i] - start[i]) ** 2 for i in range(3)) > 4096:
            raise ValueError("route exceeds 64 blocks")
        size = data["surface"]
        if not isinstance(size, list) or len(size) != 2:
            raise ValueError("native surface required")
        conditions = object_fields(
            data.get("conditions", {"day_time": 6000, "weather": "clear"}),
            {"day_time", "weather"},
            "conditions",
        )
        day = conditions["day_time"]
        if (
            type(day) is not int
            or not 0 <= day < 24000
            or not isinstance(conditions["weather"], str)
            or conditions["weather"] not in {"clear", "rain", "thunder"}
        ):
            raise ValueError("bounded fixed day time and supported weather required")
        return cls(
            label(data["id"], "case id"),
            pack,
            digest,
            start,
            end,
            label(data["dimension"], "dimension"),
            (positive(size[0], "width"), positive(size[1], "height")),
            day,
            str(conditions["weather"]),
        )


@dataclass(frozen=True)
class Arm:
    identity: str
    modules: tuple[str, ...]
    delay_ms: int

    @classmethod
    def decode(cls, value: object) -> "Arm":
        data = object_fields(value, {"id", "modules", "delay_ms"}, "arm")
        modules = data["modules"]
        delay = data["delay_ms"]
        if (
            not isinstance(modules, list)
            or type(delay) is not int
            or not 0 <= delay <= 100
        ):
            raise ValueError("module list and bounded explicit test delay required")
        ids = tuple(label(m, "module") for m in modules)
        if len(set(ids)) != len(ids):
            raise ValueError("duplicate modules")
        return cls(label(data["id"], "arm id"), ids, delay)


@dataclass(frozen=True)
class ComparisonPlan:
    scope: str
    cases: tuple[Case, ...]
    arms: tuple[Arm, ...]
    blocks: int
    window_ms: int
    warmup_ms: int
    budget_ms: int
    seed: int
    instrument: str = "frames"
    quality_cadence_ms: int = 0
    interaction: tuple[str, str, str, str] | None = None

    @classmethod
    def decode(cls, value: object) -> "ComparisonPlan":
        data = object_fields(
            value,
            {
                "schema",
                "scope",
                "cases",
                "arms",
                "blocks",
                "window_ms",
                "warmup_ms",
                "budget_ms",
                "seed",
            }
            | (
                {"instrument"}
                if isinstance(value, dict) and "instrument" in value
                else set()
            )
            | (
                {"interaction"}
                if isinstance(value, dict) and "interaction" in value
                else set()
            )
            | (
                {"quality_cadence_ms"}
                if isinstance(value, dict) and "quality_cadence_ms" in value
                else set()
            ),
            "comparison",
        )
        if (
            data["schema"] != "shaderlod-comparison-v1"
            or not isinstance(data["scope"], str)
            or data["scope"]
            not in {
                "development",
                "contribution",
                "release",
            }
        ):
            raise ValueError("unsupported comparison scope/schema")
        if (
            not isinstance(data["cases"], list)
            or not 1 <= len(data["cases"]) <= 64
            or not isinstance(data["arms"], list)
            or not 2 <= len(data["arms"]) <= 16
        ):
            raise ValueError("bounded cases and two or more arms required")
        cases = tuple(Case.decode(v) for v in data["cases"])
        arms = tuple(Arm.decode(v) for v in data["arms"])
        if len({c.identity for c in cases}) != len(cases) or len(
            {a.identity for a in arms}
        ) != len(arms):
            raise ValueError("duplicate case/arm identity")
        blocks = positive(data["blocks"], "blocks")
        window = positive(data["window_ms"], "window_ms")
        budget = positive(data["budget_ms"], "budget_ms")
        warmup, seed = data["warmup_ms"], data["seed"]
        if (
            blocks > 100
            or not 100
            <= window
            <= (60000 if data["scope"] == "development" else 300000)
            or budget > 86400000
            or type(warmup) is not int
            or not 0 <= warmup <= 60000
            or type(seed) is not int
            or seed < 0
        ):
            raise ValueError("invalid comparison limits or seed")
        if len(cases) * len(arms) * blocks * (window + warmup) >= budget:
            raise ValueError("budget needs lifecycle headroom")
        instrument = data.get("instrument", "frames")
        cadence = data.get("quality_cadence_ms", 0)
        if (
            not isinstance(instrument, str)
            or instrument not in {"frames", "stages", "memory-control"}
            or type(cadence) is not int
            or (cadence != 0 and not 100 <= cadence <= 1000)
        ):
            raise ValueError(
                "supported instrument and bounded quality cadence required"
            )
        if cadence and (window > 10000 or instrument != "frames"):
            raise ValueError("quality captures are separate and at most 10 seconds")
        if data["scope"] != "development" and (cadence or instrument != "frames"):
            raise ValueError(
                "qualification timings require minimal frame instrumentation"
            )
        interaction = None
        if "interaction" in data:
            members = data["interaction"]
            if (
                not isinstance(members, list)
                or len(members) != 4
                or not all(isinstance(item, str) for item in members)
                or len(set(members)) != 4
            ):
                raise ValueError(
                    "interaction needs four distinct original/incumbent/standalone/combined arm IDs"
                )
            by_id = {arm.identity: arm for arm in arms}
            if not set(members) <= by_id.keys():
                raise ValueError("interaction arms missing")
            original, incumbent, standalone, combined = (
                by_id[item] for item in members
            )
            if original.modules or set(combined.modules) != set(
                incumbent.modules
            ) | set(standalone.modules):
                raise ValueError(
                    "interaction module union differs from declared four cells"
                )
            interaction = (members[0], members[1], members[2], members[3])
        return cls(
            str(data["scope"]),
            cases,
            arms,
            blocks,
            window,
            warmup,
            budget,
            seed,
            str(instrument),
            cadence,
            interaction,
        )

    def schedule(self) -> tuple[tuple[int, Case, Arm], ...]:
        rng = random.Random(self.seed)
        case_order = list(self.cases)
        rng.shuffle(case_order)
        schedule: list[tuple[int, Case, Arm]] = []
        for case in case_order:
            arm_order = list(self.arms)
            rng.shuffle(arm_order)
            for block in range(self.blocks):
                ordered = arm_order if block % 2 == 0 else list(reversed(arm_order))
                schedule.extend((block, case, arm) for arm in ordered)
        return tuple(schedule)

    def wire(self) -> dict[str, object]:
        record: dict[str, object] = {
            "schema": "shaderlod-comparison-v1",
            "scope": self.scope,
            "cases": [
                {
                    "id": case.identity,
                    "pack": case.pack,
                    "sha256": case.sha256,
                    "from": list(case.start),
                    "to": list(case.end),
                    "dimension": case.dimension,
                    "surface": list(case.surface),
                    "conditions": {"day_time": case.day_time, "weather": case.weather},
                }
                for case in self.cases
            ],
            "arms": [
                {
                    "id": arm.identity,
                    "modules": list(arm.modules),
                    "delay_ms": arm.delay_ms,
                }
                for arm in self.arms
            ],
            "blocks": self.blocks,
            "window_ms": self.window_ms,
            "warmup_ms": self.warmup_ms,
            "budget_ms": self.budget_ms,
            "seed": self.seed,
            "instrument": self.instrument,
            "quality_cadence_ms": self.quality_cadence_ms,
        }

        if self.interaction is not None:
            record["interaction"] = list(self.interaction)
        return record

    def describe(self) -> dict[str, object]:
        return {
            "schema": "shaderlod-comparison-plan-v1",
            "scope": self.scope,
            "capture_ms": len(self.cases)
            * len(self.arms)
            * self.blocks
            * self.window_ms,
            "minimum_ms": len(self.cases)
            * len(self.arms)
            * self.blocks
            * (self.window_ms + self.warmup_ms),
            "budget_ms": self.budget_ms,
            "schedule": [
                {"block": block, "case": case.identity, "arm": arm.identity}
                for block, case, arm in self.schedule()
            ],
            "claim": "single-session exploratory comparison until independent qualification and acceptance policy supplied",
        }


def workload_failure(
    case: Case, observed: dict[str, object], window_ms: int
) -> str | None:
    if observed.get("progression_clock") != "client-simulation-ticks-20hz":
        return "unverified route progression clock"
    capture = observed.get("capture")
    if not isinstance(capture, dict) or not isinstance(capture.get("frames"), list):
        return "missing actual frame window"
    analysis = analyze_frames(tuple(Frame.decode(row) for row in capture["frames"]))
    measured_ms = float(str(analysis["elapsed_ns"])) / 1000000
    interval_limit = max(100, 2 * float(str(analysis["frame_ms_max"])))
    if abs(measured_ms - window_ms) > interval_limit:
        return "captured interval does not cover the requested window"
    ticks = observed.get("elapsed_client_ticks")
    expected = window_ms / 50
    if type(ticks) is not int or abs(ticks - expected) > max(2, expected * 0.02):
        return "simulation progression differs from declared workload"
    views = observed.get("view_checkpoints")
    if not isinstance(views, list) or not views:
        return "missing actual rendered-camera checkpoints"
    previous = 0
    gap_limit = max(500, interval_limit) * 1000000
    for view in views:
        if not isinstance(view, dict) or type(view.get("client_ticks")) is not int:
            return "malformed rendered-camera checkpoint"
        timestamp = view.get("elapsed_ns")
        if (
            type(timestamp) is not int
            or timestamp < previous
            or timestamp - previous > gap_limit
        ):
            return "incomplete rendered-camera checkpoint coverage"
        previous = timestamp
        pose = vector(view.get("player_pose"))
        camera = vector(view.get("camera_pose"))
        progress = min(1, max(0, view["client_ticks"] / expected))
        target = [
            case.start[i] + progress * (case.end[i] - case.start[i]) for i in range(5)
        ]
        if any(abs(pose[i] - target[i]) > (0.25 if i < 3 else 0.05) for i in range(5)):
            return "actual player pose differs from scheduled route"
        eye = view.get("eye_height")
        if (
            not isinstance(eye, (int, float))
            or isinstance(eye, bool)
            or view.get("first_person") is not True
        ):
            return "unsupported camera mode or eye state"
        camera_target = [pose[0], pose[1] + eye, pose[2]]
        for i in range(3):
            tolerance = abs(case.end[i] - case.start[i]) / expected + 0.25
            if abs(camera[i] - camera_target[i]) > tolerance:
                return "rendered camera differs from admitted route"
    if window_ms * 1000000 - previous > gap_limit:
        return "rendered-camera checkpoints ended before workload"
    return None


def execute_comparison(
    plan: ComparisonPlan,
    session: Path,
    output: Path,
    command: Callable[..., dict[str, object]] = session_command,
    policy: dict[str, object] | None = None,
) -> dict[str, object]:
    if output.exists():
        raise ValueError("fresh comparison output required")
    output.mkdir(parents=True)
    publish(
        output / "plan.json", {"definition": plan.wire(), "resolved": plan.describe()}
    )
    policy_identity = None
    if policy is not None:
        from shaderlod_host.assessment import QualificationPolicy

        declared = QualificationPolicy.decode(policy)
        if plan.scope == "development":
            raise ValueError(
                "development screening cannot become confirmation by adding a policy"
            )
        policy_identity = declared.identity
        publish(output / "policy.json", declared.wire())
    started = time.monotonic()
    deadline = started + plan.budget_ms / 1000
    execution_deadline = deadline - min(30, plan.budget_ms / 3000)
    results: list[dict[str, object]] = []
    failure = None
    attempted: list[dict[str, object]] = []
    restored = False
    inputs: dict[str, str] = {}
    baselines: dict[str, dict[str, object]] = {}

    def call(
        action: str, duration: int = 0, payload: dict[str, object] | None = None
    ) -> dict[str, object]:
        remaining = execution_deadline - time.monotonic()
        if remaining <= 0:
            raise ValueError("comparison budget exhausted")
        return command(
            session,
            action,
            duration,
            min(310 if action == "capture" else 65, remaining),
            payload,
        )

    try:
        initial = call("status")
        metadata = initial.get("session_identity")
        if isinstance(metadata, dict):
            profile = Path(str(metadata["profile"]))
            for case in plan.cases:
                source = profile / "shaderpacks" / case.pack
                if source.is_symlink():
                    raise ValueError("original archive cannot be a symlink")
                payload = source.read_bytes()
                if (
                    source.is_symlink()
                    or hashlib.sha256(payload).hexdigest() != case.sha256
                ):
                    raise ValueError("original archive preflight identity disagreement")
                name = "inputs/" + case.sha256 + ".zip"
                if name not in inputs:
                    publish_bytes(output / name, payload)
                    inputs[name] = case.sha256
        if initial.get("ready") is not True and initial.get("world_loaded") is not True:
            raise ValueError(
                "session is not ready; wait for ready status before comparison"
            )
        for index, (block, case, arm) in enumerate(plan.schedule()):
            case_dir = output / ("attempt-" + str(index + 1))
            attempt = {
                "case": case.identity,
                "arm": arm.identity,
                "block": block,
                "directory": str(case_dir.resolve()),
                "started_seconds": time.monotonic() - started,
            }
            attempted.append(attempt)
            publish(output / ("attempt-" + str(index + 1) + ".started.json"), attempt)
            call(
                "prepare",
                payload={
                    "day_time": case.day_time,
                    "weather": case.weather,
                    "pose": list(case.start),
                },
            )
            if case.identity not in baselines:
                native = initial
                initial_activation = initial.get("activation")
                if (
                    not isinstance(initial_activation, dict)
                    or initial_activation.get("archiveSha256") != case.sha256
                    or initial.get("selected_modules", []) != []
                ):
                    native = call(
                        "select",
                        payload={
                            "pack": case.pack,
                            "sha256": case.sha256,
                            "modules": [],
                        },
                    )
                baselines[case.identity] = native
            selection_started = time.monotonic()
            selected = call(
                "select",
                payload={
                    "pack": case.pack,
                    "sha256": case.sha256,
                    "modules": list(arm.modules),
                },
            )
            selection_seconds = time.monotonic() - selection_started
            if configuration(selected) != configuration(baselines[case.identity]):
                raise ValueError(
                    "candidate changed native options, surface, runtime, resources or DH configuration"
                )
            activation = selected.get("activation")
            if (
                not isinstance(activation, dict)
                or activation.get("linked") is not True
                or not any(
                    str(w).startswith("submitted-frame:")
                    for w in activation.get("witnesses", [])
                )
            ):
                raise ValueError("missing actual submitted activation")
            if (
                selected.get("surface") != list(case.surface)
                or selected.get("dimension") != case.dimension
                or selected.get("ready") is not True
            ):
                raise ValueError("surface, dimension or readiness differs from case")
            conditions_started = time.monotonic()
            conditions_state = selected
            while not conditions_match(case, conditions_state):
                if time.monotonic() - conditions_started >= 10:
                    raise ValueError(
                        "declared weather/time did not settle within fixed allowance"
                    )
                time.sleep(0.05)
                conditions_state = call("status")
            conditions_seconds = time.monotonic() - conditions_started
            if plan.warmup_ms:
                if execution_deadline - time.monotonic() <= plan.warmup_ms / 1000:
                    raise ValueError("insufficient remaining warmup budget")
                time.sleep(plan.warmup_ms / 1000)
            conditioned = call("status")
            pose = vector(conditioned.get("pose"))
            if any(
                abs(pose[i] - case.start[i]) > (0.25 if i < 3 else 0.05)
                for i in range(5)
            ):
                raise ValueError(
                    "prepared player drifted during fixed conditioning; use an appropriate prepared scene"
                )
            capture_payload: dict[str, object] = {
                "from": list(case.start),
                "to": list(case.end),
                "delay_ms": arm.delay_ms,
            }
            if plan.instrument == "stages":
                capture_payload["diagnostic"] = True
            if plan.instrument == "memory-control":
                capture_payload["durable_chunks"] = False
            quality = None
            if plan.quality_cadence_ms:
                capture_payload["quality_cadence_ms"] = plan.quality_cadence_ms
            window_started = time.monotonic() - started
            observed = call(
                "capture",
                plan.window_ms,
                capture_payload,
            )
            summary = save_live_capture(observed, case_dir)
            if plan.quality_cadence_ms:
                from shaderlod_host.quality import save_sequence

                quality = save_sequence(
                    observed, session, case_dir, plan.quality_cadence_ms, plan.window_ms
                )
            if arm.delay_ms:
                elapsed_delay = observed.get("executed_delay_ns")
                if (
                    type(elapsed_delay) is not int
                    or elapsed_delay
                    < positive(summary["frames"], "frames") * arm.delay_ms * 1000000
                ):
                    raise ValueError(
                        "known slowdown did not execute its declared delay"
                    )
            route_failure = workload_failure(case, observed, plan.window_ms)
            if isinstance(quality, dict) and quality.get("sequence_valid") is not True:
                route_failure = (
                    route_failure
                    or "quality cadence, delivery or camera alignment incomplete"
                )
            captured_activation = observed.get("activation")
            activation_matches = (
                isinstance(captured_activation, dict)
                and all(
                    captured_activation.get(key) == activation.get(key)
                    for key in ("archiveSha256", "generation", "packName")
                )
                and summary.get("generations") == [selected.get("generation")]
            )
            row: dict[str, object] = {
                "workload_valid": route_failure is None,
                "workload_failure": route_failure,
                "case": case.identity,
                "arm": arm.identity,
                "block": block,
                "session": str(session.resolve()),
                "attempt": case_dir.name,
                "summary": summary,
                "selection": selected,
                "delay_ms": arm.delay_ms,
                "instrument": plan.instrument,
                "quality_only": plan.quality_cadence_ms > 0,
                "selection_seconds": selection_seconds,
                "conditions_seconds": conditions_seconds,
                "conditioning": conditioned,
                "window_started_seconds": window_started,
                "window_ended_seconds": time.monotonic() - started,
            }
            results.append(row)
            publish(case_dir / "comparison.json", row)
            if (
                route_failure is not None
                or summary["valid"] is not True
                or not activation_matches
                or selected.get("selected_modules", []) != list(arm.modules)
                or observed.get("selected_modules", []) != list(arm.modules)
                or observed.get("surface") != list(case.surface)
                or observed.get("dimension") != case.dimension
                or not conditions_match(case, observed)
                or observed.get("options") != selected.get("options")
                or configuration(observed) != configuration(selected)
            ):
                raise ValueError("invalid measured window or configuration drift")
    except (OSError, ValueError, RuntimeError) as error:
        failure = str(error)
    finally:
        try:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise ValueError(
                    "no remaining teardown budget; recovery journal retained"
                )
            command(session, "restore", 0, min(60, remaining), None)
            restored = True
        except (OSError, ValueError, RuntimeError) as error:
            failure = (failure or "") + "; restoration failed: " + str(error)
    result: dict[str, object] = {
        "schema": "shaderlod-comparison-result-v1",
        "scope": plan.scope,
        "started_unix_ns": time.time_ns()
        - int((time.monotonic() - started) * 1000000000),
        "protocol_sha256": hashlib.sha256(
            json.dumps(plan.wire(), sort_keys=True).encode()
        ).hexdigest(),
        "state": "complete" if failure is None else "incomplete",
        "elapsed_seconds": time.monotonic() - started,
        "restored": restored,
        "failure": failure,
        "policy_sha256": policy_identity,
        "input_archives": inputs,
        "baselines": baselines,
        "observations": results,
        "attempts": attempted,
        "reliability": {
            "attempted": len(attempted),
            "captured": len(results),
            "required": len(plan.schedule()),
        },
        "interactions": interaction_effects(results, plan.interaction)
        if plan.interaction
        else [],
        "qualification": "unqualified",
        "retention": "unresolved: independent-session/coverage/quality and acceptance policy required",
    }
    publish(output / "result.json", result)
    publish_bytes(output / "report.html", render_comparison(result).encode())
    publish(
        output / "receipt.json",
        {
            "schema": "shaderlod-comparison-receipt-v1",
            "sha256": hashlib.sha256((output / "result.json").read_bytes()).hexdigest(),
            "qualified": False,
        },
    )
    return result


def render_comparison(result: dict[str, object]) -> str:
    data = json.dumps(result, allow_nan=False).replace("<", "\\u003c")
    template = (Path(__file__).with_name("templates") / "comparison.html").read_text()
    return template.replace("@@DATA@@", data)


def interaction_effects(
    rows: list[dict[str, object]], arms: tuple[str, str, str, str]
) -> list[dict[str, object]]:
    grouped: dict[tuple[str, str], dict[str, float]] = {}
    for row in rows:
        summary = row.get("summary")
        if (
            not isinstance(summary, dict)
            or row.get("workload_valid") is not True
            or summary.get("valid") is not True
        ):
            continue
        fps = summary.get("fps")
        if not isinstance(fps, (int, float)) or fps <= 0:
            continue
        grouped.setdefault((str(row["case"]), str(row["block"])), {})[
            str(row["arm"])
        ] = 1000 / fps
    output: list[dict[str, object]] = []
    for (case, block), times in grouped.items():
        if not set(arms) <= times.keys():
            output.append(
                {"case": case, "block": block, "state": "missing four-cell evidence"}
            )
            continue
        original, incumbent, standalone, combined = (times[arm] for arm in arms)
        output.append(
            {
                "case": case,
                "block": block,
                "state": "observed",
                "interaction_ms": (combined - incumbent) - (standalone - original),
                "mean_frame_ms": times,
                "meaning": "Positive means less-than-additive time reduction; descriptive block effect, no independence or higher-order guarantee",
            }
        )
    return output


def conditions_match(case: Case, observed: dict[str, object]) -> bool:
    rain = observed.get("rain")
    thunder = observed.get("thunder", 0)
    if (
        not isinstance(rain, (int, float))
        or not isinstance(thunder, (int, float))
        or observed.get("world_day_time") != case.day_time
    ):
        return False
    return (
        rain <= 0.01 and thunder <= 0.01
        if case.weather == "clear"
        else rain >= 0.99
        and (thunder >= 0.99 if case.weather == "thunder" else thunder <= 0.01)
    )


def verify_comparison(path: Path) -> dict[str, object]:
    from shaderlod_host.benchmark import verify_frame_receipt

    result = json.loads(path.read_bytes())
    receipt = json.loads((path.parent / "receipt.json").read_bytes())
    if (
        not isinstance(result, dict)
        or result.get("schema") != "shaderlod-comparison-result-v1"
        or receipt.get("sha256") != hashlib.sha256(path.read_bytes()).hexdigest()
    ):
        raise ValueError("comparison receipt/schema disagreement")
    archives = result.get("input_archives", {})
    if not isinstance(archives, dict):
        raise ValueError("input archive map required")
    for name, expected in archives.items():
        asset = path.parent / name
        if (
            name != "inputs/" + str(expected) + ".zip"
            or asset.is_symlink()
            or not asset.resolve().is_relative_to(path.parent.resolve())
            or hashlib.sha256(asset.read_bytes()).hexdigest() != expected
        ):
            raise ValueError("preserved original archive identity disagreement")
    definition = json.loads((path.parent / "plan.json").read_bytes())["definition"]
    plan = ComparisonPlan.decode(definition)
    if (
        result.get("protocol_sha256")
        != hashlib.sha256(json.dumps(definition, sort_keys=True).encode()).hexdigest()
    ):
        raise ValueError("frozen comparison protocol disagreement")
    rows = result.get("observations")
    schedule = plan.schedule()
    if (
        not isinstance(rows, list)
        or len(rows) > len(schedule)
        or (result.get("state") == "complete" and len(rows) != len(schedule))
    ):
        raise ValueError("comparison coverage differs from frozen schedule")
    for row, (block, case, arm) in zip(rows, schedule):
        if not isinstance(row, dict) or (
            row.get("case"),
            row.get("arm"),
            row.get("block"),
        ) != (case.identity, arm.identity, block):
            raise ValueError("observation differs from scheduled arm/case/block")
        if row.get("instrument", "frames") != plan.instrument or row.get(
            "quality_only", False
        ) != bool(plan.quality_cadence_ms):
            raise ValueError("instrumentation differs from frozen comparison")
        directory = path.parent / str(row.get("attempt"))
        verify_frame_receipt(directory / "receipt.json")
        summary = json.loads((directory / "summary.json").read_bytes())
        observed = json.loads((directory / "observation.json").read_bytes())
        if row.get("summary") != summary:
            raise ValueError("comparison summary disagrees with captured evidence")
        if plan.quality_cadence_ms:
            from shaderlod_host.quality import verify_sequence

            quality = verify_sequence(
                directory / "quality.json",
                observed,
                plan.quality_cadence_ms,
                plan.window_ms,
            )
            if (
                row.get("workload_valid") is True
                and quality.get("sequence_valid") is not True
            ):
                raise ValueError("invalid quality sequence promoted by comparison")
        if (
            row.get("workload_valid") is True
            and workload_failure(case, observed, plan.window_ms) is not None
        ):
            raise ValueError("invalid route promoted by comparison")
        if result.get("state") != "complete":
            continue
        selected = row.get("selection")
        if (
            not isinstance(selected, dict)
            or selected.get("surface") != list(case.surface)
            or selected.get("dimension") != case.dimension
            or not conditions_match(case, observed)
        ):
            raise ValueError("comparison environment differs from declared case")
        baseline = result.get("baselines", {}).get(case.identity)
        if isinstance(baseline, dict) and configuration(selected) != configuration(
            baseline
        ):
            raise ValueError("candidate differs from native configuration baseline")
        if configuration(observed) != configuration(selected):
            raise ValueError("configuration drift during captured window")
        activation = selected.get("activation")
        actual = observed.get("activation")
        if (
            not isinstance(activation, dict)
            or not isinstance(actual, dict)
            or activation.get("linked") is not True
            or any(
                actual.get(key) != activation.get(key)
                for key in ("archiveSha256", "generation", "packName")
            )
            or summary.get("generations") != [selected.get("generation")]
        ):
            raise ValueError("comparison activation/generation join failed")
        digest = activation.get("archiveSha256")
        witnesses = activation.get("witnesses")
        if (
            not isinstance(witnesses, list)
            or not all(isinstance(item, str) for item in witnesses)
            or set(witnesses)
            != {"linked-archive:" + str(digest), "submitted-frame:" + str(digest)}
        ):
            raise ValueError("actual submitted activation witnesses missing")
        if not arm.modules and digest != case.sha256:
            raise ValueError("original archive differs from case")
        if selected.get("selected_modules", []) != list(arm.modules) or observed.get(
            "selected_modules", []
        ) != list(arm.modules):
            raise ValueError("requested modules were not actually prepared")
    return result


def configuration(observed: dict[str, object]) -> dict[str, object]:
    dh = observed.get("dh")
    if (
        isinstance(dh, dict)
        and dh.get("present") is True
        and type(dh.get("enabled")) is not bool
    ):
        raise ValueError("DH configured state is unknown")
    return {
        "surface": observed.get("surface"),
        "dimension": observed.get("dimension"),
        "options": observed.get("options"),
        "client_options": observed.get("client_options"),
        "resource_packs": observed.get("resource_packs"),
        "dh": {key: dh.get(key) for key in ("present", "version", "enabled")}
        if isinstance(dh, dict)
        else None,
        "runtime": observed.get("runtime"),
        "renderer": observed.get("renderer"),
    }


def installation_plan(plan: ComparisonPlan) -> dict[str, object]:
    if (
        len(plan.cases) != 1
        or tuple(arm.identity for arm in plan.arms) != ("native", "installed-Off")
        or any(arm.modules or arm.delay_ms for arm in plan.arms)
        or plan.instrument != "frames"
        or plan.quality_cadence_ms
        or plan.window_ms > 60000
    ):
        raise ValueError(
            "installation needs one case, native/installed-Off empty arms and frame-only windows up to 60 seconds"
        )
    return plan.describe() | {
        "fresh_processes": 2 * plan.blocks,
        "mode": "native / installed-Off",
        "startup_and_close_allowance_seconds_per_process": 135,
    }


def verify_installation(path: Path) -> dict[str, object]:
    from shaderlod_host.benchmark import verify_frame_receipt

    result = json.loads(path.read_bytes())
    receipt = json.loads((path.parent / "receipt.json").read_bytes())
    if (
        not isinstance(result, dict)
        or result.get("schema") != "shaderlod-installation-result-v1"
        or receipt.get("sha256") != hashlib.sha256(path.read_bytes()).hexdigest()
    ):
        raise ValueError("installation result/receipt disagreement")
    plan = ComparisonPlan.decode(
        json.loads((path.parent / "plan.json").read_bytes())["definition"]
    )
    installation_plan(plan)
    rows = result.get("observations")
    if not isinstance(rows, list) or (
        result.get("state") == "complete" and len(rows) != 2 * plan.blocks
    ):
        raise ValueError("installation process coverage incomplete")
    for row in rows:
        directory = path.parent / row["capture"]
        verify_frame_receipt(directory / "receipt.json")
        observed = json.loads((directory / "observation.json").read_bytes())
        if (
            row["summary"] != json.loads((directory / "summary.json").read_bytes())
            or (observed["runtime_mods"].get("shaderlod") == "absent")
            != (row["mode"] == "native")
            or workload_failure(plan.cases[0], observed, plan.window_ms) is not None
        ):
            raise ValueError(
                "installation evidence does not match declared mode/workload"
            )
    return result

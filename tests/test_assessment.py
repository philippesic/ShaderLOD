import hashlib
import json
from pathlib import Path

import pytest

from shaderlod_host.assessment import QualificationPolicy, assess, median_interval
from shaderlod_host.benchmark import save_live_capture
from shaderlod_host.comparison import ComparisonPlan


def policy_spec(sessions: int = 6) -> dict[str, object]:
    return {
        "schema": "shaderlod-qualification-policy-v1",
        "reference": "original",
        "candidate": "identity",
        "confidence": 0.95,
        "minimum_gain": 0.1,
        "maximum_harm": 0.05,
        "cases": ["coast-pan"],
        "benefit_cases": ["coast-pan"],
        "sessions": sessions,
        "quality_criteria": "Synthetic fixture only; no runtime acceptance",
    }


def run_fixture(
    root: Path,
    policy: QualificationPolicy,
    jvm: int,
    multiplier: float = 1.2,
    client_options: dict[str, object] | None = None,
    blocks: int = 2,
) -> Path:
    if client_options is None:
        client_options = {"max_fps": 260, "vsync": False}
    spec = json.loads(
        (
            Path(__file__).resolve().parents[1]
            / "benchmarks/comparisons/asahi-null.json"
        ).read_bytes()
    )
    plan = ComparisonPlan.decode(
        spec | {"scope": "contribution", "blocks": blocks, "budget_ms": 200000}
    )
    root.mkdir()
    (root / "plan.json").write_text(json.dumps({"definition": plan.wire()}))
    rows = []
    arms = (policy.reference, policy.candidate)
    schedule = [
        (block, arm)
        for block in range(blocks)
        for arm in (arms if block % 2 == 0 else arms[::-1])
    ]
    for attempt, (block, arm) in enumerate(schedule):
        i = arms.index(arm)
        rate = 60 * (multiplier if i else 1)
        interval = round(1000000000 / rate)
        count = round(rate * 10)
        case = plan.cases[0]
        archive = case.sha256 if i == 0 else "d" * 64
        selected = {
            "session_identity": {
                "host": "fixture",
                "boot": "fixture-boot",
                "world": "test-world",
                "build_sha256": "e" * 64,
                "child": {"pid": jvm, "birth": str(jvm)},
            },
            "surface": [2560, 1600],
            "dimension": "minecraft:overworld",
            "renderer": {"fixture": True},
            "options": {},
            "generation": i + 1,
            "world_day_time": 6000,
            "rain": 0,
            "selected_modules": list(plan.arms[i].modules),
            "activation": {
                "linked": True,
                "generation": i + 1,
                "archiveSha256": archive,
                "packName": case.pack if i == 0 else "identity.zip",
                "witnesses": [
                    "linked-archive:" + archive,
                    "submitted-frame:" + archive,
                ],
            },
            "client_options": client_options,
            "loaded_main_class_sha256": "a" * 64,
            "loaded_dev_class_sha256": "b" * 64,
        }
        observation = selected | {
            "ready": True,
            "valid": True,
            "progression_clock": "client-simulation-ticks-20hz",
            "elapsed_client_ticks": 200,
            "view_checkpoints": [
                {
                    "elapsed_ns": j * 250000000,
                    "client_ticks": j * 5,
                    "player_pose": [
                        case.start[k] + j / 40 * (case.end[k] - case.start[k])
                        for k in range(5)
                    ],
                    "camera_pose": [
                        case.start[0],
                        case.start[1] + 1.62,
                        case.start[2],
                        0,
                        0,
                    ],
                    "eye_height": 1.62,
                    "first_person": True,
                }
                for j in range(41)
            ],
            "capture": {
                "overflow": False,
                "generationChanged": False,
                "frames": [
                    {
                        "sequence": n,
                        "timestamp_ns": 1000000000 + n * interval,
                        "interval_ns": interval,
                        "generation": i + 1,
                    }
                    for n in range(1, count + 1)
                ],
            },
        }
        directory = root / ("attempt-" + str(attempt + 1))
        summary = save_live_capture(observation, directory)
        rows.append(
            {
                "case": "coast-pan",
                "arm": arm,
                "block": block,
                "attempt": directory.name,
                "selection": selected,
                "summary": summary,
                "workload_valid": True,
                "instrument": "frames",
                "quality_only": False,
            }
        )
    result = {
        "schema": "shaderlod-comparison-result-v1",
        "protocol_sha256": hashlib.sha256(
            json.dumps(plan.wire(), sort_keys=True).encode()
        ).hexdigest(),
        "policy_sha256": policy.identity,
        "state": "complete",
        "restored": True,
        "observations": rows,
    }
    path = root / "result.json"
    path.write_text(json.dumps(result))
    (root / "receipt.json").write_text(
        json.dumps({"sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    )
    return path


def review(policy: QualificationPolicy, runs: list[Path]) -> dict[str, object]:
    return {
        "schema": "shaderlod-quality-review-v1",
        "reviewer": "CPU-fixture",
        "date": "2026-11-11",
        "policy_sha256": policy.identity,
        "results_sha256": [hashlib.sha256(p.read_bytes()).hexdigest() for p in runs],
        "cases": list(policy.cases),
        "approved": True,
        "limitations": "No runtime qualification",
    }


def test_small_samples_remain_inconclusive() -> None:
    assert median_interval([0.2, 0.2, 0.2], 0.95)["low"] is None
    interval = median_interval([0.2] * 6, 0.95)
    assert interval["low"] == 0.2
    assert interval["achieved_coverage"] == 0.96875


def test_acceptance_needs_independent_sessions_and_quality(tmp_path: Path) -> None:
    policy = QualificationPolicy.decode(policy_spec())
    runs = [run_fixture(tmp_path / str(i), policy, i) for i in range(6)]
    assert assess(policy, runs)["recommendation"] == "do_not_promote"
    assert (
        assess(policy, runs, review(policy, runs))["recommendation"]
        == "meets_declared_policy"
    )
    reused = [
        run_fixture(tmp_path / ("reused-" + str(i)), policy, 77) for i in range(6)
    ]
    result = assess(policy, reused, review(policy, reused))
    assert result["recommendation"] == "do_not_promote"
    assert result["problems"]


def test_unbalanced_arm_order_cannot_pass(tmp_path: Path) -> None:
    policy = QualificationPolicy.decode(policy_spec())
    for blocks in (1, 3):
        runs = [
            run_fixture(
                tmp_path / (str(blocks) + "-" + str(i)), policy, i, blocks=blocks
            )
            for i in range(6)
        ]
        result = assess(policy, runs, review(policy, runs))
        assert result["recommendation"] == "do_not_promote"
        problems = result["problems"]
        assert isinstance(problems, list)
        assert any("unbalanced" in str(problem) for problem in problems)


def test_frame_limited_or_unobserved_throughput_cannot_pass(tmp_path: Path) -> None:
    policy = QualificationPolicy.decode(policy_spec())
    capped = [
        run_fixture(
            tmp_path / ("capped-" + str(i)),
            policy,
            i,
            client_options={"max_fps": 60, "vsync": False},
        )
        for i in range(6)
    ]
    result = assess(policy, capped, review(policy, capped))
    assert result["recommendation"] == "do_not_promote"
    problems = result["problems"]
    assert isinstance(problems, list)
    assert any("frame-limited" in problem for problem in problems)
    unobserved = [
        run_fixture(tmp_path / ("unobserved-" + str(i)), policy, i, client_options={})
        for i in range(6)
    ]
    result = assess(policy, unobserved, review(policy, unobserved))
    assert result["recommendation"] == "do_not_promote"
    problems = result["problems"]
    assert isinstance(problems, list)
    assert any("unobserved" in problem for problem in problems)


def test_regression_and_missing_coverage_cannot_pass(tmp_path: Path) -> None:
    policy = QualificationPolicy.decode(policy_spec())
    runs = [run_fixture(tmp_path / str(i), policy, i, 0.8) for i in range(6)]
    result = assess(policy, runs, review(policy, runs))
    assert result["recommendation"] == "do_not_promote"
    cases = result["cases"]
    assert isinstance(cases, list)
    assert cases[0]["state"] == "fails_margin"
    with pytest.raises(ValueError, match="digest|receipt"):
        damaged = json.loads(runs[0].read_bytes())
        damaged["state"] = "incomplete"
        runs[0].write_text(json.dumps(damaged))
        assess(policy, runs)


def test_malformed_policy_and_review_refuse(tmp_path: Path) -> None:
    invalid: tuple[dict[str, object], ...] = (
        {"sessions": True},
        {"confidence": float("nan")},
        {"benefit_cases": ["unknown"]},
        {"cases": [{}]},
    )
    for changes in invalid:
        with pytest.raises(ValueError):
            QualificationPolicy.decode(policy_spec() | changes)
    policy = QualificationPolicy.decode(policy_spec())
    with pytest.raises(ValueError, match="string identities"):
        assess(policy, [], review(policy, []) | {"cases": [{}]})

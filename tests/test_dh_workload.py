import json
from pathlib import Path
import time

import pytest

from shaderlod_host.benchmark import Frame, dh_submission
from shaderlod_host.comparison import (
    ComparisonPlan,
    Stability,
    settle_workload,
    steady,
    workload_interval,
)
from shaderlod_host.storage import prepare_dh_dataset, restore_dh_dataset


def test_frozen_dh_dataset_is_restored_before_each_session(tmp_path: Path) -> None:
    world = tmp_path / "saves/Smoke"
    (world / "data").mkdir(parents=True)
    (world / "DIM-1/data").mkdir(parents=True)
    (world / "data/DistantHorizons.sqlite").write_bytes(b"overworld")
    (world / "DIM-1/data/DistantHorizons.sqlite").write_bytes(b"nether")
    (world / "level.dat").write_bytes(b"level")
    prepared = prepare_dh_dataset(tmp_path, "Smoke")
    files = prepared["files"]
    assert isinstance(files, dict)
    assert sorted(files) == [
        "DIM-1/data/DistantHorizons.sqlite",
        "data/DistantHorizons.sqlite",
    ]
    with pytest.raises(ValueError, match="already prepared"):
        prepare_dh_dataset(tmp_path, "Smoke")
    assert restore_dh_dataset(tmp_path, "Smoke")["restored_files"] == []
    (world / "data/DistantHorizons.sqlite").write_bytes(b"grown")
    (world / "data/DistantHorizons.sqlite-wal").write_bytes(b"journal")
    restored = restore_dh_dataset(tmp_path, "Smoke")
    assert restored["state"] == "prepared"
    assert restored["identity"] == prepared["identity"]
    assert restored["restored_files"] == [
        "data/DistantHorizons.sqlite",
        "data/DistantHorizons.sqlite-wal",
    ]
    assert (world / "data/DistantHorizons.sqlite").read_bytes() == b"overworld"
    assert not (world / "data/DistantHorizons.sqlite-wal").exists()
    assert (world / "level.dat").read_bytes() == b"level"
    (tmp_path / "shaderlod-dh-dataset/Smoke/data/DistantHorizons.sqlite").write_bytes(
        b"damaged"
    )
    (world / "data/DistantHorizons.sqlite").write_bytes(b"grown")
    with pytest.raises(ValueError, match="damaged"):
        restore_dh_dataset(tmp_path, "Smoke")
    assert restore_dh_dataset(tmp_path, "Other") == {"state": "unprepared"}


def test_dh_submission_counts_per_frame_or_reports_unobserved() -> None:
    frames = tuple(Frame(n, n * 10, 10, 1) for n in range(1, 5))
    counted = dh_submission({"dh_buffer_renders": 10}, frames)
    assert (counted["state"], counted["per_frame"]) == ("counted", 2.5)
    assert dh_submission({"dh_buffer_renders": -1}, frames)["state"] == "unobserved"
    assert dh_submission({}, frames)["state"] == "unobserved"


def stability() -> dict[str, object]:
    return {
        "conditioning_ms": 0,
        "interval_ms": 250,
        "intervals": 3,
        "fps_tolerance": 0.02,
        "dh_tolerance": 0.02,
        "timeout_ms": 1000,
    }


def test_stability_rule_is_bounded_and_counts_against_the_budget() -> None:
    spec = json.loads(
        (
            Path(__file__).resolve().parents[1]
            / "benchmarks/comparisons/asahi-null.json"
        ).read_bytes()
    )
    plan = ComparisonPlan.decode(spec | {"stability": stability()})
    assert plan.wire()["stability"] == stability()
    for change in (
        {"intervals": 1},
        {"fps_tolerance": 0},
        {"timeout_ms": "5"},
        {"extra": 1},
    ):
        with pytest.raises(ValueError):
            Stability.decode(stability() | change)
    with pytest.raises(ValueError, match="headroom"):
        ComparisonPlan.decode(
            spec | {"stability": stability() | {"timeout_ms": 600000}}
        )


def test_steady_needs_every_interval_within_tolerance() -> None:
    def row(fps: float, dh: float | None = None) -> dict[str, object]:
        values: dict[str, object] = {"fps": fps}
        if dh is not None:
            values["dh_per_frame"] = dh
        return values

    assert steady([row(100, 50), row(101, 50), row(100.5, 50.4)], 0.02, 0.02)
    assert not steady([row(100, 50), row(105, 50), row(100, 50)], 0.02, 0.02)
    assert not steady([row(100, 50), row(100, 55), row(100, 50)], 0.02, 0.02)
    assert steady([row(100), row(100.5), row(100.2)], 0.02, 0.02)
    assert not steady([row(100, 50), row(100), row(100, 50)], 0.02, 0.02)
    status: dict[str, object] = {
        "monotonic_ns": 0,
        "rendered_frames": 0,
        "dh_buffer_renders": 0,
    }
    later: dict[str, object] = {
        "monotonic_ns": 1_000_000_000,
        "rendered_frames": 120,
        "dh_buffer_renders": 600,
    }
    assert workload_interval(status, later) == {"fps": 120.0, "dh_per_frame": 5.0}
    assert workload_interval(status, status) is None
    assert workload_interval(status, later | {"dh_buffer_renders": -1}) == {
        "fps": 120.0
    }


def test_settle_workload_screens_cases_then_waits_for_stability(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    spec = json.loads(
        (
            Path(__file__).resolve().parents[1]
            / "benchmarks/comparisons/asahi-null.json"
        ).read_bytes()
    )
    plan = ComparisonPlan.decode(spec | {"stability": stability()})
    clock = {"ns": 0, "frames": 0, "dh": 0}
    actions: list[str] = []

    def call(action: str, payload: object = None) -> dict[str, object]:
        actions.append(action)
        if action == "screenshot":
            return {"image": "quality-1.png", "image_sha256": "a" * 64}
        if action == "status":
            clock["ns"] += 250_000_000
            clock["frames"] += 30
            clock["dh"] += 150
            return {
                "monotonic_ns": clock["ns"],
                "rendered_frames": clock["frames"],
                "dh_buffer_renders": clock["dh"],
            }
        return {}

    record = settle_workload(call, plan, tmp_path)
    assert record["state"] == "stable"
    assert actions[:3] == ["prepare", "screenshot", "prepare"]
    conditioned = ComparisonPlan.decode(
        spec | {"stability": stability() | {"conditioning_ms": 45000}}
    )
    actions.clear()
    sleeps: list[float] = []
    now = [0.0]

    def fake_sleep(seconds: float) -> None:
        sleeps.append(seconds)
        now[0] += seconds

    monkeypatch.setattr(time, "sleep", fake_sleep)
    monkeypatch.setattr(time, "monotonic", lambda: now[0])
    assert settle_workload(call, conditioned, tmp_path / "second")["state"] == "stable"
    assert max(sleeps) <= 20.0
    assert actions.count("status") >= 3
    assert (tmp_path / "readiness.json").exists()


def test_dh_runs_need_prepared_data_readiness_and_counted_windows() -> None:
    from shaderlod_host.assessment import dh_problems, dh_workload

    selection = {"dh": {"present": True}, "dh_settings": {"chunk_render_distance": 256}}
    counted = {"dh_submission": {"state": "counted"}}
    rows: list[object] = [{"selection": selection, "summary": counted}] * 2
    good: dict[str, object] = {
        "dh_dataset": {"state": "prepared", "identity": "d1", "config_sha256": "c1"},
        "readiness": {"state": "stable"},
    }
    assert dh_problems(good, rows) == []
    assert dh_workload(good, rows) == {
        "settings": {"chunk_render_distance": 256},
        "dataset": "d1",
        "config_sha256": "c1",
    }
    bad: dict[str, object] = {
        "dh_dataset": {"state": "unprepared"},
        "readiness": {"state": "timeout"},
    }
    unobserved: list[object] = [
        {"selection": selection, "summary": {"dh_submission": {"state": "unobserved"}}}
    ]
    assert len(dh_problems(bad, unobserved)) == 3
    absent: list[object] = [{"selection": {"dh": {"present": False}}}]
    assert dh_workload(good, absent) is None

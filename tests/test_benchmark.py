import json
from pathlib import Path

import pytest

from shaderlod_host.benchmark import (
    settle_surface,
    Frame,
    WorkflowPlan,
    analyze_frames,
    import_capture,
    read_frames,
)


def test_plan_cost_and_qualification_instrument() -> None:
    plan = {
        "schema": "shaderlod-workflow-v1",
        "scope": "development",
        "workload": "fixed-coast",
        "arms": ["original", "candidate"],
        "blocks": 1,
        "window_ms": 10000,
        "budget_ms": 60000,
        "instrument": "frames",
    }
    assert WorkflowPlan.decode(plan).describe()["capture_ms"] == 20000
    for changes in (
        {"blocks": True},
        {"budget_ms": 20000},
        {"arms": ["same", "same"]},
        {"scope": "release", "instrument": "diagnostic"},
        {"unknown": 3},
    ):
        with pytest.raises(ValueError):
            WorkflowPlan.decode(plan | changes)


def test_throughput_uses_elapsed_time_and_retains_spike() -> None:
    rows = [Frame(1, 1010000000, 10000000, 2), Frame(2, 1100000000, 90000000, 2)]
    summary = analyze_frames(rows)
    assert summary["fps"] == 20
    assert summary["frame_ms_max"] == 90
    assert summary["valid"] is True


def test_gap_and_generation_change_cannot_claim_throughput() -> None:
    for rows in (
        [Frame(1, 100, 10, 2), Frame(3, 120, 10, 2)],
        [Frame(1, 100, 10, 2), Frame(2, 110, 10, 3)],
    ):
        summary = analyze_frames(rows)
        assert summary["valid"] is False
        assert summary["fps"] is None


def test_corrupt_clock_order_and_types_refuse() -> None:
    for rows in (
        [],
        [Frame(1, 100, 10, 1), Frame(2, 105, 10, 1)],
        [Frame(2, 100, 10, 1), Frame(1, 110, 10, 1)],
    ):
        with pytest.raises(ValueError):
            analyze_frames(rows)
    with pytest.raises(ValueError):
        Frame.decode(
            {"sequence": True, "timestamp_ns": 10, "interval_ns": 2, "generation": 1}
        )


def test_truncated_stream_and_overwrite_refuse(tmp_path: Path) -> None:
    source = tmp_path / "frames.jsonl"
    record = json.dumps(
        {"sequence": 1, "timestamp_ns": 100, "interval_ns": 10, "generation": 2}
    )
    source.write_text(record)
    with pytest.raises(ValueError):
        read_frames(source)
    source.write_text(record + "\n")
    destination = tmp_path / "result"
    assert (
        import_capture(source, destination)["evidence"]
        == "imported observations; provenance not independently qualified"
    )
    with pytest.raises(ValueError):
        import_capture(source, destination)


def test_cli_plan_and_execution_refusal(tmp_path: Path) -> None:
    import subprocess
    import sys

    spec = Path(__file__).resolve().parents[1] / "benchmarks/profiles/frame.json"
    result = subprocess.run(
        [
            sys.executable,
            "-m",
            "shaderlod_host",
            "profile",
            "--spec",
            str(spec),
            "--plan",
        ],
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0
    assert json.loads(result.stdout)["capture_ms"] == 20000
    refused = subprocess.run(
        [sys.executable, "-m", "shaderlod_host", "profile", "--spec", str(spec)],
        capture_output=True,
        text=True,
    )
    assert refused.returncode == 2
    assert json.loads(refused.stdout)["status"] == "refused"


def test_live_capture_has_receipt_and_overflow_cannot_pass(tmp_path: Path) -> None:
    import hashlib

    from shaderlod_host.benchmark import save_live_capture

    observation = {
        "ready": True,
        "valid": True,
        "capture": {
            "overflow": True,
            "generationChanged": False,
            "frames": [
                {"sequence": 1, "timestamp_ns": 100, "interval_ns": 10, "generation": 2}
            ],
        },
    }
    target = tmp_path / "capture"
    summary = save_live_capture(observation, target)
    assert summary["valid"] is False
    assert summary["fps"] is None
    receipt = json.loads((target / "receipt.json").read_bytes())
    assert receipt["qualified"] is False
    assert all(
        hashlib.sha256((target / name).read_bytes()).hexdigest() == digest
        for name, digest in receipt["files"].items()
    )
    assert 'type="range"' in (target / "report.html").read_text()


def test_uncertain_command_is_not_replayed(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    import os
    import socket

    from shaderlod_host.benchmark import CommandIncomplete, session_command
    from shaderlod_host.worker import birth

    metadata = {
        "schema": "shaderlod-session-v1",
        "host": socket.gethostname(),
        "boot": Path("/proc/sys/kernel/random/boot_id").read_text().strip(),
        "owner_pid": os.getpid(),
        "owner_birth": birth(os.getpid()),
        "launch": str(tmp_path / "launch.json"),
        "profile": str(tmp_path),
        "executable_inputs": {},
    }
    (tmp_path / "session.json").write_text(json.dumps(metadata))
    (tmp_path / "launch.json").write_text("{}")
    monkeypatch.setattr(
        "shaderlod_host.runtime_live.executable_inputs", lambda *args: {}
    )
    with pytest.raises(CommandIncomplete, match="timed out"):
        session_command(tmp_path, "status", 0, 0.01)
    with pytest.raises(ValueError, match="outcome unknown"):
        session_command(tmp_path, "status", 0, 0.01)
    assert len(list(tmp_path.glob("command-*.json"))) == 1


def test_receipt_tamper_and_analysis_independence(tmp_path: Path) -> None:
    from shaderlod_host.benchmark import save_live_capture, verify_frame_receipt

    observation = {
        "ready": True,
        "valid": True,
        "capture": {
            "overflow": False,
            "generationChanged": False,
            "frames": [
                {"sequence": 1, "timestamp_ns": 100, "interval_ns": 10, "generation": 2}
            ],
        },
    }
    target = tmp_path / "capture"
    save_live_capture(observation, target)
    receipt = target / "receipt.json"
    assert verify_frame_receipt(receipt)["benchmark_qualified"] is False
    (target / "frames.jsonl").write_text("corrupt")
    with pytest.raises(ValueError, match="digest"):
        verify_frame_receipt(receipt)


def test_managed_profile_artifact_restores_and_preserves_foreign_edit(
    tmp_path: Path,
) -> None:
    from shaderlod_host.storage import (
        prepare_profile_artifact,
        restore_profile_artifact,
    )

    profile = tmp_path / "profile"
    (profile / "mods").mkdir(parents=True)
    target = profile / "mods/shaderlod.jar"
    target.write_bytes(b"original")
    shipping = tmp_path / "shaderlod.jar"
    shipping.write_bytes(b"new-build")
    session = tmp_path / "session"
    session.mkdir()
    record = prepare_profile_artifact(profile, shipping, session)
    assert target.read_bytes() == b"new-build"
    target.write_bytes(b"foreign-edit")
    with pytest.raises(ValueError, match="changed"):
        restore_profile_artifact(profile, record)
    assert target.read_bytes() == b"foreign-edit"
    target.write_bytes(b"new-build")
    restore_profile_artifact(profile, record)
    assert target.read_bytes() == b"original"
    assert (session / "profile-artifact-preimage.jar").read_bytes() == b"original"


def test_collector_identity_tracks_helpers_separately_from_product() -> None:
    from shaderlod_host.benchmark import collector_identity

    current = {
        "executable_inputs": {
            "C:\\repo\\build\\classes\\java\\devClient\\FrameCapture.class": "a",
            "C:\\repo\\build\\classes\\java\\main\\Product.class": "b",
        }
    }
    identity = collector_identity(current)
    assert identity == {"/build/classes/java/devClient/FrameCapture.class": "a"}
    current["executable_inputs"][
        "C:\\repo\\build\\classes\\java\\main\\Product.class"
    ] = "changed-product"
    assert collector_identity(current) == identity
    current["executable_inputs"][
        "C:\\repo\\build\\classes\\java\\devClient\\FrameCapture.class"
    ] = "changed-instrument"
    assert collector_identity(current) != identity


def test_frame_limit_distinguishes_capped_headroom_and_unobserved_state() -> None:
    from shaderlod_host.benchmark import frame_limit

    capped: dict[str, object] = {"client_options": {"max_fps": 60, "vsync": False}}
    assert frame_limit(capped, {"frame_ms_p50": 16.71})["state"] == "limited"
    below = frame_limit(capped, {"frame_ms_p50": 55.0})
    assert below["state"] == "below_limit"
    assert below["limit_fps"] == 60
    unlimited: dict[str, object] = {"client_options": {"max_fps": 260, "vsync": False}}
    assert frame_limit(unlimited, {"frame_ms_p50": 3.2})["state"] == "unlimited"
    paced: dict[str, object] = unlimited | {"monitor": [3840, 2160, 60]}
    external = frame_limit(paced, {"frame_ms_p50": 16.69, "frame_ms_p95": 16.9})
    assert (external["state"], external["limit_fps"]) == ("limited", 60)
    assert "outside the game" in str(external["reason"])
    free_running: tuple[dict[str, object], ...] = (
        {"frame_ms_p50": 16.69, "frame_ms_p95": 19.0},
        {"frame_ms_p50": 15.9, "frame_ms_p95": 16.0},
        {"frame_ms_p50": 47.3, "frame_ms_p95": 49.0},
    )
    for free in free_running:
        assert frame_limit(paced, free)["state"] == "unlimited"
    synced: dict[str, object] = {
        "client_options": {"max_fps": 260, "vsync": True},
        "monitor": [1024, 768, 60],
    }
    assert frame_limit(synced, {"frame_ms_p50": 16.68})["state"] == "limited"
    assert frame_limit(synced, {"frame_ms_p50": 30.0})["limit_fps"] == 60
    unobserved: tuple[tuple[dict[str, object], dict[str, object]], ...] = (
        ({}, {"frame_ms_p50": 16.7}),
        ({"client_options": {"max_fps": True, "vsync": False}}, {"frame_ms_p50": 9.0}),
        ({"client_options": {"max_fps": 260, "vsync": True}}, {"frame_ms_p50": 9.0}),
        (capped, {"frame_ms_p50": None}),
    )
    for observed, summary in unobserved:
        assert frame_limit(observed, summary)["state"] == "unobserved"


def test_settle_surface_switches_once_and_refuses_unready_or_stuck_sessions() -> None:
    calls: list[tuple[str, object]] = []
    states = iter(
        [
            {"ready": True, "surface": [2560, 1440]},
            {"ready": False, "surface": [3840, 2160]},
            {"ready": True, "surface": [3840, 2160]},
        ]
    )

    def call(action: str, payload: object = None) -> dict[str, object]:
        calls.append((action, payload))
        return next(states) if action == "status" else {}

    matching = {"ready": True, "surface": [3840, 2160]}
    assert settle_surface(call, [3840, 2160], matching, 1) is matching
    assert calls == []
    assert settle_surface(
        call, [3840, 2160], {"ready": True, "surface": [2560, 1440]}, 5
    ) == {
        "ready": True,
        "surface": [3840, 2160],
    }
    assert calls[0] == ("prepare", {"surface": [3840, 2160]})
    with pytest.raises(ValueError, match="not ready"):
        settle_surface(call, [3840, 2160], {"ready": False, "surface": [2560, 1440]}, 1)
    with pytest.raises(ValueError, match="did not take effect"):
        settle_surface(
            lambda action, payload=None: {"ready": True, "surface": [2560, 1440]},
            [3840, 2160],
            {"ready": True, "surface": [2560, 1440]},
            0,
        )


def test_half_window_drift_reports_settling_and_refuses_single_frames() -> None:
    from shaderlod_host.benchmark import Frame, half_window_drift

    def stream(intervals: list[int]) -> tuple[Frame, ...]:
        clock = 1000
        rows = []
        for index, interval in enumerate(intervals, 1):
            clock += interval
            rows.append(Frame(index, clock, interval, 1))
        return tuple(rows)

    steady = half_window_drift(stream([50] * 40))
    assert steady["relative_change"] == 0
    settling = half_window_drift(stream([40] * 25 + [50] * 20))
    assert settling["first_half_fps"] == 25000000
    assert settling["second_half_fps"] == 20000000
    assert settling["relative_change"] == pytest.approx(-0.2)
    assert half_window_drift(stream([50]))["relative_change"] is None


def test_pass_summary_attributes_owned_intervals_and_excludes_incomplete_gpu_frames() -> (
    None
):
    from shaderlod_host.benchmark import pass_summary

    def span(identity: int, frame: int, owner: str, wall: int) -> dict[str, object]:
        return {
            "span_id": identity,
            "submission": frame,
            "generation": 1,
            "from": owner,
            "to": "next",
            "started_ns": identity,
            "wall_ns": wall,
        }

    cpu = [
        span(1, 10, "ungrouped", 1000000),
        span(2, 10, "composite/composite1", 2000000),
        span(3, 10, "composite/composite1", 1000000),
        span(4, 20, "ungrouped", 1000000),
        span(5, 20, "composite/composite1", 3000000),
    ]
    gpu = [
        cpu[0] | {"gpu_elapsed_ns": 2000000},
        cpu[1] | {"gpu_elapsed_ns": 5000000},
        cpu[2] | {"gpu_elapsed_ns": 1000000},
        cpu[3] | {"gpu_elapsed_ns": 9000000},
    ]
    summary = pass_summary({"cpu": cpu, "gpu": gpu, "overflow": False})
    assert (summary["sampled_frames"], summary["complete_frames"]) == (2, 1)
    assert summary["gpu_ms_per_frame"] == 8
    groups = summary["groups"]
    assert isinstance(groups, list)
    assert groups[0] == {
        "group": "composite/composite1",
        "intervals_per_frame": 1.5,
        "cpu_ms_per_frame": 3,
        "gpu_ms_per_frame": 6,
        "gpu_share": 0.75,
    }
    assert groups[1]["gpu_ms_per_frame"] == 2
    assert summary["health"] == {"overflow": False}
    empty = pass_summary({"cpu": cpu[:1], "gpu": []})
    assert empty["complete_frames"] == 0
    assert empty["gpu_ms_per_frame"] is None
    invalid_streams: tuple[object, ...] = (
        None,
        {"cpu": [], "gpu": None},
        {"cpu": [{"from": 1}], "gpu": []},
        {"cpu": [], "gpu": [{"span_id": 1, "gpu_elapsed_ns": -1}]},
    )
    for invalid in invalid_streams:
        with pytest.raises(ValueError):
            pass_summary(invalid)


def test_gpu_contamination_separates_clean_foreign_and_unobserved() -> None:
    from shaderlod_host.telemetry import gpu_contamination

    def sample(at: int, processes: object) -> dict[str, object]:
        return {"unix_ns": at, "gpu": {"compute_processes": processes}}

    clean = [sample(t, {"pids": []}) for t in (100, 200, 300)]
    assert gpu_contamination(clean, 100, 300)["state"] == "clean"
    foreign = clean + [sample(250, {"pids": [4242]}), sample(900, {"pids": [7]})]
    verdict = gpu_contamination(foreign, 100, 300)
    assert (verdict["state"], verdict["foreign_pids"]) == ("foreign", [4242])
    assert gpu_contamination(clean, 400, 500)["state"] == "unobserved"
    unknown = clean + [sample(150, {"unavailable": 3})]
    assert gpu_contamination(unknown, 100, 300)["state"] == "unobserved"
    assert gpu_contamination([{"unix_ns": 150, "gpu": {}}], 100, 300)["state"] == (
        "unobserved"
    )

import json
from pathlib import Path

import pytest

from shaderlod_host.benchmark import (
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

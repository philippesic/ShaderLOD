import hashlib
import json
from pathlib import Path

import pytest

from shaderlod_host.benchmark import save_live_capture
from shaderlod_host.reports import rebuild
from test_assessment import run_fixture, policy_spec
from shaderlod_host.assessment import QualificationPolicy


def test_report_rebuild_preserves_source_and_refuses_corruption(tmp_path: Path) -> None:
    source = tmp_path / "source"
    save_live_capture(
        {
            "valid": True,
            "ready": True,
            "capture": {
                "overflow": False,
                "generationChanged": False,
                "frames": [
                    {
                        "sequence": 1,
                        "timestamp_ns": 3000000000,
                        "interval_ns": 3000000000,
                        "generation": 1,
                    }
                ],
            },
        },
        source,
    )
    before = {p.name: p.read_bytes() for p in source.iterdir()}
    output = tmp_path / "render"
    manifest = rebuild(source / "receipt.json", output)
    assert (
        manifest["report_sha256"]
        == hashlib.sha256((output / "report.html").read_bytes()).hexdigest()
    )
    assert before == {p.name: p.read_bytes() for p in source.iterdir()}
    assert json.loads((output / "summary.json").read_bytes())["frames"] == 1
    with pytest.raises(ValueError, match="fresh"):
        rebuild(source / "receipt.json", output)
    (source / "frames.jsonl").write_bytes(b"truncated")
    with pytest.raises(ValueError, match="digest"):
        rebuild(source / "receipt.json", tmp_path / "bad")


def test_comparison_derivative_links_original_assets(tmp_path: Path) -> None:
    source = run_fixture(
        tmp_path / "source", QualificationPolicy.decode(policy_spec(2)), 1, 1.1
    )
    output = tmp_path / "render"
    rebuild(source, output)
    assert (source.parent / "attempt-1").resolve().as_uri() in (
        output / "report.html"
    ).read_text()
    assert source.read_bytes() == (output / "result.json").read_bytes()


def test_validation_plan_is_read_only_and_bad_inputs_refuse(tmp_path: Path) -> None:
    import subprocess
    import sys

    spec = tmp_path / "validation.json"
    spec.write_text(
        json.dumps(
            {
                "schema": "shaderlod-validation-spec-v1",
                "surface": [1024, 768],
                "dh_enabled": False,
            }
        )
    )
    command = [
        sys.executable,
        "-m",
        "shaderlod_host",
        "validate",
        "--spec",
        str(spec),
        "--plan",
    ]
    result = subprocess.run(command, capture_output=True, text=True)
    assert result.returncode == 0
    assert json.loads(result.stdout)["maximum_control_wait_seconds"] == 75
    spec.write_text("{}")
    result = subprocess.run(command, capture_output=True, text=True)
    assert result.returncode == 2
    assert json.loads(result.stdout)["status"] == "refused"
    assert list(tmp_path.iterdir()) == [spec]


def test_campaign_verification_accepts_preserved_windows_paths(tmp_path: Path) -> None:
    import subprocess
    import sys

    policy = QualificationPolicy.decode(policy_spec(2))
    paths = [
        run_fixture(tmp_path / ("comparison-" + str(i)), policy, i) for i in (1, 2)
    ]
    definition = json.loads((paths[0].parent / "plan.json").read_bytes())["definition"]
    (tmp_path / "campaign.json").write_text(
        json.dumps(
            {
                "schema": "shaderlod-campaign-v1",
                "sessions": [definition | {"seed": 0}, definition | {"seed": 1}],
                "policy": policy.wire(),
                "startup_ms": 120000,
                "budget_ms": 900000,
            }
        )
    )
    result = tmp_path / "result.json"
    record = {
        "schema": "shaderlod-campaign-result-v1",
        "state": "complete",
        "runs": ["comparison-1\\result.json", "comparison-2\\result.json"],
    }
    result.write_text(json.dumps(record))
    command = [
        sys.executable,
        "-m",
        "shaderlod_host",
        "verify",
        "--result",
        str(result),
    ]
    checked = subprocess.run(command, capture_output=True, text=True)
    assert checked.returncode == 0
    assert json.loads(checked.stdout)["verified_runs"] == 2
    record["runs"] = ["../elsewhere/result.json"]
    result.write_text(json.dumps(record))
    assert subprocess.run(command, capture_output=True).returncode == 2

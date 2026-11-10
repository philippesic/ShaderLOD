from pathlib import Path
import pytest
from checks.contracts import Evidence, Health, Measured, Missing, decode_sample, receipt
from checks.policy import discover
from checks.publisher import publish
from checks.verify import cpu, main
import sys


def test_synthetic_round_trip() -> None:
    assert cpu()["evidence"] == Evidence.SYNTHETIC


def test_live_refused() -> None:
    with pytest.raises(ValueError, match="D1:"):
        receipt("live-functional", "synthetic")


def test_measurement_contract() -> None:
    assert decode_sample({"kind": "measured", "value": 0, "count": 1}) == Measured(0, 1)
    assert decode_sample({"kind": "missing", "reason": "unscheduled"}) == Missing(
        "unscheduled"
    )
    with pytest.raises(ValueError, match="D5:"):
        decode_sample({"kind": "missing", "value": 0})
    with pytest.raises(ValueError, match="D5:"):
        Measured(0, 0)


def test_health_and_evidence() -> None:
    assert len(Health) == 2
    assert len(Evidence) == 5
    with pytest.raises(ValueError):
        Health("UNKNOWN")


def test_publisher_once(tmp_path: Path) -> None:
    path = tmp_path / "receipt.json"
    publish(path, {"value": 1})
    original = path.read_bytes()
    with pytest.raises(FileExistsError):
        publish(path, {"value": 2})
    assert path.read_bytes() == original


def test_discovery_no_shared_edit() -> None:
    tree = {"src/NewPackSpec.java": b'class NewPackSpec { static String ID = "new"; }'}
    assert discover(tree) == ["new"]


def test_runtime_refusal(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(sys, "argv", ["verify", "--runtime"])
    assert main() == 2

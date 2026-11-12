import json
from pathlib import Path

import pytest

from shaderlod_host.campaign import Campaign
from test_assessment import policy_spec


def test_campaign_freezes_independent_repetition_and_lifecycle_budget() -> None:
    plan = json.loads(
        (
            Path(__file__).resolve().parents[1]
            / "benchmarks/comparisons/asahi-null.json"
        ).read_bytes()
    )
    plan["scope"] = "contribution"
    record = {
        "schema": "shaderlod-campaign-v1",
        "sessions": [plan, plan | {"seed": 9}],
        "policy": policy_spec(2),
        "startup_ms": 120000,
        "budget_ms": 600000,
    }
    campaign = Campaign.decode(record)
    assert campaign.describe()["capture_ms"] == 40000
    assert campaign.wire()["policy"] == policy_spec(2)
    for changes in (
        {"budget_ms": 300000},
        {"sessions": [plan]},
        {"policy": policy_spec(3)},
    ):
        with pytest.raises(ValueError):
            Campaign.decode(record | changes)


def test_tracked_campaigns_are_executable() -> None:
    root = Path(__file__).resolve().parents[1] / "benchmarks/qualification"
    for path in root.glob("*.json"):
        record = json.loads(path.read_bytes())
        if record.get("schema") == "shaderlod-campaign-v1":
            assert Campaign.decode(record).comparisons[0].window_ms > 0

import json
from pathlib import Path

import pytest

from shaderlod_host.comparison import ComparisonPlan, execute_comparison


def spec() -> dict[str, object]:
    data: dict[str, object] = json.loads(
        (
            Path(__file__).resolve().parents[1]
            / "benchmarks/comparisons/asahi-null.json"
        ).read_bytes()
    )
    return data


def test_balanced_schedule_and_finite_matrix() -> None:
    plan = ComparisonPlan.decode(spec() | {"blocks": 2})
    schedule = plan.describe()["schedule"]
    assert isinstance(schedule, list)
    assert [row["arm"] for row in schedule[:2]] == list(
        reversed([row["arm"] for row in schedule[2:]])
    )
    assert plan.describe()["capture_ms"] == 40000
    for changes in (
        {"blocks": True},
        {"budget_ms": 20000},
        {"unknown": 1},
        {"window_ms": 60001},
    ):
        with pytest.raises(ValueError):
            ComparisonPlan.decode(spec() | changes)


def test_one_native_surface_per_comparison() -> None:
    data = spec()
    assert isinstance(data["cases"], list)
    second = json.loads(json.dumps(data["cases"][0]))
    second["id"] = "coast-pan-4k"
    second["surface"] = [3840, 2160]
    data["cases"].append(second)
    with pytest.raises(ValueError, match="one native surface"):
        ComparisonPlan.decode(data)


def test_route_out_of_bounds_refuses_before_game() -> None:
    data = spec()
    assert isinstance(data["cases"], list)
    data["cases"][0]["to"][0] = 1000
    with pytest.raises(ValueError, match="64 blocks"):
        ComparisonPlan.decode(data)


def test_failed_activation_is_preserved_and_restored(tmp_path: Path) -> None:
    actions: list[str] = []

    def command(
        session: Path,
        action: str,
        duration: int,
        timeout: float,
        payload: dict[str, object] | None,
    ) -> dict[str, object]:
        actions.append(action)
        return {"ready": True, "surface": [2560, 1600]} if action == "status" else {}

    result = execute_comparison(
        ComparisonPlan.decode(spec()),
        tmp_path / "session",
        tmp_path / "output",
        command,
    )
    assert result["state"] == "incomplete"
    assert "submitted activation" in str(result["failure"])
    assert actions[-1] == "restore"
    assert (tmp_path / "output/result.json").exists()
    assert result["qualification"] == "unqualified"


def test_route_and_measured_generation_drift_cannot_promote(tmp_path: Path) -> None:
    def command(
        session: Path,
        action: str,
        duration: int,
        timeout: float,
        payload: dict[str, object] | None,
    ) -> dict[str, object]:
        if action == "status":
            return {
                "ready": True,
                "pose": [0.5, 131, 0.5, -45, 30],
                "surface": [2560, 1600],
            }
        if action == "select":
            return {
                "activation": {"linked": True, "witnesses": ["submitted-frame:abc"]},
                "ready": True,
                "surface": [2560, 1600],
                "dimension": "minecraft:overworld",
                "options": {},
                "world_day_time": 6000,
                "rain": 0,
            }
        if action == "capture":
            return {
                "valid": True,
                "ready": True,
                "surface": [2560, 1600],
                "dimension": "minecraft:overworld",
                "options": {},
                "world_day_time": 6000,
                "rain": 0,
                "capture": {
                    "overflow": False,
                    "generationChanged": True,
                    "frames": [
                        {
                            "sequence": 1,
                            "timestamp_ns": 100,
                            "interval_ns": 10,
                            "generation": 1,
                        }
                    ],
                },
            }
        return {}

    result = execute_comparison(
        ComparisonPlan.decode(spec() | {"warmup_ms": 0}),
        tmp_path / "session",
        tmp_path / "output",
        command,
    )
    assert result["state"] == "incomplete"
    assert result["observations"]
    assert result["restored"] is True
    rows = result["observations"]
    assert isinstance(rows, list)
    assert rows[0]["summary"]["frame_limit"]["state"] == "unobserved"
    scope = result["frame_limit"]
    assert isinstance(scope, dict)
    assert (scope["windows"], scope["limited"], scope["unobserved"]) == (1, 0, 1)


def test_frame_limit_scope_counts_limited_and_unrecorded_windows() -> None:
    from shaderlod_host.comparison import frame_limit_scope

    rows: list[dict[str, object]] = [
        {"summary": {"frame_limit": {"state": "limited"}}},
        {"summary": {"frame_limit": {"state": "below_limit"}}},
        {"summary": {"frame_limit": {"state": "unobserved"}}},
        {"summary": {}},
    ]
    scope = frame_limit_scope(rows)
    assert (scope["windows"], scope["limited"], scope["unobserved"]) == (4, 1, 2)
    assert "cannot show a speedup" in str(scope["meaning"])
    clear = frame_limit_scope([{"summary": {"frame_limit": {"state": "unlimited"}}}])
    assert (clear["limited"], clear["unobserved"]) == (0, 0)


def test_largest_half_window_drift_ignores_windows_without_a_measurement() -> None:
    from shaderlod_host.comparison import largest_half_window_drift

    rows: list[dict[str, object]] = [
        {"summary": {"half_window_drift": {"relative_change": -0.031}}},
        {"summary": {"half_window_drift": {"relative_change": 0.004}}},
        {"summary": {"half_window_drift": {"relative_change": None}}},
        {"summary": {}},
    ]
    assert largest_half_window_drift(rows) == 0.031
    assert largest_half_window_drift([{"summary": {}}]) is None


def test_native_configuration_guard_includes_dh_caps_and_resources() -> None:
    from shaderlod_host.comparison import configuration

    native: dict[str, object] = {
        "options": {"TAA": "true"},
        "client_options": {"vsync": True},
        "resource_packs": ["fixture"],
        "dh": {
            "present": True,
            "version": "fixture",
            "enabled": True,
            "readiness": "loading",
        },
    }
    assert configuration(native) == configuration(
        native
        | {
            "dh": {
                "present": True,
                "version": "fixture",
                "enabled": True,
                "readiness": "settled",
            }
        }
    )
    invalid_changes: tuple[dict[str, object], ...] = (
        {"client_options": {"vsync": False}},
        {"resource_packs": []},
        {"dh": {"present": True, "version": "fixture", "enabled": False}},
    )
    for changes in invalid_changes:
        assert configuration(native) != configuration(native | changes)
    with pytest.raises(ValueError, match="unknown"):
        configuration(native | {"dh": {"present": True, "enabled": None}})


def test_authored_comparisons_are_executable() -> None:
    from shaderlod_host.comparison import installation_plan

    root = Path(__file__).resolve().parents[1] / "benchmarks/comparisons"
    for path in root.glob("*.json"):
        plan = ComparisonPlan.decode(json.loads(path.read_bytes()))
        assert len(plan.schedule()) == len(plan.cases) * len(plan.arms) * plan.blocks
        if "installation" in path.name:
            assert installation_plan(plan)["fresh_processes"] == 2 * plan.blocks

from dataclasses import dataclass
import json
from pathlib import Path

from shaderlod_host.assessment import QualificationPolicy
from shaderlod_host.benchmark import object_fields, positive
from shaderlod_host.comparison import ComparisonPlan


@dataclass(frozen=True)
class Campaign:
    comparisons: tuple[ComparisonPlan, ...]
    policy: QualificationPolicy
    startup_ms: int
    budget_ms: int

    @classmethod
    def decode(cls, value: object) -> "Campaign":
        record = object_fields(
            value,
            {"schema", "sessions", "policy", "startup_ms", "budget_ms"},
            "campaign",
        )
        sessions = record["sessions"]
        if (
            record["schema"] != "shaderlod-campaign-v1"
            or not isinstance(sessions, list)
            or not 2 <= len(sessions) <= 128
        ):
            raise ValueError(
                "campaign requires 2–128 explicitly planned fresh sessions"
            )
        plans = tuple(ComparisonPlan.decode(item) for item in sessions)
        policy = QualificationPolicy.decode(record["policy"])
        if policy.sessions != len(plans):
            raise ValueError("policy and planned independent-session count differ")
        for plan in plans:
            if set(policy.cases) != {case.identity for case in plan.cases} or not {
                policy.reference,
                policy.candidate,
            } <= {arm.identity for arm in plan.arms}:
                raise ValueError(
                    "every session needs the complete paired policy coverage"
                )
            if plan.quality_cadence_ms or plan.instrument != "frames":
                raise ValueError("quality/diagnostics are separate from confirmation")
        startup = positive(record["startup_ms"], "startup_ms")
        budget = positive(record["budget_ms"], "budget_ms")
        if (
            startup > 300000
            or budget > 86400000
            or sum(p.budget_ms + startup + 15000 for p in plans) > budget
        ):
            raise ValueError(
                "campaign budget must include startup, comparison and close allowances"
            )
        return cls(plans, policy, startup, budget)

    def wire(self) -> dict[str, object]:
        return {
            "schema": "shaderlod-campaign-v1",
            "sessions": [p.wire() for p in self.comparisons],
            "policy": self.policy.wire(),
            "startup_ms": self.startup_ms,
            "budget_ms": self.budget_ms,
        }

    def describe(self) -> dict[str, object]:
        return {
            "schema": "shaderlod-campaign-plan-v1",
            "independent_sessions": len(self.comparisons),
            "capture_ms": sum(
                len(p.cases) * len(p.arms) * p.blocks * p.window_ms
                for p in self.comparisons
            ),
            "maximum_ms": self.budget_ms,
            "policy_sha256": self.policy.identity,
            "scope": "fixed plan; no retries, optional stopping or automatic promotion; quality review separate",
        }


def campaign_results(output: Path) -> list[Path]:
    definition = Campaign.decode(json.loads((output / "campaign.json").read_bytes()))
    return [
        output / ("comparison-" + str(i + 1)) / "result.json"
        for i in range(len(definition.comparisons))
    ]

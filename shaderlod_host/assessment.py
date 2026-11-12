from dataclasses import dataclass
import hashlib
import json
import math
from pathlib import Path
from statistics import median

from shaderlod_host.benchmark import (
    label,
    object_fields,
    positive,
    verify_frame_receipt,
    build_identity,
    collector_identity,
)


def fraction(value: object, name: str, upper: float = 1) -> float:
    if (
        not isinstance(value, (int, float))
        or isinstance(value, bool)
        or not 0 <= value < upper
        or not math.isfinite(value)
    ):
        raise ValueError(name + ": finite fraction required")
    return float(value)


@dataclass(frozen=True)
class QualificationPolicy:
    reference: str
    candidate: str
    confidence: float
    minimum_gain: float
    maximum_harm: float
    cases: tuple[str, ...]
    benefit_cases: tuple[str, ...]
    sessions: int
    quality_criteria: str

    @classmethod
    def decode(cls, value: object) -> "QualificationPolicy":
        data = object_fields(
            value,
            {
                "schema",
                "reference",
                "candidate",
                "confidence",
                "minimum_gain",
                "maximum_harm",
                "cases",
                "benefit_cases",
                "sessions",
                "quality_criteria",
            },
            "qualification policy",
        )
        if data["schema"] != "shaderlod-qualification-policy-v1":
            raise ValueError("unsupported policy")
        if (
            not isinstance(data["cases"], list)
            or not 1 <= len(data["cases"]) <= 64
            or not isinstance(data["benefit_cases"], list)
            or not data["benefit_cases"]
        ):
            raise ValueError("required cases and benefit cases must be explicit")
        cases = tuple(label(c, "case") for c in data["cases"])
        benefits = tuple(label(c, "benefit case") for c in data["benefit_cases"])
        if (
            len(set(cases)) != len(cases)
            or len(set(benefits)) != len(benefits)
            or not set(benefits) <= set(cases)
        ):
            raise ValueError("distinct cases and matching benefit scope required")
        confidence = fraction(data["confidence"], "confidence")
        reference, candidate = (
            label(data["reference"], "reference"),
            label(data["candidate"], "candidate"),
        )
        if reference == candidate or confidence < 0.8:
            raise ValueError("distinct arms and confidence of at least 0.8 required")
        sessions = positive(data["sessions"], "sessions")
        if sessions > 128:
            raise ValueError("bounded fixed session count required")
        return cls(
            reference,
            candidate,
            confidence,
            fraction(data["minimum_gain"], "minimum_gain"),
            fraction(data["maximum_harm"], "maximum_harm"),
            cases,
            benefits,
            sessions,
            label(data["quality_criteria"], "quality_criteria"),
        )

    def wire(self) -> dict[str, object]:
        return {
            "schema": "shaderlod-qualification-policy-v1",
            "reference": self.reference,
            "candidate": self.candidate,
            "confidence": self.confidence,
            "minimum_gain": self.minimum_gain,
            "maximum_harm": self.maximum_harm,
            "cases": list(self.cases),
            "benefit_cases": list(self.benefit_cases),
            "sessions": self.sessions,
            "quality_criteria": self.quality_criteria,
        }

    @property
    def identity(self) -> str:
        return hashlib.sha256(
            json.dumps(self.wire(), sort_keys=True, allow_nan=False).encode()
        ).hexdigest()


def median_interval(values: list[float], confidence: float) -> dict[str, object]:
    if (
        not values
        or any(not math.isfinite(v) for v in values)
        or not 0 < confidence < 1
    ):
        raise ValueError("finite independent observations and confidence required")
    ordered = sorted(values)
    n = len(ordered)
    chosen = 0
    achieved = 0.0
    for k in range(1, (n + 1) // 2 + 1):
        coverage = 1 - 2 * sum(math.comb(n, j) for j in range(k)) / (2**n)
        if coverage >= confidence:
            chosen, achieved = k, coverage
    return {
        "sessions": n,
        "median": median(ordered),
        "low": ordered[chosen - 1] if chosen else None,
        "high": ordered[n - chosen] if chosen else None,
        "requested_coverage": confidence,
        "achieved_coverage": achieved if chosen else None,
        "method": "binomial order-statistic interval for median of independent session effects",
    }


def assess(
    policy: QualificationPolicy,
    runs: list[Path],
    review: dict[str, object] | None = None,
) -> dict[str, object]:
    grouped: dict[str, dict[str, dict[str, dict[str, float]]]] = {
        case: {} for case in policy.cases
    }
    compatibility: dict[str, str] = {}
    problems: list[str] = []
    result_hashes = []
    for path in runs:
        from shaderlod_host.comparison import verify_comparison

        result = verify_comparison(path)
        if (
            not isinstance(result, dict)
            or result.get("schema") != "shaderlod-comparison-result-v1"
        ):
            raise ValueError("comparison result required")
        expected = json.loads((path.parent / "receipt.json").read_bytes())
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        if expected.get("sha256") != digest:
            raise ValueError("comparison receipt mismatch")
        result_hashes.append(digest)
        if result.get("policy_sha256") != policy.identity:
            problems.append(str(path) + ": policy was not frozen before execution")
        if result.get("state") != "complete" or result.get("restored") is not True:
            problems.append(str(path) + ": failed/incomplete attempt or restoration")
        rows = result.get("observations")
        if not isinstance(rows, list):
            raise ValueError("observation list required")
        definition = json.loads((path.parent / "plan.json").read_bytes())["definition"]
        from shaderlod_host.comparison import ComparisonPlan

        plan = ComparisonPlan.decode(definition)
        if (
            plan.scope == "development"
            or plan.instrument != "frames"
            or plan.quality_cadence_ms
        ):
            problems.append(
                str(path)
                + ": exploratory/diagnostic/quality data cannot establish confirmation"
            )
        if not set(policy.cases) <= {case.identity for case in plan.cases}:
            problems.append(str(path) + ": policy coverage missing from frozen plan")
        for index, row in enumerate(rows):
            if (
                not isinstance(row, dict)
                or not isinstance(row.get("case"), str)
                or not isinstance(row.get("arm"), str)
                or row.get("case") not in grouped
                or row.get("arm") not in {policy.reference, policy.candidate}
            ):
                continue
            case, arm = str(row["case"]), str(row["arm"])
            directory = path.parent / str(row["attempt"])
            verify_frame_receipt(directory / "receipt.json")
            observed = json.loads((directory / "observation.json").read_bytes())
            selected = row.get("selection")
            summary = row.get("summary")
            if (
                not isinstance(selected, dict)
                or not isinstance(summary, dict)
                or row.get("workload_valid") is not True
                or summary.get("valid") is not True
                or summary != json.loads((directory / "summary.json").read_bytes())
                or selected.get("session_identity") != observed.get("session_identity")
                or row.get("quality_only") is True
                or row.get("instrument", "frames") != "frames"
            ):
                problems.append(case + ": invalid workload or measurements")
                continue
            metadata = selected.get("session_identity")
            if not isinstance(metadata, dict) or not isinstance(
                metadata.get("child"), dict
            ):
                problems.append(case + ": missing JVM process identity")
                continue
            candidate_build = build_identity(metadata)
            if candidate_build is None:
                problems.append(case + ": missing exact candidate build identity")
                continue
            child = metadata["child"]
            key = json.dumps(
                [
                    metadata.get("host"),
                    metadata.get("boot"),
                    child.get("pid"),
                    child.get("birth"),
                ]
            )
            config = json.dumps(
                [
                    metadata.get("host"),
                    metadata.get("world"),
                    candidate_build,
                    metadata.get("instrumentation"),
                    selected.get("surface"),
                    selected.get("dimension"),
                    selected.get("renderer"),
                    selected.get("runtime"),
                    selected.get("runtime_mods"),
                    selected.get("world_seed"),
                    selected.get("world_path"),
                    selected.get("resource_packs"),
                    selected.get("dh", {}).get("present"),
                    selected.get("dh", {}).get("enabled"),
                    selected.get("dh", {}).get("version"),
                    selected.get("options"),
                    selected.get("client_options"),
                    selected.get("loaded_main_class_sha256"),
                    selected.get("loaded_dev_class_sha256"),
                    collector_identity(metadata),
                    next(
                        (
                            case
                            for case in definition["cases"]
                            if case["id"] == row["case"]
                        ),
                        None,
                    ),
                    observed.get("progression_clock"),
                ],
                sort_keys=True,
            )
            if case in compatibility and compatibility[case] != config:
                problems.append(
                    case + ": incompatible environments or settings; partition the case"
                )
                continue
            compatibility[case] = config
            fps = summary.get("fps")
            if (
                not isinstance(fps, (int, float))
                or isinstance(fps, bool)
                or not math.isfinite(fps)
                or fps <= 0
            ):
                problems.append(case + ": invalid throughput")
                continue
            block = str(path.resolve()) + ":" + str(row.get("block"))
            pair = grouped[case].setdefault(key, {}).setdefault(block, {})
            if arm in pair:
                raise ValueError("duplicate observation in comparison pair")
            pair[arm] = fps
    outcomes: list[dict[str, object]] = []
    family_confidence = 1 - (1 - policy.confidence) / len(policy.cases)
    for case, sessions in grouped.items():
        if len(sessions) != policy.sessions:
            problems.append(
                case + ": independent session count differs from frozen stopping rule"
            )
        effects = []
        for blocks in sessions.values():
            pairs = []
            for pair in blocks.values():
                if set(pair) != {policy.reference, policy.candidate}:
                    problems.append(case + ": missing paired arm")
                else:
                    pairs.append(pair[policy.candidate] / pair[policy.reference] - 1)
            if pairs:
                effects.append(median(pairs))
        if not effects:
            problems.append(case + ": required coverage missing")
            outcomes.append({"case": case, "state": "missing"})
            continue
        interval = median_interval(effects, family_confidence)
        low = interval["low"]
        high = interval["high"]
        threshold = (
            policy.minimum_gain
            if case in policy.benefit_cases
            else -policy.maximum_harm
        )
        state = "inconclusive"
        if isinstance(low, (int, float)) and low >= threshold:
            state = "meets_margin"
        elif isinstance(high, (int, float)) and high < threshold:
            state = "fails_margin"
        outcomes.append(
            {
                "case": case,
                "state": state,
                "margin": threshold,
                "session_effects": effects,
                "interval": interval,
            }
        )
    quality = False
    if review is not None:
        record = object_fields(
            review,
            {
                "schema",
                "reviewer",
                "date",
                "policy_sha256",
                "results_sha256",
                "cases",
                "approved",
                "limitations",
            },
            "quality review",
        )
        for key in ("results_sha256", "cases"):
            items = record[key]
            if not isinstance(items, list) or not all(
                isinstance(item, str) for item in items
            ):
                raise ValueError("quality review requires string identities")
        quality = (
            record["schema"] == "shaderlod-quality-review-v1"
            and record["approved"] is True
            and bool(label(record["reviewer"], "reviewer"))
            and bool(label(record["date"], "date"))
            and record["policy_sha256"] == policy.identity
            and isinstance(record["results_sha256"], list)
            and set(result_hashes) <= set(record["results_sha256"])
            and isinstance(record["cases"], list)
            and set(policy.cases) <= set(record["cases"])
        )
    return {
        "schema": "shaderlod-qualification-assessment-v1",
        "policy_sha256": policy.identity,
        "cases": outcomes,
        "problems": problems,
        "quality_review_complete": quality,
        "recommendation": "meets_declared_policy"
        if quality
        and not problems
        and all(case["state"] == "meets_margin" for case in outcomes)
        else "do_not_promote",
        "limits": "Conditional on independent comparable sessions and declared coverage; no universal hardware/pack or perceptual claim; small samples remain inconclusive",
    }

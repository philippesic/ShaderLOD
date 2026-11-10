from dataclasses import dataclass
from enum import StrEnum
import math


class Health(StrEnum):
    PASS = "PASS"
    FAIL = "FAIL"


class Evidence(StrEnum):
    SOURCE = "source-inspected"
    SYNTHETIC = "cpu-synthetic"
    INTEGRATION = "cpu-integration"
    FUNCTIONAL = "live-functional"
    PERFORMANCE = "live-performance-qualified"


@dataclass(frozen=True)
class Measured:
    value: float
    count: int

    def __post_init__(self) -> None:
        if (
            type(self.value) not in (int, float)
            or type(self.count) is not int
            or not math.isfinite(self.value)
            or self.count < 1
        ):
            raise ValueError("D5: invalid measurement")


@dataclass(frozen=True)
class Missing:
    reason: str

    def __post_init__(self) -> None:
        if not self.reason.strip():
            raise ValueError("D5: missing reason required")


def decode_sample(data: dict[str, object]) -> Measured | Missing:
    if data.get("kind") == "measured" and set(data) == {"kind", "value", "count"}:
        value, count = data["value"], data["count"]
        if (
            isinstance(value, (int, float))
            and not isinstance(value, bool)
            and isinstance(count, int)
            and not isinstance(count, bool)
        ):
            return Measured(value, count)
    if data.get("kind") == "missing" and set(data) == {"kind", "reason"}:
        reason = data["reason"]
        if isinstance(reason, str):
            return Missing(reason)
    raise ValueError("D5: measured or missing-with-reason required")


def receipt(evidence: str, producer: str) -> dict[str, str]:
    level = Evidence(evidence)
    if level in (Evidence.FUNCTIONAL, Evidence.PERFORMANCE):
        raise ValueError(
            "D1: live receipts unavailable without runtime-owned verification"
        )
    allowed = {
        "synthetic": Evidence.SYNTHETIC,
        "source-inspector": Evidence.SOURCE,
        "cpu-integration": Evidence.INTEGRATION,
    }
    if producer not in allowed or allowed[producer] != level:
        raise ValueError("D1: synthetic evidence relabeled")
    return {
        "schema": "foundation-receipt-v1",
        "evidence": level.value,
        "producer": producer,
    }


def runtime_status(status: int) -> int:
    if status != 2:
        raise ValueError(
            "D1: runtime stub must refuse without runtime-owned verification"
        )
    return status


def require_tests(count: int) -> None:
    if count < 1:
        raise ValueError("S2: zero tests discovered")

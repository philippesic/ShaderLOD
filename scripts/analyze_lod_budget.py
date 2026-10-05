#!/usr/bin/env python3
"""Calculate a target gap or conditional Amdahl bound, never a measured gain.

Inputs must come from the same metric and matched experiment. GPU intervals
are not total frame time. FPS reciprocals are capture-average frame durations,
not medians or tails. This tool does not validate evidence or combine effects.
"""

import argparse
import json
import math


KINDS = ("fps", "total-frame-ms", "gpu-ms")


def positive_finite(name, value):
    if not math.isfinite(value) or value <= 0:
        raise ValueError(f"{name} must be finite and positive")


def target_gap(kind, baseline, current=None, target_speedup=1.5):
    if kind not in KINDS:
        raise ValueError(f"kind must be one of {KINDS}")
    positive_finite("baseline", baseline)
    positive_finite("target_speedup", target_speedup)
    if target_speedup <= 1:
        raise ValueError("target_speedup must exceed 1")
    if current is None:
        current = baseline
    positive_finite("current", current)
    baseline_ms = 1000 / baseline if kind == "fps" else baseline
    current_ms = 1000 / current if kind == "fps" else current
    target_ms = baseline_ms / target_speedup
    return {
        "input_kind": kind,
        "interpretation": ("GPU timeline interval only; no total-frame prediction"
                           if kind == "gpu-ms" else
                           "Capture-average frame duration derived from FPS; no tail information"
                           if kind == "fps" else
                           "Supplied total-frame duration; source boundary must be validated"),
        "baseline_ms": baseline_ms,
        "current_ms": current_ms,
        "target_ms": target_ms,
        "target_fps": 1000 / target_ms if kind != "gpu-ms" else None,
        "current_throughput_uplift_percent": 100 * (baseline_ms / current_ms - 1),
        "current_time_reduction_percent": 100 * (1 - current_ms / baseline_ms),
        "target_time_reduction_percent": 100 * (1 - 1 / target_speedup),
        "remaining_time_ms": max(0, current_ms - target_ms),
        "remaining_time_reduction_percent": max(0, 100 * (1 - target_ms / current_ms)),
        "target_met_arithmetically": current_ms <= target_ms,
        "limits": "Arithmetic only, not performance acceptance, causal attribution or quality evidence.",
    }


def amdahl_bound(affected_share, effect_speedup, target_speedup=1.5):
    """Assume additive critical-path cost, unchanged remainder and zero overhead."""
    if not math.isfinite(affected_share) or not 0 <= affected_share <= 1:
        raise ValueError("affected_share must be a finite fraction in [0, 1]")
    if math.isnan(effect_speedup) or effect_speedup < 1:
        raise ValueError("effect_speedup must be at least 1 (infinity means removal)")
    positive_finite("target_speedup", target_speedup)
    if target_speedup <= 1:
        raise ValueError("target_speedup must exceed 1")
    retained = 0 if math.isinf(effect_speedup) else 1 / effect_speedup
    fraction = 1 - affected_share * (1 - retained)
    result_speedup = None if fraction == 0 else 1 / fraction
    needed = None if retained == 1 else (1 - 1 / target_speedup) / (1 - retained)
    return {
        "hypothetical_affected_share": affected_share,
        "hypothetical_effect_speedup": None if math.isinf(effect_speedup) else effect_speedup,
        "effect_completely_removed": math.isinf(effect_speedup),
        "predicted_time_reduction_percent": 100 * (1 - fraction),
        "predicted_speedup": result_speedup,
        "unbounded_speedup": fraction == 0,
        "required_affected_share": needed,
        "target_possible_under_assumptions": needed is not None and needed <= 1 and (
            affected_share >= needed or math.isclose(affected_share, needed, rel_tol=1e-12, abs_tol=1e-12)),
        "assumptions": "Independent additive critical-path work, unchanged remaining work, no overhead; supplied share is not measured by this tool.",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--kind", choices=KINDS, required=True)
    parser.add_argument("--baseline", type=float, required=True)
    parser.add_argument("--current", type=float)
    parser.add_argument("--target-speedup", type=float, default=1.5)
    parser.add_argument("--affected-share", type=float)
    parser.add_argument("--effect-speedup", type=float, default=math.inf)
    args = parser.parse_args()
    try:
        output = target_gap(args.kind, args.baseline, args.current, args.target_speedup)
        if args.affected_share is not None:
            output["conditional_amdahl_bound"] = amdahl_bound(
                args.affected_share, args.effect_speedup, args.target_speedup)
    except ValueError as error:
        parser.error(str(error))
    print(json.dumps(output, indent=2, allow_nan=False))


if __name__ == "__main__":
    main()

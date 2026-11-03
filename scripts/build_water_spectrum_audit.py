#!/usr/bin/env python3
"""Build source-only A9 evidence; never emits or installs a shader candidate.

The numerical witnesses reject shortcuts, not the possibility of approximate
water filtering. They do not sample a native texture or simulate a rendered
frame. Exact pack/member pins make this audit reproducible and fail closed.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path, PurePosixPath
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "patches/water-spectrum-experiments/source-contract-r3.json"
OUTPUT = ROOT / "dev/logs/mechanism-evaluation-r3/water-spectrum"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def photon_carriers(count: int, frequency_option: float = 1.0,
                    lacunarity_option: float = 1.0) -> list[float]:
    """Unwarped fundamental cycles/world-unit, not a full spectrum bound."""
    return [0.7 * frequency_option * (1.7 * lacunarity_option / 1.5) ** i
            for i in range(count)]


def gerstner_shape(phase: float) -> float:
    return (0.5 * math.sin(phase) + 0.5) ** 2


def shape_derivative(phase: float) -> float:
    return (0.5 * math.sin(phase) + 0.5) * math.cos(phase)


def phase_gradient_witness() -> dict:
    # Synthetic smooth n(x) in [0.4,0.6], explicitly not the native PNG.
    k, f, q = math.tau, 0.7, 1.0
    n = lambda x: 0.5 + 0.1 * math.sin(math.tau * q * x)
    height = lambda x: gerstner_shape(-k * (f * x + 2.0 * n(x)))
    phase = -k * 2.0 * n(0.0)
    native_equation_derivative = shape_derivative(phase) * -k * (
        f + 2.0 * 0.1 * math.tau * q)
    frozen_noise_derivative = shape_derivative(phase) * -k * f
    epsilon = 1.0e-6
    return {
        "kind": "synthetic algebra counterexample; not native texture/runtime",
        "noise": "n(x)=0.5+0.1*sin(2*pi*x)",
        "full_chain_derivative": native_equation_derivative,
        "central_difference_derivative": (height(epsilon) - height(-epsilon)) / (2 * epsilon),
        "frozen_noise_derivative": frozen_noise_derivative,
        "omitted_derivative": native_equation_derivative - frozen_noise_derivative,
        "native_h_0_1_forward_slope": (height(0.1) - height(0.0)) / 0.1,
        "limitation": "Analytic derivatives also differ from native h=0.1 forward differences.",
    }


def photon_ggx_ndf_for_slope(slope: float, roughness: float) -> float:
    """Pinned bsdf.glsl NDF at an upward half-vector; not full sun shading."""
    noh_sq = 1.0 / (1.0 + slope * slope)
    alpha_sq = roughness * roughness
    return alpha_sq / (math.pi * (1.0 - noh_sq + noh_sq * alpha_sq) ** 2)


def moments_witness() -> dict:
    # Equal first/second moments do not specify a normal distribution.
    s = 0.01
    a = [(-s, 0.5), (s, 0.5)]
    b = [(0.0, 2.0 / 3.0), (-math.sqrt(3.0) * s, 1.0 / 6.0),
         (math.sqrt(3.0) * s, 1.0 / 6.0)]
    result = {}
    for name, distribution in (("a", a), ("b", b)):
        result[name] = {
            "distribution": distribution,
            "mean_slope": sum(v * w for v, w in distribution),
            "second_slope_moment": sum(v * v * w for v, w in distribution),
            "mean_upward_ggx_ndf": sum(photon_ggx_ndf_for_slope(v, 0.002) * w
                                       for v, w in distribution),
        }
    result["ndf_ratio_b_over_a"] = result["b"]["mean_upward_ggx_ndf"] / result["a"]["mean_upward_ggx_ndf"]
    result["kind"] = "synthetic equal-moment counterexample, not full BRDF, pixels or quality loss"
    result["limitation"] = "Does not disprove a validated approximate lobe fit over a fixed workload."
    return result


def numerical_witnesses() -> dict:
    carriers = photon_carriers(3)
    return {
        "photon_native_default": {
            "iterations": 3,
            "unwarped_fundamentals_cycles_per_world_unit": carriers,
            "unwarped_second_harmonics": [2 * v for v in carriers],
            "carrier_ratio": 1.7 / 1.5,
            "phase_noise_uv_ratio": 2.5,
            "shape_identity": "(.5+.5*sin(p))^2 = 3/8 + sin(p)/2 - cos(2*p)/8",
            "retained_amplitude_multiplier_if_renormalized_3_to_2": 7.0 / 6.0,
            "scope": "source-default equations; actual loaded options/sampler/footprint unknown",
        },
        "phase_gradient": phase_gradient_witness(),
        "equal_moments": moments_witness(),
    }


def read_pinned_pack(path: Path, contract: dict) -> dict[str, bytes]:
    data = path.read_bytes()
    if sha256(data) != contract["archive_sha256"]:
        raise ValueError(f"archive hash mismatch: {path}")
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError("duplicate archive members")
        for name in names:
            part = PurePosixPath(name)
            if part.is_absolute() or ".." in part.parts or "\\" in name:
                raise ValueError("unsafe archive member")
        selected = {}
        for name, expected in contract["members"].items():
            try:
                member = archive.read(name)
            except KeyError as exc:
                raise ValueError(f"missing source member: {name}") from exc
            if sha256(member) != expected:
                raise ValueError(f"member hash mismatch: {name}")
            selected[name] = member
    return selected


def source_inventory(members: dict[str, bytes], symbols: list[str]) -> dict:
    occurrences, definitions, profiles = [], [], []
    symbol_pattern = re.compile(r"\b(" + "|".join(re.escape(x) for x in symbols) + r")\s*\(")
    for member, data in sorted(members.items()):
        if not member.endswith((".glsl", ".fsh", ".vsh", ".properties", ".settings")):
            continue
        for number, line in enumerate(data.decode("utf-8-sig").splitlines(), 1):
            for match in symbol_pattern.finditer(line):
                occurrences.append({"member": member, "line": number,
                                    "symbol": match.group(1), "text": line.strip()})
            if re.search(r"(?:#\s*define\s+)(?:WATER_|HYPER_DETAILED_WAVES|PW_|waterRefl|WaterParallax|waterheight|waveSize|Caustics|causticsStrength|Refraction|Waving_Water)", line):
                definitions.append({"member": member, "line": number,
                                    "text": line.strip()})
            if line.lstrip().startswith("profile."):
                profiles.append({"member": member, "line": number, "text": line.strip()})
    return {
        "symbol_occurrences_including_definitions_and_comments": occurrences,
        "option_definitions_unpreprocessed": definitions,
        "native_profile_declarations_unresolved": profiles,
        "activation_claim": "none; symbol presence and options are not actual linked/loaded behavior",
    }


def build(output: Path, manifest_path: Path = MANIFEST, root: Path = ROOT) -> dict:
    contract = json.loads(manifest_path.read_text())
    # Validate every pack before writing anything; no shader archive is emitted.
    packs = [(p, read_pinned_pack(root / p["archive"], p)) for p in contract["packs"]]
    result = {
        "schema": "shaderlod.water-spectrum-source-audit.r3",
        "disposition": "no candidate in this bounded attempt; A9 remains pending",
        "candidate_artifact": None,
        "native_equivalent_artifact": None,
        "runtime_compile": "not attempted",
        "quality": "not evaluated",
        "performance": "not evaluated",
        "root_sole_gpu_client_owner": True,
        "script_sha256": sha256(Path(__file__).read_bytes()),
        "contract_sha256": sha256(manifest_path.read_bytes()),
        "numerical_witnesses": numerical_witnesses(),
        "packs": [],
    }
    for pack, members in packs:
        record = {k: pack[k] for k in ("pack", "version", "archive", "archive_sha256", "members")}
        record.update(source_inventory(members, pack["symbols"]))
        result["packs"].append(record)
        for name, data in members.items():
            if name.endswith((".glsl", ".fsh", ".vsh", ".properties", ".settings")):
                destination = output / "sources" / pack["pack"] / name
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(data)
    output.mkdir(parents=True, exist_ok=True)
    (output / "source-audit.json").write_text(json.dumps(result, indent=2) + "\n")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    args = parser.parse_args()
    audit = build(args.output)
    print(json.dumps({"report": str(args.output / "source-audit.json"),
                      "packs": len(audit["packs"]), "candidate": None,
                      "disposition": audit["disposition"]}))

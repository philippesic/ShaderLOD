#!/usr/bin/env python3
"""Build a default-off, quality-changing main-pass cumulus fine-noise cost control."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASE = Path("dev/upstream/photon_v1.3b.zip")
BASE_SHA256 = "120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead"
MANIFEST = Path("patches/photon-experiments/density-detail-probe.json")
EXPERIMENT = "photon-cumulus-fine-density-cost-control"
SWITCH = "SHADERLOD_DENSITY_DETAIL_COST_CONTROL"
CUMULUS = "shaders/include/sky/clouds/cumulus.glsl"
SETTINGS = "shaders/settings.glsl"
MEMBERS = (CUMULUS, SETTINGS)
ARMS = ("original", "disabled", "enabled")
METADATA = "shaderlod-density-detail-probe.json"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def patch_members(files: dict[str, bytes], manifest: dict, arm: str) -> dict[str, bytes]:
    """Validate and preview pinned source; this function writes no archive."""
    if arm not in ARMS:
        raise ValueError(f"Unknown control arm: {arm}")
    if (manifest.get("schema") != 1 or manifest.get("experiment") != EXPERIMENT
            or manifest.get("base_version") != "v1.3b"
            or manifest.get("base_sha256") != BASE_SHA256
            or manifest.get("switch") != SWITCH
            or manifest.get("diagnostic_fine_noise") != 0.5
            or manifest.get("quality_class") != "quality_changing_cost_control"):
        raise ValueError("Unexpected original-Photon density control manifest")
    entries = manifest["members"]
    if tuple(entry["path"] for entry in entries) != MEMBERS:
        raise ValueError("Unexpected density control member scope/order")
    if "LICENSE" not in files or sha256(files["LICENSE"]) != manifest["license_sha256"]:
        raise ValueError("Pinned Photon license missing or changed")
    result = files.copy()
    for entry in entries:
        name = entry["path"]
        if name not in files or sha256(files[name]) != entry["source_sha256"]:
            raise ValueError(f"Pinned Photon member missing or changed: {name}")
        if len(entry["replacements"]) != (2 if name == CUMULUS else 1):
            raise ValueError(f"Unexpected reviewed anchor count: {name}")
        source = files[name].decode("utf-8")
        for replacement in entry["replacements"]:
            before, after = replacement["before"], replacement["after"]
            if not before or source.count(before) != 1:
                raise ValueError(f"Density control anchor must appear exactly once: {name}")
            source = source.replace(before, after, 1)
        disabled = source.encode()
        if sha256(disabled) != entry["patched_sha256"]:
            raise ValueError(f"Density control patched member fingerprint changed: {name}")
        enabled = disabled
        if name == SETTINGS:
            declaration = f"//#define {SWITCH}\n".encode()
            if disabled.count(declaration) != 1:
                raise ValueError("Density control option must default off exactly once")
            enabled = disabled.replace(declaration, f"#define {SWITCH}\n".encode(), 1)
            if sha256(enabled) != entry["enabled_sha256"]:
                raise ValueError("Density control enabled settings fingerprint changed")
        result[name] = files[name] if arm == "original" else enabled if arm == "enabled" else disabled
    return result


def archive_members(base: Path) -> dict[str, bytes]:
    with zipfile.ZipFile(base) as archive:
        entries = archive.infolist()
        names = [entry.filename for entry in entries]
        if len(names) != len(set(names)):
            raise ValueError("Duplicate original Photon archive entry")
        for name in names:
            path = PurePosixPath(name)
            if path.is_absolute() or ".." in path.parts or "\\" in name:
                raise ValueError(f"Unsafe Photon archive entry: {name}")
        return {entry.filename: archive.read(entry) for entry in entries if not entry.is_dir()}


def build(output: Path | None = None, base: Path | None = None,
          arm: str = "disabled", root: Path = ROOT) -> dict:
    if arm not in ARMS:
        raise ValueError(f"Unknown control arm: {arm}")
    base = (base or root / BASE).resolve()
    output = (output or root / "build" / f"Photon-DensityDetailCostControl-{arm}.zip").resolve()
    if not output.is_relative_to((root / "build").resolve()) or output.suffix.lower() != ".zip":
        raise ValueError("Standalone cost-control output must be a ZIP under ignored build/")
    if output == base:
        raise ValueError("Output may not overwrite pinned original Photon")
    if sha256(base.read_bytes()) != BASE_SHA256:
        raise ValueError("Pinned original Photon archive SHA-256 mismatch")
    manifest_bytes = (root / MANIFEST).read_bytes()
    manifest = json.loads(manifest_bytes)
    original = archive_members(base)
    if METADATA in original:
        raise ValueError("Original archive unexpectedly contains control metadata")
    files = patch_members(original, manifest, arm)
    modified = sorted(name for name in original if files[name] != original[name])
    if set(files) != set(original) or modified != ([] if arm == "original" else sorted(MEMBERS)):
        raise ValueError("Density control archive modification scope mismatch")
    metadata = {
        "experiment": EXPERIMENT, "arm": arm, "base_version": "v1.3b",
        "base_sha256": BASE_SHA256, "manifest_sha256": sha256(manifest_bytes),
        "switch": SWITCH, "switch_enabled": arm == "enabled",
        "modified_upstream_files": modified,
        "member_sha256": {name: sha256(files[name]) for name in MEMBERS},
        "license_sha256": sha256(files["LICENSE"]),
        "unchanged_upstream_member_count": len(original) - len(modified),
        "consumed_core_fields": ["shaderlodBridgeVersion", "shaderlodEnabled"] if arm == "enabled" else [],
        "activation": "private option AND PROGRAM_DEFERRED1 AND bridge version 2 AND global enabled exactly 1",
        "program_scope": "PROGRAM_DEFERRED1 only; main-pass cumulus primary and secondary density evaluations",
        "diagnostic_fine_noise": 0.5,
        "quality_class": "quality_changing_cost_control",
        "source_policy": "Fine worley_1 texture replaced by declared 0.5 diagnostic; coarse noise, literal ray counts/extents/seeds/phase functions and reconstruction unchanged",
        "physical_work_caveat": "Density/occupancy and upstream early exits can change; not an isolated fetch budget or accepted optimization",
        "distance_units": "upstream atmosphere coordinates; diagnostic has no distance/footprint threshold",
        "transition": "uniform global control; intentional density change, no quality transition or calibration",
        "fallback": "literal original fine texture sample for private off, other programs, absent/unknown core or global off",
        "runtime_compilation": "unverified", "gpu_benefit": "unmeasured",
        "attribution": "Photon v1.3b by Benjamin Stott (SixthSurge); original LICENSE retained",
    }
    files[METADATA] = (json.dumps(metadata, indent=2, sort_keys=True) + "\n").encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output.parent, suffix=".zip", delete=False) as temporary:
        temporary_path = Path(temporary.name)
    try:
        with zipfile.ZipFile(temporary_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for name, data in sorted(files.items()):
                info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.create_system = 3
                info.external_attr = 0o100644 << 16
                archive.writestr(info, data, compresslevel=9)
        temporary_path.replace(output)
    finally:
        temporary_path.unlink(missing_ok=True)
    return metadata


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--arm", choices=ARMS, default="disabled")
    parser.add_argument("--base", type=Path, help="Exact original Photon v1.3b ZIP")
    parser.add_argument("--output", type=Path, help="ZIP under ignored build/")
    args = parser.parse_args()
    try:
        metadata = build(args.output, args.base, args.arm)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, f"Cannot assemble Photon density-detail cost control: {error}\n")
    print(json.dumps(metadata, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Assemble an isolated, default-off exact depth-reuse probe from original Photon."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASE = Path("dependencies/upstream/photon_v1.3b.zip")
BASE_SHA256 = "120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead"
MANIFEST = Path("patches/photon-experiments/ao-depth-reuse-probe.json")
EXPERIMENT = "photon-ao-consecutive-depth-reuse-probe"
SWITCH = "SHADERLOD_AO_DEPTH_REUSE_PROBE"
GTAO = "shaders/include/lighting/ao/gtao.glsl"
SETTINGS = "shaders/settings.glsl"
MEMBERS = (GTAO, SETTINGS)
ARMS = ("original", "disabled", "enabled")
METADATA = "shaderlod-ao-depth-reuse-probe.json"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def patch_members(files: dict[str, bytes], manifest: dict, arm: str) -> dict[str, bytes]:
    """Apply only reviewed source fingerprints and unique anchors; no assembly."""
    if arm not in ARMS:
        raise ValueError(f"Unknown probe arm: {arm}")
    if (manifest.get("schema") != 1 or manifest.get("experiment") != EXPERIMENT
            or manifest.get("base_version") != "v1.3b"
            or manifest.get("base_sha256") != BASE_SHA256
            or manifest.get("switch") != SWITCH):
        raise ValueError("Unexpected original-Photon AO reuse manifest")
    entries = manifest["members"]
    if tuple(entry["path"] for entry in entries) != MEMBERS:
        raise ValueError("Unexpected AO reuse source scope/order")
    if "LICENSE" not in files or sha256(files["LICENSE"]) != manifest["license_sha256"]:
        raise ValueError("Pinned Photon license missing or changed")
    result = files.copy()
    for entry in entries:
        name = entry["path"]
        if name not in files or sha256(files[name]) != entry["source_sha256"]:
            raise ValueError(f"Pinned Photon member missing or changed: {name}")
        expected_count = 3 if name == GTAO else 1
        if len(entry["replacements"]) != expected_count:
            raise ValueError(f"Unexpected reviewed anchor count: {name}")
        source = files[name].decode("utf-8")
        for replacement in entry["replacements"]:
            before, after = replacement["before"], replacement["after"]
            if not before or source.count(before) != 1:
                raise ValueError(f"AO reuse anchor must appear exactly once: {name}")
            source = source.replace(before, after, 1)
        disabled = source.encode()
        if sha256(disabled) != entry["patched_sha256"]:
            raise ValueError(f"AO reuse patched member fingerprint changed: {name}")
        enabled = disabled
        if name == SETTINGS:
            declaration = f"//#define {SWITCH}\n".encode()
            if disabled.count(declaration) != 1:
                raise ValueError("AO reuse option must default off exactly once")
            enabled = disabled.replace(declaration, f"#define {SWITCH}\n".encode(), 1)
            if sha256(enabled) != entry["enabled_sha256"]:
                raise ValueError("AO reuse enabled settings fingerprint changed")
        result[name] = files[name] if arm == "original" else enabled if arm == "enabled" else disabled
    if set(result) != set(files):
        raise ValueError("AO source patch may not add archive members")
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
    base = (base or root / BASE).resolve()
    output = (output or root / "build" / f"Photon-AODepthReuseProbe-{arm}.zip").resolve()
    if not output.is_relative_to((root / "build").resolve()) or output.suffix.lower() != ".zip":
        raise ValueError("Standalone probe output must be a ZIP under ignored build/")
    if output == base:
        raise ValueError("Output may not overwrite pinned original Photon")
    if sha256(base.read_bytes()) != BASE_SHA256:
        raise ValueError("Pinned original Photon archive SHA-256 mismatch")
    manifest_bytes = (root / MANIFEST).read_bytes()
    manifest = json.loads(manifest_bytes)
    original = archive_members(base)
    if METADATA in original:
        raise ValueError("Original archive unexpectedly contains probe metadata")
    files = patch_members(original, manifest, arm)
    modified = sorted(name for name in original if files[name] != original[name])
    if modified != ([] if arm == "original" else sorted(MEMBERS)):
        raise ValueError("AO reuse archive modification scope mismatch")
    metadata = {
        "experiment": EXPERIMENT, "arm": arm, "base_version": "v1.3b",
        "base_sha256": BASE_SHA256, "manifest_sha256": sha256(manifest_bytes),
        "switch": SWITCH, "switch_enabled": arm == "enabled",
        "modified_upstream_files": modified,
        "member_sha256": {name: sha256(files[name]) for name in MEMBERS},
        "license_sha256": sha256(files["LICENSE"]),
        "unchanged_upstream_member_count": len(original) - len(modified),
        "consumed_core_fields": ["shaderlodBridgeVersion", "shaderlodEnabled"] if arm == "enabled" else [],
        "activation": "compile option on AND bridge version 2 AND global enabled exactly 1",
        "distance_units": "unchanged upstream view-space camera-relative blocks",
        "quality_fallback": "literal original horizon kernel; all sample budgets unchanged",
        "transition": "exact reuse switches without distance bands; runtime quality parity unverified",
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
        parser.exit(1, f"Cannot assemble Photon AO depth-reuse probe: {error}\n")
    print(json.dumps(metadata, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()

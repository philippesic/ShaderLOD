#!/usr/bin/env python3
"""Build only the standalone pinned Photon composite1 cloud-consumer experiment."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = Path("patches/photon-experiments/cloud-consumer-guard.json")
MEMBER = "shaders/program/c1_blend_layers.fsh"
SWITCH = "PHOTON_CLOUD_CONSUMER_GUARD"
EXPERIMENT = "photon-cloud-consumer-guard"
ARMS = ("original", "disabled", "enabled")
BASE_SHA256 = "120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def patch_member(source: bytes, manifest: dict, arm: str) -> bytes:
    """Two reviewed anchors in one fixed member; never touch pack overlays."""
    if arm not in ARMS:
        raise ValueError(f"Unknown experiment arm: {arm}")
    if (manifest.get("schema") != 1 or manifest.get("experiment") != EXPERIMENT
            or manifest.get("member") != MEMBER or manifest.get("switch") != SWITCH
            or manifest.get("base_version") != "v1.3b"
            or manifest.get("base_sha256") != BASE_SHA256):
        raise ValueError("Unexpected cloud-consumer experiment manifest")
    if sha256(source) != manifest["source_sha256"]:
        raise ValueError(f"Pinned Photon member changed: {MEMBER}")
    replacements = manifest["replacements"]
    if len(replacements) != 2:
        raise ValueError("Expected exactly two composite1 anchors")
    # Validate the disabled and enabled sources even when building original.
    disabled = source.decode("utf-8")
    for replacement in replacements:
        before, after = replacement["before"], replacement["after"]
        if not before or disabled.count(before) != 1:
            raise ValueError("Composite1 anchor must appear exactly once")
        disabled = disabled.replace(before, after, 1)
    declaration = f"//#define {SWITCH}\n"
    if disabled.count(declaration) != 1:
        raise ValueError("Experiment switch must have one default-off declaration")
    enabled = disabled.replace(declaration, f"#define {SWITCH}\n", 1)
    variants = {"original": source, "disabled": disabled.encode(), "enabled": enabled.encode()}
    for name, data in variants.items():
        if sha256(data) != manifest["member_sha256_by_arm"][name]:
            raise ValueError(f"Patched composite1 hash mismatch: {name}")
    return variants[arm]


def archive_members(base: Path) -> dict[str, bytes]:
    with zipfile.ZipFile(base) as archive:
        entries = archive.infolist()
        names = [entry.filename for entry in entries]
        if len(names) != len(set(names)):
            raise ValueError("Duplicate Photon archive entry")
        for name in names:
            path = PurePosixPath(name)
            if path.is_absolute() or ".." in path.parts or "\\" in name:
                raise ValueError(f"Unsafe Photon archive entry: {name}")
        return {entry.filename: archive.read(entry) for entry in entries if not entry.is_dir()}


def build(output: Path | None = None, base: Path | None = None,
          arm: str = "disabled", root: Path = ROOT) -> dict:
    if arm not in ARMS:
        raise ValueError(f"Unknown experiment arm: {arm}")
    base = (base or root / "dependencies/upstream/photon_v1.3b.zip").resolve()
    output = (output or root / "build" / f"Photon-CloudConsumerGuard-{arm}.zip").resolve()
    build_root = (root / "build").resolve()
    if not output.is_relative_to(build_root) or output.suffix.lower() != ".zip":
        raise ValueError("Standalone experiment output must be a ZIP under ignored build/")
    if output == base:
        raise ValueError("Output may not overwrite pinned upstream archive")
    if sha256(base.read_bytes()) != BASE_SHA256:
        raise ValueError("Pinned Photon archive SHA-256 mismatch")
    manifest_bytes = (root / MANIFEST).read_bytes()
    manifest = json.loads(manifest_bytes)
    files = archive_members(base)
    if "LICENSE" not in files or sha256(files["LICENSE"]) != manifest["license_sha256"]:
        raise ValueError("Pinned Photon license missing or changed")
    if MEMBER not in files:
        raise ValueError(f"Missing Photon member: {MEMBER}")
    original = files[MEMBER]
    files[MEMBER] = patch_member(original, manifest, arm)
    metadata = {
        "experiment": EXPERIMENT,
        "arm": arm,
        "base_version": "v1.3b",
        "base_sha256": BASE_SHA256,
        "manifest_sha256": sha256(manifest_bytes),
        "switch": SWITCH,
        "switch_enabled": arm == "enabled",
        "member_sha256": sha256(files[MEMBER]),
        "modified_upstream_files": [] if files[MEMBER] == original else [MEMBER],
        "license_sha256": sha256(files["LICENSE"]),
        "distance_policy": "unchanged; consumer dispatch only",
        "runtime_compilation": "unverified",
    }
    files["shaderlod-cloud-consumer-experiment.json"] = (
        json.dumps(metadata, indent=2, sort_keys=True) + "\n").encode()
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
    parser.add_argument("--base", type=Path, help="Exact pinned Photon original archive")
    parser.add_argument("--output", type=Path, help="ZIP under ignored build/; defaults to a standalone arm name")
    args = parser.parse_args()
    try:
        metadata = build(args.output, args.base, args.arm)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, f"Cannot assemble Photon cloud-consumer experiment: {error}\n")
    print(json.dumps(metadata, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()

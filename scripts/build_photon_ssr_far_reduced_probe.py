#!/usr/bin/env python3
"""Build a default-off, pinned B15 reduced far-SSR quality/cost probe."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASE = Path("build/photon-lighting-tier-no-atmosphere.zip")
BASE_SHA256 = "b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c"
MANIFEST = Path("patches/photon-experiments/ssr-far-reduced-probe.json")
EXPERIMENT = "photon-ssr-far-reduced-trace-probe"
SWITCH = "PHOTON_SSR_FAR_REDUCED_TRACE_PROBE"
SPECULAR = "shaders/include/lighting/specular_lighting.glsl"
SETTINGS = "shaders/settings.glsl"
MEMBERS = (SPECULAR, SETTINGS)
ARMS = ("original", "disabled", "enabled")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def patch_members(files: dict[str, bytes], manifest: dict, arm: str) -> dict[str, bytes]:
    """Two fixed members, one literal anchor each; no overlay/shared-helper edits."""
    if arm not in ARMS:
        raise ValueError(f"Unknown diagnostic arm: {arm}")
    if (manifest.get("schema") != 1 or manifest.get("experiment") != EXPERIMENT
            or manifest.get("base_version") != "v1.3b"
            or manifest.get("base_sha256") != BASE_SHA256
            or manifest.get("switch") != SWITCH):
        raise ValueError("Unexpected B15 far-SSR diagnostic manifest")
    entries = manifest["members"]
    if tuple(entry["path"] for entry in entries) != MEMBERS:
        raise ValueError("Unexpected far-SSR diagnostic member scope/order")
    result = files.copy()
    for entry in entries:
        path = entry["path"]
        if path not in files or sha256(files[path]) != entry["source_sha256"]:
            raise ValueError(f"Pinned B15 member missing or changed: {path}")
        if len(entry["replacements"]) != 1:
            raise ValueError(f"Expected one reviewed anchor: {path}")
        replacement = entry["replacements"][0]
        before, after = replacement["before"], replacement["after"]
        source = files[path].decode("utf-8")
        if not before or source.count(before) != 1:
            raise ValueError(f"Far-SSR diagnostic anchor must appear exactly once: {path}")
        disabled = source.replace(before, after, 1).encode()
        if sha256(disabled) != entry["patched_sha256"]:
            raise ValueError(f"Diagnostic member hash mismatch: {path}")
        enabled = disabled
        if path == SETTINGS:
            declaration = f"//#define {SWITCH}\n"
            if disabled.count(declaration.encode()) != 1:
                raise ValueError("Diagnostic must have one default-off declaration")
            enabled = disabled.replace(declaration.encode(), f"#define {SWITCH}\n".encode(), 1)
            if sha256(enabled) != entry["enabled_sha256"]:
                raise ValueError("Enabled far-SSR settings hash mismatch")
        result[path] = files[path] if arm == "original" else enabled if arm == "enabled" else disabled
    return result


def archive_members(base: Path) -> dict[str, bytes]:
    with zipfile.ZipFile(base) as archive:
        entries = archive.infolist()
        names = [entry.filename for entry in entries]
        if len(names) != len(set(names)):
            raise ValueError("Duplicate B15 archive entry")
        for name in names:
            path = PurePosixPath(name)
            if path.is_absolute() or ".." in path.parts or "\\" in name:
                raise ValueError(f"Unsafe B15 archive entry: {name}")
        return {entry.filename: archive.read(entry) for entry in entries if not entry.is_dir()}


def build(output: Path | None = None, base: Path | None = None,
          arm: str = "disabled", root: Path = ROOT) -> dict:
    if arm not in ARMS:
        raise ValueError(f"Unknown diagnostic arm: {arm}")
    base = (base or root / BASE).resolve()
    output = (output or root / "build" / f"Photon-SSRFarReducedTraceProbe-{arm}.zip").resolve()
    if not output.is_relative_to((root / "build").resolve()) or output.suffix.lower() != ".zip":
        raise ValueError("Standalone diagnostic output must be a ZIP under ignored build/")
    if output == base:
        raise ValueError("Output may not overwrite immutable B15 archive")
    if sha256(base.read_bytes()) != BASE_SHA256:
        raise ValueError("Pinned B15 archive SHA-256 mismatch")
    manifest_bytes = (root / MANIFEST).read_bytes()
    manifest = json.loads(manifest_bytes)
    original = archive_members(base)
    for name, key in (("LICENSE", "license_sha256"), ("shaderlod-build.json", "baseline_metadata_sha256")):
        if name not in original or sha256(original[name]) != manifest[key]:
            raise ValueError(f"B15 license/metadata missing or changed: {name}")
    files = patch_members(original, manifest, arm)
    metadata = {
        "experiment": EXPERIMENT,
        "arm": arm,
        "baseline": manifest["baseline"],
        "base_sha256": BASE_SHA256,
        "base_version": "v1.3b",
        "manifest_sha256": sha256(manifest_bytes),
        "baseline_metadata_sha256": sha256(files["shaderlod-build.json"]),
        "license_sha256": sha256(files["LICENSE"]),
        "switch": SWITCH,
        "switch_enabled": arm == "enabled",
        "member_sha256": {name: sha256(files[name]) for name in MEMBERS},
        "modified_baseline_files": sorted(name for name in original if files[name] != original[name]),
        "policy": manifest["policy"],
        "runtime_compilation": "unverified",
        "performance_intent": "Reduced far tracing vs full-trace control; actual quality and GPU benefit unverified",
    }
    files["shaderlod-ssr-far-reduced-probe.json"] = (
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
    parser.add_argument("--base", type=Path, help="Exact immutable B15 archive")
    parser.add_argument("--output", type=Path, help="Standalone ZIP under ignored build/")
    args = parser.parse_args()
    try:
        metadata = build(args.output, args.base, args.arm)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, f"Cannot assemble Photon far-SSR diagnostic: {error}\n")
    print(json.dumps(metadata, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()

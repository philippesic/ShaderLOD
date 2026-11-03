#!/usr/bin/env python3
"""Build a pinned default-off reflection depth reuse candidate; no runtime claim."""
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
MANIFEST = Path("patches/photon-experiments/reflection-lod.json")
EXPERIMENT = "photon-reflection-footprint-refinement-lod"
SWITCH = "SHADERLOD_REFLECTION_DEPTH_CACHE"
SPECULAR = "shaders/include/lighting/specular_lighting.glsl"
SETTINGS = "shaders/settings.glsl"
MEMBERS = (SPECULAR, SETTINGS)
ARMS = ("original", "disabled", "enabled")
METADATA = "shaderlod-reflection-lod.json"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def patch_members(files: dict[str, bytes], manifest: dict, arm: str) -> dict[str, bytes]:
    """Validate and preview pinned source; this function writes no archive."""
    if arm not in ARMS:
        raise ValueError(f"Unknown control arm: {arm}")
    if (manifest.get("schema") != 1 or manifest.get("experiment") != EXPERIMENT
            or manifest.get("base_version") != "v1.3b"
            or manifest.get("base_sha256") != BASE_SHA256
            or manifest.get("refinement_view_error_blocks") != 0.05
            or manifest.get("refinement_error_pixels") != 0.25
            or manifest.get("refinement_depth_tolerance_fraction") != 0.0625
            or manifest.get("switch") != SWITCH
            or manifest.get("quality_class") != "bounded_projected_refinement_lod_candidate"
            or manifest.get("iris_option_discovery", {}).get("guard_contract")
            != "standalone #ifdef private switch; helper guard uses #if !defined"):
        raise ValueError("Unexpected original-Photon reflection control manifest")
    entries = manifest["members"]
    if tuple(entry["path"] for entry in entries) != MEMBERS:
        raise ValueError("Unexpected reflection control member scope/order")
    if "LICENSE" not in files or sha256(files["LICENSE"]) != manifest["license_sha256"]:
        raise ValueError("Pinned Photon license missing or changed")
    result = files.copy()
    derived = manifest["derived_source"]
    if derived["path"] != "shaders/include/misc/raytracer.glsl" or sha256(files[derived["path"]]) != derived["sha256"]:
        raise ValueError("Pinned upstream raytracer changed")
    for entry in entries:
        name = entry["path"]
        if name not in files or sha256(files[name]) != entry["source_sha256"]:
            raise ValueError(f"Pinned Photon member missing or changed: {name}")
        if len(entry["replacements"]) != (2 if name == SPECULAR else 1):
            raise ValueError(f"Unexpected reviewed anchor count: {name}")
        source = files[name].decode("utf-8")
        for replacement in entry["replacements"]:
            before, after = replacement["before"], replacement["after"]
            if not before or source.count(before) != 1:
                raise ValueError(f"Reflection control anchor must appear exactly once: {name}")
            source = source.replace(before, after, 1)
        disabled = source.encode()
        if sha256(disabled) != entry["patched_sha256"]:
            raise ValueError(f"Reflection control patched member fingerprint changed: {name}")
        enabled = disabled
        if name == SETTINGS:
            declaration = f"//#define {SWITCH}\n".encode()
            if disabled.count(declaration) != 1:
                raise ValueError("Reflection control option must default off exactly once")
            enabled = disabled.replace(declaration, f"#define {SWITCH}\n".encode(), 1)
            if sha256(enabled) != entry["enabled_sha256"]:
                raise ValueError("Reflection control enabled settings fingerprint changed")
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
    output = (output or root / "build" / f"Photon-ReflectionFootprintLOD-{arm}.zip").resolve()
    if not output.is_relative_to((root / "build").resolve()) or output.suffix.lower() != ".zip":
        raise ValueError("Standalone reflection candidate output must be a ZIP under ignored build/")
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
    added = manifest["added_members"]
    if len(added) != 1 or added[0]["path"] != "shaders/include/shaderlod/reflection_depth_cache.glsl" or added[0]["source_path"] != "shaderpacks/photon-reflection-lod/shaders/include/shaderlod/reflection_depth_cache.glsl":
        raise ValueError("Unexpected reflection helper scope")
    helper = (root / added[0]["source_path"]).read_bytes()
    if sha256(helper) != added[0]["sha256"] or added[0]["path"] in original:
        raise ValueError("Reflection helper missing, changed or colliding")
    modified = sorted(name for name in original if files[name] != original[name])
    if set(files) != set(original) or modified != ([] if arm == "original" else sorted(MEMBERS)):
        raise ValueError("Reflection archive modification scope mismatch")
    if arm != "original":
        files[added[0]["path"]] = helper
    metadata = {
        "experiment": EXPERIMENT, "arm": arm, "base_version": "v1.3b",
        "base_sha256": BASE_SHA256, "manifest_sha256": sha256(manifest_bytes),
        "switch": SWITCH, "switch_enabled": arm == "enabled",
        "modified_upstream_files": modified,
        "member_sha256": {name: sha256(files[name]) for name in MEMBERS},
        "license_sha256": sha256(files["LICENSE"]),
        "unchanged_upstream_member_count": len(original) - len(modified),
        "added_member_sha256": {added[0]["path"]: sha256(helper)} if arm != "original" else {},
        "consumed_core_fields": manifest["consumed_core_fields"] if arm == "enabled" else [],
        "activation": "private option; core version 2/global exactly 1; finite valid distance policy; original near fallback",
        "program_scope": "specular reflection calls only; original raytracer and AO/SSGI callers unchanged",
        "quality_class": "bounded_projected_refinement_lod_candidate",
        "source_policy": "Full intersection positions/budgets/depth tolerance preserved; far refinements may stop when remaining envelope is one valid fetched texel and displacement <=0.25 texel plus <=1/16 hit depth tolerance; uniform depth-hit predicate and <=0.05-block inverse-projected hit displacement; exact depth reuse; original radiance sampling/ray weights",
        "physical_work_caveat": "View-space safety projection math adds overhead; fetches skipped only before actual texelFetch on same integer key; branches/registers may outweigh saves; compiler and hardware unverified",
        "distance_units": "camera-relative view-space Euclidean Minecraft blocks; footprint is exact integer depth texel identity",
        "transition": "smoothstep(near+width/2, far+width/2, distance) sets tolerance; refinement tolerance grows from zero to 0.25 depth texel",
        "fallback": "literal original trace for private/global off, absent/unknown/invalid bridge or policy, full near, unsupported counts",
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
        parser.exit(1, f"Cannot assemble Photon reflection depth reuse: {error}\n")
    print(json.dumps(metadata, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()

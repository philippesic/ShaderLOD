#!/usr/bin/env python3
"""Build the default-off B15 literal-secondary probe for the reviewed private profile."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = Path("patches/photon-experiments/cloud-literal-probe.json")
BASE = Path("build/photon-lighting-tier-no-atmosphere.zip")
BASE_SHA256 = "b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c"
EXPERIMENT = "photon-cloud-literal-secondary-probe"
SWITCH = "PHOTON_CLOUD_LITERAL_SECONDARY_PROBE"
SETTINGS = "shaders/settings.glsl"
MEMBERS = tuple(f"shaders/include/sky/clouds/{layer}.glsl"
                for layer in ("cumulus", "altocumulus", "cumulus_congestus")) + (SETTINGS,)
ARMS = ("original", "disabled", "enabled")
SAMPLER_EVIDENCE = Path("dev/logs/cloud-sampler-state-diagnostic-r3.json")
SAMPLER_EVIDENCE_SHA256 = "5dbc7edf7fa5b11cebd2a4ac948a2c8e9553ddf467f5141831c29e162e283e67"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def preview_design(files: dict[str, bytes], manifest: dict, enabled: bool = False) -> dict[str, bytes]:
    """Verify fixed, reviewed anchors and every proposed source fingerprint."""
    if (manifest.get("schema") != 1 or manifest.get("experiment") != EXPERIMENT
            or manifest.get("base_version") != "v1.3b"
            or manifest.get("base_sha256") != BASE_SHA256
            or manifest.get("switch") != SWITCH):
        raise ValueError("Unexpected B15 cloud-literal probe manifest")
    entries = manifest["members"]
    if tuple(entry["path"] for entry in entries) != MEMBERS:
        raise ValueError("Unexpected B15 probe member scope/order")
    result = files.copy()
    for entry in entries:
        path = entry["path"]
        if path not in files or sha256(files[path]) != entry["source_sha256"]:
            raise ValueError(f"Pinned B15 member missing or changed: {path}")
        expected_anchors = 1 if path == SETTINGS else 3
        if len(entry["replacements"]) != expected_anchors:
            raise ValueError(f"Unexpected number of reviewed anchors: {path}")
        patched = files[path].decode("utf-8")
        for replacement in entry["replacements"]:
            before, after = replacement["before"], replacement["after"]
            if not before or patched.count(before) != 1:
                raise ValueError(f"Probe anchor must appear exactly once: {path}")
            patched = patched.replace(before, after, 1)
        disabled = patched.encode()
        if sha256(disabled) != entry["patched_sha256"]:
            raise ValueError(f"Probe member hash mismatch: {path}")
        enabled_source = disabled
        if path == SETTINGS:
            declaration = f"//#define {SWITCH}\n"
            if patched.count(declaration) != 1:
                raise ValueError("Probe must have one default-off option declaration")
            enabled_source = patched.replace(declaration, f"#define {SWITCH}\n", 1).encode()
            if sha256(enabled_source) != entry["enabled_sha256"]:
                raise ValueError("Enabled probe settings hash mismatch")
        # Validate both forms before returning either arm.
        result[path] = enabled_source if enabled else disabled
    return result


def patch_members(files: dict[str, bytes], manifest: dict, arm: str) -> dict[str, bytes]:
    if arm not in ARMS:
        raise ValueError(f"Unknown probe arm: {arm}")
    if arm == "enabled" and manifest.get("sampling_safety", {}).get("status") != "controlled_profile_only":
        raise ValueError("Enabled literal probe lacks reviewed private-profile qualification")
    patched = preview_design(files, manifest, enabled=arm == "enabled")
    return files.copy() if arm == "original" else patched


def validate_sampler_report(report: dict) -> dict:
    """Check measured constraints; this is not universal derivative/parity proof."""
    if (report.get("schema") != 1 or report.get("probe") != "shaderlod.dev.cloudSamplerProbe"
            or report.get("hook") != "net.irisshaders.iris.pathways.FullScreenQuadRenderer.renderQuad()V HEAD"
            or report.get("pinned_iris") != "1.8.12+1.21.1-neoforge"
            or report.get("gl_renderer") != "Apple M3 Max"
            or report.get("gl_version") != "4.1 Metal - 91.7"
            or report.get("anisotropy_supported") is not True
            or report.get("sampler_objects_supported") is not True):
        raise ValueError("Sampler report does not match the reviewed runtime")
    programs = report.get("programs", [])
    if not programs or any(program.get("active_texture_before") is None
                           or program.get("active_texture_before") != program.get("active_texture_after")
                           for program in programs):
        raise ValueError("Sampler report lacks restored active-texture state")
    # The broad probe flag also includes an atmosphere-LUT-only program. Select
    # the complete measured input set; this does not assign shader-stage names.
    inputs = {"noisetex", "colortex8", "customtex0", "customtex1", "customtex2"}
    candidates = [program for program in programs if program.get("cloud_worley_sampler_candidate") is True
                  and inputs.issubset(program.get("samplers", {}))]
    if len(candidates) != 2 or len({program.get("current_program") for program in candidates}) != 2:
        raise ValueError("Expected two distinct measured cloud sampler candidates")
    base_expected = {"min_filter": 9729, "mag_filter": 9729, "lod_bias": 0.0,
                     "max_anisotropy": 1.0, "base_level": 0}
    for program in candidates:
        if program.get("linked") is not True or program.get("uniform_scan_truncated") is not False:
            raise ValueError("Unknown/unlinked/truncated cloud sampler candidate")
        for name in ("noisetex", "colortex8", "customtex0", "customtex1", "customtex2"):
            sampler = program.get("samplers", {}).get(name, {})
            volume = name.startswith("customtex")
            if (sampler.get("active_uniform_type") != (35679 if volume else 35678)
                    or sampler.get("texture_target") != (32879 if volume else 3553)
                    or not isinstance(sampler.get("bound_texture"), int) or sampler["bound_texture"] <= 0
                    or sampler.get("bound_sampler") != 0
                    or sampler.get("effective_parameter_source") != "texture; sampler object zero"):
                raise ValueError(f"Unknown or unexpected bound sampler: {name}")
            expected = base_expected | ({"min_lod": -1000.0, "max_lod": 1000.0, "max_level": 1000}
                                        if name == "colortex8"
                                        else {"min_lod": 0.0, "max_lod": 0.0, "max_level": 0})
            for parameters in (sampler.get("texture_parameters", {}), sampler.get("effective_sampling_parameters", {})):
                if parameters.get("status") != "queried_without_gl_error_consumption" or any(
                        parameters.get(key) != value for key, value in expected.items()):
                    raise ValueError(f"Unreviewed filter/LOD/anisotropy constraint: {name}")
    return {"candidate_program_ids": [program["current_program"] for program in candidates],
            "scope": "Sampler constraints only; no verified hash/resolution/profile/stage; no derivative/parity guarantee"}


def load_sampler_evidence(path: Path) -> dict:
    source = path.read_bytes()
    actual_hash = sha256(source)
    if actual_hash != SAMPLER_EVIDENCE_SHA256:
        raise ValueError("Sampler evidence changed; a new report requires source review")
    summary = validate_sampler_report(json.loads(source))
    return summary | {"report_sha256": actual_hash}


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
          arm: str = "disabled", root: Path = ROOT, sampler_evidence: Path | None = None) -> dict:
    if arm not in ARMS:
        raise ValueError(f"Unknown probe arm: {arm}")
    evidence = load_sampler_evidence(sampler_evidence or root / SAMPLER_EVIDENCE) if arm == "enabled" else None
    base = (base or root / BASE).resolve()
    output = (output or root / "build" / f"Photon-CloudLiteralSecondaryProbe-{arm}.zip").resolve()
    if not output.is_relative_to((root / "build").resolve()) or output.suffix.lower() != ".zip":
        raise ValueError("Standalone probe output must be a ZIP under ignored build/")
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
    modified = sorted(name for name in original if files[name] != original[name])
    metadata = {
        "experiment": EXPERIMENT,
        "arm": arm,
        "baseline": manifest["baseline"],
        "base_sha256": BASE_SHA256,
        "base_version": "v1.3b",
        "baseline_metadata_sha256": sha256(files["shaderlod-build.json"]),
        "manifest_sha256": sha256(manifest_bytes),
        "switch": SWITCH,
        "switch_enabled": arm == "enabled",
        "design_only": False,
        "sampling_safety": manifest["sampling_safety"],
        "validated_sampler_evidence": evidence,
        "modified_baseline_files": modified,
        "member_sha256": {name: sha256(files[name]) for name in MEMBERS},
        "license_sha256": sha256(files["LICENSE"]),
        "policy": "unchanged B15 budgets/quality/jitter; literal light2/ambient1 dispatch at existing occupied samples",
        "runtime_compilation": "unverified",
    }
    files["shaderlod-cloud-literal-probe.json"] = (
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
    parser.add_argument("--sampler-evidence", type=Path, help="Reviewed native r3 sampler report (same exact hash); required for enabled assembly")
    args = parser.parse_args()
    try:
        metadata = build(args.output, args.base, args.arm, sampler_evidence=args.sampler_evidence)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, f"Cannot assemble Photon cloud-literal probe: {error}\n")
    print(json.dumps(metadata, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()

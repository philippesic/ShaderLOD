#!/usr/bin/env python3
"""Build a default-off private Miniature seven-field core consumer with exact qualification."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import tempfile
import zipfile

from build_miniature_bridge_probe import read_archive
from build_miniature_refinement_probe import validate_qualification

ROOT = Path(__file__).resolve().parents[1]


def apply_patches(files: dict[str, bytes], manifest: dict) -> dict[str, bytes]:
    if manifest.get("schema") != 1 or manifest.get("kind") != "miniature-private-core-configuration-probe":
        raise ValueError("Unsupported Miniature core configuration manifest")
    for name, expected in manifest["source_sha256"].items():
        if name not in files or hashlib.sha256(files[name]).hexdigest() != expected:
            raise ValueError(f"Pinned refinement source changed: {name}")
    result = files.copy()
    for patch in manifest["patches"]:
        name = patch["path"]
        if name not in manifest["source_sha256"]:
            raise ValueError(f"Unpinned refinement patch: {name}")
        source = result[name].decode()
        if not patch["before"] or source.count(patch["before"]) != 1:
            raise ValueError(f"Refinement anchor must appear exactly once: {name}")
        result[name] = source.replace(patch["before"], patch["after"], 1).encode()
    return result


def build(output: Path, base: Path | None = None, root: Path = ROOT,
          sampler_evidence: Path | None = None, workload_evidence: Path | None = None) -> dict:
    manifest_path = root / "patches/miniature/2.19-core-config-probe.json"
    manifest_bytes = manifest_path.read_bytes()
    manifest = json.loads(manifest_bytes)
    q = manifest["qualification"]
    sampler_evidence = sampler_evidence or root / q["sampler_path"]
    workload_evidence = workload_evidence or root / q["workload_path"]
    for path, expected in ((sampler_evidence, q["sampler_sha256"]), (workload_evidence, q["workload_sha256"])):
        if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
            raise ValueError(f"Pinned qualification evidence changed: {path.name}")
    validate_qualification(json.loads(sampler_evidence.read_bytes()), json.loads(workload_evidence.read_bytes()), manifest)
    iris = root / q["iris_path"]
    if hashlib.sha256(iris.read_bytes()).hexdigest() != q["iris_sha256"]:
        raise ValueError("Pinned Iris translator artifact changed")
    with zipfile.ZipFile(iris) as jar:
        for name, expected in q["translator_class_sha256"].items():
            if hashlib.sha256(jar.read(name)).hexdigest() != expected:
                raise ValueError(f"Inspected Iris translator class changed: {name}")
    candidate = next(item for item in json.loads((root / "dev/shader-candidates.lock.json").read_text())["candidates"]
                     if item["modrinth_project"] == "miniature-shader")
    release = candidate["release"]
    if release["version"] != manifest["base_version"]:
        raise ValueError("Refinement manifest disagrees with release version")
    base = base or root / "dev/upstream/other-pack-research" / release["filename"]
    source = base.read_bytes()
    for algorithm, expected in manifest["archive_hashes"].items():
        if release[algorithm] != expected or hashlib.new(algorithm, source).hexdigest() != expected:
            raise ValueError(f"Pinned Miniature release {algorithm} mismatch")
    original = read_archive(base)
    files = apply_patches(original, manifest)
    overlay = root / "shaderpacks/miniature-core-config-probe"
    added = {p.relative_to(overlay).as_posix(): p.read_bytes() for p in sorted(overlay.rglob("*")) if p.is_file()}
    if {n: hashlib.sha256(b).hexdigest() for n, b in added.items()} != manifest["overlay_sha256"]:
        raise ValueError("Pinned refinement overlay changed")
    for name, content in added.items():
        if name in files:
            raise ValueError(f"Refinement overlay collision: {name}")
        files[name] = content
    license_bytes = (root / "patches/miniature/LICENSE.Miniature").read_bytes()
    if hashlib.sha256(license_bytes).hexdigest() != manifest["restored_license_sha256"]:
        raise ValueError("Restored Miniature MIT license changed")
    modified = sorted(n for n in original if original[n] != files[n])
    metadata = {
        "integration": "Miniature private existing-core configuration probe",
        "archive_hashes": manifest["archive_hashes"], "manifest_sha256": hashlib.sha256(manifest_bytes).hexdigest(),
        "modified_members": modified,
        "modified_member_sha256": {n: hashlib.sha256(files[n]).hexdigest() for n in modified},
        "overlay_sha256": manifest["overlay_sha256"], "qualification": q,
        "default_mode": 0, "default_debug_allow": 0,
        "modes": {"0": "original", "1": "full4 explicit control", "2": "fixed4 transition quality", "3": "bounded needed chain"},
        "declared_core_fields": manifest["declared_core_fields"], "preset_budgets": manifest["preset_budgets"],
        "validation": {"source_qualified": True, "derivative_compiled": False, "derivative_rendered": False,
                       "near_pixel_parity": False, "quality_accepted": False, "gpu_measured": False,
                       "all_seven_fields_verified": False, "working_refinement_counts_verified": False},
    }
    additions = {"LICENSE.Miniature": license_bytes,
                 "SHADERLOD_MINIATURE_CORE_CONFIGURATION.md": (root / "docs/MINIATURE_CORE_CONFIGURATION_EVIDENCE.md").read_bytes(),
                 "shaderlod-core-configuration-source.json": manifest_bytes,
                 "shaderlod-core-configuration-build.json": (json.dumps(metadata, indent=2, sort_keys=True) + "\n").encode()}
    for name, content in additions.items():
        if name in files:
            raise ValueError(f"Refinement output collision: {name}")
        files[name] = content
    output = output.resolve()
    if output == base.resolve():
        raise ValueError("Output may not overwrite pinned release")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output.parent, suffix=".zip", delete=False) as temp:
        temporary_path = Path(temp.name)
    try:
        with zipfile.ZipFile(temporary_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for name in sorted(files):
                info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.create_system = 3
                info.external_attr = 0o100644 << 16
                archive.writestr(info, files[name], compresslevel=9)
        temporary_path.replace(output)
    finally:
        temporary_path.unlink(missing_ok=True)
    return metadata


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", type=Path)
    parser.add_argument("--sampler-evidence", type=Path)
    parser.add_argument("--workload-evidence", type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "build/Miniature-ShaderLOD-CoreConfigProbe-2.19.zip")
    args = parser.parse_args()
    try:
        build(args.output, args.base, sampler_evidence=args.sampler_evidence, workload_evidence=args.workload_evidence)
    except (OSError, ValueError, KeyError, StopIteration, zipfile.BadZipFile) as error:
        parser.exit(1, f"Cannot assemble qualified core configuration probe: {error}\n")
    print(f"Built {args.output}; mode0 original, derivative rendering/quality/GPU unverified")


if __name__ == "__main__":
    main()

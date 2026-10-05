#!/usr/bin/env python3
"""Build one full-reflection MakeUp source probe from the exact frozen derivative."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import tempfile
import zipfile

from build_makeup_adapter import read_archive

ROOT = Path(__file__).resolve().parents[1]


def apply_probe(files: dict[str, bytes], manifest: dict, probe: str) -> dict[str, bytes]:
    if manifest.get("schema") != 1 or manifest.get("kind") != "makeup-source-isolation":
        raise ValueError("Unsupported MakeUp source probe manifest")
    if probe not in manifest["probes"]:
        raise ValueError(f"Unknown source probe: {probe}")
    for name, expected in manifest["source_sha256"].items():
        if name not in files or hashlib.sha256(files[name]).hexdigest() != expected:
            raise ValueError(f"Pinned derivative member changed: {name}")
    result = files.copy()
    for patch in manifest["probes"][probe]["patches"]:
        name = patch["path"]
        if name not in manifest["source_sha256"]:
            raise ValueError(f"Unpinned probe member: {name}")
        source = result[name].decode("utf-8")
        if not patch["before"] or source.count(patch["before"]) != 1:
            raise ValueError(f"Probe anchor must appear exactly once: {name}")
        result[name] = source.replace(patch["before"], patch["after"], 1).encode()
    return result


def build(output: Path, probe: str, base: Path | None = None, root: Path = ROOT) -> dict:
    manifest_path = root / "patches/makeup/probes.json"
    manifest_bytes = manifest_path.read_bytes()
    manifest = json.loads(manifest_bytes)
    base = base or root / "build/MakeUp-ShaderLOD-Experimental-9.5g.zip"
    if hashlib.sha256(base.read_bytes()).hexdigest() != manifest["base_sha256"]:
        raise ValueError("Frozen MakeUp derivative archive hash mismatch")
    original = read_archive(base)
    for name in ("LICENSE", "LICENSE.GPL-3.0", "SHADERLOD_EXPERIMENT.md", "shaderlod-source-patch.json", "shaderlod-build.json"):
        if name not in original:
            raise ValueError(f"Derivative source/attribution member missing: {name}")
    files = apply_probe(original, manifest, probe)
    modified = sorted(name for name in original if files[name] != original[name])
    metadata = {
        "probe": probe,
        "base_sha256": manifest["base_sha256"],
        "probe_manifest_sha256": hashlib.sha256(manifest_bytes).hexdigest(),
        "modified_shader_members": modified,
        "modified_member_sha256": {name: hashlib.sha256(files[name]).hexdigest() for name in modified},
        "behavior": manifest["probes"][probe]["behavior"],
        "requires_options": manifest["probes"][probe]["requires_options"],
        "prior_build_metadata_is_base_provenance": True,
        "validation": {"glsl_compiled": False, "iris_rendered": False, "gpu_measured": False},
    }
    notice = (
        f"# MakeUp ShaderLOD diagnostic: {probe}\n\n"
        + manifest["license_notice"] + "\n\n"
        + metadata["behavior"] + ". This diagnostic overrides the active fade/skip behavior\n"
        "described by the retained base SHADERLOD_EXPERIMENT.md. Full SSR weight and\n"
        "the original ten-iteration coverage are retained at every distance.\n"
        "Option 0 retains the original off code. Use option 1 with REFLECTION_SLIDER=2.\n"
        "No Java/common protocol connection. No support, accepted-quality or speedup claim.\n"
        "The classifier-full decision may be removed by the shader compiler.\n\n"
        "shaderlod-probe.json records this artifact's source changes. The retained\n"
        "shaderlod-build.json describes the frozen input artifact, not final probe hashes.\n"
        "Full editable source, original licenses and exact shaderlod-probe-source.json\n"
        "are included; build_makeup_probe.py deterministically reproduces this probe.\n"
    )
    additions = {
        "SHADERLOD_PROBE.md": notice.encode(),
        "shaderlod-probe.json": (json.dumps(metadata, indent=2, sort_keys=True) + "\n").encode(),
        "shaderlod-probe-source.json": manifest_bytes,
    }
    for name, content in additions.items():
        if name in files:
            raise ValueError(f"Probe output member collision: {name}")
        files[name] = content
    output = output.resolve()
    if output == base.resolve():
        raise ValueError("Probe output may not overwrite frozen derivative")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output.parent, suffix=".zip", delete=False) as temporary:
        temporary_path = Path(temporary.name)
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
    parser.add_argument("--probe", required=True, choices=("duplicate-implicit", "duplicate-explicit", "classifier-full"))
    parser.add_argument("--base", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    output = args.output or ROOT / f"build/MakeUp-ShaderLOD-Probe-{args.probe}-9.5g.zip"
    try:
        build(output, args.probe, args.base)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, f"Cannot assemble source probe: {error}\n")
    print(f"Built {output}; source diagnostic only, runtime unverified")


if __name__ == "__main__":
    main()

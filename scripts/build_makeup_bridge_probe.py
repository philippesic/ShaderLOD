#!/usr/bin/env python3
"""Assemble a default-off MakeUp consumer of existing protocol 2 core uniforms."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import tempfile
import zipfile

from build_makeup_adapter import read_archive

ROOT = Path(__file__).resolve().parents[1]


def apply_bridge_patches(files: dict[str, bytes], manifest: dict) -> dict[str, bytes]:
    if manifest.get("schema") != 1 or manifest.get("kind") != "makeup-core-bridge2-probe":
        raise ValueError("Unsupported MakeUp core bridge probe manifest")
    for name, expected in manifest["source_sha256"].items():
        if name not in files or hashlib.sha256(files[name]).hexdigest() != expected:
            raise ValueError(f"Pinned bridge probe source changed: {name}")
    result = files.copy()
    for patch in manifest["patches"]:
        name = patch["path"]
        if name not in manifest["source_sha256"]:
            raise ValueError(f"Unpinned bridge patch source: {name}")
        source = result[name].decode()
        if not patch["before"] or source.count(patch["before"]) != 1:
            raise ValueError(f"Bridge probe anchor must appear exactly once: {name}")
        result[name] = source.replace(patch["before"], patch["after"], 1).encode()
    return result


def build(output: Path, base: Path | None = None, root: Path = ROOT) -> dict:
    manifest_path = root / "patches/makeup/bridge2-probe.json"
    manifest_bytes = manifest_path.read_bytes()
    manifest = json.loads(manifest_bytes)
    base = base or root / "build/MakeUp-ShaderLOD-Experimental-9.5g.zip"
    if hashlib.sha256(base.read_bytes()).hexdigest() != manifest["base_sha256"]:
        raise ValueError("Frozen MakeUp derivative archive hash mismatch")
    original = read_archive(base)
    for name in ("LICENSE", "LICENSE.GPL-3.0", "shaderlod-source-patch.json", "shaderlod-build.json", "SHADERLOD_EXPERIMENT.md"):
        if name not in original:
            raise ValueError(f"Base license/source/attribution missing: {name}")
    files = apply_bridge_patches(original, manifest)
    overlay = root / "shaderpacks/makeup-bridge-probe"
    overlay_files = {path.relative_to(overlay).as_posix(): path.read_bytes()
                     for path in sorted(overlay.rglob("*")) if path.is_file()}
    if {name: hashlib.sha256(content).hexdigest() for name, content in overlay_files.items()} != manifest["overlay_sha256"]:
        raise ValueError("Pinned bridge overlay changed")
    for name, content in overlay_files.items():
        if name in files:
            raise ValueError(f"Bridge overlay may not replace source: {name}")
        files[name] = content
    modified = sorted(name for name in original if files[name] != original[name])
    metadata = {
        "integration": "MakeUp isolated core protocol 2 bridge probe",
        "base_sha256": manifest["base_sha256"],
        "bridge_manifest_sha256": hashlib.sha256(manifest_bytes).hexdigest(),
        "modified_shader_members": modified,
        "modified_member_sha256": {name: hashlib.sha256(files[name]).hexdigest() for name in modified},
        "overlay_sha256": manifest["overlay_sha256"],
        "accepted_protocol": manifest["accepted_protocol"],
        "declared_core_fields": manifest["declared_core_fields"],
        "default_bridge_option": 0,
        "default_diagnostic_option": 0,
        "prior_build_metadata_is_base_provenance": True,
        "validation": {"glsl_compiled": False, "iris_rendered": False, "bridge_transport_verified": False, "gpu_measured": False},
    }
    additions = {
        "SHADERLOD_CORE_BRIDGE_PROBE.md": (root / "docs/MAKEUP_CORE_BRIDGE_PROBE.md").read_bytes(),
        "shaderlod-bridge-source.json": manifest_bytes,
        "shaderlod-bridge-probe.json": (json.dumps(metadata, indent=2, sort_keys=True) + "\n").encode(),
    }
    for name, content in additions.items():
        if name in files:
            raise ValueError(f"Bridge output member collision: {name}")
        files[name] = content
    output = output.resolve()
    if output == base.resolve():
        raise ValueError("Bridge output may not overwrite frozen derivative")
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
    parser.add_argument("--base", type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "build/MakeUp-ShaderLOD-CoreBridge2-Probe-9.5g.zip")
    args = parser.parse_args()
    try:
        build(args.output, args.base)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, f"Cannot assemble MakeUp core bridge probe: {error}\n")
    print(f"Built {args.output}; bridge and diagnostic default off, runtime unverified")


if __name__ == "__main__":
    main()

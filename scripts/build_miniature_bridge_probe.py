#!/usr/bin/env python3
"""Assemble a pinned Miniature 2.19 core diagnostic; all SSR source stays original."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def read_archive(base: Path) -> dict[str, bytes]:
    with zipfile.ZipFile(base) as archive:
        names = [entry.filename for entry in archive.infolist() if not entry.is_dir()]
        if len(names) != len(set(names)):
            raise ValueError("Duplicate Miniature archive members")
        for name in names:
            path = PurePosixPath(name)
            if path.is_absolute() or ".." in path.parts or "\\" in name:
                raise ValueError(f"Unsafe Miniature archive member: {name}")
        return {name: archive.read(name) for name in names}


def apply_patches(files: dict[str, bytes], manifest: dict) -> dict[str, bytes]:
    if manifest.get("schema") != 1 or manifest.get("kind") != "miniature-core-diagnostic-probe":
        raise ValueError("Unsupported Miniature diagnostic manifest")
    for name, expected in manifest["source_sha256"].items():
        if name not in files or hashlib.sha256(files[name]).hexdigest() != expected:
            raise ValueError(f"Pinned Miniature source changed: {name}")
    result = files.copy()
    for patch in manifest["patches"]:
        name = patch["path"]
        if name not in manifest["source_sha256"]:
            raise ValueError(f"Unpinned Miniature patch member: {name}")
        # Decode bytes directly: preserve upstream CRLF and all untouched bytes.
        source = result[name].decode("utf-8")
        if not patch["before"] or source.count(patch["before"]) != 1:
            raise ValueError(f"Miniature anchor must appear exactly once: {name}")
        result[name] = source.replace(patch["before"], patch["after"], 1).encode("utf-8")
    return result


def build(output: Path, base: Path | None = None, root: Path = ROOT) -> dict:
    manifest_path = root / "patches/miniature/2.19-core-bridge-probe.json"
    manifest_bytes = manifest_path.read_bytes()
    manifest = json.loads(manifest_bytes)
    candidate = next(item for item in json.loads((root / "dev/shader-candidates.lock.json").read_text())["candidates"]
                     if item["modrinth_project"] == "miniature-shader")
    release = candidate["release"]
    if release["version"] != manifest["base_version"]:
        raise ValueError("Miniature manifest version differs from release pin")
    for algorithm, expected in manifest["archive_hashes"].items():
        if release[algorithm] != expected:
            raise ValueError(f"Miniature manifest differs from release {algorithm} pin")
    base = base or root / "dev/upstream/other-pack-research" / release["filename"]
    archive_bytes = base.read_bytes()
    for algorithm, expected in manifest["archive_hashes"].items():
        if hashlib.new(algorithm, archive_bytes).hexdigest() != expected:
            raise ValueError(f"Miniature base {algorithm} mismatch")
    license_bytes = (root / "patches/miniature/LICENSE.Miniature").read_bytes()
    if hashlib.sha256(license_bytes).hexdigest() != manifest["restored_license_sha256"]:
        raise ValueError("Pinned restored Miniature MIT license changed")
    original = read_archive(base)
    files = apply_patches(original, manifest)
    overlay = root / "shaderpacks/miniature-bridge-probe"
    added = {path.relative_to(overlay).as_posix(): path.read_bytes()
             for path in sorted(overlay.rglob("*")) if path.is_file()}
    if {name: hashlib.sha256(content).hexdigest() for name, content in added.items()} != manifest["overlay_sha256"]:
        raise ValueError("Pinned Miniature diagnostic overlay changed")
    for name, content in added.items():
        if name in files:
            raise ValueError(f"Miniature overlay may not replace upstream: {name}")
        files[name] = content
    modified = sorted(name for name in original if files[name] != original[name])
    metadata = {
        "integration": "Miniature core protocol 2 reflective-pixel diagnostic only",
        "base_version": manifest["base_version"],
        "archive_hashes": manifest["archive_hashes"],
        "manifest_sha256": hashlib.sha256(manifest_bytes).hexdigest(),
        "modified_upstream_members": modified,
        "modified_member_sha256": {name: hashlib.sha256(files[name]).hexdigest() for name in modified},
        "overlay_sha256": manifest["overlay_sha256"],
        "restored_license_sha256": manifest["restored_license_sha256"],
        "accepted_protocol": manifest["accepted_protocol"],
        "default_core_option": 0,
        "default_cue_option": 0,
        "normal_ssr_source_unchanged": True,
        "validation": {"glsl_compiled": False, "iris_rendered": False, "bridge_transport_verified": False, "gpu_measured": False},
    }
    additions = {
        "LICENSE.Miniature": license_bytes,
        "SHADERLOD_MINIATURE_ATTRIBUTION.md": (root / "patches/miniature/ATTRIBUTION.md").read_bytes(),
        "SHADERLOD_MINIATURE_CORE_PROBE.md": (root / "docs/MINIATURE_CORE_BRIDGE_PROBE.md").read_bytes(),
        "shaderlod-miniature-source.json": manifest_bytes,
        "shaderlod-miniature-probe.json": (json.dumps(metadata, indent=2, sort_keys=True) + "\n").encode(),
    }
    for name, content in additions.items():
        if name in files:
            raise ValueError(f"Miniature diagnostic member collision: {name}")
        files[name] = content
    output = output.resolve()
    if output == base.resolve():
        raise ValueError("Miniature output may not overwrite pinned release")
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
    parser.add_argument("--output", type=Path, default=ROOT / "build/Miniature-ShaderLOD-CoreProbe-2.19.zip")
    args = parser.parse_args()
    try:
        build(args.output, args.base)
    except (OSError, ValueError, KeyError, StopIteration, zipfile.BadZipFile) as error:
        parser.exit(1, f"Cannot assemble Miniature core diagnostic: {error}\n")
    print(f"Built {args.output}; SSR unchanged, diagnostic default off, runtime unverified")


if __name__ == "__main__":
    main()

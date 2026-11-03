#!/usr/bin/env python3
"""Assemble the pinned, default-off MakeUp water SSR experiment; no downloads."""
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
            raise ValueError("Duplicate MakeUp archive entries")
        for name in names:
            path = PurePosixPath(name)
            if path.is_absolute() or ".." in path.parts or "\\" in name:
                raise ValueError(f"Unsafe MakeUp archive entry: {name}")
        return {name: archive.read(name) for name in names}


def apply_patches(files: dict[str, bytes], manifest: dict) -> dict[str, bytes]:
    """Verify every original member before applying unique literal replacements."""
    if manifest.get("schema") != 1 or manifest.get("pack") != "MakeUp Ultra Fast":
        raise ValueError("Unsupported MakeUp patch manifest")
    for path, expected in manifest["source_sha256"].items():
        if path not in files or hashlib.sha256(files[path]).hexdigest() != expected:
            raise ValueError(f"Pinned MakeUp source changed: {path}")
    result = files.copy()
    for patch in manifest["patches"]:
        path = patch["path"]
        if path not in manifest["source_sha256"]:
            raise ValueError(f"Unpinned patch source: {path}")
        source = result[path].decode("utf-8")
        if not patch["before"] or source.count(patch["before"]) != 1:
            raise ValueError(f"Patch anchor must appear exactly once: {path}")
        result[path] = source.replace(patch["before"], patch["after"], 1).encode("utf-8")
    return result


def build(output: Path, base: Path | None = None, root: Path = ROOT) -> dict:
    manifest_path = root / "patches/makeup/9.5g.json"
    manifest = json.loads(manifest_path.read_text())
    candidate = next(item for item in json.loads((root / "dependencies/shader-candidates.lock.json").read_text())["candidates"]
                     if item["modrinth_project"] == "makeup-ultra-fast-shaders")
    release = candidate["release"]
    if manifest["base_version"] != release["version"] or manifest["source_revision"] != candidate["source"]["revision"]:
        raise ValueError("MakeUp manifest disagrees with candidate version/source pin")
    for algorithm, expected in manifest["archive_hashes"].items():
        if expected != release[algorithm]:
            raise ValueError(f"MakeUp manifest disagrees with candidate {algorithm}")
    base = base or root / "dependencies/upstream/other-pack-research" / release["filename"]
    source = base.read_bytes()
    for algorithm, expected in manifest["archive_hashes"].items():
        if hashlib.new(algorithm, source).hexdigest() != expected:
            raise ValueError(f"MakeUp base {algorithm} mismatch: {base}")
    original = read_archive(base)
    if original.get("LICENSE") != (root / "patches/makeup/LICENSE.MakeUp").read_bytes():
        raise ValueError("Retained MakeUp license disagrees with original")
    files = apply_patches(original, manifest)
    for path in sorted((root / "shaderpacks/makeup").rglob("*")):
        if path.is_file():
            relative = path.relative_to(root / "shaderpacks/makeup").as_posix()
            if relative in files:
                raise ValueError(f"Overlay may not replace upstream: {relative}")
            files[relative] = path.read_bytes()
    # Ship editable GLSL, the complete literal source patch and both license texts.
    for name, content in {
        "shaderlod-source-patch.json": manifest_path.read_bytes(),
        "LICENSE.GPL-3.0": (root / "patches/makeup/LICENSE.GPL-3.0").read_bytes(),
    }.items():
        if name in files:
            raise ValueError(f"Reserved derivative member collision: {name}")
        files[name] = content
    metadata = {
        "integration": "MakeUp-ShaderLOD-Experimental vanilla water SSR fade/skip",
        "base_version": manifest["base_version"],
        "source_revision": manifest["source_revision"],
        "archive_hashes": manifest["archive_hashes"],
        "patch_manifest_sha256": hashlib.sha256(manifest_path.read_bytes()).hexdigest(),
        "modified_upstream_files": sorted(name for name in original if files[name] != original[name]),
        "overlay_sha256": {name: hashlib.sha256(files[name]).hexdigest() for name in sorted(files.keys() - original.keys())},
        "default_enabled": False,
        "common_protocol_connected": False,
        "validation": {"glsl_compiled": False, "iris_rendered": False, "gpu_measured": False},
    }
    if "shaderlod-build.json" in files:
        raise ValueError("Reserved derivative member collision: shaderlod-build.json")
    files["shaderlod-build.json"] = (json.dumps(metadata, indent=2, sort_keys=True) + "\n").encode()
    output = output.resolve()
    if output == base.resolve():
        raise ValueError("Output may not overwrite pinned upstream")
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
    parser.add_argument("--output", type=Path, default=ROOT / "build/MakeUp-ShaderLOD-Experimental-9.5g.zip")
    args = parser.parse_args()
    try:
        result = build(args.output, args.base)
    except (OSError, ValueError, KeyError, StopIteration, zipfile.BadZipFile) as error:
        parser.exit(1, f"Cannot assemble MakeUp experiment: {error}\n")
    print(f"Built {args.output}: MakeUp {result['base_version']}, default off; runtime unverified")


if __name__ == "__main__":
    main()

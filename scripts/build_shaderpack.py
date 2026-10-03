#!/usr/bin/env python3
"""Assemble a pinned Photon ZIP with reviewed literal patches and new overlays."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def apply_patches(files: dict[str, bytes], manifest: dict) -> dict[str, bytes]:
    """Fail closed on a changed source or ambiguous/missing literal anchor."""
    if manifest.get("schema") != 1:
        raise ValueError("Unsupported Photon patch manifest schema")
    result = files.copy()
    verified = set()
    for patch in manifest["patches"]:
        path = patch["path"]
        if path not in files:
            raise ValueError(f"Missing Photon source: {path}")
        if path not in verified:
            actual = hashlib.sha256(files[path]).hexdigest()
            if actual != patch["source_sha256"]:
                raise ValueError(f"Pinned Photon source changed: {path}")
            verified.add(path)
        elif hashlib.sha256(files[path]).hexdigest() != patch["source_sha256"]:
            raise ValueError(f"Inconsistent source hash: {path}")
        source = result[path].decode("utf-8")
        if not patch["before"] or source.count(patch["before"]) != 1:
            raise ValueError(f"Patch anchor must appear exactly once: {path}")
        result[path] = source.replace(patch["before"], patch["after"], 1).encode("utf-8")
    return result


def build(output: Path, base: Path | None = None, root: Path = ROOT) -> dict:
    lock = json.loads((root / "dev/runtime.lock.json").read_text())
    photon = lock["artifacts"]["photon"]
    base = base or root / "dev/upstream" / photon["filename"]
    source = base.read_bytes()
    for algorithm, expected in photon["hashes"].items():
        if hashlib.new(algorithm, source).hexdigest() != expected:
            raise ValueError(f"Photon base {algorithm} mismatch: {base}")
    manifest = json.loads((root / "patches/photon/v1.3b.json").read_text())
    if manifest["base_version"] != photon["version"]:
        raise ValueError("Patch manifest does not match pinned Photon version")
    with zipfile.ZipFile(base) as archive:
        names = [entry.filename for entry in archive.infolist() if not entry.is_dir()]
        if len(names) != len(set(names)):
            raise ValueError("Duplicate Photon archive entries")
        for name in names:
            path = PurePosixPath(name)
            if path.is_absolute() or ".." in path.parts:
                raise ValueError(f"Unsafe Photon archive entry: {name}")
        files = {name: archive.read(name) for name in names}
    if "LICENSE" not in files:
        raise ValueError("Photon license missing")
    original = files
    files = apply_patches(files, manifest)
    overlay = root / "shaderpacks/photon"
    for path in sorted(overlay.rglob("*")):
        if path.is_file():
            relative = path.relative_to(overlay).as_posix()
            if relative in files:
                raise ValueError(f"Overlay cannot replace upstream; use a bounded patch: {relative}")
            files[relative] = path.read_bytes()
    metadata = {
        "integration": "ShaderLOD Photon PCF experiment",
        "bridge_version": 1,
        "base_version": photon["version"],
        "base_sha512": photon["hashes"]["sha512"],
        "patch_manifest_sha256": hashlib.sha256((root / "patches/photon/v1.3b.json").read_bytes()).hexdigest(),
        "modified_upstream_files": sorted(name for name in original if original[name] != files[name]),
        "overlay_sha256": {name: hashlib.sha256(files[name]).hexdigest() for name in sorted(files.keys() - original.keys())},
    }
    files["shaderlod-build.json"] = (json.dumps(metadata, indent=2, sort_keys=True) + "\n").encode()
    output = output.resolve()
    if output == base.resolve():
        raise ValueError("Output may not overwrite pinned upstream archive")
    output.parent.mkdir(parents=True, exist_ok=True)
    # Fixed timestamps, ordering, permissions, compression make rebuilds reproducible.
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
    parser.add_argument("--base", type=Path, help="Pinned upstream ZIP (defaults to dev/upstream)")
    parser.add_argument("--output", type=Path, default=ROOT / "run/shaderpacks/Photon-ShaderLOD.zip")
    args = parser.parse_args()
    try:
        metadata = build(args.output, args.base)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, f"Cannot assemble Photon ShaderLOD: {error}\n")
    print(f"Built {args.output}: Photon {metadata['base_version']}, {len(metadata['modified_upstream_files'])} bounded source modifications")


if __name__ == "__main__":
    main()

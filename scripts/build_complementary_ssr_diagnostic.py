#!/usr/bin/env python3
"""Build a PRIVATE, pinned Complementary water-reflection diagnostic (default native)."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "patches/complementary-experiments/ssr-water-removal-r1.json"
REFLECTIONS = "shaders/lib/materials/materialMethods/reflections.glsl"
MODES = ("native", "remove-water-terrain")
START = b"            // Ray Marching\r\n"
END = b"        #endif\r\n\r\n        // Method 2: Mirorred Image Reflection //"
GUARD = (
    "{enabled} && defined MC_OS_MAC && MC_VERSION == 12101 && RP_MODE == 1"
    " && defined OVERWORLD && defined GBUFFERS_WATER && !defined COMPOSITE"
    " && !defined GBUFFERS_COLORWHEEL_TRANSLUCENT"
    " && !defined DISTANT_HORIZONS && !defined DH_WATER && !defined DH_TERRAIN"
    " && !defined VOXY && !defined VOXY_PATCH && !defined VOXY_TRANSLUCENT"
    " && WORLD_SPACE_REFLECTIONS_INTERNAL == -1 && WATER_REFLECT_QUALITY >= 2"
)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def members_digest(files: dict[str, bytes]) -> str:
    """Length-delimited names and member hashes; includes non-shader/license bytes."""
    digest = hashlib.sha256()
    for name, data in sorted(files.items()):
        encoded = name.encode("utf-8")
        digest.update(len(encoded).to_bytes(8, "big"))
        digest.update(encoded)
        digest.update(hashlib.sha256(data).digest())
    return digest.hexdigest()


def read_archive(path: Path) -> dict[str, bytes]:
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate archive member")
        return {name: archive.read(name) for name in names}


def patch_reflections(source: bytes, mode: str) -> bytes:
    if mode not in MODES:
        raise ValueError("Unsupported diagnostic mode")
    if source.count(START) != 1 or source.count(END) != 1:
        raise ValueError("Pinned reflection boundaries must each occur once")
    if source.index(START) >= source.index(END):
        raise ValueError("Reversed reflection boundaries")
    condition = GUARD.format(enabled=int(mode == "remove-water-terrain"))
    opening = (
        "            // ShaderLOD SSR diagnostic R1: baked private mode; no native option change.\r\n"
        f"            #if {condition}\r\n"
        "            if (mat != 32000) { // Keep native tracing for every non-water material.\r\n"
        "            #endif\r\n"
    ).encode()
    closing = (
        f"            #if {condition}\r\n"
        "            } // ShaderLOD SSR diagnostic R1: water keeps the native miss state.\r\n"
        "            #endif\r\n"
    ).encode()
    return source.replace(START, opening + START, 1).replace(END, closing + END, 1)


def apply(files: dict[str, bytes], manifest: dict, mode: str = "native") -> dict[str, bytes]:
    if manifest.get("schema") != 1 or manifest.get("id") != "ssr-water-removal-r1":
        raise ValueError("Unsupported diagnostic manifest")
    if members_digest(files) != manifest["original_members_digest"]:
        raise ValueError("Pinned archive member content changed")
    for name, expected in manifest["source_members"].items():
        if name not in files or sha256(files[name]) != expected:
            raise ValueError("Pinned source member changed: " + name)
    output = dict(files)
    output[REFLECTIONS] = patch_reflections(files[REFLECTIONS], mode)
    if sha256(output[REFLECTIONS]) != manifest["patched_reflections_sha256"][mode]:
        raise ValueError("Diagnostic patch differs from its reviewed source pin")
    if [n for n in files if files[n] != output[n]] != [REFLECTIONS]:
        raise ValueError("Diagnostic must modify exactly the reflection member")
    return output


def validate_options(data: bytes, manifest: dict) -> None:
    # Byte identity deliberately rejects duplicate keys, native setting changes,
    # alternate exported profiles and comments. This is one retained R11 input.
    if sha256(data) != manifest["retained_options"]["sha256"]:
        raise ValueError("Native options differ from the retained R11 original")
    if data != b"SHADOW_QUALITY=2\n":
        raise ValueError("Unsupported native option payload")


def build(base: Path, output: Path, options: Path, mode: str = "native",
          manifest_path: Path = MANIFEST) -> dict:
    base, output, options = Path(base), Path(output), Path(options)
    sidecar = Path(str(output) + ".txt")
    if output.resolve() in (base.resolve(), options.resolve()) or sidecar.resolve() in (
        base.resolve(), options.resolve()
    ):
        raise ValueError("Cannot overwrite an original input")
    if output.exists() or sidecar.exists():
        raise ValueError("Output or options sidecar already exists")
    manifest = json.loads(Path(manifest_path).read_text())
    if sha256(base.read_bytes()) != manifest["original_archive_sha256"]:
        raise ValueError("Pinned original archive changed")
    option_bytes = options.read_bytes()
    validate_options(option_bytes, manifest)
    original = read_archive(base)
    files = apply(original, manifest, mode)
    evidence = {
        "schema": 1,
        "id": manifest["id"],
        "kind": "coupled-degraded-water-terrain-removal-versus-native-background",
        "mode": mode,
        "default_mode": "native",
        "version": manifest["version"],
        "original_archive_sha256": manifest["original_archive_sha256"],
        "original_members_digest": members_digest(original),
        "patched_members_digest": members_digest(files),
        "modified_upstream_members": [REFLECTIONS],
        "patched_reflections_sha256": sha256(files[REFLECTIONS]),
        "native_options_sha256": sha256(option_bytes),
        "license_sha256": sha256(files["License.txt"]),
        "guard": GUARD.format(enabled=int(mode == "remove-water-terrain")),
        "material_guard": "mat != 32000 executes original trace; mat == 32000 keeps native miss",
        "validation": {name: False for name in (
            "actual_consuming_path", "driver_compilation", "in_world_rendering",
            "quality", "performance", "dh", "pbr", "other_versions"
        )},
    }
    metadata_names = ("shaderlod-private-ssr-diagnostic.json", "SHADERLOD_PRIVATE_SSR_DIAGNOSTIC.txt")
    if any(name in original for name in metadata_names):
        raise ValueError("Diagnostic metadata collides with original member")
    files[metadata_names[0]] = (
        json.dumps(evidence, indent=2, sort_keys=True) + "\n"
    ).encode()
    files[metadata_names[1]] = (
        "Private local diagnostic; Complementary Reimagined r5.9.3 by EminGT.\n"
        "Original License.txt and all upstream attribution retained unchanged.\n"
        "Deliberately degraded coupled terrain-reflection removal/background contrast.\n"
        "Not a product, quality optimization, isolated cost ceiling or redistribution grant.\n"
        "Default builder mode native. Explicit remove-water-terrain mode changes only the\n"
        "qualified Mac 1.21.1 Overworld native water trace/hit block; background remains.\n"
        "Other material/dimension/DH/VOXY/WSR paths retain source-defined native work.\n"
        "No runtime activation, rendering, quality or performance accepted by this build.\n"
        + "Source: " + manifest["primary_page"] + "\n"
    ).encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    # Exclusive creation prevents clobbering another worker's artifact. A write
    # failure may leave a partial artifact and must be inspected, never rerun over.
    with zipfile.ZipFile(output, "x", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(files.items()):
            entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o644 << 16
            archive.writestr(entry, data)
    with sidecar.open("xb") as stream:
        stream.write(option_bytes)
    return {
        **evidence,
        "manifest_sha256": sha256(Path(manifest_path).read_bytes()),
        "output": str(output.resolve()),
        "output_sha256": sha256(output.read_bytes()),
        "options_sidecar": str(sidecar.resolve()),
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--options", type=Path, required=True)
    parser.add_argument("--mode", choices=MODES, default="native")
    args = parser.parse_args()
    print(json.dumps(build(args.base, args.output, args.options, args.mode), indent=2))

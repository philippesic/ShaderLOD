#!/usr/bin/env python3
"""Build a private, pinned Complementary copy-pass experiment; native by default.

This issues a source/launch receipt, not a runtime graph or pixel certificate.
The launch owner must recheck the installed Iris JAR, pack and effective options.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import tempfile
import zipfile

from build_supported_pack_adapters import read_archive, shader_source_digest

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "patches/complementary-experiments/copy-elision-r1.json"
PROPERTIES = "shaders/shaders.properties"
DIMENSIONS = ("world0", "world-1", "world1")
KIND = "private-identity-pass-elision"


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def properties_patch(active=False, dimension="world0"):
    """No shader option, custom macro transport, new target or replacement pass."""
    if dimension not in DIMENSIONS:
        raise ValueError("Unknown dimension; retain the original pack")
    return ("\r\n# Private ShaderLOD copy-elision R1; see external launch receipt.\r\n"
            "# Source-only candidate. Other dimensions and unknown versions stay native.\r\n"
            f"#if {1 if active else 0}\r\n"
            "#if defined IS_IRIS && defined IRIS_VERSION && IRIS_VERSION == 10812\r\n"
            "#if defined MC_VERSION && MC_VERSION == 12101\r\n"
            "#if defined WORLD_BLUR && WORLD_BLUR == 0\r\n"
            "#if defined MOTION_BLUR_EFFECT && MOTION_BLUR_EFFECT == -1\r\n"
            f"program.{dimension}/composite3.enabled=false\r\n"
            "#endif\r\n#endif\r\n#endif\r\n#endif\r\n#endif\r\n").encode()


def validate_sources(files, manifest, pack):
    if manifest.get("schema") != 1 or manifest.get("kind") != KIND:
        raise ValueError("Unsupported private experiment schema/kind")
    if pack not in manifest["packs"]:
        raise ValueError("Uncertified pack")
    pin = manifest["packs"][pack]
    if shader_source_digest(files) != pin["shader_source_digest"]:
        raise ValueError("Pinned shader source identity changed")
    for name, digest in pin["source_members"].items():
        if name not in files or sha256(files[name]) != digest:
            raise ValueError("Pinned source changed: " + name)
    # Separate absence guards make future extension of a pin an explicit review.
    for dimension in DIMENSIONS:
        for name in files:
            if re.fullmatch(rf"shaders/{re.escape(dimension)}/composite3(?:_[a-z]+)?\.csh", name):
                raise ValueError("Unreviewed composite3 compute companion")
    if re.search(rb"(?:program\.[^\r\n]*composite3\.enabled|flip\.|scale\.composite3)", files[PROPERTIES]):
        raise ValueError("Unreviewed native graph override")
    return pin


def read_effective_options(path, options_key=None):
    data = Path(path).read_bytes()
    values = json.loads(data)
    if options_key is not None:
        if not isinstance(values, dict) or options_key not in values:
            raise ValueError("Effective option snapshot key missing")
        values = values[options_key]
    if not isinstance(values, dict) or not values:
        raise ValueError("Expected a nonempty effective option dictionary")
    if any(not isinstance(k, str) or not isinstance(v, (str, int, bool)) for k, v in values.items()):
        raise ValueError("Effective options must be scalar values")
    # Do not infer defaults from sparse shaderpack .txt files or Python False == 0.
    if type(values.get("WORLD_BLUR")) not in (str, int) or str(values["WORLD_BLUR"]) != "0":
        raise ValueError("Effective WORLD_BLUR must be explicitly 0")
    if type(values.get("MOTION_BLUR_EFFECT")) not in (str, int) or str(values["MOTION_BLUR_EFFECT"]) != "-1":
        raise ValueError("Effective MOTION_BLUR_EFFECT must be explicitly -1 for R1")
    normalized = json.dumps(values, sort_keys=True, separators=(",", ":")).encode()
    return {"path": str(Path(path).resolve()), "file_sha256": sha256(data),
            "key": options_key, "values_sha256": sha256(normalized),
            "names_sha256": sha256(json.dumps(sorted(values), separators=(",", ":")).encode()),
            "value_count": len(values), "WORLD_BLUR": "0", "MOTION_BLUR_EFFECT": "-1",
            "provenance": "supplied effective snapshot; builder does not observe a running client"}


def apply(files, manifest, pack, *, active=False, dimension="world0"):
    validate_sources(files, manifest, pack)
    result = files.copy()
    result[PROPERTIES] += properties_patch(active, dimension)
    assert sorted(n for n in files if files[n] != result[n]) == [PROPERTIES]
    return result


def build(base, output, *, pack, iris=None, options=None, options_key=None,
          dimension="world0", activate_private=False, manifest_path=MANIFEST):
    base, output = Path(base), Path(output)
    if base.resolve() == output.resolve():
        raise ValueError("Cannot overwrite original pack")
    if output.exists():
        raise ValueError("Output already exists; choose a separate private copy")
    manifest_bytes = Path(manifest_path).read_bytes()
    manifest = json.loads(manifest_bytes)
    original = read_archive(base)
    pin = validate_sources(original, manifest, pack)
    archive_hash = sha256(base.read_bytes())
    if archive_hash != pin["release_archive_sha256"]:
        raise ValueError("Pinned original archive changed")
    iris_hash = sha256(Path(iris).read_bytes()) if iris is not None else None
    iris_pin = manifest["iris_source_certificate"]["jar_sha256"]
    if iris_hash is not None and iris_hash != iris_pin:
        raise ValueError("Uncertified Iris JAR; retain the original pack")
    option_receipt = read_effective_options(options, options_key) if options is not None else None
    if activate_private and (iris_hash != iris_pin or option_receipt is None):
        raise ValueError("Private activation requires exact Iris JAR and effective options")
    if activate_private:
        inventory = pin.get("activation_option_inventory")
        if not inventory or any(option_receipt[k] != inventory[k] for k in ("names_sha256", "value_count")):
            raise ValueError("Uncertified or incomplete effective option inventory; retain native")
    files = apply(original, manifest, pack, active=activate_private, dimension=dimension)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output.parent, delete=False) as temp:
        staging = Path(temp.name)
    try:
        with zipfile.ZipFile(staging, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for name, data in sorted(files.items()):
                entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                entry.compress_type = zipfile.ZIP_DEFLATED
                entry.external_attr = 0o644 << 16
                archive.writestr(entry, data)
        with output.open("xb") as final:
            final.write(staging.read_bytes())
    finally:
        staging.unlink(missing_ok=True)
    return {"schema": 1, "kind": KIND, "id": manifest["id"], "pack": pack,
            "version": pin["version"], "manifest_sha256": sha256(manifest_bytes),
            "builder_sha256": sha256(Path(__file__).read_bytes()),
            "original_archive_sha256": archive_hash,
            "original_shader_source_digest": pin["shader_source_digest"],
            "candidate_shader_source_digest": shader_source_digest(files),
            "output": str(output.resolve()), "output_sha256": sha256(output.read_bytes()),
            "modified_upstream_members": [PROPERTIES], "added_archive_members": [],
            "original_properties_sha256": sha256(original[PROPERTIES]),
            "candidate_properties_sha256": sha256(files[PROPERTIES]),
            "license_sha256": sha256(original["License.txt"]),
            "supplied_iris_jar_sha256": iris_hash, "effective_options": option_receipt,
            "private_activation_requested": activate_private, "dimension": dimension,
            "runtime_guard": "IS_IRIS, IRIS_VERSION=10812, MC_VERSION=12101, defined WORLD_BLUR=0, defined MOTION_BLUR_EFFECT=-1",
            "launch_owner_requirements": manifest["launch_owner_requirements"],
            "validation": {"source_pins": True, "properties_only": True,
                           "actual_iris_preprocessing": False, "shader_compiled": False,
                           "rendered": False, "physical_graph_parity": False,
                           "exact_pixel_parity": False, "lifecycle_parity": False,
                           "quality_accepted": False, "performance_measured": False}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--pack", choices=("complementary-reimagined", "complementary-unbound"), required=True)
    parser.add_argument("--iris", type=Path)
    parser.add_argument("--options", type=Path, help="JSON effective options; sparse .txt is not accepted")
    parser.add_argument("--options-key", help="Explicit map key, e.g. original in the retained R11 snapshot")
    parser.add_argument("--dimension", choices=DIMENSIONS, default="world0")
    parser.add_argument("--activate-private", action="store_true")
    args = parser.parse_args()
    print(json.dumps(build(args.base, args.output, pack=args.pack, iris=args.iris,
                           options=args.options, options_key=args.options_key,
                           dimension=args.dimension, activate_private=args.activate_private), indent=2))


if __name__ == "__main__":
    main()

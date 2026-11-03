#!/usr/bin/env python3
"""Build one private BSL c7 mip-request experiment; native control by default.

Only the exact source/option/JAR certificate is supported. This does not certify
actual driver state, pixels or speed. No production catalog uses this builder.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import tempfile
import zipfile

from build_supported_pack_adapters import read_archive, shader_source_digest

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "patches/resource-experiments/mip-liveness-r1.json"
MEMBER = "shaders/program/composite7.glsl"
ANCHOR = b"const bool colortex1MipmapEnabled = true;"
KIND = "private-dead-mip-chain-suppression"


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":")).encode()


def patch(active=False):
    # A literal build choice adds no discoverable shader option. GLSL guards
    # preserve unknown/missing versions, other dimensions and native AA gates.
    return (f"#if {int(active)} && defined IS_IRIS && defined IRIS_VERSION && IRIS_VERSION == 10812\r\n"
            "#if defined MC_VERSION && MC_VERSION == 12101 && defined OVERWORLD\r\n"
            "#if defined TAA && defined LIGHT_SHAFT && !defined RETRO_FILTER\r\n"
            "// Private BSL c7 dead-mip source certificate; runtime proof remains required.\r\n"
            "const bool colortex1MipmapEnabled = false;\r\n"
            "#else\r\nconst bool colortex1MipmapEnabled = true;\r\n#endif\r\n"
            "#else\r\nconst bool colortex1MipmapEnabled = true;\r\n#endif\r\n"
            "#else\r\nconst bool colortex1MipmapEnabled = true;\r\n#endif").encode()


def validate_sources(files, manifest):
    if manifest.get("schema") != 1 or manifest.get("kind") != KIND:
        raise ValueError("Unsupported private certificate")
    if shader_source_digest(files) != manifest["shader_source_digest"]:
        raise ValueError("Pinned shader source changed")
    for name, digest in manifest["source_members"].items():
        if name not in files or sha256(files[name]) != digest:
            raise ValueError("Pinned source changed: " + name)
    if files[MEMBER].count(ANCHOR) != 1:
        raise ValueError("Expected exactly one native mip request")
    if any("/composite7" in name and name.endswith(".csh") for name in files):
        raise ValueError("Uncertified c7 compute companion")


def read_options(path, key, manifest):
    raw = Path(path).read_bytes()
    values = json.loads(raw)
    if key is not None:
        if not isinstance(values, dict) or key not in values:
            raise ValueError("Missing explicit option-map key")
        values = values[key]
    # These are observed Iris strings. Do not coerce bool/int or infer defaults.
    if not isinstance(values, dict) or any(not isinstance(k, str) or not isinstance(v, str)
                                           for k, v in values.items()):
        raise ValueError("Expected a complete string-valued effective option map")
    pin = manifest["activation_options"]
    hashes = {"value_count": len(values), "names_sha256": sha256(canonical(sorted(values))),
              "values_sha256": sha256(canonical(values))}
    if any(hashes[name] != pin[name] for name in hashes):
        raise ValueError("Uncertified or incomplete effective option map")
    if any(values.get(name) != value for name, value in pin["required_values"].items()):
        raise ValueError("Native activation options changed")
    return {"path": str(Path(path).resolve()), "file_sha256": sha256(raw), "key": key,
            **hashes, "provenance": "Supplied prior observation; reobserve at private launch"}


def apply(files, manifest, active=False):
    validate_sources(files, manifest)
    result = files.copy()
    result[MEMBER] = files[MEMBER].replace(ANCHOR, patch(active), 1)
    return result


def build(base, output, *, iris=None, options=None, options_key=None,
          activate_private=False, manifest_path=MANIFEST):
    base, output = Path(base), Path(output)
    if base.resolve() == output.resolve() or output.exists():
        raise ValueError("Preserve original and prior outputs; use a new output path")
    raw_manifest = Path(manifest_path).read_bytes()
    manifest = json.loads(raw_manifest)
    files = read_archive(base)
    validate_sources(files, manifest)
    original_hash = sha256(base.read_bytes())
    if original_hash != manifest["release_archive_sha256"]:
        raise ValueError("Pinned original archive changed")
    iris_hash = sha256(Path(iris).read_bytes()) if iris is not None else None
    iris_pin = manifest["iris_source_certificate"]["jar_sha256"]
    if iris_hash is not None and iris_hash != iris_pin:
        raise ValueError("Uncertified Iris JAR")
    option_receipt = read_options(options, options_key, manifest) if options is not None else None
    if activate_private and (iris_hash != iris_pin or option_receipt is None):
        raise ValueError("Private activation requires exact Iris and full observed options")
    changed = apply(files, manifest, activate_private)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output.parent, delete=False) as temp:
        staging = Path(temp.name)
    try:
        with zipfile.ZipFile(staging, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for name, data in sorted(changed.items()):
                entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                entry.compress_type = zipfile.ZIP_DEFLATED
                entry.external_attr = 0o644 << 16
                archive.writestr(entry, data)
        with output.open("xb") as destination:
            destination.write(staging.read_bytes())
    finally:
        staging.unlink(missing_ok=True)
    return {"schema": 1, "kind": KIND, "id": manifest["id"], "pack": "bsl-shaders",
            "original_archive_sha256": original_hash, "output": str(output.resolve()),
            "output_sha256": sha256(output.read_bytes()), "manifest_sha256": sha256(raw_manifest),
            "builder_sha256": sha256(Path(__file__).read_bytes()),
            "original_source_digest": shader_source_digest(files),
            "candidate_source_digest": shader_source_digest(changed),
            "modified_upstream_members": [MEMBER], "added_archive_members": [],
            "properties_sha256": sha256(files["shaders/shaders.properties"]),
            "supplied_iris_jar_sha256": iris_hash, "effective_options": option_receipt,
            "private_activation_requested": activate_private, "dimension": "world0",
            "launch_owner_requirements": manifest["launch_owner_requirements"],
            "validation": {"source_pins": True, "one_directive_edit": True,
                           "actual_iris_preprocessing": False, "driver_compiled": False,
                           "physical_mip_call_absence": False, "exact_pixel_parity": False,
                           "lifecycle_parity": False, "dh_pbr_compatibility": False,
                           "performance_measured": False}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--iris", type=Path)
    parser.add_argument("--options", type=Path)
    parser.add_argument("--options-key")
    parser.add_argument("--activate-private", action="store_true")
    args = parser.parse_args()
    print(json.dumps(build(args.base, args.output, iris=args.iris, options=args.options,
                           options_key=args.options_key, activate_private=args.activate_private), indent=2))


if __name__ == "__main__":
    main()

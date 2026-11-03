#!/usr/bin/env python3
"""Build the private, fixed 0.85 Bliss native-TAAU pilot; never edit a profile.

This is an explicit archive/member-pinned adapter, not a general shader rewriter.
Assembly and source preprocessing do not certify Iris rendering or performance.
"""

import argparse
import hashlib
import json
import math
from pathlib import Path, PurePosixPath
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "patches/bliss-experiments/native-raster-r1.json"
BASE = ROOT / "dependencies/upstream/pack-expansion/Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip"
OUTPUT_ROOT = ROOT / "dev/logs/mechanism-evaluation-r1/raster"
BASE_SHA256 = "f41db92acc585fe9ffe0cc124d6ecaea27cef9ded91129ebc2a8e60226947b2c"
MANIFEST_SHA256 = "b7f6a52c57943ae142137b8f35e0e1be52795051f0ebea7d7a633c6c9e66acc7"
OPTION = "SHADERLOD_BLISS_RASTER"
FIXED_SCALE = 0.85
SCALE_VALUES = {round(i / 100, 2) for i in range(10, 101, 5)}


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def read_members(path):
    with zipfile.ZipFile(path) as archive:
        entries = archive.infolist()
        names = [e.filename for e in entries]
        if len(names) != len(set(names)) or any(
            PurePosixPath(n).is_absolute() or ".." in PurePosixPath(n).parts
            or "\\" in n or "\x00" in n for n in names
        ):
            raise ValueError("Unsafe or duplicate archive member")
        return {e.filename: archive.read(e) for e in entries if not e.is_dir()}


def read_manifest():
    raw = MANIFEST.read_bytes()
    if sha256(raw) != MANIFEST_SHA256:
        raise ValueError("Unreviewed manifest; exact pinned contract required")
    manifest = json.loads(raw)
    if (manifest["base_sha256"] != BASE_SHA256 or manifest["fixed_linear_scale"] != FIXED_SCALE
            or manifest["option_key"] != OPTION or not manifest["private_only"]):
        raise ValueError("Unexpected private candidate contract")
    return manifest


def patch(files, manifest):
    """Fail before mutation if any certified member or literal anchor changed."""
    for name, digest in (manifest["source_members"] | manifest["license_members"]).items():
        if name not in files or sha256(files[name]) != digest:
            raise ValueError("Pinned source member changed: " + name)
    result = dict(files)
    for entry in manifest["patches"]:
        name = entry["path"]
        source = files[name]
        if sha256(source) != entry["source_sha256"]:
            raise ValueError("Pinned patch member changed: " + name)
        for replacement in entry["replacements"]:
            before = replacement["before"].encode()
            after = replacement["after"].encode()
            if not before or source.count(before) != replacement["count"]:
                raise ValueError("Missing or ambiguous native anchor: " + name)
            source = source.replace(before, after)
        if sha256(source) != entry["patched_sha256"]:
            raise ValueError("Patched member hash mismatch: " + name)
        result[name] = source
    return result


def parse_options(raw):
    """Accept an explicit simple Iris override file; preserve its bytes verbatim."""
    values = {}
    for line in raw.decode("utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith(("#", "!")):
            continue
        match = re.fullmatch(r"([A-Za-z_][A-Za-z_0-9]*)\s*=\s*([^\s\\]+)", line)
        if not match or match[1] in values:
            raise ValueError("Ambiguous/unsupported Iris option syntax or duplicate key")
        values[match[1]] = match[2]
    if any(k.startswith("SHADERLOD_") for k in values):
        raise ValueError("Use original native options, not another ShaderLOD derivative")
    return values


def option_bool(values, name, default):
    value = values.get(name, "true" if default else "false")
    if value not in {"true", "false"}:
        raise ValueError("Expected native boolean for " + name)
    return value == "true"


def policy(values, enabled=True):
    taa = option_bool(values, "TAA", True)
    taau = option_bool(values, "TAA_UPSCALING", False)
    screenshot = option_bool(values, "SCREENSHOT_MODE", False)
    try:
        scale = float(values.get("SCALE_FACTOR", "0.75"))
        sky = int(values.get("RESOURCEPACK_SKY", "0"))
        dof = int(values.get("DOF_QUALITY", "-1"))
    except ValueError as exc:
        raise ValueError("Invalid native scale/sky/DOF option") from exc
    if not math.isfinite(scale) or scale not in SCALE_VALUES:
        raise ValueError("SCALE_FACTOR is outside the pinned native choices")
    if sky not in range(4) or dof not in range(-1, 6):
        raise ValueError("Sky/DOF is outside the pinned native choices")
    native = scale if taa and taau else 1.0
    eligible = taa and not screenshot and sky == 0 and dof != 5
    effective = min(native, FIXED_SCALE) if enabled and eligible else native
    return {"native_taa": taa, "native_taau": taau, "native_scale_option": scale,
            "native_effective_scale": native, "source_option_eligible": eligible,
            "candidate_effective_scale": effective, "private_switch": int(enabled),
            "avoids_additional_nominal_area": effective < native,
            "runtime_guard_required": ["defined IRIS_VERSION == 10812", "defined MC_VERSION == 12101", "rainStrength == 0", "VOXY absent", "actual loaded native options match", "actual shader/resource/coverage joins"],
            "interpretation": "Source policy only; full target allocation and several passes remain full size"}


def build(base, options, output):
    base, options, output = Path(base).resolve(), Path(options).resolve(), Path(output).resolve()
    sidecar, receipt = Path(str(output) + ".txt"), output.with_suffix(".json")
    destinations = (output, sidecar, receipt)
    if (output.suffix != ".zip" or not output.is_relative_to(OUTPUT_ROOT.resolve())
            or any(p.exists() for p in destinations)
            or any(p in {base, options, MANIFEST.resolve()} for p in destinations)):
        raise ValueError("Use a fresh ZIP under the ignored raster evidence directory")
    if sha256(base.read_bytes()) != BASE_SHA256:
        raise ValueError("Pinned original Bliss archive mismatch")
    manifest = read_manifest()
    native_options = options.read_bytes()
    native_values = parse_options(native_options)
    effective = policy(native_values)
    if (option_bool(native_values, "SCREENSHOT_MODE", False)
            or int(native_values.get("RESOURCEPACK_SKY", "0")) != 0
            or int(native_values.get("DOF_QUALITY", "-1")) == 5):
        raise ValueError("Unqualified screenshot/custom-sky/focus-laser path; retain original")
    original = read_members(base)
    candidate = patch(original, manifest)
    candidate_options = native_options + (b"" if not native_options or native_options.endswith(b"\n") else b"\n") + (OPTION + "=1\n").encode()
    metadata = {
        "experiment": manifest["experiment"], "private_only": True,
        "base_path": str(base), "base_sha256": BASE_SHA256,
        "manifest_sha256": MANIFEST_SHA256, "builder_sha256": sha256(Path(__file__).read_bytes()),
        "native_options_path": str(options), "native_options_sha256": sha256(native_options),
        "native_option_overrides": native_values, "candidate_options_sha256": sha256(candidate_options),
        "native_options_preserved_as_exact_prefix": True, "policy": effective,
        "modified_members": [n for n in original if original[n] != candidate[n]],
        "original_member_sha256": {n: sha256(v) for n, v in sorted(original.items())},
        "candidate_member_sha256": {n: sha256(v) for n, v in sorted(candidate.items())},
        "license_members": manifest["license_members"], "validation": manifest["runtime_validation"],
        "output": str(output), "options_output": str(sidecar),
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "x", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(candidate.items()):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            archive.writestr(info, data)
    metadata["output_sha256"] = sha256(output.read_bytes())
    with sidecar.open("xb") as handle:
        handle.write(candidate_options)
    with receipt.open("x") as handle:
        json.dump(metadata, handle, indent=2, sort_keys=True)
        handle.write("\n")
    return {k: metadata[k] for k in ("output", "output_sha256", "options_output", "policy", "validation")}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", type=Path, default=BASE)
    parser.add_argument("--options", required=True, type=Path, help="Original native Iris override bytes; explicit empty file means source defaults")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps(build(args.base, args.options, args.output), indent=2))
    except (ValueError, OSError, zipfile.BadZipFile) as exc:
        parser.exit(2, str(exc) + "\n")

#!/usr/bin/env python3
"""Assemble one isolated pinned Photon pilot; never edit a runtime profile."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "patches/photon-experiments/sky-owned-atmosphere.json"
BASE = ROOT / "run-workloads/managed-photon-preview-r1/client/shaderpacks/ShaderLOD-photon-893704058bb7a4fc.zip"
BASE_SHA256 = "970d670116e28202ae6c1067cb086294995f02ad02a3b58ef2664d208590b57f"
D4 = "shaders/program/d4_deferred_shading.fsh"
HELPER = "shaders/include/shaderlod/sky_owned_atmosphere.glsl"
METADATA = "shaderlod-sky-owned-atmosphere.json"


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def members(path):
    with zipfile.ZipFile(path) as archive:
        entries = archive.infolist()
        names = [entry.filename for entry in entries]
        if len(set(names)) != len(names) or any(
            PurePosixPath(n).is_absolute() or ".." in PurePosixPath(n).parts or "\\" in n
            for n in names
        ):
            raise ValueError("Unsafe or duplicate archive member")
        return {e.filename: archive.read(e) for e in entries if not e.is_dir()}


def patch(files, manifest):
    if (manifest["schema"] != 1 or manifest["experiment"] != "photon-sky-owned-atmosphere"
            or manifest["base_sha256"] != BASE_SHA256
            or [e["path"] for e in manifest["members"]] != [D4]
            or manifest["addition"]["path"] != HELPER
            or manifest["addition"]["source_path"] != "patches/photon-experiments/sky_owned_atmosphere.glsl"):
        raise ValueError("Unexpected isolated pilot scope")
    if sha256(files["LICENSE"]) != manifest["license_sha256"]:
        raise ValueError("Upstream license changed")
    provenance = manifest["original_helper"]
    if (provenance["path"] != "shaders/include/sky/atmosphere.glsl"
            or sha256(files[provenance["path"]]) != provenance["member_sha256"]):
        raise ValueError("Original global atmosphere changed")
    entry = manifest["members"][0]
    if sha256(files[D4]) != entry["source_sha256"]:
        raise ValueError("Pinned d4 changed")
    source = files[D4].decode()
    if len(entry["replacements"]) != 3:
        raise ValueError("Expected exactly three literal d4 anchors")
    for replacement in entry["replacements"]:
        if not replacement["before"] or source.count(replacement["before"]) != 1:
            raise ValueError("Missing or ambiguous d4 anchor")
        source = source.replace(replacement["before"], replacement["after"], 1)
    if sha256(source.encode()) != entry["patched_sha256"]:
        raise ValueError("Patched d4 hash mismatch")
    addition = (ROOT / manifest["addition"]["source_path"]).read_bytes()
    if sha256(addition) != manifest["addition"]["sha256"]:
        raise ValueError("Local helper hash mismatch")
    if HELPER in files or METADATA in files:
        raise ValueError("Baseline already contains pilot members")
    return files | {D4: source.encode(), HELPER: addition}


def build(base=BASE, output=None):
    base = Path(base).resolve()
    output = Path(output or ROOT / "dev/logs/photon-sky-branch-pilot-r1/Photon-SkyOwnedAtmosphere.zip").resolve()
    if (not output.is_relative_to((ROOT / "dev/logs").resolve()) or output.suffix != ".zip"
            or output == base or output.exists()):
        raise ValueError("Use a fresh ignored dev/logs ZIP; never overwrite baseline/output")
    if sha256(base.read_bytes()) != BASE_SHA256:
        raise ValueError("Pinned generated baseline hash mismatch")
    manifest_bytes = MANIFEST.read_bytes()
    manifest = json.loads(manifest_bytes)
    original = members(base)
    files = patch(original, manifest)
    metadata = {
        "experiment": manifest["experiment"], "baseline_sha256": BASE_SHA256,
        "manifest_sha256": sha256(manifest_bytes), "policy": manifest["policy"],
        "modified_baseline_members": [n for n in original if files[n] != original[n]],
        "added_members": [HELPER, METADATA],
        "member_sha256": {n: sha256(files[n]) for n in (D4, HELPER, "LICENSE")},
        "runtime_compilation": "unverified", "performance": "unmeasured",
    }
    files[METADATA] = (json.dumps(metadata, indent=2, sort_keys=True) + "\n").encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "x", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            archive.writestr(info, data, compresslevel=9)
    return metadata | {"output": str(output), "output_sha256": sha256(output.read_bytes())}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", type=Path, default=BASE)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    print(json.dumps(build(args.base, args.output), indent=2))

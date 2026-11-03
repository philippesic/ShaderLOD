#!/usr/bin/env python3
"""Build a private ORIGINAL-only native water-reflection alpha cue; default native."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "patches/complementary-experiments/ssr-water-coverage-r1.json"
PROGRAM = "shaders/program/gbuffers_water.glsl"
REFLECTIONS = "shaders/lib/materials/materialMethods/reflections.glsl"
MODES = ("native", "water-alpha-cue")
ANCHOR = b"    /* DRAWBUFFERS:03 */\r\n    gl_FragData[0] = color;"
GUARD = (
    "{enabled} && defined MC_OS_MAC && MC_VERSION == 12101 && RP_MODE == 1"
    " && defined OVERWORLD && defined GBUFFERS_WATER && !defined COMPOSITE"
    " && !defined GBUFFERS_COLORWHEEL_TRANSLUCENT"
    " && !defined DISTANT_HORIZONS && !defined DH_WATER && !defined DH_TERRAIN"
    " && !defined VOXY && !defined VOXY_PATCH && !defined VOXY_TRANSLUCENT"
    " && WORLD_SPACE_REFLECTIONS_INTERNAL == -1 && WORLD_SPACE_REFLECTIONS == -1"
    " && WATER_REFLECT_QUALITY == 2"
)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def members_digest(files: dict[str, bytes]) -> str:
    result = hashlib.sha256()
    for name, data in sorted(files.items()):
        encoded = name.encode("utf-8")
        result.update(len(encoded).to_bytes(8, "big"))
        result.update(encoded)
        result.update(hashlib.sha256(data).digest())
    return result.hexdigest()


def read_archive(path: Path) -> dict[str, bytes]:
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate archive member")
        return {name: archive.read(name) for name in names}


def cue_block(mode: str) -> bytes:
    if mode not in MODES:
        raise ValueError("Unsupported coverage mode")
    return (
        "    // ShaderLOD SSR coverage R1: false-color final water output, NEVER a cost mode.\r\n"
        f"    #if {GUARD.format(enabled=int(mode == 'water-alpha-cue'))}\r\n"
        "    if (mat == 32000) {\r\n"
        "        float shaderlodSsrNativeAlpha = reflection.a;\r\n"
        "        vec3 shaderlodSsrCue = vec3(1.0, 0.0, 1.0); // Invalid alpha: unknown.\r\n"
        "        if (shaderlodSsrNativeAlpha >= 0.0 && shaderlodSsrNativeAlpha <= 0.001) {\r\n"
        "            shaderlodSsrCue = vec3(0.0, 0.0, 1.0); // No above-floor terrain weight.\r\n"
        "        } else if (shaderlodSsrNativeAlpha > 0.001 && shaderlodSsrNativeAlpha <= 1.0) {\r\n"
        "            float shaderlodSsrGreenWeight = 0.75 + 0.25 * shaderlodSsrNativeAlpha;\r\n"
        "            shaderlodSsrCue = mix(vec3(0.0, 0.0, 1.0), vec3(0.0, 1.0, 0.0), shaderlodSsrGreenWeight);\r\n"
        "        }\r\n"
        "        color = vec4(shaderlodSsrCue, 1.0); // Opaque witness after native fog.\r\n"
        "    }\r\n"
        "    #endif\r\n\r\n"
    ).encode()


def apply(files: dict[str, bytes], manifest: dict, mode: str = "native") -> dict[str, bytes]:
    if manifest.get("schema") != 1 or manifest.get("id") != "ssr-water-coverage-r1":
        raise ValueError("Unsupported coverage manifest")
    if members_digest(files) != manifest["original_members_digest"]:
        raise ValueError("Pinned ORIGINAL member content changed; removal input is forbidden")
    for name, digest in manifest["source_members"].items():
        if name not in files or sha256(files[name]) != digest:
            raise ValueError("Pinned member changed: " + name)
    source = files[PROGRAM]
    if source.count(ANCHOR) != 1:
        raise ValueError("Final native water output anchor must occur exactly once")
    result = dict(files)
    result[PROGRAM] = source.replace(ANCHOR, cue_block(mode) + ANCHOR, 1)
    if sha256(result[PROGRAM]) != manifest["patched_program_sha256"][mode]:
        raise ValueError("Coverage patch differs from reviewed source pin")
    if [name for name in files if files[name] != result[name]] != [PROGRAM]:
        raise ValueError("Only the final water program member may change")
    return result


def validate_options(data: bytes, manifest: dict) -> None:
    if data != b"SHADOW_QUALITY=2\n" or sha256(data) != manifest["native_options_sha256"]:
        raise ValueError("Coverage cue requires exact retained ORIGINAL native options")


def build(base: Path, output: Path, options: Path, mode: str = "native",
          manifest_path: Path = MANIFEST) -> dict:
    base, output, options = Path(base), Path(output), Path(options)
    sidecar = Path(str(output) + ".txt")
    if any(p.resolve() in (base.resolve(), options.resolve()) for p in (output, sidecar)):
        raise ValueError("Cannot overwrite original input")
    if output.exists() or sidecar.exists():
        raise ValueError("Output or native options sidecar already exists")
    manifest_bytes = Path(manifest_path).read_bytes()
    manifest = json.loads(manifest_bytes)
    if sha256(base.read_bytes()) != manifest["original_archive_sha256"]:
        raise ValueError("Pinned ORIGINAL archive required; diagnostic derivatives forbidden")
    option_bytes = options.read_bytes()
    validate_options(option_bytes, manifest)
    original = read_archive(base)
    files = apply(original, manifest, mode)
    evidence = {
        "schema": 1, "id": manifest["id"], "mode": mode, "default_mode": "native",
        "kind": "false-color-original-water-native-alpha-coverage-only",
        "original_archive_sha256": manifest["original_archive_sha256"],
        "original_members_digest": members_digest(original),
        "patched_members_digest": members_digest(files),
        "modified_upstream_members": [PROGRAM],
        "patched_program_sha256": sha256(files[PROGRAM]),
        "unchanged_reflections_sha256": sha256(files[REFLECTIONS]),
        "native_options_sha256": sha256(option_bytes),
        "license_sha256": sha256(files["License.txt"]),
        "guard": GUARD.format(enabled=int(mode == "water-alpha-cue")),
        "legend": manifest["legend"],
        "validation": {key: False for key in (
            "driver_compilation", "in_world_rendering", "actual_cue_source_draw_join",
            "water_coverage", "above_floor_terrain_hit_coverage", "quality", "performance"
        )},
    }
    names = ("shaderlod-private-ssr-coverage.json", "SHADERLOD_PRIVATE_SSR_COVERAGE.txt")
    if any(name in files for name in names):
        raise ValueError("Coverage metadata collision")
    files[names[0]] = (json.dumps(evidence, indent=2, sort_keys=True) + "\n").encode()
    files[names[1]] = (
        "PRIVATE false-color ORIGINAL water reflection-alpha coverage cue.\n"
        "Complementary Reimagined r5.9.3 by EminGT; original License.txt retained.\n"
        "Never a removal-cost mode, benchmark, quality/product candidate or redistribution grant.\n"
        "Native reflection member/equations remain byte-identical; only final qualified water RGBA is replaced.\n"
        "Green: native final alpha >0.001; blue: alpha <=0.001; magenta: invalid/unknown.\n"
        "Green encodes surviving native terrain blend weight, not radiance energy or ray count.\n"
        "Opaque cue follows native fog; postprocessing can shift displayed colors.\n"
        "Compiler may remove newly dead RGB work; this artifact MUST NOT be timed.\n"
        "Default native mode; explicit water-alpha-cue mode required. No new native setting.\n"
    ).encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "x", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(files.items()):
            entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o644 << 16
            archive.writestr(entry, data)
    with sidecar.open("xb") as stream:
        stream.write(option_bytes)
    return {**evidence, "manifest_sha256": sha256(manifest_bytes),
            "builder_sha256": sha256(Path(__file__).read_bytes()),
            "output": str(output.resolve()), "output_sha256": sha256(output.read_bytes()),
            "options_sidecar": str(sidecar.resolve())}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--options", type=Path, required=True)
    parser.add_argument("--mode", choices=MODES, default="native")
    args = parser.parse_args()
    print(json.dumps(build(args.base, args.output, args.options, args.mode), indent=2))

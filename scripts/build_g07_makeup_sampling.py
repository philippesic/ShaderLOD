#!/usr/bin/env python3
"""Deterministic private G07 MakeUp9.5g temporal AO candidate; never install."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NATIVE_SHA = "0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8"
OPTION = "SHADERLOD_G07_AO_TEMPORAL"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def transform(files):
    result = dict(files)
    config = "shaders/lib/config.glsl"
    result[config] += (f"\n// Private G07: 0 native, 1 full-work control, 2 temporal thinning.\n"
                       f"#define {OPTION} 0 // [0 1 2]\n").encode()
    prop = "shaders/shaders.properties"
    result[prop] = result[prop].replace(b"screen.AO_SCREEN = AOSTEPS AO_STRENGTH",
        f"screen.AO_SCREEN = AOSTEPS AO_STRENGTH {OPTION}".encode())
    ao = "shaders/lib/ao.glsl"
    native = files[ao].decode()
    if native.count("float dbao(float dither)") != 1:
        raise ValueError("Native AO signature mismatch")
    native = native.replace("float dbao(float dither)", "float g07NativeAo(float dither)")
    center = "    float linearDepth = texture2DLod(depthtex0, texcoord.xy, 0.0).r;"
    if native.count(center) != 1:
        raise ValueError("Native center depth mismatch")
    native = native.replace("float g07NativeAo(float dither)",
                            "float g07NativeAo(float dither, float linearDepth)").replace(center, "")
    temporal = native.replace("g07NativeAo", "g07TemporalAo")
    temporal = temporal.replace("    linearDepth = ld(linearDepth);",
        "    // Keep the complete native hand estimator.\n"
        "    if (linearDepth < 0.56) return g07NativeAo(dither, linearDepth);\n"
        "    linearDepth = ld(linearDepth);")
    temporal = temporal.replace("    for (int i = 0; i < AOSTEPS; i++) {",
        "    // Native dither repeats every16 frames; coprime5 phases cover each\n"
        "    // native radial stratum four times at every dither phase in80 frames.\n"
        "    int omitted = int(mod(float(frameCounter), 5.0));\n"
        "    for (int j = 0; j < 4; j++) {\n"
        "        int i = j + (j >= omitted ? 1 : 0);")
    temporal = temporal.replace("ambientOcclusion /= AOSTEPS;", "ambientOcclusion *= 0.25;")
    guard = ("SHADERLOD_G07_AO_TEMPORAL == 2 && AOSTEPS == 5 && AA_TYPE == 2 && AO == 1"
             " && defined DEFERRED_SHADER && defined IRIS_VERSION && MC_VERSION == 12101"
             " && !defined NETHER && !defined THE_END && !defined UNKNOWN_DIM"
             " && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH")
    wrapper = (f"\n#if {guard}\n"
        "uniform int shaderlodG07Admitted = 0;\n"
        "uniform int shaderlodBridgeVersion = 0;\n"
        "uniform int shaderlodEnabled = 0;\n"
        "uniform int shaderlodPreset = -1;\n"
        "uniform int shaderlodTemporalVersion = 0;\n"
        "uniform int shaderlodHistoryReset = 1;\n" + temporal + "\n#endif\n"
        "float dbao(float dither) {\n"
        + center + "\n"
        f"#if {guard}\n"
        "    if (shaderlodG07Admitted == 1 && shaderlodBridgeVersion == 2\n"
        "        && shaderlodEnabled == 1 && shaderlodPreset == 1\n"
        "        && shaderlodTemporalVersion == 1 && shaderlodHistoryReset == 0)\n"
        "        return g07TemporalAo(dither, linearDepth);\n"
        "#endif\n"
        "    return g07NativeAo(dither, linearDepth);\n}\n")
    result[ao] = (native + wrapper).encode()
    return result


def build(base, output):
    if sha(base.read_bytes()) != NATIVE_SHA:
        raise ValueError("Exact pinned native MakeUp9.5g archive required")
    if output.resolve() == base.resolve():
        raise ValueError("Cannot overwrite native input")
    with zipfile.ZipFile(base) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or any(Path(n).is_absolute() or ".." in Path(n).parts or "\\" in n for n in names):
            raise ValueError("Unsafe or duplicate ZIP members")
        files = {n: archive.read(n) for n in names}
    result = transform(files)
    changed = {n: {"before": sha(files[n]), "after": sha(result[n])} for n in files if files[n] != result[n]}
    assert set(changed) == {"shaders/lib/config.glsl", "shaders/lib/ao.glsl", "shaders/shaders.properties"}
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for n in sorted(result):
            info = zipfile.ZipInfo(n, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = (0o40755 if n.endswith("/") else 0o100644) << 16
            info.create_system = 3
            archive.writestr(info, result[n], compresslevel=9)
    native = json.loads((ROOT / "src/main/resources/assets/shaderlod/makeupquad/native100.json").read_text())
    # Complete native100 sidecars; never read or alter a live profile.
    for label, mode in (("off", 0), ("control", 1), ("on", 2)):
        options = dict(native, **{OPTION: str(mode)})
        Path(str(output) + f".{label}.txt").write_text("".join(f"{k}={v}\n" for k, v in sorted(options.items())))
    receipt = {"base": str(base), "base_sha256": NATIVE_SHA, "artifact": str(output),
        "sha256": sha(output.read_bytes()), "changed_members": changed, "default_off": True,
        "native_options": native, "runtime_required": "G07-admission build; core2/enabled1/Performance1/temporal1; noDH; native100; vanilla resources and pinned stack",
        "limits": "Static80-frame pre-sqrt mean only; nonlinear mapping/TAA/motion not equivalent. No game/driver/performance validation."}
    Path(str(output) + ".json").write_text(json.dumps(receipt, indent=2) + "\n")
    return receipt


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--base", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    args = p.parse_args()
    print(json.dumps(build(args.base, args.output), indent=2))

#!/usr/bin/env python3
"""Build a private G04 MakeUp 9.5g dead-material sky experiment; never install.

Only blockType==0 skips the native analytic sky/cloud expression. Water/glass,
native cloud kernel, sample counts, AA, passes and output suffix stay unchanged.
Default OFF. Headless checks cannot establish driver work, quality or speed.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import zipfile

NATIVE = "0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8"
CALLER = "shaders/common/water_blocks_fragment.glsl"
CONFIG = "shaders/lib/config.glsl"
PROPERTIES = "shaders/shaders.properties"
OPTION = "SHADERLOD_G04_UNUSED_SKY"
DEFINE = f"\n// Private G04: exact unused material sky/cloud work; default OFF.\n#define {OPTION} 0 // [0 1]\n"
# Repeat this condition at the two syntax seams. A helper boolean #define
# would itself be discoverable as an unintended native Iris shader option.
CONDITION = f"{OPTION} == 1 && defined IS_IRIS && defined IRIS_VERSION && IRIS_VERSION == 10812 && defined MC_VERSION && MC_VERSION == 12101 && (defined MC_OS_MAC || defined MC_OS_WINDOWS) && defined WATER_F && !defined UNKNOWN_DIM && !defined THE_END && !defined NETHER && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH && V_CLOUDS == 1 && CLOUD_VOL_STYLE == 0 && CLOUD_STEPS_AVG == 10 && AA_TYPE == 2"
START = "    vec3 skyColorReflect;\n"
END = "    if(blockType > 2.5) {  // Water\n        #ifdef VANILLA_WATER"
OPEN = f"""    #if {CONDITION}
    // Native vertex assigns 0 to ordinary translucent materials. Neither
    // native material branch consumes skyColorReflect for this exact class.
    // Keep every native expression for reflective materials, including NaN.
    if (blockType == 0.0) {{
        skyColorReflect = vec3(0.0);
    }} else {{
    #endif
"""
CLOSE = f"    #if {CONDITION}\n    }}\n    #endif\n"
CUE_INIT = f"    #if {CONDITION}\n    bool shaderlodG04Skipped = false;\n    #endif\n"
CUE_SET = "        shaderlodG04Skipped = true;\n"
CUE_PAINT = f"""    #if {CONDITION}
    // Untimed cue: green skipped upward cloud loop; yellow skipped sky only.
    // Magenta retains native material work; this is not a physical fetch counter.
    bool shaderlodG04CloudLoop = false;
    #ifdef CLOUD_REFLECTION
    shaderlodG04CloudLoop = normalize((gbufferModelViewInverse * vec4(reflectWaterVector * far, 1.0)).xyz).y > 0.0;
    #endif
    blockColor.rgb = shaderlodG04Skipped ? (shaderlodG04CloudLoop ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 1.0, 0.0)) : vec3(1.0, 0.0, 1.0);
    #endif
"""


def sha(data):
    return hashlib.sha256(data).hexdigest()


def read_archive(path):
    raw = Path(path).read_bytes()
    if sha(raw) != NATIVE:
        raise ValueError("Exact native MakeUp 9.5g archive required")
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        names = z.namelist()
        if len(names) != len(set(names)) or any(
                PurePosixPath(n).is_absolute() or ".." in PurePosixPath(n).parts
                or "\\" in n or "\x00" in n for n in names):
            raise ValueError("Unsafe or duplicate native archive member")
        return {n: z.read(n) for n in names if not n.endswith("/")}


def once(source, before, after):
    if source.count(before) != 1:
        raise ValueError("Pinned source anchor missing or ambiguous")
    return source.replace(before, after, 1)


def patch(files, cue=False):
    result = dict(files)
    # Source is admitted only through the complete archive hash above.
    result[CONFIG] += DEFINE.encode()
    caller = files[CALLER].decode()
    caller = once(caller, START, START + (CUE_INIT if cue else "") + OPEN)
    caller = once(caller, END, CLOSE + END)
    if cue:
        caller = once(caller, "        skyColorReflect = vec3(0.0);\n",
                      "        skyColorReflect = vec3(0.0);\n" + CUE_SET)
        caller = once(caller, '    #include "/src/writebuffers.glsl"',
                      CUE_PAINT + '    #include "/src/writebuffers.glsl"')
    result[CALLER] = caller.encode()
    props = files[PROPERTIES].decode()
    lines = props.splitlines(keepends=True)
    matches = [i for i, line in enumerate(lines) if line.startswith("screen = ")]
    if len(matches) != 1:
        raise ValueError("Native public options screen anchor mismatch")
    i = matches[0]
    lines[i] = lines[i].rstrip("\r\n") + " [SHADERLOD_G04]\n"
    props = "".join(lines) + f"\n# Private G04 control; no native profile/option changes.\nscreen.SHADERLOD_G04 = {OPTION}\n"
    result[PROPERTIES] = props.encode()
    return result


def archive_bytes(files):
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 26, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            z.writestr(info, data)
    return out.getvalue()


def sidecar(native, enabled):
    text = native.decode()
    if OPTION in text or "SHADERLOD_" in text:
        raise ValueError("Use an original native sidecar, without generated requests")
    # The complete original sidecar bytes remain an exact prefix.
    return native + (b"" if not native or native.endswith(b"\n") else b"\n") + f"{OPTION}={int(enabled)}\n".encode()


def build(original, options, output, cue=False):
    original, options, output = Path(original), Path(options), Path(output)
    files = read_archive(original)
    native_options = options.read_bytes()
    control_options = sidecar(native_options, False)
    result = patch(files, cue)
    changed = sorted(n for n in files if result[n] != files[n])
    assert changed == sorted([CALLER, CONFIG, PROPERTIES])
    assert result["LICENSE"] == files["LICENSE"]
    metadata = {
        "id": "G04", "family": "MakeUp Ultra Fast", "version": "9.5g",
        "original_sha256": NATIVE, "default_off": True, "cue": cue,
        "option": OPTION, "changed_members": changed,
        "preimage_hashes": {n: sha(files[n]) for n in changed},
        "source_hashes": {n: sha(result[n]) for n in changed},
        "native_sidecar_sha256": sha(native_options),
        "scope": "Mac/Windows MC1.21.1/Iris1.8.12 world0 WATER_F, AA2, static10/style0 clouds; DH/Voxy refuse",
        "status": "BUILT_SOURCE_EXPERIMENT_RUNTIME_UNTESTED",
        "limits": "Native driver may already eliminate dead work. No rendered quality, physical fetch or speed claim. NeoForge/Sodium stack must be verified by root.",
    }
    result["SHADERLOD_G04.json"] = (json.dumps(metadata, sort_keys=True, indent=2) + "\n").encode()
    # Preserve all native licensing and editable sources. This file identifies
    # the only transformation and its control, rather than replacing ancestry.
    result["SHADERLOD_G04.txt"] = (
        "Private MakeUp Ultra Fast 9.5g derivative. Original LGPL-3.0-or-later\n"
        "licenses/attribution retained. G04 additions LGPL-3.0-or-later.\n"
        "One exact dead-material sky/cloud guard; no quality preset change.\n"
        f"Public Iris option {OPTION}: 0 native (default), 1 guarded.\n"
        "Use native original -> control0 -> On1 -> return original.\n"
        "Cue builds are untimed diagnostics only. Full reproducible builder\n"
        "and source preimages accompany this artifact in the G04 handoff.\n"
    ).encode()
    paths = [output, Path(str(output) + ".txt"), output.with_suffix(".json")]
    if any(p.exists() for p in paths):
        raise ValueError("Fresh output paths required")
    output.parent.mkdir(parents=True, exist_ok=True)
    raw = archive_bytes(result)
    output.write_bytes(raw)
    paths[1].write_bytes(control_options)
    receipt = dict(metadata, artifact=str(output.resolve()), sha256=sha(raw),
                   sidecar_sha256=sha(paths[1].read_bytes()),
                   unchanged_native_members=len(files) - len(changed))
    paths[2].write_text(json.dumps(receipt, indent=2) + "\n")
    return receipt


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("original", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--options", required=True, type=Path)
    parser.add_argument("--cue", action="store_true", help="Untimed semantic coverage build")
    args = parser.parse_args()
    print(json.dumps(build(args.original, args.options, args.output, args.cue), indent=2))

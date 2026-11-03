#!/usr/bin/env python3
"""Private pinned Bliss sky SSAO dead-work experiment; no runtime profile writes."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "dependencies/upstream/pack-expansion/Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip"
OPTIONS = ROOT / "dev/logs/cross-shader-performance-r3/bliss-shader-lod-next-r1/original-sidecar.txt"
OUT = ROOT / "dev/logs/speed-replication-r1/bliss-sky-elision"
BASE_SHA = "f41db92acc585fe9ffe0cc124d6ecaea27cef9ded91129ebc2a8e60226947b2c"
OPTIONS_SHA = "cc80720a057a3beb7da884e5cd54df371fcae990cc7ca601accc271f7d1d73b2"
MEMBER = "shaders/dimensions/composite.fsh"
MEMBER_SHA = "4d075c2ce671903abb4c3d98153581a3fceedc72966cf65461b5a91fedd2c5d6"
OPTION = "SHADERLOD_BLISS_SKY_ELISION"
ELIGIBLE = "defined(IS_IRIS) && defined(IRIS_VERSION) && IRIS_VERSION == 10812 && defined(MC_VERSION) && MC_VERSION == 12101 && defined(MC_OS_MAC) && defined(OVERWORLD_SHADER) && !defined(DISTANT_HORIZONS) && !defined(VOXY)"
ANCHOR = "\t\tvec2 SSAO_SSS = SSAO(viewPos, FlatNormals, hand, isLeaf, noise);"

def sha(b):
    return hashlib.sha256(b).hexdigest()

def members(path):
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        if len(names) != len(set(names)) or any(PurePosixPath(n).is_absolute() or ".." in PurePosixPath(n).parts or "\\" in n for n in names):
            raise ValueError("Unsafe or duplicate ZIP member")
        return {n: z.read(n) for n in names if not n.endswith("/")}

def patch(original, mode):
    if mode not in (0, 1, 2):
        raise ValueError("Mode must be native 0, full-work control 1, or candidate 2")
    raw = original[MEMBER]
    if sha(raw) != MEMBER_SHA:
        raise ValueError("Pinned Bliss SSAO member changed")
    text = raw.decode().replace("\r\n", "\n")
    header = f"""// Private local ShaderLOD sky dead-work experiment; upstream attribution retained.
#define {OPTION} {mode} // [0 1 2]
#if {OPTION} != 0 && {ELIGIBLE}
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;
uniform int shaderlodPreset = -1;
#endif

"""
    replacement = f"""#if {OPTION} == 2 && {ELIGIBLE}
        // Native code replaces this result for sky immediately below. No sample
        // coordinates, derivatives, geometry SSAO/SSS, or native override change.
        vec2 SSAO_SSS;
        if (swappedDepth >= 1.0 && shaderlodBridgeVersion == 2 && shaderlodEnabled == 1 && shaderlodPreset >= 0 && shaderlodPreset <= 2) {{
            SSAO_SSS = vec2(1.0, 0.0);
        }} else {{
            SSAO_SSS = SSAO(viewPos, FlatNormals, hand, isLeaf, noise);
        }}
#else
{ANCHOR}
#endif"""
    if text.count(ANCHOR) != 1:
        raise ValueError("Missing/ambiguous SSAO caller")
    changed = header + text.replace(ANCHOR, replacement)
    return original | {MEMBER: changed.replace("\n", "\r\n").encode()}

def build(base=BASE, options=OPTIONS, output_dir=OUT):
    base, options, output_dir = Path(base), Path(options), Path(output_dir).resolve()
    if not output_dir.is_relative_to(OUT.resolve()):
        raise ValueError("Only fresh outputs under owned ignored evidence directory")
    if sha(base.read_bytes()) != BASE_SHA or sha(options.read_bytes()) != OPTIONS_SHA:
        raise ValueError("Use pinned original archive and authentic retained native option bytes")
    original = members(base)
    native = options.read_bytes()
    if re.search(rb"(?m)^SHADERLOD_", native):
        raise ValueError("Original options contain private fields")
    paths = [output_dir / f"Bliss-SkyElision-{name}-r1.zip" for name in ("disabled", "control", "candidate")]
    destinations = [p for path in paths for p in (path, Path(str(path) + ".txt"))] + [output_dir / "manifest.json"]
    if any(p.exists() for p in destinations):
        raise ValueError("Refuse overwrite of evidence")
    output_dir.mkdir(parents=True, exist_ok=True)
    receipt = {"experiment": "bliss-sky-elision-r1", "private_only": True, "base": str(base), "base_sha256": BASE_SHA,
               "native_options": str(options), "native_options_sha256": OPTIONS_SHA,
               "builder_sha256": sha(Path(__file__).read_bytes()), "changed_members": [MEMBER],
               "original_member_sha256": {n: sha(b) for n, b in sorted(original.items())}, "outputs": {},
               "runtime": "UNVERIFIED", "gain": "UNMEASURED", "controls": "bridge2/enabled1/preset0..2; absent preset=-1; Off/Quality native"}
    for mode, path in enumerate(paths):
        changed = patch(original, mode)
        with zipfile.ZipFile(path, "x", compression=zipfile.ZIP_DEFLATED) as z:
            for n, b in sorted(changed.items()):
                info = zipfile.ZipInfo(n, (1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o100644 << 16
                z.writestr(info, b)
        sidecar = Path(str(path) + ".txt")
        sidecar.write_bytes(native + (b"" if native.endswith(b"\n") else b"\n") + f"{OPTION}={mode}\n".encode())
        label = ("disabled", "control", "candidate")[mode]
        receipt["outputs"][label] = {"path": str(path), "sha256": sha(path.read_bytes()), "options": str(sidecar),
            "options_sha256": sha(sidecar.read_bytes()), "mode": mode, "member_sha256": {n: sha(b) for n, b in sorted(changed.items())}}
    (output_dir / "manifest.json").write_text(json.dumps(receipt, indent=2, sort_keys=True) + "\n")
    return receipt

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=OUT)
    args = parser.parse_args()
    result = build(output_dir=args.output_dir)
    print(json.dumps({"outputs": result["outputs"], "runtime": result["runtime"]}, indent=2))

#!/usr/bin/env python3
"""Build a private, source-pinned BSL receiver quadrature experiment.

No profile/client is changed. Source assembly does not establish driver work,
quality, custom-uniform activation, sampler state or a performance improvement.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path, PurePosixPath
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "patches/bsl-experiments/receiver-budget-r1.json"
MANIFEST_SHA256 = "2d6124dad1c35dbb9a0c3997c6f4074bc871091e4dde7392e7fd47b8d325be86"
BASE = ROOT / "dependencies/upstream/other-pack-research/BSL_v10.1.8.zip"
IRIS = ROOT / "dependencies/runtime/iris-neoforge-1.8.12+mc1.21.1.jar"
RECEIPT = ROOT / "dev/logs/bsl-sanity-r7/original/captures/original-r7-still.json"
OPTIONS = ROOT / "dev/logs/bsl-sanity-r7/original-options.txt"
OUTPUT_ROOT = ROOT / "dev/logs/mechanism-evaluation-r2/bsl"
HELPER = ROOT / "shaderpacks/common/shaders/include/shaderlod/receiver_footprint_budget_r1.glsl"
OPTION = "SHADERLOD_BSL_RECEIVER"
FLOOR = "shaderlodReceiverReductionPermission"
TERRAIN = "shaders/program/gbuffers_terrain.glsl"
SHADOWS = "shaders/lib/lighting/shadows.glsl"
PROPERTIES = "shaders/shaders.properties"

GATE = """// Private receiver experiment: native is the source/default option.
#define SHADERLOD_BSL_RECEIVER 0 // [0 1]
#if SHADERLOD_BSL_RECEIVER == 1 && defined IS_IRIS && defined IRIS_VERSION && IRIS_VERSION == 10812 && defined MC_VERSION && MC_VERSION == 12101 && defined MC_OS_MAC && defined OVERWORLD && defined SHADOW && defined SHADOW_FILTER && defined TAA && defined FXAA && TAA_MODE == 0 && !defined TAA_SELECTIVE && !defined ADVANCED_MATERIALS && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH && SHADOW_PIXEL == 0 && SHADOW_BIAS == 0 && !defined HALF_LAMBERT && !defined WATER_CAUSTICS && !defined MULTICOLORED_BLOCKLIGHT && !defined MCBL_SS
#define SHADERLOD_BSL_RECEIVER_ACTIVE
#include "/lib/shaderlod/receiver_footprint_budget_r1.glsl"
uniform float shaderlodReceiverReductionPermission;
float shaderlodBslReceiverWeight = 0.0;
#endif
"""

HOIST = """
        #ifdef SHADERLOD_BSL_RECEIVER_ACTIVE
        // SHADOW_PIXEL=0, SHADOW_BIAS=0 and HALF_LAMBERT absent: native
        // receiver bias changes z only, so this is the exact native UV map.
        // This site is in uniform main flow after reconverged native helpers,
        // before main discard/material/receiver branches in the certified
        // no-PBR path. Never take derivatives in GetShadow.
        vec3 shaderlodRawShadow = ToShadow(worldPos);
        float shaderlodDistort = length(shaderlodRawShadow.xy) * shadowMapBias + (1.0 - shadowMapBias);
        vec3 shaderlodShadow = DistortShadow(shaderlodRawShadow, shaderlodDistort);
        vec2 shaderlodDx = dFdx(shaderlodShadow.xy);
        vec2 shaderlodDy = dFdy(shaderlodShadow.xy);
        // Native ring radius 1 texel + conservative nearest/linear footprint
        // 1 texel. Exact source has no shadow mipmap requests. Actual texture/
        // sampler/encoding state must still be certified by the runtime owner.
        vec2 shaderlodSupport = vec2(2.0 / shadowMapResolution);
        bool shaderlodEligible = mat == 0.0 && albedo.a >= 0.999 &&
            basicSubsurface == 0.0 && subsurface == 0.0 &&
            all(greaterThan(shaderlodShadow.xy, shaderlodSupport)) &&
            all(lessThan(shaderlodShadow.xy, vec2(1.0) - shaderlodSupport)) &&
            shaderlodShadow.z > 0.0 && shaderlodShadow.z < 1.0 &&
            shaderlodDistort > 0.0 && shadowMapResolution == 2048 && shadowDistance == 256.0;
        shaderlodBslReceiverWeight = shaderlod_receiver_reduction_weight_r1(
            shaderlodEligible, shaderlodDx, shaderlodDy, shaderlodSupport, 1.0);
        // A linked uniform (1 candidate / 0 native-equivalent) is not a GLSL
        // compile constant. Unknown values use native; actual driver work and
        // any optimizer early-out remain an observation, never an assumption.
        shaderlodBslReceiverWeight *= float(shaderlodReceiverReductionPermission == 1.0);
        #endif
"""

FILTER = """
#ifdef SHADERLOD_BSL_RECEIVER_ACTIVE
// Native center/cardinals retained at their exact locations. The four native
// diagonal weights fall continuously 1->0 while cardinal weights rise 1->2.
// Sum=9 and centroid=0 throughout; variance changes .44->4/9 at the plateau.
// This is approximate quadrature, not a bound on visibility or color error.
vec3 ShaderlodBslFilteredShadow(vec3 shadowPos, float offset, float subsurface) {
    float w = shaderlodBslReceiverWeight;
    if (!(w > 0.0 && w <= 1.0) || subsurface != 0.0 ||
        !(shadowPos.z > 0.0 && shadowPos.z < 1.0) || !(offset > 0.0 && offset < 1.0))
        return SampleFilteredShadow(shadowPos, offset, subsurface);
    float shadow0 = 0.0;
    for (int j = 0; j < 5; j++) {
        int i = j == 0 ? 0 : 2 * j - 1;
        float weight = j == 0 ? 1.0 : 1.0 + w;
        vec2 shadowOffset = shadowOffsets[i] * offset;
        shadow0 += weight * texture2DShadow(shadowtex0, vec3(shadowPos.st + shadowOffset, shadowPos.z));
    }
    if (w < 1.0) {
        for (int i = 2; i < 9; i += 2) {
            vec2 shadowOffset = shadowOffsets[i] * offset;
            shadow0 += (1.0 - w) * texture2DShadow(shadowtex0, vec3(shadowPos.st + shadowOffset, shadowPos.z));
        }
    }
    shadow0 /= 9.0;
    vec3 shadowCol = vec3(0.0);
    #ifdef SHADOW_COLOR
    if (shadow0 < 0.999) {
        for (int j = 0; j < 5; j++) {
            int i = j == 0 ? 0 : 2 * j - 1;
            float weight = j == 0 ? 1.0 : 1.0 + w;
            vec2 shadowOffset = shadowOffsets[i] * offset;
            vec3 shadowColSample = texture2D(shadowcolor0, shadowPos.st + shadowOffset).rgb *
                         texture2DShadow(shadowtex1, vec3(shadowPos.st + shadowOffset, shadowPos.z));
            shadowCol += weight * shadowColSample;
        }
        if (w < 1.0) {
            for (int i = 2; i < 9; i += 2) {
                vec2 shadowOffset = shadowOffsets[i] * offset;
                vec3 shadowColSample = texture2D(shadowcolor0, shadowPos.st + shadowOffset).rgb *
                             texture2DShadow(shadowtex1, vec3(shadowPos.st + shadowOffset, shadowPos.z));
                shadowCol += (1.0 - w) * shadowColSample;
            }
        }
        shadowCol /= 9.0;
        #ifdef WATER_CAUSTICS
        shadowCol *= 4.0;
        #endif
    }
    #endif
    shadow0 *= mix(shadow0, 1.0, subsurface);
    shadowCol *= shadowCol;
    return clamp(shadowCol * (1.0 - shadow0) + shadow0, vec3(0.0), vec3(16.0));
}
#endif

"""


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def read_members(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or any(
            PurePosixPath(n).is_absolute() or ".." in PurePosixPath(n).parts or
            "\\" in n or "\x00" in n for n in names
        ):
            raise ValueError("Unsafe or duplicate archive member")
        return {n: archive.read(n) for n in names if not n.endswith("/")}


def replace_once(source, before, after):
    before, after = before.encode(), after.encode()
    if source.count(before) != 1:
        raise ValueError("Missing or ambiguous pinned source anchor")
    return source.replace(before, after)


def crlf(text):
    return text.replace("\n", "\r\n")


def patch(files, manifest, helper, mode):
    if mode not in {"native", "candidate", "native-equivalent"}:
        raise ValueError("Unknown private mode")
    for name, digest in manifest["source_members"].items():
        if name not in files or sha256(files[name]) != digest:
            raise ValueError("Pinned source member mismatch: " + name)
    if sha256(helper) != manifest["helper_sha256"]:
        raise ValueError("Unreviewed shared policy")
    result = dict(files)
    result[TERRAIN] = replace_once(files[TERRAIN], "#ifdef FSH\r\n", "#ifdef FSH\r\n" + crlf(GATE))
    result[TERRAIN] = replace_once(result[TERRAIN], "\t\tvec3 worldPos = ToWorld(viewPos);\r\n",
                                   "\t\tvec3 worldPos = ToWorld(viewPos);\r\n" + crlf(HOIST))
    anchor = "vec3 GetShadow(vec3 worldPos, vec3 normal, float NoL, float subsurface, float skylight) {\r\n    #if SHADOW_PIXEL > 0"
    result[SHADOWS] = replace_once(files[SHADOWS], anchor, crlf(FILTER) + anchor)
    call = "    vec3 shadow = SampleFilteredShadow(shadowPos, offset, subsurface);"
    replacement = """    #ifdef SHADERLOD_BSL_RECEIVER_ACTIVE
    vec3 shadow = ShaderlodBslFilteredShadow(shadowPos, offset, subsurface);
    #else
    vec3 shadow = SampleFilteredShadow(shadowPos, offset, subsurface);
    #endif"""
    result[SHADOWS] = replace_once(result[SHADOWS], call, crlf(replacement))
    # Only private menu/control append; no program enable/scale/filter/graph edits.
    expression = manifest["permission_expressions"]["0" if mode == "native-equivalent" else "1"]
    result[PROPERTIES] = files[PROPERTIES] + crlf("\n# Private local receiver experiment\n" +
        "screen.SHADERLOD_BSL_RECEIVER = " + OPTION + "\n" +
        "uniform.float." + FLOOR + " = " + expression + "\n").encode()
    result["shaders/lib/shaderlod/receiver_footprint_budget_r1.glsl"] = helper
    return result


def validate_inputs(base, options, receipt, iris, manifest):
    for path, digest, what in [(base, manifest["base_sha256"], "original archive"),
                              (options, manifest["native_sidecar_sha256"], "native sidecar"),
                              (receipt, manifest["native_receipt_sha256"], "native receipt"),
                              (iris, manifest["iris_sha256"], "Iris binary")]:
        if sha256(Path(path).read_bytes()) != digest:
            raise ValueError("Unqualified " + what)
    evidence = json.loads(Path(receipt).read_bytes())
    values = evidence["loadedIrisOptions"]
    canonical = json.dumps(values, sort_keys=True, separators=(",", ":")).encode()
    if len(values) != 384 or sha256(canonical) != manifest["native_options_map_sha256"]:
        raise ValueError("Incomplete or different actual native option map")
    if evidence["irisFallback"] or evidence["dhPresent"] or evidence["dimension"] != "minecraft:overworld":
        raise ValueError("Unqualified native render receipt")
    if evidence["selectedArchiveSha256"] != manifest["base_sha256"]:
        raise ValueError("Receipt selected archive mismatch")
    return evidence


def build(base=BASE, options=OPTIONS, receipt=RECEIPT, iris=IRIS, output=None, mode="candidate"):
    manifest_raw = MANIFEST.read_bytes()
    if sha256(manifest_raw) != MANIFEST_SHA256:
        raise ValueError("Unreviewed private experiment manifest")
    manifest = json.loads(manifest_raw)
    output = Path(output).resolve()
    sidecar, record = Path(str(output) + ".txt"), output.with_suffix(".json")
    if output.suffix != ".zip" or not output.is_relative_to(OUTPUT_ROOT.resolve()) or any(
        p.exists() for p in (output, sidecar, record)
    ):
        raise ValueError("Use fresh ZIP and sidecars under ignored BSL evidence directory")
    native = validate_inputs(base, options, receipt, iris, manifest)
    files = read_members(base)
    result = patch(files, manifest, HELPER.read_bytes(), mode)
    output.parent.mkdir(parents=True, exist_ok=True)
    # Fixed metadata makes private output reproducible. Every author member and
    # license byte is retained unless named as one of the three patched members.
    with zipfile.ZipFile(output, "x", compression=zipfile.ZIP_DEFLATED) as archive:
        for name in sorted(result):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, result[name])
    native_sidecar = Path(options).read_bytes()
    sidecar.write_bytes(native_sidecar + (OPTION + "=" + ("0" if mode == "native" else "1") + "\n").encode())
    evidence = {
        "experiment": manifest["experiment"], "mode": mode, "private_only": True,
        "source_only": True, "base_sha256": manifest["base_sha256"],
        "archive": str(output), "archive_sha256": sha256(output.read_bytes()),
        "sidecar_sha256": sha256(sidecar.read_bytes()), "manifest_sha256": sha256(manifest_raw),
        "builder_sha256": sha256(Path(__file__).read_bytes()), "helper_sha256": sha256(HELPER.read_bytes()),
        "original_receipt": str(Path(receipt).resolve()), "original_receipt_sha256": manifest["native_receipt_sha256"],
        "native_option_count": len(native["loadedIrisOptions"]),
        "native_options_map_sha256": manifest["native_options_map_sha256"],
        "changed_members": {k: {"original": sha256(files[k]), "candidate": sha256(result[k])}
                            for k in files if result[k] != files[k]},
        "added_members": {k: sha256(result[k]) for k in result if k not in files},
        "driver_compiled": False, "rendered": False, "avoided_work_verified": False,
        "quality_accepted": False, "performance_accepted": False,
        "native_equivalent_note": "Same GLSL with runtime uniform permission 0; compiler scheduling/branch effects and linked value still require actual observation",
        "runtime_obligations": manifest["runtime_obligations"],
    }
    record.write_text(json.dumps(evidence, indent=2) + "\n")
    return evidence


def reduction_weight(eligible, dx, dy, halfextent, scale=1.0):
    """Independent CPU reference of the shared mathematical contract, not GLSL execution."""
    if not eligible or not all(math.isfinite(x) for x in (*dx, *dy, *halfextent, scale)):
        return 0.0
    if not all(0 < x <= 1 for x in halfextent) or not 1 <= scale <= 16:
        return 0.0
    det = dx[0] * dy[1] - dy[0] * dx[1]
    largest = max(sum(x*x for x in dx), sum(x*x for x in dy))
    if not 1e-20 < largest < 1e4 or not abs(det) > .01 * largest:
        return 0.0
    ex = (abs(dy[1])*halfextent[0] + abs(dy[0])*halfextent[1])*scale/abs(det)
    ey = (abs(dx[1])*halfextent[0] + abs(dx[0])*halfextent[1])*scale/abs(det)
    r2 = ex*ex + ey*ey
    if not 0 <= r2 < .25:
        return 0.0
    t = min(1, max(0, (r2-.0625)/(.25-.0625)))
    return 1-t*t*(3-2*t)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--base", type=Path, default=BASE)
    p.add_argument("--options", type=Path, default=OPTIONS)
    p.add_argument("--receipt", type=Path, default=RECEIPT)
    p.add_argument("--iris", type=Path, default=IRIS)
    p.add_argument("--output", type=Path, required=True)
    p.add_argument("--mode", choices=("native", "candidate", "native-equivalent"), default="candidate")
    print(json.dumps(build(**vars(p.parse_args())), indent=2))


if __name__ == "__main__":
    main()

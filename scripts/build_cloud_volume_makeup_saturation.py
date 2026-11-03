#!/usr/bin/env python3
"""Assemble the private B16 source preview; certified runtime assembly fails closed.

No client, profile or existing candidate is changed. Native sampler equivalence,
driver compilation, coverage, quality and speed are separate pending checks.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path, PurePosixPath
import struct
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "patches/cloud-volume-experiments/makeup-saturation-r3.json"
BASE = ROOT / "dev/prism-testing-r1/staging/packs/makeup-ultra-fast-shaders/shaderpacks/MakeUp-UltraFast-9.5g.zip"
IRIS = ROOT / "dependencies/runtime/iris-neoforge-1.8.12+mc1.21.1.jar"
OUTPUT = ROOT / "dev/logs/mechanism-evaluation-r3/cloud-volume/source-preview"
CLOUD = "shaders/lib/volumetric_clouds.glsl"
PROPERTIES = "shaders/shaders.properties"
OPTION = "SHADERLOD_MAKEUP_CLOUD_SATURATION"
PERMISSION = "shaderlodMakeupCloudSaturationPermission"
GATE = """// Private B16 source experiment; unrecognized scope retains native source.
#define SHADERLOD_MAKEUP_CLOUD_SATURATION 0 // [0 1]
#if SHADERLOD_MAKEUP_CLOUD_SATURATION == 1 && defined IS_IRIS && defined IRIS_VERSION && IRIS_VERSION == 10812 && defined MC_VERSION && MC_VERSION == 12101 && defined MC_OS_MAC && defined DEFERRED_SHADER && !defined UNKNOWN_DIM && !defined THE_END && !defined NETHER && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH && V_CLOUDS == 1 && CLOUD_VOL_STYLE == 0 && CLOUD_STEPS_AVG == 10
#define SHADERLOD_MAKEUP_CLOUD_SATURATION_ACTIVE
uniform float shaderlodMakeupCloudSaturationPermission;
#endif

"""
EXIT = """
            #ifdef SHADERLOD_MAKEUP_CLOUD_SATURATION_ACTIVE
            // First-contact density is fixed here. The ideal scalar recurrence
            // is saturated; actual GPU division parity remains a runtime check.
            // A runtime uniform keeps the same source in the overhead control.
            if (shaderlodMakeupCloudSaturationPermission == 1.0 &&
                opacity_dist > 0.0 && opacity_dist < 1.0e30 &&
                cloud_value >= opacity_dist) {
                break;
            }
            #endif
"""


def sha(data):
    return hashlib.sha256(data).hexdigest()


def read_members(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or any(
            PurePosixPath(n).is_absolute() or ".." in PurePosixPath(n).parts
            or "\\" in n or "\x00" in n for n in names
        ):
            raise ValueError("Unsafe or duplicate archive member")
        return {n: archive.read(n) for n in names if not n.endswith("/")}


def once(data, old, new):
    old, new = old.encode(), new.encode()
    if data.count(old) != 1:
        raise ValueError("Missing or ambiguous pinned source anchor")
    return data.replace(old, new)


def patch(files, manifest, mode):
    if mode not in {"candidate", "native-equivalent"}:
        raise ValueError("Unknown assembly arm")
    for name, digest in manifest["source_members"].items():
        if name not in files or sha(files[name]) != digest:
            raise ValueError("Pinned member mismatch: " + name)
    result = dict(files)
    source = GATE.encode() + files[CLOUD]
    for op, axes, factor in [("=", "xz", "HI"), ("+=", "zx", "LOW")]:
        expr = f"(intersection_pos.{axes} * 0.0002777777777777778) + (frameTimeCounter * CLOUD_{factor}_FACTOR)"
        line = f"current_value {op} texture2D(gaux2, {expr}).r;"
        declaration = "float " if op == "=" else ""
        old = declaration + line
        replacement = ("\n            #ifdef SHADERLOD_MAKEUP_CLOUD_SATURATION_ACTIVE\n"
                       f"            {declaration}current_value {op} texture2DLod(gaux2, {expr}, 0.0).r;\n"
                       "            #else\n            " + old +
                       "\n            #endif")
        source = once(source, old, replacement)
    source = once(source, "            intersection_pos += increment;", EXIT + "\n            intersection_pos += increment;")
    result[CLOUD] = source
    expression = manifest["permission_expressions"]["1" if mode == "candidate" else "0"]
    result[PROPERTIES] = files[PROPERTIES] + (
        "\n# Private B16 experiment; no program/filter/profile changes.\n"
        f"screen.SHADERLOD_MAKEUP_CLOUD = {OPTION}\n"
        f"uniform.float.{PERMISSION} = {expression}\n").encode()
    return result


def validate_certificate(manifest, certificate_path, sidecar, iris, base=BASE):
    # Pins require a reviewed actual original capture; source defaults or a
    # structurally plausible/sparse supplied JSON cannot authorize assembly.
    for key in ("approved_runtime_certificate_sha256", "native_options_count",
                "native_options_map_sha256", "native_sidecar_sha256"):
        if not manifest.get(key):
            raise ValueError("Actual original runtime certificate has not been approved: " + key)
    if certificate_path is None or sidecar is None:
        raise ValueError("Certificate and original sidecar are required")
    raw = Path(certificate_path).read_bytes()
    if sha(raw) != manifest["approved_runtime_certificate_sha256"]:
        raise ValueError("Unapproved runtime certificate")
    c = json.loads(raw)
    if sha(Path(sidecar).read_bytes()) != manifest["native_sidecar_sha256"]:
        raise ValueError("Original sidecar mismatch")
    if sha(Path(iris).read_bytes()) != manifest["iris_sha256"]:
        raise ValueError("Iris binary mismatch")
    expected = {"schema": "shaderlod-makeup-cloud-b16-original-r3",
                "selectedArchiveSha256": manifest["base_sha256"],
                "irisJarSha256": manifest["iris_sha256"], "minecraftVersion": "1.21.1",
                "dimension": "minecraft:overworld", "irisFallback": False,
                "dhPresent": False, "voxyPresent": False}
    if any(c.get(k) != v for k, v in expected.items()):
        raise ValueError("Original runtime scope mismatch")
    values = c.get("loadedIrisOptions", {})
    if (len(values) != manifest["native_options_count"] or
            sha(json.dumps(values, sort_keys=True, separators=(",", ":")).encode()) != manifest["native_options_map_sha256"]):
        raise ValueError("Incomplete/different original loaded option map")
    if any(values.get(k) != v for k, v in manifest["required_options"].items()):
        raise ValueError("Effective cloud option mismatch")
    draw, sampler = c["draw"], c["sampledGaux2"]
    for key in ("contextId", "frameCounter", "programId"):
        if type(draw.get(key)) is not int or draw[key] <= 0 or sampler.get(key) != draw[key]:
            raise ValueError("Sampler/draw identity mismatch")
    if (draw.get("programName") != "world0/deferred" or
            draw.get("gaux2TextureUnit") != sampler.get("textureUnit") or
            type(sampler.get("textureId")) is not int or sampler["textureId"] <= 0):
        raise ValueError("Actual consuming sampler is not joined")
    # The raw observations, linked source and texture/source provenance are
    # parent-reviewed inputs. Hash joins preserve that review, not fabricate it.
    for key in ("rawObservation", "linkedFragment", "linkedVertex"):
        ref = c[key]
        if sha(Path(ref["path"]).read_bytes()) != ref["sha256"]:
            raise ValueError("Missing/changed observation source: " + key)
    if c.get("originalSourceMemberSha256") != manifest["source_members"][CLOUD]:
        raise ValueError("Original cloud-source join mismatch")
    texture = "shaders/textures/clouds_natural_512_R_8bit.png"
    if (sampler.get("sourceMember") != texture or
            sampler.get("sourceSha256") != manifest["source_members"][texture] or
            sampler.get("target") != 3553 or sampler.get("width") != 512 or
            sampler.get("height") != 512 or sampler.get("level1Width") != 0 or
            sampler.get("internalFormat") != 32856 or
            sampler.get("swizzleRGBA") != [6403, 6404, 6405, 6406] or
            sampler.get("uploadSourceJoined") is not True or
            sampler.get("baseLevel") != 0 or type(sampler.get("maxLevel")) is not int or
            sampler["maxLevel"] < 0 or type(sampler.get("samplerObject")) is not int or
            sampler["samplerObject"] < 0):
        raise ValueError("Physical custom texture mismatch")
    for state in (sampler["textureState"], sampler["effectiveSamplerState"]):
        if (state.get("minFilter") != 9729 or state.get("magFilter") != 9729 or
                state.get("wrapS") != 10497 or state.get("wrapT") != 10497 or
                state.get("anisotropy") != 1.0 or state.get("compareMode") != 0 or
                not math.isfinite(state.get("minLod", math.nan)) or
                not math.isfinite(state.get("maxLod", math.nan)) or
                not state["minLod"] <= 0.0 <= state["maxLod"]):
            raise ValueError("Sampler filtering is not certified")
    if sampler["samplerObject"] == 0 and sampler["effectiveSamplerState"] != sampler["textureState"]:
        raise ValueError("Sampler zero cannot override texture state")
    if c.get("explicitLodFragmentSupported") is not True:
        raise ValueError("Actual transformed explicit-LOD capability unproved")
    validate_runtime_joins(c, manifest, base, sidecar)
    return c


def decoded_cloud_rgba(data):
    """Decode only the pinned noninterlaced grayscale8 PNG, no gamma/row flip."""
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("Cloud PNG signature mismatch")
    offset, compressed, header = 8, bytearray(), None
    while offset < len(data):
        length = struct.unpack_from(">I", data, offset)[0]
        kind = data[offset + 4:offset + 8]
        payload = data[offset + 8:offset + 8 + length]
        crc = struct.unpack_from(">I", data, offset + 8 + length)[0]
        if zlib.crc32(kind + payload) & 0xffffffff != crc:
            raise ValueError("Cloud PNG CRC mismatch")
        if kind == b"IHDR":
            header = struct.unpack(">IIBBBBB", payload)
        if kind == b"IDAT":
            compressed.extend(payload)
        offset += length + 12
        if kind == b"IEND":
            break
    if header != (512, 512, 8, 0, 0, 0, 0):
        raise ValueError("Cloud PNG must be the pinned grayscale8 layout")
    raw = zlib.decompress(compressed)
    if len(raw) != 513 * 512:
        raise ValueError("Cloud PNG decoded extent mismatch")
    previous, result = bytearray(512), bytearray()
    for y in range(512):
        filter_kind, row = raw[y * 513], bytearray(raw[y * 513 + 1:(y + 1) * 513])
        if filter_kind not in range(5):
            raise ValueError("Cloud PNG filter mismatch")
        for x in range(512):
            left, up, upper_left = row[x - 1] if x else 0, previous[x], previous[x - 1] if x else 0
            if filter_kind == 1:
                prediction = left
            elif filter_kind == 2:
                prediction = up
            elif filter_kind == 3:
                prediction = (left + up) // 2
            elif filter_kind == 4:
                p = left + up - upper_left
                distances = (abs(p - left), abs(p - up), abs(p - upper_left))
                prediction = (left, up, upper_left)[distances.index(min(distances))]
            else:
                prediction = 0
            row[x] = (row[x] + prediction) & 255
            result.extend((row[x], row[x], row[x], 255))
        previous = row
    return bytes(result)


def validate_runtime_joins(c, manifest, base, sidecar):
    """Join derived certificate fields to hashed original raw capture content."""
    sources = {}
    for key in ("rawObservation", "capture_receipt", "owner_receipt"):
        ref = c[key]
        data = Path(ref["path"]).read_bytes()
        if sha(data) != ref["sha256"]:
            raise ValueError("Original evidence hash mismatch: " + key)
        sources[key] = json.loads(data)
    witness, capture, owner = (sources[k] for k in ("rawObservation", "capture_receipt", "owner_receipt"))
    if (witness.get("status") != "actual_deferred_constructor_source_program_pass_observation"
            or witness.get("actual_pass_name") != "deferred" or witness.get("pass_group") != "DEFERRED"
            or witness.get("loaded_pack") != "MakeUp-UltraFast-9.5g.zip" or witness.get("linked") is not True
            or witness.get("uniform_scan_truncated") is not False or witness.get("uniform_name_length_truncated") is not False
            or witness.get("active_texture_before") != witness.get("active_texture_after")):
        raise ValueError("Incomplete original consuming-pass observation")
    draw = c["draw"]
    for raw_name, cert_name in (("current_glfw_context", "contextId"), ("iris_frame_source", "frameCounter"), ("current_program", "programId")):
        if witness.get(raw_name) != draw[cert_name]:
            raise ValueError("Raw consuming draw identity drift: " + raw_name)
    for stage, key in (("fragment", "linkedFragment"), ("vertex", "linkedVertex")):
        if witness["constructor_source"].get("transformed_" + stage + "_sha256") != c[key]["sha256"]:
            raise ValueError("Constructor/transformed stage hash mismatch")
    fragment = Path(c["linkedFragment"]["path"]).read_text()
    for anchor in ("vec3 get_cloud(", "float current_value = texture(gaux2,", "cloud_value += current_opacity;",
                   "cloud_value = clamp(cloud_value / opacity_dist,", "textureLod(colortex1,", "cameraPosition, 10, umbral"):
        if anchor not in fragment:
            raise ValueError("Original consuming helper/source not present: " + anchor)
    if "dhDepthTex0" in fragment or PERMISSION in fragment or "vxDepthTex" in fragment:
        raise ValueError("Unexpected original linked cloud scope")
    sample, actual = c["sampledGaux2"], witness["samplers"]["gaux2"]
    if actual.get("active_uniform_type") != 35678 or actual.get("active_uniform_size") != 1 or actual.get("uniform_location", -1) < 0:
        raise ValueError("Original gaux2 is not a live scalar sampler2D")
    for raw_name, cert_name in (("texture_unit", "textureUnit"), ("bound_texture", "textureId"), ("texture_target", "target"), ("bound_sampler", "samplerObject")):
        if actual.get(raw_name) != sample[cert_name]:
            raise ValueError("Raw sampled texture identity drift: " + raw_name)
    level, image = actual["texture_level_zero"], actual["makeup_cloud_image"]
    for raw_name, cert_name in (("width", "width"), ("height", "height"), ("internal_format", "internalFormat")):
        if level.get(raw_name) != sample[cert_name]:
            raise ValueError("Raw texture allocation drift")
    if image.get("level1_width") != sample["level1Width"] or image.get("level1_height") != 0 or image.get("swizzle_rgba") != sample["swizzleRGBA"]:
        raise ValueError("Raw mip/swizzle drift")
    state_names = {"minFilter": "min_filter", "magFilter": "mag_filter", "wrapS": "wrap_s", "wrapT": "wrap_t",
                   "anisotropy": "max_anisotropy", "compareMode": "compare_mode", "minLod": "min_lod", "maxLod": "max_lod"}
    for cert_name, raw_name in (("textureState", "texture_parameters"), ("effectiveSamplerState", "effective_sampling_parameters")):
        raw_state = actual[raw_name]
        if raw_state.get("status") != "queried_without_gl_error_consumption" or any(sample[cert_name][k] != raw_state.get(v) for k, v in state_names.items()):
            raise ValueError("Raw sampler state drift: " + cert_name)
    for cert_name, raw_name in (("baseLevel", "base_level"), ("maxLevel", "max_level")):
        if sample[cert_name] != actual["texture_parameters"].get(raw_name):
            raise ValueError("Raw texture level-range drift")
    if capture.get("loadedIrisOptions") != c["loadedIrisOptions"] or capture.get("selectedArchiveSha256") != manifest["base_sha256"]:
        raise ValueError("Actual capture native map/archive drift")
    if (capture.get("irisFallback") is not False or capture.get("dhPresent") is not False
            or capture.get("dimension") != "minecraft:overworld" or capture.get("focused") is not True
            or capture.get("glVendor") != "Apple" or capture.get("glRenderer") != "Apple M3 Max"
            or capture.get("glVersion") != "4.1 Metal - 91.7"):
        raise ValueError("Actual captured runtime scope drift")
    if owner.get("status") != "exited" or owner["plan"].get("include_dh") is not False or owner["plan"].get("apply_lod_settings") is not False:
        raise ValueError("Original-only owner scope mismatch")
    console = Path(c["rawObservation"]["path"]).parent / "console.log"
    console_bytes = console.read_bytes()
    if sha(console_bytes) != manifest["original_console_sha256"] or b"OS: Mac OS X (27.0)" not in console_bytes:
        raise ValueError("Observed OS/log fingerprint drift")
    clients = owner.get("clients", {}).values()
    if not any(str(IRIS) in client.get("argv", "") and "neoforge-21.1.253.jar" in client.get("argv", "") for client in clients):
        raise ValueError("Observed original Iris/NeoForge classpath missing")
    if Path(owner["plan"]["pack_options"]).read_bytes() != Path(sidecar).read_bytes():
        raise ValueError("Observed original sidecar drift")
    if sha(Path(base).read_bytes()) != manifest["base_sha256"]:
        raise ValueError("Original cloud asset archive mismatch")
    with zipfile.ZipFile(base) as archive:
        data = archive.read(sample["sourceMember"])
    if sha(data) != sample["sourceSha256"]:
        raise ValueError("Original cloud PNG pin mismatch")
    decoded = decoded_cloud_rgba(data)
    asset = c["asset_join"]
    if (image.get("status") != "queried_without_gl_error_consumption" or image.get("returned_bytes") != len(decoded)
            or asset.get("byte_count") != len(decoded) or image.get("rgba8_level0_sha256") != sha(decoded)
            or asset.get("driver_and_decoded_sha256") != sha(decoded)):
        raise ValueError("Actual bound storage does not equal decoded original PNG")


def build(base=BASE, output=OUTPUT, mode="source-preview", certificate=None, sidecar=None, iris=IRIS):
    manifest = json.loads(MANIFEST.read_bytes())
    if sha(Path(base).read_bytes()) != manifest["base_sha256"]:
        raise ValueError("Original archive mismatch")
    files = read_members(base)
    arms = {m: patch(files, manifest, m) for m in ("native-equivalent", "candidate")}
    output = Path(output)
    if mode == "source-preview":
        # No zip or sidecar is emitted. Preview does not assert effective options.
        if output.exists():
            raise ValueError("Refusing to overwrite source preview")
        for arm, members in arms.items():
            for name in (CLOUD, PROPERTIES):
                dest = output / arm / name
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes(members[name])
        record = {"status": "SOURCE_PREVIEW_ONLY", "base_sha256": manifest["base_sha256"],
                  "changed_members": [CLOUD, PROPERTIES], "runtime_certificate": "missing",
                  "arms": {arm: {n: sha(members[n]) for n in (CLOUD, PROPERTIES)} for arm, members in arms.items()}}
        (output / "preview.json").write_text(json.dumps(record, indent=2) + "\n")
        return record
    if mode not in arms:
        raise ValueError("Unknown runtime arm")
    validate_certificate(manifest, certificate, sidecar, iris, base)
    sidecar_out, receipt_out = Path(str(output) + ".txt"), output.with_suffix(".json")
    if output.exists() or sidecar_out.exists() or receipt_out.exists():
        raise ValueError("Refusing to overwrite an artifact")
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(arms[mode].items()):
            info = zipfile.ZipInfo(name, (2026, 10, 7, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    sidecar_out.write_bytes(Path(sidecar).read_bytes() + f"\n{OPTION}=1\n".encode())
    result = {"status": "CERTIFIED_ORIGINAL_INPUTS_ASSEMBLED_CANDIDATE_RUNTIME_UNTESTED", "mode": mode,
              "output": str(output.resolve()), "sha256": sha(output.read_bytes()),
              "sidecar": str(sidecar_out.resolve()), "sidecar_sha256": sha(sidecar_out.read_bytes()),
              "original_certificate_sha256": manifest["approved_runtime_certificate_sha256"],
              "native_options_map_sha256": manifest["native_options_map_sha256"],
              "changed_members": {n: sha(arms[mode][n]) for n in (CLOUD, PROPERTIES)},
              "runtime_activation": "pending actual per-arm loaded map, linked permission, transformed source and sampler joins"}
    receipt_out.write_text(json.dumps(result, indent=2) + "\n")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", type=Path, default=BASE)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--mode", choices=("source-preview", "native-equivalent", "candidate"), default="source-preview")
    parser.add_argument("--certificate", type=Path)
    parser.add_argument("--sidecar", type=Path)
    parser.add_argument("--iris", type=Path, default=IRIS)
    print(json.dumps(build(**vars(parser.parse_args())), indent=2))

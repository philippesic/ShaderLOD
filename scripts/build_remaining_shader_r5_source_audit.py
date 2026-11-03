#!/usr/bin/env python3
"""Build source-only evidence for the R5 audit; never build or alter a shader pack."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
EXTRACTS = ROOT / "dev/logs/generic-lod-strategy-r1/geometry/sources"
PINS = {
    "photon": "120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead",
    "complementary-reimagined": "fed6c879e732cf7fc7bc6570ebb1def43235aa7e96f62859fbdbdbfa0b484b38",
    "complementary-unbound": "98ac741dee294d2a3c82d153349148c3bb7fb688c30eb3b49c3b9ac37e968c5a",
    "bsl-shaders": "36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5",
    "makeup-ultra-fast-shaders": "0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8",
    "sildurs-vibrant-shaders": "824504bc937f9570277c0a809f5059ae4a5fe70c413cf4459b6ccff4dfed7ebe",
    "solas-shader": "b7dfe4c7353d677a92cc4c86b87bc0633f1014305d927ca6a482c303427bf825",
    "bliss-shader": "f41db92acc585fe9ffe0cc124d6ecaea27cef9ded91129ebc2a8e60226947b2c",
    "astralex": "70b6d8127a6733f0a19bd1d351b92709d6ee7496967ff49c4793d2c28c7a6c58",
}

# Canonical paths below shaders/. Ranges are evidence selections, not a claim
# that every option/caller or every byte in each archive was semantically audited.
SITES = {
    "photon": {
        "program/d4_deferred_shading.fsh": [(315, 333), (412, 450), (550, 609)],
        "include/lighting/diffuse_lighting.glsl": [(119, 200)],
        "include/lighting/specular_lighting.glsl": [(199, 287), (290, 428)],
        "include/fog/overworld/raymarched.glsl": [(14, 33), (86, 219)],
        "include/fog/overworld/analytic.glsl": [(9, 108)],
        "include/fog/overworld/constants.glsl": [(4, 12)],
        "program/c0_vl.fsh": [(126, 258)],
        "program/c1_blend_layers.fsh": [(1, 500)],
        "settings.glsl": [(290, 311), (375, 415)],
        "program/lpv/floodfill.csh": [(12, 58)],
    },
    "complementary-reimagined": {
        "program/gbuffers_terrain.glsl": [(320, 349)],
        "lib/lighting/mainLighting.glsl": [(550, 619)],
        "lib/materials/materialMethods/reflections.glsl": [(1, 65), (135, 225)],
        "program/composite.glsl": [(130, 224)],
        "lib/atmospherics/volumetricLight/volumetricLight.glsl": [(9, 65), (82, 286), (289, 390)],
        "lib/atmospherics/fog/mainFog.glsl": [(1, 250)],
        "program/composite1.glsl": [(210, 265)],
        "program/shadowcomp.glsl": [(8, 160)],
        "lib/voxelization/lightVoxelization.glsl": [(398, 425)],
        "world0/shadowcomp.csh": [(1, 8)],
    },
    "bsl-shaders": {
        "lib/lighting/forwardLighting.glsl": [(1, 140)],
        "program/gbuffers_terrain.glsl": [(350, 441)],
        "lib/reflections/roughReflections.glsl": [(1, 80)],
        "program/deferred1.glsl": [(260, 275), (345, 395)],
        "lib/atmospherics/lightShafts.glsl": [(82, 115), (211, 338)],
        "lib/atmospherics/waterFog.glsl": [(1, 40)],
        "lib/atmospherics/fog.glsl": [(1, 180)],
        "program/composite.glsl": [(350, 405)],
        "lib/settings.glsl": [(68, 88)],
        "program/shadowcomp.glsl": [(8, 145)],
        "lib/util/voxelMap.glsl": [(1, 40)],
    },
    "makeup-ultra-fast-shaders": {
        "common/solid_blocks_fragment.glsl": [(223, 300)],
        "lib/material_gloss_fragment.glsl": [(1, 28)],
        "lib/water.glsl": [(9, 60), (176, 240), (247, 317)],
        "lib/volumetric_light.glsl": [(5, 117)],
        "lib/config.glsl": [(182, 196)],
        "common/composite_fragment.glsl": [(100, 220)],
        "src/finalcolor.glsl": [(1, 44)],
        "src/fog_vertex.glsl": [(1, 150)],
    },
    "sildurs-vibrant-shaders": {
        "deferred.fsh": [(1, 16), (112, 157), (551, 565), (665, 794)],
        "gbuffers_textured.fsh": [(95, 165)],
        "deferred1.fsh": [(18, 120), (225, 357)],
        "gbuffers_water.fsh": [(197, 240), (405, 500)],
        "composite.fsh": [(57, 118), (140, 180)],
        "composite1.fsh": [(65, 97), (606, 615), (703, 813)],
    },
    "solas-shader": {
        "lib/lighting/gbuffersLighting.glsl": [(6, 66), (112, 200)],
        "programs/gbuffers_terrain.glsl": [(315, 365)],
        "lib/pbr/simpleReflection.glsl": [(1, 97)],
        "programs/deferred1.glsl": [(320, 344)],
        "lib/atmosphere/volumetrics.glsl": [(67, 310)],
        "lib/atmosphere/fog.glsl": [(32, 180)],
        "lib/water/waterFog.glsl": [(1, 40)],
        "lib/common.glsl": [(200, 209), (269, 275)],
        "programs/shadowcomp.glsl": [(1, 104)],
        "lib/vx/voxelization.glsl": [(1, 40)],
        "shaders.properties": [(193, 227)],
    },
    "bliss-shader": {
        "dimensions/composite1.fsh": [(1172, 1250), (1290, 1333)],
        "lib/specular.glsl": [(189, 300)],
        "lib/overworld_fog.glsl": [(1, 61), (65, 178), (216, 341)],
        "dimensions/composite2.fsh": [(405, 430)],
        "dimensions/fogBehindTranslucent_pass.fsh": [(370, 400)],
        "dimensions/deferred.fsh": [(285, 301)],
        "lib/settings.glsl": [(95, 111), (162, 169), (255, 274)],
        "lib/res_params.glsl": [(1, 21)],
        "dimensions/shadowcomp.csh": [(1, 175)],
        "lib/voxel_write.glsl": [(1, 30)],
    },
    "astralex": {
        "lib/lighting/forwardLighting.glsl": [(72, 140)],
        "program/gbuffers_terrain.glsl": [(615, 706)],
        "lib/reflections/roughReflections.glsl": [(1, 98)],
        "program/deferred.glsl": [(312, 353)],
        "settings/globalSettings.glsl": [(126, 145)],
        "lib/atmospherics/volumetricLight.glsl": [(54, 241)],
        "lib/atmospherics/fog.glsl": [(1, 210)],
        "lib/atmospherics/waterFog.glsl": [(1, 95)],
    },
}
SITES["complementary-unbound"] = SITES["complementary-reimagined"]


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def source_index(archive: zipfile.ZipFile) -> dict[str, str]:
    result = {}
    for name in archive.namelist():
        if name.endswith("/"):
            continue
        marker = "shaders/"
        if name.startswith(marker):
            canonical = name[len(marker):]
        elif "/" + marker in name:
            canonical = name.split("/" + marker, 1)[1]
        else:
            continue
        if canonical in result:
            raise ValueError(f"duplicate canonical shader member: {canonical}")
        result[canonical] = name
    return result


def run(output: Path) -> dict:
    staging = json.loads((ROOT / "dev/logs/generic-lod-strategy-r1/lead/artifact-pins.json").read_text())
    paths = {p["pack"]: p for p in staging if "pack" in p}
    receipt = {"kind": "source-only-no-candidate", "packs": [], "limitations": [
        "No driver compilation, loaded-option proof, runtime activation, quality, or cost evidence.",
        "Token inventory is an archive search, not an all-program semantic or preprocessor proof.",
        "Original licenses, archives, sources, and extracted sources are read-only.",
    ]}
    for pack, expected in PINS.items():
        pin = paths[pack]
        raw = (ROOT / pin["file"]).read_bytes()
        if sha(raw) != expected:
            raise ValueError(f"original archive identity mismatch: {pack}")
        with zipfile.ZipFile(ROOT / pin["file"]) as z:
            index = source_index(z)
            rec = {"pack": pack, "version": pin["version"], "archive": pin["file"],
                   "sha256": expected, "license_metadata": pin["license_metadata"],
                   "license_members": [], "selected_members": [], "compute_members": [],
                   "image_store_members": [], "shader_member_count": len(index)}
            for name in z.namelist():
                if not name.endswith("/") and re.search(r"(^|/)(license|licence|copyright)([./_ -]|$)", name, re.I):
                    rec["license_members"].append({"member": name, "sha256": sha(z.read(name))})
            for canonical, name in index.items():
                if canonical.endswith(".csh"):
                    rec["compute_members"].append(name)
                if Path(canonical).suffix in {".glsl", ".vsh", ".fsh", ".gsh", ".csh"}:
                    body = z.read(name).decode("utf-8", errors="replace")
                    code = re.sub(r"/\*.*?\*/|//[^\n]*", "", body, flags=re.S)
                    if re.search(r"\bimageStore\s*\(", code):
                        rec["image_store_members"].append(name)
            for canonical, ranges in SITES[pack].items():
                name = index[canonical]
                data = z.read(name)
                extracted = EXTRACTS / pack / "shaders" / canonical
                if extracted.read_bytes() != data:
                    raise ValueError(f"archive/extract byte mismatch: {pack}/{canonical}")
                lines = data.decode("utf-8-sig").splitlines()
                selections = []
                for first, last in ranges:
                    if first > len(lines):
                        raise ValueError(f"out-of-range evidence selection: {pack}/{canonical}:{first}")
                    end = min(last, len(lines))
                    selections.append({"first": first, "last": end,
                                       "text": "\n".join(f"{i}: {lines[i-1]}" for i in range(first, end + 1))})
                rec["selected_members"].append({"canonical": canonical, "member": name,
                    "sha256": sha(data), "archive_extract_bytes_equal": True, "ranges": selections})
            receipt["packs"].append(rec)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(receipt, indent=2) + "\n")
    return {"receipt": str(output.relative_to(ROOT)), "sha256": sha(output.read_bytes()),
            "packs": len(receipt["packs"]),
            "members": sum(len(p["selected_members"]) for p in receipt["packs"]),
            "inventory": [{"pack": p["pack"], "csh": len(p["compute_members"]),
                           "image_store_members": len(p["image_store_members"])} for p in receipt["packs"]]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "dev/logs/mechanism-evaluation-r5/remaining-shader/source-receipt.json")
    args = parser.parse_args()
    print(json.dumps(run(args.output.resolve()), indent=2))

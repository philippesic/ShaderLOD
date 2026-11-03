#!/usr/bin/env python3
"""Build one explicit pinned .75 raster chain and byte-exact native controls.

The separate staged renderer is required for actual work contraction. Source
assembly does not establish runtime viewport, sampling, rendering or quality.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
import zipfile

from build_supported_pack_adapters import read_archive, shader_source_digest

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "patches/complementary-raster-r1/contract.json"
SCALE = 0.75


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def active_extent(width, height):
    if type(width) is not int or type(height) is not int or min(width, height) < 1:
        raise ValueError("Positive integer output extent required")
    return max(1, math.floor(width * SCALE)), max(1, math.floor(height * SCALE))


def current_uv(uv, extent, allocation):
    """Base-level current image addressing, clamped to valid texel centers.

    This cannot repair mip pixels already mixed with outside strips. Native
    history UVs must NEVER pass through this function after a full-size resolve.
    """
    if len(uv) != 2 or len(extent) != 2 or len(allocation) != 2:
        raise ValueError("Expected two-dimensional coordinates")
    if any(not math.isfinite(float(v)) for v in uv):
        raise ValueError("Nonfinite UV")
    if any(type(v) is not int or v < 1 for v in (*extent, *allocation)):
        raise ValueError("Positive integer texture extents required")
    if any(e > a for e, a in zip(extent, allocation)):
        raise ValueError("Active rectangle exceeds allocation")
    return tuple(min(max(float(u) * e, 0.5), e - 0.5) / a
                 for u, e, a in zip(uv, extent, allocation))


def mip_witness():
    """Legal box-filter mip model at a native full-size allocation boundary.

    512^2 allocation, 384^2 active constant-one scene, clear-zero outside.
    Level 8 contains four 256^2 boxes. Their exact means are [1,.5;.5,.25].
    A separately allocated 384^2 source containing ones stays one at every
    mip level. No GPU/filter implementation or actual pixel result is inferred.
    """
    allocation = (512, 512)
    active = active_extent(*allocation)
    lod = 8
    box_side = 1 << lod
    values = []
    for y in range(2):
        row = []
        for x in range(2):
            overlap_x = max(0, min(active[0], (x + 1) * box_side) - x * box_side)
            overlap_y = max(0, min(active[1], (y + 1) * box_side) - y * box_side)
            row.append(overlap_x * overlap_y / box_side**2)
        values.append(row)
    return {"model": "2x box reduction, repeated eight times; source counterexample only",
            "output_allocation": allocation, "active_extent": active, "side_scale": SCALE,
            "level": lod, "native_full_allocation_mip": values,
            "separate_active_source_mip": [[1.0, 1.0], [1.0, 1.0]],
            "outside_value": 0.0, "active_value": 1.0,
            "base_uv_at_last_active_texel": current_uv((1.0, 1.0), active, allocation),
            "implication": "Base-level UV clamping cannot restore an already contaminated mip texel",
            "driver_observed": False}


def validate(base, pack, manifest_path=MANIFEST):
    manifest_bytes = Path(manifest_path).read_bytes()
    manifest = json.loads(manifest_bytes)
    if (manifest.get("schema") != 1 or manifest.get("kind") != "private-pinned-raster-contract"
            or manifest.get("policy", {}).get("side_scale") != SCALE):
        raise ValueError("Unreviewed manifest/policy")
    if pack not in manifest["packs"]:
        raise ValueError("Uncertified Complementary release")
    pin = manifest["packs"][pack]
    data = Path(base).read_bytes()
    if sha256(data) != pin["release_archive_sha256"]:
        raise ValueError("Pinned original archive changed")
    files = read_archive(base)
    if len(files) != pin["member_count"] or shader_source_digest(files) != pin["shader_source_digest"]:
        raise ValueError("Pinned original member inventory/source changed")
    for name, digest in pin["source_members"].items():
        if name not in files or sha256(files[name]) != digest:
            raise ValueError("Pinned source changed: " + name)
    if sha256(files["License.txt"]) != pin["license_sha256"]:
        raise ValueError("Original license changed")
    anchors = {
        "shaders/program/composite3.glsl": [b"const bool colortex0MipmapEnabled = true;",
            b"texture2DLod(colortex0, texCoord + offset, lod)", b"/* DRAWBUFFERS:0 */"],
        "shaders/program/composite4.glsl": [b"const bool colortex0MipmapEnabled = true;",
            b"texture2D(colortex0, bloomCoord)", b"BloomTile(8.0"],
        "shaders/program/composite6.glsl": [b"DoTAA(color, temp, z1);", b"/* DRAWBUFFERS:32 */"],
        "shaders/lib/antialiasing/taa.glsl": [b"textureCatmullRom(colortex2, prvCoord, view)",
            b"texelFetch(colortex3, texelCoordM2, 0)", b"texelFetch(colortex6, texelCoord, 0)"],
        "shaders/lib/pipelineSettings.glsl": [b"const bool colortex0Clear = true;",
            b"const bool colortex2Clear = false;"],
    }
    for name, patterns in anchors.items():
        if any(pattern not in files[name] for pattern in patterns):
            raise ValueError("Native consumer contract changed: " + name)
    return manifest, files, data, sha256(manifest_bytes)


def audit(base, *, pack, manifest_path=MANIFEST):
    manifest, files, data, digest = validate(base, pack, manifest_path)
    pin = manifest["packs"][pack]
    return {"schema": 1, "id": manifest["id"], "pack": pack, "version": pin["version"],
            "original": str(Path(base).resolve()), "archive_sha256": sha256(data),
            "member_count": len(files), "source_digest": shader_source_digest(files),
            "license_sha256": sha256(files["License.txt"]), "manifest_sha256": digest,
            "builder_sha256": sha256(Path(__file__).read_bytes()),
            "fixed_policy": manifest["policy"], "status": manifest["status"],
            "source_members": pin["source_members"], "mip_counterexample": mip_witness(),
            "required_contract": manifest["required_contract"],
            "actual_mod_consuming_viewport_bridge": False,
            "shader_compiled": False, "rendered": False, "performance_measured": False,
            "modified_upstream_members": [], "added_archive_members": [],
            "native_c3_preserved": True}


def apply(files, manifest, *, option=False):
    result = files.copy()
    for patch in manifest["patches"]:
        name, before, after = patch["path"], patch["before"].encode(), patch["after"].encode()
        if name not in manifest["packs"]["complementary-reimagined"]["source_members"]:
            raise ValueError("Unpinned patch source")
        if result[name].count(before) != patch["count"]:
            raise ValueError("Pinned literal patch count changed: " + name)
        result[name] = result[name].replace(before, after)
    if option:
        anchor = b"#define SHADERLOD_RASTER_R1 0 //[0 1]"
        if result["shaders/lib/common.glsl"].count(anchor) != 1:
            raise ValueError("Private option anchor changed")
        result["shaders/lib/common.glsl"] = result["shaders/lib/common.glsl"].replace(
            anchor, b"#define SHADERLOD_RASTER_R1 1 //[0 1]")
    local = manifest["properties_append"]
    append = (ROOT / local["path"]).read_bytes()
    if sha256(append) != local["sha256"]:
        raise ValueError("Private properties changed")
    result["shaders/shaders.properties"] += append
    for name, local in manifest["additions"].items():
        if name in result:
            raise ValueError("Private addition collision: " + name)
        data = (ROOT / local["path"]).read_bytes()
        if sha256(data) != local["sha256"]:
            raise ValueError("Private helper changed: " + name)
        result[name] = data
    if result["shaders/program/composite3.glsl"] != files["shaders/program/composite3.glsl"]:
        raise ValueError("Native c3 must remain byte-exact")
    for dimension in ("world0", "world-1", "world1"):
        for ext in ("fsh", "vsh"):
            name = f"shaders/{dimension}/composite3.{ext}"
            if result[name] != files[name]:
                raise ValueError("Native c3 wrapper changed")
    return result


def build(base, output, *, pack, activate=False, source_candidate=False, manifest_path=MANIFEST):
    """Assemble only literal reviewed patches; activation still needs Java seams."""
    base, output = Path(base), Path(output)
    if base.resolve() == output.resolve():
        raise ValueError("Cannot overwrite original archive")
    if output.exists():
        raise ValueError("Output already exists")
    receipt = audit(base, pack=pack, manifest_path=manifest_path)
    manifest, original, _, _ = validate(base, pack, manifest_path)
    if activate and not source_candidate:
        raise ValueError("R1 activation requires explicit --source-candidate; renderer/mip ownership must be integrated")
    if activate and pack != "complementary-reimagined":
        raise ValueError("Unbound effective option map is uncertified; native fallback")
    output.parent.mkdir(parents=True, exist_ok=True)
    if source_candidate:
        result = apply(original, manifest, option=activate)
        with output.open("xb") as target:
            with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED) as archive:
                for name, data in sorted(result.items()):
                    entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                    entry.compress_type = zipfile.ZIP_DEFLATED
                    entry.external_attr = 0o644 << 16
                    archive.writestr(entry, data)
        written = read_archive(output)
        if written != result or written["License.txt"] != original["License.txt"]:
            raise RuntimeError("Private source/member/license verification failed")
        receipt.update(modified_upstream_members=sorted(n for n in original if result[n] != original[n]),
                       added_archive_members=sorted(set(result) - set(original)),
                       candidate_shader_source_digest=shader_source_digest(result),
                       member_sha256={n: sha256(result[n]) for n in result if n not in original or result[n] != original[n]})
    else:
        with output.open("xb") as target:
            target.write(base.read_bytes())
        if output.read_bytes() != base.read_bytes():
            raise RuntimeError("Native archive control lost byte equality")
    receipt.update(output=str(output.resolve()), output_sha256=sha256(output.read_bytes()),
                   byte_exact_original=not source_candidate, option_enabled=activate,
                   active=False, staged_renderer_required=source_candidate)
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", type=Path, required=True)
    parser.add_argument("--pack", choices=("complementary-reimagined", "complementary-unbound"), required=True)
    parser.add_argument("--output", type=Path, help="Separate private source ZIP or original byte-exact control")
    parser.add_argument("--source-candidate", action="store_true")
    parser.add_argument("--activate", action="store_true", help="Default private option to 1; actual renderer remains unverified")
    args = parser.parse_args()
    if args.activate and not args.output:
        parser.error("--activate requires --output")
    result = (build(args.base, args.output, pack=args.pack, activate=args.activate, source_candidate=args.source_candidate) if args.output
              else audit(args.base, pack=args.pack))
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Pinned BSL B4 coordinate cue and conservative current-depth census analyzer.

No field/candidate is allocated. Cue is NEVER TIME. Census counts supplied
invocation records; a visible terrain image is explicitly only a subset.
"""
import argparse
from array import array
import hashlib
import json
import math
from pathlib import Path, PurePosixPath
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'dev/logs/cross-shader-performance-r1/bsl-shadow-tiles'
BASE = ROOT / 'dependencies/upstream/other-pack-research/BSL_v10.1.8.zip'
CAPTURE = ROOT / 'dev/logs/cross-shader-performance-r1/bsl-mip-cost-r1/original/captures/bsl-mip-cost-r1-original.json'
OPTIONS = ROOT / 'dev/logs/bsl-sanity-r7/original-options.txt'
IRIS = ROOT / 'dependencies/runtime/iris-neoforge-1.8.12+mc1.21.1.jar'
ARCHIVE_SHA = '36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5'
IRIS_SHA = '3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83'
CAPTURE_SHA = 'b2bac0a22d8b39849e7abd147224edd40515a49b35410e93248e61890cc1a441'
OPTIONS_MAP_SHA = '41a861dd87d34ad2d1a0dde900cdcc3cc813cf3d4338c3a8041b59c7493a265f'
TERRAIN = 'shaders/program/gbuffers_terrain.glsl'
SHADOWS = 'shaders/lib/lighting/shadows.glsl'
FINAL = 'shaders/program/final.glsl'
HELPER = 'shaders/lib/shaderlod/bsl_tiles_census_r1.glsl'
PINNED = {
    TERRAIN: 'c47d350132261e8f7b271f99ba8d060e4b38ee12a5da50597b664245f212c8d7',
    SHADOWS: 'b7cecede848a68f6118cdb1ad1c14dfaae32c7fbcdb82f3a11d14bf9274c064a',
    FINAL: '1b93825ce635c7744c9c166e6b71425ee5e8fa8ecb589c8aa6bb1a054c7ccc50',
    'shaders/lib/lighting/forwardLighting.glsl': '21687dd916c69c8bcd557e4b1bde367d7eeb77be01be1efc0b7e0ff20109fae8',
    'shaders/world0/gbuffers_terrain.fsh': 'fb8bd36225c0b74ab5befc33f7097e02e10196a20d3441a93819729792580801',
    'shaders/shaders.properties': '8856162fcf6846e1b56f5c092e6eaee04af687875f85e87cde87e1bd0a0d3169',
}
OFFSETS = ((0, 0), (0, 1), (.7, .7), (1, 0), (.7, -.7), (0, -1), (-.7, -.7), (-1, 0), (-.7, .7))
EPOCH_DOMAINS = ('pack_pipeline_world_versions', 'transforms_dimensions_raster',
    'geometry_material_atlas_animation', 'all_consumed_uniform_inputs',
    'depth_color_sampler_resource_generations', 'voxel_other_output_generations',
    'completed_current_frame_generation')


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def canonical(obj):
    return json.dumps(obj, sort_keys=True, separators=(',', ':')).encode()


def once(raw, before, after):
    if raw.count(before) != 1:
        raise ValueError('Missing/ambiguous exact source anchor')
    return raw.replace(before, after, 1)


def apply_cue(files):
    for name, expected in PINNED.items():
        if digest(files[name]) != expected:
            raise ValueError('Changed pinned member: ' + name)
    result = dict(files)
    include = b'#include "/lib/lighting/forwardLighting.glsl"'
    result[TERRAIN] = once(result[TERRAIN], include,
        b'#define SHADERLOD_BSL_TILES_SCOPE()\r\n#include "/lib/shaderlod/bsl_tiles_census_r1.glsl"\r\n' + include)
    entry = b'vec3 SampleFilteredShadow(vec3 shadowPos, float offset, float subsurface) {\r\n'
    result[SHADOWS] = once(result[SHADOWS], entry, entry +
        b'    #ifdef SHADERLOD_BSL_TILES_SCOPE\r\n    shaderlodBslTilesObserve(shadowPos, offset, subsurface);\r\n    #endif\r\n')
    write = b'    gl_FragData[0] = albedo;'
    result[TERRAIN] = once(result[TERRAIN], write, write +
        b'\r\n    // Diagnostic override after ALL native lighting; no sampling is bypassed.\r\n'
        b'    if (shaderlodBslTilesCensusEnabled()) gl_FragData[0] = shaderlodBslTilesRecord;')
    # Iris parses pack constants inside this native declaration comment too.
    # Root must certify the actually allocated format; source alone is not proof.
    result[FINAL] = once(result[FINAL], b'const int colortex0Format = R11F_G11F_B10F;',
        b'const int colortex0Format = RGBA32F;')
    result[HELPER] = (ROOT / 'patches/bsl-shadow-tiles-r1/census.glsl').read_bytes()
    return result


def build(output):
    output = Path(output).resolve()
    sidecar, receipt = Path(str(output) + '.txt'), output.with_suffix('.json')
    if output.suffix != '.zip' or not output.is_relative_to(OUT.resolve()) or any(p.exists() for p in (output, sidecar, receipt)):
        raise ValueError('Use fresh ZIP under ignored owned output directory')
    if digest(BASE.read_bytes()) != ARCHIVE_SHA or digest(IRIS.read_bytes()) != IRIS_SHA or digest(CAPTURE.read_bytes()) != CAPTURE_SHA:
        raise ValueError('Unqualified original archive/Iris/runtime certificate')
    cert = json.loads(CAPTURE.read_bytes())
    options = cert['loadedIrisOptions']
    if len(options) != 384 or digest(canonical(options)) != OPTIONS_MAP_SHA or cert['irisFallback'] or not cert['shaderPackInUse']:
        raise ValueError('Unqualified original option certificate')
    if OPTIONS.read_bytes() != b'':
        raise ValueError('Pinned original sidecar changed')
    with zipfile.ZipFile(BASE) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or any(PurePosixPath(n).is_absolute() or '..' in PurePosixPath(n).parts or '\\' in n or '\x00' in n for n in names):
            raise ValueError('Unsafe/duplicate archive member')
        original = {n: archive.read(n) for n in names if not n.endswith('/')}
    files = apply_cue(original)
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'x', zipfile.ZIP_DEFLATED) as archive:
        for name, raw in sorted(files.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, raw)
    sidecar.write_bytes(OPTIONS.read_bytes())
    result = {'kind': 'BSL-B4-private-coordinate-census-only', 'never_time': True,
        'builder_sha256': digest(Path(__file__).read_bytes()),
        'archive_sha256': digest(output.read_bytes()), 'original_archive_sha256': ARCHIVE_SHA,
        'iris_sha256': IRIS_SHA, 'original_capture_sha256': CAPTURE_SHA,
        'original_loaded_options_map_sha256': OPTIONS_MAP_SHA,
        'original_member_sha256': {n: digest(raw) for n, raw in original.items()},
        'modified_members': sorted(n for n in original if files[n] != original[n]),
        'added_members': [HELPER], 'cue_member_sha256': {n: digest(files[n]) for n in (TERRAIN, SHADOWS, FINAL, HELPER)},
        'unchanged_properties_sha256': digest(files['shaders/shaders.properties']),
        'license': 'LicenseRef-All-Rights-Reserved; personal local diagnostic only; do not redistribute',
        'options_actually_loaded': False, 'driver_compiled': False, 'runtime_census': False,
        'performance_acceptance': False, 'restoration': 'Reload exact original archive and empty native sidecar; cue Off retains diagnostic allocation and is not a cost baseline'}
    receipt.write_text(json.dumps(result, indent=2) + '\n')
    return result


def support_tiles(u, v, radius, width, height, uv_error=0.0):
    """Rectangular bilinear support superset, expanded for measured UV error.

    Normalized UV/radius -> PHYSICAL texels, with center at texel+0.5.
    Unknown border is ineligible. The tile field covers complete 4x4 tiles.
    """
    if not all(math.isfinite(x) for x in (u, v, radius, uv_error)) or radius < 0 or uv_error < 0:
        raise ValueError('Invalid support coordinates/error')
    lo_x = math.floor((u - radius - uv_error) * width - .5)
    hi_x = math.floor((u + radius + uv_error) * width - .5) + 1
    lo_y = math.floor((v - radius - uv_error) * height - .5)
    hi_y = math.floor((v + radius + uv_error) * height - .5) + 1
    if lo_x < 0 or lo_y < 0 or hi_x >= width or hi_y >= height:
        return None
    tiles = {(x, y) for x in range(lo_x // 4, hi_x // 4 + 1) for y in range(lo_y // 4, hi_y // 4 + 1)}
    # Full tile minima, not four probes and not merely the nine-point union.
    if any(4*x + 3 >= width or 4*y + 3 >= height for x, y in tiles):
        return None
    return tiles


def all_lit(depth, width, tiles, reference, reference_error, compare_margin):
    if tiles is None:
        return False
    if not all(math.isfinite(x) for x in (reference, reference_error, compare_margin)) or reference_error < 0 or compare_margin < 0:
        raise ValueError('Invalid comparison/error bound')
    threshold = reference + reference_error + compare_margin
    # Strictly greater avoids relying on equality/unknown conversion rounding.
    return all(math.isfinite(depth[(ty*4+dy)*width + tx*4+dx]) and
               depth[(ty*4+dy)*width + tx*4+dx] > threshold
               for tx, ty in tiles for dy in range(4) for dx in range(4))


def coordinate_records(raw, extent):
    """One visible surviving sample per unfiltered RGBA32F terrain texel.

    Clears and other programs can overwrite the image. Root MUST qualify exact
    terrain-only capture and clear sentinel; no screen image proves overdraw.
    """
    if len(raw) != math.prod(extent) * 16:
        raise ValueError('Coordinate hash/extent mismatch')
    coordinates = array('f'); coordinates.frombytes(raw)
    if sys.byteorder != 'little':
        coordinates.byteswap()
    for i in range(0, len(coordinates), 4):
        u, v, reference, radius = coordinates[i:i+4]
        yield {'multiplicity': 1, 'subsurface': 0 if radius > 0 else 1,
               'filtered_reached': radius > 0, 'offset': radius,
               'shadow_position': (u, v, reference)}


def expression_screen(width, height, pixels, eligible, hits, query_count):
    """Investment screen only: raw reads and hardware PCF are unequal-cost."""
    if pixels <= 0 or eligible < 0 or not 0 <= hits <= eligible or query_count < 0:
        raise ValueError('Invalid cost denominator')
    build_reads = 16 * math.ceil(width / 4) * math.ceil(height / 4)
    baseline = 9 * eligible
    candidate = build_reads + query_count + 9 * (eligible - hits)
    return {'p_invocations_per_output_pixel': eligible / pixels,
        'h_all_lit_fraction': hits / eligible if eligible else 0,
        'build_raw_fetch_expressions': build_reads, 'query_fetch_expressions': query_count,
        'native_primary_fetch_expressions': baseline, 'candidate_primary_fetch_expressions': candidate,
        'optimistic_expression_screen_pass': candidate < baseline,
        'useful_r32f_bytes': 4 * math.ceil(width/4) * math.ceil(height/4),
        'iris_full_r32f_bytes_per_image': 4 * width * height,
        'iris_full_r32f_bytes_two_images': 8 * width * height,
        'performance_claim': False}


def analyze(snapshot):
    """Analyze root-supplied same-epoch exact records; never invent measurements.

    Binary depth file is GL_DEPTH_COMPONENT/GL_FLOAT little endian. A visible
    coordinate image must be converted to records BEFORE ANY deferred/composite
    stage, with explicit surviving-sample population, not total invocations.
    """
    snapshot = Path(snapshot)
    data = json.loads(snapshot.read_bytes())
    if data['original_archive_sha256'] != ARCHIVE_SHA or data['iris_sha256'] != IRIS_SHA or data['options_map_sha256'] != OPTIONS_MAP_SHA:
        raise ValueError('Unqualified original snapshot provenance')
    cue_receipt_raw = (snapshot.parent / data['cue_receipt_file']).read_bytes()
    cue_receipt = json.loads(cue_receipt_raw)
    if (digest(cue_receipt_raw) != data['cue_receipt_sha256'] or
        cue_receipt['archive_sha256'] != data['capture_archive_sha256'] or
        cue_receipt['original_archive_sha256'] != ARCHIVE_SHA or
        not cue_receipt['never_time'] or not data['native_shadow_source_and_state_unchanged_verified']):
        raise ValueError('Unjoined cue capture archive/native shadow contract')
    if data['population'] not in ('visible-selected-terrain', 'complete-forward-invocations'):
        raise ValueError('Unknown invocation population')
    if data['depth_epoch'] != data['receivers_epoch'] or not data['epoch_complete']:
        raise ValueError('Not one completed current shadow epoch')
    epoch = data['producer_epoch']
    if (set(epoch) != set(EPOCH_DOMAINS) or any(not epoch[key] for key in EPOCH_DOMAINS) or
        data['depth_epoch'] != digest(canonical(epoch))):
        raise ValueError('Incomplete current producer epoch domains/digest')
    if data['compare_func'] != 'LEQUAL' or data['compare_mode'] != 'COMPARE_REF_TO_TEXTURE' or data['filter'] != 'LINEAR' or data['depth_level'] != 0:
        raise ValueError('Unqualified native comparison/filter support')
    if data['sampler_bounds_verified'] is not True or data['raw_readback_verified'] is not True:
        raise ValueError('Unverified sampler/readback bounds')
    width, height = data['physical_depth_extent']
    if width <= 0 or height <= 0:
        raise ValueError('Invalid physical extent')
    raw_path = snapshot.parent / data['depth_f32le_file']
    raw = raw_path.read_bytes()
    if digest(raw) != data['depth_sha256'] or len(raw) != width * height * 4:
        raise ValueError('Depth hash/extent mismatch')
    depth = array('f'); depth.frombytes(raw)
    if sys.byteorder != 'little':
        depth.byteswap()
    eligible = hits = query_count = excluded = 0
    if 'coordinates_rgba32f_file' in data:
        if data['population'] != 'visible-selected-terrain' or not data['terrain_only_image_and_clear_verified']:
            raise ValueError('Image is not a complete invocation census')
        coordinates_raw = (snapshot.parent / data['coordinates_rgba32f_file']).read_bytes()
        if digest(coordinates_raw) != data['coordinates_sha256']:
            raise ValueError('Coordinate hash mismatch')
        records = coordinate_records(coordinates_raw, data['output_extent'])
    else:
        records = data['records']
    tile_minima = {}
    for record in records:
        weight = record['multiplicity']
        if type(weight) is not int or weight <= 0:
            raise ValueError('Invalid invocation multiplicity')
        if record['subsurface'] != 0 or not record['filtered_reached'] or record['offset'] != 1 / data['declared_resolution']:
            excluded += weight; continue
        u, v, reference = record['shadow_position']
        if not math.isfinite(reference) or not 0 <= reference <= 1:
            excluded += weight; continue
        tiles = support_tiles(u, v, record['offset'], width, height, data['uv_error_bound'])
        # Fixed-four candidate must FALL BACK before query at larger supports.
        if tiles is None or len(tiles) > 4:
            excluded += weight; continue
        eligible += weight
        query_count += len(tiles) * weight
        for tile in tiles:
            if tile not in tile_minima:
                tx, ty = tile
                values = [depth[(ty*4+dy)*width + tx*4+dx] for dy in range(4) for dx in range(4)]
                tile_minima[tile] = min(values) if all(math.isfinite(x) for x in values) else -math.inf
        error, margin = data['reference_error_bound'], data['compare_margin']
        if not all(math.isfinite(x) and x >= 0 for x in (error, margin)):
            raise ValueError('Invalid comparison/error bound')
        hits += weight * all(tile_minima[tile] > reference + error + margin for tile in tiles)
    result = expression_screen(width, height, math.prod(data['output_extent']), eligible, hits, query_count)
    result.update({'snapshot_sha256': digest(snapshot.read_bytes()), 'depth_sha256': digest(raw),
        'eligible_invocations': eligible, 'all_lit_invocations': hits, 'excluded_native_invocations': excluded,
        'population': data['population'], 'whole_forward_expression_screen_pass': data['population'] == 'complete-forward-invocations' and result['optimistic_expression_screen_pass'],
        'runtime_provenance_independently_verified': False,
        'caveat': 'Structural snapshot checks do not prove hook completeness, actual invocation capture, derivatives, driver arithmetic or GPU cost. Visible subset cannot reject/admit whole-forward candidate.'})
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--output', type=Path)
    group.add_argument('--snapshot', type=Path)
    args = parser.parse_args()
    result = build(args.output) if args.output else analyze(args.snapshot)
    print(json.dumps(result, indent=2))

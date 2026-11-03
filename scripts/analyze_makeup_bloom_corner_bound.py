#!/usr/bin/env python3
"""Source-only G10 symmetric-quad clamp-corner opportunity ceiling.

No GPU, profile mutation or historical coordinate replay. Counts nominal
fullscreen centers using exact rationals; filter/mip assumptions are explicit.
"""
from __future__ import annotations

import argparse
from fractions import Fraction
import json
import math
from pathlib import Path

from build_makeup_bloom_pairing import BASE_SHA256, BLOOM, CALLER, NATIVE_TAPS, sha
from build_makeup_adapter import read_archive


def corner_bound(width, height, samples):
    if min(width, height, samples) < 1 or max(width, height) > 4096:
        raise ValueError('Positive dimensions <=4096 and positive iterations required')
    # Native softBaseSize=128. Select floor(log2(max(1,height/128)))
    # without floating log at powers of two. Both actual mip allocations remain
    # a runtime assumption: the finer level gives the tighter corner plateau.
    level = 0
    while height >= 128 * 2 ** (level + 1):
        level += 1
    mip_width, mip_height = max(1, width >> level), max(1, height >> level)

    def edge_count(size, mip):
        # Include equality: clamp plateau extends through first texel center.
        return sum(Fraction(2*x+1, 2*size) <= Fraction(1, 2*mip)
                   for x in range(size))

    nx, ny = edge_count(width, mip_width), edge_count(height, mip_height)
    # At mip size1 the two edges overlap. This consumer's 128-texel vertical
    # base excludes it, but count their union for small fixture dimensions.
    corners = min(width, 2*nx) * min(height, 2*ny)
    queries = width * height * 4 * samples
    return dict(width=width, height=height, iterations=samples,
                native_query_expressions=queries, finer_mip_level=level,
                assumed_finer_mip_size=[mip_width, mip_height],
                nominal_centers_per_corner_axis=[nx, ny],
                corner_pixel_ceiling=corners,
                first_ring_saved_expression_ceiling=3*corners,
                first_ring_saved_fraction=3*corners/queries,
                all_rings_saved_expression_ceiling=3*samples*corners,
                all_rings_saved_fraction=3*samples*corners/queries,
                fullscreen_predicate_evaluations=width*height,
                pixels_per_first_ring_saved_expression=(width*height/(3*corners)
                                                        if corners else None))


def analyze(archive, options, dimensions):
    archive, options = Path(archive), Path(options)
    if sha(archive.read_bytes()) != BASE_SHA256:
        raise ValueError('Exact pinned MakeUp9.5g archive required')
    files = read_archive(archive)
    bloom, caller, props = [files[n].decode() for n in
                           [BLOOM, CALLER, 'shaders/shaders.properties']]
    anchors = [NATIVE_TAPS, 'offset2 = vec2(-offset.y * 1.25, offset.x * 1.25);',
               'blurSample /= (BLOOM_SAMPLES * 4.0);']
    if not all(bloom.count(s) == 1 for s in anchors):
        raise ValueError('Native symmetric stencil/summation changed')
    if ('variable.float.softBaseSize = 128.0' not in props or
            'uniform.float.softLod = max(0.0, log(softLodScale) / 0.69314718)' not in props or
            'mipmap_bloom(colortex0, texcoord, dither)' not in caller):
        raise ValueError('Native consumer/LOD changed')
    vector = {}
    for line in options.read_text().splitlines():
        if line.strip() and not line.startswith('#'):
            k, v = line.split('=', 1)
            if k in vector:
                raise ValueError('Duplicate option')
            vector[k] = v
    samples = float(vector['BLOOM_SAMPLES'])
    if not math.isfinite(samples) or not samples.is_integer() or samples < 1:
        raise ValueError('Integer native iteration vector required')
    if vector.get('BLOOM') != 'true' or vector.get('DOF') != 'false':
        raise ValueError('This bound is for the selected bloom/noDOF consumer')
    return dict(kind='conditional-source-ceiling-not-GPU-census',
                archive=str(archive.resolve()), archive_sha256=BASE_SHA256,
                options=str(options.resolve()), options_sha256=sha(options.read_bytes()),
                native_options=vector,
                source_members={n: sha(files[n]) for n in
                                [BLOOM, CALLER, 'shaders/world0/composite1.fsh',
                                 'shaders/lib/dither.glsl', 'shaders/shaders.properties']},
                selected_graph='world0 composite1: colortex0 native mipmapped bloom -> colortex1; native TAA downstream retained',
                hypothesis='First-ring four native queries replaced by one only when all four share a clamp corner at BOTH consuming mips; keep four ordered additions',
                proof='The offsets contain +v and -v. Per axis max rounded coordinate >= center and min <= center (finite monotonic addition). If all taps clamp to one corner, the center must already lie within that corner plateau at the finer mip. Rotation/radius can only shrink eligibility. Thus at most three expressions per qualifying pixel per ring can disappear. This necessary condition ignores offsets: ceiling, not achieved coverage.',
                assumptions=['CLAMP_TO_EDGE on both axes, native linear/trilinear filtering',
                             'Full-size colortex0, native floor-halved allocated mip sizes',
                             'Nominal fullscreen centers (x+.5)/width,(y+.5)/height; no new measured Asahi UV census',
                             'Native computed LOD consumes stated finer mip; actual uniform/driver allocation not remeasured',
                             'Finite native coordinates/offsets; no NaN or overflow'],
                limits='Conditional geometric ceiling, not filter certificate, physical transactions, runtime cost lower bound or universal no-gain theorem. All16 native phases bounded only for this symmetric-corner avenue under assumptions. Other same-coordinate, edge-pair or equal-value opportunities are outside it.',
                rows=[corner_bound(w, h, int(samples)) for w,h in dimensions],
                historical_high32_sensitivity=[corner_bound(w,h,8) for w,h in dimensions],
                driver_rendered=False, performance_measured=False)


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--archive', type=Path, required=True)
    p.add_argument('--options', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    result = analyze(a.archive, a.options, [(2560,1600),(1920,1200),(1919,1199)])
    with a.output.open('x') as f:
        json.dump(result, f, indent=2)
        f.write('\n')

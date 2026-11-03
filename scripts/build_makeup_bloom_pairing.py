#!/usr/bin/env python3
"""Private G10 MakeUp 9.5g bilinear-moment tap pairing. No installation/GL.

Default-off: 0 native, 1 cheaper cell pairing, 2 pair witness,
3 exact duplicate-coordinate reuse, 4 duplicate witness (witnesses untimed).
Exact archive admission; all native options and upstream licenses retained.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path
import zipfile

from build_makeup_adapter import read_archive

BASE = Path('/Users/pippo/github/shaderlod/dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip')
BASE_SHA256 = '0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8'
OPTION = 'SHADERLOD_G10_BLOOM_PAIRING'
BLOOM = 'shaders/lib/bloom.glsl'
CALLER = 'shaders/common/composite1_fragment.glsl'
CONFIG = 'shaders/lib/config.glsl'
PROPS = 'shaders/shaders.properties'
LANG = 'shaders/lang/en_US.lang'
ENTRY = 'shaders/world0/composite1.fsh'
GUARD = ('(SHADERLOD_G10_BLOOM_PAIRING >= 1 && SHADERLOD_G10_BLOOM_PAIRING <= 4) && '
         'defined SHADERLOD_G10_WORLD0 && defined IS_IRIS && '
         'defined IRIS_VERSION && IRIS_VERSION == 10812 && '
         'defined MC_VERSION && MC_VERSION == 12101 && '
         '!defined NETHER && !defined THE_END && '
         '!defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH')

HELPER = r'''
// ShaderLOD G10 modification: conditional bilinear-moment pairing, 2026-10-26.
// Original stencil and accumulation remain the fallback. No tone-map/AA reorder.
#if G10_GUARD
int shaderlodG10Pairs = 0;

bool shaderlodG10Cell(vec2 coords, vec2 radius, vec2 size) {
    // One center fraction per axis replaces two endpoint floors. A larger
    // 0.02-texel margin covers FP32 evaluation differences conservatively for
    // the <=4096 selected target. This changes admission, not pairing math.
    vec2 center = coords * size - 0.5;
    vec2 reach = radius * size;
    vec2 phase = fract(center);
    return all(lessThanEqual(size, vec2(4096.0))) &&
           all(greaterThan(center - reach, vec2(0.02))) &&
           all(lessThan(center + reach, size - vec2(1.02))) &&
           all(greaterThanEqual(phase, reach + vec2(0.02))) &&
           all(lessThan(phase, vec2(0.98) - reach));
}
#endif

'''.replace('G10_GUARD', GUARD)

SETUP = r'''
    #if G10_GUARD && SHADERLOD_G10_BLOOM_PAIRING <= 2
        // Iris full-size colortex0; mip sizes floor each level, also at odd sizes.
        float g10Level = floor(softLod);
        vec2 g10Size0 = max(floor(vec2(viewWidth, viewHeight) / exp2(g10Level)), vec2(1.0));
        vec2 g10Size1 = max(floor(vec2(viewWidth, viewHeight) / exp2(ceil(softLod))), vec2(1.0));
    #endif
'''.replace('G10_GUARD', GUARD)

NATIVE_TAPS = '''        blurSample += texture2DLod(image, coords + offset, softLod).rgb;
        blurSample += texture2DLod(image, coords - offset, softLod).rgb;
        blurSample += texture2DLod(image, coords + offset2, softLod).rgb;
        blurSample += texture2DLod(image, coords - offset2, softLod).rgb;'''

PAIRED_TAPS = r'''        #if G10_GUARD && SHADERLOD_G10_BLOOM_PAIRING >= 3
            vec2 g10A = coords + offset;
            vec2 g10B = coords - offset;
            vec2 g10C = coords + offset2;
            vec2 g10D = coords - offset2;
            if (i == 0 && softLod >= 0.0 && softLod <= 16.0 &&
                all(lessThanEqual(abs(g10A), vec2(1.0))) &&
                all(equal(g10A, g10B)) && all(equal(g10A, g10C)) && all(equal(g10A, g10D))) {
                // Reuse an actually identical native sample; retain all four
                // native additions in their original order, no filter moments.
                vec3 g10Sample = texture2DLod(image, g10A, softLod).rgb;
                blurSample += g10Sample;
                blurSample += g10Sample;
                blurSample += g10Sample;
                blurSample += g10Sample;
                #if SHADERLOD_G10_BLOOM_PAIRING == 4
                    shaderlodG10Pairs++;
                #endif
            } else {
G10_NATIVE
            }
        #elif G10_GUARD
            vec2 g10Radius = max(abs(offset), abs(offset2));
            // The first ring contains the useful small footprints. Keep later
            // rings native and avoid paying cell-guard ALU for them.
            if (i == 0 && shaderlodG10Cell(coords, g10Radius, g10Size0) &&
                shaderlodG10Cell(coords, g10Radius, g10Size1)) {
                // B(x,y)=a+bx+cy+dxy within each certified bilinear cell.
                // Native mean offset=0; mean(dx*dy)=-(9/32)*offset.x*offset.y.
                // +/- (3/4*x,-3/8*y) has the same mean and mixed moment.
                // Both new points lie inside the certified native bounding box.
                vec2 g10Offset = offset * vec2(0.75, -0.375);
                blurSample += texture2DLod(image, coords + g10Offset, softLod).rgb * 2.0;
                blurSample += texture2DLod(image, coords - g10Offset, softLod).rgb * 2.0;
                #if SHADERLOD_G10_BLOOM_PAIRING == 2
                    shaderlodG10Pairs++;
                #endif
            } else {
G10_NATIVE
            }
        #else
G10_NATIVE
        #endif'''.replace('G10_GUARD', GUARD).replace('G10_NATIVE', NATIVE_TAPS)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def replace_once(data, before, after):
    before, after = before.encode(), after.encode()
    if data.count(before) != 1:
        raise ValueError('Pinned unique source anchor missing: ' + before[:80].decode())
    return data.replace(before, after, 1)


def patch(files, zero_dither_only=False):
    result = files.copy()
    result[ENTRY] = replace_once(files[ENTRY], '#define COMPOSITE1_SHADER',
        '#define COMPOSITE1_SHADER\n// ShaderLOD G10 exact world0 caller admission.\n#define SHADERLOD_G10_WORLD0 1')
    result[CONFIG] = replace_once(files[CONFIG], '// Options\n',
        '// Options\n// ShaderLOD G10 private experiment; original behavior by default.\n'
        '#define SHADERLOD_G10_BLOOM_PAIRING 0 // [0 1 2 3 4] Native / pairing / witness / exact reuse / witness\n')
    result[BLOOM] = replace_once(files[BLOOM], 'vec3 mipmap_bloom(', HELPER + 'vec3 mipmap_bloom(')
    result[BLOOM] = replace_once(result[BLOOM], '    for(int i = 0;', SETUP + '\n    for(int i = 0;')
    result[BLOOM] = replace_once(result[BLOOM], NATIVE_TAPS, PAIRED_TAPS)
    result[CALLER] = replace_once(files[CALLER], '#ifdef BLOOM\n    uniform float softLod;',
        '#if defined BLOOM && !defined DOF\n'
        '#if ' + GUARD + '\n'
        '    uniform float viewWidth;\n    uniform float viewHeight;\n#endif\n#endif\n\n'
        '#ifdef BLOOM\n    uniform float softLod;')
    result[CALLER] = replace_once(result[CALLER], '    /* DRAWBUFFERS:1 */',
        '#if defined BLOOM && (SHADERLOD_G10_BLOOM_PAIRING == 2 || SHADERLOD_G10_BLOOM_PAIRING == 4)\n#if ' + GUARD + '\n'
        '    // Witness only: red=no pair, green=all iterations paired; excluded from timing.\n'
        '    float g10Fraction = float(shaderlodG10Pairs) / BLOOM_SAMPLES;\n'
        '    blockColor.rgb = vec3(1.0 - g10Fraction, g10Fraction, 0.0);\n'
        '#endif\n#endif\n    /* DRAWBUFFERS:1 */')
    result[PROPS] = replace_once(files[PROPS], 'screen = <profile>',
        'screen.G10_SCREEN = SHADERLOD_G10_BLOOM_PAIRING\n\nscreen = [G10_SCREEN] <profile>')
    result[LANG] = files[LANG] + (
        '\n# ShaderLOD G10 private experiment\n'
        'screen.G10_SCREEN=G10 bloom tap pairing\n'
        'option.SHADERLOD_G10_BLOOM_PAIRING=Bloom tap pairing\n'
        'option.SHADERLOD_G10_BLOOM_PAIRING.comment=Private experiment. 0 restores native taps. 2 paints branch coverage; do not time it.\n'
        'value.SHADERLOD_G10_BLOOM_PAIRING.0=Off (native)\n'
        'value.SHADERLOD_G10_BLOOM_PAIRING.1=On\n'
        'value.SHADERLOD_G10_BLOOM_PAIRING.2=Witness\n').encode()
    if zero_dither_only:
        # For native fullscreen finite positive UV and finite viewport uniforms,
        # i=0,dither=0 makes every native offset signed zero. Retain the four
        # original additions; query reuse introduces no filtering identity.
        before = """            vec2 g10A = coords + offset;
            vec2 g10B = coords - offset;
            vec2 g10C = coords + offset2;
            vec2 g10D = coords - offset2;
            if (i == 0 && softLod >= 0.0 && softLod <= 16.0 &&
                all(lessThanEqual(abs(g10A), vec2(1.0))) &&
                all(equal(g10A, g10B)) && all(equal(g10A, g10C)) && all(equal(g10A, g10D))) {"""
        after = """            // Native i+dither is exactly zero. Every native offset is
            // signed zero; fullscreen coords are finite and strictly positive.
            if (i == 0 && dither == 0.0 && softLod >= 0.0 && softLod <= 16.0) {"""
        result[BLOOM] = replace_once(result[BLOOM], before, after)
        result[BLOOM] = replace_once(result[BLOOM],
            'vec3 g10Sample = texture2DLod(image, g10A, softLod).rgb;',
            'vec3 g10Sample = texture2DLod(image, coords, softLod).rgb;')
    return result


def archive_bytes(files):
    result = io.BytesIO()
    with zipfile.ZipFile(result, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, data in sorted(files.items()):
            entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.create_system = 3
            entry.external_attr = 0o100644 << 16
            z.writestr(entry, data, compresslevel=9)
    return result.getvalue()


def options_bytes(raw, mode):
    # Preserve every original sidecar line except our own private option.
    if mode not in (0, 1, 2, 3, 4):
        raise ValueError('Unsupported mode')
    if any(line.strip().startswith(OPTION.encode() + b'=') for line in raw.splitlines()):
        raise ValueError('Supply untouched native sidecar')
    return raw + (b'' if not raw or raw.endswith(b'\n') else b'\n') + f'{OPTION}={mode}\n'.encode()


def build(base, output, options=None, zero_dither_only=False):
    base, output = Path(base), Path(output)
    if sha(base.read_bytes()) != BASE_SHA256:
        raise ValueError('Exact pinned MakeUp 9.5g archive required')
    if output.exists() or output.resolve() == base.resolve():
        raise ValueError('Fresh output required; preserve original')
    files = read_archive(base)
    changed = patch(files, zero_dither_only)
    metadata = {
        'id': 'G10', 'family': 'MakeUp Ultra Fast', 'version': '9.5g',
        'source_revision': 'ad2d9327ad485429e4eaf2b3f98cd738ada609de',
        'original_sha256': BASE_SHA256, 'default_off': True,
        'changed_members': {n: {'preimage_sha256': sha(files[n]), 'sha256': sha(changed[n])}
                            for n in files if files[n] != changed[n]},
        'native_options': 'Unchanged definitions, values, profiles, AA, DOF, PBR and graph',
        'guard': GUARD, 'variant': 'zero-dither-exact-reuse-r2' if zero_dither_only else 'cheap-center-certificate-and-exact-coordinate-reuse-r1',
        'zero_dither_only': zero_dither_only,
        'zero_dither_scope': 'Modes3/4 only: native i=0,dither=0 and finite positive fullscreen UV, finite viewport uniforms. Four native additions preserved. Modes1/2 retain their prior precision uncertainty.',
        'ancestry': 'Existing G10 pinned native bloom pairing builder; added cheaper conservative cell guard and separate exact-coordinate reuse alternative.',
        'pairing_precision_limit': 'Moment identity does not prove native fixed-point filtering/rounding parity. Modes1/2 retain this quality uncertainty; modes3/4 only reuse identical native sample coordinates with native additions.',
        'actual_iris_preprocess': False,
        'driver_rendered': False, 'performance_measured': False,
    }
    changed['shaderlod-g10-build.json'] = (json.dumps(metadata, indent=2, sort_keys=True) + '\n').encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open('xb') as f:
        f.write(archive_bytes(changed))
    receipt = dict(metadata, output=str(output.resolve()), output_sha256=sha(output.read_bytes()),
                   builder_sha256=sha(Path(__file__).read_bytes()))
    if options is not None:
        raw = Path(options).read_bytes()
        sidecar = Path(str(output) + '.txt')
        with sidecar.open('xb') as f:
            f.write(options_bytes(raw, 0))
        for mode, label in [(1, 'on'), (2, 'witness')]:
            output.with_name(output.name + '.' + label + '.txt').write_bytes(options_bytes(raw, mode))
        receipt['options'] = {'input': str(Path(options).resolve()), 'sha256': sha(raw),
                              'native_bytes_preserved_as_prefix': True,
                              'default_sidecar': str(sidecar)}
    Path(str(output) + '.json').write_text(json.dumps(receipt, indent=2) + '\n')
    return receipt


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', type=Path, default=BASE)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--options', type=Path, help='Untouched native .zip.txt; preserved verbatim plus private option')
    parser.add_argument('--zero-dither-only', action='store_true', help='Modes3/4 exact reuse with cheaper native zero-dither certificate')
    args = parser.parse_args()
    print(json.dumps(build(args.base, args.output, args.options, args.zero_dither_only), indent=2))

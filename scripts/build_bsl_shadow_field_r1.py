#!/usr/bin/env python3
"""Pinned BSL9-PCF current minimum-field candidate and exact Iris source gate.

Private local artifacts only. No runtime, Gradle, GPU, reload or field certificate
is established by this builder. Unknown qualification uniform defaults native.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path, PurePosixPath
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'dev/logs/cross-shader-performance-r1/bsl-shadow-tiles/field-r1'
BASE = ROOT / 'dependencies/upstream/other-pack-research/BSL_v10.1.8.zip'
IRIS = ROOT / 'dependencies/runtime/iris-neoforge-1.8.12+mc1.21.1.jar'
PATCH = ROOT / 'patches/bsl-shadow-tiles-r1'
ARCHIVE_SHA = '36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5'
IRIS_SHA = '3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83'
OPTION = 'SHADERLOD_BSL_SHADOW_FIELD'
TERRAIN = 'shaders/program/gbuffers_terrain.glsl'
SHADOWS = 'shaders/lib/lighting/shadows.glsl'
SETTINGS = 'shaders/lib/settings.glsl'
PROPERTIES = 'shaders/shaders.properties'
COMPOSITE = 'shaders/program/composite.glsl'
PINNED = {
    TERRAIN: 'c47d350132261e8f7b271f99ba8d060e4b38ee12a5da50597b664245f212c8d7',
    SHADOWS: 'b7cecede848a68f6118cdb1ad1c14dfaae32c7fbcdb82f3a11d14bf9274c064a',
    SETTINGS: '5617a7a13c37373c27e742d2dffb777a1437bae83cf25693451c104915050285',
    PROPERTIES: '8856162fcf6846e1b56f5c092e6eaee04af687875f85e87cde87e1bd0a0d3169',
    COMPOSITE: '43cc8317bdebf3f7e03021a0817b3b8870074b014d895d367cbaaf11dce62a07',
}
ALIAS = b'#if SHADERLOD_BSL_SHADOW_FIELD == 1\r\n#define shadowtex0 shadowtex0HW\r\n#define shadowtex1 shadowtex1HW\r\n#endif\r\n'
SCOPE = b'#define SHADERLOD_BSL_SHADOW_FIELD_TERRAIN()\r\n'
GUARD = '#if SHADERLOD_BSL_SHADOW_FIELD == 1 && defined SHADERLOD_BSL_SHADOW_FIELD_TERRAIN && defined OVERWORLD && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH && !defined ADVANCED_MATERIALS'
INCLUDE = (GUARD + '\r\n#include "/lib/shaderlod/bsl_shadow_field_query_r1.glsl"\r\n#endif\r\n\r\n').encode()
PRIMARY = ("    for (int i = 0; i < 9; i++) {\r\n"
           "        vec2 shadowOffset = shadowOffsets[i] * offset;\r\n"
           "        shadow0 += texture2DShadow(shadowtex0, vec3(shadowPos.st + shadowOffset, shadowPos.z));\r\n"
           "    }\r\n").encode()
PRIMARY_REPLACEMENT = ((GUARD + '\r\n'
    '    // Skip PRIMARY work before its first sample; retain original continuation.\r\n'
    '    if (shaderlodBslShadowFieldAllLit(shadowPos, offset, subsurface)) {\r\n'
    '        shadow0 = 9.0;\r\n'
    '    } else\r\n'
    '#endif\r\n'
    '    {\r\n').encode() + PRIMARY + b'    }\r\n')
ADDITIONS = {
    'shaders/lib/shaderlod/bsl_shadow_field_control_r1.glsl': 'field_control_r1.glsl',
    'shaders/lib/shaderlod/bsl_shadow_field_query_r1.glsl': 'field_query_r1.glsl',
    'shaders/lib/shaderlod/bsl_shadow_field_producer_r1.glsl': 'field_producer_r1.glsl',
}
CLASSES = ('features.FeatureFlags', 'samplers.IrisSamplers', 'shadows.ShadowRenderTargets',
    'shadows.ShadowRenderer', 'shadows.ShadowCompositeRenderer', 'targets.RenderTarget',
    'shaderpack.properties.ShaderProperties', 'gl.framebuffer.ViewportData',
    'pipeline.IrisRenderingPipeline', 'pathways.FullScreenQuadRenderer',
    'uniforms.CommonUniforms', 'uniforms.SystemTimeUniforms', 'targets.ClearPassCreator')


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def once(raw, before, after):
    if raw.count(before) != 1:
        raise ValueError('Missing or ambiguous pinned anchor')
    return raw.replace(before, after, 1)


def original_files():
    if digest(BASE.read_bytes()) != ARCHIVE_SHA or digest(IRIS.read_bytes()) != IRIS_SHA:
        raise ValueError('Pinned BSL/Iris bytes changed')
    with zipfile.ZipFile(BASE) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or any(PurePosixPath(n).is_absolute() or '..' in PurePosixPath(n).parts or '\\' in n or '\x00' in n for n in names):
            raise ValueError('Unsafe or duplicate member')
        return {n: archive.read(n) for n in names if not n.endswith('/')}


def apply(files):
    for member, expected in PINNED.items():
        if digest(files[member]) != expected:
            raise ValueError('Changed pinned source: ' + member)
    result = dict(files)
    result[SETTINGS] = once(result[SETTINGS], b'//Shader Options//',
        b'//ShaderLOD private current-shadow field; native options remain unchanged.\r\n'
        b'#define SHADERLOD_BSL_SHADOW_FIELD 0 // [0 1]\r\n\r\n//Shader Options//')
    result[TERRAIN] = once(result[TERRAIN], b'#include "/lib/lighting/forwardLighting.glsl"',
        SCOPE + b'#include "/lib/lighting/forwardLighting.glsl"')
    result[SHADOWS] = ALIAS + result[SHADOWS]
    entry = b'vec3 SampleFilteredShadow(vec3 shadowPos, float offset, float subsurface) {'
    result[SHADOWS] = once(result[SHADOWS], entry, INCLUDE + entry)
    result[SHADOWS] = once(result[SHADOWS], PRIMARY, PRIMARY_REPLACEMENT)
    # Separate raw/hardware sampler mode also affects native shafts; remap their
    # identical sampler use without changing the shafts/color source body.
    result[COMPOSITE] = once(result[COMPOSITE], b'//Fragment Shader', ALIAS + b'//Fragment Shader')
    result[PROPERTIES] += ("\r\n#Private ShaderLOD B4 current field; no native option override.\r\n"
        "#if SHADERLOD_BSL_SHADOW_FIELD == 1\r\n"
        "iris.features.required=HIGHER_SHADOWCOLOR SEPARATE_HARDWARE_SAMPLERS\r\n"
        "#endif\r\n"
        "program.world0/shadowcomp1.enabled=SHADERLOD_BSL_SHADOW_FIELD == 1 && SHADOW\r\n"
        "scale.shadowcomp1=0.0625 0.0 0.0\r\n").encode()
    for target, name in ADDITIONS.items():
        if target in files:
            raise ValueError('New helper collision')
        result[target] = (PATCH / name).read_bytes()
    for stage in ('vsh', 'fsh'):
        target = 'shaders/world0/shadowcomp1.' + stage
        if target in files:
            raise ValueError('New field wrapper collision')
        result[target] = ('// Private ShaderLOD field; original BSL producer remains intact.\n'
            '#version 330 compatibility\n#define OVERWORLD\n#define ' + ('VSH' if stage == 'vsh' else 'FSH') + '\n'
            '#include "/lib/shaderlod/bsl_shadow_field_producer_r1.glsl"\n').encode()
    return result


def build(output, option):
    output = Path(output).resolve()
    sidecar, receipt = Path(str(output) + '.txt'), output.with_suffix('.json')
    if option not in (0, 1) or output.suffix != '.zip' or not output.is_relative_to(OUT.resolve()) or any(p.exists() for p in (output, sidecar, receipt)):
        raise ValueError('Use fresh private field ZIP under owned ignored directory')
    original = original_files()
    files = apply(original)
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'x', zipfile.ZIP_DEFLATED) as archive:
        for name, raw in sorted(files.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, raw)
    sidecar.write_text(OPTION + '=' + str(option) + '\n')
    record = {'kind': 'private-BSL-native9-PCF-current-minimum-field', 'private_field_option': option,
        'archive_sha256': digest(output.read_bytes()), 'sidecar_sha256': digest(sidecar.read_bytes()),
        'original_archive_sha256': ARCHIVE_SHA, 'iris_sha256': IRIS_SHA,
        'builder_sha256': digest(Path(__file__).read_bytes()),
        'original_member_sha256': {n: digest(raw) for n, raw in original.items()},
        'modified_members': sorted(n for n in original if original[n] != files[n]),
        'added_members': sorted(set(files) - set(original)),
        'field_member_sha256': {n: digest(raw) for n, raw in files.items() if n not in original or raw != original[n]},
        'license': 'LicenseRef-All-Rights-Reserved; private local adaptation only; no redistribution',
        'native_options_changed': [], 'native_primary_pcf_count': 9,
        'source_control': 'core protocol2/enabled/preset0,1,2 AND root per-frame shaderlodBslShadowFieldQualified=1',
        'default_missing_certificate': '0; native receiver, producer quad clipped',
        'source_cost_2048': cost(2048, 1920*1200, 1920*1200, 0),
        'driver_compiled': False, 'runtime_consumption': False, 'current_epoch_qualified': False,
        'quality_accepted': False, 'gpu_gain_measured': False,
        'restoration': 'private option0 removes field feature/pass at source; actual native-equivalent allocation/options still needs root certificate; exact originalZIP+empty sidecar is stock restoration'}
    receipt.write_text(json.dumps(record, indent=2) + '\n')
    return record


def cost(resolution, pixels, invocations, hits):
    if resolution != 2048 or pixels <= 0 or invocations < 0 or not 0 <= hits <= invocations:
        raise ValueError('Outside fixed native scope/cost domain')
    # Reserved current-frame marker sacrifices its original depth tile.
    reads = (128*128 - 1) * 256
    return {'useful_field_extent': [128, 128], 'producer_raw_reads': reads,
        'producer_scalar_writes': 16384, 'useful_bytes': 65536,
        'physical_r32f_pair_bytes': 2*2048*2048*4,
        'source_fullscreen_quads_per_current_shadow': 1,
        'source_viewport': [0, 0, 128, 128], 'tile_min_queries_per_eligible_receiver': 4,
        'epoch_queries_per_eligible_receiver': 1, 'added_receiver_sampler': 1,
        'native_primary_expressions': 9*invocations,
        'candidate_primary_plus_field_expressions': reads + 5*invocations + 9*(invocations-hits),
        'p_invocations_per_output_pixel': invocations/pixels,
        'h_hit_fraction': hits/invocations if invocations else 0,
        'optimistic_hit_fraction_threshold': (5 + reads/invocations)/9 if invocations else None,
        'requested_steady_field_clear_bytes_per_frame': 0,
        'initial_or_resizing_full_field_clear_bytes': 33554432,
        'clear_boundary': 'Exact Iris skips steady clear when shadowcolor2Clear=false; full initial/resizing clear still clears both full images. Actual recognition/calls remain root runtime check.',
        'registers_occupancy_spills': 'unmeasured; GLSL temporaries are not physical register counts',
        'gpu_gain_measured': False}


def support_cells(u, v):
    """Reference of shader's 1-texel expanded support at fixed native 2048.

    This proves the source geometry superset; not driver coordinate/PCF bounds.
    """
    if not all(math.isfinite(x) for x in (u, v)):
        return None
    lo = [math.floor(value * 2048 - 2.5) for value in (u, v)]
    hi = [math.floor(value * 2048 + 1.5) + 1 for value in (u, v)]
    if min(lo) < 0 or max(hi) >= 2048:
        return None
    first = tuple(x // 16 for x in lo)
    last = tuple(x // 16 for x in hi)
    if any(last[i] - first[i] > 1 for i in (0, 1)):
        return None
    return {first, (last[0], first[1]), (first[0], last[1]), last}


def scalar_field(depth, resolution, frame):
    """Scalar test/reference only. No scene snapshot or GPU result is invented."""
    if resolution != 2048 or len(depth) != resolution**2 or not 0 <= frame < 16777215:
        raise ValueError('Outside exact field producer domain')
    field = []
    for cy in range(128):
        for cx in range(128):
            if (cx, cy) == (127, 127):
                field.append(-float(frame + 1)); continue
            values = [depth[(cy*16+y)*resolution+cx*16+x] for y in range(16) for x in range(16)]
            field.append(min(values) if all(math.isfinite(x) and 0 <= x <= 1 for x in values) else 0.)
    return field


def scalar_hit(field, u, v, reference, frame):
    cells = support_cells(u, v)
    if (len(field) != 16384 or cells is None or not 0 <= reference <= 1 or
        not 0 <= frame < 16777215 or field[-1] != -float(frame+1)):
        return False
    return all(field[y*128+x] > reference + 1/65536 for x, y in cells)


def inspect(output):
    output = Path(output).resolve()
    if output.exists() or not output.is_relative_to(OUT.resolve()):
        raise ValueError('Use fresh owned inspection directory')
    if digest(IRIS.read_bytes()) != IRIS_SHA:
        raise ValueError('Changed Iris binary')
    output.mkdir(parents=True)
    rows = []
    with zipfile.ZipFile(IRIS) as jar:
        for name in CLASSES:
            qualified = 'net.irisshaders.iris.' + name
            member = qualified.replace('.', '/') + '.class'
            raw = subprocess.run(['/usr/bin/javap', '-p', '-c', '-constants', '-classpath', str(IRIS), qualified], capture_output=True, check=True).stdout
            path = output / (name + '.javap.txt'); path.write_bytes(raw)
            rows.append({'class': qualified, 'class_sha256': digest(jar.read(member)),
                'disassembly': str(path.relative_to(ROOT)), 'disassembly_sha256': digest(raw)})
    record = {'iris_sha256': IRIS_SHA, 'classes': rows,
        'boundary': 'Exact bytecode source inspection only; runtime binding/allocation/driver behavior unverified'}
    (output / 'receipt.json').write_text(json.dumps(record, indent=2) + '\n')
    return record


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--output', type=Path)
    group.add_argument('--inspect-output', type=Path)
    parser.add_argument('--option', type=int, choices=(0, 1), default=0)
    args = parser.parse_args()
    print(json.dumps(inspect(args.inspect_output) if args.inspect_output else build(args.output, args.option), indent=2))

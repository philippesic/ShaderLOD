#!/usr/bin/env python3
"""Private G08 exact native red texture representation; default off, no install.

The lean shader kernel is unchanged. R8 keeps every native 8-bit red value,
lookup, coordinate, iteration and quality setting. Actual sampler/content/output
equivalence and net timing still require the pinned driver. Raw RGBA8 is an
independent upload-path control; neither mode changes the gbuffers binding.
"""
import argparse
import json
from pathlib import Path

from build_cloud_volume_makeup_saturation import decoded_cloud_rgba
from build_makeup_unused_sky import read_archive, archive_bytes, once, sha, NATIVE

OPTION = 'SHADERLOD_G08_CLOUD_R8'
DEBUG = 'SHADERLOD_G08_CLOUD_R8_DEBUG'
CONFIG = 'shaders/lib/config.glsl'
CLOUD = 'shaders/lib/volumetric_clouds.glsl'
PROPS = 'shaders/shaders.properties'
TEXTURE = 'shaders/textures/clouds_natural_512_R_8bit.png'
RAW = 'shaders/textures/shaderlod_g08_cloud'
BASE = 'defined IS_IRIS && IRIS_VERSION == 10812 && MC_VERSION == 12101 && defined MC_OS_MAC && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH && V_CLOUDS == 2 && CLOUD_VOL_STYLE == 0 && CLOUD_SPEED == 0'
GATE = BASE + ' && defined DEFERRED_SHADER && !defined THE_END && !defined NETHER && !defined UNKNOWN_DIM'


def patch(native, diagnostic=False):
    files = dict(native)
    rgba = decoded_cloud_rgba(native[TEXTURE])
    files[RAW + '_rgba.bin'] = rgba
    files[RAW + '_r.bin'] = rgba[::4]
    # Explicit metadata avoids depending on loader filtering defaults.
    for suffix in ['_rgba.bin', '_r.bin']:
        files[RAW + suffix + '.mcmeta'] = b'{"texture":{"blur":true,"clamp":false}}\n'
    files[CONFIG] += (f'\n// Private G08 texture storage: native PNG / raw RGBA8 / raw R8.\n'
                      f'#define {OPTION} 0 // [0 1 2]\n#define {DEBUG} 0 // [0 1]\n').encode()
    anchor = 'texture.deferred.gaux2 = textures/clouds_natural_512_R_8bit.png'
    binding = (f'#if ({BASE}) && {OPTION} == 2\n'
               'texture.deferred.gaux2 = textures/shaderlod_g08_cloud_r.bin TEXTURE_2D R8 512 512 RED UNSIGNED_BYTE\n'
               f'#elif ({BASE}) && {OPTION} == 1\n'
               'texture.deferred.gaux2 = textures/shaderlod_g08_cloud_rgba.bin TEXTURE_2D RGBA8 512 512 RGBA UNSIGNED_BYTE\n'
               '#else\n' + anchor + '\n#endif')
    props = once(native[PROPS].decode(), anchor, binding)
    props = once(props, 'screen = ', 'screen = [SHADERLOD_G08_R8] ')
    props += f'\nscreen.SHADERLOD_G08_R8 = {OPTION} {DEBUG}\n'
    if diagnostic:
        guard = f'({GATE}) && {DEBUG} == 1'
        # A separate native PNG sampler, used only by the untimed comparison.
        # All candidate/reference inputs and native query spellings stay equal.
        reference = native[CLOUD].decode().replace('vec3 get_cloud(', 'vec3 shaderlodG08R8Reference(', 1)
        reference = reference.replace('texture2D(gaux2,', 'texture2D(shaderlodG08ReferenceNoise,')
        prefix = (f'#if {guard}\nuniform sampler2D shaderlodG08ReferenceNoise;\n'
                  'float shaderlodG08R8Result = 0.0;\n#endif\n')
        files[CLOUD] = (prefix + native[CLOUD].decode() + f'\n#if {guard}\n' + reference + '\n#endif\n').encode()
        props += (f'\n#if ({BASE}) && {DEBUG} == 1\n'
                  'customTexture.shaderlodG08ReferenceNoise = textures/clouds_natural_512_R_8bit.png\n'
                  'blend.deferred.colortex8=off\n#endif\n')
        caller = 'shaders/common/deferred_fragment.glsl'
        call = 'get_cloud(eyeDirection, blockColor.rgb, bright, dither, cameraPosition, CLOUD_STEPS_AVG, umbral, cloudColor, darkCloudColor)'
        old = '                blockColor.rgb = ' + call + ';'
        new = (f'                #if {guard}\n'
               '                vec3 shaderlodG08R8Native = ' + call.replace('get_cloud(', 'shaderlodG08R8Reference(', 1) + ';\n'
               '                #endif\n' + old + '\n'
               f'                #if {guard}\n'
               '                shaderlodG08R8Result = any(notEqual(blockColor.rgb, shaderlodG08R8Native)) ? 257.0 : 1.0;\n'
               '                #endif')
        source = once(native[caller].decode(), old, new)
        fmt = '''
/*
const int colortex8Format = R16F;
const bool colortex8Clear = true;
const vec4 colortex8ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
*/
'''
        source = once(source, '#include "/lib/config.glsl"', '#include "/lib/config.glsl"\n#if ' + guard + '\n' + fmt + '#endif\n')
        source = once(source, '/* DRAWBUFFERS:14 */', '#if ' + guard + '\n/* DRAWBUFFERS:148 */\n#else\n/* DRAWBUFFERS:14 */\n#endif')
        end = '    gl_FragData[1] = blockColor;'
        source = once(source, end, end + '\n#if ' + guard + '\ngl_FragData[2] = vec4(shaderlodG08R8Result);\n#endif')
        files[caller] = source.encode()
        files['SHADERLOD_FRAGMENT_WORK_DIAGNOSTIC.json'] = (json.dumps({
            'diagnostic_only': True, 'native': NATIVE, 'kind': 'G08-cloud',
            'encoding_mode': 'predicate-bits', 'encoding': '0 not a native cloud call,1 exact returned RGB,257 unequal or unordered component',
            'limits': 'Separate extra native PNG reference queries, R16F attachment and CPU readback. Not lean work or timing.'}, indent=2) + '\n').encode()
    files[PROPS] = props.encode()
    return files


def build(original, options, output, diagnostic=False):
    original, options, output = map(Path, (original, options, output))
    if output.exists() or Path(str(output) + '.txt').exists():
        raise ValueError('Fresh output required')
    native = read_archive(original)
    files = patch(native, diagnostic)
    meta = {'native': NATIVE, 'diagnostic_only': diagnostic, 'option': OPTION,
            'mechanism': 'Deferred natural cloud red storage RGBA8 to R8; identical native 8bit red, resolution, native sampling/kernel/positions/count/AA. Raw RGBA8 upload control.',
            'nominal_bytes': {'native_rgba8': 1048576, 'r8': 262144},
            'limits': 'Nominal format bytes are not hardware transaction counts or guaranteed physical cache savings. Native gbuffers binding preserved. Pinned heavy natural cloud configuration only; default Off. Runtime quality/economics unaccepted.'}
    files['SHADERLOD_G08_R8_NOTICE.json'] = (json.dumps(meta, indent=2) + '\n').encode()
    files['cloud-r8-source/build_makeup_cloud_r8.py'] = Path(__file__).read_bytes()
    output.write_bytes(archive_bytes(files))
    Path(str(output) + '.txt').write_bytes(options.read_bytes() + f'\n{OPTION}=0\n{DEBUG}=0\n'.encode())
    return dict(meta, artifact=str(output), sha256=sha(output.read_bytes()))


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    for name in ['original', 'options', 'output']:
        p.add_argument('--' + name, required=True, type=Path)
    p.add_argument('--diagnostic', action='store_true')
    print(json.dumps(build(**vars(p.parse_args())), indent=2))

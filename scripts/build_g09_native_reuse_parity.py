#!/usr/bin/env python3
"""Untimed pinned BSL G09 native-coordinate/depth/shadow equality witness.

Native reference outputs feed the ordinary renderer. Extra queries and RT8 are
diagnostic only; omission counters describe source expressions, not GPU fetches.
"""
import argparse
import json
import re
from pathlib import Path
import build_g09_native_reuse as reuse
from build_makeup_unused_sky import archive_bytes, once, sha

GATE = reuse.BASE + ' && defined PARALLAX && defined ADVANCED_MATERIALS && defined REFLECTION_SPECULAR && !defined MCBL_SS'
HEADER = f'''#if {GATE}
float shaderlodG09Saved = 0.0;
float shaderlodG09PomCalled = 0.0;
float shaderlodG09PomStepped = 0.0;
float shaderlodG09ShadowCalled = 0.0;
float shaderlodG09ShadowStepped = 0.0;
#endif
'''


def patch(native):
    files = reuse.patch(native)
    # Instrument actual candidate branches without changing arithmetic or order.
    kernel = files[reuse.KERNEL].decode()
    kernel = once(kernel, '    vec2 coord = vTexCoord.st;', '''    #ifdef SHADERLOD_G09_PARITY_ACTIVE
    shaderlodG09PomCalled = 1.0;
    #endif
    vec2 coord = vTexCoord.st;''')
    kernel = once(kernel, 'if (parallaxFade >= 1.0) {  return texCoord; }', '''if (parallaxFade >= 1.0) {
        #ifdef SHADERLOD_G09_PARITY_ACTIVE
        shaderlodG09Saved += 1.0;
        #endif
        return texCoord;
    }''')
    kernel = once(kernel, '    float depth = shaderlodG09Initial.a;', '''    #ifdef SHADERLOD_G09_PARITY_ACTIVE
    shaderlodG09Saved += 1.0;
    #endif
    float depth = shaderlodG09Initial.a;''')
    kernel = once(kernel, '        coord += stepDir;', '''        #ifdef SHADERLOD_G09_PARITY_ACTIVE
        shaderlodG09PomStepped = 1.0;
        #endif
        coord += stepDir;''')
    kernel = once(kernel, '    float parallaxshadow = 1.0;', '''    #ifdef SHADERLOD_G09_PARITY_ACTIVE
    shaderlodG09ShadowCalled = 1.0;
    #endif
    float parallaxshadow = 1.0;''')
    kernel = once(kernel, '        float currentHeight = height + parallaxdir.z * sampleStep * i;', '''        #ifdef SHADERLOD_G09_PARITY_ACTIVE
        shaderlodG09ShadowStepped = 1.0;
        #endif
        float currentHeight = height + parallaxdir.z * sampleStep * i;''')
    for name in ['texHeight', 'texHeightOffset']:
        kernel = once(kernel, f'offsetHeight = {name};  }}', f'''offsetHeight = {name};
            #ifdef SHADERLOD_G09_PARITY_ACTIVE
            shaderlodG09Saved += 1.0;
            #endif
        }}''')
    reference = native[reuse.KERNEL].decode().replace('\r\n', '\n')
    for name in ['ReadNormal', 'GetParallaxCoord', 'GetParallaxShadow']:
        reference = re.sub(r'\b' + name + r'\b', 'shaderlodG09Reference' + name, reference)
    files[reuse.KERNEL] = (HEADER + kernel + '\n#ifdef SHADERLOD_G09_PARITY_ACTIVE\n' + reference + '\n#endif\n').encode()
    terrain = files[reuse.TERRAIN].decode().replace('\r\n', '\n')
    terrain = once(terrain, '\tfloat surfaceDepth = 1.0;', '''\tfloat surfaceDepth = 1.0;
    #ifdef SHADERLOD_G09_PARITY_ACTIVE
    vec2 shaderlodG09CandidateCoord = newCoord;
    float shaderlodG09CandidateDepth = 1.0;
    float shaderlodG09Mismatch = 0.0;
    #endif''')
    terrain = once(terrain, '\t\tnewCoord = GetParallaxCoord(texCoord, parallaxFade, surfaceDepth);', '''        #ifdef SHADERLOD_G09_PARITY_ACTIVE
        newCoord = shaderlodG09ReferenceGetParallaxCoord(texCoord, parallaxFade, surfaceDepth);
        shaderlodG09CandidateCoord = GetParallaxCoord(texCoord, parallaxFade, shaderlodG09CandidateDepth);
        if (any(notEqual(newCoord, shaderlodG09CandidateCoord)) || surfaceDepth != shaderlodG09CandidateDepth)
            shaderlodG09Mismatch = 1.0;
        #else
\t\tnewCoord = GetParallaxCoord(texCoord, parallaxFade, surfaceDepth);
        #endif''')
    shadow = '''\t\t\tparallaxShadow = GetParallaxShadow(surfaceDepth, parallaxFade, newCoord, lightVec,
\t\t\t\t\t\t\t\t\t\t\t   tbnMatrix);'''
    terrain = once(terrain, shadow, '''        #ifdef SHADERLOD_G09_PARITY_ACTIVE
            parallaxShadow = shaderlodG09ReferenceGetParallaxShadow(surfaceDepth, parallaxFade, newCoord, lightVec, tbnMatrix);
            float shaderlodG09CandidateShadow = GetParallaxShadow(shaderlodG09CandidateDepth, parallaxFade, shaderlodG09CandidateCoord, lightVec, tbnMatrix);
            if (parallaxShadow != shaderlodG09CandidateShadow) shaderlodG09Mismatch = 1.0;
        #else
''' + shadow + '''
        #endif''')
    # Append the extra attachment after the native output layout, including its
    # conditional comments. MCBL_SS owns RT8 and is explicitly unsupported here.
    boundary = '\n}\n\n#endif\n\n//Vertex Shader'
    output = '''
    #ifdef SHADERLOD_G09_PARITY_ACTIVE
    /* DRAWBUFFERS:03678 */
    gl_FragData[4] = vec4(shaderlodG09Mismatch > 0.0 ? 257.0 : 1.0 + shaderlodG09Saved
        + 16.0 * shaderlodG09PomCalled + 32.0 * shaderlodG09PomStepped
        + 64.0 * shaderlodG09ShadowCalled + 128.0 * shaderlodG09ShadowStepped);
    #endif
'''
    terrain = once(terrain, boundary, output + boundary)
    # Keep the diagnostic condition out of Iris's user-option inventory.
    for name in [reuse.KERNEL]:
        files[name] = files[name].replace(b'#ifdef SHADERLOD_G09_PARITY_ACTIVE', ('#if '+GATE).encode())
    terrain = terrain.replace('#ifdef SHADERLOD_G09_PARITY_ACTIVE', '#if '+GATE)
    files[reuse.TERRAIN] = terrain.encode()
    # BSL declares the otherwise-unused MCBL attachment in final.glsl. Replace
    # that authoritative declaration only in this diagnostic/non-MCBL scope.
    final = files['shaders/program/final.glsl'].decode().replace('\r\n', '\n')
    final = once(final, 'const int colortex8Format = RGB8; //colored light', '')
    close = 'const int colortex16Format = RGBA16F;\n*/'
    final = once(final, close, close+'\n#if '+GATE+'\n/*\nconst int colortex8Format = R16F;\nconst bool colortex8Clear = true;\nconst vec4 colortex8ClearColor = vec4(0.0, 0.0, 0.0, 0.0);\n*/\n#else\n/* const int colortex8Format = RGB8; */\n#endif')
    files['shaders/program/final.glsl'] = final.encode()
    files[reuse.PROPS] += b'\nblend.gbuffers_terrain.colortex8=off\n'
    return files


def build(original, options, output):
    native = reuse.read_archive(original)
    files = patch(native)
    output = Path(output)
    if output.exists() or Path(str(output) + '.txt').exists():
        raise ValueError('Fresh diagnostic output required')
    side = Path(options).read_bytes()
    if b'SHADERLOD_' in side:
        raise ValueError('Native-only sidecar required')
    marker = dict(diagnostic_only=True, native=reuse.NATIVE, kind='G09-reuse',
                  encoding_mode='predicate-bits',
                  encoding='0 background; positive integer: base1 + saved-source-expressions(low4bits after subtracting1) +16 POMcalled +32 POMstepped +64 shadowCalled +128 shadowStepped;257 any coordinate/depth/shadow mismatch or unordered value',
                  limits='Visible last terrain fragment only, not overdraw or hardware fetch counts. Native reference RGB/coordinate/depth/shadow outputs feed rendering; extra native reference work and RT8/readback excluded from timing.')
    files['SHADERLOD_FRAGMENT_WORK_DIAGNOSTIC.json'] = (json.dumps(marker, indent=2) + '\n').encode()
    files['g09-parity-source/build_g09_native_reuse_parity.py'] = Path(__file__).read_bytes()
    output.write_bytes(archive_bytes(files))
    Path(str(output) + '.txt').write_bytes(side + b'\n' + (reuse.OPTION + '=0\n').encode())
    return dict(marker, artifact=str(output), sha256=sha(output.read_bytes()))


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    for name in ['original', 'options', 'output']:
        p.add_argument('--' + name, type=Path, required=True)
    print(json.dumps(build(**vars(p.parse_args())), indent=2))

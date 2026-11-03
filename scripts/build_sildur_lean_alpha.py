"""Private original native115 Overworld PCSS alpha candidate, no adapter expansion."""
import copy
from pathlib import Path
import zipfile
import build_sildur_underwater_ao as native

ROOT = native.ROOT
PROFILE = native.PROFILE
ORIGINAL = native.ORIGINAL
MEMBER = 'shaders/gbuffers_textured.fsh'
EXTENSION = '#extension GL_ARB_shader_texture_lod : enable\n'
ATLAS = '\tvec4 albedo = texture2D(texture, texcoord.xy)*color;'
SAMPLER = 'uniform sampler2D shadowtex0;'
QUERY = 'texture2D(shadowtex0, vertexShadowPosDiffuse.xy + (vec2(cos(angle), sin(angle)) * sqrt(alpha)) * searchScale)'
REWRITTEN_QUERY = QUERY.replace('texture2D(shadowtex0, ', 'shaderlodN04Shadow(', 1)
HELPER = '''uniform int shaderlodN04AlphaPermission;
uniform float shaderlodN04AlphaReference;
vec4 shaderlodN04Shadow(vec2 uv) {
    if (shaderlodN04AlphaPermission != 0) return texture2DLod(shadowtex0, uv, 0.0);
    return texture2D(shadowtex0, uv);
}
'''
GUARD = '    if (shaderlodN04AlphaPermission != 0 && !(albedo.a > shaderlodN04AlphaReference)) discard;\n'
GUARDED_SOURCE = '#if defined Shadows && defined PCSS && nMap == 0\n' + GUARD + '#endif\n'


def validate(pack, options):
    result = native.validate(pack, options)
    if result.get('nMap') != '0' or result.get('PCSS') != 'true' or result.get('Shadows') != 'true':
        raise ValueError('Original native115 nMap0 PCSS/Shadows required')
    return result


def build(pack, options, output):
    settings = validate(pack, options)
    output = Path(output)
    sidecar = Path(str(output) + '.txt')
    if output.exists() or sidecar.exists():
        raise ValueError('Fresh private Sildur output required')
    with zipfile.ZipFile(pack) as source:
        if len(source.namelist()) != len(set(source.namelist())):
            raise ValueError('Duplicate original member')
        raw = source.read(MEMBER)
        newline = '\r\n' if b'\r\n' in raw else '\n'
        fragment = raw.decode().replace('\r\n', '\n')
        for anchor in ['#version 120\n', ATLAS, SAMPLER, QUERY]:
            if fragment.count(anchor) != 1:
                raise ValueError('Pinned native alpha/PCSS anchor differs: ' + anchor)
        changed = fragment.replace('#version 120\n', '#version 120\n' + EXTENSION, 1)
        changed = changed.replace(SAMPLER, SAMPLER + '\n' + HELPER, 1)
        changed = changed.replace(ATLAS, ATLAS + '\n' + GUARDED_SOURCE, 1)
        changed = changed.replace(QUERY, REWRITTEN_QUERY, 1).replace('\n', newline).encode()
        output.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(output, 'x') as candidate:
            for info in source.infolist():
                candidate.writestr(copy.copy(info), changed if info.filename == MEMBER else source.read(info.filename))
    sidecar.write_bytes(Path(options).read_bytes())
    return dict(original_sha256=ORIGINAL, candidate_sha256=native.digest(output.read_bytes()),
                native_options=settings, changed_members=[MEMBER],
                scope='Only original Overworld alpha guard/PCSS query, permission zero retains native implicit sampling. All vertices, other members, native options and AA retained. Requires original-graph sampler/current-alpha/source permission; not performance or public compatibility qualification.')

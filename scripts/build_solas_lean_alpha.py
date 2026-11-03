"""Private N04 terrain-only Solas alpha prototype; permission defaults to native."""
import copy
import hashlib
import json
from pathlib import Path
import zipfile

import dev_client

ROOT = Path(__file__).resolve().parents[1]
ORIGINAL = 'b7dfe4c7353d677a92cc4c86b87bc0633f1014305d927ca6a482c303427bf825'
PROFILE = ROOT / 'src/main/resources/assets/shaderlod/solasquad/native428.json'
TERRAIN = 'shaders/programs/gbuffers_terrain.glsl'
LIBRARIES = ('shadows', 'cloudShadows', 'gbuffersLighting')
PREFIX = 'shaders/lib/shaderlod_n04/'
HELPER = '''uniform int shaderlodN04AlphaPermission;
uniform float shaderlodN04AlphaReference;
vec4 shaderlodN04Texture(sampler2D tex, vec2 uv) {
    if (shaderlodN04AlphaPermission != 0) { return textureLod(tex, uv, 0.0); }
    return texture2D(tex, uv);
}

'''
GUARD = '''    if (shaderlodN04AlphaPermission != 0 && !(albedo.a > shaderlodN04AlphaReference)) { discard; }

'''
QUERIES = {
    'shadows': (
        'texture2D(shadowtex, shadowPos.xy)',
        'texture2D(shadowcolor0, shadowPos.st)',
        'texture2D(noisetex, gl_FragCoord.xy / 512.0)',
    ),
    'cloudShadows': (
        'texture2D(noisetex, coord * 0.5 + vec2(0.17, -0.11))',
        'texture2D(noisetex, coord * 1.5 + vec2(-0.07, 0.19))',
    ),
}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def validate(pack, options):
    if digest(Path(pack).read_bytes()) != ORIGINAL:
        raise ValueError('Exact original Solas V3.7b required')
    actual = dev_client.properties(Path(options).read_text())
    expected = json.loads(PROFILE.read_text())
    if len(expected) != 428 or actual != expected:
        raise ValueError('Exact native428 required')
    return actual


def derive(members):
    """Only terrain FSH redirects; shared originals and VSH remain untouched."""
    result = dict(members)
    text = members[TERRAIN].decode()
    if text.count('#ifdef VSH') != 1:
        raise ValueError('Native vertex section changed')
    text, vertex = text.split('#ifdef VSH')
    anchor = '// Includes //\n'
    if text.count(anchor) != 1 or text.index(anchor) < text.index('#ifdef FSH'):
        raise ValueError('Native fragment include anchor changed')
    text = text.replace(anchor, HELPER + anchor)
    for name in LIBRARIES:
        old = f'#include "/lib/lighting/{name}.glsl"'
        if text.count(old) != 1:
            raise ValueError('Native terrain lighting include changed')
        text = text.replace(old, f'#include "/lib/shaderlod_n04/{name}.glsl"')
        source = members[f'shaders/lib/lighting/{name}.glsl'].decode()
        if name == 'gbuffersLighting':
            anchor = '    //Dynamic Hand Lighting\n'
            if source.count(anchor) != 1:
                raise ValueError('Native post-floodfill guard anchor changed')
            source = source.replace(anchor, GUARD + anchor)
        for query in QUERIES.get(name, ()):
            if source.count(query) != 1:
                raise ValueError('Native lighting query changed: ' + query)
            source = source.replace(query, query.replace('texture2D(', 'shaderlodN04Texture(', 1))
        result[PREFIX + name + '.glsl'] = source.encode()
    result[TERRAIN] = (text + '#ifdef VSH' + vertex).encode()
    return result


def build(pack, options, output):
    native = validate(pack, options)
    output = Path(output)
    sidecar = Path(str(output) + '.txt')
    if output.exists() or sidecar.exists():
        raise ValueError('Fresh candidate and sidecar required')
    with zipfile.ZipFile(pack) as source:
        if len(source.namelist()) != len(set(source.namelist())):
            raise ValueError('Duplicate original members')
        before = {info.filename: source.read(info.filename) for info in source.infolist()}
        if any(name.startswith(PREFIX) for name in before):
            raise ValueError('Private clone namespace already exists')
        after = derive(before)
        output.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(output, 'x') as candidate:
            for info in source.infolist():
                candidate.writestr(copy.copy(info), after[info.filename])
            for name in sorted(after.keys() - before.keys()):
                candidate.writestr(name, after[name])
    sidecar.write_bytes(Path(options).read_bytes())
    return {'original_sha256': ORIGINAL, 'candidate_sha256': digest(output.read_bytes()),
            'native_options': native, 'changed_members': [TERRAIN],
            'added_members': sorted(after.keys() - before.keys()),
            'scope': 'Private native428 terrain fragment only. Permission zero preserves original implicit queries and alpha behavior. No runtime, quality or performance qualification.'}

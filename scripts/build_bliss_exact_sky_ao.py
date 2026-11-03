"""Pinned original-derived Bliss sky SSAO bypass; private candidate, no admission widening."""
import copy
import hashlib
import json
from pathlib import Path
import zipfile
import dev_client

ROOT=Path(__file__).resolve().parents[1]
ORIGINAL='f41db92acc585fe9ffe0cc124d6ecaea27cef9ded91129ebc2a8e60226947b2c'
PROFILE=ROOT/'src/main/resources/assets/shaderlod/blissquad/native463.json'
MEMBERS={'shaders/dimensions/composite.fsh':'4d075c2ce671903abb4c3d98153581a3fceedc72966cf65461b5a91fedd2c5d6',
         'shaders/lib/indirect_lighting_effects.glsl':'b4b2d246ee465470e86f8731bd99ba606d129f442097fad42e39764cd6c676fe'}
ANCHOR=b'\t\tvec2 SSAO_SSS = SSAO(viewPos, FlatNormals, hand, isLeaf, noise);'
REPLACEMENT=b'''        vec2 SSAO_SSS = vec2(1.0,0.0);
        // Preserve the original call for NaN as well as every non-sky input.
        if (!(swappedDepth >= 1.0)) {
            SSAO_SSS = SSAO(viewPos, FlatNormals, hand, isLeaf, noise);
        }'''


def digest(data):return hashlib.sha256(data).hexdigest()


def validate(pack,options):
    if digest(Path(pack).read_bytes())!=ORIGINAL:raise ValueError('Exact original Bliss2.1.2 required')
    actual=dev_client.properties(Path(options).read_text());expected=json.loads(PROFILE.read_text())
    if len(expected)!=463 or actual!=expected:raise ValueError('Exact native463 required')
    return actual


def build(pack,options,output):
    native=validate(pack,options);output=Path(output);sidecar=Path(str(output)+'.txt')
    if output.exists() or sidecar.exists():raise ValueError('Fresh candidate and sidecar required')
    with zipfile.ZipFile(pack) as source:
        if len(source.namelist())!=len(set(source.namelist())):raise ValueError('Duplicate original members')
        for name,pin in MEMBERS.items():
            if digest(source.read(name))!=pin:raise ValueError('Native source pin changed: '+name)
        name='shaders/dimensions/composite.fsh';frag=source.read(name)
        if frag.count(ANCHOR)!=1:raise ValueError('Native SSAO call changed')
        # The actual caller has the identical immediately following overwrite.
        if b'if(swappedDepth >= 1.0) SSAO_SSS = vec2(1.0,0.0);' not in frag:raise ValueError('Native discarded-sky result changed')
        output.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(output,'x') as candidate:
            for info in source.infolist():
                data=source.read(info.filename)
                candidate.writestr(copy.copy(info),data.replace(ANCHOR,REPLACEMENT) if info.filename==name else data)
    sidecar.write_bytes(Path(options).read_bytes())
    return {'original_sha256':ORIGINAL,'candidate_sha256':digest(output.read_bytes()),
            'native_options':native,'changed_members':[name],
            'scope':'Private native463 sky-only SSAO bypass. Explicit texelFetch native SSAO; original final overwrite retained. No performance, whole-image or DH/PBR qualification.'}

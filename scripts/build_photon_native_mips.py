"""Pinned private Nether-only native mip and unused cloud-pass deletion."""
import copy
import hashlib
import json
from pathlib import Path
import zipfile
import dev_client

ROOT=Path(__file__).resolve().parents[1]
ORIGINAL='120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead'
PROFILE=ROOT/'src/main/resources/assets/shaderlod/photonquad/native366.json'
MEMBERS={'shaders/program/d4_deferred_shading.fsh':'212081e707c20c78e17cbfd3380b2f22eb696cc1aa14bdf006c07d59f53f67e0',
         'shaders/shaders.properties':'f8cc8df60f930612e8817e4176a34886c1a9de778c15427a5468ecbc4f71ee9a'}
ANCHOR=b'/*\nconst bool colortex5MipmapEnabled = true;\nconst bool colortex11MipmapEnabled = true;\n*/'
REPLACEMENT=b'''/*
const bool colortex5MipmapEnabled = true;
*/
#if defined WORLD_NETHER
/*
const bool colortex11MipmapEnabled = false;
*/
#else
/*
const bool colortex11MipmapEnabled = true;
*/
#endif'''

def digest(data):return hashlib.sha256(data).hexdigest()
def validate(pack,options):
    if digest(Path(pack).read_bytes())!=ORIGINAL:raise ValueError('Exact original Photon1.3b required')
    actual=dev_client.properties(Path(options).read_text());expected=json.loads(PROFILE.read_text())
    if len(expected)!=366 or actual!=expected:raise ValueError('Exact pinned native366 required')
    return actual

def build(pack,options,output):
    native=validate(pack,options);output=Path(output);sidecar=Path(str(output)+'.txt')
    if output.exists() or sidecar.exists():raise ValueError('Fresh candidate and sidecar required')
    with zipfile.ZipFile(pack) as source:
        names=source.namelist()
        if len(names)!=len(set(names)):raise ValueError('Duplicate original members')
        for name,pin in MEMBERS.items():
            if digest(source.read(name))!=pin:raise ValueError('Pinned native member changed: '+name)
        frag=source.read('shaders/program/d4_deferred_shading.fsh')
        if frag.count(ANCHOR)!=1:raise ValueError('Native mip anchor changed')
        props=source.read('shaders/shaders.properties')
        keys=[f'program.world-1/deferred{i}.enabled'.encode() for i in [1,2]]
        if any(key in props for key in keys):raise ValueError('Native Nether cloud pass controls changed')
        replacements={'shaders/program/d4_deferred_shading.fsh':frag.replace(ANCHOR,REPLACEMENT),
                      'shaders/shaders.properties':props+b'\n# Private native366 no-DH Nether cloud work deletion\n'+b'\n'.join(key+b' = false' for key in keys)+b'\n'}
        output.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(output,'x') as candidate:
            for info in source.infolist():candidate.writestr(copy.copy(info),replacements.get(info.filename,source.read(info.filename)))
    sidecar.write_bytes(Path(options).read_bytes())
    return {'original_sha256':ORIGINAL,'candidate_sha256':digest(output.read_bytes()),'native_options':native,'changed_members':list(MEMBERS),
            'scope':'Private exact native366/no-DH Nether d1+d2 and RT11 mip deletion; RT5 mip and Overworld/End retained. No public adapter or enablement.'}

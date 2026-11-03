"""N03 private Nether-only native366 final cloud-copy elision.

Flip properties apply globally: this artifact is intentionally not a public pack.
The maintained caller must stay in the Nether and restore the authentic original
before changing dimensions. Overworld cloud history requires these copies.
"""
import copy
from pathlib import Path
import zipfile
import build_photon_native_mips as native

ROOT=native.ROOT
ORIGINAL=native.ORIGINAL
PROFILE=native.PROFILE
PROPERTIES='shaders/shaders.properties'
TARGETS=(9,10,11,12)
ADDITION=b'\n# N03 private native366 NETHER ONLY; restore original before dimension changes\n'+b''.join(
    f'flip.composite16.colortex{target} = true\n'.encode() for target in TARGETS)

def validate(pack,options):return native.validate(pack,options)

def build(pack,options,output):
    settings=validate(pack,options);output=Path(output);sidecar=Path(str(output)+'.txt')
    if output.exists() or sidecar.exists():raise ValueError('Fresh candidate and sidecar required')
    with zipfile.ZipFile(pack) as source:
        if len(source.namelist())!=len(set(source.namelist())):raise ValueError('Duplicate original members')
        props=source.read(PROPERTIES)
        if native.digest(props)!=native.MEMBERS[PROPERTIES]:raise ValueError('Native properties changed')
        if any(f'flip.composite16.colortex{target}'.encode() in props for target in TARGETS):raise ValueError('Native terminal flip already declared')
        output.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(output,'x') as candidate:
            for info in source.infolist():
                data=source.read(info.filename)
                candidate.writestr(copy.copy(info),data+ADDITION if info.filename==PROPERTIES else data)
    sidecar.write_bytes(Path(options).read_bytes())
    return {'original_sha256':ORIGINAL,'candidate_sha256':native.digest(output.read_bytes()),
            'native_options':settings,'changed_members':[PROPERTIES],
            'scope':'Private native366 Nether ONLY: omit dead RT9..12 final copies; all shader/pass/mip source bytes and options unchanged. Unsafe Overworld; no runtime, speed or PBR qualification.'}

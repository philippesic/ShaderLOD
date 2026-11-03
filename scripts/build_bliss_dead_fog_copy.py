"""Private native463 Overworld RT13 late-flip experiment; no ordinary-route change."""
import copy
from pathlib import Path
import zipfile
import build_bliss_exact_sky_ao as native

ROOT=native.ROOT
ORIGINAL=native.ORIGINAL
PROFILE=native.PROFILE
PROPERTIES='shaders/shaders.properties'
PROPERTIES_SHA256='6c1b92c65d27eaf55f0723e5bb85e9c213aa6603d9eb558a3803caa7d3621e2f'
ADDITION=b'\n# Private native463 Overworld RT13 current-producer copy experiment\nflip.composite13.colortex13 = true\n'
validate=native.validate
digest=native.digest


def build(pack,options,output):
    profile=validate(pack,options);output=Path(output);sidecar=Path(str(output)+'.txt')
    if output.exists() or sidecar.exists():raise ValueError('Fresh candidate and sidecar required')
    with zipfile.ZipFile(pack) as original:
        if len(original.namelist())!=len(set(original.namelist())):raise ValueError('Duplicate original member')
        properties=original.read(PROPERTIES)
        if digest(properties)!=PROPERTIES_SHA256 or b'flip.composite13.colortex13' in properties:
            raise ValueError('Native final-stage flip properties changed')
        output.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(output,'x') as candidate:
            for info in original.infolist():
                candidate.writestr(copy.copy(info),original.read(info.filename)+(ADDITION if info.filename==PROPERTIES else b''))
    sidecar.write_bytes(Path(options).read_bytes())
    return {'original_sha256':ORIGINAL,'candidate_sha256':digest(output.read_bytes()),
            'native_options':profile,'changed_members':[PROPERTIES],
            'scope':'Private native463 Overworld 2560x1600 bounded fog overwrite; no DH/Voxy or ordinary-route enablement. Other dimensions/extents unqualified.'}

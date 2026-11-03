"""New N02 original-derived native463 unused RT12 outputs, only without DH."""
import copy
from pathlib import Path
import zipfile
import build_bliss_exact_sky_ao as native

ROOT=native.ROOT
ORIGINAL=native.ORIGINAL
PROFILE=native.PROFILE
MRT_ANCHORS={'shaders/dimensions/deferred1.fsh':(b'/* RENDERTARGETS:4,12 */',b'/* RENDERTARGETS:4 */'),
             'shaders/dimensions/composite.fsh':(b'/* RENDERTARGETS:3,14,12*/',b'/* RENDERTARGETS:3,14*/')}
OUTPUT=b'\t\tif(depth < 1.0)\r\n    \t\tgl_FragData[2] = vec4(vec3(0.0), depth * depth * 65000.0);\r\n\t\telse\r\n\t\t\tgl_FragData[2] = vec4(vec3(0.0), 65000.0);'

def validate(pack,options):return native.validate(pack,options)

def conditional(original,without_dh):
    return b'#ifdef DISTANT_HORIZONS\r\n'+original+b'\r\n#else\r\n'+without_dh+b'\r\n#endif'

def build(pack,options,output):
    settings=validate(pack,options);output=Path(output);sidecar=Path(str(output)+'.txt')
    if output.exists() or sidecar.exists():raise ValueError('Fresh candidate and sidecar required')
    with zipfile.ZipFile(pack) as source:
        if len(source.namelist())!=len(set(source.namelist())):raise ValueError('Duplicate original members')
        changed={}
        for member,(anchor,replacement) in MRT_ANCHORS.items():
            data=source.read(member)
            if data.count(anchor)!=1:raise ValueError('Native MRT anchor differs: '+member)
            changed[member]=data.replace(anchor,conditional(anchor,replacement))
        member='shaders/dimensions/composite.fsh'
        if changed[member].count(OUTPUT)!=1:raise ValueError('Native RT12 depth store differs')
        # Keep every other native operation, including the SSAO call and its overwrite.
        changed[member]=changed[member].replace(OUTPUT,b'#ifdef DISTANT_HORIZONS\r\n'+OUTPUT+b'\r\n#endif')
        output.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(output,'x') as candidate:
            for info in source.infolist():candidate.writestr(copy.copy(info),changed.get(info.filename,source.read(info.filename)))
    sidecar.write_bytes(Path(options).read_bytes())
    return {'original_sha256':ORIGINAL,'candidate_sha256':native.digest(output.read_bytes()),
            'native_options':settings,'changed_members':list(changed),
            'scope':'Private native463 no-DH unused RT12 MRT/output removal. Native live outputs, passes/options/AA retained; DH source branches retained but DH compatibility unqualified. Requires actual allocation/consumer/output/source checks.'}

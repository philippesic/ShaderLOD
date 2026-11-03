"""Private N04 opaque zero-emission arithmetic candidate, exact native463 only."""
import copy
from pathlib import Path
import zipfile
import build_bliss_exact_sky_ao as native

ROOT=native.ROOT
PROFILE=native.PROFILE
ORIGINAL=native.ORIGINAL
MEMBER='shaders/dimensions/composite1.fsh'
ANCHOR=b'\tfloat autoBrightnessAdjust = mix(5.0, 100.0, clamp(exp(-10.0*exposure),0.0,1.0));'
# Native463 fixes curve=2 and brightness=1; the actual attached fallback is checked.
# Exclude zero/subnormal Lighting, and underflow/overflow in the discarded target.
GUARD=b'''    if (floatBitsToUint(Emission) == 0u && abs(exposure) <= 8.0 &&
        all(bvec3(Albedo.x == 0.0 || abs(Albedo.x) >= 4.0E-38,
                  Albedo.y == 0.0 || abs(Albedo.y) >= 4.0E-38,
                  Albedo.z == 0.0 || abs(Albedo.z) >= 4.0E-38)) &&
        all(lessThanEqual(abs(Albedo), vec3(1000000.0))) &&
        all(greaterThanEqual(abs(Lighting), vec3(4.0E-38))) &&
        all(lessThanEqual(abs(Lighting), vec3(1.0E20)))) {
        Lighting = mix(Lighting, Albedo, 0.0);
        return;
    }
'''

# Used only after the maintained caller validates every UNORM16 scalar through the
# actual native decode.x/cubic functions in the same process before selection.
LEAN_GUARD=b'''    if (floatBitsToUint(Emission) == 0u && abs(exposure) <= 8.0 &&
        all(greaterThanEqual(abs(Lighting), vec3(4.0E-38))) &&
        all(lessThanEqual(abs(Lighting), vec3(1.0E20)))) {
        Lighting = mix(Lighting, Albedo, 0.0);
        return;
    }
'''

def validate(pack,options):return native.validate(pack,options)

def build(pack,options,output,*,lean=False):
    guard=LEAN_GUARD if lean else GUARD
    options_map=validate(pack,options);output=Path(output);sidecar=Path(str(output)+'.txt')
    if output.exists() or sidecar.exists():raise ValueError('Fresh candidate and sidecar required')
    with zipfile.ZipFile(pack) as source:
        if len(source.namelist())!=len(set(source.namelist())):raise ValueError('Duplicate original members')
        frag=source.read(MEMBER)
        if frag.count(ANCHOR)!=1:raise ValueError('Native emission adjustment anchor changed')
        if options_map.get('Emissive_Curve')!='2.0' or options_map.get('Emissive_Brightness')!='1.0' or options_map.get('EMISSIVE_TYPE')!='1':
            raise ValueError('Native emission domain changed')
        output.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(output,'x') as candidate:
            for info in source.infolist():
                data=source.read(info.filename)
                candidate.writestr(copy.copy(info),data.replace(ANCHOR,guard.replace(b'\n',b'\r\n')+ANCHOR) if info.filename==MEMBER else data)
    sidecar.write_bytes(Path(options).read_bytes())
    return {'original_sha256':ORIGINAL,'candidate_sha256':native.digest(output.read_bytes()),
            'native_options':options_map,'changed_members':[MEMBER],'guard_mode':'closed-native-albedo-domain' if lean else 'conservative',
            'scope':'Private original native463 opaque zero-emission guard; translucent call inactive and file unchanged. No performance or compatibility claim.'}

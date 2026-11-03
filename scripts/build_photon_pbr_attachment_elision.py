"""New N02 private LabPBR Nether tuple; the earlier native366 experiment is unchanged."""
import copy
import json
from pathlib import Path
import zipfile
import dev_client
import build_photon_native_mips as native_reference

ROOT=native_reference.ROOT
ORIGINAL=native_reference.ORIGINAL
PROFILE=native_reference.PROFILE
PBR_OPTIONS={'NORMAL_MAPPING':'true','SPECULAR_MAPPING':'true','POM':'true'}
digest=native_reference.digest


def expected_options():
    return json.loads(PROFILE.read_text())|PBR_OPTIONS


def validate(pack,options):
    if digest(Path(pack).read_bytes())!=ORIGINAL:raise ValueError('Exact original Photon1.3b required')
    actual=dev_client.properties(Path(options).read_text());expected=expected_options()
    if len(expected)!=366 or actual!=expected:raise ValueError('Exact N02 native PBR tuple required; no other quality/AA changes')
    return actual


def build(pack,options,output):
    native=validate(pack,options);output=Path(output);sidecar=Path(str(output)+'.txt')
    if output.exists() or sidecar.exists():raise ValueError('Fresh candidate and sidecar required')
    with zipfile.ZipFile(pack) as original:
        if len(original.namelist())!=len(set(original.namelist())):raise ValueError('Duplicate original member')
        for name,pin in native_reference.MEMBERS.items():
            if digest(original.read(name))!=pin:raise ValueError('Pinned native member changed: '+name)
        fragment_name='shaders/program/d4_deferred_shading.fsh';fragment=original.read(fragment_name)
        if fragment.count(native_reference.ANCHOR)!=1:raise ValueError('Native RT11 mip directive changed')
        properties_name='shaders/shaders.properties';properties=original.read(properties_name)
        keys=[f'program.world-1/deferred{i}.enabled'.encode() for i in [1,2]]
        if any(key in properties for key in keys):raise ValueError('Native Nether cloud pass properties changed')
        changed={fragment_name:fragment.replace(native_reference.ANCHOR,native_reference.REPLACEMENT),
                 properties_name:properties+b'\n# N02 private native LabPBR Nether unused cloud resources\n'+b'\n'.join(key+b' = false' for key in keys)+b'\n'}
        output.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(output,'x') as candidate:
            for info in original.infolist():candidate.writestr(copy.copy(info),changed.get(info.filename,original.read(info.filename)))
    sidecar.write_bytes(Path(options).read_bytes())
    return {'original_sha256':ORIGINAL,'candidate_sha256':digest(output.read_bytes()),'native_options':native,
            'changed_members':list(changed),'pbr_options':PBR_OPTIONS,
            'scope':'New N02 private native LabPBR Nether cloud-pass/RT11-mip deletion. Existing original native settings and all other shader members retained. Requires separate actual material, attachment, noDH/Voxy and lifetime qualification.'}

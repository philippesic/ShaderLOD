#!/usr/bin/env python3
"""Assemble private default-OFF same-draw light-rate diagnostics; never timed."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import tempfile
import zipfile
import build_photon_volume_light_rate as normal

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = Path('patches/photon-experiments/volume-light-rate-diagnostic.json')
EXPERIMENT = 'photon-volume-light-rate-diagnostic'
SWITCH = 'SHADERLOD_VOLUME_LIGHT_RATE_DIAGNOSTIC'
METADATA = 'shaderlod-volume-light-rate-diagnostic.json'
MARKER_UNIFORM = 'shaderlodVolumeLightRateDiagnosticSchema'
ARMS = ('disabled', 'sampling', 'enabled')
MEMBERS = (normal.SETTINGS, normal.CUMULUS, 'shaders/program/d1_clouds.fsh',
           'shaders/include/buffers.glsl', 'shaders/shaders.properties')
HELPER = 'shaders/include/shaderlod/volume_light_rate_diagnostic.glsl'
sha256 = normal.sha256
archive_members = normal.archive_members


def patch_members(original, manifest, arm='disabled', trace=False, root=ROOT):
    if (arm not in ARMS or manifest['schema'] != 1 or manifest['experiment'] != EXPERIMENT
        or manifest['switch'] != SWITCH or manifest['base_sha256'] != normal.BASE_SHA256
        or tuple(e['path'] for e in manifest['members']) != MEMBERS
        or manifest['storage']['attachments'] != [0,1,2]
        or manifest['storage']['colortexes'] != [9,10,11]
        or manifest['storage']['format'] != 'RGBA32F'
        or manifest['storage']['marker'] != 713001
        or manifest['layout']['cloud_extent_targets'] != [9,10,11,12,15]
        or tuple(e['path'] for e in manifest['additions']) != (normal.HELPER, HELPER)):
        raise ValueError('Unreviewed diagnostic scope/contract/arm')
    expected_inputs = {str(normal.MANIFEST), 'scripts/build_photon_volume_light_rate.py',
                       'shaderpacks/photon-volume-light-rate/' + normal.HELPER}
    if set(manifest['normal_inputs']) != expected_inputs:
        raise ValueError('Normal source pin scope changed')
    for name, pin in manifest['normal_inputs'].items():
        if sha256((root/name).read_bytes()) != pin:
            raise ValueError('Frozen normal source changed: '+name)
    normal_manifest = json.loads((root/normal.MANIFEST).read_bytes())
    baseline = normal.patch_members(original, normal_manifest, 'disabled', root)
    result = normal.patch_members(original, normal_manifest, arm, root)
    if sha256(original['LICENSE']) != manifest['license_sha256']:
        raise ValueError('Upstream attribution/license changed')
    for entry in manifest['members']:
        name = entry['path']
        if sha256(baseline[name]) != entry['source_sha256']:
            raise ValueError('Diagnostic input pin changed: '+name)
        source = baseline[name].decode()
        for replacement in entry['replacements']:
            before = replacement['before']
            if not before or source.count(before) != 1:
                raise ValueError('Ambiguous diagnostic source anchor: '+name)
            source = source.replace(before, replacement['after'], 1)
        if sha256(source.encode()) != entry['patched_sha256']:
            raise ValueError('Diagnostic result pin changed: '+name)
        if name == normal.SETTINGS:
            if trace:
                source = source.replace('//#define '+SWITCH+'\n', '#define '+SWITCH+'\n', 1)
                if sha256(source.encode()) != entry['trace_enabled_sha256']:
                    raise ValueError('Trace settings pin changed')
            if arm in ('sampling', 'enabled'):
                source = source.replace('//#define '+normal.SWITCH+'\n', '#define '+normal.SWITCH+'\n', 1)
            if arm == 'enabled':
                source = source.replace('//#define '+normal.REUSE_SWITCH+'\n', '#define '+normal.REUSE_SWITCH+'\n', 1)
        result[name] = source.encode()
    for entry in manifest['additions']:
        if entry['source_path'] != 'shaderpacks/photon-volume-light-rate-diagnostic/' + entry['path']:
            raise ValueError('Unreviewed diagnostic helper source')
        payload = (root/entry['source_path']).read_bytes()
        if sha256(payload) != entry['sha256'] or (entry['path'] == HELPER and entry['path'] in original):
            raise ValueError('Changed/conflicting diagnostic helper')
        result[entry['path']] = payload
    return result


def build(output=None, arm='disabled', trace=False, root=ROOT):
    base = (root/normal.BASE).resolve()
    output = (output or root/'build'/f'volume-light-rate-diagnostic-{arm}-{"on" if trace else "off"}.zip').resolve()
    if (not output.is_relative_to((root/'build').resolve()) or output == base
        or not output.name.startswith('volume-light-rate-diagnostic-') or output.suffix != '.zip'):
        raise ValueError('Output must be ignored build/volume-light-rate-diagnostic-*.zip')
    if sha256(base.read_bytes()) != normal.BASE_SHA256:
        raise ValueError('Pinned original archive changed')
    raw_manifest = (root/MANIFEST).read_bytes(); manifest = json.loads(raw_manifest)
    original = archive_members(base)
    if METADATA in original: raise ValueError('Conflicting diagnostic metadata')
    files = patch_members(original, manifest, arm, trace, root)
    modified = sorted(name for name in original if files[name] != original[name])
    if modified != sorted(MEMBERS): raise ValueError('Unexpected diagnostic upstream changes')
    refs = sorted(set(MEMBERS) | {normal.HELPER, HELPER, normal.COVERAGE, 'LICENSE',
                                'shaders/world0/deferred1.fsh','shaders/include/sky/clouds.glsl'})
    options = {SWITCH: [str(trace).lower()], normal.SWITCH: [str(arm != 'disabled').lower()],
               normal.REUSE_SWITCH: [str(arm == 'enabled').lower()],
               'CLOUDS_CUMULUS': ['true'], 'BLOCKY_CLOUDS': ['false']}
    metadata = {'schema':1,'experiment':EXPERIMENT,'diagnostic_only':True,
        'arm':arm,'trace_default':trace,'base_sha256':normal.BASE_SHA256,
        'manifest_sha256':sha256(raw_manifest),'normal_inputs':manifest['normal_inputs'],
        'reference_member_sha256':{name:sha256(files[name]) for name in refs},
        'required_effective_options':options,'modified_upstream_files':modified,
        'storage':manifest['storage'],'layout':manifest['layout'],
        'modes':manifest['modes'],'paths':manifest['paths'],
        'disabled_baseline':'Diagnostic OFF preprocesses normal candidate; private light-rate OFF selects literal upstream work.',
        'runtime_compilation':'unverified','performanceEvidence':False,
        'gpu_benefit':'unmeasured; instrumented draws cannot establish normal performance',
        'quality':'unaccepted; raw diagnostic overwrites cloud color/history; inspect normal arms separately',
        'attribution':manifest['attribution']}
    files[METADATA] = (json.dumps(metadata,indent=2,sort_keys=True)+'\n').encode()
    output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output.parent,suffix='.zip',delete=False) as temporary:
        temporary_path = Path(temporary.name)
    try:
        with zipfile.ZipFile(temporary_path,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
            for name,payload in sorted(files.items()):
                info=zipfile.ZipInfo(name,(1980,1,1,0,0,0)); info.compress_type=zipfile.ZIP_DEFLATED
                info.create_system=3;info.external_attr=0o100644<<16
                archive.writestr(info,payload,compresslevel=9)
        temporary_path.replace(output)
    finally: temporary_path.unlink(missing_ok=True)
    archive_sha = sha256(output.read_bytes())
    capability = {'effect':'VOLUMETRICS','operation':'shaderlod:work_trace','kind':'COUNT','minimumRetainedWork':1}
    registration = {'metadataVersion':1,
        'descriptor':{'id':f'shaderlod:photon-light-rate-diagnostic-{arm}','revision':1,
                      'protocols':{'shaderlod:core':2,'shaderlod:effects':2,'shaderlod:capabilities':1},
                      'capabilities':[capability]},
        'sourceBindings':[{'sourceSha256':archive_sha,'memberSha256':metadata['reference_member_sha256'],
                           'requiredOptions':options}],
        'exports':[{'effect':'VOLUMETRICS','operation':'shaderlod:work_trace','kind':'COUNT',
                    'uniformName':MARKER_UNIFORM}],
        'capture':{'source':str(output),'attachments':[0,1,2],'pages':[0,1,2],
                   'markerUniform':MARKER_UNIFORM,'markerValue':1,'gridSide':8,
                   'maxCaptures':8,'intervalDraws':60,'format':'RGBA32F','program':'deferred1',
                   'colortexes':[9,10,11]},
        'traceContract':metadata}
    registration_path=output.with_suffix('.registration.json')
    registration_path.write_text(json.dumps(registration,indent=2,sort_keys=True)+'\n')
    return {'archive':str(output),'sha256':archive_sha,'registration':str(registration_path),**metadata}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--arm',choices=ARMS,default='disabled')
    parser.add_argument('--trace',action='store_true',help='Enable private diagnostic; all timings invalid')
    parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    print(json.dumps(build(args.output,args.arm,args.trace),indent=2,sort_keys=True))


if __name__ == '__main__': main()

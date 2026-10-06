#!/usr/bin/env python3
"""Build pinned Photon diagnostics, never a production shader optimization."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import tempfile
import zipfile
from build_photon_density_detail_probe import archive_members, sha256

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = Path('patches/photon-experiments/shared-work-trace.json')
EXPERIMENT = 'photon-shared-work-trace'
SWITCH = 'SHADERLOD_WORK_TRACE'
METADATA = 'shaderlod-shared-work-trace.json'
SOURCES = {'original': '120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead',
           'shared': '5de7561dd24cdca80860a1d5f2c60400e74163a489cc163e916f3fd47feb27de'}
MEMBERS = ('shaders/settings.glsl', 'shaders/program/d1_clouds.fsh',
           'shaders/include/sky/clouds/cumulus.glsl',
           'shaders/include/sky/clouds/coverage_map.glsl',
           'shaders/include/buffers.glsl', 'shaders/shaders.properties')


def inventory(files: dict[str, bytes]) -> str:
    return sha256(json.dumps({n: sha256(v) for n,v in sorted(files.items())},
                            sort_keys=True, separators=(',', ':')).encode())


def patch_members(files: dict[str, bytes], manifest: dict, source: str,
                  trace: bool = False, root: Path = ROOT) -> dict[str, bytes]:
    if source not in SOURCES or manifest.get('schema') != 1 or manifest.get('experiment') != EXPERIMENT:
        raise ValueError('Unknown diagnostic source/schema')
    if manifest.get('switch') != SWITCH or manifest.get('base_version') != 'v1.3b':
        raise ValueError('Unreviewed diagnostic contract')
    if (manifest['storage'].get('attachments') != [0,1,2] or
        manifest['storage'].get('colortexes') != [9,10,11] or
        manifest['storage'].get('format') != 'RGBA32F' or
        manifest['storage'].get('exact_count_max') != 16777215 or
        manifest.get('layout',{}).get('cloud_extent_targets') != [9,10,11,12,15]):
        raise ValueError('Unreviewed exact storage contract')
    contract = manifest['sources'][source]
    if contract['sha256'] != SOURCES[source] or contract['inventory_sha256'] != inventory(files):
        raise ValueError('Frozen archive inventory mismatch')
    if sha256(files['LICENSE']) != contract['license_sha256'] or tuple(e['path'] for e in contract['members']) != MEMBERS:
        raise ValueError('License or member scope mismatch')
    result = files.copy()
    for entry in contract['members']:
        name = entry['path']
        if sha256(files[name]) != entry['source_sha256']:
            raise ValueError('Frozen source changed: '+name)
        patched = files[name].decode()
        for replacement in entry['replacements']:
            before, after = replacement['before'], replacement['after']
            if not before or patched.count(before) != 1:
                raise ValueError('Nonunique reviewed anchor: '+name)
            patched = patched.replace(before, after, 1)
        if sha256(patched.encode()) != entry['patched_sha256']:
            raise ValueError('Reviewed diagnostic fingerprint changed: '+name)
        if trace and name == 'shaders/settings.glsl':
            declaration = f'//#define {SWITCH}\n'
            if patched.count(declaration) != 1:
                raise ValueError('Private trace declaration mismatch')
            patched = patched.replace(declaration, f'#define {SWITCH}\n', 1)
            if sha256(patched.encode()) != entry['enabled_sha256']:
                raise ValueError('Trace-enabled settings pin mismatch')
        result[name] = patched.encode()
    additions = [manifest['helper']]
    if source == 'shared': additions.append(manifest['trace_adapter'])
    expected = ('shaders/include/shaderlod/work_trace.glsl', 'shaders/include/shaderlod/photon_volume_trace_adapter.glsl')
    if tuple(e['path'] for e in additions) != expected[:len(additions)]:
        raise ValueError('Helper scope mismatch')
    for entry in additions:
        data = (root/entry['source_path']).read_bytes()
        if entry['path'] in files or sha256(data) != entry['sha256']:
            raise ValueError('Helper collision/fingerprint mismatch')
        result[entry['path']] = data
    return result


def build(output: Path | None = None, source: str = 'original', trace: bool = False,
          root: Path = ROOT) -> dict:
    if source not in SOURCES: raise ValueError('Unknown source')
    output = (output or root/'build'/f'Photon-WorkTrace-{source}-{"on" if trace else "off"}.zip').resolve()
    if not output.is_relative_to((root/'build').resolve()) or output.suffix.lower() != '.zip':
        raise ValueError('Diagnostic output must be a ZIP under ignored build/')
    raw_manifest = (root/MANIFEST).read_bytes(); manifest = json.loads(raw_manifest)
    base = (root/manifest['sources'][source]['path']).resolve()
    # All inputs are immutable, including the production reference.
    if output == base or output in {(root/v['path']).resolve() for v in manifest['sources'].values()}:
        raise ValueError('May not overwrite frozen input')
    if sha256(base.read_bytes()) != SOURCES[source]: raise ValueError('Frozen ZIP fingerprint mismatch')
    original = archive_members(base); files = patch_members(original, manifest, source, trace, root)
    modified = sorted(n for n in original if files[n] != original[n]); added = sorted(set(files)-set(original))
    if modified != sorted(MEMBERS) or METADATA in files: raise ValueError('Diagnostic scope mismatch')
    reference_members = sorted(set(MEMBERS)|set(added)|{
        'LICENSE','shaders/world0/deferred1.fsh','shaders/include/sky/clouds.glsl'})
    if source == 'shared':
        reference_members += sorted(n for n in files if n.startswith('shaders/include/shaderlod/') and n not in reference_members)
    required = {SWITCH:['true'], 'CLOUDS_CUMULUS':['true'],'BLOCKY_CLOUDS':['false']}
    if source == 'shared': required['SHADERLOD_VOLUMETRIC_INTEGRATION_LOD']=['true']
    metadata = {'schema':1,'experiment':EXPERIMENT,'source':source,'diagnostic_only':True,
        'base_sha256':SOURCES[source],'manifest_sha256':sha256(raw_manifest),
        'modified_upstream_files':modified,'added_files':added,'trace_default':trace,
        'reference_member_sha256':{n:sha256(files[n]) for n in reference_members},
        'required_effective_options':required,'storage':manifest['storage'],'units':manifest['units'],
        'phase':manifest['phase'],'layout':manifest['layout'],'path_tags':manifest['path_tags'],'reason_tags':manifest['reason_tags'],
        'disabled_baseline':'Trace OFF preprocesses literal frozen source; original diagnostic and shared global OFF preserve original march; INHERIT/unity/protected/invalid paths retain original.',
        'quality_status':manifest['quality_status'],'runtime_compilation':'unverified',
        'gpu_benefit':'unmeasured; diagnostic work/timing cannot establish production performance',
        'attribution':manifest['attribution']}
    files[METADATA]=(json.dumps(metadata,indent=2,sort_keys=True)+'\n').encode()
    output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output.parent,suffix='.zip',delete=False) as temp: temporary=Path(temp.name)
    try:
        with zipfile.ZipFile(temporary,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
            for name,data in sorted(files.items()):
                info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
                info.create_system=3;info.external_attr=0o100644<<16
                archive.writestr(info,data,compresslevel=9)
        temporary.replace(output)
    finally: temporary.unlink(missing_ok=True)
    archive_sha=sha256(output.read_bytes())
    capability = {'effect':'VOLUMETRICS','operation':'shaderlod:volume_integration','kind':'COUNT','minimumRetainedWork':8}
    trace_capability = {'effect':'VOLUMETRICS','operation':'shaderlod:work_trace','kind':'COUNT','minimumRetainedWork':1}
    registration = {'metadataVersion':1,
        'descriptor':{'id':f'shaderlod:photon-work-trace-{source}','revision':1,
            'protocols':{'shaderlod:core':2,'shaderlod:effects':2,'shaderlod:capabilities':1},
            'capabilities':[capability,trace_capability]},
        'sourceBindings':[{'sourceSha256':archive_sha,'memberSha256':metadata['reference_member_sha256'],
                           'requiredOptions':required}],
        'exports':[{'effect':'VOLUMETRICS','operation':'shaderlod:work_trace','kind':'COUNT',
                    'uniformName':'shaderlodWorkTraceSchema'}],
        'capture':{'source':str(output),'attachments':[0,1,2],'pages':[0,1,2],
            'markerUniform':'shaderlodWorkTraceSchema','markerValue':1,'gridSide':4,
            'maxCaptures':8,'intervalDraws':60,'format':'RGBA32F','program':'deferred1',
            'colortexes':[9,10,11]},
        'traceContract':metadata}
    registration_path=output.with_suffix('.registration.json')
    registration_path.write_text(json.dumps(registration,indent=2,sort_keys=True)+'\n')
    return {'archive':str(output),'sha256':archive_sha,'registration':str(registration_path),**metadata}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',choices=SOURCES,default='original')
    parser.add_argument('--trace',action='store_true',help='Enable trace in archive defaults (still diagnostic-only)')
    parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    try: result=build(args.output,args.source,args.trace)
    except (OSError,ValueError,KeyError,zipfile.BadZipFile) as exc: parser.exit(1,f'Cannot assemble diagnostic: {exc}\n')
    print(json.dumps(result,indent=2,sort_keys=True))
if __name__=='__main__': main()

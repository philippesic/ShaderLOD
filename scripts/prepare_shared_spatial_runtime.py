#!/usr/bin/env python3
"""Qualify/copy frozen shared spatial controls; never assemble, build or launch."""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import shutil
import tomllib
import uuid
import zipfile

import build_photon_shared_spatial as adapter
import prepare_photon_ao_reuse_profiles as copies
import prepare_parallel_lod_profiles as parallel
import run_proof_matrix as matrix
from prepare_isolations import archive_contents, filter_effective_options
from setup_comparison import ROOT, WORLD, file_hash, world_fingerprint

SOURCE = copies.DEFAULT_SOURCES['forest']
FREEZE = Path('build/photon-shared-spatial-freeze.json')
FREEZE_SHA256 = '15982ab52d0a474e660f643a18f8d1839f2ff46273187ee44bab77f95d8938df'
MANIFEST_SHA256 = 'bd9fbf3f70bb117a1825f630cc3a20e34089bd7bf9f8dd14377e2cc72ac582fa'
WORLD_SHA256 = 'f752339d854ff2f0e0a7dc4e577dd6aedf2d0f69e39a9a7cae9dd0286c7be08b'
REPORT_SHA256, OPTIONS_SHA256 = parallel.SOURCE_PINS['forest']
ARCHIVES = {
    'original': 'c4e956dfee9ceebb6df245ea4792f5626cbc2d0ff995ccf9b49766d2286b57f1',
    'disabled': 'a8c004f5be0b341ed117220da0d2e2a0dadeefd62fd899ff64a3100c19a54ceb',
    'enabled': 'c2790b61bd4d096e24eedbf9ca800d35541ca81e8ec5efeecec122b764dbf6f0',
}
ARMS = {'original': ('original', 'FULL'), 'privateOFF': ('disabled', 'FULL'),
        'privateON-FULL': ('enabled', 'FULL'), 'privateON-ADAPTIVE': ('enabled', 'ADAPTIVE')}
require = copies.require
write = matrix.write_json


def qualify(root=ROOT, paths=None, freeze=None):
    """Inspect reviewed inputs in memory; no builder invocation or ZIP writes."""
    root = Path(root)
    manifest_path = root / adapter.MANIFEST
    require(file_hash(manifest_path) == MANIFEST_SHA256, 'Reviewed spatial manifest drift')
    freeze_path = Path(freeze) if freeze else root / FREEZE
    require(file_hash(freeze_path) == FREEZE_SHA256, 'Reviewed complete source freeze drift')
    ledger = json.loads(freeze_path.read_text())
    require(set(ledger) == set(ARCHIVES), 'Frozen arm inventory drift')
    runtime = json.loads((root / 'dev/runtime.lock.json').read_text())
    base = root / adapter.BASE
    original = archive_contents(base, adapter.BASE_SHA256, runtime['artifacts']['photon'])
    require(file_hash(base, 'sha512') == runtime['artifacts']['photon']['hashes']['sha512'], 'Runtime Photon pin drift')
    originals = adapter.archive_members(base)
    manifest = json.loads(manifest_path.read_text())
    overlay = adapter.overlays(root, manifest, originals)
    selected = {}
    for arm, digest in ARCHIVES.items():
        path = Path(paths[arm]) if paths and arm in paths else root / 'build' / f'Photon-SharedSpatial-{arm}.zip'
        require(not path.is_symlink(), 'Symlink archive unsupported')
        actual = adapter.archive_members(path)
        expected = adapter.patch_members(originals, manifest, arm)
        if arm != 'original': expected.update(overlay)
        require(set(actual) == set(expected) | {adapter.METADATA}, 'Spatial archive member inventory drift: ' + arm)
        require(actual['LICENSE'] == originals['LICENSE'], 'Photon license drift: ' + arm)
        require(all(actual[n] == b for n, b in expected.items()), 'Reviewed spatial member bytes drift: ' + arm)
        row = ledger[arm]
        require(row['archive_sha256'] == digest, 'Frozen archive pin drift: ' + arm)
        metadata = json.loads(actual[adapter.METADATA])
        require(metadata == {k:v for k,v in row.items() if k not in ('archive_sha256', 'reference_registration')},
                'Complete metadata/layout/options drift: ' + arm)
        pins = row['reference_member_sha256']
        require(all(adapter.sha256(actual[n]) == pin for n,pin in pins.items()), 'Reference member pin drift: ' + arm)
        registration = row['reference_registration']
        expected_bindings = [] if arm == 'original' else [dict(sourceSha256=digest, memberSha256=pins,
            requiredOptions=row['required_effective_options'])]
        require(registration['sourceBindings'] == expected_bindings and registration['layout'] == manifest['layout'],
                'Reviewed source registration/layout drift: ' + arm)
        require(registration['protocols'] == {'shaderlod:core':[2], 'shaderlod:effects':[2], 'shaderlod:capabilities':[1]}
                and registration['capability'] == dict(effect='opaque_lighting', kind='rate',
                    operation='shaderlod:opaque_spatial_irradiance', retainedRates=[1.,.25])
                and registration['export'] == dict(operation='shaderlod:opaque_spatial_irradiance', uniform='shaderlodOpaqueSpatialAvailable'),
                'Reviewed protocol/capability drift: ' + arm)
        require(file_hash(path) == digest, 'Complete reviewed archive bytes drift: ' + arm)
        with zipfile.ZipFile(path) as z: defaults = matrix.archive_defaults(z)
        require(defaults.get(adapter.SWITCH) == (None if arm == 'original' else str(arm == 'enabled').lower()),
                'Frozen private switch default drift: ' + arm)
        selected[arm] = dict(path=str(path.resolve()), sha256=digest, metadata=metadata,
            reference_registration=registration, complete_member_sha256={n:adapter.sha256(b) for n,b in actual.items()})
    return dict(qualified=True, freeze_sha256=FREEZE_SHA256, manifest_sha256=MANIFEST_SHA256,
        runtime_lock_sha256=file_hash(root / 'dev/runtime.lock.json'), runtime_lock=runtime,
        artifacts=selected, upstream=original | dict(path=str(base)))


def inspect_scene(source=SOURCE, root=ROOT, qualified=None):
    profile = Path(source).resolve(); root = Path(root)
    require(not any(p.is_symlink() for p in profile.rglob('*')), 'Symlink source unsupported')
    world = profile / 'saves' / WORLD
    require((world/'level.dat').is_file() and world_fingerprint(world) == WORLD_SHA256, 'Canonical forest world drift')
    report_path = profile/'workload-measurement.json'
    require(file_hash(report_path) == REPORT_SHA256, 'Canonical forest source report drift')
    report = json.loads(report_path.read_text()); effective = report['effective_shader_options']
    require(len(effective) == 366 and report['effective_shader_options_sha256'] == OPTIONS_SHA256
            and matrix.canonical_options_hash(effective) == OPTIONS_SHA256, 'Complete forest upstream option map drift')
    qualified = qualified or qualify(root)
    require(report['status'] == 'complete' and report['actual_shader_sha256'] == adapter.BASE_SHA256, 'Original forest source/status drift')
    for boundary in ('input_state_start','input_state_end'):
        state = report[boundary]
        require([state['framebuffer_width'],state['framebuffer_height']] == [2976,1672]
                and state['requested_chunks'] == state['effective_chunks'] == 24
                and state['shaders_in_use'] is True and state['fallback'] is False, 'Native source state drift')
    parent = profile.parent
    manifest_path, control_path = parent/'workload-manifest.json', parent/'control.json'
    manifest = json.loads(manifest_path.read_text())
    require(manifest.get('configuration_only') is False and manifest.get('source_world_sha256'), 'Source provenance absent')
    control, removed = copies.control_for_scene(json.loads(control_path.read_text()), 'forest')
    iris = matrix.read_option_properties((profile/'config/iris.properties').read_text()); selected = iris.get('shaderPack','')
    require(selected and Path(selected).name == selected and iris.get('enableShaders') == 'true', 'Source Iris selection drift')
    pack = profile/'shaderpacks'/selected
    require(file_hash(pack) == adapter.BASE_SHA256, 'Source original archive drift')
    requested = matrix.read_option_properties(pack.with_name(selected+'.txt').read_text())
    original = qualified['upstream']
    native, evidence = filter_effective_options(effective, requested, original, original)
    require(native | evidence['omitted_non_settings_defaults'] == effective, 'Complete upstream options not held')
    require(not any(n.startswith('SHADERLOD_') for n in effective), 'Upstream private option contamination')
    for arm,row in qualified['artifacts'].items():
        with zipfile.ZipFile(row['path']) as z: defaults = matrix.archive_defaults(z)
        options = native | ({} if arm == 'original' else {adapter.SWITCH:str(arm == 'enabled').lower()})
        matrix.normalize_requested_options(defaults, options)
        for n,allowed in row['metadata']['required_effective_options'].items():
            if n != adapter.SWITCH: require(effective.get(n) in allowed, 'Reviewed eligibility option mismatch: '+n)
    game = dict(line.split(':',1) for line in (profile/'options.txt').read_text().splitlines() if ':' in line)
    require(game.get('renderDistance') == '24', 'Source render distance drift')
    runtime = qualified['runtime_lock']; dh = runtime['artifacts']['distanthorizons']
    jars = list((profile/'mods').glob('*.jar'))
    require(len(jars) == 1 and jars[0].name == dh['filename'] and file_hash(jars[0],'sha512') == dh['hashes']['sha512'], 'Source DH inventory drift')
    files = [report_path, manifest_path, control_path, pack, pack.with_name(selected+'.txt'), *jars]
    files += [p for p in profile.rglob('*') if p.is_file() and (p.is_relative_to(profile/'config') or p.is_relative_to(profile/'resourcepacks'))]
    files.append(profile/'options.txt')
    return dict(profile=profile, world=world, control=control, removed_control_overrides=removed,
        native_options=native, upstream_effective_options=effective, option_evidence=evidence,
        source_loaded_mods=report['loaded_mods'], source_files_sha256={str(p):file_hash(p) for p in files})


def effects_config(mode):
    require(mode in ('FULL','ADAPTIVE'), 'Unsupported spatial diagnostic policy')
    config = tomllib.loads(parallel.effects_config('inherit'))
    config['effects']['opaque_lighting'].update(mode=mode, near=32., far=96., transition=8., mediumWorkRatio=1., farWorkRatio=.25)
    return '# Diagnostic requested effects; actual typed consuming-stage readback is required.\n' + '\n'.join(
        '[effects.'+name+']\n'+'\n'.join(k+'='+ (json.dumps(v) if not isinstance(v,bool) else str(v).lower()) for k,v in values.items())+'\n'
        for name,values in config['effects'].items())


def plan(output, source=SOURCE, root=ROOT):
    base = copies.plan(output, ('forest',), {'forest':source}, Path(root)/'build/Photon-SharedSpatial-enabled.zip', root)
    base.update(experiment='general-shared-spatial-driver-diagnostic', arms=list(ARMS),
        manifest=str(Path(root)/adapter.MANIFEST), manifest_sha256=MANIFEST_SHA256,
        source_report_sha256=REPORT_SHA256, upstream_effective_options_sha256=OPTIONS_SHA256,
        world_sha256=WORLD_SHA256, freeze_sha256=FREEZE_SHA256, untimed=True, diagnostic_only=True,
        performanceEvidence=False, gpuTiming=False, cpuTiming=False, cpuLayerTiming=False,
        cloudSamplerProbe=True, launch_status='HOLD: root integration/build and exclusive runtime ownership required')
    base['expected_capture_focus'] = False
    base['scene_operations'][0]['target_profiles'] = {arm:str(Path(base['output'])/'forest'/arm) for arm in ARMS}
    return base


def command(profile):
    args = ['./gradlew','runClientLodOn','-PrunDirectory='+str(profile),'-PperformanceStack=minimal','-PincludeDh=false',
        '-PmeasureWorkload=true','-PmeasureOnly=true','-PinitialWarmup=25','-PmeasurementSeconds=5',
        '-PbenchmarkWindowScale=2','-PcomparisonFullscreen=false','-PfixedWetness=0','-PcloudSamplerProbe=true']
    args += ['-P'+k+'=false' for k in ('gpuTiming','cpuTiming','cpuLayerTiming','gpuPassTiming','workTrace',
        'dhDrawEvidence','waterSceneSurvey','shadowObserve','visibilityLists','shadowCoherenceOracle')]
    for task in ('compileJava','compileDevClientJava','processResources','processDevClientResources'): args += ['-x',task]
    return args


def validate_report(report, expected, control):
    """Strict untimed workload guard; does not promote source or typed GPU proof."""
    require(report.get('status')=='complete' and report.get('request')==expected['request'], 'Incomplete/stale diagnostic report')
    require(report['request']['diagnostic_only'] is True and report['request']['performanceEvidence'] is False,
            'Diagnostic-only request missing')
    require(not report['gpu_samples'] and not report['cpu_samples'], 'Timing stream present in diagnostic')
    require(report['modPresent'] is True and report['dhPresent'] is False, 'Mod/noDH contract failed')
    require(report['actual_shader_sha256']==expected['archive_sha256'], 'Loaded source drift')
    require(report['effective_shader_options']==expected['expected_effective_options'] and
            report['effective_shader_options_sha256']==expected['expected_effective_options_sha256'], 'Loaded complete option map drift')
    require(report['warmup_seconds']==25 and report['elapsed_seconds']>=4.75, 'Warmup/capture too short')
    require(report['input_state_start']==report['input_state_end'], 'Inputs changed')
    state=report['input_state_end']
    require(state['shaders_in_use'] is True and state['fallback'] is False and state['lod_settings']['enabled'] is True,
            'Shader/global fallback mismatch')
    require([state['framebuffer_width'],state['framebuffer_height']]==[2976,1672] and
            state['requested_chunks']==state['effective_chunks']==24, 'Native extent/radius mismatch')
    require(state['dimension']=='minecraft:overworld' and state['day_time']==7000 and state['rain']==state['thunder']==0,
            'Forest scene/weather/time drift')
    for key in ('x','y','z','yaw','pitch'):
        require(matrix.camera_difference(state[key],control[key],key=='yaw')<.002,'Camera drift: '+key)
    window=report['window_state']['capture']; count=window['observations']
    require(count>0 and window['available_observations']==count and not window['transitions'] and not window['dropped_transitions'],
            'Window unavailable/changed')
    require(window['window_size_counts']=={'1488x836':count} and window['framebuffer_size_counts']=={'2976x1672':count},
            'Native window extent changed')
    for field,value in (('focused',False),('visible',True),('iconified',False)):
        require(window[field]==dict(true_observations=count if value else 0,false_observations=0 if value else count,unknown_observations=0),
                'Window '+field+' mismatch')
    return dict(diagnostic_only=True,performanceEvidence=False,
        temporal_observations=matrix.validate_temporal_inputs(report,expected['request']),
        remaining_checks='Typed consuming-stage identity/readback, physical layout, parity, motion, DH and performance remain separate')


def prepare(output, source=SOURCE, root=ROOT):
    operation = plan(output,source,root); matrix.require_exclusive_runtime(); qualified = qualify(root)
    with copies.unlocked_world(Path(source)/'saves'/WORLD):
        scene = inspect_scene(source,root,qualified)
        output = Path(operation['output']); output.mkdir(parents=True)
        base = output/'forest'; base.mkdir(); provenance = base/'source-provenance'; provenance.mkdir()
        for path in scene['source_files_sha256']:
            src = Path(path)
            relative = src.relative_to(scene['profile']) if src.is_relative_to(scene['profile']) else Path('parent')/src.name
            destination = provenance/relative; destination.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(src,destination)
        # License/settings bytes in archive_contents are inspection-only, not JSON fields.
        artifacts = qualified['artifacts']
        state = dict(schema=1,status='preparing',created_utc=datetime.now(timezone.utc).isoformat(),preparer_pid=os.getpid(),
            operation=operation, runtime_lock=qualified['runtime_lock'],runtime_lock_sha256=qualified['runtime_lock_sha256'],
            artifacts=artifacts, source_files_sha256=scene['source_files_sha256'], upstream_effective_options=scene['upstream_effective_options'],
            source_loaded_mods=scene['source_loaded_mods'], arms={}, owned_live_processes=[],resource_owner='root: idle GPU; preparer has no client/GPU ownership')
        marker = output/'shared-spatial-preparation.json'; write(marker,state)
        try:
            for arm,(source_arm,mode) in ARMS.items():
                profile = base/arm; row = artifacts[source_arm]
                shutil.copytree(scene['profile'],profile,ignore=shutil.ignore_patterns(*copies.TRANSIENT))
                world = profile/'saves'/WORLD; world.parent.mkdir()
                shutil.copytree(scene['world'],world,ignore=shutil.ignore_patterns('session.lock'))
                require(world_fingerprint(world) == WORLD_SHA256, 'Copied world drift: '+arm)
                dh = qualified['runtime_lock']['artifacts']['distanthorizons']; (profile/'mods'/dh['filename']).unlink()
                require(not list((profile/'mods').glob('*.jar')), 'Fresh unmanaged mod inventory')
                packs=profile/'shaderpacks'; packs.mkdir(); archive=Path(row['path']); shutil.copy2(archive,packs/archive.name)
                flags = {} if source_arm=='original' else {adapter.SWITCH:str(source_arm=='enabled').lower()}
                requested = scene['native_options'] | flags
                (packs/(archive.name+'.txt')).write_text(''.join(n+'='+v+'\n' for n,v in sorted(requested.items())))
                iris=matrix.read_option_properties((profile/'config/iris.properties').read_text()); iris['shaderPack']=archive.name
                (profile/'config/iris.properties').write_text(''.join(n+'='+v+'\n' for n,v in sorted(iris.items())))
                (profile/'config/shaderlod-client.toml').write_text(copies.fresh_config('adaptive'))
                (profile/'config/shaderlod-effects-client.toml').write_text(effects_config(mode))
                expected=scene['upstream_effective_options'] | flags
                require(len(expected)==366+(source_arm!='original'), 'Expected complete option inventory drift')
                request=dict(run_id=str(uuid.uuid4()),case='spatial-'+arm,stack='minimal',task='runClientLodOn',include_dh=False,
                    measurement_seconds=5.,gpu_queries=False,fixed_wetness=0.,diagnostic_only=True,performanceEvidence=False)
                write(profile/'workload-request.json',request)
                registration=base/(arm+'-source-registration.json'); write(registration,row['reference_registration'])
                state['arms'][arm]=dict(profile=str(profile),source_arm=source_arm,policy=mode,archive_sha256=row['sha256'],
                    source_registration=str(registration),source_registration_sha256=file_hash(registration),
                    expected_effective_options=expected,expected_effective_options_sha256=matrix.canonical_options_hash(expected),
                    expected_capability_available=int(source_arm=='enabled'),global_enabled=True,main_mod_present=True,dh_present=False,
                    command=command(profile),request=request,launch_status=operation['launch_status'],
                    stop_procedure='Before launch acquire dev/logs/.proof-matrix.lock and verify no clients. Record exact launcher PID/session and Java PID. Stop exact owned Java PID with SIGTERM and exact launcher process group with SIGTERM; after 20 s only those identities may receive SIGKILL. Verify both absent before next arm.',
                    input_files_sha256={str(p.relative_to(profile)):file_hash(p) for p in profile.rglob('*') if p.is_file() and not p.is_relative_to(world)})
                require(file_hash(packs/archive.name)==row['sha256'],'Copied archive changed')
            require(world_fingerprint(scene['world'])==WORLD_SHA256,'Source world changed during preparation')
            require(all(file_hash(Path(p))==pin for p,pin in scene['source_files_sha256'].items()),'Source inputs changed during preparation')
            write(base/'control.json',scene['control'])
            write(base/'workload-manifest.json',dict(schema=1,configuration_only=False,diagnostic_only=True,performanceEvidence=False,
                experiment_arms=state['arms'],source_world_sha256=WORLD_SHA256,source_option_evidence=scene['option_evidence'],
                control=scene['control'],runtime_lock=qualified['runtime_lock'],requested_chunks=24,expected_framebuffer=[2976,1672]))
            state['status']='complete'; write(marker,state)
        except BaseException as error:
            state.update(status='failed',error=str(error)); write(marker,state); raise
    return state


def main():
    parser=argparse.ArgumentParser(description=__doc__); parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--source',type=Path,default=SOURCE); parser.add_argument('--dry-run',action='store_true'); parser.add_argument('--qualify-only',action='store_true')
    args=parser.parse_args()
    try:
        operation=plan(args.output,args.source)
        if args.qualify_only:
            qualified=qualify(); scene=inspect_scene(args.source,qualified=qualified)
            result=operation | dict(status='qualified',upstream_effective_option_count=len(scene['upstream_effective_options']))
        elif args.dry_run: result=operation
        else:
            state=prepare(args.output,args.source); result=dict(status=state['status'],output=state['operation']['output'],launch_status=state['operation']['launch_status'])
        print(json.dumps(result,indent=2,sort_keys=True))
    except (OSError,ValueError,KeyError,zipfile.BadZipFile) as error: parser.exit(1,'Cannot prepare shared spatial controls: '+str(error)+'\n')

if __name__=='__main__': main()

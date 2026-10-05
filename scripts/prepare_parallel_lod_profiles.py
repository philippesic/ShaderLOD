#!/usr/bin/env python3
"""Prepare an explicit frozen Photon feature QA family; no build, client or performance claim."""
from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import tomllib
from types import MappingProxyType
import zipfile

import build_photon_reflection_lod as reflection
import build_photon_volumetric_lod as volume
import build_photon_procedural_lod as procedural
import build_photon_lighting_cost_probe as lighting
import prepare_photon_ao_reuse_profiles as copies
from prepare_isolations import archive_contents, filter_effective_options
from run_proof_matrix import archive_defaults, canonical_options_hash, normalize_requested_options, read_option_properties
from setup_comparison import ROOT, WORLD, file_hash

ORIGINAL_SHA256 = reflection.BASE_SHA256
LICENSE_SHA256 = '514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5'
require = copies.require


@dataclass(frozen=True)
class Family:
    builder: object
    manifest_sha256: str
    archive_name: str
    archive_sha256: str | None
    metadata_sha256: str | None
    experiment: str
    helpers: tuple[tuple[str, str], ...] = ()
    members: tuple[str, ...] = ()


# Reviewed adapters only. No arbitrary patch registry or archive-hash acceptance.
FAMILIES = MappingProxyType({
    'reflection': Family(reflection, '0c20609c50ee65d98ec803eb96294a0d727bff82d81a6634abb1504518d89e1a',
        'Photon-ReflectionFootprintLOD-disabled.zip', 'd11de4c94fb58520e9972a816e9739486fa8578b876fbd20c17aa933a4a82e16',
        'ea95ed11bb2625720a7f81dc8a400f98df2b3aca992f15d4a16e4d83cdb00c85', reflection.EXPERIMENT,
        (('shaders/include/shaderlod/reflection_depth_cache.glsl', 'shaderpacks/photon-reflection-lod/shaders/include/shaderlod/reflection_depth_cache.glsl'),),
        ('shaders/include/lighting/specular_lighting.glsl','shaders/settings.glsl')),
    'volume': Family(volume, '08e1611a77a2f6d6364b91eda692ea2e300b1c3c2891c87d7abd6ecec98def9b',
        'Photon-VolumetricLOD-disabled.zip', '3685e47e94cd08f3994053f300981bbdd92ae108a65f7fadb700bd6d9422a6b5',
        '1a5c62b2a549d487591fbbf1d3d04de2298e8ee0aa90e798d26c406eb678b83e', volume.EXPERIMENT, volume.ADDITIONS,
        ('shaders/include/sky/clouds/cumulus.glsl','shaders/settings.glsl')),
    'procedural': Family(procedural, '5849bc24c84f98b42a4ad7a85b013cdab6bb229a3f8c2d8e8c48d6097cd9e687',
        'Photon-ProceduralFootprint-disabled.zip', 'eb9fa7db90fffff1ee264b90098a2b14a8dd322d3e8a111b8460fc329eea0719',
        'd32929341020c8497bf4d471c754e4eb80cc041444e9ffa45377a6d73981be4c', 'photon-procedural-projected-footprint',
        tuple((name, str(procedural.HELPER_ROOT / name)) for name in (
            'shaders/include/shaderlod/procedural_footprint.glsl', 'shaders/include/shaderlod/procedural_parallax.glsl',
            'shaders/include/shaderlod/procedural_water.glsl')), ('shaders/settings.glsl','shaders/program/gbuffers_all_translucent.fsh')),
    'lighting-cost': Family(lighting, '630dffa44bbe096412295e2755edf8989ca881b246a0fbb73cff8c778d450ac9',
        'Photon-LightingRegionCostControl-disabled.zip', None, None, lighting.EXPERIMENT, (),
        ('shaders/program/d4_deferred_shading.fsh','shaders/include/lighting/shadows/pcss.glsl','shaders/settings.glsl')),
})
SOURCE_PINS = MappingProxyType({
    'forest': ('6f7662a31427f0d0863d926909c67a57b0dfba3c7ee2539c857f699c7ce3da5e',
               '4efc7af7742bc445adf2b3b1454aa448a557e1d4b024103dd5b9f5758ed68745'),
    'coastalwater': ('9524f3fdc9dec30f59a03704ad45f9cffa84b2058fe2ed48ea6a3095c31ba252',
                     'ecedfafc0a911b154db42ed786ffc468a694097ecacfb99852efeaa3dd6a5669'),
})
EFFECTS = ('shadow_filtering', 'ambient_occlusion', 'reflections', 'volumetrics', 'water_surface', 'materials', 'opaque_lighting')


def family_for(name):
    require(name in FAMILIES, 'Unknown frozen family')
    return FAMILIES[name]


def lighting_metadata(manifest, original, expected, changed):
    """Pending-assembly lighting family still requires its entire reviewed metadata contract."""
    return dict(experiment=lighting.EXPERIMENT, arm='disabled', base_version='v1.3b', base_sha256=ORIGINAL_SHA256,
        manifest_sha256=FAMILIES['lighting-cost'].manifest_sha256, switch=lighting.SWITCH, switch_enabled=False,
        modified_upstream_files=changed, member_sha256={name: reflection.sha256(expected[name]) for name in lighting.MEMBERS},
        license_sha256=LICENSE_SHA256, unchanged_upstream_member_count=len(original)-len(changed), consumed_core_fields=[],
        activation='private reduced Overworld PROGRAM_DEFERRED4 AND strict core2/enable1 AND valid radial far-edge AND approved simple material',
        program_scope='Reduced Overworld PROGRAM_DEFERRED4 only; eligible far opaque receivers',
        diagnostic_diffuse_coefficient=[0.25,0.25,0.25], diagnostic_ao_shadow=1.0,
        eligibility=manifest['eligibility'], sampling_contract=manifest['sampling_contract'], coverage_cue=manifest['coverage_cue'],
        quality_class='quality_changing_cost_control',
        source_policy='Eligible receivers bypass AO reconstruction, PCF/SSRT and diffuse helper; current material/albedo and common full-resolution composition retained; private PCSS explicit level0',
        physical_work_caveat='Receiver AO/shadows/diffuse and their highlight dependencies change; not isolated diffuse budget, strict ceiling or quality-equivalent optimization',
        distance_units='radial view-space camera-relative blocks; distance must reach far plus half transition width',
        transition='full near/band; hard diagnostic cutoff at far+width/2; no quality interpolation',
        fallback='original lighting expressions for invalid/noneligible; private off/unsupported source byte original; scoped PCSS explicit level0 in private profile',
        runtime_compilation='unverified', gpu_benefit='unmeasured',
        attribution='Photon v1.3b by Benjamin Stott (SixthSurge); original LICENSE retained')


def qualify_archives(name, original_path, private_path, runtime, root=ROOT):
    family = family_for(name); builder = family.builder; root = Path(root)
    original = archive_contents(original_path, ORIGINAL_SHA256, runtime['artifacts']['photon'])
    require(file_hash(original_path, 'sha512') == runtime['artifacts']['photon']['hashes']['sha512'], 'Original runtime-lock checksum mismatch')
    manifest_path = root / builder.MANIFEST; manifest_bytes = manifest_path.read_bytes()
    require(reflection.sha256(manifest_bytes) == family.manifest_sha256, 'Frozen feature manifest changed')
    manifest = json.loads(manifest_bytes)
    require(tuple(entry['path'] for entry in manifest['members'])==family.members, 'Explicit frozen member scope changed')
    originals, private = builder.archive_members(original_path), builder.archive_members(private_path)
    if name in ('volume', 'procedural'):
        expected = builder.patch_members(originals, manifest, 'disabled', root)
    else:
        expected = builder.patch_members(originals, manifest, 'disabled')
    helper_hashes = {}
    for member, source_path in family.helpers:
        data = (root / source_path).read_bytes()
        if name == 'reflection':
            entry = manifest['added_members'][0]
            require(entry['path'] == member and entry['source_path'] == source_path and entry['sha256'] == reflection.sha256(data), 'Reflection helper contract differs')
            expected[member] = data
        require(expected.get(member) == data and member not in originals, 'Frozen helper source mismatch/collision')
        helper_hashes[member] = reflection.sha256(data)
    require(set(expected) == set(originals) | set(helper_hashes), 'Unexpected builder helper scope')
    require(set(private) == set(expected) | {builder.METADATA}, 'Unexpected private archive member inventory')
    changed = sorted(member for member in originals if private[member] != originals[member])
    require(changed == sorted(family.members), 'Private changed upstream member scope differs')
    for entry in manifest['members']:
        require(reflection.sha256(private[entry['path']])==entry['patched_sha256'], 'Frozen patched member fingerprint differs')
    require(all(private[member] == data for member,data in expected.items()), 'Private upstream/helper bytes differ from reviewed source')
    require(private['LICENSE'] == originals['LICENSE'] and reflection.sha256(private['LICENSE']) == LICENSE_SHA256, 'Original Photon license differs')
    metadata_bytes = private[builder.METADATA]; metadata = json.loads(metadata_bytes)
    if family.metadata_sha256:
        require(reflection.sha256(metadata_bytes) == family.metadata_sha256, 'Complete frozen feature metadata changed')
    else:
        require(metadata == lighting_metadata(manifest, originals, expected, changed), 'Lighting metadata differs from reviewed contract')
    require(metadata['arm'] == 'disabled' and metadata['base_sha256'] == ORIGINAL_SHA256
            and metadata['manifest_sha256'] == family.manifest_sha256 and metadata['switch'] == builder.SWITCH
            and metadata['modified_upstream_files'] == changed, 'Feature metadata source/arm identity differs')
    with zipfile.ZipFile(private_path) as archive:
        require(archive_defaults(archive).get(builder.SWITCH) == 'false', 'Feature option must default off')
    digest = file_hash(private_path)
    if family.archive_sha256: require(digest == family.archive_sha256, 'Frozen assembled archive changed')
    original['path'] = str(original_path)
    candidate = dict(path=str(private_path), sha256=digest, metadata=metadata)
    qualification = dict(family=name, qualified=True, manifest_path=str(manifest_path), manifest_sha256=family.manifest_sha256,
        archive_sha256=digest, metadata_sha256=reflection.sha256(metadata_bytes), metadata=metadata,
        actual_modified_upstream_members=changed, helper_sha256=helper_hashes, license_sha256=LICENSE_SHA256,
        validation='Complete pinned source/helper/license/metadata equality; driver, activation, quality and benefit unverified')
    return original, candidate, qualification


def native_options(profile, original, private, family):
    selected = read_option_properties((profile / 'config/iris.properties').read_text())['shaderPack']
    sidecar = profile / 'shaderpacks' / (selected+'.txt')
    requested = read_option_properties(sidecar.read_text()) if sidecar.exists() else {}
    report = json.loads((profile / 'workload-measurement.json').read_text()); effective = report['effective_shader_options']
    require(report['status']=='complete' and report['actual_shader_sha256']==ORIGINAL_SHA256
            and canonical_options_hash(effective)==report['effective_shader_options_sha256'], 'Inspected original options/status changed')
    for boundary in ('input_state_start','input_state_end'):
        state = report[boundary]
        require([state['framebuffer_width'],state['framebuffer_height']]==[2976,1672]
                and state['requested_chunks']==state['effective_chunks']==24
                and state['shaders_in_use'] is True and state['fallback'] is False, 'Inspected native/radius/shader state changed')
    native,evidence = filter_effective_options(effective, requested, original, original)
    require(not any(key.startswith('SHADERLOD_') for key in native), 'Original private option contamination')
    with zipfile.ZipFile(original['path']) as archive: defaults = archive_defaults(archive)
    with zipfile.ZipFile(private['path']) as archive: private_defaults = archive_defaults(archive)
    require(all(private_defaults.get(key)==value for key,value in defaults.items()), 'Feature changes upstream option defaults')
    normalize_requested_options(defaults,native)
    patched = native | {family.builder.SWITCH:'true'}; normalize_requested_options(private_defaults,patched)
    return native,patched,evidence


def inspect_scene(item, probe_path, runtime, root, family_name):
    scene,profile = item['scene'],Path(item['source_profile']); family = family_for(family_name)
    require(scene in SOURCE_PINS and not any(p.is_symlink() for p in profile.rglob('*')), 'Unknown/symlink source scene')
    world = profile / 'saves' / WORLD; require((world/'level.dat').is_file(), 'Stopped source save missing')
    report_path = profile/'workload-measurement.json'; report = json.loads(report_path.read_text())
    require(file_hash(report_path)==SOURCE_PINS[scene][0] and report['effective_shader_options_sha256']==SOURCE_PINS[scene][1], 'Canonical original source report/preset pin changed')
    manifest_path,control_path = profile.parent/'workload-manifest.json',profile.parent/'control.json'
    manifest = json.loads(manifest_path.read_text()); require(manifest.get('configuration_only') is False and manifest.get('source_world_sha256'), 'Source save provenance missing')
    control,removed = copies.control_for_scene(json.loads(control_path.read_text()),scene)
    iris_path = profile/'config/iris.properties'; iris = read_option_properties(iris_path.read_text()); selected = iris.get('shaderPack','')
    require(selected and Path(selected).name==selected and iris.get('enableShaders')=='true', 'Invalid original Iris selection')
    original_path = profile/'shaderpacks'/selected
    original,private,qualification = qualify_archives(family_name,original_path,probe_path,runtime,root)
    native,patched,evidence = native_options(profile,original,private,family)
    options_path = profile/'options.txt'; game = dict(line.split(':',1) for line in options_path.read_text().splitlines() if ':' in line)
    require(game.get('renderDistance')=='24', 'Canonical radius must remain24')
    jars = list((profile/'mods').glob('*.jar')); dh = runtime['artifacts']['distanthorizons']
    require(len(jars)==(1 if scene=='forest' else 0) and all(j.name==dh['filename'] and file_hash(j,'sha512')==dh['hashes']['sha512'] for j in jars), 'Canonical source profile jars changed')
    tomllib.loads((profile/'config/shaderlod-client.toml').read_text())
    files = [report_path,manifest_path,control_path,iris_path,options_path,original_path,probe_path,Path(root)/family.builder.MANIFEST,*jars]
    files += [Path(root)/source for _,source in family.helpers]
    files += [p for p in profile.rglob('*') if p.is_file() and (p.is_relative_to(profile/'config') or p.is_relative_to(profile/'resourcepacks')) and p not in files]
    sidecar = original_path.with_name(selected+'.txt')
    if sidecar.exists(): files.append(sidecar)
    return dict(scene=scene,profile=profile,world=world,manifest=manifest,control=control,removed_control_overrides=removed,
        original=original,probe=private,qualification=qualification,native_options=native,patched_options=patched,option_evidence=evidence,
        source_files_sha256={str(p):file_hash(p) for p in files},provenance_files={
            'control-original.json':control_path,'workload-manifest-original.json':manifest_path,
            'shaderlod-client-original.toml':profile/'config/shaderlod-client.toml','iris-original.properties':iris_path,
            'options-original.txt':options_path,'workload-measurement-original.json':report_path})


def effects_config(volume_mode='inherit'):
    require(volume_mode in ('inherit','adaptive'), 'Unknown volume mode')
    rows = ['# Requested effects protocol2; loaded file is not GPU value/consumption proof.\n']
    for effect in EFFECTS:
        mode = 'ADAPTIVE' if effect=='volumetrics' and volume_mode=='adaptive' else 'INHERIT'
        near,far,width = (96,256,32) if effect=='water_surface' else (32,96,8)
        medium,low = 1.0,1.0
        if mode=='ADAPTIVE': near,far,width,medium,low = 512,1536,128,.85,.75
        rows.append(f'[effects.{effect}]\nmode="{mode}"\nnear={float(near)}\nfar={float(far)}\ntransition={float(width)}\nmediumWorkRatio={medium}\nfarWorkRatio={low}\npreserveSalientLighting=true\n')
    return '\n'.join(rows)


def plan(family_name, output, scenes=('coastalwater',), sources=None, probe=None, root=ROOT, volume_mode='inherit', debug_cue=False):
    family = family_for(family_name)
    require(not debug_cue or family_name=='lighting-cost', 'Coverage cue belongs only to lighting cost family')
    require(volume_mode=='inherit' or family_name=='volume', 'Adaptive volume override belongs only to volume family')
    effects_config(volume_mode)
    result = copies.plan(output,scenes,sources,probe or Path(root)/'build'/family.archive_name,root)
    return result | dict(family=family_name,experiment=family.experiment,manifest=str(Path(root)/family.builder.MANIFEST),
        manifest_sha256=family.manifest_sha256,archive_sha256=family.archive_sha256,metadata_sha256=family.metadata_sha256,
        smoke_only=True,gpu_timing=False,volume_mode=volume_mode,debug_cue=debug_cue,producer_effects_version=2,
        policy_note=('INHERIT volume adapter ratios .85/.75; requested file ratios unity' if volume_mode=='inherit' else
                     'Requested ADAPTIVE volume .85/.75 at512/1536/128; live GPU values unobserved') if family_name=='volume' else
                    'All seven requested effects INHERIT/unity; family consumes only its declared fields')


def prepare(family_name, output, scenes=('coastalwater',), sources=None, probe=None, root=ROOT, runtime_lock=None, volume_mode='inherit', debug_cue=False):
    operation = plan(family_name,output,scenes,sources,probe,root,volume_mode,debug_cue); family = family_for(family_name)
    inspector = lambda item,path,runtime,base: inspect_scene(item,path,runtime,base,family_name)
    state = copies.prepare(output,scenes,sources,Path(operation['probe']),runtime_lock,root,source_inspector=inspector,
        experiment=family.experiment,marker_name='parallel-lod-preparation.json',source_qualification_key='parallel_lod_source_qualification')
    marker = Path(state['operation']['output'])/'parallel-lod-preparation.json'
    state.update(status='preparing-controls',qa_operation=operation); copies.write_json(marker,state)
    try:
        for scene in scenes:
            base = Path(state['operation']['output'])/scene
            manifest_path = base/'workload-manifest.json'; manifest = json.loads(manifest_path.read_text())
            manifest.update(configuration_only=True,preparation_status='preparing-controls'); copies.write_json(manifest_path,manifest)
            control = dict(manifest['control']); control['debug']=debug_cue
            copies.write_json(base/'control.json',control)
            for arm in copies.ARMS:
                profile = base/arm
                (profile/'config/shaderlod-effects-client.toml').write_text(effects_config(volume_mode))
                config = copies.fresh_config(arm)
                if debug_cue: config = config.replace('debug=false\n','debug=true\n',1)
                (profile/'config/shaderlod-client.toml').write_text(config)
                record = manifest['experiment_arms'][arm]
                record.update(config_sha256=file_hash(profile/'config/shaderlod-client.toml'),effects_config_sha256=file_hash(profile/'config/shaderlod-effects-client.toml'),
                    requested_effects_config=tomllib.loads(effects_config(volume_mode)),debug=debug_cue,qa_only=True)
                world = profile/'saves'/WORLD
                record['input_files_sha256']={str(p.relative_to(profile)):file_hash(p) for p in profile.rglob('*') if p.is_file() and not p.is_relative_to(world)}
            manifest.update(configuration_only=False,preparation_status='complete',control=control,qa_operation=operation)
            copies.write_json(manifest_path,manifest)
        state['status']='complete'; copies.write_json(marker,state)
    except BaseException as error:
        state.update(status='failed',error=str(error)); copies.write_json(marker,state); raise
    return state


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--family',choices=tuple(FAMILIES),required=True); parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--scene',choices=('coastalwater','forest','both'),default='coastalwater')
    parser.add_argument('--probe',type=Path); parser.add_argument('--volume-mode',choices=('inherit','adaptive'),default='inherit')
    parser.add_argument('--debug-cue',action='store_true'); parser.add_argument('--dry-run',action='store_true')
    args = parser.parse_args(); scenes = copies.SCENES if args.scene=='both' else (args.scene,)
    try:
        result=(plan if args.dry_run else prepare)(args.family,args.output,scenes,probe=args.probe,volume_mode=args.volume_mode,debug_cue=args.debug_cue)
        print(json.dumps(result,indent=2,sort_keys=True))
    except (OSError,ValueError,KeyError,zipfile.BadZipFile) as error:
        parser.exit(1,'Cannot prepare frozen feature QA: '+str(error)+'\n')


if __name__=='__main__': main()

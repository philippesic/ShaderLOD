#!/usr/bin/env python3
"""Qualify immutable lighting-rate source and clone stopped original/OFF/ON profiles."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import re
import shutil
import tomllib
import zipfile

import prepare_photon_ao_reuse_profiles as copies
import build_photon_lighting_rate_probe as shader
from prepare_isolations import archive_contents, filter_effective_options
from run_proof_matrix import archive_defaults, canonical_options_hash, normalize_requested_options, read_option_properties, require_exclusive_runtime
from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint

MANIFEST_SHA256 = '683cee198706d7289ef3994212edace9fd200ffeebff458e1c317a306751b3d4'
PROBE_SHA256 = 'fca1a3115ad7cb5341807d7a06cb6939228da4603200fa831b5bdefd6eed206a'
METADATA_SHA256 = '7af75702a3d2abb33426eb66a02c18016def818d0a3d00849c84962cde945fa2'
PROBE = ROOT/'build/Photon-LightingRatePrototype-disabled.zip'
SOURCE = copies.DEFAULT_SOURCES['forest']
SOURCE_REPORT_SHA256 = '6f7662a31427f0d0863d926909c67a57b0dfba3c7ee2539c857f699c7ce3da5e'
SOURCE_OPTIONS_SHA256 = '4efc7af7742bc445adf2b3b1454aa448a557e1d4b024103dd5b9f5758ed68745'
GRAPH_OPTIONS = ('NORMAL_MAPPING','SPECULAR_MAPPING','SHADOW_COLOR','SHADOW_VPS','COLORED_LIGHTS',
    'CLOUD_SHADOWS','SH_SKYLIGHT','TAAU','BLOCKY_CLOUDS','ENVIRONMENT_REFLECTIONS')
EFFECTS_FILE = 'shaderlod-effects-client.toml'
require = copies.require


def qualify_archives(original_path, probe_path, runtime, root=ROOT):
    original=archive_contents(original_path,shader.BASE_SHA256,runtime['artifacts']['photon'])
    require(file_hash(original_path,'sha512')==runtime['artifacts']['photon']['hashes']['sha512'],'Original lock checksum differs')
    require(file_hash(probe_path)==PROBE_SHA256,'Frozen lighting-rate archive changed')
    path=Path(root)/shader.MANIFEST;raw=path.read_bytes()
    require(shader.sha256(raw)==MANIFEST_SHA256,'Frozen lighting-rate manifest changed')
    manifest=json.loads(raw);upstream=shader.archive_members(original_path);private=shader.archive_members(probe_path)
    expected=shader.patch_members(upstream,manifest,'disabled');overlay=shader.overlays(Path(root),manifest,upstream)
    require(set(private)==set(upstream)|set(overlay)|{shader.METADATA},'Private member inventory differs')
    require(all(private[name]==value for name,value in expected.items()),'Private upstream member differs from frozen patch')
    require(all(private[name]==value for name,value in overlay.items()),'Private overlay/helper differs from frozen source')
    require(shader.sha256(private[shader.METADATA])==METADATA_SHA256,'Complete private metadata differs')
    metadata=json.loads(private[shader.METADATA]);changed=sorted(n for n in upstream if private[n]!=upstream[n])
    require(changed==sorted(shader.MEMBERS),'Private source scope differs')
    require(metadata['experiment']==shader.EXPERIMENT and metadata['arm']=='disabled' and metadata['switch_enabled'] is False
        and metadata['manifest_sha256']==MANIFEST_SHA256 and metadata['base_sha256']==shader.BASE_SHA256
        and metadata['overlay_sha256']==manifest['overlays'] and metadata['modified_upstream_files']==changed
        and metadata['license_sha256']==shader.sha256(upstream['LICENSE']),'Private source/metadata contract differs')
    require(metadata['core_fields']==list(shader.CORE_FIELDS) and metadata['effect_fields']==list(shader.EFFECT_FIELDS)
        and metadata['shader_target']==2 and metadata['viewport_scale']==.5 and metadata['effects_version']==2
        and metadata['quality']=='unaccepted' and metadata['runtime_compilation']=='unverified','Private ABI/quality contract differs')
    with zipfile.ZipFile(probe_path) as archive: require(archive_defaults(archive).get(shader.SWITCH)=='false','Private option must default off')
    original['path']=str(original_path)
    probe=dict(path=str(probe_path),sha256=PROBE_SHA256,metadata=metadata)
    qualification=dict(qualified=True,manifest_path=str(path),manifest_sha256=MANIFEST_SHA256,
        archive_sha256=PROBE_SHA256,metadata_sha256=METADATA_SHA256,modified_upstream_files=changed,
        overlay_sha256=manifest['overlays'],license_sha256=metadata['license_sha256'],metadata=metadata,
        validation='All payloads, complete metadata, helper and license verified; no driver/quality/GPU acceptance')
    return original,probe,qualification


def native_options(profile,original,probe):
    iris=read_option_properties((profile/'config/iris.properties').read_text())
    sidecar=profile/'shaderpacks'/(iris['shaderPack']+'.txt')
    requested=read_option_properties(sidecar.read_text()) if sidecar.exists() else {}
    report=json.loads((profile/'workload-measurement.json').read_text());effective=report.get('effective_shader_options')
    require(report.get('status')=='complete' and report.get('actual_shader_sha256')==shader.BASE_SHA256,'Original report pin/status differs')
    require(isinstance(effective,dict) and effective and report.get('effective_shader_options_sha256')==canonical_options_hash(effective),'Effective option hash differs')
    for boundary in ('input_state_start','input_state_end'):
        state=report.get(boundary,{})
        require([state.get('framebuffer_width'),state.get('framebuffer_height')]==[2976,1672]
            and state.get('requested_chunks')==state.get('effective_chunks')==24
            and state.get('shaders_in_use') is True and state.get('fallback') is False,'Native/radius/shader source evidence differs')
    with zipfile.ZipFile(original['path']) as archive:defaults=archive_defaults(archive)
    with zipfile.ZipFile(probe['path']) as archive:private_defaults=archive_defaults(archive)
    require(all(private_defaults.get(k)==v for k,v in defaults.items()),'Private changes upstream defaults')
    native,evidence=filter_effective_options(effective,requested,original,original)
    require(not any(k.startswith('SHADERLOD_') for k in native),'Original private option contamination')
    resolved=defaults|native
    # Report values resolve source settings; absent public fields use actual pinned archive defaults.
    for name in GRAPH_OPTIONS:
        require(name in defaults and resolved.get(name)=='false','Unsupported graph option: '+name)
        native.setdefault(name,resolved[name])
    patched=native|{shader.SWITCH:'true'}
    require(normalize_requested_options(private_defaults,patched).get(shader.SWITCH)=='true','Private gate request differs')
    require(all(normalize_requested_options(defaults,native).get(k)==normalize_requested_options(private_defaults,patched).get(k)
        for k in defaults),'Held upstream options differ')
    evidence=dict(evidence=evidence,held_effective_options_sha256=canonical_options_hash(effective),
        graph_options={k:resolved[k] for k in GRAPH_OPTIONS},
        absent_options_resolved_from_pinned_defaults=[k for k in GRAPH_OPTIONS if k not in effective],
        expected_environment=dict(DISTANT_HORIZONS=False,VOXY=False),
        environment_note='Not shader options/default guesses: fresh profile removes sole pinned DH jar; launch must use minimal noDH classpath, actual runtime still unverified')
    return native,patched,evidence


def producer_semantics(root=ROOT):
    paths={name:Path(root)/'src/main/java/com/pp/shaderlod'/name for name in
        ('lod/Effect.java','ShaderLodEffectConfig.java','mixin/EffectUniformsMixin.java','ShaderLod.java','lod/EffectPolicy.java')}
    source={name:path.read_text() for name,path in paths.items()}
    require('OPAQUE_LIGHTING("OpaqueLighting", false)' in source['lod/Effect.java'],'Opaque salient category changed')
    require('preserveLighting && effect.salientLighting()' in source['ShaderLodEffectConfig.java']
        and '? new EffectPolicy(requested.enabled(), true, bands, 1, 1) : requested' in source['ShaderLodEffectConfig.java'],
        'Protection transport normalization changed')
    require('PROTOCOL_VERSION = 2' in source['ShaderLodEffectConfig.java'] and 'ADAPTIVE(2)' in source['ShaderLodEffectConfig.java'],'Effects protocol/mode changed')
    require('state.control(effect).policy()' in source['mixin/EffectUniformsMixin.java'] and EFFECTS_FILE in source['ShaderLod.java'],'Producer policy/file wiring changed')
    require('new DistanceBands(96, 256, 32) : new DistanceBands(32, 96, 8)' in source['lod/EffectPolicy.java']
        and 'new EffectPolicy(false, true, bands, 1, 1)' in source['lod/EffectPolicy.java'],'Producer defaults changed')
    return dict(source_files_sha256={str(p):file_hash(p) for p in paths.values()},effects_version=2,
        requested=dict(mode='ADAPTIVE',near=32.,far=96.,transition=8.,mediumWorkRatio=1.,farWorkRatio=.25,preserveSalientLighting=True),
        expected_transport=dict(mode=2,near=32.,far=96.,transition=8.,mediumWorkRatio=1.,farWorkRatio=.25),
        protection_reason='OpaqueLighting is non-salient; preserveSalientLighting=true stays enabled and does not force unity. Shadow/AO defaults remain protected.',
        observed_transport='unverified; source normalization and registration evidence only')


def fresh_effects_config():
    rows=['# Explicit requested OpaqueLighting experiment; retain protection and other effect defaults.\n']
    for effect in ('shadow_filtering','ambient_occlusion','reflections','volumetrics','water_surface','materials','opaque_lighting'):
        near,far,width=(96.,256.,32.) if effect=='water_surface' else (32.,96.,8.)
        rows.append(f'[effects.{effect}]\nmode="'+('ADAPTIVE' if effect=='opaque_lighting' else 'INHERIT')+'"\n'
            f'near={near}\nfar={far}\ntransition={width}\nmediumWorkRatio=1.0\nfarWorkRatio='+('0.25' if effect=='opaque_lighting' else '1.0')+'\npreserveSalientLighting=true\n')
    return ''.join(rows)


def inspect_scene(item,probe_path,runtime,root=ROOT):
    require(item['scene']=='forest','Lighting rate pilot is forest only')
    profile=Path(item['source_profile']);world=profile/'saves'/WORLD
    require(not any(p.is_symlink() for p in profile.rglob('*')),'Symlink source profile unsupported')
    require((world/'level.dat').is_file(),'Stopped canonical save missing')
    report_path=profile/'workload-measurement.json'
    require(file_hash(report_path)==SOURCE_REPORT_SHA256,'Stopped forest report pin changed')
    report=json.loads(report_path.read_text());require(report['effective_shader_options_sha256']==SOURCE_OPTIONS_SHA256,'Forest source options changed')
    manifest_path=profile.parent/'workload-manifest.json';control_path=profile.parent/'control.json'
    manifest=json.loads(manifest_path.read_text());require(manifest.get('configuration_only') is False and manifest.get('source_world_sha256'),'Source provenance missing')
    control,removed=copies.control_for_scene(json.loads(control_path.read_text()),'forest')
    iris_path=profile/'config/iris.properties';iris=read_option_properties(iris_path.read_text());selected=iris.get('shaderPack','')
    require(selected and Path(selected).name==selected and iris.get('enableShaders')=='true','Invalid source Iris selection')
    original,probe,qualification=qualify_archives(profile/'shaderpacks'/selected,probe_path,runtime,root)
    native,patched,evidence=native_options(profile,original,probe)
    options_path=profile/'options.txt';minecraft=dict(l.split(':',1) for l in options_path.read_text().splitlines() if ':' in l)
    require(minecraft.get('renderDistance')=='24','Source radius differs')
    jars=list((profile/'mods').glob('*.jar'));dh=runtime['artifacts']['distanthorizons']
    require(len(jars)==1 and jars[0].name==dh['filename'] and file_hash(jars[0],'sha512')==dh['hashes']['sha512'],'Canonical forest must contain only pinned DH')
    tomllib.loads((profile/'config/shaderlod-client.toml').read_text())
    if (profile/'config'/EFFECTS_FILE).exists():tomllib.loads((profile/'config'/EFFECTS_FILE).read_text())
    semantics=producer_semantics(root);qualification['producer_semantics']=semantics
    files=[manifest_path,control_path,iris_path,options_path,report_path,Path(root)/shader.MANIFEST,Path(probe['path']),Path(original['path']),*jars]
    files += [p for p in profile.rglob('*') if p.is_file() and (p.is_relative_to(profile/'config') or p.is_relative_to(profile/'resourcepacks')) and p not in files]
    sidecar=profile/'shaderpacks'/(selected+'.txt')
    if sidecar.exists():files.append(sidecar)
    return dict(scene='forest',profile=profile,world=world,manifest=manifest,control=control,removed_control_overrides=removed,
        original=original,probe=probe,qualification=qualification,native_options=native,patched_options=patched,option_evidence=evidence,
        source_files_sha256={str(p):file_hash(p) for p in files},
        provenance_files={'control-original.json':control_path,'workload-manifest-original.json':manifest_path,
            'shaderlod-client-original.toml':profile/'config/shaderlod-client.toml','iris-original.properties':iris_path,
            'options-original.txt':options_path,'workload-measurement-original.json':report_path})


def plan(output,source=SOURCE,probe=PROBE,root=ROOT,cue=False,option_off=False):
    result=copies.plan(output,('forest',),{'forest':source},probe,root)
    return result|dict(manifest=str(Path(root)/shader.MANIFEST),experiment=shader.EXPERIMENT,private_sha256=PROBE_SHA256,
        optional_cue=cue,optional_option_off=option_off,minimum_runtime_stack='minimal/noDH',opaque_request=dict(mode='ADAPTIVE',bands=[32,96,8],ratios=[1,.25],preserveSalientLighting=True))


def update_arm(profile,arm,root_manifest,debug=False):
    config=copies.fresh_config(arm)
    if debug:config=config.replace('debug=false','debug=true')
    (profile/'config/shaderlod-client.toml').write_text(config)
    (profile/'config'/EFFECTS_FILE).write_text(fresh_effects_config())
    core=tomllib.loads(config);effects=tomllib.loads((profile/'config'/EFFECTS_FILE).read_text())
    require(core['lod']['debug']==debug and core['lod']['enabled']==(arm=='adaptive'),'Requested core configuration differs')
    requested=effects['effects']['opaque_lighting']
    require(requested==dict(mode='ADAPTIVE',near=32.,far=96.,transition=8.,mediumWorkRatio=1.,farWorkRatio=.25,preserveSalientLighting=True),'Opaque request differs')
    record=root_manifest['experiment_arms'][arm]
    world=profile/'saves'/WORLD
    record['input_files_sha256']={str(p.relative_to(profile)):file_hash(p) for p in profile.rglob('*') if p.is_file() and not p.is_relative_to(world)}
    record.update(config_sha256=file_hash(profile/'config/shaderlod-client.toml'),effects_config_sha256=file_hash(profile/'config'/EFFECTS_FILE),
        requested_opaque=requested,debug=debug,expected_effects_version=2,expected_opaque_transport_mode=2,
        expected_transport='source model only; actual loaded fields and cache consumption pending')


def prepare(output,source=SOURCE,probe=PROBE,runtime_lock=None,root=ROOT,cue=False,source_inspector=None,option_off=False):
    operation=plan(output,source,probe,root,cue,option_off);semantics=producer_semantics(root)
    result=copies.prepare(output,('forest',),{'forest':source},probe,runtime_lock,root,
        source_inspector=source_inspector or inspect_scene,experiment=shader.EXPERIMENT,
        marker_name='photon-lighting-rate-copy-stage.json',source_qualification_key='lighting_rate_source_qualification')
    out=Path(operation['output']);marker=out/'photon-lighting-rate-preparation.json'
    state=dict(schema=1,status='configuring',operation=operation,copy_stage=result,producer_semantics=semantics,
        validation='Stopped-save/source preparation only; no compilation/rendering/quality/GPU acceptance')
    copies.write_json(marker,state)
    try:
        base=out/'forest';path=base/'workload-manifest.json';manifest=json.loads(path.read_text())
        for arm in copies.ARMS:update_arm(base/arm,arm,manifest)
        manifest.update(minimal_stack_required=True,producer_semantics=semantics,
            private_gate_override=True,normal_debug=False,quality_acceptance='pending; full AO union repaired, cast-shadow continuity unaccepted')
        copies.write_json(path,manifest)
        for kind,selected,debug,gate in (('coverage',cue,True,True),('option-off',option_off,False,False)):
            if not selected:continue
            require_exclusive_runtime()
            extra_base=out/kind/'forest';extra_base.mkdir(parents=True);extra_profile=extra_base/'adaptive'
            with unlocked_world(base/'adaptive'/'saves'/WORLD):
                shutil.copytree(base/'adaptive',extra_profile,ignore=shutil.ignore_patterns('session.lock'))
                extra_manifest=json.loads(json.dumps(manifest))
                record=extra_manifest['experiment_arms']['adaptive']
                record['requested_options'][shader.SWITCH]=str(gate).lower()
                iris=read_option_properties((extra_profile/'config/iris.properties').read_text())
                sidecar=extra_profile/'shaderpacks'/(iris['shaderPack']+'.txt')
                options=read_option_properties(sidecar.read_text());options[shader.SWITCH]=str(gate).lower()
                sidecar.write_text(''.join(f'{name}={value}\n' for name,value in sorted(options.items())))
                update_arm(extra_profile,'adaptive',extra_manifest,debug=debug)
                require(world_fingerprint(extra_profile/'saves'/WORLD)==manifest['source_world_sha256'],'Extra control copied save differs')
            extra_control=manifest['control']|{'debug':debug}
            extra_manifest.update(control=extra_control,proof_profiles=['adaptive'],experiment_arms={'adaptive':record},
                purpose='untimed qualitative coverage only' if debug else 'private option OFF original-graph control',
                timing_allowed=not debug,coverage_debug=debug,private_gate_override=gate)
            extra_manifest.pop('normal_debug',None)
            extra_manifest['copied_world_sha256']={'adaptive':world_fingerprint(extra_profile/'saves'/WORLD)}
            extra_manifest['quality_options']={'adaptive':record['requested_options']}
            copies.write_json(extra_base/'control.json',extra_control);copies.write_json(extra_base/'workload-manifest.json',extra_manifest)
            state['untimed_cue_profile' if debug else 'option_off_profile']=str(extra_profile)
        state['status']='complete';copies.write_json(marker,state)
    except BaseException as error:
        state.update(status='failed',error=str(error));copies.write_json(marker,state);raise
    return state


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True);parser.add_argument('--source',type=Path,default=SOURCE)
    parser.add_argument('--probe',type=Path,default=PROBE);parser.add_argument('--cue',action='store_true');parser.add_argument('--option-off',action='store_true');parser.add_argument('--dry-run',action='store_true')
    args=parser.parse_args()
    try:print(json.dumps((plan if args.dry_run else prepare)(args.output,args.source,args.probe,cue=args.cue,option_off=args.option_off),indent=2,sort_keys=True))
    except (OSError,ValueError,KeyError,zipfile.BadZipFile) as error:parser.exit(1,'Cannot prepare lighting-rate profiles: '+str(error)+'\n')
if __name__=='__main__':main()

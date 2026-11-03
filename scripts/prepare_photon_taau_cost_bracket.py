#!/usr/bin/env python3
"""Prepare a rejection-only ORIGINAL Photon native / TAAU .90 / native bracket.

No launcher, build, runtime capture, quality acceptance or performance claim.
Use the existing WorkloadSession and proof-matrix validators after root launch.
"""
from __future__ import annotations
from run_storage import copytree as storage_copytree

import argparse
from datetime import datetime, timezone
import hashlib
import io
import json
import math
from pathlib import Path
import shutil
import sys
import tomllib
import zipfile

import prepare_photon_taau_screen as quality
import qualify_volume_fast_artifacts as artifacts
from analyze_stack_matrix import load_equivalences
from prepare_reduced_effects_profiles import require, write_json
from run_proof_matrix import canonical_options_hash, development_fingerprints, profile_inputs, require_exclusive_runtime
from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint

OUTPUT = ROOT / 'run-workloads/taau-cost-r1'
RECEIPT = ROOT / 'build/taau-artifact-evidence-r1/artifact-receipt.json'
RECEIPT_SHA256 = 'f273c4976650f33851e71a674ba7513ad47b4d4dc4a89598246ad5f394186973'
ORDER = ('native-before', 'taau90', 'native-after')
UNITY_ORDER = ('native-before', 'taau100', 'native-after')
UNITY_RECEIPT = ROOT / 'build/mac-window-state-integrated-artifacts-r14/artifact-receipt.json'
UNITY_RECEIPT_SHA256 = '5e6b8a3305342ae18655de054b2175f2c0f64a1219269fe404bd2aafd7c96b16'
DIRECTORIES = ('build/classes/java/devClient', 'build/resources/devClient',
               'build/classes/java/main', 'build/resources/main')
EVIDENCE = dict(performanceEvidence=False, evidence_class='DIAGNOSTIC', quality='unverified',
                adapter_gain=False, shader_support_claim=False, upstream_option_calibration=True)


def command(profile, unity_graph=False):
    argv = ['./gradlew', 'runClientLodOff', '-PrunDirectory=' + str(Path(profile).resolve()),
            '-PperformanceStack=minimal', '-PincludeDh=false', '-PbenchmarkWindowScale=2',
            '-PcomparisonFullscreen=false', '-PfixedWetness=0', '-PgpuTiming=true',
            '-PgpuSampleInterval=5', '-PinitialWarmup=30', '-PmeasurementSeconds=20',
            '-PcpuTiming=true', '-PmeasureWorkload=true', '-PmeasureOnly=true',
            '-PcameraQualityOnly=false', '-PcameraQuality=']
    argv += ['-P' + name + '=false' for name in quality.OFF]
    for task in quality.BUILD_TASKS:
        argv += ['-x', task]
    if unity_graph:
        import prepare_photon_physical_resolution as physical
        argv[1] = 'runClientLodOn'
        argv += ['-PphysicalResolution=false']
        argv = [v.replace('-PfixedWetness=0', '-PfixedWetness=1').replace('-PgpuTiming=true', '-PgpuTiming=false').replace('-PinitialWarmup=30', '-PinitialWarmup=60') for v in argv]
        argv += ['-P' + name + '=false' for name in physical.OFF if name not in quality.OFF and name != 'measureWorkload']
    return argv


def plan(output=OUTPUT, source=quality.SOURCE, root=ROOT, unity_graph=False):
    result = quality.plan(output, source, root)
    result.update(EVIDENCE, arms=list(ORDER), kinds=['cost-bracket'], capture_seconds=20,
        initial_warmup_seconds=30, gpu_sample_interval=5, minimum_positive_unique_gpu_samples=60,
        maximum_original_drift_fraction=.10, minimum_frame_reduction_fraction=.05,
        metric='elapsed_seconds * 1000 / render_frames; 5% reduction equals 5.263% higher FPS',
        launch_authorized=False, launch_status='HOLD: root resource/quiet-window and capture settings evidence',
        validation='Pure plan only; exact source/save/artifact qualification pending')
    if unity_graph:
        result.update(arms=list(UNITY_ORDER), main_mod_present=True, global_enabled=False, initial_warmup_seconds=60, gpu_queries=False, minimum_positive_unique_gpu_samples=0, fixed_wetness=1, method='Whole Photon native TAA versus stock unity-scale TAAU graph differential; composite3/composite4/jitter/cloudhistory all change, not isolated reconstruction pass time', maximum_original_drift_fraction=.03, minimum_frame_reduction_fraction=None, metric='elapsed_seconds * 1000 / render_frames; query-free total timing primary', decision='Reject flat/adverse unity graph; positive benefit remains diagnostic only. Parent evaluates control variation and graph overhead against R8 conditional physical headroom; no product gain.')
    result.pop('deferred_arm', None)
    return result


def audit_equivalence(path, receipt):
    """Reuse only the pre-existing exact audited API Base pair; verify real payloads."""
    result = load_equivalences(path)
    entry = result['entry']; payloads = []
    for variant in entry['variants']:
        parent = ROOT / 'dependencies/runtime' / variant['parent_filename']
        require(file_hash(parent) == variant['parent_sha256'], 'Equivalence parent pin differs')
        with zipfile.ZipFile(parent) as archive:
            data = archive.read(variant['embedded_path'])
        require(hashlib.sha256(data).hexdigest() == variant['sha256'], 'Equivalence member pin differs')
        with zipfile.ZipFile(io.BytesIO(data)) as nested:
            names = [n for n in nested.namelist() if not n.endswith('/')]
            require(len(names) == len(set(names)), 'Duplicate equivalence payload names')
            payloads.append({n: nested.read(n) for n in names})
    require(payloads[0].keys() == payloads[1].keys(), 'Equivalence payload inventory differs')
    changed = [n for n in payloads[0] if payloads[0][n] != payloads[1][n]]
    require(changed == ['META-INF/neoforge.mods.toml'], 'Equivalence nonmetadata payload differs')
    for kind, select in (
        ('classes', lambda n: n.endswith('.class')),
        ('resources_except_changed_metadata', lambda n: not n.endswith('.class') and n not in changed),
        ('all_files_except_changed_metadata', lambda n: n not in changed)):
        for payload in payloads:
            selected = sorted(n for n in payload if select(n))
            encoded = ''.join(n + '\0' + hashlib.sha256(payload[n]).hexdigest() + '\n' for n in selected)
            actual = dict(file_count=len(selected), sha256=hashlib.sha256(encoded.encode()).hexdigest())
            require(actual == entry['proof'][kind], 'Equivalence extracted payload fingerprint differs')
    expected = receipt['expected_loaded_mods']
    require(all(expected.get(k) == v for k,v in entry['required_loaded_mods'].items()), 'Equivalence runtime differs')
    return result | {'actual_extracted_payloads_verified': True,
        'policy': 'Exact independently audited metadata-only pair qualified; preserve actual parent/member/hash in every arm. Unknown variants or other root differences are inconclusive.'}


def writable_inventory(profile):
    """Retain every config/option, complete sidecar semantics and explicit cache roles."""
    profile = Path(profile)
    result = quality.writable_settings(profile)
    if 'config/sodium-fingerprint.json' in result['nonshader_identities']:
        result['nonshader_identities']['config/sodium-fingerprint.json']['role'] = (
            'Sodium schema1 salted UUID/absolute-game-directory cache with epoch timestamp; not a shader setting or hardware fingerprint')
    inputs = profile_inputs(profile)
    result['sidecar'] = dict(requested=inputs['requested_shader_options'],
        defaults=inputs['shader_option_defaults'], semantic_sha256=inputs['semantic_shader_options_sha256'],
        raw_sha256=inputs['shader_options_sha256'])
    caches = {}
    for name in ('usercache.json', 'usernamecache.json'):
        path = profile / name
        if path.exists():
            caches[name] = dict(raw=json.loads(path.read_text()), sha256=file_hash(path))
    result['login_caches'] = caches
    result['policy'] = ('Every writable file is retained. Compare full TOML/JSON/property semantics and unknown bytes; '
        'sidecar full declared-default normalization only; no unknown configuration values ignored. '
        'Sodium schema1 salted UUID/path cache and login-cache expiry are explicit roles with raw records retained. '
        'Source-justify any startup rewrite, preserve actual captured start/end fingerprints, '
        'and reject unqualified semantic changes. Postshutdown files alone are not capture-time proof.')
    return result


def qualify_configuration_snapshot(snapshot, prepared):
    """Decode the actual bounded boundary snapshot; never substitute postrun files."""
    require(snapshot.get('complete') is True and snapshot.get('errors')==[] and isinstance(snapshot.get('files'),dict),
            'Incomplete capture-boundary configuration files')
    files=snapshot['files'];settings={};identities={};sidecars={}
    expected=set(prepared['settings'])|set(prepared['nonshader_identities'])
    for name,item in files.items():
        require(isinstance(name,str) and not Path(name).is_absolute() and '..' not in Path(name).parts,
                'Unsafe capture configuration path')
        require(isinstance(item,dict) and isinstance(item.get('bytes'),int) and not isinstance(item['bytes'],bool)
                and item['bytes']>=0 and isinstance(item.get('sha256'),str),'Malformed configuration record')
        text=item.get('utf8')
        if text is not None:
            require(isinstance(text,str) and len(text.encode())==item['bytes']
                    and hashlib.sha256(text.encode()).hexdigest()==item['sha256'],'Configuration text/hash mismatch')
        if name.startswith('shaderpacks/') and name.endswith('.zip.txt'):
            require(text is not None,'Sidecar UTF8 missing')
            sidecars[name]=quality.semantic_properties(text)
            continue
        require(name in expected,'Unqualified startup configuration inventory: '+name)
        if name=='config/sodium-fingerprint.json':
            require(text is not None,'Sodium fingerprint UTF8 missing')
            value=json.loads(text)
            require(set(value)=={'v','s','u','p','t'} and isinstance(value['v'],int)
                    and not isinstance(value['v'],bool) and value['v']==1 and isinstance(value['t'],int)
                    and not isinstance(value['t'],bool) and all(isinstance(value[k],str) and len(value[k])==128
                    and all(c in '0123456789abcdef' for c in value[k]) for k in ('s','u','p')),
                    'Unknown Sodium fingerprint identity schema')
            identities[name]=dict(raw=value,sha256=item['sha256'])
        else:
            model=prepared['settings'][name];format=model['format']
            if format=='toml':
                require(text is not None,'TOML UTF8 missing');value=tomllib.loads(text)
            elif format=='json':
                require(text is not None,'JSON UTF8 missing');value=json.loads(text)
            elif format in ('simple literal properties','minecraft colon properties'):
                require(text is not None,'Properties UTF8 missing')
                value=quality.semantic_properties(text,':' if format=='minecraft colon properties' else '=')
            else:
                require(item['sha256']==model['sha256'],'Unknown configuration bytes changed')
                settings[name]=dict(format=format,sha256=item['sha256']);continue
            settings[name]=dict(format=format,value=value)
    require(set(files)-set(sidecars)==expected,'Capture configuration inventory missing')
    require(settings==prepared['settings'],'Unqualified startup configuration semantic change')
    # Only this original ZIP exists in a prepared profile. Missing all-default
    # sidecars are legal; arbitrary extra sidecars are never normalized away.
    require(len(sidecars)<=1 and all(Path(n).name=='photon_v1.3b.zip.txt' for n in sidecars),
            'Unqualified sidecar inventory')
    values=next(iter(sidecars.values()),{})
    normalized=quality.normalize_requested_options(prepared['sidecar']['defaults'],values)
    require(canonical_options_hash(normalized)==prepared['sidecar']['semantic_sha256'],
            'Capture full sidecar semantics changed')
    return dict(settings=settings,nonshader_identities=identities,sidecar_semantic_sha256=canonical_options_hash(normalized),
        actual_configuration_files=snapshot,
        qualification='Complete captured settings; exact original defaults only; explicit Sodium salted UUID/path cache role, all raw records retained')


def bootstrap_source_evidence():
    """Pin each operation authorizing formatting/default/cache qualification."""
    jars = {
        'sodium': ROOT/'dependencies/runtime/sodium-neoforge-0.6.13+mc1.21.1.jar',
        'iris': ROOT/'dependencies/runtime/iris-neoforge-1.8.12+mc1.21.1.jar',
        'minecraft_sources': ROOT/'build/moddev/artifacts/neoforge-21.1.253-sources.jar'}
    expected = {
        'sodium': {'net/caffeinemc/mods/sodium/client/data/fingerprint/FingerprintMeasure.class':
            '5de654d60a4f7cb1a2641e41cf564b7109f477c51ba44dc2e56f407f5103d7ea',
            'net/caffeinemc/mods/sodium/client/data/fingerprint/HashedFingerprint.class':
            '5b3bdcc1271b5b14c128f981b2d500bce6e218cffcee4fe286e73dae3dd9f357'},
        'iris': {'net/irisshaders/iris/config/IrisConfig.class':
            '6307ecb634fff9b1ebbfc00d32f75d75f80916fa7bb81f8bba361ad12f956f45',
            'net/irisshaders/iris/Iris.class':
            '4cb88bd1c15635db3845cb4b3db757c611707e1fc2a00af879298c09bb45d3d1'},
        'minecraft_sources': {'net/minecraft/server/players/GameProfileCache.java':
            '8d0dc51f9e509030a66dc806cfcaec4a6679305e97f01482ed574e9976f31ff1',
            'net/minecraft/server/players/PlayerList.java':
            '4bb1f33bbf4c056e4435d789d4b9eff9bc9010603ae325cc9f650833102fdf14'}}
    records={}
    for kind,path in jars.items():
        with zipfile.ZipFile(path) as archive:
            actual={name:hashlib.sha256(archive.read(name)).hexdigest() for name in expected[kind]}
        require(actual==expected[kind],'Bootstrap source/class pin differs: '+kind)
        records[kind]=dict(path=str(path),sha256=file_hash(path),members_sha256=actual)
    require(records['minecraft_sources']['sha256']=='abf3bed3623112c03cd16602f8cbe3ba09c7566e4aa610fd310530f7b4013d83',
            'Exact NeoForge/Minecraft bootstrap source archive differs')
    paths=[ROOT/'build/taau-cost-source/sodium-fingerprint.javap.txt',
           ROOT/'build/taau-cost-source/sodium-fingerprint-fields.javap.txt',
           ROOT/'dev/logs/volume-fast-trial-r1/postrun-profile-audit.json']
    return dict(pinned_source_records=records,audited_excerpts_files_sha256={str(p):file_hash(p) for p in paths},
        qualification='Exact pinned Iris Properties.store reorders/adds comments and removes only default-valued sidecar controls; full maps still checked. '
            'Sodium FingerprintMeasure hashes random64byte salt plus UUID/absolute game directory; v/s/u/p/t annotations verified. '
            'Minecraft GameProfileCache.add sets one-month expiry and saves same name/UUID cache. '
            'Formatting-only TOML/JSON transformations retain complete values; unknown format exact hashes. '
            'No unknown values, other keys, shader lighting options or arbitrary config additions/deletions are ignored.')


def freeze_sources(root=ROOT):
    root = Path(root)
    names = ('build.gradle', 'settings.gradle', 'gradlew', 'gradle/wrapper/gradle-wrapper.jar',
             'gradle/wrapper/gradle-wrapper.properties', 'dependencies/runtime.lock.json', 'dependencies/performance-mods.lock.json',
             'dependencies/runtime-artifact-equivalences.json', 'scripts/prepare_photon_taau_cost_bracket.py',
             'scripts/prepare_photon_taau_screen.py', 'scripts/run_proof_matrix.py', 'scripts/setup_comparison.py',
             'scripts/qualify_volume_fast_artifacts.py', 'scripts/analyze_stack_matrix.py',
             'scripts/prepare_isolations.py', 'scripts/prepare_reduced_effects_profiles.py',
             'scripts/prepare_photon_ao_reuse_profiles.py', 'tests/test_photon_taau_cost_bracket.py')
    paths = [root / n for n in names]
    # Freeze actual imported repository helpers as well as the explicit entry
    # points; workers' independent scripts remain outside this read-only graph.
    for module in tuple(sys.modules.values()):
        name=getattr(module,'__file__',None)
        if name:
            path=Path(name).resolve()
            if path.suffix=='.py' and path.is_relative_to(root/'scripts'):
                paths.append(path)
    for tree in ('src/main', 'src/devClient'):
        paths += [p for p in (root / tree).rglob('*') if p.is_file()]
    require(not any(p.is_symlink() for p in paths), 'Frozen source has symlinks')
    return {str(p): file_hash(p) for p in sorted(set(paths))}


def postanalysis_plan():
    return dict(framework='Existing WorkloadSession and run_proof_matrix; no new launcher or benchmark framework',
        per_arm=[
            'Preserve initial launcher/session/Java PID, birth command and cwd/profile association separately from terminal audit; report must be newer than proven launch',
            'run_proof_matrix.profile_inputs, validate_report(report, request, prepared_inputs, started_ns, report_path), validate_temporal_inputs; full predicted Iris option map equality at both boundaries',
            'loaded_artifact_evidence valid=true, errors=[], complete_mod_coverage=true, complete_identities=true; report and captured exact registry/inventory agree with independent 9-mod/8-root receipt; unique complete root/mod coverage checked by existing qualifier',
            'qualify_volume_fast_artifacts.validate_inventory_against_receipt; explicit include_shaderlod=False for independent expected receipt validation, default remains True',
            'Actual captured input_state_start equals input_state_end; qualify each raw captured config hash against retained full settings, not postrun all-file byte identity',
            'qualify_configuration_snapshot(input_state_start/end.configuration_files, prepared_writable_inventory); full capture-boundary writable semantics equal prepared recipe and across arms; only source-qualified bootstrap rewrites allowed. Missing complete boundary evidence makes bracket inconclusive',
            'Window capture observations all available, stable focus true OR false established by native-before and matched unchanged in every arm; visible=true, iconified=false, zero transitions/drops; logical1488x836 and output2976x1672 every observation; pacing fullscreen/vsync/pause=false, limit260',
            'Actual GPU N5 frame-only, coarse CPU on; all detailed/pass/probe/camera features off, warmup30s/capture20s; >=60 positive finite unique full-render game-render GPU frame IDs, one epoch per capture, no duplicate or wrong-scope rows',
            'Source/build/runtime/archive/control/request/preparation bytes and four class/resource roots unchanged; no builds/tests/encoders or unrelated CPU during root announced quiet window; retain terminal log/screenshot and prove all owned handles absent'],
        cross_arm=['Exact scene/camera/day12500/weatherclear/wetness0/24chunks/noDH/minimal/original archive/driver/hardware/config/pacing match except TAAU false→true and inactive0.75→0.90',
            'Freeze and use prior audited exact API Base metadata-only equivalence; retain raw selected variant. Any other inventory difference is inconclusive',
            'Native-before/native-after absolute drift >10% of before in total-frame mean duration OR GPU median makes bracket inconclusive',
            'With guards passed and drift<=10%, compare taau90 to EACH native arm. <=5% mean total-frame reduction versus either, or GPU median not lower versus either: stop escalation, no useful screen signal',
            '>5% frame reduction versus BOTH originals plus agreeing GPU direction is only a promising diagnostic, requiring reverse-order repeated quiet runs, query-free corroboration and representative still/motion quality before any promotion'],
        limitations=['Single serial bracket is rejection evidence only; no causal/repeated gain or confidence interval',
            'Upstream TAAU is lower-quality calibration, not ShaderLOD optimization/adapter/support',
            'Native output does not prove c3/c4 active allocations, shrunk raster, sampler bindings, native GUI or phase-aligned temporal quality'],
        **EVIDENCE)


def unity_postanalysis_plan():
    return EVIDENCE | dict(framework='Existing WorkloadSession/run_proof_matrix and complete-settings qualifier',
        per_arm=['validate_report and validate_temporal_inputs; exact prepared complete Iris map at start/end; no fallback',
                 'Current R14 main-loaded 10-mod/9-root receipt, include_shaderlod=True; exact full inventory and all pins',
                 'qualify_configuration_snapshot at start/end against prepared writable semantics; actual focus true, stable native extents/pacing',
                 '60s warmup,20s query-free capture; empty GPU rows/zero scheduled and pending queries; finite contiguous coarse CPU run-tick coverage, no nested-clock sums',
                 'Builds/tests/encoders/worker CPU excluded during quiet lane; preserve actual process observations'],
        cross_arm=['Same stopped coastal save/pose/day12500/clear/wetness1/native2976x1672/minimal/noDH/main globalOFF/seven INHERIT controls',
                   'Only TAAU false→true and inactive0.75→1.00 selector changes; full graph also changes composite3/composite4/jitter/cloudhistory',
                   'Native control drift and within-phase variation must be materially below decision signal; >3% native drift is inconclusive',
                   'Flat/adverse whole graph closes this screen; parent compares any overhead with conditional R8 headroom, never subtract as exact recoverable native-output savings'],
        limitations=['Three independent JVMs, no matched temporal phase/history and no isolated pass time; no quality/product acceptance',
                     'Historical .90 screen arithmetic/5% promotion rule is inapplicable to unity graph diagnostic'])


def screen(frame_ms, gpu_ms):
    """Decision arithmetic only; caller must independently pass every evidence gate."""
    require(len(frame_ms) == len(gpu_ms) == 3 and all(isinstance(v,(float,int)) and not isinstance(v,bool)
            and math.isfinite(v) and v > 0 for v in (*frame_ms,*gpu_ms)), 'Need three positive finite frame/GPU metrics')
    drift = dict(frame=abs(frame_ms[2]/frame_ms[0]-1), gpu=abs(gpu_ms[2]/gpu_ms[0]-1))
    frames = [1-frame_ms[1]/frame_ms[i] for i in (0,2)]
    gpus = [1-gpu_ms[1]/gpu_ms[i] for i in (0,2)]
    verdict = ('inconclusive-original-drift' if max(drift.values()) > .10 + 1e-12 else
               'stop-no-useful-signal' if min(frames) <= .05 + 1e-12 or min(gpus) <= 0 else
               'promising-diagnostic-only')
    return EVIDENCE | dict(verdict=verdict, original_drift=drift, frame_reductions=frames,
        gpu_reductions=gpus, acceptance='Arithmetic only; runtime evidence gates must pass independently')


def prepare(output=OUTPUT, receipt=RECEIPT, receipt_sha256=RECEIPT_SHA256, unity_graph=False):
    operation = plan(output, unity_graph=unity_graph); require_exclusive_runtime()
    order = UNITY_ORDER if unity_graph else ORDER
    task = 'runClientLodOn' if unity_graph else 'runClientLodOff'
    scene = quality.qualify_source()
    receipt = Path(receipt).resolve()
    require(isinstance(receipt_sha256,str) and len(receipt_sha256)==64
            and all(c in '0123456789abcdef' for c in receipt_sha256)
            and file_hash(receipt)==receipt_sha256, 'Independent root-reviewed artifact receipt pin differs')
    external = json.loads(receipt.read_text())
    identities = {str(ROOT/n): artifacts.directory_identity(ROOT/n) for n in DIRECTORIES}
    artifacts.validate_expected_receipt(external, identities, include_shaderlod=unity_graph)
    require(len(external['expected_loaded_mods']) == (10 if unity_graph else 9) and len(external['loaded_artifact_inventory']) == (9 if unity_graph else 8),
            'Independent exact nine-mod/eight-root inventory differs')
    if unity_graph:
        import prepare_photon_physical_resolution as physical
        physical.qualify_runtime_receipt(receipt, receipt_sha256, 'physical-resolution-r8')
        require(receipt_sha256 == UNITY_RECEIPT_SHA256, 'Unity graph requires current R14 receipt')
        requested = scene['requested_options']['native'] | {'TAAU': 'true', 'TAAU_RENDER_SCALE': '1.00'}
        discovery = physical.coverage.live.discover(scene['archive'], requested, ROOT / 'build' / (Path(output).name + '-discovery'), 'stock-unity')
        expected = scene['qualification']['effective_option_maps']['native'] | {'TAAU': 'true', 'TAAU_RENDER_SCALE': '1.00'}
        require(discovery['options'] == expected, 'Actual Iris unity full map differs')
        scene['requested_options']['taau100'] = requested
        scene['qualification']['effective_option_maps']['taau100'] = expected
        scene['qualification']['effective_option_hashes']['taau100'] = canonical_options_hash(expected)
        scene['qualification']['unity_actual_iris_discovery'] = discovery
        scene['control'] = scene['control'] | {'enabled': False}
    equivalence = audit_equivalence(ROOT / 'dependencies/runtime-artifact-equivalences.json', external)
    bootstrap=bootstrap_source_evidence()
    immutable = freeze_sources()
    immutable.update(scene['qualification']['source_files_sha256'])
    immutable.update(bootstrap['audited_excerpts_files_sha256'])
    immutable.update({v['path']:v['sha256'] for v in bootstrap['pinned_source_records'].values()})
    output = Path(operation['output']); marker = output / 'taau-cost-preparation.json'
    with unlocked_world(scene['world']):
        fingerprint = world_fingerprint(scene['world'])
        output.mkdir(parents=True, exist_ok=False)
        state = EVIDENCE | dict(schema=1, status='preparing', operation=operation,
            created_at_utc=datetime.now(timezone.utc).isoformat(), launch_authorized=False,
            source_qualification=scene['qualification'], source_world_sha256=fingerprint,
            immutable_source_files_sha256=immutable, development_runtime_inputs=development_fingerprints(),
            independent_artifact_receipt=dict(path=str(receipt),sha256=receipt_sha256,receipt=external),
            api_base_equivalence=equivalence, launch_plan=[], owned_live_processes=[],
            bootstrap_source_evidence=bootstrap,
            postanalysis_plan=unity_postanalysis_plan() if unity_graph else postanalysis_plan(), stop_procedure=quality.STOP,
            launch_status='READY for root runtime/quiet-window review; no worker launch authorization',
            gaps=['Root integrated bounded complete WorkloadSession configuration capture; actual new capture completeness/errors/full startup semantics still require runtime proof',
                  'Driver/context/hardware and all guards must be checked on actual new launches; no quality or speed acceptance'])
        write_json(marker,state)
        try:
            write_json(output/'control.json',scene['control'])
            arms = {}
            for arm in order:
                mode = arm if arm in ('taau90', 'taau100') else 'native'
                arms[arm] = dict(archive=scene['archive'].name, sha256=quality.ORIGINAL_SHA256,
                    requested_options=scene['requested_options'][mode],
                    expected_effective_options=scene['qualification']['effective_option_maps'][mode],
                    expected_effective_options_sha256=scene['qualification']['effective_option_hashes'][mode],
                    copied_world_sha256=fingerprint, main_mod_present=unity_graph, dh_present=False, task=task)
            write_json(output/'workload-manifest.json',dict(schema=1,scene='coastalwater',configuration_only=False,
                preparation_status='complete',source_world=str(scene['world']),source_world_sha256=fingerprint,
                original_sha256=quality.ORIGINAL_SHA256,experiment_arms=arms,requested_chunks=24,
                expected_framebuffer=[2976,1672],control=scene['control'],dh_installed=False,
                main_mod_present=unity_graph,**EVIDENCE))
            for arm in order:
                profile=output/arm
                storage_copytree(scene['source'],profile,ignore=shutil.ignore_patterns(*quality.TRANSIENT))
                (profile/'saves').mkdir(exist_ok=True)
                storage_copytree(scene['world'],profile/'saves'/WORLD,ignore=shutil.ignore_patterns('session.lock'))
                require(world_fingerprint(profile/'saves'/WORLD)==fingerprint,'Copied world differs')
                (profile/'shaderpacks').mkdir(exist_ok=True)
                archive=profile/'shaderpacks'/scene['archive'].name
                shutil.copy2(scene['archive'],archive)
                values=arms[arm]['requested_options']
                archive.with_name(archive.name+'.txt').write_text(''.join(f'{k}={v}\n' for k,v in sorted(values.items())))
                if unity_graph:
                    main, effects = physical.coverage.fresh_config('high'), physical.coverage.effects_config('inherit')
                    physical.validate_config(main, effects)
                    (profile/'config/shaderlod-client.toml').write_text(main)
                    (profile/'config/shaderlod-effects-client.toml').write_text(effects)
                request=EVIDENCE|dict(run_id=output.name+'-'+arm,case='photon-'+arm,task=task,stack='minimal',
                    include_dh=False,measurement_seconds=20,gpu_queries=not unity_graph,fixed_wetness=1 if unity_graph else 0,sample_interval=5,
                    initial_warmup_seconds=60 if unity_graph else 30,
                    expected_framebuffer=[2976,1672],expected_window=[1488,836])
                write_json(profile/'workload-request.json',request)
                state['launch_plan'].append(dict(arm=arm,profile=str(profile),command=command(profile, unity_graph),request=request,
                    inputs=profile_inputs(profile),prepared_writable_inventory=writable_inventory(profile),
                    profile_prelaunch_files_sha256={str(p):file_hash(p) for p in sorted(profile.rglob('*'))
                        if p.is_file() and not p.is_relative_to(profile/'saves')},status='not-launched',owner='root'))
            require(world_fingerprint(scene['world'])==fingerprint,'Source world changed during preparation')
            require(all(file_hash(Path(p))==h for p,h in immutable.items()),'Source/build changed during preparation')
            state.update(status='complete',auxiliary_directory_identities=identities,
                immutable_preparation_files_sha256={str(p):file_hash(p) for p in (output/'control.json',output/'workload-manifest.json')})
            write_json(marker,state)
        except BaseException as error:
            state.update(status='failed',error=str(error));write_json(marker,state);raise
    return state


def verify(state):
    require_exclusive_runtime()
    require(state.get('status')=='complete','Incomplete preparation')
    for field in ('immutable_source_files_sha256','immutable_preparation_files_sha256'):
        for path,pin in state[field].items(): require(file_hash(Path(path))==pin,'Immutable input drift: '+path)
    require(development_fingerprints()==state['development_runtime_inputs'],'Compiled development roots drift')
    external=state['independent_artifact_receipt']
    require(file_hash(Path(external['path']))==external['sha256'],'Independent receipt drift')
    artifacts.validate_expected_receipt(external['receipt'],state['auxiliary_directory_identities'],include_shaderlod=state['operation'].get('main_mod_present', False))
    for row in state['launch_plan']:
        profile=Path(row['profile'])
        require(world_fingerprint(profile/'saves'/WORLD)==state['source_world_sha256'],'Fresh world drift')
        actual={str(p):file_hash(p) for p in sorted(profile.rglob('*')) if p.is_file() and not p.is_relative_to(profile/'saves')}
        require(actual==row['profile_prelaunch_files_sha256'],'Prelaunch profile byte/inventory drift')
        require(profile_inputs(profile)==row['inputs'],'Prepared profile contract drift')
        require(writable_inventory(profile)==row['prepared_writable_inventory'],'Prepared writable semantics drift')
    return EVIDENCE|dict(status='source-prelaunch-verified',launch_status=state['launch_status'],owned_live_processes=[])


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,default=OUTPUT)
    parser.add_argument('--artifact-receipt',type=Path,default=RECEIPT)
    parser.add_argument('--artifact-sha256',default=RECEIPT_SHA256)
    parser.add_argument('--unity-graph',action='store_true',help='Fresh query-free native/TAAU1.00/native graph diagnostic, current R14 and main global OFF')
    parser.add_argument('--dry-run',action='store_true');parser.add_argument('--verify',type=Path)
    args=parser.parse_args()
    try:
        result=verify(json.loads(args.verify.read_text())) if args.verify else plan(args.output, unity_graph=args.unity_graph) if args.dry_run else prepare(args.output, UNITY_RECEIPT if args.unity_graph and args.artifact_receipt == RECEIPT else args.artifact_receipt, UNITY_RECEIPT_SHA256 if args.unity_graph and args.artifact_sha256 == RECEIPT_SHA256 else args.artifact_sha256, args.unity_graph)
        print(json.dumps(result,indent=2,sort_keys=True))
    except (OSError,ValueError,KeyError,zipfile.BadZipFile) as error:
        parser.exit(1,'Cannot prepare Photon TAAU cost bracket: '+str(error)+'\n')


if __name__=='__main__': main()

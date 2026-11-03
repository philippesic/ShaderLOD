#!/usr/bin/env python3
"""Prepare isolated, query-free Photon live raster-budget controls; never launch."""
from __future__ import annotations
from run_storage import copytree as storage_copytree

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

import build_photon_live_pixel_budget as adapter
import prepare_photon_taau_screen as canonical
import qualify_volume_fast_artifacts as artifacts
import run_proof_matrix as matrix
from prepare_parallel_lod_profiles import effects_config
from prepare_reduced_effects_profiles import fresh_config, require, write_json
from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint

OUTPUT = ROOT / 'run-workloads/live-pixel-runtime-r1'
BUILD = ROOT / 'build/live-pixel-runtime-r1'
FREEZE = ROOT / 'build/live-pixel-budget-source-checks/freeze.json'
FREEZE_SHA256 = 'b957d770ef1c4c0119b48837eafbdeb3286210971cb87ea8dada2517ae85d236'
ARCHIVE = ROOT / 'build/live-pixel-budget-enabled.zip'
ARCHIVE_SHA256 = '0da5e4b446fca7ce300217781f3431f3e76ff0a5c3a2b0caafb2bb84f7563abb'
RECEIPT = ROOT / 'build/light-rate-artifact-evidence-r1/artifact-receipt.json'
RECEIPT_SHA256 = 'b3a01fb87e55e3a30a993f1bf1e020749411e5c016dbe9b86c592f47c957227b'
WORLD_SHA256 = '89c9629693e97f7e0c12d41a9ef2cd5a4fa82cd9585644b2dbf2b32bab8c7b46'
ARMS = ('global-off', 'global-on')
OFF = ('gpuTiming', 'cpuTiming', 'cpuLayerTiming', 'gpuPassTiming', 'gpuAttribution',
       'workTrace', 'workTraceOnly', 'spatialLayoutProbe', 'spatialLayoutOnly',
       'dhDrawEvidence', 'waterSceneSurvey', 'shadowObserve', 'visibilityLists',
       'shadowCoherenceOracle', 'smokeTest', 'gpuBenchmark', 'benchmarkOnly', 'cameraQualityOnly')


def plan(output=OUTPUT, build=BUILD, root=ROOT):
    root = Path(root).resolve()
    require(not Path(output).is_symlink() and not Path(build).is_symlink(), 'Symlink output unsupported')
    output, build = Path(output).resolve(), Path(build).resolve()
    require(output == root / 'run-workloads/live-pixel-runtime-r1', 'Only owned profile family allowed')
    require(build.parent == root / 'build' and build.name.startswith('live-pixel-runtime-'), 'Only owned build prefix allowed')
    require(not output.exists() and not build.exists(), 'Preserve previous outputs; use verify')
    return dict(schema=1, output=str(output), build=str(build), arms=list(ARMS),
        framebuffer=[2976, 1672], window=[1488, 836], expected_side_scales={'global-off': 1.0, 'global-on': .75},
        diagnostic_only=True, performanceEvidence=False, transition_accepted=False,
        client_gpu_owner='root', launch_authorized=False, runtime='unverified',
        timing_status='HOLD: new live_pixel guard and complete BenchmarkSession evidence required')


def qualify_archive():
    require(file_hash(FREEZE) == FREEZE_SHA256, 'Reviewed source freeze changed')
    freeze = json.loads(FREEZE.read_text())
    for name, pin in freeze['maintained_inputs_sha256'].items():
        require(file_hash(ROOT / name) == pin, 'Source freeze input drift: ' + name)
    for name, pin in freeze['evidence_sha256'].items():
        require(file_hash(ROOT / name) == pin, 'Source evidence drift: ' + name)
    require(file_hash(ARCHIVE) == ARCHIVE_SHA256, 'Private enabled archive drift')
    require(file_hash(ROOT / adapter.BASE) == adapter.BASE_SHA256, 'Original archive drift')
    original = adapter.archive_members(ROOT / adapter.BASE)
    manifest = json.loads((ROOT / adapter.MANIFEST).read_text())
    expected = adapter.patch_members(original, manifest, 'enabled')
    actual = adapter.archive_members(ARCHIVE)
    require(set(actual) == set(expected) | {adapter.METADATA}, 'Archive member inventory differs')
    require(all(actual[n] == payload for n, payload in expected.items()), 'Assembled member differs')
    metadata = json.loads(actual[adapter.METADATA])
    require(metadata['arm'] == 'enabled' and metadata['manifest_sha256'] == file_hash(ROOT / adapter.MANIFEST), 'Archive metadata differs')
    require(actual['LICENSE'] == original['LICENSE'], 'License changed')
    return dict(source_freeze=dict(path=str(FREEZE), sha256=FREEZE_SHA256),
        archive=str(ARCHIVE), archive_sha256=ARCHIVE_SHA256, metadata=metadata,
        complete_member_sha256={n: hashlib.sha256(payload).hexdigest() for n, payload in sorted(actual.items())},
        original_archive_sha256=adapter.BASE_SHA256, original_member_count=len(original))


JAVA_DISCOVERY = r'''import java.nio.file.*; import java.util.*; import java.util.zip.*;
import java.nio.charset.StandardCharsets;
import com.google.common.collect.ImmutableList; import com.google.common.collect.ImmutableMap;
import net.irisshaders.iris.shaderpack.option.*; import net.irisshaders.iris.shaderpack.include.*;
class LivePixelFullInventory {
 public static void main(String[] a) throws Exception {
  var nodes=ImmutableMap.<AbsolutePackPath,FileNode>builder(); var refs=new TreeMap<String,Set<String>>();
  try(var zip=new ZipFile(a[0])) {
   for(var entry:Collections.list(zip.entries())) {
    String name=entry.getName();
    if(!name.startsWith("shaders/")||!(name.endsWith(".glsl")||name.endsWith(".vsh")||name.endsWith(".fsh")||name.endsWith(".csh")||name.endsWith(".gsh")||name.endsWith(".tcs")||name.endsWith(".tes")))continue;
    String text=new String(zip.getInputStream(entry).readAllBytes(),StandardCharsets.UTF_8);
    var path=AbsolutePackPath.fromAbsolutePath("/"+name.substring(8));
    nodes.put(path,new FileNode(path,ImmutableList.copyOf(text.split("\\R"))));
    for(String key:new OptionAnnotatedSource(text).getBooleanDefineReferences().keySet())refs.computeIfAbsent(key,k->new TreeSet<>()).add(name);
   }
  }
  var constructor=IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class,ImmutableMap.class);constructor.setAccessible(true);
  var graph=constructor.newInstance(nodes.buildOrThrow(),ImmutableMap.of());
  var p=new Properties();try(var reader=Files.newBufferedReader(Path.of(a[1]))){p.load(reader);}
  var overrides=new TreeMap<String,String>();for(String key:p.stringPropertyNames())overrides.put(key,p.getProperty(key));
  var options=new ShaderPackOptions(graph,overrides);var values=options.getOptionValues();var all=new TreeMap<String,String>();
  for(String key:options.getOptionSet().getBooleanOptions().keySet())all.put(key,Boolean.toString(values.getBooleanValueOrDefault(key)));
  for(String key:options.getOptionSet().getStringOptions().keySet())if(all.put(key,values.getStringValueOrDefault(key))!=null)throw new AssertionError("duplicate option "+key);
  for(var entry:all.entrySet())System.out.println("OPTION\t"+entry.getKey()+"\t"+entry.getValue());
  for(var entry:refs.entrySet())System.out.println("REFERENCE\t"+entry.getKey()+"\t"+String.join(",",entry.getValue()));
  System.out.println("SOURCE_COUNT\t"+graph.getNodes().size());
 }
}
'''


def discover(archive, requested, build, label):
    """Pinned Iris ShaderPackOptions over every GLSL stage/include, not settings only."""
    build = Path(build); build.mkdir(parents=True, exist_ok=True)
    manifest = json.loads((ROOT / adapter.MANIFEST).read_text())
    iris = ROOT / manifest['iris_option_discovery']['jar_path']
    require(file_hash(iris) == manifest['iris_option_discovery']['jar_sha256'], 'Pinned parser jar drift')
    cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
    deps = []
    for relative, name in [('com.google.guava/guava/32.1.2-jre', 'guava-32.1.2-jre.jar'),
        ('it.unimi.dsi/fastutil/8.5.12', 'fastutil-8.5.12.jar'), ('org.slf4j/slf4j-api/2.0.9', 'slf4j-api-2.0.9.jar')]:
        matches = list((cache / relative).glob('*/' + name)); require(len(matches) == 1, 'Unique parser dependency missing: ' + name)
        deps.append(matches[0])
    source = build / 'LivePixelFullInventory.java'; source.write_text(JAVA_DISCOVERY)
    overrides = build / (label + '-requested.properties')
    overrides.write_text(''.join(f'{k}={v}\n' for k, v in sorted(requested.items())))
    argv = ['java', '--class-path', os.pathsep.join(map(str, [iris, *deps])), str(source), str(archive), str(overrides)]
    result = subprocess.run(argv, text=True, capture_output=True, timeout=30)
    log = build / (label + '-iris.log'); log.write_text(result.stdout + result.stderr)
    require(result.returncode == 0, 'Full Iris discovery failed: ' + str(log))
    options, refs, count = {}, {}, None
    for line in result.stdout.splitlines():
        fields = line.split('\t')
        if fields[0] == 'OPTION':
            require(len(fields) == 3 and fields[1] not in options, 'Duplicate/malformed parsed option')
            options[fields[1]] = fields[2]
        elif fields[0] == 'REFERENCE': refs[fields[1]] = fields[2].split(',')
        elif fields[0] == 'SOURCE_COUNT': count = int(fields[1])
    require(count and options, 'Empty complete Iris inventory')
    return dict(options=options, sha256=matrix.canonical_options_hash(options), option_count=len(options),
        boolean_reference_members=refs, source_count=count, command=argv, log=str(log), log_sha256=file_hash(log),
        parser_dependency_sha256={str(p): file_hash(p) for p in [iris, *deps]},
        scope='Actual pinned Iris ShaderPackOptions over all archive GLSL nodes; actual loaded runtime equality remains required')


def validate_maps(original, live, requested):
    require(original.get('TAA') == 'true' and original.get('TAAU') == 'false' and original.get('TAAU_RENDER_SCALE') == '0.75', 'Original defaults changed')
    expected = original | {'TAAU': 'true', adapter.SWITCH: 'true', 'SHADERLOD_LIVE_PIXEL_BUDGET_INCLUDED': 'true'}
    require(live == expected, 'Complete live map differs from original plus fixed TAAU/private/helper gates')
    require(all(live.get(k) == v for k, v in requested.items()), 'Requested option override not applied')
    return dict(changed={'TAAU': ['false', 'true']}, added={k: live[k] for k in live.keys() - original.keys()},
        note='Included helper guard is a real discovered Iris option; preserve complete equality, never omit it')


def command(profile):
    argv = ['./gradlew', 'runClientLodOn', '-PrunDirectory=' + str(Path(profile).resolve()),
        '-PperformanceStack=minimal', '-PincludeDh=false', '-PmeasureWorkload=true', '-PmeasureOnly=true',
        '-PinitialWarmup=30', '-PmeasurementSeconds=8', '-PbenchmarkWindowScale=2',
        '-PcomparisonFullscreen=false', '-PfixedWetness=0', '-PcloudSamplerProbe=true', '-PgpuSampleInterval=5']
    argv += ['-P' + key + '=false' for key in OFF]
    for task in canonical.BUILD_TASKS: argv += ['-x', task]
    return argv


def source_files():
    paths = [ROOT / n for n in ('scripts/prepare_photon_live_pixel_budget.py', 'tests/test_photon_live_pixel_runtime.py',
        'docs/PHOTON_LIVE_PIXEL_RUNTIME.md', 'build.gradle', 'dependencies/runtime.lock.json', 'dependencies/performance-mods.lock.json')]
    for tree in ('src/main', 'src/devClient'):
        paths += [p for p in (ROOT / tree).rglob('*') if p.is_file()]
    for module in ('scripts/prepare_photon_taau_screen.py', 'scripts/run_proof_matrix.py',
        'scripts/qualify_volume_fast_artifacts.py', 'scripts/prepare_parallel_lod_profiles.py',
        'scripts/prepare_reduced_effects_profiles.py', 'scripts/setup_comparison.py'):
        paths.append(ROOT / module)
    return {str(p): file_hash(p) for p in sorted(set(paths))}


def prepare(output=OUTPUT, build=BUILD):
    operation = plan(output, build); matrix.require_exclusive_runtime()
    qualified = qualify_archive(); scene = canonical.qualify_source()
    require(file_hash(RECEIPT) == RECEIPT_SHA256, 'Independent mod-present receipt drift')
    receipt = json.loads(RECEIPT.read_text())
    directories = {str(ROOT / n): artifacts.directory_identity(ROOT / n) for n in artifacts.DIRS}
    artifacts.validate_expected_receipt(receipt, directories, include_shaderlod=True)
    require(len(receipt['expected_loaded_mods']) == 10 and len(receipt['loaded_artifact_inventory']) == 9, 'Ten-mod/nine-root receipt required')
    output, build = Path(operation['output']), Path(operation['build'])
    build.mkdir(parents=True)
    requested = scene['requested_options']['native'] | {'TAA': 'true', 'TAAU': 'true', 'TAAU_RENDER_SCALE': '0.75', adapter.SWITCH: 'true'}
    original = discover(scene['archive'], scene['requested_options']['native'], build, 'original-native')
    require(original['options'] == scene['qualification']['effective_option_maps']['native'], 'Complete original Iris map differs from actual canonical map')
    live = discover(ARCHIVE, requested, build, 'live-fixed-taau')
    delta = validate_maps(original['options'], live['options'], requested)
    stock = discover(scene['archive'], scene['requested_options']['native'] | {'TAAU': 'true'}, build, 'stock-taau75')
    require(stock['options'] == original['options'] | {'TAAU': 'true'}, 'Equivalent stock .75 map differs')
    write_json(build / 'option-inventory.json', dict(original_native=original, live_fixed_taau=live, stock_taau75=stock, delta=delta))
    with unlocked_world(scene['world']):
        fingerprint = world_fingerprint(scene['world']); require(fingerprint == WORLD_SHA256, 'Canonical world drift')
        output.mkdir(parents=True)
        models = {arm: dict(requested_options=requested, expected_effective_options=live['options'],
            expected_effective_options_sha256=live['sha256'], archive=ARCHIVE.name, archive_sha256=ARCHIVE_SHA256,
            requested_main_mod_present=True, main_mod_presence_verified=False, dh_present=False,
            global_enabled=arm == 'global-on', expected_scale=.75 if arm == 'global-on' else 1.0) for arm in ARMS}
        launches = []
        for arm in ARMS:
            base = output / arm; base.mkdir()
            control = scene['control'] | {'enabled': arm == 'global-on'}
            write_json(base / 'control.json', control)
            write_json(base / 'workload-manifest.json', dict(schema=1, scene='coastalwater', control=control,
                source_world_sha256=fingerprint, configuration_only=False, diagnostic_only=True, performanceEvidence=False,
                experiment_arms={'control': models[arm]}, source_qualification=qualified))
            profile = base / 'control'
            storage_copytree(scene['source'], profile, ignore=shutil.ignore_patterns(*canonical.TRANSIENT))
            (profile / 'saves').mkdir(exist_ok=True)
            storage_copytree(scene['world'], profile / 'saves' / WORLD, ignore=shutil.ignore_patterns('session.lock'))
            require(world_fingerprint(profile / 'saves' / WORLD) == fingerprint, 'Copied world drift')
            require(not any((profile / 'mods').glob('*.jar')), 'Minimal profile mods required')
            packs = profile / 'shaderpacks'; packs.mkdir(exist_ok=True); archive = packs / ARCHIVE.name
            shutil.copy2(ARCHIVE, archive)
            archive.with_name(archive.name + '.txt').write_text(''.join(f'{k}={v}\n' for k, v in sorted(requested.items())))
            iris = matrix.read_option_properties((profile / 'config/iris.properties').read_text()); iris['shaderPack'] = archive.name
            (profile / 'config/iris.properties').write_text(''.join(f'{k}={v}\n' for k, v in sorted(iris.items())))
            (profile / 'config/shaderlod-client.toml').write_text(fresh_config('adaptive' if arm == 'global-on' else 'high'))
            (profile / 'config/shaderlod-effects-client.toml').write_text(effects_config('inherit'))
            write_json(profile / 'workload-request.json', dict(run_id='live-pixel-runtime-r1-' + arm,
                case='water-' + arm, task='runClientLodOn', stack='minimal', include_dh=False,
                measurement_seconds=8, gpu_queries=False, fixed_wetness=0, diagnostic_only=True, performanceEvidence=False))
            launches.append(dict(arm=arm, profile=str(profile), command=command(profile), owner='root', status='not-launched',
                expected=models[arm], inputs=matrix.profile_inputs(profile), prepared_writable_settings=canonical.writable_settings(profile),
                input_files_sha256={str(p): file_hash(p) for p in sorted(profile.rglob('*')) if p.is_file() and not p.is_relative_to(profile / 'saves')}))
        state = dict(schema=1, status='SOURCE_READY_ROOT_DRIVER_PENDING_TIMING_HOLD', operation=operation,
            source_qualification=qualified, canonical_source_qualification=scene['qualification'], source_world_sha256=fingerprint,
            complete_option_inventory=dict(path=str(build / 'option-inventory.json'), sha256=file_hash(build / 'option-inventory.json'), delta=delta),
            independent_artifact_receipt=dict(path=str(RECEIPT), sha256=RECEIPT_SHA256, receipt=receipt),
            auxiliary_directory_identities=directories, development_runtime_inputs=matrix.development_fingerprints(),
            source_files_sha256=source_files(), launch_plan=launches, launch_authorized=False, owned_live_processes=[],
            timing_status=operation['timing_status'], stop_procedure=canonical.STOP,
            runtime='unverified', quality='unaccepted', transition_accepted=False, performanceEvidence=False,
            current_readback_scope='Linked program core scalar int 2/global0 or1; first fullscreen/raster draw; native sampled texture allocations. No named-stage/raster-coverage proof.',
            denominator='Fixed TAAU scale1 OFF is only incremental same-graph control; original default and equivalent stock .75 are separately pinned in option inventory.')
        write_json(output / 'live-pixel-preparation.json', state)
        require(world_fingerprint(scene['world']) == fingerprint, 'Source world changed')
        return state


def verify(state):
    require(qualify_archive() == state['source_qualification'], 'Source qualification drift')
    require(source_files() == state['source_files_sha256'], 'Source files drift')
    require(matrix.development_fingerprints() == state['development_runtime_inputs'], 'Compiled runtime drift')
    require(file_hash(RECEIPT) == state['independent_artifact_receipt']['sha256'], 'Artifact receipt drift')
    directories = {str(ROOT / n): artifacts.directory_identity(ROOT / n) for n in artifacts.DIRS}
    require(directories == state['auxiliary_directory_identities'], 'Directory identity drift')
    artifacts.validate_expected_receipt(state['independent_artifact_receipt']['receipt'], directories, include_shaderlod=True)
    e = state['complete_option_inventory']; require(file_hash(Path(e['path'])) == e['sha256'], 'Option inventory drift')
    for row in state['launch_plan']:
        profile = Path(row['profile'])
        require(matrix.profile_inputs(profile) == row['inputs'], 'Prepared recipe drift')
        require(all(file_hash(Path(p)) == pin for p, pin in row['input_files_sha256'].items()), 'Prepared file drift')
        require(world_fingerprint(profile / 'saves' / WORLD) == state['source_world_sha256'], 'Copied save drift')
    return dict(status='verified-source-ready', launch_authorized=False, timing_status=state['timing_status'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dry-run', action='store_true'); parser.add_argument('--verify', type=Path)
    args = parser.parse_args()
    try:
        result = verify(json.loads(args.verify.read_text())) if args.verify else plan() if args.dry_run else prepare()
        print(json.dumps(result, indent=2, sort_keys=True))
    except (OSError, ValueError, KeyError, subprocess.SubprocessError, zipfile.BadZipFile) as failure:
        parser.exit(1, 'Cannot prepare live pixel-budget controls: ' + str(failure) + '\n')


if __name__ == '__main__': main()

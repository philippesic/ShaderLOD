#!/usr/bin/env python3
"""Validate independent-JVM matrices and pair the same case across locked stacks."""
from __future__ import annotations

import argparse
import copy
from datetime import datetime
import hashlib
import itertools
import json
import math
from pathlib import Path
import tomllib
import uuid

from analyze_proof_matrix import MATCH_FIELDS, METRICS, paired_effects, run_metrics, spread
from analyze_inprocess_control import require_sha, semantic_state
from run_proof_matrix import canonical_options_hash, read_option_properties, validate_report, validate_temporal_inputs
from summarize_workload import summarize

ROOT = Path(__file__).resolve().parents[1]
# Explicit metadata from the nested NeoForge jars in the pinned optimizer lock.
# Reflect is a Java library, without a loaded NeoForge mod identity.
EMBEDDED_MODS = {'TRansition': 'transition', 'TRender': 'trender'}
STATE_FIELDS = MATCH_FIELDS + ('effective_chunks',)
API_BASE_VARIANTS = {'620fa28434a74dfa9e94af33a3e3bf70a939e9a4b0fcae1fbc01ba2c24f32531',
                     '1aa448a0eff484c7b9f5a6aedbc5050a8d17999f31949dfebb20a7b977f80a07'}
EQUIVALENCE_RUNTIME = {'minecraft': '1.21.1', 'neoforge': '21.1.253',
                       'iris': '1.8.12-snapshot+mc1.21.1-local', 'sodium': '0.6.13+mc1.21.1'}
EQUIVALENCE_PARENTS = {'minecraft': 'a3f878f97a229b543a2c872c4a3c03b19ebd3efbd11af2caed9c258c08aebf55',
                       'neoforge': 'a3f878f97a229b543a2c872c4a3c03b19ebd3efbd11af2caed9c258c08aebf55',
                       'iris': '3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83',
                       'sodium': '7acd7af2ef75ec883a6872c17494ead65e38025151eb08a1d60e36d34377f403'}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def timestamp(value):
    try:
        result = datetime.fromisoformat(value.replace('Z', '+00:00'))
        require(result.tzinfo is not None, 'Capture timestamps require a timezone')
        return result
    except (AttributeError, TypeError, ValueError):
        raise ValueError('Missing/invalid UTC capture timestamp') from None


def artifact_identities(report):
    """Paths locate files; bytes identify jar artifacts, including nested jars."""
    result = {}
    files = report.get('loaded_mod_artifacts')
    require(isinstance(files, dict) and files, 'Loaded artifact identities missing')
    for names, item in files.items():
        require(isinstance(item, dict), 'Malformed loaded artifact')
        if 'sha256' in item:
            identity = {'sha256': require_sha(item['sha256'])}
        else:
            require(item.get('kind') == 'development-directory' and item.get('path'),
                    'Unidentified loaded artifact: ' + names)
            # Development class directories have no byte fingerprint in schema 2.
            # Keep their exact path; explicitly report this evidence limitation.
            identity = {'kind': 'development-directory', 'path': item['path']}
        for name in names.split(','):
            require(name and name not in result, 'Duplicate/empty loaded artifact mod ID')
            result[name] = identity
    require(set(result) == set(report['loaded_mods']), 'Loaded mod/artifact identity coverage differs')
    return result


def load_equivalences(path):
    """Only the explicitly reviewed API Base pair is eligible for this opt-in."""
    if path is None:
        return None
    path = Path(path)
    registry = json.loads(path.read_text())
    require(registry.get('schema') == 1 and len(registry.get('equivalences', [])) == 1,
            'Require the single reviewed equivalence registry entry')
    entry = registry['equivalences'][0]
    require(entry.get('mod_id') == 'fabric_api_base' and entry.get('mod_version') == '0.4.42+d1308ded19'
            and set(entry.get('raw_sha256', [])) == API_BASE_VARIANTS,
            'Unreviewed equivalence library/hash variant')
    require(entry.get('required_loaded_mods') == EQUIVALENCE_RUNTIME
            and entry.get('required_loaded_mod_artifacts_sha256') == EQUIVALENCE_PARENTS,
            'Equivalence registry changed the approved runtime/parent pins')
    variants = entry.get('variants', [])
    require(len(variants) == 2 and {v.get('sha256') for v in variants} == API_BASE_VARIANTS,
            'Equivalence variant proof missing')
    for variant in variants:
        expected_parent = 'iris' if variant['sha256'].startswith('620fa284') else 'sodium'
        require(variant.get('parent_mod_id') == expected_parent
                and variant.get('parent_sha256') == EQUIVALENCE_PARENTS[expected_parent],
                'Equivalence variant parent proof differs')
    proof = entry.get('proof', {})
    require(proof.get('same_entry_names') is True and proof.get('duplicate_entry_names') is False
            and proof.get('no_additional_payload_changes') is True
            and proof.get('changed_payload_paths') == ['META-INF/neoforge.mods.toml'],
            'Equivalence payload proof differs/incomplete')
    for name, count, digest in (
            ('classes', 17, '34704804f9e2834e3114d6029792a5353bd1a09ea9fa814883bb912ca9ace10a'),
            ('resources_except_changed_metadata', 2, '4afce8cc02b58dc62b4faa2e6a5c8e75ec2bc9c76e348dea42491b0175ea2385'),
            ('all_files_except_changed_metadata', 19, 'f075c9f89451356a91e59ff0c5a534c8358f3f7885747ad6c79a490cb5aceffa')):
        require(proof.get(name) == {'file_count': count, 'sha256': digest}, 'Unreviewed equivalence payload fingerprint')
    require(proof.get('sole_payload_difference') == {
        'dependency_mod_id': 'neoforge', 'field': 'versionRange', 'values': ['[21.1.169,)', '[21.1.115,)'],
        'required_runtime_version': '21.1.253', 'both_ranges_satisfied': True}, 'Unreviewed equivalence metadata difference')
    return {'path': str(path.resolve()), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'entry': entry}


def qualified_identities(report, artifacts, equivalences):
    if equivalences is None or 'fabric_api_base' not in artifacts:
        return artifacts, []
    entry = equivalences['entry']
    mods = report['loaded_mods']
    require(mods['fabric_api_base'] == entry['mod_version'], 'Equivalence library version mismatch')
    raw = artifacts['fabric_api_base'].get('sha256')
    require(raw in API_BASE_VARIANTS, 'Unknown equivalence library hash')
    for name, version in entry['required_loaded_mods'].items():
        require(mods.get(name) == version, 'Equivalence runtime version mismatch: ' + name)
    for name, digest in entry['required_loaded_mod_artifacts_sha256'].items():
        require(artifacts.get(name, {}).get('sha256') == digest, 'Equivalence pinned parent mismatch: ' + name)
    variant = next(variant for variant in entry['variants'] if variant['sha256'] == raw)
    record = {'equivalence_id': entry['id'], 'mod_id': 'fabric_api_base', 'mod_version': mods['fabric_api_base'],
              'raw_sha256': raw, 'variant': variant, 'source_attribution': 'Audited raw archive hash identifies the pinned parent copy',
              'qualification': 'Equal extracted classes/resources; sole metadata difference is a NeoForge lower bound satisfied on this exact runtime. Runtime transformed class bytes and resolver determinism are unverified.'}
    result = copy.deepcopy(artifacts)
    result['fabric_api_base'] = {'qualified_equivalence': entry['id']}
    return result, [record]


def locked_stack(report, stack, lock, equivalences=None):
    """Verify loaded optional mods, then return only invariant base identities."""
    require(stack in lock['stacks'], 'Unknown stack: ' + stack)
    mods = report.get('loaded_mods')
    require(isinstance(mods, dict) and mods, 'Loaded versions missing')
    require({'minecraft', 'neoforge', 'iris', 'sodium'} <= mods.keys(), 'Mandatory runtime mods missing')
    artifacts = artifact_identities(report)
    expected = {}
    all_optional = set()
    for name, pin in lock['artifacts'].items():
        parent = pin['jar_mod_id']
        all_optional.add(parent)
        embedded = {EMBEDDED_MODS[lib['artifact']]: (lib['version'].split('-')[0], require_sha(lib['sha256']))
                    for lib in pin.get('embedded_libraries', []) if lib['artifact'] in EMBEDDED_MODS}
        all_optional.update(embedded)
        if name in lock['stacks'][stack]:
            expected[parent] = (pin['jar_version'], require_sha(pin['hashes']['sha256']))
            expected.update(embedded)
    require(set(mods) & all_optional == set(expected), 'Unexpected/missing optional loaded mods for ' + stack)
    for name, (version, digest) in expected.items():
        require(mods[name] == version, 'Optional loaded version mismatch: ' + name)
        require(artifacts[name].get('sha256') == digest, 'Optional loaded hash mismatch: ' + name)
    jars = report.get('runtime_mod_jars_sha256')
    require(isinstance(jars, dict), 'Profile jar inventory missing')
    jars = dict(jars)
    for name, pin in lock['artifacts'].items():
        filename = pin['filename']
        if filename in jars:
            require(name in lock['stacks'][stack], 'Unexpected optimizer profile jar: ' + filename)
            require(jars.pop(filename) == pin['hashes']['sha256'], 'Optimizer profile hash mismatch: ' + filename)
    for digest in jars.values():
        require_sha(digest)
    artifacts, qualified = qualified_identities(report, artifacts, equivalences)
    return {'loaded_mods': {k: v for k, v in mods.items() if k not in expected},
            'loaded_artifacts': {k: v for k, v in artifacts.items() if k not in expected},
            'profile_jars': jars,
            'artifact_equivalences': qualified,
            'optional_loaded': {name: {'version': version, 'sha256': digest}
                                for name, (version, digest) in expected.items()}}


def window_reference(report):
    """Compare state and pacing, not observation counts or wall-clock frame IDs."""
    window = report.get('window_state')
    if window is None:
        return None
    require(window.get('schema') == 1, 'Unknown window observation schema')
    phase = window.get('capture', {})
    count = phase.get('observations', 0)
    require(isinstance(count, int) and not isinstance(count, bool) and count > 0,
            'Window capture observations missing')
    require(phase.get('available_observations') == count and phase.get('unavailable_observations') == 0,
            'Window observation unavailable during capture')
    require(not phase.get('transition_counts') and not phase.get('transitions') and phase.get('dropped_transitions') == 0,
            'Window/pacing changed during capture')
    start = phase.get('start', {}).get('state')
    end = phase.get('end', {}).get('state')
    require(isinstance(start, dict) and start == end, 'Window boundary states changed/missing')
    require(start.get('availability') == 'available', 'Window state unavailable')
    for flag in ('focused', 'iconified', 'visible'):
        require(start.get(flag) in ('true', 'false'), 'Unknown window flag: ' + flag)
        observations = phase.get(flag, {})
        require(observations.get('unknown_observations') == 0, 'Unknown capture window observations')
        require(observations.get(start[flag] + '_observations') == count,
                'Window flag observation disagreement: ' + flag)
    require(start['iconified'] == 'false' and start['visible'] == 'true', 'Hidden/minimized capture window')
    pacing = start.get('pacing', {})
    for name in ('pause_on_lost_focus', 'fullscreen', 'vsync'):
        require(isinstance(pacing.get(name), bool), 'Missing window pacing: ' + name)
    limit = pacing.get('configured_framerate_limit')
    require(isinstance(limit, int) and not isinstance(limit, bool) and limit > 0, 'Missing configured frame limit')
    require(not pacing['pause_on_lost_focus'] or start['focused'] == 'true', 'Unfocused capture with focus pause')
    for name in ('window_width', 'window_height', 'framebuffer_width', 'framebuffer_height'):
        require(isinstance(start.get(name), int) and start[name] > 0, 'Invalid window dimensions')
    for category, width, height in (('window_size_counts', 'window_width', 'window_height'),
                                    ('framebuffer_size_counts', 'framebuffer_width', 'framebuffer_height')):
        require(phase.get(category) == {f'{start[width]}x{start[height]}': count}, 'Window size observations changed')
    for name in ('framebuffer_width', 'framebuffer_height'):
        require(start[name] == report['input_state_end'][name], 'Window/framebuffer evidence differs')
    result = copy.deepcopy(start)
    result.pop('note', None)
    return result


def semantic_reference(report):
    state = copy.deepcopy(report['input_state_end'])
    require(set(STATE_FIELDS) <= state.keys(), 'Incomplete camera/weather/framebuffer/radius evidence')
    for name in STATE_FIELDS:
        if name == 'dimension':
            require(isinstance(state[name], str) and state[name], 'Dimension missing')
        else:
            require(isinstance(state[name], (int, float)) and not isinstance(state[name], bool)
                    and math.isfinite(state[name]), 'Invalid actual input: ' + name)
    # Normalization is justified by captured content, not by ignoring raw hashes.
    iris = report.get('iris_configuration')
    require(isinstance(iris, str) and iris, 'Captured Iris configuration missing')
    iris = read_option_properties(iris)
    require(iris.get('shaderPack') == state.get('shader_pack'), 'Captured Iris selection differs')
    state.pop('config/iris.properties_sha256', None)
    dh = report.get('actual_dh_configuration')
    require(not report['dhPresent'] or isinstance(dh, str) and dh, 'Captured DH configuration missing')
    if dh is not None:
        require(isinstance(dh, str), 'Malformed DH configuration')
        try:
            dh = tomllib.loads(dh)
        except tomllib.TOMLDecodeError as error:
            raise ValueError('Invalid captured DH TOML') from error
        state.pop('config/DistantHorizons.toml_sha256', None)
    if 'loaded_semantic_config' in state:
        state = semantic_state(state)
    else:
        # Ordinary schema-2 workloads lack full loaded rendering config. Equal
        # captured byte hashes are conservative; never strip this identity.
        require_sha(state.get('config/shaderlod-client.toml_sha256'))
    return {'state': state, 'iris': iris, 'dh': dh}


def check_stack_pair(base, candidate, base_report, candidate_report, manifest, lock, equivalences=None):
    """Same-case comparison; every LOD option and detail setting stays equal."""
    require(base['case'] == candidate['case'] and base['block'] == candidate['block'], 'Unmatched case/block')
    for field in ('modPresent', 'dhPresent', 'actual_shader_sha256', 'effective_shader_options',
                  'effective_shader_options_sha256', 'sampling_policy', 'backend', 'warmup_seconds'):
        require(field in base_report and field in candidate_report and base_report[field] == candidate_report[field],
                'Unmatched stack input: ' + field)
    require(semantic_reference(base_report) == semantic_reference(candidate_report), 'Unmatched semantic camera/config/detail inputs')
    for name in ('shadow_culling_active', 'separate_shadow_lists_active', 'shadow_frustum_coherence_active', 'geometry_policy'):
        require(base_report.get(name) == candidate_report.get(name), 'Unmatched runtime feature: ' + name)
    a = locked_stack(base_report, base['stack'], lock, equivalences)
    b = locked_stack(candidate_report, candidate['stack'], lock, equivalences)
    for name in ('loaded_mods', 'loaded_artifacts', 'profile_jars'):
        require(a[name] == b[name], 'Unmatched base runtime ' + name)
    properties = []
    for row, report in ((base, base_report), (candidate, candidate_report)):
        props = dict(report.get('runtime_properties', {}))
        require(props.pop('shaderlod.dev.performanceStack', None) == row['stack'], 'Runtime stack property differs')
        properties.append(props)
    require(properties[0] == properties[1], 'Unmatched instrumentation/pacing properties')
    # Request UUIDs/case/stack/block identify captures, not workload policy.
    requests = [{k: v for k, v in row['request'].items() if k not in ('run_id', 'case', 'stack', 'block')}
                for row in (base, candidate)]
    require(requests[0] == requests[1], 'Unmatched capture/warmup request policy')
    window_a, window_b = window_reference(base_report), window_reference(candidate_report)
    require(window_a == window_b, 'Unmatched window/pacing evidence')
    temporal = [validate_temporal_inputs(report, row['request'])
                for row, report in ((base, base_report), (candidate, candidate_report))]
    for name in ('wetness_policy', 'fixed_wetness'):
        require(temporal[0].get(name) == temporal[1].get(name), 'Unmatched shader weather policy')
    # Dynamic wetness is a material weather input even under fixed world rain.
    if temporal[0]['wetness_policy'] != 'fixed':
        for name in ('start_raw_wetness', 'end_raw_wetness', 'capture_applied_min', 'capture_applied_max'):
            require(temporal[0].get(name) is not None and temporal[0][name] == temporal[1].get(name),
                    'Unmatched/unobserved dynamic shader weather: ' + name)
    require_sha(manifest['source_inputs'][base['case']]['source_world_sha256'])
    return {'baseline_optional_loaded': a['optional_loaded'], 'candidate_optional_loaded': b['optional_loaded'],
            'baseline_artifact_equivalences': a['artifact_equivalences'], 'candidate_artifact_equivalences': b['artifact_equivalences'],
            'window_pacing_evidence': window_a, 'temporal_observations': temporal}


def analyze(path, lock_path=None, equivalences_path=None):
    path = Path(path)
    manifest = json.loads(path.read_text())
    require(manifest.get('schema') == 2 and manifest.get('status') == 'complete', 'Require complete schema-2 matrix')
    lock_path = Path(lock_path or ROOT / 'dev/performance-mods.lock.json')
    lock = json.loads(lock_path.read_text())
    lock_hash = hashlib.sha256(lock_path.read_bytes()).hexdigest()
    equivalences = load_equivalences(equivalences_path)
    require(manifest.get('performance_mods_lock_sha256') == lock_hash, 'Matrix optimizer lock fingerprint missing/mismatched')
    repeats = manifest.get('repeats')
    require(isinstance(repeats, int) and not isinstance(repeats, bool) and repeats > 0, 'Invalid repeat count')
    runs = manifest.get('runs')
    require(isinstance(runs, list) and runs, 'Matrix runs missing')
    require(all(run.get('status') == 'complete' for run in runs), 'Failed/incomplete matrix runs retained; analysis rejected')
    cases = set(manifest.get('cases', manifest.get('source_inputs', {}).keys()))
    stacks = set(manifest.get('stacks', [run['stack'] for run in runs]))
    require(len(stacks) >= 2 and stacks <= lock['stacks'].keys(), 'Need at least two known stacks')
    require(cases and cases == set(manifest['source_inputs']), 'Declared/source cases differ')
    expected = set(itertools.product(range(1, repeats + 1), cases, stacks))
    indexed, reports, metrics, seen_ids, seen_paths = {}, {}, {}, set(), set()
    launch_intervals = []
    evidence_limits = set()
    if 'stacks' not in manifest:
        evidence_limits.add('Legacy stack selection inferred from retained plan; removal of every row of a stack cannot be detected without a declared stacks field.')
    for run in runs:
        key = (run['block'], run['case'], run['stack'])
        require(key in expected and key not in indexed, 'Unexpected/duplicate repeat condition')
        indexed[key] = run
        request = run.get('request', {})
        identifier = request.get('run_id')
        try:
            normalized_id = str(uuid.UUID(identifier))
        except (ValueError, TypeError, AttributeError):
            raise ValueError('Invalid capture UUID') from None
        require(normalized_id not in seen_ids, 'Duplicate capture UUID')
        seen_ids.add(normalized_id)
        for name in ('block', 'case', 'stack'):
            require(request.get(name) == run[name], 'Run/request label mismatch: ' + name)
        paths = []
        for name in ('measurement', 'summary'):
            relative = Path(run[name])
            require(not relative.is_absolute() and '..' not in relative.parts, 'Report path escapes matrix')
            resolved = (path.parent / relative).resolve()
            require(resolved not in seen_paths, 'Reused capture/summary path')
            seen_paths.add(resolved)
            paths.append(resolved)
        report = json.loads(paths[0].read_text())
        require(report.get('schema') == 2, 'Require schema-2 workload evidence')
        require(report.get('request') == request, 'Report/manifest request mismatch')
        begin, end = timestamp(run.get('started_at_utc')), timestamp(run.get('finished_at_utc'))
        captured, written = timestamp(report.get('measurement_started_at_utc')), timestamp(report.get('written_at_utc'))
        require(begin <= captured <= written <= end, 'Stale or out-of-launch capture timestamps')
        launch_intervals.append((begin, end))
        inputs = manifest['source_inputs'][run['case']]
        require_sha(inputs.get('shader_sha256'))
        require_sha(inputs.get('source_world_sha256'))
        require(report.get('workload-manifest.json', {}).get('source_world_sha256') == inputs['source_world_sha256'],
                'Reported/source save identity differs or missing')
        validate_report(report, request, inputs, 0)
        require(isinstance(report['modPresent'], bool) and isinstance(report['dhPresent'], bool), 'Invalid presence evidence')
        require(report['dhPresent'] == ('distanthorizons' in report['loaded_mods']), 'Loaded DH/presence mismatch')
        require(report['modPresent'] == ('shaderlod' in report['loaded_mods']), 'Loaded ShaderLOD/presence mismatch')
        locked_stack(report, run['stack'], lock, equivalences)
        semantic_reference(report)
        window_reference(report)
        if request['gpu_queries']:
            require(report.get('backend') in ('TIMESTAMP', 'ELAPSED_ALTERNATING'), 'Unsupported GPU timing backend')
        require(isinstance(report.get('warmup_seconds'), (int, float)) and not isinstance(report['warmup_seconds'], bool)
                and math.isfinite(report['warmup_seconds']) and report['warmup_seconds'] > 0, 'Missing/invalid warmup evidence')
        options = report['effective_shader_options']
        require(all(isinstance(k, str) and isinstance(v, str) for k, v in options.items()), 'Malformed effective shader map')
        require(report['effective_shader_options_sha256'] == canonical_options_hash(options), 'Option fingerprint differs')
        declared_hash = inputs.get('effective_shader_options_sha256')
        if declared_hash is not None:
            require(declared_hash == report['effective_shader_options_sha256'], 'Effective option/source reference mismatch')
        # Never trust edited/stale summary metrics: derive again from retained samples.
        summary = json.loads(paths[1].read_text())
        rebuilt = summarize(paths[0])
        for name in ('request', 'render_fps', 'gpu', 'cpu_wall', 'cpu_thread', 'sampling_policy', 'backend'):
            require(summary.get(name) == rebuilt.get(name), 'Summary/report mismatch: ' + name)
        require(math.isfinite(rebuilt['render_fps']) and rebuilt['render_fps'] > 0, 'Invalid render FPS')
        reports[key], metrics[key] = report, run_metrics(rebuilt)
        if report.get('window_state') is None:
            evidence_limits.add('Window focus, visibility, frame limit and vsync were not observed in these older captures.')
        if 'loaded_semantic_config' not in report['input_state_end']:
            evidence_limits.add('Full loaded ShaderLOD config unavailable; equal raw config hashes and live LOD snapshots required conservatively.')
        if any(item.get('kind') == 'development-directory' for item in report['loaded_mod_artifacts'].values()):
            evidence_limits.add('Development class directories have equal paths, without byte hashes; frozen build inputs remain a runtime-owner obligation.')
    require(set(indexed) == expected, 'Incomplete case/stack/block matrix')
    ordered = sorted(launch_intervals)
    require(all(previous[1] <= following[0] for previous, following in zip(ordered, ordered[1:])),
            'Overlapping JVM launch intervals; shared-GPU comparison rejected')
    # Reject changes across blocks too, rather than matching one contaminated pair.
    for case in sorted(cases):
        reference = next(key for key in indexed if key[1] == case)
        for key in indexed:
            if key[1] != case:
                continue
            base = dict(indexed[reference], block=key[0])
            check_stack_pair(base, indexed[key], reports[reference], reports[key], manifest, lock, equivalences)
    comparisons = []
    order = [stack for stack in ('minimal', 'optimized', 'optimized-culling') if stack in stacks]
    for case in sorted(cases):
        for baseline, candidate in itertools.combinations(order, 2):
            pairs, checks = [], []
            for block in range(1, repeats + 1):
                a, b = (block, case, baseline), (block, case, candidate)
                checks.append(check_stack_pair(indexed[a], indexed[b], reports[a], reports[b], manifest, lock, equivalences))
                pairs.append((metrics[a], metrics[b]))
            comparisons.append({'case': case, 'baseline_stack': baseline, 'candidate_stack': candidate,
                                'effect_attribution': 'Optional optimizer stack only; ShaderLOD presence/settings held constant',
                                'blocks': list(range(1, repeats + 1)), 'checks': checks, 'effects': paired_effects(pairs)})
    conditions = [{'case': case, 'stack': stack,
                   'metrics': {name: spread([metrics[(block, case, stack)][name] for block in range(1, repeats + 1)
                                            if metrics[(block, case, stack)][name] is not None]) for name in METRICS}}
                  for case, stack in itertools.product(sorted(cases), order)]
    return {'schema': 1, 'manifest': str(path.resolve()), 'performance_mods_lock_sha256': lock_hash,
            'artifact_comparison_policy': 'strict raw SHA256' if equivalences is None else 'explicit qualified API Base equivalence; all other artifacts strict',
            'equivalence_registry': equivalences,
            'retained_runs': len(runs), 'conditions': conditions, 'stack_comparisons': comparisons,
            'evidence_limits': sorted(evidence_limits),
            'uncertainty': 'Blocks of independent JVM launches are the replicate unit. Median/range/MAD and per-block time reductions only; no frame-derived confidence intervals. Partial Williams cycles do not fully balance position drift. Small repeat counts remain exploratory.',
            'limits': 'Stack effects are separate from ShaderLOD incremental gain. GPU render intervals, CPU clocks and FPS-derived frame duration describe different quantities. P95 is a per-run tail, not uncertainty. Quality/rendering and moving-camera checks remain separate. Every planned run is required; failures are retained and cannot be silently filtered.'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('manifest', type=Path)
    parser.add_argument('--lock', type=Path, default=ROOT / 'dev/performance-mods.lock.json')
    parser.add_argument('--equivalences', type=Path, help='Explicit opt-in to the exact reviewed API Base artifact pair on its pinned runtime')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    contents = json.dumps(analyze(args.manifest, args.lock, args.equivalences), indent=2, allow_nan=False) + '\n'
    if args.output:
        args.output.write_text(contents)
    else:
        print(contents, end='')


if __name__ == '__main__':
    main()

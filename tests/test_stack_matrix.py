"""Cross-stack pairing rejects changed quality, runtime identity and incomplete evidence."""
import copy
from datetime import datetime, timedelta, timezone
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
import uuid

SCRIPTS = Path(__file__).resolve().parents[1] / 'scripts'
sys.path.insert(0, str(SCRIPTS))
from analyze_stack_matrix import analyze, EMBEDDED_MODS, EQUIVALENCE_RUNTIME, EQUIVALENCE_PARENTS, load_equivalences
from run_proof_matrix import canonical_options_hash
from summarize_workload import summarize


class StackMatrixTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.path = self.root / 'matrix-manifest.json'
        self.lock_path = SCRIPTS.parent / 'dev/performance-mods.lock.json'
        self.lock = json.loads(self.lock_path.read_text())
        self.manifest, self.reports = self.fixture()

    def fixture(self):
        options = {'QUALITY': 'high', 'SHADERLOD_CLOUDS': 'false', 'SHADERLOD_NEAR': '96'}
        state = {'framebuffer_width': 1488, 'framebuffer_height': 836, 'requested_chunks': 24,
                 'effective_chunks': 24, 'dimension': 'minecraft:overworld', 'day_time': 7000,
                 'rain': 0.0, 'thunder': 0.0, 'x': 240.5, 'y': 68.0, 'z': 680.5, 'yaw': -180.0,
                 'pitch': 3.0, 'shaders_in_use': True, 'fallback': False, 'shader_pack': 'photon.zip',
                 'effective_shader_options': options, 'effective_shader_options_sha256': canonical_options_hash(options),
                 'config/shaderlod-client.toml_sha256': 'a' * 64,
                 'config/iris.properties_sha256': 'b' * 64, 'config/DistantHorizons.toml_sha256': 'c' * 64}
        inputs = {'shader_pack': 'photon.zip', 'shader_sha256': 'd' * 64, 'control_sha256': 'e' * 64,
                  'source_world_sha256': 'f' * 64, 'requested_shader_options': options,
                  'shader_option_defaults': options, 'shadow_culling_configured': False,
                  'effective_shader_options_sha256': canonical_options_hash(options)}
        manifest = {'schema': 2, 'status': 'complete', 'repeats': 2,
                    'source_inputs': {'forest-high': inputs},
                    'performance_mods_lock_sha256': hashlib.sha256(self.lock_path.read_bytes()).hexdigest(),
                    'runs': []}
        reports = []
        for block in range(1, 3):
            for stack in self.lock['stacks']:
                index = len(reports)
                start = datetime(2026, 10, 5, tzinfo=timezone.utc) + timedelta(seconds=index * 60)
                stamp = lambda offset: (start + timedelta(seconds=offset)).isoformat()
                request = {'run_id': str(uuid.uuid4()), 'block': block, 'case': 'forest-high', 'stack': stack,
                           'task': 'runClientLodBaseline', 'measurement_seconds': 30, 'include_dh': True,
                           'gpu_queries': True, 'fixed_wetness': 0}
                run = {'block': block, 'case': 'forest-high', 'stack': stack, 'status': 'complete',
                       'started_at_utc': stamp(0), 'finished_at_utc': stamp(45), 'request': request,
                       'measurement': f'{index}-measurement.json', 'summary': f'{index}-summary.json'}
                mods = {'minecraft': '1.21.1', 'neoforge': '21.1.253', 'iris': '1.8.12', 'sodium': '0.6.13', 'distanthorizons': '3.3.3'}
                artifacts = {name: {'path': '/fixture/' + name + '.jar', 'sha256': '1' * 64} for name in mods}
                # Minecraft and NeoForge really can share one loaded file entry.
                artifacts['minecraft,neoforge'] = artifacts.pop('minecraft')
                artifacts.pop('neoforge')
                for name in self.lock['stacks'][stack]:
                    pin = self.lock['artifacts'][name]
                    mods[pin['jar_mod_id']] = pin['jar_version']
                    artifacts[pin['jar_mod_id']] = {'path': '/fixture/' + pin['filename'], 'sha256': pin['hashes']['sha256']}
                    for lib in pin['embedded_libraries']:
                        if lib['artifact'] in EMBEDDED_MODS:
                            mod_id = EMBEDDED_MODS[lib['artifact']]
                            mods[mod_id] = lib['version'].split('-')[0]
                            artifacts[mod_id] = {'path': '', 'sha256': lib['sha256']}
                fps = {'minimal': [100, 50], 'optimized': [125, 100], 'optimized-culling': [200, 125]}[stack][block - 1]
                supplier = {'supplierReads': 20, 'measurementReads': 10, 'appliedWetness': 0, 'appliedMin': 0, 'appliedMax': 0}
                wetness = {'mode': 'fixed', 'fixedWetness': 0, 'measurementReads': 10, 'suppliers': [supplier]}
                report = {'schema': 2, 'status': 'complete', 'profile': 'fixture', 'request': copy.deepcopy(request),
                          'measurement_started_at_utc': stamp(10), 'written_at_utc': stamp(40),
                          'modPresent': False, 'dhPresent': True, 'actual_shader_sha256': 'd' * 64,
                          'effective_shader_options': options, 'effective_shader_options_sha256': canonical_options_hash(options),
                          'control_sha256': 'e' * 64, 'input_state_start': copy.deepcopy(state), 'input_state_end': copy.deepcopy(state),
                          'workload-manifest.json': {'source_world_sha256': 'f' * 64},
                          'loaded_mods': mods, 'loaded_mod_artifacts': artifacts, 'runtime_mod_jars_sha256': {},
                          'runtime_properties': {'shaderlod.dev.performanceStack': stack, 'shaderlod.dev.gpuSampleInterval': '16'},
                          'iris_configuration': 'shaderPack=photon.zip\nenableShaders=true\n',
                          'actual_dh_configuration': '[general]\nlodChunkRenderDistanceRadius=128\n',
                          'shader_temporal_inputs_start': {'wetness': copy.deepcopy(wetness)},
                          'shader_temporal_inputs_end': {'wetness': copy.deepcopy(wetness)},
                          'warmup_seconds': 10, 'elapsed_seconds': 30, 'render_frames': fps * 30,
                          'sampling_policy': 'every16', 'backend': 'ELAPSED_ALTERNATING', 'shadow_culling_active': False,
                          'gpu_samples': [{'scope': 'game-render', 'frameId': 16, 'milliseconds': 1000 / fps}],
                          'cpu_samples': [{'scope': 'run-tick', 'frameId': 16, 'wallMilliseconds': 1000 / fps, 'threadMilliseconds': 1}]}
                window = {'availability': 'available', 'focused': 'true', 'iconified': 'false', 'visible': 'true',
                          'window_width': 744, 'window_height': 418, 'framebuffer_width': 1488, 'framebuffer_height': 836,
                          'pacing': {'pause_on_lost_focus': False, 'fullscreen': False, 'configured_framerate_limit': 260, 'vsync': False}}
                capture = {'observations': 10, 'available_observations': 10, 'unavailable_observations': 0,
                           'start': {'state': copy.deepcopy(window)}, 'end': {'state': copy.deepcopy(window)},
                           'window_size_counts': {'744x418': 10}, 'framebuffer_size_counts': {'1488x836': 10},
                           'transition_counts': {}, 'transitions': [], 'dropped_transitions': 0}
                for flag in ('focused', 'iconified', 'visible'):
                    capture[flag] = {'true_observations': 10 if window[flag] == 'true' else 0,
                                     'false_observations': 10 if window[flag] == 'false' else 0, 'unknown_observations': 0}
                report['window_state'] = {'schema': 1, 'capture': capture}
                manifest['runs'].append(run)
                reports.append(report)
        return manifest, reports

    def save(self):
        self.path.write_text(json.dumps(self.manifest))
        for run, report in zip(self.manifest['runs'], self.reports):
            path = self.root / run['measurement']
            path.write_text(json.dumps(report))
            (self.root / run['summary']).write_text(json.dumps(summarize(path)))

    def result(self):
        self.save()
        return analyze(self.path, self.lock_path)

    def set_state(self, report, name, value):
        for boundary in ('input_state_start', 'input_state_end'):
            report[boundary][name] = value

    def test_pair_by_block_not_pooled_mean_and_all_three_stack_pairs(self):
        result = self.result()
        self.assertEqual(result['retained_runs'], 6)
        self.assertEqual(len(result['stack_comparisons']), 3)
        effects = result['stack_comparisons'][0]['effects']
        self.assertEqual(effects['frame_ms']['per_block_percent'], [20, 50])
        self.assertEqual(effects['frame_ms']['percent_time_reduction']['median'], 35)
        self.assertEqual(effects['frame_ms']['percent_time_reduction']['independent_jvm_runs'], 2)
        self.assertEqual(effects['gpu_mean_ms']['per_block_percent'], [20, 50])
        self.assertNotIn('confidence_interval', effects['frame_ms'])
        self.assertIn('stack only', result['stack_comparisons'][0]['effect_attribution'])

    def test_runtime_optional_versions_hashes_and_embedded_are_proved(self):
        mutations = (
            lambda r: r['loaded_mods'].update(modernfix='wrong'),
            lambda r: r['loaded_mod_artifacts']['modernfix'].update(sha256='0' * 64),
            lambda r: r['loaded_mod_artifacts'].pop('modernfix'),
            lambda r: r['loaded_mods'].update(entityculling='1.11.2'),
        )
        for mutate in mutations:
            with self.subTest(mutate=mutate):
                saved = copy.deepcopy(self.reports[1]); mutate(self.reports[1])
                with self.assertRaises(ValueError): self.result()
                self.reports[1] = saved
        self.reports[2]['loaded_mod_artifacts']['transition']['sha256'] = '2' * 64
        with self.assertRaisesRegex(ValueError, 'Optional loaded hash'): self.result()

    def test_base_mod_versions_extra_mods_and_base_artifacts_cannot_differ(self):
        for mutate in (lambda r: r['loaded_mods'].update(iris='different'),
                       lambda r: r['loaded_mod_artifacts']['iris'].update(sha256='2' * 64),
                       lambda r: (r['loaded_mods'].update(extra='1'), r['loaded_mod_artifacts'].update(extra={'sha256': '2' * 64})),
                       lambda r: r['runtime_mod_jars_sha256'].update(extra='2' * 64)):
            saved = copy.deepcopy(self.reports[1]); mutate(self.reports[1])
            with self.assertRaises(ValueError): self.result()
            self.reports[1] = saved

    def test_shader_archive_and_every_effective_lod_option_stay_identical(self):
        self.reports[1]['actual_shader_sha256'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'Selected shader archive'): self.result()
        self.reports[1]['actual_shader_sha256'] = 'd' * 64
        for option in ('QUALITY', 'SHADERLOD_CLOUDS', 'SHADERLOD_NEAR'):
            saved = copy.deepcopy(self.reports[1])
            self.reports[1]['effective_shader_options'] = dict(saved['effective_shader_options'], **{option: 'changed'})
            self.reports[1]['effective_shader_options_sha256'] = canonical_options_hash(self.reports[1]['effective_shader_options'])
            with self.assertRaises(ValueError): self.result()
            self.reports[1] = saved

    def test_camera_weather_effective_radius_and_mod_presence_cannot_change(self):
        for field in ('x', 'yaw', 'day_time', 'rain', 'requested_chunks', 'effective_chunks', 'framebuffer_width'):
            saved = copy.deepcopy(self.reports[1])
            self.set_state(self.reports[1], field, saved['input_state_end'][field] + 1)
            with self.assertRaises(ValueError): self.result()
            self.reports[1] = saved
        self.reports[1]['modPresent'] = True
        with self.assertRaises(ValueError): self.result()

    def test_semantic_dh_iris_normalization_accepts_comments_but_rejects_values(self):
        self.reports[1]['actual_dh_configuration'] = '# rewritten\n[general]\nlodChunkRenderDistanceRadius = 128\n'
        self.reports[1]['iris_configuration'] = '# rewritten\nenableShaders=true\nshaderPack=photon.zip\n'
        self.set_state(self.reports[1], 'config/iris.properties_sha256', '0' * 64)
        self.set_state(self.reports[1], 'config/DistantHorizons.toml_sha256', '0' * 64)
        self.result()
        self.reports[1]['actual_dh_configuration'] = '[general]\nlodChunkRenderDistanceRadius=64\n'
        with self.assertRaisesRegex(ValueError, 'semantic'): self.result()

    def test_raw_config_identity_retained_when_loaded_semantics_unavailable(self):
        self.set_state(self.reports[1], 'config/shaderlod-client.toml_sha256', '0' * 64)
        with self.assertRaisesRegex(ValueError, 'semantic'): self.result()

    def test_loaded_config_semantics_allow_byte_changes_but_no_enabled_drift(self):
        config = {'lod': {'enabled': False, 'preset': 'NEAR', 'near': 32, 'far': 96, 'transition': 16, 'debug': False},
                  'terrain': {'enabled': False, 'vanillaChunkCap': 16, 'warmSourceConfirmed': False, 'warmupSeconds': 30},
                  'rendering': {'optimizeShadowCulling': False, 'separateShadowVisibilityLists': False}}
        for report in self.reports:
            self.set_state(report, 'loaded_semantic_config', copy.deepcopy(config))
        self.set_state(self.reports[1], 'config/shaderlod-client.toml_sha256', '0' * 64)
        self.result()
        config['lod']['enabled'] = True
        self.set_state(self.reports[1], 'loaded_semantic_config', config)
        with self.assertRaises(ValueError): self.result()

    def test_sampling_instrumentation_warmup_duration_and_pacing_cannot_differ(self):
        for mutate in (lambda r: r.update(sampling_policy='every32'),
                       lambda r: r.update(backend='TIMESTAMP'),
                       lambda r: r.update(warmup_seconds=35),
                       lambda r: r['runtime_properties'].update(observer='true'),
                       lambda r: r['request'].update(measurement_seconds=31)):
            saved = copy.deepcopy(self.reports[1]); mutate(self.reports[1])
            with self.assertRaises(ValueError): self.result()
            self.reports[1] = saved
        capture = self.reports[1]['window_state']['capture']
        for boundary in ('start', 'end'): capture[boundary]['state']['pacing']['vsync'] = True
        with self.assertRaisesRegex(ValueError, 'window/pacing'): self.result()

    def test_window_transitions_unknown_and_mixed_evidence_rejected(self):
        for mutate in (lambda r: r['window_state']['capture'].update(transition_counts={'focused': 2}),
                       lambda r: r['window_state']['capture'].update(unavailable_observations=1),
                       lambda r: r.pop('window_state')):
            saved = copy.deepcopy(self.reports[1]); mutate(self.reports[1])
            with self.assertRaises(ValueError): self.result()
            self.reports[1] = saved
        for report in self.reports: report.pop('window_state')
        self.assertTrue(any('not observed' in limit for limit in self.result()['evidence_limits']))

    def test_missing_entire_stack_requires_explicit_selected_stacks(self):
        self.manifest['stacks'] = list(self.lock['stacks'])
        pairs = [(run, report) for run, report in zip(self.manifest['runs'], self.reports) if run['stack'] != 'optimized-culling']
        self.manifest['runs'], self.reports = map(list, zip(*pairs))
        with self.assertRaisesRegex(ValueError, 'Incomplete'): self.result()

    def test_missing_repeat_failed_run_and_extra_or_duplicate_conditions_rejected(self):
        saved = copy.deepcopy(self.manifest)
        for modify in (lambda m: m['runs'].pop(),
                       lambda m: m['runs'][0].update(status='failed'),
                       lambda m: m['runs'].append(copy.deepcopy(m['runs'][0])),
                       lambda m: m.update(status='failed')):
            self.manifest = copy.deepcopy(saved); modify(self.manifest)
            with self.assertRaises(ValueError): self.result()

    def test_duplicate_stale_uuid_and_timestamp_failures_are_rejected(self):
        saved = copy.deepcopy(self.manifest)
        for name, value in (('run_id', 'invalid'), ('run_id', self.manifest['runs'][0]['request']['run_id']), ('block', 2)):
            self.manifest = copy.deepcopy(saved)
            self.manifest['runs'][1]['request'][name] = value
            with self.assertRaises(ValueError): self.result()
        self.manifest = saved
        self.reports[1]['measurement_started_at_utc'] = self.reports[0]['measurement_started_at_utc']
        with self.assertRaisesRegex(ValueError, 'Stale'): self.result()

    def test_summary_cannot_supply_stale_or_edited_metrics(self):
        self.save()
        path = self.root / self.manifest['runs'][0]['summary']
        summary = json.loads(path.read_text()); summary['render_fps'] = 999
        path.write_text(json.dumps(summary))
        with self.assertRaisesRegex(ValueError, 'Summary/report'): analyze(self.path, self.lock_path)

    def test_quality_change_between_blocks_rejected_even_if_each_pair_matches(self):
        for report in self.reports[3:]: self.set_state(report, 'effective_chunks', 16)
        with self.assertRaisesRegex(ValueError, 'semantic'): self.result()

    def test_fixed_shader_wetness_requires_actual_supplier_observations(self):
        self.reports[1]['shader_temporal_inputs_end']['wetness']['suppliers'][0]['appliedMax'] = .2
        with self.assertRaisesRegex(ValueError, 'wetness'): self.result()

    def test_lock_identity_required(self):
        self.manifest.pop('performance_mods_lock_sha256')
        with self.assertRaisesRegex(ValueError, 'lock fingerprint'): self.result()

    def test_reported_source_save_identity_required(self):
        self.reports[1]['workload-manifest.json']['source_world_sha256'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'source save'): self.result()

    def test_overlapping_jvm_launches_rejected(self):
        self.manifest['runs'][0]['finished_at_utc'] = self.manifest['runs'][1]['finished_at_utc']
        with self.assertRaisesRegex(ValueError, 'Overlapping JVM'): self.result()

    def test_consistent_changed_request_duration_still_rejected_between_stacks(self):
        self.manifest['runs'][1]['request']['measurement_seconds'] = 31
        self.reports[1]['request']['measurement_seconds'] = 31
        with self.assertRaisesRegex(ValueError, 'request policy'): self.result()

    def test_base_same_version_different_nested_library_bytes_rejected(self):
        for report in self.reports:
            report['loaded_mods']['fabric_api_base'] = '0.4.42+d1308ded19'
            report['loaded_mod_artifacts']['fabric_api_base'] = {'path': '', 'sha256': '3' * 64}
        self.reports[2]['loaded_mod_artifacts']['fabric_api_base']['sha256'] = '4' * 64
        with self.assertRaisesRegex(ValueError, 'base runtime loaded_artifacts'): self.result()

    def qualified_fixture(self):
        registry = SCRIPTS.parent / 'dev/runtime-artifact-equivalences.json'
        entry = load_equivalences(registry)['entry']
        for index, report in enumerate(self.reports):
            report['loaded_mods'].update(EQUIVALENCE_RUNTIME)
            for names, artifact in report['loaded_mod_artifacts'].items():
                name = names.split(',')[0]
                if name in EQUIVALENCE_PARENTS: artifact['sha256'] = EQUIVALENCE_PARENTS[name]
            report['loaded_mods']['fabric_api_base'] = entry['mod_version']
            report['loaded_mod_artifacts']['fabric_api_base'] = {'path': '', 'sha256': entry['raw_sha256'][index % 2]}
        return registry

    def test_qualified_equivalence_is_explicit_and_reports_every_raw_variant(self):
        registry = self.qualified_fixture()
        with self.assertRaisesRegex(ValueError, 'base runtime loaded_artifacts'): self.result()
        result = analyze(self.path, self.lock_path, registry)
        self.assertEqual(result['equivalence_registry']['path'], str(registry.resolve()))
        self.assertIn('qualified', result['artifact_comparison_policy'])
        checks = result['stack_comparisons'][0]['checks'][0]
        a, b = checks['baseline_artifact_equivalences'][0], checks['candidate_artifact_equivalences'][0]
        self.assertNotEqual(a['raw_sha256'], b['raw_sha256'])
        self.assertEqual(a['equivalence_id'], b['equivalence_id'])
        self.assertEqual(a['variant']['parent_mod_id'], 'iris')
        self.assertEqual(b['variant']['parent_mod_id'], 'sodium')
        self.assertIn('unverified', a['qualification'])

    def test_equivalence_rejects_unknown_runtime_parent_version_or_hash(self):
        registry = self.qualified_fixture()
        for mutate in (
                lambda r: r['loaded_mods'].update(neoforge='21.1.254'),
                lambda r: r['loaded_mod_artifacts']['iris'].update(sha256='0' * 64),
                lambda r: r['loaded_mod_artifacts']['fabric_api_base'].update(sha256='0' * 64),
                lambda r: r['loaded_mods'].update(fabric_api_base='different')):
            saved = copy.deepcopy(self.reports[1]); mutate(self.reports[1]); self.save()
            with self.assertRaisesRegex(ValueError, '[Ee]quivalence'): analyze(self.path, self.lock_path, registry)
            self.reports[1] = saved

    def test_unreviewed_registry_scope_or_payload_proof_rejected(self):
        registry = self.qualified_fixture()
        original = json.loads(registry.read_text())
        altered = self.root / 'altered-equivalences.json'
        for mutate in (
                lambda e: e.update(mod_id='other'),
                lambda e: e.update(raw_sha256=['0' * 64, '1' * 64]),
                lambda e: e['required_loaded_mods'].update(neoforge='21.1.254'),
                lambda e: e['required_loaded_mod_artifacts_sha256'].update(iris='0' * 64),
                lambda e: e['proof'].update(no_additional_payload_changes=False),
                lambda e: e['proof']['classes'].update(sha256='0' * 64)):
            document = copy.deepcopy(original); mutate(document['equivalences'][0])
            altered.write_text(json.dumps(document))
            with self.assertRaises(ValueError): load_equivalences(altered)


if __name__ == '__main__':
    unittest.main()

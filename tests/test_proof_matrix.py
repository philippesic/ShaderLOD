"""Independent-run planning, timing pairing, and contamination guards."""
import copy
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parents[1] / 'scripts'
sys.path.insert(0, str(SCRIPTS))
from run_proof_matrix import balanced_plan, validate_report, profile_inputs, FATAL_LOG, canonical_options_hash, read_option_properties, declared_defaults, normalize_requested_options
from analyze_proof_matrix import paired_effects, check_pair, run_metrics, analyze
from summarize_workload import stream


class MatrixTests(unittest.TestCase):
    def test_complete_even_design_balances_positions(self):
        plan = balanced_plan(['high', 'adaptive'], ['minimal', 'optimized'], 4)
        for block in range(1, 5):
            rows = [r for r in plan if r['block'] == block]
            self.assertEqual(len({(r['case'], r['stack']) for r in rows}), 4)
        for case in ('high', 'adaptive'):
            for stack in ('minimal', 'optimized'):
                self.assertEqual({r['position'] for r in plan if r['case'] == case and r['stack'] == stack}, {1, 2, 3, 4})
        with self.assertRaises(ValueError): balanced_plan(['a', 'a'], ['minimal'], 3)

    def test_odd_design_each_repeat_contains_every_condition(self):
        plan = balanced_plan(['a', 'b', 'c'], ['minimal'], 5)
        self.assertEqual(len(plan), 15)
        for block in range(1, 6): self.assertEqual({r['case'] for r in plan if r['block'] == block}, {'a', 'b', 'c'})
        self.assertNotEqual([r['case'] for r in plan[:3]], [r['case'] for r in plan[3:6]])

    def test_per_frame_invocations_group_but_not_independent_runs(self):
        result = stream([{'scope': 'x', 'frameId': 1, 'milliseconds': 2}, {'scope': 'x', 'frameId': 1, 'milliseconds': 3}], 'milliseconds')
        self.assertEqual(result['x']['per_sampled_frame']['mean_ms'], 5)
        pairs = [({'frame_ms': 10}, {'frame_ms': 8}), ({'frame_ms': 12}, {'frame_ms': 9})]
        estimate = paired_effects(pairs)['frame_ms']['percent_time_reduction']
        self.assertEqual(estimate['independent_jvm_runs'], 2)
        self.assertEqual(estimate['median'], 22.5)

    def fixture(self):
        state = {'shaders_in_use': True, 'fallback': False, 'framebuffer_width': 1488, 'framebuffer_height': 836, 'shader_pack': 'photon.zip'}
        request = {'run_id': 'fresh', 'case': 'forest-high', 'task': 'runClientLodOn', 'include_dh': True, 'measurement_seconds': 30, 'gpu_queries': True}
        inputs = {'shader_pack': 'photon.zip', 'shader_sha256': 'pack', 'shader_options_sha256': 'options', 'control_sha256': 'control', 'requested_shader_options': {'QUALITY': 'high'}, 'shader_option_defaults': {'QUALITY': 'high'}}
        report = {'status': 'complete', 'request': {'run_id': 'fresh'}, 'modPresent': True, 'dhPresent': True,
                  'actual_shader_sha256': 'pack', 'actual_shader_options_sha256': 'options', 'effective_shader_options': {'QUALITY': 'high'}, 'effective_shader_options_sha256': canonical_options_hash({'QUALITY': 'high'}), 'control_sha256': 'control',
                  'input_state_start': state, 'input_state_end': state.copy(), 'elapsed_seconds': 30, 'render_frames': 100,
                  'loaded_mods': {'iris': '1'}, 'cpu_samples': [1], 'gpu_samples': [{'scope': 'game-render'}]}
        return report, request, inputs

    def test_fresh_report_and_each_contamination_guard(self):
        report, request, inputs = self.fixture()
        validate_report(report, request, inputs, 0)
        mutations = [lambda r: r['request'].update(run_id='stale'), lambda r: r.update(status='warming'),
                     lambda r: r.update(actual_shader_sha256='different'), lambda r: r.update(effective_shader_options_sha256='different'),
                     lambda r: r.update(dhPresent=False), lambda r: r.update(modPresent=False),
                     lambda r: r['input_state_end'].update(framebuffer_width=900), lambda r: r.update(gpu_samples=[]),
                     lambda r: r.update(cpu_samples=[]), lambda r: r.update(elapsed_seconds=2)]
        for mutation in mutations:
            altered = copy.deepcopy(report); mutation(altered)
            with self.assertRaises(ValueError): validate_report(altered, request, inputs, 0)
        fallback = copy.deepcopy(report)
        fallback['input_state_start']['fallback'] = fallback['input_state_end']['fallback'] = True
        with self.assertRaises(ValueError): validate_report(fallback, request, inputs, 0)

    def test_shader_error_guard_ignores_known_resource_warnings(self):
        self.assertFalse(FATAL_LOG.search('Failed to parse an ResourceLocation in item.properties'))
        self.assertTrue(FATAL_LOG.search('Shader compilation failed'))
        self.assertTrue(FATAL_LOG.search('fallback=true'))

    def test_iris_timestamp_and_default_removal_are_semantic_noops(self):
        defaults = declared_defaults('#define QUALITY high // [high low]\n//#define FOG\nconst int shadowMapResolution = 2048;\n')
        requested = read_option_properties('QUALITY=high\nFOG=false\nshadowMapResolution=2048\n')
        normalized = read_option_properties('#Sun Oct 04 timestamp\n')
        self.assertEqual(canonical_options_hash(normalize_requested_options(defaults, requested)),
                         canonical_options_hash(normalize_requested_options(defaults, normalized)))
        changed = read_option_properties('QUALITY=low\n')
        self.assertNotEqual(normalize_requested_options(defaults, requested), normalize_requested_options(defaults, changed))
        with self.assertRaises(ValueError): normalize_requested_options(defaults, {'UNKNOWN': 'true'})
        report, request, inputs = self.fixture()
        report['actual_shader_options_sha256'] = 'timestamp-and-default-normalized'
        validate_report(report, request, inputs, 0)
        report['effective_shader_options'] = {'QUALITY': 'low'}
        report['effective_shader_options_sha256'] = canonical_options_hash(report['effective_shader_options'])
        with self.assertRaises(ValueError): validate_report(report, request, inputs, 0)
        report, request, inputs = self.fixture()
        inputs['requested_shader_options']['UNUSED'] = 'false'
        with self.assertRaises(ValueError): validate_report(report, request, inputs, 0)
        inputs['requested_shader_options'].pop('UNUSED')
        inputs['requested_shader_options']['INFO'] = '2'; inputs['shader_option_defaults']['INFO'] = '2'
        validate_report(report, request, inputs, 0)

    def test_missing_profile_guard(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError): profile_inputs(Path(directory))

    def pair_fixture(self):
        report = {'input_state_end': {'framebuffer_width': 1488, 'effective_chunks': 24}, 'loaded_mods': {'iris': '1'},
                  'dhPresent': True, 'sampling_policy': 'frame', 'actual_shader_options': 'QUALITY=high\n', 'effective_shader_options': {'QUALITY': 'high'}, 'actual_dh_configuration': 'same'}
        manifest = {'source_inputs': {'forest-high': {'source_world_sha256': 'world'}, 'forest-adaptive': {'source_world_sha256': 'world'}}}
        baseline, candidate = {'case': 'forest-high'}, {'case': 'forest-adaptive'}
        return baseline, candidate, report, copy.deepcopy(report), manifest

    def test_pair_camera_stack_and_upstream_quality(self):
        a, b, ra, rb, manifest = self.pair_fixture()
        rb['loaded_mods']['shaderlod'] = '1'
        self.assertEqual(check_pair(a, b, ra, rb, manifest), {})
        rb['input_state_end']['x'] = 99
        with self.assertRaises(ValueError): check_pair(a, b, ra, rb, manifest)
        rb = copy.deepcopy(ra); rb['effective_shader_options'] = {'QUALITY': 'low'}
        with self.assertRaises(ValueError): check_pair(a, b, ra, rb, manifest)
        rb = copy.deepcopy(ra); rb['input_state_end']['effective_chunks'] = 16
        self.assertEqual(check_pair(a, b, ra, rb, manifest)['effective_chunks'], [24, 16])

    def test_error_matrix_not_silently_ranked(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'matrix-manifest.json'
            path.write_text(json.dumps({'schema': 2, 'status': 'failed'}))
            with self.assertRaises(ValueError): analyze(path)


if __name__ == '__main__': unittest.main()

"""Independent-run planning, timing pairing, and contamination guards."""
import copy
import importlib.util
import json
import os
import subprocess
import zipfile
from pathlib import Path
import sys
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parents[1] / 'scripts'
sys.path.insert(0, str(SCRIPTS))
from run_proof_matrix import balanced_plan, validate_report, validate_optional_stack, profile_inputs, FATAL_LOG, canonical_options_hash, read_option_properties, declared_defaults, normalize_requested_options, wetness_value, validate_temporal_inputs, preserve_screenshot, archive_defaults, camera_difference, CASES, CLOUD_CASE_OPTIONS
from analyze_proof_matrix import paired_effects, check_pair, run_metrics, analyze
from summarize_workload import stream


class MatrixTests(unittest.TestCase):
    def test_requested_optimizer_must_load_exact_pinned_bytes(self):
        lock = {'stacks': {'minimal': [], 'optimized-culling': ['entityculling']},
                'artifacts': {'entityculling': {'jar_mod_id': 'entityculling',
                    'jar_version': '1.11.2', 'hashes': {'sha256': 'pinned'}}}}
        report = {'runtime_properties': {'shaderlod.dev.performanceStack': 'optimized-culling'},
                  'loaded_mods': {'entityculling': '1.11.2'},
                  'loaded_mod_artifacts': {'entityculling': {'sha256': 'pinned'}}}
        validate_optional_stack(report, 'optimized-culling', lock)
        for mutation in (lambda r: r['loaded_mods'].clear(),
                         lambda r: r['loaded_mods'].update(entityculling='different'),
                         lambda r: r['loaded_mod_artifacts']['entityculling'].update(sha256='different'),
                         lambda r: r['runtime_properties'].update({'shaderlod.dev.performanceStack': 'minimal'})):
            altered = copy.deepcopy(report); mutation(altered)
            with self.assertRaises(ValueError): validate_optional_stack(altered, 'optimized-culling', lock)
        report['runtime_properties']['shaderlod.dev.performanceStack'] = 'minimal'
        with self.assertRaises(ValueError): validate_optional_stack(report, 'minimal', lock)
        report['loaded_mods'].clear()
        validate_optional_stack(report, 'minimal', lock)

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
                  'shadow_culling_active': False, 'loaded_mods': {'iris': '1'}, 'cpu_samples': [1], 'gpu_samples': [{'scope': 'game-render'}]}
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

    def test_fixed_wetness_policy_and_each_observed_supplier(self):
        for value in ('nan', 'inf', '-0.01', '1.01'):
            with self.assertRaises(Exception): wetness_value(value)
        self.assertEqual(wetness_value('0'), 0)
        self.assertEqual(wetness_value('1'), 1)
        supplier = {'supplierReads': 30, 'measurementReads': 10, 'appliedWetness': 1, 'appliedMin': 1, 'appliedMax': 1}
        wetness = {'mode': 'fixed', 'fixedWetness': 1, 'measurementReads': 10, 'suppliers': [supplier]}
        report = {'shader_temporal_inputs_start': {'wetness': copy.deepcopy(wetness)}, 'shader_temporal_inputs_end': {'wetness': copy.deepcopy(wetness)}}
        self.assertEqual(validate_temporal_inputs(report, {'fixed_wetness': 1})['wetness_policy'], 'fixed')
        report['shader_temporal_inputs_end']['wetness']['suppliers'].append(dict(supplier, appliedWetness=.7))
        with self.assertRaises(ValueError): validate_temporal_inputs(report, {'fixed_wetness': 1})
        report['shader_temporal_inputs_end']['wetness']['suppliers'].pop()
        report['shader_temporal_inputs_end']['wetness']['suppliers'][0]['appliedMin'] = None
        with self.assertRaises(ValueError): validate_temporal_inputs(report, {'fixed_wetness': 1})
        dynamic = {'shader_temporal_inputs_start': {'wetness': {'mode': 'dynamic', 'rawWetness': .3}},
                   'shader_temporal_inputs_end': {'wetness': {'mode': 'dynamic', 'rawWetness': .5, 'measurementReads': 10}}}
        self.assertEqual(validate_temporal_inputs(dynamic, {})['end_raw_wetness'], .5)
        with self.assertRaises(ValueError): validate_temporal_inputs(dynamic, {'fixed_wetness': 1})

    def test_screenshots_preserved_and_stale_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); profile = root / 'profile'; output = root / 'output'
            (profile / 'screenshots').mkdir(parents=True); output.mkdir()
            screenshot = profile / 'screenshots/workload-final.png'
            contents = b'\x89PNG\r\n\x1a\nfixture'
            screenshot.write_bytes(contents)
            result = preserve_screenshot(profile, output, 'run', 0)
            self.assertEqual((output / result['screenshot']).read_bytes(), contents)
            with self.assertRaises(ValueError): preserve_screenshot(profile, output, 'stale', screenshot.stat().st_mtime_ns + 1)
            screenshot.write_text('invalid')
            with self.assertRaises(ValueError): preserve_screenshot(profile, output, 'bad', 0)

    def test_archive_default_controls_outside_settings(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'pack.zip'
            with zipfile.ZipFile(path, 'w') as archive:
                archive.writestr('shaders/settings.glsl', '#define QUALITY high // [high low]\n')
                archive.writestr('shaders/pass.fsh', '#define FRAGMENT_SHADER\n#define AMBIGUOUS one\n')
                archive.writestr('shaders/pass.vsh', '#define AMBIGUOUS two\n')
            with zipfile.ZipFile(path) as archive:
                defaults = archive_defaults(archive)
            self.assertEqual(defaults['FRAGMENT_SHADER'], 'true')
            self.assertNotIn('AMBIGUOUS', defaults)

    def test_makeup_option_layout_and_unknown_pack_rejection(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'pack.zip'
            with zipfile.ZipFile(path, 'w') as archive:
                archive.writestr('shaders/lib/config.glsl', '#define REFLECTION_SLIDER 2 // [0 1 2]\n')
                archive.writestr('shaders/lib/helper.glsl', '#define SHADERLOD_MAKEUP_WATER_SSR 0 // [0 1]\n')
            with zipfile.ZipFile(path) as archive:
                defaults = archive_defaults(archive)
            self.assertEqual(normalize_requested_options(defaults, {'SHADERLOD_MAKEUP_WATER_SSR': '1'})['REFLECTION_SLIDER'], '2')
            with zipfile.ZipFile(path, 'w') as archive:
                archive.writestr('shaders/unknown.glsl', '#define REFLECTION_SLIDER 2\n')
            with zipfile.ZipFile(path) as archive:
                with self.assertRaises(ValueError): archive_defaults(archive)

    def test_java_wetness_supplier_preservation_and_clamp(self):
        root = SCRIPTS.parent
        iris = root / 'dev/runtime/iris-neoforge-1.8.12+mc1.21.1.jar'
        if not iris.exists(): self.skipTest('Pinned Iris jar unavailable for standalone tracker test')
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            java = path / 'TrackerCheck.java'
            java.write_text(r'''import com.pp.shaderlod.dev.timing.ShaderInputTracker;
import java.util.concurrent.atomic.AtomicInteger;
public class TrackerCheck {
 public static void main(String[] args) {
  if (ShaderInputTracker.parseFixedWetness(null) != null) throw new AssertionError();
  for (String value : new String[]{"NaN", "Infinity", "-0.1", "1.1"}) {
   try { ShaderInputTracker.parseFixedWetness(value); throw new AssertionError(value); }
   catch (IllegalArgumentException expected) {}
  }
  if (ShaderInputTracker.applyWetness(.2f, 2f) != 1 || ShaderInputTracker.applyWetness(.2f, -1f) != 0) throw new AssertionError("clamp");
  AtomicInteger calls = new AtomicInteger();
  var first = ShaderInputTracker.wrapWetness(() -> { calls.incrementAndGet(); return .2f; }, "first");
  var second = ShaderInputTracker.wrapWetness(() -> { calls.incrementAndGet(); return .7f; }, "second");
  ShaderInputTracker.beginMeasurement();
  float fixed = args.length == 0 ? -1 : Float.parseFloat(args[0]);
  if (first.getAsFloat() != (fixed < 0 ? .2f : fixed) || second.getAsFloat() != (fixed < 0 ? .7f : fixed)) throw new AssertionError("publish");
  var snapshot = ShaderInputTracker.snapshot();
  if (calls.get() != 2 || snapshot.suppliers().size() != 2 || snapshot.measurementReads() != 2) throw new AssertionError("supplier count");
  if (snapshot.suppliers().get(0).rawWetness() != .2f || snapshot.suppliers().get(1).rawWetness() != .7f) throw new AssertionError("raw per supplier");
  if (fixed >= 0 && (snapshot.appliedMin() != fixed || snapshot.appliedMax() != fixed)) throw new AssertionError("all fixed");
  ShaderInputTracker.snapshot();
  if (calls.get() != 2) throw new AssertionError("snapshot reevaluated smoother");
 }
}''')
            source = root / 'src/devClient/java/com/pp/shaderlod/dev/timing/ShaderInputTracker.java'
            subprocess.run(['javac', '--release', '21', '-cp', str(iris), '-d', str(path), str(source), str(java)], check=True, capture_output=True)
            classpath = os.pathsep.join((str(path), str(iris)))
            subprocess.run(['java', '-cp', classpath, 'TrackerCheck'], check=True, capture_output=True)
            subprocess.run(['java', '-Dshaderlod.dev.fixedWetness=1', '-cp', classpath, 'TrackerCheck', '1'], check=True, capture_output=True)

    def test_culling_arm_requires_present_enabled_configured_actual_active(self):
        report, request, inputs = self.fixture()
        request['case'] = 'water-culling'
        inputs['shadow_culling_configured'] = True
        report['shadow_culling_active'] = True
        for boundary in ('input_state_start', 'input_state_end'): report[boundary]['lod_settings'] = {'enabled': True}
        validate_report(report, request, inputs, 0)
        for mutation in (lambda r: r.update(shadow_culling_active=False), lambda r: r.pop('shadow_culling_active'),
                         lambda r: r.update(shadow_culling_active=1), lambda r: r.update(modPresent=False)):
            altered = copy.deepcopy(report); mutation(altered)
            with self.assertRaises(ValueError): validate_report(altered, request, inputs, 0)
        for boundary in ('input_state_start', 'input_state_end'): report[boundary]['lod_settings']['enabled'] = False
        with self.assertRaises(ValueError): validate_report(report, request, inputs, 0)
        for boundary in ('input_state_start', 'input_state_end'): report[boundary]['lod_settings']['enabled'] = True
        inputs['shadow_culling_configured'] = False
        with self.assertRaises(ValueError): validate_report(report, request, inputs, 0)

    def test_ordinary_arm_culling_false_unless_explicitly_configured(self):
        report, request, inputs = self.fixture()
        validate_report(report, request, inputs, 0)
        report['shadow_culling_active'] = True
        with self.assertRaises(ValueError): validate_report(report, request, inputs, 0)
        inputs['shadow_culling_configured'] = True
        for boundary in ('input_state_start', 'input_state_end'): report[boundary]['lod_settings'] = {'enabled': True}
        validate_report(report, request, inputs, 0)
        report.pop('shadow_culling_active')
        with self.assertRaises(ValueError): validate_report(report, request, inputs, 0)

    def test_cloud_arm_names_pin_effect_and_mode_options(self):
        for case, options in CLOUD_CASE_OPTIONS.items():
            report, request, inputs = self.fixture()
            request['case'] = case
            inputs['requested_shader_options'] = options
            report['effective_shader_options'] = copy.deepcopy(options)
            report['effective_shader_options_sha256'] = canonical_options_hash(options)
            for boundary in ('input_state_start', 'input_state_end'): report[boundary]['lod_settings'] = {'enabled': True}
            validate_report(report, request, inputs, 0)
            report['effective_shader_options']['SHADERLOD_CLOUD_MODE'] = '99'
            report['effective_shader_options_sha256'] = canonical_options_hash(report['effective_shader_options'])
            # Even a misprepared requested map cannot make a mislabeled mode valid.
            inputs['requested_shader_options'] = copy.deepcopy(report['effective_shader_options'])
            with self.assertRaises(ValueError): validate_report(report, request, inputs, 0)
            self.assertEqual(CASES[case][1], 'runClientLodOn')

    def test_missing_profile_guard(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError): profile_inputs(Path(directory))

    def test_circular_yaw_and_strict_finite_camera_guard(self):
        self.assertEqual(camera_difference(-180, 180, circular=True), 0)
        self.assertEqual(camera_difference(-180, 540, circular=True), 0)
        self.assertEqual(camera_difference(-179, 180, circular=True), 1)
        self.assertEqual(camera_difference(-180, 180), 360)  # XYZ and pitch stay linear.
        for invalid in (None, True, False, '180', float('nan'), float('inf'), -float('inf')):
            with self.assertRaises(ValueError): camera_difference(invalid, 180, circular=True)
            with self.assertRaises(ValueError): camera_difference(-180, invalid, circular=True)
        report, request, inputs = self.fixture()
        inputs['control'] = {'yaw': 540, 'pitch': 3}
        for boundary in ('input_state_start', 'input_state_end'): report[boundary].update(yaw=-180, pitch=3)
        validate_report(report, request, inputs, 0)
        for boundary in ('input_state_start', 'input_state_end'): report[boundary]['yaw'] = -179
        with self.assertRaises(ValueError): validate_report(report, request, inputs, 0)
        for invalid in (None, True, float('nan'), float('inf')):
            for boundary in ('input_state_start', 'input_state_end'): report[boundary]['yaw'] = invalid
            with self.assertRaises(ValueError): validate_report(report, request, inputs, 0)

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

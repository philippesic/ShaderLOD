"""Pure effect planning/options checks; never copy real worlds or launch a client."""
import argparse
import copy
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from prepare_effect_profiles import ARMS, EFFECTS, effect_flags, options_for_arm, plan_effects
from run_proof_matrix import CASES, validate_report, canonical_options_hash
from analyze_proof_matrix import check_pair


class EffectTests(unittest.TestCase):
    def test_each_effect_arm_only_changes_declared_lod_switches(self):
        common = {'ENVIRONMENT_REFLECTIONS': 'true', 'SKY_REFLECTIONS': 'true', 'POM': 'false'}
        switches = list(EFFECTS.values()) + ['SHADERLOD_WATER_ANALYTIC', 'SHADERLOD_CACHED_SKY', 'SHADERLOD_PRESERVE_LIGHTING']
        declared = {name: ['true', 'false'] for name in switches}
        for arm in ARMS:
            options = options_for_arm(common, arm, declared)
            self.assertTrue(all(options[key] == value for key, value in common.items()))
            if arm == 'high': self.assertEqual(options, common); continue
            self.assertEqual(options['SHADERLOD_WATER_ANALYTIC'], 'false')
            self.assertEqual(options['SHADERLOD_CACHED_SKY'], 'false')
            self.assertEqual(options['SHADERLOD_PRESERVE_LIGHTING'], 'true')
            true_effects = {name for name in EFFECTS if options[EFFECTS[name]] == 'true'}
            expected = set(EFFECTS) if arm in ('adaptive', 'patched-off') else {arm[:-5]} if arm.endswith('-only') else set()
            self.assertEqual(true_effects, expected)
        with self.assertRaises(ValueError): effect_flags('invented')
        with self.assertRaises(ValueError): options_for_arm(common, 'alloff', {})

    def test_fresh_plan_exact_hash_and_same_runner_paths(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = argparse.Namespace(source_proof_root=root / 'source', output=root / 'output', scenes=['coastalwater'],
                                      archive_path=None, backup_existing=False)
            planned = plan_effects(args)
            self.assertEqual(planned['archive_sha256'], 'ab1b6d859829fe3e0925b69cdd3759369c44bc876cc97de77d9878ed27b8658d')
            self.assertFalse(planned['analytic_enabled'])
            self.assertEqual(planned['arms'], list(ARMS))
            self.assertFalse((root / 'output').exists())
            for arm in ARMS:
                profile, task = CASES['water-' + arm]
                self.assertEqual(profile, 'coastalwater/' + arm)
                self.assertEqual(task, 'runClientLodOff' if arm == 'high' else 'runClientLodOn')
            (root / 'output').mkdir()
            with self.assertRaises(ValueError): plan_effects(args)

    def fixture(self, case):
        state = {'shaders_in_use': True, 'fallback': False, 'framebuffer_width': 1488, 'framebuffer_height': 836,
                 'shader_pack': 'pack.zip', 'lod_settings': {'enabled': True}}
        request = {'run_id': 'fresh', 'case': case, 'task': 'runClientLodOn', 'include_dh': False, 'measurement_seconds': 15, 'gpu_queries': True}
        options = {'SHADERLOD_CLOUDS': 'false', 'ENVIRONMENT_REFLECTIONS': 'true'}
        inputs = {'shader_pack': 'pack.zip', 'shader_sha256': 'pack', 'control_sha256': 'control',
                  'requested_shader_options': options, 'shader_option_defaults': options}
        report = {'status': 'complete', 'request': {'run_id': 'fresh'}, 'modPresent': True, 'dhPresent': False,
                  'actual_shader_sha256': 'pack', 'control_sha256': 'control', 'effective_shader_options': copy.deepcopy(options),
                  'effective_shader_options_sha256': canonical_options_hash(options), 'input_state_start': state,
                  'input_state_end': copy.deepcopy(state), 'elapsed_seconds': 15, 'render_frames': 100,
                  'loaded_mods': {'iris': '1'}, 'cpu_samples': [1], 'gpu_samples': [{'scope': 'game-render'}]}
        return report, request, inputs

    def test_effect_arms_require_global_enabled_and_actual_options(self):
        for arm in ('alloff', 'cloud-only', 'water-only', 'ssr-only', 'fog-only', 'materials-only'):
            report, request, inputs = self.fixture('water-' + arm)
            validate_report(report, request, inputs, 0)
            for boundary in ('input_state_start', 'input_state_end'): report[boundary]['lod_settings']['enabled'] = False
            with self.assertRaises(ValueError): validate_report(report, request, inputs, 0)
        report, request, inputs = self.fixture('water-alloff')
        report['effective_shader_options']['SHADERLOD_CLOUDS'] = 'true'
        report['effective_shader_options_sha256'] = canonical_options_hash(report['effective_shader_options'])
        with self.assertRaises(ValueError): validate_report(report, request, inputs, 0)

    def test_pair_rejects_quality_changes_but_describes_effect_switches(self):
        baseline = {'case': 'water-high'}
        original = {'input_state_end': {'framebuffer_width': 1488}, 'loaded_mods': {'iris': '1'}, 'dhPresent': False,
                    'sampling_policy': 'frame', 'effective_shader_options': {'QUALITY': 'high'}, 'actual_dh_configuration': 'same'}
        manifest = {'source_inputs': {'water-high': {'source_world_sha256': 'world'}}}
        for arm in ('alloff', 'cloud-only', 'water-only', 'ssr-only', 'fog-only', 'materials-only'):
            case = 'water-' + arm
            manifest['source_inputs'][case] = {'source_world_sha256': 'world'}
            candidate = copy.deepcopy(original)
            candidate['effective_shader_options'].update(effect_flags(arm))
            changes = check_pair(baseline, {'case': case}, original, candidate, manifest)
            self.assertIn('shaderlod_pack_options', changes)
            candidate['effective_shader_options']['QUALITY'] = 'low'
            with self.assertRaises(ValueError): check_pair(baseline, {'case': case}, original, candidate, manifest)


if __name__ == '__main__': unittest.main()

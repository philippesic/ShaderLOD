"""Keep one exact private intervention separate from upstream quality changes."""
import copy
import json
from pathlib import Path
import sys
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from analyze_proof_matrix import check_pair, ORIGINAL_PHOTON_SHA256, REDUCED_SSR_SHA256, REDUCED_SSR_GATE
from analyze_inprocess_control import ssr_cost_members


class ReducedEffectsAnalysisTests(unittest.TestCase):
    def fixture(self):
        archive = ROOT / 'build/Photon-SSRFarReducedTraceProbe-enabled.zip'
        if not archive.exists(): self.skipTest('Ignored pinned reduced archive absent')
        with zipfile.ZipFile(archive) as z:
            metadata = json.loads(z.read('shaderlod-ssr-far-reduced-probe.json'))
        qualification = dict(qualified=True, archive_sha256=REDUCED_SSR_SHA256,
                             policy='reduced', member_sha256=ssr_cost_members(REDUCED_SSR_SHA256),
                             probe_metadata=metadata)
        preparation = dict(experiment_family='reduced-ssr-all-five-effects', reduced_source_qualification=qualification)
        base = dict(input_state_end={}, dhPresent=False, sampling_policy='fixed', loaded_mods={'iris':'pinned'},
                    effective_shader_options={'QUALITY':'high'}, actual_shader_sha256=ORIGINAL_PHOTON_SHA256,
                    **{'workload-manifest.json': preparation})
        candidate = copy.deepcopy(base)
        candidate['actual_shader_sha256'] = REDUCED_SSR_SHA256
        candidate['effective_shader_options'][REDUCED_SSR_GATE] = 'true'
        manifest = {'source_inputs': {'water-high': {'source_world_sha256':'equal', 'shader_sha256':ORIGINAL_PHOTON_SHA256},
                                     'water-adaptive': {'source_world_sha256':'equal', 'shader_sha256':REDUCED_SSR_SHA256}}}
        return {'case':'water-high'}, {'case':'water-adaptive'}, base, candidate, manifest

    def test_exact_gate_annotated_as_source_intervention(self):
        result = check_pair(*self.fixture())
        self.assertIn('qualified_source_intervention', result)
        self.assertNotIn('upstream_shader_options', result)

    def test_other_native_quality_difference_still_rejected(self):
        args = self.fixture(); args[3]['effective_shader_options']['QUALITY'] = 'low'
        with self.assertRaisesRegex(ValueError, 'upstream quality'): check_pair(*args)
        args = self.fixture(); args[3]['effective_shader_options']['PHOTON_UNKNOWN_PROBE'] = 'true'
        with self.assertRaisesRegex(ValueError, 'upstream quality'): check_pair(*args)

    def test_pin_metadata_family_and_gate_fail_closed(self):
        mutations = [lambda a: a[3].update(actual_shader_sha256='unknown'),
                     lambda a: a[4]['source_inputs']['water-adaptive'].update(shader_sha256='unknown'),
                     lambda a: a[3]['workload-manifest.json'].update(experiment_family='unknown'),
                     lambda a: a[3]['workload-manifest.json']['reduced_source_qualification']['member_sha256'].update(LICENSE='unknown'),
                     lambda a: a[3]['effective_shader_options'].update({REDUCED_SSR_GATE:'false'}),
                     lambda a: a[2]['effective_shader_options'].update({REDUCED_SSR_GATE:'false'})]
        for mutate in mutations:
            args = self.fixture(); mutate(args)
            with self.subTest(mutate=mutate), self.assertRaises(ValueError): check_pair(*args)

    def test_unmatched_scene_still_rejected(self):
        args = self.fixture(); args[3]['input_state_end']['day_time'] = 1
        with self.assertRaisesRegex(ValueError, 'Unmatched actual input'): check_pair(*args)


if __name__ == '__main__': unittest.main()

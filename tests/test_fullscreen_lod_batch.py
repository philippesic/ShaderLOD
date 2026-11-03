"""Bounded pinned-source, copy and actual-fullscreen contract fixtures; no client/build."""
import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import tomllib
import unittest
from unittest import mock
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/'scripts'))
import prepare_fullscreen_lod_batch as batch
import run_proof_matrix as runner
from setup_comparison import WORLD, file_hash, world_fingerprint
from analyze_proof_matrix import qualified_fullscreen_lod_pair


class FullscreenBatchTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve()

    def test_plan_preserves_old_windowed_evidence_and_unknown_actual_dimensions(self):
        plan = batch.plan(self.root/'run-workloads/fresh', root=self.root)
        self.assertIsNone(plan['framebuffer'])
        self.assertIsNone(plan['observed_fullscreen'])
        self.assertEqual(plan['source_evidence_framebuffer'], [2976,1672])
        self.assertEqual(plan['arms'], list(batch.ARMS))
        self.assertEqual(plan['producer_effects_version'], 2)
        self.assertFalse((self.root/'run-workloads/fresh').exists())
        self.assertTrue(all(set(item['target_profiles']) == set(batch.ARMS) for item in plan['scene_operations']))
        with self.assertRaises(ValueError): batch.plan(self.root/'tracked', root=self.root)
        with self.assertRaises(ValueError): batch.feature_flags('universal')

    def test_only_three_explicit_gate_maps_and_additive_case_labels(self):
        for arm in batch.ARMS[1:]:
            flags = batch.feature_flags(arm)
            self.assertEqual(set(flags), set(batch.combined.SWITCHES))
            self.assertEqual(sum(value == 'true' for value in flags.values()), len(batch.ARM_TRACKS[arm]))
        self.assertEqual(batch.feature_flags('high'), {})
        for prefix,scene in [('forest','forest'),('water','coastalwater')]:
            for arm in batch.ARMS[2:]:
                self.assertEqual(runner.CASES[prefix+'-'+arm], (scene+'/'+arm, 'runClientLodOn'))
        self.assertEqual(runner.CASES['water-high'], ('coastalwater/high','runClientLodOff'))
        self.assertEqual(runner.CASES['forest-adaptive'], ('forest/adaptive','runClientLodOn'))


    def fullscreen_report(self):
        pacing = dict(pause_on_lost_focus=False, fullscreen=True, configured_framerate_limit=260, vsync=False)
        state = dict(pacing=pacing, framebuffer_width=3456, framebuffer_height=2234, window_width=1728, window_height=1117)
        counts = lambda yes: dict(true_observations=3 if yes else 0, false_observations=0 if yes else 3, unknown_observations=0)
        capture = dict(observations=3, available_observations=3, unavailable_observations=0,
            focused=counts(True), visible=counts(True), iconified=counts(False), transition_counts={},
            start={'state':state}, end={'state':copy.deepcopy(state)}, framebuffer_size_counts={'3456x2234':3}, window_size_counts={'1728x1117':3})
        properties = {'shaderlod.dev.'+name:'false' for name in ('cloudSamplerProbe','dhDrawEvidence','waterSceneSurvey','shadowObserve','visibilityLists','shadowCoherenceOracle')}
        properties['shaderlod.dev.cpuLayerTiming'] = 'false'
        return dict(window_state={'capture':capture}, input_state_end=dict(framebuffer_width=3456,framebuffer_height=2234),runtime_properties=properties)

    def test_discovered_dimensions_and_each_contamination_guard(self):
        report = self.fullscreen_report()
        reference = runner.validate_fullscreen_report(report, {})
        self.assertEqual(reference['framebuffer'], [3456,2234])
        self.assertEqual(reference['window'], [1728,1117])
        self.assertTrue(reference['fullscreen'] and reference['validated'])
        request = dict(expected_framebuffer=[3456,2234], expected_window=[1728,1117], expected_focus='true')
        mutations = [lambda r:r['window_state']['capture']['end']['state']['pacing'].update(fullscreen=False),
                     lambda r:r['window_state']['capture']['transition_counts'].update(focused=1),
                     lambda r:r['window_state']['capture']['focused'].update(true_observations=2,false_observations=1),
                     lambda r:r['runtime_properties'].update({'shaderlod.dev.cloudSamplerProbe':'true'}),
                     lambda r:r['runtime_properties'].update({'shaderlod.dev.cpuLayerTiming':'true'}),
                     lambda r:r['input_state_end'].update(framebuffer_width=2976),
                     lambda r:r['window_state']['capture']['framebuffer_size_counts'].update({'2976x1672':1})]
        for mutation in mutations:
            altered = copy.deepcopy(report); mutation(altered)
            with self.assertRaises(ValueError): runner.validate_fullscreen_report(altered, request)
        with self.assertRaises(ValueError): runner.validate_fullscreen_report(report, request|{'expected_window':[1700,1000]})
        with self.assertRaises(ValueError): runner.validate_fullscreen_report(report, request|{'expected_focus':'false'})

    def test_copy_fixture_expands_controls_without_touching_source_or_production_pins(self):
        source = self.root/'canonical/coastalwater/high'
        world = source/'saves'/WORLD
        world.mkdir(parents=True); (world/'level.dat').write_bytes(b'stopped fixture world')
        for directory in ('config','mods','logs','shaderpacks'): (source/directory).mkdir()
        (source/'logs/old.log').write_text('retained source log')
        (source/'options.txt').write_text('renderDistance:24\n')
        (source/'config/iris.properties').write_text('shaderPack=original.zip\nenableShaders=true\n')
        (source/'config/shaderlod-client.toml').write_text(batch.copies.fresh_config('high'))
        original = self.root/'original.zip'; original.write_bytes(b'fixture original archive')
        private = self.root/'build'/batch.ARCHIVE_NAME; private.parent.mkdir(); private.write_bytes(b'fixture combined archive')
        runtime = self.root/'runtime.json'; runtime.write_text(json.dumps({'artifacts':{'distanthorizons':{'filename':'DH.jar'}}}))
        fingerprint = world_fingerprint(world)
        record = dict(scene='coastalwater',profile=source,world=world,manifest={'fixture':True},
            control=batch.copies.POSES['coastalwater']|dict(preset='PERFORMANCE',debug=False,weather='clear'),removed_control_overrides={},
            original=dict(path=str(original),sha256=file_hash(original)),probe=dict(path=str(private),sha256=file_hash(private)),
            qualification=dict(manifest_path=str(self.root/batch.combined.MANIFEST),manifest_sha256=batch.MANIFEST_SHA256),
            native_options={'UPSTREAM':'unchanged'},patched_options={'UPSTREAM':'unchanged'}|batch.feature_flags('patched-off'),
            option_evidence={'fixture':True},source_files_sha256={str(source/'options.txt'):file_hash(source/'options.txt')},
            provenance_files={'shaderlod-client-original.toml':source/'config/shaderlod-client.toml'})
        output = self.root/'run-workloads/new'
        with mock.patch.object(batch.copies,'require_exclusive_runtime'), mock.patch.object(batch,'inspect_scene',return_value=record), mock.patch.object(batch,'compiled_inputs',return_value={'fixture':'frozen'}):
            state = batch.prepare(output,scenes=('coastalwater',),sources={'coastalwater':source},probe=private,root=self.root,runtime_lock=runtime)
        self.assertEqual(state['status'], 'complete')
        manifest = json.loads((output/'coastalwater/workload-manifest.json').read_text())
        self.assertIsNone(manifest['expected_framebuffer'])
        self.assertEqual(set(manifest['experiment_arms']),set(batch.ARMS))
        self.assertFalse((output/'coastalwater/adaptive').exists())
        for arm in batch.ARMS:
            profile = output/'coastalwater'/arm
            self.assertEqual(world_fingerprint(profile/'saves'/WORLD),fingerprint)
            self.assertFalse((profile/'logs').exists())
            self.assertEqual(manifest['experiment_arms'][arm]['private_tracks'],list(batch.ARM_TRACKS[arm]))
            self.assertEqual(manifest['experiment_arms'][arm]['global_enabled'], arm not in ('high','patched-off'))
            self.assertEqual(manifest['experiment_arms'][arm]['runtime_blocked'],arm in batch.KNOWN_RUNTIME_BLOCKED)
            effects = tomllib.loads((profile/'config/shaderlod-effects-client.toml').read_text())['effects']
            self.assertEqual(effects['volumetrics']['mode'],'ADAPTIVE')
            self.assertTrue(all(value['preserveSalientLighting'] for value in effects.values()))
        self.assertEqual(world_fingerprint(world),fingerprint)
        self.assertTrue((source/'logs/old.log').exists())

    def test_unprepared_new_private_family_is_not_an_analysis_exemption(self):
        with self.assertRaisesRegex(ValueError,'explicit six-arm'):
            qualified_fullscreen_lod_pair({'case':'water-high'}, {'case':'water-combined'}, {}, {}, {})

    def test_known_driver_failure_blocks_timing_but_not_owned_untimed_smoke(self):
        base = self.root/'coastalwater'; base.mkdir()
        profile = base/'combined'
        (base/'workload-manifest.json').write_text(json.dumps(dict(experiment_family=batch.EXPERIMENT,
            experiment_arms={'combined':{'runtime_blocked':True}})))
        runner.validate_fullscreen_batch_readiness(profile, False)
        with self.assertRaisesRegex(ValueError,'blocked by retained driver failure'):
            runner.validate_fullscreen_batch_readiness(profile, True)



if __name__ == '__main__': unittest.main()

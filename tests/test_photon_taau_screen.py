"""Bounded source/preparation checks; no client, build, or large save copy."""
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import prepare_photon_taau_screen as prep
from run_proof_matrix import canonical_options_hash, normalize_requested_options


class PhotonTaauScreenTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve()

    def test_plan_is_pure_and_preserves_existing_source_output(self):
        output = self.root / 'run-workloads/fresh'
        result = prep.plan(output, self.root / 'source/high', self.root)
        self.assertFalse(output.exists()); self.assertFalse(result['performanceEvidence'])
        self.assertFalse(result['adapter_gain']); self.assertFalse(result['main_mod_present'])
        self.assertEqual(result['arms'], ['native', 'taau90']); self.assertIn('quality', result['deferred_arm'])
        self.assertEqual(result['expected_framebuffer'], [2976, 1672])
        with self.assertRaisesRegex(ValueError, 'ignored'): prep.plan(self.root / 'tracked', root=self.root)
        output.mkdir(parents=True)
        with self.assertRaisesRegex(ValueError, 'Preserving'): prep.plan(output, root=self.root)
        with self.assertRaisesRegex(ValueError, 'separate'): prep.plan(self.root / 'build/source/new', self.root / 'build/source', self.root)


    def config_fixture(self):
        profile = self.root / 'profile'; (profile / 'config').mkdir(parents=True)
        (profile / 'options.txt').write_text('renderDistance:24\nmaxFps:260\nenableVsync:false\n')
        (profile / 'config/iris.properties').write_text('# timestamp\nenableShaders=true\nshaderPack=photon.zip\n')
        (profile / 'config/extra.toml').write_text('[section]\nkeep=true\n')
        (profile / 'config/sodium-fingerprint.json').write_text(json.dumps(dict(v=1,s='a'*128,u='b'*128,p='c'*128,t=1)))
        return profile

    def test_complete_semantic_config_covers_unknown_and_comments(self):
        profile = self.config_fixture(); first = prep.writable_settings(profile)
        iris = profile / 'config/iris.properties'; iris.write_text('shaderPack=photon.zip\n# new timestamp\nenableShaders=true\n')
        self.assertEqual(first['settings'], prep.writable_settings(profile)['settings'])
        iris.write_text('shaderPack=photon.zip\nenableShaders=false\n')
        self.assertNotEqual(first['settings'], prep.writable_settings(profile)['settings'])
        (profile / 'config/unknown.bin').write_text('do not ignore')
        self.assertIn('config/unknown.bin', prep.writable_settings(profile)['settings'])
        (profile / 'config/extra.toml').write_text('[section]\nkeep=false\n')
        self.assertNotEqual(first['settings_sha256'], prep.writable_settings(profile)['settings_sha256'])

    def test_cache_identity_is_explicit_preserved_and_schema_bounded(self):
        profile = self.config_fixture(); result = prep.writable_settings(profile)
        identity = result['nonshader_identities']['config/sodium-fingerprint.json']
        self.assertIn('not a shader setting', identity['role']); self.assertEqual(identity['raw']['p'], 'c'*128)
        path = profile / 'config/sodium-fingerprint.json'
        raw = identity['raw'] | {'t': 2, 'p': 'd'*128}; path.write_text(json.dumps(raw))
        changed = prep.writable_settings(profile)
        self.assertEqual(result['settings'], changed['settings'])
        self.assertNotEqual(result['nonshader_identities'], changed['nonshader_identities'])
        path.write_text(json.dumps(raw | {'renderScale': 0.9}))
        with self.assertRaisesRegex(ValueError, 'identity schema'): prep.writable_settings(profile)

    def test_properties_do_not_hide_duplicate_or_unknown_syntax(self):
        with self.assertRaisesRegex(ValueError, 'Duplicate'): prep.semantic_properties('a=1\na=2\n')
        with self.assertRaisesRegex(ValueError, 'Unsupported'): prep.semantic_properties('unparsed line')
        with self.assertRaisesRegex(ValueError, 'Unsupported'): prep.semantic_properties('a=continued\\\n next')
        self.assertEqual(prep.semantic_properties('a=x=y\n'), {'a':'x=y'})

    def test_untimed_commands_use_frozen_workload_or_camera_harness(self):
        profile = self.root / 'profile'; request = self.root / 'camera.json'
        still, motion = prep.command(profile, 'still'), prep.command(profile, 'motion', request)
        for argv in (still, motion):
            self.assertEqual(argv[:2], ['./gradlew', 'runClientLodOff'])
            for option in ('gpuTiming=false', 'performanceStack=minimal', 'includeDh=false', 'gpuPassTiming=false', 'cpuLayerTiming=false', 'benchmarkWindowScale=2'):
                self.assertIn('-P' + option, argv)
            for task in prep.BUILD_TASKS:
                self.assertIn(task, argv)
            self.assertNotIn('build', argv)
        self.assertIn('-PmeasureWorkload=true', still); self.assertIn('-PmeasureOnly=true', still)
        self.assertIn('-PcameraQualityOnly=true', motion); self.assertIn('-PmeasureWorkload=false', motion)
        self.assertIn('-PcameraQuality=' + str(request), motion)
        with self.assertRaisesRegex(ValueError, 'Motion request'): prep.command(profile, 'motion')

    def test_motion_request_bounded_matches_canonical_pose_and_settles(self):
        request = prep.motion_request(); points = request['waypoints']
        self.assertEqual(request['fps'], 10); self.assertEqual(request['warmupSeconds'], 30)
        self.assertEqual(points[0], points[-1] | {'seconds':0})
        self.assertEqual(points[-1]['seconds'], 20)
        self.assertTrue(request['rejectFullBlockCamera']); self.assertEqual(request['expectedCameraFluid'], 'NONE')
        self.assertEqual(points[0]['x'], 500.5); self.assertEqual(points[0]['yaw'], 87.38)
        for a,b in zip(points, points[1:]):
            duration = b['seconds'] - a['seconds']; self.assertGreaterEqual(duration, .25)
            self.assertLessEqual(1.5*abs(a['yaw']-b['yaw'])/duration, 90)

    def test_default_sidecar_normalization_does_not_require_byte_identity(self):
        defaults = {'TAA':'true','TAAU':'false','TAAU_RENDER_SCALE':'0.75'}
        self.assertEqual(normalize_requested_options(defaults, defaults), normalize_requested_options(defaults, {}))
        self.assertNotEqual(normalize_requested_options(defaults, {}), normalize_requested_options(defaults, {'TAAU':'true'}))


if __name__ == '__main__': unittest.main()

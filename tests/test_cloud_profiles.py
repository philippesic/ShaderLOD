"""Pure cloud-recipe and dry-run fixtures; never hash/copy live worlds or launch clients."""
import argparse
import contextlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import prepare_cloud_profiles as profiles
from prepare_isolations import PINNED


class CloudProfileTests(unittest.TestCase):
    def declarations(self):
        allowed = {name: ['true', 'false'] for name in profiles.EFFECTS.values()}
        allowed.update({name: ['true', 'false'] for name in (
            'SHADERLOD_CLOUD_OVERHEAD', 'SHADERLOD_PRESERVE_LIGHTING',
            'SHADERLOD_CACHED_SKY', 'SHADERLOD_WATER_ANALYTIC')})
        return allowed | {'SHADERLOD_CLOUD_MODE': ['0', '1', '2'], 'CLOUDS_TEMPORAL_UPSCALING': ['1', '2', '3', '4']}

    def args(self, root, **changes):
        values = dict(source_proof_root=root / 'source', output=root / 'output',
                      scenes=['coastalwater'], cloud_upscaling=4, archive_path=root / 'candidate.zip',
                      without_dh=True, dry_run=True)
        return argparse.Namespace(**(values | changes))

    def test_exact_modes_protection_and_only_cloud_effect_enabled(self):
        common = {'ENVIRONMENT_REFLECTIONS': 'true', 'POM': 'false', 'CLOUDS_CUMULUS_PRIMARY_STEPS_H': '40'}
        recipes = {
            'patched-off': ('0', 'false', 'true'), 'alloff': ('0', 'false', 'false'),
            'cloud-current': ('0', 'false', 'true'), 'cloud-independent': ('1', 'true', 'true'),
            'cloud-smooth': ('2', 'true', 'true'), 'cloud-independent-unprotected': ('1', 'false', 'true')}
        for arm in profiles.ARMS:
            for scale in (1, 4):
                requested = profiles.options_for_arm(common, arm, self.declarations(), scale)
                self.assertEqual(str(scale), requested['CLOUDS_TEMPORAL_UPSCALING'])
                for name, value in common.items(): self.assertEqual(value, requested[name])
                if arm == 'high':
                    self.assertFalse(any(name.startswith('SHADERLOD_') for name in requested)); continue
                mode, protection, cloud_enabled = recipes[arm]
                self.assertEqual(mode, requested['SHADERLOD_CLOUD_MODE'])
                self.assertEqual(protection, requested['SHADERLOD_CLOUD_OVERHEAD'])
                self.assertEqual(cloud_enabled, requested['SHADERLOD_CLOUDS'])
                for effect, option in profiles.EFFECTS.items():
                    if effect != 'cloud': self.assertEqual('false', requested[option])
                self.assertEqual('true', requested['SHADERLOD_PRESERVE_LIGHTING'])
                self.assertEqual('false', requested['SHADERLOD_CACHED_SKY'])
                self.assertEqual('false', requested['SHADERLOD_WATER_ANALYTIC'])

    def test_independent_unprotected_is_a_single_option_causal_control(self):
        a = profiles.options_for_arm({}, 'cloud-independent', self.declarations(), 4)
        b = profiles.options_for_arm({}, 'cloud-independent-unprotected', self.declarations(), 4)
        changed = {name for name in a if a[name] != b[name]}
        self.assertEqual({'SHADERLOD_CLOUD_OVERHEAD'}, changed)
        current = profiles.options_for_arm({}, 'cloud-current', self.declarations(), 4)
        self.assertEqual({'SHADERLOD_CLOUD_MODE'}, {name for name in current if current[name] != b[name]})

    def test_unknown_or_unavailable_controls_fail_closed(self):
        for scale in (0, 2, 3, 5):
            with self.assertRaises(ValueError): profiles.options_for_arm({}, 'high', self.declarations(), scale)
        with self.assertRaises(ValueError): profiles.cloud_flags('invented')
        allowed = self.declarations()
        allowed.pop('SHADERLOD_CLOUD_MODE')
        with self.assertRaisesRegex(ValueError, 'SHADERLOD_CLOUD_MODE'):
            profiles.options_for_arm({}, 'cloud-smooth', allowed, 4)
        allowed = self.declarations(); allowed['SHADERLOD_CLOUD_OVERHEAD'] = ['false']
        with self.assertRaisesRegex(ValueError, 'SHADERLOD_CLOUD_OVERHEAD'):
            profiles.options_for_arm({}, 'cloud-independent', allowed, 4)

    def test_plan_is_fresh_exact_and_does_not_override_source_preset(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); args = self.args(root)
            planned = profiles.plan_clouds(args)
            self.assertEqual('cloud-transition-no-atmosphere', planned['variant'])
            self.assertEqual(PINNED[profiles.SELECTOR][1], planned['archive_sha256'])
            self.assertEqual(list(profiles.ARMS), planned['arms'])
            self.assertEqual(4, planned['cloud_upscaling'])
            self.assertIn('Preserve source', planned['preset_policy'])
            self.assertEqual('off', args.analytic)
            self.assertFalse((root / 'output').exists())
            self.assertEqual([str(root.resolve() / 'output/coastalwater' / arm) for arm in profiles.ARMS],
                             planned['scene_operations'][0]['target_profiles'])
            self.assertNotIn('high', planned['dynamic_mod_enabled_arms'])
            self.assertNotIn('patched-off', planned['dynamic_mod_enabled_arms'])
            (root / 'output').mkdir()
            with self.assertRaises(ValueError): profiles.plan_clouds(args)

    def test_plan_rejects_replacement_and_source_overlap(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for changes in ({'backup_existing': True}, {'scenes': ['forest']},
                            {'scenes': ['cloudstorm', 'cloudstorm']}, {'output': root / 'source/child'}):
                with self.subTest(changes=changes), self.assertRaises(ValueError):
                    profiles.plan_clouds(self.args(root, **changes))

    def test_dry_run_only_delegates_provenance_validation_without_save_work(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'dev/upstream').mkdir(parents=True)
            photon = {'filename': 'original.zip', 'version': 'v1.3b', 'hashes': {'sha512': 'fixture'}}
            (root / 'dev/runtime.lock.json').write_text(json.dumps({'artifacts': {'photon': photon}}))
            settings = '\n'.join('#define ' + name + (' 0 // [0 1 2]' if name == 'SHADERLOD_CLOUD_MODE'
                else ' 4 // [1 2 3 4]' if name == 'CLOUDS_TEMPORAL_UPSCALING' else '') for name in self.declarations())
            with zipfile.ZipFile(root / 'dev/upstream/original.zip', 'w') as archive:
                archive.writestr('LICENSE', 'fixture license')
                archive.writestr('shaders/settings.glsl', settings)
            args = self.args(root)
            with patch.object(profiles, 'ROOT', root), patch.object(profiles, 'archive_contents', return_value={'settings': settings}) as inspect_archive, \
                 patch.object(profiles, 'prepare') as validate, \
                 patch.object(profiles, 'world_fingerprint', side_effect=AssertionError('No world hash in dry run')), \
                 patch.object(profiles.shutil, 'copytree', side_effect=AssertionError('No copy in dry run')), \
                 patch.object(profiles, 'write_json', side_effect=AssertionError('No writes in dry run')), \
                 contextlib.redirect_stdout(io.StringIO()):
                profiles.prepare_clouds(args)
            validate.assert_called_once_with(args)
            self.assertTrue(args.dry_run)
            self.assertEqual(PINNED[profiles.SELECTOR][1], inspect_archive.call_args.args[1])
            self.assertFalse(args.output.exists())


if __name__ == '__main__': unittest.main()

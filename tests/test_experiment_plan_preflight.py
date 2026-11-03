"""Offline checks; authentic saved plan schema, temporary files, no client/build."""
import copy
from datetime import datetime, timedelta, timezone
import hashlib
import importlib.util
import json
import shlex
import shutil
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
SPEC = importlib.util.spec_from_file_location('preflight', ROOT / 'scripts/check_experiment_plan.py')
pre = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(pre)
FIXTURE = ROOT / 'tests/fixtures/experiment-plan/reimagined-interval-cue-r1.json'


class ExperimentPlanTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.now = datetime(2026, 10, 10, 20, tzinfo=timezone.utc)
        self.p = json.loads(FIXTURE.read_text())
        old_root = '/Users/pippo/github/shaderlod'
        # Relocate the real argv/paths; only dependent filesystem contents are small stand-ins.
        self.p = json.loads(json.dumps(self.p).replace(old_root, str(self.root)))
        profile = Path(self.p['profile'])
        for name in ('config', 'mods', 'shaderpacks', 'saves/' + pre.dc.WORLD):
            (profile / name).mkdir(parents=True, exist_ok=True)
        (profile / 'saves' / pre.dc.WORLD / 'level.dat').write_bytes(b'not a Minecraft world')
        (profile / 'config/iris.properties').write_text('shaderPack=' + self.p['pack_name'] + '\n')
        (self.root / 'gradlew').write_text('not executable; checker never launches')
        (self.root / 'dev').mkdir(exist_ok=True)
        (self.root / "dependencies").mkdir(exist_ok=True)
        (self.root / 'dependencies/performance-mods.lock.json').write_text(json.dumps({'stacks': {'minimal': [], 'optimized': [], 'optimized-culling': []}}))
        (self.root / 'dependencies/runtime.lock.json').write_text(json.dumps({'artifacts': {'distanthorizons': {'filename': 'dh.jar'}}}))
        for pack in (Path(self.p['pack']), profile / 'shaderpacks/ComplementaryReimagined_r5.9.3.zip'):
            pack.parent.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(pack, 'w') as z:
                z.writestr('shaders/shaders.properties', 'screen=MODE\n')
                z.writestr('shaders/composite.fsh', 'void main() {}')
        self.p['expected_loaded_sha256'] = hashlib.sha256(Path(self.p['pack']).read_bytes()).hexdigest()
        options = Path(self.p['pack_options'])
        options.parent.mkdir(parents=True, exist_ok=True)
        options.write_text('MODE=3\n')
        Path(self.p['expected_options_source']).write_text(json.dumps({'MODE': '3', 'NATIVE': 'true'}))
        self.p['expected_options_count'] = 2
        (profile.parent / 'control.json').write_text(json.dumps(self.p['scene_input']))
        review = Path(self.p['sourceReview'])
        review.parent.mkdir(parents=True, exist_ok=True)
        review.write_text('Small stand-in for a pinned source review, not source admission.')
        self.freeze = {'files': {str(review): hashlib.sha256(review.read_bytes()).hexdigest()}}
        self.p['stop_at_utc'] = (self.now + timedelta(seconds=120)).isoformat()
        self.root_patch = patch.object(pre.dc, 'ROOT', self.root)
        self.root_patch.start()
        self.addCleanup(self.root_patch.stop)

    def check(self, p=None, **kwargs):
        return pre.check_plan(p or self.p, freeze=self.freeze, now=self.now, **kwargs)

    def test_real_record_shape_and_owner_wrapper(self):
        self.assertEqual(self.check()['errors'], [])
        self.assertEqual(self.check({'plan': self.p, 'status': 'pending'})['errors'], [])
        self.assertIn('actual JVM/argfile consumption', self.check()['unverified'])

    def test_custom_saved_world_requires_neutral_control_and_exact_file(self):
        p = copy.deepcopy(self.p)
        p['world'] = 'ShaderLOD-Benchmark-overworld-3-flat'
        world = Path(p['profile']) / 'saves' / p['world']
        world.mkdir()
        (world / 'level.dat').write_bytes(b'bounded fixture')
        self.assertIn('profile_world_iris', {e['check'] for e in self.check(p)['errors']})
        p['benchmark_control_probe'] = str(self.root / 'neutral.json')
        self.assertNotIn('profile_world_iris', {e['check'] for e in self.check(p)['errors']})
        p['world'] = '../outside'
        self.assertIn('profile_world_iris', {e['check'] for e in self.check(p)['errors']})

    def test_independent_errors_reported_together(self):
        p = copy.deepcopy(self.p)
        p['stop_at_utc'] = None
        p['ready_timeout'] = 1000
        p['profile'] = str(self.root / 'missing')
        p['pack'] = str(self.root / 'missing.zip')
        p['command'] += ['-PincludeDh=true']
        p['expected_options_count'] = 400
        self.freeze['files'] = {}
        errors = {e['check'] for e in self.check(p)['errors']}
        self.assertTrue({'finite_deadline', 'ready_timeout', 'profile_world_iris', 'shader_archive',
                         'launcher_command', 'expected_options', 'source_review_pin'}.issubset(errors))

    def test_authentic_null_deadline_and_unpinned_review(self):
        # The archived plan has both defects; no repaired fixture is mislabeled original.
        raw = json.loads(FIXTURE.read_text())
        self.assertIsNone(raw['stop_at_utc'])
        self.assertIn('photon-display', raw['sourceReview'])
        self.p['stop_at_utc'] = raw['stop_at_utc']
        self.freeze['files'] = {}
        errors = {e['check'] for e in self.check()['errors']}
        self.assertEqual(errors, {'finite_deadline', 'source_review_pin'})

    def test_deadline_margin_and_historical_scope(self):
        self.p['stop_at_utc'] = (self.now + timedelta(seconds=75)).isoformat()
        self.assertIn('finite_deadline', {e['check'] for e in self.check()['errors']})
        self.p['stop_at_utc'] = (self.now - timedelta(seconds=5)).isoformat()
        self.assertEqual(self.check(historical=True)['errors'], [])
        self.assertEqual(self.check(historical=True)['scope'], 'historical static inspection')

    def test_command_profile_and_task_conflicts(self):
        self.p['command'][1] = 'runClientLodOff'
        self.assertIn('launcher_command', {e['check'] for e in self.check()['errors']})

    def test_performance_stack_uses_existing_lock(self):
        self.p['command'] = ['-PperformanceStack=full' if a.startswith('-PperformanceStack=') else a
                             for a in self.p['command']]
        errors = {e['check']: e['error'] for e in self.check()['errors']}
        self.assertEqual(set(errors), {'launcher_command'})
        self.assertIn('Unknown performanceStack: full', errors['launcher_command'])
        self.p['command'] = ['-PperformanceStack=optimized' if a.startswith('-PperformanceStack=') else a
                             for a in self.p['command']]
        self.assertEqual(self.check()['errors'], [])
        self.assertIn('optional runtime artifact availability/hashes', self.check()['unverified'])

    def test_named_capture_collision_and_original_archive(self):
        destination = next(a.split('=', 1)[1] for a in self.p['command'] if a.startswith('-PcontrolCaptureReceipt='))
        target = Path(destination) / (self.p['protocol_capture_name'] + '.json')
        target.parent.mkdir(parents=True)
        target.write_text('{}')
        self.assertIn('named_capture', {e['check'] for e in self.check()['errors']})
        self.assertEqual(self.check(historical=True)['errors'], [])

    def test_capture_destination_file_and_independent_deadline_defect(self):
        self.p['command'] = [('-PcontrolCaptureReceipt=' + str(self.root / 'gradlew'))
            if a.startswith('-PcontrolCaptureReceipt=') else a for a in self.p['command']]
        self.p['stop_at_utc'] = None
        errors = {e['check']: e['error'] for e in self.check()['errors']}
        self.assertEqual(set(errors), {'named_capture', 'finite_deadline'})
        self.assertIn('not a directory', errors['named_capture'])

    def test_capture_destination_missing_ancestor_and_access(self):
        self.p['command'] = [('-PcontrolCaptureReceipt=' + str(self.root / 'gradlew/missing'))
            if a.startswith('-PcontrolCaptureReceipt=') else a for a in self.p['command']]
        self.assertIn('named_capture', {e['check'] for e in self.check()['errors']})
        self.p['command'] = [('-PcontrolCaptureReceipt=' + str(self.root / 'new/captures'))
            if a.startswith('-PcontrolCaptureReceipt=') else a for a in self.p['command']]
        self.assertEqual(self.check()['errors'], [])
        with patch.object(pre.os, 'access', return_value=False):
            self.assertIn('named_capture', {e['check'] for e in self.check()['errors']})
        self.assertFalse((self.root / 'new').exists())

    def test_receipt_exact_fields_and_mismatch_batch(self):
        receipt = dict(status='saved-observations', irisLoadedPack=self.p['pack_name'],
                       irisSelectedPack=self.p['pack_name'], selectedArchiveSha256=self.p['expected_loaded_sha256'],
                       loadedIrisOptions={'MODE': '3', 'NATIVE': 'true'}, irisFallback=False,
                       shaderPackInUse=True, fullscreen=True, focused=True,
                       framebufferWidth=1920, framebufferHeight=1200)
        self.assertEqual(self.check(receipt=receipt)['errors'], [])
        receipt.update(focused=False, loadedIrisOptions={})
        error = self.check(receipt=receipt)['errors'][0]['error']
        self.assertIn('focused', error)
        self.assertIn('loadedIrisOptions', error)

    def test_authentic_receipt_schema(self):
        receipt = json.loads((FIXTURE.parent / 'reimagined-control-receipt-fields-r1.json').read_text())
        self.assertEqual(receipt['status'], 'saved-observations')
        self.assertEqual(len(receipt['loadedIrisOptions']), 399)
        Path(self.p['expected_options_source']).write_text(json.dumps(receipt['loadedIrisOptions']))
        self.p['expected_options_count'] = 399
        # Rebase only the expected hash to the small temporary archive.
        receipt['selectedArchiveSha256'] = self.p['expected_loaded_sha256']
        self.assertEqual(self.check(receipt=receipt)['errors'], [])

    def test_no_mutation_or_process_calls(self):
        before = {p: p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        with patch.object(pre.dc, 'prepare', side_effect=AssertionError('mutation')), \
             patch.object(pre.dc, 'process_table', side_effect=AssertionError('process inspection')), \
             patch.object(pre.dc, 'supervise', side_effect=AssertionError('launch')):
            self.assertEqual(self.check()['errors'], [])
        after = {p: p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        self.assertEqual(before, after)

    def test_malformed_records_refused_without_traceback(self):
        for record in (None, [], {'plan': []}):
            self.assertTrue(pre.check_plan(record)['errors'])
        self.p['command'] = None
        self.assertIn('launcher_command', {e['check'] for e in self.check()['errors']})

    def test_optional_build_reuses_existing_api(self):
        with patch.object(pre.dc, 'validate_built') as validate:
            self.assertEqual(self.check(check_built=True)['errors'], [])
            validate.assert_called_once_with()

    def fabric_plan(self):
        p = copy.deepcopy(self.p)
        p.update(launcher='fabric1211', mode='on', include_dh=False,
                 fullscreen=True, apply_lod_settings=False)
        p.pop('protocol_capture_name', None)
        game = shlex.join(['--gameDir', p['profile'], '--username', 'Dev',
                          '--fullscreen', '--width', '1920', '--height', '1200',
                          '--quickPlaySingleplayer', pre.dc.WORLD])
        p['command'] = [str(self.root / 'gradlew'), '-p', 'fabric', '--no-daemon',
                        '--max-workers=2', '--console=plain', 'runClient', '-PfabricRoundtrip=true',
                        '-PfabricRendererSpeedProbe=true', '-PfabricRoundtripOriginal=' + p['pack_name'],
                        '-PfabricRoundtripDh=false', '-PfabricRoundtripUi=false', '--args=' + game]
        for task in pre.dc.SKIPPABLE:
            p['command'] += ['-x', task]
        return p

    def test_explicit_fabric_launcher_validates_forwarded_real_entry_shape(self):
        self.assertEqual(self.check(self.fabric_plan())['errors'], [])
        p = self.fabric_plan()
        p['launcher'] = 'fabric263'
        self.assertIn('launcher', {e['check'] for e in self.check(p)['errors']})

    def test_fabric_rejects_wrong_duplicate_project_task_and_game_directory(self):
        changes = [lambda a: a.__setitem__(a.index('fabric'), 'ports/1.21.11/fabric'),
                   lambda a: a.extend(['-p', 'fabric']), lambda a: a.append('runClient'),
                   lambda a: a.append('--project-dir=other'), lambda a: a.append('build'),
                   lambda a: a.append('--args=--gameDir wrong'),
                   lambda a: a.__setitem__(next(i for i, v in enumerate(a) if v.startswith('--args=')),
                       '--args=--gameDir wrong --fullscreen --quickPlaySingleplayer ' + pre.dc.WORLD)]
        for change in changes:
            p = self.fabric_plan(); change(p['command'])
            self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']}, p['command'])
        for duplicate in ('--gameDir "' + self.p['profile'] + '"', '--fullscreen',
                          '--quickPlaySingleplayer ' + pre.dc.WORLD):
            p = self.fabric_plan()
            p['command'] = [v + ' ' + duplicate if v.startswith('--args=') else v for v in p['command']]
            self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']})

    def test_fabric_requires_candidate_original_and_no_ui_dh_research_properties(self):
        for key, value in [('fabricRoundtrip', 'false'), ('fabricRendererSpeedProbe', 'false'),
                           ('fabricRoundtripOriginal', 'foreign.zip'), ('fabricRoundtripDh', 'true'),
                           ('fabricRoundtripUi', 'true')]:
            p = self.fabric_plan()
            p['command'] = ['-P' + key + '=' + value if v.startswith('-P' + key + '=') else v
                            for v in p['command']]
            self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']}, key)
        p = self.fabric_plan(); p['command'] += ['-PexperimentalFabricRenderer1211=false']
        self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']})
        p = self.fabric_plan(); p['command'] += ['-PfabricRendererSpeedProbe=true']
        self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']})

    def test_fabric_already_built_skip_vector_and_empty_profile_mods(self):
        p = self.fabric_plan(); p['command'] = p['command'][:-2]
        self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']})
        p = self.fabric_plan(); (Path(p['profile']) / 'mods/foreign.jar').write_bytes(b'foreign')
        self.assertIn('profile_mods', {e['check'] for e in self.check(p)['errors']})

    def test_fabric_untimed_source_probe_is_explicit_and_excludes_speed_probe(self):
        p = self.fabric_plan()
        p['command'] = [arg.replace('-PfabricRendererSpeedProbe=true', '-PfabricRendererSourceProbe=true')
                        for arg in p['command']]
        self.assertEqual(self.check(p)['errors'], [])
        p['command'] += ['-PfabricRendererSpeedProbe=true']
        self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']})

    def test_fabric_rejects_invalid_probe_values(self):
        p = self.fabric_plan()
        p['command'] += ['-PfabricRendererSourceProbe=unknown']
        self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']})

    def test_fabric_never_reuses_neoforge_build_receipt(self):
        with patch.object(pre.dc, 'validate_built') as validate:
            errors = {e['check'] for e in self.check(self.fabric_plan(), check_built=True)['errors']}
            self.assertEqual(errors, {'current_build_receipt'})
            validate.assert_not_called()

    def port_fabric_plan(self):
        p = self.fabric_plan(); p['launcher'] = 'fabric12111'
        launcher = self.root / 'ports/1.21.11/runtime/gradle-9.4.0/bin/gradle'
        launcher.parent.mkdir(parents=True, exist_ok=True); launcher.write_text('not executable; static only')
        p['command'][0] = str(launcher)
        p['command'][p['command'].index('fabric')] = str(self.root / 'ports/1.21.11/fabric')
        mapping = {'fabricRoundtrip': 'portRoundtrip', 'fabricRendererSpeedProbe': 'portRendererSpeedProbe',
                   'fabricRoundtripOriginal': 'portRoundtripOriginal', 'fabricRoundtripDh': 'portQualityRoundtrip',
                   'fabricRoundtripUi': 'portRoundtripUi'}
        p['command'] = ['-P' + mapping[v[2:].split('=', 1)[0]] + '=' + v.split('=', 1)[1]
                        if v.startswith('-P') else v for v in p['command']]
        p['command'].append('-PportRendererSpeedCandidate=true')
        return p

    def test_explicit12111_fabric_real_launcher_and_negative_version_candidate_inputs(self):
        p = self.port_fabric_plan(); self.assertEqual(self.check(p)['errors'], [])
        for key, value in [('portRendererSpeedCandidate', 'false'), ('portRendererSpeedProbe', 'false'),
                           ('portQualityRoundtrip', 'true'), ('portRoundtripOriginal', 'foreign.zip')]:
            wrong = copy.deepcopy(p)
            wrong['command'] = ['-P' + key + '=' + value if v.startswith('-P' + key + '=') else v for v in wrong['command']]
            self.assertIn('launcher_command', {e['check'] for e in self.check(wrong)['errors']}, key)
        wrong = copy.deepcopy(p); wrong['command'][wrong['command'].index('-p') + 1] = str(self.root / 'ports/26.1.2/fabric')
        self.assertIn('launcher_command', {e['check'] for e in self.check(wrong)['errors']})
        wrong = copy.deepcopy(p); wrong['command'].append('-PportRendererSpeedCandidate=true')
        self.assertIn('launcher_command', {e['check'] for e in self.check(wrong)['errors']})

    def test_explicit262_fabric_pins_launcher_module_and_rejects_borrowed_receipt(self):
        p = self.port_fabric_plan(); p['launcher'] = 'fabric262'
        launcher = self.root / 'ports/26.2/runtime/gradle-9.5.0/bin/gradle'
        launcher.parent.mkdir(parents=True); launcher.write_text('not executable; static only')
        p['command'][0] = str(launcher)
        p['command'][p['command'].index('-p') + 1] = str(self.root / 'ports/26.2/fabric')
        self.assertEqual(self.check(p)['errors'], [])
        for module in ('ports/1.21.11/fabric', 'ports/26.3/fabric'):
            wrong = copy.deepcopy(p)
            wrong['command'][wrong['command'].index('-p') + 1] = str(self.root / module)
            self.assertIn('launcher_command', {e['check'] for e in self.check(wrong)['errors']})
        wrong = copy.deepcopy(p)
        wrong['command'][0] = str(self.root / 'ports/1.21.11/runtime/gradle-9.4.0/bin/gradle')
        self.assertIn('launcher_command', {e['check'] for e in self.check(wrong)['errors']})
        wrong = copy.deepcopy(p); wrong['launcher'] = 'fabric263'
        self.assertIn('launcher', {e['check'] for e in self.check(wrong)['errors']})
        with patch.object(pre.dc, 'validate_built') as validate:
            self.assertEqual({e['check'] for e in self.check(p, check_built=True)['errors']},
                             {'current_build_receipt'})
            validate.assert_not_called()

    def bsl_original_plan(self):
        p = self.fabric_plan()
        pack = self.root / 'authentic-BSL_v10.1.8.zip'
        shutil.copy2(ROOT / 'dependencies/upstream/other-pack-research/BSL_v10.1.8.zip', pack)
        p.update(pack=str(pack), pack_name=pack.name,
                 installed_pack=str(Path(p['profile']) / 'shaderpacks' / pack.name),
                 expected_loaded_pack=pack.name,
                 expected_loaded_sha256='36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5',
                 expected_options_count=384)
        shutil.copy2(pack, p['installed_pack'])
        native = ROOT / 'src/main/resources/assets/shaderlod/bslquad/native384.json'
        target = self.root / 'src/main/resources/assets/shaderlod/bslquad/native384.json'
        target.parent.mkdir(parents=True, exist_ok=True); shutil.copy2(native, target)
        for name in ('source-proof.json', 'api-proof.json'):
            shutil.copy2(native.parent / name, target.parent / name)
        Path(p['expected_options_source']).write_bytes(native.read_bytes())
        values = json.loads(native.read_text())
        Path(p['pack_options']).write_text(''.join(key + '=' + value + '\n' for key, value in values.items()))
        (Path(p['profile']) / 'config/iris.properties').write_text(
            'shaderPack=' + pack.name + '\nenableShaders=true\nenableDebugOptions=true\n')
        (Path(p['profile']) / 'config/shaderlod-client.json').write_text(
            json.dumps(dict(enabled=False, preset='PERFORMANCE', debug=False)))
        p['command'] = [v for v in p['command'] if not v.startswith('-PfabricRendererSpeedProbe=')]
        p['command'] = ['-PfabricRoundtripOriginal=' + pack.name if v.startswith('-PfabricRoundtripOriginal=') else v
                        for v in p['command']]
        p['command'] += ['-PfabricOriginalSourceProbe=true', '-PfabricOriginalSourceFamily=bsl']
        return p

    def test_bsl_original_only_uses_real_archive_native384_and_actual_explicit_flags(self):
        p = self.bsl_original_plan(); self.assertEqual(self.check(p)['errors'], [])
        p['command'].remove('-PfabricRoundtrip=true')
        self.assertEqual(self.check(p)['errors'], [], 'Original flag is its own compiled entrypoint')

    def sildur_port_original_plan(self):
        p = self.bsl_original_plan()
        archive = ROOT / "dependencies/upstream/pack-expansion/Sildur's Vibrant Shaders v2.02 Extreme.zip"
        pack = self.root / archive.name
        shutil.copy2(archive, pack)
        p.update(launcher='fabric12111', pack=str(pack), pack_name=pack.name,
                 installed_pack=str(Path(p['profile']) / 'shaderpacks' / pack.name),
                 expected_loaded_pack=pack.name,
                 expected_loaded_sha256='824504bc937f9570277c0a809f5059ae4a5fe70c413cf4459b6ccff4dfed7ebe',
                 expected_options_count=115)
        shutil.copy2(pack, p['installed_pack'])
        native = ROOT / 'src/main/resources/assets/shaderlod/sildurquad/native115.json'
        folder = self.root / 'src/main/resources/assets/shaderlod/sildurquad'
        folder.mkdir(parents=True)
        for name in ('native115.json', 'source-proof.json', 'api-proof.json'):
            shutil.copy2(native.parent / name, folder / name)
        Path(p['expected_options_source']).write_bytes(native.read_bytes())
        Path(p['pack_options']).write_text('')
        profile = Path(p['profile'])
        (profile / 'config/iris.properties').write_text(
            'shaderPack=' + pack.name + '\nenableShaders=true\nenableDebugOptions=true\n')
        (profile / 'config/sodium-options.json').write_text(
            json.dumps({'performance': {'use_no_error_g_l_context': False}}))
        launcher = self.root / 'ports/1.21.11/runtime/gradle-9.4.0/bin/gradle'
        launcher.parent.mkdir(parents=True)
        launcher.write_text('not executable; static only')
        p['command'][0] = str(launcher)
        p['command'][p['command'].index('-p') + 1] = str(self.root / 'ports/1.21.11/fabric')
        p['command'] = [v for v in p['command'] if not v.startswith('-Pfabric')]
        p['command'] += ['-PportSildurOriginalSource=true', '-PportRoundtripOriginal=' + pack.name]
        return p

    def test_sildur12111_original_accepts_authentic_native115_and_rejects_mixed_modes(self):
        p = self.sildur_port_original_plan()
        self.assertEqual(self.check(p)['errors'], [])
        for flag in ('portBslOriginalSource', 'portRoundtrip', 'portRendererSpeedProbe',
                     'portRendererSpeedCandidate', 'portRoundtripUi', 'portQualityRoundtrip',
                     'experimentalRendererResearch'):
            wrong = copy.deepcopy(p)
            wrong['command'].append('-P' + flag + '=true')
            self.assertIn('launcher_command', {e['check'] for e in self.check(wrong)['errors']}, flag)
        wrong = copy.deepcopy(p)
        wrong['command'] = [v for v in wrong['command'] if not v.startswith('-PportSildurOriginalSource=')]
        self.assertIn('launcher_command', {e['check'] for e in self.check(wrong)['errors']})

    def test_sildur12111_original_refuses_foreign_inputs_and_export_alias(self):
        p = self.sildur_port_original_plan()
        native = Path(p['expected_options_source'])
        before = native.read_bytes()
        values = json.loads(before); values['SHADERLOD_FOREIGN'] = 'true'
        native.write_text(json.dumps(values))
        self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']})
        native.write_bytes(before)
        Path(p['pack_options']).write_text('TAA=true\n')
        self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']})
        Path(p['pack_options']).write_text('')
        exports = Path(p['profile']) / 'patched_shaders'
        exports.symlink_to(self.root, target_is_directory=True)
        self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']})
        exports.unlink()
        for name in ('native115.json', 'source-proof.json', 'api-proof.json'):
            f = self.root / 'src/main/resources/assets/shaderlod/sildurquad' / name
            data = f.read_bytes(); f.write_bytes(data + b'foreign')
            self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']}, name)
            f.write_bytes(data)

    def test_sildur12111_debug_exports_refuse_native_modal_context_conflict(self):
        p = self.sildur_port_original_plan()
        options = Path(p['profile']) / 'config/sodium-options.json'
        for value in (True, None, 'false'):
            options.write_text(json.dumps({'performance': {'use_no_error_g_l_context': value}}))
            self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']}, value)
        options.unlink()
        self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']})

    def test_bsl_original_only_rejects_missing_unknown_family_wrong_original_and_mixed_modes(self):
        p = self.bsl_original_plan()
        for key, value in [('fabricOriginalSourceFamily', ''), ('fabricOriginalSourceFamily', 'photon'),
                           ('fabricRoundtripOriginal', 'foreign.zip'), ('fabricOriginalSourceProbe', 'false')]:
            wrong = copy.deepcopy(p)
            wrong['command'] = ['-P' + key + '=' + value if v.startswith('-P' + key + '=') else v for v in wrong['command']]
            self.assertIn('launcher_command', {e['check'] for e in self.check(wrong)['errors']}, key + '=' + value)
        wrong = copy.deepcopy(p); wrong['command'].remove('-PfabricOriginalSourceFamily=bsl')
        self.assertIn('launcher_command', {e['check'] for e in self.check(wrong)['errors']})
        for flag in ('fabricRendererSpeedProbe', 'fabricRendererSourceProbe', 'experimentalRendererResearch',
                     'experimentalFabricRenderer1211', 'fabricRoundtripUi', 'fabricRoundtripDh'):
            wrong = copy.deepcopy(p)
            wrong['command'] = [v for v in wrong['command'] if not v.startswith('-P' + flag + '=')]
            wrong['command'].append('-P' + flag + '=true')
            self.assertIn('launcher_command', {e['check'] for e in self.check(wrong)['errors']}, flag)

    def test_bsl_original_only_refuses_changed_or_missing_installed_archive_and_native_option_map(self):
        p = self.bsl_original_plan(); expected = json.loads(Path(p['expected_options_source']).read_text())
        for change in ('missing', 'changed', 'extra'):
            wrong = dict(expected); key = next(iter(wrong))
            if change == 'missing': wrong.pop(key)
            if change == 'changed': wrong[key] = 'foreign'
            if change == 'extra': wrong['SHADERLOD_PRIVATE_SOURCE'] = '1'
            Path(p['expected_options_source']).write_text(json.dumps(wrong))
            self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']}, change)
        Path(p['expected_options_source']).write_text(json.dumps(expected))
        Path(p['installed_pack']).write_bytes(b'foreign')
        self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']})
        Path(p['installed_pack']).unlink()
        self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']})

    def test_bsl_original_only_refuses_unprepared_selected_debug_or_on_profile(self):
        p = self.bsl_original_plan(); iris = Path(p['profile']) / 'config/iris.properties'
        iris.write_text('shaderPack=' + p['pack_name'] + '\nenableShaders=true\nenableDebugOptions=false\n')
        self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']})
        iris.write_text('shaderPack=foreign.zip\nenableShaders=true\nenableDebugOptions=true\n')
        self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']})
        iris.write_text('shaderPack=' + p['pack_name'] + '\nenableShaders=true\nenableDebugOptions=true\n')
        config = Path(p['profile']) / 'config/shaderlod-client.json'
        config.write_text(json.dumps(dict(enabled=True, preset='PERFORMANCE', debug=False)))
        self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']})

    def test_bsl_original_only_refuses_stale_exports_and_foreign_actual_overrides(self):
        p = self.bsl_original_plan(); exports = Path(p['profile']) / 'patched_shaders'
        exports.mkdir(); (exports / 'old.fsh').write_text('stale historical export')
        self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']})
        (exports / 'old.fsh').unlink()
        self.assertEqual(self.check(p)['errors'], [])
        with Path(p['pack_options']).open('a') as output: output.write('SHADERLOD_UNKNOWN=1\n')
        self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']})

    def test_bsl_original_only_requires_unchanged_native_and_ancestry_certificate_resources(self):
        p = self.bsl_original_plan()
        folder = self.root / 'src/main/resources/assets/shaderlod/bslquad'
        for name in ('native384.json', 'source-proof.json', 'api-proof.json'):
            resource = folder / name; before = resource.read_bytes()
            resource.write_bytes(before + b'changed')
            self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']}, name)
            resource.unlink()
            self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']}, name)
            resource.write_bytes(before)

    def bsl_renderer_plan(self, source=False):
        p = self.bsl_original_plan()
        p['command'] = [a for a in p['command'] if not a.startswith(('-PfabricOriginalSourceProbe=', '-PfabricOriginalSourceFamily='))]
        p['command'] += ['-PfabricRendererResearchFamily=bsl',
                        '-PfabricRendererSourceProbe=true' if source else '-PfabricRendererSpeedProbe=true']
        iris = Path(p['profile']) / 'config/iris.properties'
        iris.write_text('shaderPack=' + p['pack_name'] + '\nenableShaders=true\nenableDebugOptions=' + str(source).lower() + '\n')
        return p

    def test_named_bsl_renderer_real_certificate_source_and_clean_speed_inputs_admit(self):
        for source in (False, True):
            self.assertEqual(self.check(self.bsl_renderer_plan(source))['errors'], [])

    def test_named_bsl_renderer_missing_unknown_and_mixed_family_modes_refuse(self):
        p = self.bsl_renderer_plan()
        for replacement in (None, '', 'unknown', 'photon'):
            wrong = copy.deepcopy(p)
            wrong['command'] = [a for a in wrong['command'] if not a.startswith('-PfabricRendererResearchFamily=')]
            if replacement is not None: wrong['command'].append('-PfabricRendererResearchFamily=' + replacement)
            self.assertIn('launcher_command', {e['check'] for e in self.check(wrong)['errors']}, replacement)
        for flag in ('fabricOriginalSourceProbe', 'fabricRendererSourceProbe', 'fabricRoundtripUi',
                     'fabricRoundtripDh', 'experimentalRendererResearch', 'experimentalFabricRenderer1211'):
            wrong = copy.deepcopy(p)
            wrong['command'] = [a for a in wrong['command'] if not a.startswith('-P' + flag + '=')]
            wrong['command'].append('-P' + flag + '=true')
            self.assertIn('launcher_command', {e['check'] for e in self.check(wrong)['errors']}, flag)

    def test_named_bsl_renderer_wrong_source_or_debug_policy_refuses(self):
        p = self.bsl_renderer_plan()
        p['command'] = ['-PfabricRoundtripOriginal=foreign.zip' if a.startswith('-PfabricRoundtripOriginal=') else a for a in p['command']]
        self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']})
        p = self.bsl_renderer_plan(); iris = Path(p['profile']) / 'config/iris.properties'
        iris.write_text(iris.read_text().replace('enableDebugOptions=false', 'enableDebugOptions=true'))
        self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']})
        p = self.bsl_renderer_plan(True)
        Path(p['installed_pack']).write_bytes(b'foreign')
        self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']})

    def test_native_bsl_cost_control_explicit_speed_and_no_reload_admits(self):
        p = self.bsl_renderer_plan(); p['command'].append('-PfabricNativeCostControl=true')
        self.assertEqual(self.check(p)['errors'], [])

    def test_native_cost_control_missing_family_source_mixed_or_invalid_values_refuse(self):
        for source in (False, True):
            p = self.bsl_renderer_plan(source); p['command'].append('-PfabricNativeCostControl=true')
            if source: self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']})
        for flag, value in [('fabricNativeCostControl', 'false'), ('fabricNativeCostControl', ''),
                            ('fabricRendererResearchFamily', 'unknown'), ('fabricRendererSpeedProbe', 'false'),
                            ('fabricOriginalSourceProbe', 'true'), ('fabricRoundtripUi', 'true'),
                            ('fabricRoundtripDh', 'true'), ('fabricNativeCostReload', 'true')]:
            p = self.bsl_renderer_plan(); p['command'].append('-PfabricNativeCostControl=true')
            p['command'] = [a for a in p['command'] if not a.startswith('-P' + flag + '=')]
            p['command'].append('-P' + flag + '=' + value)
            self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']}, flag)
        p = self.fabric_plan(); p['command'].append('-PfabricNativeCostControl=true')
        self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']})

    def single_bsl_plan(self, phase):
        p = self.bsl_renderer_plan(); p['command'].append('-PfabricRendererSinglePhase=' + phase)
        path = Path(p['profile']) / 'config/shaderlod-client.json'
        config = json.loads(path.read_text()); config['enabled'] = phase == 'on'
        path.write_text(json.dumps(config)); return p

    def test_single_bsl_window_phases_require_matching_preloaded_core(self):
        for phase in ('original', 'on', 'original-return'):
            p = self.single_bsl_plan(phase)
            self.assertEqual(self.check(p)['errors'], [], phase)
            path = Path(p['profile']) / 'config/shaderlod-client.json'
            config = json.loads(path.read_text()); config['enabled'] = not config['enabled']
            path.write_text(json.dumps(config))
            self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']}, phase)

    def test_single_bsl_window_unknown_phase_and_mixed_producers_refuse(self):
        for phase in ('', 'unknown', 'off', 'native-off-2'):
            p = self.single_bsl_plan(phase)
            self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']}, phase)
        for flag, value in [('fabricNativeCostControl', 'true'), ('fabricNativeCostControl', 'false'),
                            ('fabricRendererSourceProbe', 'true'), ('fabricOriginalSourceProbe', 'true'),
                            ('fabricRoundtripUi', 'true'), ('fabricRoundtripDh', 'true'),
                            ('fabricRendererResearchFamily', 'unknown'), ('fabricRendererSpeedProbe', 'false'),
                            ('fabricRoundtrip', 'false'), ('fabricNativeCostReload', 'true')]:
            p = self.single_bsl_plan('on')
            p['command'] = [a for a in p['command'] if not a.startswith('-P' + flag + '=')]
            p['command'].append('-P' + flag + '=' + value)
            self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']}, flag)

    def test_single_bsl_window_wrong_preset_debug_archive_and_family_refuse(self):
        for key, value in [('preset', 'QUALITY'), ('debug', True)]:
            p = self.single_bsl_plan('on'); path = Path(p['profile']) / 'config/shaderlod-client.json'
            config = json.loads(path.read_text()); config[key] = value; path.write_text(json.dumps(config))
            self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']}, key)
        p = self.single_bsl_plan('on'); Path(p['installed_pack']).write_bytes(b'foreign')
        self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']})
        p = self.fabric_plan(); p['command'].append('-PfabricRendererSinglePhase=original')
        self.assertIn('launcher_command', {e['check'] for e in self.check(p)['errors']})


if __name__ == '__main__':
    unittest.main()

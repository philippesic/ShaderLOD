import hashlib
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import benchmark_performance_stack as stack
import run_benchmark as runner


class PerformanceStackTest(unittest.TestCase):
    def test_only_g03_and_matched_originals_can_select_optional_tuple(self):
        args = dict(performance_stack='optimized', standard_test=True, candidate='public', feature='early_alpha')
        stack.validate_request(SimpleNamespace(**args))
        stack.validate_request(SimpleNamespace(**(args | {'feature': None, 'native_startup_check': True})))
        for change in ({'standard_test': False}, {'candidate': 'c02-bloom-vrs'}, {'feature': 'ao_render_scale'},
                       {'bliss_qualification': 'null'}, {'dh': True}, {'features': ['early_alpha']}, {'with_g10': True}):
            with self.assertRaises(ValueError): stack.validate_request(SimpleNamespace(**(args | change)))
        control=args | {'candidate':'native-control','feature':None}
        stack.validate_request(SimpleNamespace(**control))
        for change in ({'feature':'early_alpha'},{'features':['early_alpha']},{'dh':True},{'with_g10':True}):
            with self.assertRaises(ValueError):stack.validate_request(SimpleNamespace(**(control|change)))
        parser = runner.argument_parser()
        self.assertEqual('minimal', parser.parse_args(['--pack', 'native.zip', '--output', 'report']).performance_stack)

    def test_file_integrity_and_actual_loaded_versions_are_both_required(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'dependencies/runtime/performance').mkdir(parents=True)
            artifacts = {}
            for name in stack.OPTIONAL_IDS:
                data = name.encode()
                artifacts[name] = dict(filename=name+'.jar', size=len(data), version='1.2-neoforge', runtime_version='1.2', generated_configs=[name+'.cfg'],
                                       hashes={h: hashlib.new(h, data).hexdigest() for h in ('sha256', 'sha512')})
                (root / 'dependencies/runtime/performance' / (name+'.jar')).write_bytes(data)
            lock = dict(destination='dependencies/runtime/performance', stacks={'optimized': list(stack.OPTIONAL_IDS)}, artifacts=artifacts)
            (root / 'dependencies/performance-mods.lock.json').write_text(json.dumps(lock))
            stack.validate_cache(root, 'optimized')
            loaded = dict.fromkeys(stack.OPTIONAL_IDS, '1.2')
            self.assertEqual(loaded, stack.observe(root, 'optimized', {'loaded_stack': loaded})['observed_optional_versions'])
            for actual in ({}, loaded | {'modernfix': 'wrong'}):
                with self.assertRaises(ValueError): stack.observe(root, 'optimized', {'loaded_stack': actual})
            with self.assertRaises(ValueError): stack.observe(root, 'minimal', {'loaded_stack': loaded})
            self.assertEqual({}, stack.observe(root, 'minimal', {'loaded_stack': {'iris': 'pinned'}})['observed_optional_versions'])
            path = root / 'dependencies/runtime/performance/modernfix.jar'
            path.write_bytes(b'corrupted')
            with self.assertRaises(ValueError): stack.validate_cache(root, 'optimized')
            path.unlink()
            with self.assertRaises(FileNotFoundError): stack.validate_cache(root, 'optimized')

    def test_explicit_recovery_preserves_only_new_known_configs_and_never_existing_edits(self):
        from contextlib import nullcontext
        from unittest.mock import patch
        import benchmark_iteration as iteration
        import experiment_lifecycle as life
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            runtime = root / 'dependencies/runtime/performance'
            runtime.mkdir(parents=True)
            entries = {}
            for name in stack.OPTIONAL_IDS:
                (runtime / (name+'.jar')).write_bytes(name.encode())
                entries[name] = dict(filename=name+'.jar', generated_configs=[name+'.cfg'])
            (root / 'dependencies/performance-mods.lock.json').write_text(json.dumps(dict(
                destination='dependencies/runtime/performance', stacks={'optimized': list(stack.OPTIONAL_IDS)}, artifacts=entries)))
            source, profile, output = root/'canonical', root/'slot/client', root/'report'
            for path in [source/'config', profile/'config', output]:path.mkdir(parents=True)
            existing = profile/'config/modernfix.cfg'
            existing.write_bytes(b'original settings')
            original = iteration.profile_map(profile)
            existing.write_bytes(b'valuable changed settings')
            created = profile/'config/ferritecore.cfg'; created.write_bytes(b'new owned defaults')
            unknown = profile/'config/unrelated.cfg'; unknown.write_bytes(b'valuable unknown file')
            data = dict(plan={'performance_stack': 'optimized'}, profile=str(profile), release_verified=True,
                        configuration_restored=True, iteration_profile={'source': str(source), 'original': original},
                        inputs={str(path):life.digest(path) for path, _ in stack.artifacts(root, 'optimized')})
            op = SimpleNamespace(data=data, output=output, save=lambda: None)
            with patch.object(iteration, 'stopped_profiles', return_value=nullcontext()):
                stack.preserve_created_configs(op, root)
                stack.preserve_created_configs(op, root)  # Idempotent retention; no replay/deletion.
            self.assertFalse(created.exists())
            self.assertEqual(b'new owned defaults', (output/'preserved-config-creations/ferritecore.cfg').read_bytes())
            self.assertEqual(b'valuable changed settings', existing.read_bytes())
            self.assertEqual(b'valuable unknown file', unknown.read_bytes())
            self.assertEqual('preserved', data['optional_config_preservation'][str(created)]['status'])
            created.write_bytes(b'another writer created a file')
            with patch.object(iteration, 'stopped_profiles', return_value=nullcontext()):
                with self.assertRaisesRegex(ValueError, 'collision'):stack.preserve_created_configs(op, root)
            self.assertEqual(b'another writer created a file', created.read_bytes())
            data['release_verified'] = False
            with self.assertRaisesRegex(ValueError, 'Release'):stack.preserve_created_configs(op, root)

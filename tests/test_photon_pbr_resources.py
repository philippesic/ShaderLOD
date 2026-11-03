from pathlib import Path
import json
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import benchmark_photon_pbr_resources as probe


class PhotonPbrResourcesTest(unittest.TestCase):
    def test_bootstrap_and_qualification_are_distinct_exact_profiles(self):
        producer = probe.producer
        pack = producer.ROOT / 'dependencies/upstream/photon_v1.3b.zip'
        with tempfile.TemporaryDirectory() as tmp:
            native = Path(tmp) / 'native.txt'
            pbr = Path(tmp) / 'pbr.txt'
            native.write_text(''.join(f'{k}={v}\n' for k, v in sorted(json.loads(producer.PROFILE.read_text()).items())))
            pbr.write_text(''.join(f'{k}={v}\n' for k, v in sorted(producer.expected_options().items())))
            identity = probe.identity(pack, pbr, native)
            self.assertNotEqual(identity['qualified_original'], identity['generated'])
            self.assertEqual(identity['native_options']['POM'], 'true')
            self.assertEqual(identity['native_options']['TAA'], 'true')
            with self.assertRaises(ValueError):
                probe.identity(pack, pbr, pbr)
            state = dict(pack=identity['qualified_original'], effective_options=identity['native_options'],
                         installed=dict(master=False, flags={}), dimension='minecraft:the_nether',
                         dh_installed=False, loaded_stack={}, fallback=False, width=2560, height=1600)
            probe.activation(state, identity, expected_pack=identity['qualified_original'])
            for key, value in [('dimension', 'minecraft:overworld'), ('dh_installed', True),
                               ('fallback', True), ('width', 1920)]:
                with self.subTest(key=key), self.assertRaises(ValueError):
                    probe.activation(state | {key: value}, identity, expected_pack=identity['qualified_original'])

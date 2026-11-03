"""Private platform retargeting; does not qualify driver behavior."""
import sys
import os
import subprocess
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import build_makeup_scalar_exposure as scalar


class ScalarPlatformTest(unittest.TestCase):
    def test_only_exact_platform_guard_changes_in_real_graph(self):
        path = os.environ.get('SHADERLOD_MAKEUP_NATIVE') or str(Path(__file__).resolve().parents[1]/'dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip')
        if not path:
            self.skipTest('Set SHADERLOD_MAKEUP_NATIVE to the pinned native archive')
        native = scalar.read_archive(path)
        for diagnostic in (False, True):
            mac = scalar.patch(native, diagnostic)
            linux = scalar.patch(native, diagnostic, 'linux')
            self.assertEqual(mac.keys(), linux.keys())
            self.assertEqual(native['LICENSE'], linux['LICENSE'])
            for name, data in mac.items():
                self.assertEqual(linux[name], data.replace(scalar.BASE.encode(),
                    scalar.BASE.replace('MC_OS_MAC', 'MC_OS_LINUX').encode()), name)
                if data == native.get(name):
                    self.assertEqual(linux[name], data, name)

    def test_guard_accepts_only_target_and_retains_dependency_vetoes(self):
        for target in ('MAC', 'LINUX'):
            guard = scalar.BASE.replace('MC_OS_MAC', 'MC_OS_' + target)
            for host in ('MAC', 'LINUX', 'WINDOWS'):
                for veto in (None, 'DISTANT_HORIZONS', 'VOXY', 'VOXY_PATCH'):
                    source = '#if ' + guard + '\nADMITTED\n#else\nNATIVE\n#endif\n'
                    args = ['cpp', '-P', '-DIS_IRIS=1', '-DIRIS_VERSION=10812',
                            '-DMC_VERSION=12101', '-DMC_OS_' + host + '=1']
                    if veto:
                        args.append('-D' + veto + '=1')
                    result = subprocess.run(args, input=source, text=True,
                                            capture_output=True, check=True).stdout.strip()
                    self.assertEqual(result, 'ADMITTED' if target == host and not veto else 'NATIVE')

    def test_invalid_target_fails_before_source_transformation(self):
        with self.assertRaises(ValueError):
            scalar.patch({}, platform='windows')


if __name__ == '__main__':
    unittest.main()

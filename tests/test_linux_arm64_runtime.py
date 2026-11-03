import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('arm_runtime', Path(__file__).parents[1]/'scripts/linux_arm64_runtime.py')
runtime = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runtime)


class ArmRuntimeTests(unittest.TestCase):
    def test_full_jdk_required_and_major_checked(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name in ('bin/java', 'bin/javac', 'lib/ct.sym'):
                p=root/name;p.parent.mkdir(exist_ok=True);p.touch()
            (root/'release').write_text('JAVA_VERSION="21.0.13"\n')
            self.assertEqual(runtime.java_home(tmp), root)
            (root/'lib/ct.sym').unlink()
            with self.assertRaises(ValueError): runtime.java_home(tmp)
            (root/'lib/ct.sym').touch();(root/'release').write_text('JAVA_VERSION="17.0.1"\n')
            with self.assertRaises(ValueError): runtime.java_home(tmp)

    def test_locked_extraction_and_corruption_fail_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);jar=root/'native.jar';library=b'locked arm64 library'
            with zipfile.ZipFile(jar,'w') as z:
                z.writestr('linux/arm64/libtest.so',library)
                z.writestr('../../unsafe.so',b'ignored')
            lock={'artifacts':[{'url':'https://example.invalid/native.jar','sha256':runtime.digest(jar.read_bytes())}], 'libraries':{'libtest.so':runtime.digest(library)}}
            with patch('urllib.request.urlopen',side_effect=AssertionError('cached pin should avoid network')):
                runtime.setup(lock,root)
            self.assertEqual((root/'natives/libtest.so').read_bytes(),library)
            self.assertFalse((root/'unsafe.so').exists())
            (root/'natives/libtest.so').write_bytes(b'changed')
            with self.assertRaises(ValueError): runtime.verify_libraries(lock,root/'natives')
            (root/'natives/libtest.so').unlink();(root/'natives/libtest.so').symlink_to(jar)
            with self.assertRaises(ValueError): runtime.verify_libraries(lock,root/'natives')

    def test_path_jdk_resolution_has_useful_missing_prerequisite(self):
        with patch.object(runtime.shutil,'which',return_value=None), patch.object(runtime,'installed_jdks',return_value=[]):
            with self.assertRaisesRegex(ValueError,'pass --jdk or set JAVA_HOME'):
                runtime.java_home('')

    def test_cached_full_jdk_is_found_without_path_compiler(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            for name in ('bin/java','bin/javac','lib/ct.sym'):
                p=root/name;p.parent.mkdir(exist_ok=True);p.touch()
            (root/'release').write_text('JAVA_VERSION="21.0.13"')
            with patch.object(runtime.shutil,'which',return_value=None), patch.object(runtime,'installed_jdks',return_value=[root]):
                self.assertEqual(root,runtime.java_home(''))

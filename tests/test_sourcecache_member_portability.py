"""Compile the actual private source-member predicate without game dependencies.

prepare_fixture(path) emits a standalone Java 21 probe for a Windows JRE.
It reports the legacy Windows failure and exercises the current production body.
"""
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'src/main/java/com/pp/shaderlod/optimization/sourcecache/EarlyAlphaSourceCache.java'
LEGACY = r'''private static String legacy(String name){if(name==null||name.isBlank()||name.startsWith("/")||name.contains("..")||name.contains("\\")||!Path.of(name).normalize().toString().equals(name))throw new IllegalStateException("Unsafe Photon member/resource");return name;}'''


def prepare_fixture(destination):
    destination = Path(destination)
    destination.mkdir(parents=True, exist_ok=True)
    # Extract verbatim: the probe cannot silently test a second implementation.
    source = SOURCE.read_text()
    method = re.search(r' private static String safe\(String name\)\{[^\n]+\}', source).group(0)
    recipe = json.loads((ROOT / 'patches/photon-experiments/live-pixel-budget.json').read_text())
    valid = ['LICENSE', 'shaders/lib/common.glsl']
    for key in ('addition', 'common_addition'):
        valid += [recipe[key]['path'], recipe[key]['source_path']]
    for target, pin in recipe['additions'].items():
        valid += [target, pin['path']]
    valid += list(recipe['source_dependencies'])
    valid += [member['path'] for member in recipe['members']]
    valid = sorted(set(valid))
    literals = ','.join(json.dumps(name) for name in valid)
    harness = '''import java.nio.file.Path;
import java.util.Arrays;
public class SourceCacheMemberPortabilityProbe {
METHOD
LEGACY
 public static void main(String[] args) {
  String[] valid = new String[]{VALID};
  String[] invalid = new String[]{null,""," ","/shaders/a","//host/share/a","shaders\\\\a","shaders//a","shaders/./a","shaders/../a","./shaders/a","../a","shaders/","shaders/a..b","C:/shaders/a","C:shaders/a","shaders/\\0a"};
  for(String name:valid)if(!name.equals(safe(name)))throw new AssertionError("altered valid name: "+name);
  for(String name:invalid){boolean rejected=false;try{safe(name);}catch(IllegalStateException expected){rejected=true;}if(!rejected)throw new AssertionError("accepted unsafe name: "+name);}
  String target="shaders/lib/common.glsl";boolean legacyAccepted=true;try{legacy(target);}catch(IllegalStateException expected){legacyAccepted=false;}
  boolean windows=System.getProperty("os.name").startsWith("Windows");
  if(windows&&legacyAccepted)throw new AssertionError("legacy Windows defect not reproduced");
  if(!windows&&!legacyAccepted)throw new AssertionError("unexpected legacy non-Windows refusal");
  System.out.println("os="+System.getProperty("os.name")+"; java="+System.getProperty("java.version")+"; legacyAccepted="+legacyAccepted+"; normalized="+Path.of(target).normalize());
  System.out.println("PASS current production predicate: "+valid.length+" actual Photon/valid names; "+invalid.length+" unsafe names refused");
 }
}
'''.replace('METHOD', method).replace('LEGACY', LEGACY).replace('VALID', literals)
    target = destination / 'SourceCacheMemberPortabilityProbe.java'
    target.write_text(harness)
    return target


class SourceCacheMemberPortabilityTest(unittest.TestCase):
    def test_actual_predicate_and_pinned_photon_paths(self):
        javac = shutil.which('javac')
        java = shutil.which('java')
        self.assertIsNotNone(javac, 'JDK required')
        with tempfile.TemporaryDirectory(prefix='sourcecache-members-') as directory:
            source = prepare_fixture(directory)
            compile_result = subprocess.run([javac, '--release', '21', '-d', directory, str(source)], capture_output=True, text=True)
            self.assertEqual(0, compile_result.returncode, compile_result.stderr)
            result = subprocess.run([java, '-cp', directory, 'SourceCacheMemberPortabilityProbe'], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn('PASS current production predicate', result.stdout)


if __name__ == '__main__':
    unittest.main()

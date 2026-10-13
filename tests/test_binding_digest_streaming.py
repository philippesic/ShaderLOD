"""Standalone JVM checks of actual source-certificate stamps; no Gradle/game launch."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('stream_prepare', ROOT / 'scripts/prepare_binding_digest_streaming.py')
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)
JAVA = ROOT / 'dev/runtime/jdk25/jdk-25.0.2.jdk/Contents/Home/bin/java'
JAVAC = JAVA.with_name('javac')
SESSION = ROOT / 'dev/logs/concept-transfer-r1/bsl-neoforge-main/runtime-r1'

PROBE = r'''
import com.pp.shaderlod.integration.bindingcompat.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
public class DigestProbe {
 static String legacy(Path p)throws Exception {
  return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
 }
 public static void main(String[] args)throws Exception {
  Path p=Path.of(args[1]);
  switch(args[0]) {
   case "hash" -> System.out.println(SourceArchiveDigest.sha256(p));
   case "stamp" -> System.out.println(Class.forName("com.pp.shaderlod.integration.bindingcompat."+args[2])
       .getMethod("stamp",Path.class,String.class,String.class).invoke(null,p.getParent(),p.getFileName().toString(),p.getFileName().toString()));
   case "provider-failure" -> {
    for(var provider:Security.getProviders())Security.removeProvider(provider.getName());
    try {SourceArchiveDigest.sha256(p);throw new AssertionError("Missing SHA must refuse");}
    catch(NoSuchAlgorithmException expected){System.out.println("caught-provider-failure");}
   }
   case "allocation" -> {
    var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
    if(!bean.isThreadAllocatedMemorySupported()) {System.out.println("unsupported");return;}
    bean.setThreadAllocatedMemoryEnabled(true);long tid=Thread.currentThread().threadId();
    for(int i=0;i<5;i++){legacy(p);SourceArchiveDigest.sha256(p);}
    long oldBytes=0,newBytes=0;
    for(int i=0;i<10;i++) {
     long start=bean.getThreadAllocatedBytes(tid);String oldHash=legacy(p);oldBytes+=bean.getThreadAllocatedBytes(tid)-start;
     start=bean.getThreadAllocatedBytes(tid);String newHash=SourceArchiveDigest.sha256(p);newBytes+=bean.getThreadAllocatedBytes(tid)-start;
     if(!oldHash.equals(newHash))throw new AssertionError("Digest changed");
    }
    System.out.println(oldBytes/10+" "+newBytes/10);
   }
   default -> throw new IllegalArgumentException(args[0]);
  }
 }
}
'''


class StreamingDigestTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not JAVAC.is_file() or not (SESSION / 'actual-runClient-inputs.json').is_file():
            raise unittest.SkipTest('Pinned local JDK and authentic producer record required')
        cls.tmp = tempfile.TemporaryDirectory(prefix='stream-digest-test-')
        cls.addClassCleanup(cls.tmp.cleanup)
        cls.directory = Path(cls.tmp.name)
        cls.stage = cls.directory / 'candidate'
        cls.receipt = prepare.prepare(cls.stage)
        actual = json.loads((SESSION / 'actual-runClient-inputs.json').read_text())
        plan = json.loads((SESSION / 'plan.json').read_text())
        gson = [p for p in actual['classpath'] if Path(p).name.startswith('gson-')]
        if len(gson) != 1 or not Path(gson[0]).is_file() or not Path(plan['manualArtifact']).is_file():
            raise unittest.SkipTest('Authentic Gson/MAIN artifact unavailable')
        cls.archive = Path(plan['pack'])
        cls.classes = cls.directory / 'classes'; cls.classes.mkdir()
        harness = cls.directory / 'DigestProbe.java'; harness.write_text(PROBE)
        deps = os.pathsep.join([plan['manualArtifact'], gson[0]])
        sources = sorted(str(p) for p in (cls.stage / 'overlay').rglob('*.java'))
        result = subprocess.run([str(JAVAC), '-cp', deps, '-d', str(cls.classes), *sources, str(harness)],
                                capture_output=True, text=True, timeout=30)
        if result.returncode:
            raise AssertionError('Actual certificate compile failed: ' + result.stderr)
        cls.classpath = str(cls.classes) + os.pathsep + deps

    def java(self, mode, path, *args, success=True):
        result = subprocess.run([str(JAVA), '-cp', self.classpath, 'DigestProbe', mode, str(path), *args],
                                capture_output=True, text=True, timeout=20)
        if success:
            self.assertEqual(result.returncode, 0, result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0)
        return result.stdout.strip()

    def test_actual_three_certificates_and_chunk_boundaries(self):
        expected = hashlib.sha256(self.archive.read_bytes()).hexdigest()
        self.assertEqual(self.java('hash', self.archive), expected)
        for name in prepare.PINS:
            # The actual stamp API accepts a path and hashes it independently of
            # later family certification; this is not an admission assertion.
            self.assertEqual(self.java('stamp', self.archive, name[:-5]), '[' + expected + ', ' + expected + ']')
        for size in (0, 65535, 65536, 65537, 131089):
            with self.subTest(size=size):
                p = self.directory / ('size-' + str(size)); data = bytes(i % 251 for i in range(size)); p.write_bytes(data)
                self.assertEqual(self.java('hash', p), hashlib.sha256(data).hexdigest())

    def test_same_size_same_mtime_mutation_and_read_failure(self):
        p = self.directory / 'changed.zip'; shutil.copy2(self.archive, p)
        before = p.stat(); original = self.java('stamp', p, 'BslSourceCertificate')
        data = bytearray(p.read_bytes()); data[len(data)//2] ^= 1; p.write_bytes(data)
        os.utime(p, ns=(before.st_atime_ns, before.st_mtime_ns))
        self.assertEqual(p.stat().st_size, before.st_size)
        self.assertEqual(p.stat().st_mtime_ns, before.st_mtime_ns)
        self.assertNotEqual(self.java('stamp', p, 'BslSourceCertificate'), original)
        expected = hashlib.sha256(data).hexdigest()
        self.assertEqual(self.java('stamp', p, 'BslSourceCertificate'), '[' + expected + ', ' + expected + ']')
        self.java('hash', self.directory / 'absent', success=False)
        self.java('hash', self.directory, success=False)
        self.assertEqual(self.java('provider-failure', p), 'caught-provider-failure')

    def test_exact_overlay_scope_and_no_overwrite(self):
        self.assertEqual(len(self.receipt['outputs']), 4)
        for name, digest in self.receipt['inputs'].items():
            actual = (ROOT / name).read_bytes()
            self.assertEqual(hashlib.sha256(actual).hexdigest(), digest)
            candidate = (self.stage / 'overlay' / name).read_bytes()
            self.assertEqual(candidate.replace(prepare.AFTER.encode(), prepare.BEFORE.encode(), 1), actual)
        patch = self.stage / 'integration.patch'
        result = subprocess.run(['git', 'apply', '--check', str(patch)], cwd=ROOT, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        before = patch.read_bytes()
        with self.assertRaisesRegex(ValueError, 'already exists'):
            prepare.prepare(self.stage)
        self.assertEqual(patch.read_bytes(), before)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            helper = root / 'scripts/java/SourceArchiveDigest.java'; helper.parent.mkdir(parents=True)
            helper.write_bytes((ROOT / 'scripts/java/SourceArchiveDigest.java').read_bytes())
            for name in prepare.PINS:
                p = root / prepare.PACKAGE / name; p.parent.mkdir(parents=True, exist_ok=True)
                p.write_bytes((ROOT / prepare.PACKAGE / name).read_bytes() + b'\n')
            out = root / 'refused'
            with self.assertRaisesRegex(ValueError, 'certificate changed'):
                prepare.prepare(out, root)
            self.assertFalse(out.exists())

    def test_actual_archive_temporary_allocation_reduced(self):
        result = self.java('allocation', self.archive)
        if result == 'unsupported':
            self.skipTest('JVM per-thread allocation accounting unavailable')
        old, new = map(int, result.split())
        self.assertGreater(old, self.archive.stat().st_size)
        self.assertLess(new, 131072, 'Stream should not allocate an archive-sized temporary')
        print(f'BSL bytes allocated per full hash: legacy={old}, streamed={new}; no FPS/timing claim')


if __name__ == '__main__':
    unittest.main()

import importlib.util
import json
import os
import hashlib
import difflib
from pathlib import Path
import shutil
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('installer', ROOT / 'scripts/install_renderer_rate_patch.py')
i = importlib.util.module_from_spec(spec)
spec.loader.exec_module(i)


class PatchInstallTests(unittest.TestCase):
    def test_exact_patch_apply_and_source_drift_rejection(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d) / 'target'
            root.mkdir()
            fixture = os.environ.get('SHADER_RATE_SOURCE_FIXTURE')
            artifact = i.ARTIFACT if fixture else Path(d) / 'artifact'
            if not fixture:
                artifact.mkdir()
                fixture = Path(d) / 'fixture'
                fixture.mkdir()
                name = 'ports/26.2/fabric/source.java'
                before, after = 'class Counter { int cap=120; }\n', 'class Counter { int cap=260; }\n'
                p = fixture / name
                p.parent.mkdir(parents=True)
                p.write_text(before)
                hashes = {name: {'beforeSha256': hashlib.sha256(before.encode()).hexdigest(),
                                 'afterSha256': hashlib.sha256(after.encode()).hexdigest()}}
                (artifact / 'baseline-sha256.json').write_text(json.dumps(hashes))
                (artifact / 'consumer.patch').write_text(''.join(difflib.unified_diff(
                    before.splitlines(True), after.splitlines(True), fromfile='a/'+name, tofile='b/'+name)))
            manifest = json.loads((artifact / 'baseline-sha256.json').read_text())
            for name in manifest:
                p = root / name
                p.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(Path(fixture) / name, p)
            release = root / 'release.json'
            release.write_text(json.dumps({'zeroClients': True, 'runtimeLockFree': True, 'errors': []}))
            self.assertFalse(i.install(root, artifact=artifact)['applied'])
            with self.assertRaises(ValueError):
                i.install(root, artifact=artifact, apply=True)
            release.write_text(json.dumps({'zeroClients': False, 'runtimeLockFree': True, 'errors': []}))
            with self.assertRaises(ValueError):
                i.install(root, artifact=artifact, apply=True, release=release)
            release.write_text(json.dumps({'zeroClients': True, 'runtimeLockFree': True, 'errors': [],
                                           'releaseErrors': ['Client stop failed']}))
            with self.assertRaises(ValueError):
                i.install(root, artifact=artifact, apply=True, release=release)
            # Actual262 release contract: startup trial failed, exact stop/lock release succeeded.
            failed_trial = {'observedUtc': '2026-10-11T03:35:01Z', 'zeroClients': True,
                            'runtimeLockFree': True, 'ownerStatus': 'startup-failed',
                            'errors': ['java.lang.IllegalStateException: Backup prompt native viewport/focus gate failed'],
                            'reportStatus': 'failed'}
            release.write_text(json.dumps(failed_trial))
            retained_release = release.read_bytes()
            result = i.install(root, artifact=artifact, apply=True, release=release)
            self.assertTrue(result['applied'])
            self.assertEqual(release.read_bytes(), retained_release)
            self.assertEqual(result['priorTrialErrors'], failed_trial['errors'])
            self.assertEqual(result['priorTrialReportStatus'], 'failed')
            self.assertTrue(result['requiresFreshOwnerCensusAndRelease'])
            self.assertFalse(result['exactOwnerProcessBindingVerified'])
            self.assertFalse(result['liveCensusVerified'])
            for name, hashes in manifest.items():
                self.assertEqual(i.sha(root / name), hashes['afterSha256'])
            self.assertEqual(i.sha(root / 'scripts/qualify_renderer_bracket.py'), i.sha(i.QUALIFIER))
            with self.assertRaises(ValueError):
                i.install(root, artifact=artifact)
            # Wrong preimage must fail before any other file mutation.
            first = next(iter(manifest))
            (root / first).write_text('owner update')
            with self.assertRaises(ValueError):
                i.install(root, artifact=artifact)

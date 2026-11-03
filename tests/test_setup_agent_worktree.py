"""Worktree readiness uses actual lock format without global skills or downloads."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class WorktreeSetup(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name).resolve() / "checkout"
        self.root.mkdir()
        subprocess.run(["git", "init", "--quiet", str(self.root)], check=True)
        for relative in ("scripts/setup_agent_worktree.py", "gradlew", "gradle/wrapper/gradle-wrapper.jar",
                         "gradle/wrapper/gradle-wrapper.properties", "build.gradle", "settings.gradle",
                         "dependencies/runtime.lock.json"):
            destination = self.root / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / relative, destination)
        self.env = dict(os.environ, HOME=str(self.root / "empty-home"), PYTHONPATH="", PYTHONDONTWRITEBYTECODE="1")

    def tearDown(self):
        self.temporary.cleanup()

    def run_setup(self, *arguments, expected=0):
        result = subprocess.run([sys.executable, "scripts/setup_agent_worktree.py", *arguments],
                                cwd=self.root, env=self.env, capture_output=True, text=True, timeout=25)
        self.assertEqual(result.returncode, expected, result.stderr)
        return json.loads(result.stdout)

    def test_clean_checkout_reports_missing_dependencies_and_is_idempotent(self):
        first = self.run_setup()
        self.assertTrue(first["tooling_ready"])
        self.assertFalse(first["build_inputs_ready"])
        self.assertEqual(first["compilation"], "not run")
        self.assertTrue(all(a["status"] == "missing" for a in first["runtime_artifacts"]))
        self.assertEqual(first, self.run_setup())
        self.run_setup("--require-build-inputs", expected=2)
        self.assertFalse((self.root / "dependencies/runtime").exists())

    def test_explicit_source_copies_only_verified_missing_inputs_never_overwrites(self):
        # Fixture bytes exercise checksum transport, not authentic shader/runtime qualification.
        lock_path = self.root / "dependencies/runtime.lock.json"
        lock = json.loads(lock_path.read_text())
        source = self.root.parent / "explicit-runtime"
        source.mkdir()
        for name in ("iris", "sodium"):
            data = ("transport fixture " + name).encode()
            (source / lock["artifacts"][name]["filename"]).write_bytes(data)
            lock["artifacts"][name]["hashes"]["sha512"] = hashlib.sha512(data).hexdigest()
        lock_path.write_text(json.dumps(lock))
        copied = self.run_setup("--runtime-source", str(source))
        self.assertTrue(all(a["status"] == "verified" for a in copied["runtime_artifacts"]))
        self.assertEqual(copied, self.run_setup("--runtime-source", str(source)))
        target = Path(copied["runtime_artifacts"][0]["path"])
        target.write_bytes(b"unrelated existing bytes")
        rejected = self.run_setup("--runtime-source", str(source))
        self.assertFalse(rejected["build_inputs_ready"])
        self.assertEqual(target.read_bytes(), b"unrelated existing bytes")

    def test_bad_explicit_source_is_reported_without_partial_install(self):
        source = self.root.parent / "wrong-runtime"
        source.mkdir()
        lock = json.loads((self.root / "dependencies/runtime.lock.json").read_text())
        for name in ("iris", "sodium"):
            (source / lock["artifacts"][name]["filename"]).write_bytes(b"wrong bytes")
        report = self.run_setup("--runtime-source", str(source), "--require-build-inputs", expected=2)
        self.assertTrue(any("checksum mismatch" in reason for reason in report["missing"]))
        self.assertFalse((self.root / "dependencies/runtime").exists())


if __name__ == "__main__":
    unittest.main()

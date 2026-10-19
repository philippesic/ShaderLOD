"""Portable entry-point checks against isolated copies of authentic project state."""
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


class ProjectCheckpoint(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name).resolve()
        (self.root / "scripts").mkdir()
        shutil.copytree(ROOT / "scripts/agent_runtime", self.root / "scripts/agent_runtime",
                        ignore=shutil.ignore_patterns("__pycache__"))
        shutil.copy2(ROOT / "scripts/project_checkpoint.py", self.root / "scripts/project_checkpoint.py")
        (self.root / "docs").mkdir()
        self.status = self.root / "docs/STATUS.md"
        self.original = (ROOT / "docs/STATUS.md").read_bytes()
        self.status.write_bytes(self.original)
        self.env = dict(os.environ, HOME=str(self.root / "empty-home"), PYTHONPATH="", PYTHONDONTWRITEBYTECODE="1")

    def tearDown(self):
        self.temporary.cleanup()

    def cli(self, action, *arguments, okay=True):
        proc = subprocess.run([sys.executable, "scripts/project_checkpoint.py", action,
                               "docs/STATUS.md", *arguments], cwd=self.root, env=self.env,
                              capture_output=True, text=True, timeout=5)
        self.assertEqual(proc.returncode, 0 if okay else 1, proc.stderr)
        return json.loads(proc.stdout if okay else proc.stderr)

    def incoming(self):
        facts = self.cli("show", "--json")["facts"]
        facts["status"] = "paused"
        if facts["schema_version"] == 2:
            facts["progress"]["activity"] = "idle"
        incoming = self.root / "incoming.json"
        incoming.write_text(json.dumps(facts))
        return incoming

    def test_read_dryrun_update_preserves_schema_and_exact_history(self):
        current = self.cli("show", "--json")
        self.assertEqual(self.cli("lint")["facts"], current["facts"])
        incoming = self.incoming()
        arguments = ("--input", str(incoming), "--expected-sha", current["sha256"])
        preview = self.cli("update", *arguments)
        self.assertFalse(preview["applied"])
        self.assertEqual(self.status.read_bytes(), self.original)
        self.assertEqual(list(self.status.parent.glob("STATUS.history.*")), [])
        updated = self.cli("update", *arguments, "--apply")
        self.assertTrue(updated["applied"])
        self.assertEqual(Path(updated["archive"]).read_bytes(), self.original)
        facts = self.cli("show", "--json")["facts"]
        self.assertEqual(facts["schema_version"], current["facts"]["schema_version"])
        self.assertEqual(facts["progress"]["allocation_seconds"], current["facts"]["progress"]["allocation_seconds"])

    def test_stale_sha_and_foreign_lock_refuse_without_mutation(self):
        incoming = self.incoming()
        failure = self.cli("update", "--input", str(incoming), "--expected-sha", "0" * 64,
                           "--apply", okay=False)
        self.assertIn("mismatch", failure["error"])
        lock = self.status.parent / ".STATUS.md.checkpoint.lock"
        lock.write_text("another cooperative writer")
        self.cli("update", "--input", str(incoming), "--expected-sha",
                 hashlib.sha256(self.original).hexdigest(), "--apply", okay=False)
        self.assertEqual(lock.read_text(), "another cooperative writer")
        self.assertEqual(self.status.read_bytes(), self.original)

    def test_project_hook_is_compact_read_only_without_global_skill(self):
        hook = self.root / ".codex/hooks/compaction_handoff.py"
        hook.parent.mkdir(parents=True)
        shutil.copy2(ROOT / ".codex/hooks/compaction_handoff.py", hook)
        facts = self.cli("show", "--json")["facts"]
        for source in ("startup", "resume", "compact", "fork"):
            with self.subTest(source=source):
                event = {"hook_event_name": "SessionStart", "source": source}
                proc = subprocess.run([sys.executable, str(hook)], input=json.dumps(event),
                                      cwd=self.root, env=self.env, capture_output=True, text=True, timeout=5)
                self.assertEqual(proc.returncode, 0, proc.stderr)
                message = json.loads(proc.stdout)["hookSpecificOutput"]["additionalContext"]
                self.assertIn(facts["observed_at"], message)
                self.assertIn("scripts/project_checkpoint.py", message)
                self.assertIn("recorded owner", message)
                self.assertLess(len(message), 850)
                self.assertEqual(self.status.read_bytes(), self.original)


if __name__ == "__main__":
    unittest.main()

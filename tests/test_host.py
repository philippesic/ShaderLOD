from dataclasses import replace
import json
from pathlib import Path
import subprocess
import sys

import pytest

from shaderlod_host.contracts import Request, decode_request, digest, integer
from shaderlod_host.runtime import (
    execute,
    validate_control,
    verify_result,
    recover_execution,
)
from shaderlod_host.storage import BusyRefusal, publish, reclaim, recover_journal


def request(root: Path, code: str = "pass", timeout_ms: int = 1000) -> Request:
    program = root / "program.py"
    program.write_text(code)
    return decode_request(
        {
            "schema": "shaderlod-request-v1",
            "operation": "op",
            "attempt": 1,
            "owner": "test-owner",
            "purpose": "cpu-verify",
            "argv": [sys.executable, "program.py"],
            "source": {"program.py": digest(program.read_bytes())},
            "instrumentation": "none",
            "runtime_sha256": digest(Path(sys.executable).read_bytes()),
            "timeout_ms": timeout_ms,
            "max_attempts": 2,
            "cumulative_ms": 2000,
        }
    )


def test_closed_wire_and_identity(tmp_path: Path) -> None:
    original = request(tmp_path)
    assert decode_request(original.wire()) == original
    for update in (
        {"attempt": True},
        {"schema": "unknown"},
        {"timeout_ms": False},
        {"purpose": "live"},
        {"unknown": 1},
        {"source": {"../escape": "0" * 64}},
    ):
        with pytest.raises(ValueError):
            decode_request(original.wire() | update)


def test_process_execution_and_result_agreement(tmp_path: Path) -> None:
    req = request(tmp_path)
    result = execute(req, tmp_path, tmp_path / "results", tmp_path / "owners")
    assert result["state"] == "complete"
    assert verify_result(result, req) == result
    with pytest.raises(ValueError, match="identity disagreement"):
        verify_result(result, replace(req, instrumentation="diagnostic"))
    with pytest.raises(ValueError):
        verify_result(result | {"schema": "unknown"}, req)
    with pytest.raises(ValueError, match="false completion"):
        verify_result(result | {"returncode": 1}, req)


def test_deadline_and_failed_attempt_budget_persist(tmp_path: Path) -> None:
    req = request(tmp_path, "import time\ntime.sleep(5)", 80)
    req = replace(req, cumulative_ms=80)
    result = execute(req, tmp_path, tmp_path / "results", tmp_path / "owners")
    assert result["state"] == "failed"
    assert result["reason"] == "deadline exhausted"
    assert result["returncode"] is not None
    with pytest.raises(ValueError, match="budget exhausted"):
        execute(
            replace(req, attempt=2), tmp_path, tmp_path / "results", tmp_path / "owners"
        )
    assert (tmp_path / "results/op/attempt-1.json").exists()


def test_nonblocking_exclusion_and_crash_release(tmp_path: Path) -> None:
    namespace = tmp_path / "owners"
    with BusyRefusal(namespace, "host/device"):
        with pytest.raises(RuntimeError, match="busy"):
            with BusyRefusal(namespace, "host/device"):
                pytest.fail("concurrent admission")
    with BusyRefusal(namespace, "host/device"):
        pass
    code = "from pathlib import Path; from shaderlod_host.storage import BusyRefusal; import os; lock=BusyRefusal(Path(__import__('sys').argv[1]), 'host/device'); lock.__enter__(); os._exit(0)"
    subprocess.run([sys.executable, "-c", code, str(namespace)], check=True)
    with BusyRefusal(namespace, "host/device"):
        pass


def test_control_replay_foreign_and_cancel(tmp_path: Path) -> None:
    req = request(tmp_path, "import time\ntime.sleep(5)")
    control = {
        "schema": "shaderlod-control-v1",
        "operation": "op",
        "attempt": 1,
        "owner": "test-owner",
        "action": "cancel",
        "sequence": 1,
    }
    assert validate_control(control, req, 0) == 1
    for change in (
        {"owner": "foreign"},
        {"attempt": 2},
        {"sequence": 0},
        {"schema": "unknown"},
    ):
        with pytest.raises(ValueError):
            validate_control(control | change, req, 0)
    with pytest.raises(ValueError, match="replayed"):
        validate_control(control, req, 1)
    path = tmp_path / "control.json"
    path.write_text(json.dumps(control))
    result = execute(req, tmp_path, tmp_path / "results", tmp_path / "owners", path)
    assert result["state"] == "cancelled"


def test_source_drift_and_attempt_replay_refused(tmp_path: Path) -> None:
    req = request(tmp_path)
    execute(req, tmp_path, tmp_path / "results", tmp_path / "owners")
    with pytest.raises(ValueError, match="stale attempt"):
        execute(req, tmp_path, tmp_path / "results", tmp_path / "owners")
    (tmp_path / "program.py").write_text("changed")
    with pytest.raises(ValueError, match="identity drift"):
        execute(
            replace(req, attempt=2), tmp_path, tmp_path / "results", tmp_path / "owners"
        )


def test_atomic_publication_failure_and_exclusive_create(tmp_path: Path) -> None:
    path = tmp_path / "result.json"
    with pytest.raises(TypeError):
        publish(path, {"unsupported": object()})
    assert not path.exists()
    publish(path, {"value": 1})
    with pytest.raises(FileExistsError):
        publish(path, {"value": 2})
    assert json.loads(path.read_bytes()) == {"value": 1}
    assert not list(tmp_path.glob(".publish-*"))


def test_recovery_is_idempotent_and_preserves_budget(tmp_path: Path) -> None:
    path = tmp_path / "journal.json"
    record = {
        "schema": "shaderlod-journal-v1",
        "owner": "owner",
        "state": "running",
        "request": "identity",
        "reserved_ms": 250,
        "attempt": 1,
        "process": None,
    }
    publish(path, record)
    restored = recover_journal(path, "owner")
    assert restored["state"] == "recovery-required"
    assert restored["reserved_ms"] == 250
    assert recover_journal(path, "owner") == restored
    with pytest.raises(ValueError, match="foreign"):
        recover_journal(path, "other")


def test_reclaim_classification_identity_and_symlink(tmp_path: Path) -> None:
    root = tmp_path / "owned"
    root.mkdir()
    candidate = root / "scratch"
    candidate.write_bytes(b"disposable")
    desc = {
        "schema": "shaderlod-reclaim-v1",
        "owner": "task",
        "class": "disposable",
        "sha256": digest(candidate.read_bytes()),
        "active": False,
        "dependencies": [],
        "rebuild": "test fixture",
    }
    for change in (
        {"active": True},
        {"class": "retained"},
        {"dependencies": ["result"]},
        {"sha256": "0" * 64},
    ):
        with pytest.raises(ValueError):
            reclaim(root, candidate, desc | change, tmp_path / "receipt.json")
    alias = root / "alias"
    alias.symlink_to(candidate)
    with pytest.raises(ValueError, match="escapes"):
        reclaim(root, alias, desc, tmp_path / "receipt.json")
    assert reclaim(root, candidate, desc, tmp_path / "receipt.json") == 10
    assert not candidate.exists()
    assert (
        json.loads((tmp_path / "receipt.complete.json").read_bytes())["status"]
        == "deleted"
    )


def test_runner_death_stops_owned_group_without_foreign_kill(tmp_path: Path) -> None:
    import time

    if sys.platform != "linux":
        pytest.fail("supported Linux supervision test requires Linux")
    marker = tmp_path / "spawned.json"
    code = (
        "import json,os,subprocess,sys,time\n"
        "child=subprocess.Popen([sys.executable,'-c','import time;time.sleep(10)'])\n"
        f"open({str(marker)!r},'w').write(json.dumps([os.getpid(), child.pid]))\n"
        "time.sleep(10)\n"
    )
    req = request(tmp_path, code, 2000)
    req = replace(req, cumulative_ms=4000)
    request_file = tmp_path / "request.json"
    request_file.write_text(json.dumps(req.wire()))
    runner = subprocess.Popen(
        [
            sys.executable,
            "-m",
            "shaderlod_host",
            "run",
            "--request",
            str(request_file),
            "--source-root",
            str(tmp_path),
            "--output-root",
            str(tmp_path / "results"),
            "--ownership-root",
            str(tmp_path / "owners"),
        ],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    foreign = subprocess.Popen([sys.executable, "-c", "import time;time.sleep(10)"])
    try:
        deadline = time.monotonic() + 3
        while not marker.exists() and time.monotonic() < deadline:
            time.sleep(0.01)
        assert marker.exists()
        owned = json.loads(marker.read_bytes())
        runner.kill()
        runner.wait(timeout=1)
        journal = recover_journal(tmp_path / "results/op/journal.json", req.owner)
        watchdog = journal["process"]
        assert isinstance(watchdog, dict)
        owned.append(watchdog["pid"])
        deadline = time.monotonic() + 2
        while time.monotonic() < deadline:
            alive = []
            for pid in owned:
                try:
                    value = Path(f"/proc/{pid}/stat").read_text()
                except (FileNotFoundError, ProcessLookupError):
                    continue
                if value[value.rfind(")") + 2 :].split()[0] != "Z":
                    alive.append(pid)
            if not alive:
                break
            time.sleep(0.01)
        assert not alive
        assert foreign.poll() is None
        journal = recover_journal(tmp_path / "results/op/journal.json", req.owner)
        assert journal["state"] == "recovery-required"
        with pytest.raises(ValueError, match="recovery-required"):
            execute(
                replace(req, attempt=2),
                tmp_path,
                tmp_path / "results",
                tmp_path / "owners",
            )
        receipt = recover_execution(req, tmp_path / "results", tmp_path / "owners")
        assert receipt["state"] == "interrupted"
        assert receipt["reserved_ms"] == 2000
        assert (
            recover_execution(req, tmp_path / "results", tmp_path / "owners") == receipt
        )
        child_path = tmp_path / "results/op/process-1.json"
        original_child = json.loads(child_path.read_bytes())
        child_path.write_text(json.dumps(original_child | {"boot": "foreign"}))
        with pytest.raises(ValueError, match="unverifiable"):
            recover_execution(req, tmp_path / "results", tmp_path / "owners")
        child_path.write_text(json.dumps(original_child))
        resumed = execute(
            replace(req, attempt=2), tmp_path, tmp_path / "results", tmp_path / "owners"
        )
        assert resumed["state"] == "failed"
        assert integer(resumed["reserved_ms"], "reserved_ms", 0) >= 4000
        assert foreign.poll() is None
    finally:
        if runner.poll() is None:
            runner.kill()
        runner.wait(timeout=1)
        foreign.terminate()
        foreign.wait(timeout=1)


def test_snapshot_executes_frozen_inputs_and_checks_runtime(tmp_path: Path) -> None:
    req = request(tmp_path)
    result = execute(req, tmp_path, tmp_path / "results", tmp_path / "owners")
    frozen = tmp_path / "results/op/snapshot-1/program.py"
    assert frozen.read_bytes() == (tmp_path / "program.py").read_bytes()
    assert result["runtime_sha256"] == digest(Path(sys.executable).read_bytes())
    with pytest.raises(ValueError, match="runtime executable identity"):
        execute(
            replace(req, runtime_sha256="0" * 64),
            tmp_path,
            tmp_path / "other",
            tmp_path / "owners",
        )


def test_world_copy_preserves_source_and_refuses_unsafe_reuse(tmp_path: Path) -> None:
    from shaderlod_host.storage import copytree

    source = tmp_path / "original"
    source.mkdir()
    (source / "level.dat").write_bytes(b"frozen-world")
    (source / "session.lock").write_bytes(b"not-an-input")
    target = tmp_path / "copy"
    copytree(source, target, 0)
    assert (target / "level.dat").read_bytes() == b"frozen-world"
    assert not (target / "session.lock").exists()
    (target / "level.dat").write_bytes(b"changed-world")
    assert (source / "level.dat").read_bytes() == b"frozen-world"
    with pytest.raises(ValueError):
        copytree(source, target, 0)
    (source / "escape").symlink_to(tmp_path)
    with pytest.raises(ValueError, match="symlink"):
        copytree(source, tmp_path / "unsafe", 0)


def test_input_identity_excludes_assets_but_includes_classpath(tmp_path: Path) -> None:
    from shaderlod_host.runtime_live import executable_inputs
    import os

    assets = tmp_path / "assets"
    classes = tmp_path / "classes"
    assets.mkdir()
    classes.mkdir()
    (assets / "large-world-input").write_text("assets")
    (classes / "Executed.class").write_bytes(b"code")
    legacy = tmp_path / "build/moddev/developmentLegacyClasspath.txt"
    legacy.parent.mkdir(parents=True)
    legacy.write_text("")
    args = tmp_path / "arguments.txt"
    args.write_text("-cp " + str(classes) + os.pathsep + str(assets / "missing.jar"))
    observed = executable_inputs(
        {"argv": ["java", "@" + str(args), "--assetsDir", str(assets)]}, tmp_path
    )
    assert str(classes / "Executed.class") in observed
    assert str(assets / "large-world-input") not in observed
    assert str(args) in observed

import hashlib
from pathlib import Path

import pytest

from shaderlod_host.runtime_live import (
    attempt_receipt,
    preserve_outputs,
    read_functional,
)


def test_default_receipt_is_contained_and_ignores_global_data_home(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setenv("XDG_DATA_HOME", str(tmp_path / "data"))
    repository = tmp_path / "repo"
    repository.mkdir()
    receipt = attempt_receipt(None, repository, "without-dh")
    assert receipt.parent == repository / ".local/artifacts/attempts"
    assert receipt.name.endswith("-without-dh.json")
    assert receipt.parent.is_dir()


def test_receipt_inside_repository_refuses(tmp_path: Path) -> None:
    with pytest.raises(ValueError, match="require .local/artifacts"):
        attempt_receipt(tmp_path / "build/reports/live/a.json", tmp_path, "dh")


def test_artifact_symlink_cannot_redirect_into_source(tmp_path: Path) -> None:
    source = tmp_path / "src"
    source.mkdir()
    (tmp_path / ".local").mkdir()
    (tmp_path / ".local/artifacts").symlink_to(source, target_is_directory=True)
    with pytest.raises(ValueError, match="require .local/artifacts"):
        attempt_receipt(tmp_path / ".local/artifacts/attempt.json", tmp_path, "dh")
    assert not (source / "attempt.json").exists()


def test_existing_receipt_refuses(tmp_path: Path) -> None:
    existing = tmp_path / "evidence/a.json"
    existing.parent.mkdir()
    existing.write_text("{}")
    with pytest.raises(ValueError, match="already exists"):
        attempt_receipt(existing, tmp_path / "repo", "dh")


def test_probe_outputs_move_beside_receipt(tmp_path: Path) -> None:
    profile = tmp_path / "profile"
    profile.mkdir()
    (profile / "shaderlod-functional.json").write_bytes(b"{}")
    (profile / "shaderlod-functional.png").write_bytes(b"image")
    (profile / "options.txt").write_bytes(b"keep")
    destination = tmp_path / "evidence/attempt"
    preserved = preserve_outputs(profile, destination)
    assert preserved == {
        "shaderlod-functional.json": hashlib.sha256(b"{}").hexdigest(),
        "shaderlod-functional.png": hashlib.sha256(b"image").hexdigest(),
    }
    assert sorted(p.name for p in profile.iterdir()) == ["options.txt"]
    assert (destination / "shaderlod-functional.png").read_bytes() == b"image"


@pytest.mark.parametrize("payload", [b"{broken", b"[]", b"true", b"123"])
def test_malformed_probe_remains_available_for_failure_receipt(
    tmp_path: Path, payload: bytes
) -> None:
    source = tmp_path / "shaderlod-functional.json"
    source.write_bytes(payload)
    observed, failure = read_functional(source)
    assert observed is None and failure
    destination = tmp_path / "preserved"
    preserve_outputs(tmp_path, destination)
    assert (destination / source.name).read_bytes() == payload


def test_relative_session_paths_remain_valid_in_profile_working_directory(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    from contextlib import nullcontext
    import json
    import subprocess
    import zipfile
    from shaderlod_host import runtime_live, storage

    root = tmp_path / "repo"
    root.mkdir()
    profile = root / "profile"
    (profile / "saves/world").mkdir(parents=True)
    shipping = root / "shipping.jar"
    with zipfile.ZipFile(shipping, "w"):
        pass
    program = root / "program.txt"
    program.write_text('--quickPlaySingleplayer "previous"')
    launch = root / "launch.json"
    launch.write_text(
        json.dumps(
            {
                "argv": ["java", "-cp", "classes", "@" + str(program)],
                "environment": {},
                "shipping_jar": str(shipping),
            }
        )
    )
    commands: list[tuple[list[str], dict[str, object]]] = []

    class CompletedWorker:
        def __init__(self, argv: list[str], **kwargs: object) -> None:
            commands.append((argv, kwargs))

        def wait(self, timeout: float) -> int:
            return 0

        def poll(self) -> int:
            return 0

    monkeypatch.chdir(root)
    monkeypatch.setattr(
        runtime_live, "__file__", str(root / "shaderlod_host/runtime_live.py")
    )
    monkeypatch.setattr(runtime_live, "display_environment", lambda path: {})
    monkeypatch.setattr(runtime_live, "executable_inputs", lambda *args: {})
    monkeypatch.setattr(runtime_live, "profile_artifact", lambda *args: nullcontext())
    monkeypatch.setattr(
        storage,
        "snapshot_inputs",
        lambda source, target: target.write_bytes(b"snapshot"),
    )
    monkeypatch.setattr(subprocess, "Popen", CompletedWorker)
    session = Path(".local/artifacts/session")
    assert (
        runtime_live.benchmark_session(
            launch,
            profile,
            root / "display",
            session,
            root / ".local/ownership",
            60,
            "world",
        )
        == 0
    )
    argv, options = commands[0]
    java = json.loads(argv[6])
    assert "@" + str(root / session / "world-program-args.txt") in java
    assert argv[7] == str(root / session / "child.json")
    assert options["cwd"] == profile
    assert (root / session / "world-program-args.txt").is_file()

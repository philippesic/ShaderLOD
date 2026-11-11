import hashlib
from pathlib import Path

import pytest

from shaderlod_host.runtime_live import attempt_receipt, preserve_outputs


def test_default_receipt_is_outside_repository(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setenv("XDG_DATA_HOME", str(tmp_path / "data"))
    repository = tmp_path / "repo"
    repository.mkdir()
    receipt = attempt_receipt(None, repository, "without-dh")
    assert receipt.parent == tmp_path / "data/shaderlod-next/evidence/attempts"
    assert receipt.name.endswith("-without-dh.json")
    assert receipt.parent.is_dir()


def test_receipt_inside_repository_refuses(tmp_path: Path) -> None:
    with pytest.raises(ValueError, match="outside the repository"):
        attempt_receipt(tmp_path / "build/reports/live/a.json", tmp_path, "dh")


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

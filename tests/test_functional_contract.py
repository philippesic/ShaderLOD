from pathlib import Path
import pytest
from shaderlod_host.contracts import digest
from shaderlod_host.functional_contract import verify_functional


def fixture(root: Path) -> tuple[dict[str, object], str]:
    packs = root / "shaderpacks"
    packs.mkdir()
    (packs / "native.zip").write_bytes(b"native")
    (packs / "derived.zip").write_bytes(b"derived")
    (root / "shaderlod-functional.png").write_bytes(b"image")
    (root / "shaderlod-functional-identity.png").write_bytes(b"identity image")
    sha = digest(b"native")

    def active(name: str, payload: bytes, generation: int) -> dict[str, object]:
        value = digest(payload)
        return {
            "packName": name,
            "archiveSha256": value,
            "generation": generation,
            "linked": True,
            "effectiveOptions": {"TAA": "true"},
            "witnesses": ["linked-archive:" + value, "submitted-frame:" + value],
        }

    return {
        "schema": "shaderlod-functional-v3",
        "scope": "functional-only",
        "source": "native.zip",
        "dh": {"present": True, "enabled": True},
        "profiler": {},
        "native_width": 2560,
        "native_height": 1600,
        "native_rendered_sections": 3,
        "identity_rendered_sections": 3,
        "outcome": "PASS",
        "native_activation": active("native.zip", b"native", 1),
        "selection": active("native.zip", b"native", 2),
        "identity_probe": active("derived.zip", b"derived", 4),
        "recovery_activation": active("native.zip", b"native", 8),
        "cancel_staged_unchanged": True,
        "cancel_preserved_active": True,
        "off_restored": True,
        "options_preserved": True,
        "failed_apply_restored": True,
        "failed_apply_reason": "Apply failed: Iris rejected the shader graph",
        "identity_output_sha256": digest(b"identity image"),
        "restored_state": {
            "pack": "native.zip",
            "enabled": True,
            "queued": {},
            "effective": {"TAA": "true"},
        },
        "image_has_variation": True,
        "rendered_output_sha256": digest(b"image"),
    }, sha


def test_success_requires_observed_joins_not_only_producer_flag(tmp_path: Path) -> None:
    record, sha = fixture(tmp_path)
    assert verify_functional(record, tmp_path, "native.zip", sha, True) == record
    record["identity_probe"] = {"linked": True}
    with pytest.raises(ValueError, match="activation"):
        verify_functional(record, tmp_path, "native.zip", sha, True)


def test_unknown_or_contradictory_receipt_refuses(tmp_path: Path) -> None:
    record, sha = fixture(tmp_path)
    record["schema"] = "future-version"
    with pytest.raises(ValueError, match="schema"):
        verify_functional(record, tmp_path, "native.zip", sha, True)
    record["schema"] = "shaderlod-functional-v3"
    record["image_has_variation"] = False
    with pytest.raises(ValueError, match="output"):
        verify_functional(record, tmp_path, "native.zip", sha, True)


@pytest.mark.parametrize(
    "key",
    [
        "cancel_staged_unchanged",
        "cancel_preserved_active",
        "off_restored",
        "options_preserved",
        "failed_apply_restored",
    ],
)
def test_caller_pass_cannot_replace_actual_ui_cases(tmp_path: Path, key: str) -> None:
    record, sha = fixture(tmp_path)
    del record[key]
    with pytest.raises(ValueError, match="restoration"):
        verify_functional(record, tmp_path, "native.zip", sha, True)


def test_linked_without_frame_and_legacy_version_refuse(tmp_path: Path) -> None:
    record, sha = fixture(tmp_path)
    record["schema"] = "shaderlod-functional-v1"
    with pytest.raises(ValueError, match="schema"):
        verify_functional(record, tmp_path, "native.zip", sha, True)
    record["schema"] = "shaderlod-functional-v3"
    active = record["identity_probe"]
    assert isinstance(active, dict)
    active["witnesses"] = ["linked-archive:" + active["archiveSha256"]]
    with pytest.raises(ValueError, match="witness"):
        verify_functional(record, tmp_path, "native.zip", sha, True)


@pytest.mark.parametrize(
    "key", ["native_rendered_sections", "identity_rendered_sections"]
)
def test_empty_world_render_does_not_qualify(tmp_path: Path, key: str) -> None:
    record, sha = fixture(tmp_path)
    record[key] = 0
    with pytest.raises(ValueError, match="rendered sections"):
        verify_functional(record, tmp_path, "native.zip", sha, True)


@pytest.mark.parametrize("enabled", [False, None, "true", 1])
def test_dh_presence_does_not_prove_enabled(tmp_path: Path, enabled: object) -> None:
    record, sha = fixture(tmp_path)
    record["dh"] = {"present": True, "enabled": enabled}
    with pytest.raises(ValueError, match="DH enabled"):
        verify_functional(record, tmp_path, "native.zip", sha, True)


def test_missing_dh_enabled_state_refuses_but_absent_mode_passes(
    tmp_path: Path,
) -> None:
    record, sha = fixture(tmp_path)
    record["dh"] = {"present": True}
    with pytest.raises(ValueError, match="DH enabled"):
        verify_functional(record, tmp_path, "native.zip", sha, True)
    record["dh"] = {"present": False}
    assert verify_functional(record, tmp_path, "native.zip", sha, False) == record


@pytest.mark.parametrize("witnesses", [[{}, {}], [[], []], [1, True]])
def test_malformed_witnesses_are_contract_failures(
    tmp_path: Path, witnesses: list[object]
) -> None:
    record, sha = fixture(tmp_path)
    active = record["native_activation"]
    assert isinstance(active, dict)
    active["witnesses"] = witnesses
    with pytest.raises(ValueError, match="witness"):
        verify_functional(record, tmp_path, "native.zip", sha, True)


def test_malformed_witness_publishes_failure_and_preserves_outputs(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    import json
    import subprocess
    import sys
    from shaderlod_host import runtime_live

    root = tmp_path / "repo"
    (root / "shaderlod_host").mkdir(parents=True)
    (root / "dependencies").mkdir()
    (root / "dependencies/runtime.lock.json").write_text('{"artifacts": {}}')
    profile = tmp_path / "profile"
    profile.mkdir()
    record, sha = fixture(profile)
    active = record["native_activation"]
    assert isinstance(active, dict)
    active["witnesses"] = [{}, {}]
    payload = json.dumps(record).encode()
    (root / "dependencies/primary-pack.json").write_text(
        json.dumps({"filename": "native.zip", "sha256": sha})
    )
    java = tmp_path / "java"
    java.write_bytes(b"test executable")
    launch = tmp_path / "launch.json"
    launch.write_text(
        json.dumps({"argv": [str(java)], "cwd": str(profile), "environment": {}})
    )
    receipt = tmp_path / "evidence/attempt.json"

    class CompletedWorker:
        def __init__(self, *args: object, **kwargs: object) -> None:
            (profile / "shaderlod-functional.json").write_bytes(payload)

        def wait(self, timeout: float) -> int:
            return 0

        def poll(self) -> int:
            return 0

    monkeypatch.setattr(
        runtime_live, "__file__", str(root / "shaderlod_host/runtime_live.py")
    )
    monkeypatch.setattr(runtime_live, "display_environment", lambda path: {})
    monkeypatch.setattr(runtime_live, "executable_inputs", lambda *args: {})
    monkeypatch.setattr(subprocess, "Popen", CompletedWorker)
    monkeypatch.setattr(
        sys,
        "argv",
        [
            "runtime_live",
            "--launch",
            str(launch),
            "--display",
            str(tmp_path / "display"),
            "--receipt",
            str(receipt),
            "--ownership-root",
            str(tmp_path / "ownership"),
        ],
    )
    assert runtime_live.main() == 1
    result = json.loads(receipt.read_bytes())
    assert result["outcome"] == "FAIL"
    assert "witness" in result["contract_failure"]
    archived = receipt.with_suffix("") / "shaderlod-functional.json"
    assert archived.read_bytes() == payload
    assert result["outputs"]["sha256"][archived.name] == digest(payload)
    assert not (profile / archived.name).exists()

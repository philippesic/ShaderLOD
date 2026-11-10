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
    sha = digest(b"native")

    def active(name: str, payload: bytes, generation: int) -> dict[str, object]:
        value = digest(payload)
        return {
            "packName": name,
            "archiveSha256": value,
            "generation": generation,
            "linked": True,
            "effectiveOptions": {"TAA": "true"},
            "witnesses": ["linked-archive:" + value],
        }

    return {
        "schema": "shaderlod-functional-v1",
        "scope": "functional-only",
        "source": "native.zip",
        "dh": {"present": True},
        "profiler": {},
        "native_width": 2560,
        "native_height": 1600,
        "outcome": "PASS",
        "native_activation": active("native.zip", b"native", 1),
        "selection": active("native.zip", b"native", 2),
        "identity_probe": active("derived.zip", b"derived", 4),
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
    record["schema"] = "shaderlod-functional-v1"
    record["image_has_variation"] = False
    with pytest.raises(ValueError, match="output"):
        verify_functional(record, tmp_path, "native.zip", sha, True)

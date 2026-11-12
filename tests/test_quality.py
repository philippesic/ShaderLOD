import hashlib
from pathlib import Path

import pytest

from shaderlod_host.quality import save_sequence


def test_motion_gaps_and_assets_are_explicit(tmp_path: Path) -> None:
    session = tmp_path / "session"
    output = tmp_path / "output"
    session.mkdir()
    frames = []
    for i, timestamp in enumerate((100000000, 800000000)):
        name = str(i) + ".png"
        payload = b"fixture" + bytes([i])
        (session / name).write_bytes(payload)
        frames.append(
            {
                "image": name,
                "image_sha256": hashlib.sha256(payload).hexdigest(),
                "elapsed_ns": timestamp,
                "client_ticks": i,
                "pose": [0, 0, 0, 0, 0],
            }
        )
    result = save_sequence({"quality_frames": frames}, session, output, 250, 1000)
    assert result["cadence_met"] is False
    assert result["max_gap_ms"] == 700
    assert (output / "quality.html").exists()
    frames[0]["image_sha256"] = "f" * 64
    with pytest.raises(ValueError, match="digest"):
        save_sequence({"quality_frames": frames}, session, tmp_path / "bad", 250, 1000)


def test_quality_verification_checks_images_and_reduces_the_original(
    tmp_path: Path,
) -> None:
    import json
    from shaderlod_host.quality import verify_sequence

    source = tmp_path / "source"
    source.mkdir()
    (source / "frame.png").write_bytes(b"CPU-image-fixture")
    frame = {
        "image": "frame.png",
        "image_sha256": hashlib.sha256(b"CPU-image-fixture").hexdigest(),
        "elapsed_ns": 100000000,
        "client_ticks": 2,
        "pose": [0, 0, 0, 0, 0],
    }
    observed: dict[str, object] = {"quality_frames": [frame]}
    output = tmp_path / "output"
    result = save_sequence(observed, source, output, 250, 1000)
    path = output / "quality.json"
    assert verify_sequence(path, observed, 250, 1000) == result
    path.write_text(json.dumps(result | {"sequence_valid": True}))
    with pytest.raises(ValueError, match="analysis"):
        verify_sequence(path, observed, 250, 1000)
    path.write_text(json.dumps(result))
    (output / "frame.png").write_bytes(b"changed")
    with pytest.raises(ValueError, match="digest"):
        verify_sequence(path, observed, 250, 1000)

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

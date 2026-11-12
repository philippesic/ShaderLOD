from pathlib import Path

import pytest

from shaderlod_host.storage import prepare_profile_artifact, restore_profile_artifact


def test_absent_control_preserves_the_original_artifact_and_rejects_foreign_write(
    tmp_path: Path,
) -> None:
    profile = tmp_path / "profile"
    (profile / "mods").mkdir(parents=True)
    jar = profile / "mods/shaderlod.jar"
    jar.write_bytes(b"old")
    shipping = tmp_path / "shaderlod.jar"
    shipping.write_bytes(b"current")
    session = tmp_path / "session"
    session.mkdir()
    receipt = prepare_profile_artifact(profile, shipping, session, absent=True)
    assert not jar.exists()
    jar.write_bytes(b"foreign")
    with pytest.raises(ValueError, match="changed"):
        restore_profile_artifact(profile, receipt)
    assert jar.read_bytes() == b"foreign"
    jar.unlink()
    restore_profile_artifact(profile, receipt)
    assert jar.read_bytes() == b"old"

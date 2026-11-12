import json
from pathlib import Path
from types import SimpleNamespace

import pytest

from shaderlod_host import runtime_live


def test_display_environment_rejects_stale_invocation_and_public_file(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    path = tmp_path / "environment.json"
    path.write_text(
        json.dumps(
            {
                "invocation_id": "current",
                "environment": {
                    "DISPLAY": ":1",
                    "WAYLAND_DISPLAY": "test",
                    "XDG_RUNTIME_DIR": str(tmp_path),
                },
            }
        )
    )
    metadata = SimpleNamespace(st_uid=17, st_mode=0o100600)
    original_stat = Path.stat
    monkeypatch.setattr(
        Path,
        "stat",
        lambda self, **kwargs: (
            metadata if self == path else original_stat(self, **kwargs)
        ),
    )
    monkeypatch.setattr(Path, "is_socket", lambda self: True)
    assert (
        runtime_live.checked_display_environment(path, "current", 17)["DISPLAY"] == ":1"
    )
    with pytest.raises(ValueError, match="stale service"):
        runtime_live.checked_display_environment(path, "previous", 17)
    metadata.st_mode = 0o100644
    with pytest.raises(ValueError, match="private"):
        runtime_live.checked_display_environment(path, "current", 17)


def test_display_units_are_local_and_do_not_overwrite_changed_output(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    source = Path(runtime_live.__file__).with_name("templates") / "systemd-units.json"
    template = tmp_path / "shaderlod_host/templates/systemd-units.json"
    template.parent.mkdir(parents=True)
    template.write_bytes(source.read_bytes())
    monkeypatch.setattr(runtime_live, "project_root", lambda: tmp_path)
    output = tmp_path / ".local/services"
    result = runtime_live.write_display_units(output)
    assert isinstance(result["files"], list)
    assert len(result["files"]) == 2
    unit = output / "shaderlod-display.service"
    contents = unit.read_text()
    assert str(tmp_path / ".local/display/config") in contents
    assert str(tmp_path / ".local/cache/mesa") in contents
    assert "-m shaderlod_host display-publish" in contents
    assert "headless_display.py" not in contents
    unit.write_text("user edit")
    with pytest.raises(ValueError, match="service output changed"):
        runtime_live.write_display_units(output)
    assert unit.read_text() == "user edit"
    with pytest.raises(ValueError, match="repository-local"):
        runtime_live.write_display_units(tmp_path / "outside")


def test_worker_uses_local_renderer_cache(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    from shaderlod_host import config

    monkeypatch.setattr(config, "project_root", lambda: tmp_path)
    environment = config.worker_environment(
        {"XDG_CACHE_HOME": "/shared/cache", "MESA_SHADER_CACHE_DIR": "/shared/mesa"}
    )
    assert environment["XDG_CACHE_HOME"] == str(tmp_path / ".local/cache")
    assert environment["MESA_SHADER_CACHE_DIR"] == str(tmp_path / ".local/cache/mesa")

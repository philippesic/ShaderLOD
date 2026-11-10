import json
import os
from pathlib import Path


def display_environment(path: Path) -> dict[str, str]:
    record = json.loads(path.read_bytes())
    values = record.get("environment")
    if not isinstance(values, dict) or not record.get("invocation_id"):
        raise ValueError("unrecognized display environment")
    permitted = {
        "DISPLAY",
        "WAYLAND_DISPLAY",
        "XDG_RUNTIME_DIR",
        "XAUTHORITY",
        "DBUS_SESSION_BUS_ADDRESS",
    }
    environment = dict(os.environ)
    for key, value in values.items():
        if key in permitted and isinstance(value, str):
            environment[key] = value
    if not environment.get("DISPLAY"):
        raise ValueError("hardware display unavailable")
    return environment


def worker_environment(overrides: dict[str, str] | None = None) -> dict[str, str]:
    environment = dict(os.environ) if overrides is None else dict(overrides)
    environment["PYTHONPATH"] = str(Path(__file__).resolve().parents[1])
    return environment

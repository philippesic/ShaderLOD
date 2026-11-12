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


def project_root() -> Path:
    return Path(__file__).resolve().parents[1]


def evidence_root(repository: Path | None = None) -> Path:
    return (repository or project_root()) / ".local/artifacts"


def validate_artifact_path(path: Path, repository: Path) -> Path:
    resolved = path.resolve()
    root = repository.resolve()
    if (
        path.absolute().is_relative_to(root) or resolved.is_relative_to(root)
    ) and not resolved.is_relative_to(evidence_root(root)):
        raise ValueError("artifacts inside the repository require .local/artifacts")
    return resolved


def display_publication() -> dict[str, object]:
    keys = (
        "DISPLAY",
        "WAYLAND_DISPLAY",
        "XAUTHORITY",
        "DBUS_SESSION_BUS_ADDRESS",
        "XDG_RUNTIME_DIR",
    )
    return {
        "environment": {key: os.environ[key] for key in keys if key in os.environ},
        "invocation_id": os.environ.get("INVOCATION_ID"),
        "publisher_pid": os.getpid(),
    }


def worker_environment(overrides: dict[str, str] | None = None) -> dict[str, str]:
    environment = dict(os.environ) if overrides is None else dict(overrides)
    environment["PYTHONPATH"] = str(project_root())
    environment["XDG_CACHE_HOME"] = str(project_root() / ".local/cache")
    environment["MESA_SHADER_CACHE_DIR"] = str(project_root() / ".local/cache/mesa")
    return environment

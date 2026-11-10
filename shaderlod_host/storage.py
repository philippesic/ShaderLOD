import json
import os
from pathlib import Path
import tempfile
from typing import BinaryIO

from shaderlod_host.contracts import canonical, digest, integer, mapping, text


def sync_directory(path: Path) -> None:
    if os.name == "posix":
        descriptor = os.open(path, os.O_RDONLY)
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)


def publish(path: Path, record: dict[str, object]) -> None:
    payload = canonical(record)
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, name = tempfile.mkstemp(prefix=".publish-", dir=path.parent)
    staging = Path(name)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        os.link(staging, path)
        sync_directory(path.parent)
    finally:
        staging.unlink(missing_ok=True)


def replace(path: Path, record: dict[str, object]) -> None:
    payload = canonical(record)
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, name = tempfile.mkstemp(prefix=".journal-", dir=path.parent)
    staging = Path(name)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(staging, path)
        sync_directory(path.parent)
    finally:
        staging.unlink(missing_ok=True)


class BusyRefusal:
    def __init__(self, namespace: Path, host_device: str) -> None:
        namespace.mkdir(parents=True, exist_ok=True)
        path = namespace / (digest(host_device.encode()) + ".owner")
        if path.is_symlink():
            raise ValueError("ownership path is symlink")
        self.stream: BinaryIO = path.open("a+b")
        self.locked = False

    def __enter__(self) -> "BusyRefusal":
        try:
            if os.name == "nt":
                import msvcrt

                self.stream.seek(0)
                if not self.stream.read(1):
                    self.stream.write(b"0")
                    self.stream.flush()
                self.stream.seek(0)
                getattr(msvcrt, "locking")(
                    self.stream.fileno(), getattr(msvcrt, "LK_NBLCK"), 1
                )
            else:
                import fcntl

                fcntl.flock(self.stream.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
            self.locked = True
            return self
        except OSError:
            self.stream.close()
            raise RuntimeError(
                "host/device busy; advisory participating-owner exclusion only"
            ) from None

    def __exit__(self, *args: object) -> None:
        self.stream.close()
        self.locked = False


def recover_journal(path: Path, expected_owner: str) -> dict[str, object]:
    record = mapping(json.loads(path.read_bytes()), "journal")
    if (
        set(record)
        != {"schema", "owner", "state", "request", "reserved_ms", "attempt", "process"}
        or record["schema"] != "shaderlod-journal-v1"
    ):
        raise ValueError("unknown journal schema")
    integer(record["attempt"], "attempt")
    integer(record["reserved_ms"], "reserved_ms", 0)
    text(record["request"], "request")
    if record["state"] not in {
        "running",
        "interrupted",
        "recovery-required",
        "complete",
        "failed",
        "cancelled",
    }:
        raise ValueError("invalid journal state")
    if record["owner"] != expected_owner:
        raise ValueError("foreign journal owner")
    if record["state"] == "running":
        record["state"] = "recovery-required"
        replace(path, record)
    return record


def reclaim(
    root: Path, candidate: Path, description: dict[str, object], receipt: Path
) -> int:
    required = {
        "schema",
        "owner",
        "class",
        "sha256",
        "active",
        "dependencies",
        "rebuild",
    }
    if set(description) != required or description["schema"] != "shaderlod-reclaim-v1":
        raise ValueError("unknown reclaim schema")
    text(description["owner"], "owner")
    if (
        description["class"] not in {"disposable", "reproducible"}
        or description["active"] is not False
        or description["dependencies"] != []
    ):
        raise ValueError("artifact is active, retained or referenced")
    text(description["rebuild"], "rebuild")
    if root.is_symlink():
        raise ValueError("reclaim owned root is symlink")
    base = root.resolve(strict=True)
    if (
        candidate.is_symlink()
        or not candidate.resolve(strict=True).is_relative_to(base)
        or candidate.resolve() == base
    ):
        raise ValueError("reclaim path escapes owned root")
    relative = candidate.absolute().relative_to(root.absolute())
    cursor = root.absolute()
    for part in relative.parts:
        cursor /= part
        if cursor.is_symlink():
            raise ValueError("reclaim symlink refused")
    if (
        not candidate.is_file()
        or digest(candidate.read_bytes()) != description["sha256"]
    ):
        raise ValueError("reclaim identity mismatch")
    if receipt.resolve().is_relative_to(candidate.resolve()):
        raise ValueError("deletion receipt inside candidate")
    stat = candidate.stat()
    publish(
        receipt,
        {
            "schema": "shaderlod-deletion-v1",
            "path": str(candidate),
            "bytes": stat.st_size,
            "classification": description,
            "status": "planned",
        },
    )
    if (
        candidate.stat() != stat
        or digest(candidate.read_bytes()) != description["sha256"]
    ):
        raise ValueError("reclaim changed before delete")
    candidate.unlink()
    publish(
        receipt.with_suffix(".complete.json"),
        {
            "schema": "shaderlod-deletion-v1",
            "path": str(candidate),
            "bytes": stat.st_size,
            "status": "deleted",
        },
    )
    return stat.st_size


def copytree(source: Path, destination: Path, reserve_bytes: int) -> None:
    import shutil

    if type(reserve_bytes) is not int or reserve_bytes < 0:
        raise ValueError("nonnegative operational reserve required")
    if source.is_symlink() or not source.is_dir() or destination.exists():
        raise ValueError("fresh destination and regular source tree required")
    origin = source.resolve(strict=True)
    target = destination.resolve()
    if target.is_relative_to(origin) or origin.is_relative_to(target):
        raise ValueError("copy roots overlap")
    members = list(source.rglob("*"))
    if any(p.is_symlink() for p in members):
        raise ValueError("copy source contains symlink")
    incoming = sum(p.stat().st_size for p in members if p.is_file())
    destination.parent.mkdir(parents=True, exist_ok=True)
    if shutil.disk_usage(destination.parent).free < incoming + reserve_bytes:
        raise ValueError("copy would exhaust capacity reserve")
    shutil.copytree(source, destination, ignore=shutil.ignore_patterns("session.lock"))

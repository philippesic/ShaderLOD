from contextlib import contextmanager
import json
import os
from pathlib import Path
import tempfile
import io
import tarfile
from typing import BinaryIO, Iterator

from shaderlod_host.contracts import canonical, digest, integer, mapping, text


def sync_directory(path: Path) -> None:
    if os.name == "posix":
        descriptor = os.open(path, os.O_RDONLY)
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)


def publish(path: Path, record: dict[str, object]) -> None:
    publish_bytes(path, canonical(record))


def publish_bytes(path: Path, payload: bytes) -> None:
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


def replace_bytes(path: Path, payload: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, name = tempfile.mkstemp(prefix=".bytes-", dir=path.parent)
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


def prepare_profile_artifact(
    profile: Path, shipping: Path, session: Path, absent: bool = False
) -> dict[str, object]:
    marker = profile / "shaderlod-benchmark-profile.json"
    if marker.exists():
        raise ValueError("prior profile-artifact journal requires recovery")
    target = profile / "mods" / shipping.name
    if target.is_symlink() or not shipping.is_file():
        raise ValueError("regular shipping artifact and managed target required")
    payload = shipping.read_bytes()
    existing = target.read_bytes() if target.exists() else None
    record: dict[str, object] = {
        "schema": "shaderlod-profile-artifact-v1",
        "target": str(target.resolve()),
        "session": str(session.resolve()),
        "managed_sha256": None if absent else digest(payload),
        "original_sha256": None if existing is None else digest(existing),
        "backup": str((session / "profile-artifact-preimage.jar").resolve()),
    }
    if existing is not None:
        publish_bytes(Path(str(record["backup"])), existing)
    publish(marker, record)
    if absent:
        target.unlink(missing_ok=True)
    else:
        replace_bytes(target, payload)
    return record


def restore_profile_artifact(profile: Path, expected: dict[str, object]) -> None:
    marker = profile / "shaderlod-benchmark-profile.json"
    if json.loads(marker.read_bytes()) != expected:
        raise ValueError("profile journal changed before restoration")
    target = Path(str(expected["target"]))
    if target.is_symlink() or (
        target.exists()
        if expected["managed_sha256"] is None
        else not target.exists()
        or digest(target.read_bytes()) != expected["managed_sha256"]
    ):
        raise ValueError("managed profile artifact changed; preserve before recovery")
    if expected["original_sha256"] is None:
        target.unlink(missing_ok=True)
    else:
        original = Path(str(expected["backup"])).read_bytes()
        if digest(original) != expected["original_sha256"]:
            raise ValueError("profile artifact preimage changed")
        replace_bytes(target, original)
    marker.unlink()


@contextmanager
def profile_artifact(
    profile: Path, shipping: Path, session: Path, absent: bool = False
) -> Iterator[dict[str, object]]:
    record = prepare_profile_artifact(profile, shipping, session, absent)
    try:
        yield record
    finally:
        restore_profile_artifact(profile, record)


def snapshot_inputs(root: Path, target: Path) -> None:
    paths: set[Path] = set()
    for directory in (
        "shaderlod_host",
        "src",
        "benchmarks",
        "dependencies",
        "build/classes",
        "build/resources",
        "build/libs",
    ):
        paths.update(
            path
            for path in (root / directory).rglob("*")
            if path.is_file()
            and not path.is_symlink()
            and "__pycache__" not in path.parts
            and path.suffix not in {".zip", ".jar", ".pyc"}
        )
    paths.update(path for path in (root / "build/libs").glob("*.jar"))
    paths.update(
        root / name
        for name in (
            "build.gradle",
            "settings.gradle",
            "gradle.properties",
            "gradle.lockfile",
        )
        if (root / name).is_file()
    )
    stream = io.BytesIO()
    with tarfile.open(fileobj=stream, mode="w") as archive:
        for path in sorted(paths):
            archive.add(path, arcname=path.relative_to(root), recursive=False)
    publish_bytes(target, stream.getvalue())


def prepare_primary_profile(
    profile: Path, filename: str, expected: str, output: Path
) -> dict[str, object]:
    if (
        output.exists()
        or (profile / "shaderlod-recovery.json").exists()
        or (profile / "shaderlod-benchmark-profile.json").exists()
    ):
        raise ValueError(
            "fresh setup evidence and quiescent recovered profile required"
        )
    if (
        not isinstance(filename, str)
        or Path(filename).name != filename
        or "\\" in filename
        or not isinstance(expected, str)
        or len(expected) != 64
        or any(c not in "0123456789abcdef" for c in expected)
    ):
        raise ValueError("regular pack filename and SHA-256 required")
    archive = profile / "shaderpacks" / filename
    if archive.is_symlink() or digest(archive.read_bytes()) != expected:
        raise ValueError("pinned original pack required")
    target = profile / "config/iris.properties"
    before = target.read_bytes()
    replacements = {"shaderPack": filename, "enableShaders": "true"}
    lines = []
    found = set()
    for line in before.decode().splitlines():
        key = line.split("=", 1)[0].strip()
        if key in replacements:
            if key in found:
                raise ValueError("duplicate Iris property")
            found.add(key)
            line = key + "=" + replacements[key]
        lines.append(line)
    lines.extend(
        key + "=" + value for key, value in replacements.items() if key not in found
    )
    payload = ("\n".join(lines) + "\n").encode()
    publish_bytes(output / "iris-properties-preimage.txt", before)
    if target.read_bytes() != before:
        raise ValueError("profile configuration changed before setup")
    replace_bytes(target, payload)
    result: dict[str, object] = {
        "schema": "shaderlod-prepared-primary-profile-v1",
        "profile": str(profile.resolve()),
        "pack": filename,
        "archive_sha256": expected,
        "original_config_sha256": digest(before),
        "prepared_config_sha256": digest(payload),
        "scope": "Explicit prepared-slot initial shader selection; existing shader option files and other native settings preserved",
    }
    publish(output / "result.json", result)
    return result


def tree_identity(source: Path) -> dict[str, str]:
    if source.is_symlink() or not source.is_dir():
        raise ValueError("regular input tree required")
    files = {}
    for path in sorted(source.rglob("*")):
        if path.is_symlink():
            raise ValueError("input tree contains a symlink")
        if path.is_file() and path.name != "session.lock":
            files[path.relative_to(source).as_posix()] = digest(path.read_bytes())
    return files


def prepare_world(
    source: Path, destination: Path, evidence: Path, definition: dict[str, object]
) -> dict[str, object]:
    if (
        set(definition) != {"schema", "id", "tree_sha256", "exposure", "description"}
        or definition["schema"] != "shaderlod-world-v1"
        or evidence.exists()
    ):
        raise ValueError("world definition and fresh evidence location required")
    before = tree_identity(source)
    identity = digest(canonical(before))
    if identity != definition["tree_sha256"]:
        raise ValueError("world source differs from declared tree identity")
    copytree(source, evidence / "original", 1073741824)
    if before != tree_identity(source) or before != tree_identity(
        evidence / "original"
    ):
        raise ValueError("world source changed during preparation; copies preserved")
    copytree(evidence / "original", destination, 1073741824)
    if tree_identity(destination) != before:
        raise ValueError("prepared world copy differs from frozen input")
    result: dict[str, object] = {
        "schema": "shaderlod-prepared-world-v1",
        "definition": definition,
        "source": str(source.resolve()),
        "frozen": str((evidence / "original").resolve()),
        "working": str(destination.resolve()),
        "files": before,
        "tree_sha256": identity,
        "scope": "Initial input identity; the bounded working world is reused and may evolve during integrated-server execution",
    }
    publish(evidence / "manifest.json", result)
    return result

import hashlib
import json
import os
import platform
from pathlib import Path
import subprocess
import sys
import tempfile
import tarfile
import urllib.request
import venv
import zipfile


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def acquire(url: str, target: Path, expected: str) -> None:
    if target.exists() and digest(target) == expected:
        return
    target.parent.mkdir(parents=True, exist_ok=True)
    descriptor, name = tempfile.mkstemp(prefix=".download-", dir=target.parent)
    os.close(descriptor)
    staged = Path(name)
    try:
        urllib.request.urlretrieve(url, staged)
        if digest(staged) != expected:
            raise RuntimeError("TOOLS: downloaded artifact checksum mismatch")
        staged.replace(target)
    finally:
        staged.unlink(missing_ok=True)


def install_bytes(payload: bytes, target: Path, expected: str) -> None:
    if hashlib.sha256(payload).hexdigest() != expected:
        raise RuntimeError("TOOLS: archive member checksum mismatch")
    target.parent.mkdir(parents=True, exist_ok=True)
    descriptor, name = tempfile.mkstemp(prefix=".member-", dir=target.parent)
    staged = Path(name)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(payload)
        staged.replace(target)
    finally:
        staged.unlink(missing_ok=True)


def install_jdk(archive: Path, java: Path) -> None:
    if (java / "bin/java").is_file() and (java / "release").is_file():
        return
    if java.exists():
        raise RuntimeError(
            "TOOLS: incomplete JDK at "
            + str(java)
            + "; preserve or remove this owned installation before retry"
        )
    java.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".jdk-", dir=java.parent) as temporary:
        staging = Path(temporary)
        with tarfile.open(archive) as bundle:
            bundle.extractall(staging, filter="data")
        candidate = staging / java.name
        if (
            not (candidate / "bin/java").is_file()
            or not (candidate / "release").is_file()
        ):
            raise RuntimeError("TOOLS: incomplete JDK archive")
        candidate.rename(java)


def runtime_artifacts(root: Path, cache: Path) -> None:
    manifest = json.loads((root / "dependencies/runtime.lock.json").read_text())
    for item in manifest["artifacts"].values():
        expected = item["hashes"]["sha256"]
        filename = str(item["filename"])
        if Path(filename).name != filename:
            raise ValueError("TOOLS: runtime artifact path escape")
        target = root / "dependencies/runtime" / filename
        cached = cache / "runtime" / expected / filename
        if target.is_file() and digest(target) == expected:
            if not cached.exists():
                acquire(target.as_uri(), cached, expected)
        else:
            acquire(item["url"], cached, expected)
            acquire(cached.as_uri(), target, expected)


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    os.chdir(root)
    if sys.version_info[:2] != (3, 13):
        raise RuntimeError("TOOLS: Python 3.13 required")
    lock = json.loads((root / "dependencies/check-tools.lock.json").read_text())
    if digest(root / "gradle/wrapper/gradle-wrapper.jar") != lock["wrapper"]["sha256"]:
        raise RuntimeError("TOOLS: wrapper checksum mismatch")
    arch = platform.machine()
    item = lock["jdk"].get(f"{sys.platform}-{arch}")
    if item is None:
        raise RuntimeError("TOOLS: unsupported platform")
    cache = Path.home() / ".cache/shaderlod-foundation"
    cache.mkdir(parents=True, exist_ok=True)
    runtime_artifacts(root, cache)
    archive = cache / item["url"].rsplit("/", 1)[1]
    acquire(item["url"], archive, item["sha256"])
    install = Path.home() / ".local/share/shaderlod-jdks"
    java = install / "jdk-21.0.13+9"
    install_jdk(archive, java)
    release = (java / "release").read_text()
    if (
        'JAVA_RUNTIME_VERSION="21.0.13+9-LTS"' not in release
        or 'IMPLEMENTOR="Eclipse Adoptium"' not in release
    ):
        raise RuntimeError("TOOLS: incorrect JDK release")
    pylock = root / "dependencies/python.lock"
    envdir = cache / ("py313-" + digest(pylock)[:16])
    stamp = envdir / "ready"
    if not stamp.exists():
        venv.create(envdir, with_pip=True)
        subprocess.run(
            [
                str(envdir / "bin/python"),
                "-m",
                "pip",
                "install",
                "--require-hashes",
                "--only-binary=:all:",
                "-r",
                str(pylock),
            ],
            check=True,
        )
        stamp.write_text(digest(pylock))
    p2 = Path.home() / ".gradle/caches/p2-data"
    for artifact in json.loads((root / "dependencies/greclipse.lock.json").read_text())[
        "artifacts"
    ]:
        target = p2 / artifact["path"]
        if not target.exists():
            target.parent.mkdir(parents=True, exist_ok=True)
            if "url" in artifact:
                acquire(artifact["url"], target, artifact["sha256"])
            else:
                with zipfile.ZipFile(p2 / artifact["parent"]) as bundle:
                    install_bytes(
                        bundle.read(artifact["member"]), target, artifact["sha256"]
                    )
        if digest(target) != artifact["sha256"]:
            raise RuntimeError("TOOLS: Groovy formatter artifact checksum mismatch")
    if sys.platform == "linux" and arch in ("aarch64", "arm64"):
        native_lock = json.loads(
            (root / "dependencies/linux-arm64-runtime.lock.json").read_text()
        )
        native_root = root / "dependencies/runtime/linux-arm64"
        for native in native_lock["artifacts"]:
            archive_path = native_root / native["url"].rsplit("/", 1)[1]
            acquire(native["url"], archive_path, native["sha256"])
            with zipfile.ZipFile(archive_path) as bundle:
                for member in bundle.namelist():
                    name = Path(member).name
                    if name in native_lock["libraries"]:
                        target = native_root / "natives" / name
                        expected = native_lock["libraries"][name]
                        if not target.exists() or digest(target) != expected:
                            install_bytes(bundle.read(member), target, expected)
    primary = json.loads((root / "dependencies/primary-pack.json").read_text())
    acquire(
        primary["url"],
        root / "dependencies/runtime" / primary["filename"],
        primary["sha256"],
    )
    env = dict(
        os.environ, JAVA_HOME=str(java), FOUNDATION_PYTHON=str(envdir / "bin/python")
    )
    env["PATH"] = str(java / "bin") + ":" + env["PATH"]
    if sys.argv[1] == "check":
        options = sys.argv[2:]
        if options not in ([], ["--fast"], ["--full"], ["--fresh"]):
            raise ValueError("CHECK: expected --fast, --full or --fresh")
        report = root / "build/reports/foundation/check.json"
        report.unlink(missing_ok=True)
        task = "fastCheck" if options == ["--fast"] else "check"
        extra = ["--rerun-tasks"] if options == ["--fresh"] else []
        return subprocess.call(["./gradlew", "--no-daemon", task, *extra], env=env)
    return subprocess.call(
        [str(envdir / "bin/python"), "-m", "checks.verify", *sys.argv[2:]], env=env
    )


if __name__ == "__main__":
    sys.exit(main())

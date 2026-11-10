import hashlib
import json
import os
import platform
from pathlib import Path
import subprocess
import sys
import tarfile
import urllib.request
import venv
import zipfile


def digest(path: Path) -> str:
    return hashlib.file_digest(path.open("rb"), "sha256").hexdigest()


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
    archive = cache / item["url"].rsplit("/", 1)[1]
    if not archive.exists():
        urllib.request.urlretrieve(item["url"], archive)
    if digest(archive) != item["sha256"]:
        raise RuntimeError("TOOLS: JDK checksum mismatch")
    install = Path.home() / ".local/share/shaderlod-jdks"
    java = install / "jdk-21.0.13+9"
    if not java.exists():
        install.mkdir(parents=True, exist_ok=True)
        with tarfile.open(archive) as bundle:
            bundle.extractall(install, filter="data")
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
                urllib.request.urlretrieve(artifact["url"], target)
            else:
                with zipfile.ZipFile(p2 / artifact["parent"]) as bundle:
                    target.write_bytes(bundle.read(artifact["member"]))
        if digest(target) != artifact["sha256"]:
            raise RuntimeError("TOOLS: Groovy formatter artifact checksum mismatch")
    env = dict(
        os.environ, JAVA_HOME=str(java), FOUNDATION_PYTHON=str(envdir / "bin/python")
    )
    env["PATH"] = str(java / "bin") + ":" + env["PATH"]
    if sys.argv[1] == "check":
        return subprocess.call(["./gradlew", "--no-daemon", "check"], env=env)
    return subprocess.call(
        [str(envdir / "bin/python"), "-m", "checks.verify", *sys.argv[2:]], env=env
    )


if __name__ == "__main__":
    sys.exit(main())

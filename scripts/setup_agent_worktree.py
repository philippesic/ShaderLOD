#!/usr/bin/env python3
"""Inspect a development checkout; optionally copy explicitly supplied pinned jars.

No downloads, Gradle invocation, game launches, global settings or job ownership.
Default success means the local Python/Git tooling is usable. Build prerequisites
and compilation are reported separately; --require-build-inputs makes missing
build prerequisites an error. Root support is NeoForge Minecraft 1.21.1 only.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile


def digest(path):
    with path.open("rb") as stream:
        checksum = hashlib.sha512()
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            checksum.update(block)
    return checksum.hexdigest()


def copy_missing(source, target, expected):
    if not source.is_file() or digest(source) != expected:
        raise ValueError(f"source missing or checksum mismatch: {source}")
    target.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix=".agent-runtime-", dir=target.parent)
    try:
        with os.fdopen(descriptor, "wb") as destination, source.open("rb") as origin:
            shutil.copyfileobj(origin, destination)
        if digest(Path(temporary)) != expected:
            raise ValueError(f"source changed during copy: {source}")
        os.link(temporary, target)  # Exclusive creation: never replace another file.
    finally:
        os.unlink(temporary)


def inspect(root, runtime_source=None):
    tooling_missing = []
    build_missing = []
    if sys.version_info < (3, 10):
        tooling_missing.append("Python 3.10 or newer is required")
    try:
        git = subprocess.run(["git", "-C", str(root), "rev-parse", "--show-toplevel"],
                             capture_output=True, text=True, timeout=10)
        git_root = git.stdout.strip()
        if git.returncode or Path(git_root).resolve() != root:
            tooling_missing.append("--root must be the root of a Git checkout")
    except (OSError, subprocess.TimeoutExpired):
        tooling_missing.append("Git executable unavailable")
    for relative in ("gradlew", "gradle/wrapper/gradle-wrapper.jar",
                     "gradle/wrapper/gradle-wrapper.properties", "build.gradle", "settings.gradle"):
        if not (root / relative).is_file():
            build_missing.append(f"missing build input: {relative}")
    java_home = os.environ.get("JAVA_HOME")
    java = str(Path(java_home) / "bin/java") if java_home else shutil.which("java")
    java_major = None
    if java:
        try:
            probe = subprocess.run([java, "-version"], capture_output=True, text=True, timeout=10)
            match = re.search(r'version "(?:1\.)?(\d+)', probe.stderr + probe.stdout)
            if probe.returncode == 0 and match:
                java_major = int(match[1])
        except (OSError, subprocess.TimeoutExpired):
            pass
    artifacts = []
    try:
        lock = json.loads((root / "dev/runtime.lock.json").read_text())
        required_java = lock["java"]
        if type(required_java) is not int or required_java < 1:
            raise ValueError("invalid java version")
        if java_major is None or java_major < required_java:
            build_missing.append(f"Java {required_java}+ launcher unavailable; configure JAVA_HOME explicitly")
        for name in ("iris", "sodium"):
            artifact = lock["artifacts"][name]
            filename = artifact["filename"]
            expected = artifact["hashes"]["sha512"]
            if Path(filename).name != filename or not re.fullmatch(r"[a-f0-9]{128}", expected):
                raise ValueError(f"invalid pinned artifact: {name}")
            target = root / "dev/runtime" / filename
            state = "missing"
            if target.exists() or target.is_symlink():
                state = "verified" if target.is_file() and digest(target) == expected else "checksum mismatch"
            elif runtime_source and not tooling_missing:
                try:
                    copy_missing(runtime_source / filename, target, expected)
                    state = "verified"
                except (OSError, ValueError) as exc:
                    build_missing.append(str(exc))
            if state != "verified":
                build_missing.append(f"{state}: {target}; supply --runtime-source with checksum-pinned files or run the separately authorized scripts/setup_runtime.py --download-only")
            artifacts.append({"name": name, "path": str(target), "status": state})
    except (OSError, ValueError, KeyError, TypeError) as exc:
        required_java = None
        build_missing.append(f"cannot read pinned root runtime inputs: {exc}")
    return {"root": str(root), "scope": "root NeoForge MC1.21.1 development prerequisites",
            "tooling_ready": not tooling_missing, "build_inputs_ready": not tooling_missing and not build_missing,
            "compilation": "not run", "python": sys.version.split()[0],
            "java_executable": java, "java_launcher_major": java_major,
            "required_java_toolchain": required_java, "runtime_artifacts": artifacts,
            "missing": tooling_missing + build_missing,
            "next_build": "./gradlew build (may resolve Gradle/toolchain/Maven dependencies; not executed by setup)",
            "other_ports": "Each ports/<version>/<loader> build has separate inputs; not qualified here."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--runtime-source", type=Path, help="explicit existing dev/runtime directory; copy verified missing Iris/Sodium only")
    parser.add_argument("--require-build-inputs", action="store_true")
    args = parser.parse_args()
    report = inspect(args.root.resolve(), args.runtime_source.resolve() if args.runtime_source else None)
    print(json.dumps(report, indent=2))
    return 0 if report["tooling_ready"] and (not args.require_build_inputs or report["build_inputs_ready"]) else 2


if __name__ == "__main__":
    raise SystemExit(main())

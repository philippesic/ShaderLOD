#!/usr/bin/env python3
"""Run the standalone lossless PNG fixture with JDK 21, without Gradle/Minecraft.

Usage: python3 scripts/check_fast_png_writer.py [--java-home /path/to/jdk-21]
Each invocation retains sources' hashes, exact commands, logs, classes and tiny
PNG outputs under a unique ignored build/fast-png-checks/check-* directory.
Run only after the client/GPU owner releases timing runs: this invokes javac.
"""

from __future__ import annotations

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
import time


ROOT = Path(__file__).resolve().parents[1]
WRITER = ROOT / "src/devClient/java/com/pp/shaderlod/dev/FastPngWriter.java"
FIXTURE = ROOT / "tests/java/com/pp/shaderlod/dev/FastPngWriterFixture.java"


def jdk_version(home: Path) -> tuple[Path, Path, str] | None:
    """Require both tools in one JDK 21; probing does not compile any source."""
    suffix = ".exe" if os.name == "nt" else ""
    java = home / "bin" / ("java" + suffix)
    javac = home / "bin" / ("javac" + suffix)
    if not java.is_file() or not javac.is_file():
        return None
    try:
        compiler = subprocess.run([str(javac), "-version"], capture_output=True,
                                  text=True, timeout=10, check=False)
        runtime = subprocess.run([str(java), "-version"], capture_output=True,
                                 text=True, timeout=10, check=False)
    except (OSError, subprocess.TimeoutExpired):
        return None
    compiler_text = compiler.stdout + compiler.stderr
    runtime_text = runtime.stdout + runtime.stderr
    compiler_major = re.search(r"\bjavac\s+(\d+)(?:[.\s+-]|$)", compiler_text)
    runtime_major = re.search(r'\b(?:openjdk|java)\s+(?:version\s+)?"?(\d+)(?:[.\s+-]|$)', runtime_text)
    if (compiler.returncode != 0 or runtime.returncode != 0
            or compiler_major is None or runtime_major is None
            or compiler_major.group(1) != "21" or runtime_major.group(1) != "21"):
        return None
    return java, javac, compiler_text.strip() + "\n" + runtime_text.strip()


def find_jdk(explicit: Path | None) -> tuple[Path, Path, Path, str]:
    if explicit is not None:
        home = explicit.expanduser().resolve()
        tools = jdk_version(home)
        if tools is None:
            raise ValueError(f"--java-home must contain working JDK 21 java/javac: {home}")
        return home, *tools

    candidates: list[Path] = []
    if os.environ.get("JAVA_HOME"):
        candidates.append(Path(os.environ["JAVA_HOME"]))
    executable = shutil.which("javac")
    if executable:
        candidates.append(Path(executable).resolve().parent.parent)
    locator = Path("/usr/libexec/java_home")
    if sys.platform == "darwin" and locator.is_file():
        try:
            located = subprocess.run([str(locator), "-v", "21"], capture_output=True,
                                     text=True, timeout=10, check=False)
            if located.returncode == 0 and located.stdout.strip():
                candidates.append(Path(located.stdout.strip()))
        except (OSError, subprocess.TimeoutExpired):
            pass
    # Narrow fallback patterns avoid probing every installed historical JDK.
    candidates.extend(sorted(Path("/Library/Java/JavaVirtualMachines").glob("*21*/Contents/Home")))
    candidates.extend(sorted(Path("/usr/lib/jvm").glob("*21*")))
    candidates.extend(sorted((Path.home() / ".sdkman/candidates/java").glob("*21*")))
    if os.name == "nt" and os.environ.get("ProgramFiles"):
        program_files = Path(os.environ["ProgramFiles"])
        candidates.extend(sorted((program_files / "Java").glob("*21*")))
        candidates.extend(sorted((program_files / "Eclipse Adoptium").glob("*21*")))
    seen: set[Path] = set()
    for candidate in candidates:
        home = candidate.expanduser().resolve()
        if home in seen:
            continue
        seen.add(home)
        tools = jdk_version(home)
        if tools is not None:
            return home, *tools
    raise ValueError("No JDK 21 found; pass --java-home /path/to/jdk-21")


def source_hashes() -> dict[str, str]:
    return {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in (WRITER, FIXTURE, Path(__file__).resolve())}


def run_command(command: list[str], name: str, output: Path) -> dict:
    started = time.monotonic()
    try:
        result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True,
                                timeout=60, check=False)
        stdout, stderr, code = result.stdout, result.stderr, result.returncode
        timed_out = False
    except subprocess.TimeoutExpired as failure:
        def as_text(value: str | bytes | None) -> str:
            return value.decode("utf-8", errors="replace") if isinstance(value, bytes) else value or ""
        stdout, stderr, code = as_text(failure.stdout), as_text(failure.stderr), None
        timed_out = True
    except OSError as failure:
        stdout, stderr, code, timed_out = "", str(failure), None, False
    (output / f"{name}.stdout.log").write_text(stdout, encoding="utf-8")
    (output / f"{name}.stderr.log").write_text(stderr, encoding="utf-8")
    return {"command": command, "exit_code": code, "timed_out": timed_out,
            "wall_seconds": time.monotonic() - started,
            "stdout_log": f"{name}.stdout.log", "stderr_log": f"{name}.stderr.log"}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", type=Path, help="JDK 21 directory; otherwise autodetect")
    args = parser.parse_args()
    try:
        home, java, javac, version = find_jdk(args.java_home)
        before = source_hashes()
    except (OSError, ValueError) as failure:
        print(f"ERROR: {failure}", file=sys.stderr)
        return 2

    parent = ROOT / "build/fast-png-checks"
    parent.mkdir(parents=True, exist_ok=True)
    output = Path(tempfile.mkdtemp(prefix="check-", dir=parent))
    classes = output / "classes"
    empty = output / "empty-classpath"
    classes.mkdir()
    empty.mkdir()
    report = {"schema": 1, "status": "running", "java_home": str(home),
              "java_version": version, "source_sha256_before": before,
              "output_directory": str(output), "commands": [],
              "evidence_kind": "Standalone lossless PNG regression; no Minecraft rendering or performance acceptance"}
    report_path = output / "check.json"

    def save() -> None:
        report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    save()
    print(f"Standalone PNG check outputs: {output}", flush=True)
    # Explicit empty compiler class/source paths exclude ambient classpaths and
    # implicit repository source discovery. Only these two source files compile.
    compiler = run_command([str(javac), "--release", "21", "-encoding", "UTF-8",
                            "-classpath", str(empty), "-sourcepath", str(empty),
                            "-d", str(classes), str(WRITER), str(FIXTURE)], "compile", output)
    report["commands"].append(compiler)
    save()
    if compiler["exit_code"] == 0:
        fixture = run_command([str(java), "-Djava.awt.headless=true", "-cp", str(classes),
                               "com.pp.shaderlod.dev.FastPngWriterFixture", str(output / "png")],
                              "fixture", output)
        report["commands"].append(fixture)
        fixture_stdout = (output / "fixture.stdout.log").read_text(encoding="utf-8").strip()
        report["fixture_pass_marker"] = fixture_stdout.startswith("PASS: 6 lossless roundtrips")
        report["status"] = ("passed" if fixture["exit_code"] == 0 and report["fixture_pass_marker"]
                            else "fixture_failed")
    else:
        report["status"] = "compile_failed"
    try:
        after = source_hashes()
        report["source_sha256_after"] = after
        report["sources_unchanged"] = before == after
        if before != after:
            report["status"] = "source_changed"
    except OSError as failure:
        report["status"] = "source_unavailable"
        report["failure"] = str(failure)
    save()
    if report["status"] == "passed":
        print((output / "fixture.stdout.log").read_text(encoding="utf-8").strip())
        print(f"PASS: standalone check retained at {report_path}")
        return 0
    print(f"FAIL: {report['status']}; see {report_path}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())

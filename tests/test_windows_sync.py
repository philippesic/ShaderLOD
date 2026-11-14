import hashlib
import os
import json
from pathlib import Path
import subprocess

import pytest

from shaderlod_host.runtime_live import WINDOWS_BUILD, prepare_windows_dh, sync_windows


def checkout(root: Path) -> None:
    artifacts = {
        "iris": b"iris",
        "sodium": b"sodium",
        "distanthorizons": b"dh",
    }
    (root / "dependencies/runtime").mkdir(parents=True)
    (root / "shaderlod_host").mkdir()
    (root / "benchmarks").mkdir()
    (root / "shaderlod_host/module.py").write_text("VALUE = 1\n")
    (root / "benchmarks/spec.json").write_text("{}\n")
    lock = {
        "artifacts": {
            key: {
                "filename": key + ".jar",
                "hashes": {"sha256": hashlib.sha256(value).hexdigest()},
            }
            for key, value in artifacts.items()
        }
    }
    (root / "dependencies/runtime.lock.json").write_text(json.dumps(lock))
    (root / "dependencies/primary-pack.json").write_text(
        json.dumps(
            {"filename": "pack.zip", "sha256": hashlib.sha256(b"pack").hexdigest()}
        )
    )
    for key, value in artifacts.items():
        (root / "dependencies/runtime" / (key + ".jar")).write_bytes(value)
    (root / "dependencies/runtime/pack.zip").write_bytes(b"pack")
    (root / ".gitignore").write_text("dependencies/runtime/\nbuild/\n")
    for command in (
        ["init", "-q"],
        ["add", "-A"],
        ["-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qm", "c"],
    ):
        subprocess.run(["git", "-C", str(root), *command], check=True)


def built(root: Path) -> None:
    for relative in WINDOWS_BUILD:
        target = root / relative
        if relative.endswith(".jar"):
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(b"jar")
        else:
            target.mkdir(parents=True, exist_ok=True)
            (target / "Built.class").write_bytes(b"class")


def test_sync_replaces_code_and_preserves_prepared_state(tmp_path: Path) -> None:
    root = tmp_path / "checkout"
    output = tmp_path / "windows"
    root.mkdir()
    checkout(root)
    for kept in ("run-benchmark/options.txt", "build/moddev/windows-launch.json"):
        (output / kept).parent.mkdir(parents=True, exist_ok=True)
        (output / kept).write_text("prepared")
    (output / "shaderlod_host").mkdir()
    (output / "shaderlod_host/removed.py").write_text("stale")
    result = sync_windows(root, output, built, lambda: False)
    head = subprocess.run(
        ["git", "-C", str(root), "rev-parse", "HEAD"], capture_output=True, check=True
    ).stdout.decode()
    assert result["commit"] == head.strip()
    assert (output / "SOURCE_COMMIT").read_text() == head
    assert not (output / "shaderlod_host/removed.py").exists()
    assert (output / "shaderlod_host/module.py").read_text() == "VALUE = 1\n"
    assert (output / "dependencies/runtime/pack.zip").read_bytes() == b"pack"
    assert (output / "build/classes/java/devClient/Built.class").exists()
    assert (output / "run-benchmark/options.txt").read_text() == "prepared"
    assert (output / "build/moddev/windows-launch.json").read_text() == "prepared"
    assert sorted(p.name for p in output.iterdir() if p.name.startswith(".")) == []


def test_sync_refuses_dirty_checkouts_live_java_and_tampered_artifacts(
    tmp_path: Path,
) -> None:
    root = tmp_path / "checkout"
    root.mkdir()
    checkout(root)
    with pytest.raises(ValueError, match="Java process"):
        sync_windows(root, tmp_path / "a", built, lambda: True)
    (root / "shaderlod_host/module.py").write_text("VALUE = 2\n")
    with pytest.raises(ValueError, match="tracked changes"):
        sync_windows(root, tmp_path / "b", built, lambda: False)
    subprocess.run(["git", "-C", str(root), "checkout", "-q", "."], check=True)
    (root / "dependencies/runtime/iris.jar").write_bytes(b"other")
    with pytest.raises(ValueError, match="hash mismatch"):
        sync_windows(root, tmp_path / "c", built, lambda: False)
    assert not (tmp_path / "c/SOURCE_COMMIT").exists()


def test_dh_slot_adds_every_pinned_mod_and_its_own_launch(tmp_path: Path) -> None:
    root = tmp_path / "windows"
    root.mkdir()
    checkout(root)
    canonical = tmp_path / "canonical"
    (canonical / "config").mkdir(parents=True)
    (canonical / "options.txt").write_text("fov:70\n")
    launch = root / "build/moddev/windows-launch.json"
    launch.parent.mkdir(parents=True)
    launch.write_text(json.dumps({"argv": ["java"], "cwd": "elsewhere"}))
    with pytest.raises(ValueError, match="must run first"):
        prepare_windows_dh(canonical, root)
    launch.write_text(
        json.dumps({"argv": ["java"], "cwd": "elsewhere", "runtime_snapshot": "s"})
    )
    result = prepare_windows_dh(canonical, root)
    profile = root / "run-benchmark-dh"
    assert result["mods"] == ["distanthorizons.jar", "iris.jar", "sodium.jar"]
    assert (profile / "mods/distanthorizons.jar").read_bytes() == b"dh"
    assert (profile / "shaderpacks/pack.zip").read_bytes() == b"pack"
    assert (profile / "options.txt").read_text() == "fov:70\n"
    written = json.loads((root / "build/moddev/windows-launch-dh.json").read_bytes())
    assert written["cwd"] == str(profile)
    assert written["argv"] == ["java"]
    with pytest.raises(ValueError):
        prepare_windows_dh(canonical, root)


def test_contain_copies_outside_runtime_inputs_and_rewrites_references(
    tmp_path: Path,
) -> None:
    from shaderlod_host.runtime_live import contain_windows_runtime

    external = tmp_path / "Bench"
    root = tmp_path / "ShaderLOD"
    java = external / "tools/java/jdk/bin/java"
    java.parent.mkdir(parents=True)
    java.write_bytes(b"java")
    (external / "tools/java/jdk/lib").mkdir()
    (external / "tools/java/jdk/lib/modules").write_bytes(b"modules")
    jars = [external / "cache" / name for name in ("a.jar", "b.jar", "c.jar")]
    for jar in jars:
        jar.parent.mkdir(parents=True, exist_ok=True)
        jar.write_bytes(jar.name.encode())
    inside = root / "build/classes/java/main"
    inside.mkdir(parents=True)
    launch = root / "build/moddev/windows-launch.json"
    launch.parent.mkdir(parents=True)
    launch.write_text(
        json.dumps(
            {
                "argv": [
                    str(java),
                    "-cp",
                    os.pathsep.join([str(inside), str(jars[0]), str(jars[1])]),
                ],
                "cwd": str(root / "run-benchmark"),
            }
        )
    )
    (root / "build/moddev/developmentLegacyClasspath.txt").write_text(
        str(jars[2]) + "\n"
    )
    vm = root / ".local/artifacts/windows-runtime/vm-args.txt"
    vm.parent.mkdir(parents=True)
    vm.write_text("-Xmx6G\n-Dfoo=" + jars[0].as_posix() + "\n")
    result = contain_windows_runtime(root, external)
    contained = root / ".local/artifacts/windows-runtime/external"
    assert result["files"] == 3
    assert (contained / "tools/java/jdk/lib/modules").read_bytes() == b"modules"
    rewritten = json.loads(launch.read_bytes())["argv"]
    assert rewritten[0] == str(contained / "tools/java/jdk/bin/java")
    assert str(contained / "cache/a.jar") in rewritten[2]
    assert str(inside) in rewritten[2]
    for path in (launch, vm, root / "build/moddev/developmentLegacyClasspath.txt"):
        assert str(external) not in path.read_text()
    (contained / "cache/b.jar").write_bytes(b"other")
    launch.write_text(
        json.dumps({"argv": [str(java), "-cp", str(jars[1])], "cwd": str(root)})
    )
    with pytest.raises(ValueError, match="differs"):
        contain_windows_runtime(root, external)

import hashlib
import io
import json
from pathlib import Path
import tarfile
import urllib.error
from unittest.mock import patch

import pytest

from checks.bootstrap import acquire, install_bytes, install_jdk, runtime_artifacts
from checks.contracts import Measured, decode_sample, receipt
from checks.policy import validate
from checks.publisher import publish


def test_boolean_measurements_refused() -> None:
    for value, count in ((True, 1), (1, True), (False, False)):
        with pytest.raises(ValueError, match="D5:"):
            decode_sample({"kind": "measured", "value": value, "count": count})
        with pytest.raises(ValueError, match="D5:"):
            Measured(value, count)
    assert decode_sample({"kind": "measured", "value": 0, "count": 1}) == Measured(0, 1)


def test_failed_publication_leaves_no_partial_result(tmp_path: Path) -> None:
    path = tmp_path / "evidence.json"
    with pytest.raises(TypeError):
        publish(path, {"bad": object()})
    assert list(tmp_path.iterdir()) == []
    with pytest.raises(ValueError):
        publish(path, {"bad": float("nan")})
    assert not path.exists()
    publish(path, {"value": 2})
    assert json.loads(path.read_text()) == {"value": 2}
    with pytest.raises(FileExistsError):
        publish(path, {"value": 3})
    assert json.loads(path.read_text()) == {"value": 2}
    assert list(tmp_path.iterdir()) == [path]


def test_policy_accepts_legitimate_authored_inputs() -> None:
    asset = b"\x89PNG\r\n\x1a\n"
    tree = {
        "docs/PORTING_PLAN.md": b"# Porting plan\n",
        "src/main/java/app/world/Region.java": b"package app.world; class Region {}",
        "schemas/profiler.json": b'{"frames": [], "operationId": "schema-property"}',
        "buffer.py": b"import io\nbuffer = io.StringIO()\nbuffer.write('value')\n",
        "profiler.py": b"import threading\ngpu_buffer_guard = threading.Lock()\n",
        "src/main/resources/icon.png": asset,
        "checks/fixtures.json": json.dumps(
            {
                "src/main/resources/icon.png": {
                    "sha256": hashlib.sha256(asset).hexdigest(),
                    "purpose": "mod icon",
                }
            }
        ).encode(),
    }
    assert validate(tree, [], "policy") == []
    assert any(
        e.startswith("S4:")
        for e in validate({"runs/capture.json": b"{}"}, [], "policy")
    )
    assert any(
        e.startswith("S4:") for e in validate({"src/icon.png": asset}, [], "policy")
    )
    assert any(
        e.startswith("D3:")
        for e in validate(
            {
                "library.py": b"from pathlib import Path\nPath('evidence.json').write_text('bad')\n"
            },
            [],
            "policy",
        )
    )


def test_unknown_receipt_producer_refused() -> None:
    with pytest.raises(ValueError, match="D1:"):
        receipt("cpu-integration", "arbitrary")
    assert (
        receipt("cpu-integration", "cpu-integration")["producer"] == "cpu-integration"
    )


def test_interrupted_download_can_retry(tmp_path: Path) -> None:
    source = tmp_path / "source"
    source.write_bytes(b"verified")
    target = tmp_path / "cache" / "artifact"
    expected = hashlib.sha256(source.read_bytes()).hexdigest()

    def interrupt(url: str, filename: Path) -> None:
        filename.write_bytes(b"partial")
        raise urllib.error.URLError("interrupted")

    with patch("urllib.request.urlretrieve", interrupt):
        with pytest.raises(urllib.error.URLError):
            acquire(source.as_uri(), target, expected)
    assert not target.exists()
    assert list(target.parent.iterdir()) == []
    target.write_bytes(b"old partial")
    acquire(source.as_uri(), target, expected)
    assert target.read_bytes() == b"verified"
    with pytest.raises(RuntimeError, match="checksum"):
        acquire(source.as_uri(), target, "0" * 64)
    assert target.read_bytes() == b"verified"


def test_incomplete_jdk_never_becomes_installed(tmp_path: Path) -> None:
    archive = tmp_path / "jdk.tar"
    java = tmp_path / "installed" / "jdk-test"
    with tarfile.open(archive, "w") as bundle:
        info = tarfile.TarInfo("jdk-test/release")
        info.size = 7
        bundle.addfile(info, io.BytesIO(b"release"))
    with pytest.raises(RuntimeError, match="incomplete JDK archive"):
        install_jdk(archive, java)
    assert not java.exists()
    with tarfile.open(archive, "a") as bundle:
        info = tarfile.TarInfo("jdk-test/bin/java")
        info.size = 4
        bundle.addfile(info, io.BytesIO(b"java"))
    install_jdk(archive, java)
    assert (java / "bin/java").read_bytes() == b"java"


def test_shader_properties_directives_are_executable_syntax() -> None:
    from checks.comments import scan

    assert (
        scan(
            "src/main/resources/shaders.properties",
            "#ifdef DH\nvalue=true\n#endif\n",
            [],
        )
        == []
    )
    assert scan("src/main/resources/shaders.properties", "# explanation\n", [])
    assert scan(
        "src/main/resources/shaders.properties", "#define X 1 // explanation\n", []
    )


def test_invalid_archive_member_keeps_previous_cache(tmp_path: Path) -> None:
    path = tmp_path / "formatter.jar"
    path.write_bytes(b"previous")
    with pytest.raises(RuntimeError, match="checksum"):
        install_bytes(b"partial", path, "0" * 64)
    assert path.read_bytes() == b"previous"
    assert list(tmp_path.iterdir()) == [path]
    expected = hashlib.sha256(b"complete").hexdigest()
    install_bytes(b"complete", path, expected)
    assert path.read_bytes() == b"complete"


def test_runtime_acquisition_reuses_verified_cache(tmp_path: Path) -> None:
    root = tmp_path / "checkout"
    cache = tmp_path / "cache"
    target = root / "dependencies/runtime/mod.jar"
    target.parent.mkdir(parents=True)
    target.write_bytes(b"verified mod")
    expected = hashlib.sha256(target.read_bytes()).hexdigest()
    manifest = root / "dependencies/runtime.lock.json"
    manifest.write_text(
        json.dumps(
            {
                "artifacts": {
                    "mod": {
                        "filename": "mod.jar",
                        "url": "https://unavailable.invalid/mod.jar",
                        "hashes": {"sha256": expected},
                    }
                }
            }
        )
    )
    runtime_artifacts(root, cache)
    target.unlink()
    runtime_artifacts(root, cache)
    assert target.read_bytes() == b"verified mod"
    manifest.write_text(
        json.dumps(
            {
                "artifacts": {
                    "mod": {
                        "filename": "../outside.jar",
                        "url": "unused",
                        "hashes": {"sha256": expected},
                    }
                }
            }
        )
    )
    with pytest.raises(ValueError, match="path escape"):
        runtime_artifacts(root, cache)

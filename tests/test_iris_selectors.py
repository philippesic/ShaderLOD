import io
import json
from pathlib import Path
import re
import struct
import zipfile


def class_methods(data: bytes) -> set[str]:
    stream = io.BytesIO(data)

    def number(size: int) -> int:
        return int.from_bytes(stream.read(size), "big")

    def attributes() -> None:
        for _ in range(number(2)):
            stream.read(2)
            stream.read(number(4))

    assert stream.read(4) == b"\xca\xfe\xba\xbe"
    stream.read(4)
    constants: dict[int, str] = {}
    count = number(2)
    index = 1
    while index < count:
        tag = number(1)
        if tag == 1:
            constants[index] = stream.read(number(2)).decode("utf-8", errors="replace")
        elif tag in (5, 6):
            stream.read(8)
            index += 1
        else:
            sizes = {
                3: 4,
                4: 4,
                7: 2,
                8: 2,
                9: 4,
                10: 4,
                11: 4,
                12: 4,
                15: 3,
                16: 2,
                17: 4,
                18: 4,
                19: 2,
                20: 2,
            }
            stream.read(sizes[tag])
        index += 1
    stream.read(6)
    stream.read(2 * number(2))
    for _ in range(number(2)):
        stream.read(6)
        attributes()
    methods = set()
    for _ in range(number(2)):
        stream.read(2)
        methods.add(constants[number(2)] + constants[number(2)])
        attributes()
    return methods


def test_registered_mixin_selectors_match_pinned_jars() -> None:
    root = Path(__file__).resolve().parents[1]
    lock = json.loads((root / "dependencies/runtime.lock.json").read_text())
    registrations = (
        ("iris", "src/main/resources/shaderlod.mixins.json", "src/main/java"),
        (
            "iris",
            "src/devClient/resources/shaderlod_dev.mixins.json",
            "src/devClient/java",
        ),
        (
            "distanthorizons",
            "src/devClient/resources/shaderlod_dev_dh.mixins.json",
            "src/devClient/java",
        ),
    )
    for artifact in ("iris", "distanthorizons"):
        check_registrations(
            root,
            root / "dependencies/runtime" / lock["artifacts"][artifact]["filename"],
            [
                (config, source)
                for owner, config, source in registrations
                if owner == artifact
            ],
        )


def check_registrations(
    root: Path, archive: Path, registrations: list[tuple[str, str]]
) -> None:
    with zipfile.ZipFile(archive) as jar:
        registered_sources: list[Path] = []
        for config_path, source_path in registrations:
            config = json.loads((root / config_path).read_text())
            names = config.get("mixins", []) + config.get("client", [])
            assert names, f"{config_path}: no registered mixins"
            registered_sources.extend(
                (
                    root
                    / source_path
                    / (config["package"] + "." + name).replace(".", "/")
                ).with_suffix(".java")
                for name in names
            )
        for source in registered_sources:
            text = source.read_text()
            target = re.search(r'@Mixin\(\s*targets\s*=\s*"([^"]+)"', text)
            if target:
                owner = target[1]
            else:
                value = re.search(r"@Mixin\(value = (\w+)\.class", text)
                assert value, f"{source}: unsupported mixin target declaration"
                imported = re.search(r"import ([\w.]+\." + value[1] + r");", text)
                assert imported, f"{source}: target import missing"
                owner = imported[1]
            methods = class_methods(jar.read(owner.replace(".", "/") + ".class"))
            selectors = re.findall(r'method\s*=\s*"([^"]+)"', text)
            assert selectors, f"{source}: no supported method selectors"
            for selector in selectors:
                matches = (
                    selector in methods
                    if "(" in selector
                    else any(method.startswith(selector + "(") for method in methods)
                )
                assert matches, (
                    f"{source}: pinned {archive.name} lacks {owner}.{selector}; review integration before updating"
                )


def test_method_reader_preserves_overload_descriptors() -> None:
    def utf(value: bytes) -> bytes:
        return b"\x01" + struct.pack(">H", len(value)) + value

    constants = utf(b"load") + utf(b"()V") + utf(b"(I)V")
    header = b"\xca\xfe\xba\xbe" + struct.pack(">HHH", 0, 65, 4)
    body = struct.pack(">HHHHHHH", 0, 0, 0, 0, 0, 2, 0)
    first = struct.pack(">HHH", 1, 2, 0)
    second = struct.pack(">HHHH", 0, 1, 3, 0)
    assert class_methods(header + constants + body + first + second) == {
        "load()V",
        "load(I)V",
    }

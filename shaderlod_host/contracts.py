from collections.abc import Mapping
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import re
from types import MappingProxyType
from typing import cast


def text(value: object, name: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{name}: nonempty string required")
    return value


def integer(value: object, name: str, minimum: int = 1) -> int:
    if type(value) is not int or value < minimum:
        raise ValueError(f"{name}: integer >= {minimum} required")
    return value


def mapping(value: object, name: str) -> dict[str, object]:
    if not isinstance(value, dict) or any(not isinstance(k, str) for k in value):
        raise ValueError(f"{name}: object required")
    return cast(dict[str, object], value)


def identity(value: object) -> str:
    result = text(value, "identity")
    if re.fullmatch(r"[a-f0-9]{64}", result) is None:
        raise ValueError("identity: sha256 required")
    return result


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical(value: object) -> bytes:
    return (
        json.dumps(value, allow_nan=False, sort_keys=True, separators=(",", ":")) + "\n"
    ).encode()


@dataclass(frozen=True)
class Request:
    operation: str
    attempt: int
    owner: str
    purpose: str
    argv: tuple[str, ...]
    source: Mapping[str, str]
    instrumentation: str
    runtime_sha256: str
    timeout_ms: int
    max_attempts: int
    cumulative_ms: int

    def wire(self) -> dict[str, object]:
        return {
            "schema": "shaderlod-request-v1",
            "operation": self.operation,
            "attempt": self.attempt,
            "owner": self.owner,
            "purpose": self.purpose,
            "argv": list(self.argv),
            "source": dict(self.source),
            "instrumentation": self.instrumentation,
            "runtime_sha256": self.runtime_sha256,
            "timeout_ms": self.timeout_ms,
            "max_attempts": self.max_attempts,
            "cumulative_ms": self.cumulative_ms,
        }

    @property
    def identity(self) -> str:
        return digest(canonical(self.wire()))


def decode_request(value: object) -> Request:
    data = mapping(value, "request")
    fields = {
        "schema",
        "operation",
        "attempt",
        "owner",
        "purpose",
        "argv",
        "source",
        "instrumentation",
        "runtime_sha256",
        "timeout_ms",
        "max_attempts",
        "cumulative_ms",
    }
    if set(data) != fields or data["schema"] != "shaderlod-request-v1":
        raise ValueError("unsupported request schema or fields")
    operation = text(data["operation"], "operation")
    if re.fullmatch(r"[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}", operation) is None:
        raise ValueError("operation: unsafe identifier")
    purpose = text(data["purpose"], "purpose")
    if purpose not in {"cpu-verify", "cpu-profile"}:
        raise ValueError(
            "purpose unavailable: live and comparison methodology not implemented"
        )
    argv = data["argv"]
    if (
        not isinstance(argv, list)
        or not argv
        or any(not isinstance(a, str) or not a or "\0" in a for a in argv)
    ):
        raise ValueError("argv: nonempty argument vector required")
    source = {k: identity(v) for k, v in mapping(data["source"], "source").items()}
    if not source:
        raise ValueError("source: explicit executable input closure required")
    for path in source:
        if Path(path).is_absolute() or ".." in Path(path).parts or "\\" in path:
            raise ValueError("source: unsafe path")
    instrumentation = text(data["instrumentation"], "instrumentation")
    if instrumentation not in {"none", "diagnostic"}:
        raise ValueError("unsupported instrumentation")
    attempt = integer(data["attempt"], "attempt")
    maximum = integer(data["max_attempts"], "max_attempts")
    if attempt > maximum:
        raise ValueError("attempt limit exhausted")
    return Request(
        operation,
        attempt,
        text(data["owner"], "owner"),
        purpose,
        tuple(cast(list[str], argv)),
        MappingProxyType(source),
        instrumentation,
        identity(data["runtime_sha256"]),
        integer(data["timeout_ms"], "timeout_ms"),
        maximum,
        integer(data["cumulative_ms"], "cumulative_ms"),
    )


def verify_source(root: Path, source: Mapping[str, str]) -> None:
    base = root.resolve(strict=True)
    for name, expected in source.items():
        path = base / name
        if path.is_symlink() or not path.resolve(strict=True).is_relative_to(base):
            raise ValueError("source escapes root")
        if digest(path.read_bytes()) != expected:
            raise ValueError(f"source identity drift: {name}")

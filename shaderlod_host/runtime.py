import json
import os
import sys
from pathlib import Path
import shutil
import socket
import subprocess
import time

from shaderlod_host.contracts import (
    Request,
    decode_request,
    canonical,
    digest,
    integer,
    mapping,
    verify_source,
)
from shaderlod_host.config import worker_environment
from shaderlod_host.storage import BusyRefusal, publish, recover_journal, replace
from shaderlod_host.worker import birth


def validate_control(value: object, request: Request, last_sequence: int) -> int:
    control = mapping(value, "control")
    if (
        set(control)
        != {"schema", "operation", "attempt", "owner", "action", "sequence"}
        or control["schema"] != "shaderlod-control-v1"
    ):
        raise ValueError("unknown control schema")
    integer(control["attempt"], "attempt")
    if (
        control["operation"] != request.operation
        or control["attempt"] != request.attempt
        or control["owner"] != request.owner
    ):
        raise ValueError("foreign or stale control")
    sequence = integer(control["sequence"], "sequence")
    if sequence <= last_sequence or control["action"] != "cancel":
        raise ValueError("replayed or unsupported control")
    return sequence


def budget_identity(request: Request) -> str:
    data = request.wire()
    del data["attempt"]
    return digest(canonical(data))


def execute(
    request: Request,
    source_root: Path,
    output_root: Path,
    ownership_root: Path,
    control: Path | None = None,
    reserve_bytes: int = 0,
) -> dict[str, object]:
    request = decode_request(request.wire())
    if sys.platform != "linux":
        raise ValueError(
            "process supervision unavailable on this platform; Linux only until Windows qualification"
        )
    if (
        not Path(request.argv[0]).is_absolute()
        or digest(Path(request.argv[0]).read_bytes()) != request.runtime_sha256
    ):
        raise ValueError("runtime executable identity disagreement")
    if type(reserve_bytes) is not int or reserve_bytes < 0:
        raise ValueError("invalid disk reserve")
    output_root.mkdir(parents=True, exist_ok=True)
    if output_root.is_symlink() or ownership_root.is_symlink():
        raise ValueError("owned roots cannot be symlinks")
    operation_root = output_root / request.operation
    operation_root.mkdir(exist_ok=True)
    if operation_root.is_symlink():
        raise ValueError("operation root cannot be symlink")
    journal_path = operation_root / "journal.json"
    result_path = operation_root / f"attempt-{request.attempt}.json"
    with BusyRefusal(ownership_root, socket.gethostname() + ":cpu-runner"):
        verify_source(source_root, request.source)
        if shutil.disk_usage(output_root).free < reserve_bytes:
            raise ValueError("insufficient disk headroom")
        reserved = 0
        identity = budget_identity(request)
        if journal_path.exists():
            previous = recover_journal(journal_path, request.owner)
            if previous["state"] == "recovery-required":
                raise ValueError("recovery-required: prior process lifetime unresolved")
            if (
                previous["request"] != identity
                or request.attempt != integer(previous["attempt"], "attempt") + 1
            ):
                raise ValueError("stale attempt or changed execution identity")
            reserved = integer(previous["reserved_ms"], "reserved_ms", 0)
        elif request.attempt != 1:
            raise ValueError("first attempt must be one")
        if (
            request.attempt > request.max_attempts
            or reserved + request.timeout_ms > request.cumulative_ms
        ):
            raise ValueError("cumulative attempt budget exhausted")
        if result_path.exists():
            raise FileExistsError(result_path)
        journal: dict[str, object] = {
            "schema": "shaderlod-journal-v1",
            "owner": request.owner,
            "state": "running",
            "request": identity,
            "reserved_ms": reserved + request.timeout_ms,
            "attempt": request.attempt,
            "process": None,
        }
        replace(journal_path, journal)
        snapshot = operation_root / f"snapshot-{request.attempt}"
        snapshot.mkdir()
        for name, expected in request.source.items():
            data = (source_root / name).read_bytes()
            if digest(data) != expected:
                raise ValueError("source changed during snapshot")
            destination = snapshot / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(data)
            destination.chmod(0o444)
        command = list(request.argv)
        for index, argument in enumerate(command[1:], 1):
            path = Path(argument)
            if path.is_absolute() and path.is_relative_to(source_root.resolve()):
                relative = path.relative_to(source_root.resolve()).as_posix()
                if relative not in request.source:
                    raise ValueError("absolute source argument lacks declared identity")
                command[index] = str(snapshot / relative)
        start = time.monotonic()
        state = "failed"
        reason = "startup failure"
        process: subprocess.Popen[bytes] | None = None
        returncode: int | None = None
        rejected_controls: list[str] = []
        sequence = 0
        try:
            process = subprocess.Popen(
                [
                    sys.executable,
                    "-m",
                    "shaderlod_host.worker",
                    str(start + request.timeout_ms / 1000),
                    str(os.getpid()),
                    birth(os.getpid()),
                    json.dumps(command),
                    str(operation_root / f"process-{request.attempt}.json"),
                    request.identity,
                ],
                cwd=snapshot,
                env=worker_environment(),
                stdin=subprocess.DEVNULL,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            journal["process"] = {
                "host": socket.gethostname(),
                "boot": Path("/proc/sys/kernel/random/boot_id").read_text().strip(),
                "pid": process.pid,
                "birth": birth(process.pid),
            }
            replace(journal_path, journal)
            while True:
                returncode = process.poll()
                if returncode is not None:
                    state = "complete" if returncode == 0 else "failed"
                    reason = (
                        "deadline exhausted" if returncode == 124 else "process exited"
                    )
                    break
                if (time.monotonic() - start) * 1000 >= request.timeout_ms + 500:
                    state, reason = "failed", "deadline exhausted"
                    break
                if control is not None and control.exists():
                    try:
                        sequence = validate_control(
                            json.loads(control.read_bytes()), request, sequence
                        )
                    except (ValueError, OSError) as error:
                        message = str(error)
                        if message not in rejected_controls:
                            rejected_controls.append(message)
                    else:
                        state, reason = "cancelled", "owner cancellation"
                        break
                time.sleep(0.01)
        except OSError as error:
            reason = f"startup failure: {error}"
        finally:
            if process is not None and process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=1)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=1)
            if process is not None:
                returncode = process.returncode
        elapsed_ms = max(0, int((time.monotonic() - start) * 1000))
        source_valid = True
        try:
            verify_source(source_root, request.source)
            verify_source(snapshot, request.source)
        except (ValueError, OSError):
            source_valid = False
            state, reason = "failed", "source identity drift during execution"
        if reserved + elapsed_ms > request.cumulative_ms and state == "complete":
            state, reason = (
                "failed",
                "cumulative resource budget exhausted during cleanup",
            )
        journal["reserved_ms"] = reserved + max(request.timeout_ms, elapsed_ms)
        result: dict[str, object] = {
            "schema": "shaderlod-result-v1",
            "request": request.identity,
            "operation": request.operation,
            "attempt": request.attempt,
            "owner": request.owner,
            "state": state,
            "reason": reason,
            "returncode": returncode,
            "elapsed_ms": elapsed_ms,
            "reserved_ms": journal["reserved_ms"],
            "source_valid": source_valid,
            "process": journal["process"],
            "instrumentation": request.instrumentation,
            "runtime_sha256": request.runtime_sha256,
            "evidence": "cpu-integration",
            "observations": {"process_exit": returncode},
            "rejected_controls": rejected_controls,
            "limits": [
                "Linux process-group supervision; deliberate new-session escape is outside trusted CPU task contract",
                "no live renderer, GPU observation or performance qualification",
                "budget accounting reserves each attempt deadline including interrupted attempts",
                "Windows execution unavailable until equivalent process supervision is implemented and qualified",
            ],
        }
        publish(result_path, result)
        journal["state"] = state
        replace(journal_path, journal)
        return result


def verify_result(value: object, request: Request) -> dict[str, object]:
    result = mapping(value, "result")
    expected = {
        "schema",
        "request",
        "operation",
        "attempt",
        "owner",
        "state",
        "reason",
        "returncode",
        "elapsed_ms",
        "reserved_ms",
        "source_valid",
        "process",
        "instrumentation",
        "runtime_sha256",
        "evidence",
        "observations",
        "rejected_controls",
        "limits",
    }
    if set(result) != expected or result["schema"] != "shaderlod-result-v1":
        raise ValueError("unknown result schema or fields")
    integer(result["attempt"], "attempt")
    if (
        result["request"] != request.identity
        or result["operation"] != request.operation
        or result["attempt"] != request.attempt
        or result["owner"] != request.owner
        or result["instrumentation"] != request.instrumentation
        or result["runtime_sha256"] != request.runtime_sha256
    ):
        raise ValueError("result identity disagreement")
    if (
        result["state"] not in {"complete", "failed", "cancelled"}
        or result["evidence"] != "cpu-integration"
    ):
        raise ValueError("result state/evidence unavailable")
    integer(result["elapsed_ms"], "elapsed_ms", 0)
    reserved = integer(result["reserved_ms"], "reserved_ms", 0)
    if (reserved > request.cumulative_ms and result["state"] == "complete") or type(
        result["source_valid"]
    ) is not bool:
        raise ValueError("invalid resource/source state")
    if reserved < integer(result["elapsed_ms"], "elapsed_ms", 0):
        raise ValueError("resource accounting undercounts elapsed work")
    process = result["process"]
    if process is not None:
        observed = mapping(process, "process")
        if set(observed) != {"host", "boot", "pid", "birth"}:
            raise ValueError("unknown process identity")
        integer(observed["pid"], "pid")
        for field in ("host", "boot", "birth"):
            if not isinstance(observed[field], str) or not observed[field]:
                raise ValueError("invalid process identity")
    code = result["returncode"]
    if code is not None and type(code) is not int:
        raise ValueError("invalid process exit")
    if result["state"] == "complete" and (
        code != 0 or result["source_valid"] is not True
    ):
        raise ValueError("false completion")
    if (
        not isinstance(result["limits"], list)
        or not isinstance(result["rejected_controls"], list)
        or not isinstance(result["reason"], str)
    ):
        raise ValueError("invalid result reporting")
    if mapping(result["observations"], "observations") != {"process_exit": code}:
        raise ValueError("observed process disagreement")
    return result


def recover_execution(
    request: Request, output_root: Path, ownership_root: Path
) -> dict[str, object]:
    if sys.platform != "linux":
        raise ValueError("recovery unavailable outside qualified Linux process model")
    request = decode_request(request.wire())
    operation_root = output_root / request.operation
    if (
        output_root.is_symlink()
        or operation_root.is_symlink()
        or ownership_root.is_symlink()
    ):
        raise ValueError("recovery roots cannot be symlinks")
    with BusyRefusal(ownership_root, socket.gethostname() + ":cpu-runner"):
        journal_path = operation_root / "journal.json"
        if journal_path.is_symlink():
            raise ValueError("recovery journal cannot be symlink")
        journal = recover_journal(journal_path, request.owner)
        if (
            journal["request"] != budget_identity(request)
            or journal["attempt"] != request.attempt
        ):
            raise ValueError("recovery request identity disagreement")
        if journal["state"] not in {"recovery-required", "interrupted"}:
            raise ValueError("no interrupted attempt to reconcile")
        path = operation_root / f"process-{request.attempt}.json"
        if path.is_symlink():
            raise ValueError("child identity cannot be symlink")
        child = mapping(json.loads(path.read_bytes()), "child identity")
        boot = Path("/proc/sys/kernel/random/boot_id").read_text().strip()
        if (
            set(child) != {"schema", "request", "host", "boot", "pid", "birth"}
            or child["schema"] != "shaderlod-child-v1"
            or child["request"] != request.identity
            or child["host"] != socket.gethostname()
            or child["boot"] != boot
        ):
            raise ValueError("unverifiable child identity; preserve recovery state")
        leader = integer(child["pid"], "child pid")
        if not isinstance(child["birth"], str) or not child["birth"].isdecimal():
            raise ValueError("invalid child birth identity")
        process = mapping(journal["process"], "watchdog identity")
        if (
            set(process) != {"host", "boot", "pid", "birth"}
            or process["host"] != socket.gethostname()
            or process["boot"] != boot
        ):
            raise ValueError("unverifiable watchdog identity")
        watchdog = integer(process["pid"], "watchdog pid")
        for entry in Path("/proc").iterdir():
            if not entry.name.isdecimal():
                continue
            try:
                value = (entry / "stat").read_text()
            except FileNotFoundError:
                continue
            fields = value[value.rfind(")") + 2 :].split()
            if fields[0] == "Z":
                continue
            if (
                (int(entry.name) == watchdog and fields[19] == process["birth"])
                or int(fields[2]) == leader
                or int(fields[3]) == leader
            ):
                raise ValueError(
                    "owned process lifetime not quiescent; recovery refused"
                )
        journal["state"] = "interrupted"
        replace(journal_path, journal)
        return {
            "schema": "shaderlod-recovery-v1",
            "request": request.identity,
            "state": "interrupted",
            "reserved_ms": journal["reserved_ms"],
            "action": "verified quiescence; no processes killed; original attempt preserved",
        }

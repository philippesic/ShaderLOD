import argparse
from contextlib import nullcontext
import hashlib
import json
import os
from pathlib import Path
import socket
import shlex
import shutil
import subprocess
import sys
import threading
import time

from shaderlod_host.config import (
    display_environment,
    evidence_root,
    worker_environment,
)
from shaderlod_host.functional_contract import verify_functional
from shaderlod_host.storage import (
    BusyRefusal,
    publish,
    profile_artifact,
)
from shaderlod_host.worker import birth, boot


def executable_inputs(
    launch: dict[str, object], root: Path, profile: Path | None = None
) -> dict[str, str]:
    paths: set[Path] = set()
    pending = list(launch["argv"]) if isinstance(launch["argv"], list) else []
    visited: set[str] = set()
    while pending:
        token = str(pending.pop())
        if token.startswith("@") and token not in visited:
            visited.add(token)
            path = Path(token[1:])
            paths.add(path)
            pending.extend(shlex.split(path.read_text(), comments=True))
        else:
            for piece in token.split(os.pathsep):
                path = Path(piece)
                if path.is_absolute() and path.is_file():
                    paths.add(path)
    legacy = root / "build/moddev/developmentLegacyClasspath.txt"
    paths.add(legacy)
    paths.update(Path(line) for line in legacy.read_text().splitlines() if line)
    paths.update((root / "shaderlod_host").glob("*.py"))
    if profile is not None:
        paths.update((profile / "mods").glob("*.jar"))
        paths.update(
            path for path in (profile / "resourcepacks").rglob("*") if path.is_file()
        )
    for directory in [
        root / "build/classes/java",
        root / "build/resources",
        root / "dependencies/runtime",
    ]:
        paths.update(path for path in directory.rglob("*") if path.is_file())
    return {
        str(path): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in sorted(paths)
        if path.is_file()
    }


PROBE_OUTPUTS = (
    "shaderlod-functional.json",
    "shaderlod-functional.png",
    "shaderlod-functional-identity.png",
)


def preserve_outputs(profile: Path, destination: Path) -> dict[str, str]:
    preserved: dict[str, str] = {}
    for name in PROBE_OUTPUTS:
        source = profile / name
        if source.is_file():
            destination.mkdir(parents=True, exist_ok=True)
            preserved[name] = hashlib.sha256(source.read_bytes()).hexdigest()
            shutil.move(source, destination / name)
    return preserved


def read_functional(path: Path) -> tuple[dict[str, object] | None, str | None]:
    try:
        observed = json.loads(path.read_bytes()) if path.exists() else None
        if observed is not None and not isinstance(observed, dict):
            raise ValueError("functional output must be an object")
        return observed, None
    except (OSError, ValueError) as error:
        return None, str(error)


def attempt_receipt(requested: Path | None, repository: Path, mode: str) -> Path:
    if requested is None:
        stamp = time.strftime("%Y%m%dT%H%M%SZ", time.gmtime())
        requested = evidence_root() / "attempts" / f"{stamp}-{mode}.json"
    resolved = requested.resolve()
    if resolved.is_relative_to(repository.resolve()):
        raise ValueError(
            "receipts belong outside the repository; omit --receipt to use "
            + str(evidence_root() / "attempts")
        )
    if resolved.exists() or resolved.with_suffix("").exists():
        raise ValueError("receipt already exists; choose a new --receipt or omit it")
    resolved.parent.mkdir(parents=True, exist_ok=True)
    return resolved


def benchmark_session(
    launch_path: Path,
    profile: Path,
    display: Path,
    session: Path,
    ownership: Path,
    timeout: int,
    world: str = "ShaderLOD-Smoke",
    ancestor: tuple[int, str] | None = None,
) -> int:
    if sys.platform not in {"linux", "win32"} or not 1 <= timeout <= 14400:
        raise ValueError("Linux/Windows session with 1–14400 second deadline required")
    root = Path(__file__).resolve().parents[1]
    if session.exists() or session.resolve().is_relative_to(root):
        raise ValueError("fresh external session directory required")
    if not world or world in {".", ".."} or "/" in world or "\\" in world:
        raise ValueError("single prepared world name required")
    if not profile.is_dir() or not (profile / "saves" / world).is_dir():
        raise ValueError("prepared profile and selected world required")
    launch = json.loads(launch_path.read_bytes())
    if not isinstance(launch.get("argv"), list) or not all(
        isinstance(a, str) for a in launch["argv"]
    ):
        raise ValueError("invalid launch argv")
    argv = list(launch["argv"])
    if "-cp" not in argv:
        raise ValueError("explicit classpath launch required")
    position = argv.index("-cp")
    argv[position:position] = [
        "-Dshaderlod.functionalProbe=false",
        "-Dshaderlod.benchmarkDirectory=" + str(session.resolve()),
    ]
    profile = profile.resolve(strict=True)
    shipping = Path(launch["shipping_jar"])
    import zipfile

    with zipfile.ZipFile(shipping) as archive:
        for compiled in (root / "build/classes/java/main").rglob("*.class"):
            member = compiled.relative_to(root / "build/classes/java/main").as_posix()
            if archive.read(member) != compiled.read_bytes():
                raise ValueError(
                    "shipping JAR differs from compiled sources; build jar before opening session"
                )
    environment = (
        worker_environment() if os.name == "nt" else display_environment(display)
    )
    environment.update(launch["environment"])
    with BusyRefusal(ownership, socket.gethostname() + ":gpu:0"):
        recover_profile_artifact(profile)
        session.mkdir(parents=True)
        found_world = False
        for index, token in enumerate(argv):
            if token.startswith("@"):
                args = shlex.split(Path(token[1:]).read_text(), comments=True)
                if "--quickPlaySingleplayer" in args:
                    position = args.index("--quickPlaySingleplayer") + 1
                    if position >= len(args):
                        raise ValueError("missing quick-play world argument")
                    args[position] = world
                    from shaderlod_host.storage import publish_bytes

                    frozen = session / "world-program-args.txt"
                    publish_bytes(
                        frozen,
                        (
                            "\n".join(
                                '"' + a.replace("\\", "\\\\").replace('"', '\\"') + '"'
                                for a in args
                            )
                            + "\n"
                        ).encode(),
                    )
                    argv[index] = "@" + str(frozen)
                    found_world = True
        if not found_world:
            raise ValueError("launch must declare one quick-play world")
        launch = dict(launch, argv=argv)
        publish(session / "launch.json", launch)
        launch_path = session / "launch.json"
        with profile_artifact(profile, shipping, session):
            from shaderlod_host.storage import snapshot_inputs

            snapshot_inputs(root, session / "inputs.tar")
            before = executable_inputs(launch, root, profile)
            with (session / "client.log").open("xb") as log:
                worker = subprocess.Popen(
                    [
                        sys.executable,
                        "-m",
                        "shaderlod_host.worker",
                        str(time.monotonic() + timeout),
                        str(os.getpid()),
                        birth(os.getpid()),
                        json.dumps(argv),
                        str(session / "child.json"),
                        "session",
                    ]
                    + (
                        []
                        if ancestor is None
                        else [json.dumps({"pid": ancestor[0], "birth": ancestor[1]})]
                    ),
                    cwd=profile,
                    env=worker_environment(environment),
                    stdout=log,
                    stderr=subprocess.STDOUT,
                    stdin=subprocess.DEVNULL,
                )
                publish(
                    session / "session.json",
                    {
                        "schema": "shaderlod-session-v1",
                        "host": socket.gethostname(),
                        "boot": boot(),
                        "owner_pid": os.getpid(),
                        "owner_birth": birth(os.getpid()),
                        "profile": str(profile.resolve()),
                        "world": world,
                        "launch": str(launch_path.resolve()),
                        "executable_inputs": before,
                        "input_snapshot_sha256": hashlib.sha256(
                            (session / "inputs.tar").read_bytes()
                        ).hexdigest(),
                        "timeout_seconds": timeout,
                        "capabilities": [
                            "status",
                            "capture",
                            "select",
                            "restore",
                            "prepare",
                            "screenshot",
                            "close",
                            "validate",
                            "recover",
                        ],
                        "qualification": "unqualified development session",
                        "child_record": str(session / "child.json"),
                    },
                )
                try:
                    telemetry_stop = threading.Event()
                    telemetry_errors: list[str] = []
                    telemetry = threading.Thread(
                        target=record_telemetry,
                        args=(session, telemetry_stop, telemetry_errors),
                        daemon=True,
                    )
                    telemetry.start()
                    try:
                        status = worker.wait(timeout=timeout + 5)
                    except KeyboardInterrupt:
                        status = 130
                    finally:
                        telemetry_stop.set()
                        telemetry.join(timeout=3)
                finally:
                    if worker.poll() is None:
                        worker.terminate()
                        worker.wait(timeout=3)
                unchanged = before == executable_inputs(launch, root, profile)
        publish(
            session / "closed.json",
            {
                "schema": "shaderlod-session-close-v1",
                "returncode": status,
                "inputs_unchanged": unchanged,
                "profile_artifact_restored": True,
                "telemetry_errors": telemetry_errors,
                "telemetry_stopped": not telemetry.is_alive(),
                "restoration": "profile artifact restored; shader/world state is established by acknowledged close and recovery journals",
                "shader_recovery_pending": (
                    profile / "shaderlod-recovery.json"
                ).exists(),
                "world_recovery_pending": (
                    profile / "shaderlod-benchmark-world.json"
                ).exists(),
            },
        )
        return status


def record_telemetry(
    session: Path, stopping: threading.Event, errors: list[str]
) -> None:
    from shaderlod_host.telemetry import NvidiaSensors, host_sample

    sensors = NvidiaSensors()
    started = time.monotonic_ns()
    index = 0
    try:
        while not stopping.is_set():
            index += 1
            sensor_started = time.perf_counter_ns()
            child = (
                json.loads((session / "child.json").read_bytes())
                if (session / "child.json").exists()
                else None
            )
            record = host_sample(None if child is None else child["pid"])
            record["schema"] = "shaderlod-telemetry-v1"
            record["elapsed_ns"] = time.monotonic_ns() - started
            record["unix_ns"] = time.time_ns()
            record["gpu"] = sensors.sample()
            record["sample_cost_ns"] = time.perf_counter_ns() - sensor_started
            publish(session / "telemetry" / (str(index) + ".json"), record)
            stopping.wait(1)
    except (OSError, ValueError, AttributeError) as error:
        errors.append(str(error))
    finally:
        sensors.close()


def recover_profile_artifact(profile: Path) -> dict[str, object] | None:
    from shaderlod_host.storage import restore_profile_artifact

    marker = profile / "shaderlod-benchmark-profile.json"
    if not marker.exists():
        return None
    record = json.loads(marker.read_bytes())
    previous = Path(record["session"])
    metadata = json.loads((previous / "session.json").read_bytes())
    if (
        metadata.get("host") != socket.gethostname()
        or Path(metadata["profile"]).resolve() != profile.resolve()
    ):
        raise ValueError("foreign profile-artifact recovery owner")
    for pid, identity in [(metadata["owner_pid"], metadata["owner_birth"])]:
        if metadata["boot"] == boot():
            try:
                if birth(pid) == identity:
                    raise ValueError("profile-artifact owner is still live")
            except (OSError, ProcessLookupError):
                pass
    child = json.loads((previous / "child.json").read_bytes())
    if child.get("host") != metadata["host"] or child.get("boot") != metadata["boot"]:
        raise ValueError("profile recovery child identity disagreement")
    if child["boot"] == boot():
        try:
            if birth(child["pid"]) == child["birth"]:
                raise ValueError("owned client still live; profile recovery refused")
        except (OSError, ProcessLookupError):
            pass
    restore_profile_artifact(profile, record)
    outcome: dict[str, object] = {
        "schema": "shaderlod-profile-recovery-v1",
        "artifact_restored": True,
        "previous_session": str(previous),
        "world_journal_pending": (profile / "shaderlod-benchmark-world.json").exists(),
    }
    publish(previous / "profile-recovery.json", outcome)
    return outcome


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Bounded Asahi functional execution, no performance study"
    )
    parser.add_argument("--launch", type=Path, required=True)
    parser.add_argument("--profile", type=Path)
    parser.add_argument("--without-dh", action="store_true")
    parser.add_argument("--native-control", action="store_true")
    parser.add_argument("--shipping-jar", type=Path)
    parser.add_argument("--display", type=Path, required=True)
    parser.add_argument("--receipt", type=Path)
    parser.add_argument("--ownership-root", type=Path, required=True)
    parser.add_argument("--timeout", type=int, default=180)
    args = parser.parse_args()
    if sys.platform != "linux" or not 1 <= args.timeout <= 600:
        parser.error("Linux and bounded 1–600 second timeout required")
    launch = json.loads(args.launch.read_bytes())
    argv = launch["argv"]
    if not isinstance(argv, list) or not all(isinstance(a, str) for a in argv):
        raise ValueError("invalid argument vector")
    if args.shipping_jar is not None:
        if args.native_control or args.shipping_jar.is_symlink():
            raise ValueError(
                "shipping probe requires a regular JAR and development probe"
            )
        root = Path(__file__).resolve().parents[1]
        rewritten = []
        for item in argv:
            if item.startswith("-Dfml.modFolders="):
                entries = [
                    entry
                    for entry in item.split("=", 1)[1].split(":")
                    if not entry.startswith("shaderlod%%")
                ]
                rewritten.append("-Dfml.modFolders=" + ":".join(entries))
            else:
                rewritten.append(item)
        argv = rewritten
        if "-cp" in argv:
            position = argv.index("-cp") + 1
            excluded = {
                str(root / "build/classes/java/main"),
                str(root / "build/resources/main"),
            }
            argv[position] = ":".join(
                item for item in argv[position].split(":") if item not in excluded
            )
        launch["argv"] = argv
    if args.native_control:
        argv = [item for item in argv if not item.startswith("-Dfml.modFolders=")]
        if "-cp" in argv:
            position = argv.index("-cp") + 1
            root = Path(__file__).resolve().parents[1]
            argv[position] = ":".join(
                item
                for item in argv[position].split(":")
                if not item.startswith(str(root / "build/classes"))
                and not item.startswith(str(root / "build/resources"))
            )
        launch["argv"] = argv
    mode = (
        "native-control"
        if args.native_control
        else "without-dh"
        if args.without_dh
        else "dh"
    )
    receipt = attempt_receipt(args.receipt, Path(__file__).resolve().parents[1], mode)
    profile = (args.profile or Path(launch["cwd"])).resolve(strict=True)
    if (profile / "shaderlod-functional.json").exists():
        raise ValueError(
            "earlier probe outputs remain in "
            + str(profile)
            + "; move shaderlod-functional* under "
            + str(evidence_root())
            + " before a new attempt"
        )
    environment = display_environment(args.display)
    environment.update(launch["environment"])
    with BusyRefusal(args.ownership_root, socket.gethostname() + ":gpu:0"):
        root = Path(__file__).resolve().parents[1]
        lock = json.loads((root / "dependencies/runtime.lock.json").read_bytes())
        mods = profile / "mods"
        mods.mkdir(exist_ok=True)
        artifacts = {
            key: value
            for key, value in lock["artifacts"].items()
            if not (args.without_dh and key == "distanthorizons")
        }
        expected = {item["filename"] for item in artifacts.values()}
        attempt_directory = receipt.with_suffix("")
        attempt_directory.mkdir(parents=True)
        publish(
            attempt_directory / "session.json",
            {
                "host": socket.gethostname(),
                "boot": boot(),
                "owner_pid": os.getpid(),
                "owner_birth": birth(os.getpid()),
                "profile": str(profile),
            },
        )
        artifact_context = (
            profile_artifact(profile, args.shipping_jar, attempt_directory)
            if args.shipping_jar is not None
            else nullcontext()
        )
        with artifact_context:
            shipping = None
            if args.shipping_jar is not None:
                shipping = {
                    "filename": args.shipping_jar.name,
                    "sha256": hashlib.sha256(
                        args.shipping_jar.read_bytes()
                    ).hexdigest(),
                }
                expected.add(args.shipping_jar.name)

            if any(p.name not in expected for p in mods.iterdir()):
                raise ValueError(
                    "unmanaged profile mod; preserve and resolve before launch"
                )
            for item in artifacts.values():
                payload = (
                    root / "dependencies/runtime" / item["filename"]
                ).read_bytes()
                if hashlib.sha256(payload).hexdigest() != item["hashes"]["sha256"]:
                    raise ValueError("runtime mod identity disagreement")
                target = mods / item["filename"]
                if target.exists():
                    if target.is_symlink() or target.read_bytes() != payload:
                        raise ValueError("profile mod drift")
                else:
                    target.write_bytes(payload)
            before = executable_inputs(launch, root, profile)
            started = time.monotonic()
            with receipt.with_suffix(".log").open("xb") as log:
                worker = subprocess.Popen(
                    [
                        sys.executable,
                        "-m",
                        "shaderlod_host.worker",
                        str(time.monotonic() + args.timeout),
                        str(os.getpid()),
                        birth(os.getpid()),
                        json.dumps(argv),
                        str(receipt.with_suffix("") / "child.json"),
                        "functional",
                    ],
                    cwd=profile,
                    env=worker_environment(environment),
                    stdin=subprocess.DEVNULL,
                    stdout=log,
                    stderr=subprocess.STDOUT,
                )
                try:
                    status = worker.wait(timeout=args.timeout + 5)
                finally:
                    if worker.poll() is None:
                        worker.terminate()
                        worker.wait(timeout=3)
                functional = profile / "shaderlod-functional.json"
                observed, contract_error = read_functional(functional)
                if observed is not None:
                    pin = json.loads(
                        (root / "dependencies/primary-pack.json").read_bytes()
                    )
                    try:
                        observed = verify_functional(
                            observed,
                            profile,
                            pin["filename"],
                            pin["sha256"],
                            not args.without_dh,
                        )
                    except (OSError, ValueError) as error:
                        contract_error = str(error)
                outputs = preserve_outputs(profile, receipt.with_suffix(""))
                result = {
                    "schema": "shaderlod-live-attempt-v1",
                    "platform": sys.platform,
                    "scope": "live-functional only",
                    "returncode": status,
                    "elapsed_seconds": time.monotonic() - started,
                    "java_sha256": hashlib.sha256(
                        Path(argv[0]).read_bytes()
                    ).hexdigest(),
                    "launch_sha256": hashlib.sha256(
                        args.launch.read_bytes()
                    ).hexdigest(),
                    "functional": observed,
                    "dh_mode": "absent" if args.without_dh else "enabled",
                    "native_control": args.native_control,
                    "shipping_jar": shipping,
                    "profile": str(profile),
                    "outputs": {
                        "directory": str(receipt.with_suffix("")),
                        "sha256": outputs,
                    },
                    "executable_inputs": before,
                    "inputs_unchanged": before
                    == executable_inputs(launch, root, profile),
                    "outcome": "PASS"
                    if status == 0
                    and observed is not None
                    and observed.get("outcome") == "PASS"
                    else "FAIL",
                    "limits": "Advisory participating-owner exclusion; ambient occupancy must be checked before launch; no image-equivalence/performance qualification",
                }
                if contract_error is not None:
                    result["outcome"] = "FAIL"
                    result["contract_failure"] = contract_error
                if result["inputs_unchanged"] is not True:
                    result["outcome"] = "FAIL"
                    result["identity_failure"] = "Executable input drift"
                publish(receipt, result)
                print("RECEIPT: " + str(receipt))
                print(
                    json.dumps(
                        {
                            key: result[key]
                            for key in (
                                "schema",
                                "scope",
                                "outcome",
                                "returncode",
                                "elapsed_seconds",
                            )
                        },
                        sort_keys=True,
                    )
                )
                return 0 if result["outcome"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())


def prepare_windows_runtime(
    legacy: Path, canonical_profile: Path, root: Path
) -> dict[str, object]:
    from shaderlod_host.storage import copytree, publish_bytes, replace_bytes

    if os.name != "nt":
        raise ValueError("native Windows preparation required")
    profile = root / "run-benchmark"
    copytree(canonical_profile, profile, 1073741824)
    python = root / "tools/python"
    copytree(legacy.parent / "tools/python", python, 268435456)
    for pth in python.glob("python*._pth"):
        replace_bytes(pth, pth.read_bytes() + b"\n" + str(root).encode() + b"\n")
    lock = json.loads((root / "dependencies/runtime.lock.json").read_bytes())
    mods = profile / "mods"
    mods.mkdir(exist_ok=True)
    for key, item in lock["artifacts"].items():
        if key == "distanthorizons":
            continue
        payload = (root / "dependencies/runtime" / item["filename"]).read_bytes()
        if hashlib.sha256(payload).hexdigest() != item["hashes"]["sha256"]:
            raise ValueError("runtime artifact hash mismatch")
        publish_bytes(mods / item["filename"], payload)
    pin = json.loads((root / "dependencies/primary-pack.json").read_bytes())
    pack = root / "dependencies/runtime" / pin["filename"]
    if hashlib.sha256(pack.read_bytes()).hexdigest() != pin["sha256"]:
        raise ValueError("primary pack mismatch")
    publish_bytes(profile / "shaderpacks" / pin["filename"], pack.read_bytes())
    build = root / "build/moddev"
    build.mkdir(parents=True, exist_ok=True)
    jars = [
        line
        for line in (legacy / "build/moddev/clientLodOnLegacyClasspath.txt")
        .read_text()
        .splitlines()
        if line.lower().endswith(".jar")
    ]
    artifacts = legacy / "build/moddev/artifacts"
    jars = [
        str(artifacts / ("neoforge-" + lock["neoforge"] + suffix))
        for suffix in (".jar", "-client-extra-aka-minecraft-resources.jar")
    ] + jars
    if not jars or not all(Path(line).is_file() for line in jars):
        raise ValueError("legacy runtime dependencies unavailable")
    publish_bytes(
        build / "developmentLegacyClasspath.txt", ("\n".join(jars) + "\n").encode()
    )
    program = (
        (legacy / "build/moddev/clientLodOnRunProgramArgs.txt")
        .read_text()
        .split("# User Supplied Program Arguments")[0]
    )
    publish_bytes(
        build / "benchmarkRunProgramArgs.txt",
        (program + "\n--quickPlaySingleplayer\nShaderLOD-Smoke\n").encode(),
    )
    java = next((legacy.parent / "tools/java").glob("*/bin/java.exe"))
    classpaths = (
        [
            str(root / "build/classes/java" / name)
            for name in ("main", "devClient", "benchmarkCore")
        ]
        + [str(root / "build/resources" / name) for name in ("main", "devClient")]
        + jars
    )
    manifest: dict[str, object] = {
        "shipping_jar": str(root / "build/libs/shaderlod-0.1.0.jar"),
        "argv": [
            str(java),
            "@" + str(legacy / "build/moddev/clientLodOnRunVmArgs.txt"),
            "-Dfml.modFolders="
            + ";".join(
                mod + "%%" + str(root / suffix)
                for mod, suffix in [
                    ("shaderlod", "build/classes/java/main"),
                    ("shaderlod", "build/resources/main"),
                    ("shaderlod_dev", "build/classes/java/devClient"),
                    ("shaderlod_dev", "build/resources/devClient"),
                ]
            ),
            "-cp",
            ";".join(classpaths),
            "@" + str(build / "benchmarkRunProgramArgs.txt"),
        ],
        "cwd": str(profile),
        "environment": {},
    }
    publish(build / "windows-launch.json", manifest)
    return {
        "schema": "shaderlod-windows-preparation-v1",
        "launch": str(build / "windows-launch.json"),
        "profile": str(profile),
        "python": str(python / "python.exe"),
        "legacy_inputs_preserved": True,
    }


def execute_campaign(
    campaign: object,
    launch: Path,
    profile: Path,
    display: Path,
    ownership: Path,
    output: Path,
    world: str,
    resume: bool = False,
    control: Path | None = None,
) -> dict[str, object]:
    with BusyRefusal(output.parent / (output.name + "-ownership"), "campaign"):
        return campaign_attempts(
            campaign,
            launch,
            profile,
            display,
            ownership,
            output,
            world,
            resume,
            control,
        )


def campaign_attempts(
    campaign: object,
    launch: Path,
    profile: Path,
    display: Path,
    ownership: Path,
    output: Path,
    world: str,
    resume: bool,
    control: Path | None,
) -> dict[str, object]:
    from shaderlod_host.assessment import assess
    from shaderlod_host.benchmark import session_command, wait_ready
    from shaderlod_host.campaign import Campaign
    from shaderlod_host.comparison import execute_comparison

    if not isinstance(campaign, Campaign):
        raise ValueError("decoded campaign required")
    root = Path(__file__).resolve().parents[1]
    input_identity: dict[str, object] = {
        "inputs": executable_inputs(json.loads(launch.read_bytes()), root, profile),
        "profile": str(profile.resolve()),
        "world": world,
    }
    if resume:
        if (
            (output / "result.json").exists()
            or json.loads((output / "campaign.json").read_bytes()) != campaign.wire()
            or json.loads((output / "campaign-inputs.json").read_bytes())
            != input_identity
        ):
            raise ValueError(
                "resume requires the same unfinished frozen plan, source, profile and world"
            )
    else:
        if output.exists():
            raise ValueError(
                "fresh campaign output required; use explicit --resume for an unfinished campaign"
            )
        output.mkdir(parents=True)
        publish(output / "campaign.json", campaign.wire())
        publish(output / "campaign-inputs.json", input_identity)
    spent = sum(
        int(json.loads(path.read_bytes())["reserved_ms"])
        for path in output.glob("admission-*.json")
    )
    deadline = time.monotonic() + (campaign.budget_ms - spent) / 1000
    runs = []
    failures = []
    for index, comparison in enumerate(campaign.comparisons):
        session = output / ("session-" + str(index + 1))
        allowance = (campaign.startup_ms + comparison.budget_ms) / 1000 + 15
        admission = output / ("admission-" + str(index + 1) + ".json")
        if admission.exists():
            run = output / ("comparison-" + str(index + 1))
            closed = (
                json.loads((session / "closed.json").read_bytes())
                if (session / "closed.json").exists()
                else {}
            )
            completed = (
                json.loads((run / "result.json").read_bytes())
                if (run / "result.json").exists()
                else {}
            )
            if (
                completed.get("state") != "complete"
                or closed.get("returncode") != 0
                or closed.get("world_recovery_pending") is not False
                or closed.get("shader_recovery_pending") is not False
                or closed.get("profile_artifact_restored") is not True
            ):
                raise ValueError(
                    "prior admitted session is incomplete or not proven closed; no replay or automatic replacement"
                )
            receipt = json.loads((run / "receipt.json").read_bytes())
            if (
                receipt.get("sha256")
                != hashlib.sha256((run / "result.json").read_bytes()).hexdigest()
            ):
                raise ValueError("resumed comparison receipt differs")
            runs.append(run / "result.json")
            continue
        if control is not None and control.exists():
            request = json.loads(control.read_bytes())
            if (
                set(request) != {"action", "after_sessions"}
                or request["action"] != "pause"
                or type(request["after_sessions"]) is not int
                or request["after_sessions"] < 0
            ):
                raise ValueError(
                    "campaign control requires explicit pause and completed-session boundary"
                )
            if len(runs) >= request["after_sessions"]:
                paused: dict[str, object] = {
                    "schema": "shaderlod-campaign-pause-v1",
                    "state": "paused",
                    "completed_sessions": len(runs),
                    "reserved_ms": spent,
                    "remaining_plan_unchanged": True,
                }
                checkpoint = output / ("paused-" + str(len(runs)) + ".json")
                if not checkpoint.exists():
                    publish(checkpoint, paused)
                return paused
        if time.monotonic() + allowance > deadline:
            failures.append(
                {
                    "session": index + 1,
                    "failure": "remaining campaign budget cannot admit complete planned session",
                }
            )
            break
        publish(
            admission,
            {
                "schema": "shaderlod-campaign-admission-v1",
                "session": index + 1,
                "reserved_ms": int(allowance * 1000),
                "started_unix_ns": time.time_ns(),
            },
        )
        spent += int(allowance * 1000)
        argv = [
            sys.executable,
            "-m",
            "shaderlod_host",
            "session-open",
            "--launch",
            str(launch.resolve()),
            "--profile",
            str(profile.resolve()),
            "--display",
            str(display.resolve()),
            "--ownership-root",
            str(ownership.resolve()),
            "--session",
            str(session.resolve()),
            "--timeout",
            str(int(allowance)),
            "--world",
            world,
            "--ancestor-pid",
            str(os.getpid()),
            "--ancestor-birth",
            birth(os.getpid()),
        ]
        with (output / ("launcher-" + str(index + 1) + ".log")).open("xb") as log:
            owner = subprocess.Popen(
                argv,
                stdin=subprocess.DEVNULL,
                stdout=log,
                stderr=subprocess.STDOUT,
                env=worker_environment(),
            )
            try:
                ready_deadline = time.monotonic() + campaign.startup_ms / 1000
                while not (session / "session.json").exists():
                    if owner.poll() is not None or time.monotonic() >= ready_deadline:
                        raise ValueError(
                            "session startup failed or exhausted its fixed allowance"
                        )
                    time.sleep(0.05)
                wait_ready(session, max(0.1, ready_deadline - time.monotonic()))
                run = output / ("comparison-" + str(index + 1))
                result = execute_comparison(
                    comparison, session, run, policy=campaign.policy.wire()
                )
                runs.append(run / "result.json")
                if result["state"] != "complete":
                    raise ValueError(
                        "planned comparison incomplete; no automatic retry"
                    )
            except (OSError, ValueError, RuntimeError) as error:
                failures.append({"session": index + 1, "failure": str(error)})
            finally:
                if owner.poll() is None:
                    try:
                        session_command(
                            session,
                            "close",
                            0,
                            min(15, max(0.1, deadline - time.monotonic())),
                        )
                    except (OSError, ValueError, RuntimeError) as error:
                        failures.append(
                            {"session": index + 1, "close_failure": str(error)}
                        )
                        owner.terminate()
                try:
                    owner.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    owner.kill()
                    owner.wait(timeout=3)
                    failures.append(
                        {
                            "session": index + 1,
                            "close_failure": "supervisor forced down; inspect recovery journals",
                        }
                    )
            if failures:
                break
    assessment = assess(campaign.policy, runs) if runs else None
    result = {
        "schema": "shaderlod-campaign-result-v1",
        "state": "complete"
        if len(runs) == len(campaign.comparisons) and not failures
        else "incomplete",
        "runs": [str(path.relative_to(output)) for path in runs],
        "failures": failures,
        "assessment": assessment,
        "quality": "required separately; no automatic acceptance",
    }
    publish(output / "result.json", result)
    return result


def freeze_windows_runtime(legacy: Path, output: Path, root: Path) -> dict[str, object]:
    from shaderlod_host.storage import publish_bytes, replace_bytes

    if os.name != "nt" or output.exists() or output.resolve().is_relative_to(root):
        raise ValueError("fresh external native Windows runtime destination required")
    manifest_path = root / "build/moddev/windows-launch.json"
    manifest = json.loads(manifest_path.read_bytes())
    legacy = legacy.resolve(strict=True)
    argv = list(manifest["argv"])
    old_classpath = (root / "build/moddev/developmentLegacyClasspath.txt").read_text()
    sources = {Path(item) for item in argv[argv.index("-cp") + 1].split(os.pathsep)}
    sources.update(Path(item) for item in old_classpath.splitlines() if item)
    copied = {}
    total = sum(
        p.stat().st_size for p in sources if p.is_file() and p.is_relative_to(legacy)
    )
    output.parent.mkdir(parents=True, exist_ok=True)
    if shutil.disk_usage(output.parent).free < total + 1073741824:
        raise ValueError("insufficient capacity for runtime snapshot and reserve")
    output.mkdir()
    for path in sorted(sources):
        if path.is_relative_to(legacy):
            target = output / path.relative_to(legacy)
            payload = path.read_bytes()
            publish_bytes(target, payload)
            copied[str(target)] = hashlib.sha256(payload).hexdigest()

    def relocated(text: str) -> str:
        return text.replace(str(legacy), str(output)).replace(
            legacy.as_posix(), output.as_posix()
        )

    argv[argv.index("-cp") + 1] = relocated(argv[argv.index("-cp") + 1])
    vm = next(Path(item[1:]) for item in argv if item.startswith("@"))
    base_vm = vm.read_text().split("# User JVM Arguments")[0]
    logging = legacy / "build/moddev/clientLodOnLog4j2.xml"
    publish_bytes(output / "logging.xml", logging.read_bytes())
    new_vm = output / "vm-args.txt"
    entries = [
        base_vm,
        "-Xmx6G",
        "-Dlog4j2.configurationFile=" + (output / "logging.xml").as_posix(),
        "-Djava.net.preferIPv6Addresses=system",
        "-DignoreList=mixinextras-neoforge-0.5.3.jar,client-extra,neoforge-",
        "-DlegacyClassPath.file="
        + (root / "build/moddev/developmentLegacyClasspath.txt").as_posix(),
    ]
    publish_bytes(new_vm, ("\n".join(entries) + "\n").encode())
    argv[argv.index("@" + str(vm))] = "@" + str(new_vm)
    manifest["argv"] = argv
    manifest["runtime_snapshot"] = str(output)
    serialized = json.dumps(manifest, sort_keys=True).encode()
    if (
        str(legacy).encode() in serialized
        or str(legacy).encode() in new_vm.read_bytes()
    ):
        raise ValueError(
            "source checkout dependency remains; snapshot preserved without changing launch"
        )
    publish_bytes(output / "previous-launch.json", manifest_path.read_bytes())
    publish_bytes(output / "previous-legacy-classpath.txt", old_classpath.encode())
    replace_bytes(
        root / "build/moddev/developmentLegacyClasspath.txt",
        relocated(old_classpath).encode(),
    )
    replace_bytes(manifest_path, serialized)
    record: dict[str, object] = {
        "schema": "shaderlod-windows-runtime-snapshot-v1",
        "files": copied,
        "copied_bytes": total,
        "runtime": str(output),
        "launch_sha256": hashlib.sha256(serialized).hexdigest(),
        "shared_dependencies": "Existing user-owned toolchain and Gradle artifact cache outside the original source checkout remain referenced",
    }
    publish(output / "manifest.json", record)
    return record

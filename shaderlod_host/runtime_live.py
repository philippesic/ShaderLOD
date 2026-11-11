import argparse
import hashlib
import json
import os
from pathlib import Path
import socket
import shlex
import shutil
import subprocess
import sys
import time

from shaderlod_host.config import (
    display_environment,
    evidence_root,
    worker_environment,
)
from shaderlod_host.functional_contract import verify_functional
from shaderlod_host.storage import BusyRefusal, publish
from shaderlod_host.worker import birth


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
            for piece in token.split(":"):
                path = Path(piece)
                if path.is_absolute() and path.is_file():
                    paths.add(path)
    legacy = root / "build/moddev/developmentLegacyClasspath.txt"
    paths.add(legacy)
    paths.update(Path(line) for line in legacy.read_text().splitlines() if line)
    paths.update((root / "shaderlod_host").glob("*.py"))
    if profile is not None:
        paths.update((profile / "mods").glob("*.jar"))
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
        shipping = None
        if args.shipping_jar is not None:
            payload = args.shipping_jar.read_bytes()
            name = args.shipping_jar.name
            expected.add(name)
            target = mods / name
            if target.exists():
                if target.is_symlink() or target.read_bytes() != payload:
                    raise ValueError(
                        "shipping profile JAR drift; preserve old artifact before replacement"
                    )
            else:
                target.write_bytes(payload)
            shipping = {"filename": name, "sha256": hashlib.sha256(payload).hexdigest()}

        if any(p.name not in expected for p in mods.iterdir()):
            raise ValueError(
                "unmanaged profile mod; preserve and resolve before launch"
            )
        for item in artifacts.values():
            payload = (root / "dependencies/runtime" / item["filename"]).read_bytes()
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
                pin = json.loads((root / "dependencies/primary-pack.json").read_bytes())
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
                "java_sha256": hashlib.sha256(Path(argv[0]).read_bytes()).hexdigest(),
                "launch_sha256": hashlib.sha256(args.launch.read_bytes()).hexdigest(),
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
                "inputs_unchanged": before == executable_inputs(launch, root, profile),
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

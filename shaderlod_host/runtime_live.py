import argparse
import hashlib
import json
import os
from pathlib import Path
import socket
import shlex
import subprocess
import sys
import time

from shaderlod_host.config import display_environment
from shaderlod_host.functional_contract import verify_functional
from shaderlod_host.storage import BusyRefusal, publish
from shaderlod_host.worker import birth


def executable_inputs(launch: dict[str, object], root: Path) -> dict[str, str]:
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


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Bounded Asahi functional execution, no performance study"
    )
    parser.add_argument("--launch", type=Path, required=True)
    parser.add_argument("--profile", type=Path)
    parser.add_argument("--without-dh", action="store_true")
    parser.add_argument("--native-control", action="store_true")
    parser.add_argument("--display", type=Path, required=True)
    parser.add_argument("--receipt", type=Path, required=True)
    parser.add_argument("--ownership-root", type=Path, required=True)
    parser.add_argument("--timeout", type=int, default=180)
    args = parser.parse_args()
    if sys.platform != "linux" or not 1 <= args.timeout <= 600:
        parser.error("Linux and bounded 1–600 second timeout required")
    launch = json.loads(args.launch.read_bytes())
    argv = launch["argv"]
    if not isinstance(argv, list) or not all(isinstance(a, str) for a in argv):
        raise ValueError("invalid argument vector")
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
    profile = (args.profile or Path(launch["cwd"])).resolve(strict=True)
    if (profile / "shaderlod-functional.json").exists():
        raise ValueError("preserve prior functional evidence before a new attempt")
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
        before = executable_inputs(launch, root)
        started = time.monotonic()
        with args.receipt.with_suffix(".log").open("xb") as log:
            worker = subprocess.Popen(
                [
                    sys.executable,
                    str(Path(__file__).with_name("worker.py")),
                    str(time.monotonic() + args.timeout),
                    str(os.getpid()),
                    birth(os.getpid()),
                    json.dumps(argv),
                ],
                cwd=profile,
                env=environment,
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
            observed = (
                json.loads(functional.read_bytes()) if functional.exists() else None
            )
            contract_error = None
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
                "profile": str(profile),
                "executable_inputs": before,
                "inputs_unchanged": before == executable_inputs(launch, root),
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
            publish(args.receipt, result)
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

import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time


def birth(pid: int) -> str:
    value = Path(f"/proc/{pid}/stat").read_text()
    return value[value.rfind(")") + 2 :].split()[19]


def main() -> int:
    if sys.platform != "linux":
        return 125
    deadline = float(sys.argv[1])
    parent_pid = int(sys.argv[2])
    parent_birth = sys.argv[3]
    argv = json.loads(sys.argv[4])
    stopping = False

    def stop(signum: int, frame: object) -> None:
        nonlocal stopping
        stopping = True

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    child = subprocess.Popen(
        argv,
        start_new_session=True,
        stdin=subprocess.DEVNULL,
        stdout=None,
        stderr=None,
    )
    exitcode = 124
    try:
        while True:
            status = os.waitid(
                os.P_PID, child.pid, os.WEXITED | os.WNOHANG | os.WNOWAIT
            )
            if status is not None:
                exitcode = (
                    status.si_status
                    if status.si_code == os.CLD_EXITED
                    else 128 + status.si_status
                )
                break
            try:
                parent_alive = birth(parent_pid) == parent_birth
            except (OSError, ValueError, IndexError):
                parent_alive = False
            if stopping or not parent_alive or time.monotonic() >= deadline:
                break
            time.sleep(0.005)
    finally:
        try:
            os.killpg(child.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        child.wait(timeout=1)
    return exitcode


if __name__ == "__main__":
    sys.exit(main())

import ctypes
from ctypes import wintypes
import json
import os
from pathlib import Path
import signal
import subprocess
import socket
import sys
import time

from shaderlod_host.storage import publish


def boot() -> str:
    if os.name == "nt":
        import winreg

        with getattr(winreg, "OpenKey")(
            getattr(winreg, "HKEY_LOCAL_MACHINE"),
            r"SYSTEM\CurrentControlSet\Control\Session Manager\Memory Management\PrefetchParameters",
        ) as key:
            value, _ = getattr(winreg, "QueryValueEx")(key, "BootId")
            return str(value)
    return Path("/proc/sys/kernel/random/boot_id").read_text().strip()


def birth(pid: int) -> str:
    if os.name == "nt":
        kernel = getattr(ctypes, "WinDLL")("kernel32", use_last_error=True)
        kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
        kernel.OpenProcess.restype = wintypes.HANDLE
        kernel.GetProcessTimes.argtypes = [
            wintypes.HANDLE,
            ctypes.POINTER(wintypes.FILETIME),
            ctypes.POINTER(wintypes.FILETIME),
            ctypes.POINTER(wintypes.FILETIME),
            ctypes.POINTER(wintypes.FILETIME),
        ]
        kernel.CloseHandle.argtypes = [wintypes.HANDLE]
        handle = kernel.OpenProcess(0x1000, False, pid)
        if not handle:
            raise ProcessLookupError("process identity unavailable")
        times = [wintypes.FILETIME() for _ in range(4)]
        try:
            if not kernel.GetProcessTimes(handle, *[ctypes.byref(t) for t in times]):
                raise ProcessLookupError("process times unavailable")
            return str((times[0].dwHighDateTime << 32) | times[0].dwLowDateTime)
        finally:
            kernel.CloseHandle(handle)
    value = Path(f"/proc/{pid}/stat").read_text()
    return value[value.rfind(")") + 2 :].split()[19]


class WindowsJob:
    def __init__(self, pid: int) -> None:
        class Basic(ctypes.Structure):
            _fields_ = [
                ("process_time", ctypes.c_longlong),
                ("job_time", ctypes.c_longlong),
                ("flags", wintypes.DWORD),
                ("min_working_set", ctypes.c_size_t),
                ("max_working_set", ctypes.c_size_t),
                ("active_processes", wintypes.DWORD),
                ("affinity", ctypes.c_size_t),
                ("priority", wintypes.DWORD),
                ("scheduling", wintypes.DWORD),
            ]

        class Extended(ctypes.Structure):
            _fields_ = [
                ("basic", Basic),
                ("io_counters", ctypes.c_ulonglong * 6),
                ("process_memory", ctypes.c_size_t),
                ("job_memory", ctypes.c_size_t),
                ("peak_process_memory", ctypes.c_size_t),
                ("peak_job_memory", ctypes.c_size_t),
            ]

        self.kernel = getattr(ctypes, "WinDLL")("kernel32", use_last_error=True)
        self.kernel.CreateJobObjectW.argtypes = [ctypes.c_void_p, wintypes.LPCWSTR]
        self.kernel.CreateJobObjectW.restype = wintypes.HANDLE
        self.kernel.SetInformationJobObject.argtypes = [
            wintypes.HANDLE,
            ctypes.c_int,
            ctypes.c_void_p,
            wintypes.DWORD,
        ]
        self.kernel.AssignProcessToJobObject.argtypes = [
            wintypes.HANDLE,
            wintypes.HANDLE,
        ]
        self.kernel.TerminateJobObject.argtypes = [wintypes.HANDLE, wintypes.UINT]
        self.kernel.CloseHandle.argtypes = [wintypes.HANDLE]
        self.kernel.OpenProcess.argtypes = [
            wintypes.DWORD,
            wintypes.BOOL,
            wintypes.DWORD,
        ]
        self.kernel.OpenProcess.restype = wintypes.HANDLE
        self.handle = self.kernel.CreateJobObjectW(None, None)
        if not self.handle:
            raise OSError("cannot create owned Windows job")
        process = self.kernel.OpenProcess(0x0101, False, pid)
        limits = Extended()
        limits.basic.flags = 0x2000
        try:
            if (
                not process
                or not self.kernel.SetInformationJobObject(
                    self.handle, 9, ctypes.byref(limits), ctypes.sizeof(limits)
                )
                or not self.kernel.AssignProcessToJobObject(self.handle, process)
            ):
                raise OSError("cannot attach child to kill-on-close Windows job")
        except OSError:
            self.kernel.CloseHandle(self.handle)
            raise
        finally:
            if process:
                self.kernel.CloseHandle(process)

    def stop(self) -> None:
        self.kernel.TerminateJobObject(self.handle, 124)
        self.kernel.CloseHandle(self.handle)


def main() -> int:
    if sys.platform not in {"linux", "win32"}:
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
    job = None
    if os.name == "nt":
        try:
            job = WindowsJob(child.pid)
        except OSError:
            child.kill()
            child.wait(timeout=3)
            raise
    try:
        if len(sys.argv) >= 7:
            destination = Path(sys.argv[5])
            record = {
                "schema": "shaderlod-child-v1",
                "request": sys.argv[6],
                "host": socket.gethostname(),
                "boot": boot(),
                "pid": child.pid,
                "birth": birth(child.pid),
            }
            publish(destination, record)
        while True:
            if os.name == "nt":
                windows_status = child.poll()
                if windows_status is not None:
                    exitcode = windows_status
                    break
                status = None
            else:
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
                if len(sys.argv) >= 8:
                    ancestor = json.loads(sys.argv[7])
                    parent_alive = (
                        parent_alive and birth(ancestor["pid"]) == ancestor["birth"]
                    )
            except (OSError, ValueError, IndexError):
                parent_alive = False
            if stopping or not parent_alive or time.monotonic() >= deadline:
                break
            time.sleep(0.005)
    finally:
        if job is not None:
            job.stop()
        else:
            try:
                os.killpg(child.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
        child.wait(timeout=1)
    return exitcode


if __name__ == "__main__":
    sys.exit(main())


def probe_worker(output: Path) -> dict[str, object]:
    if output.exists():
        raise ValueError("fresh watchdog probe directory required")
    output.mkdir(parents=True)
    observations = []
    for name, child_code, parent_identity, seconds, expected in [
        ("normal", "pass", birth(os.getpid()), 5, 0),
        ("deadline", "import time; time.sleep(20)", birth(os.getpid()), 0.3, 124),
        ("parent-loss", "import time; time.sleep(20)", "invalid-parent-birth", 5, 124),
        ("ancestor-loss", "import time; time.sleep(20)", birth(os.getpid()), 5, 124),
    ]:
        record = output / (name + "-child.json")
        started = time.monotonic()
        run = subprocess.run(
            [
                sys.executable,
                "-m",
                "shaderlod_host.worker",
                str(started + seconds),
                str(os.getpid()),
                parent_identity,
                json.dumps([sys.executable, "-c", child_code]),
                str(record),
                name,
            ]
            + (
                [json.dumps({"pid": os.getpid(), "birth": "invalid-ancestor-birth"})]
                if name == "ancestor-loss"
                else []
            ),
            capture_output=True,
            timeout=10,
        )
        quiescent = True
        if record.exists():
            child = json.loads(record.read_bytes())
            try:
                quiescent = birth(child["pid"]) != child["birth"]
            except (OSError, ValueError, ProcessLookupError):
                quiescent = True
        else:
            quiescent = False
        observed = {
            "case": name,
            "returncode": run.returncode,
            "expected": expected,
            "elapsed_seconds": time.monotonic() - started,
            "child_quiescent": quiescent,
            "stderr": run.stderr.decode(errors="replace"),
            "pass": run.returncode == expected and quiescent,
        }
        observations.append(observed)
        publish(output / (name + ".json"), observed)
    result: dict[str, object] = {
        "schema": "shaderlod-watchdog-probe-v1",
        "platform": sys.platform,
        "boot": boot(),
        "observations": observations,
        "pass": all(o["pass"] for o in observations),
    }
    publish(output / "result.json", result)
    return result

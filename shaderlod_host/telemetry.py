import ctypes
from pathlib import Path
import time
import os


class NvidiaSensors:
    def __init__(self) -> None:
        self.library = None
        self.handle = ctypes.c_void_p()
        self.reason = "NVML unavailable"
        candidates = ["nvml.dll", "libnvidia-ml.so.1"]
        for candidate in candidates:
            try:
                loaded = ctypes.CDLL(candidate)
                if loaded.nvmlInit_v2() != 0:
                    self.reason = "NVML initialization refused"
                    continue
                loaded.nvmlDeviceGetHandleByIndex_v2.argtypes = [
                    ctypes.c_uint,
                    ctypes.POINTER(ctypes.c_void_p),
                ]
                if (
                    loaded.nvmlDeviceGetHandleByIndex_v2(0, ctypes.byref(self.handle))
                    != 0
                ):
                    loaded.nvmlShutdown()
                    self.reason = "NVML device 0 unavailable"
                    continue
                self.library = loaded
                break
            except (OSError, AttributeError) as error:
                self.reason = str(error)

    def sample(self) -> dict[str, object]:
        if self.library is None:
            return {"status": "unavailable", "reason": self.reason}
        record: dict[str, object] = {
            "status": "available",
            "adapter": "NVML device 0; match UUID/name before interpretation",
        }
        for key, symbol in (
            ("name", "nvmlDeviceGetName"),
            ("uuid", "nvmlDeviceGetUUID"),
        ):
            buffer = ctypes.create_string_buffer(128)
            result = getattr(self.library, symbol)(self.handle, buffer, len(buffer))
            record[key] = (
                buffer.value.decode(errors="replace")
                if result == 0
                else {"unavailable": result}
            )
        for key, symbol, selector in (
            ("temperature_c", "nvmlDeviceGetTemperature", 0),
            ("graphics_clock_mhz", "nvmlDeviceGetClockInfo", 0),
            ("memory_clock_mhz", "nvmlDeviceGetClockInfo", 2),
            ("power_mw", "nvmlDeviceGetPowerUsage", None),
        ):
            value = ctypes.c_uint()
            function = getattr(self.library, symbol)
            result = (
                function(self.handle, ctypes.byref(value))
                if selector is None
                else function(self.handle, selector, ctypes.byref(value))
            )
            record[key] = value.value if result == 0 else {"unavailable": result}
        reasons = ctypes.c_ulonglong()
        result = self.library.nvmlDeviceGetCurrentClocksThrottleReasons(
            self.handle, ctypes.byref(reasons)
        )
        record["clock_limit_reason_bits"] = (
            reasons.value if result == 0 else {"unavailable": result}
        )

        class Memory(ctypes.Structure):
            _fields_ = [
                ("total", ctypes.c_ulonglong),
                ("free", ctypes.c_ulonglong),
                ("used", ctypes.c_ulonglong),
            ]

        memory = Memory()
        result = self.library.nvmlDeviceGetMemoryInfo(self.handle, ctypes.byref(memory))
        record["memory_used_bytes"] = (
            memory.used if result == 0 else {"unavailable": result}
        )
        return record

    def close(self) -> None:
        if self.library is not None:
            self.library.nvmlShutdown()
            self.library = None


def host_sample(pid: int | None = None) -> dict[str, object]:
    started = time.perf_counter_ns()
    record: dict[str, object] = {}
    if Path("/proc/meminfo").exists():
        memory = {}
        for line in Path("/proc/meminfo").read_text().splitlines():
            key, value = line.split(":", 1)
            if key in {
                "MemAvailable",
                "MemTotal",
                "SwapFree",
                "SwapTotal",
                "Dirty",
                "Writeback",
            }:
                memory[key + "_bytes"] = int(value.split()[0]) * 1024
        record["memory"] = memory
    elif os.name == "nt":
        record["memory"] = windows_memory(pid)
    else:
        record["memory"] = {
            "status": "unavailable",
            "reason": "portable host memory adapter not implemented; JVM heap recorded separately",
        }
    temperatures = []
    for path in sorted(Path("/sys/class/thermal").glob("thermal_zone*/temp")):
        try:
            temperatures.append(
                {
                    "sensor": str(path),
                    "type": path.with_name("type").read_text().strip(),
                    "celsius": int(path.read_text()) / 1000,
                }
            )
        except (OSError, ValueError) as error:
            temperatures.append({"sensor": str(path), "unavailable": str(error)})
    record["thermal"] = (
        temperatures
        if temperatures
        else {"status": "unavailable", "reason": "no readable kernel thermal zones"}
    )
    record["sample_cost_ns"] = time.perf_counter_ns() - started
    return record


def windows_memory(pid: int | None) -> dict[str, object]:
    from ctypes import wintypes

    class MemoryStatus(ctypes.Structure):
        _fields_ = [
            ("length", wintypes.DWORD),
            ("load", wintypes.DWORD),
            ("total", ctypes.c_ulonglong),
            ("available", ctypes.c_ulonglong),
            ("commit_limit", ctypes.c_ulonglong),
            ("commit_available", ctypes.c_ulonglong),
            ("virtual_total", ctypes.c_ulonglong),
            ("virtual_available", ctypes.c_ulonglong),
            ("extended", ctypes.c_ulonglong),
        ]

    class ProcessMemory(ctypes.Structure):
        _fields_ = [("length", wintypes.DWORD), ("faults", wintypes.DWORD)] + [
            (key, ctypes.c_size_t)
            for key in (
                "peak_working_set",
                "working_set",
                "peak_paged_pool",
                "paged_pool",
                "peak_nonpaged_pool",
                "nonpaged_pool",
                "pagefile",
                "peak_pagefile",
                "private",
            )
        ]

    kernel = getattr(ctypes, "WinDLL")("kernel32", use_last_error=True)
    kernel.GlobalMemoryStatusEx.argtypes = [ctypes.POINTER(MemoryStatus)]
    kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
    kernel.OpenProcess.restype = wintypes.HANDLE
    kernel.CloseHandle.argtypes = [wintypes.HANDLE]
    status = MemoryStatus()
    status.length = ctypes.sizeof(status)
    record: dict[str, object] = {}
    if kernel.GlobalMemoryStatusEx(ctypes.byref(status)):
        record.update(
            {
                "host_total_bytes": status.total,
                "host_available_bytes": status.available,
                "host_commit_limit_bytes": status.commit_limit,
                "host_commit_available_bytes": status.commit_available,
            }
        )
    else:
        record["host"] = {"unavailable": "GlobalMemoryStatusEx failed"}
    handle = None if pid is None else kernel.OpenProcess(0x0410, False, pid)
    if handle:
        psapi = getattr(ctypes, "WinDLL")("psapi", use_last_error=True)
        psapi.GetProcessMemoryInfo.argtypes = [
            wintypes.HANDLE,
            ctypes.POINTER(ProcessMemory),
            wintypes.DWORD,
        ]
        process = ProcessMemory()
        process.length = ctypes.sizeof(process)
        try:
            if psapi.GetProcessMemoryInfo(
                handle, ctypes.byref(process), ctypes.sizeof(process)
            ):
                record.update(
                    {
                        "process_working_set_bytes": process.working_set,
                        "process_peak_working_set_bytes": process.peak_working_set,
                        "process_private_bytes": process.private,
                        "process_page_faults": process.faults,
                    }
                )
            else:
                record["process"] = {"unavailable": "GetProcessMemoryInfo failed"}
        finally:
            kernel.CloseHandle(handle)
    else:
        record["process"] = {
            "unavailable": "owned process not yet published or no longer readable"
        }
    return record

"""Native Windows memory endpoints; no sampling inside timed frames."""
import ctypes
from ctypes import wintypes as w
import time


def gpu_observation():
    import csv
    import io
    import subprocess
    fields = ('name', 'driver_version', 'temperature.gpu', 'pstate',
              'clocks.current.graphics', 'clocks.current.memory', 'utilization.gpu',
              'power.draw', 'fan.speed', 'memory.used')
    try:
        result = subprocess.run(['nvidia-smi', '--query-gpu=' + ','.join(fields),
                                 '--format=csv,noheader,nounits'], capture_output=True,
                                text=True, timeout=10, check=True)
        return {'devices': [dict(zip(fields, (v.strip() for v in row)))
                            for row in csv.reader(io.StringIO(result.stdout))],
                'scope': 'External NVIDIA endpoint snapshot, not continuous thermal/throttling evidence.'}
    except (OSError, subprocess.SubprocessError) as error:
        return {'unavailable': str(error)}


def memory_observation(pid):
    started = time.monotonic_ns()
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    psapi = ctypes.WinDLL('psapi', use_last_error=True)

    class MemoryStatus(ctypes.Structure):
        _fields_ = [('length', w.DWORD), ('load', w.DWORD)] + [
            (name, ctypes.c_ulonglong) for name in (
                'total_phys', 'avail_phys', 'total_pagefile', 'avail_pagefile',
                'total_virtual', 'avail_virtual', 'avail_extended_virtual')]

    class ProcessMemory(ctypes.Structure):
        _fields_ = [('cb', w.DWORD), ('faults', w.DWORD)] + [
            (name, ctypes.c_size_t) for name in (
                'peak_working_set', 'working_set', 'peak_paged_pool', 'paged_pool',
                'peak_nonpaged_pool', 'nonpaged_pool', 'pagefile_usage',
                'peak_pagefile_usage', 'private_usage')]

    kernel.OpenProcess.argtypes = [w.DWORD, w.BOOL, w.DWORD]
    kernel.OpenProcess.restype = w.HANDLE
    kernel.CloseHandle.argtypes = [w.HANDLE]
    kernel.CloseHandle.restype = w.BOOL
    kernel.GetProcessTimes.argtypes = [w.HANDLE] + [ctypes.POINTER(w.FILETIME)] * 4
    kernel.GetProcessTimes.restype = w.BOOL
    kernel.GlobalMemoryStatusEx.argtypes = [ctypes.POINTER(MemoryStatus)]
    kernel.GlobalMemoryStatusEx.restype = w.BOOL
    psapi.GetProcessMemoryInfo.argtypes = [w.HANDLE, ctypes.POINTER(ProcessMemory), w.DWORD]
    psapi.GetProcessMemoryInfo.restype = w.BOOL

    def checked(ok):
        if not ok:
            raise ctypes.WinError(ctypes.get_last_error())

    handle = kernel.OpenProcess(0x0400 | 0x0010, False, pid)
    checked(handle)
    try:
        creation, exit_time, system, user = (w.FILETIME() for _ in range(4))
        checked(kernel.GetProcessTimes(handle, ctypes.byref(creation), ctypes.byref(exit_time),
                                       ctypes.byref(system), ctypes.byref(user)))
        memory = ProcessMemory(); memory.cb = ctypes.sizeof(memory)
        checked(psapi.GetProcessMemoryInfo(handle, ctypes.byref(memory), memory.cb))
        host = MemoryStatus(); host.length = ctypes.sizeof(host)
        checked(kernel.GlobalMemoryStatusEx(ctypes.byref(host)))
        ticks = lambda v: (v.dwHighDateTime << 32) | v.dwLowDateTime
        return {'started_ns': started, 'finished_ns': time.monotonic_ns(),
                'process': {'pid': pid, 'start_ticks': ticks(creation),
                            'page_faults': memory.faults, 'VmRSS_kib': memory.working_set // 1024,
                            'private_commit_kib': memory.private_usage // 1024,
                            'user_100ns': ticks(user), 'system_100ns': ticks(system)},
                'memory': {'MemTotal_kib': host.total_phys // 1024,
                           'MemAvailable_kib': host.avail_phys // 1024},
                'pressure': {}, 'swap_pages': {}, 'cgroups': {}, 'page_bytes': None,
                'unavailable': ['Linux PSI/cgroups', 'hard versus soft page faults', 'host swap I/O'],
                'scope': 'Windows GetProcessMemoryInfo and GlobalMemoryStatusEx endpoints; page faults combine hard and soft faults. Private commit is not physical pagefile I/O. Missing Linux counters are unavailable, not zero.'}
    finally:
        kernel.CloseHandle(handle)

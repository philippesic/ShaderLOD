"""Nonblocking operation and Java-compatible world locks on supported hosts."""
from contextlib import contextmanager
import errno
import json
import os
from pathlib import Path
import time


def _sharing_conflict(error):
    # CPython's CRT-backed file open loses GetLastError and reports EACCES
    # without winerror. os.replace/CreateFileW retain Windows error numbers.
    code = getattr(error, 'winerror', None)
    return os.name == 'nt' and (code in (5, 32, 33) or
                               code is None and error.errno in (errno.EACCES, errno.EPERM))


def read_published_json(path):
    """Read an atomic publication despite brief Windows rename/delete sharing.

    Retrying only this read never replays an experiment command. Persistent
    access failures, missing files and invalid JSON still fail closed.
    """
    deadline = time.monotonic() + 3
    while True:
        try:
            return json.loads(Path(path).read_text())
        except PermissionError as error:
            if not _sharing_conflict(error) or time.monotonic() >= deadline:
                raise
            time.sleep(.02)


def replace_published(source, destination):
    """Bound transient Windows reader sharing conflicts; never replay a command."""
    if os.name != 'nt':
        os.replace(source, destination)
        return
    deadline = time.monotonic() + 3
    while True:
        try:
            os.replace(source, destination)
            return
        except PermissionError as error:
            if not _sharing_conflict(error) or time.monotonic() >= deadline:
                raise
            time.sleep(.02)


def exclusive(stream):
    if os.name == 'nt':
        import msvcrt
        stream.seek(0)
        msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
    else:
        import fcntl
        fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)


@contextmanager
def world_lock(path):
    if os.name == 'nt':
        import ctypes
        from ctypes import wintypes as w
        import msvcrt
        kernel = ctypes.WinDLL('kernel32', use_last_error=True)
        kernel.CreateFileW.argtypes = [w.LPCWSTR, w.DWORD, w.DWORD, w.LPVOID,
                                      w.DWORD, w.DWORD, w.HANDLE]
        kernel.CreateFileW.restype = w.HANDLE
        # Share the lock file for rename/delete; Windows still prevents renaming
        # its parent directory. World restoration therefore keeps that directory.
        handle = kernel.CreateFileW(str(path), 0x80000000 | 0x40000000, 7,
                                    None, 3, 0x80, None)
        if handle == ctypes.c_void_p(-1).value:
            raise ctypes.WinError(ctypes.get_last_error())
        try:
            fd = msvcrt.open_osfhandle(handle, os.O_RDWR | os.O_BINARY)
        except BaseException:
            kernel.CloseHandle.argtypes = [w.HANDLE]
            kernel.CloseHandle(handle)
            raise
        with os.fdopen(fd, 'r+b') as stream:
            exclusive(stream)
            yield stream
    else:
        import fcntl
        with path.open('r+b') as stream:
            fcntl.lockf(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
            yield stream

#!/usr/bin/env python3
"""Bound generated profile copies and reuse a small pool of disposable clients.

Worlds are independent writable copies, never hardlinks or shared live saves.
Reports belong outside the pool: checkout resets a slot to its source inputs.
"""
from __future__ import annotations

import argparse
import ctypes
from contextlib import contextmanager
import errno
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import sys
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
GIB = 1024 ** 3
MIN_FREE = 32 * GIB
MAX_WORKLOAD = 96 * GIB
MAX_SLOTS = 8
TRANSIENT = {'session.lock', 'backups', 'save-backups', 'logs', 'screenshots',
             'camera-quality', 'crash-reports', 'downloads', '.DS_Store'}
PROFILE_INPUTS = {'saves', 'config', 'mods', 'shaderpacks', 'resourcepacks',
                  'defaultconfigs', 'options.txt', 'optionsof.txt'}


def scoped_usage(paths, *, max_entries=100000):
    """Bounded metadata census of explicit operation roots, never a disk scan.

    Allocated bytes count per-file blocks, not APFS exclusive/reclaimable bytes.
    Unknown paths remain protected; categories do not authorize deletion.
    """
    roots = sorted({safe_path(p) for p in paths}, key=lambda p: (len(p.parts), str(p)))
    selected = []
    for path in roots:
        if not any(path.is_relative_to(parent) for parent in selected):
            selected.append(path)
    result = dict(logical_bytes=0, allocated_bytes=0, files=0, entries=0,
                  categories={}, filesystems={}, roots=list(map(str, selected)),
                  allocation_note='Per-file blocks; APFS sharing/snapshots are unknown, not reclaim estimates. WSL host backing is unmeasured.')
    seen = set()
    pending = list(selected)
    while pending:
        path = pending.pop()
        if not path.exists():
            continue
        safe_path(path)
        info = path.stat()
        result['entries'] += 1
        require(result['entries'] <= max_entries, 'Scoped storage entry limit exceeded')
        require(stat.S_ISDIR(info.st_mode) or stat.S_ISREG(info.st_mode), 'Special file refused: ' + str(path))
        if stat.S_ISDIR(info.st_mode):
            with os.scandir(path) as entries:
                for entry in entries:
                    require(not entry.is_symlink(), 'Symlink in scoped storage: ' + entry.path)
                    pending.append(Path(entry.path))
                    require(len(pending) + result['entries'] <= max_entries,
                            'Scoped storage entry limit exceeded')
            continue
        if (info.st_dev, info.st_ino) in seen:
            continue
        seen.add((info.st_dev, info.st_ino))
        parts = set(path.parts)
        category = ('captures' if path.suffix.lower() == '.png' or 'camera-quality' in parts else
                    'backups' if parts & {'backups', 'save-backups', 'configuration-before'} else
                    'worlds' if 'saves' in parts or path.suffix.lower() in {'.sqlite', '.mca'} else
                    'generated-shaders' if path.suffix.lower() in {'.glsl', '.frag', '.vert', '.fsh', '.vsh'} else
                    'logs' if path.suffix.lower() in {'.log', '.jfr'} else
                    'reusable-inputs' if parts & {'mods', 'shaderpacks', 'resourcepacks'} else 'reports-and-other')
        row = result['categories'].setdefault(category, dict(logical_bytes=0, allocated_bytes=0, files=0))
        allocated = getattr(info, 'st_blocks', 0) * 512
        for item in (result, row):
            item['logical_bytes'] += info.st_size
            item['allocated_bytes'] += allocated
            item['files'] += 1
    for path in selected:
        probe = path
        while not probe.exists():
            probe = probe.parent
        info = probe.stat()
        result['filesystems'][str(info.st_dev)] = dict(probe=str(probe), free_bytes=shutil.disk_usage(probe).free)
    return result


def require(ok, message):
    if not ok:
        raise ValueError(message)


def safe_path(path):
    path = Path(os.path.abspath(path))
    # macOS itself routes tempfile paths through /var -> /private/var. Admit
    # only these exact OS aliases, never links inside a source or destination.
    aliases = {'/var': '/private/var', '/tmp': '/private/tmp'} if sys.platform == 'darwin' else {}
    for part in (path, *path.parents):
        if part.is_symlink():
            require(str(part) in aliases and str(part.resolve()) == aliases[str(part)],
                    'Symlink path refused: ' + str(path))
    return path.resolve()


def tree_bytes(path, ignore=None):
    """Metadata only; do not follow links or descend into discarded captures."""
    path = safe_path(path)
    if not path.exists():
        return 0
    total = 0
    pending = [path]
    while pending:
        directory = pending.pop()
        with os.scandir(directory) as entries:
            rows = list(entries)
        omitted = set(ignore(str(directory), [e.name for e in rows])) if ignore else set()
        for entry in rows:
            if entry.name in omitted:
                continue
            info = entry.stat(follow_symlinks=False)
            require(not stat.S_ISLNK(info.st_mode), 'Symlink in copied tree: ' + entry.path)
            if stat.S_ISDIR(info.st_mode):
                pending.append(Path(entry.path))
            else:
                require(stat.S_ISREG(info.st_mode), 'Special file refused: ' + entry.path)
                total += info.st_size
    return total


def clone_file(source, destination):
    """APFS copy-on-write, falling back to a real independent copy elsewhere."""
    if sys.platform == 'darwin':
        library = ctypes.CDLL(None, use_errno=True)
        clone = library.clonefile
        clone.argtypes = (ctypes.c_char_p, ctypes.c_char_p, ctypes.c_int)
        clone.restype = ctypes.c_int
        if clone(os.fsencode(source), os.fsencode(destination), 0) == 0:
            return str(destination)
        code = ctypes.get_errno()
        if code not in (errno.ENOTSUP, errno.EXDEV, errno.EINVAL, errno.ENOSYS):
            raise OSError(code, os.strerror(code), str(destination))
    return shutil.copy2(source, destination)


@contextmanager
def storage_lock():
    # Preparation callers often already own the client lane; this lock is separate.
    path = ROOT / 'dev/logs/.run-storage.lock'
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('a+b') as stream:
        if os.name == 'nt':
            import msvcrt
            stream.write(b'0'); stream.flush(); stream.seek(0)
            msvcrt.locking(stream.fileno(), msvcrt.LK_LOCK, 1)
        else:
            import fcntl
            fcntl.flock(stream, fcntl.LOCK_EX)
        yield


def admit(destination, incoming, root=None, replacing_bytes=0, reserve_bytes=MIN_FREE):
    """Conservative logical-byte budget: clone savings are never assumed."""
    destination, root = safe_path(destination), safe_path(root or ROOT)
    require(type(incoming) is int and incoming >= 0 and type(replacing_bytes) is int
            and replacing_bytes >= 0 and type(reserve_bytes) is int and reserve_bytes >= 0, 'Invalid storage admission size')
    probe = destination.parent
    while not probe.exists():
        probe = probe.parent
    free = shutil.disk_usage(probe).free
    require(free >= reserve_bytes + incoming,
            f'Storage reserve would fall below {reserve_bytes / GIB:g} GiB; reuse a world-pool slot or remove unused artifacts')
    workloads = root / 'run-workloads'
    if destination.is_relative_to(workloads):
        used = tree_bytes(workloads)
        require(replacing_bytes <= used, 'Invalid replacement credit')
        require(used - replacing_bytes + incoming <= MAX_WORKLOAD,
                'Generated workloads would exceed 96 GiB; reuse a world-pool slot or clean unused captures/worlds')


def copytree(source, destination, *, ignore=None, reserve_bytes=MIN_FREE, root=None, replacing_bytes=0, **kwargs):
    """Preparation copy with capacity admission, COW and no inherited run output.

    Preserve copytree's fresh-destination contract. Reuse is explicit via checkout,
    so an existing directory never silently supplies stale scientific inputs.
    """
    source, destination = safe_path(source), safe_path(destination)
    require(not destination.exists(), 'Existing copy destination preserved: ' + str(destination))
    require(source != destination and source not in destination.parents and destination not in source.parents,
            'Source/destination overlap')
    source_profile = (source / 'saves').is_dir()
    source_world = (source / 'level.dat').is_file()

    def omitted(directory, names):
        result = set(ignore(directory, names)) if ignore else set()
        if source_world or (source_profile and Path(directory).is_relative_to(source / 'saves')):
            # Legacy scientific fingerprints include every world payload file.
            # Keep those bytes unchanged; only the live lock is not a save input.
            result.update(set(names) & {'session.lock'})
        elif source_profile:
            result.update(set(names) & TRANSIENT)
        return result

    incoming = tree_bytes(source, omitted)
    # Serialize admissions across maintained preparation commands.
    with storage_lock():
        admit(destination, incoming, root, replacing_bytes=replacing_bytes, reserve_bytes=reserve_bytes)
        kwargs.setdefault('copy_function', clone_file)
        return shutil.copytree(source, destination, ignore=omitted, **kwargs)


def fingerprint(world):
    result = {}
    for base, directories, files in os.walk(world):
        directories[:] = sorted(name for name in directories if name not in TRANSIENT)
        for name in sorted(files):
            if name in TRANSIENT:
                continue
            path = safe_path(Path(base) / name)
            digest = hashlib.sha256()
            with path.open('rb') as stream:
                for block in iter(lambda: stream.read(1024 * 1024), b''):
                    digest.update(block)
            result[str(path.relative_to(world))] = digest.hexdigest()
    return result


def checkout(source, slot, root=None, *, verify_before_replace=None):
    """Reset one of eight named workspaces from an unchanged canonical profile."""
    import fcntl
    from contextlib import ExitStack
    import dev_client as client
    source, root = safe_path(source), safe_path(root or ROOT)
    require(re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}', slot), 'Invalid workspace slot')
    pool = root / 'run-workloads/world-pool'
    require(not source.is_relative_to(pool), 'Use a canonical source outside the disposable pool')
    tree_bytes(source, lambda directory, names: set(names) & TRANSIENT)
    worlds = sorted((source / 'saves').iterdir())
    require(worlds and all((w / 'level.dat').is_file() for w in worlds), 'Source must contain only prepared worlds')
    target = safe_path(pool / slot)
    marker = target / 'workspace.json'
    with client.runtime_lock(), ExitStack() as locks:
        require(not client.clients(client.process_table()), 'Active Minecraft client; checkout refused')
        pool.mkdir(parents=True, exist_ok=True)
        if target.exists():
            safe_path(marker)
            require(marker.is_file(), 'Unmanaged existing workspace preserved')
            receipt = json.loads(marker.read_text())
            require(receipt.get('schema') == 1 and receipt.get('slot') == slot
                    and receipt.get('source') == str(source),
                    'Workspace source changed; choose another slot')
            tree_bytes(target)  # Reject symlinks before removing owned input copies.
            require(not list(target.glob('.client-reset-*')),
                    'Interrupted reset preserved; inspect its staged/prior inputs before reusing ' + str(target))
        else:
            require(len(list(pool.iterdir())) < MAX_SLOTS, 'Eight workspace slots already exist; reuse one')
        for world in worlds + list((target / 'client/saves').glob('*')):
            lockfile = safe_path(world / 'session.lock')
            if lockfile.exists():
                stream = locks.enter_context(lockfile.open('r+b'))
                fcntl.lockf(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        before = {w.name: fingerprint(w) for w in worlds}
        if verify_before_replace:verify_before_replace()
        # Keep the previous slot intact if capacity or source checks fail.
        def input_filter(directory, names):
            return ((set(names) - PROFILE_INPUTS) if Path(directory) == source else set(names) & TRANSIENT)

        admitted = tree_bytes(source, input_filter)
        admit(target / 'client', admitted, root, replacing_bytes=tree_bytes(target / 'client'))
        created = not target.exists()
        target.mkdir(exist_ok=True)
        stage = Path(tempfile.mkdtemp(prefix='.client-reset-', dir=target))
        stage_stat = stage.stat()
        profile, staged_profile, previous = target / 'client', stage / 'client', stage / 'previous-client'
        control, old_control = target / 'control.json', stage / 'previous-control.json'
        committed = installed = control_changed = False
        try:
            if control.is_file():
                clone_file(control, old_control)
            copytree(source, staged_profile, ignore=input_filter, root=root,
                     replacing_bytes=tree_bytes(profile) + tree_bytes(stage))
            after = {w.name: fingerprint(staged_profile / 'saves' / w.name) for w in worlds}
            require(before == after == {w.name: fingerprint(w) for w in worlds}, 'World changed during checkout')
            source_control = source.parent / 'control.json'
            if source_control.is_file():
                shutil.copy2(source_control, stage / 'control.json')
            record = dict(schema=1, slot=slot, source=str(source), world_files=before, status='ready')
            (stage / 'workspace.json').write_text(json.dumps(record, indent=2) + '\n')
            if verify_before_replace:verify_before_replace()
            if profile.exists():
                profile.rename(previous)
            staged_profile.rename(profile)
            installed = True
            if (stage / 'control.json').exists():
                (stage / 'control.json').replace(control)
            else:
                control.unlink(missing_ok=True)
            control_changed = True
            (stage / 'workspace.json').replace(marker)
            committed = True
        finally:
            if not committed:
                if installed:
                    profile.rename(staged_profile)
                if previous.exists():
                    previous.rename(profile)
                if control_changed:
                    if old_control.exists():
                        old_control.replace(control)
                    else:
                        control.unlink(missing_ok=True)
            # If rollback raises, retain the staging tree and its prior inputs.
            current = safe_path(stage).stat()
            require((current.st_dev, current.st_ino) == (stage_stat.st_dev, stage_stat.st_ino),
                    'Reset stage identity changed; preserve it for its owner')
            shutil.rmtree(stage)
            if created and not marker.exists():
                target.rmdir()
        return profile


def retired_captures(root, days=14):
    """Only old frame PNGs; retain metadata and first/middle/last per sequence."""
    require(days >= 14, 'Capture retention must be at least 14 days')
    root = safe_path(root)
    cutoff = time.time() - days * 86400
    result = []
    for base, directories, files in os.walk(root / 'run-workloads', followlinks=False):
        if '.storage-keep' in files:
            directories[:] = []
            continue
        if 'camera-quality' not in Path(base).parts:
            continue
        frames = sorted(Path(base) / name for name in files
                        if re.fullmatch(r'frame-\d+\.png', name))
        if len(frames) < 5:
            continue
        for path in frames:
            safe_path(path)
        if max(p.stat().st_mtime for p in frames) >= cutoff:
            continue
        keep = {frames[0], frames[len(frames) // 2], frames[-1]}
        for path in frames:
            if path not in keep:
                info = path.stat()
                result.append(dict(path=str(path), bytes=info.st_size,
                                   device=info.st_dev, inode=info.st_ino, mtime_ns=info.st_mtime_ns))
    return result


def prune_captures(root=None, days=14, apply=False):
    import dev_client as client
    root = safe_path(root or ROOT)
    with client.runtime_lock():
        require(not client.clients(client.process_table()), 'Active Minecraft client; retention refused')
        rows = retired_captures(root, days)
        if not apply or not rows:
            return dict(apply=False, frames=len(rows), bytes=sum(r['bytes'] for r in rows))
        receipt = root / 'dev/logs/storage-retention' / (str(time.time_ns()) + '.jsonl')
        receipt.parent.mkdir(parents=True, exist_ok=True)
        with receipt.open('x') as journal:
            for row in rows:
                path = safe_path(row['path'])
                info = path.stat()
                require((info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns) ==
                        (row['device'], row['inode'], row['bytes'], row['mtime_ns']), 'Capture identity changed')
                require(not client.clients(client.process_table()), 'Client started; retention stopped')
                journal.write(json.dumps(row) + '\n'); journal.flush(); os.fsync(journal.fileno())
                # Record retirement before the first removal, including on partial failure.
                marker = path.parent / 'STORAGE-RETENTION.json'
                if not marker.exists():
                    marker.write_text(json.dumps(dict(raw_motion_sequence_retired=True,
                        receipt=str(receipt), policy='14+ days; retain first/middle/last PNG and metadata')) + '\n')
                path.unlink()
        return dict(apply=True, frames=len(rows), bytes=sum(r['bytes'] for r in rows), receipt=str(receipt))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='action', required=True)
    command = sub.add_parser('checkout', help='Reset/reuse a named client from canonical inputs')
    command.add_argument('--source-profile', type=Path, required=True)
    command.add_argument('--slot', required=True, help='Stable loader-version-scene key; do not timestamp it')
    command = sub.add_parser('copy', help='Copy fresh inputs with explicit operation storage admission')
    command.add_argument('--source', type=Path, required=True)
    command.add_argument('--destination', type=Path, required=True)
    command.add_argument('--reserve-mib', type=int, default=MIN_FREE // (1024 * 1024),
                         help='Predeclared operation reserve; general copies default to 32768 MiB')
    sub.add_parser('usage', help='Report generated workload bytes and remaining disk space')
    command = sub.add_parser('prune-captures', help='Retire old raw frames; keep metadata and three images')
    command.add_argument('--days', type=int, default=14)
    command.add_argument('--apply', action='store_true', help='Delete the listed unused frames; default is preview')
    args = parser.parse_args()
    if args.action == 'checkout':
        print(checkout(args.source_profile, args.slot))
    elif args.action == 'copy':
        print(copytree(args.source, args.destination, reserve_bytes=args.reserve_mib * 1024 * 1024))
    elif args.action == 'prune-captures':
        print(json.dumps(prune_captures(days=args.days, apply=args.apply), indent=2))
    else:
        print(json.dumps(dict(workload_bytes=tree_bytes(ROOT / 'run-workloads'),
                              max_workload_bytes=MAX_WORKLOAD, min_free_bytes=MIN_FREE,
                              free_bytes=shutil.disk_usage(ROOT).free), indent=2))


if __name__ == '__main__':
    main()

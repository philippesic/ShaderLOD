"""Storage admission and explicit world-clone lifecycle for finite benchmarks only."""
from __future__ import annotations
from contextlib import ExitStack
import fcntl
import fnmatch
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import uuid

RESERVE_BYTES = 512 * 1024 * 1024
MAX_ENTRIES = 100_000
MARKER = 'benchmark-storage.json'
# Only reuses size evidence after the caller re-admits the exact canonical hash.
_WORLD_BYTES = {}


def require(ok, message):
    if not ok:
        raise ValueError(message)


def safe_path(path):
    path = Path(os.path.abspath(path))
    require(not any(p.is_symlink() for p in (path, *path.parents)), 'Symlink path refused: ' + str(path))
    return path.resolve()


def inventory(path, ignore=()):
    """Bounded metadata traversal, no payload read; mirrors copytree ignore patterns."""
    path = safe_path(path)
    require(path.is_dir(), 'Inventory directory missing: ' + str(path))
    total = count = 0
    pending = [path]
    while pending:
        directory = pending.pop()
        with os.scandir(directory) as entries:
            for entry in entries:
                count += 1
                require(count <= MAX_ENTRIES, 'Inventory entry limit exceeded')
                info = entry.stat(follow_symlinks=False)
                require(not stat.S_ISLNK(info.st_mode), 'Symlink inventory refused: ' + entry.path)
                if any(fnmatch.fnmatch(entry.name, pattern) for pattern in ignore):
                    continue
                if stat.S_ISDIR(info.st_mode):
                    pending.append(Path(entry.path))
                else:
                    require(stat.S_ISREG(info.st_mode), 'Nonregular inventory entry: ' + entry.path)
                    total += info.st_size
    return dict(path=str(path), bytes=total, entries=count)


def capacity(destination, copy_bytes=0, reserve_bytes=RESERVE_BYTES):
    destination = safe_path(destination)
    require(type(copy_bytes) is int and copy_bytes >= 0 and type(reserve_bytes) is int and reserve_bytes >= 0,
            'Invalid storage byte requirement')
    filesystem = destination
    while not filesystem.exists():
        filesystem = filesystem.parent
    free = shutil.disk_usage(filesystem).free
    needed = copy_bytes + reserve_bytes
    receipt = dict(destination=str(destination), filesystem_probe=str(filesystem),
                   device=filesystem.stat().st_dev, free_bytes=free, copy_bytes=copy_bytes,
                   output_reserve_bytes=reserve_bytes, required_bytes=needed)
    require(free >= needed, 'Insufficient destination storage: ' + json.dumps(receipt, sort_keys=True))
    return receipt


def copy_requirement(profile, world, admitted_world_sha256, copies, transient):
    require(re.fullmatch('[0-9a-f]{64}', admitted_world_sha256) is not None, 'Missing admitted world fingerprint')
    key = (str(safe_path(world)), admitted_world_sha256)
    if key not in _WORLD_BYTES:
        _WORLD_BYTES[key] = inventory(world, ('session.lock',))
    record = dict(profile=inventory(profile, transient), world=_WORLD_BYTES[key], copies=copies,
                  admitted_world_sha256=admitted_world_sha256)
    record['bytes'] = copies * (record['profile']['bytes'] + record['world']['bytes'])
    return record


def write(path, value):
    path = safe_path(path)
    temporary = path.with_name(path.name + '.tmp')
    safe_path(temporary).write_text(json.dumps(value, indent=2) + '\n')
    temporary.replace(path)


def identity(path):
    value = safe_path(path).stat()
    return dict(device=value.st_dev, inode=value.st_ino)


def create_manifest(root, canonical, admission):
    root = safe_path(root)
    marker = root / MARKER
    require(not marker.exists(), 'Existing storage manifest preserved')
    record = dict(schema=1, owner=str(uuid.uuid4()), root=str(root), root_identity=identity(root),
                  canonical_inputs=[str(safe_path(p)) for p in canonical], disposable_world_clones=[],
                  preserved='Everything except registered saves/world clone directories',
                  admission=admission, release=None, cleanup=None)
    write(marker, record)
    return record


def register_clone(root, world):
    marker = safe_path(root) / MARKER
    record = json.loads(marker.read_text())
    world = safe_path(world)
    require(world.is_relative_to(root) and world.parent.name == 'saves', 'Clone must be an owned saves/world path')
    record['disposable_world_clones'].append(dict(path=str(world), identity=identity(world)))
    write(marker, record)


def processes():
    result = subprocess.run(['ps', '-axo', 'pid=,pgid=,command='], capture_output=True, text=True, check=True)
    rows = []
    for line in result.stdout.splitlines():
        fields = line.strip().split(None, 2)
        require(len(fields) == 3 and fields[0].isdigit(), 'Malformed process inventory')
        rows.append(dict(pid=int(fields[0]), session=fields[1], command=fields[2]))
    return rows


def cleanup(root, owner, evidence, lock, launcher_sessions, dry_run=True):
    """Called under the finite runner's lock, after evidence export and child release.

    Never discovers old runs or signals processes. The caller supplies an explicit
    per-run opt-in owner; no age-based or all-runs deletion entry point exists.
    """
    root = safe_path(root)
    marker = root / MARKER
    record = json.loads(safe_path(marker).read_text())
    require(record.get('schema') == 1 and record.get('owner') == owner
            and isinstance(owner, str) and str(uuid.UUID(owner)) == owner,
            'Malformed or mismatched manifest owner')
    require(record.get('root') == str(root) and record.get('root_identity') == identity(root), 'Manifest root identity drift')
    release = record.get('release')
    require(isinstance(release, dict) and release.get('children_reaped') is True
            and release.get('authorized_owner') == owner, 'Missing explicit owner release')
    require(not lock.closed and dict(device=os.fstat(lock.fileno()).st_dev, inode=os.fstat(lock.fileno()).st_ino) == identity(Path(release['lane_lock'])), 'Lane lock identity mismatch')
    # The finite runner must retain its original flock; test another open description.
    with safe_path(lock.name).open('a+') as contender:
        try:
            fcntl.flock(contender, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            pass
        else:
            fcntl.flock(contender, fcntl.LOCK_UN)
            raise ValueError('Runner does not hold lane lock')
    live = processes()
    require(not any(str(r['session']) in set(map(str, launcher_sessions)) for r in live), 'Active launcher session')
    markers = ('net.neoforged.devlaunch.Main', 'net.minecraft.client.main.Main', 'BootstrapLauncher',
               'clientLodOnRunProgramArgs.txt', 'clientLodOffRunProgramArgs.txt', 'KnotClient', 'forgeclientdev', 'neoforgeclient')
    require(not any(any(m in r['command'] for m in markers) for r in live), 'Active Minecraft process')
    preserved = []
    for path in evidence:
        path = safe_path(path)
        require(path.is_file(), 'Missing retained evidence: ' + str(path))
        preserved.append(dict(path=str(path), bytes=path.stat().st_size,
                              sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
    require(preserved, 'Missing retained evidence')
    clones = record.get('disposable_world_clones')
    require(isinstance(clones, list) and clones, 'Missing explicit clone inventory')
    canonical = [safe_path(p) for p in record['canonical_inputs']]
    paths = [safe_path(c['path']) for c in clones]
    require(len(set(paths)) == len(paths) and all(a not in b.parents for a in paths for b in paths if a != b), 'Duplicate/nested clone path')
    require(isinstance(launcher_sessions, list) and launcher_sessions and all(type(p) is int and p > 0 for p in launcher_sessions), 'Missing launcher group release')
    with ExitStack() as locks:
        details = []
        for clone, path in zip(clones, paths):
            require(path.is_relative_to(root) and path != root and path.parent.name == 'saves' and path.name == 'ShaderLOD-Landscape', 'Clone path escape')
            require(all(path != p and path not in p.parents and p not in path.parents for p in canonical), 'Canonical input overlap')
            require(all(not Path(p['path']).is_relative_to(path) for p in preserved), 'Evidence inside disposable clone')
            require(clone['identity'] == identity(path), 'Clone identity drift')
            # Existing Java NIO session locks are lockf-compatible on this Mac.
            session = path / 'session.lock'
            if session.exists():
                stream = locks.enter_context(safe_path(session).open('r+b'))
                try: fcntl.lockf(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
                except BlockingIOError as error: raise ValueError('World session lock active') from error
            details.append(inventory(path))
        receipt = dict(status='dry-run' if dry_run else 'deleting', owner=owner,
                       clones=details, retained_evidence=preserved, process_absence=dict(checked_processes=len(live), active_clients=0, active_launcher_groups=0),
                       launcher_sessions=launcher_sessions, lane_lock=release['lane_lock'])
        # Persist exact paths/bytes/evidence BEFORE any deletion; failure preserves clones.
        record['cleanup'] = receipt
        write(marker, record)
        if not dry_run:
            for path, clone in zip(paths, clones):
                require(clone['identity'] == identity(path), 'Clone changed before deletion')
                shutil.rmtree(path)
            receipt['status'] = 'complete'
            write(marker, record)
    return receipt

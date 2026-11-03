#!/usr/bin/env python3
"""Durable boundaries for the versioned local benchmark caller, not a scheduler."""
from __future__ import annotations
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import tempfile
import tomllib
import uuid
import zipfile

import run_storage as storage

PROTOCOL = 'neoforge-1211-benchmark-v1'
MIB = 1024 ** 2
LIMITS = dict(report_bytes=256*MIB, profile_growth_bytes=512*MIB,
              log_bytes=64*MIB, capture_bytes=256*MIB, max_entries=100000)
TERMINAL = {'complete', 'failed-restored'}


def require(ok, message):
    if not ok:
        raise ValueError(message)


def now():
    return datetime.now(timezone.utc).isoformat()


def digest(path):
    path = storage.safe_path(path)
    if not path.exists():
        return None
    require(path.is_file(), 'Expected regular file: ' + str(path))
    value = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(MIB), b''):
            value.update(block)
    return value.hexdigest()


def neoforge_mod_version(path, mod_id):
    """The loaded version comes from the pinned JAR, not its release label."""
    with zipfile.ZipFile(storage.safe_path(path)) as archive:
        member = archive.getinfo('META-INF/neoforge.mods.toml')
        require(member.file_size <= MIB, 'Oversized runtime mod metadata')
        metadata = tomllib.loads(archive.read(member).decode())
    rows = [row for row in metadata.get('mods', []) if row.get('modId') == mod_id]
    require(len(rows) == 1 and isinstance(rows[0].get('version'), str) and
            rows[0]['version'] and '$' not in rows[0]['version'],
            'Unsupported/nonliteral runtime mod version: '+mod_id)
    return rows[0]['version']


def atomic_json(path, value):
    path = storage.safe_path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix='.'+path.name+'-', dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as stream:
            json.dump(value, stream, indent=2); stream.write('\n')
            stream.flush(); os.fsync(stream.fileno())
        os.replace(temporary, path)
        if os.name != 'nt':
            directory = os.open(path.parent, os.O_RDONLY)
            try: os.fsync(directory)
            finally: os.close(directory)
    finally:
        Path(temporary).unlink(missing_ok=True)


@contextmanager
def operation_lock(output):
    """One writer/recovery owner; lock stays in the durable report root."""
    import fcntl
    with storage.safe_path(Path(output)/'.operation.lock').open('a+b') as stream:
        try:
            fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as error:
            raise ValueError('Experiment has a live operation owner') from error
        yield


def configuration_paths(profile, pack):
    profile = storage.safe_path(profile)
    # OptimizationConfig makes one numbered backup on its first write. Track
    # its exact next destination before launch so normal CAS restoration can
    # remove only a creation acknowledged inside an owned mutation.
    config = profile/'config/shaderlod-optimizations.properties'
    future = set()
    if config.is_file():
        backup = config.with_name(config.name+'.bak')
        suffix = 1
        while backup.exists():
            backup = config.with_name(config.name+'.bak.'+str(suffix))
            suffix += 1
        future.add(backup)
    # Existing direct config/sidecars only, plus exact targets preparation creates.
    return sorted(future | set((profile/'config').glob('*')) |
                  set((profile/'shaderpacks').glob('*.txt')) |
                  {profile/'options.txt', profile.parent/'control.json',
                   profile/'shaderpacks'/Path(pack).name,
                   profile/'shaderpacks'/(Path(pack).name+'.txt'), profile/'config/fml.toml'})


class Operation:
    def __init__(self, output):
        self.output = storage.safe_path(output)
        self.path = self.output/'operation.json'
        self.data = json.loads(self.path.read_text())
        require(self.data.get('schema') == 1 and self.data.get('protocol') == PROTOCOL,
                'Unsupported experiment manifest')
        require(self.data['output'] == str(self.output), 'Experiment output moved; retain for owner')
        info = self.output.stat()
        require(self.data['output_identity'] == [info.st_dev, info.st_ino], 'Experiment output identity changed')
        storage.safe_path(self.data['profile'])
        profile_info = Path(self.data['profile']).stat()
        require(self.data['profile_identity'] == [profile_info.st_dev, profile_info.st_ino],
                'Runtime profile identity changed; preserve replacement and backups')

    @classmethod
    def create(cls, output, profile, plan, paths, *, limits=None):
        output, profile = map(storage.safe_path, (output, profile))
        require(not output.exists(), 'Fresh experiment output required; use recover for interrupted work')
        require(not output.is_relative_to(profile.parent) and not profile.is_relative_to(output),
                'Report root must be outside the runtime slot')
        output.mkdir(parents=True)
        info = output.stat()
        profile_info = profile.stat()
        data = dict(schema=1, protocol=PROTOCOL, id=str(uuid.uuid4()), created=now(),
                    output=str(output), output_identity=[info.st_dev, info.st_ino],
                    profile=str(profile), profile_identity=[profile_info.st_dev, profile_info.st_ino],
                    state='preparing', plan=plan, configuration={}, additional_backups=[],
                    processes={}, commands=[], captures={}, evidence={}, cleanup=[],
                    limits=LIMITS | (limits or {}), mutation_pending=False, sequence=0)
        # Write manifest before any backup: failed backup is recoverable without guessing.
        atomic_json(output/'operation.json', data)
        op = cls(output)
        for path in paths:
            path = storage.safe_path(path)
            require(path.is_relative_to(profile) or path == profile.parent/'control.json',
                    'Configuration outside owned profile: ' + str(path))
            if path.is_dir():
                continue  # Never recurse/copy arbitrary config caches.
            value = digest(path)
            relative = path.relative_to(profile.parent)
            backup = output/'configuration-before'/relative
            row = dict(before=value, expected=value, backup=str(backup), restored=False,
                       mode=(path.stat().st_mode & 0o7777) if value is not None else None)
            if value is not None:
                backup.parent.mkdir(parents=True, exist_ok=True)
                with path.open('rb') as src, backup.open('xb') as dst:
                    import shutil
                    shutil.copyfileobj(src, dst, MIB); dst.flush(); os.fsync(dst.fileno())
                require(digest(backup) == value, 'Backup changed during copy: ' + str(path))
                os.chmod(backup, row['mode'])
            op.data['configuration'][str(path)] = row
            op.save()
        return op

    def save(self):
        self.data['updated'] = now()
        atomic_json(self.path, self.data)

    def reload(self):
        self.__init__(self.output)

    def assert_expected(self):
        for value, row in self.data['configuration'].items():
            require(digest(value) == row['expected'], 'Configuration differs from last owned checkpoint: '+value)

    def begin_mutation(self, action):
        self.assert_expected()
        require(not self.data['mutation_pending'], 'Unresolved configuration mutation; recover without replay')
        self.data['mutation_pending'] = action
        self.save()

    def acknowledge_mutation(self, action):
        require(self.data['mutation_pending'] == action, 'Mutation acknowledgment identity differs')
        for value, row in self.data['configuration'].items():
            row['expected'] = digest(value)
        self.data['mutation_pending'] = False
        self.data['last_owned_checkpoint'] = dict(action=action, at=now(),
            provenance='Exact file bytes after this acknowledged operation while the runtime lane is exclusive')
        self.save()

    def command(self, action, arguments):
        require(self.data['state'] not in TERMINAL, 'Terminal experiment cannot replay commands')
        self.data['sequence'] += 1
        identity = 'q'+self.data['id'][:8]+'_'+str(self.data['sequence'])+'_'+action
        row = dict(id=identity, action=action, arguments=arguments, status='intent', at=now())
        self.data['commands'].append(row); self.save()
        return row

    def complete_command(self, row, receipt):
        require(receipt.get('request_id') == row['id'], 'Control receipt request identity differs')
        require(receipt.get('success') is True and receipt.get('status') != 'PENDING',
                'Control command failed: '+str(receipt.get('error', receipt)))
        row.update(status='complete', receipt=receipt, completed=now()); self.save()

    def pin(self, paths):
        for path in paths:
            path = storage.safe_path(path)
            require(path.is_relative_to(self.output), 'Evidence outside operation report')
            value = digest(path)
            require(value is not None, 'Missing experiment evidence: '+str(path))
            self.data['evidence'][str(path)] = value
        self.save()

    def restore(self):
        """CAS against recorded operation state; interruption never adopts current bytes."""
        conflicts = []
        for value, row in self.data['configuration'].items():
            path = storage.safe_path(value)
            current = digest(path)
            if current == row['before']:
                require(current is None or row.get('mode') is None or (path.stat().st_mode & 0o7777)==row['mode'],
                        'Original configuration mode differs: '+value)
                row['restored'] = True
                continue
            # A crash inside an unacknowledged write makes that state uncertain.
            if self.data['mutation_pending'] or current != row['expected']:
                conflicts.append(value); continue
            if row['before'] is None:
                require(digest(path) == current, 'Concurrent configuration writer')
                path.unlink()
            else:
                backup = storage.safe_path(row['backup'])
                require(backup.is_relative_to(self.output/'configuration-before') and
                        digest(backup) == row['before'], 'Configuration backup identity changed')
                temporary = path.with_name('.'+path.name+'.'+self.data['id']+'.restore')
                with temporary.open('xb') as stream:
                    stream.write(backup.read_bytes()); stream.flush(); os.fsync(stream.fileno())
                if row.get('mode') is not None:os.chmod(temporary,row['mode'])
                try:
                    require(digest(path) == current, 'Concurrent configuration writer')
                    temporary.replace(path)
                finally:
                    temporary.unlink(missing_ok=True)
            row['restored'] = True
            self.save()  # Repeated recovery skips every completed file.
        self.data['configuration_restored'] = not conflicts
        self.data['recovery_conflicts'] = conflicts
        self.data['state'] = 'recovery-required' if conflicts else 'restored'
        self.save()
        require(not conflicts, 'Uncertain configuration preserved; inspect operation recovery_conflicts')

    def record_preparation_backups(self, backup):
        backup = storage.safe_path(backup)
        require(backup == self.output/'session/backups', 'Unexpected preparation backup root')
        for value, row in self.data['configuration'].items():
            path = backup/Path(value).relative_to(Path(self.data['profile']).parent)
            if path.is_file():
                require(digest(path) == row['before'], 'Preparation backup differs from original configuration')
                self.data['additional_backups'].append(dict(path=str(path), original=value, sha256=row['before']))
        self.save()

    def dispose_backups(self):
        """Only verified own redundant backups; raw evidence is retained/pinnable."""
        require(self.data['state'] in TERMINAL and self.data.get('configuration_restored'),
                'Terminal restoration required before disposal')
        if pinned(self.output):
            return dict(retained='Pinned operation evidence')
        free_before = storage.shutil.disk_usage(self.output).free
        removed = 0
        targets = []
        for value, row in self.data['configuration'].items():
            require(digest(value) == row['before'], 'Configuration changed after restoration')
            targets.append(dict(path=row['backup'], sha256=row['before'], original=value))
        targets += self.data['additional_backups']
        for target in targets:
            path = storage.safe_path(target['path'])
            require(path.is_relative_to(self.output/'configuration-before') or
                    path.is_relative_to(self.output/'session/backups'), 'Backup escapes report')
            require(digest(target['original']) == target['sha256'], 'Configuration changed after restoration')
            if pinned(path.parent):
                continue
            if not path.exists():
                continue
            require(digest(path) == target['sha256'], 'Backup changed; retain it')
            item = dict(path=str(path), sha256=target['sha256'], bytes=path.stat().st_size,
                        reason='Verified terminal restoration; redundant owned backup', status='intent')
            self.data['cleanup'].append(item); self.save()
            path.unlink(); item['status'] = 'removed'; removed += item['bytes']; self.save()
        receipt = dict(logical_removed_bytes=removed, free_before_bytes=free_before,
                       free_after_bytes=storage.shutil.disk_usage(self.output).free,
                       note='Observed filesystem delta includes concurrent/APFS activity; no exclusive reclaim estimate')
        self.data['backup_disposal'] = receipt; self.save()
        return receipt


def pinned(path):
    return any((p/'.storage-keep').exists() for p in (Path(path), *Path(path).parents))


def runtime_ready(state, plan, owner):
    require(state.get('game_directory') == plan['profile'], 'Runtime game directory differs')
    require(str(state.get('client_pid')) in owner.get('clients', {}), 'Runtime client lacks recorded process ownership')
    require(state.get('pack') == plan['pack_name'] and state.get('pipeline_identity') and state.get('pack_identity'),
            'Runtime selected pack/pipeline identity differs')
    require(state.get('world_loaded') and state.get('meshes_ready') and not state.get('fallback'),
            'Runtime world/meshes/shaders not ready')
    require(state.get('main_mod') == (plan['mode'] == 'on'), 'Runtime main mod state differs')
    require(state.get('focused') and state.get('fullscreen') and state.get('screen') == 'none' and
            [state.get('width'), state.get('height')] == plan.get('native_viewport',[1920,1200]), 'Runtime native viewport/focus differs')
    for key, version in plan['runtime_versions'].items():
        require(state.get('loaded_stack', {}).get(key) == version, 'Runtime loaded version differs: '+key)
    for key, value in plan.get('requested_options', {}).items():
        require(state.get('effective_options', {}).get(key) == value, 'Runtime requested option differs: '+key)


def capture_complete(operation, label, receipt, *, recording=True):
    directory = storage.safe_path(operation.output/'windows'/label)
    require(receipt.get('path') == str(directory) and receipt.get('label') == label,
            'Capture receipt path/label differs')
    paths = [directory/name for name in ('result.json', 'guard.json', 'before.json', 'after.json')]
    result = json.loads(paths[0].read_text()); guard = json.loads(paths[1].read_text())
    windows = result.get('manifest', {}).get('windows', [])
    require(guard == receipt and guard.get('valid') is True and guard.get('completed_render_frames', 0) > 0,
            'Capture guard/receipt is incomplete')
    require(len(windows) == 1 and windows[0].get('id') == label, 'Capture manifest window identity differs')
    require(not recording or (windows[0].get('validity') == 'VALID' and
            windows[0].get('statistics', {}).get('count', 0) > 0), 'Capture timing is incomplete/invalid')
    if operation.data.get('runtime_binding'):
        before, after = (json.loads(path.read_text()) for path in paths[2:])
        binding = operation.data['runtime_binding']
        for state in (before, after):
            runtime_ready(state, operation.data.get('runtime_plan',operation.data['plan']), {'clients': {str(binding['client_pid']): {}}})
            for key in ('client_pid', 'game_directory', 'pack', 'pipeline_identity', 'pack_identity', 'effective_options'):
                require(state.get(key) == binding.get(key), 'Capture runtime identity/options changed: '+key)
    require(label not in operation.data['captures'], 'Completed capture cannot be replayed')
    operation.pin(paths)
    operation.data['captures'][label] = dict(complete=True, recording=recording, files=list(map(str, paths)))
    operation.save()


class StorageGuard:
    def __init__(self, output, profile, *, limits=None):
        self.output, self.profile = map(storage.safe_path, (output, profile))
        self.limits = LIMITS | (limits or {})
        self.baseline = storage.scoped_usage([self.profile], max_entries=self.limits['max_entries'])['logical_bytes']
        self.last = None

    def check(self, *, full=True):
        for base in (self.output, self.profile):
            require(storage.shutil.disk_usage(base).free >= storage.MIN_FREE,
                    'Runtime storage reserve below 32 GiB; stop and preserve evidence')
        for path in (self.output/'session/console.log', self.output/'supervisor.log', self.profile/'logs/latest.log'):
            path = storage.safe_path(path)
            require(not path.exists() or path.stat().st_size <= self.limits['log_bytes'],
                    'Runtime log limit reached; stop and preserve '+str(path))
        if full:
            report = storage.scoped_usage([self.output], max_entries=self.limits['max_entries'])
            profile = storage.scoped_usage([self.profile], max_entries=self.limits['max_entries'])
            require(report['logical_bytes'] <= self.limits['report_bytes'], 'Experiment report limit reached')
            require(profile['logical_bytes']-self.baseline <= self.limits['profile_growth_bytes'], 'Runtime profile growth limit reached')
            captures = sum(r['categories'].get('captures', {}).get('logical_bytes', 0) for r in (report, profile))
            require(captures <= self.limits['capture_bytes'], 'Capture storage limit reached; preserve raw evidence')
            self.last = dict(at=now(), report=report, profile=profile)
        return self.last


def process_absent(proof, table, identity):
    if not proof:
        return True
    row = table.get(int(proof['pid']))
    if row:
        require(identity(row) != proof, 'Recorded process still owns operation')
    return True  # Reused PID is not ours and must never be signaled.

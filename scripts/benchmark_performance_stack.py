"""Finite optional-stack selection for G03 and its matched original baselines."""
import hashlib
import json
from pathlib import Path

CHOICES = ('minimal', 'optimized')
OPTIONAL_IDS = ('modernfix', 'ferritecore', 'immediatelyfast')


def validate_request(args):
    selected = getattr(args, 'performance_stack', 'minimal')
    if selected not in CHOICES:
        raise ValueError('Unknown maintained performance stack')
    if selected == 'minimal':
        return
    if (not getattr(args, 'standard_test', False) or getattr(args, 'bliss_qualification', None)
            or getattr(args, 'dh', False) or getattr(args, 'candidate', 'public') != 'public'
            or getattr(args, 'features', None) or getattr(args, 'with_g10', False)
            or getattr(args, 'g10_control', False)
            or not (getattr(args, 'native_startup_check', False) or getattr(args, 'feature', None) == 'early_alpha')):
        raise ValueError('Optimized stack is scoped to G03 singleton checks/tests and matched original startup/baselines; frozen/private protocols remain minimal')


def artifacts(root, selected):
    if selected == 'minimal':
        return []
    if selected != 'optimized':
        raise ValueError('Unknown maintained performance stack')
    root = Path(root)
    lock = json.loads((root / 'dependencies/performance-mods.lock.json').read_text())
    if tuple(lock['stacks'][selected]) != OPTIONAL_IDS:
        raise ValueError('Optimized stack no longer matches the finite three-mod tuple')
    return [(root / lock['destination'] / lock['artifacts'][name]['filename'],
             lock['artifacts'][name]) for name in OPTIONAL_IDS]


def validate_cache(root, selected):
    for path, artifact in artifacts(root, selected):
        data = path.read_bytes()
        if len(data) != artifact['size'] or any(
                hashlib.new(kind, data).hexdigest() != artifact['hashes'][kind]
                for kind in ('sha256', 'sha512')):
            raise ValueError('Pinned optional runtime artifact differs: ' + path.name)


def observe(root, selected, state):
    loaded = state.get('loaded_stack')
    if not isinstance(loaded, dict):
        raise ValueError('Actual loaded stack unavailable')
    actual = {name: loaded[name] for name in OPTIONAL_IDS if name in loaded}
    expected = {name: artifact['runtime_version'] for name, (_, artifact) in zip(OPTIONAL_IDS, artifacts(root, selected))}
    if actual != expected:
        raise ValueError('Actual optional stack differs from requested ' + selected + ': ' + repr(actual))
    return {'requested': selected, 'observed_optional_versions': actual,
            'scope': 'Exact pinned optional artifacts and actual JVM module versions; not compatibility or performance qualification.'}


def configuration_paths(root, profile, selected):
    result = []
    for _, artifact in artifacts(root, selected):
        for name in artifact['generated_configs']:
            if Path(name).name != name or name in ('.', '..'):
                raise ValueError('Unsafe optional config name')
            result.append(Path(profile) / 'config' / name)
    return result


def preserve_created_configs(op, root):
    """Explicit recovery only: retain known defaults absent before an old launch.

    New operations predeclare these paths. This repairs older interrupted setup
    without discarding conflicting files or weakening whole-profile equality.
    """
    import benchmark_iteration as iteration
    import experiment_lifecycle as life
    import run_storage as storage
    selected = op.data.get('plan', {}).get('performance_stack', 'minimal')
    life.require(selected == 'optimized', 'Optional config recovery requires a recorded optimized launch')
    life.require(op.data.get('release_verified') and op.data.get('configuration_restored'),
                 'Release and configuration restoration must precede config preservation')
    record = op.data['iteration_profile']
    profile, source = Path(op.data['profile']), Path(record['source'])
    # Bind the newly declared filenames to the exact JARs the failed operation used.
    for path, _ in artifacts(root, selected):
        life.require(op.data['inputs'].get(str(path)) == life.digest(path), 'Failed launch optional JAR differs')
    with iteration.stopped_profiles(profile, source):
        for path in configuration_paths(root, profile, selected):
            if record['original']['config'].get(path.name) is not None:
                continue  # Existing settings remain governed by normal CAS restoration.
            path = storage.safe_path(path)
            saved = storage.safe_path(op.output / 'preserved-config-creations' / path.name)
            rows = op.data.setdefault('optional_config_preservation', {})
            prior = rows.get(str(path))
            if not path.exists():
                if prior:
                    life.require(saved.is_file() and life.digest(saved) == prior['sha256'], 'Preserved config missing or changed')
                    prior['status'] = 'preserved'; op.save()
                continue
            life.require(path.is_file() and not saved.exists(), 'Config preservation collision; keep both files')
            info = path.stat(); digest = life.digest(path)
            saved.parent.mkdir(exist_ok=True)
            rows[str(path)] = dict(saved=str(saved), sha256=digest, bytes=info.st_size, status='intent')
            op.save()
            now = path.stat()
            life.require((now.st_dev, now.st_ino, now.st_size, now.st_mtime_ns) ==
                         (info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns)
                         and life.digest(path) == digest, 'Concurrent config writer; preservation withheld')
            path.rename(saved)
            life.require(life.digest(saved) == digest, 'Moved config identity differs')
            rows[str(path)]['status'] = 'preserved'; op.save()

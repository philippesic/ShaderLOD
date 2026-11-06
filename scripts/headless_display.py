#!/usr/bin/env python3
"""Owned, GPU-backed virtual display for headless Linux development.

The installed user service calls --publish; launchers call environment().
No global display environment or personal desktop configuration is changed.
"""
import argparse
import json
import os
from pathlib import Path
import signal
import subprocess
import time

UNIT = 'shaderlod-display.service'
KEYS = ('DISPLAY', 'WAYLAND_DISPLAY', 'XAUTHORITY', 'DBUS_SESSION_BUS_ADDRESS', 'XDG_RUNTIME_DIR')


def directory():
    return Path(os.environ.get('XDG_RUNTIME_DIR', f'/run/user/{os.getuid()}')) / 'shaderlod-display'


def publish():
    path = directory() / 'environment.json'
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    data = {'environment': {k: os.environ[k] for k in KEYS if k in os.environ},
            'invocation_id': os.environ.get('INVOCATION_ID'), 'publisher_pid': os.getpid()}
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(data) + '\n')
    temporary.chmod(0o600)
    temporary.replace(path)
    while True:
        signal.pause()


def read_environment():
    path = directory() / 'environment.json'
    if path.stat().st_uid != os.getuid() or path.stat().st_mode & 0o077:
        raise ValueError('Headless display environment must be private to this user')
    data = json.loads(path.read_text())
    invocation = subprocess.check_output(
        ['systemctl', '--user', 'show', UNIT, '-p', 'InvocationID', '--value'], text=True).strip()
    if not invocation or data.get('invocation_id') != invocation:
        raise ValueError('Headless display environment belongs to an old service invocation')
    env = data['environment']
    if not all(isinstance(env.get(k), str) and (env[k] or k == 'XAUTHORITY') for k in KEYS):
        raise ValueError('Incomplete headless display environment')
    if not (Path(env['XDG_RUNTIME_DIR']) / env['WAYLAND_DISPLAY']).is_socket():
        raise ValueError('Headless Wayland socket is unavailable')
    return {k: env[k] for k in KEYS}


def wait_ready(timeout=30):
    deadline = time.monotonic() + timeout
    error = None
    while time.monotonic() < deadline:
        try:
            env = dict(os.environ, **read_environment())
            result = subprocess.run(['glxinfo', '-B'], env=env, capture_output=True, text=True, timeout=10)
            if result.returncode or 'Accelerated: yes' not in result.stdout:
                raise ValueError('Headless GLX hardware acceleration unavailable: ' + result.stderr)
            screen = subprocess.run(['xrandr', '--current'], env=env, capture_output=True, text=True, timeout=10)
            if screen.returncode or 'current 2560 x 1600' not in screen.stdout:
                raise ValueError('Headless framebuffer is not 2560x1600: ' + screen.stdout)
            return {'environment': read_environment(), 'glxinfo': result.stdout, 'xrandr': screen.stdout}
        except (OSError, ValueError, subprocess.SubprocessError) as exc:
            error = exc
            time.sleep(.2)
    raise ValueError(f'Headless display did not become ready: {error}')


def environment(env):
    """Use the explicitly installed service only when no desktop was selected."""
    if env.get('DISPLAY') or env.get('WAYLAND_DISPLAY'):
        return env
    installed = subprocess.run(['systemctl', '--user', 'show', UNIT, '-p', 'LoadState', '--value'],
                               capture_output=True, text=True, timeout=10)
    if installed.returncode or installed.stdout.strip() != 'loaded':
        raise ValueError('No display selected; install the headless display service (docs/HEADLESS.md)')
    subprocess.run(['systemctl', '--user', 'start', UNIT], check=True, timeout=45)
    return dict(env, **wait_ready()['environment'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--publish', action='store_true')
    parser.add_argument('--wait', action='store_true')
    args = parser.parse_args()
    if args.publish:
        publish()
    else:
        print(json.dumps(wait_ready(), indent=2))


if __name__ == '__main__':
    main()

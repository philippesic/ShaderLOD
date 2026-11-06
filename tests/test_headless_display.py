import json
import os
from pathlib import Path
import socket
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import headless_display as display


class HeadlessDisplayTests(unittest.TestCase):
    def test_existing_desktop_is_not_replaced(self):
        env = {'DISPLAY': ':7', 'KEEP': 'yes'}
        with patch.object(display.subprocess, 'run', side_effect=AssertionError('must not start service')):
            self.assertEqual(display.environment(env), env)

    def test_stale_or_exposed_environment_rejected(self):
        with tempfile.TemporaryDirectory() as tmp, socket.socket(socket.AF_UNIX) as sock:
            root = Path(tmp)
            sock.bind(str(root / 'wayland-test'))
            p = root / 'environment.json'
            p.write_text(json.dumps({'invocation_id': 'old', 'environment': {
                'DISPLAY': ':0', 'WAYLAND_DISPLAY': 'wayland-test', 'XAUTHORITY': '',
                'DBUS_SESSION_BUS_ADDRESS': 'unix:path=test', 'XDG_RUNTIME_DIR': str(root)}}))
            p.chmod(0o600)
            with patch.object(display, 'directory', return_value=root), \
                 patch.object(display.subprocess, 'check_output', return_value='new\n'):
                with self.assertRaisesRegex(ValueError, 'old service invocation'):
                    display.read_environment()
            with patch.object(display, 'directory', return_value=root), \
                 patch.object(display.subprocess, 'check_output', return_value='old\n'):
                self.assertEqual(display.read_environment()['XAUTHORITY'], '')
                p.chmod(0o644)
                with self.assertRaisesRegex(ValueError, 'private'):
                    display.read_environment()

    def test_software_renderer_rejected(self):
        from subprocess import CompletedProcess
        with patch.object(display, 'read_environment', return_value={}), \
             patch.object(display.subprocess, 'run', return_value=CompletedProcess([], 0, 'Accelerated: no', '')), \
             patch.object(display.time, 'monotonic', side_effect=[0, 0, 2]), \
             patch.object(display.time, 'sleep'):
            with self.assertRaisesRegex(ValueError, 'hardware acceleration unavailable'):
                display.wait_ready(timeout=1)

    def test_wrong_resolution_rejected(self):
        from subprocess import CompletedProcess
        results = [CompletedProcess([], 0, 'Accelerated: yes', ''),
                   CompletedProcess([], 0, 'current 1463 x 914', '')]
        with patch.object(display, 'read_environment', return_value={}), \
             patch.object(display.subprocess, 'run', side_effect=results), \
             patch.object(display.time, 'monotonic', side_effect=[0, 0, 2]), \
             patch.object(display.time, 'sleep'):
            with self.assertRaisesRegex(ValueError, 'not 2560x1600'):
                display.wait_ready(timeout=1)

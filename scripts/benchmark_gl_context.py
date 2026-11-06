"""Real context selection and minimum-version proof for maintained comparisons."""
import re

CHOICES = ('native', '3.2', '4.3', '4.5', '4.6')


def validate_request(request, *, frozen_qualification=False):
    if request not in CHOICES:
        raise ValueError('Unsupported benchmark GL context request: ' + str(request))
    if frozen_qualification and request != 'native':
        raise ValueError('Explicit GL context is outside the frozen qualification protocol')
    return request


def observe(request, state):
    validate_request(request)
    actual = state.get('gpu_driver', '')
    match = re.match(r'^(\d+)\.(\d+)(?:[.\s]|$)', actual)
    if not match:
        raise ValueError('Actual desktop OpenGL version unavailable: ' + str(actual))
    version = tuple(map(int, match.groups()))
    if request != 'native' and version < tuple(map(int, request.split('.'))):
        raise ValueError('Actual OpenGL context is below request: ' + actual + ' < ' + request)
    return {'requested': request, 'actual_version': list(version), 'actual_version_string': actual,
            'scope': 'GLFW requested minimum; actual returned context governs capabilities. Higher returned versions are recorded, never relabeled as the requested lower version.'}

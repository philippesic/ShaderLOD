"""Private N04 Sildur unused underwater AO result; native115 only."""
import copy
import hashlib
import json
from pathlib import Path
import zipfile

import dev_client

ROOT = Path(__file__).resolve().parents[1]
ORIGINAL = '824504bc937f9570277c0a809f5059ae4a5fe70c413cf4459b6ccff4dfed7ebe'
PROFILE = ROOT / 'src/main/resources/assets/shaderlod/sildurquad/native115.json'
MEMBERS = ('shaders/deferred.fsh', 'shaders/world-1/deferred.fsh', 'shaders/world1/deferred.fsh')
ANCHOR = b'''\t\tfloat occlusion = funSSDO(noise, fragpos0, normal);
\t\tif(isEyeInWater < 0.9) ao = pow(1.0-occlusion, ao_strength);'''
REPLACEMENT = b'''\t\tif(isEyeInWater < 0.9) {
\t\t\tfloat occlusion = funSSDO(noise, fragpos0, normal);
\t\t\tao = pow(1.0-occlusion, ao_strength);
\t\t}'''


def digest(data):
    return hashlib.sha256(data).hexdigest()


def validate(pack, options):
    if digest(Path(pack).read_bytes()) != ORIGINAL:
        raise ValueError('Exact original Sildur Vibrant 2.02 Extreme required')
    actual = dev_client.properties(Path(options).read_text())
    expected = json.loads(PROFILE.read_text())
    if len(expected) != 115 or actual != expected or actual.get('SSDO') != 'true':
        raise ValueError('Exact native115 with native SSDO required')
    return actual


def build(pack, options, output):
    native = validate(pack, options)
    output = Path(output)
    sidecar = Path(str(output) + '.txt')
    if output.exists() or sidecar.exists():
        raise ValueError('Fresh candidate and sidecar required')
    with zipfile.ZipFile(pack) as source:
        if len(source.namelist()) != len(set(source.namelist())):
            raise ValueError('Duplicate original members')
        for name in MEMBERS:
            data = source.read(name)
            if data.count(ANCHOR) != 1 or b'uniform int isEyeInWater;' not in data:
                raise ValueError('Native uniform/call anchor changed: ' + name)
        output.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(output, 'x') as candidate:
            for info in source.infolist():
                data = source.read(info.filename)
                if info.filename in MEMBERS:
                    data = data.replace(ANCHOR, REPLACEMENT)
                candidate.writestr(copy.copy(info), data)
    sidecar.write_bytes(Path(options).read_bytes())
    return {'original_sha256': ORIGINAL, 'candidate_sha256': digest(output.read_bytes()),
            'native_options': native, 'changed_members': list(MEMBERS),
            'scope': 'Exact native uniform predicate around otherwise discarded SSDO call; no query-count, timing, DH/PBR or other-dimension runtime qualification.'}

#!/usr/bin/env python3
"""G08 single native-name kernel preparation. No qualified permission producer.

Archives are deliberately default Off, including the owner uniform. These are
source experiments, not an admitted Linux implementation or a timing input.
"""
import argparse
import json
import tempfile
import zipfile
from pathlib import Path

import build_makeup_cloud_four_groups as group
from build_makeup_unused_sky import archive_bytes, once, read_archive, sha

OPTION = group.OPTION
GATE = group.GATE.replace('defined MC_OS_MAC', 'defined MC_OS_LINUX')+' && CLOUD_STEPS_AVG == 16 && AA_TYPE == 2'
PERMISSION = 'shaderlodG08CurrentOwner'


def build(original, options, output, control='skip'):
    if control not in ('clone', 'forcedfalse', 'skip'):
        raise ValueError('Unknown control')
    original, options, output = map(Path, (original, options, output))
    if output.exists() or Path(str(output)+'.txt').exists():
        raise ValueError('Fresh output required')
    if b'SHADERLOD_' in options.read_bytes():
        raise ValueError('Native options required')
    native = read_archive(original)
    with tempfile.TemporaryDirectory() as td:
        base = Path(td)/'base.zip'
        group.build(original, options, base, control)
        with zipfile.ZipFile(base) as z:
            generated = {n: z.read(n) for n in z.namelist() if not n.endswith('/')}
    # Extract only the selected kernel. Restore the original caller, properties,
    # graph and every other file; no duplicate reference or diagnostic survives.
    source = generated[group.m.CLOUD].decode()
    start = source.index('vec3 get_cloud(')
    reference = native[group.m.CLOUD].decode().replace('vec3 get_cloud(', 'vec3 shaderlodG08GroupReferenceCloud(', 1)
    end = source.index(reference, start)-len('\n#if '+group.GATE+'\n')
    kernel = source[start:end]
    kernel = once(kernel, '\n#if '+group.GATE+'\nshaderlodG08GroupCalled=1.0;\n#endif\n', '\n')
    if control != 'clone':
        for line in (
            '                    shaderlodG08GroupAttempts+=1.0;',
            '                    if(shaderlodG08GroupEmpty)shaderlodG08GroupPotential+=1.0;',
            '            if(shaderlodG08GroupEmpty && !(current_opacity==0.0))shaderlodG08GroupBadSteps+=1.0;',
        ):
            kernel = once(kernel, line, '')
        kernel = once(kernel, 'bool allowed=i+4<=samples', 'bool allowed='+PERMISSION+'==1 && i+4<=samples')
    kernel = kernel.replace(group.GATE, GATE)
    header = ('#if '+GATE+'\nuniform int '+PERMISSION+';\n'
              'uniform int shaderlodG08GroupSkip;\n'+group.m.HELPER+'\n#endif\n')
    files = dict(native)
    files[group.m.CLOUD] = (header+kernel).encode()
    files['shaders/lib/config.glsl'] += f'\n#define {OPTION} 0 // [0 1]\n'.encode()
    for name in (group.m.ATLAS, group.m.ATLAS+'.mcmeta'):
        files[name] = generated[name]
    files['shaders/shaders.properties'] += (
        f'\n#if {GATE}\ntexture.deferred.noisetex = textures/shaderlod_g08_directional.png\n#endif\n'
        f'uniform.int.{PERMISSION}=0\n'
        f'uniform.int.shaderlodG08GroupSkip={int(control=="skip")}\n').encode()
    marker = dict(control=control, default_off=True, runtime_admitted=False,
                  permission_producer='ABSENT; current texture/support/owner/epoch joins unresolved',
                  layout='one get_cloud definition, original include and caller; no witness, trace, arrays, stores or extra RT',
                  native=sha(original.read_bytes()),
                  limits='Source preparation only. Never enable owner permission by properties. No quality/work/cost result.')
    files['SHADERLOD_G08_LEAN_PREPARATION.json'] = (json.dumps(marker, indent=2)+'\n').encode()
    files['lean-source/build_g08_lean_layout.py'] = Path(__file__).read_bytes()
    output.write_bytes(archive_bytes(files))
    Path(str(output)+'.txt').write_bytes(options.read_bytes()+f'\n{OPTION}=0\n'.encode())
    return dict(marker, artifact=str(output), sha256=sha(output.read_bytes()))


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('original', 'options', 'output'):
        p.add_argument('--'+name, type=Path, required=True)
    p.add_argument('--control', choices=('clone', 'forcedfalse', 'skip'), default='skip')
    print(json.dumps(build(**vars(p.parse_args())), indent=2))

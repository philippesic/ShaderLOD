"""Private Asahi initial RGBA reuse only; literal native shadow and caller bytes.

No public admission, early return, shadow reuse, extra kernels or render targets.
"""
import json
from pathlib import Path
import build_g09_native_reuse as reuse
from build_makeup_unused_sky import archive_bytes, once, sha

GATE = reuse.BASE.replace('defined MC_OS_MAC', 'defined MC_OS_LINUX')

def patch(native, control):
    if control not in {'native', 'forcedfalse', 'initial'}:
        raise ValueError('Unknown initial-only control')
    files = dict(native)
    if control == 'native':
        return files
    s = native[reuse.KERNEL].decode()  # preserve native CRLF and shadow bytes
    first = '    vec3 normalMap = ReadNormal(coord).xyz * 2.0 - 1.0;'
    s = once(s, first, f'#if {GATE}\r\n    vec4 shaderlodG09Initial = ReadNormal(coord);\r\n    vec3 normalMap = shaderlodG09Initial.xyz * 2.0 - 1.0;\r\n#else\r\n{first}\r\n#endif')
    depth = '    float depth = ReadNormal(coord).a;'
    value = ('shaderlodG09InitialReuse != 0 ? shaderlodG09Initial.a : ReadNormal(coord).a'
             if control == 'forcedfalse' else 'shaderlodG09Initial.a')
    s = once(s, depth, f'#if {GATE}\r\n    float depth = {value};\r\n#else\r\n{depth}\r\n#endif')
    if control == 'forcedfalse':
        s = f'#if {GATE}\r\nuniform int shaderlodG09InitialReuse;\r\n#endif\r\n' + s
        files[reuse.PROPS] += b'\nuniform.int.shaderlodG09InitialReuse=0\n'
    files[reuse.KERNEL] = s.encode()
    assert files[reuse.KERNEL].split(b'float GetParallaxShadow', 1)[1] == native[reuse.KERNEL].split(b'float GetParallaxShadow', 1)[1]
    assert files[reuse.TERRAIN] == native[reuse.TERRAIN]
    return files

def build(original, options, output, control):
    native = reuse.read_archive(original)
    files = patch(native, control)
    output = Path(output)
    side = Path(options).read_bytes()
    if b'SHADERLOD_' in side or output.exists() or Path(str(output)+'.txt').exists():
        raise ValueError('Fresh output and native-only sidecar required')
    marker = dict(kind='G09-initial-only', control=control, private_development_only=True,
                  native=reuse.NATIVE, gate=GATE, public_admission=False,
                  limits='Linux macro is private diagnostic selection, not exact host admission. No default or public support change. One source query expression saved; hardware saving unmeasured.')
    files['SHADERLOD_G09_INITIAL_NOTICE.json'] = (json.dumps(marker, indent=2)+'\n').encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(archive_bytes(files)); Path(str(output)+'.txt').write_bytes(side)
    return dict(marker, artifact=str(output.resolve()), sha256=sha(output.read_bytes()), audit=False)

if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ['original', 'options', 'output']:
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--control', choices=['native', 'forcedfalse', 'initial'], required=True)
    print(json.dumps(build(**vars(parser.parse_args())), indent=2))

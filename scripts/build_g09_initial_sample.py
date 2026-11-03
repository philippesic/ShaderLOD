#!/usr/bin/env python3
"""Pinned BSL G09 initial RGBA sharing experiment. No traversal cache."""
import argparse
import json
from pathlib import Path
from build_supported_pack_adapters import ROOT, build

def recipe():
    data = json.loads((ROOT / 'patches/adapters/bsl-shaders.json').read_text())
    # Append after all inherited transformations; never replace their guards.
    first = '    vec3 normalMap = ReadNormal(coord).xyz * 2.0 - 1.0;'
    depth = '    float depth = ReadNormal(coord).a;'
    data['patches'].extend([
        {'path': 'shaders/lib/surface/parallax.glsl', 'before': first,
         'after': '    vec4 shaderlodG09InitialNormal = ReadNormal(coord);\n'
                  '    vec3 normalMap = shaderlodG09InitialNormal.xyz * 2.0 - 1.0;'},
        {'path': 'shaders/lib/surface/parallax.glsl', 'before': depth,
         'after': '    float depth = shaderlodG09InitialNormal.a;'}])
    return data

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    manifest = args.output.with_suffix('.recipe.json')
    if manifest.exists():
        raise ValueError('Refusing existing recipe output')
    manifest.parent.mkdir(parents=True, exist_ok=True)
    manifest.write_text(json.dumps(recipe(), indent=2)+'\n')
    print(json.dumps(build(args.base, args.output, manifest), indent=2))

if __name__ == '__main__':
    main()

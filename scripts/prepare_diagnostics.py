#!/usr/bin/env python3
"""Create stopped-world forest geometry, raster and DH-quality controls."""
from pathlib import Path
import shutil
from setup_comparison import ROOT, WORLD, unlocked_world


def main():
    base = ROOT / 'run-workloads/forest'
    source = base / 'high'
    with unlocked_world(source / 'saves' / WORLD):
        for mode in ['geometry', 'raster75', 'dh-low']:
            target = base / mode
            if target.exists():
                raise SystemExit(f'Preserving existing {target}; move it aside for fresh diagnostic preparation')
        for mode in ['geometry', 'raster75', 'dh-low']:
            target = base / mode
            shutil.copytree(source, target, ignore=shutil.ignore_patterns('session.lock', 'logs', 'screenshots', 'workload-measurement*.json'))
            if mode == 'geometry':
                (target / 'config/shaderlod-client.toml').write_text('[lod]\nenabled=true\npreset="BALANCED"\nnear=32.0\nfar=96.0\ntransition=8.0\ndebug=false\n[terrain]\nenabled=true\nvanillaChunkCap=16\nwarmSourceConfirmed=true\nwarmupSeconds=20\n')
            elif mode == 'raster75':
                file = target / 'shaderpacks/photon_v1.3b.zip.txt'
                file.write_text(file.read_text() + 'TAA=true\nTAAU=true\nTAAU_RENDER_SCALE=0.75\n')
            else:
                file = target / 'config/DistantHorizons.toml'
                text = file.read_text()
                if 'horizontalQuality = "MEDIUM"' not in text: raise ValueError('Expected audited MEDIUM source')
                file.write_text(text.replace('horizontalQuality = "MEDIUM"', 'horizontalQuality = "LOW"'))
            print('Prepared', target)


if __name__ == '__main__': main()

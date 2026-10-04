#!/usr/bin/env python3
"""Run fixed configurations serially and wait for full JVM termination."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
CASES = {
    'cloud-high': ('cloudstorm/high', 'runClientLodOff'),
    'cloud-lower': ('cloudstorm/lower', 'runClientLodOff'),
    'cloud-adaptive': ('cloudstorm/adaptive', 'runClientLodOn'),
    'cloud-combined': ('cloudstorm/combined', 'runClientLodOn'),
    'forest-final': ('forest/final-adaptive', 'runClientLodOn'),
    'forest-high': ('forest/high', 'runClientLodOff'),
    'forest-geometry': ('forest/geometry', 'runClientLodOn'),
    'forest-raster75': ('forest/raster75', 'runClientLodOff'),
    'forest-dh-low': ('forest/dh-low', 'runClientLodOff'),
}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('cases', nargs='+', choices=CASES)
    p.add_argument('--no-gpu-queries', action='store_true')
    p.add_argument('--tag', default='repeat')
    p.add_argument('--sample-interval', type=int, default=1, choices=range(1,61))
    args = p.parse_args()
    output = ROOT / 'dev/logs' / args.tag
    output.mkdir(parents=True, exist_ok=True)
    subprocess.run(['./gradlew', 'build', 'compileDevClientJava'], cwd=ROOT, check=True)
    for case in args.cases:
        location, task = CASES[case]
        profile = ROOT / 'run-workloads' / location
        report = profile / 'workload-measurement.json'
        if report.exists(): report.rename(profile / 'workload-measurement-previous.json')
        cmd = ['./gradlew', task, '-PrunDirectory=run-workloads/' + location,
               '-PmeasureWorkload=true', '-PmeasureOnly=true', '-PgpuPassTiming=false',
               '-PcpuLayerTiming=false', '-PinitialWarmup=45', '-PmeasurementSeconds=30']
        cmd.append('-PgpuSampleInterval=' + str(args.sample_interval))
        if args.no_gpu_queries: cmd.append('-PgpuTiming=false')
        print('Starting', case, flush=True)
        with (output / (case + '-console.log')).open('w') as log:
            subprocess.run(cmd, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=240)
        data = json.loads(report.read_text())
        if data['status'] != 'complete': raise RuntimeError('Incomplete capture: ' + case)
        shutil.copy2(report, output / (case + '-measurement.json'))
        subprocess.run(['python3', 'scripts/summarize_workload.py', str(report), '--output',
                        str(output / (case + '-summary.json'))], cwd=ROOT, check=True)
        print('Completed', case, data['render_frames'] / data['elapsed_seconds'], 'FPS', flush=True)


if __name__ == '__main__': main()

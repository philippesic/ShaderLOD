#!/usr/bin/env python3
"""Fresh pinned spatial camera controls. Never build, launch, or grade images."""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import re

import prepare_shared_spatial_runtime as runtime
from setup_comparison import ROOT, file_hash

ROUTES = ('still', 'pan', 'approach-return')
POSE = dict(x=256.5, y=95., z=768.5, yaw=135., pitch=9.)


def camera_request(control, route, label):
    runtime.require(route in ROUTES, 'Unknown quality route')
    runtime.require(isinstance(label, str) and re.fullmatch(r'[A-Za-z0-9_-]{1,64}', label), 'Invalid request label')
    runtime.require(all(type(control.get(k)) in (int, float) and control[k] == v for k, v in POSE.items())
                    and control.get('time') == 7000 and control.get('weather') == 'clear', 'Pinned forest control drift')
    points = [dict(POSE, seconds=0)]
    if route == 'still':
        points.append(dict(POSE, seconds=20))
    elif route == 'pan':
        points.extend([dict(POSE, seconds=5, yaw=141.), dict(POSE, seconds=10, yaw=129.),
                       dict(POSE, seconds=15, yaw=141.), dict(POSE, seconds=20)])
    else:
        # Minecraft yaw 135 faces negative X/Z. Surface/tier coverage is NOT
        # implied by this 24-block translation; see the quality report.
        radians = math.radians(POSE['yaw'])
        points.extend([dict(POSE, seconds=10, x=POSE['x']-24*math.sin(radians),
                            z=POSE['z']+24*math.cos(radians)), dict(POSE, seconds=20)])
    return dict(schema=1, label=label, warmupSeconds=30, fps=10, pngCompressionLevel=0,
                maximumInFlightImages=2, expectedCameraFluid='NONE', rejectFullBlockCamera=True,
                waypoints=points)


def command(profile, request):
    # Reuse the pinned untimed runtime launch, but remove workload activation
    # and its sampler diagnostic. Capture is an ordinary rendered-image check.
    args = [arg for arg in runtime.command(profile) if not any(arg.startswith('-P'+key+'=') for key in
            ('measureWorkload', 'measureOnly', 'initialWarmup', 'measurementSeconds', 'cloudSamplerProbe'))]
    args += ['-PcameraQuality='+str(Path(request).resolve()), '-PcameraQualityOnly=true',
             '-PcloudSamplerProbe=false', '-Pbenchmark=false', '-PbenchmarkOnly=false']
    return args


def prepare(output, route='still', label=None, source=runtime.SOURCE, root=ROOT):
    label = label or 'shared-spatial-'+route+'-r1'
    # Validate the requested route/label before any profile mutation.
    camera_request(POSE | dict(time=7000, weather='clear'), route, label)
    state = runtime.prepare(output, source, root)
    output = Path(state['operation']['output'])
    marker = output/'shared-spatial-quality-preparation.json'
    result = dict(schema=1, status='preparing', route=route, diagnostic_only=True,
                  performanceEvidence=False, requested_slots=201, expected_framebuffer=[2976, 1672],
                  copied_world_sha256=runtime.WORLD_SHA256, ordinary_quality_capture=True,
                  runtime_preparation=str(output/'shared-spatial-preparation.json'),
                  runtime_preparation_sha256=file_hash(output/'shared-spatial-preparation.json'),
                  owned_live_processes=[], arms={})
    runtime.write(marker, result)
    try:
        control_path = output/'forest/control.json'
        control = json.loads(control_path.read_text())
        request = camera_request(control, route, label)
        request_path = output/'camera-request.json'
        runtime.write(request_path, request)
        result.update(control_sha256=file_hash(control_path), camera_request=str(request_path),
                      camera_request_sha256=file_hash(request_path), request=request)
        for arm, row in state['arms'].items():
            profile = Path(row['profile'])
            # These are NEW profiles. Leave source/driver preparation untouched.
            (profile/'workload-request.json').unlink()
            result['arms'][arm] = dict(profile=str(profile), command=command(profile, request_path),
                archive_sha256=row['archive_sha256'], policy=row['policy'],
                expected_effective_options=row['expected_effective_options'],
                expected_effective_options_sha256=row['expected_effective_options_sha256'],
                source_registration=row['source_registration'],
                source_registration_sha256=row['source_registration_sha256'],
                input_files_sha256={str(p.relative_to(profile)):file_hash(p) for p in profile.rglob('*')
                                    if p.is_file() and not p.is_relative_to(profile/'saves')},
                stop_procedure=row['stop_procedure'])
        # The source preparer already qualified whole archives, complete option
        # maps, cloned canonical saves, source preservation and personal configs.
        result.update(status='prepared', validation='Pinned preparation only; no driver, tier coverage, visual or temporal quality acceptance',
                      launch_status='HOLD: sole operator must first accept driver/layout/consuming-stage controls')
        runtime.write(marker, result)
    except BaseException as error:
        result.update(status='failed', error=str(error)); runtime.write(marker, result); raise
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--source', type=Path, default=runtime.SOURCE)
    parser.add_argument('--route', choices=ROUTES, default='still')
    parser.add_argument('--label')
    parser.add_argument('--qualify-only', action='store_true')
    args = parser.parse_args()
    if args.qualify_only:
        runtime.plan(args.output, args.source)
        scene = runtime.inspect_scene(args.source, qualified=runtime.qualify())
        result = dict(status='qualified', output_created=False,
                      request=camera_request(scene['control'], args.route, args.label or 'shared-spatial-'+args.route+'-r1'))
    else:
        state = prepare(args.output, args.route, args.label, args.source)
        result = dict(status=state['status'], output=str(Path(args.output).resolve()), launch_status=state['launch_status'])
    print(json.dumps(result, indent=2, sort_keys=True))


if __name__ == '__main__':
    main()

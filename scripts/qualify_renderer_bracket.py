#!/usr/bin/env python3
"""Qualify recorded finite renderer windows; never launch or rerun a client.

This supplements (does not replace) the runner's source, quality, restoration,
exclusive-owner and freeze checks. No numeric performance acceptance threshold.
"""
import argparse
import json
import math
from pathlib import Path


def qualify(report, version):
    errors = []
    arms = {}
    if report.get('status') != 'complete-observations':
        errors.append('Incomplete actual bracket')
    if version not in ('1.21.11', '26.2'):
        errors.append('No admitted report adapter for ' + version + '; 1.21.1 uses the legacy Minecraft limiter, not the newer tracker')
    for arm in ('original', 'on', 'off'):
        state = report.get(arm, {})
        problems = []
        frames, seconds = state.get('renderFrames'), state.get('elapsedSeconds')
        denominator_valid = type(frames) is int and frames > 0 and type(seconds) in (int, float) and math.isfinite(seconds) and seconds > 0
        if not denominator_valid:
            problems.append('Missing or invalid completed-render denominator')
        required = {'configuredFramerateLimit': 260, 'effectiveFramerateLimit': 260,
                    'inactivityFpsLimit': 'MINIMIZED', 'framerateThrottleReason': 'NONE',
                    'minimumEffectiveFramerateLimit': 260, 'maximumEffectiveFramerateLimit': 260,
                    'vsync': False}
        if version == '26.2':
            required.update(nativeWindowFocused=True, windowIconified=False, rejectedMeasuredFrames=0,
                            minimumConfiguredFramerateLimit=260, maximumConfiguredFramerateLimit=260,
                            rateObservedFrames=frames)
        elif version == '1.21.11':
            required.update(focused=True, iconified=False, invalidMeasuredFrames=0)
        for key, expected in required.items():
            actual = state.get(key)
            if key not in state or type(actual) is not type(expected) or actual != expected:
                problems.append('Missing or rejected actual observation: ' + key)
        if problems:
            errors.extend(arm + ': ' + problem for problem in problems)
        arms[arm] = {'valid': not problems, 'renderFrames': frames, 'elapsedSeconds': seconds,
                     'meanElapsedFrameMs': 1000 * seconds / frames if denominator_valid else None}
    result = {'measurementValid': not errors, 'errors': errors, 'arms': arms,
              'causalSpeedClaimAccepted': False,
              'claimLimits': 'Elapsed/completed renders include observer overhead; no GPU completion, stable causal effect or quality acceptance is inferred.'}
    if all(arms[arm]['meanElapsedFrameMs'] is not None for arm in arms):
        initial, on, returned = (arms[arm]['meanElapsedFrameMs'] for arm in ('original', 'on', 'off'))
        result['comparisons'] = {'throughputVsInitialPercent': 100 * (initial / on - 1),
                                 'throughputVsReturnPercent': 100 * (returned / on - 1),
                                 'returnFrameTimeDriftPercent': 100 * (returned / initial - 1)}
        result['attribution'] = ('Opposite original comparisons: no directional speed attribution'
                                 if (initial - on) * (returned - on) <= 0 else
                                 'Same-direction descriptive signal only; return drift and host context require scientific review')
    state = report.get('on', {})
    # These counters already exist; do not turn cached fills into per-frame savings.
    if 'stageInputCommands' in state:
        before, after = state.get('stageInputCommands'), state.get('stageOutputCommands')
        scope = 'whole-stage cached batch fills (reload/warmup included); submitted work unknown'
    else:
        before, after = state.get('inputCommands'), state.get('outputCommands')
        scope = 'measured-window helper inputs/outputs; copied GL entries show consumption, not GPU completion'
    if type(before) is int and type(after) is int and before > 0 and 0 <= after <= before:
        result['workObservation'] = {'inputCommands': before, 'outputCommands': after,
            'removedCommands': before - after, 'removedFraction': (before - after) / before,
            'scope': scope, 'totalFrameSpeedupBoundProven': False,
            'nextDecision': 'Review actual avoided work and both original comparisons before extending this attempt; no automatic rerun or universal cutoff.'}
    else:
        result['workObservation'] = {'scope': 'No authentic command denominator available', 'totalFrameSpeedupBoundProven': False}
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    parser.add_argument('--version', required=True)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    result = qualify(json.loads(args.report.read_text()), args.version)
    text = json.dumps(result, indent=2) + '\n'
    if args.output:
        args.output.write_text(text)
    else:
        print(text, end='')
    return 0 if result['measurementValid'] else 1


if __name__ == '__main__':
    raise SystemExit(main())

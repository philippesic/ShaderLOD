#!/usr/bin/env python3
"""Inspect or execute the adopted finite NeoForge26.2 BSL/Reimagined control producer.

Default is read-only. Execution requires fresh inputs freezing this operation;
sealed historical/current private operations are not silently migrated.
Family runtime acceptance remains in the exact previously reviewed run.py.
"""
from __future__ import annotations

import argparse
import copy
from datetime import datetime, timedelta, timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

import dev_client as dc

R = dc.ROOT
SELF = Path(__file__).resolve()
MODULE = R / 'ports/26.2/neoforge'
PYTHON = R / 'dev/runtime/bsl-caller-python-20261011/bin/python'
FAMILIES = {
    'bsl': ('bslNeoMainControl', 'BSL_v10.1.8.zip', 384,
            'c253b45d5f3717cbf45a084bb85d67ac4f6d07d41f50e23a27d28543000f59de'),
    'reimagined': ('reimaginedNeoMainControl', 'ComplementaryReimagined_r5.9.3.zip', 398,
                  'b90785de50960ef6bb3114d92d2a660384ae86c0527fb553374610339513d0b7'),
}


def require(ok, message):
    dc.require(ok, message)


def read(path):
    return json.loads(path.read_text())


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def shape(plan, session):
    """Validate authentic pre-producer argv without fabricated generated inputs."""
    selected = [name for name, spec in FAMILIES.items() if plan.get(spec[0]) is True]
    require(len(selected) == 1 and plan.get('makeupNeoControl') is False
            and plan.get('makeupNeoMainControl') is False, 'Explicit BSL XOR Reimagined MAIN only')
    family = selected[0]
    selector, archive, count, _ = FAMILIES[family]
    require(all(plan.get(spec[0]) is True if name == family else (plan.get(spec[0]) is None or plan.get(spec[0]) is False)
                for name, spec in FAMILIES.items()), 'Family selectors must be booleans when present')
    require(plan.get('launcher') == 'neoforge262' and plan.get('mode') == 'on'
            and plan.get('fullscreen') is True and plan.get('include_dh') is False
            and plan.get('already_built') is True and plan.get('apply_lod_settings') is False,
            'Exact built fullscreen/no-DH/no launch-Apply control required')
    require(plan.get('duration_seconds') == 330
            and plan.get('roundtripStages') == ['original', 'on', 'quality', 'off'], 'Four stages /330 seconds required')
    require(plan.get('finalCallerStampPending') is True and plan.get('stop_at_utc') == 'PENDING_FINAL330_STAMP',
            'Fresh unstarted operation required; never renew an expired/used stamp')
    require(plan.get('exclusive_owner') == '/root', 'Parent owns the finite operation')
    require(plan.get('pack_name') == archive and plan.get('expected_loaded_pack') == archive
            and plan.get('expected_options_count') == count, 'Family archive/native count mismatch')
    profile = Path(plan['profile'])
    require(profile.is_absolute() and profile.is_relative_to(R / 'run-workloads/concept-transfer-r1')
            and profile.parts[-3:] == ('run-workloads', 'port-262-runtime-r1', 'neoforge'), 'Exact private profile suffix required')
    require(not any(p.is_symlink() for p in (profile, *profile.parents)), 'Profile must not traverse symlinks')
    require(plan.get('explicit_env') == {'JAVA_HOME': str(R / 'dev/runtime/jdk25/jdk-25.0.2.jdk/Contents/Home'),
                                       'JAVA_TOOL_OPTIONS': '-XX:ActiveProcessorCount=2'}, 'Pinned JDK25 environment required')
    for key, name in [('manualArtifactInit', 'capture.gradle'), ('manualArtifactInputs', 'actual-runClient-inputs.json'),
                      ('expected_options_source', 'expected-options.json'), ('pack_options', 'native-options.txt')]:
        require(plan.get(key) == str(session / name), 'Session path mismatch: ' + key)
    argv = plan.get('command')
    require(isinstance(argv, list) and all(isinstance(v, str) for v in argv), 'Command must be string argv')
    require(argv[:4] == [str(R / 'ports/26.2/runtime/gradle-9.5.0/bin/gradle'), '-p', str(MODULE), 'runClient'],
            'Exact producer executable/module/task prefix required')
    props, flags, skips, inits = {}, [], [], []
    i = 4
    while i < len(argv):
        value = argv[i]
        if value in ('-I', '-x'):
            require(i + 1 < len(argv), 'Missing argument for ' + value)
            (inits if value == '-I' else skips).append(argv[i + 1]); i += 2; continue
        if value.startswith('-P'):
            key, equal, val = value[2:].partition('=')
            require(equal and key not in props, 'Missing/duplicate property: ' + key)
            props[key] = val
        else:
            flags.append(value)
        i += 1
    expected = dict(portRoundtrip='true', portNativeFamily=family + '-volume', portRoundtripOriginal=archive,
                    portQualityRoundtrip='false', portRoundtripUi='true', portBindingInputFix='false',
                    portMainBindingCompat='true', portRunDirectory=str(profile), portStartupDeadlineEpochMillis='0')
    require(props == expected, 'Exact MAIN family/profile/unstarted deadline properties required')
    require(sorted(flags) == sorted(['--no-daemon', '--max-workers=2', '--console=plain'])
            and inits == [str(session / 'capture.gradle')] and sorted(skips) == sorted(dc.SKIPPABLE),
            'Exact flags/init/four compile-resource skips required')
    return family, props


def verify_pins(pins, skip=()):
    for name, digest in pins.items():
        if name not in skip:
            require(sha(Path(name)) == digest, 'Preparation source/input drift: ' + name)


def inspect(session):
    require(session.is_absolute() and session.is_dir() and not any(p.is_symlink() for p in (session, *session.parents)),
            'Absolute nonsymlink existing session required')
    plan = read(session / 'plan.json')
    family, props = shape(plan, session)
    for name in ('producer-owner.json', 'owner.json', 'producer-after', 'producer-command.json', 'actual-runClient-inputs.json'):
        require(not (session / name).exists(), 'Fresh one-shot operation only; existing ' + name)
    pins = read(session / 'preparation-source-freeze.json')
    require(isinstance(pins, dict) and pins, 'Nonempty preparation freeze required')
    for path in (session / 'plan.json', session / 'run.py', session / 'capture.gradle', R / 'scripts/dev_client.py',
                 R / 'scripts/check_experiment_plan.py', Path(plan['manualArtifact'])):
        require(str(path) in pins, 'Required frozen consumer/input missing: ' + str(path))
    verify_pins(pins)
    require(sha(session / 'run.py') == FAMILIES[family][3], 'Unreviewed family caller; explicit adoption required')
    require(sha(session / 'capture.gradle') == plan['manualArtifactInitSha256'], 'Changed producer init')
    require(sha(Path(plan['manualArtifact'])) == plan['manualArtifactSha256'], 'Changed MAIN artifact')
    require(PYTHON.is_file() and Path(plan['command'][0]).is_file(), 'Pinned caller/producer executable missing')
    quiet = read(session / 'quiet.json')
    return {'family': family, 'plan': plan, 'pins': pins, 'properties': props,
            'operationFrozen': pins.get(str(SELF)) == sha(SELF),
            'quietAcknowledged': quiet.get('owner') == '/root' and quiet.get('workersReleased') is True
                                 and quiet.get('buildsReleased') is True}


def stamped(plan, now):
    result = copy.deepcopy(plan)
    stop = now + timedelta(seconds=330)
    result['stop_at_utc'] = stop.isoformat()
    result['finalCallerStampPending'] = False
    key = '-PportStartupDeadlineEpochMillis='
    require(sum(v.startswith(key) for v in plan['command']) == 1, 'Exactly one startup deadline required')
    result['command'] = [key + str(int(stop.timestamp() * 1000)) if v.startswith(key) else v for v in plan['command']]
    return result


def retire_producer(job, proof, session_id):
    """A retained child is signaled only after two exact identity/session checks."""
    if job.poll() is not None:
        return
    for _ in range(2):
        row = dc.process_table().get(job.pid)
        require(row and dc.identity(row) == proof and os.getsid(job.pid) == session_id,
                'Producer identity changed; retain ownership, no signal')
    job.terminate()
    job.wait(timeout=10)


def produce(command, session, plan):
    env = dict(os.environ); env.update(plan['explicit_env'])
    with (session / 'producer.log').open('x') as log:
        job = subprocess.Popen(command, cwd=R, env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        proof = session_id = None
        owner = dict(pid=job.pid, process=None, session=None, command=command,
                     deadlineUtc=plan['stop_at_utc'], producerTimeoutSeconds=90,
                     status='identity-pending',
                     stopProcedure='Double PID/birth/argv/session check before retained-child terminate; no group kill')
        try:
            # Publish the retained handle's PID before census, even if no ps row
            # can be obtained. An unidentified live child never earns release.
            dc.write_json(session / 'producer-owner.json', owner)
            row = None
            for _ in range(20):
                row = dc.process_table().get(job.pid)
                if row or job.poll() is not None:
                    break
                time.sleep(.05)
            require(row is not None, 'Producer identity unavailable')
            proof, session_id = dc.identity(row), os.getsid(job.pid)
            owner.update(process=proof, session=session_id, status='running')
            dc.write_json(session / 'producer-owner.json', owner)
            code = job.wait(timeout=90)
            require(code == 0, 'Producer failed: exit ' + str(code))
        except BaseException as error:
            cleanup_error = None
            try:
                if proof is not None and session_id is not None:
                    retire_producer(job, proof, session_id)
                else:
                    # No identity means no signal; wait once on the retained
                    # child, then explicitly retain ownership if it is alive.
                    job.wait(timeout=90)
            except BaseException as failure:
                cleanup_error = str(failure)
            owner.update(status='failed', error=str(error), cleanupError=cleanup_error,
                         retainedChildExited=job.poll() is not None)
            try:
                dc.write_json(session / 'producer-owner.json', owner)
            except OSError:
                pass  # Error text below retains PID even when receipt IO fails.
            raise ValueError('Producer operation failed; retained PID=' + str(job.pid)
                             + '; childExited=' + str(owner['retainedChildExited'])
                             + '; ' + str(error) + ('; cleanup: ' + cleanup_error if cleanup_error else '')) from error
    return proof


def world_map(root):
    return {str(p.relative_to(root)): sha(p) for p in root.rglob('*') if p.is_file() and p.name != 'session.lock'}


def static_inputs():
    paths = []
    for root in (R / 'src/main', MODULE / 'src/main', MODULE / 'src/devClient',
                 MODULE / 'build/classes/java/devClient', MODULE / 'build/resources/devClient'):
        paths.extend(p for p in root.rglob('*') if p.is_file())
    return {str(p): sha(p) for p in paths}


def freeze_generated(session, plan, before):
    require(static_inputs() == before, 'Source/class/resource set changed during producer')
    profile = Path(plan['profile']); jar = plan['manualArtifact']
    actual = read(session / 'actual-runClient-inputs.json')
    folders = '-Dfml.modFolders=shaderlod%%' + jar + ':shaderlod_dev%%' + str(MODULE / 'build/classes/java/devClient') + ':shaderlod_dev%%' + str(MODULE / 'build/resources/devClient')
    dirs = {str(MODULE / 'build/classes/java/main'), str(MODULE / 'build/resources/main')}
    require(actual['modFolders'] == folders and actual['runModFolders'] == [folders]
            and folders in (MODULE / 'build/moddev/runClient.sh').read_text(), 'Both actual ModDev providers must be JAR-only')
    require(all(cp.count(jar) == 1 and not dirs.intersection(cp) for cp in (actual['classpath'], actual['launchClasspath']))
            and actual['gameDirectory'] == str(profile), 'Actual JAR-only classpaths/profile mismatch')
    (session / 'producer-after').mkdir()
    for path in (MODULE / 'build/moddev').iterdir():
        if path.is_file():
            shutil.copy2(path, session / 'producer-after' / path.name)
    canonical = R / 'run-workloads/complementary-shadow-sequence-r11/original/client/saves/ShaderLOD-Landscape'
    world = profile / 'saves/ShaderLOD-Landscape'; mapping = world_map(canonical)
    require(len(mapping) == 237 and world_map(world) == mapping, 'Canonical/private237 world mismatch')
    paths = [session / name for name in ('plan.json', 'run.py', 'owned-window-probe.js', 'quiet.json',
             'expected-options.json', 'native-options.txt', 'capture.gradle', 'actual-runClient-inputs.json')]
    paths += [Path(plan[k]) for k in ('pack', 'installed_pack', 'sourceReview', 'manualArtifact')]
    paths += [Path(plan['installed_pack'] + '.txt'), profile / 'options.txt', SELF]
    paths += [p for p in (profile / 'config').rglob('*') if p.is_file()]
    dc.write_json(session / 'freeze.json', dict(files={str(p): sha(p) for p in paths}, canonicalWorldPath=str(canonical),
                  canonicalWorld=mapping, cloneWorldPath=str(world), cloneWorld=world_map(world),
                  status='currentMAINInputsCompiledProducerCaptured_noRuntimeAdmission'))
    built = [Path(p) for p in before]
    built += [Path(p) for key in ('compileJavaSources', 'compileDevSources') for p in actual[key]]
    built += [MODULE / 'build.gradle', R / 'scripts/dev_client.py', R / 'scripts/check_experiment_plan.py', Path(jar), SELF]
    built += [Path(p) for p in actual['classpath'] if Path(p).is_file()]
    built += [p for root in (MODULE / 'build/moddev', session / 'producer-after') for p in root.iterdir() if p.is_file()]
    built += [profile.parents[2] / 'ports/26.2/runtime/sodium-neoforge-0.9.2+mc26.2.jar']
    dc.write_json(session / 'port-build-freeze.json', {str(p): sha(p) for p in built})


def cleanup_caller(session):
    if (session / 'owner.json').exists():
        owner = read(session / 'owner.json')
        if (session / 'release.json').exists():
            table = dc.process_table()
            if not dc.clients(table) and (not owner.get('launcher') or owner['launcher']['pid'] not in table):
                return  # Preserve the caller's already-recorded teardown evidence.
        if owner.get('clients'):
            dc.stop(session)
        dc.retire_source_launcher(session)


def execute(session, observation):
    require(observation['operationFrozen'], 'Fresh preparation must freeze this operation; sealed sessions remain unchanged')
    require(observation['quietAcknowledged'], 'Parent quiet acknowledgment required')
    # Recheck immediately before any mutation; no observation object grants stale admission.
    observation = inspect(session)
    require(observation['operationFrozen'] and observation['quietAcknowledged'], 'Operation/quiet drift')
    with dc.runtime_lock():
        require(not dc.clients(dc.process_table()), 'Client lane occupied')
    before = static_inputs()
    plan = stamped(observation['plan'], datetime.now(timezone.utc))
    dc.write_json(session / 'plan.json', plan)
    command = [*plan['command'][:3], 'captureNeoMakeupInputs', *plan['command'][4:]]
    dc.write_json(session / 'producer-command.json', command)
    proof = produce(command, session, plan)
    verify_pins(observation['pins'], skip=(str(session / 'plan.json'),))
    require(read(session / 'plan.json') == plan, 'Stamped plan changed during producer')
    freeze_generated(session, plan, before)
    check = subprocess.run([str(PYTHON), str(session / 'run.py'), '--preflight'], cwd=R,
                           capture_output=True, text=True, timeout=90)
    (session / 'final-caller-preflight.log').write_text(check.stdout + check.stderr)
    require(check.returncode == 0, 'Finite caller preflight failed: ' + check.stdout + check.stderr)
    with dc.runtime_lock():
        table = dc.process_table()
        require(not dc.clients(table) and proof['pid'] not in table, 'Producer/client lane not released')
    dc.write_json(session / 'producer-result.json', dict(exitCode=0, exactProducerAbsent=True,
                  observedUtc=datetime.now(timezone.utc).isoformat(), sourceOnly=True, stopAtUtc=plan['stop_at_utc']))
    remaining = (datetime.fromisoformat(plan['stop_at_utc']) - datetime.now(timezone.utc)).total_seconds()
    require(130 < remaining <= 330, 'Insufficient remaining finite caller window')
    try:
        result = subprocess.run([str(PYTHON), str(session / 'run.py')], cwd=R, timeout=remaining + 30)
        require(result.returncode == 0, 'Finite caller failed: exit ' + str(result.returncode))
    except BaseException:
        cleanup_caller(session)
        raise
    # Never infer release from a zero exit or overwrite the family's release receipt.
    with dc.runtime_lock():
        table = dc.process_table()
        require(not dc.clients(table), 'Client remains after caller')
        owner = read(session / 'owner.json')
        require(not owner.get('launcher') or owner['launcher']['pid'] not in table, 'Launcher remains after caller')
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--session', required=True, type=Path)
    parser.add_argument('--execute', action='store_true', help='Explicit producer and finite client operation for fresh adopted inputs')
    args = parser.parse_args(argv)
    try:
        observation = inspect(args.session)
        if args.execute:
            return execute(args.session, observation)
        print(json.dumps(dict(status='read-only-preparation-inspection', family=observation['family'],
              command=observation['plan']['command'], properties=observation['properties'],
              operationFrozen=observation['operationFrozen'], quietAcknowledged=observation['quietAcknowledged'],
              runtimeAdmission=False, stampStarted=False), indent=2))
        return 0
    except (ValueError, OSError, KeyError, TypeError, subprocess.SubprocessError) as error:
        print(str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())

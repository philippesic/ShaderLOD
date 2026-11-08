"""Filter untimed JFR samples to the exact candidate window, excluding reloads."""
from collections import Counter
from datetime import datetime
import json
from pathlib import Path
import subprocess

HERE = Path(__file__).resolve().parent


def instant(value):
    return datetime.fromisoformat(value.replace('Z', '+00:00'))


def main():
    rows = {}
    for name in ('reimagined-native-profile-quality-v5', 'reimagined-alpha-profile-quality-v5'):
        report = HERE/name
        operation = json.loads((report/'operation.json').read_text())
        commands = operation['commands']
        start_index = next(i for i, c in enumerate(commands)
                           if c['action'] == 'window_start' and c['arguments']['label'] == 'candidate')
        start = instant(commands[start_index]['completed'])
        stop = instant(next(c for c in commands[start_index+1:] if c['action'] == 'window_stop')['at'])
        samples = report/'jfr-samples.json'
        if not samples.exists():
            with samples.open('xb') as stream:
                subprocess.run(['/usr/lib/jvm/java-21-openjdk/bin/java', '-m', 'jdk.jfr/jdk.jfr.internal.tool.Main',
                                'print', '--json', '--events', 'jdk.ExecutionSample,jdk.NativeMethodSample',
                                str(report/'candidate-cpu.jfr')], stdout=stream, check=True)
        events = json.loads(samples.read_text())['recording']['events']
        by_type = {}
        for event in events:
            value = event['values']
            if value.get('sampledThread', {}).get('javaName') != 'Render thread':
                continue
            if not start <= instant(value['startTime']) <= stop:
                continue
            counters = by_type.setdefault(event['type'], dict(samples=0, leaf=Counter(), project_inclusive=Counter(), swap_samples=0))
            counters['samples'] += 1
            frames = [f['method']['type']['name'].replace('/', '.')+'.'+f['method']['name']
                      for f in value.get('stackTrace', {}).get('frames', [])]
            if frames:
                counters['leaf'][frames[0]] += 1
            if 'org.lwjgl.glfw.GLFW.glfwSwapBuffers' in frames:
                counters['swap_samples'] += 1
            counters['project_inclusive'].update({f for f in frames if f.startswith('com.pp.shaderlod.')})
        rows[name] = dict(candidate_start=start.isoformat(), candidate_stop=stop.isoformat(),
                          candidate_window_seconds=(stop-start).total_seconds(), by_event_type={
                              kind: {key: counter.most_common(12) if isinstance(counter, Counter) else counter
                                     for key, counter in counters.items()}
                              for kind, counters in by_type.items()})
    result = dict(scope='Untimed functional checks only; exact ~3s candidate windows. Reloads excluded. Event sampling periods differ, so event types are not pooled. Sparse inclusive counts overlap; neither CPU durations nor GPU percentages are inferred.',
                  profiles=rows)
    (HERE/'cpu-profile-filtered-v5.json').write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps(result))


if __name__ == '__main__':
    main()

"""Compare observed native/candidate graph work without treating requests as work."""
from collections import Counter
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent


def snapshot(actual):
    count = actual.get('completed_frames', 0)
    events = [e for e in actual.get('events', []) if 0 < e.get('frame', 0) <= count]
    start = next((e for e in events if e.get('kind') == 'frame_start'), {})
    images = {
        str(image['target']): {
            bank: {key: image[bank].get(key) for key in ('exists', 'width', 'height', 'format')}
            for bank in ('main', 'alt')
        } for image in start.get('images', [])
    }
    custom = [{k: value[k] for k in ('stage', 'name')} | {
        'texture': {k: value.get('texture', {}).get(k) for k in ('exists', 'width', 'height', 'format')}
    } for value in start.get('custom_textures', [])]
    return dict(completed_frames=count, error=actual.get('error'), observing=actual.get('observing'),
                native_event_counts=dict(Counter(e['kind'] for e in events)),
                iris_colour_storage=images, custom_textures=custom)


def main():
    result = json.loads((HERE/'results.json').read_text())
    rows = []
    for run in result['runs']:
        if run['validity'] != 'VALID' or run['kind'] != 'test' or run.get('frame_time_reduction_pct', 0) >= -2:
            continue
        summary = json.loads((HERE/run['name']/'summary.json').read_text())
        observations = summary.get('mip_family_observations', {})
        native_key = next((k for k in observations if k.startswith('native')), None)
        if native_key is None:
            continue
        native = snapshot(observations[native_key]['actual'])
        for label, value in observations.items():
            if not label.startswith('candidate'):
                continue
            candidate = snapshot(value['actual'])
            images_a, images_b = native['iris_colour_storage'], candidate['iris_colour_storage']
            rows.append(dict(
                run=run['name'], shader=run['shader'], goal=run['goal'], label=label,
                case=run['case'], context=run['gl_context'], effect_pct=run['frame_time_reduction_pct'],
                native=native, candidate=candidate,
                added_colour_targets=sorted(images_b.keys()-images_a.keys(), key=int),
                removed_colour_targets=sorted(images_a.keys()-images_b.keys(), key=int),
                changed_colour_storage=[key for key in images_a.keys() & images_b.keys() if images_a[key] != images_b[key]],
            ))
    output = dict(scope='Untimed finite observed native events and Iris colour/custom texture inventory only; two completed frames per packet, excluding partial next frame. Physical object names are excluded from storage comparison. This does not enumerate every custom mod allocation, infer GPU duration, or prove lifetime/image equivalence. Earlier implementation attempts remain separately identified by report/context.', rows=rows)
    (HERE/'regression-work-audit.json').write_text(json.dumps(output, indent=2)+'\n')
    compact=[]
    for row in rows:
        compact.append({k:row[k] for k in ('run','shader','goal','label','case','effect_pct','added_colour_targets','removed_colour_targets','changed_colour_storage')} | {
            'native_event_counts':row['native']['native_event_counts'],
            'candidate_event_counts':row['candidate']['native_event_counts'],
            'completed_frames':[row['native']['completed_frames'],row['candidate']['completed_frames']],
            'added_storage':{target:row['candidate']['iris_colour_storage'][target] for target in row['added_colour_targets']},
        })
    (HERE/'regression-work-summary.json').write_text(json.dumps(dict(scope=output['scope'],rows=compact),indent=2)+'\n')
    print(json.dumps(dict(packets=len(rows), added_targets=[(r['run'], r['added_colour_targets']) for r in rows if r['added_colour_targets']],
                          changed_storage=[(r['run'], r['changed_colour_storage']) for r in rows if r['changed_colour_storage']])))


if __name__ == '__main__':
    main()

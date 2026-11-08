"""Check warm-up/drift descriptions without trimming or treating frames as IID."""
import json
from pathlib import Path
import statistics

HERE = Path(__file__).resolve().parent


def main():
    data = json.loads((HERE/'results.json').read_text())
    rows, flags = [], []
    for run in data['runs']:
        if run['validity'] != 'VALID' or not run.get('measurement_qualified'):
            continue
        candidate_means = []
        for label, metric in run['arms'].items():
            path = HERE/run['name']/'windows'/label/'result.json'
            vector = json.loads(path.read_text())['manifest']['windows'][0]['samples']
            values = [r['totalFrameNs']/1e6 for r in vector if r.get('totalFrameNs') is not None]
            assert len(values) == metric['samples']
            quarters = [values[i*len(values)//4:(i+1)*len(values)//4] for i in range(4)]
            means = [statistics.mean(part) for part in quarters]
            change = 100*(means[-1]-means[0])/metric['mean_total_frame_ms']
            row = dict(run=run['name'], shader=run['shader'], goal=run['goal'], kind=run['kind'],
                       case=run['case'], phase=run['phase'], window=label, samples=len(values),
                       equal_count_quarter_means_ms=means, first_to_last_quarter_change_pct=change)
            rows.append(row)
            if abs(change) > 2:
                flags.append(row | dict(reason='Absolute first-to-last equal-count quarter change exceeds exploratory2% review threshold'))
            if label.startswith('candidate'):
                candidate_means.append(metric['mean_total_frame_ms'])
        if len(candidate_means) == 2:
            difference = 100*(candidate_means[1]-candidate_means[0])/statistics.mean(candidate_means)
            if abs(difference) > 2:
                flags.append(dict(run=run['name'], shader=run['shader'], goal=run['goal'],
                                  reason='Two candidate-window means differ by more than exploratory2% review threshold',
                                  candidate_window_means_ms=candidate_means, change_pct=difference))
    scope = ('Descriptive chronological audit of all valid measured windows, including retained earlier variants. '
             'Four equal-frame-count blocks, not fixed wall-time quarters or independent replicates. '
             'The2% flag is an exploratory inspection threshold, not a significance test. '
             'No frames/windows are excluded and no warm-up correction is applied to measured results.')
    (HERE/'time-trend-audit.json').write_text(json.dumps(dict(scope=scope, windows=rows, flags=flags), indent=2)+'\n')
    summary = dict(scope=scope, windows=len(rows), flagged=len(flags), flags=flags,
                   max_absolute_quarter_change_pct=max(abs(r['first_to_last_quarter_change_pct']) for r in rows))
    (HERE/'time-trend-summary.json').write_text(json.dumps(summary, indent=2)+'\n')
    print(json.dumps(summary))


if __name__ == '__main__':
    main()

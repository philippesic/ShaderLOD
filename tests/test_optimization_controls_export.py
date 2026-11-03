"""Prevent partial or invalid timing exports from being labelled complete."""
import csv
import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('optimization_controls', ROOT / 'scripts/optimization_controls.py')
controls = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(controls)


class ExportSummaryTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.path = Path(directory.name) / 'samples.csv'
        self.metadata = self.path.with_name('metadata.properties')
        self.rows = [dict(phase=phase, frame=str(i), epoch='1', frame_wall_ns='10000000',
                          render_wall_ns='8000000', render_cpu_ns='7000000', process_cpu_ns='12000000',
                          gpu_ns='6000000', gpu_status='valid elapsed query; OpenGL GL_TIME_ELAPSED; nanoseconds; counterBits=32')
                     for phase in ('OFF', 'ON', 'RETURN') for i in range(2)]
        self.props = {'feature': 'early_alpha', 'phase': 'COMPLETE'}
        for phase in ('OFF', 'ON', 'RETURN'):
            self.props['samples_' + phase] = '2'
            for metric, value in [('frame_wall', 10), ('render_wall', 8), ('render_cpu', 7), ('process_cpu', 12), ('gpu', 6)]:
                self.props[f'{metric}_{phase.lower()}_valid_samples'] = '2.0'
                self.props[f'{metric}_{phase.lower()}_ms'] = str(value)

    def summarize(self):
        with self.path.open('w', newline='') as stream:
            writer = csv.DictWriter(stream, fieldnames=list(self.rows[0]))
            writer.writeheader()
            writer.writerows(self.rows)
        self.metadata.write_text('\n'.join(k + '=' + v for k, v in self.props.items()))
        return controls.summarize(self.path)

    def test_complete_export(self):
        result = self.summarize()
        self.assertTrue(result['complete_bracket'])
        self.assertEqual(result['phases']['OFF']['fps'], 100)

    def set_frame_times(self, off, on, returned):
        values = dict(OFF=off, ON=on, RETURN=returned)
        for row in self.rows:
            row['frame_wall_ns'] = str(values[row['phase']] * 1_000_000)
        for phase, value in values.items():
            self.props[f'frame_wall_{phase.lower()}_ms'] = str(value)

    def test_original_on_order_and_drift_keep_improvement_direction(self):
        self.rows.sort(key=lambda row: ('ON', 'OFF', 'RETURN').index(row['phase']))
        self.props.update(baseline_state='ON', return_state='ON', phase_order='ON,OFF,RETURN')
        self.set_frame_times(20, 10, 12)
        result = self.summarize()
        self.assertTrue(result['complete_bracket'])
        self.assertEqual(result['metrics']['frame_wall_ns']['on_minus_off_ms'], -10)
        self.assertEqual(result['metrics']['frame_wall_ns']['baseline_drift_ms'], 2)

    def test_old_exports_default_to_off_return(self):
        self.set_frame_times(20, 10, 22)
        result = self.summarize()
        self.assertTrue(result['complete_bracket'])
        self.assertEqual(result['return_state'], 'OFF')
        self.assertEqual(result['metrics']['frame_wall_ns']['baseline_drift_ms'], 2)

    def test_bad_return_state_is_inconclusive(self):
        self.props['return_state'] = 'RETURN'
        result = self.summarize()
        self.assertFalse(result['complete_bracket'])
        self.assertIsNone(result['metrics']['frame_wall_ns']['baseline_drift_ms'])

    def test_truncation_and_failed_phase_are_not_complete(self):
        self.rows.pop()
        self.assertFalse(self.summarize()['complete_bracket'])
        self.props['phase'] = 'FAILED'
        self.assertIn('Exporter phase is not COMPLETE', self.summarize()['validation_issues'])

    def test_csv_without_metadata_is_unverified(self):
        self.summarize()
        self.metadata.unlink()
        self.assertFalse(controls.summarize(self.path)['complete_bracket'])

    def test_gpu_missing_is_honest_not_inferred(self):
        for row in self.rows:
            row['gpu_ns'] = ''
            row['gpu_status'] = 'unsupported: no native timer query'
        for phase in ('off', 'on', 'return'):
            self.props['gpu_' + phase + '_valid_samples'] = '0.0'
            del self.props['gpu_' + phase + '_ms']
        result = self.summarize()
        self.assertTrue(result['complete_bracket'])
        self.assertIsNone(result['phases']['ON']['gpu_ns']['mean_ms'])

    def test_gpu_value_requires_provenance(self):
        self.rows[0]['gpu_status'] = 'CPU wall substitute'
        self.assertFalse(self.summarize()['complete_bracket'])

    def test_denominator_and_mean_must_match(self):
        self.props['render_cpu_on_valid_samples'] = '1.0'
        self.props['render_cpu_on_ms'] = '99'
        result = self.summarize()
        self.assertFalse(result['complete_bracket'])
        self.assertTrue(any('denominator mismatch' in issue for issue in result['validation_issues']))
        self.assertTrue(any('mean mismatch' in issue for issue in result['validation_issues']))


if __name__ == '__main__':
    unittest.main()

import importlib.util
from pathlib import Path
import unittest
import tempfile
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('repeat',Path(__file__).parents[1]/'scripts/linux_repeatability.py')
repeat=importlib.util.module_from_spec(spec);spec.loader.exec_module(repeat)


class RepeatabilityTests(unittest.TestCase):
    def test_control_waits_for_complete_receipt(self):
        with tempfile.TemporaryDirectory() as tmp:
            control=repeat.Control(Path(tmp)/'control.json')
            receipt=Path(tmp)/'receipts'/f'repeat-{control.sequence}-1.json'
            receipt.parent.mkdir()
            receipt.touch()
            with patch.object(Path,'read_text',side_effect=['', '{"success":true,"state":{"ready":true}}']), patch.object(repeat.time,'sleep'):
                self.assertEqual(control.request('status')['state'],{'ready':True})

    def test_control_preserves_terminal_failure(self):
        with tempfile.TemporaryDirectory() as tmp:
            control=repeat.Control(Path(tmp)/'control.json')
            receipt=Path(tmp)/'receipts'/f'repeat-{control.sequence}-1.json'
            receipt.parent.mkdir()
            receipt.write_text('{"success":false,"error":"native reload failed"}')
            with self.assertRaisesRegex(RuntimeError,'native reload failed'):
                control.request('reload')

    def rows(self, values):
        return [{'statistics':{'medianMs':v}} for v in values]

    def test_drift_noise_and_binding_cap_rejected(self):
        self.assertTrue(repeat.summarize(self.rows([7,7.01,7.02,7]),5)['timing_pass'])
        self.assertFalse(repeat.summarize(self.rows([7,7,8,8]),5)['timing_pass'])
        self.assertFalse(repeat.summarize(self.rows([7,7,9,7,7]),5)['timing_pass'])
        self.assertFalse(repeat.summarize(self.rows([3.85]*4),5)['timing_pass'])

    def test_short_or_stalled_runs_cannot_pass(self):
        with self.assertRaises(ValueError): repeat.summarize(self.rows([7]),5)
        rows=[{'valid':True,'wall_ns':20e9} for _ in range(6)]
        self.assertTrue(repeat.protocol_valid(rows,[0,120,240,360,480,600],721,12))
        self.assertFalse(repeat.protocol_valid(rows[:1],[0],721,12))
        self.assertFalse(repeat.protocol_valid(rows,[0,120,240,360,480,650],721,12))
        self.assertFalse(repeat.protocol_valid(rows,[0,120,240,360,480,600],1800,12))
        rows[-1]['wall_ns']=100e9
        self.assertFalse(repeat.protocol_valid(rows,[0,120,240,360,480,600],721,12))

    def test_warmup_requires_three_stable_controls(self):
        self.assertFalse(repeat.warmup_stable(self.rows([7,7])))
        self.assertFalse(repeat.warmup_stable(self.rows([7,7.8,8])))
        self.assertTrue(repeat.warmup_stable(self.rows([7,7.8,7.81,7.82])))

    def test_cleanup_attempts_restore_after_capture_failure(self):
        calls=[]
        class Fake:
            def request(self, action, **kwargs):
                calls.append(action)
                if action=='window_stop': raise RuntimeError('lost stop receipt')
                if action=='status': return {'state':initial}
                return {'success':True}
        initial={'fps_limit':60,'vsync':False}
        with tempfile.TemporaryDirectory() as tmp, patch.object(repeat,'stable',side_effect=lambda x:x):
            result=repeat.cleanup(Fake(),True,initial,Path(tmp))
        self.assertEqual(calls,['window_stop','cancel','video','status'])
        self.assertFalse(result['verified'])

    def test_invariants_include_reset_identity_and_generation(self):
        source={key:0 for key in ('main_mod','pack','width','height','distance','camera','day_time','rain_level','selected_resource_packs','loaded_stack','effective_options','fps_limit','vsync','fullscreen','client_pid','client_started_at','game_directory','pack_identity','pipeline_identity')}
        source['installed']={'generation':1,'reloads':0,'master':False,'flags':{}}
        original=repeat.stable(source)
        changed=source|{'pipeline_identity':2}
        self.assertNotEqual(original,repeat.stable(changed))
        changed=source|{'installed':source['installed']|{'reloads':1}}
        self.assertNotEqual(original,repeat.stable(changed))

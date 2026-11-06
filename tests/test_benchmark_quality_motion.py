import json
from pathlib import Path
from types import SimpleNamespace
import sys
import tempfile
import unittest
from unittest.mock import Mock

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import benchmark_quality_motion as quality


class QualityMotionTest(unittest.TestCase):
    def test_recording_cannot_be_added_to_scored_or_private_protocols(self):
        args=dict(functional_check=True,candidate='public',dh=False,scene='saved',comparison_order=None)
        quality.validate_request(SimpleNamespace(**args))
        quality.validate_request(SimpleNamespace(**(args|{'scene':'natural-terrain'})))
        for change in ({'functional_check':False},{'candidate':'native-control'},
                       {'dh':True},{'scene':'natural-cave'},{'comparison_order':'ABBA'}):
            with self.assertRaises(ValueError):quality.validate_request(SimpleNamespace(**(args|change)))

    def test_capture_rejects_unowned_output_and_incomplete_sequence(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);out=root/'quality-native';out.mkdir()
            camera={k:0 for k in ('x','y','z','yaw','pitch')}
            control=Mock();control.send.side_effect=[{'state':{'camera':camera}},
                {'success':True,'quality':{'output_directory':str(root/'other')}}]
            # The real capture requires a fresh root; construct after its check.
            out.rmdir()
            with self.assertRaisesRegex(ValueError,'escaped owned'):quality.capture(control,root,'native')
            def send(action,**kwargs):
                if action=='status':return {'state':{'camera':camera}}
                if action=='quality_start':
                    out.mkdir();(out/'manifest.json').write_text(json.dumps({
                        'status':'complete','skipped_capture_slots':1,'png_callback_successes':201,
                        'writer_pending_at_finalization':False}))
                    return {'success':True,'quality':{'output_directory':str(out)}}
                return {'quality':{'finalized':True}}
            control.send.side_effect=send
            with self.assertRaisesRegex(ValueError,'incomplete'):quality.capture(control,root,'native')

"""Admission refusals must happen before profile/output mutation or process launch."""
import os
from pathlib import Path
from types import SimpleNamespace
from unittest import TestCase, mock
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import cost_map_live as live

class LiveAdmission(TestCase):
 def args(self,**kw):
  return SimpleNamespace(profile=Path('/unused-profile'),output=Path('/unused-output'),seconds=8,dh=False,dh_transition_check=False,**kw)
 def test_budget_refuses_before_storage_or_launch(self):
  a=self.args();a.seconds=31
  with mock.patch.dict(os.environ,SHADERLOD_ARM64_RUNTIME_READY='1'),mock.patch('headless_display.environment',return_value={}),mock.patch.object(live.storage,'admit') as admit:
   with self.assertRaisesRegex(ValueError,'1..30'):live.run(a)
   admit.assert_not_called()
 def test_transition_requires_declared_dh(self):
  a=self.args();a.dh_transition_check=True
  with mock.patch.dict(os.environ,SHADERLOD_ARM64_RUNTIME_READY='1'),mock.patch('headless_display.environment',return_value={}),mock.patch.object(live.storage,'admit') as admit:
   with self.assertRaisesRegex(ValueError,'requires --dh'):live.run(a)
   admit.assert_not_called()
 def test_existing_client_refuses_before_configuration_backup(self):
  with mock.patch.dict(os.environ,SHADERLOD_ARM64_RUNTIME_READY='1'),mock.patch('headless_display.environment',return_value={}),mock.patch.object(live.storage,'admit'),mock.patch.object(live.dev,'clients',return_value={1:{}}),mock.patch.object(live.dev,'process_table',return_value={}),mock.patch.object(live.life.Operation,'create') as create:
   with self.assertRaisesRegex(ValueError,'Another client'):live.run(self.args())
   create.assert_not_called()

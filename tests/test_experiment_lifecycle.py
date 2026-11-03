"""Tiny real files and explicit fake runtime receipts; no Java/Gradle/client launch."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch, Mock

import experiment_lifecycle as life
import run_storage as storage


class LifecycleTests(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory(prefix='shaderlod-lifecycle-');self.addCleanup(self.temp.cleanup)
  self.root=Path(self.temp.name).resolve();self.profile=self.root/'slot/client'
  (self.profile/'config').mkdir(parents=True)
  self.file=self.profile/'config/iris.properties';self.file.write_text('original')
  self.new=self.profile/'config/new.toml';self.out=self.root/'report'
  self.op=life.Operation.create(self.out,self.profile,{},[self.file,self.new])
 def mutate(self):
  self.op.begin_mutation('configure');self.file.write_text('owned');self.new.write_text('new owned')
  self.op.acknowledge_mutation('configure')
 def test_next_optimization_backup_is_tracked_and_restored_only_after_acknowledgment(self):
  config=self.profile/'config/shaderlod-optimizations.properties';config.write_text('original flags')
  (config.parent/(config.name+'.bak')).write_text('older')
  (config.parent/(config.name+'.bak.1')).write_text('older still')
  backup=config.parent/(config.name+'.bak.2')
  paths=life.configuration_paths(self.profile,self.root/'pack.zip')
  self.assertIn(backup,paths)
  op=life.Operation.create(self.root/'second-report',self.profile,{},paths)
  op.begin_mutation('apply');backup.write_text('original flags');op.acknowledge_mutation('apply')
  op.restore();self.assertFalse(backup.exists());self.assertEqual(config.read_text(),'original flags')
  self.assertEqual((config.parent/(config.name+'.bak.1')).read_text(),'older still')
 def test_unacknowledged_optimization_backup_is_preserved(self):
  config=self.profile/'config/shaderlod-optimizations.properties';config.write_text('original flags')
  backup=config.parent/(config.name+'.bak')
  op=life.Operation.create(self.root/'second-report',self.profile,{},life.configuration_paths(self.profile,self.root/'pack.zip'))
  op.begin_mutation('apply');backup.write_text('uncertain')
  with self.assertRaisesRegex(ValueError,'Uncertain'):op.restore()
  self.assertEqual(backup.read_text(),'uncertain')
 def test_acknowledged_changes_restore_and_repeated_recovery_skips_completed_files(self):
  self.mutate();self.op.restore();self.assertEqual(self.file.read_text(),'original');self.assertFalse(self.new.exists())
  with patch.object(Path,'replace',side_effect=AssertionError('completed restoration replayed')):self.op.restore()
 def test_external_change_is_preserved_not_adopted_by_recovery(self):
  self.mutate();self.file.write_text('other owner')
  with self.assertRaisesRegex(ValueError,'Uncertain'):self.op.restore()
  self.assertEqual(self.file.read_text(),'other owner');self.assertTrue(Path(self.op.data['configuration'][str(self.file)]['backup']).exists())
 def test_interrupted_unacknowledged_mutation_is_retained(self):
  self.op.begin_mutation('configure');self.file.write_text('uncertain')
  reopened=life.Operation(self.out)
  with self.assertRaisesRegex(ValueError,'Uncertain'):reopened.restore()
  self.assertEqual(self.file.read_text(),'uncertain')
 def test_missing_original_file_is_restored_only_if_owned_deletion_acknowledged(self):
  self.op.begin_mutation('owned-delete');self.file.unlink();self.op.acknowledge_mutation('owned-delete');self.op.restore()
  self.assertEqual(self.file.read_text(),'original')
 def test_unmanaged_new_file_is_preserved(self):
  extra=self.profile/'config/unknown';extra.write_text('someone else')
  self.op.restore();self.assertEqual(extra.read_text(),'someone else')
 def test_changed_backup_refuses_without_modifying_runtime_file(self):
  self.mutate();Path(self.op.data['configuration'][str(self.file)]['backup']).write_text('changed')
  with self.assertRaisesRegex(ValueError,'backup identity'):self.op.restore()
  self.assertEqual(self.file.read_text(),'owned')
 def test_terminal_disposal_is_exact_manifested_and_pin_aware(self):
  self.mutate();self.op.restore();self.op.data['state']='complete';self.op.save()
  sentinel=self.out/'configuration-before/unknown';sentinel.write_text('not manifested')
  (self.out/'.storage-keep').write_text('pin raw evidence')
  self.assertIn('retained',self.op.dispose_backups());(self.out/'.storage-keep').unlink()
  result=self.op.dispose_backups();self.assertEqual(result['logical_removed_bytes'],8)
  self.assertTrue(sentinel.exists());self.assertEqual(self.op.dispose_backups()['logical_removed_bytes'],0)
  self.assertTrue(self.op.data['cleanup']);self.assertTrue(all(r['status']=='removed' for r in self.op.data['cleanup']))
 def test_nonterminal_disposal_and_moved_or_replaced_output_are_refused(self):
  with self.assertRaisesRegex(ValueError,'Terminal'):self.op.dispose_backups()
  moved=self.root/'moved';self.out.rename(moved)
  with self.assertRaisesRegex(ValueError,'moved'):life.Operation(moved)
 def test_configuration_symlink_is_refused_and_target_preserved(self):
  self.file.unlink();self.file.symlink_to(self.root/'elsewhere')
  with self.assertRaisesRegex(ValueError,'Symlink'):self.op.restore()
 def test_operation_lock_refuses_concurrent_recovery(self):
  with life.operation_lock(self.out):
   with self.assertRaisesRegex(ValueError,'live operation owner'):
    with life.operation_lock(self.out):pass
 def test_command_receipt_must_match_request_and_completion_is_not_replayed(self):
  row=self.op.command('status',{})
  with self.assertRaisesRegex(ValueError,'identity'):self.op.complete_command(row,{'request_id':'wrong','success':True})
  self.op.complete_command(row,{'request_id':row['id'],'success':True})
  self.op.data['state']='complete'
  with self.assertRaisesRegex(ValueError,'Terminal'):self.op.command('window_start',{})
 def state(self):
  return dict(game_directory=str(self.profile),client_pid=44,pack='Original.zip',pipeline_identity=7,pack_identity=8,
   world_loaded=True,meshes_ready=True,fallback=False,main_mod=True,focused=True,fullscreen=True,screen='none',
   width=1920,height=1200,loaded_stack={'minecraft':'1.21.1'})
 def test_runtime_identity_readiness_and_actual_versions_are_required(self):
  plan=dict(profile=str(self.profile),pack_name='Original.zip',mode='on',runtime_versions={'minecraft':'1.21.1'})
  owner={'clients':{'44':{}}};life.runtime_ready(self.state(),plan,owner)
  for key,value in [('game_directory','elsewhere'),('client_pid',55),('pack','Candidate.zip'),('pipeline_identity',0),
   ('meshes_ready',False),('fallback',True),('focused',False),('main_mod',False),('width',1280),('loaded_stack',{})]:
   with self.subTest(key=key),self.assertRaises(ValueError):life.runtime_ready(self.state()|{key:value},plan,owner)
 def test_explicit_viewport_is_verified_without_relaxing_default(self):
  plan=dict(profile=str(self.profile),pack_name='Original.zip',mode='on',runtime_versions={'minecraft':'1.21.1'},native_viewport=[2560,1600])
  owner={'clients':{'44':{}}}
  life.runtime_ready(self.state()|{'width':2560,'height':1600},plan,owner)
  with self.assertRaisesRegex(ValueError,'viewport'):life.runtime_ready(self.state(),plan,owner)
 def capture(self):
  directory=self.out/'windows/a';directory.mkdir(parents=True)
  guard=dict(label='a',path=str(directory),valid=True,completed_render_frames=10)
  (directory/'guard.json').write_text(json.dumps(guard))
  (directory/'result.json').write_text(json.dumps({'manifest':{'windows':[{'id':'a','validity':'VALID','statistics':{'count':10}}]}}))
  for name in ('before','after'):(directory/(name+'.json')).write_text('{}')
  return directory,guard
 def test_capture_requires_complete_identity_guard_samples_and_outputs(self):
  directory,guard=self.capture()
  with self.assertRaisesRegex(ValueError,'path/label'):life.capture_complete(self.op,'a',guard|{'label':'wrong'})
  (directory/'after.json').unlink()
  with self.assertRaisesRegex(ValueError,'Missing'):life.capture_complete(self.op,'a',guard)
  (directory/'after.json').write_text('{}');life.capture_complete(self.op,'a',guard)
  with self.assertRaisesRegex(ValueError,'replayed'):life.capture_complete(self.op,'a',guard)
 def test_zero_sample_or_wrong_window_capture_never_completes(self):
  directory,guard=self.capture()
  for row in ({'id':'wrong','validity':'VALID','statistics':{'count':10}}, {'id':'a','validity':'VALID','statistics':{'count':0}}):
   (directory/'result.json').write_text(json.dumps({'manifest':{'windows':[row]}}))
   with self.assertRaises(ValueError):life.capture_complete(self.op,'a',guard)
  self.assertEqual(self.op.data['captures'],{})
 def test_growth_and_logs_guard_stops_without_deleting_raw_evidence(self):
  with patch.object(storage.shutil,'disk_usage',return_value=Mock(free=40*storage.GIB)):
   guard=life.StorageGuard(self.out,self.profile,limits={'profile_growth_bytes':5,'log_bytes':4})
   self.new.write_text('123456')
   with self.assertRaisesRegex(ValueError,'profile growth'):guard.check()
   self.new.unlink();(self.out/'supervisor.log').write_text('12345')
   with self.assertRaisesRegex(ValueError,'log limit'):guard.check(full=False)
   self.assertTrue((self.out/'supervisor.log').exists())
 def test_low_space_never_blocks_restoration(self):
  self.mutate()
  with patch.object(storage.shutil,'disk_usage',return_value=Mock(free=1)):
   with self.assertRaisesRegex(ValueError,'reserve'):life.StorageGuard(self.out,self.profile).check()
   self.op.restore()
  self.assertEqual(self.file.read_text(),'original')
 def test_recycled_pid_is_not_signaled_or_adopted(self):
  proof={'pid':44,'start':'before'}
  life.process_absent(proof,{44:{'pid':44,'start':'after'}},lambda row:row)
  with self.assertRaisesRegex(ValueError,'still owns'):life.process_absent(proof,{44:proof},lambda row:row)

 def test_replaced_runtime_profile_cannot_be_adopted_during_recovery(self):
  old=self.profile.with_name('old-client');self.profile.rename(old);self.profile.mkdir()
  with self.assertRaisesRegex(ValueError,'profile identity changed'):life.Operation(self.out)
  self.assertEqual('original',(old/'config/iris.properties').read_text())
 def test_requested_options_must_match_actual_iris_values(self):
  plan=dict(profile=str(self.profile),pack_name='Original.zip',mode='on',runtime_versions={'minecraft':'1.21.1'},requested_options={'TAA':'true'})
  with self.assertRaisesRegex(ValueError,'option differs'):life.runtime_ready(self.state()|{'effective_options':{'TAA':'false'}},plan,{'clients':{'44':{}}})
  life.runtime_ready(self.state()|{'effective_options':{'TAA':'true'}},plan,{'clients':{'44':{}}})
 def test_scoped_capture_budget_preserves_raw_frames(self):
  with patch.object(storage.shutil,'disk_usage',return_value=Mock(free=40*storage.GIB)):
   guard=life.StorageGuard(self.out,self.profile,limits={'capture_bytes':4})
   frame=self.out/'frame-0001.png';frame.write_bytes(b'12345')
   with self.assertRaisesRegex(ValueError,'Capture storage'):guard.check()
   self.assertEqual(b'12345',frame.read_bytes())
 def test_preparation_backups_retire_after_terminal_verification(self):
  backup=self.out/'session/backups/client/config/iris.properties';backup.parent.mkdir(parents=True);backup.write_text('original')
  self.op.record_preparation_backups(self.out/'session/backups')
  self.op.restore();self.op.data['state']='complete';self.op.save();self.op.dispose_backups()
  self.assertFalse(backup.exists());self.assertTrue(any(r['path']==str(backup) for r in self.op.data['cleanup']))

 def test_runtime_version_is_literal_pinned_jar_metadata_not_release_label(self):
  import zipfile
  jar=self.root/'release-1.8.12.jar'
  with zipfile.ZipFile(jar,'w') as z:z.writestr('META-INF/neoforge.mods.toml','[[mods]]\nmodId="iris"\nversion="1.8.12-snapshot+mc1.21.1-local"\n')
  self.assertEqual('1.8.12-snapshot+mc1.21.1-local',life.neoforge_mod_version(jar,'iris'))
  with self.assertRaisesRegex(ValueError,'Unsupported'):life.neoforge_mod_version(jar,'sodium')


if __name__=='__main__':unittest.main()

"""Registry -> mock query owner -> canonical JSON -> catalog -> export -> offline read."""
from pathlib import Path
import sys
import json
import subprocess
import tempfile
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import cost_map as cm
from pinned_tools import java_tool
ROOT=Path(__file__).resolve().parents[1]

class Pipeline(unittest.TestCase):
 def test_registry_to_report(self):
  with tempfile.TemporaryDirectory() as tmp:
   root=Path(tmp)
   sources=list((ROOT/'src/devClient/java/com/pp/shaderlod/dev/timing/costmap').glob('*.java'))
   sources+=list((ROOT/'src/devClientTest/java/com/pp/shaderlod/dev/timing/costmap').glob('*.java'))
   subprocess.run([java_tool('javac'),'--release','21','-d',tmp,*map(str,sources)],check=True)
   capture=root/'input.json'
   subprocess.run([java_tool('java'),'-cp',tmp,'com.pp.shaderlod.dev.timing.costmap.CostMapPipelineFixture',str(capture)],check=True)
   data=json.loads(capture.read_text());out=root/'out';r=cm.export(data,out)
   cpu=next(x for x in r['costs'] if x['metric']=='cpu_wall')
   gpu=next(x for x in r['costs'] if x['metric']=='gpu_elapsed')
   self.assertEqual(cpu['mean_ns'],200);self.assertEqual(cpu['median_ns'],200);self.assertEqual(cpu['p95_ns'],300)
   self.assertAlmostEqual(cpu['variance_ns2'],20000/3)
   self.assertEqual(gpu['count'],3);self.assertEqual(gpu['mean_ns'],100)
   self.assertEqual(len(r['cpu_trace']),3);self.assertEqual(r['gpu_trace'],[])
   self.assertTrue(all(x['duration_ns'] is None and x['status']=='NOT_APPLICABLE' for x in r['coverage'] if 'concern' in x))
   self.assertEqual(json.loads((out/'capture.json').read_text()),data)
   self.assertEqual(cm.summarize(json.loads((out/'capture.json').read_text())),r)
   self.assertTrue((out/'finalized.json').is_file());self.assertTrue((out/'report.html').is_file())
   self.assertEqual(next(x for x in r['costs'] if x['metric']=='cpu_thread')['count'],0)
   self.assertEqual(next(x for x in r['costs'] if x['metric']=='cpu_thread')['missingness']['UNKNOWN'],3)
   self.assertEqual(r['frame_pacing']['count'],1)
   import copy
   timestamps=copy.deepcopy(data)
   for e in timestamps['events']:
    if e.get('gpu') and e['gpu']['availability']['status']=='MEASURED':
     g=e['gpu'];g['backend']='TIMESTAMP';g['beginValue']=100000;g['endValue']=100100
     g['clock']['generation']=e['invocation']['eventId']
   partitioned=cm.summarize(timestamps)
   self.assertEqual(partitioned['gpu_trace'],[])
   self.assertEqual(len(partitioned['gpu_clock_traces']),3)
   self.assertEqual(len(partitioned['cpu_trace']),3)
   sparse=copy.deepcopy(data)
   first=True
   for e in sparse['events']:
    if e.get('cpu'):
     if first:e['cpu']['wallEndNs']=e['cpu']['wallStartNs'];first=False
     else:e['cpu']=None
   sparse_summary=next(x for x in cm.summarize(sparse)['costs'] if x['metric']=='cpu_wall')
   self.assertEqual(sparse_summary['count'],1);self.assertEqual(sparse_summary['mean_ns'],0)
   self.assertEqual(sparse_summary['missingness']['UNKNOWN'],2)
   with self.assertRaises(FileExistsError):cm.export(data,out)
   # Duplicate terminal results must never become a second sample.
   data['events'].append(next(e for e in data['events'] if e.get('cpu')))
   with self.assertRaises(ValueError):cm.summarize(data)
 def test_catalog_and_dh(self):
  for pack in cm.SEEDS:
   scope={'id':1,'name':'raw','program':'deferred1' if 'makeup' not in pack else 'deferred','graph_generation':1}
   manifest={'pack_revision':pack,'dh_state':'ENABLED_READY'}
   self.assertEqual(cm.catalog(scope,manifest)['mapping'],'unmapped')
   pinned=next(p for p in cm.SOURCE_CATALOG['packs'] if p['pack_revision']==pack)
   member='shaders/world0/'+scope['program']+'.fsh'
   manifest.update(archive_sha256=pinned['archive_sha256'],dimension='world0',options_hash='fixture')
   manifest['verified_graph_programs']={scope['program']:{'source_member':member,'source_hash':pinned['source_members'][member]['sha256'],'effective_options_hash':'fixture','graph_generation':1,'dh_state':'ENABLED_READY','dh_consumer':True}}
   match=cm.catalog(scope,manifest);self.assertEqual(match['mapping'],'mixed');self.assertTrue(match['dh_consumer'])
   manifest['pack_revision']='future-version';self.assertEqual(cm.catalog(scope,manifest)['mapping'],'unmapped')
  for state,status in [('ABSENT','NOT_APPLICABLE'),('FAILED','INVALID'),('UNSUPPORTED','UNSUPPORTED'),('ENABLED_NOT_READY','UNKNOWN'),('ENABLED_READY','UNKNOWN')]:
   rows=cm.coverage({'dh_state':state});self.assertEqual(len(rows),len(cm.DH_ROWS));self.assertTrue(all(r['status']==status for r in rows))
  rows=cm.coverage({'dh_state':'INSTALLED_RENDERING_DISABLED'})
  self.assertEqual(next(r for r in rows if r['concern']=='DH/generation')['status'],'UNKNOWN')
 def test_feasibility(self):
  from types import SimpleNamespace
  a=SimpleNamespace(fps=60,seconds=20,windows=3,processes=2,cadence=4,scope_sets=7,minimum=100)
  self.assertTrue(cm.plan(a)['feasible']);a.seconds=1;self.assertFalse(cm.plan(a)['feasible'])

class SourceInventory(unittest.TestCase):
 def test_pinned_archive_and_graph_members(self):
  import hashlib
  import zipfile
  catalog=json.loads((ROOT/'docs/cost-map-catalog.json').read_text())
  self.assertEqual(len(catalog['packs']),4)
  for pack in catalog['packs']:
   archive=ROOT/'dependencies/upstream/other-pack-research'/pack['archive']
   if not archive.exists():self.skipTest('pinned original archives unavailable')
   self.assertEqual(hashlib.sha256(archive.read_bytes()).hexdigest(),pack['archive_sha256'])
   with zipfile.ZipFile(archive) as z:
    self.assertTrue(pack['source_members'])
    self.assertEqual(len(pack['dh_program_members']),4)
    for member,identity in pack['source_members'].items():
     self.assertEqual(hashlib.sha256(z.read(member)).hexdigest(),identity['sha256'])
     self.assertEqual(len(z.read(member)),identity['bytes'])
    self.assertFalse(any('/dh_shadow.' in n for n in pack['dh_program_members']))

class LiveSinkHeadless(unittest.TestCase):
 def test_real_canonical_sink_to_offline_report(self):
  cached=Path.home()/'.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson'
  jars=sorted(cached.glob('*/*/*.jar'))
  if not jars:self.skipTest('cached Gson required for canonical live-sink fixture')
  with tempfile.TemporaryDirectory(prefix='cost-map-session-') as tmp:
   sources=list((ROOT/'src/devClient/java/com/pp/shaderlod/dev/timing/costmap').glob('*.java'))
   sources += [ROOT/'src/devClient/java/com/pp/shaderlod/dev/timing/CostMapSession.java',ROOT/'src/devClientTest/java/com/pp/shaderlod/dev/timing/CostMapSessionTest.java']
   subprocess.run([java_tool('javac'),'--release','21','-cp',str(jars[-1]),'-d',tmp,*map(str,sources)],check=True)
   capture=Path(tmp)/'capture'
   subprocess.run([java_tool('java'),'-cp',tmp+':'+str(jars[-1]),'com.pp.shaderlod.dev.timing.CostMapSessionTest',str(capture)],check=True)
   data=json.loads((capture/'capture.json').read_text());r=cm.export(data,Path(tmp)/'report')
   self.assertFalse(r['gpu_trace'])
   self.assertEqual(next(x for x in r['costs'] if x['metric']=='cpu_wall')['mean_ns'],300)
   self.assertEqual(next(x for x in r['costs'] if x['metric']=='gpu_elapsed')['mean_ns'],100)
   self.assertEqual(data['frames'][0]['rootStartNs'],1000)
   self.assertTrue(data['frames'][-1]['partialEnd'])
   completed=next(e['gpu'] for e in data['events'] if e.get('gpu') and e['gpu']['availability']['status']=='MEASURED')
   self.assertEqual(completed['invocation']['frame']['frameId'],1)
   self.assertEqual(completed['retrievalFrame']['frameId'],2)

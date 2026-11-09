import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import cost_map as cm
from cost_map_live_catalog import bind
ROOT=Path(__file__).resolve().parents[1]

class DhCoverage(unittest.TestCase):
 def test_four_pack_binding_and_refusals(self):
  for pack in cm.SOURCE_CATALOG['packs']:
   with self.subTest(pack=pack['pack_revision']),tempfile.TemporaryDirectory() as tmp:
    root=Path(tmp)
    programs=list(cm.SEEDS[pack['pack_revision']])
    rows=[dict(name=p,valid=True) for p in programs]
    (root/'manifest.json').write_text(json.dumps(dict(dimension='minecraft:overworld',effective_options={'a':'true'},programs=rows)))
    for p in programs:(root/(p+'.fsh')).write_text('float d=texture2D(dhDepthTex0, uv).r;')
    data=dict(manifest=dict(graph_evidence=str(root),graph_generation=2,dh_state='ENABLED_READY'),scopes=[dict(id=i,name='iris/'+p,program='unknown',graph_generation=2) for i,p in enumerate(programs)])
    bind(data,ROOT/'dependencies/upstream/other-pack-research'/pack['archive'])
    for scope in data['scopes']:
     self.assertEqual(cm.catalog(scope,data['manifest'])['mapping'],'mixed')
     self.assertTrue(cm.catalog(scope,data['manifest'])['dh_consumer'])
     scope['graph_generation']=3
     self.assertEqual(cm.catalog(scope,data['manifest'])['mapping'],'unmapped')
    data['manifest']['archive_sha256']='wrong'
    self.assertFalse(cm.catalog(data['scopes'][0],data['manifest'])['features'])
 def test_worker_upload_missingness(self):
  samples=[dict(kind='worker',thread='DH-World Gen Thread[0]',startNs=10,endNs=30,cpuNs=None,bytes=0,failed=False),dict(kind='upload/DATA',thread='Render thread',startNs=10,endNs=15,cpuNs=3,bytes=64,failed=False)]
  manifest=dict(dh_state='ENABLED_NOT_READY',dh_tasks=dict(samples=samples))
  rows=cm.task_summary(manifest)
  self.assertEqual(next(r for r in rows if r['concern']=='DH/generation' and r['metric']=='thread')['mean_ns'],None)
  self.assertEqual(next(r for r in rows if r['concern']=='DH/upload' and r['metric']=='wall')['mean_ns'],5)
  self.assertEqual(next(r for r in cm.coverage(manifest) if r['concern']=='DH/conversion')['duration_ns'],None)
  manifest['dh_state']='ABSENT'
  self.assertTrue(all(r['status']=='NOT_APPLICABLE' for r in cm.coverage(manifest)))
 def test_java_worker_ownership(self):
  jdk=Path('/home/philip/.gradle/jdks/eclipse_adoptium-21-aarch64-linux.2/bin')
  if not jdk.exists():self.skipTest('JDK unavailable')
  with tempfile.TemporaryDirectory() as tmp:
   sources=[ROOT/'src/devClient/java/com/pp/shaderlod/dev/timing/DhResidencyBounds.java',ROOT/'src/devClient/java/com/pp/shaderlod/dev/timing/DhTaskTimings.java',ROOT/'src/devClientTest/java/com/pp/shaderlod/dev/timing/DhTaskTimingsTest.java']
   subprocess.run([str(jdk/'javac'),'--release','21','-d',tmp,*map(str,sources)],check=True)
   subprocess.run([str(jdk/'java'),'-cp',tmp,'com.pp.shaderlod.dev.timing.DhTaskTimingsTest'],check=True)

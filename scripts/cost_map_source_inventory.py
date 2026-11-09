"""Inventory pinned original source members. Does not establish an enabled runtime graph."""
from pathlib import Path
import hashlib
import json
import zipfile
from cost_map_features import SEEDS
ROOT=Path(__file__).resolve().parents[1]
PACKS={'reimagined-r5.9.3':'ComplementaryReimagined_r5.9.3.zip','unbound-r5.9.3':'ComplementaryUnbound_r5.9.3.zip','bsl-10.1.8':'BSL_v10.1.8.zip','makeup-9.5g':'MakeUp-UltraFast-9.5g.zip'}

def inventory():
 rows=[]
 for revision,name in PACKS.items():
  p=ROOT/'dependencies/upstream/other-pack-research'/name
  with zipfile.ZipFile(p) as z:
   sources={}
   wanted=set(SEEDS[revision])|{'dh_terrain','dh_water','dh_shadow'}
   for member in z.namelist():
    if member.startswith('shaders/world0/') and Path(member).stem in wanted and member.endswith(('.fsh','.vsh','.csh')):
     body=z.read(member);sources[member]={'sha256':hashlib.sha256(body).hexdigest(),'bytes':len(body)}
   dh_paths=[n for n in z.namelist() if n.startswith('shaders/world0/dh_') and n.endswith(('.fsh','.vsh'))]
  rows.append({'pack_revision':revision,'archive':name,'archive_sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'dimension':'world0',
               'source_members':sources,'source_features':SEEDS[revision], 'dh_program_members':dh_paths,
               'dh_shadow_source':'not supplied under world0/dh_shadow; runtime support unknown',
               'states':{'ABSENT':'dedicated DH render rows not_applicable; raw shared graph still requires observation','ENABLED_READY':'terrain/water source present; preprocessed source, attachments, compatibility and runtime invocations unverified'},
               'confidence':'source inventory only; mixed source seeds; no feature execution proof'})
 return {'schema_version':1,'revision':'source-inventory-20261108','packs':rows}

if __name__=='__main__':print(json.dumps(inventory(),indent=2))

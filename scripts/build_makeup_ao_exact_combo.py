#!/usr/bin/env python3
"""Compose the pinned G06 exact attenuation and G07 exact pair-saturation pilots."""
import argparse
import json
from pathlib import Path
import build_makeup_ao_attenuation as attenuation
import build_makeup_ao_tap_skip as tap
from build_makeup_unused_sky import read_archive, archive_bytes, sha, NATIVE

def patch(native):return tap.patch(attenuation.patch(native,vertex_guard=True))

def build(original,options,output):
 native=read_archive(original);files=patch(native);side=Path(options).read_bytes()
 if b'SHADERLOD_' in side:raise ValueError('Native sidecar only')
 output=Path(output);paths=[output,Path(str(output)+'.txt'),output.with_suffix('.json')]
 if any(p.exists() for p in paths):raise ValueError('Fresh output paths required')
 changed=sorted(n for n in native if files[n]!=native[n]);added=sorted(files.keys()-native.keys())
 metadata={'ids':['G06','G07'],'variant':'exact-AO-combo-r1','base_sha256':NATIVE,'default_off':True,'options':[attenuation.OPTION,tap.OPTION],'changed_members':changed,'added_members':added,'source_hashes':{n:sha(files[n]) for n in changed+added},'scope':'MakeUp9.5g native100/AA2/AO1/5steps rootMac/Iris1.8.12/MC1.21.1 Overworld; noDH/Voxy; unchanged native graph/quality/AA.','mechanism':'Compose existing exact pilot transformations: vertex-uniform-camera guard with native ao_att==1 skip; otherwise retain native AO with saturated pair second-tap elision. Per-feature controls and combined control needed; no additive speed claim.'}
 files['SHADERLOD_EXACT_AO_COMBO_NOTICE.txt']=('Private MakeUp Ultra Fast9.5g derivative; native LGPL-3.0-or-later licenses/attribution retained.\nAdditions LGPL-3.0-or-later. Reproducible builder scripts/build_makeup_ao_exact_combo.py.\n'+json.dumps(metadata,indent=2)+'\n').encode();raw=archive_bytes(files);output.parent.mkdir(parents=True,exist_ok=True);output.write_bytes(raw);paths[1].write_bytes(side+(b'' if side.endswith(b'\n') else b'\n')+b''.join(f'{name}=0\n'.encode() for name in metadata['options']));metadata.update(artifact=str(output.resolve()),sha256=sha(raw),native_sidecar_sha256=sha(side));paths[2].write_text(json.dumps(metadata,indent=2)+'\n');return metadata

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--original',required=True,type=Path);p.add_argument('--options',required=True,type=Path);p.add_argument('--output',required=True,type=Path);print(json.dumps(build(**vars(p.parse_args())),indent=2))

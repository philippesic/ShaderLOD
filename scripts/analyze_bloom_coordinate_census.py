#!/usr/bin/env python3
"""Offline sort of actual GPU coordinates; no production32way search."""
from pathlib import Path
import json,argparse,struct,hashlib
import numpy as np

def analyze(receipts):
 assert len(receipts)==3
 receipts=sorted(receipts,key=lambda x:x['group']);w,h=receipts[0]['width'],receipts[0]['height'];header=lambda r:(r['width'],r['height'],struct.pack('f',r['frame_mod']),struct.pack('f',r['dither_shift']),struct.pack('f',r['soft_lod']))
 assert all(header(r)==header(receipts[0]) and r['state_restored'] for r in receipts)
 actual=receipts[0]['actual_sampler'];params=actual['samplers']['colortex0']['effective_sampling_parameters'];levels=actual['actual_sampled_mip_levels'];assert actual['status']=='complete' and all(x['width']>0 and x['height']>0 for x in levels)
 assert params['min_filter']==9987 and params['base_level']==0 and params['max_anisotropy']==1.0
 assert struct.pack('f',actual['soft_lod'])==struct.pack('f',receipts[0]['soft_lod'])
 assert all(x['width']==max(1,w//2**x['level']) and x['height']==max(1,h//2**x['level']) for x in levels)
 assert params['wrap_s']==33071 and params['wrap_t']==33071,'Rigorous plateau key requires actual clamp-to-edge'
 dtype='<f4' if receipts[0]['byte_order']=='LITTLE_ENDIAN' else '>f4';maps=[]
 for r in receipts:
  group=[]
  for file in r['files']:
   p=Path(file['path']);assert p.stat().st_size==w*h*16
   # Source receipt has SHA; hash after analysis/cleanup only if raw is removed.
   group.append(np.memmap(p,dtype=dtype,mode='r',shape=(h,w,4)))
  maps.append(group)
 hist=np.zeros(32,dtype=np.int64);exacthist=hist.copy();total=0;affected=0;exacttotal=0;zero_first=0;witness=[];overlaps=0;blocks=0
 for y in range(0,h,32):
  end=min(h,y+32);coords=np.zeros((end-y,w,32,2),dtype=np.float32);filled=np.zeros(32,dtype=bool)
  for r,group in zip(receipts,maps):
   start=r['start_tap'];values=np.stack([part for m in group for part in [m[y:end,:,0:2],m[y:end,:,2:4]]],axis=2)
   assert np.isfinite(values).all() and (np.abs(values)<2).all(),'Unused/sentinel/nonfinite native coordinate'
   for local in range(12):
    tap=start+local
    if filled[tap]:assert np.array_equal(coords[:,:,tap,:].view(np.uint32),values[:,:,local,:].view(np.uint32));overlaps+=(end-y)*w
    else:coords[:,:,tap,:]=values[:,:,local,:];filled[tap]=True
  assert filled.all();bits=coords.view(np.uint32);bits[coords==0]=0
  exact=(bits[...,0].astype(np.uint64)<<32)|bits[...,1].astype(np.uint64);exactsort=np.sort(exact,axis=-1);exactdups=np.sum(exactsort[:,:,1:]==exactsort[:,:,:-1],axis=-1);exacttotal+=int(exactdups.sum());exacthist+=np.bincount(exactdups.ravel(),minlength=32)
  keys=[]
  for axis in range(2):
   value=coords[...,axis];key=bits[...,axis].copy();sizes=[x['width' if axis==0 else 'height'] for x in levels]
   # Each original float32 is represented exactly in float64. Strict thresholds
   # use rational half-texel boundaries at BOTH actually allocated mip levels.
   left=np.logical_and.reduce([value.astype(np.float64)<=.5/s for s in sizes]);right=np.logical_and.reduce([value.astype(np.float64)>=1-.5/s for s in sizes]);key[left]=0x7fc00001;key[right]=0x7fc00002;keys.append(key)
  key=(keys[0].astype(np.uint64)<<32)|keys[1].astype(np.uint64);ordered=np.sort(key,axis=-1);dups=np.sum(ordered[:,:,1:]==ordered[:,:,:-1],axis=-1);total+=int(dups.sum());affected+=int(np.count_nonzero(dups));hist+=np.bincount(dups.ravel(),minlength=32)
  zero_first+=int(np.count_nonzero((exact[:,:,0]==exact[:,:,1])&(exact[:,:,0]==exact[:,:,2])&(exact[:,:,0]==exact[:,:,3])))
  if len(witness)<32:
   for yy,xx in np.argwhere(dups>0)[:32-len(witness)]:
    values=coords[yy,xx];witness.append({'x':int(xx),'y_gl':int(y+yy),'duplicate_queries':int(dups[yy,xx]),'exact_duplicate_queries':int(exactdups[yy,xx]),'query_uv_ieee754_bits':[[f'{int(a):08x}',f'{int(b):08x}'] for a,b in values.view(np.uint32)],'duplicate_key_tap_groups':[[i for i,k in enumerate(key[yy,xx]) if k==unique] for unique,count in zip(*np.unique(key[yy,xx],return_counts=True)) if count>1]})
  blocks+=1
 return {'width':w,'height':h,'native_taps':32,'frame_mod':receipts[0]['frame_mod'],'dither_shift':receipts[0]['dither_shift'],'soft_lod':receipts[0]['soft_lod'],'actual_mip_levels':levels,'actual_texture_parameters':params,'overlap_coordinates_bit_identical':overlaps,'pixels':w*h,'native_query_expressions':w*h*32,'exact_duplicate_queries':exacttotal,'exact_or_both_mip_clamp_duplicate_queries':total,'additional_clamp_duplicate_queries':total-exacttotal,'duplicate_query_fraction':total/(w*h*32),'affected_pixels':affected,'actual_first4_identical_pixels':zero_first,'duplicate_histogram':hist.tolist(),'exact_duplicate_histogram':exacthist.tolist(),'witness':witness,'scope':'Actual instrumented native32query coordinates fromonecompiledshader; matchingdither/header and alloverlapbits. Offline exact+bothactualmip clampplateau keys only, no approximatefilterfusion. Both normalizedUV axes identical or identically clamped atBOTHmips guarantee identicalfootprint. This is source-query opportunity, not physicaltransactions/GPU gain. No production32waysearch. Testednativeperiods/resolutions only, no infinitealternatives/exhaustion claim.'}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('result',type=Path);p.add_argument('--output',required=True,type=Path);a=p.parse_args();d=json.loads(a.result.read_text());rows=[]
 for width in sorted({x['width'] for x in d['coordinates']}):
  for frame in sorted({x['frame_mod'] for x in d['coordinates'] if x['width']==width}):
   row=analyze([x for x in d['coordinates'] if x['width']==width and x['frame_mod']==frame]);rows.append(row);print({k:row[k] for k in ['width','height','frame_mod','exact_duplicate_queries','additional_clamp_duplicate_queries','duplicate_query_fraction','affected_pixels']},flush=True)
 a.output.write_text(json.dumps({'rows':rows,'runtime_restored':d.get('restored'),'clients':d.get('clients_remaining')},indent=2))

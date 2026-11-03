#!/usr/bin/env python3
"""Pinned A8 source/fragment-budget certificate. Does not build a shader candidate."""
from __future__ import annotations
import argparse
import hashlib
import json
import math
import re
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'dev/logs/cross-shader-performance-r1/depth-hierarchy'
PINS = {
 'photon': '120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead',
 'bsl-shaders': '36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5',
 'solas-shader': 'b7dfe4c7353d677a92cc4c86b87bc0633f1014305d927ca6a482c303427bf825',
 'bliss-shader': 'f41db92acc585fe9ffe0cc124d6ecaea27cef9ded91129ebc2a8e60226947b2c',
}
SITES = {
 'photon': {
  'include/misc/raytracer.glsl': [(8,16),(47,125)],
  'include/misc/lod_mod_support.glsl': [(11,62),(132,142)],
  'include/lighting/shadows/ssrt.glsl': [(20,100),(128,196)],
  'program/d2_clouds_upscaling.fsh': [(195,230)],
  'program/d3_ao.fsh': [(90,117),(148,177)],
  'include/lighting/specular_lighting.glsl': [(199,230),(316,427)],
  'shaders.properties': [(340,365),(535,555)],
 },
 'bsl-shaders': {
  'lib/reflections/raytrace.glsl': [(30,115)],
  'lib/reflections/simpleReflections.glsl': [(1,30)],
  'lib/reflections/roughReflections.glsl': [(1,75)],
  'program/deferred1.glsl': [(157,174),(191,243),(345,395),(420,470)],
  'program/gbuffers_water.glsl': [(226,241),(537,578)],
 },
 'solas-shader': {
  'lib/pbr/raytracer.glsl': [(30,100)],
  'lib/pbr/simpleReflection.glsl': [(1,60)],
  'lib/pbr/waterReflection.glsl': [(1,35)],
  'lib/lighting/screenSpaceShadows.glsl': [(1,49)],
  'lib/common.glsl': [(169,208),(812,827)],
  'programs/deferred1.glsl': [(163,196),(325,341),(369,445)],
  'programs/gbuffers_water.glsl': [(135,146),(250,287)],
 },
 'bliss-shader': {
  'dimensions/deferred1.fsh': [(1,58)],
  'dimensions/composite1.fsh': [(59,86),(376,426),(1203,1206),(1240,1258)],
  'lib/specular.glsl': [(63,122),(260,285)],
  'lib/indirect_lighting_effects.glsl': [(202,247),(358,430)],
  'lib/settings.glsl': [(95,110),(263,272)],
 },
}

def sha(data: bytes) -> str:
 return hashlib.sha256(data).hexdigest()

def dimensions(width: int, height: int) -> list[tuple[int,int]]:
 if width < 1 or height < 1:
  raise ValueError('positive dimensions required')
 levels = [(width,height)]
 while levels[-1] != (1,1):
  w,h = levels[-1]
  levels.append(((w+1)//2,(h+1)//2))
 return levels

def atlas_plan(width: int, height: int) -> dict:
 """Two parity atlases; largest tile left, remaining tiles stacked to its right.

 Separate source and output texture objects prevent raster feedback. Ceil
 reduction includes every odd edge; this is an atlas, not a GL mip chain.
 """
 levels = dimensions(width,height)
 atlases=[]
 for parity in (0,1):
  assigned=[(i,w,h) for i,(w,h) in enumerate(levels) if i%2==parity]
  if not assigned:
   atlases.append({'width':0,'height':0,'bytes':0,'tiles':[]})
   continue
  first, *tail = assigned
  _,w0,h0=first
  aw=w0 + max((w for _,w,_ in tail),default=0)
  ah=max(h0,sum(h for _,_,h in tail))
  tiles=[{'level':first[0],'x':0,'y':0,'width':w0,'height':h0}]
  y=0
  for i,w,h in tail:
   tiles.append({'level':i,'x':w0,'y':y,'width':w,'height':h})
   y+=h
  atlases.append({'width':aw,'height':ah,'bytes':aw*ah*8,'tiles':tiles})
 n=width*height
 output_cells=sum(w*h for w,h in levels)
 reduction_cells=output_cells-n
 # Actual child count, excluding absent padded children: each prior-level cell
 # is read exactly once by its parent. Four fetch expressions per parent if
 # implementation clamps absent children instead; budget that higher value.
 actual_child_reads=sum(w*h for w,h in levels[:-1])
 return {'width':width,'height':height,'levels':levels,'draws_per_domain':len(levels),
  'atlases':atlases,'allocated_bytes':sum(a['bytes'] for a in atlases),
  'logical_bytes':output_cells*8,'base_depth_reads':n,
  'reduction_child_reads':actual_child_reads,'reduction_fetch_upper':4*reduction_cells,
  'output_cells':output_cells,'minimum_traffic_bytes':n*4+actual_child_reads*8+output_cells*8,
  'consumer_sampler_groups':2,'producer_source_sampler_groups':1,
  'consumer_reads_branches_and_native_fallback':'not included; depend on actual traversal/coverage',
  'full_rebuild_each_frame':True}

def conservative_pyramid(grid: list[list[float]]) -> list[list[list[tuple[float,float]]]]:
 """CPU reference only: raw scalar min/max, no projection or shader acceptance.

 Unknown/nonfinite/out-of-range normalized depths are [0,1], never empty.
 Copying finite source float32 endpoints does not introduce depth arithmetic.
 """
 if not grid or not grid[0] or any(len(r)!=len(grid[0]) for r in grid):
  raise ValueError('nonempty rectangular grid required')
 levels=[[[ (v,v) if math.isfinite(v) and 0<=v<=1 else (0.,1.) for v in row] for row in grid]]
 while len(levels[-1])>1 or len(levels[-1][0])>1:
  src=levels[-1]; h=len(src); w=len(src[0]); dst=[]
  for y in range(0,h,2):
   row=[]
   for x in range(0,w,2):
    children=[src[cy][cx] for cy in range(y,min(y+2,h)) for cx in range(x,min(x+2,w))]
    row.append((min(v[0] for v in children),max(v[1] for v in children)))
   dst.append(row)
  levels.append(dst)
 return levels

def excludes_native_interval(bound: tuple[float,float], interval: tuple[float,float]) -> bool:
 """Inclusive overlap test: equality/unknown/invalid values retain native work."""
 lo,hi=bound; a,b=interval
 if not all(math.isfinite(v) for v in (lo,hi,a,b)) or lo>hi or a>b:
  return False
 return hi<a or lo>b

def ssr_z_exclusion_math(z_ray: float, z_bounds: tuple[float,float], thickness: float) -> bool:
 """Real-arithmetic necessary Euclidean-hit condition, NOT a GPU certificate.

 Reversed/NaN/invalid bounds retain native work. Metadata still needs the
 terminal native sample. Runtime projection/filter/error bounds are absent.
 """
 lo,hi=z_bounds
 if not all(math.isfinite(v) for v in (z_ray,lo,hi,thickness)) or lo>hi or thickness<0:
  return False
 separation=max(lo-z_ray,z_ray-hi,0.)
 return separation>thickness

def sampler_closure(z: zipfile.ZipFile, index: dict[str,str], entry: str) -> dict:
 """Lexical include closure across all conditions; not linked active uniforms."""
 seen=set(); samplers=set(); members=[]
 def visit(path: str):
  if path in seen: return
  seen.add(path); data=z.read(index[path])
  code=re.sub(r'/\*.*?\*/|//[^\n]*','',data.decode('utf-8'),flags=re.S)
  extracted=ROOT/'dev/logs/generic-lod-strategy-r1/geometry/sources/solas-shader/shaders'/path
  if extracted.read_bytes()!=data: raise ValueError('include closure extract mismatch: '+path)
  members.append({'member':index[path],'canonical':path,'sha256':sha(data)})
  for declaration in re.finditer(r'\buniform\s+sampler\w+\s+([^;]+);',code):
   samplers.update(name.strip() for name in declaration.group(1).split(','))
  for included in re.findall(r'#include\s+"/([^\"]+)"',code): visit(included)
 visit(entry)
 return {'entry':entry,'members':sorted(members,key=lambda v:v['canonical']),
  'all_conditional_sampler_names':sorted(samplers),
  'no_voxy_upper_groups':len(samplers-{'vxDepthTexOpaque','vxDepthTexTrans'}),
  'limitation':'All conditional declarations; actual preprocessing/linker and binding groups unverified.'}

def source_index(z: zipfile.ZipFile) -> dict[str,str]:
 index={}
 for name in z.namelist():
  if name.endswith('/'): continue
  if name.startswith('shaders/'): canonical=name[8:]
  elif '/shaders/' in name: canonical=name.split('/shaders/',1)[1]
  else: continue
  if canonical in index: raise ValueError('duplicate shader member: '+canonical)
  index[canonical]=name
 return index

def run(output: Path) -> dict:
 pins={p['pack']:p for p in json.loads((ROOT/'dev/logs/generic-lod-strategy-r1/lead/artifact-pins.json').read_text()) if 'pack' in p}
 receipt={'kind':'A8-source-contract-and-cost-no-candidate','candidate_emitted':False,
  'packs':[],'budgets':[atlas_plan(*wh) for wh in [(1920,1200),(2560,1440),(480,300)]],
  'not_verified':['actual consumer activation and iterations','GL linked sampler headroom and attachment support',
   'actual transformed hand/DH depth-write/copy ordering and DH epochs at proposed insertion point','native filter/projection rounding certificate',
   'driver compilation','still/motion quality','hierarchy build plus consumer GPU timings'],
  'stopping_reason':'Exact portable Solas SSR+contact formulation rejected: contact uses undefined reversed-edge smoothstep. SSR-only remains deferred for active cost/coverage, numeric projection/filter proof and binding/epoch integration.'}
 for pack,expected in PINS.items():
  pin=pins[pack]; archive=ROOT/pin['file']; raw=archive.read_bytes()
  if sha(raw)!=expected: raise ValueError('archive pin mismatch: '+pack)
  record={'pack':pack,'archive':pin['file'],'sha256':expected,'version':pin['version'],
   'license_metadata':pin['license_metadata'],'members':[],'license_members':[]}
  with zipfile.ZipFile(archive) as z:
   index=source_index(z)
   for name in z.namelist():
    if not name.endswith('/') and any(Path(name).name.lower().startswith(prefix) for prefix in ('license','licence','copyright')):
     record['license_members'].append({'member':name,'sha256':sha(z.read(name))})
   for canonical,ranges in SITES[pack].items():
    data=z.read(index[canonical]); extracted=ROOT/'dev/logs/generic-lod-strategy-r1/geometry/sources'/pack/'shaders'/canonical
    if extracted.read_bytes()!=data: raise ValueError('extract mismatch: '+pack+'/'+canonical)
    lines=data.decode('utf-8',errors='strict').splitlines()
    record['members'].append({'canonical':canonical,'member':index[canonical],'sha256':sha(data),
     'excerpts':[{'start':a,'end':min(b,len(lines)),'numbered':'\n'.join(f'{i}: {lines[i-1]}' for i in range(a,min(b,len(lines))+1))} for a,b in ranges]})
   if pack=='solas-shader':
    record['deferred1_sampler_closure']=sampler_closure(z,index,'programs/deferred1.glsl')
    record['water_sampler_closure']=sampler_closure(z,index,'programs/gbuffers_water.glsl')
    contact=z.read(index['lib/lighting/screenSpaceShadows.glsl']).decode('utf-8')
    if 'smoothstep(thickness + 1.0, thickness, zDelta)' not in contact:
     raise ValueError('Solas contact falsifier changed; reevaluate design')
    record['portable_contact_falsifier']={'native_expression':'smoothstep(thickness + 1.0, thickness, zDelta)',
     'reason':'GLSL 4.10 smoothstep is undefined when edge0 >= edge1; cannot assume exact zero/one factor from bounds.',
     'spec':'https://registry.khronos.org/OpenGL/specs/gl/GLSLangSpec.4.10.pdf'}
  receipt['packs'].append(record)
 pipeline=ROOT/'dev/logs/mechanism-evaluation-r5/shadow-producer/iris-shadow-pipeline.javap.txt'
 text=pipeline.read_text(); start=text.index('  public void beginHand();'); end=text.index('  public void finalizeLevelRendering();',start)
 receipt['existing_iris_order_evidence']={'path':str(pipeline.relative_to(ROOT)),'sha256':sha(pipeline.read_bytes()),'excerpts':text[start:end],
  'limitation':'Retained pinned bytecode evidence reused; no fresh client/mixin call-order observation.'}
 binary=ROOT/'dependencies/runtime/iris-neoforge-1.8.12+mc1.21.1.jar'
 binary_pin='3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83'
 if sha(binary.read_bytes())!=binary_pin: raise ValueError('Iris binary pin changed')
 hand=OUT/'hand-depth-order.javap.txt'
 if hand.exists():
  body=hand.read_text(); start=body.index('  private void iris$beginTranslucents('); end=body.index('  private static void',start)
  method=body[start:end]
  ordered=['WorldRenderingPipeline.beginHand','HandRenderer.renderSolid','WorldRenderingPipeline.beginTranslucents']
  if sorted(method.index(token) for token in ordered)!=[method.index(token) for token in ordered]:
   raise ValueError('pinned vanilla hand/order witness changed')
  with zipfile.ZipFile(binary) as z:
   classes=['net/irisshaders/iris/mixin/MixinLevelRenderer.class','net/irisshaders/iris/pathways/HandRenderer.class','net/irisshaders/iris/targets/RenderTargets.class']
   receipt['fresh_binary_order_evidence']={'archive':str(binary.relative_to(ROOT)),'sha256':binary_pin,
    'classes':{name:sha(z.read(name)) for name in classes},'javap_path':str(hand.relative_to(ROOT)),
    'javap_sha256':sha(hand.read_bytes()),'solid_hand_before_depth1_copy_and_deferred':True,
    'limitation':'Exact binary/method order, not execution of transformed mixins or external DH ordering.'}
 output.mkdir(parents=True,exist_ok=True)
 (output/'source-resource-receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
 return receipt

if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__); parser.add_argument('--output',type=Path,default=OUT)
 args=parser.parse_args(); result=run(args.output)
 print(json.dumps({'candidate_emitted':False,'packs':len(result['packs']),'receipt':str(args.output/'source-resource-receipt.json'),
  'budgets':[{k:b[k] for k in ('width','height','allocated_bytes','minimum_traffic_bytes','draws_per_domain')} for b in result['budgets']]},indent=2))

#!/usr/bin/env python3
"""Source/control checks only; not driver rendering or speed."""
import importlib.util,json,re,subprocess
from pathlib import Path
spec=importlib.util.spec_from_file_location('bsl_speed_build',Path(__file__).with_name('build.py'));b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
f=b.files(b.ORIGINAL);stage=b.STAGE
c=b.files(stage/'BSL-speed-replication-r1-candidate-private.zip')
checks=[];runs=[]
def run(cmd,stdin=None):
 r=subprocess.run(cmd,input=stdin,text=True,capture_output=True);runs.append({'cmd':cmd,'code':r.returncode,'output':r.stdout+r.stderr});assert r.returncode==0,r.stdout+r.stderr;return r.stdout
def expand(n,fs):
 return re.sub(r'^\s*#include\s+"([^"]+)"[^\n]*$',lambda m:expand('shaders/'+m[1].lstrip('/') if m[1].startswith('/') else str(Path(n).parent/m[1]),fs),fs[n].decode(),flags=re.M)
def cpp(s,defs=''):
 return run(['clang','-E','-P','-x','c','-'],defs+re.sub(r'^\s*#(?:version|extension).*$','',s,flags=re.M))
def tokens(s):return re.findall(r'\w+|[^\s]',s)
manifest=json.loads((stage/'manifest.json').read_text())
for arm,mode in [('original',None),('disabled',0),('control',1),('candidate',2),('cue',3)]:
 info=manifest['arms'][arm];p=Path(info['path']);assert b.sha(p.read_bytes())==info['sha256'];fs=b.files(p)
 assert {n:b.sha(v) for n,v in fs.items()}==info['members']
 opts=json.loads((stage/f'{arm}-options.json').read_text());orig=json.loads((stage/'original-options.json').read_text());assert {k:v for k,v in opts.items() if k!=b.KEY}==orig
 if mode is not None:assert opts[b.KEY]==str(mode) and set(n for n in f if f[n]!=fs[n])=={b.SET,b.PROP,b.SHADOW,b.TERRAIN}
 else:assert p.read_bytes()==b.ORIGINAL.read_bytes()
checks.append('Five authentic pinned archives/maps/member-preservation')
for mode,extra in [(0,''),(2,'#define DISTANT_HORIZONS\n'),(2,'#define VOXY\n'),(2,'#define WORLD_CURVATURE\n'),(2,'#undef IRIS_VERSION\n#define IRIS_VERSION 11008\n'),(2,'#undef MC_VERSION\n#define MC_VERSION 12111\n')]:
 fs=b.patch(f,mode)
 for suffix in ['fsh','vsh']:
  entry='shaders/world0/gbuffers_terrain.'+suffix;defs='#define IS_IRIS\n#define IRIS_VERSION 10812\n#define MC_VERSION 12101\n'+extra
  assert tokens(cpp(expand(entry,f),defs))==tokens(cpp(expand(entry,fs),defs)),(mode,extra,suffix)
  checks.append('Fallback tokens '+str((mode,extra,suffix)))
# Disable native TAA in settings, not by a preamble that settings would override.
fs=c.copy();fs[b.SET]=fs[b.SET].replace(b'#define TAA\r\n',b'//#define TAA\r\n',1)
ff=f.copy();ff[b.SET]=ff[b.SET].replace(b'#define TAA\r\n',b'//#define TAA\r\n',1)
assert tokens(cpp(expand('shaders/world0/gbuffers_terrain.fsh',ff),'#define IS_IRIS\n#define IRIS_VERSION 10812\n#define MC_VERSION 12101\n'))==tokens(cpp(expand('shaders/world0/gbuffers_terrain.fsh',fs),'#define IS_IRIS\n#define IRIS_VERSION 10812\n#define MC_VERSION 12101\n'));checks.append('Native TAA-off original token fallback')
for entry in ['shaders/world0/gbuffers_water.fsh','shaders/world0/gbuffers_entities.fsh','shaders/world-1/gbuffers_terrain.fsh','shaders/world1/gbuffers_terrain.fsh']:
 defs='#define IS_IRIS\n#define IRIS_VERSION 10812\n#define MC_VERSION 12101\n'
 assert tokens(cpp(expand(entry,f),defs))==tokens(cpp(expand(entry,c),defs)),entry;checks.append('Original other caller '+entry)
base='''#version 330 compatibility
#extension GL_ARB_shader_texture_lod : enable
#define SHADOW
#define SHADOW_FILTER
#define SHADOW_COLOR
#define OVERWORLD
#define SHADOW_BIAS 0
#define SHADOW_PIXEL 0
#define SHADOW_SKY_FALLOFF 0.5
#define CLOUD_HEIGHT 192
#define CLOUD_SPEED 1.0
#define CLOUD_SCALE 1.0
#define CLOUD_THICKNESS 1.0
#define CLOUD_STRETCH 1.0
#define CLOUD_AMOUNT 1.0
#define CLOUD_DENSITY 1.0
#define CLOUD_OPACITY 1.0
#define CLOUD_BASE 0
const int shadowMapResolution=2048;
uniform float shadowDistance, shadowMapBias, far, time, rainStrength;
uniform vec3 cameraPosition,lightVec;
uniform mat4 gbufferModelViewInverse;
uniform sampler2D noisetex;
uniform int frameCounter;
vec3 ToShadow(vec3 p){return p;}
float Bayer8(vec2 p){return 0.5;}
'''
for mode in range(4):
 defs=f'#define {b.KEY} {mode}\n'
 if mode:defs+='#define SHADERLOD_BSL_SPEED_SUPPORTED\n#define SHADERLOD_BSL_SPEED_CONSUMER\n'
 path=stage/f'helper-mode-{mode}.frag';path.write_text(base+defs+c[b.SHADOW].decode()+'\nvoid main(){gl_FragColor=vec4(GetShadow(vec3(100),vec3(0,1,0),0.5,0.0,1.0),1.0);}\n');run(['glslangValidator','-S','frag',str(path)]);checks.append('Complete shadow helper parser mode'+str(mode))
native=f[b.SHADOW].decode().replace('\r\n','\n');candidate=c[b.SHADOW].decode().replace('\r\n','\n');a=native.index('vec3 SampleFilteredShadow(');z=native.index('\nvec3 GetShadow(',a);assert native[a:z] in candidate
for n in f:
 if n not in {b.SET,b.SHADOW,b.TERRAIN,b.PROP}:assert f[n]==c[n]
checks.append('Native9 helper verbatim; all other upstream members byte-identical')
phase=[[p+v*3 for v in range(3)] for p in range(3)];assert sorted(sum(phase,[]))==list(range(9));checks.append('Three native counter phases partition complete native9 support once')
assert 'receiverDistance > shaderlodNear' in candidate and 'shaderlodPreset != 1' in candidate
assert candidate.index('if (reduceKernel)')<candidate.index('shadow = shaderlodBslSpeedFiltered3(')<candidate.index('shaderlodBslSpeedReduced = true;')
checks.append('Core/near guard and cue after actual reduced return')
result={'status':'PASS_SOURCE_ONLY','checks':checks,'runs':runs,'phase_support':phase,'driver_render':False,'quality':False,'speed':False,'native_sampler_state':'UNVERIFIED','temporal_reconstruction_equivalence':False,'notes':'Mean of linear9 comparison values across three stationary frames equals full9; nonlinear colored/subsurface outputs and TAA acceptance may differ.'}
(stage/'checks.json').write_text(json.dumps(result,indent=2)+'\n');print('PASS_SOURCE_ONLY',len(checks),'checks')

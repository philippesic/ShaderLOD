#!/usr/bin/env python3
"""Pinned private BSL native384 early-alpha control/candidate; no runtime claim."""
import argparse, hashlib, json, zipfile
from pathlib import Path, PurePosixPath
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'dependencies/upstream/other-pack-research/BSL_v10.1.8.zip'
BASE_SHA='36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5'
OPTIONS=ROOT/'src/main/resources/assets/shaderlod/bslquad/native384.json'
OPTIONS_SHA='0aaa1276ea6e24106e6ed062a3bd149d1b53c09693d04a4637dcb19e6c43c394'
P='shaders/shaderlod/bsl_early_alpha_r1/'
ENTRY='shaders/world0/gbuffers_terrain.fsh'
TERRAIN='shaders/program/gbuffers_terrain.glsl'
LIGHT='shaders/lib/lighting/forward.glsl'
SHADOW='shaders/lib/lighting/shadows.glsl'
def sha(b):return hashlib.sha256(b).hexdigest()
def once(s,a,b):
 if s.count(a)!=1:raise ValueError('Missing/ambiguous native anchor: '+repr(a[:80]))
 return s.replace(a,b,1)
def load(base=BASE,options=OPTIONS):
 if sha(Path(base).read_bytes())!=BASE_SHA or sha(Path(options).read_bytes())!=OPTIONS_SHA:raise ValueError('Pinned original/native384 mismatch')
 with zipfile.ZipFile(base) as z:
  names=z.namelist()
  if len(names)!=len(set(names)) or any(PurePosixPath(n).is_absolute() or '..' in PurePosixPath(n).parts or '\\' in n for n in names):raise ValueError('Unsafe inventory')
  return {n:z.read(n) for n in names if not n.endswith('/')}
def derive(files,candidate):
 result=dict(files)
 terrain=files[TERRAIN].decode();shadow=files[SHADOW].decode()
 # The native forward include is discovered from exact pinned source, never a guessed path.
 lighting=[n for n,b in files.items() if n.startswith('shaders/lib/lighting/') and b'#include "/lib/lighting/shadows.glsl"' in b]
 if len(lighting)!=1:raise ValueError('Ambiguous shadow include owner')
 owner=lighting[0];light=files[owner].decode()
 terrain=once(terrain,'#include "/'+owner.removeprefix('shaders/')+'"','#include "/shaderlod/bsl_early_alpha_r1/lighting.glsl"')
 light=once(light,'#include "/lib/lighting/shadows.glsl"','#include "/shaderlod/bsl_early_alpha_r1/shadows.glsl"')
 declaration='uniform bool shaderlodBslAlphaEnabled;\r\nuniform bool shaderlodBslAlphaQualified;\r\nuniform int shaderlodBslAlphaVersion;\r\nuniform int shaderlodBslAlphaPreset;\r\nuniform float shaderlodBslAlphaReference;\r\n'
 stub='bool shaderlodBslNativeAlphaReject(float finalAlpha) { return false; }\r\n'
 terrain=once(terrain,'uniform int bedrockLevel;',declaration+stub+'uniform int bedrockLevel;')
 if candidate:
  anchor='    vec4 albedo = texture2D(texture, texCoord) * vec4(color.rgb, 1.0);'
  terrain=once(terrain,anchor,anchor+'\r\n    if (shaderlodBslAlphaEnabled && shaderlodBslAlphaQualified && shaderlodBslAlphaVersion == 1 && shaderlodBslAlphaPreset == 1 && shaderlodBslNativeAlphaReject(albedo.a)) discard;')
 # Both arms retain native implicit sampling if disabled/unqualified; equal-filter nonmip qualification required.
 shadow=once(shadow,'    return shadow2D(shadowtex, shadowPos).x;','    if (shaderlodBslAlphaEnabled && shaderlodBslAlphaQualified && shaderlodBslAlphaVersion == 1 && shaderlodBslAlphaPreset == 1 && shaderlodBslAlphaReference >= 0.0 && shaderlodBslAlphaReference <= 1.0) return shadow2DLod(shadowtex, shadowPos, 0.0).x;\r\n    return shadow2D(shadowtex, shadowPos).x;')
 color='vec3 shaderlodBslShadowColor(vec2 coord) {\r\n    if (shaderlodBslAlphaEnabled && shaderlodBslAlphaQualified && shaderlodBslAlphaVersion == 1 && shaderlodBslAlphaPreset == 1 && shaderlodBslAlphaReference >= 0.0 && shaderlodBslAlphaReference <= 1.0) return texture2DLod(shadowcolor0, coord, 0.0).rgb;\r\n    return texture2D(shadowcolor0, coord).rgb;\r\n}\r\n'
 shadow=once(shadow,'vec3 SampleBasicShadow(',color+'vec3 SampleBasicShadow(')
 import re
 shadow,count=re.subn(r'texture2D\(shadowcolor0, ([^\n]*?)\)\.rgb',r'shaderlodBslShadowColor(\1)',shadow)
 # Exclude dispatch helper fallback, which must remain native (replacement above catches it too).
 shadow=shadow.replace('return shaderlodBslShadowColor(coord);','return texture2D(shadowcolor0, coord).rgb;')
 if count!=4:raise ValueError('Native color-call census changed: '+str(count))
 result[ENTRY]=once(files[ENTRY].decode(),'#include "/program/gbuffers_terrain.glsl"','#include "/shaderlod/bsl_early_alpha_r1/terrain.glsl"').encode()
 result[P+'terrain.glsl']=terrain.encode();result[P+'lighting.glsl']=light.encode();result[P+'shadows.glsl']=shadow.encode()
 result[P+'NOTICE']=b'Private ShaderLOD experiment derived from BSL Shaders v10 Series by Capt Tatsu. Original licenses/credits retained. Native384 only; no quality or speed acceptance.\n'
 return result

def build(output,base=BASE,options=OPTIONS):
 output=Path(output).resolve()
 if not output.is_relative_to((ROOT/'dev/logs').resolve()):raise ValueError('Fresh ignored dev/logs destination required')
 original=load(base,options);record={'kind':'private-bsl-native384-early-alpha-r1','original_sha256':BASE_SHA,'native384_sha256':OPTIONS_SHA,'arms':{}}
 output.mkdir(parents=True,exist_ok=True)
 for arm,candidate in [('control',False),('candidate',True)]:
  files=derive(original,candidate);path=output/('BSL-early-alpha-r1-'+arm+'.zip')
  with zipfile.ZipFile(path,'x',compression=zipfile.ZIP_DEFLATED) as z:
   for name,b in sorted(files.items()):
    i=zipfile.ZipInfo(name,(1980,1,1,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,b)
  record['arms'][arm]={'path':str(path),'sha256':sha(path.read_bytes()),'members':{n:sha(b) for n,b in sorted(files.items())},'modifiedOriginalMembers':[n for n,b in original.items() if files[n]!=b]}
  side=Path(str(path)+'.txt');side.write_text('') # No overrides: all384 native values load from original shader defaults.
 record['builder_sha256']=sha(Path(__file__).read_bytes());(output/'manifest.json').write_text(json.dumps(record,indent=2)+'\n');return record
if __name__=='__main__':
 a=argparse.ArgumentParser();a.add_argument('--output',type=Path,required=True);v=a.parse_args();print(json.dumps(build(v.output),indent=2))

#!/usr/bin/env python3
"""Thin native AO witness: no arrays, loops or arithmetic added to either kernel."""
from pathlib import Path
import argparse,json
import build_makeup_ao_parity as parity
from build_makeup_unused_sky import read_archive,archive_bytes,once,sha,NATIVE

def build(original,options,output,control='skip'):
 original,options,output=map(Path,[original,options,output]);assert sha(original.read_bytes())==NATIVE
 if output.exists() or Path(str(output)+'.txt').exists():raise ValueError('Fresh output required')
 files,opt=parity.patch(read_archive(original),'G07-positive',native_control=control=='clone');guard=parity.tap.CONDITION
 if control=='forcedfalse':
  n=parity.tap.KERNEL;s=files[n].decode();s=once(s,'float shaderlodG07Skipped = 0.0;','uniform int shaderlodCausalSkip;\nfloat shaderlodG07Skipped = 0.0;');s=once(s,'if (!(angle + dist >= 1.0)) {','if (shaderlodCausalSkip==0 || !(angle + dist >= 1.0)) {');files[n]=s.encode();files[parity.tap.PROPS]+=b'\nuniform.int.shaderlodCausalSkip=0\n'
 n='shaders/common/deferred_fragment.glsl';s=files[n].decode();s=once(s,'const int colortex8Format = R16F;', 'const int colortex8Format = RGBA32F;')
 anchor='const bool colortex8Clear = true;';s=once(s,anchor,anchor+'\n'+''.join(f'const int colortex{t}Format = RGBA32F;\nconst bool colortex{t}Clear = true;\nconst vec4 colortex{t}ClearColor = vec4(0.0,0.0,0.0,0.0);\n' for t in range(9,14)))
 s=once(s,'/* DRAWBUFFERS:148 */','/* RENDERTARGETS: 1,4,8,9,10,11,12,13 */')
 s=once(s,'gl_FragData[2] = vec4(shaderlodAoMismatch > 0.0 ? 257.0 : 1.0 + shaderlodAoOmitted);','''gl_FragData[2] = vec4(shaderlodAoMismatch>0.0?2.0:1.0,-1.0,shaderlodAoOmitted,-1.0);
    gl_FragData[3] = vec4(shaderlodAoReference,shaderlodAoCandidate,ao_att,dither);
    gl_FragData[4] = vec4(texcoord,depth,linearDepth);
    gl_FragData[5] = vec4(near,far,aspectRatioInverse,fovYInverse);
    gl_FragData[6] = vec4(fogDensityCoeff,rainStrength,float(frameCounter),float(AOSTEPS));
    gl_FragData[7] = vec4(pixelSizeX,pixelSizeY,float(eyeBrightnessSmooth.x),float(eyeBrightnessSmooth.y));''');files[n]=s.encode();files[parity.tap.PROPS]+=''.join(f'\nblend.deferred.colortex{t}=off\n' for t in range(9,14)).encode()
 marker={'diagnostic_only':True,'kind':'G07','native':NATIVE,'control':control,'last_target':13,'targets':{'8':'flag0clear/1equal/2mismatch,-1,actualomittedexpressions,-1','9':'nativeFinalAo,candidateFinalAo,attenuation,dither','10':'actualUV.xy,callerRawDepth,callerLinearDepth','11':'near,far,aspectRatioInverse,fovYInverse','12':'fogDensityCoeff,rainStrength,frameCounter,AOSTEPS','13':'pixelSizeX,pixelSizeY,eyeBrightnessSmooth.xy'},'limits':'No kernel arithmetic/arrays/trace changes except forcedfalse uniformcontrol. MRTstores can still change compilation. Firstactualfailingpixel finalAO and inputs asbits; doesnot yetidentify firstinternaldivergence. NativeRGB, no leanquality or speed acceptance.'}
 files['SHADERLOD_CAUSAL_DIAGNOSTIC.json']=(json.dumps(marker,indent=2)+'\n').encode();files['causal-source/build_g07_causal_inputs.py']=Path(__file__).read_bytes();output.write_bytes(archive_bytes(files));Path(str(output)+'.txt').write_bytes(options.read_bytes()+f'\n{opt}=1\n'.encode());return {'artifact':str(output),'sha256':sha(output.read_bytes()),**marker}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__)
 for n in ['original','options','output']:p.add_argument('--'+n,required=True,type=Path)
 p.add_argument('--control',choices=['clone','forcedfalse','skip'],default='skip');print(json.dumps(build(**vars(p.parse_args())),indent=2))

#!/usr/bin/env python3
"""Focused first-divergence gate before any shadow-loop reuse; no arrays or loop instrumentation."""
from pathlib import Path
import argparse,json,tempfile,zipfile
import build_g09_causal_inputs as thin
from build_makeup_unused_sky import archive_bytes,once,sha

def build(original,options,output,control='forcedfalse'):
 output=Path(output)
 if output.exists() or Path(str(output)+'.txt').exists():raise ValueError('Fresh output required')
 with tempfile.TemporaryDirectory() as t:
  base=Path(t)/'thin.zip';thin.build(original,options,base,control)
  with zipfile.ZipFile(base) as z:files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
 kernel=files[thin.reuse.KERNEL].decode();gate=thin.parity.GATE
 kernel='#if '+gate+'\nvec4 shaderlodShadowPreN=vec4(-12345.0),shaderlodShadowPreC=vec4(-12345.0);\n#endif\n'+kernel
 anchor='    float texHeightOffset = texture2DGradARB(normals, ptexCoord, dcdx, dcdy).a;'
 assert kernel.count(anchor)==2
 first=kernel.index(anchor);kernel=kernel[:first]+kernel[first:].replace(anchor,anchor+'\n    shaderlodShadowPreC=vec4(ptexCoord,texHeight,texHeightOffset);',1)
 second=kernel.rindex(anchor);kernel=kernel[:second]+kernel[second:].replace(anchor,anchor+'\n    shaderlodShadowPreN=vec4(ptexCoord,texHeight,texHeightOffset);',1);files[thin.reuse.KERNEL]=kernel.encode()
 t=files[thin.reuse.TERRAIN].decode();anchor='''    gl_FragData[4]=vec4(shaderlodG09Mismatch>0.0?2.0:1.0,shaderlodG09First,shaderlodG09Saved,-1.0);
    gl_FragData[5]=vec4(newCoord,shaderlodG09CandidateCoord);
    gl_FragData[6]=vec4(surfaceDepth,shaderlodG09CandidateDepth,shaderlodG09NativeShadow,shaderlodG09CandidateShadow);
    gl_FragData[7]=vec4(dcdx,dcdy);'''
 t=once(t,anchor,'''    float shaderlodPreFirst=-1.0;
    for(int field=0;field<4;field++)if(!(shaderlodShadowPreN[field]==shaderlodShadowPreC[field])&&shaderlodPreFirst<0.0)shaderlodPreFirst=float(field);
    gl_FragData[4]=vec4(shaderlodG09Mismatch>0.0?2.0:1.0,shaderlodPreFirst,shaderlodG09Saved,-1.0);
    gl_FragData[5]=shaderlodShadowPreN;
    gl_FragData[6]=shaderlodShadowPreC;
    gl_FragData[7]=vec4(shaderlodG09NativeShadow,shaderlodG09CandidateShadow,surfaceDepth,shaderlodG09CandidateDepth);''');files[thin.reuse.TERRAIN]=t.encode()
 marker=json.loads(files['SHADERLOD_CAUSAL_DIAGNOSTIC.json']);marker['targets']={'8':'flagfinaloutput0clear/1equal/2fail,firstpreloopfield,actualomittedqueries,-1','9':'nativePreloopActualOffsetUV,baseHeight,offsetHeight','10':'candidatePreloopActualOffsetUV,baseHeight,offsetHeight','11':'nativeFinalShadow,candidateFinalShadow,nativeSurfaceDepth,candidateSurfaceDepth'};marker['limits']='Focused shadowpreloop after equal POM inputs. No arrays or loop arithmetic instrumentation; native/candidate preloop actual queryUV andtwoheights exported before anyshadowloop shortcut. Compilerwitness effects remain; no harmlessprecision or qualityacceptance.';files['SHADERLOD_CAUSAL_DIAGNOSTIC.json']=(json.dumps(marker,indent=2)+'\n').encode();files['causal-source/build_g09_shadow_preloop_trace.py']=Path(__file__).read_bytes();output.write_bytes(archive_bytes(files));Path(str(output)+'.txt').write_bytes(Path(options).read_bytes()+f'\n{thin.reuse.OPTION}=3\n'.encode());return dict(marker,artifact=str(output),sha256=sha(output.read_bytes()))
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__)
 for n in ['original','options','output']:p.add_argument('--'+n,required=True,type=Path)
 p.add_argument('--control',choices=['clone','forcedfalse','shadow'],default='forcedfalse');print(json.dumps(build(**vars(p.parse_args())),indent=2))

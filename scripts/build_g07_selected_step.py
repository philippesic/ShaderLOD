#!/usr/bin/env python3
"""Selected native AO iteration with five state vectors; no trace arrays."""
from pathlib import Path
import argparse,json,tempfile,zipfile
import build_g07_causal_inputs as thin
from build_makeup_unused_sky import archive_bytes,once,sha

def instrument(s,tag,candidate):
 anchor='        dist = clamp(0.25 * checkDepth - 1.0, 0.0, 1.0);'
 s=once(s,anchor,anchor+f'\n        if(i==shaderlodAoStep)shaderlodPre{tag}=vec4(offset,sampleLinearDepth,checkDepth);')
 anchor='        ambientOcclusion += clamp(angle + dist, 0.0, 1.0);'
 s=once(s,anchor,anchor+f'\n        if(i==shaderlodAoStep)shaderlodPost{tag}=vec4(sampleLinearDepth,checkDepth,clamp(angle+dist,0.0,1.0),ambientOcclusion);')
 if candidate:s=once(s,'else { shaderlodG07Skipped += 1.0; }','else { shaderlodG07Skipped += 1.0;if(i==shaderlodAoStep)shaderlodSelectedSkipped=1.0; }')
 return s

def build(original,options,output,control='forcedfalse'):
 output=Path(output)
 if output.exists() or Path(str(output)+'.txt').exists():raise ValueError('Fresh output required')
 with tempfile.TemporaryDirectory() as t:
  base=Path(t)/'thin.zip';thin.build(original,options,base,control)
  with zipfile.ZipFile(base) as z:files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
 name=thin.parity.tap.KERNEL;s=files[name].decode();start=s.index('float shaderlodAoReferenceKernel(')
 candidate=instrument(s[:start],'C',control!='clone');reference=instrument(s[start:],'N',False)
 files[name]=('uniform int shaderlodAoStep;\nvec4 shaderlodPreN=vec4(-12345.0),shaderlodPreC=vec4(-12345.0),shaderlodPostN=vec4(-12345.0),shaderlodPostC=vec4(-12345.0);\nfloat shaderlodSelectedSkipped=0.0;\n'+candidate+reference).encode()
 name='shaders/common/deferred_fragment.glsl';s=files[name].decode();a=s.index('gl_FragData[2] = vec4(shaderlodAoMismatch');b=s.index('gl_FragData[7] =',a);end=s.index(';',b)+1
 s=s[:a]+'''float shaderlodLocalFirst=-1.0;
    for(int field=0;field<4;field++)if(!(shaderlodPreN[field]==shaderlodPreC[field])&&shaderlodLocalFirst<0.0)shaderlodLocalFirst=float(field);
    for(int field=0;field<4;field++)if(!(shaderlodSelectedSkipped>0.0&&field<2)&&!(shaderlodPostN[field]==shaderlodPostC[field])&&shaderlodLocalFirst<0.0)shaderlodLocalFirst=4.0+float(field);
    gl_FragData[2]=vec4(shaderlodAoMismatch>0.0?2.0:1.0,shaderlodLocalFirst,shaderlodAoOmitted,float(shaderlodAoStep));
    gl_FragData[3]=shaderlodPreN;
    gl_FragData[4]=shaderlodPreC;
    gl_FragData[5]=shaderlodPostN;
    gl_FragData[6]=shaderlodPostC;
    gl_FragData[7]=vec4(shaderlodAoReference,shaderlodAoCandidate,shaderlodSelectedSkipped,dither);'''+s[end:];files[name]=s.encode()
 files[thin.parity.tap.PROPS]+=b'\nuniform.int.shaderlodAoStep=frameCounter % 5\n'
 marker=json.loads(files['SHADERLOD_CAUSAL_DIAGNOSTIC.json']);marker.update(selected_step_count=5,targets={'8':'finalflag,firstlocalfield,actualshortcuts,actualselectedstep','9':'nativeOffsetXY,positiveLD,positiveCheckDepth','10':'candidateOffsetXY,positiveLD,positiveCheckDepth','11':'nativeNegativeLD,negativeCheck,pairClamp,accum','12':'candidateNegativeLD,negativeCheck,pairClamp,accum','13':'nativeFinalAO,candidateFinalAO,selectedSkipped,actualDither'},limits='Only one selected iteration stored, no arrays or arithmetic recurrence change. Negative query state omitted/notcompared when actuallyskipped; pair/accum always compared. Localfirstfield is notglobalfirst. Original explicitLOD queries preserved. Instrumentation may change compiler; no harmlessprecision or leanquality inference.');files['SHADERLOD_CAUSAL_DIAGNOSTIC.json']=(json.dumps(marker,indent=2)+'\n').encode();files['causal-source/build_g07_selected_step.py']=Path(__file__).read_bytes();output.write_bytes(archive_bytes(files));Path(str(output)+'.txt').write_bytes(Path(options).read_bytes()+f'\n{thin.parity.tap.OPTION}=1\n'.encode());return dict(marker,artifact=str(output),sha256=sha(output.read_bytes()))
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__)
 for n in ['original','options','output']:p.add_argument('--'+n,required=True,type=Path)
 p.add_argument('--control',choices=['clone','forcedfalse','skip'],default='forcedfalse');print(json.dumps(build(**vars(p.parse_args())),indent=2))

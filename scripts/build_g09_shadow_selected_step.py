#!/usr/bin/env python3
"""Thin selected-iteration shadow bisection; native RGB and explicit gradients retained."""
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
 gate=thin.parity.GATE;kernel=files[thin.reuse.KERNEL].decode()
 kernel='#if '+gate+'\nuniform int shaderlodShadowStep;\nvec4 shaderlodStepN=vec4(-12345.0),shaderlodStepC=vec4(-12345.0);\nvec2 shaderlodAccumN=vec2(-12345.0),shaderlodAccumC=vec2(-12345.0);\n#endif\n'+kernel
 anchor='        parallaxshadow = min(parallaxshadow, sampleShadow);'
 assert kernel.count(anchor)==2
 first=kernel.index(anchor);kernel=kernel[:first]+kernel[first:].replace(anchor,anchor+'\n    if(i==shaderlodShadowStep){shaderlodStepC=vec4(parallaxCoord,offsetHeight,currentHeight);shaderlodAccumC=vec2(parallaxshadow,sampleShadow);}',1)
 second=kernel.rindex(anchor);kernel=kernel[:second]+kernel[second:].replace(anchor,anchor+'\n    if(i==shaderlodShadowStep){shaderlodStepN=vec4(parallaxCoord,offsetHeight,currentHeight);shaderlodAccumN=vec2(parallaxshadow,sampleShadow);}',1);files[thin.reuse.KERNEL]=kernel.encode()
 t=files[thin.reuse.TERRAIN].decode();anchor='''    gl_FragData[4]=vec4(shaderlodG09Mismatch>0.0?2.0:1.0,shaderlodG09First,shaderlodG09Saved,-1.0);
    gl_FragData[5]=vec4(newCoord,shaderlodG09CandidateCoord);
    gl_FragData[6]=vec4(surfaceDepth,shaderlodG09CandidateDepth,shaderlodG09NativeShadow,shaderlodG09CandidateShadow);
    gl_FragData[7]=vec4(dcdx,dcdy);'''
 t=once(t,anchor,'''    float shaderlodStepFirst=-1.0;
    for(int field=0;field<4;field++)if(!(shaderlodStepN[field]==shaderlodStepC[field])&&shaderlodStepFirst<0.0)shaderlodStepFirst=float(field);
    for(int field=0;field<2;field++)if(!(shaderlodAccumN[field]==shaderlodAccumC[field])&&shaderlodStepFirst<0.0)shaderlodStepFirst=4.0+float(field);
    gl_FragData[4]=vec4(shaderlodG09Mismatch>0.0?2.0:1.0,shaderlodStepFirst,shaderlodG09Saved,float(shaderlodShadowStep));
    gl_FragData[5]=shaderlodStepN;
    gl_FragData[6]=shaderlodStepC;
    gl_FragData[7]=vec4(shaderlodAccumN.x,shaderlodAccumC.x,shaderlodAccumN.y,shaderlodAccumC.y);''');files[thin.reuse.TERRAIN]=t.encode()
 files[thin.reuse.PROPS]+=b'\nuniform.int.shaderlodShadowStep=frameCounter % 8\n'
 marker=json.loads(files['SHADERLOD_CAUSAL_DIAGNOSTIC.json']);marker['selected_step_in_flag_w']=True;marker['targets']={'8':'finalflag,firstselectedstepfield,actualomittedqueries,actualselecteduniformstep','9':'nativeStepUV,height,currentHeight','10':'candidateStepUV,height,currentHeight','11':'nativeAccum,candidateAccum,nativeSampleShadow,candidateSampleShadow'};marker['limits']='Only one dynamically selected iteration is stored, no arrays/recurrence changes. Firstfield is local to actual selectedstep, not first across alliterations; join step0..7 and prior preloop witness. Original explicit gradients retained. Stores/selection can change compilation; nonreproduction is not quality clearance.';files['SHADERLOD_CAUSAL_DIAGNOSTIC.json']=(json.dumps(marker,indent=2)+'\n').encode();files['causal-source/build_g09_shadow_selected_step.py']=Path(__file__).read_bytes();output.write_bytes(archive_bytes(files));Path(str(output)+'.txt').write_bytes(Path(options).read_bytes()+f'\n{thin.reuse.OPTION}=3\n'.encode());return dict(marker,artifact=str(output),sha256=sha(output.read_bytes()))
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__)
 for n in ['original','options','output']:p.add_argument('--'+n,required=True,type=Path)
 p.add_argument('--control',choices=['clone','forcedfalse','shadow'],default='forcedfalse');print(json.dumps(build(**vars(p.parse_args())),indent=2))

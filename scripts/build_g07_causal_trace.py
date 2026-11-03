#!/usr/bin/env python3
"""Untimed native/reference vs actual G07 control flow, first scalar + raw bits.
Native RGB, all reference queries retained. Trace instrumentation is a witness,
not evidence about uninstrumented compiler precision or speed.
"""
from pathlib import Path
import argparse,json
import build_makeup_ao_tap_skip as tap
import build_makeup_ao_attenuation as ao
from build_makeup_unused_sky import NATIVE,read_archive,archive_bytes,once,sha
FIELDS=['ditherSample','phase','offset.x','offset.y','positiveLD','positiveCheck','firstAngle','firstDist','negativeLD','negativeCheck','finalAngle','finalDist','pairClamp','accumulation','shortcut']
PRE=['rawCenterDepth','linearCenterDepth','handCheck','farAndCheck','scaleFactor.x','scaleFactor.y','dither','far']
SIZE=len(PRE)+5*len(FIELDS)
def instrument(source,name,trace,candidate):
 source=once(source,'float dbao(',f'float {name}(')
 source=once(source,'    float ambientOcclusion = 0.0;',f'''    for (int t=0;t<{SIZE};t++) {trace}[t] = -1234567.0;
    float ambientOcclusion = 0.0;''')
 source=once(source,'    float handCheck = linearDepth < 0.56 ? 1024.0 : 1.0;',f'    {trace}[0] = linearDepth;\n    float handCheck = linearDepth < 0.56 ? 1024.0 : 1.0;')
 anchor='    float checkDepth;'
 source=once(source,anchor,anchor+f'''\n    {trace}[1]=linearDepth; {trace}[2]=handCheck; {trace}[3]=farAndCheck;
    {trace}[4]=scaleFactor.x; {trace}[5]=scaleFactor.y; {trace}[6]=dither; {trace}[7]=far;''')
 anchor='        offset = vec2(cos(n), sin(n)) * ditherSample * scaleFactor;'
 source=once(source,anchor,anchor+f'''\n        int b=8+i*15;
        {trace}[b]=ditherSample; {trace}[b+1]=n; {trace}[b+2]=offset.x; {trace}[b+3]=offset.y;''')
 anchor='        angle = clamp(0.5 - checkDepth, 0.0, 1.0);'
 source=once(source,anchor,f'        {trace}[b+4]=sampleLinearDepth; {trace}[b+5]=checkDepth;\n'+anchor)
 anchor='        dist = clamp(0.25 * checkDepth - 1.0, 0.0, 1.0);'
 source=once(source,anchor,anchor+f'\n        {trace}[b+6]=angle; {trace}[b+7]=dist; {trace}[b+14]=0.0;')
 anchor='        angle += clamp(0.5 - checkDepth, 0.0, 1.0);'
 source=once(source,anchor,f'        {trace}[b+8]=sampleLinearDepth; {trace}[b+9]=checkDepth;\n'+anchor)
 anchor='        ambientOcclusion += clamp(angle + dist, 0.0, 1.0);'
 source=once(source,anchor,f'        {trace}[b+10]=angle; {trace}[b+11]=dist; {trace}[b+12]=clamp(angle+dist,0.0,1.0);\n'+anchor+f'\n        {trace}[b+13]=ambientOcclusion;')
 if candidate:
  source=once(source,'if (!(angle + dist >= 1.0))','if (!(shaderlodCausalAllowSkip != 0 && angle + dist >= 1.0))')
  source=once(source,'else { shaderlodG07Skipped += 1.0; }',f'else {{ shaderlodG07Skipped += 1.0; {trace}[b+14]=1.0; }}')
 return source

def build(original,options,output,control='skip'):
 original,options,output=map(Path,[original,options,output]);assert sha(original.read_bytes())==NATIVE
 if output.exists() or Path(str(output)+'.txt').exists():raise ValueError('Fresh output required')
 native=read_archive(original);files=tap.patch(native);reference=instrument(native['shaders/lib/ao.glsl'].decode(),'shaderlodCausalReference','shaderlodTraceN',False)
 if control=='clone':candidate=instrument(native['shaders/lib/ao.glsl'].decode(),'shaderlodCausalCandidate','shaderlodTraceC',False);candidate='float shaderlodG07Skipped=0.0;\n'+candidate
 else:candidate=instrument(tap.kernel(native,cue=True).decode(),'shaderlodCausalCandidate','shaderlodTraceC',True)
 files[tap.KERNEL]=(f'uniform int shaderlodCausalAllowSkip;\nfloat shaderlodTraceN[{SIZE}];\nfloat shaderlodTraceC[{SIZE}];\n'+reference+'\n'+candidate).encode()
 call=f'''        #if {tap.CONDITION}
        float shaderlodNativeRaw=shaderlodCausalReference(dither);
        float shaderlodCandidateRaw=shaderlodCausalCandidate(dither);
        float shaderlodNativeScalar=mix(shaderlodNativeRaw,1.0,ao_att);
        float shaderlodCandidateScalar=mix(shaderlodCandidateRaw,1.0,ao_att);
        float final_ao=shaderlodNativeScalar;
        int shaderlodFirst=-1;
        int shaderlodFirstShortcut=-1;
        for(int t=0;t<8;t++) if(shaderlodFirst<0 && !(shaderlodTraceN[t]==shaderlodTraceC[t])) shaderlodFirst=t;
        for(int step=0;step<5;step++) {{
            int b=8+step*15;
            bool skipped=shaderlodTraceC[b+14]>0.0;
            if(skipped && shaderlodFirstShortcut<0) shaderlodFirstShortcut=step;
            for(int field=0;field<14;field++) {{
                bool comparable=!(skipped && field>=8 && field<=11);
                int t=b+field;
                if(comparable && shaderlodFirst<0 && !(shaderlodTraceN[t]==shaderlodTraceC[t])) shaderlodFirst=t;
            }}
        }}
        #else
{ao.NATIVE_CALL}
        #endif'''
 text=once(files[tap.CALLER].decode(),ao.NATIVE_CALL,call)
 formats='\n/*\n'+''.join(f'const int colortex{i}Format = RGBA32F;\nconst bool colortex{i}Clear = true;\nconst vec4 colortex{i}ClearColor = vec4(0.0,0.0,0.0,0.0);\n' for i in range(8,14))+'*/\n'
 text=once(text,'#include "/lib/config.glsl"','#include "/lib/config.glsl"\n#if '+tap.CONDITION+formats+'#endif\n')
 text=once(text,'/* DRAWBUFFERS:14 */',f'#if {tap.CONDITION}\n    /* RENDERTARGETS: 1,4,8,9,10,11,12,13 */\n    #else\n    /* DRAWBUFFERS:14 */\n    #endif')
 outputs='''
    #if CONDITION
    int first=shaderlodFirst<0 ? 0 : shaderlodFirst;
    int step=shaderlodFirst<8 ? 0 : (shaderlodFirst-8)/15;
    int b=8+step*15;
    gl_FragData[2]=vec4(shaderlodNativeScalar==shaderlodCandidateScalar?1.0:2.0,float(shaderlodFirst),shaderlodG07Skipped,float(shaderlodFirstShortcut));
    gl_FragData[3]=vec4(shaderlodTraceN[first],shaderlodTraceC[first],shaderlodNativeScalar,shaderlodCandidateScalar);
    gl_FragData[4]=vec4(texcoord.xy,dither,shaderlodTraceN[0]);
    gl_FragData[5]=vec4(shaderlodTraceN[b+2],shaderlodTraceN[b+3],shaderlodTraceC[b+2],shaderlodTraceC[b+3]);
    gl_FragData[6]=vec4(shaderlodTraceN[b+4],shaderlodTraceC[b+4],shaderlodTraceN[b+5],shaderlodTraceC[b+5]);
    gl_FragData[7]=vec4(shaderlodTraceN[b+12],shaderlodTraceC[b+12],shaderlodTraceN[b+13],shaderlodTraceC[b+13]);
    #endif
'''.replace('CONDITION',tap.CONDITION)
 text=once(text,'    gl_FragData[1] = blockColor;','    gl_FragData[1] = blockColor;\n'+outputs);files[tap.CALLER]=text.encode()
 files[tap.PROPS]+=('\nuniform.int.shaderlodCausalAllowSkip = '+('1' if control=='skip' else '0')+'\n'+''.join(f'blend.deferred.colortex{i}=off\n' for i in range(8,14))).encode()
 marker={'kind':'G07','native':NATIVE,'diagnostic_only':True,'control':control,'fields':PRE+[f'step{i}.{n}' for i in range(5) for n in FIELDS],'targets':{'8':'flag0clear/1equal/2outputfail, firstField, actualShortcuts, firstShortcutStep','9':'firstNative, firstCandidate, nativeAO, candidateAO','10':'uvX,uvY,dither,rawCenterDepth','11':'nativeOffsetXY,candidateOffsetXY at first differing step','12':'nativePositiveLD,candidatePositiveLD,nativePositiveCheck,candidatePositiveCheck','13':'nativePair,candidatePair,nativeAccum,candidateAccum'},'limits':'All reference/native queries execute; native RGB. Global trace stores can affect compiler optimization. First common scalar mismatch is a witnessed location, not a harmless-precision explanation. Fields8..11 after an actual skipped negative query are excluded from first mismatch selection; native final pair/accumulation remain compared. Uniform forcedfalse preserves actual conditional source/compiled flow.'}
 files['SHADERLOD_CAUSAL_DIAGNOSTIC.json']=(json.dumps(marker,indent=2)+'\n').encode();files['causal-source/build_g07_causal_trace.py']=Path(__file__).read_bytes();output.parent.mkdir(parents=True,exist_ok=True);output.write_bytes(archive_bytes(files));Path(str(output)+'.txt').write_bytes(options.read_bytes()+f'\n{tap.OPTION}=1\n'.encode());return {'artifact':str(output),'sha256':sha(output.read_bytes()),**marker}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--original',required=True,type=Path);p.add_argument('--options',required=True,type=Path);p.add_argument('--output',required=True,type=Path);p.add_argument('--control',choices=['clone','forcedfalse','skip'],default='skip');print(json.dumps(build(**vars(p.parse_args())),indent=2))

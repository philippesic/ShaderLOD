#!/usr/bin/env python3
"""Thin BSL native/candidate control with actual final-state and explicit-gradient bits."""
from pathlib import Path
import argparse,json,re
import build_g09_native_reuse as reuse
import build_g09_native_reuse_parity as parity
from build_makeup_unused_sky import archive_bytes,once,sha

def build(original,options,output,control='shadow'):
 original,options,output=map(Path,[original,options,output]);native=reuse.read_archive(original);files=parity.patch(native);gate=parity.GATE
 if output.exists() or Path(str(output)+'.txt').exists():raise ValueError('Fresh output required')
 s=files[reuse.KERNEL].decode();boundary='\n#ifdef SHADERLOD_G09_PARITY_ACTIVE\n';assert boundary not in s # parity has expanded inventory guards
 ref_anchor='\n#if '+gate+'\n';start=s.rfind(ref_anchor);assert start>0
 candidate=s[:start];reference=s[start:]
 if control=='clone':candidate=parity.HEADER.replace('#endif\n','#endif\n',1)+native[reuse.KERNEL].decode().replace('\r\n','\n')
 else:
  candidate=once(candidate,'    float depth = shaderlodG09Initial.a;','    float depth = shaderlodCausalInitialReuse!=0 ? shaderlodG09Initial.a : ReadNormal(coord).a;')
  candidate=once(candidate,'if (parallaxFade >= 1.0) {','if (false) {')
  candidate=once(candidate,'    if (normalCheck < -1.999) return texCoord;','    if (parallaxFade >= 1.0 || normalCheck < -1.999) return texCoord;')
  candidate=once(candidate,'if (shaderlodG09Reuse) {','if (shaderlodCausalShadowReuse!=0 && shaderlodG09Reuse) {')
  # Existing logical omission counter is conditional on actual initial reuse.
  candidate=once(candidate,'    shaderlodG09Saved += 1.0;\n    #endif\n    float depth =', '    if(shaderlodCausalInitialReuse!=0)shaderlodG09Saved += 1.0;\n    #endif\n    float depth =')
 s='#if '+gate+'\nuniform int shaderlodCausalInitialReuse;\nuniform int shaderlodCausalShadowReuse;\n#endif\n'+candidate+reference
 files[reuse.KERNEL]=s.encode()
 t=files[reuse.TERRAIN].decode();t=once(t,'    float shaderlodG09Mismatch = 0.0;','''    float shaderlodG09Mismatch = 0.0;
    float shaderlodG09First=-1.0;
    float shaderlodG09NativeShadow=1.0,shaderlodG09CandidateShadow=1.0;''')
 anchor='''        if (any(notEqual(newCoord, shaderlodG09CandidateCoord)) || surfaceDepth != shaderlodG09CandidateDepth)
            shaderlodG09Mismatch = 1.0;'''
 t=once(t,anchor,anchor+'''
        if(!(newCoord.x==shaderlodG09CandidateCoord.x))shaderlodG09First=3600.0;
        else if(!(newCoord.y==shaderlodG09CandidateCoord.y))shaderlodG09First=3601.0;
        else if(!(surfaceDepth==shaderlodG09CandidateDepth))shaderlodG09First=3602.0;''')
 t=once(t,'            if (parallaxShadow != shaderlodG09CandidateShadow) shaderlodG09Mismatch = 1.0;','''            shaderlodG09NativeShadow=parallaxShadow;
            if (parallaxShadow != shaderlodG09CandidateShadow) {shaderlodG09Mismatch=1.0;if(shaderlodG09First<0.0)shaderlodG09First=3608.0;}''')
 # Candidate shadow local must survive the native-shadow branch for MRT export.
 t=once(t,'            float shaderlodG09CandidateShadow = GetParallaxShadow','            shaderlodG09CandidateShadow = GetParallaxShadow')
 anchor='''    /* DRAWBUFFERS:03678 */
    gl_FragData[4] = vec4(shaderlodG09Mismatch > 0.0 ? 257.0 : 1.0 + shaderlodG09Saved
        + 16.0 * shaderlodG09PomCalled + 32.0 * shaderlodG09PomStepped
        + 64.0 * shaderlodG09ShadowCalled + 128.0 * shaderlodG09ShadowStepped);'''
 t=once(t,anchor,'''    /* RENDERTARGETS: 0,3,6,7,8,9,10,11 */
    gl_FragData[4]=vec4(shaderlodG09Mismatch>0.0?2.0:1.0,shaderlodG09First,shaderlodG09Saved,-1.0);
    gl_FragData[5]=vec4(newCoord,shaderlodG09CandidateCoord);
    gl_FragData[6]=vec4(surfaceDepth,shaderlodG09CandidateDepth,shaderlodG09NativeShadow,shaderlodG09CandidateShadow);
    gl_FragData[7]=vec4(dcdx,dcdy);''');files[reuse.TERRAIN]=t.encode()
 final=files['shaders/program/final.glsl'].decode();final=once(final,'const int colortex8Format = R16F;','const int colortex8Format = RGBA32F;')
 # RT9 has a native declaration, retain it in fallback only.
 matches=re.findall(r'^const int colortex9Format = [^;]+;[^\n]*$',final,flags=re.M);assert len(matches)==1
 final=final.replace(matches[0],'#if !('+gate+')\n'+matches[0]+'\n#endif')
 anchor='const bool colortex8Clear = true;';final=once(final,anchor,anchor+'\n'+''.join(f'const int colortex{x}Format = RGBA32F;\nconst bool colortex{x}Clear = true;\nconst vec4 colortex{x}ClearColor=vec4(0.0,0.0,0.0,0.0);\n' for x in range(9,12)));files['shaders/program/final.glsl']=final.encode()
 files[reuse.PROPS]+=('\nuniform.int.shaderlodCausalInitialReuse='+('1' if control in ['initial','shadow'] else '0')+'\nuniform.int.shaderlodCausalShadowReuse='+('1' if control=='shadow' else '0')+'\n'+''.join(f'blend.gbuffers_terrain.colortex{x}=off\n' for x in range(9,12))).encode()
 marker=dict(diagnostic_only=True,kind='G09',native=reuse.NATIVE,control=control,last_target=11,targets={'8':'flag0clear/1equal/2mismatch,firstfinalstage,actualomissions,-1','9':'nativePOMcoord.xy,candidatePOMcoord.xy','10':'nativeDepth,candidateDepth,nativeShadow,candidateShadow','11':'actualexplicitdcdx.xy,dcdy.xy'},limits='Thin parity kernel preserves native explicit gradient lookups/recurrence; no arrays or intermediate trace additions. Clone literal native arithmetic, forcedfalse actualuniform initial/shadow disabled and no fadedreturn. Finalfirststage is not first internal operation; MRT can affect compilation; no precision or speed claim.')
 files['SHADERLOD_CAUSAL_DIAGNOSTIC.json']=(json.dumps(marker,indent=2)+'\n').encode();files['causal-source/build_g09_causal_inputs.py']=Path(__file__).read_bytes();output.write_bytes(archive_bytes(files));Path(str(output)+'.txt').write_bytes(options.read_bytes()+f'\n{reuse.OPTION}=3\n'.encode());return dict(marker,artifact=str(output),sha256=sha(output.read_bytes()))
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__)
 for n in ['original','options','output']:p.add_argument('--'+n,required=True,type=Path)
 p.add_argument('--control',choices=['clone','forcedfalse','initial','shadow'],default='shadow');print(json.dumps(build(**vars(p.parse_args())),indent=2))

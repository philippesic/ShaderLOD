#!/usr/bin/env python3
"""Untimed BSL POM first divergent explicit-gradient state, native RGB retained."""
from pathlib import Path
import re,json,argparse
import build_g09_native_reuse as reuse
import build_g09_native_reuse_parity as parity
from build_makeup_unused_sky import archive_bytes,once,sha
GATE=parity.GATE+' && PARALLAX_QUALITY == 128 && SELF_SHADOW_QUALITY == 8'
HEADER=f'''#if {GATE}
uniform int shaderlodCausalInitialReuse;
uniform int shaderlodCausalShadowReuse;
float shaderlodCausalFirst=-1.0;
float shaderlodCausalSkipped=0.0;
float shaderlodCausalFirstSkip=-1.0;
float shaderlodCausalN=0.0,shaderlodCausalC=0.0;
vec2 shaderlodCausalUvN=vec2(0.0),shaderlodCausalUvC=vec2(0.0),shaderlodShadowUvN=vec2(0.0),shaderlodShadowUvC=vec2(0.0);
vec2 shaderlodInitialUvN=vec2(0.0),shaderlodInitialUvC=vec2(0.0),shaderlodLastUvN=vec2(0.0),shaderlodLastUvC=vec2(0.0);
vec4 shaderlodInitialRgbaN=vec4(0.0),shaderlodInitialRgbaC=vec4(0.0);
int shaderlodReadsN=0,shaderlodReadsC=0;
vec4 shaderlodPomN[129];
vec4 shaderlodShadowN[8];
float shaderlodShadowAccumN[8];
void shaderlodRecord(float code,vec4 n,vec4 c) {{
    for(int field=0;field<4;field++) {{
        if(shaderlodCausalFirst<0.0 && !(n[field]==c[field])) {{
            shaderlodCausalFirst=code*4.0+float(field);shaderlodCausalN=n[field];shaderlodCausalC=c[field];
            shaderlodCausalUvN=n.xy;shaderlodCausalUvC=c.xy;
            if(code<2.0) {{shaderlodCausalUvN=shaderlodInitialUvN;shaderlodCausalUvC=shaderlodInitialUvC;}}
            if(code>=300.0 && code<400.0 || code==902.0) {{shaderlodCausalUvN=shaderlodShadowUvN;shaderlodCausalUvC=shaderlodShadowUvC;}}
        }}
    }}
}}
#endif
'''
def instrument(source,reference):
 tag='N' if reference else 'C';prefix='shaderlodG09Reference' if reference else ''
 # Actual query expression and explicit gradients retained, no replacement LOD.
 anchor='\treturn texture2DGradARB(normals, coord, dcdx, dcdy);'
 code=f'''    vec4 shaderlodRead=texture2DGradARB(normals, coord, dcdx, dcdy);
    shaderlodLastUv{tag}=coord;
    if(shaderlodReads{tag}==0) {{shaderlodInitialUv{tag}=coord;shaderlodInitialRgba{tag}=shaderlodRead;}}
    shaderlodReads{tag}++;
    return shaderlodRead;'''
 source=once(source,anchor,code)
 anchor='    vec2 coord = vTexCoord.st;'
 setup='    for(int t=0;t<129;t++) shaderlodPomN[t]=vec4(-1234567.0);\n' if reference else ''
 source=once(source,anchor,setup+anchor)
 if not reference:
  # All reuse controls stay real uniforms, retaining conditional control flow
  # even for the forcedfalse archive. The final native fade comparator remains.
  source=once(source,'    float depth = shaderlodG09Initial.a;', '''    float depth;
    if(shaderlodCausalInitialReuse!=0) {
        depth=shaderlodG09Initial.a;shaderlodCausalSkipped+=1.0;
        if(shaderlodCausalFirstSkip<0.0)shaderlodCausalFirstSkip=2.0;
    } else depth=ReadNormal(coord).a;''')
  source=once(source,'if (parallaxFade >= 1.0) {  return texCoord; }','if (false) { return texCoord; }')
  # Do not test or time the already unprofitable faded-return mechanism here.
  source=once(source,'    if (normalCheck < -1.999) return texCoord;','    if (parallaxFade >= 1.0 || normalCheck < -1.999) return texCoord;')
  source=once(source,'if (shaderlodG09Reuse) { offsetHeight = texHeightOffset;  }', '''if (shaderlodCausalShadowReuse!=0 && shaderlodG09Reuse) {
            offsetHeight=texHeightOffset;shaderlodCausalSkipped+=1.0;
            if(shaderlodCausalFirstSkip<0.0)shaderlodCausalFirstSkip=200.0+float(i);
        }''')
 # Initial normal query inputs/outputs and initial depth precede any loop reuse.
 anchor='    float normalCheck = normalMap.x + normalMap.y;'
 if not reference:source=once(source,anchor,anchor+'''
    shaderlodRecord(0.0,vec4(shaderlodInitialUvN,0.0,0.0),vec4(shaderlodInitialUvC,0.0,0.0));
    shaderlodRecord(1.0,shaderlodInitialRgbaN,shaderlodInitialRgbaC);''')
 # Anchor after the conditional initial depth declaration, just before loop.
 anchor='    for(int i = 0; i < PARALLAX_QUALITY; i++){'
 state=f'vec4(shaderlodLastUv{tag},depth,currentStep)'
 initial=f'    shaderlodPomN[0]={state};\n' if reference else f'    shaderlodRecord(2.0,shaderlodPomN[0],{state});\n'
 source=once(source,anchor,initial+anchor)
 anchor='        currentStep -= sampleStep;'
 step=f'        shaderlodPomN[i+1]={state};' if reference else f'        shaderlodRecord(100.0+float(i),shaderlodPomN[i+1],{state});'
 source=once(source,anchor,anchor+'\n'+step)
 anchor='    float parallaxshadow = 1.0;'
 if reference:source=once(source,anchor,'    for(int t=0;t<8;t++){shaderlodShadowN[t]=vec4(-1234567.0);shaderlodShadowAccumN[t]=-1234567.0;}\n'+anchor)
 anchor='        parallaxshadow = min(parallaxshadow, sampleShadow);'
 if reference:extra='        shaderlodShadowN[i]=vec4(parallaxCoord,offsetHeight,currentHeight);shaderlodShadowAccumN[i]=parallaxshadow;'
 else:extra='''        shaderlodRecord(200.0+float(i),shaderlodShadowN[i],vec4(parallaxCoord,offsetHeight,currentHeight));
        shaderlodRecord(300.0+float(i),vec4(shaderlodShadowAccumN[i],0.0,0.0,0.0),vec4(parallaxshadow,0.0,0.0,0.0));'''
 source=once(source,anchor,anchor+'\n'+f'        shaderlodShadowUv{tag}=parallaxCoord;\n'+extra)
 return source

def build(original,options,output,control='initial'):
 output=Path(output);original=Path(original);options=Path(options)
 if output.exists() or Path(str(output)+'.txt').exists():raise ValueError('Fresh output required')
 native=reuse.read_archive(original);files=reuse.patch(native)
 ref=native[reuse.KERNEL].decode().replace('\r\n','\n');candidate=ref if control=='clone' else files[reuse.KERNEL].decode().replace('\r\n','\n')
 ref=instrument(ref,True)
 for name in ['ReadNormal','GetParallaxCoord','GetParallaxShadow']:ref=re.sub(r'\b'+name+r'\b','shaderlodG09Reference'+name,ref)
 # Clone follows the same trace storage but native source arithmetic/control.
 if control=='clone':
  cand=instrument(candidate,True)
  cand=cand.replace('    for(int t=0;t<129;t++) shaderlodPomN[t]=vec4(-1234567.0);\n','')
  cand=cand.replace('    for(int t=0;t<8;t++){shaderlodShadowN[t]=vec4(-1234567.0);shaderlodShadowAccumN[t]=-1234567.0;}\n','')
  cand=cand.replace('shaderlodInitialUvN','shaderlodInitialUvC').replace('shaderlodInitialRgbaN','shaderlodInitialRgbaC').replace('shaderlodLastUvN','shaderlodLastUvC').replace('shaderlodReadsN','shaderlodReadsC').replace('shaderlodShadowUvN=parallaxCoord','shaderlodShadowUvC=parallaxCoord')
  cand=once(cand,'    float normalCheck = normalMap.x + normalMap.y;','''    float normalCheck = normalMap.x + normalMap.y;
    shaderlodRecord(0.0,vec4(shaderlodInitialUvN,0.0,0.0),vec4(shaderlodInitialUvC,0.0,0.0));
    shaderlodRecord(1.0,shaderlodInitialRgbaN,shaderlodInitialRgbaC);''')
  cand=once(cand,'    shaderlodPomN[0]=vec4(shaderlodLastUvC,depth,currentStep);','    shaderlodRecord(2.0,shaderlodPomN[0],vec4(shaderlodLastUvC,depth,currentStep));')
  cand=once(cand,'        shaderlodPomN[i+1]=vec4(shaderlodLastUvC,depth,currentStep);','        shaderlodRecord(100.0+float(i),shaderlodPomN[i+1],vec4(shaderlodLastUvC,depth,currentStep));')
  cand=once(cand,'        shaderlodShadowN[i]=vec4(parallaxCoord,offsetHeight,currentHeight);shaderlodShadowAccumN[i]=parallaxshadow;','''        shaderlodRecord(200.0+float(i),shaderlodShadowN[i],vec4(parallaxCoord,offsetHeight,currentHeight));
        shaderlodRecord(300.0+float(i),vec4(shaderlodShadowAccumN[i],0.0,0.0,0.0),vec4(parallaxshadow,0.0,0.0,0.0));''')
 else:cand=instrument(candidate,False)
 files[reuse.KERNEL]=('#if '+GATE+'\n'+HEADER+'\n'+ref+'\n'+cand+'\n#else\n'+native[reuse.KERNEL].decode().replace('\r\n','\n')+'\n#endif\n').encode()
 terrain=files[reuse.TERRAIN].decode().replace('\r\n','\n')
 terrain=once(terrain,'\tfloat surfaceDepth = 1.0;','''\tfloat surfaceDepth = 1.0;
    #if GATE
    vec2 shaderlodCandidateCoord=newCoord;
    float shaderlodCandidateDepth=1.0;
    float shaderlodNativeShadow=1.0,shaderlodCandidateShadow=1.0;
    float shaderlodMismatch=0.0;
    #endif'''.replace('GATE',GATE))
 terrain=once(terrain,'\t\tnewCoord = GetParallaxCoord(texCoord, parallaxFade, surfaceDepth);','''        #if GATE
        newCoord=shaderlodG09ReferenceGetParallaxCoord(texCoord,parallaxFade,surfaceDepth);
        shaderlodCandidateCoord=GetParallaxCoord(texCoord,parallaxFade,shaderlodCandidateDepth);
        shaderlodRecord(900.0,vec4(newCoord,surfaceDepth,0.0),vec4(shaderlodCandidateCoord,shaderlodCandidateDepth,0.0));
        if(any(notEqual(newCoord,shaderlodCandidateCoord)) || surfaceDepth!=shaderlodCandidateDepth)shaderlodMismatch=1.0;
        #else
        newCoord=GetParallaxCoord(texCoord,parallaxFade,surfaceDepth);
        #endif'''.replace('GATE',GATE))
 shadow='''\t\t\tparallaxShadow = GetParallaxShadow(surfaceDepth, parallaxFade, newCoord, lightVec,
\t\t\t\t\t\t\t\t\t\t\t   tbnMatrix);'''
 terrain=once(terrain,shadow,'''        #if GATE
            parallaxShadow=shaderlodG09ReferenceGetParallaxShadow(surfaceDepth,parallaxFade,newCoord,lightVec,tbnMatrix);
            shaderlodNativeShadow=parallaxShadow;
            shaderlodCandidateShadow=GetParallaxShadow(shaderlodCandidateDepth,parallaxFade,shaderlodCandidateCoord,lightVec,tbnMatrix);
            shaderlodRecord(902.0,vec4(parallaxShadow,0.0,0.0,0.0),vec4(shaderlodCandidateShadow,0.0,0.0,0.0));
            if(parallaxShadow!=shaderlodCandidateShadow)shaderlodMismatch=1.0;
        #else
'''.replace('GATE',GATE)+shadow+'\n        #endif')
 boundary='\n}\n\n#endif\n\n//Vertex Shader'
 diagnostic_output='''
    #if GATE
    /* RENDERTARGETS: 0,3,6,7,8,9,10,11 */
    gl_FragData[4]=vec4(shaderlodMismatch>0.0?2.0:1.0,shaderlodCausalFirst,shaderlodCausalSkipped,shaderlodCausalFirstSkip);
    gl_FragData[5]=vec4(shaderlodCausalN,shaderlodCausalC,shaderlodCausalUvN);
    gl_FragData[6]=vec4(shaderlodCausalUvC,surfaceDepth,shaderlodCandidateDepth);
    gl_FragData[7]=vec4(dcdx,dcdy);
    #endif
'''.replace('GATE',GATE)
 terrain=once(terrain,boundary,diagnostic_output+boundary);files[reuse.TERRAIN]=terrain.encode()
 final=files['shaders/program/final.glsl'].decode().replace('\r\n','\n')
 original_formats=[]
 for target in range(8,12):
  pattern=r'^const int colortex'+str(target)+r'Format = [^;]+;[^\n]*$'
  matches=re.findall(pattern,final,flags=re.M)
  if len(matches)>1 or (target<10 and len(matches)!=1):raise ValueError('Exact native format missing: '+str(target))
  original_formats.extend(matches);final=re.sub(pattern,'',final,flags=re.M)
 formats='\n#if '+GATE+'\n/*\n'+''.join(f'const int colortex{t}Format = RGBA32F;\nconst bool colortex{t}Clear = true;\nconst vec4 colortex{t}ClearColor=vec4(0.0,0.0,0.0,0.0);\n' for t in range(8,12))+'*/\n#else\n/*\n'+'\n'.join(original_formats)+'\n*/\n#endif\n'
 close='const int colortex16Format = RGBA16F;\n*/';final=once(final,close,close+formats);files['shaders/program/final.glsl']=final.encode()
 properties='\nuniform.int.shaderlodCausalInitialReuse='+('1' if control in ['initial','shadow'] else '0')+'\n'
 properties+='uniform.int.shaderlodCausalShadowReuse='+('1' if control=='shadow' else '0')+'\n'
 properties+=''.join(f'blend.gbuffers_terrain.colortex{t}=off\n' for t in range(8,12));files[reuse.PROPS]+=properties.encode()
 marker={'kind':'G09','native':reuse.NATIVE,'diagnostic_only':True,'control':control,'last_target':11,
 'fields':'firstCode=stage*4+component; stage0 initial actualqueryUV,1 initial normalRGBA,2 initial POMdepth/step tuple,100+i POM queryUV/depth/currentStep afteriteration,200+i shadow queryUV/height/currentHeight,300+i shadowaccumulation,900 finalPOMcoord/depth,902 finalshadow. First common float mismatch recorded in execution/source order. -1 none.',
 'targets':{'8':'flag0clear/1outputequal/2coord-depth-shadowfail,firstCode,actualsavedexpressions,firstskipstage','9':'firstNativeScalar,firstCandidateScalar,firstNativeQueryUV','10':'firstCandidateQueryUV,nativeSurfaceDepth,candidateSurfaceDepth','11':'actualexplicitdcdx.xy,dcdy.xy shared unchanged by both functions'},
 'limits':'Native reference coordinate/depth/shadow feed nativeRGB. All explicit texture2DGradARB gradients and original recurrence/termination retained. Tracearrays/globalstores can change compiler optimization; compare clone/forcedfalse and oldthinwitness. No scalar count as physical transactions, no speed or harmlessprecision inference. Only initialRGBA/i1shadow reuse gated by realuniforms; fadedreturn excluded here based priornegativeeconomics.'}
 files['SHADERLOD_CAUSAL_DIAGNOSTIC.json']=(json.dumps(marker,indent=2)+'\n').encode();files['causal-source/build_g09_causal_trace.py']=Path(__file__).read_bytes();output.parent.mkdir(parents=True,exist_ok=True);output.write_bytes(archive_bytes(files));Path(str(output)+'.txt').write_bytes(options.read_bytes()+f'\n{reuse.OPTION}=3\n'.encode());return {'artifact':str(output),'sha256':sha(output.read_bytes()),**marker}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__)
 for n in ['original','options','output']:p.add_argument('--'+n,required=True,type=Path)
 p.add_argument('--control',choices=['clone','forcedfalse','initial','shadow'],default='initial');print(json.dumps(build(**vars(p.parse_args())),indent=2))

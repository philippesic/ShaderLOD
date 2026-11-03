#!/usr/bin/env python3
"""Actual cloud first-state witness, one selected iteration without arrays."""
from pathlib import Path
import argparse,json,tempfile,zipfile
import build_makeup_cloud_four_groups as group
from build_makeup_unused_sky import archive_bytes,once,sha

def instrument(s,tag):
 anchor='            if (current_opacity > 0.0) {'
 s=once(s,anchor,f'            if(i==shaderlodCloudStep)shaderlodCloudPre{tag}=vec4(intersection_pos,current_value);\n'+anchor)
 anchor='            intersection_pos += increment;'
 return once(s,anchor,f'            if(i==shaderlodCloudStep)shaderlodCloudPost{tag}=vec3(current_opacity,cloud_value,density);\n'+anchor)

def build(original,options,output,control='forcedfalse'):
 output=Path(output)
 if output.exists() or Path(str(output)+'.txt').exists():raise ValueError('Fresh output required')
 with tempfile.TemporaryDirectory() as t:
  base=Path(t)/'cloud.zip';group.build(original,options,base,control)
  with zipfile.ZipFile(base) as z:files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
 n=group.m.CLOUD;s=files[n].decode();start=s.index('vec3 shaderlodG08GroupReferenceCloud(')
 # Candidate has both selected native fallback and active loop: instrument only
 # the active loop, whose original query/control/arithmetic order is retained.
 cand=s[:start];ref=s[start:]
 if control=='clone':cand=instrument(cand,'C')
 else:
  a=cand.index('        bool shaderlodG08GroupEmpty=false;');z=cand.index('\n#else\n',a)
  active=cand[a:z]
  # The native update is outside the optional group body, so capture post state
  # inside that body before its closing brace while locals are in scope.
  active=once(active,'            if (current_opacity > 0.0) {','            if(i==shaderlodCloudStep)shaderlodCloudPreC=vec4(intersection_pos,current_value);\n            if (current_opacity > 0.0) {')
  active=once(active,'            }\n            intersection_pos += increment;','            if(i==shaderlodCloudStep)shaderlodCloudPostC=vec3(current_opacity,cloud_value,density);\n            }\n            intersection_pos += increment;')
  cand=cand[:a]+active+cand[z:]
 ref=instrument(ref,'N')
 header='uniform int shaderlodCloudStep;\nvec4 shaderlodCloudPreN=vec4(-12345.0),shaderlodCloudPreC=vec4(-12345.0);\nvec3 shaderlodCloudPostN=vec3(-12345.0),shaderlodCloudPostC=vec3(-12345.0);\nvec3 shaderlodCloudRgbN=vec3(0.0),shaderlodCloudRgbC=vec3(0.0);\n'
 files[n]=(header+cand+ref).encode()
 n='shaders/common/deferred_fragment.glsl';s=files[n].decode()
 s=once(s,'const int colortex8Format = R16F;','const int colortex8Format = RGBA32F;')
 s=once(s,'const bool colortex8Clear = true;','const bool colortex8Clear = true;\n'+''.join(f'const int colortex{t}Format=RGBA32F;\nconst bool colortex{t}Clear=true;\nconst vec4 colortex{t}ClearColor=vec4(0.0);\n' for t in range(9,14)))
 s=once(s,'/* DRAWBUFFERS:148 */','/* RENDERTARGETS: 1,4,8,9,10,11,12,13 */')
 s=once(s,'                blockColor.rgb=shaderlodGroupNative;','                shaderlodCloudRgbN=shaderlodGroupNative;shaderlodCloudRgbC=shaderlodGroupCandidate;\n                blockColor.rgb=shaderlodGroupNative;')
 a=s.index('    gl_FragData[2]=vec4(shaderlodG08GroupCalled');z=s.index(';',a)+1
 s=s[:a]+'''    float shaderlodCloudFirst=-1.0;
    if(shaderlodCloudPreC.x!=-12345.0) {
      for(int f=0;f<4;f++)if(!(shaderlodCloudPreN[f]==shaderlodCloudPreC[f])&&shaderlodCloudFirst<0.0)shaderlodCloudFirst=float(f);
      for(int f=0;f<3;f++)if(!(shaderlodCloudPostN[f]==shaderlodCloudPostC[f])&&shaderlodCloudFirst<0.0)shaderlodCloudFirst=4.0+float(f);
    }
    gl_FragData[2]=vec4(shaderlodG08GroupCalled>0.0?(shaderlodG08GroupMismatch>0.0?2.0:1.0):0.0,shaderlodCloudFirst,shaderlodG08GroupPotential,float(shaderlodCloudStep));
    gl_FragData[3]=shaderlodCloudPreN;
    gl_FragData[4]=shaderlodCloudPreC;
    gl_FragData[5]=vec4(shaderlodCloudPostN,shaderlodCloudPostC.x);
    gl_FragData[6]=vec4(shaderlodCloudPostC.yz,shaderlodCloudRgbN.r,shaderlodCloudRgbC.r);
    gl_FragData[7]=vec4(shaderlodCloudRgbN.gb,shaderlodCloudRgbC.gb);'''+s[z:];files[n]=s.encode()
 files['shaders/shaders.properties']+=b'\nuniform.int.shaderlodCloudStep=(frameCounter / 8) % 16\n'+''.join(f'\nblend.deferred.colortex{t}=off\n' for t in range(9,14)).encode()
 marker=dict(diagnostic_only=True,kind='G08',native=group.NATIVE,control=control,last_target=13,selected_step_count=16,targets={'8':'finalRGBflag,firstlocalstatefield,potentialEmptyGroups,selectedStep','9':'nativePositionXYZ,currentValue','10':'candidatePositionXYZ,currentValue','11':'nativeOpacity,cloudValue,density,candidateOpacity','12':'candidateCloudValue,density,nativeR,candidateR','13':'nativeGB,candidateGB'},limits='One selected iteration, no arrays/recurrence changes. Unexecuted/skipped or beyondsamples state sentinel; localfield compares only actually executed candidate step. Native implicit queries retained; witness may change compiler. NativeRGB feeds renderer. Forcedfalse first divergence precedes actual skipping; no precision/derivative explanation or quality/speed clearance.')
 files['SHADERLOD_CAUSAL_DIAGNOSTIC.json']=(json.dumps(marker,indent=2)+'\n').encode();files['causal-source/build_g08_selected_step.py']=Path(__file__).read_bytes();output.write_bytes(archive_bytes(files));Path(str(output)+'.txt').write_bytes(Path(options).read_bytes()+f'\n{group.OPTION}=1\n'.encode());return dict(marker,artifact=str(output),sha256=sha(output.read_bytes()))

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__)
 for n in ['original','options','output']:p.add_argument('--'+n,required=True,type=Path)
 p.add_argument('--control',choices=['clone','forcedfalse','skip'],default='forcedfalse');print(json.dumps(build(**vars(p.parse_args())),indent=2))

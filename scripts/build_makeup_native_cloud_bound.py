"""Finite native100 single-layer four-step cloud tail bound and independent audit."""
import json
from pathlib import Path
import build_makeup_directional_cloud_bounds as m
from build_makeup_unused_sky import read_archive,archive_bytes,once,sha
from build_cloud_volume_makeup_saturation import decoded_cloud_rgba
import dev_client as dev

ROOT=Path(__file__).resolve().parents[1]
MARKER='SHADERLOD_G08_NATIVE100_BOUND.json'
CONFIG='shaders/lib/config.glsl'
CALLER='shaders/common/deferred_fragment.glsl'
LEAN='ShaderLOD-G08-native100-bound.zip'
WORK='ShaderLOD-G08-native100-bound-work.zip'


def validate(original,options):
    expected=json.loads((ROOT/'src/main/resources/assets/shaderlod/makeupquad/native100.json').read_text())
    if sha(Path(original).read_bytes())!=m.NATIVE or dev.properties(Path(options).read_text())!=expected:
        raise ValueError('Exact original MakeUp9.5g/native100 required')
    return expected


def work_files(base,native,*,layout="loop"):
    files=dict(base);gate=m.NATIVE100_GATE;guard=f'({gate}) && {m.DEBUG} == 1'
    cloud=files[m.CLOUD].decode()
    cloud=once(cloud,'float shaderlodG08SavedSteps = 0.0;','float shaderlodG08SavedSteps = 0.0;\nfloat shaderlodG08OpacityErrors=0.0;\nfloat shaderlodG08Difference=0.0;\nconst float shaderlodG08DirectionalSchema=1023.0;')
    reference=native[m.CLOUD].decode().replace('vec3 get_cloud(','vec3 shaderlodG08NativeReferenceCloud(',1)
    omitted='current_value <= 0.0' if layout=='alu' else 'shaderlodG08SavedSteps > 0.0 && float(i) >= float(samples)-shaderlodG08SavedSteps'
    reference=once(reference,'            if (current_opacity > 0.0) {',f'''            if (({omitted}) && !(current_opacity == 0.0)) shaderlodG08OpacityErrors += 1.0;
            if (current_opacity > 0.0) {{''')
    cloud+='\n#if '+guard+'\n'+reference+'\n#endif\n';files[m.CLOUD]=cloud.encode()
    call='get_cloud(eyeDirection, blockColor.rgb, bright, dither, cameraPosition, CLOUD_STEPS_AVG, umbral, cloudColor, darkCloudColor)'
    anchor='                blockColor.rgb = '+call+';'
    candidate=call.replace('blockColor.rgb','shaderlodG08Input')
    reference_call=candidate.replace('get_cloud(','shaderlodG08NativeReferenceCloud(',1)
    replacement=f'''                #if {guard}
                vec3 shaderlodG08Input=blockColor.rgb;
                vec3 shaderlodG08Candidate={candidate};
                vec3 shaderlodG08Reference={reference_call};
                shaderlodG08Difference=any(notEqual(shaderlodG08Candidate,shaderlodG08Reference))?1.0:0.0;
                if(!all(lessThan(abs(shaderlodG08Candidate),vec3(65536.0))) || !all(lessThan(abs(shaderlodG08Reference),vec3(65536.0))))shaderlodG08Difference+=2.0;
                blockColor.rgb=shaderlodG08Reference;
                #else
'''+anchor+'\n                #endif'
    caller=once(files[CALLER].decode(),anchor,replacement)
    fmt='''/*
const int colortex8Format = RGBA16F;
const bool colortex8Clear = true;
const vec4 colortex8ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
*/'''
    caller=once(caller,'#include "/lib/config.glsl"','#include "/lib/config.glsl"\n#if '+guard+'\n'+fmt+'\n#endif')
    caller=once(caller,'/* DRAWBUFFERS:14 */','#if '+guard+'\n    /* DRAWBUFFERS:148 */\n    #else\n    /* DRAWBUFFERS:14 */\n    #endif')
    caller=once(caller,'    gl_FragData[1] = blockColor;','    gl_FragData[1] = blockColor;\n#if '+guard+'\n    gl_FragData[2]=vec4(shaderlodG08SavedSteps,shaderlodG08OpacityErrors,shaderlodG08Difference,shaderlodG08DirectionalSchema);\n#endif')
    files[CALLER]=caller.encode();files['shaders/shaders.properties']+=b'\nblend.deferred.colortex8=off\n'
    return files


def build_pair(original,options,directory,*,layout="loop"):
    if layout not in ("loop","split","alu"):raise ValueError("Unknown bound layout")
    expected=validate(original,options);directory=Path(directory)
    native=read_archive(Path(original));tail=0 if layout=="alu" else 4
    base=m.patch(native,tail_steps=tail,native_queries=True,native100=True,split_tail=layout=="split",zero_guard=layout=="alu")
    if layout=="alu":base[m.CLOUD]=(f'#if ({m.NATIVE100_GATE}) && {m.OPTION} == 2\nconst int shaderlodG08NativeAluGuardSchema=1023;\n#endif\n'.encode()+base[m.CLOUD])
    pixels=decoded_cloud_rgba(native['shaders/textures/clouds_natural_512_R_8bit.png']);atlas=[] if layout=="alu" else m.bounds(pixels)
    rgba=bytes(v for red in atlas for v in (red,53,197,255))
    metadata={'original_sha256':m.NATIVE,'native100':True,'layout':layout,'tail_steps':tail,'native_queries':True,
              'cloud_red_sha256':sha(pixels[::4]),'atlas_rgba_sha256':sha(rgba) if layout!='alu' else None,'atlas_png_sha256':sha(base[m.ATLAS]) if layout!='alu' else None,
              'native_options':expected,'gate':m.NATIVE100_GATE,'producer_sha256':sha(Path(__file__).read_bytes()),
              'directional_producer_sha256':sha(Path(m.__file__).read_bytes()),
              'work_unit':'guarded native ALU body; all native texture queries retained' if layout=='alu' else 'omitted native sample expression',
              'scope':('Private native100 single-layer nonpositive-density ALU guard; all ten native implicit texture queries, positions, threshold and AA retained; no atlas.' if layout=='alu' else 'Private LinuxAsahi/Iris10812/MC12101/native100CLOUDS1 ten original samples, one conservative directional atlas query at final4-step checkpoint; native implicit cloud queries, positions/threshold/AA preserved.')}
    results={};directory.mkdir(parents=True,exist_ok=True)
    for name,diagnostic in ((LEAN,False),(WORK,True)):
        output=directory/name
        if output.exists() or Path(str(output)+'.txt').exists():raise ValueError('Fresh owned source outputs required')
        files=work_files(base,native,layout=layout) if diagnostic else dict(base)
        notice=metadata|{'diagnostic_only':diagnostic,'schema':1023 if diagnostic else None,
                         'encoding':('RGBA16F(guarded native ALU iterations0..10, nonzero-or-NaN native opacity count at guarded positions, numericRGBdifference+2nonfiniteflag, schema1023); all native texture queries retained; displayedRGBnative' if layout=='alu' else 'RGBA16F(saved native sample expressions0..4, nonzero-or-NaN native opacity count at omitted positions, numericRGBdifference+2nonfiniteflag, schema1023); displayedRGBnative') if diagnostic else None}
        files[MARKER]=(json.dumps(notice,indent=2)+'\n').encode()
        files['SHADERLOD_G08_DIRECTIONAL_NOTICE.txt']=(json.dumps(notice,indent=2)+'\n').encode()
        output.write_bytes(archive_bytes(files));Path(str(output)+'.txt').write_bytes(Path(options).read_bytes()+f'\n{m.OPTION}=2\n{m.DEBUG}={1 if diagnostic else 0}\n'.encode())
        results['work' if diagnostic else 'lean']={'name':name,'sha256':sha(output.read_bytes())}
    return metadata|{'artifacts':results}

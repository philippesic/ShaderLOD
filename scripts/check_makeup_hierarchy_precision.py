#!/usr/bin/env python3
"""Untimed Asahi G05 certificate probe; no game/profile or product changes.

Tests the proposed uncorrected RG16F scratch replacement against the existing
RG32F min-bound contract. This is not a Minecraft quality or speed test.
"""
import argparse
import ctypes as C
import ctypes.util
import hashlib
import json
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[1]
NATIVE = '0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8'


def f32(x):
    return struct.unpack('f', struct.pack('f', x))[0]


def witness():
    # A far but non-sky depth, exactly representable in the authentic D32F input.
    native = f32(0.9993)
    half = struct.unpack('e', struct.pack('e', native))[0]
    march = f32((native + half) / 2)
    assert 0 < native < march < half < 1
    return native, march


def run(original, probe=None):
    raw = original.read_bytes()
    if hashlib.sha256(raw).hexdigest() != NATIVE:
        raise ValueError('Authentic pinned MakeUp9.5g required')
    import zipfile
    with zipfile.ZipFile(original) as z:
        water = z.read('shaders/lib/water.glsl').decode()
        license_names = [n for n in z.namelist() if 'license' in n.lower()]
    constructor = ROOT/'src/devClient/java/com/pp/shaderlod/experiment/hierarchy/DepthHierarchyR50.java'
    consumer = ROOT/'scripts/build_makeup_depth_hierarchy_consumer.py'
    # These checks bind the counterexample to the current lower-bound consumer,
    # full-precision construction and authentic negative-transition comparator.
    for text, token in [(constructor.read_text(), 'GL30.GL_RG32F'),
                        (consumer.read_text(), 'shaderlodCachedLower>screenMarchPos.z'),
                        (water, 'depthDifference = screenDepth - screenMarchPos.z;'),
                        (water, 'searchFlag == false && depthDifference < 0.0 && hiddenFlag == false')]:
        if token not in text:
            raise ValueError('Current source seam changed: ' + token)
    egl = C.CDLL(ctypes.util.find_library('EGL'))
    gl = C.CDLL(ctypes.util.find_library('GL'))
    def bind(lib, name, result, *args):
        fn = getattr(lib, name); fn.restype = result; fn.argtypes = args; return fn
    ptr, I, U = C.c_void_p, C.c_int, C.c_uint
    display = bind(egl, 'eglGetPlatformDisplay', ptr, U, ptr, C.POINTER(I))(0x31DD, None, None)
    major, minor = I(), I()
    if not bind(egl, 'eglInitialize', U, ptr, C.POINTER(I), C.POINTER(I))(display, C.byref(major), C.byref(minor)):
        raise RuntimeError('Surfaceless EGL unavailable')
    context = surface = None
    texture = U(); framebuffer = U(); vao = U(); program = 0; shaders = []
    try:
        if not bind(egl, 'eglBindAPI', U, U)(0x30A2): raise RuntimeError('OpenGL EGL API unavailable')
        attributes = (I*7)(0x3033, 1, 0x3040, 8, 0x3024, 8, 0x3038)
        config, count = ptr(), I()
        if not bind(egl, 'eglChooseConfig', U, ptr, C.POINTER(I), C.POINTER(ptr), I, C.POINTER(I))(display, attributes, C.byref(config), 1, C.byref(count)) or count.value != 1:
            raise RuntimeError('OpenGL pbuffer config unavailable')
        surface = bind(egl, 'eglCreatePbufferSurface', ptr, ptr, ptr, C.POINTER(I))(display, config, (I*5)(0x3057,1,0x3056,1,0x3038))
        context = bind(egl, 'eglCreateContext', ptr, ptr, ptr, ptr, C.POINTER(I))(display, config, None, (I*1)(0x3038))
        if not surface or not context or not bind(egl, 'eglMakeCurrent', U, ptr, ptr, ptr, ptr)(display, surface, surface, context):
            raise RuntimeError('EGL context failed')
        get_string = bind(gl, 'glGetString', C.c_char_p, U)
        identity = {k:get_string(v).decode() for k,v in [('vendor',0x1F00),('renderer',0x1F01),('version',0x1F02)]}
        if 'Apple M1' not in identity['renderer'] or 'asahi' not in identity['version']:
            raise RuntimeError('Asahi Apple M1 required: '+str(identity))
        if probe is not None:
            return probe(gl, bind, identity)
        bind(gl, 'glGenTextures', None, I, C.POINTER(U))(1, C.byref(texture))
        bind(gl, 'glBindTexture', None, U, U)(0x0DE1, texture.value)
        upload = bind(gl, 'glTexImage2D', None, U,I,I,I,I,I,U,U,ptr)
        read = bind(gl, 'glGetTexImage', None, U,I,U,U,ptr)
        error = bind(gl, 'glGetError', U)
        # Render-store conversion is the operation used by the hierarchy builder;
        # CPU texture uploads can use different conversion rounding on this driver.
        create_shader=bind(gl,'glCreateShader',U,U)
        for kind,source in [(0x8B31,b'#version 330 core\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.-1.,0.,1.);}'),
                            (0x8B30,b'#version 330 core\nuniform float nativeDepth;layout(location=0) out vec2 bounds;void main(){bounds=vec2(nativeDepth);}')]:
            shader=create_shader(kind);shaders.append(shader);code=C.c_char_p(source)
            bind(gl,'glShaderSource',None,U,I,C.POINTER(C.c_char_p),ptr)(shader,1,C.byref(code),None)
            bind(gl,'glCompileShader',None,U)(shader)
            okay=I();bind(gl,'glGetShaderiv',None,U,U,C.POINTER(I))(shader,0x8B81,C.byref(okay))
            if not okay.value:raise RuntimeError('Precision witness shader failed compilation')
        program=bind(gl,'glCreateProgram',U)()
        for shader in shaders:bind(gl,'glAttachShader',None,U,U)(program,shader)
        bind(gl,'glLinkProgram',None,U)(program)
        okay=I();bind(gl,'glGetProgramiv',None,U,U,C.POINTER(I))(program,0x8B82,C.byref(okay))
        if not okay.value:raise RuntimeError('Precision witness failed link')
        bind(gl,'glUseProgram',None,U)(program)
        bind(gl,'glGenFramebuffers',None,I,C.POINTER(U))(1,C.byref(framebuffer))
        bind(gl,'glBindFramebuffer',None,U,U)(0x8D40,framebuffer.value)
        bind(gl,'glGenVertexArrays',None,I,C.POINTER(U))(1,C.byref(vao))
        bind(gl,'glBindVertexArray',None,U)(vao.value)
        bind(gl,'glViewport',None,I,I,I,I)(0,0,1,1)
        native, march = witness()
        rows = []
        for name, format_ in [('RG32F',0x8230),('RG16F',0x822F)]:
            values = (C.c_float*2)(native,native); actual = (C.c_float*2)()
            upload(0x0DE1,0,format_,1,1,0,0x8227,0x1406,values)
            bind(gl,'glFramebufferTexture2D',None,U,U,U,U,I)(0x8D40,0x8CE0,0x0DE1,texture.value,0)
            if bind(gl,'glCheckFramebufferStatus',U,U)(0x8D40)!=0x8CD5:raise RuntimeError('Witness FBO incomplete')
            location=bind(gl,'glGetUniformLocation',I,U,C.c_char_p)(program,b'nativeDepth')
            bind(gl,'glUniform1f',None,I,C.c_float)(location,native)
            bind(gl,'glDrawArrays',None,U,I,I)(0x0004,0,3)
            read(0x0DE1,0,0x8227,0x1406,actual)
            if error()!=0: raise RuntimeError('OpenGL storage/readback error')
            lower = actual[0]
            rows.append(dict(format=name,native_depth=native,march_z=march,stored_lower=lower,
                stored_upper=actual[1],lower_is_conservative=lower<=native,
                native_difference=f32(native-march),certificate=lower>march,
                native_positive_miss=f32(native-march)>0))
        assert rows[0]['stored_lower']==native and not rows[0]['certificate']
        failed = rows[1]['certificate'] and not rows[1]['native_positive_miss']
        # Record contrary evidence; do not require the proposed failure to occur.
        narrowed=rows[1]
        positive_march=f32((native+narrowed['stored_lower'])/2)
        narrowed['lost_positive_certificate_example']=dict(march_z=positive_march,native_positive_miss=native>positive_march,narrowed_certificate=narrowed['stored_lower']>positive_march)
        narrowed['upper_is_conservative']=narrowed['stored_upper']>=native
        return dict(outcome='not-exact-minmax; lower-only-consumer-unqualified',identity=identity,rows=rows,
            false_positive_counterexample_reproduced=failed,native_ancestry_license_entries=license_names,
            source_pins={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [original,constructor,consumer,Path(__file__)]},
            scope='Actual Asahi compiled fragment render-store conversion/readback; CPU FP32 comparison matches current source predicate. Not actual Minecraft trace, scene coverage, linked GPU arithmetic, still/motion or performance evidence.',
            next='Prove lower-only conservative storage across required inputs and every scratch stage; collect actual same-fragment output/skip counts before deciding whether a live port and balanced timing are justified. Repair upper rounding if upper bounds are used. RG32F remains unchanged.')
    finally:
        if context:
            if program:bind(gl,'glDeleteProgram',None,U)(program)
            for shader in shaders:bind(gl,'glDeleteShader',None,U)(shader)
            if vao.value:bind(gl,'glDeleteVertexArrays',None,I,C.POINTER(U))(1,C.byref(vao))
            if framebuffer.value:bind(gl,'glDeleteFramebuffers',None,I,C.POINTER(U))(1,C.byref(framebuffer))
            if texture.value: bind(gl,'glDeleteTextures',None,I,C.POINTER(U))(1,C.byref(texture))
            bind(egl,'eglMakeCurrent',U,ptr,ptr,ptr,ptr)(display,None,None,None)
            bind(egl,'eglDestroyContext',U,ptr,ptr)(display,context)
        if surface: bind(egl,'eglDestroySurface',U,ptr,ptr)(display,surface)
        bind(egl,'eglTerminate',U,ptr)(display)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--original',required=True,type=Path)
    parser.add_argument('--output',required=True,type=Path)
    args=parser.parse_args()
    if args.output.exists(): parser.error('Fresh evidence output required')
    result=run(args.original.resolve())
    args.output.write_text(json.dumps(result,indent=2)+'\n')
    print(result['outcome'])

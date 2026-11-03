#!/usr/bin/env python3
"""Finite Asahi render-store stress of G05 lower-only normal-half truncation.

Universal correctness is a separate bit-level proof; this tests implementation.
No game, timing, or public activation is performed.
"""
import argparse
import ctypes as C
import hashlib
import json
import math
from pathlib import Path
import random
import struct
from check_makeup_hierarchy_precision import run, ROOT

LOWER_GLSL = '''float conservativeLower(float d) {
    uint b=floatBitsToUint(d);
    if(b>0x3f800000u || b<0x38800000u) return 0.0;
    return uintBitsToFloat(b & 0xffffe000u);
}
'''
# Positive IEEE32 ordering rejects negative, NaN, infinity, >1; +subnormal
# and small normals are zero. This also rejects -0 safely (returns +0).
def bits(f): return struct.unpack('<I',struct.pack('<f',f))[0]
def value(b): return struct.unpack('<f',struct.pack('<I',b))[0]
def lower_bits(b): return 0 if b>0x3f800000 or b<0x38800000 else b&0xffffe000

def probe(gl, bind, identity):
    I,U,P=C.c_int,C.c_uint,C.c_void_p
    # Both sides and midpoints of every normal-half boundary in [2^-14,1],
    # plus subnormals, FP32 boundary neighbors, invalids, and seeded full-bit input.
    inputs={0,1,0x007fffff,0x00800000,0x387fffff,0x38800000,0x3f800000,
            0x3f800001,0x7f800000,0xff800000,0x7fc00000,0x7f800001,0x80000000,0xbf800000,bits(.9993)}
    for b in range(0x38800000,0x3f800001,8192):
        inputs.update(x for x in (b-1,b,b+1,b+4095,b+4096,b+4097,b+8191) if x<=0x3f800001)
    rng=random.Random(20261031)
    inputs.update(rng.getrandbits(32) for _ in range(20000))
    data=sorted(inputs);w=513;h=(len(data)+w-1)//w;data += [bits(1.)]*(w*h-len(data))
    raw=(U*len(data))(*data)
    textures=[];fb=U();vao=U();program=0;shaders=[]
    gen=bind(gl,'glGenTextures',None,I,C.POINTER(U));texbind=bind(gl,'glBindTexture',None,U,U)
    upload=bind(gl,'glTexImage2D',None,U,I,I,I,I,I,U,U,P)
    attach=bind(gl,'glFramebufferTexture2D',None,U,U,U,U,I)
    uniform=bind(gl,'glGetUniformLocation',I,U,C.c_char_p)
    ui=bind(gl,'glUniform1i',None,I,I);u2=bind(gl,'glUniform2i',None,I,I,I)
    def texture(width,height,fmt,ptr=None):
        t=U();gen(1,C.byref(t));textures.append(t);texbind(0x0de1,t.value)
        for key,v in [(0x2801,0x2600),(0x2800,0x2600),(0x2802,0x812f),(0x2803,0x812f)]:
            bind(gl,'glTexParameteri',None,U,U,I)(0x0de1,key,v)
        upload(0x0de1,0,fmt,width,height,0,0x1903 if ptr is not None else 0x8227,0x1406,ptr)
        return t.value
    try:
        vertex='#version 330 core\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.-1.,0.,1.);}'
        fragment='''#version 330 core
uniform sampler2D nativeDepth;uniform ivec2 logicalSize;uniform int factor;
layout(location=0) out vec2 bounds;
'''+LOWER_GLSL+'''
void main(){
 ivec2 origin=ivec2(gl_FragCoord.xy)*factor;float lo=1.;bool invalid=false;
 for(int y=0;y<4;y++)for(int x=0;x<4;x++)if(x<factor&&y<factor){
  ivec2 p=origin+ivec2(x,y);
  if(p.x<logicalSize.x&&p.y<logicalSize.y){
   float d=texelFetch(nativeDepth,p,0).r;uint b=floatBitsToUint(d);
   if(b>0x3f800000u && b!=0x80000000u)invalid=true;
   else lo=min(lo,d);
  }
 }
 bounds=vec2(invalid?0.:conservativeLower(lo),1.);
}
'''
        for kind,src in [(0x8b31,vertex),(0x8b30,fragment)]:
            shader=bind(gl,'glCreateShader',U,U)(kind);shaders.append(shader);code=C.c_char_p(src.encode())
            bind(gl,'glShaderSource',None,U,I,C.POINTER(C.c_char_p),P)(shader,1,C.byref(code),None)
            bind(gl,'glCompileShader',None,U)(shader);ok=I();bind(gl,'glGetShaderiv',None,U,U,C.POINTER(I))(shader,0x8b81,C.byref(ok))
            if not ok.value:raise RuntimeError('Shader compilation failed')
        program=bind(gl,'glCreateProgram',U)()
        for shader in shaders:bind(gl,'glAttachShader',None,U,U)(program,shader)
        bind(gl,'glLinkProgram',None,U)(program);ok=I();bind(gl,'glGetProgramiv',None,U,U,C.POINTER(I))(program,0x8b82,C.byref(ok))
        if not ok.value:raise RuntimeError('Link failed')
        bind(gl,'glUseProgram',None,U)(program);ui(uniform(program,b'nativeDepth'),0)
        bind(gl,'glGenFramebuffers',None,I,C.POINTER(U))(1,C.byref(fb));bind(gl,'glBindFramebuffer',None,U,U)(0x8d40,fb.value)
        bind(gl,'glGenVertexArrays',None,I,C.POINTER(U))(1,C.byref(vao));bind(gl,'glBindVertexArray',None,U)(vao.value)
        source=texture(w,h,0x822e,raw);rows=[];raw_outputs=[]
        # First pass tests all values individually, then idempotent stores, then
        # the actual four reduction factors with odd edges on 513 x odd extent.
        expected=[lower_bits(b) for b in data]
        for stage,factor in enumerate([1,1,4,4,4,2]):
            ow,oh=(w+factor-1)//factor,(h+factor-1)//factor
            dest=texture(ow,oh,0x822f);attach(0x8d40,0x8ce0,0x0de1,dest,0)
            if bind(gl,'glCheckFramebufferStatus',U,U)(0x8d40)!=0x8cd5:raise RuntimeError('Incomplete FBO')
            texbind(0x0de1,source);u2(uniform(program,b'logicalSize'),w,h);ui(uniform(program,b'factor'),factor)
            bind(gl,'glViewport',None,I,I,I,I)(0,0,ow,oh);bind(gl,'glDrawArrays',None,U,I,I)(4,0,3)
            texbind(0x0de1,dest);out=(C.c_float*(ow*oh*2))();bind(gl,'glGetTexImage',None,U,I,U,U,P)(0x0de1,0,0x8227,0x1406,out)
            if bind(gl,'glGetError',U)()!=0:raise RuntimeError('GL error')
            if stage>=2:
                expected=[min(expected[yy*w+xx] for yy in range(y*factor,min((y+1)*factor,h)) for xx in range(x*factor,min((x+1)*factor,w))) for y in range(oh) for x in range(ow)]
            mismatches=[]
            for n,e in enumerate(expected):
                if bits(out[n*2])!=e or out[n*2+1]!=1.:
                    mismatches.append(dict(index=n,expected_bits=e,lower=out[n*2],upper=out[n*2+1]))
            raw_outputs.append(bytes(out).hex())
            rows.append(dict(stage=stage,factor=factor,input_extent=[w,h],output_extent=[ow,oh],texels=ow*oh,mismatches=mismatches[:10],mismatch_count=len(mismatches)))
            if mismatches:
                return dict(passed=False,identity=identity,rows=rows,input_bits=data,raw_rg32_readback_hex=raw_outputs,fragment=fragment,stopped_on_first_failing_stage=True)
            source=dest;w,h=ow,oh
        return dict(passed=True,identity=identity,rows=rows,input_bits=data,raw_rg32_readback_hex=raw_outputs,fragment=fragment,
                    scope='Finite actual linked shader/render-store/reduction stress, not universal proof or Minecraft consumption')
    finally:
        if program:bind(gl,'glDeleteProgram',None,U)(program)
        for shader in shaders:bind(gl,'glDeleteShader',None,U)(shader)
        for t in textures:bind(gl,'glDeleteTextures',None,I,C.POINTER(U))(1,C.byref(t))
        if fb.value:bind(gl,'glDeleteFramebuffers',None,I,C.POINTER(U))(1,C.byref(fb))
        if vao.value:bind(gl,'glDeleteVertexArrays',None,I,C.POINTER(U))(1,C.byref(vao))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--original',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    if a.output.exists():p.error('Fresh output required')
    result=run(a.original,probe);result['source_pins']={str(f.relative_to(ROOT)):hashlib.sha256(f.read_bytes()).hexdigest() for f in [Path(__file__).resolve(),ROOT/'scripts/check_makeup_hierarchy_precision.py',a.original.resolve()]}
    a.output.write_text(json.dumps(result,indent=2)+'\n');print({k:result[k] for k in ['passed','identity','rows']});raise SystemExit(0 if result['passed'] else 1)

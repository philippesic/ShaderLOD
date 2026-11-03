"""Pinned BSL clear cloud detail bound, source route and local arithmetic checks."""
from pathlib import Path
import copy
import json
import math
import random
import re
import shutil
import os
import struct
import subprocess
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
from build_supported_pack_adapters import apply,read_archive,sha256,CATALOG,RESOURCE_ROOT
from prepare_bsl_cloud_detail_bound import (
    RECIPE,HELPER,CERT,CLOUD,DEFERRED,ENTRY,NOISE,NOISE_META,MARKER,PINS,
    CLOUD_EXTRA,CLOUD_CALL_AFTER,DEFERRED_AFTER,HELPER_AFTER,
    candidate_manifest,recover,prepare,
)
from discover_iris_options import discover
BASE=ROOT/'dependencies/upstream/other-pack-research/BSL_v10.1.8.zip'
NATIVE=ROOT/'src/main/resources/assets/shaderlod/bslquad/native384.json'

def expand(files,name,options):
    s=files[name].decode().replace('\r\n','\n')
    for k,v in options.items():
        if v=='false': s=re.sub(r'(?m)^(\s*)#define '+re.escape(k)+r'\b[^\n]*',r'\1// disabled '+k,s)
        elif v=='true': s=re.sub(r'(?m)^(\s*)//\s*#define '+re.escape(k)+r'\b',r'\1#define '+k,s)
        else: s=re.sub(r'(?m)^(\s*#define '+re.escape(k)+r'\s+)\S+',lambda m:m[1]+v,s)
    return re.sub(r'^\s*#include\s+"([^"]+)"\s*$',
        lambda m:expand(files,'shaders'+m[1] if m[1].startswith('/') else str(Path(name).parent/m[1]),options),s,flags=re.M)

def cpp(files,name,options):
    s=re.sub(r'(?m)^\s*#(?:version|extension)\b[^\n]*','',expand(files,name,options))
    p=subprocess.run(['clang','-E','-P','-x','c','-'],input=s,text=True,capture_output=True,timeout=15)
    if p.returncode:raise AssertionError(p.stderr)
    return p.stdout

def f32(x):return struct.unpack('f',struct.pack('f',x))[0]
def mix(x,y,a,mode):
    if mode=='weighted':return f32(f32(x*f32(1-a))+f32(y*a))
    if mode=='difference':return f32(x+f32(f32(y-x)*a))
    return f32(x+f32(y-x)*a)
def raw(b,d,c,s,detail,amount,mode):
    x=f32(mix(b,d,f32(f32(.0476)*detail),mode)*21)
    x=mix(f32(x-c),f32(21-f32(c*2.5)),0.0,mode)
    return f32(x-f32(f32(s*3)+amount))
def coverage(g,amount):
    x=f32(abs(f32(g-.125)))
    x=f32(x*(f32(f32(2.14)-f32(amount*f32(.1))) if g>.125 else 8))
    return f32(f32(x*x)*4)

class BslCloudDetailBoundTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not BASE.is_file():raise unittest.SkipTest('Pinned BSL original absent')
        cls.original=read_archive(BASE);cls.current=json.loads((ROOT/RECIPE).read_text())
        cls.current_source=apply(cls.original,cls.current)
        cls.native=json.loads(NATIVE.read_text())
        # The bound is already part of the maintained pinned recipe. Exercise
        # its actual output rather than replaying the historical migration.
        cls.helper=(ROOT/HELPER).read_bytes()
        cls.candidate=cls.current
        cls.changed=cls.current_source

    def test_authentic_pins_and_current_bound_scope(self):
        self.assertEqual(sha256(BASE.read_bytes()),self.current['release_archive_sha256'])
        for n,h in PINS.items():self.assertEqual(sha256(self.original[n]),h)
        self.assertEqual(len(self.native),384)
        for k,v in {'CLOUDS':'2','CLOUD_BASE':'0','CLOUD_DETAIL':'1.0','AURORA':'0','TAA':'true'}.items():
            self.assertEqual(self.native[k],v)
        self.assertIn(b'#include "/program/deferred1.glsl"', self.changed[ENTRY])
        self.assertEqual(self.changed[NOISE],self.original[NOISE])
        self.assertEqual(self.changed[NOISE_META],self.original[NOISE_META])
        self.assertIn(b'texture.noise=tex/noise.png',self.original['shaders/shaders.properties'])
        self.assertIn(b'#extension GL_ARB_shader_texture_lod : enable',self.original[ENTRY])
        self.assertIn(HELPER_AFTER.encode(),self.changed['shaders/lib/shaderlod/adapter.glsl'])
        self.assertLess(self.changed[DEFERRED].index(('define '+MARKER).encode()),
                        self.changed[DEFERRED].index(b'#include "/lib/atmospherics/clouds.glsl"'))
        self.assertIn(b'if (!secondary) return CloudSample(',self.changed[CLOUD])
        self.assertIn(b'return CloudSample(nativeCoord, wind, cloudGradient, sunCoverage, dither);',self.changed[CLOUD])

    def test_selected_wrapper_and_fallback_preprocess(self):
        if not shutil.which('clang'):self.skipTest('clang unavailable')
        on=dict(self.changed);helper='shaders/lib/shaderlod/adapter.glsl'
        on[helper]=on[helper].replace(b'#define SHADERLOD_REFINEMENT_ADAPTER 0 // [0 1]',
                                     b'#define SHADERLOD_REFINEMENT_ADAPTER 1 // [0 1]')
        self.assertNotEqual(on[helper],self.changed[helper])
        selected=cpp(on,ENTRY,self.native)
        self.assertIn('ShaderLodCloudPrimaryClearBound',selected)
        self.assertIn('texture2DLod(noisetex, coord.xy + detailZ, 0.0).b',selected)
        self.assertNotIn('ShaderLodCloudPrimaryClearBound',cpp(self.changed,ENTRY,self.native))
        for wrapper in ('shaders/world-1/deferred1.fsh','shaders/world1/deferred1.fsh'):
            self.assertNotIn('ShaderLodCloudPrimaryClearBound',cpp(on,wrapper,self.native))
        for key in ('DISTANT_HORIZONS','VOXY','ADVANCED_MATERIALS'):
            modified=dict(on)
            modified[ENTRY]=on[ENTRY].replace(b'#define OVERWORLD',
                        ('#define OVERWORLD\n#define '+key).encode())
            options=dict(self.native)
            options.pop(key,None)  # The injected compile variant must survive native-option substitution.
            if key == 'ADVANCED_MATERIALS':
                options[key] = 'true'
            compiled=cpp(modified,ENTRY,options)
            if key == 'ADVANCED_MATERIALS':
                self.assertIn('if (!secondary) return CloudSample(',compiled)
            else:
                self.assertNotIn('ShaderLodCloudPrimaryClearBound',compiled)
        self.assertIn('rainStrength != 0.0',selected)
        self.assertIn('shaderlodPreset != 0',selected)
        self.assertIn('upper < -0.001',selected)
        self.assertIn('secondary ? shaderlodOptReflectionCloudDetail : shaderlodOptCloudPrimaryDetail',selected)

    def test_bound_float32_no_false_certificates_in_fixed_domain(self):
        # Independent small implementation-focused check; alternative evidence has 120k cases.
        rng=random.Random(19337);modes=('weighted','difference','fused')
        certified=0
        for _ in range(5000):
            b=f32(rng.random());g=f32(rng.random());s=f32(rng.random())
            detail=f32(rng.choice([.3,.7,1,1.3,1.7]));amount=f32(rng.choice([8,9,10,11,12]))
            c=coverage(g,amount);w=f32(f32(.0476)*detail)
            # Candidate's ordered upper expression for each plausible mix association.
            upper=min(raw(b,1,c,s,detail,amount,mode) for mode in modes)
            if upper<-.001:
                certified+=1
                for d in (0,.5,1,f32(rng.random())):
                    self.assertLessEqual(max(raw(b,d,c,s,detail,amount,m) for m in modes),0.0)
        self.assertGreater(certified,100)
        self.assertIn('upper = mix(noiseBase, 1.0, detailWeight) * 21.0;',CLOUD_EXTRA)
        self.assertIn('upper = upper - noiseCoverage;',CLOUD_EXTRA)
        self.assertIn('upper = upper - (sunCoverage * 3.0 + CLOUD_AMOUNT);',CLOUD_EXTRA)
        self.assertIn('if (upper < -0.001) return 0.0;',CLOUD_EXTRA)
        self.assertNotIn('for (',CLOUD_EXTRA)
        self.assertNotIn('sampleStep',CLOUD_EXTRA)
        self.assertIn('texture2DLod(noisetex, coord.xy + detailZ, 0.0)',CLOUD_EXTRA)

    def test_glslang_selected_source(self):
        if not shutil.which('glslangValidator') or not shutil.which('clang'):
            self.skipTest('GLSLang/clang unavailable')
        on=dict(self.changed);h='shaders/lib/shaderlod/adapter.glsl'
        on[h]=on[h].replace(b'#define SHADERLOD_REFINEMENT_ADAPTER 0 // [0 1]',
                             b'#define SHADERLOD_REFINEMENT_ADAPTER 1 // [0 1]')
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'selected.frag'
            path.write_text('#version 330 compatibility\n'+cpp(on,ENTRY,self.native))
            p=subprocess.run(['glslangValidator','-S','frag',str(path)],text=True,capture_output=True,timeout=15)
            self.assertEqual(p.returncode,0,p.stdout+p.stderr)




if __name__=='__main__':unittest.main()

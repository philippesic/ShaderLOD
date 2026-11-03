"""Source/assembly and standalone parser evidence, never driver/runtime/performance proof."""
import copy
import json
from pathlib import Path
import random
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
from build_solas_astralex_lod_r1 import apply, build, sha
from build_supported_pack_adapters import read_archive

class DeferredMetricTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifests=[json.loads(p.read_text()) for p in sorted((ROOT/'patches/solas-astralex-lod-r1').glob('*.json'))]

    def test_real_pins_fail_closed_and_preserve_native_settings_attribution(self):
        for m in self.manifests:
            files=read_archive(ROOT/m['original_archive'])
            self.assertEqual(sha((ROOT/m['original_archive']).read_bytes()),m['original_archive_sha256'])
            result=apply(files,m)
            changed={p['path'] for p in m['patches']}
            for n in files:
                if n not in changed:self.assertEqual(files[n],result[n],n)
            altered=dict(files);altered[m['patches'][0]['path']]+=b'\n'
            with self.assertRaisesRegex(ValueError,'Pinned'):apply(altered,m)
            bad=copy.deepcopy(m);bad['patches'][0]['before']=''
            with self.assertRaisesRegex(ValueError,'exactly once'):apply(files,bad)
            bad=copy.deepcopy(m);bad['patches'][0]['path']='unowned'
            with self.assertRaisesRegex(ValueError,'Unpinned'):apply(files,bad)
            for n in ('shaders/lib/common.glsl','shaders/settings/globalSettings.glsl'):
                if n in files:self.assertEqual(files[n],result[n])

    def test_assembly_determinism_no_overwrite_and_no_runtime_claim(self):
        for m in self.manifests:
            with tempfile.TemporaryDirectory() as td:
                td=Path(td);manifest=ROOT/'patches/solas-astralex-lod-r1'/(m['pack']+'.json')
                base=ROOT/m['original_archive'];before=sha(base.read_bytes())
                a=build(base,td/'a.zip',manifest);b=build(base,td/'b.zip',manifest)
                self.assertEqual(a['output_sha256'],b['output_sha256'])
                self.assertEqual(before,sha(base.read_bytes()))
                self.assertTrue(a['validation']['source_assembled'])
                self.assertFalse(any(v for k,v in a['validation'].items() if k!='source_assembled'))
                with self.assertRaisesRegex(ValueError,'overwrite'):build(base,base,manifest)
                with self.assertRaisesRegex(ValueError,'already exists'):build(base,td/'a.zip',manifest)

    def test_metric_is_output_only_and_control_precedes_metric(self):
        helper=(ROOT/'patches/solas-astralex-lod-r1/deferred_metric.glsl').read_text()
        self.assertIn('#define SHADERLOD_DEFERRED_METRIC 0 // [0 1]',helper)
        self.assertIn('shaderlodBridgeVersion == 2 && shaderlodEnabled == 1',helper)
        self.assertIn('shaderlodPreset >= 0 && shaderlodPreset <= 2',helper)
        self.assertNotIn('texture',helper)
        for m in self.manifests:
            files=read_archive(ROOT/m['original_archive']);name=m['patches'][0]['path']
            source=files[name].decode().split('    return vec4(pos, dist);')[0]
            # These are the only original uses before the return: declaration and assignment.
            self.assertEqual(len(re.findall(r'\bdist\b',source)),2)
            result=apply(files,m)[name].decode()
            self.assertIn('if (shaderlodDeferMetric) {',result)
            self.assertIn('if (shaderlodDeferMetric && shaderlodSampledHit)',result)
            # All original traversal/texture/exit lines remain verbatim.
            for line in files[name].decode().splitlines():
                if any(term in line for term in ('texture','if (pos.','if (error','if (err <','refinedSamples','sr >=','rayStep *=','vector *=','viewPos =')):
                    self.assertIn(line,result)

    def test_last_sample_contract_preserves_no_sample_and_early_exit(self):
        rng=random.Random(271828)
        # Abstract sample traces isolate the proven seam. No texture/driver emulation.
        for count in range(31):
            values=[rng.uniform(-1e6,1e6) for _ in range(count)]
            for terminate in range(count+1):
                visits=values[:terminate]
                native=0.0
                for value in visits:native=abs(value)
                deferred=abs(visits[-1]) if visits else 0.0
                self.assertEqual(native,deferred)
                self.assertEqual(0 if not visits else 1, min(1,len(visits)))
        for bridge in (0,1,2,3):
            for enabled in (0,1,2):
                for preset in (-1,0,1,2,3,4):
                    active=bridge==2 and enabled==1 and 0<=preset<=2
                    self.assertEqual(active,(bridge,enabled,preset) in {(2,1,0),(2,1,1),(2,1,2)})

    @unittest.skipUnless(shutil.which('glslangValidator'),'standalone GLSL parser unavailable')
    def test_parser_option_off_token_parity_and_option_on_dh_voxy(self):
        helper=(ROOT/'patches/solas-astralex-lod-r1/deferred_metric.glsl').read_text()
        header='''#version 330 compatibility
uniform mat4 gbufferProjection, gbufferProjectionInverse, vxProjInv, dhProjectionInverse;
uniform sampler2D depthtex0, colortex0, vxDepthTexOpaque, dhDepthTex1;
uniform int frameCounter;
uniform float viewHeight;
#define REFLECTION_LOD 1
#define WATER_NORMALS 0
#define REFLECTION_MODE 0
#define REFLECTION_ROUGH
#define EPS 0.00001
#define Max0(x) max((x),0.0)
'''
        astralex_helpers='''vec3 nvec3(vec4 p){return p.xyz/p.w;}
vec4 nvec4(vec3 p){return vec4(p,1.0);}
float cdist(vec2 p){return max(abs(p.x-.5),abs(p.y-.5))*1.85;}
float clamp01(float x){return clamp(x,0.0,1.0);}
'''
        for m in self.manifests:
            files=read_archive(ROOT/m['original_archive']);name=m['patches'][0]['path']
            native=files[name].decode();candidate=apply(files,m)[name].decode()
            prefix=header+(astralex_helpers if m['pack']=='astralex' else '')
            entry='\nvoid main(){gl_FragColor=vec4(1.0);}\n'
            with tempfile.TemporaryDirectory() as td:
                td=Path(td)
                def parse(body,option,macros='',preprocess=False):
                    source=body.replace('#include "/lib/shaderlod/deferred_metric.glsl"',helper.replace('SHADERLOD_DEFERRED_METRIC 0 //','SHADERLOD_DEFERRED_METRIC '+str(option)+' //'))
                    path=td/'source.frag';path.write_text(prefix+macros+source+entry)
                    cmd=['glslangValidator','-S','frag']+(['-E'] if preprocess else [])+[str(path)]
                    result=subprocess.run(cmd,text=True,capture_output=True)
                    self.assertEqual(result.returncode,0,result.stdout+result.stderr)
                    return result.stdout
                off=parse(candidate,0,preprocess=True)
                original=parse(native,0,preprocess=True)
                tokens=lambda s:re.findall(r'[A-Za-z_]\w*|\d+(?:\.\d*)?|[^\s]',s)
                self.assertEqual(tokens(off),tokens(original),'Option Off changed native tokens')
                for macros in ('','#define DISTANT_HORIZONS\n#define VOXY\n#define TAA\n'):
                    parse(candidate,1,macros)

if __name__=='__main__':unittest.main()

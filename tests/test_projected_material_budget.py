"""Private source/math/static GLSL checks. No driver, runtime, quality or GPU proof."""
import copy
import hashlib
import json
import math
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from build_complementary_projected_pom import apply, build, MANIFEST
from build_supported_pack_adapters import read_archive
BASE = ROOT / 'dependencies/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip'
POM = 'shaders/lib/materials/materialMethods/pomEffects.glsl'
CUSTOM = 'shaders/lib/materials/materialHandling/customMaterials.glsl'


def expand(files, name, stack=()):
    if name in stack:
        raise ValueError('Cyclic include')
    def include(m):
        return expand(files, 'shaders/'+m.group(1).lstrip('/'), stack+(name,))
    return re.sub(r'^\s*#include\s+"([^"]+)"[^\n]*', include, files[name].decode(), flags=re.M)


def spacing_floor(ray, dx, dy, tile, output_scale=1):
    """Independent real-arithmetic oracle for the stated pixel/texel spacing rule."""
    if not all(math.isfinite(x) for x in (*ray, *dx, *dy, *tile, output_scale)):
        return None
    if min(tile)<1 or max(tile)>65536 or not 1<=output_scale<=16:
        return None
    det = dx[0]*dy[1]-dy[0]*dx[1]
    largest = max(sum(x*x for x in dx), sum(x*x for x in dy))
    if not (1e-20<largest<1e8 and abs(det)>0.01*largest):
        return None
    pixels = math.hypot((dy[1]*ray[0]-dy[0]*ray[1])/det,
                        (-dx[1]*ray[0]+dx[0]*ray[1])/det)*output_scale
    texels = math.hypot(ray[0]*tile[0],ray[1]*tile[1])
    return math.ceil(max(pixels*4,texels*2))


def smooth(a,b,x):
    t=max(0,min(1,(x-a)/(b-a)))
    return t*t*(3-2*t)


def distance_count(d, u=128, preset=1):
    # Both selected medium/far ratios equal, so the first band reaches the floor.
    ratio=.75 if preset==0 else .5
    return max((u+1)//2, math.ceil(u*(1+(ratio-1)*smooth(23,27,d))))


class ProjectedMaterialTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not BASE.exists():
            raise unittest.SkipTest('Pinned original absent; actual-source tests unavailable')
        cls.manifest=json.loads(MANIFEST.read_text())
        cls.original=read_archive(BASE)
        cls.candidate=apply(cls.original,cls.manifest)

    def test_real_assembly_pins_and_unchanged_members(self):
        self.assertEqual(hashlib.sha256(BASE.read_bytes()).hexdigest(),self.manifest['release_archive_sha256'])
        changed={n for n in self.original if self.original[n]!=self.candidate[n]}
        self.assertEqual(changed,{POM,CUSTOM,'shaders/shaders.properties'})
        for n in ('License.txt','shaders/lib/common.glsl','shaders/lib/lighting/mainLighting.glsl',
                  'shaders/lib/util/dFdxdFdy.glsl','shaders/program/gbuffers_terrain.glsl'):
            self.assertEqual(self.original[n],self.candidate[n])
        old=self.original[POM].split(b'float GetParallaxShadow',1)[1]
        self.assertEqual(old,self.candidate[POM].split(b'float GetParallaxShadow',1)[1])
        m=copy.deepcopy(self.manifest);m['patches'][0]['before']=''
        with self.assertRaisesRegex(ValueError,'exactly once'):apply(self.original,m)
        drift=self.original.copy();drift[POM]+=b' drift'
        with self.assertRaisesRegex(ValueError,'Pinned'):apply(drift,self.manifest)

    def test_spacing_units_and_fallback(self):
        self.assertEqual(spacing_floor((.1,0),(.01,0),(0,.02),(32,32)),40)
        # Twice output resolution requires twice the pixel floor; atlas scale cancels.
        self.assertEqual(spacing_floor((.1,0),(.01,0),(0,.02),(32,32),2),80)
        self.assertEqual(spacing_floor((.1,0),(.5,0),(0,.5),(1024,1024)),205)
        for dx,dy in (((0,0),(0,0)),((.01,0),(.01,0)),((1,0),(0,.0001))):
            self.assertIsNone(spacing_floor((.1,0),dx,dy,(32,32)))
        for invalid in (math.nan,math.inf,-math.inf):
            self.assertIsNone(spacing_floor((invalid,0),(.01,0),(0,.01),(32,32)))
        self.assertIsNone(spacing_floor((.1,0),(.01,0),(0,.01),(32,32),.75))

    def test_whole_depth_march_and_transition_cases(self):
        for n in (16,64,96,118,128,256):
            interval=.13/n;depth_step=1/n
            self.assertAlmostEqual(n*interval,.13)
            self.assertAlmostEqual(n*depth_step,1)
            # Same preceding-index convention, coherently rescaled in both coordinates.
            i=n/2+1;p=max(i-1,0)
            self.assertAlmostEqual(p*interval,.065)
            self.assertAlmostEqual(p*depth_step,.5)
        self.assertEqual([distance_count(d) for d in (22.6,23,24,25,26,27,29,31)],
                         [128,128,118,96,74,64,64,64])
        self.assertEqual(distance_count(27,preset=0),96)
        pom=self.candidate[POM].decode()
        self.assertLess(pom.index('float minHeight = 1.0 - invParallaxQuality'),pom.index('invParallaxQuality = 1.0 / float(shaderlodMarchCount)'))
        self.assertIn('i < shaderlodMarchCount',pom)
        self.assertIn('(-viewVector.z * float(shaderlodMarchCount))',pom)
        self.assertIn('traceCoordDepth.z -= pI * invParallaxQuality',pom)
        helper=self.candidate['shaders/include/shaderlod/projected_material_budget.glsl'].decode()
        self.assertNotRegex(helper,r'\b(?:texture\w*|dFdx|dFdy)\s*\(')
        adapter=self.candidate['shaders/lib/shaderlod/projected_pom_adapter.glsl'].decode()
        self.assertIn('shaderlodPreset < 0 || shaderlodPreset > 2',adapter)
        self.assertIn('fade >= 0.5 && fade < 1.0',adapter)
        self.assertIn('viewVector.z * viewVector.z > 0.0625 * view_length2',adapter)
        self.assertIn('#define SHADERLOD_PROJECTED_POM 0 // [0 1]',adapter)

    def source(self,files,enabled=0,extra='',quality=128):
        files=files.copy()
        # Stub unrelated emission body only; real complete POM/custom consumer bodies compiled.
        files['shaders/lib/materials/materialMethods/customEmission.glsl']=b'float GetCustomEmission(vec4 s, vec2 t) { return 0.0; }\n'
        helper='shaders/lib/shaderlod/projected_pom_adapter.glsl'
        if helper in files:
            files[helper]=files[helper].replace(b'#define SHADERLOD_PROJECTED_POM 0',f'#define SHADERLOD_PROJECTED_POM {enabled}'.encode())
        prefix='''#version 400 compatibility
#define GBUFFERS_TERRAIN
#define POM
#define POM_DEPTH 0.80
#define POM_DISTANCE 32
#define POM_LIGHTING_MODE 2
#define NORMAL_MAP_STRENGTH 100
#define RP_MODE 3
#define DIRECTIONAL_BLOCKLIGHT 0
#define OSIEBCA (1.0/255.0)
#define CUSTOM_EMISSION_INTENSITY 100
uniform sampler2D normals, tex, specular;
uniform vec2 atlasSize;
uniform int mat, frameCounter;
in vec2 texCoord, signMidCoordPos;
in vec4 vTexCoordAM;
in vec3 viewVector;
vec4 glColor=vec4(1.0);
vec3 lightVec=vec3(0.0,0.0,1.0), upVec=vec3(0.0,1.0,0.0);
mat3 tbnMatrix=mat3(1.0);
float NdotUmax0=1.0;
float pow2(float x) {return x*x;}
float max0(float x) {return max(x,0.0);}
'''+f'#define POM_QUALITY {quality}\n'+extra+'\n'
        return prefix+expand(files,CUSTOM)+'''\nvoid main() {
 vec4 c=vec4(1.0);vec3 n=vec3(0.0,0.0,1.0), shadow=vec3(1.0);
 vec2 lm=vec2(0.0);float nd=1.0, sg=0.0, sd=0.0, h=0.0, e=0.0, m=0.0;
 GetCustomMaterials(c,n,lm,nd,shadow,sg,sd,h,e,m,vec3(0.0,0.0,-26.0),26.0);
 gl_FragColor=c;
}\n'''

    def test_glsl_static_and_off_upstream_token_parity(self):
        validator=shutil.which('glslangValidator')
        self.assertIsNotNone(validator,'Static GLSL validator required for these checks')
        with tempfile.TemporaryDirectory() as td:
            def run(source,preprocess=False):
                path=Path(td)/'pom.frag';path.write_text(source)
                r=subprocess.run([validator,'-S','frag',*(['-E'] if preprocess else []),str(path)],capture_output=True,text=True)
                self.assertEqual(r.returncode,0,r.stdout+r.stderr)
                return r.stdout
            def tokens(s):
                s=re.sub(r'/\*.*?\*/|//[^\n]*','',s,flags=re.S)
                s=re.sub(r'^\s*#.*$','',s,flags=re.M)
                return re.findall(r'[A-Za-z_]\w*|\d+(?:\.\d*)?|[^\s]',s)
            original=run(self.source(self.original),True)
            off=run(self.source(self.candidate,0),True)
            self.assertEqual(tokens(original),tokens(off))
            for extra in ('','#define DISTANT_HORIZONS\n','#define POM_ALLOW_CUTOUT\n','#define VOXY\n'):
                source=self.source(self.candidate,1,extra)
                run(source)
                if extra:
                    self.assertEqual(tokens(run(self.source(self.original,extra=extra),True)),tokens(run(source,True)))
            for q in (64,128,256):
                run(self.source(self.candidate,1,quality=q))

    def test_full_terrain_entrypoints_preprocess_and_off_parity(self):
        validator=shutil.which('glslangValidator')
        self.assertIsNotNone(validator)
        with tempfile.TemporaryDirectory() as td:
            for world in ('world0','world-1','world1'):
                outputs={}
                for label,original,enabled in (('original',self.original,0),('off',self.candidate,0),('on',self.candidate,1)):
                    files=original.copy()
                    files['shaders/lib/common.glsl']=files['shaders/lib/common.glsl'].replace(b'#define RP_MODE 1',b'#define RP_MODE 3')
                    helper='shaders/lib/shaderlod/projected_pom_adapter.glsl'
                    if helper in files:
                        files[helper]=files[helper].replace(b'#define SHADERLOD_PROJECTED_POM 0',f'#define SHADERLOD_PROJECTED_POM {enabled}'.encode())
                    source=expand(files,f'shaders/{world}/gbuffers_terrain.fsh')
                    source=source.replace('#version 130','#version 130\n#define IS_IRIS\n#define MC_VERSION 12101\n#define MC_GL_VERSION 410\n#define MC_OS_MAC\n#define MC_GLSL_VERSION 410',1)
                    path=Path(td)/'terrain.frag';path.write_text(source)
                    result=subprocess.run([validator,'-S','frag','-E',str(path)],capture_output=True,text=True)
                    self.assertEqual(result.returncode,0,result.stderr)
                    # Preprocessor preserves comments; strip them before token comparison.
                    cleaned=re.sub(r'/\*.*?\*/|//[^\n]*','',result.stdout,flags=re.S)
                    cleaned=re.sub(r'^\s*#.*$','',cleaned,flags=re.M)
                    outputs[label]=re.findall(r'[A-Za-z_]\w*|\d+(?:\.\d*)?|[^\s]',cleaned)
                self.assertEqual(outputs['original'],outputs['off'],world)
                self.assertIn('shaderlodMarchCount',outputs['on'],world)

    def test_private_archive_preserves_license_and_explicit_validation(self):
        with tempfile.TemporaryDirectory() as td:
            out=Path(td)/'private.zip';receipt=build(BASE,out)
            files=read_archive(out)
            self.assertEqual(files['License.txt'],self.original['License.txt'])
            self.assertFalse(receipt['default_enabled'])
            self.assertFalse(any(receipt['validation'].values()))
            self.assertEqual(receipt['output_sha256'],hashlib.sha256(out.read_bytes()).hexdigest())
            with self.assertRaisesRegex(ValueError,'already exists'):build(BASE,out)
            with self.assertRaisesRegex(ValueError,'overwrite'):build(BASE,BASE)

if __name__=='__main__':unittest.main()

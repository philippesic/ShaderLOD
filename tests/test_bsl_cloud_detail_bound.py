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
BASE=ROOT/'dev/upstream/other-pack-research/BSL_v10.1.8.zip'
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
        cls.helper=(ROOT/HELPER).read_bytes().replace(b'uniform int shaderlodPreset;',HELPER_AFTER.encode(),1)
        cls.candidate=candidate_manifest(cls.current,sha256(cls.helper))
        with tempfile.TemporaryDirectory() as temp:
            stage=Path(temp)
            p=stage/HELPER;p.parent.mkdir(parents=True);p.write_bytes(cls.helper)
            for n,info in cls.candidate['additions'].items():
                q=stage/info['path'];q.parent.mkdir(parents=True,exist_ok=True)
                if not q.exists():q.write_bytes((ROOT/info['path']).read_bytes())
            cls.changed=apply(cls.original,cls.candidate,stage)

    def test_authentic_pins_scope_and_exact_recovery(self):
        self.assertEqual(sha256(BASE.read_bytes()),self.current['release_archive_sha256'])
        for n,h in PINS.items():self.assertEqual(sha256(self.original[n]),h)
        self.assertEqual(len(self.native),384)
        for k,v in {'CLOUDS':'2','CLOUD_BASE':'0','CLOUD_DETAIL':'1.0','AURORA':'0','TAA':'true'}.items():
            self.assertEqual(self.native[k],v)
        self.assertEqual(sorted(n for n in self.changed if self.changed[n]!=self.current_source[n]),
                         [CLOUD,'shaders/lib/shaderlod/adapter.glsl',DEFERRED])
        for n in (CLOUD,'shaders/lib/shaderlod/adapter.glsl',DEFERRED):
            self.assertEqual(recover(n,self.changed[n]),self.current_source[n])
        self.assertEqual(self.changed[ENTRY],self.original[ENTRY])
        self.assertEqual(self.changed[NOISE],self.original[NOISE])
        self.assertEqual(self.changed[NOISE_META],self.original[NOISE_META])
        self.assertIn(b'texture.noise=tex/noise.png',self.original['shaders/shaders.properties'])
        self.assertIn(b'#extension GL_ARB_shader_texture_lod : enable',self.original[ENTRY])
        self.assertIn(HELPER_AFTER.encode(),self.changed['shaders/lib/shaderlod/adapter.glsl'])
        self.assertLess(self.changed[DEFERRED].index(('define '+MARKER).encode()),
                        self.changed[DEFERRED].index(b'#include "/lib/atmospherics/clouds.glsl"'))
        self.assertIn(b'if (fadeFaster) noise = CloudSample(',self.changed[CLOUD])
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
            self.assertNotIn('ShaderLodCloudPrimaryClearBound',cpp(modified,ENTRY,options))
        self.assertIn('rainStrength != 0.0',selected)
        self.assertIn('shaderlodPreset != 0',selected)
        self.assertIn('upper < -0.001',selected)
        self.assertIn('if (fadeFaster) noise = CloudSample',selected)

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

    def test_real_builder_overlay_and_pin_refusals(self):
        protected={p:(ROOT/p).read_bytes() for p in (RECIPE,HELPER,CATALOG,CERT,RESOURCE_ROOT+RECIPE,RESOURCE_ROOT+HELPER)}
        with tempfile.TemporaryDirectory() as temp:
            dest=Path(temp)/'candidate';receipt=prepare(BASE,dest)
            self.assertTrue(receipt['exact_body_recovered'])
            self.assertEqual(set(receipt['overlay']),set(protected))
            self.assertEqual((dest/'overlay'/RECIPE).read_bytes(),(dest/'overlay'/(RESOURCE_ROOT+RECIPE)).read_bytes())
            self.assertEqual((dest/'overlay'/HELPER).read_bytes(),(dest/'overlay'/(RESOURCE_ROOT+HELPER)).read_bytes())
            cat=(dest/'overlay'/CATALOG).read_text()
            self.assertIn(receipt['candidate_digest'],cat)
            self.assertIn(receipt['current_adapter_digest'],cat.split('LEGACY_ADAPTER_DIGESTS')[1])
            cert=(dest/'overlay'/CERT).read_text()
            self.assertIn('RECIPE_SHA="'+receipt['overlay'][RECIPE]['after']+'"',cert)
            self.assertEqual(protected,{p:(ROOT/p).read_bytes() for p in protected})
            self.assertTrue(all(x is False for x in receipt['validation'].values()))
            saved=(dest/'receipt.json').read_bytes()
            with self.assertRaisesRegex(ValueError,'already exists'):prepare(BASE,dest)
            self.assertEqual(saved,(dest/'receipt.json').read_bytes())
            wrong=Path(temp)/'wrong.zip';wrong.write_bytes(BASE.read_bytes()+b'x')
            with self.assertRaisesRegex(ValueError,'archive hash'):prepare(wrong,Path(temp)/'refused')
        for name in (CLOUD,DEFERRED,ENTRY,NOISE,NOISE_META):
            bad=dict(self.original);bad[name]+=b'X'
            with self.assertRaisesRegex(ValueError,'Pinned shader source identity changed|Pinned source changed'):
                apply(bad,self.candidate,ROOT)  # Source digest or exact member pin refuses.
        with self.assertRaisesRegex(ValueError,'already pinned'):candidate_manifest(self.candidate,sha256(self.helper))

    def test_real_iris_option_inventory_existing_parser(self):
        cmd=json.loads((ROOT/'dev/logs/developer-cycle-r2/option-discovery-r2/bsl-command.json').read_text())['command']
        def val(flag):return cmd[cmd.index(flag)+1]
        required=[Path(val('--iris')),Path(val('--java'))]+[Path(cmd[i+1]) for i,x in enumerate(cmd) if x=='--dependency']
        if not all(p.is_file() for p in required):self.skipTest('Pinned Iris parser dependencies absent')
        with tempfile.TemporaryDirectory() as temp:
            dest=Path(temp)/'candidate';prepare(BASE,dest)
            report=discover(BASE,dest/'candidate.zip',NATIVE,Path(val('--iris')),val('--iris-sha256'),
                [Path(cmd[i+1]) for i,x in enumerate(cmd) if x=='--dependency'],Path(val('--java')),
                ROOT/'dev/logs/developer-cycle-r2/option-discovery-r2/added-overrides.json')
            self.assertEqual(report['nativeOptionCount'],384)
            self.assertEqual(report['candidateOptionCount'],388)
            self.assertEqual(report['addedDeclarations'],['SHADERLOD_REFINEMENT_ADAPTER',
                'SHADERLOD_REFINEMENT_ADAPTER_INCLUDED','SHADERLOD_REFINEMENT_BUDGET_INCLUDED',
                'SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED'])
            self.assertTrue(report['nativeMapPreserved'])
            self.assertFalse(report['loadedMapVerified'])
            self.assertNotIn(MARKER,report['candidateExpectedOptions'])

    def test_real_java_selection_and_certificate_recovery(self):
        jdk=ROOT/'dev/runtime/jdk25/jdk-25.0.2.jdk/Contents/Home/bin'
        saved=ROOT/'dev/logs/concept-transfer-r1/bsl-neoforge-main/checks-classpath.json'
        if not (jdk/'javac').is_file() or not saved.is_file():
            self.skipTest('Recorded JDK25/classpath unavailable')
        dependencies=[Path(p) for p in json.loads(saved.read_text())]
        if not all(p.exists() for p in dependencies):
            self.skipTest('A recorded actual Java dependency is unavailable')
        source=r'''
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.packs.PackSelectionService;
import com.pp.shaderlod.integration.bindingcompat.BslSourceCertificate;
import com.pp.shaderlod.integration.bslquad.BslQuadEligibility;
import java.nio.file.*;import java.util.*;
public class BslBoundConsumerCheck {
 static void ok(PackSelectionService.Result r){if(!r.accepted())throw new AssertionError(r.detail());}
 static void admit(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception {
   Path dir=Path.of(args[0]);String original="Original.zip";
   byte[] nativeZip=Files.readAllBytes(dir.resolve(original));
   byte[] nativeSidecar=Files.readAllBytes(dir.resolve(original+".txt"));
   var service=new PackSelectionService();
   var balanced=service.apply(dir,original,true,LodPreset.BALANCED,Map.of(),true);ok(balanced);
   String generated=balanced.packFile();admit(!original.equals(generated),"Balanced bypassed generated graph");
   var nativeMap=BslQuadEligibility.nativeOptions();var generatedMap=BslSourceCertificate.generatedOptions();
   admit(BslSourceCertificate.validate(dir,original,original,nativeMap,false).eligible(),"Native certificate");
   admit(BslSourceCertificate.validate(dir,generated,original,generatedMap,false).eligible(),"Generated certificate");
   var restart=service.apply(dir,generated,true,LodPreset.BALANCED,Map.of(),true);ok(restart);
   admit(restart.status()==PackSelectionService.Status.UNCHANGED,"Restart mutated");
   var quality=service.apply(dir,generated,true,LodPreset.QUALITY,Map.of(),true);ok(quality);
   admit(original.equals(quality.packFile()),"Quality graph");
   admit(BslSourceCertificate.validate(dir,generated,original,generatedMap,true).eligible(),"Native-return certificate");
   admit(!BslSourceCertificate.validate(dir,generated,original,generatedMap,false).eligible(),"Inactive generated admitted");
   var performance=service.apply(dir,original,true,LodPreset.PERFORMANCE,Map.of(),true);ok(performance);
   admit(original.equals(performance.packFile()),"Performance native bypass");
   var off=service.apply(dir,original,false,LodPreset.BALANCED,Map.of(),false);ok(off);
   admit(original.equals(off.packFile()),"Off graph");
   admit(Arrays.equals(nativeZip,Files.readAllBytes(dir.resolve(original))),"Native archive changed");
   admit(Arrays.equals(nativeSidecar,Files.readAllBytes(dir.resolve(original+".txt"))),"Native sidecar changed");
   var changed=new TreeMap<>(generatedMap);changed.put("TAA","false");
   admit(!BslSourceCertificate.validate(dir,generated,original,changed,true).eligible(),"Native option tamper admitted");
   Path member=dir.resolve(generated);byte[] old=Files.readAllBytes(member);
   Files.write(member,new byte[]{1},StandardOpenOption.APPEND);
   admit(!BslSourceCertificate.validate(dir,generated,original,generatedMap,true).eligible(),"Archive tamper admitted");
   var refused=service.apply(dir,generated,false,LodPreset.BALANCED,Map.of());
   admit(refused.status()==PackSelectionService.Status.REFUSED,"Tampered Apply admitted");
   Files.write(member,old);
   admit(BslSourceCertificate.validate(dir,generated,original,generatedMap,true).eligible(),"Restored generated refused");
   System.out.println("real BSL selection/certificate/recovery consumer passed");
 }
}'''
        with tempfile.TemporaryDirectory() as temp:
            tmp=Path(temp);dest=tmp/'candidate';prepare(BASE,dest)
            classes=tmp/'classes';classes.mkdir()
            resources=tmp/'resources'
            shutil.copytree(ROOT/'src/main/resources/assets/shaderlod/adapters',
                            resources/'assets/shaderlod/adapters')
            for name in (RESOURCE_ROOT+RECIPE,RESOURCE_ROOT+HELPER):
                target=resources/name.removeprefix('src/main/resources/')
                target.write_bytes((dest/'overlay'/name).read_bytes())
            # Original native/source/API proof resources are pinned and unchanged.
            shutil.copytree(ROOT/'src/main/resources/assets/shaderlod/bslquad',
                            resources/'assets/shaderlod/bslquad')
            harness=tmp/'BslBoundConsumerCheck.java';harness.write_text(source)
            cp=os.pathsep.join(map(str,dependencies))
            command=[str(jdk/'javac'),'-proc:none','-cp',cp,'-d',str(classes),
                str(dest/'overlay'/CATALOG),
                str(ROOT/'src/main/java/com/pp/shaderlod/packs/PackSelectionService.java'),
                str(ROOT/'src/main/java/com/pp/shaderlod/LodPreset.java'),
                str(dest/'overlay'/CERT),str(harness)]
            built=subprocess.run(command,text=True,capture_output=True,timeout=30)
            self.assertEqual(built.returncode,0,built.stdout+built.stderr)
            packs=tmp/'packs';packs.mkdir();shutil.copyfile(BASE,packs/'Original.zip')
            (packs/'Original.zip.txt').write_text('# Native BSL quality\r\nTAA=true\r\nFXAA=true\r\nCLOUDS=2\r\n')
            run_cp=os.pathsep.join([str(classes),str(resources),cp])
            run=subprocess.run([str(jdk/'java'),'-ea','-cp',run_cp,'BslBoundConsumerCheck',str(packs)],
                               text=True,capture_output=True,timeout=30)
            self.assertEqual(run.returncode,0,run.stdout+run.stderr)
            self.assertIn('real BSL selection/certificate/recovery consumer passed',run.stdout)

if __name__=='__main__':unittest.main()

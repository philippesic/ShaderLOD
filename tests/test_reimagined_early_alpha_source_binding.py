"""Ordinary/temporal/receiver current reader pins with purpose separation; no GL claim."""
import hashlib
import json
from pathlib import Path
import re
import sys
import unittest

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
from build_supported_pack_adapters import apply,read_archive,shader_source_digest

OLD_SOURCE='bb847b2915b38d8022c4c5a23f9f6f524f895b7a8c9dab05af93b82c2940cb38'
OLD_RECIPE='beb02922f2905a9939a5c4e75fc474ad956156ea7c885da12f06cf7ba9a96108'
NEW_SOURCE='2d2467ef372ab37e303f3736179fed301dead8d2132c0eae486e3cb0dee617e9'
NEW_RECIPE='7eb35919b4c5781ebd2a6bf4dac58b5b7b40b2e9979ec6afa9b7529e371bd158'


class ReimaginedEarlyAlphaSourceBindingTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.reader=(ROOT/'src/main/java/com/pp/shaderlod/optimization/reimaginedearlyalpha/ReimaginedEarlyAlphaRuntime.java').read_text()
        cls.recipe=(ROOT/'patches/adapters/complementary-reimagined.json').read_bytes()
        cls.generated=apply(read_archive(ROOT/'dependencies/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip'),json.loads(cls.recipe))

    def test_all_three_readers_current_with_separate_purpose_pins(self):
        pins=re.findall(r'\.equals\(receiver\?"([a-f0-9]{64})":"([a-f0-9]{64})"\)',self.reader)
        self.assertEqual(pins,[(NEW_SOURCE,NEW_SOURCE),(NEW_RECIPE,NEW_RECIPE)])
        self.assertEqual(shader_source_digest(self.generated),pins[0][1])
        self.assertEqual(hashlib.sha256(self.recipe).hexdigest(),pins[1][1])
        mirror=ROOT/'src/main/resources/assets/shaderlod/adapters/patches/adapters/complementary-reimagined.json'
        self.assertEqual(mirror.read_bytes(),self.recipe)
        self.assertEqual(pins[0][0],shader_source_digest(self.generated))
        self.assertEqual(pins[1][0],hashlib.sha256(self.recipe).hexdigest())
        self.assertIn('sourceUncached(ShaderPack p){return sourceUncached(p,SourceReader.ORDINARY);}',self.reader)
        self.assertIn('()->sourceUncached(p,SourceReader.TEMPORAL)',self.reader)
        self.assertIn('()->sourceUncached(p,SourceReader.RECEIVER)',self.reader)
        self.assertIn('boolean receiver=reader==SourceReader.RECEIVER',self.reader)
        self.assertIn('TEMPORAL_SOURCE_CACHE.check(',self.reader)
        self.assertIn('SOURCE_CACHE.check(',self.reader)

    def test_three_purposes_have_separate_memos_keys_and_lifecycle_invalidation(self):
        for cache in ('SOURCE_CACHE','TEMPORAL_SOURCE_CACHE','RECEIVER_SOURCE_CACHE'):
            self.assertIn(cache+'=new com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache()',self.reader)
        for purpose in ('ORDINARY','TEMPORAL','RECEIVER'):
            self.assertIn('java.util.Arrays.asList(SourceReader.'+purpose+',OptimizationRuntimeContext.core()',self.reader)
        for method,stop in (('invalidate()','public static void destroy'),('destroy(Object p)','public static void prepared')):
            body=self.reader.split('public static void '+method,1)[1].split(stop,1)[0]
            for cache in ('SOURCE_CACHE','TEMPORAL_SOURCE_CACHE','RECEIVER_SOURCE_CACHE'):
                self.assertIn(cache+'.invalidate()',body)
        for purpose, method, cache in (('ORDINARY','cachedSource','SOURCE_CACHE'),
                ('TEMPORAL','cachedTemporalSource','TEMPORAL_SOURCE_CACHE'),
                ('RECEIVER','cachedReceiverSource','RECEIVER_SOURCE_CACHE')):
            self.assertIn('case '+purpose+'->'+method+'(pack)',self.reader)
            body=self.reader.split('private static boolean '+method+'(ShaderPack p)',1)[1].split('private static boolean ',1)[0]
            self.assertIn(cache+'.check(',body)
            for other in {'SOURCE_CACHE','TEMPORAL_SOURCE_CACHE','RECEIVER_SOURCE_CACHE'}-{cache}:
                self.assertNotIn(other+'.check(',body.replace(cache+'.check(',''))

    def test_untimed_probe_reports_distinct_temporal_and_receiver_state(self):
        probe=(ROOT/'src/devClient/java/com/pp/shaderlod/dev/BenchmarkTunerProbe.java').read_text()
        for field in ('temporal_status','receiver_status','receiver_source_admitted'):
            self.assertIn('result.addProperty("'+field+'"',probe)
        self.assertIn('!com.pp.shaderlod.measurement.RuntimeMeasurement.requested()',probe)


    def test_tampered_source_recipe_and_native_profile_remain_outside_pins(self):
        changed=dict(self.generated);changed['shaders/program/gbuffers_terrain.glsl']+=b'\n// unowned edit\n'
        self.assertNotEqual(shader_source_digest(changed),NEW_SOURCE)
        self.assertNotEqual(hashlib.sha256(self.recipe+b' ').hexdigest(),NEW_RECIPE)
        self.assertIn('if(!(reader!=SourceReader.ORDINARY?temporalProfile(m):profile(m)))return false;',self.reader)
        self.assertIn('GeneratedRendererOptionContract.accepts("complementary-reimagined",m)',self.reader)
        self.assertIn('PackSelectionService.verifyGeneratedRendererGraph(path,m)',self.reader)
        profile=json.loads((ROOT/'src/main/resources/assets/shaderlod/rendererquad/generated-main-options.json').read_text())['complementary-reimagined']
        self.assertEqual(len(profile),411)
        actual=ROOT/'run-workloads/performance-readiness-20261016/ao-r39/reimagined/receipts/profile_6dffaedf1b3d.json'
        if actual.exists():
            values=json.loads(actual.read_text())['effective_shader_options']
            self.assertEqual(set(values),set(profile))
            normalized=dict(values)
            for suffix in ('DEPTH_PRIMING','AO_HALF_RES','DEAD_MIP_CHAIN','SPATIAL_REUSE','SPATIAL_RATE','PIXEL_GRAPH'):
                key='SHADERLOD_REIMAGINED_'+suffix
                self.assertIn(normalized[key],('0','1'));normalized[key]='0'
            self.assertEqual(normalized,profile)

    def test_existing_off_quality_native_alpha_sampler_and_revocation_gates_remain(self):
        for guard in ('core.preset()==LodPreset.QUALITY','OptimizationId.EARLY_ALPHA','Iris.isFallback()',
                'Iris.getShaderPackOptionQueue().isEmpty()','MakeUpQuadRuntime.loadedStackMatches()',
                'MakeUpQuadRuntime.resourcesMatch(', 'ReimaginedEarlyAlphaPlugin.exactApi()',
                'ReimaginedEarlyAlphaContract.alpha(ref)','ReimaginedEarlyAlphaContract.sampler(',
                'GL_SAMPLE_ALPHA_TO_COVERAGE','GL20.glUniform1i(l,0)',
                'shaderlodReimaginedEarlyAlphaReference','shaderlodReimaginedEarlyAlphaPermission'):
            self.assertIn(guard,self.reader)
        self.assertIn('receiverSourceForDraw(p,pipe,pack)',self.reader) # Receiver retains its independent source purpose.
        self.assertIn('drawTemporalSource(pack)',self.reader) # Temporal draw remains on its current policy.


if __name__=='__main__':unittest.main()

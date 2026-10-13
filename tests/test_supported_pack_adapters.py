"""Pinned source/assembly checks; no GLSL driver, Iris, DH, PBR or GPU evidence."""
import copy
import hashlib
import json
import shutil
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from build_supported_pack_adapters import (apply, build, read_archive, shader_source_digest,
    sync_resources, catalog_update, CATALOG, RESOURCE_ROOT)

class SupportedPackAdapterTests(unittest.TestCase):
    def manifests(self, kinds=('ssr-hit-refinement', 'volumetric-sample-budget')):
        manifests = [json.loads(p.read_text()) for p in sorted((ROOT / 'patches/adapters').glob('*.json')) if p.name != 'catalog.json']
        return [manifest for manifest in manifests if manifest.get('kind') in kinds]

    def fixture(self, manifest):
        files = {n: b'fixture\n' for n in manifest['source_members']}
        for name in {p['path'] for p in manifest['patches']}:
            files[name] = '\nfixture separator\n'.join(p['before'] for p in manifest['patches'] if p['path'] == name).encode()
        files['unrelated-readme'] = b'original credit'
        manifest = copy.deepcopy(manifest)
        manifest['source_members'] = {n: hashlib.sha256(files[n]).hexdigest() for n in manifest['source_members']}
        manifest['shader_source_digest'] = shader_source_digest(files)
        return files, manifest

    def test_all_pinned_members_and_unique_anchors_fail_closed(self):
        for m in self.manifests():
            files,m=self.fixture(m)
            result=apply(files,m)
            self.assertEqual(result['unrelated-readme'],files['unrelated-readme'])
            for name in m['source_members']:
                changed=files.copy();changed[name]+=b'drift'
                with self.assertRaisesRegex(ValueError,'Pinned (shader source identity|source) changed'):apply(changed,m)
            changed=copy.deepcopy(m);changed['patches'][0]['before']=''
            with self.assertRaisesRegex(ValueError,'exactly once'):apply(files,changed)
            changed=copy.deepcopy(m);changed['patches'][0]['path']='unrelated-readme'
            with self.assertRaisesRegex(ValueError,'Unpinned'):apply(files,changed)
            changed=copy.deepcopy(m);changed['additions']['unrelated-readme']=next(iter(m['additions'].values()))
            with self.assertRaisesRegex(ValueError,'collides'):apply(files,changed)

    def test_determinism_preserves_attribution_and_validation_labels(self):
        for m in self.manifests():
            files,m=self.fixture(m)
            with tempfile.TemporaryDirectory() as td:
                td=Path(td);base=td/'original.zip';manifest=td/'manifest.json'
                with zipfile.ZipFile(base,'w') as z:
                    for n,b in files.items():z.writestr(n,b)
                manifest.write_text(json.dumps(m));before=base.read_bytes()
                first=build(base,td/'one.zip',manifest);second=build(base,td/'two.zip',manifest)
                self.assertEqual((td/'one.zip').read_bytes(),(td/'two.zip').read_bytes())
                self.assertEqual(base.read_bytes(),before)
                self.assertFalse(any(first['validation'].values()))
                self.assertFalse(first['default_enabled'])
                out=read_archive(td/'one.zip')
                for name in files:
                    if name not in first['modified_upstream_members']:self.assertEqual(files[name],out[name])
                with self.assertRaisesRegex(ValueError,'overwrite'):build(base,base,manifest)
                with self.assertRaisesRegex(ValueError,'already exists'):build(base,td/'one.zip',manifest)

    def test_all_shader_stages_and_control_properties_are_identity(self):
        files={"shaders/entry.fsh":b"original"}
        before=shader_source_digest(files)
        for ext in (".gsh", ".csh", ".tcs", ".tes", ".comp", ".geom", ".tesc", ".tese"):
            self.assertNotEqual(before,shader_source_digest({**files,"shaders/stage"+ext:b"changed"}))
        self.assertNotEqual(before,shader_source_digest({**files,"shaders/block.properties":b"changed"}))
        self.assertEqual(before,shader_source_digest({**files,"README.md":b"changed","shaders/lang/en_us.lang":b"changed"}))

    def test_archive_entry_guards(self):
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)/'bad.zip'
            for name in ('../escape','/absolute','shaders\\escape'):
                with zipfile.ZipFile(p,'w') as z:z.writestr(name,b'x')
                with self.assertRaisesRegex(ValueError,'Unsafe'):read_archive(p)

    def test_shared_budget_fails_full_and_only_hit_refinement_is_budgeted(self):
        text=(ROOT/'patches/adapters/shared/refinement_budget.glsl').read_text()
        self.assertIn('bridge != 2 || enabled != 1 || preset < 0 || preset > 2',text)
        self.assertIn('!(distance_blocks >= 0.0 && distance_blocks <= 16777216.0)',text)
        self.assertIn('shaderlod_refinement_blend(distance_blocks, shaderlodNear)',text)
        self.assertIn('shaderlod_refinement_blend(distance_blocks, shaderlodFar)',text)
        self.assertIn('shaderlodTransition * 0.5 <= shaderlodNear)) return original;',text)
        self.assertIn('boundary - 0.5 * shaderlodTransition',text)
        self.assertIn('boundary + 0.5 * shaderlodTransition',text)
        self.assertNotIn('texture',text);self.assertNotIn('frame',text)
        for m in self.manifests(('ssr-hit-refinement',)):
            binding=(ROOT/m['additions']['shaders/lib/shaderlod/adapter.glsl']['path']).read_text()
            self.assertIn('#define SHADERLOD_REFINEMENT_ADAPTER 0 // [0 1]',binding)
            # Reflection invariants apply to reflection consumers, not independent
            # volume/AO patches carried by the same maintained recipe.
            edits='\n'.join(p['after'] for p in m['patches'] if
                any(part in p['path'].lower() for part in ('reflection', 'water.glsl')))
            self.assertIn('!defined DISTANT_HORIZONS && !defined VOXY',edits)
            if m['id'].startswith('makeup'):
                self.assertIn('for (int i = 0; i < RAYMARCH_STEPS; i++)',edits)
                self.assertIn('if (searchFlag && shaderlodRefinements >= shaderlodRefinementLimit) break;',edits)
            elif m['id'] in ('bsl-shaders', 'solas-shader', 'astralex'):
                self.assertIn('length(viewPos)',edits)
                self.assertIn('shaderlod_refinement_budget',edits)
                self.assertNotIn('for (',edits)
                self.assertFalse(any('raytrace' in p['path'].lower() for p in m['patches']))
            else:
                self.assertIn('refinementCount = shaderlod_refinement_budget',edits)
                self.assertNotIn('sampleCount =',edits)
            for p in m['patches']:
                self.assertNotIn('reflection.rgb',p['after']);self.assertNotIn('reflection.a',p['after'])

    def test_available_real_sources_assemble_only_declared_members(self):
        locations={'complementary-reimagined':'dev/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip',
            'complementary-unbound':'dev/upstream/pack-expansion/ComplementaryUnbound_r5.9.3.zip',
            'makeup-ultra-fast-shaders':'dev/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip',
            'bsl-shaders':'dev/upstream/other-pack-research/BSL_v10.1.8.zip',
            'solas-shader':'dev/upstream/pack-expansion/Solas Shader V3.7b.zip',
            'astralex':'dev/upstream/pack-expansion/§r§lAstra§4§lLex§r§l_By_LexBoosT_§4§lV93.0§r§l.zip',
            'bliss-shader':'dev/upstream/pack-expansion/Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip',
            'sildurs-vibrant-shaders':"dev/upstream/pack-expansion/Sildur's Vibrant Shaders v2.02 Extreme.zip"}
        for m in self.manifests():
            p=ROOT/locations[m['id']]
            if not p.exists():continue
            files=read_archive(p);result=apply(files,m)
            changed={n for n in files if files[n]!=result[n]}
            self.assertEqual(changed,{p['path'] for p in m['patches']})
            # Original shader quality profiles and DH programs untouched except options screen.
            for n in files:
                if 'dh_' in n or '/DH_' in n or n.endswith('water_dh.glsl'):self.assertEqual(files[n],result[n])

    def test_new_water_bindings_preserve_coarse_and_pbr_consumers(self):
        sources={'bsl-shaders':'dev/upstream/other-pack-research/BSL_v10.1.8.zip',
            'solas-shader':'dev/upstream/pack-expansion/Solas Shader V3.7b.zip'}
        for m in self.manifests():
            if m['id'] not in sources:continue
            edits='\n'.join(p['after'] for p in m['patches'] if
                any(part in p['path'].lower() for part in ('reflection', 'water.glsl')))
            self.assertNotIn('!defined ADVANCED_MATERIALS',edits)
            if m['id']=='bsl-shaders':
                self.assertIn('length(viewPos), maxf, 3)',edits)
                self.assertIn('defined SHADERLOD_BSL_WATER',edits)
                untouched=('shaders/lib/reflections/raytrace.glsl',
                    'shaders/lib/reflections/roughReflections.glsl','shaders/lib/settings.glsl')
            else:
                self.assertIn('shaderlodRefinementCount, 4)',edits)
                self.assertIn('!defined GBUFFERS_WATER_COLORWHEEL',edits)
                untouched=('shaders/lib/pbr/raytracer.glsl',
                    'shaders/lib/pbr/simpleReflection.glsl','shaders/lib/common.glsl')
            source=ROOT/sources[m['id']]
            if not source.exists():continue
            files=read_archive(source);result=apply(files,m)
            for name in untouched:self.assertEqual(files[name],result[name])
            self.assertEqual([l for l in files['shaders/shaders.properties'].splitlines() if l.startswith(b'profile.')],
                [l for l in result['shaders/shaders.properties'].splitlines() if l.startswith(b'profile.')])

    def test_remaining_pack_effect_semantics_and_original_controls(self):
        for id in ('astralex', 'bliss-shader', 'sildurs-vibrant-shaders'):
            m=json.loads((ROOT/'patches/adapters'/ (id+'.json')).read_text())
            edits='\n'.join(p['after'] for p in m['patches'])
            self.assertIn('!defined DISTANT_HORIZONS && !defined VOXY',edits)
            if id=='astralex':
                self.assertIn('length(viewPos), 6, 5)',edits)
                self.assertIn('shaderlodNearHit = shaderlodNearHit || (dist < difFactor && err > difFactor)',edits)
                self.assertIn('shaderlodNearHit ? 6 : shaderlodRefinementCount',edits)
                self.assertNotIn('vector *=',edits)
            else:
                self.assertEqual(m['kind'],'volumetric-sample-budget')
                binding=(ROOT/m['additions']['shaders/lib/shaderlod/adapter.glsl']['path']).read_text()
                self.assertIn('#define SHADERLOD_EFFECT_ADAPTER 0 // [0 1]',binding)
                self.assertNotIn('distance',binding)
                if id=='bliss-shader':
                    self.assertIn('shaderlod_effect_budget(SAMPLECOUNT, 4)',edits)
                    self.assertFalse(any('specular' in p['path'] or 'translucent.fsh' in p['path'] for p in m['patches']))
                else:
                    self.assertIn('shaderlod_effect_budget(6, 5)',edits)
                    self.assertIn('1.0 / float(shaderlodSampleCount)',edits)
                    self.assertIn('vL *= 6.0 / float(shaderlodSampleCount)',edits)
                    self.assertFalse(any('world' in p['path'] for p in m['patches']))
        helper=(ROOT/'patches/adapters/shared/uniform_sample_budget.glsl').read_text()
        self.assertIn('bridge != 2 || enabled != 1 || preset < 0 || preset > 2',helper)
        self.assertNotIn('texture(',helper);self.assertNotIn('dFdx',helper)

class AdapterResourceSyncTests(unittest.TestCase):
    adapter = 'bsl-shaders'
    original = ROOT / 'dev/upstream/other-pack-research/BSL_v10.1.8.zip'

    def setUp(self):
        if not self.original.exists():
            self.skipTest('Authentic pinned BSL archive unavailable')
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        shutil.copytree(ROOT / 'patches/adapters', self.root / 'patches/adapters')
        shutil.copytree(ROOT / RESOURCE_ROOT, self.root / RESOURCE_ROOT)
        (self.root / CATALOG).parent.mkdir(parents=True)
        shutil.copyfile(ROOT / CATALOG, self.root / CATALOG)
        self.recipe = self.root / 'patches/adapters/bsl-shaders.json'

    def snapshot(self):
        return {str(p.relative_to(self.root)): p.read_bytes()
            for p in self.root.rglob('*') if p.is_file()}

    def changed_recipe(self):
        manifest = json.loads(self.recipe.read_bytes())
        # An authored source change on the genuine pinned input, not a fabricated
        # archive or refreshed source pin. Only generated output identity changes.
        manifest['patches'][0]['after'] = '// authoring sync fixture\n' + manifest['patches'][0]['after']
        self.recipe.write_text(json.dumps(manifest, indent=2) + '\n')
        return manifest

    def test_real_original_dry_run_apply_and_idempotence(self):
        before = self.snapshot()
        self.assertEqual(sync_resources(self.original, self.adapter, self.root)['changed_files'], [])
        self.assertEqual(before, self.snapshot())
        manifest = self.changed_recipe()
        before = self.snapshot()
        planned = sync_resources(self.original, self.adapter, self.root)
        self.assertFalse(planned['applied'])
        self.assertEqual(set(planned['changed_files']), {RESOURCE_ROOT + 'patches/adapters/bsl-shaders.json', CATALOG})
        self.assertEqual(before, self.snapshot())
        old_catalog = (self.root / CATALOG).read_text()
        old_digest = shader_source_digest(apply(read_archive(self.original),
            json.loads((ROOT / 'patches/adapters/bsl-shaders.json').read_bytes())))
        applied = sync_resources(self.original, self.adapter, self.root, write=True)
        self.assertEqual(planned['changed_files'], applied['changed_files'])
        self.assertEqual((self.root / (RESOURCE_ROOT + 'patches/adapters/bsl-shaders.json')).read_bytes(), self.recipe.read_bytes())
        catalog = (self.root / CATALOG).read_text()
        self.assertIn(old_digest, catalog.split('LEGACY_ADAPTER_DIGESTS = Map.ofEntries(')[1])
        self.assertIn(manifest['shader_source_digest'], catalog)
        # Every unrelated family entry and previous legacy identity is retained.
        import re
        for digest in re.findall(r'"([0-9a-f]{64})"', old_catalog):
            self.assertIn(digest, catalog)
        after = self.snapshot()
        self.assertEqual(sync_resources(self.original, self.adapter, self.root, write=True)['changed_files'], [])
        self.assertEqual(after, self.snapshot())

    def test_declared_helper_and_mirror_drift_are_propagated(self):
        manifest = self.changed_recipe()
        helper = manifest['additions']['shaders/lib/shaderlod/adapter.glsl']
        source = self.root / helper['path']
        source.write_bytes(source.read_bytes() + b'\n// authored local binding\n')
        helper['sha256'] = hashlib.sha256(source.read_bytes()).hexdigest()
        self.recipe.write_text(json.dumps(manifest))
        mirror = self.root / (RESOURCE_ROOT + helper['path'])
        mirror.write_bytes(b'stale mirror')
        sync_resources(self.original, self.adapter, self.root, write=True)
        self.assertEqual(mirror.read_bytes(), source.read_bytes())

    def test_bad_helper_and_shared_consumer_refuse_before_writes(self):
        manifest = json.loads(self.recipe.read_bytes())
        helper = manifest['additions']['shaders/lib/shaderlod/uniform_sample_budget.glsl']
        source = self.root / helper['path']
        source.write_bytes(source.read_bytes() + b'\n// changed shared policy\n')
        for update_selected in (False, True):
            if update_selected:
                helper['sha256'] = hashlib.sha256(source.read_bytes()).hexdigest()
                self.recipe.write_text(json.dumps(manifest))
            before = self.snapshot()
            with self.assertRaisesRegex(ValueError, 'consumer pin mismatch'):
                sync_resources(self.original, self.adapter, self.root, write=True)
            self.assertEqual(before, self.snapshot())

    def test_bad_source_catalog_and_unsafe_path_refuse_before_writes(self):
        manifest = self.changed_recipe()
        original_recipe = self.recipe.read_bytes()
        original_catalog = (self.root / CATALOG).read_bytes()
        cases = ('source', 'catalog', 'duplicate', 'path')
        for case in cases:
            with self.subTest(case=case):
                self.recipe.write_bytes(original_recipe)
                (self.root / CATALOG).write_bytes(original_catalog)
                altered = copy.deepcopy(manifest)
                if case == 'source':
                    altered['shader_source_digest'] = '0' * 64
                elif case == 'catalog':
                    (self.root / CATALOG).write_bytes(original_catalog.replace(manifest['shader_source_digest'].encode(), b'0' * 64))
                elif case == 'duplicate':
                    import re
                    entry = re.search(r'new Pack\("bsl-shaders",.*?\)', original_catalog.decode(), re.S).group()
                    (self.root / CATALOG).write_bytes(original_catalog + entry.encode())
                else:
                    altered['additions']['shaders/lib/shaderlod/adapter.glsl']['path'] = 'patches/adapters/../../../escape'
                self.recipe.write_text(json.dumps(altered))
                before = self.snapshot()
                with self.assertRaises(ValueError):
                    sync_resources(self.original, self.adapter, self.root, write=True)
                self.assertEqual(before, self.snapshot())

    def test_coordinated_source_repin_cannot_break_other_packaged_consumers(self):
        name = 'patches/adapters/shared/uniform_sample_budget.glsl'
        helper = self.root / name
        helper.write_bytes(helper.read_bytes() + b'\n// coordinated source change\n')
        digest = hashlib.sha256(helper.read_bytes()).hexdigest()
        for path in (self.root / 'patches/adapters').glob('*.json'):
            manifest = json.loads(path.read_bytes())
            changed = False
            for addition in manifest.get('additions', {}).values():
                if addition['path'] == name:
                    addition['sha256'] = digest
                    changed = True
            if changed:
                path.write_text(json.dumps(manifest))
        before = self.snapshot()
        with self.assertRaisesRegex(ValueError, 'Packaged shared helper consumer pin mismatch'):
            sync_resources(self.original, self.adapter, self.root, write=True)
        self.assertEqual(before, self.snapshot())

    def test_new_legacy_entry_preserves_previous_current(self):
        source = (self.root / CATALOG).read_text()
        import re
        match = re.search(r'new Pack\("bliss-shader",.*?"([0-9a-f]{64})", "([0-9a-f]{64})"\)', source, re.S)
        original, previous = match.groups()
        updated = catalog_update(source, 'bliss-shader', original, '1' * 64)
        self.assertIn('Map.entry("bliss-shader", List.of("' + previous + '"))', updated)
        self.assertEqual(catalog_update(updated, 'bliss-shader', original, '1' * 64), updated)

if __name__=='__main__':unittest.main()

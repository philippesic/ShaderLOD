#!/usr/bin/env python3
"""Audit the actual built port artifacts; never establishes applied mixins/rendering."""
import hashlib, json, pathlib, xml.etree.ElementTree as ET, zipfile
root = pathlib.Path(__file__).resolve().parent
repository = root.parent.parent
expected = {str(path.relative_to(repository / 'src/main/resources')): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in (repository / 'src/main/resources/assets/shaderlod/adapters').rglob('*') if path.is_file()}
rows = []
for loader in ('neoforge', 'fabric'):
    module = root / loader
    suites = [ET.parse(path).getroot() for path in (module / 'build/test-results/test').glob('TEST-*.xml')]
    counts = {key: sum(int(suite.get(key, '0')) for suite in suites) for key in ('tests', 'failures', 'errors', 'skipped')}
    assert counts['tests'] >= 124 and counts['failures'] == counts['errors'] == counts['skipped'] == 0, (loader, counts)
    jars = list((module / 'build/libs').glob('*.jar')); assert len(jars) == 1, jars
    jar = jars[0]
    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())
        for name, digest in expected.items():
            assert hashlib.sha256(archive.read(name)).hexdigest() == digest, (loader, name)
        assert 'META-INF/LICENSE.shaderlod' in names
        assert not any('/dev/' in name or 'SpatialComposite' in name or 'ShadowCulling' in name for name in names)
        for classfile in ('com/pp/shaderlod/integration/PackSelectionTransaction.class',
                          'com/pp/shaderlod/integration/KnownRuntimeBoundary.class',
                          'com/pp/shaderlod/packs/PackSelectionService.class',
                          'com/pp/shaderlod/mixin/EffectUniformsMixin.class',
                          'com/pp/shaderlod/mixin/IrisWorldCompletionMixin.class'):
            assert classfile in names, (loader, classfile)
        if loader == 'fabric':
            metadata = json.loads(archive.read('fabric.mod.json'))
            assert metadata['depends']['minecraft'] == '1.21.11'
            assert metadata['depends']['iris'] == '1.10.8+mc1.21.11'
            assert metadata['depends']['sodium'] == '0.8.14'
        else:
            metadata = archive.read('META-INF/neoforge.mods.toml').decode()
            for version in ('[1.21.11]', '[1.10.8+mc1.21.11]', '[0.8.14+mc1.21.11]'):
                assert version in metadata, version
    rows.append({'loader': loader, 'jar': str(jar.relative_to(repository)), 'sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
                 'tests': counts, 'adapterResourceCount': len(expected), 'runtimeVerified': False})
    print(loader, counts, len(expected), 'exact adapter resources', rows[-1]['sha256'])
(root / 'evidence/artifact-audit.json').write_text(json.dumps(rows, indent=2) + '\n')

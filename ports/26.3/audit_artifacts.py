#!/usr/bin/env python3
"""Audit the actual built port artifacts; never establishes applied mixins/rendering."""
import hashlib, json, pathlib, subprocess, xml.etree.ElementTree as ET, zipfile
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
        import struct
        assert struct.unpack('>H', archive.read('com/pp/shaderlod/integration/RendererBackend.class')[6:8])[0] == 69, 'Java25 class major'
        assert not any('/dev/' in name or 'SpatialComposite' in name or 'ShadowCulling' in name for name in names)
        for classfile in ('com/pp/shaderlod/integration/PackSelectionTransaction.class',
                          'com/pp/shaderlod/integration/KnownRuntimeBoundary.class',
                          'com/pp/shaderlod/integration/RendererBackend.class',
                          'com/pp/shaderlod/packs/PackSelectionService.class',
                          'com/pp/shaderlod/mixin/EffectUniformsMixin.class',
                          'com/pp/shaderlod/mixin/IrisWorldCompletionMixin.class'):
            assert classfile in names, (loader, classfile)
        if loader == 'fabric':
            metadata = json.loads(archive.read('fabric.mod.json'))
            assert metadata['depends']['minecraft'] == '26.3'
            assert metadata['depends']['java'] == '>=25'
            assert metadata['depends']['fabric-api'] == '0.162.0+26.3'
            assert metadata['depends']['iris'] == '1.11.7+mc26.3'
            assert metadata['depends']['sodium'] == '0.9.2'
        else:
            metadata = archive.read('META-INF/neoforge.mods.toml').decode()
            for version in ('[26.3.0.52-beta]', '[26.3]', '[1.11.7+mc26.3]', '[0.9.2+mc26.3]'):
                assert version in metadata, version
    java = repository / 'dependencies/runtime/jdk25/jdk-25.0.2.jdk/Contents/Home/bin/javap'
    settings = 'com.pp.shaderlod.fabric.FabricLodSettings' if loader == 'fabric' else 'com.pp.shaderlod.ShaderLodSettings'
    code = subprocess.check_output([str(java), '-p', '-c', '-classpath', str(jar), settings, 'com.pp.shaderlod.integration.RendererBackend', 'com.pp.shaderlod.integration.KnownRuntimeBoundary'], text=True)
    (root / 'evidence' / (loader + '-facade-bytecode.txt')).write_text(code)
    assert code.index('RendererBackend.requireApplySupported') < code.index('prepare:(Z)Z') < code.index('Iris.reload:()V'), loader
    assert 'DeviceInfo.backendName' in code and 'GpuDevice.getDeviceInfo' in code
    assert 'GpuDeviceAccessor' not in code and 'backend/opengl/GlDevice' not in code
    assert 'RendererBackend$Refusal' in code
    rows.append({'loader': loader, 'jar': str(jar.relative_to(repository)), 'sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
                 'tests': counts, 'adapterResourceCount': len(expected), 'runtimeVerified': False})
    print(loader, counts, len(expected), 'exact adapter resources', rows[-1]['sha256'])
(root / 'evidence/artifact-audit.json').write_text(json.dumps(rows, indent=2) + '\n')

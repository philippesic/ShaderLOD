#!/usr/bin/env python3
"""Audit this explicit port's actual publisher jars and synchronous GL submission seam."""
import hashlib, io, json, pathlib, re, subprocess, zipfile
root=pathlib.Path(__file__).resolve().parent
repo=root.parents[1]
javap=repo/'dependencies/runtime/jdk25/jdk-25.0.2.jdk/Contents/Home/bin/javap'
lock=json.loads((root/'dependencies.lock.json').read_text())
out=root/'evidence/renderer-speed-r1';out.mkdir(parents=True,exist_ok=True)
results=[]
for loader in ('fabric','neoforge'):
    manifest=json.loads((root/(loader+'-speed-api-pins.json')).read_text())
    sources={};artifact_receipts=[];classpath=[]
    for kind in ('iris','sodium'):
        artifact=next(a for a in lock['artifacts'] if a['loader']==loader and a['kind']==kind)
        path=root/'runtime'/artifact['filename'];data=path.read_bytes()
        for algorithm,digest in artifact['hashes'].items():
            assert hashlib.new(algorithm,data).hexdigest()==digest,(loader,artifact['filename'],algorithm)
        artifact_receipts.append({'path':str(path),'sha256':hashlib.sha256(data).hexdigest()})
        z=zipfile.ZipFile(io.BytesIO(data))
        if kind=='sodium' and loader=='neoforge':
            member=next(n for n in z.namelist() if n.startswith('META-INF/jarjar/') and n.endswith('.jar'))
            data=z.read(member);path=out/'sodium-neoforge-inner.jar';path.write_bytes(data)
            artifact_receipts.append({'parent':artifact['filename'],'member':member,'sha256':hashlib.sha256(data).hexdigest()})
            z=zipfile.ZipFile(io.BytesIO(data))
        classpath.append(str(path));sources.update({n:z.read(n) for n in z.namelist() if n.endswith('.class')})
    minecraft=pathlib.Path(manifest['minecraftConsumerArtifact'])
    data=minecraft.read_bytes();artifact_receipts.append({'path':str(minecraft),'sha256':hashlib.sha256(data).hexdigest()})
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        sources.update({n:z.read(n) for n in manifest['pins'] if n.startswith('com/mojang/')})
    classpath.append(str(minecraft))
    for member,digest in manifest['pins'].items():assert hashlib.sha256(sources[member]).hexdigest()==digest,member
    runtime=(root/loader/'src/main/java/com/pp/shaderlod/integration/makeupquad/MakeUpQuadRuntime.java').read_text()
    backend=(root/loader/'src/main/java/com/pp/shaderlod/integration/RendererBackend.java').read_text()
    for method in re.findall(r'RendererBackend\.(\w+)\(',runtime):
        assert re.search(r'public static [^\n{};]*\b'+re.escape(method)+r'\(',backend),'Local backend method absent: '+loader+': '+method
    assert 'RendererBackend.requireOpenGl();' in runtime,'26.2 must retain exact name/class backend admission'
    java=(root/loader/'src/main/java/com/pp/shaderlod/integration/makeupquad/MakeUpQuadApiPins.java').read_text()
    java_production=java[java.index('private static final String[][] PINS'):java.index('    };')+6]
    java_pins=dict(re.findall(r'\{"([^"]+)","([a-f0-9]{64})"\}',java_production))
    assert java_pins==manifest['pins'],'Java bootstrap and audited pins differ'
    def inspect(tag,classes,verbose=False):
        text=subprocess.check_output([str(javap),'-p','-s','-v' if verbose else '-c','-classpath',':'.join(classpath),*classes],text=True)
        (out/(loader+'-'+tag+'.txt')).write_text(text);return text
    batch=inspect('batch',['net.caffeinemc.mods.sodium.client.gpu.device.batch.GLDrawBatch','net.caffeinemc.mods.sodium.client.gpu.device.batch.MultiDrawBatch'])
    renderer=inspect('renderer',['net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer','net.caffeinemc.mods.sodium.client.render.chunk.SharedQuadIndexBuffer$IndexFormat$1'])
    transformer=inspect('transformer',['net.irisshaders.iris.pipeline.transform.transformer.SodiumTransformer'],True)
    prefix='com.mojang.blaze3d.opengl.' if root.name=='26.2' else 'com.mojang.renderpearl.backend.opengl.'
    consumer=inspect('consumer',[prefix+'GlRenderPass',prefix+'GlCommandEncoder'])
    assert manifest['renderDescriptor'] in renderer,'Exact redirect descriptor changed'
    assert 'MultiDrawBatch.draw:' in renderer,'No native batch draw invocation'
    assert 'MultiDrawBatch.isFilled:Z' in renderer,'Expected cached batch lifecycle absent'
    assert 'pElementPointer' in batch and 'pElementCount' in batch and 'pBaseVertex' in batch
    assert 'multiDrawIndexed:' in batch and 'updateMaxElementCount' in batch
    assert '_draw_id = a_LightAndData[3]' in transformer,'Chunk identity is no longer a vertex attribute'
    assert 'nglMultiDrawElementsBaseVertex' in consumer and 'executeDraws:' in consumer,'Synchronous GL consumer changed'
    assert 'net/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch.class' not in sources,'Old seam unexpectedly present'
    old=(repo/'src/main/java/com/pp/shaderlod/integration/makeupquad/MakeUpQuadApiPins.java').read_text()
    old_pins=dict(re.findall(r'\{"([^"]+)","([a-f0-9]{64})"\}',old))
    wrong=[p for p,h in old_pins.items() if p not in sources or hashlib.sha256(sources[p]).hexdigest()!=h]
    assert wrong,'Old API gate must refuse actual new jars'
    development=None
    if loader=='fabric':
        development=pathlib.Path(manifest['loomDevelopmentMinecraftConsumerArtifact'])
        dev_sources=dict(sources)
        with zipfile.ZipFile(development) as z:
            for member in manifest['loomDevelopmentPins']:
                if member.startswith('com/mojang/'):dev_sources[member]=z.read(member)
        for member,digest in manifest['loomDevelopmentPins'].items():assert hashlib.sha256(dev_sources[member]).hexdigest()==digest,member
        dev_java=java[java.index('private static final String[][] LOOM_DEVELOPMENT_PINS'):java.index('    };',java.index('private static final String[][] LOOM_DEVELOPMENT_PINS'))+6]
        assert dict(re.findall(r'\{"([^"]+)","([a-f0-9]{64})"\}',dev_java))==manifest['loomDevelopmentPins']
        dev_consumer=subprocess.check_output([str(javap),'-p','-s','-c','-classpath',str(development),prefix+'GlRenderPass',prefix+'GlCommandEncoder'],text=True)
        (out/(loader+'-loom-consumer.txt')).write_text(dev_consumer)
        assert 'executeDraws:' in dev_consumer and 'nglMultiDrawElementsBaseVertex' in dev_consumer
        # Actual mixed production/development bytes must match neither whole tuple.
        changed=[m for m,h in manifest['pins'].items() if h!=manifest['loomDevelopmentPins'][m]]
        assert len(changed)==2,changed
        mixed=dict(dev_sources);mixed[changed[0]]=sources[changed[0]]
        assert all(not all(hashlib.sha256(mixed[m]).hexdigest()==h for m,h in profile.items()) for profile in (manifest['pins'],manifest['loomDevelopmentPins']))
    results.append({'loader':loader,'minecraft':root.name,'artifactPins':artifact_receipts,'classPins':len(manifest['pins']),
                    'loomDevelopmentArtifact':str(development) if development else None,'completeProfileMixRefuses':True if development else None,'oldApiRefusedMembers':wrong,'staticSeam':'PASS','runtimeDriverQualitySpeed':'UNVERIFIED'})
(root/'evidence/renderer-speed-r1/audit.json').write_text(json.dumps(results,indent=2)+'\n')
print(root.name+': both exact loader binary seams PASS; old API admission refuses; runtime/quality/speed UNVERIFIED')

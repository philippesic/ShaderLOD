"""New N02 Nether LabPBR resource deletion, with an explicit unscored bootstrap."""
from pathlib import Path
import build_photon_pbr_attachment_elision as producer
import benchmark_native_mips as staging
import benchmark_private_off as private_off
import benchmark_bliss_exact_ao as source_text
import experiment_lifecycle as life


def identity(pack, options, bootstrap_options):
    native = producer.validate(pack, options)
    producer.native_reference.validate(pack, bootstrap_options)
    inputs = [Path(__file__).resolve(), Path(producer.__file__).resolve(),
              Path(producer.native_reference.__file__).resolve(), Path(staging.__file__).resolve(),
              Path(private_off.__file__).resolve(), Path(source_text.__file__).resolve(),
              producer.PROFILE, Path(options).resolve(), Path(bootstrap_options).resolve()]
    pin = life.digest(inputs[0])[:12]
    return dict(candidate='n02-photon-pbr-resources', family='photon',
                original_sha256=producer.ORIGINAL, native_options=native,
                required_dimension='minecraft:the_nether',
                producers={str(p): life.digest(p) for p in inputs},
                generated='ShaderLOD-N02-Photon-PBR-resources-' + pin + '.zip',
                qualified_original='ShaderLOD-N02-Photon-PBR-native-' + pin + '.zip',
                scope='Native LabPBR/POM and AA throughout qualified Nether arms. Base native366 is used only to enter Nether before PBR activation and conditioning. No DH/Voxy or public promotion.')


def stage(op, pack, options, identity):
    result = staging.stage(op, pack, options, identity, build=producer.build)
    directory = op.output / 'g02-inputs'
    original = directory / identity['qualified_original']
    original.write_bytes(Path(pack).read_bytes())
    sidecar = Path(str(original) + '.txt')
    sidecar.write_bytes(Path(options).read_bytes())
    expected = {name: row['expected'] for name, row in op.data['configuration'].items()}
    op.begin_mutation('stage-n02-photon-pbr-native')
    try:
        for source in [original, sidecar]:
            target = Path(op.data['profile']) / 'shaderpacks' / source.name
            life.require(str(target) in expected and life.digest(target) == expected[str(target)],
                         'Qualified Photon native staging target changed')
            target.write_bytes(source.read_bytes())
            expected[str(target)] = life.digest(source)
    finally:
        life.require(all(life.digest(name) == pin for name, pin in expected.items()),
                     'Qualified Photon staging ownership uncertain')
        op.acknowledge_mutation('stage-n02-photon-pbr-native')
    op.pin([original, sidecar])
    return result | {'qualified_original_sha256': life.digest(original)}


def activation(state, identity, *, expected_pack):
    staging.activation(state, identity, expected_pack=expected_pack)
    life.require(state.get('dimension') == 'minecraft:the_nether'
                 and state.get('dh_installed') is False and 'voxy' not in state.get('loaded_stack', {})
                 and state.get('fallback') is False
                 and (state.get('width'), state.get('height')) == (2560, 1600),
                 'Actual native PBR Nether at native extent without DH/Voxy required')


def source_work(packet, *, active):
    actual = packet['actual']
    life.require(actual.get('stopped') is True and actual.get('completed_frames') == 2
                 and not actual.get('error'), 'Stopped complete PBR resource observation required')
    starts = [e for e in actual['events'] if e['kind'] == 'frame_start']
    life.require([e['frame'] for e in starts] == [1, 2, 3], 'PBR frame/reset sequence differs')
    rows = []
    for start in starts:
        images = {i['target']: i for i in start['images']}
        life.require(all((t not in images) if active else t in images and
                         all(images[t][bank].get('exists') is True for bank in ['main', 'alt'])
                         for t in range(9, 13)), 'Actual unused PBR cloud allocation differs')
        if start['frame'] == 3:
            continue
        events = [e for e in actual['events'] if e['frame'] == start['frame']]
        banks = {i[b]['id']: (i['target'], b) for i in images.values() for b in ['main', 'alt']}
        life.require(len(banks) == 2 * len(images), 'Ambiguous owned PBR images')
        def join(tex):
            key = tex['id'] if isinstance(tex, dict) else tex
            life.require(key in banks, 'Unowned PBR image')
            return banks[key]
        finals = [e for e in events if e['kind'] == 'final_return']
        life.require(len(finals) == 1 and finals[0]['completed_frames'] == start['frame'],
                     'Actual PBR final return missing')
        signature = []
        for event in events:
            kind = event['kind']
            if kind in ['terrain_setup', 'completed_quad']:
                life.require(not any(9 <= r['target'] <= 12 for r in event['reads']),
                             'Unexpected live PBR cloud sampler')
                if event['name'] in ['deferred1', 'deferred2']:
                    life.require(not active, 'Deleted PBR cloud pass still executed')
                    continue
                writes = []
                for w in event['writes']:
                    if event['name'] == 'final':
                        owner = event.get('presentation_target', {})
                        life.require(w['location'] == 0 and owner.get('texture') == w['texture']
                                     and w['texture'] not in banks
                                     and (owner.get('width'), owner.get('height')) == (2560, 1600),
                                     'PBR presentation ownership differs')
                        writes.append((0, ('presentation', 'color')))
                    else:
                        writes.append((w['location'], join(w['texture'])))
                signature.append((kind, event['name'], [(r['target'], join(r['texture']))
                                  for r in event['reads']], writes, event['raster_state']))
            elif kind == 'mip_return':
                image = join(event['texture'])
                if image[0] == 11:
                    life.require(not active, 'Deleted PBR RT11 mip still executed')
                    continue
                signature.append((kind, image, event['read_alt']))
            elif kind == 'swap_copy_return':
                src, dst = join(event['source_texture']), join(event['destination_texture'])
                life.require(src[0] == dst[0] and src != dst and dst[1] == 'main',
                             'PBR copy ownership differs')
                if 9 <= dst[0] <= 12:
                    life.require(not active, 'Unallocated PBR cloud copy still executed')
                    continue
                signature.append((kind, src, dst))
            if kind in ['terrain_setup', 'completed_quad', 'mip_return', 'swap_copy_return']:
                life.require(events.index(event) < events.index(finals[0]), 'PBR work after final return')
        names = [e['name'] for e in events if e['kind'] == 'completed_quad']
        life.require(all(names.count(n) == (0 if active else 1) for n in ['deferred1', 'deferred2']),
                     'PBR cloud pass count differs')
        rows.append(dict(frame=start['frame'], physical_signature=signature,
                         absent_targets=list(range(9, 13)) if active else []))
    return {'frames': rows, 'scope': 'Actual allocation, source and returned work at this scene; not material-branch counts or comprehensive POM/visual compatibility.'}


def assess(observations):
    native = {s['name']: s for s in observations['native']['actual']['sources']}
    result = {}
    for label, packet in observations.items():
        active = label == 'candidate'
        result[label] = source_work(packet, active=active)
        sources = {s['name']: s for s in packet['actual']['sources']}
        life.require(set(sources) == set(native) - ({'deferred1', 'deferred2'} if active else set()),
                     'Another selected PBR program changed')
        for name, source in sources.items():
            for key in ['vertex_source', 'fragment_source']:
                life.require(source_text.canonical(source[key]) == source_text.canonical(native[name][key]),
                             'Another attached PBR operation changed: ' + name + '/' + key)
            for key in ['parsed_flips', 'parsed_draw_buffers', 'parsed_mips']:
                expected = [5] if active and name == 'deferred4' and key == 'parsed_mips' else native[name].get(key)
                life.require(source.get(key) == expected, 'Another PBR directive changed')
        if label != 'native':
            life.require([r['physical_signature'] for r in result[label]['frames']]
                         == [r['physical_signature'] for r in result['native']['frames']],
                         'Other PBR physical work changed')
    return result

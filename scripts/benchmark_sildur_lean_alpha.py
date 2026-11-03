"""Private original Sildur PCSS alpha contract; full attached-source comparison."""
import hashlib
import re
from pathlib import Path

import build_sildur_lean_alpha as producer
import benchmark_bliss_exact_ao as text_contract
import benchmark_native_mips as staging
import benchmark_private_off as private_off
import experiment_lifecycle as life


def identity(pack, options):
    native = producer.validate(pack, options)
    inputs = [Path(producer.__file__).resolve(), Path(__file__).resolve(), producer.PROFILE,
              Path(staging.__file__).resolve(), Path(private_off.__file__).resolve(),
              Path(text_contract.__file__).resolve()]
    return {'candidate': 'n04-sildur-lean-alpha', 'family': 'sildurs-vibrant-shaders',
            'original_sha256': producer.ORIGINAL, 'native_options': native,
            'producers': {str(p): life.digest(p) for p in inputs},
            'generated': 'ShaderLOD-N04-Sildur-alpha-' + life.digest(inputs[0])[:12] + '.zip',
            'required_dimension': 'minecraft:overworld',
            'scope': 'Private native115 terrain-only alpha guard; no DH/Voxy or public requests. Sampler/current-alpha/source proof required before permission.'}


def stage(op, pack, options, contract):
    return staging.stage(op, pack, options, contract, build=producer.build)


def activation(state, contract, *, expected_pack):
    staging.activation(state, contract, expected_pack=expected_pack)
    life.require(state.get('dimension') == 'minecraft:overworld' and state.get('dh_installed') is False
                 and 'voxy' not in state.get('loaded_stack', {}) and state.get('fallback') is False,
                 'Private Sildur requires Overworld without DH/Voxy/fallback')


def canonical(text):
    # Iris emits modern texture(), and normalizes native float literal suffixes.
    return text_contract.canonical(text.replace('texture2DLod(', 'textureLod(').replace('texture2D(', 'texture(')).replace('#extensionGL_ARB_shader_texture_lod:enable','')


# Iris's translucent Sodium pass uses gbuffers_water, whose file remains native.
TERRAIN = {'sodium_solid', 'sodium_cutout'}


def source_work(packet, *, active):
    actual = packet['actual']
    life.require(actual.get('settled_rearm') is True, 'Settled Sildur terrain-source receipt required')
    sources = {r['name']: r for r in actual['sources']}
    life.require('sodium_cutout' in sources, 'Actual Sildur Sodium CUTOUT source required')
    for name in TERRAIN & sources.keys():
        fragment = canonical(sources[name]['fragment_source'])
        life.require(fragment.count(canonical(producer.GUARD)) == int(active)
                     and fragment.count(canonical(producer.HELPER)) == int(active),
                     'Actual private Sildur guard/helper differs: ' + name)
        for frame in [1, 2]:
            life.require(any(e['kind'] == 'terrain_setup' and e['frame'] == frame and e['name'] == name
                             for e in actual['events']), 'Actual settled Sildur terrain setup missing: ' + name)
    if 'sodium_translucent' in sources:
        water=canonical(sources['sodium_translucent']['fragment_source'])
        life.require(canonical(producer.GUARD) not in water and canonical(producer.HELPER) not in water,
                     'Private Sildur changed the native water shader')
    cutout = canonical(sources['sodium_cutout']['fragment_source'])
    life.require('uniformfloatiris_currentAlphaTest;' in cutout
                 and re.search(r'if\(!\(iris_FragData0\.a>iris_currentAlphaTest\)\)\{discard;\}\}$', cutout),
                 'Actual native Iris GREATER alpha test differs')
    return {'compiled_guard': active, 'program': sources['sodium_cutout']['program'],
            'observed_terrain': sorted(TERRAIN & sources.keys()),
            'vertex_sha256': hashlib.sha256(sources['sodium_cutout']['vertex_source'].encode()).hexdigest(),
            'fragment_sha256': hashlib.sha256(sources['sodium_cutout']['fragment_source'].encode()).hexdigest(),
            'scope': 'Actual attached terrain sources and setups; not completed draws or avoided fragments.'}


def assess(observations):
    native = {s['name']: s for s in observations['native']['actual']['sources']}
    result = {}
    for label, packet in observations.items():
        active = label == 'candidate'
        result[label] = source_work(packet, active=active)
        sources = {s['name']: s for s in packet['actual']['sources']}
        life.require(sources.keys() == native.keys(), 'Private Sildur selected program set differs')
        for name, source in sources.items():
            for stage in ['vertex_source', 'fragment_source']:
                value = canonical(source[stage])
                if active and name in TERRAIN and stage == 'fragment_source':
                    value = value.replace(canonical(producer.HELPER), '', 1).replace(canonical(producer.GUARD), '', 1)
                    value=value.replace(canonical(producer.REWRITTEN_QUERY),canonical(producer.QUERY))
                life.require(value == canonical(native[name][stage]),
                             'Private Sildur changed another attached operation: ' + name + '/' + stage)
            for key in ['parsed_mips', 'parsed_flips', 'parsed_draw_buffers']:
                life.require(source.get(key) == native[name].get(key), 'Private Sildur changed pass directives')
    return result


def arm(control, observations, contract, archive_sha):
    # Full source comparison precedes controller approval of the exact current pair.
    result = assess(observations)['candidate']
    return control.send('sildur_lean_alpha', arm=True, pack=contract['generated'], sha256=archive_sha,
                        vertex_sha256=result['vertex_sha256'], fragment_sha256=result['fragment_sha256'])['sildur_lean_alpha']


def progress(row, *, active, before=None):
    life.require(row.get('armed') is active, 'Private Sildur permission ownership differs')
    for key in ['setups', 'grants', 'refusals']:
        life.require(type(row.get(key)) is int and row[key] >= 0, 'Typed private Sildur setup counters required')
    if active:
        life.require(type(row.get('draw_calls')) is int and row['draw_calls']>0,'Private Sildur native draw hook missing')
        life.require(row['grants'] > 0, 'Private Sildur sampler/current-alpha permission never granted: ' + str(row.get('last')))
        if before is not None:
            life.require(row['grants'] > before['grants'], 'Private Sildur permission did not progress during window')
    return row

"""Admission for the existing C02 runtime; explicitly a bloom quality tradeoff."""
import json
import build_bsl_native_mips as bsl
import experiment_lifecycle as life

QUALITY = 'QUALITY TRADEOFF: 2x2 BSL composite4 bloom shading; not image-equivalent.'


def identity(pack, options):
    native = bsl.validate(pack, options)
    expected = json.loads((bsl.ROOT/'src/main/resources/assets/shaderlod/bslquad/native384.json').read_text())
    life.require(native == expected, 'C02 requires non-PBR native384')
    return {'candidate':'c02-bloom-vrs', 'original_sha256':bsl.ORIGINAL,
            'native_options':native, 'quality':QUALITY}


def activation(state, contract, expected_pack):
    life.require(state.get('pack') == expected_pack, 'C02 original pack selection changed')
    life.require(state.get('effective_options') == contract['native_options'], 'C02 native options changed')
    installed = state.get('installed', {})
    life.require(installed.get('master') is True and installed.get('flags')
                 and not any(v in (True, 'true') for v in installed['flags'].values()),
                 'C02 requires master On with no other requested features')


def progress(status, *, active, before=None):
    life.require(status.get('requested') is active, 'C02 request differs from arm')
    backend = status.get('backend', {})
    life.require(backend.get('failed') is False
                 and isinstance(backend.get('activated_draws'), int)
                 and backend['activated_draws'] == backend.get('restored_scopes'),
                 'C02 backend failed or scoped graphics state not restored')
    if active:
        life.require(status.get('hardware_available') is True, 'C02 hardware/context unavailable: '+str(status.get('missing')))
        life.require(status.get('qualified_draws', 0) > 0 and backend['activated_draws'] > 0,
                     'C02 no qualified activated bloom draws: '+str(status.get('last')))
    if before is not None:
        delta = backend['activated_draws'] - before['backend']['activated_draws']
        life.require(delta > 0 if active else delta == 0, 'C02 executed draw count differs from arm')
        if active:
            life.require(status['qualified_draws'] > before['qualified_draws'], 'C02 qualified draws did not advance')

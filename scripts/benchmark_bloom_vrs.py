"""Admission for the existing C02 runtime; explicitly a bloom quality tradeoff."""
import json
from pathlib import Path
import dev_client
import build_bsl_native_mips as bsl
import experiment_lifecycle as life

QUALITY = 'QUALITY TRADEOFF: 2x2 qualified native composite4 bloom shading; not image-equivalent.'


PROFILES = {
    bsl.ORIGINAL: ('bsl-shaders',1,'bslquad/native384.json','0aaa1276ea6e24106e6ed062a3bd149d1b53c09693d04a4637dcb19e6c43c394',384),
    'fed6c879e732cf7fc7bc6570ebb1def43235aa7e96f62859fbdbdbfa0b484b38': ('complementary-reimagined',3,'reimaginedquad/native398.json','ccfca334aeaa074a74d1f8e9071b707d32161a6b771366fefcc0f87f3b1175f4',398),
    '98ac741dee294d2a3c82d153349148c3bb7fb688c30eb3b49c3b9ac37e968c5a': ('complementary-unbound',3,'unboundquad/native398.json','b64d94998c99bf867e80d350a4a0a8108c58507e0401eb053a70e3aca81a5036',398),
}


def identity(pack, options):
    archive=life.digest(pack)
    life.require(archive in PROFILES,'C02 requires a pinned original non-PBR BSL or Complementary archive')
    family,target,resource,pin,count=PROFILES[archive]
    path=bsl.ROOT/'src/main/resources/assets/shaderlod'/resource
    life.require(life.digest(path)==pin,'C02 native option resource differs')
    expected=json.loads(path.read_text());native=dev_client.properties(Path(options).read_text())
    life.require(len(expected)==count and native==expected,'C02 requires the exact complete non-PBR native profile')
    return {'candidate':'c02-bloom-vrs','family':family,'original_sha256':archive,
            'native_options':native,'output_target':target,'program':'composite4','quality':QUALITY}


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

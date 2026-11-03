"""Private N04 cheaper zero guard, selected only after same-run native domain proof."""
from functools import partial
from pathlib import Path
import benchmark_bliss_zero_emission as base
import benchmark_bliss_albedo_domain as domain
import experiment_lifecycle as life

producer = base.producer
private_off = base.private_off
activation = base.activation


def identity(pack, options):
    result = base.identity(pack, options)
    result['producers'].update({str(Path(p).resolve()): life.digest(p)
                                for p in [__file__, domain.__file__]})
    result['generated'] = 'ShaderLOD-N04-Bliss-zero-lean-' + life.digest(producer.__file__)[:12] + '.zip'
    result['guard_mode'] = 'closed-native-albedo-domain'
    result['scope'] += ' Cheaper albedo predicate requires exhaustive same-run native GPU domain proof before selection.'
    return result


def stage(op, pack, options, identity):
    return base.staging.stage(op, pack, options, identity,
                              build=partial(producer.build, lean=True))


def require_native_domain(observations):
    life.require('native' in observations, 'Native albedo proof required before lean selection')
    return domain.validate(observations['native'])


def source_work(packet, *, active):
    return base.source_work(packet, active=active, guard=producer.LEAN_GUARD)


def assess(observations):
    require_native_domain(observations)
    return base.assess(observations, guard=producer.LEAN_GUARD)

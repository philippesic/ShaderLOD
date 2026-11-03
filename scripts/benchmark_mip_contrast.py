"""Private/public deletion contrast in the maintained benchmark process."""
from pathlib import Path
import shutil
import experiment_lifecycle as life
import benchmark_iteration as iteration


def public_identity(pack,root,private):
    life.require(private['native_options'].get('ADVANCED_MATERIALS')=='false','Public integration contrast retains existing exact non-PBR profile')
    result=iteration.public_identity(pack,root)
    life.require(result['family']=='bsl-shaders','BSL integration contrast only')
    return result


def activate_public(control,identity,private,*,original):
    control.send('configure',master=False,features=['dead_mip_chain'])
    result=control.send('settings_apply',enabled=True,preset='PERFORMANCE')
    life.require(result.get('success') is True,'BSL public contrast Apply failed')
    control.ready();control.send('configure',features=['dead_mip_chain']);state=control.ready()
    iteration.activation(state,feature='dead_mip_chain',enabled=True,expected_pack=identity['generated'],
                         family='bsl-shaders',native_options=private['native_options'],
                         compile_control=identity['compile_controls']['dead_mip_chain'])
    return state


def preserve_public(op,profile,identity):
    archive=Path(profile)/'shaderpacks'/identity['generated'];saved=op.output/'selected-public-candidate.zip'
    pin=life.digest(archive);shutil.copy2(archive,saved)
    life.require(pin is not None and life.digest(archive)==pin and life.digest(saved)==pin,'BSL public contrast archive changed')
    op.pin([saved]);return pin


def comparison(output):
    rows={label:iteration.arm_metrics(Path(output)/'windows'/label,label) for label in ('native','candidate','public','return')}
    rows['scope']='Single-process chronological native/private-deletion/public-singleton/return, matched native options and executed2/0/0/2 work witnesses. Descriptive contrast; no significance or cross-session subtraction.'
    return rows

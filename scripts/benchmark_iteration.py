"""Small, descriptive evaluation for the maintained native/candidate/return test.

This is a development comparison, not a significance test or qualification gate.
"""
import hashlib
import json
import math
import os
import shutil
import stat
import statistics
from contextlib import contextmanager, ExitStack
from pathlib import Path

import experiment_lifecycle as life
import run_storage as storage


def tree_map(root, *, save_locks=False):
    root=storage.safe_path(root)
    if not root.exists():return {'type':'absent'}
    result={}
    for path in [root,*sorted(root.rglob('*'))] if root.is_dir() else [root]:
        if save_locks and path.name=='session.lock':continue
        storage.safe_path(path)
        info=path.lstat();relative=str(path.relative_to(root))
        life.require(stat.S_ISREG(info.st_mode) or stat.S_ISDIR(info.st_mode),'Unsupported profile input type: '+str(path))
        result[relative]={'type':'directory' if path.is_dir() else 'file','mode':stat.S_IMODE(info.st_mode)}
        if path.is_file():result[relative].update(bytes=info.st_size,sha256=life.digest(path))
    return result


def profile_map(profile):
    return {name:tree_map(Path(profile)/name,save_locks=name=='saves') for name in sorted(storage.PROFILE_INPUTS)}


@contextmanager
def stopped_profiles(*profiles):
    import dev_client as dev
    from benchmark_file_locks import world_lock
    with dev.runtime_lock(),ExitStack() as locks:
        life.require(not dev.clients(dev.process_table()),'Client census is occupied')
        for profile in profiles:
            for path in sorted((Path(profile)/'saves').glob('*/session.lock')):
                locks.enter_context(world_lock(storage.safe_path(path)))
        yield


def check_profile_inputs(profile):
    """Read-only early check; prepare_profile repeats under ownership before copying."""
    profile=Path(profile)
    marker=json.loads((profile.parent/'workspace.json').read_text())
    source=storage.safe_path(marker['source'])
    life.require(source.is_dir(),'Canonical source is missing: '+str(source)+'; preserve this slot and select a slot with an existing canonical source')
    original=profile_map(profile);canonical=profile_map(source)
    differences=[];categories=[]
    for name in original:
        if original[name]!=canonical[name]:
            categories.append(name)
            keys=sorted(set(original[name])|set(canonical[name]))
            for key in keys:
                if original[name].get(key)!=canonical[name].get(key):
                    differences.append(name+'/'+key)
                    if len(differences)>=8:break
    life.require(not differences,'Managed profile differs from canonical inputs (bytes, modes or presence); categories '+', '.join(categories)+'; example paths: '+', '.join(differences[:8])+'; preserve changes and inspect before checkout/reset')
    return {'source':str(source),'matching':True}


def prepare_profile(op):
    """Fresh full maps and independent whole-save backup, before any staging."""
    profile=Path(op.data['profile'])
    marker=json.loads((profile.parent/'workspace.json').read_text())
    source=storage.safe_path(marker['source'])
    life.require(source.is_dir(),'Canonical source is missing: '+str(source)+'; preserve this slot')
    with stopped_profiles(profile,source):
        original=profile_map(profile);canonical=profile_map(source)
        life.require(original==canonical,'Standard test needs a clean managed checkout; differing original inputs are preserved')
        backup=op.output/'world-before'
        storage.copytree(profile/'saves',backup,ignore=lambda directory,names:set(names)&{'session.lock'},reserve_bytes=storage.MIN_FREE)
        life.require(tree_map(backup,save_locks=True)==original['saves'],'Independent whole-world backup differs')
        life.require(profile_map(profile)==original and profile_map(source)==canonical,'Inputs changed during backup')
        op.data['iteration_profile']={'source':str(source),'original':original,'canonical':canonical,
                                      'world_backup':str(backup),'reset_attempted':False,'restored':False}
        op.save()


def finish_profile(op):
    """Restore only owned saves, then verify all eight inputs before one reset."""
    record=op.data.get('iteration_profile')
    if record is None:return
    life.require(not record.get('reset_attempted') or record.get('reset_verified') and record.get('restored'),
                 'A reset was already attempted; preserve evidence without replay')
    life.require(op.data.get('release_verified') and op.data.get('configuration_restored'),'Release/configuration restore must precede whole-profile restoration')
    profile=Path(op.data['profile']);source=Path(record['source']);backup=Path(record['world_backup'])
    with stopped_profiles(profile,source):
        life.require(profile_map(source)==record['canonical'],'Canonical inputs changed; reset refused')
        current=profile_map(profile)
        if record.get('reset_verified'):
            info=profile.stat()
            life.require(current==record['original'] and [info.st_dev,info.st_ino]==record.get('new_profile_identity'),
                         'Previously verified reset no longer matches; preserve evidence')
            # A later report/cleanup failure does not authorize another reset.
            life.atomic_json(op.output/'final-restoration.json',record)
            return
        life.require({k:v for k,v in current.items() if k!='saves'}=={k:v for k,v in record['original'].items() if k!='saves'},
                     'Non-world original inputs differ; reset refused')
        life.require(tree_map(backup,save_locks=True)==record['original']['saves'],'World backup changed; restoration refused')
        if current['saves']!=record['original']['saves']:
            staged=op.output/'world-restore'
            if not staged.exists():storage.copytree(backup,staged,reserve_bytes=storage.MIN_FREE)
            life.require(tree_map(staged,save_locks=True)==record['original']['saves'] and profile_map(profile)==current,
                         'World changed during stopped restoration')
            if os.name == 'nt':
                # Windows forbids renaming a directory containing an open lock
                # handle. Keep the Java locks held and restore its contents.
                post=op.output/'world-after'
                if not post.exists():
                    storage.copytree(profile/'saves',post,ignore=lambda d,n:set(n)&{'session.lock'},reserve_bytes=storage.MIN_FREE)
                    life.require(tree_map(post,save_locks=True)==current['saves'],'Stopped-world snapshot differs')
                    record['post_world']=current['saves'];op.save()
                life.require(tree_map(post,save_locks=True)==record['post_world'],'Retained stopped-world evidence changed')
                restore_locked_world_contents(staged,profile/'saves')
            else:
                record['post_world']=current['saves'];op.save()
                (profile/'saves').rename(op.output/'world-after')
                staged.rename(profile/'saves')
        life.require(profile_map(profile)==record['original'],'Full original map verification failed; reset refused')
        life.require(profile_map(source)==record['canonical'],'Canonical changed before reset')
        record['restored']=True;op.save()
        if os.name == 'nt':
            # Exact full-map equality already proves the slot canonical. Avoid
            # a redundant whole-profile rename that cannot retain Windows locks.
            record.update(reset_attempted=True,reset_verified=True,
                          reset_strategy='verified-in-place-under-world-locks')
            info=profile.stat();op.data['profile_identity']=[info.st_dev,info.st_ino]
            record['new_profile_identity']=op.data['profile_identity'];op.save()
            life.atomic_json(op.output/'final-restoration.json',record)
            return
    # checkout obtains its own fresh lane and save locks. No old-inode lock reuse.
    def verify():
        life.require(profile_map(profile)==record['original'] and profile_map(source)==record['canonical'],
                     'Full maps changed before checked reset')
    record['reset_attempted']=True;op.save()
    storage.checkout(source,profile.parent.name,verify_before_replace=verify)
    with stopped_profiles(profile,source):
        life.require(profile_map(profile)==record['original'] and profile_map(source)==record['canonical'],
                     'Fresh maps after reset differ')
        info=profile.stat();op.data['profile_identity']=[info.st_dev,info.st_ino]
        record['reset_verified']=True;record['new_profile_identity']=op.data['profile_identity'];op.save()
    life.atomic_json(op.output/'final-restoration.json',record)


def restore_locked_world_contents(source, destination):
    """Restore stopped Windows worlds without releasing their session locks."""
    source=storage.safe_path(source);destination=storage.safe_path(destination)
    original={p.name:p for p in source.iterdir()}
    current={p.name:p for p in destination.iterdir()}
    life.require(original.keys()==current.keys() and all(p.is_dir() for p in [*original.values(),*current.values()]),
                 'World roots changed; preserve for explicit recovery')
    for name,backup in original.items():
        world=current[name]
        storage.tree_bytes(world)  # Reject links/special files before mutation.
        for child in world.iterdir():
            if child.name=='session.lock':continue
            if child.is_dir():shutil.rmtree(child)
            else:child.unlink()
        for child in backup.iterdir():
            if child.name=='session.lock':continue
            target=world/child.name
            if child.is_dir():storage.copytree(child,target,reserve_bytes=storage.MIN_FREE)
            else:storage.clone_file(child,target)
        os.chmod(world,stat.S_IMODE(backup.stat().st_mode))


def dispose_worlds(op):
    record=op.data.get('iteration_profile',{})
    if op.data.get('state')!='complete' or not record.get('reset_verified') or life.pinned(op.output):
        return {'retained':'Failure, pending restoration or evidence pin'}
    removed=[];profile=Path(op.data['profile']);source=Path(record['source'])
    with stopped_profiles(profile,source):
        life.require(profile_map(profile)==record['original'] and profile_map(source)==record['canonical'],
                     'Fresh original/canonical verification required before backup disposal')
        for name,expected in [('world-before',record['original']['saves']),('world-after',record.get('post_world'))]:
            path=op.output/name
            if not path.exists():continue
            life.require(expected is not None and tree_map(path,save_locks=True)==expected,'Owned world backup differs; retain it')
            usage=storage.scoped_usage([path]);row={'path':str(path),'logical_bytes':usage['logical_bytes'],'status':'intent'}
            record.setdefault('world_disposal',[]).append(row);op.save()
            shutil.rmtree(path);row['status']='removed';op.save();removed.append(row)
    return {'removed':removed}



def resource_contract(profile):
    """Freeze the prepared profile's requested resource IDs and owned file bytes."""
    lines=(Path(profile)/'options.txt').read_text().splitlines()
    entries=[line.split(':',1)[1] for line in lines if line.startswith('resourcePacks:')]
    life.require(len(entries)<=1,'Duplicate resourcePacks option')
    requested=json.loads(entries[0]) if entries else []
    life.require(isinstance(requested,list) and all(isinstance(x,str) for x in requested)
                 and len(requested)==len(set(requested)),'Malformed resource pack selection')
    files={}
    for identifier in requested:
        if not identifier.startswith('file/'):continue
        name=identifier[5:]
        life.require(name and Path(name).name==name and name not in ('.','..'),'Unsafe resource pack ID')
        path=Path(profile)/'resourcepacks'/name
        life.require(path.exists(),'Requested resource pack missing: '+identifier)
        files[identifier]=tree_map(path)
    return {'requested_ids':requested,'files':files}


def resource_selection(state, contract, *, baseline=None):
    """Refuse silent pack removal and cross-arm changes before candidate timing."""
    selected=state.get('selected_resource_packs')
    life.require(isinstance(selected,list) and all(isinstance(x,str) for x in selected)
                 and len(selected)==len(set(selected)),'Actual resource pack selection unavailable/malformed')
    requested=contract['requested_ids']
    life.require(set(requested)<=set(selected),'Requested resource packs were not loaded')
    life.require({x for x in selected if x.startswith('file/')}==
                 {x for x in requested if x.startswith('file/')},'Unexpected file resource pack loaded')
    if baseline is not None:life.require(selected==baseline,'Resource pack selection changed between arms')
    return list(selected)



def c01_identity(pack, options, root, *, mode='deferred_cpu',group=32):
    life.require(group in (8,16,32),'Finite C01 group required')
    life.require(mode in ('deferred_cpu','deferred','deferred_wait'),'Finite C01 mode required')
    """One existing finite private C01 prototype, never a public admission route."""
    life.require(life.digest(pack)=='0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8',
                 'C01 requires pinned original MakeUp9.5g')
    import dev_client as dev
    expected=json.loads((Path(root)/'src/main/resources/assets/shaderlod/makeupquad/native100.json').read_text())
    life.require(options is not None and dev.properties(Path(options).read_text())==expected,
                 'C01 requires the complete native100 option map')
    return {'candidate':'c01-bounds','original_sha256':life.digest(pack),'native_options':expected,
            'mode':mode,'prefix':1,'group':group,'frames':20000,'delay':256,
            'scope':'Finite private current-frame extra occlusion on original MakeUp; no DH, public/default or depth-priming claim.'}


def c01_activation(state, identity, *, expected_pack, features=None):
    life.require(state.get('pack')==expected_pack,'C01 selected original differs')
    actual=state.get('effective_options',{})
    life.require(({k:v for k,v in actual.items() if not k.startswith('SHADERLOD_')} if features else actual)==identity['native_options'],'C01 native100 options differ')
    installed=state.get('installed',{})
    if features:
        life.require(installed.get('master') is True and {k for k,v in installed.get('flags',{}).items() if v in (True,'true')}==set(features),'C01 public combination requests differ')
        return
    life.require(installed.get('master') is False and not any(x in (True,'true') for x in installed.get('flags',{}).values()),
                 'Private candidate requires native Off and empty public requests in every arm')


def c01_work(record, *, active, before=None, mode='deferred_cpu'):
    life.require(record.get('mode')==(mode if active else 'off'),'C01 mode differs')
    if not active:return
    life.require(not record.get('refusal') and record.get('remaining_frames',0)>0
                 and record.get('proxy_queries',0)>0
                 and (record.get('cpu_result_reads',0)>0 if mode=='deferred_cpu' else record.get('cpu_result_reads')==0),
                 'C01 actual query/read admission unavailable: '+str(record.get('refusal','')))
    if before is not None:
        life.require(record.get('frames',0)>before.get('frames',0)
                     and record['proxy_queries']>before['proxy_queries']
                     and (record['cpu_result_reads']>before['cpu_result_reads'] if mode=='deferred_cpu' else record['cpu_result_reads']==0),'C01 work did not progress during timing')



def g10_identity(pack, options, root, *, native_control=False):
    base=c01_identity(pack,options,root)
    producers={str(Path(root).resolve()/'scripts'/name):life.digest(Path(root)/'scripts'/name)
               for name in ('build_makeup_bloom_pairing.py','build_makeup_fullscreen_work.py')}
    key=hashlib.sha256(json.dumps(producers,sort_keys=True).encode()).hexdigest()[:12]
    return {'candidate':'g10-native-control' if native_control else 'g10-exact-bloom','compile_mode':'0' if native_control else '3','original_sha256':base['original_sha256'],
            'native_options':base['native_options'],'producers':producers,
            'generated':'ShaderLOD-G10-'+('native-' if native_control else 'exact-')+key+'.zip','work':'ShaderLOD-G10-work-'+key+'.zip',
            'scope':('Known native-equivalent private pack control; compile selector0, no active reuse; not an optimization result.' if native_control else 'Private exact i0/zero-dither duplicate sample reuse; four native additions; no approximate filter pairing/public admission.')}


def stage_g10(op, pack, options, identity):
    import build_makeup_bloom_pairing as producer
    import build_makeup_fullscreen_work as witness
    life.require(all(life.digest(p)==v for p,v in identity['producers'].items()),'G10 producer changed since preflight')
    directory=op.output/'g10-inputs';directory.mkdir()
    lean=directory/identity['generated'];work=directory/identity['work']
    producer.build(pack,lean,zero_dither_only=True)
    raw=Path(options).read_bytes();Path(str(lean)+'.txt').write_bytes(producer.options_bytes(raw,int(identity.get('compile_mode','3'))))
    witness.build(lean,work,'G10-bloom');Path(str(work)+'.txt').write_bytes(producer.options_bytes(raw,4))
    outputs={str(Path(op.data['profile'])/'shaderpacks'/source.name):source for source in (lean,Path(str(lean)+'.txt'),work,Path(str(work)+'.txt'))}
    expected={name:row['expected'] for name,row in op.data['configuration'].items()}
    op.begin_mutation('stage-g10-private')
    try:
        for target,source in outputs.items():
            life.require(target in expected and life.digest(target)==expected[target],'G10 stage target changed')
            Path(target).write_bytes(source.read_bytes());expected[target]=life.digest(source)
    finally:
        life.require(all(life.digest(name)==value for name,value in expected.items()),'G10 stage ownership uncertain; preserve files')
        op.acknowledge_mutation('stage-g10-private')
    op.pin(list(directory.iterdir()))
    return {'lean_sha256':life.digest(lean),'work_sha256':life.digest(work)}


def g10_activation(state, identity, *, active, expected_pack, mode=None):
    actual=dict(state.get('effective_options',{}));mode=mode or identity.get('compile_mode','3')
    if active:life.require(actual.pop('SHADERLOD_G10_BLOOM_PAIRING',None)==mode,'G10 private compile selection differs')
    c01_activation(dict(state,effective_options=actual),identity,expected_pack=expected_pack)


def g10_observation(control):
    """Collect completed native submissions outside timing; always disarm GL queries."""
    try:
        control.send('g10_bloom_status',observe=True)
        control.wait(2)
        return control.send('g10_bloom_status')['g10_bloom']
    finally:
        control.send('g10_bloom_status',observe=False)


def g10_draw(record, *, active, before=None, expected_source=None):
    life.require(record.get('qualified') is True and record.get('draws',0)>0,'G10 linked native bloom draw unavailable')
    if expected_source is not None:life.require(record.get('program_source')==expected_source,'G10 actual bloom role differs')
    fragment=record.get('fragment_source','')
    life.require(bool('g10Sample' in fragment)==active,'G10 actual linked branch differs')
    compact=''.join(fragment.split())
    if active:life.require('dither==0.0' in compact and compact.count('+=g10Sample')==4,'G10 linked exact branch/additions differ')
    if before is not None:
        life.require(record.get('program')==before.get('program') and record['draws']>before['draws'],'G10 consuming draw did not progress')


def g10_image(record):
    draw=record['draw_receipt'];image=record['image']
    life.require(draw.get('qualified') is True and draw.get('work_texture',0)>0,'G10 work producer unavailable')
    life.require(image.get('state_restored') is True and image.get('diagnostic',{}).get('kind')=='G10-bloom','G10 work image refused')
    banks=[row for row in image.get('banks',[]) if row.get('texture')==draw['work_texture']]
    life.require(len(banks)==1,'G10 observed writer texture not joined to work bank')
    row=banks[0];pixels=row['width']*row['height'];hist=row['histogram']
    life.require(pixels>0 and row.get('eligible_pixels')==pixels and len(hist)>4
                 and hist[1]+hist[4]==pixels and sum(hist)==pixels,'G10 work encoding/coverage differs')
    return {'producer_phase':int(draw['draw_counter'])&15,'pixels':pixels,'reuse_pixels':hist[4],
            'omitted_source_query_expressions':3*hist[4],'texture':row['texture'],
            'scope':'Completed producer draw joined to actual R16F texture; source expression opportunities, not physical texture transactions.'}


def public_identity(pack, root):
    """Predict product-owned paths from the original archive and shipped recipe."""
    import build_supported_pack_adapters as producer
    files = producer.read_archive(pack)
    source = producer.shader_source_digest(files)
    matches = []
    for path in sorted((root/'src/main/resources/assets/shaderlod/adapters/patches/adapters').glob('*.json')):
        raw = path.read_bytes(); recipe = json.loads(raw)
        if recipe.get('shader_source_digest') != source:
            continue
        producer.apply(files, recipe, root)
        matches.append((recipe['id'], hashlib.sha256(raw).hexdigest(), path, recipe.get('optimization_compile_controls', {}), {}))
    # Photon has a separate maintained manifest/producer, as in the normal
    # PackSelectionService. Keep its exact original/archive and helper pins.
    path = root/'src/main/resources/assets/shaderlod/adapters/patches/photon-experiments/live-pixel-budget.json'
    if path.is_file():
        raw = path.read_bytes(); recipe = json.loads(raw)
        if life.digest(pack) == recipe.get('base_sha256'):
            import build_photon_live_pixel_budget as photon
            generated = photon.patch_members(files, recipe, 'enabled', root=root)
            for dimension in ('world0','world1','world-1'):
                for stage in ('fsh','vsh'):
                    name = 'shaders/'+dimension+'/gbuffers_terrain_solid.'+stage
                    life.require(b'#define PROGRAM_GBUFFERS_TERRAIN_SOLID' in generated[name].splitlines(),
                                 'Pinned Photon solid-program marker missing')
            matches.append(('photon', hashlib.sha256(raw).hexdigest(), path, recipe.get('optimization_compile_controls', {}),
                            {'PROGRAM_GBUFFERS_TERRAIN_SOLID':'true'}))
    life.require(len(matches) == 1, 'Standard public test requires one pinned maintained original adapter')
    family, recipe, path, controls, markers = matches[0]
    original = life.digest(pack)
    identity = hashlib.sha256((pack.name+original+recipe).encode()).hexdigest()[:16]
    state = hashlib.sha256((pack.name+'\0'+original+'\0'+recipe).encode()).hexdigest()
    return dict(family=family, original_sha256=original, recipe_sha256=recipe,
                recipe_path=str(path), compile_controls=controls, program_markers=markers, generated='ShaderLOD-'+family+'-'+identity+'.zip',
                state='.shaderlod-selection-v2-'+state+'.json',
                legacy_state='.shaderlod-selection-'+hashlib.sha256(pack.name.encode()).hexdigest()[:24]+'.json')


def activation(state, *, feature, enabled, expected_pack, family, native_options=None, compile_control=None, program_markers=None):
    life.require(state.get('pack') == expected_pack, 'Actual selected pack differs from planned public selection')
    installed = state.get('installed', {})
    life.require(installed.get('family') == family, 'Actual public family differs')
    life.require(installed.get('master') is enabled, 'Actual master setting differs')
    flags = installed.get('flags', {})
    requested = {key for key, value in flags.items() if value in ('true', True)}
    planned={feature} if isinstance(feature,str) else set(feature)
    life.require(requested == (planned if enabled else set()), 'Actual raw requests are not the planned exact feature set/empty set')
    options = state.get('effective_options')
    life.require(isinstance(options, dict) and options, 'Actual Iris option map missing')
    markers = program_markers or {}
    if enabled:
        life.require(all(options.get(key)==value for key,value in markers.items()), 'Pinned generated program marker differs')
    if enabled and 'dead_mip_chain' in planned:
        mip_key = {'astralex':'SHADERLOD_ASTRALEX_DEAD_MIP_CHAIN', 'photon':'SHADERLOD_PHOTON_DEAD_MIP_CHAIN'}.get(family)
        if mip_key:
            life.require(options.get(mip_key) == '1', 'Requested mip removal did not activate: ' + str(state.get('installed',{}).get('dead_mip_setup')))
    if enabled and compile_control:
        expected = compile_control.get('options')
        if expected is None and 'option' in compile_control:
            expected = {compile_control['option']: compile_control['enabled']}
        life.require(isinstance(expected, dict) and expected, 'Unknown candidate compile-control contract')
        refused = [key for key, value in expected.items() if options.get(key) != str(value)]
        life.require(not refused, 'Candidate compile admission refused: '+', '.join(sorted(refused)))
    if native_options is not None:
        changed = [key for key, value in native_options.items() if options.get(key) != value]
        added = set(options)-set(native_options)
        life.require(not changed, 'Original native options changed: '+', '.join(sorted(changed)))
        life.require(all(key.startswith('SHADERLOD_') or key in markers and options[key]==markers[key] for key in added), 'Unexpected non-adapter Iris options')
    return options


def original_option_snapshot(state, *, expected_pack, family):
    """Bind native choices from the observed original before A-first or B-first arms."""
    return dict(activation(state,feature=(),enabled=False,expected_pack=expected_pack,family=family))


def cpu_work(state, *, features, active, before=None, after=None):
    """Require real consumer progress, never equate a request with executed work."""
    installed=state.get('installed',{})
    work=installed.get('renderer_work',{})
    life.require(work.get('enabled') is True and work.get('armed') is False,
                 'CPU work window missing or still armed')
    life.require(work.get('invalid_compaction_metadata') == 0,'Invalid CPU compaction metadata')
    if active and 'quad_command_compaction' in features:
        life.require(installed.get('compaction_admitted') is True,'CPU compaction admission refused')
        life.require(work.get('compaction_eligible_batches',0)>0,'CPU compaction consumer did not execute')
    if active and 'index_scalar_cache' in features:
        life.require(work.get('scalar_hits',0)>0,'CPU scalar cache has no consuming hits')
    if active and 'shadow_admission_cache' in features:
        interval=(after or {}).get('admission_cache_window',{})
        first=interval.get('before',{}).get('hits')
        last=interval.get('after',{}).get('hits')
        life.require(type(first) is int and type(last) is int and last>first,
                     'CPU admission cache has no interval hits')
    if not active:
        life.require(work.get('compaction_eligible_batches') == 0 and work.get('scalar_hits') == 0,
                     'CPU controls executed candidate work')
    return work


def arm_metrics(directory, label):
    """Validate every declared total-frame sample before allowing the next arm."""
    directory=Path(directory)
    guard=json.loads((directory/'guard.json').read_text())
    rows=json.loads((directory/'result.json').read_text())['manifest']['windows']
    frames=guard.get('completed_render_frames')
    life.require(guard.get('valid') is True and type(frames) is int and frames>0,
                 'Invalid completed-render guard: '+label)
    life.require(len(rows)==1 and rows[0].get('id')==label and rows[0].get('validity')=='VALID',
                 'Invalid timing export: '+label)
    vector=rows[0].get('samples')
    life.require(isinstance(vector,list) and vector,'Missing total-frame samples: '+label)
    excluded=guard.get('excluded_initial_unavailable_interval',False)
    life.require(type(excluded) is bool,'Invalid initial-interval declaration: '+label)
    if excluded:
        # The maintained producer exports its first unavailable interval but
        # explicitly excludes it from statistics. No other missing row is legal.
        life.require(len(vector)>1 and isinstance(vector[0],dict) and vector[0].get('totalFrameNs') is None,
                     'Initial unavailable-interval declaration differs: '+label)
        vector=vector[1:]
    samples=[]
    for row in vector:
        value=row.get('totalFrameNs') if isinstance(row,dict) else None
        life.require(type(value) in (int,float) and math.isfinite(value) and value>0,
                     'Invalid total-frame sample: '+label)
        samples.append(value/1e6)
    count=rows[0].get('statistics',{}).get('count')
    life.require(type(count) is int and count==len(samples),'Declared sample count differs: '+label)
    guard_count=guard.get('statistics',{}).get('count')
    life.require(type(guard_count) is int and guard_count==count,'Guard sample count differs: '+label)
    wall=guard.get('wall_ns')
    life.require(type(wall) in (int,float) and math.isfinite(wall) and wall>0,'Missing wall duration: '+label)
    ordered=sorted(samples)
    return dict(samples=len(samples),excluded_initial_unavailable_interval=excluded,
                mean_total_frame_ms=sum(samples)/len(samples),
                variance_total_frame_ms2=statistics.variance(samples) if len(samples)>1 else None,
                sd_total_frame_ms=statistics.stdev(samples) if len(samples)>1 else None,
                p95_total_frame_ms=ordered[max(0,math.ceil(.95*len(ordered))-1)],
                completed_render_frames=frames,wall_seconds=wall/1e9,
                completed_frame_ms=wall/frames/1e6)


def baseline_evaluation(output, *, execution_completed, restored):
    result={'execution':'COMPLETE' if execution_completed else 'FAILED',
            'validity':'INVALID','improvement':'NOT_EVALUATED',
            'limits':'Unmodified native, main mod absent. Frame SD describes jitter; window SD describes within-process variation, neither is a confidence interval.'}
    if not execution_completed or not restored:return result
    try:
        result['arms']={label:arm_metrics(Path(output)/'windows'/label,label) for label in ('native_0','native_1','native_2')}
        means=[row['mean_total_frame_ms'] for row in result['arms'].values()]
        result.update(validity='VALID',mean_window_ms=statistics.mean(means),
                      sd_window_mean_ms=statistics.stdev(means),
                      variance_window_mean_ms2=statistics.variance(means),
                      first_to_last_drift_ms=means[-1]-means[0])
    except (OSError,ValueError,KeyError,TypeError) as error:result['error']=str(error)
    return result


def comparison_sequence(order):
    life.require(order in ('ABBA','BAAB'),'Balanced comparison order must be ABBA or BAAB')
    counts={False:0,True:0};sequence=[]
    for role in order:
        active=role=='B';index=counts[active];counts[active]+=1
        sequence.append((('candidate_' if active else 'native_')+str(index),active))
    return sequence


def balanced_evaluation(output, *, order, execution_completed, restored):
    result={'execution':'COMPLETE' if execution_completed else 'FAILED','validity':'INVALID',
            'improvement':'DESCRIPTIVE_ONLY','order':order,
            'limits':'Four order-balanced windows in one process; means, frame jitter and window-mean variation are descriptive. Repeat processes and inspect known-null noise, actual work and quality before any benefit claim.'}
    if not execution_completed or not restored:return result
    try:
        result['arms']={label:arm_metrics(Path(output)/'windows'/label,label) for label,_ in comparison_sequence(order)}
        groups={role:[r['mean_total_frame_ms'] for label,r in result['arms'].items() if label.startswith(role)] for role in ('native','candidate')}
        result['group_means_ms']={role:statistics.mean(values) for role,values in groups.items()}
        result['group_window_mean_sd_ms']={role:statistics.stdev(values) for role,values in groups.items()}
        result['group_window_mean_variance_ms2']={role:statistics.variance(values) for role,values in groups.items()}
        a,b=(result['group_means_ms'][role] for role in ('native','candidate'))
        result.update(validity='VALID',frame_time_reduction_pct=100*(a-b)/a,
                      native_window_spread_pct=100*abs(groups['native'][1]-groups['native'][0])/a,
                      candidate_window_spread_pct=100*abs(groups['candidate'][1]-groups['candidate'][0])/b)
    except (OSError,ValueError,KeyError,TypeError) as error:result['error']=str(error)
    return result


def ownership_diagnostic(control, kind, *, active):
    """Finite untimed receipts; observation never grants optimization admission."""
    if kind == 'g01-graph':
        try:
            row = control.send('g01_graph', frames=2)['g01_graph']
            for _ in range(30):
                if not row.get('active'): break
                control.wait(.2)
                row = control.send('g01_graph')['g01_graph']
            life.require(not row.get('active') and row.get('completed_final_passes') == 2
                         and not row.get('failure') and isinstance(row.get('constructor'), dict),
                         'Incomplete current G01 constructor/execution join')
            return row
        finally:
            control.send('g01_graph', cancel=True)
    life.require(kind in ('g09-storage','g09-prefix','g09-functional'), 'Unknown ownership diagnostic')
    row = control.send('g09_prefix_admission')
    if active:
        receipt = json.loads(row['prefix_status'])
        if kind=='g09-functional':
            support=receipt.get('private_sampler_support',{});rays=receipt.get('private_ray_comparison',{})
            life.require(receipt.get('private_functional_qualification') is True
                         and receipt.get('actual_prefix_abi',{}).get('shaderlodBslPrefixActive')==1
                         and support.get('state_restored') is True and support.get('sample_count',0)>0
                         and support.get('ceiling_violations')==0 and support.get('nearest_differences')==0
                         and support.get('zero_ceiling_fault_detections',0)>0
                         and rays.get('state_restored') is True and rays.get('rays')==12288
                         and rays.get('mismatch_codes')==0 and rays.get('exit_iteration_differences')==0
                         and rays.get('omitted_ReadNormal_expressions',0)>0
                         and len(rays.get('coverage_exit_reentry_wrap_upper_transition',[]))==4
                         and all(v>0 for v in rays['coverage_exit_reentry_wrap_upper_transition']),
                         'Incomplete private functional G09 support/ray/publication receipt: '+str(receipt.get('reason')))
            row['prefix_functional_receipt']=receipt
            return row
        if kind=='g09-prefix':
            bounds=receipt.get('actual_fresh_image_and_bounds',{})
            pixels=bounds.get('pixels',[])
            life.require(receipt.get('status')=='diagnostic admission-only native hold; filtered support/rounding unverified'
                         and receipt.get('owner_retired') is True and receipt.get('descriptors_unpublished') is True
                         and receipt.get('shaderlodBslPrefixActive')==0
                         and bounds.get('image',{}).get('rgba8StorageVerified') is True and len(pixels)==3
                         and all(type(p.get('maximumAlpha')) is int and 0<=p['maximumAlpha']<=255
                                 and len(p.get('rgbaSha256',''))==64 for p in pixels),
                         'Incomplete Active0 G09 prefix image observation')
            row['prefix_image_receipt']=receipt
            return row
        life.require(receipt.get('status') == 'property-only native hold; no support/H/admission'
                     and receipt.get('owner_retired') is True and receipt.get('image_absent') is True
                     and receipt.get('descriptors_unpublished') is True
                     and receipt.get('actual_active_after', {}).get('shaderlodBslPrefixActive') == 0,
                     'Incomplete native-hold G09 storage observation')
        row['storage_receipt'] = receipt
    return row


def untimed_work(control, *, feature, family):
    """Use existing opt-in witnesses after timing; always disarm before returning.

    BSL requires observed compilation, hence an explicitly untimed reload.
    Raw witness completeness is retained, never inferred from request flags.
    """
    if family == 'makeup-ultra-fast-shaders' and feature == 'early_alpha':
        try:
            control.send('early_alpha_status',observe=True,reset=True)
            control.wait(2)
            result=control.send('early_alpha_status')
            life.require('makeup_post_sample_draw_witness' in result,'G03 draw witness receipt missing')
        finally:
            control.send('early_alpha_status',observe=False,reset=False)
        return {'draw_admission_status':result['makeup_early_alpha_status'],
                'draw_counters':result['makeup_early_alpha_costs'],
                'source_diagnostic':result['makeup_early_alpha_source_diagnostic'],
                'post_sample_draw_witness':result.get('makeup_post_sample_draw_witness'),
                'scope':'Current source/admission and cumulative qualified draw observations; not avoided fragments or a GPU work counter. CPU-cost instrumentation remains off.'}
    if family == 'bsl-shaders' and feature in ('reflection_next_event_refinement', 'reflection_projected_refinement'):
        return {'draw_admission_status':control.send('reflection_status')['bsl_exact_reflection'],
                'scope':'Existing owned water-draw permission status only; not a count of reused samples or saved hardware work'}
    if family == 'bsl-shaders' and feature == 'ao_depth_reuse':
        return {'draw_admission':control.send('ao_depth_status')['ao_depth'],
                'scope':'Existing owned AO draw/sampler qualification and cumulative draw counts; not a count of avoided samples'}
    action = ('frame_copy_observe' if feature == 'dead_frame_copy' and family == 'makeup-ultra-fast-shaders'
              else 'bsl_mip_observe' if feature == 'dead_mip_chain' and family == 'bsl-shaders' else None)
    if action is None:
        return None
    try:
        control.send(action, enabled=True, reset=True, **({'reload':True} if action == 'bsl_mip_observe' else {}))
        control.ready()
        control.wait(2)
        if action == 'frame_copy_observe':
            return control.send('status')['state']['installed']['frame_copy']
        return control.send(action, enabled=True, reset=False)['bsl_mip_witness']
    finally:
        control.send(action, enabled=False, reset=False)


def combination_copy_work(record, *, active):
    frames=record.get('finalPassesCompleted',0)
    life.require(type(frames) is int and frames>0 and record.get('finalPassesEntered')==frames
                 and record.get('executedSwapCopies')==frames*(3 if active else 6),
                 'Combined executed copy witness refused')
    life.require(record.get('apiPinned') is True and not any(record.get(key,0) for key in
                 ('metadataFailures','streamMismatches','unknownByteCopies','invalidExtentCopies','executedFallbackCopies')),
                 'Combined copy metadata refused')
    return record


CROSS_ALPHA_FAMILIES={'sildurs-vibrant-shaders','solas-shader','astralex','bsl-shaders','complementary-reimagined','complementary-unbound'}

AO_SCALE_FAMILIES={'bsl-shaders','complementary-reimagined','complementary-unbound'}

DEPTH_REUSE_FAMILIES={'bsl-shaders','complementary-reimagined','complementary-unbound'}

REFLECTION_REUSE_FAMILIES={'makeup-ultra-fast-shaders','bsl-shaders'}


def reflection_reuse_work(state,*,family,active,before):
    row=state.get('installed',{}).get('reflection_reuse_work',{})
    previous=before.get('installed',{}).get('reflection_reuse_work',{})
    life.require(family in REFLECTION_REUSE_FAMILIES and row.get('family')==previous.get('family')==family,'Wrong reflection-reuse runtime family')
    delta={}
    for key in ('permission_grants','nearest_grants','linear_grants'):
        now=row.get('counters',{}).get(key);old=previous.get('counters',{}).get(key)
        life.require(type(now) is int and type(old) is int and now>=old>=0,'Missing or reset reflection-reuse counters')
        delta[key]=now-old
    life.require(delta['nearest_grants']>0 and row['counters'].get('owned_programs',0)>0 if active else all(v==0 for v in delta.values()),
                 'Next-event route requires actual NEAREST draw permission progress; '+str(row.get('status')))
    return row|{'window_delta':delta,'scope':'Native water draw permissions in the exact recorded window; setup permission0 is expected between draws. No executed branch, avoided-query or GPU-completion count.'}


def depth_reuse_work(state,*,family,active,before):
    row=state.get('installed',{}).get('depth_reuse_work',{})
    previous=before.get('installed',{}).get('depth_reuse_work',{})
    life.require(family in DEPTH_REUSE_FAMILIES and row.get('family')==previous.get('family')==family,'Wrong depth-reuse runtime family')
    now=row.get('counters',{}).get('qualified_draws');old=previous.get('counters',{}).get('qualified_draws')
    life.require(type(now) is int and type(old) is int and now>=old>=0,'Missing or reset depth-reuse counters')
    life.require(now>old and row['counters'].get('owned_programs',0)>0 if active else now==old,
                 'Depth-reuse draw permission progress differs from arm')
    return row|{'window_qualified_draws':now-old,'scope':'Current source/API/owner/viewport/typed sampler permissions in the exact recorded window; not avoided depth queries or GPU completions'}


def ao_scale_work(state,*,family,active,before):
    row=state.get('installed',{}).get('ao_scale_work',{})
    previous=before.get('installed',{}).get('ao_scale_work',{})
    life.require(family in AO_SCALE_FAMILIES and row.get('family')==previous.get('family')==family,
                 'Wrong AO-scale runtime family')
    delta={}
    for key in ('producer_ready','producer_returns','consumer_ready'):
        now=row.get('counters',{}).get(key);old=previous.get('counters',{}).get(key)
        life.require(type(now) is int and type(old) is int and now>=old>=0,'Missing or reset AO-scale work counters')
        delta[key]=now-old
        life.require(delta[key]>0 if active else delta[key]==0,
                     'AO-scale producer/consumer progress differs from arm: '+key+'; '+str(row.get('status')))
    for key in ('native_fallbacks','native_fallback_returns','skipped_native_recovery'):
        if key in row.get('counters',{}):
            delta[key]=row['counters'][key]-previous['counters'][key]
            life.require(delta[key]>=0,'AO-scale fallback counters reset')
    return row|{'window_delta':delta,'scope':'Completed producer returns and consumer permissions during the exact recorded window; not avoided samples or GPU time'}


def family_early_alpha_work(state,*,family,active,before=None):
    row=state.get('installed',{}).get('family_early_alpha',{})
    life.require(family in CROSS_ALPHA_FAMILIES and row.get('family')==family,'Wrong early-alpha runtime family')
    counters=row.get('counters',{});qualified=counters.get('qualified_draws')
    life.require(type(qualified) is int and qualified>=0,'Missing family early-alpha permission counters')
    if active:
        life.require(counters.get('owned_programs',0)>0 and counters.get('source_ready') is True and qualified>0,
                     'Family early-alpha owned CUTOUT/source/sampler permission refused: '+str(row.get('status')))
    if before is not None:
        previous=before['installed']['family_early_alpha']['counters']['qualified_draws']
        life.require(qualified>previous if active else qualified==previous,
                     'Family early-alpha permission progress differs from arm')
    return row | {'scope':'Current owned CUTOUT/source/sampler permission and bracketing qualified-draw progress; not avoided-fragment count'}


def early_alpha_work(state, *, before=None, activation=None):
    family=state.get('installed',{}).get('family')
    if family in CROSS_ALPHA_FAMILIES:
        return family_early_alpha_work(state,family=family,active=True,before=before)
    counters=state.get('installed',{}).get('early_alpha_draw_counters',{})
    qualified=counters.get('qualified_draws',0)
    setup=state.get('installed',{}).get('early_alpha_source_setup',{})
    diagnostic=(activation or {}).get('source_diagnostic',{})
    if setup.get('rewritten') is True and setup.get('rewritten_programs',0)>0 and all(
            diagnostic.get(key) is True for key in ('exact_api','declaration','profile','source','compile_enabled','pack_view','input_present','input_current')):
        witness=(activation or {}).get('post_sample_draw_witness')
        if witness is not None:life.require(witness.get('linked_programs',0)>0,'G03 rewritten CUTOUT link witness unavailable')
        return {'route':'asahi_linked_cutout_submissions' if witness is not None and witness.get('completed_batches',0)>0 else 'asahi_source_setup_only','setup':setup,'draw_counters':counters,'post_sample_draw_witness':witness,
                'limits':'Avoided fragment count unmeasured. Source-only route has no observed submitted CUTOUT batch; linked route counts untimed native submissions, not GPU primitives.'}
    life.require(isinstance(qualified,int) and qualified>0,'Early-alpha actual qualified draw admission unavailable')
    if before is not None:
        previous=before.get('installed',{}).get('early_alpha_draw_counters',{}).get('qualified_draws',0)
        life.require(qualified>previous,'Early-alpha qualified draw count did not progress')
    return counters


def functional_evaluation(*, execution_completed, restored):
    return {'execution':'COMPLETE' if execution_completed else 'FAILED',
            'validity':'VALID' if execution_completed and restored else 'INVALID',
            'improvement':'NOT_EVALUATED','closure':False,
            'limits':'Functional candidate activation, source/work checks and restoration only; short captures provide no performance or visual qualification.'}


def evaluate(output, *, execution_completed, restored):
    result = dict(execution='COMPLETE' if execution_completed else 'FAILED',
                  completion='INCOMPLETE', validity='INVALID', improvement='UNRESOLVED',
                  review='CLOSURE_ONLY', closure=False, arms={},
                  limits='One chronological development comparison; no statistical, visual, DH/PBR, hardware-universal or closure claim.')
    try:
        summary=Path(output)/'summary.json'
        if summary.is_file() and json.loads(summary.read_text()).get('purpose')=='functional-check':
            return functional_evaluation(execution_completed=execution_completed,restored=restored)
        life.require(execution_completed and restored, 'Execution and restoration must both complete')
        for label in ('native', 'candidate', 'return'):
            result['arms'][label] = arm_metrics(Path(output)/'windows'/label, label)
        arms = result['arms']; baseline = (arms['native']['mean_total_frame_ms']+arms['return']['mean_total_frame_ms'])/2
        candidate = arms['candidate']['mean_total_frame_ms']
        reduction = 100*(baseline-candidate)/baseline
        native_means = [arms[label]['mean_total_frame_ms'] for label in ('native', 'return')]
        # The chronological midpoint is descriptive only. A candidate inside
        # the observed native range cannot distinguish a change from drift.
        comparison = ('BELOW_BOTH_NATIVE_CONTROLS' if candidate < min(native_means)
                      else 'ABOVE_BOTH_NATIVE_CONTROLS' if candidate > max(native_means)
                      else 'INCONCLUSIVE_WITHIN_NATIVE_DRIFT')
        result.update(completion='COMPLETE', validity='VALID',
                      improvement='OBSERVED_LOWER_FRAME_TIME' if reduction>0 else 'OBSERVED_HIGHER_FRAME_TIME' if reduction<0 else 'NO_OBSERVED_CHANGE',
                      baseline_mean_total_frame_ms=baseline, frame_time_reduction_percent=reduction,
                      speedup_ratio=baseline/candidate,
                      drift_comparison=comparison,
                      reduction_vs_native_controls_percent=[100*(value-candidate)/value for value in native_means],
                      native_return_drift_percent=100*(arms['return']['mean_total_frame_ms']-arms['native']['mean_total_frame_ms'])/baseline)
    except (OSError, ValueError, KeyError, TypeError) as error:
        result['error'] = str(error)
    return result


def g09_functional_work(row,*,active,before=None):
    life.require(row.get('private_functional') is True and row.get('binding_active') is False,'Private prefix idle binding refused')
    count=row.get('active_setups')
    life.require(type(count) is int and count>=0 and (not active or count>0),'No current private prefix setup admission: '+str(row.get('prefix_status')))
    if before is not None:life.require(count>before['active_setups'] if active else count==before['active_setups'],'Private prefix admission progress differs')

"""Bind reviewed mixed feature descriptions to an owned, pinned live graph export."""
import hashlib
import json
import re
from pathlib import Path
from cost_map import SOURCE_CATALOG, SEEDS


def bind(data, archive):
    manifest = data['manifest']
    digest = hashlib.sha256(Path(archive).read_bytes()).hexdigest()
    pinned = next((p for p in SOURCE_CATALOG['packs'] if p['archive_sha256'] == digest), None)
    if pinned is None:
        manifest['semantic_binding_refusal'] = 'archive not in reviewed four-pack catalog'
        return
    graph_dir = Path(manifest['graph_evidence'])
    graph = json.loads((graph_dir/'manifest.json').read_text())
    if graph['dimension'] != 'minecraft:overworld' or 'effective_options' not in graph:
        manifest['semantic_binding_refusal'] = 'Overworld graph/effective options required'
        return
    options_hash = hashlib.sha256(json.dumps(graph['effective_options'],sort_keys=True).encode()).hexdigest()
    manifest.update(pack_revision=pinned['pack_revision'],archive_sha256=digest,dimension='world0',options_hash=options_hash)
    verified = {}
    for row in graph['programs']:
        name = row['name']
        member = 'shaders/world0/'+name+'.fsh'
        source = pinned['source_members'].get(member)
        path = graph_dir/(name+'.fsh')
        if not row['valid'] or not source or not path.is_file():
            continue
        text = path.read_text()
        verified[name] = dict(source_member=member,source_hash=source['sha256'],
            selected_source_hash=hashlib.sha256(text.encode()).hexdigest(),
            effective_options_hash=options_hash,graph_generation=manifest['graph_generation'],
            dh_state=manifest['dh_state'],dh_consumer=bool(re.search(r'(?:texture\w*|texelFetch)\s*\(\s*dh\w*(?:Depth|Color)\w*',text)),
            meaning='mixed potential features from reviewed source; selected preprocessed source retained; no function cost attribution')
    manifest['verified_graph_programs'] = verified
    for scope in data['scopes']:
        name=scope['name']
        program=name[5:] if name.startswith('iris/') else 'dh_terrain' if name=='dh/world/opaque' else 'dh_water' if name=='dh/world/translucent' else None
        if program in verified:
            scope['program']=program


def workload_evidence(manifest):
    """Classify observed work separately from the requested steady/streaming intent."""
    from cost_map import task_kind
    kinds={task_kind(s) for s in manifest.get('dh_tasks',{}).get('samples',[])}
    queues=manifest.get('after',{}).get('dh_queues',{})
    generation=manifest.get('identity',{}).get('dh',{}).get('exactSettings',{}).get('generation',{}).get('value')
    busy=any(row.get('getQueueSize',0)>0 or row.get('getRunningTaskCount',0)>0 for row in queues.values())
    return dict(request=manifest.get('workload_request','observed'),generation=generation,
                observed_tasks=sorted(kinds),queues_at_end=queues,
                classification='streaming/generation observed' if kinds & {'generation','conversion','propagation','loading'} else
                    'background queues active; steady unproven' if busy else
                    'no completed streaming tasks observed; endpoint evidence only',
                limits='No task completions is not zero worker cost; startup/queue drain is conditioning. Saved LOD bytes retained in world-before/world-after; no cache purge or traversal implied.')

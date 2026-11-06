"""Bounded final inventory and removal of this task's redundant plotting venv."""
import importlib.metadata
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import sys

HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
sys.path.insert(0,str(ROOT/'scripts'))
import dev_client as dev
import experiment_lifecycle as life
import run_storage as storage

life.require(not dev.clients(dev.process_table()),'Storage pass requires idle clients')
def census(path):
    path=storage.safe_path(path);total=files=dirs=links=0;pending=[path]
    while pending:
        for x in pending.pop().iterdir():
            v=x.lstat()
            if stat.S_ISLNK(v.st_mode):links+=1  # Count, never follow or mutate.
            elif stat.S_ISDIR(v.st_mode):dirs+=1;pending.append(x)
            else:
                life.require(stat.S_ISREG(v.st_mode),'Unexpected inventory entry '+str(x))
                files+=1;total+=v.st_size
    return {'path':str(path),'logical_bytes':total,'files':files,'directories':dirs,'symlinks_not_followed':links,
            'size_scope':'Logical regular-file bytes; not exclusive physical occupancy or a reclaim estimate.'}

actions=[];redundant=storage.safe_path(HERE/'plot-venv');retained=storage.safe_path(HERE/'chart-env')
if redundant.exists():
    cfg=(redundant/'pyvenv.cfg').read_text()
    life.require(str(redundant.relative_to(ROOT)) in cfg and 'venv' in cfg,'Unexpected redundant venv provenance')
    version='import sys,matplotlib;print(sys.version);print(matplotlib.__version__)'
    left=subprocess.check_output([str(redundant/'bin/python'),'-c',version],text=True)
    right=subprocess.check_output([str(retained/'bin/python'),'-c',version],text=True)
    life.require(left==right,'Keep differing plotting environments')
    size=census(redundant);before=shutil.disk_usage(HERE).free
    # Exact newly created task venv, no running consumer. rmtree unlinks child
    # symlinks without following them; the pre-existing chart-env stays intact.
    shutil.rmtree(redundant);after=shutil.disk_usage(HERE).free
    actions.append({'removed':str(redundant),'reason':'Redundant task-created plotting environment; existing chart-env has matching Python/Matplotlib and remains reusable.',
                    'logical_bytes':size['logical_bytes'],'free_bytes_before':before,'free_bytes_after':after,'observed_delta_bytes':after-before})

fixture=ROOT/'build/fast-png-checks/check-m9o2903k/retention.json'
fixture_retention=json.loads(fixture.read_text())
reports=[census(x) for x in HERE.iterdir() if x.is_dir() and x.name not in ('chart-env','plot-venv')]
archives=[]
for row in json.loads((HERE/'retained-evidence.json').read_text())['archives']:
    path=storage.safe_path(row['retention']);life.require(life.digest(path)==row['retention_sha256'],'Retention receipt changed')
    record=json.loads(path.read_text());archive=storage.safe_path(record['archive'])
    life.require(archive.stat().st_size==record['archive_bytes'] and record['archive_sha256']==row['archive_sha256'],'Archive size/receipt differs')
    life.require(life.digest(path.parent/'manifest.json')==record['recorder_manifest_sha256'],'Recorder receipt changed')
    life.require(life.digest(path.parent.parent/'operation.json')==record['original_operation_manifest_sha256'],'Original operation changed')
    archives.append({k:record[k] for k in ('archive','verified_pngs','removed_loose_duplicate_pngs','free_bytes_before','free_bytes_after','observed_free_delta_bytes')})
profiles=[]
for slot in ('workflow-validation-878e98fb8be9','neoforge-1211-asahi-pbr'):
    record=census(ROOT/'run-workloads/world-pool'/slot/'client')
    record.update(category='protected prepared slot',owner='Existing managed workload',retention='Preserve canonical worlds/configuration/resources and reusable caches; canonical equality verified in terminal-audit.json.')
    profiles.append(record)
cache=census(retained);cache.update(category='reproducible reusable plotting cache',retention='Keep to regenerate standalone figures; standard venv symlinks not followed.')
free=shutil.disk_usage(HERE).free
record={'host':'Native Linux / Asahi Apple M1','scope':'Task artifact root, exact captured fixture output and two reused prepared profiles. No whole-home scan, shared cache cleanup, process kill or disk compaction.',
        'artifact_root':str(HERE),'root_regular_file_logical_bytes':sum(x.stat().st_size for x in HERE.iterdir() if x.is_file()),
        'reports':reports,'retained_plot_cache':cache,'profiles':profiles,'archive_count':len(archives),'archive_actions':archives,
        'archive_verification':'Every PNG archive member was hashed before loose duplicate removal. Final pass checks immutable retention/recorder/operation hashes and current archive byte lengths; no repeated full decompression.',
        'scratch_cleanup':fixture_retention,'redundant_cache_cleanup':actions,'free_bytes_after_inventory':free,'free_GiB_after_inventory':free/1024**3,
        'retention':'Keep measurements, source/graph/work/variance receipts, failed attempts, verified lossless archives, five loose keyframes per arm, previews, plots, plans, recovery conflicts and reusable plotting cache. Shared Gradle/pip/runtime caches and unknown ownership are preserved.',
        'free_space_note':'Per-action free-space changes are observed values. Delayed filesystem release, archive creation and concurrent activity can affect them; logical removed sizes are not physical savings.'}
(HERE/'storage-inventory.json').write_text(json.dumps(record,indent=2)+'\n')
print(json.dumps({'archives':len(archives),'reports':len(reports),'profiles':len(profiles),'free_GiB':record['free_GiB_after_inventory']}))

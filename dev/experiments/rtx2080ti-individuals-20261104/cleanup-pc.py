import json,shutil,datetime
from pathlib import Path
import dev_client as dev
root=Path('C:/ShaderLODBench');evidence=root/'ShaderLOD/dev/experiments/rtx2080ti-individuals-20261104'
assert not dev.clients(dev.process_table())
assert (evidence/'terminal-audit.json').is_file() and (evidence/'transfer-verification.json').is_file()
paths=['individual-inputs.tar.gz','individual-results.tar.gz','individual-results.sha256','individual-job.ps1','individuals.log','followup-job.ps1','followup.log','prepare-pc.ps1','register-task.ps1','register-followup.ps1','retire-first-task.ps1','retire-followup.ps1']
removed=[]
for name in paths:
    p=root/name
    if p.exists():
        assert p.is_file() and not p.is_symlink(),p
        removed.append({'path':str(p),'bytes':p.stat().st_size,'category':'task-owned transport or launcher scratch; source/evidence retained'})
before=shutil.disk_usage(root).free
for item in removed:Path(item['path']).unlink()
after=shutil.disk_usage(root).free
retained=[]
for p,reason in [(evidence,'Unique experiment evidence; retained on PC and verified local copy'),(root/'canonical-pbr','Reusable pinned PBR canonical profile'),(root/'ShaderLOD/run-workloads/world-pool/rtx2080ti-pbr','Reusable prepared PBR slot')]:
    retained.append({'path':str(p),'logical_bytes':sum(f.stat().st_size for f in p.rglob('*') if f.is_file()),'reason':reason,'next_review':'After these benchmark results are superseded; preserve active/shared/canonical data'})
report={'host':'DESKTOP-T4JKGJB','filesystem':'Windows C: native shutil.disk_usage','before_free_bytes':before,'after_free_bytes':after,'observed_free_delta_bytes':after-before,'removed':removed,'retained':retained,'note':'Windows free-space change may include concurrent activity. WSL VHDX untouched; no compaction.'}
(evidence/'storage-remote.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({k:report[k] for k in ('before_free_bytes','after_free_bytes','observed_free_delta_bytes')}))

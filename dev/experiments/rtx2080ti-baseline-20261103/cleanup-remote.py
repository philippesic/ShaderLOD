from pathlib import Path
import json,shutil
import dev_client as dev
r=Path('C:/ShaderLODBench');assert not dev.clients(dev.process_table())
report={'host':'DESKTOP-T4JKGJB','free_before_bytes':shutil.disk_usage(r).free,'removed':[],'retained':[]}
for name,prerequisite in [('tools/microsoft-jdk21.zip','tools/java'),('tools/python.zip','tools/python/python.exe'),('tools/psutil-7.2.2-cp37-abi3-win_amd64.whl','tools/python/psutil'),('source-and-profile.tar.gz','canonical-client'),('native-preview.png','ShaderLOD/dev/experiments/rtx2080ti-baseline-20261103/functional-06/native.png')]:
 p=r/name;assert p.is_file() and not p.is_symlink() and (r/prerequisite).exists()
 report['removed'].append({'path':str(p),'logical_bytes':p.stat().st_size,'reason':'Owned disposable setup/download duplicate; extracted runtime or canonical/evidence retained'});p.unlink()
for name,reason in [('ShaderLOD','Source snapshot, managed slot, builds and raw experiment evidence'),('canonical-client','Canonical original world/profile'),('gradle-cache','Reusable Gradle, assets and dependencies'),('tools','Verified native Python/JDK and dependencies')]:
 p=r/name;size=sum(x.stat().st_size for x in p.rglob('*') if x.is_file() and not x.is_symlink());report['retained'].append({'path':str(p),'logical_bytes':size,'reason':reason})
report['free_after_bytes']=shutil.disk_usage(r).free
report['scope']='Native Windows C volume only; no WSL filesystem cleanup or VHDX compaction. Free-space delta can include concurrent activity.'
(r/'storage-inventory.json').write_text(json.dumps(report,indent=2));print(json.dumps(report))

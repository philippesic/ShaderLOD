#!/usr/bin/env python3
"""Fetch immutable publisher artifacts; hash-check originals before use."""
import hashlib,json,pathlib,urllib.request,zipfile,os,subprocess
root=pathlib.Path(__file__).resolve().parent
runtime=root/'runtime';runtime.mkdir(exist_ok=True)
evidence=root/'evidence';evidence.mkdir(exist_ok=True)
(evidence/'download-owner.json').write_text(json.dumps({'owner':'port_262','pid':os.getpid(),'birth':subprocess.check_output(['ps','-p',str(os.getpid()),'-o','lstart=']).decode().strip(),'argv':subprocess.check_output(['ps','-p',str(os.getpid()),'-o','command=']).decode().strip(),'stop':'Verify exact PID/birth/argv then SIGTERM PID only'},indent=2))
receipts=[]
for a in json.loads((root/'dependencies.lock.json').read_text())['artifacts']:
 target=runtime/a['filename']
 data=target.read_bytes() if target.exists() else urllib.request.urlopen(a['url'],timeout=90).read()
 for algorithm,expected in a['hashes'].items():
  assert hashlib.new(algorithm,data).hexdigest()==expected,(a['filename'],algorithm)
 if not target.exists():target.write_bytes(data)
 receipts.append(dict(filename=a['filename'],sha256=hashlib.sha256(data).hexdigest(),sha512=hashlib.sha512(data).hexdigest(),url=a['url']))
libraries=runtime/'iris-libraries';libraries.mkdir(exist_ok=True)
with zipfile.ZipFile(runtime/'iris-fabric-1.11.4+mc26.2.jar') as z:
 for name in z.namelist():
  if name.startswith('META-INF/jars/') and any(lib in name for lib in ('antlr4-runtime','glsl-transformer','jcpp')):
   (libraries/pathlib.Path(name).name).write_bytes(z.read(name))
(evidence/'artifact-receipts.json').write_text(json.dumps(receipts,indent=2)+'\n')
print('Publisher hashes verified for all exact Iris/Sodium/DH artifacts')

# Ordinary JUnit has no NeoForge JarJar loader; link exact nested shipped dependencies.
neoforge_libraries=runtime/'neoforge-libraries';neoforge_libraries.mkdir(exist_ok=True)
nested_receipts=[]
for artifact in json.loads((root/'dependencies.lock.json').read_text())['artifacts']:
 if artifact['loader']!='neoforge' or artifact['kind'] not in ('iris','sodium'):continue
 with zipfile.ZipFile(runtime/artifact['filename']) as archive:
  for member in archive.namelist():
   if member.startswith('META-INF/jarjar/') and member.endswith('.jar'):
    data=archive.read(member);(neoforge_libraries/pathlib.Path(member).name).write_bytes(data)
    nested_receipts.append(dict(parent=artifact['filename'],member=member,sha256=hashlib.sha256(data).hexdigest()))
(evidence/'neoforge-nested-library-receipts.json').write_text(json.dumps(nested_receipts,indent=2)+'\n')

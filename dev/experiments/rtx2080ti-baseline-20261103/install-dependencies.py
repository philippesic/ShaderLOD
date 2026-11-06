import urllib.request,json,hashlib,zipfile,io,pathlib
root=pathlib.Path('/mnt/c/ShaderLODBench/tools/python')
for name,version in [('pillow','11.1.0'),('numpy','2.2.6')]:
 data=json.load(urllib.request.urlopen(f'https://pypi.org/pypi/{name}/{version}/json'))
 item=next(x for x in data['urls'] if 'cp312-cp312-win_amd64.whl' in x['filename'])
 blob=urllib.request.urlopen(item['url']).read()
 assert hashlib.sha256(blob).hexdigest()==item['digests']['sha256']
 with zipfile.ZipFile(io.BytesIO(blob)) as z:z.extractall(root)
 print(item['filename'], 'verified and installed')

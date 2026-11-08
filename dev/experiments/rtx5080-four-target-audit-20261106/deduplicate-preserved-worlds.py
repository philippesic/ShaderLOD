"""Repack verified recovery copies by content; retain every path and original byte.

This only migrates this campaign's off-host world-copy backup, never live worlds.
Restore each files[] path by writing its objects/<sha256> bytes independently.
"""
import hashlib
import json
from pathlib import Path
import sys
import tarfile
import zipfile

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parents[2]/'scripts'))
import run_storage


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    tag = 'terminal-world-copies-v1'
    receipt = json.loads((HERE/(tag+'-verification.json')).read_text())
    source = HERE/(tag+'.tar.gz')
    target = HERE/(tag+'-content.zip')
    migrated = HERE/(tag+'-migration.json')
    assert receipt['verified'] and receipt['retired'] and not target.exists() and not migrated.exists()
    assert source.is_file() and not source.is_symlink() and str(source) == receipt['archive']
    assert digest(source) == receipt['archive_sha256']
    manifest = json.loads((HERE/(tag+'-source.json')).read_text())
    expected = {row['path']: row for row in manifest['files']}
    assert all('/world-restore/' in name and '..' not in Path(name).parts for name in expected)
    run_storage.admit(HERE, sum({r['sha256']: r['bytes'] for r in expected.values()}.values())+32*2**20)
    seen, objects = set(), set()
    with tarfile.open(source, 'r|gz') as archive, zipfile.ZipFile(target, 'x', compression=zipfile.ZIP_DEFLATED) as output:
        for member in archive:
            if member.isdir():
                continue
            assert member.isfile() and member.name in expected and member.name not in seen
            row = expected[member.name]
            with archive.extractfile(member) as stream:
                data = stream.read()
            assert len(data) == row['bytes'] and hashlib.sha256(data).hexdigest() == row['sha256']
            if row['sha256'] not in objects:
                output.writestr('objects/'+row['sha256'], data)
                objects.add(row['sha256'])
            seen.add(member.name)
        assert seen == set(expected)
        output.writestr('PRESERVATION-MANIFEST.json', json.dumps(manifest, indent=2)+'\n')
    with zipfile.ZipFile(target) as archive:
        restored = json.loads(archive.read('PRESERVATION-MANIFEST.json'))
        assert restored == manifest
        assert set(archive.namelist()) == {'objects/'+s for s in objects} | {'PRESERVATION-MANIFEST.json'}
        for sha in objects:
            with archive.open('objects/'+sha) as stream:
                assert hashlib.file_digest(stream, 'sha256').hexdigest() == sha
        assert all(archive.getinfo('objects/'+r['sha256']).file_size == r['bytes'] for r in expected.values())
    result = dict(source_archive=str(source), source_sha256=receipt['archive_sha256'],
                  archive=str(target), archive_sha256=digest(target), archive_bytes=target.stat().st_size,
                  files=len(expected), unique_files=len(objects), source_bytes=receipt['source_bytes'],
                  verified=True, old_archive_retired=False,
                  restore='For each files[] row in PRESERVATION-MANIFEST.json, write objects/<sha256> to its path as an independent file. Do not hardlink mutable recovery worlds.')
    migrated.write_text(json.dumps(result, indent=2)+'\n')
    assert digest(source) == receipt['archive_sha256']
    reclaimed = source.stat().st_size
    source.unlink()
    result['old_archive_retired'] = True
    result['reclaimed_bytes'] = reclaimed-target.stat().st_size
    migrated.write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps(result))


if __name__ == '__main__':
    main()

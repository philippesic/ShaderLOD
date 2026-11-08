"""Retire redundant transport ZIPs only when every extracted byte still verifies."""
import hashlib
import json
from pathlib import Path
import re

HERE = Path(__file__).resolve().parent


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    retired = []
    for archive in sorted(HERE.glob('timings-export-*.zip')):
        if not re.fullmatch(r'timings-export-[0-9a-f]{32}\.zip', archive.name):
            continue
        receipt_path = archive.with_suffix('.json')
        if not receipt_path.exists():
            continue
        receipt = json.loads(receipt_path.read_text())
        assert not archive.is_symlink() and receipt['local_archive'] == str(archive)
        assert archive.stat().st_size == receipt['archive_bytes'] and digest(archive) == receipt['archive_sha256']
        for row in receipt['files']:
            relative = Path(row['path'])
            assert not relative.is_absolute() and '..' not in relative.parts
            target = HERE/relative
            assert target.is_file() and not target.is_symlink()
            assert target.stat().st_size == row['bytes'] and digest(target) == row['sha256'], row['path']
        archive.unlink()
        receipt['local_archive_retired'] = True
        receipt['retirement_proof'] = 'Every extracted file rehashed against the independently verified transport manifest; extracted evidence retained.'
        receipt_path.write_text(json.dumps(receipt, indent=2)+'\n')
        retired.append(dict(archive=archive.name, bytes=receipt['archive_bytes'], verified_files=len(receipt['files'])))
    result = dict(retired=retired, reclaimed_bytes=sum(row['bytes'] for row in retired), extracted_evidence_retained=True)
    (HERE/'transport-retirement.json').write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps(result))


if __name__ == '__main__':
    main()

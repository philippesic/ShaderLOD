"""Preserve released quality captures one arm at a time with verified raw bytes.

Archive each arm before retiring its source PNGs, avoiding a second full-sequence
allocation. No live profile/world or acquisition input is changed.
"""
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import shutil
import zipfile

import dev_client
import experiment_lifecycle as life
import run_storage


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def preserve(report, archive_root):
    report = run_storage.safe_path(report)
    archive_root = run_storage.safe_path(archive_root)
    op = json.loads((report/'operation.json').read_text())
    life.require(op.get('state') in ('complete', 'failed-restored')
                 and op.get('configuration_restored') and op.get('release_verified')
                 and op.get('iteration_profile', {}).get('reset_verified'), 'Released/restored report required')
    table = dev_client.process_table()
    life.require(not dev_client.clients(table), 'Client active; keep captures in place')
    life.process_absent(op['processes'].get('controller'), table, dev_client.identity)
    receipt_path = report/'quality-preservation.json'
    life.require(not receipt_path.exists(), 'Preserve previous archival receipt; do not replay')
    archive_root.mkdir(parents=True, exist_ok=True)
    result = dict(scope='Original PNG/metadata bytes, one verified archive per arm', archives=[])

    def row(path):
        life.require(path.is_file() and not path.is_symlink(), 'Regular capture file required')
        before = path.stat()
        sha = digest(path)
        after = path.stat()
        life.require((before.st_size, before.st_mtime_ns) == (after.st_size, after.st_mtime_ns), 'Capture changed')
        return dict(path=path.relative_to(report).as_posix(), bytes=before.st_size, sha256=sha)

    for label in ('native', 'candidate', 'return'):
        directory = report/('quality-'+label)
        if not directory.exists():
            continue
        life.require(directory.is_dir() and not directory.is_symlink()
                     and not (os.name == 'nt' and os.path.isjunction(directory)), 'Owned regular capture directory required')
        for base, dirs, _ in os.walk(directory, followlinks=False):
            for name in dirs:
                path = Path(base)/name
                life.require(not path.is_symlink() and not (os.name == 'nt' and os.path.isjunction(path)), 'Capture link refused')
        paths = sorted(p for p in directory.rglob('*') if p.is_file())
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            rows = list(pool.map(row, paths))
        life.require(rows, 'Empty quality capture')
        archive = archive_root/(report.name+'-'+label+'.zip')
        life.require(not archive.exists(), 'Fresh archive required')
        with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_STORED) as output:
            for path in paths:
                run_storage.admit(archive, path.stat().st_size+128*2**20)
                output.write(path, path.relative_to(report).as_posix())
            output.writestr('PRESERVATION-MANIFEST.json', json.dumps(rows, indent=2)+'\n')
        with zipfile.ZipFile(archive) as output:
            life.require(set(output.namelist()) == {r['path'] for r in rows} | {'PRESERVATION-MANIFEST.json'}, 'Archive members differ')
            for record in rows:
                with output.open(record['path']) as stream:
                    life.require(hashlib.file_digest(stream, 'sha256').hexdigest() == record['sha256'], 'Archived bytes differ')
                life.require(row(report/record['path']) == record, 'Source changed before retirement')
        receipt = dict(label=label, archive=str(archive), archive_bytes=archive.stat().st_size,
                       archive_sha256=digest(archive), files=len(rows), source_bytes=sum(r['bytes'] for r in rows),
                       verified=True, retired=False)
        result['archives'].append(receipt)
        life.atomic_json(receipt_path, result)
        shutil.rmtree(directory)
        receipt['retired'] = True
        life.atomic_json(receipt_path, result)
    life.require(result['archives'], 'No quality captures found')
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    parser.add_argument('--archive-root', required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(preserve(args.report, args.archive_root)))

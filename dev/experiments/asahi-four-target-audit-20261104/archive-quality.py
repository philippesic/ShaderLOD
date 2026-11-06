"""Keep bit-exact PNG evidence in verified gzip archives, plus five loose keyframes.

Run only with no owned GPU client; explicit task report roots, never broad cleanup.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys
import tarfile
import time

HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2];sys.path.insert(0,str(ROOT/'scripts'))
import dev_client as dev
import experiment_lifecycle as life
import run_storage as storage

def digest_stream(stream):
    h=hashlib.sha256()
    for block in iter(lambda:stream.read(1024*1024),b''):h.update(block)
    return h.hexdigest()

p=argparse.ArgumentParser();p.add_argument('reports',nargs='+');a=p.parse_args()
life.require(not dev.clients(dev.process_table()),'Archive only while GPU clients are idle')
for name in a.reports:
    report=storage.safe_path(HERE/name);life.require(report.parent==HERE,'Explicit audit report root required')
    summary=json.loads((report/'summary.json').read_text())
    # Later managed comparisons replace the runtime profile directory. Do not
    # reopen an old operation as a runtime/CAS owner merely to retain its files.
    operation=json.loads((report/'operation.json').read_text());identity=report.stat()
    life.require(operation['output']==str(report) and operation['output_identity']==[identity.st_dev,identity.st_ino],
                 'Original owned output directory identity changed')
    life.require(operation['state'] in life.TERMINAL and operation.get('release_verified') is True and summary.get('configuration_restored') is True
                 and summary.get('clients_remaining')==0,'Terminal/restored/released capture required')
    for directory in sorted(report.glob('quality-*')):
        if directory.is_file() and not directory.is_symlink():continue  # retain review JSON/overview files
        life.require(not directory.is_symlink() and directory.is_dir(),'Owned quality directory required')
        manifest=json.loads((directory/'manifest.json').read_text());frames=[r for r in manifest['frames'] if r.get('status')=='written']
        if not frames:continue
        retention=directory/'archive-retention.json'
        if retention.exists():continue
        expected={r['file']:r['png_sha256'] for r in frames}
        life.require(len(expected)==len(frames) and all(re.fullmatch(r'frame-\d{4}\.png',n) for n in expected),'Unique flat PNG inventory required')
        images=[storage.safe_path(directory/n) for n in expected]
        for image in images:life.require(image.parent==directory and image.is_file() and life.digest(image)==expected[image.name],'Current frame differs from immutable recorder receipt')
        source_bytes=sum(f.stat().st_size for f in images);free_before=shutil.disk_usage(HERE).free
        life.require(free_before>=storage.MIN_FREE+source_bytes,'Archive worst-case reserve unavailable')
        archive=directory/'lossless-native-frames.tar.gz';temporary=directory/'lossless-native-frames.tar.gz.tmp'
        life.require(not archive.exists() and not temporary.exists(),'Fresh archive required')
        print('ARCHIVE',directory.name,name,source_bytes,flush=True)
        with temporary.open('xb') as file:
            with tarfile.open(fileobj=file,mode='w:gz',compresslevel=1) as tar:
                for image in images:tar.add(image,arcname=image.name,recursive=False)
        found={}
        with tarfile.open(temporary,'r|gz') as tar:
            for member in tar:
                life.require(member.isfile() and member.name in expected and member.name not in found,'Unexpected archive entry')
                with tar.extractfile(member) as stream:found[member.name]=digest_stream(stream)
        life.require(found==expected,'Lossless archive SHA-256 verification failed')
        temporary.rename(archive)
        ordered=sorted(expected);keep={ordered[round(i*(len(ordered)-1)/4)] for i in range(5)}
        # Recheck exact source ownership/recoverability immediately before removing duplicates.
        for image in images:
            life.require(life.digest(image)==expected[image.name],'Frame changed before cleanup; preserve it')
            if image.name not in keep:image.unlink()
        free_after=shutil.disk_usage(HERE).free
        record={'source':'CameraQualitySession completed/drained PNG writes; per-frame encoded byte hashes in manifest.json',
                'archive':str(archive),'archive_sha256':life.digest(archive),'archive_bytes':archive.stat().st_size,
                'verified_pngs':len(expected),'loose_keyframes':sorted(keep),'source_png_bytes':source_bytes,
                'removed_loose_duplicate_pngs':len(expected)-len(keep),'free_bytes_before':free_before,
                'free_bytes_after':free_after,'observed_free_delta_bytes':free_after-free_before,
                'restoration':'Extract the flat frame-*.png members from the verified tar.gz into a fresh directory; compare each encoded PNG SHA-256 with manifest.json. No pixels or metadata were changed.',
                'scope':'Lossless transport compression. Original incomplete/failed capture status remains unchanged. Concurrent activity can affect free-byte deltas.'}
        record['original_operation_manifest_sha256']=life.digest(report/'operation.json')
        record['recorder_manifest_sha256']=life.digest(directory/'manifest.json')
        retention.write_text(json.dumps(record,indent=2)+'\n')
        index_path=HERE/'retained-evidence.json'
        index=json.loads(index_path.read_text()) if index_path.exists() else {'scope':'Additional terminal artifact retention; original operation/recovery records remain unchanged.','archives':[]}
        index['archives'].append({'retention':str(retention),'retention_sha256':life.digest(retention),
                                  'archive':str(archive),'archive_sha256':record['archive_sha256']})
        life.atomic_json(index_path,index)
        print('RETAINED',name,directory.name,len(expected),'frames; free delta',record['observed_free_delta_bytes'],flush=True)

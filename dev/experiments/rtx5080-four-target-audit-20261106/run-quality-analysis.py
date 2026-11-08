"""Inspect preserved RGB8 bytes after all scheduled performance windows finish."""
import argparse
import hashlib
import json
from pathlib import Path
import runpy
import subprocess
import time

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
native = runpy.run_path(str(HERE/'preserve-old-runs.py'))['native']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--after-status', default='modern-regression-v7-retry-status.json')
    args = parser.parse_args()
    assert Path(args.after_status).name == args.after_status
    status = HERE/'quality-analysis-status.json'
    assert not status.exists(), 'Fresh analysis status required'

    def stage(name, **fields):
        status.write_text(json.dumps(dict(stage=name, time=time.time(), **fields), indent=2)+'\n')
        print(name, fields.get('report', ''), flush=True)

    try:
        stage('waiting-for-performance-queue')
        deadline = time.monotonic()+7200
        while True:
            path = HERE/args.after_status
            if path.exists():
                current = json.loads(path.read_text())
                assert current['stage'] != 'failed', 'Runtime continuation needs repair before analysis'
                if current['stage'] == 'diagnostics-collected-quality-analysis-and-final-audit-pending':
                    break
            assert time.monotonic() < deadline
            time.sleep(5)
        script = HERE/'analyze-quality-archive.py'
        subprocess.run(['scp', '-q', '-o', 'BatchMode=yes', '-o', 'RemoteCommand=none', str(script),
                        'pstack-pc:C:/ShaderLODBench/ShaderLOD/'+str(script.relative_to(ROOT))], check=True)
        reports = ['makeup-copy-motion-quality-v5', 'bsl-bloom-motion-quality-v6',
                   'reimagined-bloom-motion-quality-v6', 'unbound-bloom-motion-quality-v6',
                   'makeup-sky-motion-quality-v6']
        for report in reports:
            stage('analyzing-preserved-frames', report=report)
            receipt = json.loads((HERE/(report+'-preservation.json')).read_text())
            archives = receipt.get('archives', [receipt] if 'archive' in receipt else [])
            temporary_copies = []
            for archive in archives:
                probe = "from pathlib import Path;import json,shutil;p=Path("+repr(archive['archive'])+");print(json.dumps(dict(exists=p.exists(),free=shutil.disk_usage('C:/').free)))"
                available = json.loads(native(probe, timeout=30))
                if available['exists']:
                    continue
                local = Path(archive['local_archive'])
                assert available['free'] >= 32*2**30+archive['archive_bytes']+128*2**20
                with local.open('rb') as stream:
                    assert hashlib.file_digest(stream, 'sha256').hexdigest() == archive['archive_sha256']
                subprocess.run(['scp', '-q', '-o', 'BatchMode=yes', '-o', 'RemoteCommand=none', str(local),
                                'pstack-pc:'+archive['archive'].replace('\\', '/')], check=True)
                temporary_copies.append(archive)
            output_name = report+'-analysis-v7'
            code = r'''
import hashlib,json,subprocess
from pathlib import Path
root=Path(r'C:\ShaderLODBench\ShaderLOD');here=root/'dev/experiments/rtx5080-four-target-audit-20261106'
probe="import sys;sys.path.insert(0,'scripts');import dev_client as d;assert not d.clients(d.process_table())"
venv=r'C:\ShaderLODBench\tools\python\Scripts\python.exe'
subprocess.run([venv,'-c',probe],cwd=root,check=True)
archives=ARCHIVES
for row in archives:
 p=Path(row['archive']);assert p.stat().st_size==row['archive_bytes']
 with p.open('rb') as stream:assert hashlib.file_digest(stream,'sha256').hexdigest()==row['archive_sha256']
command=[venv,'-u',str(here/'analyze-quality-archive.py'),*[r['archive'] for r in archives],'--output',str(here/OUTPUT)]
if SELECTED:command.append('--selected-only')
with (here/(OUTPUT+'.log')).open('xb') as stream:
 p=subprocess.run(command,cwd=root,stdout=stream,stderr=subprocess.STDOUT,timeout=1200)
assert p.returncode==0,'Analysis failed; retained native log'
d=json.loads((here/OUTPUT/'quality-analysis.json').read_text());print(json.dumps(dict(captures_complete=d['captures_complete'],analyzed_slots=d['analyzed_slots'],aggregate=d['aggregate'])))
'''.replace('ARCHIVES', repr(archives)).replace('OUTPUT', repr(output_name)).replace('SELECTED', repr(report == 'makeup-copy-motion-quality-v5'))
            try:
                result = json.loads(native(code, timeout=1400))
            except subprocess.CalledProcessError as error:
                print(error.stdout, error.stderr)
                raise RuntimeError('Native pixel analysis failed; inspect retained log') from None
            assert result['captures_complete'], 'Incomplete raw captures remain unqualified'
            target = HERE/output_name
            assert not target.exists()
            target.mkdir()
            for filename in ('quality-analysis.json', 'contact-sheet.jpg', 'detail-sheet.png'):
                subprocess.run(['scp', '-q', '-o', 'BatchMode=yes', '-o', 'RemoteCommand=none',
                                'pstack-pc:C:/ShaderLODBench/ShaderLOD/'+str(target.relative_to(ROOT)/filename),
                                str(target/filename)], check=True)
            for archive in temporary_copies:
                with Path(archive['local_archive']).open('rb') as stream:
                    assert hashlib.file_digest(stream, 'sha256').hexdigest() == archive['archive_sha256']
                code = "from pathlib import Path;import hashlib;p=Path("+repr(archive['archive'])+");assert p.parent==Path(r'C:\\ShaderLODBench\\quality-archives');assert hashlib.file_digest(p.open('rb'),'sha256').hexdigest()=="+repr(archive['archive_sha256'])+";p.unlink()"
                native(code, timeout=120)
            stage('case-analysis-collected', report=report, **result)
        stage('pixel-analysis-collected-visual-review-and-final-audit-pending')
    except Exception as error:
        stage('failed', error=str(error))
        raise


if __name__ == '__main__':
    main()

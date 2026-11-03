#!/usr/bin/env python3
"""Focused local checks, with optional authentic archive integrations explicitly selected."""
import argparse
import json
import os
from pathlib import Path
import sys
import unittest
import tempfile

ROOT=Path(__file__).resolve().parents[1]
FAST=('test_dev_client','test_experiment_lifecycle','test_run_benchmark','test_run_storage',
      'test_benchmark_storage','test_experiment_plan_preflight','test_linux_arm64_runtime')
OPTIONAL_PREFLIGHT={
 'test_sildur12111_debug_exports_refuse_native_modal_context_conflict',
 'test_sildur12111_original_accepts_authentic_native115_and_rejects_mixed_modes',
 'test_sildur12111_original_refuses_foreign_inputs_and_export_alias',
}
PACKS={
 'sildur':("dependencies/upstream/pack-expansion/Sildur's Vibrant Shaders v2.02 Extreme.zip",()),
 'photon':('dependencies/upstream/photon_v1.3b.zip',('test_photon_volume_resolution',)),
 'solas':(None,('test_solas_astralex_lod_r1',)),
 'bliss':('dependencies/upstream/pack-expansion/Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip',('test_bliss_cloud_budget',)),
 'reimagined':('dependencies/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip',('test_reimagined_depth_recipe_binding',)),
}

def main(argv=None):
 local_tools=ROOT/'dependencies/toolchain/bin'
 if local_tools.is_dir(): os.environ['PATH']=str(local_tools)+os.pathsep+os.environ.get('PATH','')
 local_python=ROOT/'dependencies/test-python/usr/lib64/python3.13/site-packages'
 if local_python.is_dir():
  sys.path.insert(0,str(local_python))
  os.environ['PYTHONPATH']=str(local_python)+os.pathsep+os.environ.get('PYTHONPATH','')
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('--integration',choices=PACKS,action='append',default=[])
 args=p.parse_args(argv);modules=list(FAST);missing=[]
 for name in args.integration:
  archive,tests=PACKS[name]
  archives=[archive] if archive else [json.loads(p.read_text())['original_archive'] for p in sorted((ROOT/'patches/solas-astralex-lod-r1').glob('*.json'))]
  for archive in archives:
   if not (ROOT/archive).is_file():missing.append(f'{name}: pinned input required: {ROOT/archive}')
  modules.extend(tests)
 if missing:
  print('Optional integration prerequisites missing (no selected checks run):\n'+'\n'.join(missing),file=sys.stderr)
  print('Restore the exact pinned inputs (preserving archive licenses); do not substitute another version. Run without --integration for self-contained lifecycle checks.',file=sys.stderr)
  return 2
 sys.path[:0]=[str(ROOT/'tests'),str(ROOT/'scripts')]
 os.environ['PYTHONPATH']=os.pathsep.join([str(ROOT/'scripts'),str(ROOT/'tests'),os.environ.get('PYTHONPATH','')])
 suite=unittest.defaultTestLoader.loadTestsFromNames(modules)
 def selected(tests):
  for test in tests:
   if isinstance(test,unittest.TestSuite):yield from selected(test)
   elif test.id().split('.')[-1] not in OPTIONAL_PREFLIGHT or 'sildur' in args.integration:yield test
 if 'sildur' not in args.integration:print('Not selected: 3 authentic Sildur preflight cases (--integration sildur); pack checks require explicit --integration.',flush=True)
 # Storage-copy fixtures retain the real32GiB reserve; /tmp can be a small tmpfs.
 workspace = ROOT / 'dev/experiments'
 workspace.mkdir(parents=True, exist_ok=True)
 with tempfile.TemporaryDirectory(prefix='fast-check-fixtures-',dir=workspace) as temp:
  previous=tempfile.tempdir;tempfile.tempdir=temp;os.environ['TMPDIR']=temp
  try:result=unittest.TextTestRunner(verbosity=1).run(unittest.TestSuite(selected(suite)))
  finally:tempfile.tempdir=previous
 return 0 if result.wasSuccessful() else 1

if __name__=='__main__':sys.exit(main())

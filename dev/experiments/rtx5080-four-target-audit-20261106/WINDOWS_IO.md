# Windows ownership-file access repair

The V3 batch finishes six functional checks valid/restored/released, then stops on
reimagined-baseline-gl46-r1 before scoring. Its owner.json read raises plain
PermissionError(errno13), with no WinError prefix. The earlier retry accepted only
winerror5/32/33 and therefore missed CPython's CRT-backed open failure.

A native exclusive-handle probe reproduces errno13 with winerror=None. The minimal
repair admits Windows EACCES/EPERM without winerror to the existing three-second
bounded read/publication retry. POSIX errors, invalid JSON and permanent denial
still fail; no command is replayed and no ownership/identity check is weakened.
The native regression fixture holds a real exclusive Windows handle, observes
CRT read denial, releases it after150ms, and verifies the maintained reader
recovers. Persistent-denial fixtures retain the time bound.103 benchmark tests
pass on both hosts (3 Asahi/11 Windows platform skips).

The failed baseline is explicitly recovered with --preserve-conflicts after exact
process release. Uncertain FML/Iris/options/selected-pack bytes are retained in its
report before restoring the recorded originals and checking the full world/profile
map. No scored windows exist and the attempt cannot be used as a performance bar.

Before replacing source, all5034 build bindings plus scripts/tests/definitions
were verified and archived. freeze-owned-inputs.py now makes this repeated
preservation procedure reusable; it requires stopped/released/restored reports,
checks exact controller absence, uses capacity admission, verifies every archived
hash and rechecks source hashes and runtime release. Its compact receipt is
native-before-shared-reader-92b154c-20261106.json. The ZIP is also hash-verified
in Asahi's ignored frozen/ directory. Native original report evidence stays intact.

The following scoring queue uses fresh reimagined-baseline-gl46-r1-retry1,
the remaining three modern-context baselines, and the first55 maintained
optimization/control comparisons. Its metadata identifies this controller repair;
rendering implementations are unchanged from the last verified native snapshot.

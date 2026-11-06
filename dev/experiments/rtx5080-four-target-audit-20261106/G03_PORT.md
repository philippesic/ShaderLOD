# G03 RTX5080 source path repair

The first native RTX5080 check failed with “Early-alpha actual qualified draw
admission unavailable.” It restored/released successfully and is not a slower
performance result. Its source diagnostic proves the exact API, declaration,
native100 profile, original/generated source and requested compile flag, but both
source rewrites and qualified native submissions were zero. The source-only route
was explicitly gated to the Asahi OS/architecture/GPU/driver tuple.

The actual native RTX5080 GL3.2 linked CUTOUT fragment was extracted from the
maintained untimed source observation and compared byte-for-byte with the existing
complete semantic fixture. They are identical; see g03-native-source-parity.json
and makeup-g03-check-native-r1. No partial-source acceptance was introduced.

The candidate patch adds the finite observed Windows amd64 / RTX5080 / NVIDIA617.42
GL3.2 and GL4.6 tuple to the existing host policy and platform dispatch. The complete
fragment hash, exact native alpha policy, current pack/options, stack/API, no DH or
Voxy, actual compiler/link ownership and runtime work evidence remain mandatory.
All native derivative-dependent reads stay before the added discard, and native
output/final alpha test stay literal. Existing Asahi host acceptance is retained;
other NVIDIA models/drivers, integrated GPUs and other platforms remain refused.
This prepares live validation, not a compatibility or visual qualification claim.
G03 work labels are now platform-neutral; the old Asahi analyzer accepts both
historical and new labels without changing its retained measurements.

BSL's source-only G03 also requires the exact three-mod optimizer tuple. The
maintained benchmark CLI previously always selected minimal. It now exposes a
finite --performance-stack optimized option for G03 and its matched original
startup/baselines only. It verifies pinned JARs, freezes them as operation inputs,
and checks actual JVM module versions before scoring. Other private/frozen
protocols retain their minimal stack. This fixes the maintained harness rather
than injecting Gradle arguments through a report-local timing caller.

Local checks pass: complete-source/alpha/host refusal tests, BSL source/policy
tests and development compilation;96 benchmark Python tests pass (2 platform
skips). MakeUp native r2 functional retry passes with an actual linked rewritten CUTOUT,
completed native submissions, all three arms, restoration and zero clients. Native
Python tests also pass (11 POSIX skips). Quality and scored performance remain
outstanding; no G03 speedup has been claimed.

BSL optimized setup initially exposed two harness defects before scoring. The
release labels include a loader suffix that the pinned JARs' module versions do
not; the lock now records both explicitly, using the JARs' own NeoForge metadata
and actual JVM observations. The strict runtime guard remains an exact match.
Four known settings files were also created by the selected modules. Future
operations predeclare their absent paths in normal CAS restoration. For the older
failed setup, explicit maintained recover --preserve-optional-configs retained
the new files before restoring the original profile/world maps. Existing or
unknown edits remain protected. Frozen old harness inputs were preserved before
the recovery implementation was installed. Recovery completed failed-restored
with zero replayed commands; BSL optimized r2 is now running.

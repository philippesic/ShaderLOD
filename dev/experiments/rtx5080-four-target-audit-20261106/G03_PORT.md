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


## BSL source path and startup recovery

BSL original-only baseline r1 is valid on native GL3.2 at3840×2160, after120s
conditioning and three20s windows. It is a minimal-stack baseline; the G03
optimized tuple needs its own controls and baseline. This supplies no candidate
performance verdict.

The native BSL CUTOUT text is byte-identical to the complete existing semantic
fixture. Windows still selected the legacy generated adapter, bypassing the
pure source lowering. Selection now keeps the unchanged original only for the
finite verified Windows host and the exact BSL singleton; source, native384,
module/API and resource checks remain independent. Actual shader text/type,
compile status, successful linked program and immutable terrain-slot ownership
are checked. The terrain slot is read explicitly: Iris's general CUTOUT lookup
returns the shadow program during shadow rendering and had invalidated a valid
terrain certificate in r3. The pinned Iris API/reflection test covers that slot.

R4 failed before any capture on a startup file-checkpoint race. Family migration
atomically appended the BSL flag map after the initial prepare receipt and after
the controller acknowledged startup. The maintained harness now keeps that same
startup mutation open through subsequent read-only callbacks and one second of
stable tracked file bytes. Explicit stopped-runtime recovery retained the late
configuration under configuration-conflicts-preserved before restoring its
original backup; zero commands were replayed. Recovery requires verified exact
process absence and runtime/world locks. Ordinary restoration still refuses
unacknowledged changes. R4 is failed-restored; the preserved file and old frozen
Python inputs are retained in its report. R5 passed the repaired startup boundary.

R5 established immutable linked terrain ownership but its observation counted no
submissions. R6 untimed diagnostics found the real candidate program was submitted,
but a no-op family persistence incremented the global request generation after
linking. That mutable counter is not the immutable program lifetime. The observer
now requires actual current program/context/pipeline, exact flags, selected pack,
world and resource identity; the comparison separately pins request generation
across each scored window. Compile/link generation matching remains enforced.
The observer grants no runtime permission and is disarmed before timing.

The fresh BSL G03 optimized/native ABBA process completed VALID and restored
all configuration/world inputs with zero clients. Both candidate windows retain
current rewritten CUTOUT ownership and untimed completed native submissions; the
observer is disarmed during timing. R1–R6 functional failures are not performance
regressions. No BSL candidate gain is claimed from this single process.
Frozen pre-repair sources/classes remain in the corresponding reports. Relevant
Java source/policy/fallback tests and compilation pass;98 benchmark Python tests
pass (2 Asahi skips),29 lifecycle tests pass, and focused real-process startup/
restoration checks pass. The full169-test Python benchmark/smoke suite also passes (2 Asahi skips),
including real fixture-process restoration and deliberate refusal paths. Its fake
client now reports explicit simulated GPU metadata; real GL validation is intact.


First BSL G03 timing (one process, exact optimized tuple, native GL3.2, saved pose1,
3840×2160/native AA;120s conditioning, four20s balanced windows):

| Window | Mean ms | Frame variance ms² | Frame SD ms |
| --- | ---: | ---: | ---: |
| Native0 | 2.986397 | 0.010977 | 0.104769 |
| Candidate0 | 2.997879 | 0.027997 | 0.167323 |
| Candidate1 | 3.008409 | 0.010564 | 0.102784 |
| Native1 | 3.007030 | 0.010888 | 0.104346 |

Native group mean2.996713ms, candidate3.003144ms: apparent reduction−0.214594%.
Native window-mean variance0.000212859ms²; candidate0.0000554442ms².
Between-process variance is unavailable (n1), never zero. The small difference is
unresolved against within-process drift; independent opposite-order repetition
and known-null controls remain required. This is not a visual qualification.
Raw [summary](bsl-g03-test-optimized-native-abba-r1/summary.json) and original
samples are retained off-machine. Initial MakeUp G03 still inspection shows no
obvious missing foliage/geometry; clouds/temporal detail differ between captures,
and moving-camera quality remains untested.
